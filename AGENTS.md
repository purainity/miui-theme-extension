# AGENTS.md

## 项目定位

MIUI 主题扩展：Xposed 模块（LSPosed）+ 配置 UI，单一 APK，单模块 `:app`，包名 `me.purainity.miuithemeextension`。目标系统 MIUI 12–14 及更新的 MIUI 版本。

技术栈：纯 Kotlin（XML Views + ViewBinding + Material 3），业务 Hook 基于 legacy Xposed API（`de.robv.android.xposed.*`，compileOnly 引入）。

具体设计（Hook 点、开关语义、核心机制、兼容边界）一律以 `docs/MIUI主题扩展开发文档.md` 为准，本文件不重复。修改代码前先读相关章节；**修改 Hook 点 / 开关 / UI 后必须同步修改文档，保持文档-代码一致**。

`docs/已有的主题破解和MIUI扩展模块对比分析报告.md` 为只读背景研究，任何情况下不得修改。

## 常用命令

```bash
./gradlew assembleDebug         # Debug 构建
./gradlew assembleRelease       # Release 构建（需下方签名属性）
./gradlew testDebugUnitTest     # 单元测试（改码后的既定验证步骤）
./gradlew testDebugUnitTest --tests "me.purainity.miuithemeextension.PolicyTest"   # 单测试类
./gradlew connectedAndroidTest  # Instrumented 测试（需连接设备或模拟器）
./gradlew lint                  # 静态检查
```

Release 签名通过 Gradle 属性注入：`ANDROID_RELEASE_KEYSTORE_FILE` / `ANDROID_RELEASE_KEYSTORE_PASSWORD` / `ANDROID_RELEASE_KEY_ALIAS` / `ANDROID_RELEASE_KEY_PASSWORD`，四项齐备才启用（仓库内无 keystore 文件）。

## 项目结构

```
miui-theme-extension/
├── app/src/
│   ├── main/
│   │   ├── java/me/purainity/miuithemeextension/     # 主包
│   │   │   ├── MainActivity.kt                       # 配置首页：激活状态、开关列表、环境信息
│   │   │   ├── AboutActivity.kt                      # 关于页
│   │   │   ├── ModuleApplication.kt                  # Application，启用动态取色
│   │   │   ├── config/
│   │   │   │   ├── ModuleConfig.kt                   # 全部开关定义与默认值
│   │   │   │   ├── ConfigStore.kt                    # 配置持久化与跨进程契约
│   │   │   │   └── ConfigProvider.kt                 # 配置跨进程同步 ContentProvider
│   │   │   ├── dex/
│   │   │   │   └── DexIndex.kt                       # 只读 DEX 索引，混淆对抗按特征定位方法
│   │   │   ├── hook/
│   │   │   │   ├── XposedEntry.kt                    # Xposed 入口，按宿主进程分发
│   │   │   │   ├── ThemeHooks.kt                     # 主题壁纸进程 Hook：购买态、价格、广告、大图标
│   │   │   │   ├── WidgetHooks.kt                    # 智能助理与系统桌面的小组件 Hook
│   │   │   │   ├── SystemHooks.kt                    # system_server 框架层 Hook：DRM 瞬时窗口
│   │   │   │   ├── ScopedHookWindow.kt               # 瞬时 DRM 窗口，按线程授权
│   │   │   │   ├── HostConfigClient.kt               # 宿主进程配置客户端，快照与变更通知
│   │   │   │   ├── ReversibleMutationJournal.kt      # 已改写宿主对象的回滚日志
│   │   │   │   ├── OwnedRightsFiles.kt               # 大图标授权占位文件
│   │   │   │   ├── FeedBinding.kt                    # 大图标 feed 绑定方法定位
│   │   │   │   ├── WeakIdentityMap.kt                # 身份弱引用映射
│   │   │   │   └── HookSupport.kt                    # Hook 通用支撑与统一日志
│   │   │   └── ui/
│   │   │       ├── SettingsRowPresenter.kt           # 设置行展示逻辑
│   │   │       ├── EnvironmentInfo.kt                # 环境信息条目与宿主包版本快照
│   │   │       ├── EdgeToEdge.kt                     # edge-to-edge 与系统栏 inset
│   │   │       ├── IconHider.kt                      # 桌面图标隐藏（activity-alias）
│   │   │       └── OpenSourceProjects.kt             # 开源项目清单
│   │   ├── assets/                                   # xposed_init，声明 Xposed 入口类名
│   │   └── res/                                      # XML 布局、Material Symbols 风格矢量图标、M3 主题
│   ├── test/                                         # 7 个单元测试类（JUnit 4）
│   └── androidTest/                                  # 2 个 Instrumented 测试类（Espresso）
└── docs/
    ├── MIUI主题扩展开发文档.md                       # 权威实现依据
    └── 已有的主题破解和MIUI扩展模块对比分析报告.md   # 只读背景研究
```

## 行为红线

与 `docs/MIUI主题扩展开发文档.md` 第 2 节一致，以该文档为准：

- 业务 Hook 只用 legacy Xposed API（`de.robv.android.xposed.*`，compileOnly 引入）
- UI 使用 XML Views + ViewBinding + Material Design 3 控件
- 混淆对抗使用纯 Kotlin 实现的 DEX 只读解析，不依赖任何运行时特征检索库
- 不引入：`com.android.support` 旧支持库、携带 so 原生库的依赖
- 禁止引入 libxposed（任何形式，含 100/101/102 Hook API 与 Service Provider）
- 禁止经典 `XSharedPreferences` 与 New XSharedPreferences（NSP）两种配置读取方案，配置跨进程同步一律走 ContentProvider（选型依据见开发文档 2.2）
- 禁止任何需要 root 权限的功能
- 文案禁止出现“HyperOS”，一律表述为 MIUI

## 代码与文档规范

- 文档一律中文
- 引号一律使用中文双引号“”，不使用方引号「」
- 代码注释使用中文；单行注释末尾默认不加句号，长句之间的分隔仍允许句号；一句话不拆成多行，不是同一句话的内容可以分行书写
- 引号、注释句号这类写作与注释风格规范只写入本文件（AGENTS.md），不进开发文档
- Hook 点注释注明 `docs/MIUI主题扩展开发文档.md` 章节号（如 6.1 #5、8.3），其余注释一律不标注章节号；宿主与功能称谓跟文档一致（主题壁纸 / 智能助理 / 系统桌面 / system_server / 小组件）

## 版本与 Git

- 除非用户明确要求，不修改 `versionCode` / `versionName`
- 完成修改后主动暂存本次会话实际改动的文件：仅使用显式路径，禁止 `git add -A` / `git add .`
- 除非用户明确要求，不创建提交
- 提交消息统一中文，建议格式 `类型: 简述`（功能 / 修复 / 文档 / 构建），首行保持一行
- `push` / `reset --hard` / `rebase` / 删分支等操作需用户明示
