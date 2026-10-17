package me.purainity.miuithemeextension.hook

import me.purainity.miuithemeextension.config.ModuleConfig
import me.purainity.miuithemeextension.config.ResourceKind
import me.purainity.miuithemeextension.config.ResourcePolicy
import java.lang.ref.WeakReference

/**
 * 已改写对象的回滚日志（8.6.1）：记录模块主动改写过的宿主对象，并在对应开关关闭时恢复其原始值。
 * 主题壁纸会复用详情页与 feed 的内存对象，仅停止 Hook 不能撤销已写入的“已购买/免费”状态。
 * 因此记录必须按对象身份去重、仅首次捕获原值，且不能阻止宿主回收对象
 */
class ReversibleMutationJournal {
    private data class Entry(
        val target: WeakReference<Any>,
        val productId: String,
        val kind: ResourceKind,
        val restore: (Any) -> Unit
    )

    private val tracked = WeakIdentityMap<Unit>()
    private val entries = ArrayList<Entry>()

    /** 首次改写同一对象时记录原状（按对象身份去重）；重复改写不可覆盖最初的原状 */
    @Synchronized
    fun record(target: Any, productId: String, kind: ResourceKind, restore: (Any) -> Unit): Boolean {
        if (tracked[target] != null) return false
        tracked[target] = Unit
        entries += Entry(WeakReference(target), productId, kind, restore)
        return true
    }

    /** 恢复已不再被当前配置允许改写的对象，并清理已被宿主回收的记录 */
    @Synchronized
    fun restoreDisabled(config: ModuleConfig) {
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val target = entry.target.get()
            if (target == null) {
                iterator.remove()
                continue
            }
            if (!ResourcePolicy.allows(config, entry.productId, entry.kind)) {
                try {
                    entry.restore(target)
                } catch (error: Throwable) {
                    HookLog.error("restore cached resource", error)
                }
                tracked.remove(target)
                iterator.remove()
            }
        }
    }
}
