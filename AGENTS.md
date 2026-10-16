# AGENTS.md

## 项目定位

MIUI 主题扩展：Xposed 模块（LSPosed）+ 配置 UI，单一 APK，单模块 `:app`，包名 `me.purainity.miuithemeextension`。目标系统 MIUI 12–14。

具体设计（Hook 点、开关语义、核心机制、兼容边界）一律以 `docs/MIUI主题扩展项目规划.md` 为准，本文件不重复。

## 行为红线

**技术红线**（与 `docs/MIUI主题扩展项目规划.md` 第 2 节一致，以该文档为准）：

必须遵守：

- 业务 Hook 只用 legacy Xposed API（`de.robv.android.xposed.*`，compileOnly 引入）；
- UI 使用 Kotlin + XML Views + ViewBinding 与 Material Design 3 控件；
- 混淆对抗使用纯 Kotlin 实现的 DEX 只读解析，不依赖任何运行时特征检索库。

禁止：

- 禁止 DataBinding；
- 禁止 `com.android.support` 旧支持库；
- 禁止引入任何携带 so 原生库的依赖；
- 禁止引入 libxposed（任何形式，含 100/101/102 Hook API 与 Service Provider）；
- 禁止 `XSharedPreferences`；
- 禁止任何需要 root 权限的功能；
- 禁止实现小窗、背屏/副屏相关功能；
- 文案禁止出现 "HyperOS"，一律表述为 MIUI。

**协作规范**（仅约束 agent 行为，规划文档不涉及）：

- 改 Hook 点 / 开关 / UI 后必须同步 `docs/MIUI主题扩展项目规划.md`，保持文档-代码一致；
- `docs/已有的主题破解和MIUI扩展模块对比分析报告.md` 为只读背景研究，任何情况下不得修改；
- 文档一律中文；
- 代码要有中文注释；
- 除非用户明确要求，不修改 `versionCode` / `versionName`。

## 常用命令

```
./gradlew :app:assembleRelease
./gradlew :app:testDebugUnitTest                                  # 改码后的既定验证步骤
./gradlew :app:testDebugUnitTest --tests "me.purainity.miuithemeextension.PolicyTest"   # 单测试类
./gradlew :app:connectedDebugAndroidTest                          # 仪器测试，需连接设备
./gradlew :app:lintDebug
```

## Git 工作流

- 主动暂存本次会话实际改动的文件：显式路径，禁止 `git add -A` / `git add .`；
- 除非用户明确要求，不创建提交；提交消息统一中文，建议格式 `类型: 简述`（功能 / 修复 / 文档 / 构建），首行一行；
- `push` / `reset --hard` / `rebase` / 删分支等操作需用户明示。

## 文档地图

- `docs/MIUI主题扩展项目规划.md` —— 权威实现依据，改动须同步；
- `docs/已有的主题破解和MIUI扩展模块对比分析报告.md` —— 只读背景研究，不得修改。
