package com.example.protocol

import com.example.model.LogLevel
import com.example.model.TerminalLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MediaTek Download Agent (DA) Upload Pipeline
 * Strictly ported from Python mtkclient: mtk_daloader.py & mtk_brom.py
 * Pure Native Implementation - No Mock Data.
 */
object MtkDaUploader {

    const val CMD_SEND_DA: Byte = 0xD7.toByte()
    const val CMD_JUMP_DA: Byte = 0xD5.toByte()
    const val STATUS_OK: Int = 0x0000

    private fun readU16BE(usb: TargetPhoneUsbManager, timeoutMs: Int = 2000): Int? {
        val buf = ByteArray(2)
        var totalRead = 0
        val startTime = System.currentTimeMillis()
        while (totalRead < 2 && (System.currentTimeMillis() - startTime < timeoutMs)) {
            val temp = ByteArray(2 - totalRead)
            val r = usb.readRaw(temp, timeoutMs)
            if (r > 0) {
                System.arraycopy(temp, 0, buf, totalRead, r)
                totalRead += r
            } else if (r < 0) return null
        }
        if (totalRead < 2) return null
        return ((buf[0].toInt() and 0xFF) shl 8) or (buf[1].toInt() and 0xFF)
    }

    suspend fun sendDa(
        usb: TargetPhoneUsbManager,
        daAddress: Long,
        daData: ByteArray,
        sigLen: Int = 0,
        logCallback: (TerminalLog) -> Unit,
        onProgress: (Float) -> Unit
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        logCallback(TerminalLog("", "[DA UPLOADER] Sending DA Stage to 0x%08X (Size: %d bytes, Sig: %d)".format(daAddress, daData.size, sigLen), LogLevel.INFO))

        // Ensure Clean Pipe & Clear Endpoint Halt from Exploit
        usb.clearEndpointHalt()
        usb.flush(20)
        delay(50)

        // 1. Send CMD_SEND_DA (0xD7) with Retry Loop
        var echoOk = false
        val cmdBytes = byteArrayOf(CMD_SEND_DA)
        for (attempt in 1..3) {
            val written = usb.writeRaw(cmdBytes, 1000)
            if (written > 0) {
                val echo = ByteArray(1)
                val read = usb.readRaw(echo, 1000)
                if (read > 0 && echo[0] == CMD_SEND_DA) {
                    echoOk = true
                    break
                }
            }
            usb.clearEndpointHalt()
            usb.flush(20)
            delay(100)
        }

        if (!echoOk) {
            return@withContext Result.failure(IllegalStateException("Failed writing CMD_SEND_DA command byte to USB."))
        }

        // 2. Send DA Header Block: Address (4B), Length (4B), SigLen (4B) in Big-Endian
        val paramBuf = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
        paramBuf.putInt((daAddress and 0xFFFFFFFFL).toInt())
        paramBuf.putInt(daData.size)
        paramBuf.putInt(sigLen)

        if (usb.writeRaw(paramBuf.array(), 1000) != 12) {
            return@withContext Result.failure(IllegalStateException("Failed writing DA parameter block to USB."))
        }

        // 3. Read Status Confirmation (0x0000 = OK)
        val status = readU16BE(usb, 2500)
        if (status == null || status != STATUS_OK) {
            val statHex = if (status != null) "0x%04X".format(status) else "Timeout"
            return@withContext Result.failure(IllegalStateException("DA upload parameter rejected by BootROM (Status: $statHex)."))
        }

        // 4. Stream DA Binary in 4KB Chunks
        val chunkSize = 4096
        val totalBytes = daData.size
        var offset = 0

        while (offset < totalBytes) {
            val thisChunk = minOf(chunkSize, totalBytes - offset)
            val chunk = ByteArray(thisChunk)
            System.arraycopy(daData, offset, chunk, 0, thisChunk)

            val w = usb.writeRaw(chunk, 2000)
            if (w != thisChunk) {
                return@withContext Result.failure(IllegalStateException("USB Pipe write error at offset $offset."))
            }

            offset += thisChunk
            onProgress(offset.toFloat() / totalBytes.toFloat())
        }

        // 5. Read BROM Checksum & Final Execution Status
        readU16BE(usb, 3000) // Read Checksum
        val finalStatus = readU16BE(usb, 3000)

        if (finalStatus == null || finalStatus != STATUS_OK) {
            val statHex = if (finalStatus != null) "0x%04X".format(finalStatus) else "Timeout"
            return@withContext Result.failure(IllegalStateException("DA Stage data transfer incomplete (Status: $statHex)."))
        }

        logCallback(TerminalLog("", "[DA UPLOADER] DA Stage successfully staged in target memory.", LogLevel.SUCCESS))
        return@withContext Result.success(true)
    }

    suspend fun jumpDa(
        usb: TargetPhoneUsbManager,
        daAddress: Long,
        logCallback: (TerminalLog) -> Unit
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        logCallback(TerminalLog("", "[DA UPLOADER] Jumping to DA entry point 0x%08X...".format(daAddress), LogLevel.INFO))

        // 1. Send CMD_JUMP_DA (0xD5)
        if (usb.writeRaw(byteArrayOf(CMD_JUMP_DA), 1000) <= 0) {
            return@withContext Result.failure(IllegalStateException("Failed writing CMD_JUMP_DA."))
        }

        val echo = ByteArray(1)
        val read = usb.readRaw(echo, 1000)
        if (read <= 0 || echo[0] != CMD_JUMP_DA) {
            return@withContext Result.failure(IllegalStateException("CMD_JUMP_DA echo mismatch."))
        }

        // 2. Send Jump Target Address
        val addrBuf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt((daAddress and 0xFFFFFFFFL).toInt()).array()
        if (usb.writeRaw(addrBuf, 1000) != 4) {
            return@withContext Result.failure(IllegalStateException("Failed writing DA jump address."))
        }

        // 3. Read Execution Status
        val status = readU16BE(usb, 2500)
        if (status == null || status != STATUS_OK) {
            val statHex = if (status != null) "0x%04X".format(status) else "Timeout"
            return@withContext Result.failure(IllegalStateException("DA jump execution rejected (Status: $statHex)."))
        }

        logCallback(TerminalLog("", "[DA UPLOADER] DA execution started successfully.", LogLevel.SUCCESS))
        return@withContext Result.success(true)
    }
}
