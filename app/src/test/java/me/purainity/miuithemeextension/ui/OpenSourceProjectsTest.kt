package me.purainity.miuithemeextension.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSourceProjectsTest {
    @Test
    fun projectsHaveUniqueNamesAndValidDisplayFields() {
        val projects = OpenSourceProjects.all
        assertTrue(projects.isNotEmpty())
        assertEquals(projects.size, projects.map { it.name }.distinct().size)
        projects.forEach { project ->
            assertTrue(project.name.isNotBlank() && project.author.isNotBlank() && project.license.isNotBlank())
            assertTrue(listOf(project.name, project.author, project.license).none { '\n' in it })
            assertTrue(project.url.startsWith("https://"))
            assertTrue(!project.url.contains("/tree/"))
            assertTrue(project.license != "Apache-2.0")
        }
        listOf("Material Symbols", "AndroidX", "Xposed API").forEach { name ->
            assertEquals("Apache License 2.0", projects.single { it.name == name }.license)
        }
    }
}
