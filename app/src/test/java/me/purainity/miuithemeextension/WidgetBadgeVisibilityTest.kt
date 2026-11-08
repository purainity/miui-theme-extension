package me.purainity.miuithemeextension

import android.view.View
import me.purainity.miuithemeextension.config.ModuleConfig
import me.purainity.miuithemeextension.config.Setting
import me.purainity.miuithemeextension.hook.WidgetBadgeVisibility
import org.junit.Assert.*
import org.junit.Test

class WidgetBadgeVisibilityTest {
    @Test fun hidesOnlyVisibleBadgesWhenAllGatesAreEnabled() {
        val state = WidgetBadgeVisibility()
        val badge = Any()
        val enabled = ModuleConfig(values = mapOf(
            Setting.MASTER to true, Setting.WIDGETS to true, Setting.WIDGET_BADGES to true,
        ))
        assertEquals(View.GONE, state.intercept(badge, View.VISIBLE, enabled.enabled(Setting.WIDGET_BADGES)))
        assertEquals(View.INVISIBLE, state.intercept(badge, View.INVISIBLE, enabled.enabled(Setting.WIDGET_BADGES)))
        assertEquals(View.GONE, state.intercept(badge, View.GONE, enabled.enabled(Setting.WIDGET_BADGES)))
        for (gate in listOf(Setting.MASTER, Setting.WIDGETS, Setting.WIDGET_BADGES)) {
            val disabled = enabled.copy(values = enabled.values + (gate to false))
            assertEquals(View.VISIBLE, state.intercept(badge, View.VISIBLE, disabled.enabled(Setting.WIDGET_BADGES)))
        }
    }

    @Test fun replaysTheLatestHostVisibilityAcrossToggleAndRebinding() {
        val state = WidgetBadgeVisibility()
        val badge = Any()
        assertEquals(View.VISIBLE, state.intercept(badge, View.VISIBLE, false))
        val first = state.snapshot().single().second
        assertEquals(View.GONE, state.intercept(badge, first, true))
        assertEquals(View.VISIBLE, state.snapshot().single().second)
        assertEquals(View.INVISIBLE, state.intercept(badge, View.INVISIBLE, true))
        val latest = state.snapshot().single().second
        assertEquals(View.INVISIBLE, state.intercept(badge, latest, false))
        assertEquals(View.VISIBLE, state.intercept(badge, View.VISIBLE, false))
    }

    @Test fun tracksEqualObjectsByIdentity() {
        data class Badge(val value: Int)
        val first = Badge(1)
        val second = Badge(1)
        val state = WidgetBadgeVisibility()
        state.intercept(first, View.VISIBLE, true)
        state.intercept(second, View.INVISIBLE, true)
        val snapshot = state.snapshot()
        assertEquals(2, snapshot.size)
        assertEquals(View.VISIBLE, snapshot.single { it.first === first }.second)
        assertEquals(View.INVISIBLE, snapshot.single { it.first === second }.second)
    }
}
