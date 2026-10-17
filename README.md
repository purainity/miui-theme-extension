# MIUI 主题扩展

一个面向 MIUI 主题壁纸的 Xposed 模块：解锁付费内容（主题、字体、大图标、小组件等），允许应用本地导入的第三方主题，隐藏付费标识与商店广告。模块自带 Material Design 3 风格的配置界面，单一 APK，基于 legacy Xposed API。

## 功能

| 功能 | 默认 | 开启后效果 |
| --- | --- | --- |
| 总开关 | 关 | 所有功能生效的前提 |
| 允许使用第三方主题 | 开 | 本地导入的 mtz 主题可正常应用，不会被系统还原 |
| 解锁付费主题资源 | 关 | 主题、字体、壁纸、锁屏、图标、息屏、铃声等资源可视为免费直接使用（含 DRM 授权层） |
| 隐藏主题付费标识 | 开 | 主题与字体卡片的价格显示为免费 |
| 解锁付费大图标 | 关 | 付费大图标可下载应用，列表价格显示为免费 |
| 解锁付费小组件 | 关 | 智能助理/桌面/商店中的付费 MAML 小组件（含超级壁纸）可下载、可添加 |
| 隐藏小组件付费标识 | 开 | 小组件卡片不再显示付费标识 |
| 隐藏主题商店广告 | 关 | 广告判定失效、视频广告失效、广告卡片不占位 |
| 隐藏模块桌面图标 | 关 | 隐藏桌面图标，仍可从 LSPosed 打开设置 |

- 部分开关存在从属关系：“解锁付费主题资源”从属于“允许使用第三方主题”，“隐藏主题付费标识”从属于“解锁付费主题资源”，“隐藏小组件付费标识”从属于“解锁付费小组件”；依赖关闭时子开关随之禁用，存储值保留；
- 安装后总开关默认关闭，需在模块内开启后各项功能才会生效。

## 环境要求

- 运行 MIUI 12–14（或更新 MIUI 版本）的小米设备；
- [LSPosed](https://github.com/LSPosed/LSPosed)（模块基于 legacy Xposed API，`xposedminversion=82`，兼容官方 1.x / 2.x 与 Vector 分支）。

## 使用方法

1. 安装 APK，在 LSPosed 中启用模块；
2. 勾选作用域：**Android 系统框架、主题壁纸、智能助理、系统桌面** 四项（模块已声明推荐作用域，启用时按 LSPosed 提示应用即可）；
3. 重启手机（首次启用必须）；
4. 打开模块，确认激活状态卡片显示“模块已激活”，按需开启各项功能。

首次启用需重启手机；此后开关修改即时生效，返回宿主应用即可看到效果，个别已渲染的页面需刷新或重新进入。

## 工作原理

- 基于 legacy Xposed API，在四个宿主进程内 Hook：主题壁纸（购买态、价格、DRM、大图标、广告）、智能助理与系统桌面（小组件付费判断与角标）、system_server（DRM 框架层瞬时窗口，窗口之外系统 DRM 真实运行）；
- 被混淆的 DRM 业务方法与大图标任务经纯 Kotlin 实现的 DEX 只读解析器按特征定位，不依赖任何运行时特征检索库；
- 配置经导出的只读 ContentProvider 跨进程同步（ContentObserver 变更通知），不使用 XSharedPreferences；
- 完整设计（Hook 点明细、开关语义、资源分类、兼容边界）见 [docs/MIUI主题扩展项目规划.md](docs/MIUI主题扩展项目规划.md)；
- 背景研究：已有主题破解与 MIUI 扩展模块的对比分析见 [docs/已有的主题破解和MIUI扩展模块对比分析报告.md](docs/已有的主题破解和MIUI扩展模块对比分析报告.md)。

## 构建

```
./gradlew :app:assembleRelease
```

- 构建产物在 `app/build/outputs/apk/release/`；
- Gradle daemon 经 `gradle/gradle-daemon-jvm.properties` 锁定 JDK 25，缺失时由 foojay resolver 自动下载，无需本地预装；
- Release 签名密钥经 Gradle 属性提供：`ANDROID_RELEASE_KEYSTORE_FILE`、`ANDROID_RELEASE_KEYSTORE_PASSWORD`、`ANDROID_RELEASE_KEY_ALIAS`、`ANDROID_RELEASE_KEY_PASSWORD`，未配置时输出未签名 APK。

### 开发与测试

```
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

## 致谢

- Miui主题破解（`com.yuk.fuckMiuiThemeManager`）—— yuk、YuKongA、linqiqi
- [PersonalizeHyperTheme](https://github.com/tangjin2580/PersonalizeHyperTheme)、[FuckMiuiThemeManager](https://github.com/tangjin2580/FuckMiuiThemeManager)（tangjin）
- MIUI扩展（`com.miui.android.t`）

## 免责声明

本项目由 AI 生成，仅供个人学习与技术研究，请勿用于非法用途。使用本项目造成的任何后果由使用者自行承担，与作者无关。
