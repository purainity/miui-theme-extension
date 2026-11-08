package me.purainity.miuithemeextension.ui

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** 让内容避开透明系统栏与屏幕缺口，需要时也避开输入法 */
fun View.applySystemBarInsets(includeIme: Boolean = false) {
    val initialLeft = paddingLeft
    val initialTop = paddingTop
    val initialRight = paddingRight
    val initialBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val insetTypes = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or
            if (includeIme) WindowInsetsCompat.Type.ime() else 0
        val insets = windowInsets.getInsets(insetTypes)
        view.setPadding(
            initialLeft + insets.left,
            initialTop + insets.top,
            initialRight + insets.right,
            initialBottom + insets.bottom,
        )
        windowInsets
    }
    ViewCompat.requestApplyInsets(this)
}
