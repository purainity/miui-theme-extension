package me.purainity.miuithemeextension.hook

/** 瞬时 DRM 窗口（8.3）：每进程安装、按线程授权。每次调用（含未启用的嵌套调用）都会压入一帧 */
class ScopedHookWindow<T>(private val install: () -> List<() -> Unit>, private val onError: (Exception) -> Unit) {
    /** 一次调用压入的一帧：携带窗口值与该帧是否租借了全局 Hook */
    class Frame<T> internal constructor(val value: T, internal val leased: Boolean)
    // 每线程的帧栈，嵌套调用按后进先出配对弹出
    private val stack: ThreadLocal<ArrayList<Frame<T>>> = object : ThreadLocal<ArrayList<Frame<T>>>() {
        override fun initialValue(): ArrayList<Frame<T>> = ArrayList()
    }
    // 进程级租借计数与卸载动作，归零时整体卸载
    private var references = 0
    private var removers = emptyList<() -> Unit>()
    /** 压入一帧；enabled 时首帧安装 Hook 并递增引用计数，安装失败降级为未租借帧 */
    fun enter(value: T, enabled: Boolean): Frame<T> {
        val leased = synchronized(this) {
            if (!enabled) false else {
                try {
                    if (references == 0) removers = install().also { check(it.isNotEmpty()) }
                    references++
                    true
                } catch (e: Exception) { onError(e); false }
            }
        }
        return Frame(value, leased).also { requireNotNull(stack.get()).add(it) }
    }
    /** 当前线程最内层已租借帧的窗口值，无租借帧返回 null */
    fun current(): T? = requireNotNull(stack.get()).lastOrNull()?.takeIf { it.leased }?.value
    /** 弹出指定帧；租借计数归零时逆序卸载全部 Hook */
    fun exit(frame: Frame<T>) {
        val frames = requireNotNull(stack.get())
        val index = frames.indexOfLast { it === frame }
        // 帧不存在说明已退出过：重复退出幂等（8.3）
        if (index < 0) return
        frames.removeAt(index)
        if (frames.isEmpty()) stack.remove()
        if (frame.leased) synchronized(this) {
            references--
            if (references == 0) {
                removers.asReversed().forEach { remove -> try { remove() } catch (e: Exception) { onError(e) } }
                removers = emptyList()
            }
        }
    }
}
