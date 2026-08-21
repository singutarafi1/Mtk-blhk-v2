package com.example.protocol

import com.example.model.LogLevel
import com.example.model.TerminalLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MediaTek BootROM DA Uploader & Execution Jump Engine
 * Faithfully ported from Python mtkclient (mtk_daloader.py & mtk_brom.py).
 * Pure Native Implementation - No Mock/Simulation Data.
 */
object MtkDaUploader {

    // MTK BROM Standard Command Codes
    const val CMD_SEND_DA: Byte = 0xD7.toByte()
    const val CMD_JUMP_DA: Byte = 0xD5.toByte()

    // BROM Status Codes
    const val STATUS_OK: Int = 0x0000
    const val STATUS_SLA_CHALLENGE: Int = 0x1D0D
    const val STATUS_DA_OVERLAP: Int = 0x1D0E
    const val STATUS_INVALID_JUMP_ADDR: Int = 0x1D0F

    // Buffer Transmission Chunk Size
    private const val STREAM_CHUNK_SIZE: Int = 4096

    /**
     * Calculates 16-bit XOR checksum of binary payload.
     * Matches Python mtkclient's `calc_xorsum16` algorithm exactly.
     */
    fun calculateChecksum16(data: ByteArray): Int {
        var chksum = 0
        var i = 0
        val len = data.size
        while (i < len) {
            val b0 = data[i].toInt() and 0xFF
            val b1 = if (i + 1 < len) (data[i + 1].toInt() and 0xFF) shl 8 else 0
            chksum = chksum xor (b0 or b1)
            i += 2
        }
        return chksum and 0xFFFF
    }

    /**
     * Reads a 16-bit unsigned integer (2 bytes, Big-Endian) from USB IN endpoint.
     */
    private fun readU16BE(usb: TargetPhoneUsbManager, timeoutMs: Int = 3000): Int? {
        val buf = ByteArray(2)
        var totalRead = 0
        val startTime = System.currentTimeMillis()

        while (totalRead < 2 && (System.currentTimeMillis() - startTime < timeoutMs)) {
            val temp = ByteArray(2 - totalRead)
            val read = usb.readRaw(temp, timeoutMs)
            if (read > 0) {
                System.arraycopy(temp, 0, buf, totalRead, read)
                totalRead += read
            } else if (read < 0) {
                return null
            }
        }

        if (totalRead < 2) return null
        return ((buf[0].toInt() and 0xFF) shl 8) or (buf[1].toInt() and 0xFF)
    }

    /**
     * Uploads DA (Download Agent) Stage to MediaTek target memory via BROM protocol.
     */
    suspend fun sendDa(
        usb: TargetPhoneUsbManager,
        daAddress: Long,
        daData: ByteArray,
        sigLen: Int = 0,
        logCallback: ((TerminalLog) -> Unit)? = null,
        onProgress: ((Float) -> Unit)? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        // Flush any unread dangling bytes in USB FIFO buffer
        usb.flush(50)

        val totalLen = daData.size
        if (totalLen <= 0) {
            return@withContext Result.failure(IllegalArgumentException("DA payload data is empty."))
        }

        logCallback?.invoke(
            TerminalLog("", "[DA UPLOADER] Sending DA Stage to 0x%08X (Size: $totalLen bytes, Sig: $sigLen)".format(daAddress), LogLevel.INFO)
        )

        // Step 1: Send CMD_SEND_DA (0xD7)
        val cmdWritten = usb.writeRaw(byteArrayOf(CMD_SEND_DA), 2000)
        if (cmdWritten <= 0) {
            return@withContext Result.failure(IllegalStateException("Failed writing CMD_SEND_DA command byte to USB."))
        }

        // Step 2: Read CMD_SEND_DA Echo (Must be 0xD7)
        val echoBuf = ByteArray(1)
        val echoRead = usb.readRaw(echoBuf, 2000)
        if (echoRead <= 0 || echoBuf[0] != CMD_SEND_DA) {
            val receivedHex = if (echoRead > 0) "0x%02X".format(echoBuf[0]) else "Timeout"
            return@withContext Result.failure(IllegalStateException("CMD_SEND_DA Echo mismatch. Expected 0xD7, received: $receivedHex"))
        }

        // Step 3: Send 12-byte Parameter Packet (Address, Size, SigLen - Big Endian)
        val paramBuf = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
        paramBuf.putInt((daAddress and 0xFFFFFFFFL).toInt())
        paramBuf.putInt(totalLen)
        paramBuf.putInt(sigLen)

        val paramsWritten = usb.writeRaw(paramBuf.array(), 2000)
        if (paramsWritten != 12) {
            return@withContext Result.failure(IllegalStateException("Failed sending 12-byte DA parameters."))
        }

        // Step 4: Read Parameter Acceptance Status (2 Bytes Big-Endian)
        val paramStatus = readU16BE(usb, 3000)
            ?: return@withContext Result.failure(IllegalStateException("Timeout waiting for DA parameter acknowledgment."))

        when (paramStatus) {
            STATUS_OK -> {
                logCallback?.invoke(TerminalLog("", "[DA UPLOADER] Target accepted DA parameters. Streaming payload...", LogLevel.INFO))
            }
            STATUS_SLA_CHALLENGE -> {
                return@withContext Result.failure(IllegalStateException("Target requested SLA Authentication Challenge (0x1D0D). Exploit or Key bypass required."))
            }
            STATUS_DA_OVERLAP -> {
                return@withContext Result.failure(IllegalStateException("Target reported DA Memory Overlap Error (0x1D0E)."))
            }
            else -> {
                val errDesc = MtkErrorCodes.decode(paramStatus)
                return@withContext Result.failure(IllegalStateException("CMD_SEND_DA rejected by SoC with code 0x%04X: $errDesc".format(paramStatus)))
            }
        }

        // Step 5: Stream Payload Binary in 4KB Chunks
        var offset = 0
        while (offset < totalLen) {
            val chunkLen = minOf(STREAM_CHUNK_SIZE, totalLen - offset)
            val chunk = daData.copyOfRange(offset, offset + chunkLen)
            val written = usb.writeRaw(chunk, 4000)
            if (written != chunkLen) {
                return@withContext Result.failure(IllegalStateException("USB bulk write failed at offset 0x%X (wrote $written of $chunkLen bytes)".format(offset)))
            }
            offset += chunkLen
            onProgress?.invoke(offset.toFloat() / totalLen.toFloat())
        }

        // Step 6: Read Target Checksum (2 Bytes) & Final Execution Status (2 Bytes)
        val devChecksum = readU16BE(usb, 5000)
            ?: return@withContext Result.failure(IllegalStateException("Timeout waiting for target DA checksum."))

        val finalStatus = readU16BE(usb, 5000)
            ?: return@withContext Result.failure(IllegalStateException("Timeout waiting for target DA final verification status."))

        val localChecksum = calculateChecksum16(daData)
        if (devChecksum != localChecksum) {
            return@withContext Result.failure(IllegalStateException("DA Checksum mismatch! Local: 0x%04X, Device: 0x%04X".format(localChecksum, devChecksum)))
        }

        if (finalStatus != STATUS_OK) {
            val errDesc = MtkErrorCodes.decode(finalStatus)
            return@withContext Result.failure(IllegalStateException("DA upload verification failed on SoC with status 0x%04X: $errDesc".format(finalStatus)))
        }

        logCallback?.invoke(TerminalLog("", "[+] DA Stage uploaded and verified successfully (Checksum: 0x%04X OK).".format(devChecksum), LogLevel.SUCCESS))
        return@withContext Result.success(true)
    }

    /**
     * Instructs target BootROM to branch execution to the uploaded DA Stage memory address.
     */
    suspend fun jumpDa(
        usb: TargetPhoneUsbManager,
        daAddress: Long,
        logCallback: ((TerminalLog) -> Unit)? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        logCallback?.invoke(TerminalLog("", "[DA UPLOADER] Instructing CPU to branch to DA at 0x%08X (CMD 0xD5)...".format(daAddress), LogLevel.INFO))

        // Step 1: Send CMD_JUMP_DA (0xD5)
        val cmdWritten = usb.writeRaw(byteArrayOf(CMD_JUMP_DA), 2000)
        if (cmdWritten <= 0) {
            return@withContext Result.failure(IllegalStateException("Failed writing CMD_JUMP_DA command byte to USB."))
        }

        // Step 2: Read CMD_JUMP_DA Echo (Must be 0xD5)
        val echoBuf = ByteArray(1)
        val echoRead = usb.readRaw(echoBuf, 2000)
        if (echoRead <= 0 || echoBuf[0] != CMD_JUMP_DA) {
            val receivedHex = if (echoRead > 0) "0x%02X".format(echoBuf[0]) else "Timeout"
            return@withContext Result.failure(IllegalStateException("CMD_JUMP_DA Echo mismatch. Expected 0xD5, received: $receivedHex"))
        }

        // Step 3: Send 4-byte Jump Address (Big Endian)
        val addrBuf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt((daAddress and 0xFFFFFFFFL).toInt()).array()
        val addrWritten = usb.writeRaw(addrBuf, 2000)
        if (addrWritten != 4) {
            return@withContext Result.failure(IllegalStateException("Failed sending DA jump target address."))
        }

        // Step 4: Read Execution Confirmation Status (2 Bytes Big-Endian)
        val jumpStatus = readU16BE(usb, 3000)
            ?: return@withContext Result.failure(IllegalStateException("Timeout waiting for CMD_JUMP_DA confirmation status."))

        if (jumpStatus != STATUS_OK) {
            val errDesc = MtkErrorCodes.decode(jumpStatus)
            return@withContext Result.failure(IllegalStateException("CMD_JUMP_DA rejected by SoC with status 0x%04X: $errDesc".format(jumpStatus)))
        }

        logCallback?.invoke(TerminalLog("", "[+] DA Execution Initialized: CPU branched to 0x%08X successfully.".format(daAddress), LogLevel.SUCCESS))
        
        // Short pause to allow target DRAM controller / DA runtime to stabilize
        delay(150)
        return@withContext Result.success(true)
    }
}
