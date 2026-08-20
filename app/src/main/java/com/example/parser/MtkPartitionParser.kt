package com.example.parser

import com.example.model.PartitionEntry
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Universal MediaTek Partition Parser supporting:
 * - GPT (GUID Partition Table with EFI Part Header)
 * - BPI (MediaTek "BPI\x00" Header)
 * - PMT (MediaTek "1vTP" PMTv1, "3vTP" PMTv3, "MPT3")
 * - MBR (Master Boot Record 0x55AA and Extended Partition Tables)
 * Ported from mtkclient bpi.py, gpt.py, mbr.py, pmt.py
 */
object MtkPartitionParser {

    const val AB_FLAG_OFFSET = 6
    const val AB_PARTITION_ATTR_SLOT_ACTIVE = (0x1L shl 2)
    const val AB_PARTITION_ATTR_BOOT_SUCCESSFUL = (0x1L shl 6)
    const val AB_PARTITION_ATTR_UNBOOTABLE = (0x1L shl 7)

    fun parse(data: ByteArray, pageSize: Long = 512L): List<PartitionEntry> {
        if (data.size < 512) return emptyList()

        // 1. Check BPI Header
        if (data.size >= 4 && data[0] == 'B'.code.toByte() && data[1] == 'P'.code.toByte() && data[2] == 'I'.code.toByte() && data[3] == 0.toByte()) {
            return parseBpi(data)
        }

        // 2. Check PMTv1 / PMTv3 Header
        if (data.size >= 4) {
            val sig4 = String(data.copyOfRange(0, 4), Charsets.US_ASCII)
            if (sig4 == "1vTP" || sig4 == "3vTP" || sig4 == "MPT3" || sig4 == "PTv1" || sig4 == "PTv3") {
                return parsePmt(data, pageSize)
            }
        }

        // 3. Check GPT Header (at offset 0, 512, or 4096)
        for (offset in listOf(0, 512, 4096)) {
            if (offset + 8 <= data.size) {
                val sig = String(data.copyOfRange(offset, offset + 8), Charsets.US_ASCII)
                if (sig == "EFI PART") {
                    return GptParser.parseRawGpt(data)
                }
            }
        }

        // 4. Check MBR Header (0x55AA marker at offset 510)
        if (data.size >= 512 && (data[510].toInt() and 0xFF) == 0x55 && (data[511].toInt() and 0xFF) == 0xAA) {
            return parseMbr(data, pageSize)
        }

        // Fallback to standard GPT parser
        return GptParser.parseRawGpt(data)
    }

    /**
     * Parses MediaTek BPI Table
     */
    private fun parseBpi(data: ByteArray): List<PartitionEntry> {
        val partitions = mutableListOf<PartitionEntry>()
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val sectorSize = 512L

        var pos = 0x800
        while (pos + 0x80 <= data.size) {
            buf.position(pos)
            // Skip 16 bytes GUID
            val testGuid = buf.long
            if (testGuid == 0L) break

            buf.position(pos + 16 + 16)
            val firstLba = buf.long
            val lastLba = buf.long
            val flags = buf.long

            val nameBytes = ByteArray(0x48)
            buf.get(nameBytes)
            val name = parseUtf16LeName(nameBytes)
            if (name.isNotEmpty()) {
                val startAddr = firstLba * sectorSize
                val sizeBytes = (lastLba - firstLba + 1).coerceAtLeast(0) * sectorSize
                partitions.add(
                    createPartitionEntry(
                        index = partitions.size,
                        name = name,
                        startAddr = startAddr,
                        sizeBytes = sizeBytes
                    )
                )
            }
            pos += 0x80
        }
        return partitions
    }

    /**
     * Parses MediaTek PMTv1 / PMTv3 Table
     */
    private fun parsePmt(data: ByteArray, pageSize: Long): List<PartitionEntry> {
        val partitions = mutableListOf<PartitionEntry>()
        val sig = String(data.copyOfRange(0, 4), Charsets.US_ASCII)
        val isV1 = (sig == "1vTP" || sig == "PTv1")
        val entrySize = if (isV1) 0x58 else 0x60
        val maxEntries = if (isV1) 40 else 128

        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until maxEntries) {
            val pos = 0x08 + (i * entrySize)
            if (pos + entrySize > data.size) break

            buf.position(pos)
            val nameBytes = ByteArray(if (isV1) 64 else 64)
            buf.get(nameBytes)
            val name = parseAsciiOrUtf8Name(nameBytes)
            if (name.isEmpty() || name == "BMTPOOL") continue

            val size = buf.long
            val offset = if (isV1) buf.long else {
                buf.long // part_id
                buf.long // offset
            }

            if (size > 0) {
                partitions.add(
                    createPartitionEntry(
                        index = partitions.size,
                        name = name,
                        startAddr = offset,
                        sizeBytes = size
                    )
                )
            }
        }
        return partitions
    }

    /**
     * Parses Master Boot Record (MBR) and Extended Partition entries
     */
    private fun parseMbr(data: ByteArray, pageSize: Long): List<PartitionEntry> {
        val partitions = mutableListOf<PartitionEntry>()
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until 4) {
            val entryOffset = 446 + (i * 16)
            if (entryOffset + 16 > data.size) break

            buf.position(entryOffset)
            val bootIndicator = buf.get().toInt() and 0xFF
            buf.get() // start head
            buf.short // start sect cylinder
            val systemId = buf.get().toInt() and 0xFF
            buf.get() // end head
            buf.short // end sect cylinder
            val relativeSector = buf.int.toLong() and 0xFFFFFFFFL
            val totalSectors = buf.int.toLong() and 0xFFFFFFFFL

            if (systemId != 0 && totalSectors > 0) {
                val name = "mbr_part_$i"
                val startAddr = relativeSector * 512L
                val sizeBytes = totalSectors * 512L
                partitions.add(
                    createPartitionEntry(
                        index = partitions.size,
                        name = name,
                        startAddr = startAddr,
                        sizeBytes = sizeBytes
                    )
                )
            }
        }
        return partitions
    }

    private fun createPartitionEntry(
        index: Int,
        name: String,
        startAddr: Long,
        sizeBytes: Long
    ): PartitionEntry {
        val isNv = isNvramPartition(name)
        val isSelected = isEssentialPartition(name)
        val fileName = when (name.lowercase()) {
            "preloader" -> "preloader.bin"
            "boot" -> "boot.img"
            "recovery" -> "recovery.img"
            "vbmeta" -> "vbmeta.img"
            "super" -> "super.img"
            "dtbo" -> "dtbo.img"
            "logo" -> "logo.bin"
            "nvram", "nvdata", "protect1", "protect2", "secro", "nvcfg", "proinfo", "frp", "seccfg" -> "$name.bin"
            else -> "$name.img"
        }

        return PartitionEntry(
            partitionIndex = index,
            partitionName = name,
            fileName = fileName,
            linearStartAddrHex = "0x%X".format(startAddr),
            physicalStartAddrHex = "0x%X".format(startAddr),
            partitionSizeHex = "0x%X".format(sizeBytes),
            sizeBytes = sizeBytes,
            region = if (name.lowercase() == "preloader") "EMMC_BOOT_1" else "EMMC_USER",
            isDownload = true,
            isProtectedNv = isNv,
            isSelectedForFlashing = isSelected
        )
    }

    private fun parseUtf16LeName(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (i in 0 until bytes.size step 2) {
            val charCode = (bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
            if (charCode == 0) break
            sb.append(charCode.toChar())
        }
        return sb.toString().trim()
    }

    private fun parseAsciiOrUtf8Name(bytes: ByteArray): String {
        val endIdx = bytes.indexOf(0.toByte()).let { if (it >= 0) it else bytes.size }
        return String(bytes, 0, endIdx, Charsets.UTF_8).trim()
    }

    private fun isNvramPartition(name: String): Boolean {
        val lower = name.lowercase()
        return lower in listOf(
            "nvram", "nvdata", "protect1", "protect2", "protect_f", "protect_s",
            "secro", "nvcfg", "proinfo", "seccfg", "sec1", "persist", "persist_backup"
        )
    }

    private fun isEssentialPartition(name: String): Boolean {
        val lower = name.lowercase()
        return lower in listOf(
            "preloader", "boot", "recovery", "vbmeta", "vbmeta_system", "vbmeta_vendor",
            "dtbo", "super", "system", "vendor", "product", "md1img", "spmfw", "logo"
        )
    }
}
