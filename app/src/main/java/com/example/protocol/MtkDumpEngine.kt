package com.example.protocol

import com.example.model.LogLevel
import com.example.model.OperationProgress
import com.example.model.PartitionEntry
import com.example.model.TerminalLog
import com.example.storage.BackupStorageManager
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MediaTek Native Firmware Read & Dump Engine
 * Real USB hardware read operations via XFlash & Legacy protocols - No simulation.
 * Ported from Python mtkclient/Library/DA/xflash/xflash_lib.py & dalegacy_lib.py
 */
class MtkDumpEngine(
    private val usb: TargetPhoneUsbManager,
    private val storageManager: BackupStorageManager,
    private val logCallback: (TerminalLog) -> Unit,
    private val progressCallback: (OperationProgress) -> Unit
) {
    private val xflashEngine = MtkXFlashEngine(usb, logCallback)

    companion object {
        const val LEGACY_CMD_READ = 0xD6.toByte()
        const val LEGACY_ACK: Byte = 0x5A.toByte()
    }

    private fun log(message: String, level: LogLevel = LogLevel.INFO) {
        logCallback(TerminalLog("", message, level))
    }

    /**
     * Dumps a single partition to storage safely via buffered stream.
     * Direct real USB I/O.
     */
    suspend fun dumpPartition(
        partition: PartitionEntry,
        outputFile: File,
        storageType: MtkFlashEngine.StorageType = MtkFlashEngine.StorageType.EMMC,
        partType: Int = MtkFlashEngine.EmmcPartition.USER.value,
        useXFlash: Boolean = true
    ): Result<String> {
        val startAddr = partition.startLinearAddress
        val totalBytes = if (partition.sizeBytes > 0) partition.sizeBytes else 4194304L

        log("[DUMP] Starting Dump for '${partition.partitionName}' @ 0x%X (Size: ${totalBytes / 1024} KB)".format(startAddr), LogLevel.INFO)
        log("[DUMP] Target File: ${outputFile.absolutePath}", LogLevel.INFO)

        if (outputFile.parentFile?.exists() == false) {
            outputFile.parentFile?.mkdirs()
        }

        val startTime = System.currentTimeMillis()
        val outStream = BufferedOutputStream(FileOutputStream(outputFile), 512 * 1024)

        try {
            if (useXFlash) {
                val success = xflashEngine.readFlashToStream(
                    storage = storageType.value,
                    partType = partType,
                    address = startAddr,
                    length = totalBytes,
                    outputStream = outStream,
                    chunkSize = xflashEngine.maxPacketSize
                ) { read, total ->
                    val progress = (read.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.001)
                    val speedKb = (read / 1024.0) / elapsedSec
                    progressCallback(
                        OperationProgress(
                            isRunning = true,
                            title = "Dumping ${partition.partitionName}...",
                            detail = "Dumped: ${read / 1024} KB / ${total / 1024} KB (%.1f KB/s)".format(speedKb),
                            percentage = progress * 100f
                        )
                    )
                }

                outStream.close()

                if (!success) {
                    outputFile.delete()
                    log("[-] [DUMP FAILED]: Target USB read error during partition dump.", LogLevel.ERROR)
                    return Result.failure(IllegalStateException("XFlash read failed for ${partition.partitionName}"))
                }
            } else {
                // Legacy DA Read
                val padding = if (totalBytes % 512L != 0L) (512L - (totalBytes % 512L)).toInt() else 0
                val paddedLength = totalBytes + padding
                val headerBuf = ByteBuffer.allocate(1 + 1 + 1 + 8 + 8 + 4).order(ByteOrder.BIG_ENDIAN)
                headerBuf.put(LEGACY_CMD_READ)
                headerBuf.put(storageType.value.toByte())
                headerBuf.put(partType.toByte())
                headerBuf.putLong(startAddr)
                headerBuf.putLong(paddedLength)
                headerBuf.putInt(65536)

                val written = usb.writeRaw(headerBuf.array(), 2000)
                if (written <= 0) {
                    outStream.close()
                    outputFile.delete()
                    log("[-] Legacy Read Header rejected by target.", LogLevel.ERROR)
                    return Result.failure(IllegalStateException("Legacy Read Header rejected"))
                }

                var processed = 0L
                val chunkSize = 65536
                val buffer = ByteArray(chunkSize)

                while (processed < totalBytes) {
                    val toRead = minOf(chunkSize.toLong(), totalBytes - processed).toInt()
                    val r = usb.readRaw(buffer, 3000)
                    if (r <= 0) {
                        outStream.close()
                        outputFile.delete()
                        log("[-] Read timeout at offset 0x%X".format(startAddr + processed), LogLevel.ERROR)
                        return Result.failure(IllegalStateException("USB Read Timeout"))
                    }
                    outStream.write(buffer, 0, minOf(r, toRead))
                    processed += r

                    val progress = (processed.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                    progressCallback(
                        OperationProgress(
                            isRunning = true,
                            title = "Dumping ${partition.partitionName}...",
                            detail = "Dumped: ${processed / 1024} KB / ${totalBytes / 1024} KB",
                            percentage = progress * 100f
                        )
                    )
                }
                outStream.close()
            }

            log("[+] [DUMP COMPLETE]: Partition '${partition.partitionName}' dumped to ${outputFile.name} (${outputFile.length()} bytes).", LogLevel.SUCCESS)
            return Result.success(outputFile.absolutePath)
        } catch (e: Exception) {
            try { outStream.close() } catch (_: Exception) {}
            outputFile.delete()
            log("[-] [DUMP ERROR]: ${e.message}", LogLevel.ERROR)
            return Result.failure(e)
        }
    }

    /**
     * Dumps all partitions to a target directory
     */
    suspend fun dumpAllPartitions(
        chipPlatform: String,
        partitions: List<PartitionEntry>,
        destinationDir: File,
        skipPartitions: List<String> = emptyList()
    ): Result<List<String>> {
        if (!destinationDir.exists()) destinationDir.mkdirs()
        val dumped = mutableListOf<String>()

        for ((idx, part) in partitions.withIndex()) {
            if (skipPartitions.any { it.equals(part.partitionName, ignoreCase = true) }) {
                log("[DUMP SKIP] Skipping ${part.partitionName}", LogLevel.INFO)
                continue
            }

            val outFile = File(destinationDir, "${part.partitionName}.bin")
            log("Dumping [${idx + 1}/${partitions.size}]: ${part.partitionName}...", LogLevel.INFO)
            val res = dumpPartition(part, outFile)
            if (res.isSuccess) {
                dumped.add(outFile.absolutePath)
            } else {
                log("[-] Error reading partition ${part.partitionName}", LogLevel.ERROR)
            }
        }
        return Result.success(dumped)
    }
}
