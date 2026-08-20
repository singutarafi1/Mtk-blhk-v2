package com.example.protocol

import com.example.model.LogLevel
import com.example.model.OperationProgress
import com.example.model.PartitionEntry
import com.example.model.TerminalLog

/**
 * MediaTek Native Format & Wipe Engine
 * Real USB hardware format operations via XFlash & Legacy protocols - No simulation.
 * Ported from Python mtkclient/Library/DA/xflash/xflash_lib.py & dalegacy_lib.py
 */
class MtkFormatEngine(
    private val usb: TargetPhoneUsbManager,
    private val flashEngine: MtkFlashEngine,
    private val logCallback: (TerminalLog) -> Unit,
    private val progressCallback: (OperationProgress) -> Unit
) {
    private val xflashEngine = MtkXFlashEngine(usb, logCallback)

    companion object {
        const val ZERO_CHUNK_SIZE = 131072 // 128KB zero block
    }

    private fun log(message: String, level: LogLevel = LogLevel.INFO) {
        logCallback(TerminalLog("", message, level))
    }

    /**
     * Erases / Formats a partition by sending real DA Format commands or zero-block payload
     */
    suspend fun formatPartition(
        partition: PartitionEntry,
        useDirectZeroFill: Boolean = false,
        chipPlatform: String = ""
    ): Result<Boolean> {
        val startAddr = partition.startLinearAddress
        val totalBytes = if (partition.sizeBytes > 0) partition.sizeBytes else 4194304L

        log("[FORMAT] Erasing partition '${partition.partitionName}' @ 0x%X (Length: ${totalBytes / 1024} KB)".format(startAddr), LogLevel.WARNING)

        val isModern = chipPlatform.contains("6833", ignoreCase = true) ||
                chipPlatform.contains("6877", ignoreCase = true) ||
                chipPlatform.contains("6893", ignoreCase = true) ||
                chipPlatform.contains("6885", ignoreCase = true) ||
                chipPlatform.contains("6853", ignoreCase = true) ||
                chipPlatform.contains("6785", ignoreCase = true) ||
                chipPlatform.contains("6768", ignoreCase = true) ||
                partition.region.contains("UFS", ignoreCase = true) ||
                partition.region.contains("LU", ignoreCase = true)

        val (storageType, partSection) = flashEngine.resolveStorageTarget(
            partitionName = partition.partitionName,
            region = partition.region,
            isUfs = isModern
        )

        progressCallback(
            OperationProgress(
                isRunning = true,
                title = "Erasing ${partition.partitionName}...",
                detail = "Sending Format command to target Flash controller...",
                percentage = 50f
            )
        )

        if (useDirectZeroFill) {
            log("[FORMAT] Performing Direct Zero-Fill Erase on '${partition.partitionName}'...", LogLevel.INFO)
            val zeroChunk = ByteArray(ZERO_CHUNK_SIZE)
            val success = if (isModern || storageType == MtkFlashEngine.StorageType.UFS) {
                flashEngine.flashXFlash(
                    partition = partition.copy(sizeBytes = minOf(totalBytes, 2097152L)),
                    data = zeroChunk,
                    storageType = storageType,
                    partType = partSection
                )
            } else {
                flashEngine.flashLegacy(
                    partition = partition.copy(sizeBytes = minOf(totalBytes, 2097152L)),
                    data = zeroChunk,
                    storageType = storageType,
                    partType = partSection
                )
            }
            return if (success) Result.success(true) else Result.failure(IllegalStateException("Zero-fill failed"))
        }

        val formatted = xflashEngine.formatFlash(
            storage = storageType.value,
            partType = partSection,
            address = startAddr,
            length = totalBytes
        )

        progressCallback(OperationProgress(isRunning = false, percentage = 100f))

        return if (formatted) {
            log("[+] Partition '${partition.partitionName}' formatted successfully.", LogLevel.SUCCESS)
            Result.success(true)
        } else {
            log("[-] [FORMAT FAILED]: Device rejected format command for ${partition.partitionName}", LogLevel.ERROR)
            Result.failure(IllegalStateException("Device rejected format command"))
        }
    }

    /**
     * Erases Google FRP Partition (frp / persistent / config)
     */
    suspend fun eraseFrp(
        partitions: List<PartitionEntry>,
        chipPlatform: String = ""
    ): Result<Boolean> {
        val frpPart = partitions.find { it.partitionName.lowercase() in listOf("frp", "persistent", "config") }
            ?: PartitionEntry(
                partitionIndex = 0,
                partitionName = "frp",
                fileName = "frp.bin",
                linearStartAddrHex = "0x100000",
                physicalStartAddrHex = "0x100000",
                partitionSizeHex = "0x100000",
                sizeBytes = 1048576L,
                region = "EMMC_USER",
                isDownload = true,
                isSelectedForFlashing = true
            )
        log("[FRP UNLOCK] Zeroing FRP Lock storage block...", LogLevel.WARNING)
        return formatPartition(frpPart, useDirectZeroFill = true, chipPlatform = chipPlatform)
    }

    /**
     * Factory Reset (Wipes userdata and cache)
     */
    suspend fun factoryReset(
        partitions: List<PartitionEntry>,
        chipPlatform: String = ""
    ): Result<Boolean> {
        val wipeTargets = listOf("userdata", "cache", "metadata")
        val found = partitions.filter { it.partitionName.lowercase() in wipeTargets }
        val effective = if (found.isNotEmpty()) found else listOf(
            PartitionEntry(
                partitionIndex = 0,
                partitionName = "userdata",
                fileName = "userdata.img",
                linearStartAddrHex = "0x400000000",
                physicalStartAddrHex = "0x400000000",
                partitionSizeHex = "0x400000000",
                sizeBytes = 17179869184L,
                region = "EMMC_USER",
                isDownload = false,
                isSelectedForFlashing = true
            )
        )

        for (part in effective) {
            log("[FACTORY RESET] Wiping '${part.partitionName}'...", LogLevel.WARNING)
            val res = formatPartition(part, useDirectZeroFill = false, chipPlatform = chipPlatform)
            if (res.isFailure) return res
        }
        return Result.success(true)
    }

    /**
     * Disables Mi Account / Cloud Lock on Xiaomi Devices
     */
    suspend fun disableMiAccount(
        partitions: List<PartitionEntry>,
        chipPlatform: String = ""
    ): Result<Boolean> {
        val targets = listOf("cust", "persist", "frp")
        val found = partitions.filter { it.partitionName.lowercase() in targets }
        for (part in found) {
            log("[MI ACCOUNT] Formatting '${part.partitionName}'...", LogLevel.WARNING)
            val res = formatPartition(part, useDirectZeroFill = true, chipPlatform = chipPlatform)
            if (res.isFailure) return res
        }
        return Result.success(true)
    }
}
