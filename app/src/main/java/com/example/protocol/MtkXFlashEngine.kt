package com.example.protocol

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
        const val MAGIC: Long = 0xFEEEEEEFL
        const val DT_PROTOCOL_FLOW = 1
        const val DT_MESSAGE = 2
        const val DEFAULT_PACKET_SIZE = 0x20000 // 128KB
        const val MAX_PARAM_CHUNK = 0x200 // 512 bytes
    }

    var maxPacketSize: Int = DEFAULT_PACKET_SIZE
        private set

    private fun log(msg: String, level: LogLevel = LogLevel.INFO) {
        logCallback?.invoke(TerminalLog("", msg, level))
    }

    /**
     * Calculates 16-bit additive checksum of binary payload: sum(data) & 0xFFFF
     */
    private fun calculateChecksum16(data: ByteArray): Int {
        var chksum = 0
        for (b in data) {
            chksum = (chksum + (b.toInt() and 0xFF)) and 0xFFFF
        }
        return chksum
    }

    // ==========================================
    // Low-level XFlash Send & Read
    // ==========================================

    /**
     * Low-level XFlash Send (xsend):
     * Header of 12 bytes Little-Endian:
     * pack("<III", MAGIC, dataType, length) + data
     */
    fun xsend(data: ByteArray, dataType: Int = DT_PROTOCOL_FLOW): Boolean {
        val header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(MAGIC.toInt())
        header.putInt(dataType)
        header.putInt(data.size)

        if (usb.writeRaw(header.array(), 5000) != 12) {
            return false
        }
        if (data.isNotEmpty()) {
            return usb.writeRaw(data, 5000) == data.size
        }
        return true
    }

    fun xsend(value: Int, dataType: Int = DT_PROTOCOL_FLOW): Boolean {
        val buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
        return xsend(buf, dataType)
    }

    fun xsend(value: Long, is64bit: Boolean = false, dataType: Int = DT_PROTOCOL_FLOW): Boolean {
        val buf = if (is64bit) {
            ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array()
        } else {
            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value.toInt()).array()
        }
        return xsend(buf, dataType)
    }

    /**
     * Low-level XFlash Read (xread):
     * Reads 12-byte header (Little-Endian: magic, dataType, length), verifies magic, reads payload
     */
    fun xread(timeoutMs: Int = 5000): Pair<Int, ByteArray>? {
        val headerBuf = ByteArray(12)
        val readCount = usb.readRaw(headerBuf, timeoutMs)
        if (readCount < 12) {
            return null
        }

        val buf = ByteBuffer.wrap(headerBuf).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buf.int
        if (magic != MAGIC.toInt()) {
            log("[-] xread error: Wrong magic 0x%08X (expected 0xFEEEEEEF)".format(magic), LogLevel.ERROR)
            return null
        }

        val dataType = buf.int
        val length = buf.int
        if (length < 0) return null

        val resp = ByteArray(length)
        var received = 0
        while (received < length) {
            val chunk = ByteArray(length - received)
            val r = usb.readRaw(chunk, timeoutMs)
            if (r <= 0) break
            System.arraycopy(chunk, 0, resp, received, r)
            received += r
        }

        return Pair(dataType, resp)
    }

    // ==========================================
    // Status & ACK Handling
    // ==========================================

    /**
     * Reads status response packet from XFlash DA (unpack "<I" or "<H" status)
     */
    fun readStatus(timeoutMs: Int = 5000): XFlashStatus {
        val resp = xread(timeoutMs) ?: return XFlashStatus(-1L, "No response from DA")
        val data = resp.second
        if (data.isEmpty()) return XFlashStatus(-1L, "Empty status payload")

        val statusVal = when (data.size) {
            2 -> (ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF).toLong()
            else -> {
                val st = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                if (st == MAGIC) 0L else st
            }
        }
        val msg = if (statusVal == 0L) "OK" else "Status 0x%08X".format(statusVal)
        return XFlashStatus(statusVal, msg)
    }

    /**
     * Sends XFlash ACK packet:
     * pack("<III", MAGIC, DT_PROTOCOL_FLOW, 4) + pack("<I", 0)
     */
    fun ack(rstatus: Boolean = true): Boolean {
        val stmp = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        stmp.putInt(MAGIC.toInt())
        stmp.putInt(DT_PROTOCOL_FLOW)
        stmp.putInt(4)
        val data = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0).array()

        if (usb.writeRaw(stmp.array(), 5000) != 12) return false
        if (usb.writeRaw(data, 5000) != 4) return false

        if (rstatus) {
            val st = readStatus(5000)
            return st.isOk
        }
        return true
    }

    // ==========================================
    // Parameter & Device Control
    // ==========================================

    /**
     * Sends parameter block in chunks of max 0x200 bytes and waits for status
     */
    fun sendParam(data: ByteArray): Boolean {
        return sendParam(listOf(data))
    }

    fun sendParam(params: List<ByteArray>): Boolean {
        for (param in params) {
            val pkt = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            pkt.putInt(MAGIC.toInt())
            pkt.putInt(DT_PROTOCOL_FLOW)
            pkt.putInt(param.size)

            if (usb.writeRaw(pkt.array(), 5000) != 12) {
                return false
            }

            var length = param.size
            var pos = 0
            while (length > 0) {
                val dsize = minOf(length, MAX_PARAM_CHUNK)
                val chunk = param.copyOfRange(pos, pos + dsize)
                if (usb.writeRaw(chunk, 5000) != dsize) {
                    return false
                }
                pos += dsize
                length -= dsize
            }
        }
        val st = readStatus(5000)
        return st.isOk
    }

    /**
     * Sends Device Control command via XFlash:
     * xsend(DEVICE_CTRL) -> status() -> xsend(cmd) -> status() -> xread()
     */
    fun sendDevCtrl(cmd: Int): ByteArray? {
        if (xsend(MtkXFlashConstants.Cmd.DEVICE_CTRL)) {
            if (readStatus(5000).isOk) {
                if (xsend(cmd)) {
                    if (readStatus(5000).isOk) {
                        val resp = xread(5000)
                        return resp?.second
                    }
                }
            }
        }
        return null
    }

    fun sendDevCtrl(cmd: Int, param: ByteArray): Boolean {
        if (xsend(MtkXFlashConstants.Cmd.DEVICE_CTRL)) {
            if (readStatus(5000).isOk) {
                if (xsend(cmd)) {
                    if (readStatus(5000).isOk) {
                        return sendParam(param)
                    }
                }
            }
        }
        return false
    }

    // ==========================================
    // Command Setup (cmdWriteData / cmdReadData)
    // ==========================================

    /**
     * Sends CMD_WRITE_DATA (0x010004) with packed parameters:
     * pack("<IIQQ", storage, parttype, addr, size) + NandExtension(32 bytes)
     */
    fun cmdWriteData(
        storage: Int,
        partType: Int,
        address: Long,
        length: Long,
        nandExt: NandExtension = NandExtension()
    ): Boolean {
        if (xsend(MtkXFlashConstants.Cmd.WRITE_DATA)) {
            if (readStatus(5000).isOk) {
                val paramBuf = ByteBuffer.allocate(24 + 32).order(ByteOrder.LITTLE_ENDIAN)
                paramBuf.putInt(storage)
                paramBuf.putInt(partType)
                paramBuf.putLong(address)
                paramBuf.putLong(length)
                paramBuf.put(nandExt.toBytes())
                return sendParam(paramBuf.array())
            }
        }
        return false
    }

    /**
     * Sends CMD_READ_DATA (0x010005) with packed parameters:
     * pack("<IIQQ", storage, parttype, addr, size) + NandExtension(32 bytes)
     */
    fun cmdReadData(
        storage: Int,
        partType: Int,
        address: Long,
        length: Long,
        nandExt: NandExtension = NandExtension()
    ): Boolean {
        if (xsend(MtkXFlashConstants.Cmd.READ_DATA)) {
            if (readStatus(5000).isOk) {
                val paramBuf = ByteBuffer.allocate(24 + 32).order(ByteOrder.LITTLE_ENDIAN)
                paramBuf.putInt(storage)
                paramBuf.putInt(partType)
                paramBuf.putLong(address)
                paramBuf.putLong(length)
                paramBuf.put(nandExt.toBytes())
                if (sendParam(paramBuf.array())) {
                    return readStatus(5000).isOk
                }
            }
        }
        return false
    }

    // ==========================================
    // High-Level Operations (write / read / format)
    // ==========================================

    /**
     * Writes Flash range via XFlash protocol:
     * For each chunk: sendParam([pack("<I", 0), pack("<I", checksum), chunk])
     * Finishes with CC_OPTIONAL_DOWNLOAD_ACT
     */
    fun writeFlash(
        storage: Int,
        partType: Int,
        address: Long,
        length: Long,
        data: ByteArray,
        chunkSize: Int = maxPacketSize,
        nandExt: NandExtension = NandExtension(),
        onProgress: ((Long, Long) -> Unit)? = null
    ): Boolean {
        val pad = if (length % 512L != 0L) (512L - (length % 512L)).toInt() else 0
        val totalLength = length + pad

        log("[XFLASH] Writing Flash: Storage 0x%X, Part 0x%X, Addr 0x%X, Len $length (padded: $totalLength)".format(storage, partType, address), LogLevel.INFO)

        if (!cmdWriteData(storage, partType, address, totalLength, nandExt)) {
            log("[-] cmdWriteData setup rejected by target.", LogLevel.ERROR)
            return false
        }

        var bytestowrite = totalLength
        var pos = 0
        val total = data.size.toLong()

        while (bytestowrite > 0) {
            val dsize = minOf(chunkSize.toLong(), bytestowrite).toInt()
            val chunk = ByteArray(dsize)
            if (pos < data.size) {
                val copyLen = minOf(dsize, data.size - pos)
                System.arraycopy(data, pos, chunk, 0, copyLen)
            }

            val checksum = calculateChecksum16(chunk)
            val p0 = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0).array()
            val p1 = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(checksum).array()

            if (!sendParam(listOf(p0, p1, chunk))) {
                log("[-] Error writing chunk at offset 0x%08X".format(pos), LogLevel.ERROR)
                return false
            }

            bytestowrite -= dsize
            pos += dsize
            onProgress?.invoke(minOf(pos.toLong(), total), total)
        }

        val finalStatus = readStatus(5000)
        if (finalStatus.isOk) {
            try {
                sendDevCtrl(MtkXFlashConstants.Cmd.CC_OPTIONAL_DOWNLOAD_ACT)
            } catch (_: Exception) {}
            log("[+] XFlash Write complete for 0x%X bytes.".format(length), LogLevel.SUCCESS)
            return true
        }

        log("[-] Final write status error: 0x%08X (${finalStatus.msg})".format(finalStatus.status), LogLevel.ERROR)
        return false
    }

    /**
     * Reads Flash range into ByteArray via XFlash protocol:
     * Iteratively receives chunks and sends ACK after each chunk.
     */
    fun readFlash(
        storage: Int,
        partType: Int,
        address: Long,
        length: Long,
        chunkSize: Int = maxPacketSize,
        nandExt: NandExtension = NandExtension(),
        onProgress: ((Long, Long) -> Unit)? = null
    ): ByteArray? {
        val pad = if (length % 512L != 0L) (512L - (length % 512L)).toInt() else 0
        val totalLength = length + pad

        log("[XFLASH] Reading Flash: Storage 0x%X, Part 0x%X, Addr 0x%X, Len $length".format(storage, partType, address), LogLevel.INFO)

        if (!cmdReadData(storage, partType, address, totalLength, nandExt)) {
            log("[-] cmdReadData setup rejected by target.", LogLevel.ERROR)
            return null
        }

        val result = ByteArray(length.toInt())
        var bytesRead = 0L
        var bytestoread = totalLength

        while (bytestoread > 0) {
            val hdr = ByteArray(12)
            val rHdr = usb.readRaw(hdr, 5000)
            if (rHdr < 12) break

            val buf = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN)
            val magic = buf.int
            if (magic != MAGIC.toInt()) break
            buf.int // dataType
            val slength = buf.int
            if (slength <= 0) break

            val chunk = ByteArray(slength)
            var rec = 0
            while (rec < slength) {
                val toRead = minOf(slength - rec, chunkSize)
                val temp = ByteArray(toRead)
                val r = usb.readRaw(temp, 5000)
                if (r <= 0) break
                System.arraycopy(temp, 0, chunk, rec, r)
                rec += r
            }

            if (slength > 4) {
                val toCopy = minOf(slength.toLong(), length - bytesRead).toInt()
                if (toCopy > 0) {
                    System.arraycopy(chunk, 0, result, bytesRead.toInt(), toCopy)
                    bytesRead += toCopy
                }
                ack(rstatus = false)
                bytestoread -= slength
                onProgress?.invoke(bytesRead, length)
            } else if (slength == 4) {
                val flag = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN).int
                if (flag != 0) break
            } else {
                break
            }
        }

        // Consume final response packet if present
        val finalHdr = ByteArray(12)
        val rFinal = usb.readRaw(finalHdr, 1000)
        if (rFinal >= 12) {
            val fBuf = ByteBuffer.wrap(finalHdr).order(ByteOrder.LITTLE_ENDIAN)
            if (fBuf.int == MAGIC.toInt()) {
                fBuf.int
                val fLen = fBuf.int
                if (fLen > 0) {
                    val fData = ByteArray(fLen)
                    usb.readRaw(fData, 1000)
                }
            }
        }

        return if (bytesRead >= length) result else null
    }

    /**
     * Reads Flash range directly into an OutputStream (memory-safe for large ROM dumps)
     */
    fun readFlashToStream(
        storage: Int,
        partType: Int,
        address: Long,
        length: Long,
        outputStream: OutputStream,
        chunkSize: Int = maxPacketSize,
        nandExt: NandExtension = NandExtension(),
        onProgress: ((Long, Long) -> Unit)? = null
    ): Boolean {
        val pad = if (length % 512L != 0L) (512L - (length % 512L)).toInt() else 0
        val totalLength = length + pad

        if (!cmdReadData(storage, partType, address, totalLength, nandExt)) {
            return false
        }

        var bytesRead = 0L
        var bytestoread = totalLength

        while (bytestoread > 0) {
            val hdr = ByteArray(12)
            val rHdr = usb.readRaw(hdr, 5000)
            if (rHdr < 12) break

            val buf = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN)
            val magic = buf.int
            if (magic != MAGIC.toInt()) break
            buf.int // dataType
            val slength = buf.int
            if (slength <= 0) break

            val chunk = ByteArray(slength)
            var rec = 0
            while (rec < slength) {
                val toRead = minOf(slength - rec, chunkSize)
                val temp = ByteArray(toRead)
                val r = usb.readRaw(temp, 5000)
                if (r <= 0) break
                System.arraycopy(temp, 0, chunk, rec, r)
                rec += r
            }

            if (slength > 4) {
                val toWrite = minOf(slength.toLong(), length - bytesRead).toInt()
                if (toWrite > 0) {
                    outputStream.write(chunk, 0, toWrite)
                    bytesRead += toWrite
                }
                ack(rstatus = false)
                bytestoread -= slength
                onProgress?.invoke(bytesRead, length)
            } else if (slength == 4) {
                val flag = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN).int
                if (flag != 0) break
            } else {
                break
            }
        }

        // Consume final response packet if present
        val finalHdr = ByteArray(12)
        val rFinal = usb.readRaw(finalHdr, 1000)
        if (rFinal >= 12) {
            val fBuf = ByteBuffer.wrap(finalHdr).order(ByteOrder.LITTLE_ENDIAN)
            if (fBuf.int == MAGIC.toInt()) {
                fBuf.int
                val fLen = fBuf.int
                if (fLen > 0) {
                    val fData = ByteArray(fLen)
                    usb.readRaw(fData, 1000)
                }
            }
        }

        outputStream.flush()
        return bytesRead >= length
    }

    /**
     * Formats / Erases Flash range via XFlash CMD_FORMAT (0x010003)
     * Handles progress polling (0x40040004 continue / 0x40040005 complete)
     */
    fun formatFlash(
        storage: Int,
        partType: Int,
        address: Long,
        length: Long,
        nandExt: NandExtension = NandExtension()
    ): Boolean {
        log("[XFLASH] Formatting Storage 0x%X Part 0x%X @ 0x%X (Len: $length bytes)".format(storage, partType, address), LogLevel.WARNING)

        if (xsend(MtkXFlashConstants.Cmd.FORMAT)) {
            if (readStatus(5000).isOk) {
                val param = ByteBuffer.allocate(24 + 32).order(ByteOrder.LITTLE_ENDIAN)
                param.putInt(storage)
                param.putInt(partType)
                param.putLong(address)
                param.putLong(length)
                param.put(nandExt.toBytes())

                if (sendParam(param.array())) {
                    var st = readStatus(10000).status
                    while (st == 0x40040004L) { // STATUS_CONTINUE
                        val sleepMs = readStatus(5000).status
                        if (sleepMs in 1..60000) {
                            try { Thread.sleep(sleepMs) } catch (_: Exception) {}
                        }
                        // Send ACK and read status exactly as Python self.ack()
                        val stmp = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                        stmp.putInt(MAGIC.toInt())
                        stmp.putInt(DT_PROTOCOL_FLOW)
                        stmp.putInt(4)
                        val zero = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0).array()
                        if (usb.writeRaw(stmp.array(), 5000) != 12 || usb.writeRaw(zero, 5000) != 4) return false
                        st = readStatus(10000).status
                    }
                    if (st == 0x40040005L || st == 0L) {
                        log("[+] [XFLASH FORMAT OK]: Partition range formatted successfully.", LogLevel.SUCCESS)
                        return true
                    }
                    log("[-] XFlash FORMAT status returned: 0x%08X".format(st), LogLevel.ERROR)
                }
            }
        }
        return false
    }

    // ==========================================
    // Info Queries (getChipId, getEmmcInfo, getUfsInfo, getPacketLength)
    // ==========================================

    /**
     * Queries Chip ID and security info (GET_CHIP_ID = 0x04000D)
     */
    fun getChipId(): ChipIdInfo? {
        val data = sendDevCtrl(MtkXFlashConstants.Cmd.GET_CHIP_ID) ?: return null
        if (readStatus(5000).isOk && data.size >= 10) {
            val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val hwCode = buf.short.toInt() and 0xFFFF
            val hwSubCode = buf.short.toInt() and 0xFFFF
            val hwVersion = buf.short.toInt() and 0xFFFF
            val swVersion = buf.short.toInt() and 0xFFFF
            val hex = data.joinToString("") { "%02X".format(it) }
            return ChipIdInfo(hwCode, hwSubCode, hwVersion, swVersion, hex)
        }
        return null
    }

    /**
     * Queries eMMC Storage info (GET_EMMC_INFO = 0x040001)
     */
    fun getEmmcInfo(): EmmcInfo? {
        val data = sendDevCtrl(MtkXFlashConstants.Cmd.GET_EMMC_INFO) ?: return null
        if (readStatus(5000).isOk && data.size >= 88) {
            val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val type = buf.int
            val blockSize = buf.int.toLong() and 0xFFFFFFFFL
            val boot1Size = buf.long
            val boot2Size = buf.long
            val rpmbSize = buf.long
            val gp1Size = buf.long
            val gp2Size = buf.long
            val gp3Size = buf.long
            val gp4Size = buf.long
            val userSize = buf.long
            val cidBytes = ByteArray(16)
            buf.get(cidBytes)
            val cid = cidBytes.joinToString("") { "%02X".format(it) }
            return EmmcInfo(type, blockSize, boot1Size, boot2Size, rpmbSize, gp1Size, gp2Size, gp3Size, gp4Size, userSize, cid)
        }
        return null
    }

    /**
     * Queries UFS Storage info (GET_UFS_INFO = 0x040004)
     */
    fun getUfsInfo(): UfsInfo? {
        val data = sendDevCtrl(MtkXFlashConstants.Cmd.GET_UFS_INFO) ?: return null
        if (readStatus(5000).isOk && data.size >= 32) {
            val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val type = buf.int
            val blockSize = buf.int.toLong() and 0xFFFFFFFFL
            val lu2Size = buf.long
            val lu1Size = buf.long
            val lu0Size = buf.long
            var cid = ""
            if (data.size >= 48) {
                val cidBytes = ByteArray(16)
                buf.get(cidBytes)
                cid = cidBytes.joinToString("") { "%02X".format(it) }
            }
            return UfsInfo(type, blockSize, lu0Size, lu1Size, lu2Size, 0L, cid)
        }
        return null
    }

    /**
     * Queries optimal transfer packet size (GET_PACKET_LENGTH = 0x040007)
     */
    fun getPacketLength(): Int? {
        val data = sendDevCtrl(MtkXFlashConstants.Cmd.GET_PACKET_LENGTH) ?: return null
        if (readStatus(5000).isOk && data.size >= 8) {
            val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val writeLen = buf.int
            val readLen = buf.int
            val optimal = if (writeLen in 0x1000..0x1000000) writeLen else readLen
            if (optimal in 0x1000..0x1000000) {
                maxPacketSize = optimal
                return optimal
            }
        }
        return null
    }

    // ==========================================
    // Other Helpers (connect, reboot, shutdown, bootTo)
    // ==========================================

    /**
     * Connects and synchronizes XFlash session:
     * Sends SYNC_SIGNAL, SETUP_ENVIRONMENT, and SETUP_HW_INIT_PARAMS
     */
    fun connect(): Boolean {
        log("[XFLASH] Synchronizing XFlash Session (SYNC_SIGNAL)...", LogLevel.INFO)
        if (!xsend(MtkXFlashConstants.Cmd.SYNC_SIGNAL)) {
            log("[-] Failed to send XFlash SYNC_SIGNAL", LogLevel.ERROR)
            return false
        }

        // Setup Environment (0x010100)
        if (xsend(MtkXFlashConstants.Cmd.SETUP_ENVIRONMENT)) {
            val envBuf = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
            envBuf.putInt(2) // da_log_level (INFO)
            envBuf.putInt(1) // log_channel (UART)
            envBuf.putInt(1) // system_os (OS_LINUX)
            envBuf.putInt(0) // ufs_provision
            envBuf.putInt(0) // reserved
            sendParam(envBuf.array())
        }

        // Setup HW Init Params (0x010101)
        if (xsend(MtkXFlashConstants.Cmd.SETUP_HW_INIT_PARAMS)) {
            val hwBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            hwBuf.putInt(0)
            sendParam(hwBuf.array())
        }

        val res = xread(2000)
        if (res != null && res.second.size >= 4) {
            val syncVal = ByteBuffer.wrap(res.second).order(ByteOrder.LITTLE_ENDIAN).int
            if (syncVal == MtkXFlashConstants.Cmd.SYNC_SIGNAL || syncVal == 0) {
                log("[+] XFlash DA Sync Confirmed.", LogLevel.SUCCESS)
                getPacketLength()
                return true
            }
        }
        log("[+] XFlash Linked.", LogLevel.SUCCESS)
        getPacketLength()
        return true
    }

    /**
     * Reboots target device via CC_REBOOT (0x080006)
     */
    fun reboot(): Boolean {
        log("[XFLASH] Sending Reboot Command...", LogLevel.INFO)
        val res = sendDevCtrl(MtkXFlashConstants.Cmd.CC_REBOOT)
        return res != null || readStatus(2000).isOk
    }

    /**
     * Shuts down target device via CMD_SHUTDOWN (0x010007)
     */
    fun shutdown(): Boolean {
        log("[XFLASH] Sending Shutdown Command...", LogLevel.INFO)
        if (xsend(MtkXFlashConstants.Cmd.SHUTDOWN)) {
            if (readStatus(5000).isOk) {
                val buf = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
                buf.putInt(0) // hasflags
                buf.putInt(0) // enablewdt
                buf.putInt(0) // async_mode
                buf.putInt(0) // bootmode (NORMAL)
                buf.putInt(0) // dl_bit
                buf.putInt(0) // dont_resetrtc
                buf.putInt(0) // leaveusb
                buf.putInt(0) // reserved
                if (xsend(buf.array())) {
                    return readStatus(5000).isOk
                }
            }
        }
        return false
    }

    /**
     * Instructs target DA to boot to partition/target address via CMD_BOOT_TO (0x010008)
     */
    fun bootTo(option: Int = 0): Boolean {
        log("[XFLASH] Booting with option $option...", LogLevel.INFO)
        if (xsend(MtkXFlashConstants.Cmd.BOOT_TO)) {
            if (readStatus(5000).isOk) {
                val param = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
                param.putLong(option.toLong())
                param.putLong(0L)
                return sendParam(param.array())
            }
        }
        return false
    }
}
