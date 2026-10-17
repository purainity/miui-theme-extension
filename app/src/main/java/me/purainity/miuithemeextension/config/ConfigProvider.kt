package me.purainity.miuithemeextension.config

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Process

/** 配置读取侧（8.6）：向作用域内的宿主导出只读配置快照 */
class ConfigProvider : ContentProvider() {
    private val app get() = requireNotNull(context)
    override fun onCreate() = true

    /** 调用方的宿主身份（8.6）：system UID 视为 android 宿主，其余按调用方 UID 反查包名并与四宿主求交 */
    private fun callerHosts(): Set<String> = if (Binder.getCallingUid() == Process.SYSTEM_UID) setOf("android")
        else app.packageManager.getPackagesForUid(Binder.getCallingUid()).orEmpty().toSet().intersect(ConfigContract.HOSTS)
    // 仅本应用与作用域宿主可读
    private fun checkReader() {
        if (Binder.getCallingUid() != Process.myUid() && callerHosts().isEmpty()) throw SecurityException("Target host required")
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        checkReader()
        return when (uri) {
            ConfigContract.CONFIG_URI -> {
                // 单行快照；挂上通知 URI，宿主侧 ContentObserver 据此感知变更（8.6）
                val config = ConfigStore(app).read()
                MatrixCursor(arrayOf("revision") + Setting.entries.map { it.key }).apply {
                    addRow((listOf<Any>(config.version) + Setting.entries.map { if (config[it]) 1 else 0 }).toTypedArray())
                    setNotificationUri(app.contentResolver, uri)
                }
            }
            else -> throw IllegalArgumentException("Unknown URI")
        }
    }
    override fun getType(uri: Uri) = "vnd.android.cursor.item/vnd.${ConfigContract.AUTHORITY}"

    // 只读提供器，insert/update/delete 一律拒绝（8.6）
    override fun insert(uri: Uri, values: ContentValues?): Uri = throw SecurityException("Read only")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = throw SecurityException("Read only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = throw SecurityException("Read only")
}
