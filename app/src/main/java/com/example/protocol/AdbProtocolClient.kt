package com.example.protocol

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Native Android ADB Protocol Client implementing USB raw transfer framing.
 * Adb Packet Format:
 * [Command (4B)][Arg0 (4B)][Arg1 (4B)][DataLength (4B)][DataChecksum (4B)][Magic (4B)]
 */
class AdbProtocolClient(
    private val usbManager: UsbManager,
    private val device: UsbDevice
) {
    companion object {
        const val A_SYNC = 0x434e5953
        const val A_CNXN = 0x4e584e43
        const val A_OPEN = 0x4e45504f
        const val A_OKAY = 0x59414b4f
        const val A_CLSE = 0x45534c43
        const val A_WRTE = 0x45545257
        const val A_AUTH = 0x48545541

        const val ADB_VERSION = 0x01000000
        const val MAX_PAYLOAD = 4096
    }

    private var connection: UsbDeviceConnection? = null
    private var adbInterface: UsbInterface? = null
    private var inEndpoint: UsbEndpoint? = null
    private var outEndpoint: UsbEndpoint? = null

    private var localIdCounter = 1

    suspend fun open(): Boolean = withContext(Dispatchers.IO) {
        try {
            val conn = usbManager.openDevice(device) ?: return@withContext false
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC &&
                    iface.interfaceSubclass == 0x42 &&
                    iface.interfaceProtocol == 0x01
                ) {
                    var inEp: UsbEndpoint? = null
                    var outEp: UsbEndpoint? = null
                    for (j in 0 until iface.endpointCount) {
                        val ep = iface.getEndpoint(j)
                        if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                            if (ep.direction == UsbConstants.USB_DIR_IN) inEp = ep
                            else outEp = ep
                        }
                    }
                    if (inEp != null && outEp != null) {
                        conn.claimInterface(iface, true)
                        connection = conn
                        adbInterface = iface
                        inEndpoint = inEp
                        outEndpoint = outEp
                        return@withContext true
                    }
                }
            }
            conn.close()
            return@withContext false
        } catch (_: Exception) {
            return@withContext false
        }
    }

    fun close() {
        try {
            adbInterface?.let { connection?.releaseInterface(it) }
            connection?.close()
        } catch (_: Exception) {}
        connection = null
    }

    private fun sendPacket(cmd: Int, arg0: Int, arg1: Int, data: ByteArray? = null): Boolean {
        val conn = connection ?: return false
        val outEp = outEndpoint ?: return false

        val dataLen = data?.size ?: 0
        var checksum = 0
        if (data != null) {
            for (b in data) {
                checksum += (b.toInt() and 0xFF)
            }
        }
        val magic = cmd xor -0x1

        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(cmd)
        header.putInt(arg0)
        header.putInt(arg1)
        header.putInt(dataLen)
        header.putInt(checksum)
        header.putInt(magic)

        val headerBytes = header.array()
        val hWritten = conn.bulkTransfer(outEp, headerBytes, headerBytes.size, 2000)
        if (hWritten != 24) return false

        if (data != null && data.isNotEmpty()) {
            val dWritten = conn.bulkTransfer(outEp, data, data.size, 3000)
            if (dWritten != data.size) return false
        }
        return true
    }

    private fun readPacket(): Pair<IntArray, ByteArray?>? {
        val conn = connection ?: return null
        val inEp = inEndpoint ?: return null

        val headerBuf = ByteArray(24)
        val hRead = conn.bulkTransfer(inEp, headerBuf, 24, 3000)
        if (hRead != 24) return null

        val bb = ByteBuffer.wrap(headerBuf).order(ByteOrder.LITTLE_ENDIAN)
        val cmd = bb.int
        val arg0 = bb.int
        val arg1 = bb.int
        val dataLen = bb.int
        val checksum = bb.int
        val magic = bb.int

        if ((cmd xor -0x1) != magic) return null

        var data: ByteArray? = null
        if (dataLen > 0) {
            data = ByteArray(dataLen)
            var totalRead = 0
            while (totalRead < dataLen) {
                val chunkSize = (dataLen - totalRead).coerceAtMost(MAX_PAYLOAD)
                val tempBuf = ByteArray(chunkSize)
                val read = conn.bulkTransfer(inEp, tempBuf, chunkSize, 3000)
                if (read <= 0) break
                System.arraycopy(tempBuf, 0, data, totalRead, read)
                totalRead += read
            }
        }
        return Pair(intArrayOf(cmd, arg0, arg1, dataLen, checksum, magic), data)
    }

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        val banner = "host::AndroidMtkBridge\u0000".toByteArray(Charsets.UTF_8)
        if (!sendPacket(A_CNXN, ADB_VERSION, MAX_PAYLOAD, banner)) {
            return@withContext false
        }
        val response = readPacket() ?: return@withContext false
        val cmd = response.first[0]
        return@withContext (cmd == A_CNXN)
    }

    /**
     * Executes an ADB Shell command (e.g. getprop, reboot, pm) and streams or returns the result.
     */
    suspend fun executeShell(command: String): String = withContext(Dispatchers.IO) {
        val localId = localIdCounter++
        val dest = "shell:$command\u0000".toByteArray(Charsets.UTF_8)
        if (!sendPacket(A_OPEN, localId, 0, dest)) {
            return@withContext "ERROR: Failed to send A_OPEN to ADB target"
        }

        var remoteId = 0
        val sb = StringBuilder()

        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 10000L) {
            val packet = readPacket() ?: break
            val cmd = packet.first[0]
            val arg0 = packet.first[1]
            val arg1 = packet.first[2]
            val payload = packet.second

            when (cmd) {
                A_OKAY -> {
                    remoteId = arg0
                }
                A_WRTE -> {
                    if (payload != null) {
                        sb.append(String(payload, Charsets.UTF_8))
                    }
                    sendPacket(A_OKAY, localId, remoteId)
                }
                A_CLSE -> {
                    sendPacket(A_CLSE, localId, remoteId)
                    break
                }
            }
        }
        return@withContext sb.toString().trim()
    }
}
