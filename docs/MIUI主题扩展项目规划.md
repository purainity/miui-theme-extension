# MIUI 主题扩展 · 项目规划

## 1. 项目定位

一个 Xposed 模块，用于解锁 MIUI 主题壁纸中的付费内容（主题、字体、大图标、小组件、超级壁纸），隐藏付费标识与商店广告。模块附带一个 Material Design 3 风格的配置界面，两者打包为单一 APK。

| 项 | 值 |
|---|---|
| 包名 | `me.purainity.miuithemeextension` |
| 语言 / 构建 | Kotlin；minSdk 24，targetSdk 37 |
| Xposed 入口 | `me.purainity.miuithemeextension.hook.XposedEntry`，仅实现 `IXposedHookLoadPackage`；不使用 zygote 入口，框架层 Hook 全部在 `android` 宿主进程内完成 |
| 推荐作用域 | manifest 通过 `xposedscope` 静态声明四项：Android 系统框架、MIUI 主题壁纸、MIUI 智能助理、MIUI 桌面 |
| 目标系统 | MIUI 12–14 及更新的 MIUI 版本 |
| 日志 | 统一前缀 `MIUI 主题扩展: `，写入 Xposed 日志；同一 Hook 点的同类异常只记录首次，避免刷屏 |

宿主进程共四个：

| 进程 | 职责 |
|---|---|
| `com.android.thememanager` | 主题壁纸：购买态、价格、DRM、大图标、广告、MAML 入口 |
| `com.miui.personalassistant` | 智能助理：小组件详情页的付费判断与角标 |
| `com.miui.home` | 桌面：MAML 小组件的下载许可 |
| `android` | system_server：DRM 框架层，瞬时窗口 |

---

## 2. 技术红线

**必须遵守：**

- 业务 Hook 只用 legacy Xposed API（`de.robv.android.xposed.*`，compileOnly 引入）；
- UI 使用 Kotlin + XML Views + ViewBinding 与 Material Design 3 控件；
- 混淆对抗使用纯 Kotlin 实现的 DEX 只读解析，不依赖任何运行时特征检索库。

**禁止：**

- 禁止 DataBinding；
- 禁止 `com.android.support` 旧支持库；
- 禁止引入任何携带 so 原生库的依赖；
- 禁止引入 libxposed（任何形式，含 100/101/102 Hook API 与 Service Provider）；
- 禁止 `XSharedPreferences`；
- 禁止任何需要 root 权限的功能；
- 禁止实现小窗、背屏/副屏相关功能；
- 文案禁止出现 "HyperOS"，一律表述为 MIUI。

---

## 3. 功能开关

### 3.1 开关定义

| 键 | 默认值 | 界面名称 | 开启后的效果 |
|---|---|---|---|
| `master_enabled` | false | 总开关 | 所有功能生效的前提；关闭后一切 Hook 放行原逻辑 |
| `unlock_paid_resources` | false | 解锁付费主题与字体 | 付费主题/字体/壁纸的购买态、授权态、价格视为免费可用（含 DRM 授权层）；主题与字体共用同一底层购买对象，不拆分 |
| `hide_paid_badges` | true | 隐藏主题付费标识 | 主题详情页价格/折扣控件归零显示（仅 UI 层） |
| `unlock_large_icons` | false | 解锁付费大图标 | 付费大图标主题可下载应用 |
| `unlock_paid_widgets` | false | 解锁付费小组件 | 智能助理/桌面/商店中付费 MAML 小组件可下载、可添加；超级壁纸按 MAML 资源链路适配 |
| `hide_widget_pay_badges` | true | 隐藏小组件付费标识 | 智能助理组件卡片不再显示付费角标（仅 UI 层） |
| `hide_theme_ads` | false | 隐藏主题商店广告 | 广告判定无效、视频广告失效、广告卡片不占位 |
| `hide_launcher_icon` | false | 隐藏模块图标 | 隐藏桌面图标（默认显示）；与激活状态无关 |

### 3.2 联动规则

`hide_paid_badges` 从属于 `unlock_paid_resources` 或 `unlock_large_icons`（任一开启即可交互）；`hide_widget_pay_badges` 从属于 `unlock_paid_widgets`。具体行为：

- 依赖不满足时，子开关原生禁用、整行不可点击，标题与说明文字保持原色不变；
- 关闭解锁开关只禁用子开关，存储值保留；重新开启解锁开关时不自动改写子开关；
- 总开关关闭时，其余全部开关禁用。

### 3.3 生效判定

回调内的判定公式（`m` = master，`r` = resources，`l` = large_icons，`w` = widgets）：

- 角标类：`hide_paid_badges` 生效 = `m && (r || l) && hide_paid_badges`；`hide_widget_pay_badges` 生效 = `m && w && hide_widget_pay_badges`；
- 广告类、小组件类：`m && 对应开关`；
- 数据层与 DRM 层按**资源类别**分开关（见 7.1）：普通资源 → `r`，大图标 → `l`，MAML → `w`；
- 无论开关状态如何，无有效 `productId` 的探测对象一律放行原逻辑（见 7.2）；
- 开关关闭即放行，常驻 Hook 不需要动态卸载；瞬时 Hook 的清理在任何路径下都会执行（见 7.3）。

### 3.4 开关与 Hook 的对应关系

| 开关 | 覆盖的 Hook（编号见 6.1–6.4） |
|---|---|
| `master_enabled` | 全部 Hook 点的总门控 |
| `unlock_paid_resources` | 6.1 #1–#5 中类别为普通资源的调用；6.1 #14 中参数为普通资源；6.2 #3–#4 窗口类别为普通资源 |
| `unlock_large_icons` | 6.1 #15 的授权文件部分；6.1 #1–#5、#14 中类别为大图标；6.2 #3–#4 窗口类别为大图标 |
| `unlock_paid_widgets` | 6.1 #13、#17；6.1 #1–#5、#14 中类别为 MAML；6.2 #3–#4 窗口类别为 MAML；6.3 全部 #1–#11；6.4 全部 |
| `hide_paid_badges` | 6.1 #6 |
| `hide_widget_pay_badges` | 6.3 #12 |
| `hide_theme_ads` | 6.1 #7–#12；6.2 #5–#6 |
| `hide_launcher_icon` | 无 Hook，仅控制 activity-alias 启停 |

---

## 4. 配置界面

单 Activity 布局，自上而下依次为：

1. **激活状态卡片**。显示当前激活状态：激活时提示"模块已激活 / 在下方按需开启各功能"；未激活时提示"模块未激活 / 先在 LSPosed 中启用模块并勾选作用域，再重启手机"。判定方式见 7.7。
2. **版本信息卡片**。每项两行：标签在上、内容在下，内容可长按选中复制。显示项：模块版本（versionName + versionCode）、设备型号（`ro.product.marketname` 与 device，取不到回退 `Build.MODEL`）、系统版本（Android 版本 + API）、MIUI 版本（`Build.VERSION.INCREMENTAL`）、三个宿主版本（未安装显示"未安装"，读取失败显示"无法读取"）。包可见性查询仅用于版本展示。
3. **总开关卡片**。独立卡片，位于设置区最上方；关闭时下方所有开关禁用。
4. **功能分组卡片**。四组：
   - 主题与字体：解锁付费主题与字体、隐藏主题付费标识、解锁付费大图标；
   - 小组件：解锁付费小组件、隐藏小组件付费标识;
   - 主题商店：隐藏主题商店广告；
   - 其他设置：隐藏模块桌面图标。
   每行为"标题 + 一句效果说明 + 开关"，整行可点击。
5. **禁用态说明**。被依赖的子开关禁用时，说明追加依赖提示（"开启主题或大图标解锁后可用"/"开启小组件解锁后可用"）。
6. **图标隐藏**。通过指向主 Activity 的 `activity-alias` 启停实现（`DONT_KILL_APP`）：设置失败回滚存储值并提示；每次回到界面时以组件实际状态回写存储（外部修改自愈）；隐藏后仍可从 LSPosed 进入设置；说明文字提醒在 LSPosed 中关闭"强制显示桌面图标"。

其他约定：文案统一使用"MIUI 主题壁纸 / MIUI 智能助理 / MIUI 桌面 / MIUI 版本"称谓；字符串末尾不加句号；界面为 Material Design 3 风格——顶部为大标题折叠应用栏（展开 152dp、滚动收起为 64dp 工具栏，未滚动为 surface 色、滚动后为 surfaceContainer 色），设置行按 MD3 双行列表规范排布，边到边布局下顶栏背景延伸到状态栏后面。

---

## 5. Hook 总体设计

### 5.1 进程分发

入口按 `packageName` 分发：

- 模块自身进程：挂激活自检（7.7）；
- `android`：安装框架层 Hook（6.2）；
- 其余三个宿主：先 hook `Application.attach(Context)` 的 after 拿到宿主 Context 并连接配置客户端（7.6），再安装各自的业务 Hook；主题壁纸进程同时接收 APK 路径（含 split）供 DEX 扫描（7.4）。

### 5.2 公共约定

- **容错**：目标类容错查找，不存在只记日志跳过；每个 Hook 点独立异常隔离，回调内部再隔离一次——单点失败既不影响原方法执行，也不影响其他 Hook 点（致命虚拟机错误除外）；
- **匹配**：按名匹配时必须同时核验参数与返回类型；混淆目标一律经特征扫描定位（7.4），不出现任何硬编码混淆名；
- **纪律**：回调先门控后动作；after 回调在原方法抛出异常时不执行，不掩盖宿主异常；
- **版本策略**：按类、签名、特征三级判定，不按宿主版本号套用整组 Hook。

---

## 6. 各进程 Hook 明细

### 6.1 `com.android.thememanager`（主题壁纸）

| # | 目标 | 匹配方式 | 时机 | 行为 | 开关 |
|---|---|---|---|---|---|
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
| 13 | `com.miui.maml.widget.edit.MamlutilKt` → `themeManagerSupportPaidWidget(Context)` | 按名，`(Context)Z`，存在才挂 | before | `setResult(false)`——让宿主认为不支持付费组件机制，整条付费拦截链短路 | unlock_paid_widgets |
| 14 | DRM 业务方法（特征扫描，7.4）：`controller.online` 包下返回 `DrmResult`、参数为单个 Resource 的实例方法 | 特征定位 | after | 原方法无异常、返回非 null 且首个参数按类别放行时，`setResult(真实 DRM_SUCCESS)`（7.3） | 按类别 |
| 15 | 大图标应用任务（特征扫描，7.4）：参数 `[Void[]]`、任务类持有 Resource 实例字段 | 特征定位 + 字段确认 | before + after | before：把任务持有的 Resource 压入线程任务栈并绑定到本次调用；productId 校验通过且开关开启时准备授权占位文件（7.5）。after：无条件出栈（含异常路径） | 栈部分服务类别判定；授权文件部分 = unlock_large_icons |
| 16 | `Resource` → `clone()` | 按名，`()Resource`（排除返回 Object 的 bridge 方法） | after | 把原对象的类别标签传播给克隆对象——DRM 校验内部会克隆资源，不传播会丢类别 | 标签机制 |
| 17 | `superwallpaper.activity.MamlSuperWallpaperDetailActivity`（以 `Activity.onCreate` 为入口，回调内校验实例类型） | 类存在才挂 | before | 修正 intent 中 `extra_bundle` 的 classLoader，反序列化 `extra_superwallpaper_info`，为该 Resource 登记 MAML 标签——超级壁纸由此归入小组件开关 | 标签机制 |

### 6.2 `android`（system_server，DRM 框架层）

| # | 目标 | 匹配方式 | 时机 | 行为 | 开关 |
|---|---|---|---|---|---|
| 1 | `com.android.server.SystemServer` → `startBootstrapServices` | 按名 | before | 读取 `mSystemContext` 字段连接配置客户端（system_server 没有 Application.attach） | 基础设施 |
| 2 | `android.app.ActivityThread` → `systemMain()` | 按名，无参 | after | 经 `getSystemContext()` 连接配置客户端（备用路径） | 基础设施 |
| 3 | `miui.drm.ThemeReceiver` → `validateTheme` | 精确签名双版本匹配：四参 `(Context,String,String,Map)→void` 或三参 `(Context,String,String)→boolean`；同名的 synthetic 桥接方法因方法名不同不会被误挂 | before + after | before：连接配置客户端；按 `args[1]`（主题目录路径）判定类别（7.1 路径版），进入瞬时 DRM 窗口。after：无条件退出窗口 | 按类别 |
| 4 | `miui.drm.DrmManager` → `isLegal`（全部返回 DrmResult 的重载）、`isPermanentRights`、`isRightsFileLegal`（返回 boolean 的重载） | 按名 + 返回类型；仅在窗口存续期间动态挂载 | before（窗口内） | 当前线程存在已租借窗口且窗口类别对应开关开启时：`isLegal` → `setResult(真实 DRM_SUCCESS)`，后两者 → `setResult(true)`；否则放行真实校验 | 按类别 |
| 5 | `miui.drm.DrmManager` → `isSupportAd` | 按名，单参（Context 或 File） | before | `setResult(false)` | hide_theme_ads |
| 6 | `miui.drm.DrmManager` → `setSupportAd(Context, boolean)` | 精确签名 | before | `setResult(null)`，跳过原方法 | hide_theme_ads |

### 6.3 `com.miui.personalassistant`（智能助理）

| # | 目标 | 匹配方式 | 时机 | 行为 |
|---|---|---|---|---|
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
|---|---|---|---|---|
| 1 | `com.miui.maml.widget.edit.MamlutilKt` → `themeManagerSupportPaidWidget(Context)` | 按名，`(Context)Z` | before | `setResult(false)` |
| 2 | `com.miui.home.launcher.gadget.MaMlPendingHostView` → `isCanAutoStartDownload()` | 按名，`()Z` | before | `setResult(true)` |

两点均属 `unlock_paid_widgets`。

---

## 7. 核心机制

### 7.1 资源分类（ResourcePolicy）

主题壁纸中的每个资源对象归入四类之一：普通资源、大图标、MAML、未知。判定顺序：

1. **身份标签缓存**：`toResource`（6.1 #5）与超级壁纸入口（6.1 #17）登记过的对象直接命中（对象与其 `onlineProperties` 均可作为键命中）；
2. **任务栈**：当前线程正在执行的大图标任务所持有的 Resource 判为大图标；
3. **`category` 字段**，其次 **`productType` 字段**：按 `/ - _ . :` 分词后匹配关键词——`largeicons` 族 → 大图标；`maml / widget / spwallpaper / splockscreen / spaod / superwallpaper` → MAML；`theme / font / wallpaper / lockscreen / icons` 族 → 普通资源；其余 → 未知（不处理）。两字段的解析结果合成规则：`largeicons` 族或 MAML 类别优先采用；其次 `category` 解析非未知时采用 `category`，否则采用 `productType`。

框架层（6.2 #3）使用路径等价判定：`/data/system/theme/` 下的路径含 `large_icons/largeicons` 段 → 大图标，含 MAML 关键词段 → MAML，否则普通资源；非主题路径不处理。

标签缓存以**对象身份**（identity）为键、弱引用持有——宿主模型类可能实现值相等，按值缓存会把标签误发给相等对象。`clone()` 的 after（6.1 #16）负责把标签带到克隆体上。

三个解锁开关分别对应三类资源；"未知"类别永不改写。

### 7.2 门控与探测保护

宿主在应用大图标前，会构造**没有 productId 的临时对象**探测环境（期望读到价格非零、购买态 false、DRM 非成功）；任何一项被强改都会导致应用失败（`apply failed`）。因此 6.1 #1–#5、#14 的回调在动作前先做对象判定：

- 取对象的 `productId`（经 `onlineProperties` 间接读取，详情对象则直接读字段）；为空白 → 视为探测对象，放行原逻辑；
- 非空白 → 按 7.1 判定类别，对应开关开启才注入。

大图标任务执行期间，任务持有的 Resource 进入 ThreadLocal 任务栈参与类别判定；任务方法退出时无条件出栈，保证异常路径不残留。

### 7.3 DRM 处理

**真实枚举注入**：所有注入的 `DRM_SUCCESS` 一律从被 Hook 方法的**真实返回类型**反射取得（`returnType.getField("DRM_SUCCESS")`），不打包任何 `miui.drm` 存根，保证枚举实例来自宿主进程实际加载的框架类。

**瞬时窗口（ScopedHookWindow）**：`validateTheme` 会对主题目录的子目录递归自调用、逐个组件调用 `DrmManager.isLegal`。窗口机制按"每进程安装、按线程授权"运作：

- 进入 `validateTheme` 时按路径类别决定是否租借；租借使引用计数 0→1，此时才动态挂载 6.2 #4 的全部目标（含失败回滚已挂部分）；
- 每次调用（含嵌套递归、含未租借的调用）都压入线程帧栈；`isLegal` 等回调只认本线程最上层的已租借帧；
- 递归进入子目录时类别可能变化：外层已租借、内层若对应开关关闭则内层帧未租借，`isLegal` 对该子目录放行真实校验——单次校验扫描内可按路径类别区别对待；
- 帧退出即弹栈；引用计数归零立即逆序 unhook；重复退出幂等；异常路径同样清理。

与常驻 Hook 不同，这套窗口保证系统其余时间的 DRM 全部真实运行。

### 7.4 混淆对抗（DEX 特征扫描）

主题壁纸的 DRM 业务方法与大图标任务方法已被混淆且每次发版改名，用纯 Kotlin DEX 只读解析器按特征定位：

- 解析 DEX 的 string/type/proto/method/class 五张表，把 `const-string` 指令关联到所属方法（含完整指令宽度表、payload 指令处理与边界校验）；
- DRM 业务方法：非 static、类前缀 `com.android.thememanager.controller.online`、返回 `miui.drm.DrmManager$DrmResult`、参数为单个 Resource、方法体字符串含 `"check rights isLegal:"` 或同时含 `theme`、`ThemeManagerTag`、`/system`；
- 大图标任务：非 static、参数 `[Void[]]`、字符串同时含 `apply failed`、`/data/system/theme/large_icons/`、`default_large_icon_product_id`，且任务类持有 Resource 实例字段；
- 扫描覆盖 split APK；以各 dex 条目（名称:CRC:大小）计算指纹缓存于宿主私有存储，命中缓存直接反序列化方法清单，未命中全量扫描后写入；
- 任一环节失败整段放弃，不影响其他 Hook；两类方法都命中才写缓存。

### 7.5 大图标授权文件与归属管理

大图标任务方法执行前（6.1 #15 before）：

1. 遍历任务类字段取第一个 Resource 类型值 → `getProductId()`（安全校验：非空、长度 ≤180、无路径分隔符与控制字符），任一步为空则放弃本次；
2. 在宿主外部文件目录的 `MIUI/theme/.data/rights/theme/` 下创建空文件 `<productId>-largeicons.mra`，已存在则跳过。

空文件只满足存在性检查，真实授权仍依赖 7.3 的 DRM 处理，两者配合使用。

**归属管理**：以 journal 文件（位于宿主内部存储 `filesDir/.miui-theme-extension-rights`，文件锁 + Properties + 临时文件原子替换 + fsync）记录本模块创建的每个文件及其时间戳。功能关闭时只清理 journal 中归属本模块**且仍为空**的文件——宿主已有的、或随后被写入过内容的授权文件一律不动。创建失败时回滚（文件仍为空且时间戳未变则删除）。

### 7.6 配置跨进程同步

- **写入侧**（模块 UI 进程）：设备保护存储（Direct Boot 可用）的 SharedPreferences 保存全部键值，每次写入令 `revision+1` 并 `notifyChange`；
- **读取侧**（宿主进程）：导出只读 ContentProvider（authority `me.purainity.miuithemeextension.config`）。鉴权：system UID 视为 `android` 宿主，其余按调用方 UID 反查包名并与四宿主求交；`insert/update/delete` 一律拒绝。宿主获得 Context 后注册 ContentObserver，并在注册后立即查询快照刷新内存缓存。主题商店被 MIUI 降为后台时，虽仍保留观察者注册，却可能延迟跨 UID 回调；因此在每个宿主 Activity 的 `onResume` 前同步重新查询快照。读取失败按全关配置处理且只记一次日志。
- 开关变化通过 ContentObserver 立即通知运行中的应用宿主；用户从设置返回宿主、或重新进入详情时由前台边界拉取保证读取最新 revision；未运行的宿主会在下次启动并注册观察者后读取最新快照。同步正确性不依赖广播或轮询。

### 7.6.1 已改写对象的回滚

主题壁纸会复用详情页与 Resource 的内存对象。`toResource()` 在解锁生效时写入的 `bought`、`productPrice`、`originPrice` 及 Resource 购买态/价格，必须以对象身份弱引用保存首次改写前的值。配置更新后，若该对象类别对应的解锁开关已关闭，立即恢复原值并移除记录；因此用户返回列表后立即再次进入详情页时，不会继续读取上一次解锁留下的缓存状态。仅停止 Hook 不能替代此回滚。

### 7.7 激活自检与图标隐藏

- **激活自检**：模块自身进程内 hook `ModuleStatus.isActive()` 使其返回 true；UI 据此渲染激活卡片，未激活时该方法返回编译期默认 false。不检测、不上报各宿主的接入状态；
- **图标隐藏**：见第 4 节第 6 条，与 Hook 状态完全无关。

---

## 8. 兼容性边界

| 项 | 说明 |
|---|---|
| `validateTheme` 签名 | MIUI 14 起为四参 `(Context,String,String,Map)→void`，MIUI 13 及更早为三参 `(Context,String,String)→boolean`，两种签名均已按精确参数匹配挂载（分别经真机 dexdump 与框架编译桩核实）。后续版本若再变更签名，该点静默跳过，框架层窗口整体不生效，主题壁纸进程的 6.1 #14 不受影响 |
| DEX 扫描缓存 | 仅当 DRM 与大图标两类方法都命中才写指纹缓存；只命中一类时每次冷启动全量重扫，属性能损耗，无功能影响 |
| 重载覆盖 | `isCanDirectAddMaMl` 按 `(int)` 精确签名挂载（各基线实测唯一重载）；`isSupportAd` 按单参 Context/File 匹配（恰为框架全部重载）。宿主未来新增其他重载时需核对语义后再扩展 |
| 生效时机 | 已隐藏的 View 需刷新页面才恢复；关闭解锁开关时会先回滚已改写的缓存对象，返回列表后立即再次进入详情页即可看到原购买态；大图标授权文件在关闭功能时仅清理本模块创建且仍为空的文件 |
