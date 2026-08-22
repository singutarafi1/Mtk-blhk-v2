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
 * Strictly ported from Python mtkclient:
 * - mtkclient/Library/Preloader/preloader.py (send_da, jump_da, upload_data, prepare_data)
 * - mtkclient/Library/mtk_brom.py
 * 100% Real Protocol Implementation - No Mock Data.
 */
object MtkDaUploader {

    const val CMD_SEND_DA: Byte = 0xD7.toByte()
    const val CMD_JUMP_DA: Byte = 0xD5.toByte()
    const val STATUS_OK: Int = 0x0000

    /**
     * Purges all stale leftover bytes from the USB IN endpoint FIFO.
     */
    private fun purgeFifo(usb: TargetPhoneUsbManager) {
        val discardBuf = ByteArray(1024)
        while (true) {
            val read = usb.readRaw(discardBuf, 15)
            if (read <= 0) break
        }
    }

    /**
     * Replicates Python echo(data):
     * Writes binary data and immediately reads back exactly len(data) bytes.
     */
    private fun echo(usb: TargetPhoneUsbManager, data: ByteArray, timeoutMs: Int = 1000): Boolean {
        val written = usb.writeRaw(data, timeoutMs)
        if (written != data.size) return false

        val rx = ByteArray(data.size)
        var totalRead = 0
        val startTime = System.currentTimeMillis()

        while (totalRead < data.size && (System.currentTimeMillis() - startTime < timeoutMs)) {
            val temp = ByteArray(data.size - totalRead)
            val r = usb.readRaw(temp, timeoutMs)
            if (r > 0) {
                System.arraycopy(temp, 0, rx, totalRead, r)
                totalRead += r
            } else if (r < 0) return false
        }

        if (totalRead != data.size) return false
        return data.contentEquals(rx)
    }

    private fun echoWord32(usb: TargetPhoneUsbManager, value: Long, timeoutMs: Int = 1000): Boolean {
        val buf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt((value and 0xFFFFFFFFL).toInt()).array()
        return echo(usb, buf, timeoutMs)
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

    /**
     * Replicates Python prepare_data(dadata[:-sig_len], dadata[-sig_len:], size)
     */
    private fun prepareData(daData: ByteArray, sigLen: Int): Pair<Int, ByteArray> {
        val dataLen = daData.size - sigLen
        val rawPayload = if (sigLen > 0 && dataLen > 0) {
            daData.copyOfRange(0, dataLen)
        } else {
            daData
        }

        val paddedPayload = if (rawPayload.size % 2 != 0) {
            val p = ByteArray(rawPayload.size + 1)
            System.arraycopy(rawPayload, 0, p, 0, rawPayload.size)
            p[rawPayload.size] = 0x00
            p
        } else {
            rawPayload
        }

        val fullData = if (sigLen > 0 && dataLen > 0) {
            val sigData = daData.copyOfRange(dataLen, daData.size)
            val combined = ByteArray(paddedPayload.size + sigData.size)
            System.arraycopy(paddedPayload, 0, combined, 0, paddedPayload.size)
            System.arraycopy(sigData, 0, combined, paddedPayload.size, sigData.size)
            combined
        } else {
            paddedPayload
        }

        var checksum = 0
        val buf = ByteBuffer.wrap(fullData).order(ByteOrder.LITTLE_ENDIAN)
        while (buf.remaining() >= 2) {
            checksum = checksum xor (buf.short.toInt() and 0xFFFF)
        }
        if (buf.remaining() == 1) {
            checksum = checksum xor (buf.get().toInt() and 0xFF)
        }

        return Pair(checksum and 0xFFFF, fullData)
    }

    /**
     * Uploads DA Stage 1 (DA_PL) matching Python Preloader.send_da() exactly.
     */
    suspend fun sendDa(
        usb: TargetPhoneUsbManager,
        daAddress: Long,
        daData: ByteArray,
        sigLen: Int = 0,
        logCallback: (TerminalLog) -> Unit,
        onProgress: (Float) -> Unit
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        logCallback(TerminalLog("", "[DA UPLOADER] Preparing DA Stage (Input: %d bytes, Sig: %d)".format(daData.size, sigLen), LogLevel.INFO))

        val (expectedChecksum, preparedPayload) = prepareData(daData, sigLen)
        val payloadSize = preparedPayload.size.toLong()

        logCallback(TerminalLog("", "[DA UPLOADER] Sending DA Header: Addr=0x%08X, Len=%d, SigLen=%d".format(daAddress, payloadSize, sigLen), LogLevel.INFO))

        // Clean FIFO thoroughly before starting BROM SEND_DA exchange
        purgeFifo(usb)
        delay(50)

        // 1. Step 1: Send CMD_SEND_DA (0xD7) -> Verify 1-byte Echo
        var echoCmdOk = false
        for (retry in 1..3) {
            if (echo(usb, byteArrayOf(CMD_SEND_DA), 500)) {
                echoCmdOk = true
                break
            }
            purgeFifo(usb)
            delay(50)
        }

        if (!echoCmdOk) {
            return@withContext Result.failure(IllegalStateException("BROM rejected CMD_SEND_DA (0xD7 echo mismatch)."))
        }

        // 2. Step 2: Send Address (4B) -> Verify 4-byte Echo
        if (!echoWord32(usb, daAddress, 1000)) {
            return@withContext Result.failure(IllegalStateException("BROM rejected DA Address (0x%08X echo mismatch).".format(daAddress)))
        }

        // 3. Step 3: Send Length (4B) -> Verify 4-byte Echo
        if (!echoWord32(usb, payloadSize, 1000)) {
            return@withContext Result.failure(IllegalStateException("BROM rejected DA Length (%d echo mismatch).".format(payloadSize)))
        }

        // 4. Step 4: Send Sig_Len (4B) -> Verify 4-byte Echo
        if (!echoWord32(usb, sigLen.toLong(), 1000)) {
            return@withContext Result.failure(IllegalStateException("BROM rejected DA Sig_Len (%d echo mismatch).".format(sigLen)))
        }

        // 5. Step 5: Read Status Word (2 bytes, 0x0000 = OK)
        val status = readU16BE(usb, 2000)
        if (status == null || status != STATUS_OK) {
            val statHex = if (status != null) "0x%04X".format(status) else "Timeout"
            return@withContext Result.failure(IllegalStateException("DA Header rejected by BootROM (Status: $statHex)."))
        }

        logCallback(TerminalLog("", "[+] BROM accepted DA parameters (0x0000 OK). Streaming payload...", LogLevel.SUCCESS))

        // 6. Upload Payload Data with 0x2000 ZLP Framing
        val pktsize = 1024
        var pos = 0
        var zlpCounter = 0
        val totalBytes = preparedPayload.size

        while (pos < totalBytes) {
            val chunkSize = minOf(pktsize, totalBytes - pos)
            val chunk = ByteArray(chunkSize)
            System.arraycopy(preparedPayload, pos, chunk, 0, chunkSize)

            val written = usb.writeRaw(chunk, 1500)
            if (written != chunkSize) {
                return@withContext Result.failure(IllegalStateException("USB write failure at offset $pos."))
            }

            pos += chunkSize
            zlpCounter += chunkSize

            if (zlpCounter >= 0x2000 && pos < totalBytes) {
                usb.writeRaw(ByteArray(0), 500)
                zlpCounter = 0
            }

            onProgress(pos.toFloat() / totalBytes.toFloat())
        }

        // Final terminating ZLP
        usb.writeRaw(ByteArray(0), 500)
        delay(120)

        // 7. Read Checksum & Status
        val receivedChecksum = readU16BE(usb, 2500)
        val finalStatus = readU16BE(usb, 2500)

        if (receivedChecksum != null && receivedChecksum != expectedChecksum) {
            logCallback(TerminalLog("", "[!] Checksum diff: Computed=0x%04X, Device=0x%04X (Non-fatal warning)".format(expectedChecksum, receivedChecksum), LogLevel.WARNING))
        }

        if (finalStatus == null || finalStatus != STATUS_OK) {
            val statHex = if (finalStatus != null) "0x%04X".format(finalStatus) else "Timeout"
            return@withContext Result.failure(IllegalStateException("DA Payload transfer failed (Status: $statHex)."))
        }

        logCallback(TerminalLog("", "[+] DA Stage 1 successfully uploaded to SRAM.", LogLevel.SUCCESS))
        return@withContext Result.success(true)
    }

    /**
     * Jumps execution to DA Stage 1 matching Python Preloader.jump_da() exactly.
     */
    suspend fun jumpDa(
        usb: TargetPhoneUsbManager,
        daAddress: Long,
        logCallback: (TerminalLog) -> Unit
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        logCallback(TerminalLog("", "[DA UPLOADER] Jumping to DA entry point 0x%08X...".format(daAddress), LogLevel.INFO))

        purgeFifo(usb)

        // 1. Send CMD_JUMP_DA (0xD5) -> Verify 1-byte Echo
        if (!echo(usb, byteArrayOf(CMD_JUMP_DA), 1000)) {
            return@withContext Result.failure(IllegalStateException("BROM rejected CMD_JUMP_DA (0xD5 echo mismatch)."))
        }

        // 2. Send Jump Address (4B) -> Verify 4-byte Echo
        if (!echoWord32(usb, daAddress, 1000)) {
            return@withContext Result.failure(IllegalStateException("BROM rejected Jump Address (0x%08X echo mismatch).".format(daAddress)))
        }

        // 3. Read Jump Status Word (2 bytes)
        val status = readU16BE(usb, 2000)
        if (status == null || status != STATUS_OK) {
            val statHex = if (status != null) "0x%04X".format(status) else "Timeout"
            return@withContext Result.failure(IllegalStateException("BROM Jump DA execution failed (Status: $statHex)."))
        }

        logCallback(TerminalLog("", "[+] BootROM jumped to DA Stage 1 successfully.", LogLevel.SUCCESS))
        return@withContext Result.success(true)
    }
}
