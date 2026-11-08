package me.purainity.miuithemeextension.ui

/** 开源项目条目：名称、作者、协议与主页 */
data class OpenSourceProject(val name: String, val author: String, val license: String, val url: String)

object OpenSourceProjects {
    /** 直接依赖、传递依赖与随应用分发的资源，链接一律指向项目主页 */
    val all = listOf(
        OpenSourceProject("Material Symbols", "Google", "Apache-2.0", "https://github.com/google/material-design-icons"),
        OpenSourceProject("AndroidX", "Android Open Source Project", "Apache-2.0", "https://github.com/androidx/androidx"),
        OpenSourceProject("View Binding", "Android Open Source Project", "Apache-2.0", "https://android.googlesource.com/platform/frameworks/data-binding/"),
        OpenSourceProject("Material Components", "Google", "Apache-2.0", "https://github.com/material-components/material-components-android"),
        OpenSourceProject("Kotlin", "JetBrains", "Apache-2.0", "https://github.com/JetBrains/kotlin"),
        OpenSourceProject("kotlinx.coroutines", "JetBrains", "Apache-2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
        OpenSourceProject("kotlinx.serialization", "JetBrains", "Apache-2.0", "https://github.com/Kotlin/kotlinx.serialization"),
        OpenSourceProject("JetBrains Annotations", "JetBrains", "Apache-2.0", "https://github.com/JetBrains/java-annotations"),
        OpenSourceProject("Guava", "Google", "Apache-2.0", "https://github.com/google/guava"),
        OpenSourceProject("Error Prone", "Google", "Apache-2.0", "https://github.com/google/error-prone"),
        OpenSourceProject("JSpecify", "JSpecify Authors", "Apache-2.0", "https://github.com/jspecify/jspecify"),
        OpenSourceProject("Xposed API", "rovo89", "Apache-2.0", "https://github.com/rovo89/Xposed"),
    )
}
