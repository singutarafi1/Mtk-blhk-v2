package com.example.protocol

import android.util.Log
import com.example.model.LogLevel
import com.example.model.TerminalLog
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.delay

/**
 * MediaTek BootROM DA Uploader & Jump Engine
 * Faithful port of Python mtkclient/Library/mtk_preloader.py: send_da() & jump_da()
 */
object MtkDaUploader {

    private const val TAG = "MtkDaUploader"

    const val CMD_SEND_DA: Byte = 0xD7.toByte()
    const val CMD_JUMP_DA: Byte = 0xD5.toByte()

    const val STATUS_OK: Int = 0x0000
    const val STATUS_SLA_CHALLENGE: Int = 0x1D0D

    /**
     * Calculates 16-bit XOR checksum matching Python mtkclient/Library/mtk_preloader.py.
     */
    fun calculateChecksum16(data: ByteArray): Int {
        var chksum = 0
        var i = 0
        val len = data.size
        while (i < len) {
            val b0 = data[i].toInt() and 0xFF
            val b1 = if (i + 1 < len) (data[i + 1].toInt() and 0xFF) shl 8 else 0
            val word = b0 or b1
            chksum = chksum xor word
            i += 2
        }
        return chksum and 0xFFFF
    }

    private suspend fun echoByte(usb: TargetPhoneUsbManager, cmd: Byte, timeoutMs: Int = 2000): Boolean {
        val wrote = usb.writeRaw(byteArrayOf(cmd), timeoutMs)
        if (wrote <= 0) return false
        val echoBuf = ByteArray(1)
        val read = usb.readRaw(echoBuf, timeoutMs)
        return read > 0 && echoBuf[0] == cmd
    }

    private suspend fun echoDword(usb: TargetPhoneUsbManager, value: Long, timeoutMs: Int = 2000): Boolean {
        val buf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value.toInt()).array()
        val wrote = usb.writeRaw(buf, timeoutMs)
        if (wrote <= 0) return false
        val echoBuf = ByteArray(4)
        val read = usb.readRaw(echoBuf, timeoutMs)
        return read >= 4 && echoBuf.contentEquals(buf)
    }

    private suspend fun readU16(usb: TargetPhoneUsbManager, timeoutMs: Int = 3000): Int? {
        val buf = ByteArray(2)
        val read = usb.readRaw(buf, timeoutMs)
        if (read < 2) return null
        return ((buf[0].toInt() and 0xFF) shl 8) or (buf[1].toInt() and 0xFF)
    }

    suspend fun sendDa(
        usb: TargetPhoneUsbManager,
        daAddress: Long,
        daData: ByteArray,
        sigLen: Int = 0,
        logCallback: ((TerminalLog) -> Unit)? = null,
        onProgress: ((Float) -> Unit)? = null
    ): Result<Boolean> {
        val totalLen = daData.size
        logCallback?.invoke(TerminalLog("", "[DA UPLOADER] Sending DA Stage 1 to 0x%08X (Size: $totalLen bytes, Sig: $sigLen)".format(daAddress), LogLevel.INFO))

        if (!echoByte(usb, CMD_SEND_DA)) {
            return Result.failure(IllegalStateException("Failed writing CMD_SEND_DA or echo mismatch"))
        }

        if (!echoDword(usb, daAddress)) {
            return Result.failure(IllegalStateException("Failed sending DA target address echo"))
        }
        if (!echoDword(usb, totalLen.toLong())) {
            return Result.failure(IllegalStateException("Failed sending DA size echo"))
        }
        if (!echoDword(usb, sigLen.toLong())) {
            return Result.failure(IllegalStateException("Failed sending DA sigLen echo"))
        }

        val statusCode = readU16(usb, 3000)
            ?: return Result.failure(IllegalStateException("Timeout waiting for CMD_SEND_DA acknowledgment"))

        if (statusCode == STATUS_SLA_CHALLENGE) {
            return Result.failure(IllegalStateException("Target requested SLA Authentication (0x1D0D)"))
        }
        if (statusCode != STATUS_OK) {
            return Result.failure(IllegalStateException("CMD_SEND_DA rejected with error code 0x%04X".format(statusCode)))
        }

        logCallback?.invoke(TerminalLog("", "[DA UPLOADER] Target acknowledged upload slot. Streaming binary...", LogLevel.INFO))

        val chunkSize = 4096
        var offset = 0
        while (offset < totalLen) {
            val chunkLen = minOf(chunkSize, totalLen - offset)
            val chunk = daData.copyOfRange(offset, offset + chunkLen)
            val wrote = usb.writeRaw(chunk, 3000)
            if (wrote <= 0) {
                return Result.failure(IllegalStateException("USB write timeout streaming DA data at offset 0x%X".format(offset)))
            }
            offset += chunkLen
            onProgress?.invoke(offset.toFloat() / totalLen.toFloat())
        }

        val devChecksum = readU16(usb, 4000)
            ?: return Result.failure(IllegalStateException("Timeout reading DA checksum verification response"))
        val finalStatus = readU16(usb, 4000)
            ?: return Result.failure(IllegalStateException("Timeout reading DA final status code"))

        val localChecksum = calculateChecksum16(daData)
        if (devChecksum != localChecksum) {
            return Result.failure(IllegalStateException("DA Checksum mismatch"))
        }
        if (finalStatus != STATUS_OK) {
            return Result.failure(IllegalStateException("DA verification rejected with status code 0x%04X".format(finalStatus)))
        }

        logCallback?.invoke(TerminalLog("", "[+] [DA UPLOAD SUCCESS]: DA Stage 1 verified (Checksum 0x%04X OK)".format(devChecksum), LogLevel.SUCCESS))
        return Result.success(true)
    }

    suspend fun jumpDa(
        usb: TargetPhoneUsbManager,
        daAddress: Long,
        logCallback: ((TerminalLog) -> Unit)? = null
    ): Result<Boolean> {
        logCallback?.invoke(TerminalLog("", "[DA UPLOADER] Jumping to DA @ 0x%08X (CMD 0xD5)...".format(daAddress), LogLevel.INFO))

        if (!echoByte(usb, CMD_JUMP_DA)) {
            return Result.failure(IllegalStateException("Failed sending CMD_JUMP_DA"))
        }

        if (!echoDword(usb, daAddress)) {
            return Result.failure(IllegalStateException("Failed sending DA jump target address echo"))
        }

        // Read 4-byte address echo and verify
        val echoBuf = ByteArray(4)
        val echoRead = usb.readRaw(echoBuf, 3000)
        if (echoRead < 4) {
            return Result.failure(IllegalStateException("Timeout reading JUMP_DA address echo"))
        }
        val expectedAddr = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(daAddress.toInt()).array()
        if (!echoBuf.contentEquals(expectedAddr)) {
            return Result.failure(IllegalStateException("JUMP_DA address echo mismatch"))
        }

        // Read 2-byte status
        val statusCode = readU16(usb, 3000)
            ?: return Result.failure(IllegalStateException("Timeout waiting for CMD_JUMP_DA confirmation"))

        if (statusCode != STATUS_OK) {
            return Result.failure(IllegalStateException("CMD_JUMP_DA rejected with code 0x%04X".format(statusCode)))
        }

        logCallback?.invoke(TerminalLog("", "[+] [DA EXECUTION INITIALIZED]: Target CPU branched to DA Stage 1.", LogLevel.SUCCESS))
        delay(100)
        return Result.success(true)
    }
}