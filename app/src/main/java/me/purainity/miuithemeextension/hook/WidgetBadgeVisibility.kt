package me.purainity.miuithemeextension.hook

import android.view.View

/** 以弱引用保存宿主最近请求的角标可见性，供开关切换时重放 */
internal class WidgetBadgeVisibility {
    private val requested = WeakIdentityMap<Int>()

    fun intercept(view: Any, visibility: Int, hidden: Boolean): Int {
        requested[view] = visibility
        return if (hidden && visibility == View.VISIBLE) View.GONE else visibility
    }

    fun snapshot(): List<Pair<Any, Int>> = requested.snapshot()
}
