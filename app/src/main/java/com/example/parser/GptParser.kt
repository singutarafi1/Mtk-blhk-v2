package com.example.parser

import com.example.model.PartitionEntry
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

object GptParser {

    private const val GPT_HEADER_SIGNATURE = "EFI PART" // 8 bytes: 45 46 49 20 50 41 52 54
    private const val GPT_HEADER_SIZE = 92
    private const val GPT_PARTITION_ENTRY_SIZE = 128

    /**
     * Parses raw GPT sectors (LBA 1 header + LBA 2..33 partition entries).
     * Supports both standard 512-byte sectors (eMMC/NAND) and 4096-byte (4K) sectors (UFS).
     */
    fun parseRawGpt(gptBytes: ByteArray, sectorSizeOverride: Long? = null): List<PartitionEntry> {
        if (gptBytes.size < 512) {
            return emptyList()
        }

        // LBA 1: GPT Header (offset 0 if passed starting from LBA1, or 512 for eMMC LBA0, or 4096 for UFS 4K LBA0)
        var headerOffset = 0
        var detectedSectorSize = sectorSizeOverride ?: 512L

        if (isGptHeader(gptBytes, 0)) {
            headerOffset = 0
        } else if (gptBytes.size >= 1024 && isGptHeader(gptBytes, 512)) {
            headerOffset = 512
            detectedSectorSize = sectorSizeOverride ?: 512L
        } else if (gptBytes.size >= 8192 && isGptHeader(gptBytes, 4096)) {
            headerOffset = 4096
            detectedSectorSize = sectorSizeOverride ?: 4096L
        } else {
            return emptyList()
        }

        val sectorSize = sectorSizeOverride ?: detectedSectorSize
        val buffer = ByteBuffer.wrap(gptBytes).order(ByteOrder.LITTLE_ENDIAN)

        // Read GPT Header fields
        buffer.position(headerOffset + 80) // offset 80: Number of partition entries (uint32)
        val numEntries = buffer.int.coerceIn(0, 128)

        buffer.position(headerOffset + 84) // offset 84: Size of partition entry (uint32)
        val entrySize = buffer.int.coerceIn(128, 512)

        // Partition entries start at LBA 2 (usually offset after header by 1 sector size)
        val entriesOffset = if (headerOffset == 0) sectorSize.toInt() else headerOffset + sectorSize.toInt()

        val partitions = mutableListOf<PartitionEntry>()

        for (i in 0 until numEntries) {
            val entryStart = entriesOffset + (i * entrySize)
            if (entryStart + entrySize > gptBytes.size) {
                break
            }

            buffer.position(entryStart)

            // Bytes 0..15: Partition Type GUID (16 bytes)
            val typeGuidLow = buffer.long
            val typeGuidHigh = buffer.long
            if (typeGuidLow == 0L && typeGuidHigh == 0L) {
                // Empty / unused GPT entry
                continue
            }

            // Bytes 16..31: Unique Partition GUID (16 bytes)
            buffer.position(entryStart + 16)
            val uniqueGuidLow = buffer.long
            val uniqueGuidHigh = buffer.long

            // Bytes 32..39: Starting LBA (uint64)
            val firstLba = buffer.long
            // Bytes 40..47: Ending LBA (uint64)
            val lastLba = buffer.long
            // Bytes 48..55: Attributes (uint64)
            val attributes = buffer.long

            // Bytes 56..127: Partition Name (36 UTF-16LE characters / 72 bytes)
            val nameBytes = ByteArray(72)
            buffer.position(entryStart + 56)
            buffer.get(nameBytes)

            val partName = parseUtf16LeName(nameBytes)
            if (partName.isEmpty()) {
                continue
            }

            // Calculate Addresses & Sizes based on detected or configured sector size (512 vs 4096 UFS)
            val startLinearAddress = firstLba * sectorSize
            val sectorCount = (lastLba - firstLba + 1).coerceAtLeast(0)
            val partitionSizeBytes = sectorCount * sectorSize

            val isNv = isNvramPartition(partName)
            val isSelectedByDefault = isEssentialPartition(partName)

            val fileName = when (partName.lowercase()) {
                "preloader" -> "preloader.bin"
                "boot" -> "boot.img"
                "recovery" -> "recovery.img"
                "vbmeta" -> "vbmeta.img"
                "vbmeta_system" -> "vbmeta_system.img"
                "vbmeta_vendor" -> "vbmeta_vendor.img"
                "md1img" -> "md1img.img"
                "super" -> "super.img"
                "dtbo" -> "dtbo.img"
                "logo" -> "logo.bin"
                "spmfw" -> "spmfw.img"
                "nvram", "nvdata", "protect1", "protect2", "secro", "nvcfg", "proinfo", "frp", "seccfg" -> "$partName.bin"
                else -> "$partName.img"
            }

            partitions.add(
                PartitionEntry(
                    partitionIndex = partitions.size,
                    partitionName = partName,
                    fileName = fileName,
                    linearStartAddrHex = "0x%X".format(startLinearAddress),
                    physicalStartAddrHex = "0x%X".format(startLinearAddress),
                    partitionSizeHex = "0x%X".format(partitionSizeBytes),
                    sizeBytes = partitionSizeBytes,
                    region = if (partName.lowercase() == "preloader") "EMMC_BOOT_1" else "EMMC_USER",
                    isDownload = true,
                    isProtectedNv = isNv,
                    isSelectedForFlashing = isSelectedByDefault
                )
            )
        }

        return partitions
    }

    private fun isGptHeader(bytes: ByteArray, offset: Int): Boolean {
        if (offset + 8 > bytes.size) return false
        val sig = String(bytes, offset, 8, Charsets.US_ASCII)
        return sig == GPT_HEADER_SIGNATURE
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
            "preloader", "boot", "recovery", "vbmeta", "vbmeta_system",
            "vbmeta_vendor", "md1img", "spmfw", "super", "dtbo", "lk", "lk2"
        )
    }
}
