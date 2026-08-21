package com.example.protocol

import com.example.model.LogLevel
import com.example.model.TerminalLog
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.delay

/**
 * MediaTek BootROM DA Uploader & Jump Engine
 * Status-only implementation (no echo dependency) for universal compatibility.
 */
object MtkDaUploader {

    private const val TAG = "MtkDaUploader"

    const val CMD_SEND_DA: Byte = 0xD7.toByte()
    const val CMD_JUMP_DA: Byte = 0xD5.toByte()

    const val STATUS_OK: Int = 0x0000
    const val STATUS_SLA_CHALLENGE: Int = 0x1D0D

    /**
     * Calculates 16-bit XOR checksum matching Python mtkclient.
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

    private suspend fun readU16(usb: TargetPhoneUsbManager, timeoutMs: Int = 3000): Int? {
        val buf = ByteArray(2)
        val read = usb.readRaw(buf, timeoutMs)
        if (read < 2) return null
        return ((buf[0].toInt() and 0xFF) shl 8) or (buf[1].toInt() and 0xFF)
    }

    /**
     * Uploads DA Stage 1 to target BROM. Does NOT rely on echo bytes.
     * Sends command + parameters, then reads 2-byte status.
     */
    suspend fun sendDa(
        usb: TargetPhoneUsbManager,
        daAddress: Long,
        daData: ByteArray,
        sigLen: Int = 0,
        logCallback: ((TerminalLog) -> Unit)? = null,
        onProgress: ((Float) -> Unit)? = null
    ): Result<Boolean> {
        // Flush any leftover USB data
        usb.flush(50)

        val totalLen = daData.size
        logCallback?.invoke(
            TerminalLog("", "[DA UPLOADER] Sending DA Stage 1 to 0x%08X (Size: $totalLen bytes, Sig: $sigLen)".format(daAddress), LogLevel.INFO)
        )

        // 1. Send CMD_SEND_DA (0xD7)
        if (usb.writeRaw(byteArrayOf(CMD_SEND_DA), 2000) <= 0) {
            return Result.failure(IllegalStateException("Failed writing CMD_SEND_DA"))
        }

        // 2. Send address, size, sigLen (big-endian)
        val paramBuf = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
        paramBuf.putInt(daAddress.toInt())
        paramBuf.putInt(totalLen)
        paramBuf.putInt(sigLen)
        if (usb.writeRaw(paramBuf.array(), 2000) <= 0) {
            return Result.failure(IllegalStateException("Failed sending DA parameters"))
        }

        // 3. Read status
        val statusCode = readU16(usb, 3000)
            ?: return Result.failure(IllegalStateException("Timeout waiting for CMD_SEND_DA acknowledgment"))

        if (statusCode == STATUS_SLA_CHALLENGE) {
            return Result.failure(IllegalStateException("Target requested SLA Authentication (0x1D0D)"))
        }
        if (statusCode != STATUS_OK) {
            return Result.failure(IllegalStateException("CMD_SEND_DA rejected with error code 0x%04X".format(statusCode)))
        }

        logCallback?.invoke(TerminalLog("", "[DA UPLOADER] Target accepted upload slot. Streaming binary...", LogLevel.INFO))

        // 4. Stream DA payload
        val chunkSize = 4096
        var offset = 0
        while (offset < totalLen) {
            val chunkLen = minOf(chunkSize, totalLen - offset)
            val chunk = daData.copyOfRange(offset, offset + chunkLen)
            val wrote = usb.writeRaw(chunk, 3000)
            if (wrote <= 0) {
                return Result.failure(IllegalStateException("USB write timeout at offset 0x%X".format(offset)))
            }
            offset += chunkLen
            onProgress?.invoke(offset.toFloat() / totalLen.toFloat())
        }

        // 5. Read checksum & final status
        val devChecksum = readU16(usb, 4000)
            ?: return Result.failure(IllegalStateException("Timeout reading DA checksum"))
        val finalStatus = readU16(usb, 4000)
            ?: return Result.failure(IllegalStateException("Timeout reading DA final status"))

        val localChecksum = calculateChecksum16(daData)
        if (devChecksum != localChecksum) {
            return Result.failure(IllegalStateException("DA Checksum mismatch (local=0x%04X, dev=0x%04X)".format(localChecksum, devChecksum)))
        }
        if (finalStatus != STATUS_OK) {
            return Result.failure(IllegalStateException("DA verification rejected with status 0x%04X".format(finalStatus)))
        }

        logCallback?.invoke(TerminalLog("", "[+] [DA UPLOAD SUCCESS]: DA Stage 1 verified (Checksum 0x%04X OK)".format(devChecksum), LogLevel.SUCCESS))
        return Result.success(true)
    }

    /**
     * Instructs target BROM to jump to DA Stage 1 address.
     * No echo verification, only status check.
     */
    suspend fun jumpDa(
        usb: TargetPhoneUsbManager,
        daAddress: Long,
        logCallback: ((TerminalLog) -> Unit)? = null
    ): Result<Boolean> {
        logCallback?.invoke(TerminalLog("", "[DA UPLOADER] Jumping to DA @ 0x%08X (CMD 0xD5)...".format(daAddress), LogLevel.INFO))

        // 1. Send CMD_JUMP_DA
        if (usb.writeRaw(byteArrayOf(CMD_JUMP_DA), 2000) <= 0) {
            return Result.failure(IllegalStateException("Failed sending CMD_JUMP_DA"))
        }

        // 2. Send address (big-endian)
        val addrBuf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(daAddress.toInt()).array()
        if (usb.writeRaw(addrBuf, 2000) <= 0) {
            return Result.failure(IllegalStateException("Failed sending DA jump address"))
        }

        // 3. Read status
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
