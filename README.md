# MIUI 主题扩展

一个面向 MIUI 主题壁纸的 Xposed 模块：解锁付费内容（主题、字体、大图标、小组件等），允许应用本地导入的第三方主题，隐藏付费标识与商店广告。模块自带 Material Design 3 风格的配置界面，单一 APK，基于 legacy Xposed API。

## 功能特性

| 功能 | 默认 | 开启后效果 |
| --- | --- | --- |
| 总开关 | 关 | 所有功能生效的前提 |
| 允许使用第三方主题 | 开 | 本地导入的 mtz 主题可正常应用，不会被系统还原 |
| 解锁付费主题资源 | 关 | 主题、字体、壁纸、锁屏、图标、息屏、铃声等资源可视为免费直接使用（含 DRM 授权层） |
| 隐藏主题付费标识 | 开 | 主题与字体卡片的价格显示为免费 |
| 解锁付费大图标 | 关 | 付费大图标可下载应用，列表价格显示为免费 |
| 解锁付费小组件 | 关 | 智能助理/桌面/商店中的付费 MAML 小组件（含超级壁纸）可下载、可添加 |
| 隐藏小组件付费标识 | 开 | 小组件卡片不再显示付费标识 |
| 隐藏主题商店广告 | 开 | 广告判定失效、视频广告失效、广告卡片不占位 |
| 隐藏模块桌面图标 | 关 | 隐藏桌面图标，仍可从 LSPosed 打开设置 |

- 部分开关存在从属关系：“解锁付费主题资源”从属于“允许使用第三方主题”，“隐藏主题付费标识”从属于“解锁付费主题资源”，“隐藏小组件付费标识”从属于“解锁付费小组件”；依赖关闭时子开关整行禁用、存储值保留，重新开启时不自动改写
- 总开关关闭时其余全部开关禁用；安装后总开关默认关闭，需在模块内开启后各项功能才会生效
- 大图标不设单独的角标开关：feed 卡片价格直读数据模型字段，由“解锁付费大图标”在数据层统一归零
- 开关修改即时生效，无需重启；已渲染的页面需刷新或重新进入
- 配置界面遵循 Material Design 3：激活状态卡显示模块激活状态，版本信息卡展示模块版本、系统与 MIUI 版本、Xposed API 版本与三个宿主版本（可长按复制）

## 环境要求

- Android 9 及以上的小米设备
- MIUI 12–14，或更新的 MIUI 版本
- [LSPosed](https://github.com/LSPosed/LSPosed)（模块基于 legacy Xposed API，`xposedminversion=82`，兼容官方 1.x / 2.x 与 Vector 分支）

## 使用方法

1. 前往 [Releases](https://github.com/purainity/miui-theme-extension/releases) 页面下载最新 APK 安装，在 LSPosed 中启用模块
2. 勾选作用域：**Android 系统框架、主题壁纸、智能助理、系统桌面** 四项（模块已声明推荐作用域，启用时按 LSPosed 提示应用即可）
3. 重启手机（首次启用必须）
4. 打开模块，确认激活状态卡显示“模块已激活”；未激活时按卡片提示检查 LSPosed 启用与作用域
5. 开启总开关，按分组按需开启各功能：主题资源（第三方主题 / 解锁资源 / 隐藏付费标识）、大图标、小组件、广告、其他设置
6. 返回宿主应用查看效果，个别已渲染的页面需刷新或重新进入；关闭开关立即恢复原状

首次启用需重启手机；此后开关修改即时生效，无需再次重启。

## 工作原理

- 基于 legacy Xposed API，在四个宿主进程内 Hook：主题壁纸（购买态、价格、DRM、大图标、广告）、智能助理与系统桌面（小组件付费判断与角标）、system_server（DRM 框架层瞬时窗口）
- DRM 采用瞬时窗口：仅在主题校验（validateTheme）执行期间按路径类别租借放行，窗口之外系统 DRM 全部真实运行；递归校验子目录时可按类别区别对待
- 探测保护：宿主应用大图标前会构造无 productId 的临时对象探测环境，此类对象一律放行原逻辑不做改写，避免破坏宿主探测
- 被混淆的 DRM 业务方法与大图标任务经纯 Kotlin 实现的 DEX 只读解析器按特征定位（结果按指纹缓存，命中免扫），不依赖任何运行时特征检索库
- 配置经导出的只读 ContentProvider 跨进程同步（ContentObserver 变更通知，宿主回到前台时拉取快照），不使用 XSharedPreferences；模块不可用时保持全关、系统 DRM 真实运行
- 已改写的宿主内存对象（详情页、feed 卡片）按对象身份记录原值，关闭对应开关立即回滚，避免读到残留的“已购买/免费”状态
- 大图标授权以空占位文件满足存在性检查，真实授权仍由 DRM 层处理；功能关闭时只清理本模块创建且仍为空的文件
- 完整设计（Hook 点明细、开关语义、资源分类、兼容边界）见 [docs/MIUI主题扩展开发文档.md](docs/MIUI主题扩展开发文档.md)
- 背景研究：已有主题破解与 MIUI 扩展模块的对比分析见 [docs/已有的主题破解和MIUI扩展模块对比分析报告.md](docs/已有的主题破解和MIUI扩展模块对比分析报告.md)

## 开发指南

### 构建

```bash
./gradlew assembleDebug      # Debug APK
./gradlew assembleRelease    # Release APK（需签名配置）
```

Release 签名通过 Gradle 属性注入（如 `~/.gradle/gradle.properties`）：

```properties
ANDROID_RELEASE_KEYSTORE_FILE=/path/to/keystore
ANDROID_RELEASE_KEYSTORE_PASSWORD=******
ANDROID_RELEASE_KEY_ALIAS=******
ANDROID_RELEASE_KEY_PASSWORD=******
```

四项齐备时启用 v2 + v3 签名（debug 构建同样复用该签名），未配置时 Release 输出未签名 APK；仓库内不含任何 keystore 文件。

### 测试与检查

```bash
./gradlew testDebugUnitTest     # 单元测试
./gradlew connectedAndroidTest  # Instrumented 测试（需连接设备或模拟器）
./gradlew lint                  # 静态检查
```

## 开源致谢

- **[PersonalizeHyperTheme](https://github.com/tangjin2580/PersonalizeHyperTheme)、[FuckMiuiThemeManager](https://github.com/tangjin2580/FuckMiuiThemeManager)（tangjin）**
  已有主题破解模块，对比分析报告的研究对象
- **Miui主题破解（`com.yuk.fuckMiuiThemeManager`）**
  作者 yuk、YuKongA、linqiqi；已有主题破解模块
- **MIUI扩展（`com.miui.android.t`）**
  已有 MIUI 扩展模块，对比分析报告的研究对象

## 许可证

本项目以 [GPL-3.0](./LICENSE) 许可证开源。

## 免责声明

本项目由 AI 生成，仅供个人学习与技术研究，请勿用于非法用途。使用本项目造成的任何后果由使用者自行承担，与作者无关。
