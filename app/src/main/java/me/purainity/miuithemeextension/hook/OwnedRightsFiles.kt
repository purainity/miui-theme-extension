package me.purainity.miuithemeextension.hook

import me.purainity.miuithemeextension.config.ResourcePolicy
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.Properties

/**
 * 大图标授权占位文件（8.5）：空文件只满足存在性检查，真实授权仍依赖 8.3 的 DRM 处理
 * 归属管理只登记本模块新建的空文件；宿主已有的、或随后被写入内容的授权文件一律归属宿主
 */
class OwnedRightsFiles(private val folder: File, private val journal: File) {
    // 进程内 synchronized 与文件锁双层互斥（8.5），并发操作日志时串行化
    private fun <T> locked(action: (Properties) -> T): T = synchronized(LOCK) {
        journal.parentFile?.mkdirs()
        RandomAccessFile(File(journal.path + ".lock"), "rw").use { lockFile ->
            lockFile.channel.lock().use {
                val data = Properties()
                if (journal.isFile) journal.inputStream().use { input -> data.load(input) }
                action(data)
            }
        }
    }
    // 临时文件 + fsync + 原子重命名（8.5），中途失败不会损坏旧日志
    private fun save(data: Properties) {
        val pending = File(journal.path + ".tmp")
        FileOutputStream(pending).use { output -> data.store(output, null); output.fd.sync() }
        check(pending.renameTo(journal)) { "Cannot save rights journal" }
    }
    /** 按商品 id 计算授权文件路径：先过 safeProductId 校验（8.5），再验证路径未逃出目标目录 */
    private fun target(id: String): File {
        require(ResourcePolicy.safeProductId(id))
        val root = folder.canonicalFile
        val file = File(root, "$id-largeicons.mra")
        require(file.canonicalFile.parentFile == root && file.canonicalFile == file.absoluteFile)
        return file
    }
    /** 为大图标新建空授权占位文件（8.5）并登记归属；已存在则跳过，登记失败回滚不留半成品 */
    fun prepare(id: String, enabled: () -> Boolean): Boolean = locked { data ->
        if (!enabled()) return@locked false
        val file = target(id)
        if (!folder.isDirectory) check(folder.mkdirs() || folder.isDirectory)
        if (!file.createNewFile()) return@locked false
        val stamp = file.lastModified()
        val key = file.canonicalPath
        data.setProperty(key, stamp.toString())
        try { save(data) } catch (e: Exception) {
            if (file.length() == 0L && file.lastModified() == stamp) file.delete()
            throw e
        }
        true
    }
    /** 清理仍归属本模块且仍为空的授权文件（8.5）；已被写入内容或已删除的条目仅从日志移除 */
    fun cleanup() = locked { data ->
        val root = folder.canonicalFile
        data.stringPropertyNames().toList().forEach { path ->
            val file = File(path)
            if (file.canonicalFile.parentFile != root || file.canonicalFile != file.absoluteFile) return@forEach
            val stamp = data.getProperty(path).toLongOrNull()
            val owned = file.isFile && file.length() == 0L && stamp != null && file.lastModified() == stamp
            if (!file.exists() || !owned || file.delete()) data.remove(path)
        }
        save(data)
    }
    companion object { private val LOCK = Any() }
}
