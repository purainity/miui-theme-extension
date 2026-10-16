# 已有的主题破解和MIUI扩展模块对比分析报告

分析对象为以下10个模块：

| # | 模块 | 包名 | 版本 |
|---|------|------|------|
| 1-6 | Miui主题破解 | `com.yuk.fuckMiuiThemeManager` | 1.1 / 1.2 / 1.3 / 1.4 / 1.7-final / 1.8.0-for 0.07 |
| 7 | HyperOS主题破解 | `com.fuck.HyperOSTheme` | 1.0 |
| 8-9 | PersonalizeHyperTheme | `com.tangjin.personalizehyper.theme` | 2.0.0 / 2.0.1 |
| 10 | MIUI扩展 | `com.miui.android.t` | 1.1 |

---

## 一、谱系总览

这10个模块看似来源各异，实际上只有**4条独立的代码线**：

| 家族 | 成员 | 识别依据 |
|---|---|---|
| **A. yuk原版** | v1.1 → v1.8.0 共6个版本 | 包名`com.yuk.fuckMiuiThemeManager`，日志tag `FuckThemeManager`，Kotlin编写 |
| **B. yuk换皮** | HyperOS主题破解 1.0 | 与v1.8.0逻辑同源：同为85个类、相同DexKit查询串、相同的`FuckThemeManager`日志tag，仅改包名重签名 |
| **C. yuk深度增强** | PersonalizeHyperTheme 2.0.0 / 2.0.1 | 沿用yuk的Hook目标、日志tag和大图标授权文件机制，额外增加副屏兼容与日志UI |
| **D. 独立项目** | MIUI扩展 1.1 | `com.miui.android.t`，Java编写，7个xposed_init入口，功能远超主题破解 |

签名链也印证了这个脉络：yuk 1.1-1.2（feac90b9）→ 1.3-1.4（a997df35，换证书重签）→ 1.7-1.8（be9f8629）；HyperOS主题破解（492682f8）；PersonalizeHyperTheme两版同证书（a40da80a）；MIUI扩展（6c1dba06）。

---

## 二、yuk家族：一部对抗混淆升级的演化史

yuk系列是这批模块的主干，它四年间走过的路，基本就是小米主题商店加固史的镜像。

### v1.1（19个类）：直连Hook的黄金时代

适配的是主题壁纸4.0.x——那个年代业务类还完全没混淆，直接按名字Hook就行。设计分两层：

**第一层：`initZygote`全局DRM Hook**。`miui.drm.DrmManager`位于boot classpath，影响所有进程。模块里内置了一个编译期存根类`miui.drm.DrmManager`，运行时靠父类加载器委托机制Hook到framework里的真实类：

| 目标方法 | 手法 | 注入结果 |
|---|---|---|
| `isLegal()` | MethodReplacement | `DRM_SUCCESS` |
| `isPermanentRights()` | MethodReplacement | `true` |
| `isRightsFileLegal()` | MethodReplacement | `true` |
| `isSupportAd()` | MethodReplacement | `false` |
| `setSupportAd(Context, boolean)` | before改参 `args[1]=false` | 原方法仍会执行，只是参数被改 |

**第二层：`handleLoadPackage`业务Hook**（仅`com.android.thememanager`进程）：

| 类.方法 | 手法 |
|---|---|
| `Resource.isAuthorizedResource()` | before `setResult(true)` |
| `Resource.isProductBought()` | before `setResult(true)` |
| `Resource.setProductBought(boolean)` | before改参 `args[0]=true` |
| `Resource.setProductPrice(int)` / `getProductPrice()` | before改参`0` / 返回`0` |
| `ResourceOnlineProperties` 同样4个方法 | 同上 |
| `OnlineResourceDetail.toResource()` | after反射写`thisObject`字段：`productPrice=0`、`bought=true` |

注意`toResource`的after写的是**详情对象本身**，而此时方法返回的Resource已经从详情字段复制完毕——这个细节后面还会提到，它是理解yuk后期改法的钥匙。

### v1.2（20个类）：加上去广告

只多了一个Hook：`AdInfoResponse.isAdValid(AdInfo)` → before `setResult(false)`。另外DRM回调从MethodReplacement统一改成了before+setResult常量的写法，效果等价（before里setResult同样会跳过原方法），只是实现风格变了。

### v1.3（24个类）：Kotlin重写，回调全部改为after

这个版本用Kotlin/Intrinsics整个重写了一遍，行为上有个容易被误读的变化：**所有回调都改成了after风格**。`Resource.isProductBought`注册了两个一模一样的after回调（都`setResult(true)`），乍看像"before+after双保险"，实际只是重复注册，并没有before。

新增了两个UI层的处理：

- `BaseAdViewHolder(View, RecommendListViewAdapter)`构造器after → 广告View宽高设为0并`setVisibility(GONE)`
- `DiscountPriceView.f(int)` after → 反射取字段`b`（TextView），直接`setText("免费")`

`toResource`的after在`productPrice=0`、`bought=true`之外新增写入`originPrice=0`。此外开始使用`findClassIfExists`容错。值得一提的是，`setProductBought`/`setProductPrice`这两个setter的改参也放在了after里——原方法已经执行完，此时改args对本次调用没有任何作用，属于无害的代码瑕疵。

### v1.4（24个类）：广告处理收窄

v1.3把所有`BaseAdViewHolder`子类一网打尽的做法会误伤正常条目。v1.4把广告隐藏重构为`hook(Class, Class)`辅助方法，只针对3个具体广告类的构造器下手：`PureAdBannerViewHolder`、`SelfFontItemAdViewHolder`、`SelfRingtoneItemAdViewHolder`。

### v1.7-final（106个类）：为4.2.x混淆时代做的彻底重写

这是变化最大的一个版本。引入了**EZXHelper**和**DexKit**两个库，放弃了`initZygote`常驻Hook，改为按包名sparse-switch分发的**4包架构**（thememanager / personalassistant / miui.home / android）。

**android包（system_server）——"瞬时Hook"设计**：

```
ThemeReceiver.validateTheme
 ├─ before: 动态 hookAllMethods(DrmManager.isLegal) → DRM_SUCCESS，把Unhook列表存入一个共享的ObjectRef
 └─ after : 逐个 unhook() —— 校验一结束立即摘除，系统其余时间DRM照常运行
```

比起v1.1的全局常驻Hook，这个设计大大缩小了影响面。不过共享的Unhook列表没有任何线程隔离和重入保护，`validateTheme`并发执行时前后两个invocation会互相踩踏——是个明显的并发缺口。

**thememanager包**：

- `OnlineResourceDetail.toResource`（findAllMethods匹配所有重载）after → 用EZXHelper的`Args.field`写详情对象`bought=true`
- `DiscountPriceView`：不再依赖混淆前的名字`f`，改为**按签名匹配**`(int,int)void`，before只把`args[1]=0`（第二个价格参数归零）
- `MamlutilKt.themeManagerSupportPaidWidget` after → `false`
- **DexKit** `batchFindMethodsUsingStrings`按字符串特征定位被混淆的方法：
  - `"DrmResult"` ← {`theme`, `ThemeManagerTag`, `/system`} → after `DRM_SUCCESS`（定位DRM业务校验方法）
  - `"LargeIcon"` ← {`apply failed`, `/data/system/theme/large_icons/`, `default_large_icon_product_id`} → before复杂逻辑（定位大图标应用任务）

**大图标授权文件占位**——LargeIcon方法的before回调会遍历`thisObject`的字段找到Resource类型字段，调用`getProductId()`，然后直接在thememanager的私有目录里**伪造.mra授权文件**：

```
/storage/emulated/0/Android/data/com.android.thememanager/
  files/MIUI/theme/.data/rights/theme/<productId>-largeicons.mra
  → createNewFile()   ← 创建一个空文件
```

注意这个机制是yuk 1.7首创的（1.8只是加了mkdirs），并不是PersonalizeHyperTheme的发明。

**personalassistant包（智能助理，11个Hook）**：`themeManagerSupportPaidWidget→false`、`isCanDirectAddMaMl→true`、`isCanDownload→true`、`isCanAutoDownloadMaMl→true`、`PickerDetailResponse`的`isPay→false`/`isBought→true`、`PickerDetailResponseWrapper`同上、`shouldCheckMamlBoughtState→false`、`isTargetPositionMamlPayAndDownloading→false`、`checkIsIndependentProcessWidgetForPosition→true`——目标是让付费MAML小组件可下载、可添加。

**home包（新增）**：`themeManagerSupportPaidWidget→false`、`MaMlPendingHostView.isCanAutoStartDownload→true`。

**最重要的删除**：v1.7彻底删掉了`Resource`/`ResourceOnlineProperties`的直连Hook。原因并不是这两个类被混淆了——宿主验证表明它们在4.0.1.4和4.2.6.7里**都保持原名未混淆**。真正的原因在大图标流程：应用大图标前，宿主会用一个临时的、没有productId的Resource探测环境（检查价格非零、购买态false、DRM非成功），任何一项被全局Hook强改，探测就会命中`apply failed`，大图标应用直接失败。所以1.7以后yuk宁可放弃全局数据Hook，改用"toResource写详情对象 + DexKit定点处理DRM和大图标"的窄打击面方案。

### v1.8.0-for 0.07（85个类）：适配4.2.6.7

- EZXHelper升级为finders链式风格，智能助理的11个Hook全部简化成`HookFactory.returnConstant`
- DexKit特征串随宿主代码变化扩充：DrmResult增加`"check rights isLegal:"`，LargeIcon增加`"largeicons"`、`"relativePackageList is empty"`
- `toResource` after → 只写`bought=true`，不再碰价格字段
- `.mra`文件创建前增加了父目录检查（`getParentFile()`不存在则`mkdirs()`）

---

## 三、HyperOS主题破解 1.0：一次无创新的搬运

与yuk v1.8.0的核心Hook逻辑**完全一致**：同为85个类，4包分发、相同的DexKit查询串、相同的瞬时validateTheme Hook，甚至连日志tag都还叫`FuckThemeManager`。差异只有三处：包名改成`com.fuck.HyperOSTheme`、换了签名证书（V1+V2）、minSdk 29 / targetSdk 34。

结论：纯换皮重分发版本，没有任何自己的东西。

---

## 四、PersonalizeHyperTheme 2.0.0 / 2.0.1：yuk的深度增强

这条线基于yuk 1.8.0改造，加入了自己的独立思考。两个版本都经过了R8混淆合并（工具类被并进`kotlin.ResultKt`之类的位置）。

### 2.0.0的三个重点

**① 继承而非发明**：大图标`.mra`授权文件占位机制直接沿用yuk 1.7/1.8的写法。要注意空文件只满足存在性判断，真实DRM校验还需要XML解析加RSA验签，所以它必须配合DexKit定位的DRM Hook一起用，单靠文件占位是不够的。

**② 副屏（rear screen）兼容**——这是它真正的创新。折叠屏外屏/背屏的MAML组件需要真实DRM才能工作，一刀切的Hook会让副屏坏掉。它用两层检测解决：

- `toResource` after：读取详情对象的`category`字段，包含`"rear"`时**跳过**强制`bought=true`，并把静态变量`rearActiveUntil`设为`now + 60000`（打开一个60秒的"真实DRM窗口"）
- DRM Hook（after）：落在`rearActiveUntil`时间窗内就放行真实DRM结果，窗口外才强制`DRM_SUCCESS`
- 智能助理的`shouldCheckMamlBoughtState`/`isTargetPositionMamlPayAndDownloading`改用带调用栈扫描的智能回调：调用栈里出现`"rearscreen"`字样就放行原逻辑，否则强制`false`

**③ 工程化**：Compose UI的日志查看器（LogActivity）、更新弹窗、LogHelper、用`firstOrNull`容错替代抛异常。日志读取和"重启宿主"功能通过执行`am force-stop`等shell命令实现——**依赖root**。

### 2.0.1的现代化重构

- **迁移到libxposed API 102**：入口改为`Api102Entry extends XposedModule`，声明于`META-INF/xposed/{java_init.list, module.prop, scope.list}`（staticScope静态作用域、`autoHotReload=false`并附注释说明热重载的坑）
- 生命周期分发：`onModuleLoaded`初始化 → `onPackageReady`分发hookHome/hookThemeManager/hookPersonalAssistant → `onSystemServerStarting`挂框架层Hook
- Hook写法改为`HookBuilder.intercept(Chain)`链式拦截器
- `toResource` after升级为手动反射沿类层级找`category`/`bought`字段（2.0.0的`getObjectField`是单级的），`"rear"`识别与60秒窗口机制原样沿用
- `libdexkit.so`直接打包双ABI，同时带上了Compose的`libandroidx.graphics.path.so`

---

## 五、MIUI扩展 1.1：功能多元的Java旁支

和yuk没有任何血缘关系。xposed_init里并列注册**7个入口类**，每个Hook类自己在`handleLoadPackage`里判断包名——新旧两代Hook类（ThemeHook/Theme13Hook、PersonalassistantHook/Personalassistant13Hook）**同时加载、同时尝试**，不存在按宿主版本自动选择的逻辑，类找不到就静默失败。

**ThemeHook（旧版目标组合）**：
- `OnlineResourceDetailPresenter.m`（混淆名）after → `true`。但这个完整类名在4.0.1.4和4.2.6.7中都不存在，这个Hook从未真正生效过
- `AdInfoResponse.isAdValid` → `false`、`AdInfo.isVideoAd` → `false`（去广告，有效）

**Theme13Hook（MIUI13+/HyperOS）**——购买态策略与yuk**方向相反**：

| 方法 | MIUI扩展 | yuk |
|---|---|---|
| `Resource.isAuthorizedResource` | before `false` | before `true` |
| `Resource.isProductBought` | before `false` | before `true` |
| `OnlineResourceDetail.toResource` after | 写详情对象`bought=true` | 相同 |

这里的`false`该怎么理解？宿主源码给出了答案：`Resource.isAuthorizedResource()`的方法体就是直接调用`isProductBought()`。所以`false`的含义是"**未购买/未授权**"，并不是"非付费资源"；它和`toResource`写入的`bought=true`构成两个不同层面的状态。yuk走"伪装已购"路线，MIUI扩展走"伪装未购、同时让详情对象标记已购"的路线，两条路殊途同归——都能让商店跳过真正的授权流程。

**PersonalassistantHook（旧）**：`themeManagerSupportPaidWidget→false`，外加独有的`SuitEntity.isShowPayLogo()→false`（隐藏付费角标）。

**Personalassistant13Hook（13个Hook）**：包含与yuk同目标的10个业务Hook，另有3个**只记日志不改行为**的回调：`PickerDetailViewModel.startMaMlDownload`、`PickerDetailViewModel.mamlDownloading`、`PickerDetailActionController.transToMaMlEditor`。`isCanDirectAddMaMl(int)`三个智能助理宿主都存在，yuk按名匹配同样命中，算不上它的独有适配。

**MiHomeContentHook**：home进程的`themeManagerSupportPaidWidget(Context)→false`。

**MulitiWindowsHook（与破解无关的系统增强）**——android包（system_server）：
- `MiuiFreeFormManagerService.shouldStopStartFreeform` → `false`
- `MiuiMultiWindowUtils.multiFreeFormSupported` → `true`
- `MiuiFreeFormStackDisplayStrategy.getMaxMiuiFreeFormStackCount`（两处）→ `0x100`（小窗数量上限提到256），另有`...ForFlashBack`等变体

**BaseResHook（XResources资源注入）**：向`com.miui.home`、`com.android.systemui`、`miui.systemui.plugin`注入模块自带的drawable（妙享层、应用流转按钮等），用于系统UI美化——这是唯一不靠方法Hook、靠资源替换实现功能的入口。

---

## 六、横向对比总表

| 维度 | yuk 1.1-1.4 | yuk 1.7/1.8 | HyperOS主题破解 | PHT 2.0.0 | PHT 2.0.1 | MIUI扩展 |
|---|---|---|---|---|---|---|
| 目标包 | thememanager | thememanager、personalassistant、home、android | thememanager、personalassistant、home、android | thememanager、personalassistant、home、android | thememanager、personalassistant、home、android | thememanager、personalassistant、home、systemui（资源注入）、android（多窗） |
| 语言/API | Kotlin、传统Xposed | Kotlin、EZXHelper、DexKit、传统Xposed | Kotlin、EZXHelper、DexKit、传统Xposed | Kotlin、EZXHelper、DexKit、Compose UI、传统Xposed | Kotlin、EZXHelper、DexKit、libxposed API 102 | Java、传统Xposed |
| DRM处理 | initZygote全局常驻Hook | validateTheme瞬时Hook、DexKit定点DRM、空`.mra`文件占位 | validateTheme瞬时Hook、DexKit定点DRM、空`.mra`文件占位 | validateTheme瞬时Hook、DexKit定点DRM、空`.mra`文件占位、副屏60秒时间窗 | validateTheme瞬时Hook、DexKit定点DRM、空`.mra`文件占位、副屏60秒时间窗（Chain拦截器实现） | 无DRM Hook |
| 抗混淆 | 无（直连类名） | DexKit字符串定位 | DexKit字符串定位 | DexKit字符串定位 | DexKit字符串定位 | 无（手动指定混淆名`m`） |
| 破解策略 | 伪装已购、价格归零 | 伪装已购、详情对象bought=true | 伪装已购、详情对象bought=true | 伪装已购、详情对象bought=true（副屏跳过） | 伪装已购、详情对象bought=true（副屏跳过） | 伪装未购、详情对象bought=true |
| 副屏兼容 | 无 | 无 | 无 | 调用栈检测、category检测、60秒DRM时间窗 | 调用栈检测、category检测、60秒DRM时间窗（category/bought改为手动反射沿类层级查找） | 无 |
| 去广告 | isAdValid判定无效（1.2起）、广告视图隐藏（1.3起） | 无（已移除） | 无（已移除） | 无（已移除） | 无（已移除） | isAdValid判定无效、isVideoAd判定无效 |
| 独有功能 | — | MAML付费组件解锁、大图标破解 | —（与1.8完全一致） | Compose日志UI、更新弹窗、root重启宿主 | 模块热重载陷阱说明、META-INF/xposed声明、libdexkit双ABI打包 | 256小窗上限、付费角标隐藏、XResources资源注入 |

---

## 七、实现细节与陷阱

如果要在这些模块的基础上做自己的实现，以下几点是最值得吸取的教训：

**1. `toResource`的after写`thisObject`改不了本次返回值。**
after回调执行时，返回的Resource已经从详情对象复制完字段了。写`thisObject.bought=true`影响的只是详情对象后续被再次读取时的值。想改返回值就得处理`getResult()`——而这又会撞上大图标检测（见下条），需要格外小心。

**2. 大图标检测是叠加式方案的头号兼容点。**
宿主在应用大图标前用临时Resource探测（无productId、价格非零、购买态false、DRM非成功），强改任何一项都会触发`apply failed`。yuk 1.7+删除全局数据Hook、只处理有真实标识的资源，就是为了绕开它。早期直连Hook和后期大图标方案**不能无条件叠加**。

**3. 空`.mra`文件≠有效授权。**
文件占位只过存在性判断，真实DRM还有XML解析+RSA验签。业务DRM（4.2.x中混淆为`controller.online.zy`）和framework DRM（`DrmManager`）是两层独立的处理，需要分别对待。

**4. after里改参数是无效操作。**
v1.3的`setProductBought`/`setProductPrice`在after中改args，原方法早已执行完毕，改了也白改。要改参数必须在before里做。

**5. 瞬时Hook需要并发保护。**
yuk 1.7的validateTheme瞬时Hook用单个共享ObjectRef存放Unhook列表，没有线程隔离和重入处理。`validateTheme`并发时，前一个invocation的after可能把后一个正在使用的Hook摘掉。正确的做法是按线程（或引用计数）隔离临时Hook的挂载窗口。

**6. 签名匹配比名字匹配稳，特征匹配比签名匹配稳。**
`DiscountPriceView`的入口方法名从4.0的`toq`变成4.2的`setPrice`，但`(int,int)void`签名没变；DRM和大图标这类被混淆的方法则只能靠字符串特征定位。按"类名+签名+特征"三级判定，比按宿主版本号套用整组Hook可靠得多。另外要进入价格控件的"免费"显示分支需要**原价和现价同时归零**——yuk 1.7只把`args[1]`清零是不彻底的。

---

## 八、总结

这10个模块本质上是同一套破解思路——Hook掉"购买态 / 价格 / DRM校验"三层判断——在四年间对抗小米混淆升级的完整演化史：

- **v1.1-1.4**：业务类未混淆，全局直连Hook简单粗暴；
- **v1.7-1.8**：DRM业务类被混淆，转向DexKit字符串定位+瞬时Hook+`.mra`文件占位，并为了绕开大图标检测主动收窄打击面；
- **HyperOS主题破解**：一次没有任何创新的换皮搬运；
- **PersonalizeHyperTheme**：在yuk肩膀上补齐了副屏兼容（栈检测+category+60秒DRM窗口）和现代API（libxposed 102）；
- **MIUI扩展**：唯一的独立旁支，破解策略与yuk相反（伪装未购而非伪装已购），还捎带了多窗口、资源注入等与主题无关的系统增强。

对于要自研模块的场景，最有参考价值的是三条主线各自的教训：yuk证明了"窄打击面+特征定位"是长期存活的必要条件；PersonalizeHyperTheme证明了副屏场景必须给真实DRM留窗口；MIUI扩展则证明了"伪装未购"是和"伪装已购"并行有效的另一条路。
