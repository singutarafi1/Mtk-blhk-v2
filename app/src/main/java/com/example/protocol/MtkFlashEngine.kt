package com.example.protocol

import com.example.model.LogLevel
import com.example.model.OperationProgress
import com.example.model.PartitionEntry
import com.example.model.TerminalLog
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MediaTek Native Flashing Engine
 * - XFlash protocol (0x010004) through MtkXFlashEngine
 * - Legacy DA protocol (0x62) for older chips
 * No simulation / fake data.
 */
class MtkFlashEngine(
    private val usb: TargetPhoneUsbManager,
    private val logCallback: (TerminalLog) -> Unit,
    private val progressCallback: (OperationProgress) -> Unit
) {
    private val xflashEngine = MtkXFlashEngine(usb, logCallback)

    enum class StorageType(val value: Int) {
        EMMC(MtkXFlashConstants.DaStorage.EMMC),
        SDMMC(MtkXFlashConstants.DaStorage.SDMMC),
        NAND(MtkXFlashConstants.DaStorage.NAND),
        NOR(MtkXFlashConstants.DaStorage.NOR),
        UFS(MtkXFlashConstants.DaStorage.UFS),
        RAM(MtkXFlashConstants.DaStorage.RAM)
    }

    enum class EmmcPartition(val value: Int) {
        BOOT1(MtkXFlashConstants.EmmcPartitionType.BOOT1),
        BOOT2(MtkXFlashConstants.EmmcPartitionType.BOOT2),
        RPMB(MtkXFlashConstants.EmmcPartitionType.RPMB),
        GP1(MtkXFlashConstants.EmmcPartitionType.GP1),
        GP2(MtkXFlashConstants.EmmcPartitionType.GP2),
        GP3(MtkXFlashConstants.EmmcPartitionType.GP3),
        GP4(MtkXFlashConstants.EmmcPartitionType.GP4),
        USER(MtkXFlashConstants.EmmcPartitionType.USER)
    }

    enum class UfsPartition(val value: Int) {
        LU0(MtkXFlashConstants.UFSPartitionType.USER),
        LU1(MtkXFlashConstants.UFSPartitionType.BOOT1),
        LU2(MtkXFlashConstants.UFSPartitionType.BOOT2),
        RPMB(MtkXFlashConstants.UFSPartitionType.RPMB)
    }

    companion object {
        const val LEGACY_CMD_WRITE_DATA = 0x62.toByte()
        const val LEGACY_ACK: Byte = 0x5A.toByte()
        const val LEGACY_CONT_CHAR: Byte = 0x69.toByte()
    }

    private fun log(message: String, level: LogLevel = LogLevel.INFO) {
        logCallback(TerminalLog("", message, level))
    }

    /**
     * Resolves target partition region to storage type and section code.
     */
    fun resolveStorageTarget(
        partitionName: String,
        region: String,
        isUfs: Boolean = false
    ): Pair<StorageType, Int> {
        val lowerName = partitionName.lowercase()
        val lowerRegion = region.lowercase()

        return if (isUfs) {
            val section = when {
                lowerName == "preloader" || lowerRegion.contains("boot1") || lowerRegion.contains("lu1") -> UfsPartition.LU1.value
                lowerName == "preloader2" || lowerRegion.contains("boot2") || lowerRegion.contains("lu2") -> UfsPartition.LU2.value
                lowerName == "rpmb" || lowerRegion.contains("rpmb") -> UfsPartition.RPMB.value
                else -> UfsPartition.LU0.value
            }
            Pair(StorageType.UFS, section)
        } else {
            val section = when {
                lowerName == "preloader" || lowerRegion.contains("boot_1") || lowerRegion.contains("boot1") -> EmmcPartition.BOOT1.value
                lowerName == "preloader2" || lowerRegion.contains("boot_2") || lowerRegion.contains("boot2") -> EmmcPartition.BOOT2.value
                lowerName == "rpmb" || lowerRegion.contains("rpmb") -> EmmcPartition.RPMB.value
                lowerRegion.contains("gp1") -> EmmcPartition.GP1.value
                lowerRegion.contains("gp2") -> EmmcPartition.GP2.value
                lowerRegion.contains("gp3") -> EmmcPartition.GP3.value
                lowerRegion.contains("gp4") -> EmmcPartition.GP4.value
                else -> EmmcPartition.USER.value
            }
            Pair(StorageType.EMMC, section)
        }
    }

    /**
     * Flashes a partition using XFlash protocol (CMD_WRITE_DATA 0x010004).
     * Real USB I/O via MtkXFlashEngine.
     */
    suspend fun flashXFlash(
        partition: PartitionEntry,
        data: ByteArray,
        storageType: StorageType = StorageType.EMMC,
        partType: Int = EmmcPartition.USER.value
    ): Boolean {
        val startAddr = partition.startLinearAddress
        val totalLength = data.size.toLong()

        log(
            "[XFLASH] Flashing '${partition.partitionName}' @ 0x%X (Size: $totalLength bytes)".format(startAddr),
            LogLevel.INFO
        )

        val startTime = System.currentTimeMillis()
        val success = xflashEngine.writeFlash(
            storage = storageType.value,
            partType = partType,
            address = startAddr,
            length = totalLength,
            data = data
        ) { written, total ->
            val progress = (written.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.001)
            val speedKb = (written / 1024.0) / elapsedSec
            progressCallback(
                OperationProgress(
                    isRunning = true,
                    title = "Flashing ${partition.partitionName}...",
                    detail = "Written: ${written / 1024} KB / ${total / 1024} KB (%.1f KB/s)".format(speedKb),
                    percentage = progress * 100f
                )
            )
        }

        if (success) {
            log("[+] [FLASH SUCCESS]: Partition '${partition.partitionName}' written successfully.", LogLevel.SUCCESS)
        } else {
            log("[-] [FLASH FAILED]: Error writing '${partition.partitionName}' to flash.", LogLevel.ERROR)
        }
        return success
    }

    /**
     * Flashes a partition using Legacy DA protocol (0x62 SDMMC_WRITE_DATA).
     * Real USB endpoint I/O.
     */
    suspend fun flashLegacy(
        partition: PartitionEntry,
        data: ByteArray,
        storageType: StorageType = StorageType.EMMC,
        partType: Int = EmmcPartition.USER.value,
        chunkSize: Int = 0x20000
    ): Boolean {
        val startAddr = partition.startLinearAddress
        val totalLength = data.size.toLong()
        val padding = if (totalLength % 512L != 0L) (512L - (totalLength % 512L)).toInt() else 0
        val paddedLength = totalLength + padding

        log(
            "[LEGACY FLASH] Writing '${partition.partitionName}' @ 0x%X (Size: $totalLength bytes, Padded: $paddedLength bytes)".format(startAddr),
            LogLevel.INFO
        )

        val headerBuf = ByteBuffer.allocate(1 + 1 + 1 + 8 + 8 + 4).order(ByteOrder.BIG_ENDIAN)
        headerBuf.put(LEGACY_CMD_WRITE_DATA)
        headerBuf.put(storageType.value.toByte())
        headerBuf.put(partType.toByte())
        headerBuf.putLong(startAddr)
        headerBuf.putLong(paddedLength)
        headerBuf.putInt(chunkSize)

        val written = usb.writeRaw(headerBuf.array(), 2000)
        if (written <= 0) {
            log("[-] Legacy write header rejected by target.", LogLevel.ERROR)
            return false
        }

        val ackBuf = ByteArray(1)
        val ackRead = usb.readRaw(ackBuf, 3000)
        if (ackRead <= 0 || ackBuf[0] != LEGACY_ACK) {
            log("[-] Target did not ACK Legacy Write Command.", LogLevel.ERROR)
            return false
        }

        var offset = 0
        val startTime = System.currentTimeMillis()

        while (offset < totalLength) {
            usb.writeRaw(byteArrayOf(LEGACY_ACK), 1000)

            val currentChunkSize = minOf(chunkSize, (totalLength - offset).toInt())
            val chunkBuffer = ByteArray(currentChunkSize + if (offset + currentChunkSize >= totalLength) padding else 0)
            System.arraycopy(data, offset, chunkBuffer, 0, currentChunkSize)

            val wroteChunk = usb.writeRaw(chunkBuffer, 5000)
            if (wroteChunk <= 0) {
                log("[-] Write timeout at offset 0x%X".format(offset), LogLevel.ERROR)
                return false
            }

            val checksum = calculateChecksum16(chunkBuffer)
            val chksumBuf = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(checksum.toShort()).array()
            usb.writeRaw(chksumBuf, 1000)

            val contBuf = ByteArray(1)
            val contRead = usb.readRaw(contBuf, 3000)
            if (contRead <= 0 || contBuf[0] != LEGACY_CONT_CHAR) {
                log("[-] Chunk data ACK failed at offset 0x%X".format(offset), LogLevel.ERROR)
                return false
            }

            offset += currentChunkSize
            val progress = (offset.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f)
            val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.001)
            val speedKb = (offset / 1024.0) / elapsedSec
            progressCallback(
                OperationProgress(
                    isRunning = true,
                    title = "Flashing ${partition.partitionName}...",
                    detail = "Written: ${offset / 1024} KB / ${totalLength / 1024} KB (%.1f KB/s)".format(speedKb),
                    percentage = progress * 100f
                )
            )
        }

        log("[+] Legacy Flash Complete for '${partition.partitionName}' [100% OK]", LogLevel.SUCCESS)
        return true
    }

    private fun calculateChecksum16(data: ByteArray): Int {
        var sum = 0
        for (b in data) {
            sum = (sum + (b.toInt() and 0xFF)) and 0xFFFF
        }
        return sum
    }
}