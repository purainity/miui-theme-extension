package me.purainity.miuithemeextension.hook

import me.purainity.miuithemeextension.config.ResourcePolicy
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.Properties

/** 只登记本模块新建的空文件；宿主已有的、或随后被写入内容的授权文件一律归属宿主 */
class OwnedRightsFiles(private val folder: File, private val journal: File) {
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
    private fun save(data: Properties) {
        val pending = File(journal.path + ".tmp")
        FileOutputStream(pending).use { output -> data.store(output, null); output.fd.sync() }
        check(pending.renameTo(journal)) { "Cannot save rights journal" }
    }
    private fun target(id: String): File {
        require(ResourcePolicy.safeProductId(id))
        val root = folder.canonicalFile
        val file = File(root, "$id-largeicons.mra")
        require(file.canonicalFile.parentFile == root && file.canonicalFile == file.absoluteFile)
        return file
    }
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
