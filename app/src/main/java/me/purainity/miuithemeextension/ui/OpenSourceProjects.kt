package me.purainity.miuithemeextension.ui

/** 开源项目条目：名称、作者、协议与主页 */
data class OpenSourceProject(val name: String, val author: String, val license: String, val url: String)

object OpenSourceProjects {
    /** 直接依赖、传递依赖与随应用分发的资源，链接一律指向项目主页 */
    val all = listOf(
        OpenSourceProject("Material Symbols", "Google", "Apache License 2.0", "https://github.com/google/material-design-icons"),
        OpenSourceProject("AndroidX", "Android Open Source Project", "Apache License 2.0", "https://github.com/androidx/androidx"),
        OpenSourceProject("View Binding", "Android Open Source Project", "Apache License 2.0", "https://android.googlesource.com/platform/frameworks/data-binding/"),
        OpenSourceProject("Material Components", "Google", "Apache License 2.0", "https://github.com/material-components/material-components-android"),
        OpenSourceProject("Kotlin", "JetBrains", "Apache License 2.0", "https://github.com/JetBrains/kotlin"),
        OpenSourceProject("kotlinx.coroutines", "JetBrains", "Apache License 2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
        OpenSourceProject("kotlinx.serialization", "JetBrains", "Apache License 2.0", "https://github.com/Kotlin/kotlinx.serialization"),
        OpenSourceProject("JetBrains Annotations", "JetBrains", "Apache License 2.0", "https://github.com/JetBrains/java-annotations"),
        OpenSourceProject("Guava", "Google", "Apache License 2.0", "https://github.com/google/guava"),
        OpenSourceProject("Error Prone", "Google", "Apache License 2.0", "https://github.com/google/error-prone"),
        OpenSourceProject("JSpecify", "JSpecify Authors", "Apache License 2.0", "https://github.com/jspecify/jspecify"),
        OpenSourceProject("Xposed API", "rovo89", "Apache License 2.0", "https://github.com/rovo89/Xposed"),
    )
}
