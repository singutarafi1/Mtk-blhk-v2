package com.example.protocol

import com.example.model.LogLevel
import com.example.model.OperationProgress
import com.example.model.PartitionEntry
import com.example.model.TerminalLog
import com.example.storage.BackupStorageManager
import java.io.File

/**
 * MediaTek Calibration, Bootloader SecCfg & NVRAM Engine
 * Real hardware operations only - no simulation.
 */
class MtkCalibrationEngine(
    private val usb: TargetPhoneUsbManager,
    private val flashEngine: MtkFlashEngine,
    private val dumpEngine: MtkDumpEngine,
    private val storageManager: BackupStorageManager,
    private val logCallback: (TerminalLog) -> Unit,
    private val progressCallback: (OperationProgress) -> Unit
) {

    private fun log(message: String, level: LogLevel = LogLevel.INFO) {
        logCallback(TerminalLog("", message, level))
    }

    /**
     * Unlocks bootloader by writing a valid SecCfg V4 unlock payload to seccfg partition.
     */
    suspend fun unlockBootloader(
        partitions: List<PartitionEntry>,
        isSimulation: Boolean = false // ignored, real hardware only
    ): Result<Boolean> {
        log("==================================================", LogLevel.WARNING)
        log(">>> [BOOTLOADER UNLOCK] Generating SecCfg Unlock Payload", LogLevel.WARNING)
        log("==================================================", LogLevel.INFO)

        val seccfgPart = partitions.find { it.partitionName.lowercase() == "seccfg" }
            ?: return Result.failure(IllegalArgumentException("seccfg partition not found in partition list"))

        log("[SECCFG] Target partition: '${seccfgPart.partitionName}' @ ${seccfgPart.linearStartAddrHex}", LogLevel.INFO)

        // Generate SecCfg V4 Unlock Payload
        val v4UnlockPayload = MtkSecCfgEngine.createV4Payload(unlock = true, critical = true)
        log("[+] Generated SecCfg V4 Payload (${v4UnlockPayload.size} bytes, State: 0x03 UNLOCKED)", LogLevel.INFO)

        val verify = MtkSecCfgEngine.parseSecCfg(v4UnlockPayload)
        log("[+] Payload Validation: ${verify.message} [Engine: ${verify.hwType}]", LogLevel.SUCCESS)

        // Determine storage/part from partition region
        val isUfs = seccfgPart.region.contains("UFS", true) || seccfgPart.region.contains("LU", true)
        val (storageType, partSection) = flashEngine.resolveStorageTarget(
            partitionName = seccfgPart.partitionName,
            region = seccfgPart.region,
            isUfs = isUfs
        )

        // Write seccfg using XFlash (test devices are XFlash). For legacy chips, implement flashLegacy here if needed.
        val success = flashEngine.flashXFlash(
            partition = seccfgPart,
            data = v4UnlockPayload,
            storageType = storageType,
            partType = partSection
        )

        if (success) {
            log("==================================================", LogLevel.SUCCESS)
            log("BOOTLOADER UNLOCK COMPLETE: SecCfg partition flashed [ 100% OK ].", LogLevel.SUCCESS)
            log("Device Bootloader State: [ UNLOCKED ]", LogLevel.SUCCESS)
            log("==================================================", LogLevel.SUCCESS)
            return Result.success(true)
        } else {
            log("[-] Failed to write SecCfg unlock payload.", LogLevel.ERROR)
            return Result.failure(IllegalStateException("SecCfg unlock payload write failed"))
        }
    }

    /**
     * Locks bootloader by writing a valid SecCfg V4 lock payload.
     */
    suspend fun lockBootloader(
        partitions: List<PartitionEntry>,
        isSimulation: Boolean = false // ignored
    ): Result<Boolean> {
        log("==================================================", LogLevel.WARNING)
        log(">>> [BOOTLOADER LOCK] Generating SecCfg Lock Payload", LogLevel.WARNING)
        log("==================================================", LogLevel.INFO)

        val seccfgPart = partitions.find { it.partitionName.lowercase() == "seccfg" }
            ?: return Result.failure(IllegalArgumentException("seccfg partition not found in partition list"))

        val v4LockPayload = MtkSecCfgEngine.createV4Payload(unlock = false, critical = false)
        log("[+] Generated SecCfg V4 Payload (${v4LockPayload.size} bytes, State: 0x01 LOCKED)", LogLevel.INFO)

        val verify = MtkSecCfgEngine.parseSecCfg(v4LockPayload)
        log("[+] Payload Validation: ${verify.message}", LogLevel.INFO)

        val isUfs = seccfgPart.region.contains("UFS", true) || seccfgPart.region.contains("LU", true)
        val (storageType, partSection) = flashEngine.resolveStorageTarget(
            partitionName = seccfgPart.partitionName,
            region = seccfgPart.region,
            isUfs = isUfs
        )

        val success = flashEngine.flashXFlash(
            partition = seccfgPart,
            data = v4LockPayload,
            storageType = storageType,
            partType = partSection
        )

        if (success) {
            log("==================================================", LogLevel.SUCCESS)
            log("BOOTLOADER LOCK COMPLETE: SecCfg locked successfully.", LogLevel.SUCCESS)
            log("==================================================", LogLevel.SUCCESS)
            return Result.success(true)
        } else {
            return Result.failure(IllegalStateException("SecCfg lock write failed"))
        }
    }

    /**
     * Performs NVRAM / NVDATA calibration backup bundle.
     * Real read operations via MtkDumpEngine.
     */
    suspend fun backupNvramBundle(
        chipPlatform: String,
        partitions: List<PartitionEntry>
    ): Result<String> {
        log("==================================================", LogLevel.INFO)
        log(">>> [NVRAM CALIBRATION BACKUP] Extracting Radio & IMEI Partitions", LogLevel.INFO)
        log("==================================================", LogLevel.INFO)

        val nvNames = listOf("nvram", "nvdata", "nvcfg", "protect1", "protect2", "proinfo")
        val targets = partitions.filter { it.partitionName.lowercase() in nvNames }

        if (targets.isEmpty()) {
            return Result.failure(IllegalStateException("No NV partitions found in partition list"))
        }

        val sessionFolder = "${chipPlatform}_NVRAM_Backup_${System.currentTimeMillis()}"
        val targetDir = File(storageManager.getBackupDirectory(), sessionFolder)
        targetDir.mkdirs()

        log("Found ${targets.size} NV calibration partitions to archive.", LogLevel.INFO)

        val dumpedPaths = mutableListOf<String>()
        for ((idx, part) in targets.withIndex()) {
            val outFile = File(targetDir, "${part.partitionName}.bin")
            log("[${idx + 1}/${targets.size}] Reading ${part.partitionName} (${part.formattedSize})...", LogLevel.INFO)

            val isModern = isModernChip(chipPlatform) || part.region.contains("UFS", true) || part.region.contains("LU", true)
            val (storageType, partSection) = flashEngine.resolveStorageTarget(part.partitionName, part.region, isModern)

            val res = dumpEngine.dumpPartition(
                partition = part,
                outputFile = outFile,
                storageType = storageType,
                partType = partSection,
                useXFlash = true // Use XFlash for modern devices; for legacy override if needed
            )
            if (res.isSuccess) {
                dumpedPaths.add(outFile.absolutePath)
                log("[+] Saved: ${outFile.name} (${outFile.length() / 1024} KB)", LogLevel.SUCCESS)
            } else {
                log("[-] Failed to dump ${part.partitionName}", LogLevel.WARNING)
            }
        }

        // Generate scatter file as metadata
        storageManager.generateScatterFile(chipPlatform, partitions, sessionFolder)
        log("[+] NVRAM Calibration archive complete: ${targetDir.absolutePath}", LogLevel.SUCCESS)
        return Result.success(targetDir.absolutePath)
    }

    private fun isModernChip(platform: String): Boolean {
        val p = platform.lowercase()
        return p.contains("6833") || p.contains("6877") || p.contains("6893") ||
                p.contains("6885") || p.contains("6853") || p.contains("6873") ||
                p.contains("6983") || p.contains("6785") || p.contains("6768") ||
                p.contains("dimensity")
    }
}