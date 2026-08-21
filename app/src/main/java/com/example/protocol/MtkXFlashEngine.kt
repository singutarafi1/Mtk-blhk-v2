package com.example.protocol

import android.util.Log
import com.example.model.LogLevel
import com.example.model.TerminalLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MediaTek XFlash Protocol Engine (DA Stage 2 Protocol)
 * Direct, faithful Kotlin port of Python mtkclient:
 * - mtkclient/Library/DA/xflash/xflash_lib.py
 * - mtkclient/Library/DA/xflash/xflash_param.py
 * 100% Real Hardware Implementation - No Mock/Simulation Data.
 */
class MtkXFlashEngine(
    private val usb: TargetPhoneUsbManager,
    private val logCallback: ((TerminalLog) -> Unit)? = null
) {
    companion object {
        private const val TAG = "MtkXFlashEngine"
        
        // Protocol Magic & Defaults
        const val MAGIC: Int = 0xFEEEEEEF.toInt()
        const val DEFAULT_PACKET_SIZE = 0x20000 // 128KB
        const val MAX_PARAM_CHUNK = 0x200       // 512 bytes parameter sub-packet

        // Status codes matching xflash_param.py
        const val STATUS_OK = 0x00L
        const val STATUS_FORMAT_RUNNING = 0x40040004L
        const val STATUS_FORMAT_DONE = 0x40040005L
    }

    var maxPacketSize: Int = DEFAULT_PACKET_SIZE
        private set

    private fun log(msg: String, level: LogLevel = LogLevel.INFO) {
        logCallback?.invoke(TerminalLog("", msg, level))
    }

    private fun intLE(value: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    private fun longLE(value: Long): ByteArray =
        ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array()

    /**
     * Calculates 16-bit additive checksum matching xflash_lib.py
     */
    fun calculateChecksum16(data: ByteArray, length: Int = data.size): Int {
        var chksum = 0
        for (i in 0 until length) {
            chksum = (chksum + (data[i].toInt() and 0xFF)) and 0xFFFF
        }
        return chksum
    }

    // =========================================================================
    // 1. Low-Level XFlash Frame Transmission (xsend / xread)
    // =========================================================================

    /**
     * Sends XFlash Frame: [MAGIC (4B: 0xFEEEEEEF)][DataType (4B)][Length (4B)][Payload (NB)]
     */
    fun xsend(data: ByteArray, dataType: Int = MtkXFlashConstants.DataType.DT_PROTOCOL_FLOW): Boolean {
        val header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(MAGIC)
        header.putInt(dataType)
        header.putInt(data.size)

        if (usb.writeRaw(header.array(), 5000) != 12) return false
        if (data.isNotEmpty()) {
            return usb.writeRaw(data, 5000) == data.size
        }
        return true
    }

    /**
     * Reads XFlash Frame: Validates 12-byte header magic and extracts payload.
     */
    fun xread(timeoutMs: Int = 5000): Pair<Int, ByteArray>? {
        val headerBuf = ByteArray(12)
        var totalHeaderRead = 0
        val startHTime = System.currentTimeMillis()

        while (totalHeaderRead < 12 && (System.currentTimeMillis() - startHTime < timeoutMs)) {
            val r = usb.readRaw(ByteArray(12 - totalHeaderRead).also {
                val len = usb.readRaw(it, timeoutMs)
                if (len > 0) {
                    System.arraycopy(it, 0, headerBuf, totalHeaderRead, len)
                    totalHeaderRead += len
                }
            }, timeoutMs)
            if (r < 0) break
        }

        if (totalHeaderRead < 12) return null

        val buf = ByteBuffer.wrap(headerBuf).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buf.int
        if (magic != MAGIC) {
            Log.w(TAG, "XFlash Magic mismatch: Expected 0x%08X, got 0x%08X".format(MAGIC, magic))
            return null
        }

        val dataType = buf.int
        val length = buf.int

        if (length < 0 || length > 0x1000000) { // Safety ceiling: 16MB per single frame
            Log.e(TAG, "Invalid payload length in XFlash frame: $length bytes")
            return null
        }

        val payload = ByteArray(length)
        var received = 0
        val startPTime = System.currentTimeMillis()

        while (received < length && (System.currentTimeMillis() - startPTime < timeoutMs)) {
            val temp = ByteArray(length - received)
            val r = usb.readRaw(temp, timeoutMs)
            if (r <= 0) break
            System.arraycopy(temp, 0, payload, received, r)
            received += r
        }

        if (received < length) {
            Log.w(TAG, "Incomplete XFlash payload: Received $received of $length bytes")
            return null
        }

        return Pair(dataType, payload)
    }

    /**
     * Reads and decodes XFlash Return Status Code from DA.
     */
    fun readStatus(timeoutMs: Int = 5000): XFlashStatus {
        val resp = xread(timeoutMs) ?: return XFlashStatus(-1L, "Timeout waiting for XFlash status")
        val data = resp.second
        val status = when (data.size) {
            2 -> (ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF).toLong()
            4 -> {
                val v = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                if (v == 0xFEEEEEEFL) 0L else v
            }
            else -> 0L
        }
        return XFlashStatus(status, if (status == STATUS_OK) "OK" else "0x%08X".format(status))
    }

    /**
     * Sends XFlash ACK token (4 zero bytes) and optionally verifies status.
     */
    fun ack(rstatus: Boolean = true): Boolean {
        if (!xsend(ByteArray(4))) return false
        if (rstatus) return readStatus(5000).isOk
        return true
    }

    // =========================================================================
    // 2. Parameter Transmission & Device Control (send_param / send_dev_ctrl)
    // =========================================================================

    private fun sendRawPacket(data: ByteArray): Boolean {
        val header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(MAGIC)
        header.putInt(MtkXFlashConstants.DataType.DT_PROTOCOL_FLOW)
        header.putInt(data.size)

        if (usb.writeRaw(header.array(), 5000) != 12) return false

        var offset = 0
        while (offset < data.size) {
            val dsize = minOf(MAX_PARAM_CHUNK, data.size - offset)
            val chunk = data.copyOfRange(offset, offset + dsize)
            if (usb.writeRaw(chunk, 5000) != dsize) return false
            offset += dsize
        }
        return true
    }

    fun sendParam(data: ByteArray): Boolean = sendParam(listOf(data))

    fun sendParam(params: List<ByteArray>): Boolean {
        for (p in params) {
            if (!sendRawPacket(p)) return false
        }
        return readStatus(5000).isOk
    }

    /**
     * Executes Device Control queries (e.g. GET_EMMC_INFO, GET_UFS_INFO, REBOOT).
     */
    fun sendDevCtrl(ctrlCode: Int, param: ByteArray? = null): ByteArray? {
        if (!xsend(intLE(MtkXFlashConstants.Cmd.DEVICE_CTRL))) return null
        if (!readStatus(5000).isOk) return null

        if (!xsend(intLE(ctrlCode))) return null
        if (!readStatus(5000).isOk) return null

        return if (param == null) {
            xread(5000)?.second
        } else {
            if (!sendParam(param)) return null
            xread(5000)?.second
        }
    }

    /**
     * Performs Initial XFlash Synchronization (SYNC_SIGNAL 0x434E5953).
     */
    fun connect(): Boolean {
        log("[XFLASH] Establishing XFlash Session Handshake...", LogLevel.INFO)
        if (!xsend(intLE(MtkXFlashConstants.Cmd.SYNC_SIGNAL))) return false
        if (!readStatus(5000).isOk) return false

        getPacketLength()?.let { 
            if (it in 0x1000..0x100000) {
                maxPacketSize = it 
            }
        }
        log("[XFLASH] Session active. Negotiated Packet Size: ${maxPacketSize / 1024} KB", LogLevel.SUCCESS)
        return true
    }

    // =========================================================================
    // 3. Command Setup Headers (WRITE_DATA, READ_DATA, FORMAT)
    // =========================================================================

    private fun cmdWriteData(storage: Int, partType: Int, address: Long, length: Long): Boolean {
        if (!xsend(intLE(MtkXFlashConstants.Cmd.WRITE_DATA))) return false
        if (!readStatus(5000).isOk) return false

        // 56-byte Setup Structure: Storage(4B) + PartType(4B) + Address(8B) + Length(8B) + NandExt(32B)
        val param = ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN)
        param.putInt(storage)
        param.putInt(partType)
        param.putLong(address)
        param.putLong(length)
        param.put(NandExtension().toBytes())

        return sendParam(param.array())
    }

    private fun cmdReadData(storage: Int, partType: Int, address: Long, length: Long): Boolean {
        if (!xsend(intLE(MtkXFlashConstants.Cmd.READ_DATA))) return false
        if (!readStatus(5000).isOk) return false

        val param = ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN)
        param.putInt(storage)
        param.putInt(partType)
        param.putLong(address)
        param.putLong(length)
        param.put(NandExtension().toBytes())

        return sendParam(param.array())
    }

    // =========================================================================
    // 4. High-Level Flash Operations (Write, Read, Stream Read, Format, BootTo)
    // =========================================================================

    /**
     * Flashes binary payload to target partition via XFlash Protocol.
     */
    suspend fun writeFlash(
        storage: Int,
        partType: Int,
        address: Long,
        length: Long,
        data: ByteArray,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val pad = if (length % 512L != 0L) (512L - (length % 512L)).toInt() else 0
        val paddedLength = length + pad

        if (!cmdWriteData(storage, partType, address, paddedLength)) {
            log("[-] cmdWriteData setup rejected by DA Stage 2.", LogLevel.ERROR)
            return@withContext false
        }

        var offset = 0
        val total = data.size
        val chunkSize = maxPacketSize

        while (offset < total) {
            val curLen = minOf(chunkSize, total - offset)
            val chunk = ByteArray(curLen + if (offset + curLen >= total) pad else 0)
            System.arraycopy(data, offset, chunk, 0, curLen)

            val checksum = calculateChecksum16(chunk)
            val p0 = intLE(0)
            val p1 = intLE(checksum)

            if (!sendParam(listOf(p0, p1, chunk))) {
                log("[-] Write chunk failed at offset 0x%X".format(offset), LogLevel.ERROR)
                return@withContext false
            }
            offset += curLen
            onProgress?.invoke(offset.toLong(), total.toLong())
        }

        if (!readStatus(5000).isOk) return@withContext false
        sendDevCtrl(MtkXFlashConstants.Cmd.CC_OPTIONAL_DOWNLOAD_ACT)

        log("[+] Write complete for address 0x%08X".format(address), LogLevel.SUCCESS)
        return@withContext true
    }

    /**
     * Reads partition data into Memory ByteArray (Intended for small partitions like GPT, nvram).
     */
    suspend fun readFlash(
        storage: Int,
        partType: Int,
        address: Long,
        length: Long,
        onProgress: ((Long, Long) -> Unit)? = null
    ): ByteArray? = withContext(Dispatchers.IO) {
        val pad = if (length % 512L != 0L) (512L - (length % 512L)).toInt() else 0
        val paddedLength = length + pad

        if (!cmdReadData(storage, partType, address, paddedLength)) return@withContext null

        val result = ByteArray(length.toInt())
        var readBytes = 0L

        while (readBytes < length) {
            val resp = xread(5000) ?: return@withContext null
            val payload = resp.second

            if (payload.size > 4) {
                val copyLen = minOf(payload.size.toLong(), length - readBytes).toInt()
                System.arraycopy(payload, 0, result, readBytes.toInt(), copyLen)
                readBytes += copyLen
                ack(false)
                onProgress?.invoke(readBytes, length)
            } else if (payload.size == 4) {
                val status = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).int
                if (status != 0) return@withContext null
            }
        }
        return@withContext result
    }

    /**
     * Streams partition dump directly to disk file via OutputStream (Prevents Android OOM Crashes on GB Partitions).
     */
    suspend fun readFlashToStream(
        storage: Int,
        partType: Int,
        address: Long,
        length: Long,
        out: OutputStream,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val pad = if (length % 512L != 0L) (512L - (length % 512L)).toInt() else 0
        val paddedLength = length + pad

        if (!cmdReadData(storage, partType, address, paddedLength)) return@withContext false

        var readBytes = 0L
        while (readBytes < length) {
            val resp = xread(5000) ?: return@withContext false
            val payload = resp.second

            if (payload.size > 4) {
                val copyLen = minOf(payload.size.toLong(), length - readBytes).toInt()
                out.write(payload, 0, copyLen)
                readBytes += copyLen
                ack(false)
                onProgress?.invoke(readBytes, length)
            } else if (payload.size == 4) {
                val status = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).int
                if (status != 0) return@withContext false
            }
        }
        out.flush()
        return@withContext true
    }

    /**
     * Erases / Formats partition range using DA Stage 2 hardware formatting.
     */
    suspend fun formatFlash(
        storage: Int,
        partType: Int,
        address: Long,
        length: Long
    ): Boolean = withContext(Dispatchers.IO) {
        log("[XFLASH] Sending FORMAT command (Addr: 0x%X, Len: 0x%X)...".format(address, length), LogLevel.WARNING)

        if (!xsend(intLE(MtkXFlashConstants.Cmd.FORMAT))) return@withContext false
        if (!readStatus(5000).isOk) return@withContext false

        val param = ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN)
        param.putInt(storage)
        param.putInt(partType)
        param.putLong(address)
        param.putLong(length)
        param.put(NandExtension().toBytes())

        if (!sendParam(param.array())) return@withContext false

        var status = readStatus(10000)
        while (status.status == STATUS_FORMAT_RUNNING) {
            delay(100)
            ack(false)
            status = readStatus(10000)
        }

        val success = status.status == STATUS_FORMAT_DONE || status.isOk
        log(if (success) "[+] Format operation completed successfully." else "[-] Format failed: ${status.msg}", if (success) LogLevel.SUCCESS else LogLevel.ERROR)
        return@withContext success
    }

    /**
     * Uploads DA Stage 2 binary extension directly into Target DRAM via BOOT_TO (0x010008).
     */
    fun bootTo(address: Long, data: ByteArray, onProgress: ((Long, Long) -> Unit)? = null): Boolean {
        if (!xsend(intLE(MtkXFlashConstants.Cmd.BOOT_TO))) return false
        if (!readStatus(5000).isOk) return false

        val param = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        param.putLong(address)
        param.putLong(data.size.toLong())
        if (!sendRawPacket(param.array())) return false

        var offset = 0
        while (offset < data.size) {
            val cur = minOf(maxPacketSize, data.size - offset)
            if (!sendRawPacket(data.copyOfRange(offset, offset + cur))) return false
            if (!readStatus(5000).isOk) return false
            offset += cur
            onProgress?.invoke(offset.toLong(), data.size.toLong())
        }
        return true
    }

    // =========================================================================
    // 5. Hardware Information Queries
    // =========================================================================

    fun getPacketLength(): Int? {
        val data = sendDevCtrl(MtkXFlashConstants.Cmd.GET_PACKET_LENGTH) ?: return null
        if (data.size >= 8) {
            val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val w = buf.int
            val r = buf.int
            return if (w > 0) w else r
        }
        return null
    }

    fun getChipId(): ChipIdInfo? {
        val data = sendDevCtrl(MtkXFlashConstants.Cmd.GET_CHIP_ID) ?: return null
        if (data.size >= 8) {
            val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val hw = buf.short.toInt() and 0xFFFF
            val hwSub = buf.short.toInt() and 0xFFFF
            val hwVer = buf.short.toInt() and 0xFFFF
            val swVer = buf.short.toInt() and 0xFFFF
            return ChipIdInfo(hw, hwSub, hwVer, swVer, data.joinToString("") { "%02X".format(it) })
        }
        return null
    }

    fun getEmmcInfo(): EmmcInfo? {
        val data = sendDevCtrl(MtkXFlashConstants.Cmd.GET_EMMC_INFO) ?: return null
        if (data.size >= 72) {
            val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val type = buf.int
            val blockSize = buf.int.toLong() and 0xFFFFFFFFL
            val boot1 = buf.long
            val boot2 = buf.long
            val rpmb = buf.long
            val gp1 = buf.long
            val gp2 = buf.long
            val gp3 = buf.long
            val gp4 = buf.long
            val user = buf.long
            val cidBytes = ByteArray(16)
            buf.get(cidBytes)
            return EmmcInfo(type, blockSize, boot1, boot2, rpmb, gp1, gp2, gp3, gp4, user, cidBytes.joinToString("") { "%02X".format(it) })
        }
        return null
    }

    fun getUfsInfo(): UfsInfo? {
        val data = sendDevCtrl(MtkXFlashConstants.Cmd.GET_UFS_INFO) ?: return null
        if (data.size >= 32) {
            val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val type = buf.int
            val blockSize = buf.int.toLong() and 0xFFFFFFFFL
            val lu0 = buf.long
            val lu1 = buf.long
            val lu2 = buf.long
            val rpmb = buf.long
            val cidBytes = if (data.size >= 48) ByteArray(16).also { buf.get(it) } else ByteArray(0)
            return UfsInfo(type, blockSize, lu0, lu1, lu2, rpmb, cidBytes.joinToString("") { "%02X".format(it) })
        }
        return null
    }

    fun reboot(): Boolean {
        log("[XFLASH] Sending DA Reboot Command (CC_REBOOT)...", LogLevel.INFO)
        return sendDevCtrl(MtkXFlashConstants.Cmd.CC_REBOOT) != null
    }
}
