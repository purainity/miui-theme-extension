package me.purainity.miuithemeextension

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import me.purainity.miuithemeextension.databinding.ActivityAboutBinding
import me.purainity.miuithemeextension.databinding.ItemNavigationBinding
import me.purainity.miuithemeextension.databinding.ItemOpenSourceProjectBinding
import me.purainity.miuithemeextension.ui.OpenSourceProjects
import me.purainity.miuithemeextension.ui.applySystemBarInsets

/** 关于页：页头（图标、名称与版本）、项目信息卡与开源许可卡 */
class AboutActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()
        binding.toolbar.setNavigationOnClickListener { finish() }
        @Suppress("DEPRECATION")
        val version = packageManager.getPackageInfo(packageName, 0)
        binding.aboutHeader.appVersion.text = getString(R.string.about_version, version.versionName, version.longVersionCode)
        navigationRow(
            binding.projectRow, R.drawable.ic_code, getString(R.string.about_project_github),
            "https://github.com/purainity/miui-theme-extension",
        ) { open("https://github.com/purainity/miui-theme-extension") }
        navigationRow(
            binding.authorRow, R.drawable.ic_person, getString(R.string.about_author),
            "https://github.com/purainity",
        ) { open("https://github.com/purainity") }
        OpenSourceProjects.all.forEach { project ->
            val row = ItemOpenSourceProjectBinding.inflate(layoutInflater, binding.licenses, true)
            row.title.text = project.name
            row.projectMeta.text = getString(R.string.licenses_project_meta, project.author, project.license)
            row.projectUrl.text = project.url
            row.root.isFocusable = true
            row.root.setOnClickListener { open(project.url) }
        }
    }

    /** 项目信息卡的导航行：图标 + 标题 + 副标题 + 外链图标 */
    private fun navigationRow(row: ItemNavigationBinding, icon: Int, title: String, subtitle: String, onClick: () -> Unit) {
        row.icon.setImageResource(icon)
        row.title.text = title
        row.subtitle.text = subtitle
        row.subtitle.visibility = View.VISIBLE
        row.trailingIcon.setImageResource(R.drawable.ic_external)
        row.root.isFocusable = true
        row.root.setOnClickListener { onClick() }
    }

    /** 系统浏览器打开链接，无可用浏览器时提示 */
    private fun open(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.open_link_failed, Toast.LENGTH_SHORT).show()
        }
    }
}
