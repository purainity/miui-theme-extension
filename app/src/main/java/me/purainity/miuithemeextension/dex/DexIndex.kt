package me.purainity.miuithemeextension.dex

import java.io.File
import java.util.zip.ZipFile

/** 最小化只读 DEX 索引，将 const-string 指令关联到其所属方法 */
class DexIndex(private val bytes: ByteArray) {
    data class MethodRef(val owner: String, val name: String, val parameters: List<String>, val returns: String,
                         val access: Int, val strings: Set<String>) {
        val isStatic get() = access and 8 != 0
    }
    private fun range(offset: Int, size: Int) {
        require(offset >= 0 && size >= 0 && offset.toLong() + size <= bytes.size) { "DEX bounds" }
    }
    private fun u1(offset: Int): Int { range(offset, 1); return bytes[offset].toInt() and 255 }
    private fun u2(offset: Int): Int = u1(offset) or (u1(offset + 1) shl 8)
    private fun u4(offset: Int): Int {
        range(offset, 4)
        val value = u2(offset).toLong() or (u2(offset + 2).toLong() shl 16)
        require(value <= Int.MAX_VALUE) { "DEX integer overflow" }
        return value.toInt()
    }
    private inner class Reader(var offset: Int) {
        fun leb(): Int {
            var result = 0L
            for (shift in 0..28 step 7) {
                val value = u1(offset++)
                result = result or ((value and 127).toLong() shl shift)
                if (value and 128 == 0) {
                    require(result <= Int.MAX_VALUE)
                    return result.toInt()
                }
            }
            error("Invalid ULEB128")
        }
    }
    fun methods(prefix: String): List<MethodRef> {
        range(0, 112)
        require(bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(100, 101, 120, 10))) { "Not DEX" }
        require(u4(40) == 0x12345678 && u4(36) == 112 && u4(32) == bytes.size) { "Unsupported DEX header" }
        fun table(countOffset: Int, width: Int): Pair<Int, Int> {
            val count = u4(countOffset)
            val offset = u4(countOffset + 4)
            require(count <= bytes.size / width)
            range(offset, count * width)
            return count to offset
        }
        val (stringCount, stringOffset) = table(56, 4)
        val strings = Array(stringCount) { index ->
            val reader = Reader(u4(stringOffset + index * 4))
            val length = reader.leb()
            require(length <= bytes.size)
            val text = StringBuilder(length)
            while (true) {
                val ch = u1(reader.offset++)
                if (ch == 0) break
                text.append(when {
                    ch and 128 == 0 -> ch.toChar()
                    ch and 224 == 192 -> {
                        val next = u1(reader.offset++); require(next and 192 == 128)
                        (((ch and 31) shl 6) or (next and 63)).toChar()
                    }
                    ch and 240 == 224 -> {
                        val second = u1(reader.offset++); val third = u1(reader.offset++)
                        require(second and 192 == 128 && third and 192 == 128)
                        (((ch and 15) shl 12) or ((second and 63) shl 6) or (third and 63)).toChar()
                    }
                    else -> error("Invalid modified UTF-8")
                })
                require(text.length <= length)
            }
            require(text.length == length)
            text.toString()
        }
        val (typeCount, typeOffset) = table(64, 4)
        val types = Array(typeCount) { strings[u4(typeOffset + it * 4)] }
        val (protoCount, protoOffset) = table(72, 12)
        val (fieldCount, _) = table(80, 8)
        val (methodCount, methodOffset) = table(88, 8)
        val (classCount, classOffset) = table(96, 32)
        val output = ArrayList<MethodRef>()
        for (index in 0 until classCount) {
            val classDef = classOffset + index * 32
            val owner = types[u4(classDef)]
            if (!owner.startsWith(prefix)) continue
            val dataOffset = u4(classDef + 24)
            if (dataOffset == 0) continue
            val reader = Reader(dataOffset)
            val staticFields = reader.leb(); val instanceFields = reader.leb()
            val directMethods = reader.leb(); val virtualMethods = reader.leb()
            require(staticFields.toLong() + instanceFields <= fieldCount && directMethods.toLong() + virtualMethods <= methodCount)
            repeat(staticFields + instanceFields) { reader.leb(); reader.leb() }
            for (count in listOf(directMethods, virtualMethods)) {
                var methodIndex = 0
                repeat(count) {
                    val delta = reader.leb()
                    require(delta.toLong() + methodIndex < methodCount)
                    methodIndex += delta
                    val access = reader.leb(); val code = reader.leb()
                    if (code != 0) {
                        val methodId = methodOffset + methodIndex * 8
                        require(types[u2(methodId)] == owner)
                        val protoIndex = u2(methodId + 2); require(protoIndex < protoCount)
                        val proto = protoOffset + protoIndex * 12
                        val paramsOffset = u4(proto + 8)
                        val params = if (paramsOffset == 0) emptyList() else {
                            val size = u4(paramsOffset); require(size <= bytes.size / 2)
                            range(paramsOffset + 4, size * 2)
                            List(size) { types[u2(paramsOffset + 4 + it * 2)] }
                        }
                        output += MethodRef(owner, strings[u4(methodId + 4)], params, types[u4(proto + 4)], access,
                            codeStrings(code, strings))
                    }
                }
            }
        }
        return output
    }
    private fun codeStrings(code: Int, strings: Array<String>): Set<String> {
        range(code, 16)
        val units = u4(code + 12)
        require(units <= bytes.size / 2)
        val start = code + 16
        range(start, units * 2)
        var cursor = 0
        val found = HashSet<String>()
        fun word(relative: Int): Int { require(relative in 0 until units); return u2(start + relative * 2) }
        fun wide(relative: Int): Int { require(relative >= 0 && relative + 1 < units); return u4(start + relative * 2) }
        while (cursor < units) {
            val instruction = word(cursor)
            val opcode = instruction and 255
            if (opcode == 0x1a) found += strings[word(cursor + 1)]
            if (opcode == 0x1b) found += strings[wide(cursor + 1)]
            val width = if (opcode == 0 && instruction != 0) when (instruction) {
                0x100 -> 4L + word(cursor + 1) * 2L
                0x200 -> 2L + word(cursor + 1) * 4L
                0x300 -> 4L + (word(cursor + 1).toLong() * wide(cursor + 2) + 1) / 2
                else -> error("Unknown payload")
            } else instructionWidth(opcode).toLong()
            require(width > 0 && cursor.toLong() + width <= units) { "DEX instruction bounds" }
            cursor += width.toInt()
        }
        return found
    }
    private fun instructionWidth(op: Int): Int = when (op) {
        0x00, 0x01, 0x04, 0x07, in 0x0a..0x12, 0x1d, 0x1e, 0x21, 0x27, 0x28, in 0x7b..0x8f, in 0xb0..0xcf -> 1
        0x02, 0x05, 0x08, 0x13, 0x15, 0x16, 0x19, 0x1a, 0x1c, 0x1f, 0x20, 0x22, 0x23, 0x29,
        in 0x2d..0x3d, in 0x44..0x6d, in 0x90..0xaf, in 0xd0..0xe2, 0xfe, 0xff -> 2
        0x03, 0x06, 0x09, 0x14, 0x17, 0x1b, in 0x24..0x26, in 0x2a..0x2c, in 0x6e..0x72, in 0x74..0x78, 0xfc, 0xfd -> 3
        0x18 -> 5
        0xfa, 0xfb -> 4
        else -> error("Unsupported DEX opcode: $op")
    }
    companion object {
        fun scanApk(file: File, prefix: String, onError: (String, Exception) -> Unit = { _, e -> throw e }): List<MethodRef> =
            ZipFile(file).use { zip ->
                zip.entries().asSequence().filter { Regex("classes[0-9]*\\.dex").matches(it.name) }.flatMap { entry ->
                    try {
                        require(entry.size in 112..96L * 1024 * 1024)
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        require(bytes.size.toLong() == entry.size)
                        DexIndex(bytes).methods(prefix).asSequence()
                    } catch (e: Exception) { onError(entry.name, e); emptySequence() }
                }.toList()
            }
    }
}

object HostDiscovery {
    private const val RESOURCE = "Lcom/android/thememanager/basemodule/resource/model/Resource;"
    private const val RESULT = "Lmiui/drm/DrmManager\$DrmResult;"
    fun drm(method: DexIndex.MethodRef): Boolean = !method.isStatic &&
        method.owner.startsWith("Lcom/android/thememanager/controller/online/") &&
        method.returns == RESULT && method.parameters == listOf(RESOURCE) &&
        (method.strings.any { it.startsWith("check rights isLegal:") } ||
            method.strings.containsAll(setOf("theme", "ThemeManagerTag", "/system")))
    fun largeIcon(method: DexIndex.MethodRef): Boolean = !method.isStatic &&
        method.parameters == listOf("[Ljava/lang/Void;") &&
        method.strings.containsAll(setOf("apply failed", "/data/system/theme/large_icons/", "default_large_icon_product_id"))
}
