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
     * Calculates 16-bit XOR checksum matching Python mtkclient/Library/mtk_preloader.py:
     *
     * def checksum(data):
     *     chksum = 0
     *     for i in range(0, len(data), 2):
     *         val = data[i] | (data[i + 1] << 8) if i + 1 < len(data) else data[i]
     *         chksum ^= val
     *     return chksum & 0xFFFF
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

    /**
     * Sends a 1-byte command and verifies the 1-byte echo from BootROM
     */
    private suspend fun echoByte(usb: TargetPhoneUsbManager, cmd: Byte, timeoutMs: Int = 2000): Boolean {
        val wrote = usb.writeRaw(byteArrayOf(cmd), timeoutMs)
        if (wrote <= 0) return false

        val echoBuf = ByteArray(1)
        val read = usb.readRaw(echoBuf, timeoutMs)
        if (read <= 0) return false
        return echoBuf[0] == cmd
    }

    /**
     * Sends a 4-byte big-endian dword and verifies the 4-byte echo from BootROM
     */
    private suspend fun echoDword(usb: TargetPhoneUsbManager, value: Long, timeoutMs: Int = 2000): Boolean {
        val buf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value.toInt()).array()
        val wrote = usb.writeRaw(buf, timeoutMs)
        if (wrote <= 0) return false

        val echoBuf = ByteArray(4)
        val read = usb.readRaw(echoBuf, timeoutMs)
        if (read < 4) return false
        return echoBuf.contentEquals(buf)
    }

    /**
     * Reads a 2-byte big-endian unsigned integer (u16)
     */
    private suspend fun readU16(usb: TargetPhoneUsbManager, timeoutMs: Int = 3000): Int? {
        val buf = ByteArray(2)
        val read = usb.readRaw(buf, timeoutMs)
        if (read < 2) return null
        return ((buf[0].toInt() and 0xFF) shl 8) or (buf[1].toInt() and 0xFF)
    }

    /**
     * Uploads Download Agent (DA) binary into Target SRAM/DRAM via BROM CMD SEND_DA (0xD7)
     * Direct port of mtkclient send_da(address, size, sig_len, dadata)
     */
    suspend fun sendDa(
        usb: TargetPhoneUsbManager,
        daAddress: Long,
        daData: ByteArray,
        sigLen: Int = 0,
        logCallback: ((TerminalLog) -> Unit)? = null,
        onProgress: ((Float) -> Unit)? = null
    ): Result<Boolean> {
        val totalLen = daData.size
        logCallback?.invoke(
            TerminalLog(
                "",
                "[DA UPLOADER] Sending DA Stage 1 to 0x%08X (Size: $totalLen bytes, Sig: $sigLen)".format(daAddress),
                LogLevel.INFO
            )
        )

        // 1. Send CMD_SEND_DA (0xD7) with echo
        if (!echoByte(usb, CMD_SEND_DA)) {
            logCallback?.invoke(TerminalLog("", "[-] Failed sending CMD_SEND_DA (0xD7) or echo mismatch.", LogLevel.ERROR))
            return Result.failure(IllegalStateException("Failed writing CMD_SEND_DA (0xD7) or echo mismatch"))
        }

        // 2. Send 4-byte Target Address, 4-byte Length, 4-byte Sig Length (Big Endian with echo)
        if (!echoDword(usb, daAddress)) {
            logCallback?.invoke(TerminalLog("", "[-] Target Address 0x%08X echo verification failed.".format(daAddress), LogLevel.ERROR))
            return Result.failure(IllegalStateException("Failed sending DA target address echo"))
        }
        if (!echoDword(usb, totalLen.toLong())) {
            logCallback?.invoke(TerminalLog("", "[-] DA Size ($totalLen) echo verification failed.", LogLevel.ERROR))
            return Result.failure(IllegalStateException("Failed sending DA size echo"))
        }
        if (!echoDword(usb, sigLen.toLong())) {
            logCallback?.invoke(TerminalLog("", "[-] DA SigLen ($sigLen) echo verification failed.", LogLevel.ERROR))
            return Result.failure(IllegalStateException("Failed sending DA sigLen echo"))
        }

        // 3. Read 2-byte command status response (expect 0x0000)
        val statusCode = readU16(usb, 3000)
            ?: return Result.failure(IllegalStateException("Timeout waiting for CMD_SEND_DA acknowledgment from target"))

        if (statusCode == STATUS_SLA_CHALLENGE) {
            logCallback?.invoke(TerminalLog("", "[-] Target requested SLA Authentication (0x1D0D). Security bypass required.", LogLevel.ERROR))
            return Result.failure(IllegalStateException("Target requested SLA Authentication (0x1D0D)"))
        }

        if (statusCode != STATUS_OK) {
            logCallback?.invoke(TerminalLog("", "[-] CMD_SEND_DA rejected with error code 0x%04X".format(statusCode), LogLevel.ERROR))
            return Result.failure(IllegalStateException("CMD_SEND_DA rejected with error code 0x%04X".format(statusCode)))
        }

        logCallback?.invoke(TerminalLog("", "[DA UPLOADER] Target acknowledged DA upload slot. Streaming binary...", LogLevel.INFO))

        // 4. Stream DA Payload in chunks
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

        // 5. Read 2-byte Checksum response & 2-byte Final Status
        val devChecksum = readU16(usb, 4000)
            ?: return Result.failure(IllegalStateException("Timeout reading DA checksum verification response"))

        val finalStatus = readU16(usb, 4000)
            ?: return Result.failure(IllegalStateException("Timeout reading DA final status code"))

        val localChecksum = calculateChecksum16(daData)

        if (devChecksum != localChecksum) {
            logCallback?.invoke(
                TerminalLog(
                    "",
                    "[-] [DA CHECKSUM MISMATCH]: Local 0x%04X != Device 0x%04X".format(localChecksum, devChecksum),
                    LogLevel.ERROR
                )
            )
            return Result.failure(IllegalStateException("DA Checksum mismatch (0x%04X != 0x%04X)".format(localChecksum, devChecksum)))
        }

        if (finalStatus != STATUS_OK) {
            logCallback?.invoke(
                TerminalLog(
                    "",
                    "[-] DA verification rejected with status code 0x%04X".format(finalStatus),
                    LogLevel.ERROR
                )
            )
            return Result.failure(IllegalStateException("DA verification rejected with status code 0x%04X".format(finalStatus)))
        }

        logCallback?.invoke(
            TerminalLog(
                "",
                "[+] [DA UPLOAD SUCCESS]: DA Stage 1 verified (Checksum 0x%04X OK)".format(devChecksum),
                LogLevel.SUCCESS
            )
        )
        return Result.success(true)
    }

    /**
     * Instructs target BROM to jump execution into uploaded DA address via CMD JUMP_DA (0xD5)
     * Direct port of mtkclient jump_da(addr)
     */
    suspend fun jumpDa(
        usb: TargetPhoneUsbManager,
        daAddress: Long,
        logCallback: ((TerminalLog) -> Unit)? = null
    ): Result<Boolean> {
        logCallback?.invoke(
            TerminalLog(
                "",
                "[DA UPLOADER] Jumping execution to DA @ 0x%08X (CMD 0xD5)...".format(daAddress),
                LogLevel.INFO
            )
        )

        // 1. Send CMD_JUMP_DA (0xD5) with echo
        if (!echoByte(usb, CMD_JUMP_DA)) {
            logCallback?.invoke(TerminalLog("", "[-] Failed sending CMD_JUMP_DA (0xD5) or echo mismatch.", LogLevel.ERROR))
            return Result.failure(IllegalStateException("Failed sending CMD_JUMP_DA (0xD5)"))
        }

        // 2. Send 4-byte Target Address (Big Endian with echo)
        if (!echoDword(usb, daAddress)) {
            logCallback?.invoke(TerminalLog("", "[-] JUMP_DA target address echo verification failed.", LogLevel.ERROR))
            return Result.failure(IllegalStateException("Failed sending DA jump target address echo"))
        }

        // 3. Read 2-byte response status
        val statusCode = readU16(usb, 3000)
            ?: return Result.failure(IllegalStateException("Timeout waiting for CMD_JUMP_DA confirmation"))

        if (statusCode != STATUS_OK) {
            logCallback?.invoke(
                TerminalLog(
                    "",
                    "[-] CMD_JUMP_DA rejected by target with code 0x%04X".format(statusCode),
                    LogLevel.ERROR
                )
            )
            return Result.failure(IllegalStateException("CMD_JUMP_DA rejected by target with code 0x%04X".format(statusCode)))
        }

        logCallback?.invoke(
            TerminalLog(
                "",
                "[+] [DA EXECUTION INITIALIZED]: Target CPU branched to DA Stage 1.",
                LogLevel.SUCCESS
            )
        )
        delay(100) // Allow DA firmware to initialize hardware PLL and clocks
        return Result.success(true)
    }
}
