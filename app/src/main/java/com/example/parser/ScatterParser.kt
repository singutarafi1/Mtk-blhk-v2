package com.example.parser

import com.example.model.PartitionEntry

object ScatterParser {

    /**
     * Strictly and dynamically parses MediaTek scatter format (v1.1.0, v1.1.2, v2.0.0+).
     * Populates partition list EXACTLY as defined in the specific scatter file.
     * Returns empty list if no valid partition entries found in the file.
     */
    fun parseScatter(content: String): Pair<String, List<PartitionEntry>> {
        var platform = "MTK_DEVICE"
        val partitions = mutableListOf<PartitionEntry>()

        val lines = content.lines()
        var currentPartitionIndex = 0
        var currentPartName = ""
        var currentFileName = ""
        var currentLinearAddr = "0x0"
        var currentPhysicalAddr = "0x0"
        var currentSize = "0x0"
        var currentRegion = "EMMC_USER"
        var currentIsDownload = true
        var inPartitionBlock = false

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            if (line.contains("platform:", ignoreCase = true) || line.contains("project:", ignoreCase = true)) {
                val parts = line.split(":")
                if (parts.size >= 2) {
                    val p = parts[1].trim()
                    if (p.isNotEmpty()) {
                        platform = p
                    }
                }
                continue
            }

            if (line.startsWith("- partition_index:", ignoreCase = true) || line.startsWith("partition_index:", ignoreCase = true)) {
                if (inPartitionBlock && currentPartName.isNotEmpty()) {
                    val sizeBytes = parseHexOrDec(currentSize)
                    val isNv = isNvramPartition(currentPartName)
                    partitions.add(
                        PartitionEntry(
                            partitionIndex = currentPartitionIndex,
                            partitionName = currentPartName,
                            fileName = currentFileName.ifEmpty { "NONE" },
                            linearStartAddrHex = currentLinearAddr,
                            physicalStartAddrHex = currentPhysicalAddr,
                            partitionSizeHex = currentSize,
                            sizeBytes = sizeBytes,
                            region = currentRegion,
                            isDownload = currentIsDownload,
                            isProtectedNv = isNv,
                            isSelectedForFlashing = currentIsDownload && currentFileName.isNotEmpty() && currentFileName != "NONE"
                        )
                    )
                }

                inPartitionBlock = true
                val idxStr = line.substringAfter(":").trim()
                currentPartitionIndex = idxStr.toIntOrNull() ?: partitions.size
                currentPartName = ""
                currentFileName = ""
                currentLinearAddr = "0x0"
                currentPhysicalAddr = "0x0"
                currentSize = "0x0"
                currentRegion = "EMMC_USER"
                currentIsDownload = true
                continue
            }

            if (inPartitionBlock) {
                when {
                    line.startsWith("partition_name:", ignoreCase = true) -> {
                        currentPartName = line.substringAfter(":").trim()
                    }
                    line.startsWith("file_name:", ignoreCase = true) -> {
                        currentFileName = line.substringAfter(":").trim()
                    }
                    line.startsWith("linear_start_addr:", ignoreCase = true) -> {
                        currentLinearAddr = line.substringAfter(":").trim()
                    }
                    line.startsWith("physical_start_addr:", ignoreCase = true) -> {
                        currentPhysicalAddr = line.substringAfter(":").trim()
                    }
                    line.startsWith("partition_size:", ignoreCase = true) -> {
                        currentSize = line.substringAfter(":").trim()
                    }
                    line.startsWith("region:", ignoreCase = true) -> {
                        currentRegion = line.substringAfter(":").trim()
                    }
                    line.startsWith("is_download:", ignoreCase = true) -> {
                        currentIsDownload = line.substringAfter(":").trim().equals("true", ignoreCase = true)
                    }
                }
            }
        }

        // Add final partition block if present
        if (inPartitionBlock && currentPartName.isNotEmpty()) {
            val sizeBytes = parseHexOrDec(currentSize)
            val isNv = isNvramPartition(currentPartName)
            partitions.add(
                PartitionEntry(
                    partitionIndex = currentPartitionIndex,
                    partitionName = currentPartName,
                    fileName = currentFileName.ifEmpty { "NONE" },
                    linearStartAddrHex = currentLinearAddr,
                    physicalStartAddrHex = currentPhysicalAddr,
                    partitionSizeHex = currentSize,
                    sizeBytes = sizeBytes,
                    region = currentRegion,
                    isDownload = currentIsDownload,
                    isProtectedNv = isNv,
                    isSelectedForFlashing = currentIsDownload && currentFileName.isNotEmpty() && currentFileName != "NONE"
                )
            )
        }

        return Pair(platform, partitions)
    }

    private fun parseHexOrDec(str: String): Long {
        return try {
            val clean = str.trim()
            if (clean.startsWith("0x", ignoreCase = true)) {
                val hexOnly = clean.substring(2)
                java.lang.Long.parseUnsignedLong(hexOnly, 16)
            } else {
                java.lang.Long.parseUnsignedLong(clean, 10)
            }
        } catch (_: Exception) {
            0L
        }
    }

    private fun isNvramPartition(name: String): Boolean {
        val lower = name.lowercase()
        return lower in listOf(
            "nvram", "nvdata", "protect1", "protect2", "protect_f", "protect_s",
            "secro", "nvcfg", "proinfo", "seccfg", "sec1", "persist", "persist_backup"
        )
    }
}

