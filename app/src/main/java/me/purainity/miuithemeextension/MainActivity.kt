package me.purainity.miuithemeextension

import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import me.purainity.miuithemeextension.config.ConfigStore
import me.purainity.miuithemeextension.config.Setting
import me.purainity.miuithemeextension.databinding.ActivityMainBinding
import me.purainity.miuithemeextension.databinding.ItemSettingRowBinding
import me.purainity.miuithemeextension.ui.EnvironmentInfo
import me.purainity.miuithemeextension.ui.HostPackage
import me.purainity.miuithemeextension.ui.IconHider
import me.purainity.miuithemeextension.ui.SettingsRowPresenter

/** 模块配置界面：只负责装配与事件接线，行展示逻辑在 SettingsRowPresenter，数据源在 EnvironmentInfo */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var store: ConfigStore
    private lateinit var iconHider: IconHider
    private lateinit var rows: List<SettingRow>
    private var bindingValues = false

    /** 一个设置行 = 公共行布局 binding + 对应的开关与文案资源 */
    private data class SettingRow(
        val setting: Setting,
        val row: ItemSettingRowBinding,
        val label: Int,
        val effect: Int
    ) {
        val container: View get() = row.root
        val title: TextView get() = row.rowTitle
        val summary: TextView get() = row.rowSummary
        val control: MaterialSwitch get() = row.rowSwitch
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        store = ConfigStore(this)
        iconHider = IconHider(packageManager, ComponentName(this, "$packageName.Launcher"))
        // 边到边布局：顶栏吃顶部 inset（背景延伸到状态栏后面），滚动区吃左右与底部 inset（内容可滚到导航条后面）
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBar) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, 0)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.contentScroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
        }
        rows = listOf(
            SettingRow(Setting.MASTER, binding.masterRow, R.string.master_title, R.string.master_summary),
            SettingRow(Setting.RESOURCES, binding.resourcesRow, R.string.resources_label, R.string.resources_summary),
            SettingRow(Setting.PAID_BADGES, binding.paidBadgesRow, R.string.paidBadges_label, R.string.paidBadges_summary),
            SettingRow(Setting.LARGE_ICONS, binding.largeIconsRow, R.string.largeIcons_label, R.string.largeIcons_summary),
            SettingRow(Setting.WIDGETS, binding.widgetsRow, R.string.widgets_label, R.string.widgets_summary),
            SettingRow(Setting.WIDGET_BADGES, binding.widgetBadgesRow, R.string.widgetBadges_label, R.string.widgetBadges_summary),
            SettingRow(Setting.ADS, binding.adsRow, R.string.ads_label, R.string.ads_summary),
            SettingRow(Setting.HIDE_ICON, binding.hideIconRow, R.string.hideIcon_label, R.string.hideIcon_summary)
        )
        rows.forEach { row ->
            row.title.setText(row.label)
            row.container.setOnClickListener { if (row.control.isEnabled) row.control.performClick() }
            row.control.setOnCheckedChangeListener { _, checked ->
                if (!bindingValues) {
                    val old = store.read()[row.setting]
                    if (!store.write(row.setting, checked)) toast(R.string.save_failed)
                    else if (row.setting == Setting.HIDE_ICON) {
                        // 图标设置失败时回滚存储值并提示
                        try { iconHider.apply(checked) } catch (_: Exception) {
                            store.write(row.setting, old)
                            toast(R.string.icon_failed)
                        }
                    }
                    renderSettings()
                    renderActivation()
                }
            }
        }
        renderEnvironment()
    }

    override fun onResume() {
        super.onResume()
        renderSettings()
        renderActivation()
    }

    /** 版本信息卡片：标签与内容按声明顺序写入公共信息行 */
    private fun renderEnvironment() {
        val info = EnvironmentInfo(
            resolve = { getString(it) },
            format = { id, args -> getString(id, *args) },
            moduleVersionName = BuildConfig.VERSION_NAME,
            moduleVersionCode = BuildConfig.VERSION_CODE.toLong(),
            androidRelease = Build.VERSION.RELEASE,
            androidSdk = Build.VERSION.SDK_INT,
            miuiVersion = Build.VERSION.INCREMENTAL,
            systemProperty = ::readSystemProperty,
            fallbackModel = Build.MODEL,
            device = Build.DEVICE,
            hostLookup = ::hostPackage
        )
        val infoRows = listOf(
            binding.moduleVersionRow, binding.deviceModelRow, binding.androidVersionRow, binding.miuiVersionRow,
            binding.hostThemeRow, binding.hostAssistantRow, binding.hostHomeRow
        )
        info.entries().forEachIndexed { index, entry ->
            infoRows[index].rowLabel.setText(entry.label)
            infoRows[index].rowValue.text = entry.value
        }
    }

    /** 重新渲染全部开关行：勾选值、可交互性、说明与无障碍描述 */
    private fun renderSettings() {
        bindingValues = true
        try {
            val config = store.read()
            // 图标设置被外部修改时以组件实际状态为准回写存储（自愈）
            val hidden = iconHider.hidden()
            if (hidden != config[Setting.HIDE_ICON]) store.write(Setting.HIDE_ICON, hidden)
            rows.forEach { row ->
                row.control.isChecked = SettingsRowPresenter.checked(row.setting, config, hidden)
                row.control.isEnabled = config.interactive(row.setting)
                row.container.isClickable = row.control.isEnabled
                val hint = SettingsRowPresenter.dependencyHint(row.setting, config)?.let { getString(it) }
                // 禁用时说明仍保持可见；依赖提示解释子开关为何不可用
                row.summary.text = SettingsRowPresenter.summary(getString(row.effect), hint)
            }
        } finally { bindingValues = false }
    }

    /** 激活状态卡片：图标、容器颜色与文案随激活状态切换 */
    private fun renderActivation() {
        val active = ModuleStatus.isActive()
        val container = MaterialColors.getColor(binding.activationCard, if (active)
            com.google.android.material.R.attr.colorPrimaryContainer else com.google.android.material.R.attr.colorErrorContainer)
        val content = MaterialColors.getColor(binding.activationCard, if (active)
            com.google.android.material.R.attr.colorOnPrimaryContainer else com.google.android.material.R.attr.colorOnErrorContainer)
        binding.activationCard.setCardBackgroundColor(container)
        binding.activation.setText(if (active) R.string.active else R.string.inactive)
        binding.activationHint.setText(if (active) R.string.active_hint else R.string.inactive_hint)
        binding.activation.setTextColor(content)
        binding.activationHint.setTextColor(content)
        binding.activationIcon.setImageResource(if (active) R.drawable.ic_status_active else R.drawable.ic_status_inactive)
        binding.activationIcon.imageTintList = ColorStateList.valueOf(content)
    }

    /** 宿主包版本查询：未安装返回 null，其余异常交由上层按“无法读取”处理 */
    private fun hostPackage(pkg: String): HostPackage? = try {
        val info = packageManager.getPackageInfo(pkg, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode
        else @Suppress("DEPRECATION") info.versionCode.toLong()
        HostPackage(info.versionName, code)
    } catch (missing: PackageManager.NameNotFoundException) { null }

    /** 通过 getprop 读取系统属性；失败返回空字符串 */
    private fun readSystemProperty(name: String): String = try {
        ProcessBuilder("getprop", name).start().inputStream.bufferedReader().use { it.readLine().orEmpty().trim() }
    } catch (_: Exception) { "" }

    private fun toast(message: Int) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
}
