package me.purainity.miuithemeextension

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.purainity.miuithemeextension.ui.OpenSourceProjects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutActivityTest {
    @Test
    fun headerIconMatchesTextHeightAndLicenseRowsWrap() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val density = activity.resources.displayMetrics.density
                fun measure(view: View, widthDp: Int) {
                    val width = (widthDp * density + 0.5f).toInt()
                    view.measure(
                        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    )
                    view.layout(0, 0, width, view.measuredHeight)
                }
                val header = activity.layoutInflater.inflate(R.layout.view_about_header, null)
                header.findViewById<TextView>(R.id.appVersion).text = "版本 1.0（1）"
                listOf(240, 360).forEach { width ->
                    measure(header, width)
                    val icon = header.findViewById<View>(R.id.appIcon)
                    val text = header.findViewById<View>(R.id.nameAndVersion)
                    assertTrue(text.height > 0)
                    assertEquals(text.top, icon.top)
                    assertEquals(text.bottom, icon.bottom)
                    assertEquals(icon.height, icon.width)
                }

                val row = activity.layoutInflater.inflate(R.layout.item_open_source_project, null)
                val title = row.findViewById<TextView>(R.id.title)
                val url = row.findViewById<TextView>(R.id.projectUrl)
                title.text = "AndroidX Very Long Project Name With Many Components"
                url.text = "https://github.com/example/project-with-a-very-long-name"
                measure(row, 240)
                assertTrue(title.layout.lineCount > 1)
                assertTrue(url.layout.lineCount > 1)
                assertNull(title.ellipsize)
                assertNull(url.ellipsize)
            }
        }
    }

    @Test
    fun aboutCardsUseSecondaryPageSpacingAndListProjects() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                fun dp(value: Int) = (value * activity.resources.displayMetrics.density + 0.5f).toInt()
                val projectCard = activity.findViewById<View>(R.id.projectCard)
                assertEquals(dp(16), (projectCard.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
                listOf(R.id.thanksHeading, R.id.licensesHeading).forEach { id ->
                    val heading = activity.findViewById<View>(id)
                    val margins = heading.layoutParams as ViewGroup.MarginLayoutParams
                    assertEquals(dp(20), margins.topMargin)
                    assertEquals(dp(8), margins.bottomMargin)
                    assertEquals(dp(4), margins.marginStart)
                    assertEquals(dp(4), margins.marginEnd)
                }
                val licenses = activity.findViewById<ViewGroup>(R.id.licenses)
                assertEquals(OpenSourceProjects.all.size, licenses.childCount)
                OpenSourceProjects.all.forEachIndexed { index, project ->
                    val row = licenses.getChildAt(index)
                    assertEquals(project.name, row.findViewById<TextView>(R.id.title).text.toString())
                    assertEquals(
                        activity.getString(R.string.licenses_project_meta, project.author, project.license),
                        row.findViewById<TextView>(R.id.projectMeta).text.toString(),
                    )
                    assertEquals(project.url, row.findViewById<TextView>(R.id.projectUrl).text.toString())
                    assertTrue(row.isClickable && row.isFocusable)
                }
            }
        }
    }
}
