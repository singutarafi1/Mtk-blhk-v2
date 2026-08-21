package com.example.protocol

import android.util.Log
import com.example.model.LogLevel
import com.example.model.TerminalLog
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MediaTek XFlash Protocol Engine
 * Complete, faithful Kotlin port of Python mtkclient:
 * - mtkclient/Library/DA/xflash/xflash_lib.py
 * - mtkclient/Library/DA/xflash/xflash_param.py
 */
class MtkXFlashEngine(
    private val usb: TargetPhoneUsbManager,
    private val logCallback: ((TerminalLog) -> Unit)? = null
) {
    companion object {
        private const val TAG = "MtkXFlashEngine"
        const val DEFAULT_PACKET_SIZE = 0x20000 // 128KB
        const val MAX_PARAM_CHUNK = 0x200 // 512 bytes
    }

    var maxPacketSize: Int = DEFAULT_PACKET_SIZE
        private set

    private fun log(msg: String, level: LogLevel = LogLevel.INFO) {
        logCallback?.invoke(TerminalLog("", msg, level))
    }

    private fun intLE(value: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    fun calculateChecksum16(data: ByteArray): Int {
        var chksum = 0
        for (b in data) {
            chksum = (chksum + (b.toInt() and 0xFF)) and 0xFFFF
        }
        return chksum
    }

    // ------- Low-level XFlash I/O -------
    fun xsend(data: ByteArray, dataType: Int = MtkXFlashConstants.DataType.DT_PROTOCOL_FLOW): Boolean {
        val header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(MtkXFlashConstants.MAGIC)
        header.putInt(dataType)
        header.putInt(data.size)

        if (usb.writeRaw(header.array(), 5000) != 12) return false
        if (data.isNotEmpty()) {
            return usb.writeRaw(data, 5000) == data.size
        }
        return true
    }

    fun xread(timeoutMs: Int = 5000): Pair<Int, ByteArray>? {
        val headerBuf = ByteArray(12)
        if (usb.readRaw(headerBuf, timeoutMs) < 12) return null

        val buf = ByteBuffer.wrap(headerBuf).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buf.int
        if (magic != MtkXFlashConstants.MAGIC) {
            Log.w(TAG, "XFlash Magic mismatch: 0x%08X".format(magic))
            return null
        }
        val dataType = buf.int
        val length = buf.int

        val payload = ByteArray(length)
        var received = 0
        while (received < length) {
            val temp = ByteArray(length - received)
            val r = usb.readRaw(temp, timeoutMs)
            if (r <= 0) break
            System.arraycopy(temp, 0, payload, received, r)
            received += r
        }

        return Pair(dataType, payload)
    }

    fun readStatus(timeoutMs: Int = 5000): XFlashStatus {
        val resp = xread(timeoutMs) ?: return XFlashStatus(-1L, "Timeout")
        val data = resp.second
        val status = when (data.size) {
            2 -> (ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF).toLong()
            4 -> {
                val v = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                if (v == 0xFEEEEEEFL) 0L else v
            }
            else -> 0L
        }
        return XFlashStatus(status, if (status == 0L) "OK" else "0x%08X".format(status))
    }

    fun ack(rstatus: Boolean = true): Boolean {
        if (!xsend(ByteArray(4))) return false
        if (rstatus) return readStatus(5000).isOk
        return true
    }

    // ------- Parameter / Device Control -------
    private fun sendRawPacket(data: ByteArray): Boolean {
        val header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(MtkXFlashConstants.MAGIC)
        header.putInt(MtkXFlashConstants.DataType.DT_PROTOCOL_FLOW)
        header.putInt(data.size)

        if (usb.writeRaw(header.array(), 5000) != 12) return false

        var offset = 0
        while (offset < data.size) {
            val dsize = minOf(MAX_PARAM_CHUNK, data.size - offset)
            if (usb.writeRaw(data.copyOfRange(offset, offset + dsize), 5000) != dsize) return false
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

    fun sendDevCtrl(ctrlCode: Int, param: ByteArray? = null): ByteArray? {
        if (!xsend(intLE(MtkXFlashConstants.Cmd.DEVICE_CTRL))) return null
        if (!readStatus(5000).isOk) return null

        if (!xsend(intLE(ctrlCode))) return null
        if (!readStatus(5000).isOk) return null

        if (param == null) {
            return xread(5000)?.second
        } else {
            if (!sendParam(param)) return null
            return xread(5000)?.second
        }
    }

    fun connect(): Boolean {
        log("[XFLASH] Syncing XFlash Session...", LogLevel.INFO)
        if (!xsend(intLE(MtkXFlashConstants.Cmd.SYNC_SIGNAL))) return false
        if (!readStatus(5000).isOk) return false

        getPacketLength()?.let { maxPacketSize = it }
        log("[XFLASH] Linked. PacketSize=${maxPacketSize / 1024}KB", LogLevel.SUCCESS)
        return true
    }

    // ------- Command Setup -------
    fun cmdWriteData(storage: Int, partType: Int, address: Long, length: Long): Boolean {
        if (!xsend(intLE(MtkXFlashConstants.Cmd.WRITE_DATA))) return false
        if (!readStatus(5000).isOk) return false

        val param = ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN)
        param.putInt(storage)
        param.putInt(partType)
        param.putLong(address)
        param.putLong(length)
        param.put(ByteArray(32)) // NandExtension zero

        return sendParam(param.array())
    }

    fun cmdReadData(storage: Int, partType: Int, address: Long, length: Long): Boolean {
        if (!xsend(intLE(MtkXFlashConstants.Cmd.READ_DATA))) return false
        if (!readStatus(5000).isOk) return false

        val param = ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN)
        param.putInt(storage)
        param.putInt(partType)
        param.putLong(address)
        param.putLong(length)
        param.put(ByteArray(32))

        return sendParam(param.array())
    }

    // ------- High-level operations -------
    fun writeFlash(
        storage: Int, partType: Int, address: Long, length: Long, data: ByteArray,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Boolean {
        val pad = if (length % 512L != 0L) (512L - (length % 512L)).toInt() else 0
        val paddedLength = length + pad

        if (!cmdWriteData(storage, partType, address, paddedLength)) {
            log("[-] cmdWriteData rejected", LogLevel.ERROR)
            return false
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
                log("[-] Write chunk failed at 0x%X".format(offset), LogLevel.ERROR)
                return false
            }
            offset += curLen
            onProgress?.invoke(offset.toLong(), total.toLong())
        }

        if (!readStatus(5000).isOk) return false
        sendDevCtrl(MtkXFlashConstants.Cmd.CC_OPTIONAL_DOWNLOAD_ACT)

        log("[+] Write complete", LogLevel.SUCCESS)
        return true
    }

    fun readFlash(
        storage: Int, partType: Int, address: Long, length: Long,
        onProgress: ((Long, Long) -> Unit)? = null
    ): ByteArray? {
        val pad = if (length % 512L != 0L) (512L - (length % 512L)).toInt() else 0
        val paddedLength = length + pad

        if (!cmdReadData(storage, partType, address, paddedLength)) return null

        val result = ByteArray(length.toInt())
        var read = 0L

        while (read < length) {
            val resp = xread(5000) ?: return null
            val payload = resp.second

            if (payload.size > 4) {
                val copyLen = minOf(payload.size.toLong(), length - read).toInt()
                System.arraycopy(payload, 0, result, read.toInt(), copyLen)
                read += copyLen
                ack(false)
                onProgress?.invoke(read, length)
            } else if (payload.size == 4) {
                val status = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).int
                if (status != 0) return null
            }
        }
        return result
    }

    fun readFlashToStream(
        storage: Int, partType: Int, address: Long, length: Long, out: OutputStream,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Boolean {
        val pad = if (length % 512L != 0L) (512L - (length % 512L)).toInt() else 0
        val paddedLength = length + pad

        if (!cmdReadData(storage, partType, address, paddedLength)) return false

        var read = 0L
        while (read < length) {
            val resp = xread(5000) ?: return false
            val payload = resp.second

            if (payload.size > 4) {
                val copyLen = minOf(payload.size.toLong(), length - read).toInt()
                out.write(payload, 0, copyLen)
                read += copyLen
                ack(false)
                onProgress?.invoke(read, length)
            } else if (payload.size == 4) {
                val status = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).int
                if (status != 0) return false
            }
        }
        out.flush()
        return true
    }

    fun formatFlash(storage: Int, partType: Int, address: Long, length: Long): Boolean {
        log("[XFLASH] Formatting...", LogLevel.WARNING)

        if (!xsend(intLE(MtkXFlashConstants.Cmd.FORMAT))) return false
        if (!readStatus(5000).isOk) return false

        val param = ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN)
        param.putInt(storage)
        param.putInt(partType)
        param.putLong(address)
        param.putLong(length)
        param.put(ByteArray(32))

        if (!sendParam(param.array())) return false

        var status = readStatus(10000)
        while (status.status == 0x40040004L) {
            Thread.sleep(100)
            ack(false)
            status = readStatus(10000)
        }
        val success = status.status == 0x40040005L || status.isOk
        log(if (success) "[+] Format OK" else "[-] Format failed: ${status.msg}", if (success) LogLevel.SUCCESS else LogLevel.ERROR)
        return success
    }

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

    // ------- Info Queries -------
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
        log("[XFLASH] Reboot command sent", LogLevel.INFO)
        return sendDevCtrl(MtkXFlashConstants.Cmd.CC_REBOOT) != null
    }
}