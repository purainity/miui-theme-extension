package me.purainity.miuithemeextension.hook

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference

/** 身份弱引用映射（8.1）：宿主模型类可能实现值相等。标签必须跟随对象本身，绝不跟随另一个值相等的模型 */
class WeakIdentityMap<V> {
    private val queue = ReferenceQueue<Any>()
    // 身份键：以 identityHashCode 定位，目标被回收后自然失配
    private class Key(value: Any, queue: ReferenceQueue<Any>? = null) : WeakReference<Any>(value, queue) {
        private val identity = System.identityHashCode(value)
        override fun hashCode() = identity
        override fun equals(other: Any?): Boolean = this === other ||
            other is Key && get() != null && get() === other.get()
    }
    private val values = HashMap<Key, V>()
    // 清理键已被回收的条目
    private fun clean() { while (true) values.remove(queue.poll() ?: break) }
    @Synchronized operator fun get(key: Any): V? { clean(); return values[Key(key)] }
    @Synchronized operator fun set(key: Any, value: V) { clean(); values[Key(key, queue)] = value }
    @Synchronized fun remove(key: Any): V? { clean(); return values.remove(Key(key)) }
}
