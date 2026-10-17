package me.purainity.miuithemeextension.hook

import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.core.content.edit
import de.robv.android.xposed.XC_MethodHook
import me.purainity.miuithemeextension.config.*
import me.purainity.miuithemeextension.dex.*
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.zip.ZipFile

/** 主题壁纸进程的 Hook（6.1）：购买态与价格改写、付费标识与广告隐藏、大图标 feed 与混淆方法识别 */
class ThemeHooks(private val hooks: Hooks) {
    private val client = hooks.client
    // 身份标签缓存：对象与其 onlineProperties 载体各自成键（8.1 判定顺序第 1 步）
    private val tags = WeakIdentityMap<ResourceKind>()
    private val mutations = ReversibleMutationJournal()
    // 原值读取区间内的资源栈：区间内对象的改写全部旁路（8.6.1）
    private val originalRead = ThreadLocal<ArrayList<Any>>()
    // 任务栈：本线程执行中的大图标任务持有的资源（8.1 判定顺序第 2 步）
    private val tasks: ThreadLocal<ArrayList<Any>> = object : ThreadLocal<ArrayList<Any>>() {
        override fun initialValue(): ArrayList<Any> = ArrayList()
    }
    private var rights: OwnedRightsFiles? = null
    // Resource 的商品字段经其 onlineProperties 载体间接读取，其余对象自身即载体（8.2）
    private fun properties(obj: Any): Any = if (obj.javaClass.name == RESOURCE) Hooks.read(obj, "onlineProperties") ?: obj else obj
    // 读商品 productId
    private fun id(obj: Any): String? = Hooks.read(properties(obj), "productId") as? String
    /** 资源类别判定（8.1）：标签缓存优先，进行中的大图标任务次之，最后按 category 与 productType 字符串匹配 */
    private fun kind(obj: Any): ResourceKind {
        tags[obj]?.let { return it }
        val props = properties(obj)
        tags[props]?.let { return it }
        if (requireNotNull(tasks.get()).any { it === obj || properties(it) === props }) return ResourceKind.LARGE_ICON
        val category = Hooks.read(props, "category") as? String ?: ""
        val type = Hooks.read(obj, "productType") as? String ?: ""
        val categoryKind = ResourcePolicy.kind(category)
        val explicit = ResourcePolicy.kind(type)
        // 两字段解析结果的合成：大图标/MAML 优先，其次 category 非未知取 category，否则取 productType（8.1）
        return when {
            categoryKind in setOf(ResourceKind.LARGE_ICON, ResourceKind.MAML) -> categoryKind
            explicit in setOf(ResourceKind.LARGE_ICON, ResourceKind.MAML) -> explicit
            categoryKind != ResourceKind.UNSUPPORTED -> categoryKind
            else -> explicit
        }
    }
    // 该对象是否处于原值读取区间内
    private fun readingOriginal(obj: Any): Boolean = originalRead.get()?.any { it === obj } == true
    /** 原值读取区间：区间内该资源的购买态与价格改写全部旁路，快照与回滚读写真实值（8.6.1） */
    private fun <T> withOriginalResource(resource: Any, block: () -> T): T {
        val stack = originalRead.get() ?: ArrayList<Any>().also { originalRead.set(it) }
        stack += resource
        return try { block() } finally {
            val index = stack.indexOfLast { it === resource }
            if (index >= 0) stack.removeAt(index)
            if (stack.isEmpty()) originalRead.remove()
        }
    }
    /** 数据层注入判定（8.2）：productId 非空白、非原值读取区间且类别对应开关开启 */
    private fun allowed(obj: Any?): Boolean = obj != null && !readingOriginal(obj) &&
        ResourcePolicy.allows(client.config, id(obj), kind(obj))
    /** 本地导入主题（无 productId 的普通资源）在第三方主题开关下放行 DRM；不触碰价格与购买态 */
    private fun thirdParty(obj: Any?): Boolean = obj != null && !readingOriginal(obj) &&
        client.config.enabled(Setting.THIRD_THEME) &&
        kind(obj) == ResourceKind.STANDARD && id(obj).isNullOrBlank()
    // 对象与其属性载体同时打标签
    // 对象与其属性载体同时打标签（8.1：两者均作为键命中）
    private fun tag(obj: Any, value: ResourceKind) { tags[obj] = value; tags[properties(obj)] = value }

    /** 保存对象的字段原值，仅首次改写前捕获（8.6.1）。宿主会复用详情页与 feed 的内存对象，关闭开关时必须逐项还原 */
    private fun rememberFields(target: Any, productId: String, kind: ResourceKind, names: List<String>) {
        val original = names.mapNotNull { name ->
            Hooks.fields(target.javaClass).firstOrNull { it.name == name && !Modifier.isStatic(it.modifiers) }?.let { field ->
                field.isAccessible = true
                FieldValue(field, field.get(target))
            }
        }
        if (original.isNotEmpty()) mutations.record(target, productId, kind) { target ->
            original.forEach { value ->
                value.field.isAccessible = true
                value.field.set(target, value.value)
            }
        }
    }

    /** 详情对象的原值快照：详情页复用这些对象（8.6.1） */
    private fun rememberDetail(detail: Any, productId: String, kind: ResourceKind) {
        rememberFields(detail, productId, kind, listOf("bought", "productPrice", "originPrice"))
    }

    /** 保存 Resource 的原购买态和价格；使用公开 getter/setter，避免依赖宿主私有字段名 */
    private fun rememberResource(resource: Any, productId: String, kind: ResourceKind) {
        val bought = try { withOriginalResource(resource) { Hooks.call(resource, "isProductBought") as? Boolean } } catch (_: Exception) { null }
        val price = try { withOriginalResource(resource) { Hooks.call(resource, "getProductPrice") as? Int } } catch (_: Exception) { null }
        if (bought == null && price == null) return
        mutations.record(resource, productId, kind) { target ->
            withOriginalResource(target) {
                if (bought != null) target.javaClass.getMethod("setProductBought", Boolean::class.javaPrimitiveType).invoke(target, bought)
                if (price != null) target.javaClass.getMethod("setProductPrice", Int::class.javaPrimitiveType).invoke(target, price)
            }
        }
    }

    private data class FieldValue(val field: Field, val value: Any?)

    /** 安装主题壁纸进程的全部 Hook（6.1）；混淆目标经 DEX 扫描定位，需宿主上下文与 APK 路径（8.4） */
    fun install(source: String?, splits: Array<out String>) {
        resourceHooks(RESOURCE)
        resourceHooks(PROPERTIES)
        hooks.methods(DETAIL) { it.name == "toResource" && it.returnType.name == RESOURCE }.forEach { target ->
            hooks.install(target, after = { param ->
                val detail = param.thisObject ?: return@install
                if (!param.hasThrowable() && allowed(detail)) {
                    // 为详情对象与返回的 Resource 分别登记类别标签（6.1 #5）
                    val kind = kind(detail)
                    val productId = id(detail) ?: return@install
                    tag(detail, kind)
                    val resource = param.result
                    if (resource != null) tag(resource, kind)
                    rememberDetail(detail, productId, kind)
                    listOf("bought" to true, "productPrice" to 0, "originPrice" to 0).forEach { (name, value) ->
                        try { Hooks.write(detail, name, value) } catch (e: Exception) { HookLog.error("detail/$name", e) }
                    }
                    if (resource != null) {
                        rememberResource(resource, productId, kind)
                        try { resource.javaClass.getMethod("setProductBought", Boolean::class.javaPrimitiveType).invoke(resource, true) }
                        catch (e: Exception) { HookLog.error("detail/bought", e) }
                        try { resource.javaClass.getMethod("setProductPrice", Int::class.javaPrimitiveType).invoke(resource, 0) }
                        catch (e: Exception) { HookLog.error("detail/price", e) }
                    }
                }
            })
        }
        hooks.methods(RESOURCE) { it.name == "clone" && it.parameterTypes.size == 0 && it.returnType.name == RESOURCE }.forEach {
            hooks.install(it, after = { param ->
                val clone = param.result
                val original = param.thisObject
                if (!param.hasThrowable() && clone != null && original != null) {
                    tags[original]?.let { value -> tag(clone, value) }
                }
            })
        }
        hooks.methods("com.android.thememanager.basemodule.views.DiscountPriceView") {
            Modifier.isPublic(it.modifiers) && it.returnType == Void.TYPE &&
                it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType))
        }.forEach { hooks.install(it, before = { param ->
            if (client.config.enabled(Setting.PAID_BADGES)) { param.args[0] = 0; param.args[1] = 0 }
        }) }
        ads()
        largeIconFeed()
        WidgetHooks.maml(hooks)
        mamlWallpaper()
        // 旧 presenter 类名在两个基线 APK 中均不存在，不做投机性的布尔 Hook 挂载
        // 授权占位文件目录与归属日志的落位见 8.5
        client.onAttached += { context ->
            context.getExternalFilesDir(null)?.let { root ->
                rights = OwnedRightsFiles(File(root, "MIUI/theme/.data/rights/theme"), File(context.filesDir, ".miui-theme-extension-rights"))
            }
            discover(listOfNotNull(source) + splits)
        }
        // 配置变化时清理授权占位文件，并回滚不再被允许的改写（8.5、8.6.1）
        client.listeners += { config ->
            cleanupRights()
            mutations.restoreDisabled(config)
        }
    }
    /** 大图标开关关闭时清理本模块创建且仍为空的授权占位文件（8.5） */
    private fun cleanupRights() {
        if (!client.config.enabled(Setting.LARGE_ICONS)) try { rights?.cleanup() }
        catch (e: Exception) { HookLog.error("rights cleanup", e) }
    }
    /** 购买态与价格的读写方法常量改写（6.1 #1–#4）：读返回已购与零价，写参数强制已购与零价 */
    private fun resourceHooks(name: String) {
        hooks.methods(name) { method -> when (method.name) {
            "isProductBought", "isAuthorizedResource" -> method.parameterTypes.size == 0 && method.returnType == Boolean::class.javaPrimitiveType
            "getProductPrice" -> method.parameterTypes.size == 0 && method.returnType == Int::class.javaPrimitiveType
            "setProductBought" -> method.returnType == Void.TYPE && method.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType))
            "setProductPrice" -> method.returnType == Void.TYPE && method.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
            else -> false
        } }.forEach { method ->
            hooks.install(method, before = { param ->
                if (allowed(param.thisObject)) {
                    when (method.name) {
                        "isProductBought", "isAuthorizedResource" -> param.result = true
                        "getProductPrice" -> param.result = 0
                        "setProductBought" -> param.args[0] = true
                        "setProductPrice" -> param.args[0] = 0
                    }
                }
            })
        }
    }
    /** 广告隐藏（6.1 #7–#12）：广告有效性判定置否，推荐流广告卡片构造后压为 0 尺寸并隐藏 */
    private fun ads() {
        hooks.constant("com.android.thememanager.basemodule.ad.model.AdInfoResponse", "isAdValid", false, Setting.ADS,
            signature = { it.parameterTypes.size == 1 && it.parameterTypes[0].name == "com.android.thememanager.basemodule.ad.model.AdInfo" })
        hooks.constant("com.android.thememanager.basemodule.ad.model.AdInfo", "isVideoAd", false, Setting.ADS)
        val prefix = "com.android.thememanager.recommend.view.listview.viewholder."
        val names = setOf("PureAdBannerViewHolder", "SelfFontItemAdViewHolder", "SelfRingtoneItemAdViewHolder")
        val covered = HashSet<String>()
        // 把广告视图尺寸压为 0 并彻底隐藏
        fun hide(param: XC_MethodHook.MethodHookParam) {
            if (!client.config.enabled(Setting.ADS)) return
            val view = param.args.firstOrNull() as? View ?: return
            val params = view.layoutParams
            if (params != null) { params.width = 0; params.height = 0; view.layoutParams = params }
            else when (view.parent) {
                is android.widget.FrameLayout -> view.layoutParams = android.widget.FrameLayout.LayoutParams(0, 0)
                is android.widget.LinearLayout -> view.layoutParams = android.widget.LinearLayout.LayoutParams(0, 0)
                is android.widget.RelativeLayout -> view.layoutParams = android.widget.RelativeLayout.LayoutParams(0, 0)
            }
            view.visibility = View.GONE
        }
        names.forEach { name ->
            hooks.find(prefix + name)?.declaredConstructors?.filter {
                it.parameterTypes.firstOrNull() == View::class.java && it.parameterTypes.size in 2..3 &&
                    it.parameterTypes[1].simpleName == "RecommendListViewAdapter" &&
                    (it.parameterTypes.size == 2 || it.parameterTypes[2] == Int::class.javaPrimitiveType)
            }?.forEach { if (hooks.install(it, after = { param -> if (!param.hasThrowable()) hide(param) }) != null) covered += name }
        }
        // 兜底 Hook（#12）仅当 #9–#11 三类都没挂上时才安装；构造器同样校验次参为 RecommendListViewAdapter
        if (covered.isEmpty()) hooks.find(prefix + "BaseAdViewHolder")?.declaredConstructors?.filter {
            it.parameterTypes.size == 2 && it.parameterTypes[0] == View::class.java &&
                it.parameterTypes[1].simpleName == "RecommendListViewAdapter"
        }?.forEach { hooks.install(it, after = { param ->
            val holder = param.thisObject
            if (!param.hasThrowable() && holder != null && holder.javaClass.simpleName in names &&
                holder.javaClass.simpleName !in covered) hide(param)
        }) }
    }
    /**
     * 大图标 feed 数据层改写（6.1 #18–#19）。
     * 推荐流卡片价格直读 `UIProduct`/`LargeIconElement`，不经过 Resource 管线（Resource Hook 天然覆盖不到），故单独改写。
     * 价格 ≤0 时宿主格式化自带“免费”分支
     */
    private fun largeIconFeed() {
        // #18 选择器与购买入口的元素工厂：明名类，按重载签名匹配四个 create 中的 (UIProduct, int) 版本
        hooks.methods(ELEMENT) { method ->
            method.name == "create" && Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.size == 2 && method.parameterTypes[0].name == PRODUCT &&
                method.parameterTypes[1] == Int::class.javaPrimitiveType && method.returnType.name == ELEMENT
        }.forEach { method ->
            hooks.install(method, after = { param ->
                val element = param.result ?: return@install
                if (!param.hasThrowable()) zeroElement(element)
            })
        }
        // #19 feed 卡片绑定：三个明名 ViewHolder 及其内部类，按参数结构定位商品载体（方法名已混淆，不可按名匹配）
        val productClass = hooks.find(PRODUCT) ?: return
        listOf("LargeIconHomeCardViewHolder", "LargeIconTwoItemCardViewHolder", "LargeIconItemViewHolder")
            .mapNotNull { name -> hooks.find(HOLDER_PREFIX + name) }
            .flatMap { holder -> sequenceOf(holder) + holder.declaredClasses.asSequence() }
            .forEach { holder ->
                holder.declaredMethods.asSequence()
                    .filter { method -> !method.isSynthetic && !method.isBridge && !Modifier.isStatic(method.modifiers) && method.returnType == Void.TYPE }
                    .mapNotNull { method -> FeedBinding.productSlot(method.parameterTypes, productClass)?.let { method to it } }
                    .forEach { (method, slot) ->
                        val (index, field) = slot
                        hooks.install(method, before = { param ->
                            if (!client.config.enabled(Setting.LARGE_ICONS)) return@install
                            val carrier = param.args.getOrNull(index) ?: return@install
                            val product = if (field == null) carrier else {
                                field.isAccessible = true
                                field.get(carrier)
                            }
                            if (product != null && productClass.isInstance(product)) zeroProduct(product)
                        })
                    }
            }
    }
    /** feed 商品（UIProduct）改写（#19）：价格归零 + 已购标记，原值入 journal（8.6.1）；productUuid 为空的探测对象放行（8.2） */
    private fun zeroProduct(product: Any) {
        val productId = Hooks.read(product, "productUuid") as? String
        if (productId.isNullOrBlank()) return
        rememberFields(product, productId, ResourceKind.LARGE_ICON, listOf("currentPriceInCent", "originPriceInCent", "isPurchased"))
        Hooks.write(product, "currentPriceInCent", 0)
        Hooks.write(product, "originPriceInCent", 0)
        Hooks.write(product, "isPurchased", java.lang.Boolean.TRUE)
    }
    /** 选择器元素（LargeIconElement）改写（#18）：价格归零 + 已购标记，原值入 journal（8.6.1） */
    private fun zeroElement(element: Any) {
        if (!client.config.enabled(Setting.LARGE_ICONS)) return
        val productId = Hooks.read(element, "productId") as? String
        if (productId.isNullOrBlank()) return
        rememberFields(element, productId, ResourceKind.LARGE_ICON, listOf("currentPriceInCent", "originPriceInCent", "hasBought"))
        Hooks.write(element, "currentPriceInCent", 0)
        Hooks.write(element, "originPriceInCent", 0)
        Hooks.write(element, "hasBought", true)
    }
    /** MAML 超级壁纸详情（6.1 #17）：修正 bundle 的 classloader 并反序列化资源对象，登记 MAML 标签 */
    @Suppress("DEPRECATION")
    private fun mamlWallpaper() {
        val activity = hooks.find("com.android.thememanager.superwallpaper.activity.MamlSuperWallpaperDetailActivity") ?: return
        hooks.install(Activity::class.java.getDeclaredMethod("onCreate", Bundle::class.java), before = { param ->
            if (activity.isInstance(param.thisObject)) {
                val bundle = (param.thisObject as Activity).intent.getBundleExtra("extra_bundle")
                bundle?.classLoader = hooks.loader
                val resource = bundle?.getSerializable("extra_superwallpaper_info")
                if (resource?.javaClass?.name == RESOURCE) tag(resource, ResourceKind.MAML)
            }
        })
    }
    /** DEX 扫描定位混淆方法（8.4）：DRM 业务方法与大图标应用任务，命中结果按 APK 指纹缓存 */
    private fun discover(paths: List<String>) {
        try {
            val context = client.context ?: return
            val cache = context.getSharedPreferences("miui_theme_extension_dex", 0)
            // 以全部 dex 的名称、CRC 与大小构造 APK 指纹
            val fingerprint = paths.joinToString("|") { path ->
                ZipFile(path).use { zip ->
                    val dexEntries = zip.entries().asSequence().filter { it.name.endsWith(".dex") }
                        .joinToString(",") { "${it.name}:${it.crc}:${it.size}" }
                    "$path:$dexEntries"
                }
            }
            val cached = if (cache.getString("fingerprint", null) == fingerprint) cache.getStringSet("methods", null) else null
            val found: List<Pair<String, DexIndex.MethodRef>> = cached?.map { line ->
                val parts = line.split('|'); require(parts.size == 5)
                parts[0] to DexIndex.MethodRef(parts[1], parts[2], parts[3].split(',').filter { it.isNotEmpty() }, parts[4], 0, emptySet())
            } ?: paths.flatMap { path ->
                DexIndex.scanApk(File(path), "Lcom/android/thememanager/") { dex, e -> HookLog.error("DEX/$dex", e) }
                    .mapNotNull { method -> when {
                        HostDiscovery.drm(method) -> "drm" to method
                        HostDiscovery.largeIcon(method) -> "large" to method
                        else -> null
                    } }
            }
            // 将 DEX 引用回查为运行时 Method 并安装 Hook
            var drmFound = 0; var largeFound = 0
            found.distinctBy { it.second.owner + it.second.name + it.second.parameters }.forEach { (type, ref) ->
                val clazz = hooks.find(ref.owner.removePrefix("L").removeSuffix(";").replace('/', '.')) ?: return@forEach
                val method = clazz.declaredMethods.firstOrNull { target -> target.name == ref.name &&
                    target.parameterTypes.map { Hooks.descriptor(it) } == ref.parameters && Hooks.descriptor(target.returnType) == ref.returns &&
                    !Modifier.isStatic(target.modifiers) } ?: return@forEach
                if (type == "drm") {
                    val success = Hooks.drmSuccess(method)
                    if (hooks.install(method, after = { param ->
                        // 两条放行路径：有 productId 的资源按类别判定（8.2 探测保护）
                        // 无 productId 的普通资源即本地导入主题，在第三方主题开关下放行 DRM（6.1 #14 ②）
                        // 治个别 mtz 在应用时被应用层校验拦截的场景，与框架层窗口共同覆盖“导入-应用-持久”全链路
                        val resource = param.args.firstOrNull()
                        if (!param.hasThrowable() && param.result != null &&
                            (allowed(resource) || thirdParty(resource))) { param.result = success }
                    }) != null) drmFound++
                } else if (Hooks.fields(clazz).any { it.type.name == RESOURCE && !Modifier.isStatic(it.modifiers) }) {
                    if (largeTask(method)) largeFound++
                }
            }
            // 两类目标都命中才写缓存，避免半命中状态被长期缓存
            if (cached == null && drmFound > 0 && largeFound > 0) {
                cache.edit {
                    putString("fingerprint", fingerprint)
                    putStringSet("methods", found.map { (type, ref) -> "$type|${ref.owner}|${ref.name}|${ref.parameters.joinToString(",")}|${ref.returns}" }.toSet())
                }
            }
            HookLog.info("主题壁纸: DRM=$drmFound, 大图标=$largeFound")
        } catch (e: Exception) { HookLog.error("discovery", e) }
    }
    /** 大图标应用任务（6.1 #15）：before 把资源压入任务栈并准备授权占位文件（8.5），after 无条件出栈（含异常路径） */
    private fun largeTask(method: Method): Boolean = hooks.install(method, before = { param ->
        val task = param.thisObject ?: return@install
        val resource = Hooks.fields(task.javaClass).filter { it.type.name == RESOURCE && !Modifier.isStatic(it.modifiers) }
            .map { field -> field.isAccessible = true; field.get(task) }.firstOrNull { it != null }
        if (resource != null) {
            requireNotNull(tasks.get()).add(resource)
            param.setObjectExtra("mte.task", resource)
            val productId = id(resource)
            if (productId != null && ResourcePolicy.safeProductId(productId) && client.config.enabled(Setting.LARGE_ICONS)) {
                rights?.prepare(productId) { client.config.enabled(Setting.LARGE_ICONS) }
            }
        }
    }, after = { param ->
        val resource = param.getObjectExtra("mte.task")
        if (resource != null) {
            val stack = requireNotNull(tasks.get())
            val index = stack.indexOfLast { it === resource }
            if (index >= 0) stack.removeAt(index)
            if (stack.isEmpty()) tasks.remove()
        }
    }) != null

    companion object {
        private const val RESOURCE = "com.android.thememanager.basemodule.resource.model.Resource"
        private const val PROPERTIES = "com.android.thememanager.basemodule.resource.model.ResourceOnlineProperties"
        private const val DETAIL = "com.android.thememanager.detail.theme.model.OnlineResourceDetail"
        private const val ELEMENT = "com.android.thememanager.model.LargeIconElement"
        private const val PRODUCT = "com.android.thememanager.router.recommend.entity.UIProduct"
        private const val HOLDER_PREFIX = "com.android.thememanager.recommend.view.listview.viewholder."
    }
}
