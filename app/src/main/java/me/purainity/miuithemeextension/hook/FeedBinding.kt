package me.purainity.miuithemeextension.hook

import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * 大图标 feed 绑定方法的“商品载体”定位。
 *
 * 推荐流卡片（LargeIconHomeCardViewHolder 等）的绑定方法参数形态有两种：直接传 `UIProduct`，
 * 或传持有 `UIProduct` 实例字段的对象参数（如 LargeIconProductElement 包装元素）。
 * 方法名随宿主混淆变化，因此按参数结构匹配，不出现任何硬编码混淆名（规划 5.2）。
 */
object FeedBinding {
    /**
     * 返回商品载体：参数下标 + 取值字段（字段为 null 表示参数本身即商品对象）。
     * 匹配规则：恰一个 `UIProduct` 直参，或恰一个非 int 对象参数且其类声明了 `UIProduct` 实例字段；
     * 其余参数仅允许 int/Integer。不匹配返回 null。
     */
    fun productSlot(params: Array<Class<*>>, product: Class<*>): Pair<Int, Field?>? {
        fun intOnly(type: Class<*>) = type == Int::class.javaPrimitiveType || type == Int::class.javaObjectType
        val direct = params.indices.filter { params[it] == product }
        if (direct.isNotEmpty()) {
            if (direct.size == 1 && params.indices.all { it == direct[0] || intOnly(params[it]) }) return direct[0] to null
            return null
        }
        val objects = params.indices.filter { !intOnly(params[it]) }
        val index = objects.singleOrNull() ?: return null
        val field = params[index].declaredFields.firstOrNull { it.type == product && !Modifier.isStatic(it.modifiers) }
            ?: return null
        return index to field
    }
}
