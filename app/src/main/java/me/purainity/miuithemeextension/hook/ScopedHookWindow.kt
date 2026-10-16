package me.purainity.miuithemeextension.hook

/** 每进程安装、按线程授权。每次调用（含未启用的嵌套调用）都会压入一帧 */
class ScopedHookWindow<T>(private val install: () -> List<() -> Unit>, private val onError: (Exception) -> Unit) {
    class Frame<T> internal constructor(val value: T, internal val leased: Boolean)
    private val stack: ThreadLocal<ArrayList<Frame<T>>> = object : ThreadLocal<ArrayList<Frame<T>>>() {
        override fun initialValue(): ArrayList<Frame<T>> = ArrayList()
    }
    private var references = 0
    private var removers = emptyList<() -> Unit>()
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
    fun current(): T? = requireNotNull(stack.get()).lastOrNull()?.takeIf { it.leased }?.value
    fun exit(frame: Frame<T>) {
        val frames = requireNotNull(stack.get())
        val index = frames.indexOfLast { it === frame }
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
