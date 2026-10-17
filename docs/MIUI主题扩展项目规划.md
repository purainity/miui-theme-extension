# MIUI 主题扩展 · 项目规划

## 1. 项目定位

一个 Xposed 模块，用于解锁 MIUI 主题壁纸中的付费内容（覆盖普通资源、大图标、小组件三类，见第 7 节），允许应用本地导入的第三方主题，隐藏付费标识与商店广告。模块附带一个配置界面（界面设计见第 4 节），两者打包为单一 APK。

| 项 | 值 |
| --- | --- |
| 包名 | `me.purainity.miuithemeextension` |
| 语言 / 构建 | Kotlin；minSdk 24，targetSdk 37 |
| Xposed 入口 | `me.purainity.miuithemeextension.hook.XposedEntry`，仅实现 `IXposedHookLoadPackage`，不使用 zygote 入口 |
| 推荐作用域 | manifest 通过 `xposedscope` 静态声明下方四个宿主进程 |
| 目标系统 | MIUI 12–14 及更新的 MIUI 版本 |
| 日志 | 统一前缀 `MIUI 主题扩展: `，写入 Xposed 日志；同一 Hook 点的同类异常只记录首次，避免刷屏 |

宿主进程共四个：

| 进程 | 职责 |
| --- | --- |
| `com.android.thememanager` | 主题壁纸：购买态、价格、DRM、大图标、广告、MAML 入口（6.1） |
| `com.miui.personalassistant` | 智能助理：小组件详情页的付费判断与角标（6.3） |
| `com.miui.home` | 系统桌面：MAML 小组件的下载许可（6.4） |
| `android` | system_server：DRM 框架层，瞬时窗口（6.2、8.3） |

---

## 2. 技术红线

**必须遵守：**

- 业务 Hook 只用 legacy Xposed API（`de.robv.android.xposed.*`，compileOnly 引入）；
- UI 使用 Kotlin + XML Views + ViewBinding 与 Material Design 3 控件；
- 混淆对抗使用纯 Kotlin 实现的 DEX 只读解析（机制见 8.4），不依赖任何运行时特征检索库。

**禁止：**

- 禁止 DataBinding；
- 禁止 `com.android.support` 旧支持库；
- 禁止引入任何携带 so 原生库的依赖；
- 禁止引入 libxposed（任何形式，含 100/101/102 Hook API 与 Service Provider）；
- 禁止经典 `XSharedPreferences` 与 New XSharedPreferences（NSP）两种配置读取方案，配置跨进程同步一律走 ContentProvider（选型依据见 2.1）；
- 禁止任何需要 root 权限的功能；
- 文案禁止出现 "HyperOS"，一律表述为 MIUI。

### 2.1 选型依据（红线背后的架构决策）

**API 线——只走 legacy（rovo89 XposedBridge）：**

- legacy API 是唯一在 LSPosed 官方 1.x（止于 1.9.2）、2.x（2.0.0+）与 Vector 分支全线上受支持的模块 API：官方 2.0.1 移除 libxposed API 100 支持并明确"基于 rovo89 Xposed API 的模块不受影响"；
- libxposed Modern API 存在 100→101→102 断裂（100 从未正式发布即被 2.0.1 移除，102 引入热重载），且 2.0.3 起禁止交叉调用（legacy 模块不得调用 libxposed API；targetApiVersion ≥ 102 的模块不得调用 legacy API）——单 APK 无法用 Modern 覆盖全部框架版本；
- 官方点名的工程禁忌：不得硬编码调用栈深度做判断、不得用调用栈探测框架实现（2.x 责任链模式下调用栈变深，官方已点名因此损坏的模块案例）；运行时框架版本判断只走 `XposedBridge.getXposedVersion()`。

**配置线——ContentProvider 而非 XSharedPreferences：**

- 经典 `XSharedPreferences` 跨应用读取在 LSPosed 1.x 上受 SELinux 限制不可靠（官方自 v2.0.0 才正式支持，且无变更通知能力）；
- New XSharedPreferences（NSP）由声明 `xposedminversion ≥ 93` 或 `xposedsharedprefs` meta-data 启用，LSPosed 2.2.0 起对"声明 NSP 且存在 others 可读 xml"的模块显示废弃警告，官方计划 2.3.0 移除——因此 manifest 锁定 `xposedminversion=82` 且不声明 `xposedsharedprefs`，这也是官方给出的消除警告方式之一；
- 导出只读 ContentProvider 走标准 Android IPC：不依赖任何框架特有机制、全框架版本可用，且具备变更通知能力；provider 由系统按需拉起（无需模块 app 先行运行）。同步机制详见 8.6。

---

## 3. 功能开关

### 3.1 开关定义

| 键 | 默认值 | 界面名称 | 开启后的效果 |
| --- | --- | --- | --- |
| `master_enabled` | false | 总开关 | 所有功能生效的前提；关闭后一切 Hook 放行原逻辑 |
| `allow_third_party_themes` | true | 允许使用第三方主题 | 本地导入的 mtz 主题可正常应用，不会被系统还原；不改写价格与购买态。承载框架层 DRM 窗口对普通资源类路径的放行（6.2 #3–#4），以及应用层 DRM 对无 productId 普通资源的放行（6.1 #14）；`unlock_paid_resources` 从属于本开关 |
| `unlock_paid_resources` | false | 解锁付费主题资源 | 普通资源类（主题、字体、锁屏样式、息屏、铃声等，类别全集见第 7 节）的购买态、授权态、价格视为免费可用（含 DRM 授权层）；主题与字体共用同一底层购买对象，不拆分；从属于 `allow_third_party_themes`（数据层改写依赖框架层 DRM 窗口持续放行才能持久生效） |
| `hide_paid_badges` | true | 隐藏主题付费标识 | 主题与字体卡片的价格/折扣控件归零显示（仅 UI 层；实测该控件仅被主题、字体卡片使用，故仅从属于本解锁开关） |
| `unlock_large_icons` | false | 解锁付费大图标 | 付费大图标可下载应用，商店 feed 卡片价格显示为免费；另含应用任务授权文件（8.5） |
| `unlock_paid_widgets` | false | 解锁付费小组件 | 智能助理/桌面/商店中付费 MAML 小组件可下载、可添加；超级壁纸按 MAML 资源链路适配 |
| `hide_widget_pay_badges` | true | 隐藏小组件付费标识 | 智能助理组件卡片不再显示付费角标（仅 UI 层） |
| `hide_theme_ads` | false | 隐藏主题商店广告 | 广告判定无效、视频广告失效、广告卡片不占位 |
| `hide_launcher_icon` | false | 隐藏模块图标 | 隐藏桌面图标（默认显示）；与激活状态无关 |

### 3.2 联动规则

`unlock_paid_resources` 从属于 `allow_third_party_themes`；`hide_paid_badges` 从属于 `unlock_paid_resources`；`hide_widget_pay_badges` 从属于 `unlock_paid_widgets`。具体行为：

- 依赖不满足时，子开关原生禁用、整行不可点击，标题与说明文字保持原色不变；
- 关闭解锁开关只禁用子开关，存储值保留；重新开启解锁开关时不自动改写子开关；
- 总开关关闭时，其余全部开关禁用；
- 大图标**不设**角标开关：角标开关对应纯展示层 Hook（主题/字体的 `DiscountPriceView` 入参、小组件的 `isShowPayLogo` 标志），而大图标卡片价格直读 `UIProduct`/`LargeIconElement` 模型字段——该字段同时被购买判定读取，feed 显示归零（6.1 #18–#19）与 #1–#5 同属数据层逻辑，由 `unlock_large_icons` 统一承担，不可按字段组拆分回滚。

### 3.3 生效判定

回调内的判定公式：

- 角标类：`hide_paid_badges` 生效 = 总开关、`allow_third_party_themes`、`unlock_paid_resources`、`hide_paid_badges` 均开启；`hide_widget_pay_badges` 生效 = 总开关、`unlock_paid_widgets`、`hide_widget_pay_badges` 均开启；
- 广告类、小组件类：总开关与对应开关均开启；
- DRM 层（框架窗口 6.2 #3–#4 与 6.1 #14 中无 productId 的本地资源）按**资源类别**分开关：普通资源 → `allow_third_party_themes`，大图标 → `unlock_large_icons`，MAML → `unlock_paid_widgets`（`unlock_paid_resources` 从属 `allow_third_party_themes`，故普通资源的数据层生效还需两者均开启）；
- 数据层（6.1 #1–#5）按资源类别分开关（见 8.1，类别全集与收费现状见第 7 节）：普通资源 → `unlock_paid_resources`，大图标 → `unlock_large_icons`，MAML → `unlock_paid_widgets`；
- 无论开关状态如何，无有效 `productId` 的对象在数据层一律放行原逻辑（见 8.2）；
- 开关关闭即放行，常驻 Hook 不需要动态卸载；瞬时 Hook 的清理在任何路径下都会执行（见 8.3）。

### 3.4 开关与 Hook 的对应关系

| 开关 | 覆盖的 Hook（编号见 6.1–6.4） |
| --- | --- |
| `master_enabled` | 全部 Hook 点的总门控 |
| `allow_third_party_themes` | 6.2 #3–#4 窗口类别为普通资源（含本地导入主题的持久保持）；6.1 #14 中参数为无 productId 的普通资源（本地主题应用时被应用层校验拦截的兜底） |
| `unlock_paid_resources` | 6.1 #1–#5 中类别为普通资源的调用；6.1 #14 中参数为普通资源（有 productId）；6.2 #3–#4 窗口类别为普通资源（经从属于 `allow_third_party_themes` 间接生效） |
| `unlock_large_icons` | 6.1 #15、#18–#19；6.1 #1–#5、#14 中类别为大图标；6.2 #3–#4 窗口类别为大图标 |
| `unlock_paid_widgets` | 6.1 #13、#17；6.1 #1–#5、#14 中类别为 MAML；6.2 #3–#4 窗口类别为 MAML；6.3 全部 #1–#11；6.4 全部 |
| `hide_paid_badges` | 6.1 #6 |
| `hide_widget_pay_badges` | 6.3 #12 |
| `hide_theme_ads` | 6.1 #7–#12；6.2 #5–#6 |
| `hide_launcher_icon` | 无 Hook，仅控制 activity-alias 启停 |

---

## 4. 配置界面

单 Activity 布局，自上而下依次为：

1. **激活状态卡片**。显示当前激活状态：激活时提示"模块已激活 / 首次启用需重启手机，此后开关修改即时生效"；未激活时提示"模块未激活 / 请先在 LSPosed 中启用模块并勾选作用域，再重启手机"。判定方式见 8.7。
2. **版本信息卡片**。每项两行：标签在上、内容在下，内容可长按选中复制。显示项：模块版本（versionName + versionCode）、设备型号（`ro.product.marketname` 与 device，取不到回退 `Build.MODEL`）、系统版本（Android 版本 + API）、MIUI 版本（`Build.VERSION.INCREMENTAL`）、Xposed API 版本（由模块加载入口读取 `XposedBridge.getXposedVersion()` 并随激活标志记入 `ModuleStatus`，UI 读取记录值；未加载显示"未检测到"）、三个宿主版本（未安装显示"未安装"，读取失败显示"无法读取"）。包可见性查询仅用于版本展示。
3. **总开关卡片**。独立卡片，位于设置区最上方；关闭时下方所有开关禁用。
4. **功能分组卡片**。五组：
   - 主题资源：允许使用第三方主题、解锁付费主题资源、隐藏主题付费标识；
   - 大图标：解锁付费大图标；
   - 小组件：解锁付费小组件、隐藏小组件付费标识；
   - 广告：隐藏主题商店广告；
   - 其他设置：隐藏模块桌面图标。
   每行为"标题 + 一句效果说明 + 开关"，整行可点击；角标开关跟随对应解锁开关分组，大图标无角标开关——feed 卡片价格由数据层 Hook 直接归零（6.1 #18–#19），无需 UI 层角标。
5. **禁用态说明**。被依赖的子开关禁用时原生禁用、整行不可点击，不追加额外说明文案。
6. **图标隐藏**。通过指向主 Activity 的 `activity-alias` 启停实现（`DONT_KILL_APP`）：设置失败回滚存储值并提示；每次回到界面时以组件实际状态回写存储（外部修改自愈）；隐藏后仍可从 LSPosed 进入设置；说明文字提醒在 LSPosed 中关闭"强制显示桌面图标"。

其他约定：文案统一使用"主题壁纸 / 智能助理 / 系统桌面 / MIUI 版本"称谓；字符串末尾不加句号；界面为 Material Design 3 风格——顶部为大标题折叠应用栏（展开 152dp、滚动收起为 64dp 工具栏，未滚动为 surface 色、滚动后为 surfaceContainer 色），设置行按 MD3 双行列表规范排布并以浮层涟漪提供触摸反馈，边到边布局下顶栏背景延伸到状态栏后面；激活状态图标的无障碍描述与当前激活文案保持同步。

---

## 5. Hook 总体设计

### 5.1 进程分发

入口按 `packageName` 分发：

- 模块自身进程：挂激活自检（8.7）；
- `android`：安装框架层 Hook（6.2）；
- 其余三个宿主：先 hook `Application.attach(Context)` 的 after 拿到宿主 Context 并连接配置客户端（8.6），再安装各自的业务 Hook；主题壁纸进程同时接收 APK 路径（含 split）供 DEX 扫描（8.4）。

### 5.2 公共约定

- **容错**：目标类容错查找，不存在只记日志跳过；每个 Hook 点独立异常隔离，回调内部再隔离一次——单点失败既不影响原方法执行，也不影响其他 Hook 点（致命虚拟机错误除外）；
- **匹配**：按名匹配时必须同时核验参数与返回类型；混淆目标一律经特征扫描定位（8.4），不出现任何硬编码混淆名；
- **纪律**：回调先门控后动作；after 回调在原方法抛出异常时**仍会执行**（异常已被框架捕获进 param，`hasThrowable()` 为 true），且 `setResult` 会顺带清除 throwable——因此凡改写结果的 after 回调必须先判 `!hasThrowable()`，命中异常时只做清理（弹栈、退窗口），绝不改写结果或参数，不掩盖宿主异常；
- **版本策略**：按类、签名、特征三级判定，不按宿主版本号套用整组 Hook。

---

## 6. 各进程 Hook 明细

### 6.1 `com.android.thememanager`（主题壁纸）

| # | 目标 | 匹配方式 | 时机 | 行为 | 开关 |
| --- | --- | --- | --- | --- | --- |
| 1 | `basemodule.resource.model.Resource`、`basemodule.resource.model.ResourceOnlineProperties` → `isAuthorizedResource()`、`isProductBought()` | 按名，无参返回 boolean（后者没有 `isAuthorizedResource`，自动跳过） | before | `setResult(true)` | 按类别 |
| 2 | 同上 → `setProductBought(boolean)` | 按名，`(Z)V` | before | `args[0]=true` | 按类别 |
| 3 | 同上 → `getProductPrice()` | 按名，`()I` | before | `setResult(0)` | 按类别 |
| 4 | 同上 → `setProductPrice(int)` | 按名，`(I)V` | before | `args[0]=0` | 按类别 |
| 5 | `detail.theme.model.OnlineResourceDetail` → `toResource()` | 按名，返回 `Resource`，全重载 | after | 详情对象写 `bought=true`、`productPrice=0`、`originPrice=0`（逐字段独立容错）；对本次返回的 Resource 反射调用 `setProductBought(true)`、`setProductPrice(0)`；为详情对象与返回对象登记类别标签 | 按类别 |
| 6 | `basemodule.views.DiscountPriceView` → public `(II)V` 价格入口 | 按签名匹配，不依赖方法名；只挂 public，同签名 private 辅助方法不挂 | before | `args[0]=0`、`args[1]=0`——原价现价同时归零，由宿主自身渲染免费态，禁止硬编码"免费"文案 | hide_paid_badges |
| 7 | `basemodule.ad.model.AdInfoResponse` → `isAdValid(AdInfo)` | 按名，单参 AdInfo | before | `setResult(false)` | hide_theme_ads |
| 8 | `basemodule.ad.model.AdInfo` → `isVideoAd()` | 按名，`()Z` | before | `setResult(false)` | hide_theme_ads |
| 9–11 | `PureAdBannerViewHolder`、`SelfFontItemAdViewHolder`、`SelfRingtoneItemAdViewHolder` 构造器 `(View, RecommendListViewAdapter[, int])` | 首参 View、次参 RecommendListViewAdapter、可选第三参 int（兼容新版增参） | after | `args[0]` 非空时：已有 LayoutParams 则宽高置 0，没有则按父容器类型（FrameLayout/LinearLayout/RelativeLayout）新建 `(0,0)`；再 `setVisibility(GONE)` | hide_theme_ads |
| 12 | `BaseAdViewHolder` 构造器 `(View, RecommendListViewAdapter)` | 仅当 #9–#11 三类都没挂上时才兜底挂载；回调内再校验 `thisObject` 的类名属于 #9–#11 且未被覆盖 | after | 同 #9–#11 | hide_theme_ads |
| 13 | `com.miui.maml.widget.edit.MamlutilKt` → `themeManagerSupportPaidWidget(Context)` | 按名，`(Context)Z`，存在才挂 | before | `setResult(false)`——让 MAML SDK 判定主题壁纸不支持付费组件，其授权工具（`MamlDrmUtilKt.isLegal`、`downloadAndCopyRight`、`queryMamlRights` 等）直接返回 `DRM_ERROR_UNKNOWN`/`-3`/`null`，调用方按无付费机制处理。经逆向核实，该 SDK 链在主题壁纸进程内无外部调用方；保留作为跨版本保险（存在才挂，零成本） | unlock_paid_widgets |
| 14 | DRM 业务方法（特征扫描，8.4）：`controller.online` 包下返回 `DrmResult`、参数为单个 Resource 的实例方法 | 特征定位 | after | 原方法无异常、返回非 null 时放行判定分两条：① 首个参数有 productId 且按类别放行（探测保护见 8.2）→ `setResult(真实 DRM_SUCCESS)`（8.3），开关按类别；② 首个参数为**无 productId 的普通资源**（本地导入主题）且 `allow_third_party_themes` 开启 → 同样 `setResult(DRM_SUCCESS)`——应用时被应用层校验拦截的兜底，与框架层窗口（6.2 #3–#4）共同覆盖"导入-应用-持久"全链路；价格与购买态不触碰 | ① 按类别；② allow_third_party_themes |
| 15 | 大图标应用任务（特征扫描，8.4）：参数 `[Void[]]`、任务类持有 Resource 实例字段 | 特征定位 + 字段确认 | before + after | before：把任务持有的 Resource 压入线程任务栈并绑定到本次调用；productId 校验通过且开关开启时准备授权占位文件（8.5）。after：无条件出栈（含异常路径） | 栈部分服务类别判定；授权文件部分 = unlock_large_icons |
| 16 | `Resource` → `clone()` | 按名，`()Resource`（排除返回 Object 的 bridge 方法） | after | 把原对象的类别标签传播给克隆对象——DRM 校验内部会克隆资源，不传播会丢类别 | 标签机制 |
| 17 | `superwallpaper.activity.MamlSuperWallpaperDetailActivity`（以 `Activity.onCreate` 为入口，回调内校验实例类型） | 类存在才挂 | before | 修正 intent 中 `extra_bundle` 的 classLoader，反序列化 `extra_superwallpaper_info`，为该 Resource 登记 MAML 标签——超级壁纸由此归入小组件开关 | 标签机制 |
| 18 | `com.android.thememanager.model.LargeIconElement` → `create(UIProduct, int)` 静态工厂 | 按名 + 重载签名（static、首参 `router.recommend.entity.UIProduct`、次参 int、返回 LargeIconElement） | after | 大图标解锁开启、返回非 null 且元素 `productId` 非空时：写 `currentPriceInCent=0`、`originPriceInCent=0`、`hasBought=true`，原值入 journal（8.6.1）——选择器与购买入口的元素由此构造 | unlock_large_icons |
| 19 | `LargeIconHomeCardViewHolder`、`LargeIconTwoItemCardViewHolder`、`LargeIconItemViewHolder`（各含内部类）→ 绑定方法 | 类名 + 参数结构匹配：实例、非桥接、void 返回；参数恰一个 `UIProduct`，或恰一个声明 `UIProduct` 实例字段的对象参数，其余参数限 int/Integer（方法名已混淆，不按名匹配） | before | 大图标解锁开启且商品 `productUuid` 非空时：改写该 `UIProduct` 的 `currentPriceInCent=0`、`originPriceInCent=0`、`isPurchased=true`，原值入 journal（8.6.1）——feed 卡片价格直读该字段，归零后宿主格式化函数渲染"免费" | unlock_large_icons |

### 6.2 `android`（system_server，DRM 框架层）

> 窗口租借的类别 → 开关映射：普通资源 → `allow_third_party_themes`，大图标 → `unlock_large_icons`，MAML → `unlock_paid_widgets`；`unlock_paid_resources` 从属 `allow_third_party_themes`，框架层无需单列。

| # | 目标 | 匹配方式 | 时机 | 行为 | 开关 |
| --- | --- | --- | --- | --- | --- |
| 1 | `com.android.server.SystemServer` → `startBootstrapServices` | 按名 | before | 读取 `mSystemContext` 字段连接配置客户端（system_server 没有 Application.attach）。`createSystemContext()` 先于 `startBootstrapServices()` 执行，before 阶段该字段已可用；但此时 ContentService 尚未启动，这次异步尝试预期失败，真正的拉取发生在 validateTheme 入口（8.6） | 基础设施 |
| 2 | `android.app.ActivityThread` → `systemMain()` | 按名，无参 | after | 经 `getSystemContext()` 连接配置客户端（备用路径） | 基础设施 |
| 3 | `miui.drm.ThemeReceiver` → `validateTheme` | 精确签名双版本匹配：四参 `(Context,String,String,Map)→void` 或三参 `(Context,String,String)→boolean`；同名的 synthetic 桥接方法因方法名不同不会被误挂 | before + after | before：同步拉取配置（eager 连接，见 8.6——validateTheme 只发生在系统就绪后，同步执行确保本帧窗口的租借判定读到真实配置）；按 `args[1]`（主题目录路径）判定类别（8.1 路径版），进入瞬时 DRM 窗口。after：无条件退出窗口 | 按类别 |
| 4 | `miui.drm.DrmManager` → `isLegal`（全部返回 DrmResult 的重载）、`isPermanentRights`、`isRightsFileLegal`（返回 boolean 的重载） | 按名 + 返回类型；仅在窗口存续期间动态挂载 | before（窗口内） | 当前线程存在已租借窗口且窗口类别对应开关开启时：`isLegal` → `setResult(真实 DRM_SUCCESS)`，后两者 → `setResult(true)`；否则放行真实校验 | 按类别 |
| 5 | `miui.drm.DrmManager` → `isSupportAd` | 按名，单参（Context 或 File） | before | `setResult(false)` | hide_theme_ads |
| 6 | `miui.drm.DrmManager` → `setSupportAd(Context, boolean)` | 精确签名 | before | `setResult(null)`，跳过原方法 | hide_theme_ads |

### 6.3 `com.miui.personalassistant`（智能助理）

| # | 目标 | 匹配方式 | 时机 | 行为 |
| --- | --- | --- | --- | --- |
| 1 | `com.miui.maml.widget.edit.MamlutilKt` → `themeManagerSupportPaidWidget(Context)` | 按名，`(Context)Z` | before | `setResult(false)` |
| 2 | `picker.business.detail.bean.PickerDetailResponse` → `isPay()` | 按名，`()Z` | before | `setResult(false)` |
| 3 | 同上 → `isBought()` | 按名，`()Z` | before | `setResult(true)` |
| 4 | `picker.business.detail.bean.PickerDetailResponseWrapper` → `isPay()` | 按名，`()Z` | before | `setResult(false)` |
| 5 | 同上 → `isBought()` | 按名，`()Z` | before | `setResult(true)` |
| 6 | `picker.business.detail.PickerDetailViewModel` → `isCanDirectAddMaMl(int)` | 按名，`(I)Z` 精确签名 | after | `setResult(true)` |
| 7 | `picker.business.detail.utils.PickerDetailDownloadManager$Companion` → `isCanDownload(PickerDetailResponse)` | 按名，单参 Response | before | `setResult(true)` |
| 8 | `picker.business.detail.utils.PickerDetailUtil` → `isCanAutoDownloadMaMl()` | 按名，无参 | before | `setResult(true)` |
| 9 | `PickerDetailViewModel` → `shouldCheckMamlBoughtState(int)` | 按名，`(I)Z` | before | `setResult(false)` |
| 10 | `PickerDetailViewModel` → `isTargetPositionMamlPayAndDownloading(int)` | 按名，`(I)Z` | before | `setResult(false)` |
| 11 | `PickerDetailViewModel` → `checkIsIndependentProcessWidgetForPosition(int)` | 按名，`(I)Z` | before | `setResult(true)` |
| 12 | `picker.bean.cards.SuitEntity` → `isShowPayLogo()` | 按名，`()Z` | after | `setResult(false)` |

本进程 #1–#11 属 `unlock_paid_widgets`，#12 属 `hide_widget_pay_badges`（均含 master 门控）。

### 6.4 `com.miui.home`（桌面）

| # | 目标 | 匹配方式 | 时机 | 行为 |
| --- | --- | --- | --- | --- |
| 1 | `com.miui.maml.widget.edit.MamlutilKt` → `themeManagerSupportPaidWidget(Context)` | 按名，`(Context)Z` | before | `setResult(false)` |
| 2 | `com.miui.home.launcher.gadget.MaMlPendingHostView` → `isCanAutoStartDownload()` | 按名，`()Z` | before | `setResult(true)` |

两点均属 `unlock_paid_widgets`。

---

## 7. 主题壁纸资源类别与收费现状

本节记录主题壁纸（`com.android.thememanager`）的资源类别体系、收费现状与 Hook 对应关系，作为开关划分（3.1）与资源分类（8.1）的依据。

### 7.1 分类体系（多套概念并存，各司其职）

主题壁纸内部并存多套"类别"概念，来源与用途互不相同，分析时必须分开、不可混用：

| 维度 | 载体（源文件名） | 内容 | 用途 / 边界 |
| --- | --- | --- | --- |
| **商品类别注册表** | `ConstantsHelper` 的 `id` Map | 恰好 11 个 resourceCode → FlexTab 对象：`hybrid / widget_suit / theme / wallpaper / videowallpaper / aod / ringtone / fonts / icons / miwallpaper / largeicons` | 唯一把 resourceCode 注册为商品类别（TAB）的权威表；`widget` 不在其中——它是历史服务端类型码（`ConstantsHelper` 的服务端码映射仍含 `widget → WIDGET`），不再是商品类别 |
| TAB 显隐与排序 | `Config.main_tab_sort`（主配置，默认值即上表 11 码的逗号序列）+ `Config$AbTestResult.main_tab_sort`（AB 实验覆盖，优先级更高） | 逗号序列 | 决定显示哪些 TAB 及顺序；可被服务端实验动态下发，不同设备可见 TAB 集合可能不同；不是类型全集 |
| **购买能力判定** | `ResourceContextManager` 构造 `ResourceContext` 时调用 `util.se.t()`（唯一调用方） | `t() = !ki() && !ni7()`，名单见下表 | 客户端唯一的购买能力开关 |
| 存储路径映射 | `ConstantsHelper` 的 `u` Map | resourceCode → 主题存储目录（如 `spwallpaper` → `<theme 路径>/spwallpaper`） | 按码取内容/授权文件目录；不是类别归属表 |
| 主题模块位掩码 | `ConstantsHelper` 的 `a`/`bb` Map 与常量库的 `0x1..0x20000000` 位序列 | resourceCode → Long 位掩码（-1 为全集） | 描述主题包含哪些部件（选装模块）；不是商品类别 |
| 显示名映射 | `ConstantsHelper` 的 `w` Map | 如 `spwallpaper → "SuperWallpaper"` | 展示用 PascalCase 名；与收费判定无关 |
| 规范目录名 | `ConstantsHelper` 的 `bo` Map | 目录名归一化（如 `photoframe_2x2 → photo_frame_2x2`） | 目录名转换，非类别判定 |
| 主题内子资源码 | `RelatedResource.getResourceCode()` | `framework-res / launcher / statusbar / BootAnimation / boots / animation / contact / Mms / incallshow / thirdapp / notification / alarm / bootaudio / audioeffect / ringtones / alarmscreen / mmsringtone / calendarringtone` 等 | 不在注册表、无独立 productId，随父资源逐项校验（见 7.3） |
| 组件描述符 | gadget 位掩码常量 | `clock_1x2 / photo_frame_* / icons_2x3 / HOVER / POP` 等 | 组件选择器描述符，非商品 |
| 常量原料库 | `ThemeResourceConstants`（常量接口） | 340 个常量字段（以 String 码为主，另有 long 位掩码、int、数组与 Set），无任何判定逻辑 | 上述各表的原料来源 |

判定链路（一个 resourceCode 的收费能力由谁决定）：

```
main_tab_sort（服务端：TAB 显隐与顺序）
  → ConstantsHelper.id（注册表：是否为商品类别）
    → ResourceContextManager 构造 ResourceContext 时调 se.t() 写入 purchaseSupported
      → se.t = !ki() && !ni7()   ← 客户端唯一硬编码免费名单
        → 服务端价格字段（Resource.productPrice / UIProduct.currentPriceInCent）决定最终收费/免费展示
```

由此形成四层收费版图：

| 层 | 类别 | 说明 |
| --- | --- | --- |
| 硬编码免费（代码层永不可购买） | `ki()` 名单：`bootaudio / ringtone / notification / alarm / audioeffect`；`ni7()` 名单（equals 精确匹配）：`wallpaper / lockscreen` | purchaseSupported=false；铃声 TAB 实为第三方酷音 H5（kuyin123.com），无购买管线 |
| 可收费、当前运营免费 | `theme`（广告变现）、`hybrid`、`icons`、`lockstyle`、`aod`、`videowallpaper` 族 | purchaseSupported=true、付费框架（米币/微信支付、试用体系）完整保留，当前价格全 0 |
| 实际收费 | `fonts`（免费/付费混排）、`largeicons`、`widget_suit`（TAB 码；历史码 `widget` 同链路） | 与商店 TAB 现状一致（`WidgetPurchaseActivity`、大图标批量购买体系） |
| 可收费、入口当前关闭 | `miwallpaper` 族；超级壁纸变体码 `spwallpaper / splockscreen / spaod`（不在注册表） | miwallpaper TAB 被 `main_tab_sort` 服务端隐藏；有试用体系（`DrmManager.getTrialLimits`）；超级壁纸独立详情入口 `MamlSuperWallpaperDetailActivity` 完整保留 |

补充事实：

- 铃声 TAB 为第三方酷音 H5 页面（kuyin123.com），本地仅 WebView 容器与试听控制，无购买管线；
- 免费主题依赖广告变现，配套框架 `DrmManager.setSupportAd`（对应 6.2 #5/#6 的去广告 Hook）；
- 任何类别都可能携带 `productId`/价格/购买态字段，收费与否最终由服务端按资源决定。

### 7.2 资源类别 × 收费状态 × 开关 × Hook 对应表

按 7.1 注册表的 11 个商品类别码组织（`lockscreen`/`lockstyle`/sp\* 无 TAB 码，单列注明码位性质）：

| 类别 | 码位性质 | 收费现状 | 归类 / 开关 | 对应 Hook |
| --- | --- | --- | --- | --- |
| `theme` | 注册表码（主题 TAB） | 免费（广告变现） | 普通资源：数据层 → `unlock_paid_resources`，DRM 层 → `allow_third_party_themes`（前者从属后者，下同） | 6.1 #1–#5、#6；#14、6.2 #3–#4 |
| `hybrid` | 注册表码（隐藏 TAB） | 可收费，当前免费 | 普通资源：数据层 → `unlock_paid_resources`，DRM 层 → `allow_third_party_themes` | 同上 |
| `fonts` / `Font` | 注册表码（字体 TAB） | 实际收费（免费/付费混排） | 普通资源：数据层 → `unlock_paid_resources`，DRM 层 → `allow_third_party_themes` | 同上 |
| `wallpaper`（+inner/outer/fold 变体） | 注册表码（壁纸 TAB） | 硬编码免费（`ni7()`） | 普通资源：数据层 → `unlock_paid_resources`，DRM 层 → `allow_third_party_themes` | 同上（免费态下经探测保护为无操作） |
| `lockscreen`（+fold/inner/outer） | 非 TAB 码（壁纸 TAB/主题子资源承载） | 硬编码免费（`ni7()`） | 普通资源：数据层 → `unlock_paid_resources`，DRM 层 → `allow_third_party_themes` | 同上 |
| `lockstyle`（+pad/inner/outer） | 非 TAB 码（主题子资源为主） | 可收费 | 普通资源：数据层 → `unlock_paid_resources`，DRM 层 → `allow_third_party_themes` | 独立对象同上；子资源经 #14 按父资源分类 |
| `icons` | 注册表码（图标 TAB） | 可收费，当前免费 | 普通资源：数据层 → `unlock_paid_resources`，DRM 层 → `allow_third_party_themes` | 同 `theme` |
| `videowallpaper`（+launcher/lockscreen/fold）、`dynamicvideowallpaper` | 注册表码（动态壁纸 TAB） | 可收费，当前免费 | 普通资源：数据层 → `unlock_paid_resources`，DRM 层 → `allow_third_party_themes` | 同 `theme` |
| `miwallpaper`（+inner/outer/pad） | 注册表码（隐藏 TAB） | 可收费、有试用体系，入口当前关闭 | 普通资源：数据层 → `unlock_paid_resources`，DRM 层 → `allow_third_party_themes` | 同 `theme` |
| `aod` | 注册表码（息屏 TAB） | 可收费，当前免费 | 普通资源：数据层 → `unlock_paid_resources`，DRM 层 → `allow_third_party_themes` | 同 `theme` |
| `ringtone` | 注册表码（铃声 TAB，实为酷音 H5） | 硬编码免费（`ki()`） | 普通资源：数据层 → `unlock_paid_resources`，DRM 层 → `allow_third_party_themes` | 同 `theme`；当前版本无操作（铃声族其余码见 7.3） |
| `largeicons` | 注册表码（大图标 TAB） | 实际收费（批量购买体系、专用购买端点） | 大图标 → `unlock_large_icons` | 6.1 #15、#18–#19（feed 数据层）、#1–#5（按类别）、#14；6.2 #3–#4（`large_icons` 路径） |
| `widget_suit`（TAB 码）与历史码 `widget` | 注册表码（小部件 TAB）/ 历史服务端类型码（服务端码映射仍含该码，不再是商品类别） | 实际收费（`WidgetPurchaseActivity`） | MAML → `unlock_paid_widgets` | 6.1 #13；6.3 #1–#11；6.4 |
| `spwallpaper / splockscreen / spaod / SuperWallpaper` | 超级壁纸变体码，不在注册表 | 可收费、试用/购买状态机（ThemeStatus + `getTrialLimits`），入口当前关闭；zip/.right 成对校验属小组件编辑器链路，非本族链路 | MAML → `unlock_paid_widgets` | 6.1 #17、#13；6.3；6.4 |

> 表注：普通资源的 DRM 放行拆为两层自 `allow_third_party_themes` 引入起——框架层校验（6.2 #3–#4 窗口，治系统回退）与本地导入资源的应用层放行（6.1 #14 无 productId 分支）由该开关门控；数据层购买态/价格改写（#1–#5）仍由 `unlock_paid_resources` 门控（从属前者）。大图标与 MAML 不变。

### 7.3 非独立商品（不占开关）

| 类别 | 性质 | DRM 覆盖方式 |
| --- | --- | --- |
| 主题内子模块：`framework-res / launcher / statusbar / BootAnimation / boots / animation / contact / Mms / incallshow / thirdapp / notification / alarm / bootaudio / audioeffect / ringtones / alarmscreen / mmsringtone / calendarringtone` 等 | `RelatedResource` 子资源，无独立 productId（音频族码同时位于 `ki()` 硬编码免费名单） | #14 按父资源分类 + 逐子资源 `.mra` 校验；6.2 #3 路径判定兜底 |
| 桌面 gadget 元素：`Clock_1x2/2x2/2x4/3x4`、`photo_frame_*`、`icons_2x3/4x3`、`HOVER`、`POP` | 组件选择器描述符，非商品 | 无 productId，探测保护（8.2）自动放行 |

补充：推荐流（首页 TAB 与列表）的商品卡片价格直读 `UIProduct.currentPriceInCent`，不经 Resource 管线——Resource 层 Hook 天然覆盖不到。字体/主题卡片经 `DiscountPriceView` 显示（6.1 #6 覆盖），大图标卡片自绘价格文本，故另设 #18–#19 数据层改写；价格 ≤0 时宿主格式化函数自带"免费"分支。

### 7.4 DRM 校验链速览

- 应用层 DrmService（源文件名，混淆类名随版本变化，即 6.1 #14 特征扫描的目标）：`clone` 资源 → 内容路径为空或 `/system` 前缀直接放行 → 逐子资源查 `.mra` 授权文件 → 授权文件 SHA-1 等于空文件哈希（`da39a3ee…`）时跳过该项校验（8.5 空占位文件方案的依据）→ 其余调框架 `miui.drm.DrmManager.isLegal`；
- MAML 小组件编辑器链路（`widget-edit` SDK，消费方为智能助理/桌面进程；主题壁纸进程内该链路无外部调用方，见 6.1 #13）：`MamlDrmUtilKt.isLegal` 先经 `themeManagerSupportPaidWidget` 门禁（读主题壁纸 manifest 的 `supportPaidWidget` 元数据），再校验 `<productId>.zip` 与 `<productId>.right` 成对文件；
- 超级壁纸（sp\*）：走试用/购买状态机（`ThemeStatus`）+ 试用体系（`DrmManager.getTrialLimits`，经 `ResourceApplyHelper` 读取），不经 zip/.right 校验；
- 大图标应用任务（源文件名 `ApplyLargeIconTask`，即 6.1 #15 目标）：执行前构造无 productId 的探测 Resource（期望价格非零、购买态 false、DRM 非成功），即 8.2 探测保护的来源；
- 框架层 DRM（`ThemeReceiver.validateTheme`、`DrmManager`）位于系统框架，不在主题壁纸 APK 内，由 6.2 处理。

---

## 8. 核心机制

### 8.1 资源分类（ResourcePolicy）

主题壁纸中的每个资源对象归入四类之一：普通资源、大图标、MAML、未知（类别全集与收费现状见第 7 节）。判定顺序：

1. **身份标签缓存**：`toResource`（6.1 #5）与超级壁纸入口（6.1 #17）登记过的对象直接命中（对象与其 `onlineProperties` 均可作为键命中）；
2. **任务栈**：当前线程正在执行的大图标任务所持有的 Resource 判为大图标；
3. **`category` 字段**，其次 **`productType` 字段**（`productType` 实际是 `OnlineResourceDetail` 的字段，`Resource` 上不存在；对详情对象直读字段，对其余对象反射读取）：按小写子串**有序**匹配——先大图标（`largeicon` / `large_icon`，先于 `icons` 判定，防止被其吞掉），再 MAML（`maml / widget / spwallpaper / splockscreen / spaod / superwallpaper`，先于普通资源词判定，防止 `spwallpaper`→`wallpaper`、`spaod`→`aod` 误吞），再普通资源（`theme / font / wallpaper / lockscreen / lockstyle / icons / icon / hybrid / aod / ringtone / notification / alarm / bootaudio / audioeffect`，子串自动覆盖 `videowallpaper / miwallpaper / mmsringtone` 等无分隔符复合词），其余 → 未知（不处理）。两字段的解析结果合成规则：`largeicons` 族或 MAML 类别优先采用；其次 `category` 解析非未知时采用 `category`，否则采用 `productType`。

框架层（6.2 #3）使用路径等价判定：`/data/system/theme/` 下的路径含 `large_icons/largeicons` 段 → 大图标，含 MAML 关键词段 → MAML，否则普通资源；非主题路径不处理。

标签缓存以**对象身份**（identity）为键、弱引用持有——宿主模型类可能实现值相等，按值缓存会把标签误发给相等对象。`clone()` 的 after（6.1 #16）负责把标签带到克隆体上。

三个解锁开关分别对应三类资源；"未知"类别永不改写。

### 8.2 门控与探测保护

宿主在应用大图标前，会构造**没有 productId 的临时对象**探测环境（期望读到价格非零、购买态 false、DRM 非成功）；任何一项被强改都会导致应用失败（`apply failed`）。因此 6.1 #1–#5、#14 的回调在动作前先做对象判定：

- 取对象的 `productId`（经 `onlineProperties` 间接读取，详情对象则直接读字段）；
- **数据层（#1–#5）**：productId 为空白 → 一律放行原逻辑，不做任何改写（含本地导入主题——第三方主题开关不触碰价格与购买态即由此保证）；非空白 → 按 8.1 判定类别，对应开关开启才注入；
- **应用层 DRM（#14）**：productId 非空白 → 按类别判定（大图标探测对象属大图标类别，真实 DRM 结果保持，探测安全）；productId 空白且类别为普通资源（本地导入主题）→ `allow_third_party_themes` 开启时注入 `DRM_SUCCESS`。

大图标任务执行期间，任务持有的 Resource 进入 ThreadLocal 任务栈参与类别判定；任务方法退出时无条件出栈，保证异常路径不残留。

### 8.3 DRM 处理

**真实枚举注入**：所有注入的 `DRM_SUCCESS` 一律从被 Hook 方法的**真实返回类型**反射取得（`returnType.getField("DRM_SUCCESS")`），不打包任何 `miui.drm` 存根，保证枚举实例来自宿主进程实际加载的框架类。

**瞬时窗口（ScopedHookWindow）**：`validateTheme` 会对主题目录的子目录递归自调用、逐个组件调用 `DrmManager.isLegal`。窗口机制按"每进程安装、按线程授权"运作：

- 进入 `validateTheme` 时按路径类别决定是否租借；租借使引用计数 0→1，此时才动态挂载 6.2 #4 的全部目标（含失败回滚已挂部分）；
- 每次调用（含嵌套递归、含未租借的调用）都压入线程帧栈；`isLegal` 等回调只认本线程最上层的已租借帧；
- 递归进入子目录时类别可能变化：外层已租借、内层若对应开关关闭则内层帧未租借，`isLegal` 对该子目录放行真实校验——单次校验扫描内可按路径类别区别对待；
- 帧退出即弹栈；引用计数归零立即逆序 unhook；重复退出幂等；异常路径同样清理。

与常驻 Hook 不同，这套窗口保证系统其余时间的 DRM 全部真实运行。

### 8.4 混淆对抗（DEX 特征扫描）

主题壁纸的 DRM 业务方法与大图标任务方法已被混淆且每次发版改名，用纯 Kotlin DEX 只读解析器按特征定位：

- 解析 DEX 的 string/type/proto/method/class 五张表，把 `const-string` 指令关联到所属方法（含完整指令宽度表、payload 指令处理与边界校验）；
- DRM 业务方法：非 static、类前缀 `com.android.thememanager.controller.online`、返回 `miui.drm.DrmManager$DrmResult`、参数为单个 Resource、方法体字符串含 `"check rights isLegal:"` 或同时含 `theme`、`ThemeManagerTag`、`/system`；
- 大图标任务：非 static、参数 `[Void[]]`、字符串同时含 `apply failed`、`/data/system/theme/large_icons/`、`default_large_icon_product_id`，且任务类持有 Resource 实例字段；
- 扫描覆盖 split APK；以各 dex 条目（名称:CRC:大小）计算指纹缓存于宿主私有存储，命中缓存直接反序列化方法清单，未命中全量扫描后写入；
- 任一环节失败整段放弃，不影响其他 Hook；两类方法都命中才写缓存。

### 8.5 大图标授权文件与归属管理

大图标任务方法执行前（6.1 #15 before）：

1. 遍历任务类字段取第一个 Resource 类型值 → `getProductId()`（安全校验：非空、长度 ≤180、无路径分隔符与控制字符），任一步为空则放弃本次；
2. 在宿主外部文件目录的 `MIUI/theme/.data/rights/theme/` 下创建空文件 `<productId>-largeicons.mra`，已存在则跳过。

空文件只满足存在性检查，真实授权仍依赖 8.3 的 DRM 处理，两者配合使用。

**归属管理**：以 journal 文件（位于宿主内部存储 `filesDir/.miui-theme-extension-rights`，文件锁 + Properties + 临时文件原子替换 + fsync）记录本模块创建的每个文件及其时间戳。功能关闭时只清理 journal 中归属本模块**且仍为空**的文件——宿主已有的、或随后被写入过内容的授权文件一律不动。创建失败时回滚（文件仍为空且时间戳未变则删除）。

### 8.6 配置跨进程同步

- **写入侧**（模块 UI 进程）：设备保护存储（Direct Boot 可用）的 SharedPreferences 保存全部键值，每次写入令 `revision+1` 并 `notifyChange`；
- **读取侧**（宿主进程）：导出只读 ContentProvider（authority `me.purainity.miuithemeextension.config`，`directBootAware` 覆盖开机未解锁阶段）。鉴权：system UID 视为 `android` 宿主，其余按调用方 UID 反查包名并与四宿主求交；`insert/update/delete` 一律拒绝。宿主获得 Context 后注册 ContentObserver，并在注册后立即查询快照刷新内存缓存。主题商店被 MIUI 降为后台时，虽仍保留观察者注册，却可能延迟跨 UID 回调；因此在每个宿主 Activity 的 `onResume` 前同步重新查询快照。读取失败按全关配置处理且只记一次日志。
- **system_server 读取时机**：两个早期注入点（6.2 #1/#2）在开机阶段 ContentService 尚未启动时异步尝试、预期失败（不阻塞开机）；真正的拉取发生在 validateTheme 入口（6.2 #3）——**同步执行**（此时系统必然就绪，binder 线程短暂阻塞可接受），确保本帧窗口的租借判定读到真实配置；未成功读取期间每次 validateTheme 都重试，成功后由 ContentObserver 接管变更；模块不可用（卸载/禁用）时保持全关，系统 DRM 真实运行（fail-closed）。
- 开关变化通过 ContentObserver 立即通知运行中的应用宿主；用户从设置返回宿主、或重新进入详情时由前台边界拉取保证读取最新 revision；未运行的宿主会在下次启动并注册观察者后读取最新快照。同步正确性不依赖广播或轮询。

### 8.6.1 已改写对象的回滚

主题壁纸会复用详情页与 Resource 的内存对象。`toResource()` 在解锁生效时写入的 `bought`、`productPrice`、`originPrice` 及 Resource 购买态/价格，必须以对象身份弱引用保存首次改写前的值。配置更新后，若该对象类别对应的解锁开关已关闭，立即恢复原值并移除记录；因此用户返回列表后立即再次进入详情页时，不会继续读取上一次解锁留下的缓存状态。仅停止 Hook 不能替代此回滚。

大图标 feed 的 `UIProduct` / `LargeIconElement`（6.1 #18–#19）同样进 journal：feed 对象在滚动中反复绑定，按对象身份去重、仅首次捕获原值；关闭 `unlock_large_icons` 后立即还原价格与购买态，已渲染的卡片随列表刷新恢复原价。

### 8.7 激活自检与图标隐藏

- **激活自检**：不 Hook 任何方法——模块加载入口在自身进程经 `param.classLoader` 反射调用 `ModuleStatus.markActive()` 置静态标志（必须经应用 classloader 取类：新框架的模块类加载器与宿主 classpath 隔离，直接静态赋值有类身份分裂风险，反射走应用 classloader 保证与 UI 读取同一个 Class 实例）；框架 API 版本由入口直接调用 `XposedBridge.getXposedVersion()`（该类仅模块类加载器可见，应用自身 classloader 反射不可达——UI 侧 `Class.forName` 必失败）并经同一反射通道记入 `ModuleStatus`；UI 读 `ModuleStatus.isActive()` 渲染激活卡片，未被框架加载时保持编译期默认 false。不检测、不上报各宿主的接入状态；
- **图标隐藏**：见第 4 节第 6 条，与 Hook 状态完全无关。

---

## 9. 兼容性边界

| 项 | 说明 |
| --- | --- |
| `validateTheme` 签名 | 新版为四参 `(Context,String,String,Map)→void`，旧版为三参 `(Context,String,String)→boolean`，两种签名均已按精确参数匹配挂载（分别经真机 dexdump 与框架编译桩核实）。后续版本若再变更签名，该点静默跳过，框架层窗口整体不生效，主题壁纸进程的 6.1 #14 不受影响 |
| DEX 扫描缓存 | 仅当 DRM 与大图标两类方法都命中才写指纹缓存；只命中一类时每次冷启动全量重扫，属性能损耗，无功能影响 |
| 重载覆盖 | `isCanDirectAddMaMl` 按 `(int)` 精确签名挂载（实测唯一重载）；`isSupportAd` 按单参 Context/File 匹配（恰为框架全部重载）。宿主未来新增其他重载时需核对语义后再扩展 |
| 生效时机 | 已隐藏的 View 需刷新页面才恢复；关闭解锁开关时会先回滚已改写的缓存对象，返回列表后立即再次进入详情页即可看到原购买态；大图标授权文件在关闭功能时仅清理本模块创建且仍为空的文件 |
| 框架层配置读取时机 | system_server 两个早期注入点（6.2 #1/#2）的连接尝试在 ContentService 启动前必然失败（预期行为，异步执行不阻塞开机）；配置在 validateTheme 入口同步重拉直至成功（见 8.6）；模块不可用期间框架层开关保持全关、系统 DRM 真实运行（fail-closed） |
