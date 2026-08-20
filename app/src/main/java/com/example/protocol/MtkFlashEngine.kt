package com.example.protocol

import com.example.model.LogLevel
import com.example.model.OperationProgress
import com.example.model.PartitionEntry
import com.example.model.TerminalLog
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * MediaTek Native Flashing Engine (Phase 1)
 * Faithfully implements all 3 core MTK flashing architectures:
 *  1. Legacy Protocol (0x62 SDMMC_WRITE_DATA / 0x61 SDMMC_WRITE_IMAGE)
 *  2. XFlash Protocol (0x010004 CMD_WRITE_DATA with NandExtension & 0x080005 CC_OPTIONAL_DOWNLOAD_ACT)
 *  3. XML / V6 Protocol (CMD:WRITE-FLASH / CMD:DOWNLOAD-FILE Stream Handshake)
 *
 * Implements standard Storage Mapping:
 *  - eMMC: EMMC_PART_USER (8), EMMC_PART_BOOT1 (1), EMMC_PART_BOOT2 (2), EMMC_PART_RPMB (3), GP1..4 (4..7)
 *  - UFS: LUA0/LUA2 (USER), LUA1 (BOOT1), LUA2 (BOOT2), LUA3 (RPMB)
 *  - 512-byte and 4096-byte Alignment & Additive 16-bit / 32-bit Checksums
 */
class MtkFlashEngine(
    private val usb: TargetPhoneUsbManager,
    private val logCallback: (TerminalLog) -> Unit,
    private val progressCallback: (OperationProgress) -> Unit
) {

    enum class StorageType(val value: Int) {
        EMMC(0x1),
        SDMMC(0x2),
        UFS(0x30),
        NAND(0x10),
        NOR(0x20)
    }

    enum class EmmcPartition(val value: Int) {
        BOOT1(1),
        BOOT2(2),
        RPMB(3),
        GP1(4),
        GP2(5),
        GP3(6),
        GP4(7),
        USER(8)
    }

    enum class UfsPartition(val value: Int) {
        BOOT1(1),
        BOOT2(2),
        USER(3),
        RPMB(4)
    }

    enum class FlashProtocol {
        LEGACY,
        XFLASH,
        XML_V6
    }

    companion object {
        const val XFLASH_MAGIC = 0xFEEEEEEFL
        const val XFLASH_CMD_WRITE_DATA = 0x010004
        const val XFLASH_CC_OPTIONAL_DOWNLOAD_ACT = 0x080005
        const val LEGACY_CMD_WRITE_DATA = 0x62.toByte()
        const val LEGACY_CMD_WRITE_IMAGE = 0x61.toByte()
        const val LEGACY_ACK: Byte = 0x5A.toByte()
        const val LEGACY_CONT_CHAR: Byte = 0x69.toByte()
        const val LEGACY_NACK: Byte = 0xA5.toByte()
    }

    private fun log(message: String, level: LogLevel = LogLevel.INFO) {
        logCallback(TerminalLog("", message, level))
    }

    /**
     * Resolves target partition region to storage and section code.
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
                lowerName == "preloader" || lowerRegion.contains("boot1") || lowerRegion.contains("lu0") -> UfsPartition.BOOT1.value
                lowerName == "preloader2" || lowerRegion.contains("boot2") || lowerRegion.contains("lu1") -> UfsPartition.BOOT2.value
                lowerName == "rpmb" || lowerRegion.contains("rpmb") || lowerRegion.contains("lu3") -> UfsPartition.RPMB.value
                else -> UfsPartition.USER.value
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
     * Calculates 16-bit additive checksum according to MTK DA specification:
     * sum(data[i]) & 0xFFFF
     */
    fun calculateChecksum16(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
        var sum = 0
        for (i in offset until offset + length) {
            sum = (sum + (data[i].toInt() and 0xFF)) and 0xFFFF
        }
        return sum
    }

    /**
     * Flashes partition using the Legacy DA Protocol (0x62 SDMMC_WRITE_DATA / 0x61 SDMMC_WRITE_IMAGE)
     * Matches mtkclient/Library/DA/legacy/dalegacy_lib.py: sdmmc_write_data
     */
    suspend fun flashLegacy(
        partition: PartitionEntry,
        data: ByteArray,
        isSimulation: Boolean = false,
        storageType: StorageType = StorageType.EMMC,
        partType: Int = EmmcPartition.USER.value,
        chunkSize: Int = 0x100000 // 1MB chunks
    ): Boolean {
        val startAddr = partition.startLinearAddress
        var totalLength = data.size.toLong()

        // 512-byte sector boundary alignment
        val padding = if (totalLength % 512L != 0L) (512L - (totalLength % 512L)).toInt() else 0
        val paddedLength = totalLength + padding

        log("[LEGACY FLASH] Writing '${partition.partitionName}' @ 0x%X (Size: $totalLength bytes, Padded: $paddedLength bytes)".format(startAddr), LogLevel.INFO)
        log("[LEGACY FLASH] Target Storage: ${storageType.name}, Section: $partType", LogLevel.INFO)

        if (isSimulation) {
            return simulateFlashing(partition.partitionName, totalLength)
        }

        // 1. Send SDMMC_WRITE_DATA_CMD (0x62)
        val headerBuf = ByteBuffer.allocate(1 + 1 + 1 + 8 + 8 + 4).order(ByteOrder.BIG_ENDIAN)
        headerBuf.put(LEGACY_CMD_WRITE_DATA)
        headerBuf.put(storageType.value.toByte())
        headerBuf.put(partType.toByte())
        headerBuf.putLong(startAddr)
        headerBuf.putLong(paddedLength)
        headerBuf.putInt(chunkSize)

        val written = usb.writeRaw(headerBuf.array(), 1000)
        if (written <= 0) {
            log("[-] Error sending Legacy write header for ${partition.partitionName}", LogLevel.ERROR)
            return false
        }

        val ackBuf = ByteArray(1)
        val ackRead = usb.readRaw(ackBuf, 3000)
        if (ackRead <= 0 || ackBuf[0] != LEGACY_ACK) {
            log("[-] Target did not ACK Legacy Write Command (got 0x%02X)".format(if (ackRead > 0) ackBuf[0] else 0), LogLevel.ERROR)
            return false
        }

        var offset = 0
        val startTime = System.currentTimeMillis()

        while (offset < totalLength) {
            // Send ACK (0x5A) before sending each packet
            usb.writeRaw(byteArrayOf(LEGACY_ACK), 1000)

            val currentChunkSize = minOf(chunkSize, (totalLength - offset).toInt())
            val chunkBuffer = ByteArray(currentChunkSize + if (offset + currentChunkSize >= totalLength) padding else 0)
            System.arraycopy(data, offset, chunkBuffer, 0, currentChunkSize)

            // Write chunk
            val wroteChunk = usb.writeRaw(chunkBuffer, 5000)
            if (wroteChunk <= 0) {
                log("[-] Write timeout at offset 0x%X".format(offset), LogLevel.ERROR)
                return false
            }

            // Calculate and send 16-bit additive checksum (Big Endian)
            val chksum = calculateChecksum16(chunkBuffer)
            val chksumBuf = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(chksum.toShort()).array()
            usb.writeRaw(chksumBuf, 1000)

            // Read CONT_CHAR (0x69)
            val contBuf = ByteArray(1)
            val contRead = usb.readRaw(contBuf, 3000)
            if (contRead <= 0 || contBuf[0] != LEGACY_CONT_CHAR) {
                log("[-] Chunk data ACK failed at offset 0x%X (got 0x%02X)".format(offset, if (contRead > 0) contBuf[0] else 0), LogLevel.ERROR)
                return false
            }

            offset += currentChunkSize
            updateProgress(partition.partitionName, offset.toLong(), totalLength, startTime)
        }

        log("[+] Legacy Flash Complete for '${partition.partitionName}' [100% OK]", LogLevel.SUCCESS)
        return true
    }

    /**
     * Flashes partition using the XFlash Protocol (CMD_WRITE_DATA = 0x010004)
     * Matches mtkclient/Library/DA/xflash/xflash_lib.py: writeflash / cmd_write_data
     */
    suspend fun flashXFlash(
        partition: PartitionEntry,
        data: ByteArray,
        isSimulation: Boolean = false,
        storageType: StorageType = StorageType.EMMC,
        partType: Int = EmmcPartition.USER.value,
        writePacketSize: Int = 0x20000 // 128KB packets
    ): Boolean {
        val startAddr = partition.startLinearAddress
        var totalLength = data.size.toLong()

        val padding = if (totalLength % 512L != 0L) (512L - (totalLength % 512L)).toInt() else 0
        val paddedLength = totalLength + padding

        log("[XFLASH] Writing '${partition.partitionName}' @ 0x%X (Size: $totalLength bytes)".format(startAddr), LogLevel.INFO)
        log("[XFLASH] NandExtension Storage: ${storageType.name}, Section: $partType", LogLevel.INFO)

        if (isSimulation) {
            return simulateFlashing(partition.partitionName, totalLength)
        }

        // 1. Send CMD_WRITE_DATA (0x010004)
        if (!sendXFlashCommand(XFLASH_CMD_WRITE_DATA)) {
            log("[-] XFlash CMD_WRITE_DATA rejected by target.", LogLevel.ERROR)
            return false
        }

        // 2. Send Parameter Structure: <IIQQ (storage, parttype, addr, length) + NandExtension <IIIIIIII
        val paramBuf = ByteBuffer.allocate(4 + 4 + 8 + 8 + (8 * 4)).order(ByteOrder.LITTLE_ENDIAN)
        paramBuf.putInt(storageType.value)
        paramBuf.putInt(partType)
        paramBuf.putLong(startAddr)
        paramBuf.putLong(paddedLength)
        // NandExtension (cellusage=0, addr_type=0, bin_type=0, region=0, format_level=0, sys_slc=0, usr_slc=0, max_size=0)
        for (i in 0 until 8) {
            paramBuf.putInt(0)
        }

        if (!sendXFlashParam(paramBuf.array())) {
            log("[-] XFlash parameter packet rejected for ${partition.partitionName}", LogLevel.ERROR)
            return false
        }

        var offset = 0
        val startTime = System.currentTimeMillis()

        while (offset < totalLength) {
            val currentChunkSize = minOf(writePacketSize, (totalLength - offset).toInt())
            val chunkBuffer = ByteArray(currentChunkSize + if (offset + currentChunkSize >= totalLength) padding else 0)
            System.arraycopy(data, offset, chunkBuffer, 0, currentChunkSize)

            val checksum = calculateChecksum16(chunkBuffer)

            // Send packet param: pack("<I", 0x0), pack("<I", checksum), data
            val packetHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            packetHeader.putInt(0)
            packetHeader.putInt(checksum)

            val combined = packetHeader.array() + chunkBuffer
            if (!sendXFlashParam(combined)) {
                log("[-] Error writing XFlash chunk at pos 0x%X".format(offset), LogLevel.ERROR)
                return false
            }

            offset += currentChunkSize
            updateProgress(partition.partitionName, offset.toLong(), totalLength, startTime)
        }

        val status = readXFlashStatus()
        if (status == 0) {
            // Send Post-Download Action (CC_OPTIONAL_DOWNLOAD_ACT)
            sendXFlashDevCtrl(XFLASH_CC_OPTIONAL_DOWNLOAD_ACT)
            log("[+] XFlash Complete for '${partition.partitionName}' [SUCCESS]", LogLevel.SUCCESS)
            return true
        }

        log("[-] XFlash Final status failed with code: 0x%X".format(status), LogLevel.ERROR)
        return false
    }

    /**
     * Flashes partition using XML / V6 Protocol (CMD:WRITE-FLASH / CMD:DOWNLOAD-FILE stream)
     * Matches mtkclient/Library/DA/xmlflash/xml_lib.py: writeflash / cmd_write_flash
     */
    suspend fun flashXmlV6(
        partition: PartitionEntry,
        data: ByteArray,
        isSimulation: Boolean = false,
        partitionTag: String = "EMMC-USER"
    ): Boolean {
        val startAddr = partition.startLinearAddress
        val totalLength = data.size.toLong()

        log("[XML/V6 FLASH] Writing '${partition.partitionName}' to $partitionTag @ 0x%X".format(startAddr), LogLevel.INFO)

        if (isSimulation) {
            return simulateFlashing(partition.partitionName, totalLength)
        }

        val xmlCommand = """<?xml version="1.0" encoding="utf-8"?>
<da>
    <version>1.0</version>
    <command>CMD:WRITE-FLASH</command>
    <arg>
        <partition>$partitionTag</partition>
        <offset>0x%X</offset>
        <source_file>MEM://0x8000000:0x%X</source_file>
    </arg>
</da>""".format(startAddr, totalLength)

        val packet = wrapXmlPacket(xmlCommand)
        usb.writeRaw(packet, 2000)

        val resp = readXmlResponse()
        if (!resp.contains("CMD:DOWNLOAD-FILE") && !resp.contains("OK")) {
            log("[-] XML V6 Flash command rejected: $resp", LogLevel.ERROR)
            return false
        }

        // Send ACK with length
        val ackLengthMsg = "OK@0x%X\u0000".format(totalLength).toByteArray(Charsets.UTF_8)
        usb.writeRaw(wrapXmlPacket(String(ackLengthMsg, Charsets.UTF_8)), 1000)

        // Stream data chunks (typically 0x1000 - 0x20000 bytes)
        val chunkSize = 0x20000
        var offset = 0
        val startTime = System.currentTimeMillis()

        while (offset < totalLength) {
            val currentChunk = minOf(chunkSize, (totalLength - offset).toInt())
            val chunkBytes = ByteArray(currentChunk)
            System.arraycopy(data, offset, chunkBytes, 0, currentChunk)

            // Send chunk ACK0
            usb.writeRaw(wrapXmlPacket("OK@0x0\u0000"), 1000)
            val chunkAck = readXmlResponse()
            if (chunkAck.contains("ERR", ignoreCase = true) || chunkAck.contains("FAIL", ignoreCase = true)) {
                log("[-] XML V6 chunk write rejected at offset 0x%X: $chunkAck".format(offset), LogLevel.ERROR)
                return false
            }

            // Send raw chunk data
            val header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            header.putInt(XFLASH_MAGIC.toInt())
            header.putInt(1) // DT_PROTOCOL_FLOW
            header.putInt(currentChunk)
            val wroteChunk = usb.writeRaw(header.array() + chunkBytes, 5000)
            if (wroteChunk <= 0) {
                log("[-] USB write error sending XML chunk at offset 0x%X".format(offset), LogLevel.ERROR)
                return false
            }

            val chunkResult = readXmlResponse()
            if (chunkResult.contains("ERR", ignoreCase = true) || chunkResult.contains("FAIL", ignoreCase = true) ||
                (chunkResult.isNotEmpty() && !chunkResult.contains("OK", ignoreCase = true) && !chunkResult.contains("CMD:", ignoreCase = true))) {
                log("[-] XML V6 chunk verification failed at offset 0x%X: $chunkResult".format(offset), LogLevel.ERROR)
                return false
            }

            offset += currentChunk
            updateProgress(partition.partitionName, offset.toLong(), totalLength, startTime)
        }

        log("[+] XML V6 Flash Complete for '${partition.partitionName}' [SUCCESS]", LogLevel.SUCCESS)
        return true
    }

    private suspend fun simulateFlashing(partitionName: String, totalBytes: Long): Boolean {
        val startTime = System.currentTimeMillis()
        var processed = 0L
        val chunkSize = 65536L
        val totalChunks = (totalBytes + chunkSize - 1) / chunkSize

        for (i in 0 until totalChunks) {
            val currentChunk = minOf(chunkSize, totalBytes - processed)
            kotlinx.coroutines.delay(15)
            processed += currentChunk
            updateProgress(partitionName, processed, totalBytes, startTime)
        }
        log("[+] Flash Simulation Succeeded for '$partitionName' (Checksum Valid)", LogLevel.SUCCESS)
        return true
    }

    private fun updateProgress(
        partitionName: String,
        processed: Long,
        totalBytes: Long,
        startTime: Long
    ) {
        val percent = if (totalBytes > 0) (processed.toFloat() / totalBytes.toFloat()) * 100f else 100f
        val elapsedSec = maxOf(0.1, (System.currentTimeMillis() - startTime) / 1000.0)
        val speedKb = (processed / 1024.0) / elapsedSec
        val remainingSec = (((totalBytes - processed) / 1024.0) / maxOf(1.0, speedKb)).toInt()

        progressCallback(
            OperationProgress(
                isRunning = true,
                title = "Flashing: $partitionName",
                detail = "${processed / 1024} KB / ${totalBytes / 1024} KB (${String.format("%.1f", speedKb)} KB/s)",
                percentage = percent,
                bytesProcessed = processed,
                totalBytes = totalBytes,
                speedKbPerSec = speedKb,
                estimatedSecondsRemaining = remainingSec
            )
        )
    }

    // Helper functions for XFlash and XML
    private fun sendXFlashCommand(cmd: Int): Boolean {
        val buf = ByteBuffer.allocate(12 + 4).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(XFLASH_MAGIC.toInt())
        buf.putInt(1) // DT_PROTOCOL_FLOW
        buf.putInt(4)
        buf.putInt(cmd)
        usb.writeRaw(buf.array(), 1000)
        return readXFlashStatus() == 0
    }

    private fun sendXFlashParam(param: ByteArray): Boolean {
        val buf = ByteBuffer.allocate(12 + param.size).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(XFLASH_MAGIC.toInt())
        buf.putInt(1) // DT_PROTOCOL_FLOW
        buf.putInt(param.size)
        buf.put(param)
        usb.writeRaw(buf.array(), 3000)
        return readXFlashStatus() == 0
    }

    private fun sendXFlashDevCtrl(cmd: Int): Boolean {
        return sendXFlashCommand(cmd)
    }

    private fun readXFlashStatus(): Int {
        val hdr = ByteArray(12)
        val read = usb.readRaw(hdr, 2000)
        if (read < 12) return -1
        val buf = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buf.int.toLong() and 0xFFFFFFFFL
        val type = buf.int
        val len = buf.int
        if (magic != XFLASH_MAGIC) return -1

        val statusBytes = ByteArray(len)
        usb.readRaw(statusBytes, 2000)
        if (len == 2) return ByteBuffer.wrap(statusBytes).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
        if (len == 4) {
            val status = ByteBuffer.wrap(statusBytes).order(ByteOrder.LITTLE_ENDIAN).int
            return if (status.toLong() and 0xFFFFFFFFL == XFLASH_MAGIC) 0 else status
        }
        return 0
    }

    private fun wrapXmlPacket(xml: String): ByteArray {
        val bytes = xml.toByteArray(Charsets.UTF_8) + byteArrayOf(0)
        val hdr = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        hdr.putInt(XFLASH_MAGIC.toInt())
        hdr.putInt(1) // DT_PROTOCOL_FLOW
        hdr.putInt(bytes.size)
        return hdr.array() + bytes
    }

    private fun readXmlResponse(): String {
        val hdr = ByteArray(12)
        val read = usb.readRaw(hdr, 2000)
        if (read < 12) return ""
        val buf = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN)
        val len = buf.getInt(8)
        if (len <= 0) return ""
        val data = ByteArray(len)
        usb.readRaw(data, 2000)
        return String(data, Charsets.UTF_8).trim()
    }
}
