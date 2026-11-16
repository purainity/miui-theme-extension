package me.purainity.miuithemeextension.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class UiLayoutRegressionTest {
    private val android = "http://schemas.android.com/apk/res/android"
    private val app = "http://schemas.android.com/apk/res-auto"

    private fun elements(layout: String): List<Element> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(File("src/main/res/layout/$layout.xml"))
        val nodes = document.getElementsByTagName("*")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    @Test
    fun aboutIconUsesSquareTextHeightConstraints() {
        val views = elements("view_about_header")
        assertEquals("androidx.constraintlayout.widget.ConstraintLayout", views.first().tagName)
        val icon = views.single { it.getAttributeNS(android, "id") == "@+id/appIcon" }
        assertEquals("0dp", icon.getAttributeNS(android, "layout_width"))
        assertEquals("0dp", icon.getAttributeNS(android, "layout_height"))
        assertEquals("1:1", icon.getAttributeNS(app, "layout_constraintDimensionRatio"))
        assertEquals("@id/nameAndVersion", icon.getAttributeNS(app, "layout_constraintTop_toTopOf"))
        assertEquals("@id/nameAndVersion", icon.getAttributeNS(app, "layout_constraintBottom_toBottomOf"))
    }

    @Test
    fun aboutCardsAndHeadingsUseSecondaryPageSpacing() {
        val views = elements("activity_about")
        val projectCard = views.single { it.getAttributeNS(android, "id") == "@+id/projectCard" }
        assertEquals("16dp", projectCard.getAttributeNS(android, "layout_marginTop"))
        listOf("thanksHeading", "licensesHeading").forEach { id ->
            val heading = views.single { it.getAttributeNS(android, "id") == "@+id/$id" }
            assertEquals("20dp", heading.getAttributeNS(android, "layout_marginTop"))
            assertEquals("8dp", heading.getAttributeNS(android, "layout_marginBottom"))
            assertEquals("4dp", heading.getAttributeNS(android, "layout_marginStart"))
            assertEquals("4dp", heading.getAttributeNS(android, "layout_marginEnd"))
        }
    }

    @Test
    fun openSourceTitleAndUrlCanWrapWithoutEllipsis() {
        val views = elements("item_open_source_project")
        listOf("title", "projectUrl").forEach { id ->
            val view = views.single { it.getAttributeNS(android, "id") == "@+id/$id" }
            assertEquals("wrap_content", view.getAttributeNS(android, "layout_height"))
            assertFalse(view.hasAttributeNS(android, "maxLines"))
            assertFalse(view.hasAttributeNS(android, "singleLine"))
            assertFalse(view.hasAttributeNS(android, "ellipsize"))
        }
    }
}
