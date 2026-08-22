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

    private fun echoBytes(usb: TargetPhoneUsbManager, data: ByteArray, timeoutMs: Int = 1000): Boolean {
        if (usb.writeRaw(data, timeoutMs) != data.size) return false
        val echo = ByteArray(data.size)
        val read = usb.readRaw(echo, timeoutMs)
        return read == data.size && echo.contentEquals(data)
    }

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

    private fun readU32BE(usb: TargetPhoneUsbManager, timeoutMs: Int = 2000): Long? {
        val buf = ByteArray(4)
        var totalRead = 0
        val startTime = System.currentTimeMillis()
        while (totalRead < 4 && (System.currentTimeMillis() - startTime < timeoutMs)) {
            val temp = ByteArray(4 - totalRead)
            val r = usb.readRaw(temp, timeoutMs)
            if (r > 0) {
                System.arraycopy(temp, 0, buf, totalRead, r)
                totalRead += r
            } else if (r < 0) return null
        }
        if (totalRead < 4) return null
        return ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
    }

    suspend fun sendDa(
        usb: TargetPhoneUsbManager,
        daAddress: Long,
        daData: ByteArray,
        sigLen: Int = 0,
        maxPacketSize: Int = 0x400,
        logCallback: (TerminalLog) -> Unit,
        onProgress: (Float) -> Unit
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val hasSig = sigLen in 1..daData.size
        val body = if (hasSig) daData.copyOfRange(0, daData.size - sigLen) else daData
        val sigTail = if (hasSig) daData.copyOfRange(daData.size - sigLen, daData.size) else ByteArray(0)
        var payload = body + sigTail
        if (payload.size % 2 != 0) payload += byteArrayOf(0)

        var genChecksum = 0
        var ci = 0
        while (ci < payload.size) {
            val word = (payload[ci].toInt() and 0xFF) or ((payload[ci + 1].toInt() and 0xFF) shl 8)
            genChecksum = genChecksum xor word
            ci += 2
        }
        genChecksum = genChecksum and 0xFFFF

        logCallback(TerminalLog("", "[DA UPLOADER] Sending DA Stage to 0x%08X (Size: %d bytes, Sig: %d)".format(daAddress, payload.size, sigLen), LogLevel.INFO))

        usb.clearEndpointHalt()
        usb.flush(20)
        delay(50)

        var echoOk = false
        for (attempt in 1..3) {
            if (echoBytes(usb, byteArrayOf(CMD_SEND_DA), 1000)) {
                echoOk = true
                break
            }
            usb.clearEndpointHalt()
            usb.flush(20)
            delay(100)
        }
        if (!echoOk) {
            return@withContext Result.failure(IllegalStateException("Failed writing CMD_SEND_DA command byte to USB (echo mismatch)."))
        }

        val addrBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
            .putInt((daAddress and 0xFFFFFFFFL).toInt()).array()
        if (!echoBytes(usb, addrBytes, 1000)) {
            return@withContext Result.failure(IllegalStateException("Failed writing DA address to USB (echo mismatch)."))
        }

        val lenBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(payload.size).array()
        if (!echoBytes(usb, lenBytes, 1000)) {
            return@withContext Result.failure(IllegalStateException("Failed writing DA length to USB (echo mismatch)."))
        }

        val sigLenBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(sigLen).array()
        if (!echoBytes(usb, sigLenBytes, 1000)) {
            return@withContext Result.failure(IllegalStateException("Failed writing DA sigLen to USB (echo mismatch)."))
        }

        val status = readU16BE(usb, 2500)
        if (status == null || status > 0xFF) {
            val statHex = if (status != null) "0x%04X".format(status) else "Timeout"
            return@withContext Result.failure(IllegalStateException("DA upload parameter rejected by BootROM (Status: $statHex)."))
        }

        val totalBytes = payload.size
        var offset = 0
        while (offset < totalBytes) {
            val thisChunk = minOf(maxPacketSize, totalBytes - offset)
            val chunk = payload.copyOfRange(offset, offset + thisChunk)

            val w = usb.writeRaw(chunk, 2000)
            if (w != thisChunk) {
                return@withContext Result.failure(IllegalStateException("USB Pipe write error at offset $offset."))
            }

            offset += thisChunk
            if (offset % 0x2000 == 0) {
                usb.writeRaw(ByteArray(0), 500)
            }
            onProgress(offset.toFloat() / totalBytes.toFloat())
        }
        usb.writeRaw(ByteArray(0), 500)
        delay(120)

        val checksum = readU16BE(usb, 3000)
        val finalStatus = readU16BE(usb, 3000)

        if (checksum != null && checksum != 0 && checksum != genChecksum) {
            logCallback(TerminalLog("", "[DA UPLOADER] Warning: checksum mismatch (got 0x%04X, expected 0x%04X).".format(checksum, genChecksum), LogLevel.WARNING))
        }
        if (finalStatus == null || finalStatus > 0xFF) {
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

        if (!echoBytes(usb, byteArrayOf(CMD_JUMP_DA), 1000)) {
            return@withContext Result.failure(IllegalStateException("CMD_JUMP_DA echo mismatch."))
        }

        val addrBuf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt((daAddress and 0xFFFFFFFFL).toInt()).array()
        if (usb.writeRaw(addrBuf, 1000) != 4) {
            return@withContext Result.failure(IllegalStateException("Failed writing DA jump address
