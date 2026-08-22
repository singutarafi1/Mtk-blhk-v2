package com.example.protocol

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

enum class UsbDeviceMode(val label: String, val description: String) {
    BROM("MTK BROM", "MediaTek BootROM Mode"),
    PRELOADER("Preloader", "MTK Preloader / DA VCOM Port"),
    FASTBOOT("Fastboot", "Android Fastboot Mode"),
    ADB("ADB Debugging", "Android USB Debugging"),
    MTP("MTP / Media", "Media Transfer Protocol"),
    CDC_SERIAL("CDC Serial", "Serial / UART Bridge"),
    UNKNOWN("USB Device", "Connected USB Peripheral"),
    NONE("Unplugged", "No USB Device Connected")
}

sealed class TargetPhoneState {
    object Disconnected : TargetPhoneState()
    data class RequestingPermission(val deviceName: String, val mode: UsbDeviceMode, val vidPid: String) : TargetPhoneState()
    data class Connected(val deviceName: String, val mode: UsbDeviceMode, val isBromMode: Boolean, val vidPid: String, val fileDescriptor: Int = -1) : TargetPhoneState()
    data class Error(val message: String) : TargetPhoneState()
}

class TargetPhoneUsbManager(private val context: Context) {

    companion object {
        const val ACTION_USB_PHONE_PERMISSION = "com.example.mtkbridge.USB_PHONE_PERMISSION"
        const val MTK_VID = 0x0E8D
        const val MTK_PID_BROM = 0x0003
        const val MTK_PID_BOOTROM_GENERIC = 0x0001
        const val MTK_PID_DA_HIGH_SPEED = 0x0002
        const val MTK_PID_PRELOADER_ALT = 0x0005
        const val MTK_PID_PRELOADER = 0x2000
        const val MTK_PID_PRELOADER_2 = 0x2001
        const val MTK_PID_CDC = 0x2004
        const val MTK_PID_DEBUG = 0x2005

        val SUPPORTED_VIDS = setOf(0x0E8D, 0x1004, 0x0BB4, 0x2A45, 0x1782, 0x1A86, 0x10C4, 0x0403, 0x18D1, 0x2717)
    }

    val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var usbConnection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var inEndpoint: UsbEndpoint? = null
    private var outEndpoint: UsbEndpoint? = null

    var currentDevice: UsbDevice? = null
        private set

    private val scope = CoroutineScope(Dispatchers.IO)
    var onDeviceAutoConnectedListener: ((TargetPhoneState.Connected) -> Unit)? = null

    @Volatile
    var strictBromOnlyMode: Boolean = false

    private val _phoneState = MutableStateFlow<TargetPhoneState>(TargetPhoneState.Disconnected)
    val phoneState: StateFlow<TargetPhoneState> = _phoneState.asStateFlow()

    private val isConnecting = AtomicBoolean(false)
    private val isPermissionRequested = AtomicBoolean(false)
    private var lastPermissionRequestTimestamp = 0L
    private var requestedDeviceKey: String? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                ACTION_USB_PHONE_PERMISSION -> {
                    isPermissionRequested.set(false)
                    requestedDeviceKey = null
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted && device != null) {
                        if (strictBromOnlyMode && !isBromDevice(device)) return
                        scope.launch { connectDevice(device) }
                    } else {
                        _phoneState.value = TargetPhoneState.Error("USB Permission denied.")
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (device != null) {
                        if (strictBromOnlyMode && !isBromDevice(device)) return
                        scope.launch {
                            if (usbManager.hasPermission(device)) connectDevice(device)
                            else requestDevicePermission(device)
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    isPermissionRequested.set(false)
                    requestedDeviceKey = null
                    disconnect()
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PHONE_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }
    }

    fun isBromDevice(device: UsbDevice): Boolean {
        return device.vendorId == MTK_VID && device.productId in listOf(
            MTK_PID_BROM, MTK_PID_BOOTROM_GENERIC, MTK_PID_DA_HIGH_SPEED, MTK_PID_PRELOADER_ALT
        )
    }

    fun detectDeviceMode(device: UsbDevice): UsbDeviceMode {
        if (isBromDevice(device)) return UsbDeviceMode.BROM
        val vid = device.vendorId
        val pid = device.productId
        if (vid == MTK_VID && (pid == MTK_PID_PRELOADER || pid == MTK_PID_PRELOADER_2 || pid == MTK_PID_CDC || pid == MTK_PID_DEBUG)) {
            return UsbDeviceMode.PRELOADER
        }
        return UsbDeviceMode.UNKNOWN
    }

    fun isMediaTekDevice(device: UsbDevice): Boolean = device.vendorId in SUPPORTED_VIDS
    fun getAttachedDevices(): List<UsbDevice> = usbManager.deviceList.values.toList()
    fun isBromConnected(): Boolean = currentDevice?.let { isBromDevice(it) } ?: false

    fun requestDevicePermission(device: UsbDevice) {
        val deviceKey = "${device.vendorId}:${device.productId}:${device.deviceName}"
        val now = System.currentTimeMillis()
        if (isPermissionRequested.get() && (now - lastPermissionRequestTimestamp < 8000L) && requestedDeviceKey == deviceKey) return

        isPermissionRequested.set(true)
        lastPermissionRequestTimestamp = now
        requestedDeviceKey = deviceKey

        val mode = detectDeviceMode(device)
        val vidPidStr = String.format("0x%04X:0x%04X", device.vendorId, device.productId)
        _phoneState.value = TargetPhoneState.RequestingPermission(
            deviceName = device.productName ?: "Target Phone",
            mode = mode,
            vidPid = vidPidStr
        )

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val permissionIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(ACTION_USB_PHONE_PERMISSION).setPackage(context.packageName),
            flags
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    suspend fun scanAndConnect(forceBromOnly: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val requireBrom = forceBromOnly || strictBromOnlyMode
        val deviceList = usbManager.deviceList
        if (deviceList.isEmpty()) {
            if (!isPermissionRequested.get()) _phoneState.value = TargetPhoneState.Disconnected
            return@withContext false
        }

        if (requireBrom) {
            val bromDevice = deviceList.values.firstOrNull { isBromDevice(it) } ?: return@withContext false
            if (!usbManager.hasPermission(bromDevice)) {
                requestDevicePermission(bromDevice)
                return@withContext false
            }
            return@withContext connectDevice(bromDevice)
        }

        val targetDevice = deviceList.values.firstOrNull { it.vendorId == MTK_VID }
            ?: deviceList.values.firstOrNull { isMediaTekDevice(it) }
            ?: return@withContext false

        if (!usbManager.hasPermission(targetDevice)) {
            requestDevicePermission(targetDevice)
            return@withContext false
        }
        return@withContext connectDevice(targetDevice)
    }

    suspend fun connectDevice(targetDevice: UsbDevice): Boolean = withContext(Dispatchers.IO) {
        if (isConnecting.getAndSet(true)) return@withContext true
        try {
            if (usbConnection != null && currentDevice?.deviceId == targetDevice.deviceId) return@withContext true

            val connection = usbManager.openDevice(targetDevice) ?: run {
                _phoneState.value = TargetPhoneState.Error("Failed to open USB Device.")
                return@withContext false
            }

            var bulkIn: UsbEndpoint? = null
            var bulkOut: UsbEndpoint? = null
            var claimedIface: UsbInterface? = null

            for (i in 0 until targetDevice.interfaceCount) {
                val iface = targetDevice.getInterface(i)
                var tempIn: UsbEndpoint? = null
                var tempOut: UsbEndpoint? = null
                for (j in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(j)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (ep.direction == UsbConstants.USB_DIR_IN) tempIn = ep else tempOut = ep
                    }
                }
                if (tempIn != null && tempOut != null) {
                    claimedIface = iface
                    bulkIn = tempIn
                    bulkOut = tempOut
                    break
                }
            }

            if (claimedIface == null || bulkIn == null || bulkOut == null) {
                connection.close()
                return@withContext false
            }

            usbInterface?.let { usbConnection?.releaseInterface(it) }
            usbConnection?.close()

            connection.claimInterface(claimedIface, true)
            usbConnection = connection
            usbInterface = claimedIface
            inEndpoint = bulkIn
            outEndpoint = bulkOut
            currentDevice = targetDevice

            val mode = detectDeviceMode(targetDevice)
            val isBrom = (mode == UsbDeviceMode.BROM)
            val vidPidStr = String.format("0x%04X:0x%04X", targetDevice.vendorId, targetDevice.productId)

            val state = TargetPhoneState.Connected(
                deviceName = targetDevice.productName ?: "MediaTek Device",
                mode = mode,
                isBromMode = isBrom,
                vidPid = "$vidPidStr [${mode.label}]",
                fileDescriptor = connection.fileDescriptor
            )
            _phoneState.value = state
            onDeviceAutoConnectedListener?.invoke(state)
            return@withContext true
        } catch (e: Exception) {
            _phoneState.value = TargetPhoneState.Error("USB Exception: ${e.message}")
            return@withContext false
        } finally {
            isConnecting.set(false)
        }
    }

    /**
     * Resets USB Endpoint Halt (STALL) condition safely for Android.
     */
    fun clearEndpointHalt(): Boolean {
        val conn = usbConnection ?: return false
        val inEp = inEndpoint ?: return false
        val outEp = outEndpoint ?: return false

        try {
            // Android requires the clean endpoint address without corrupted flags, 
            // or we use standard clear feature with masked endpoint address (mask 0x0F).
            val outAddress = outEp.address and 0x0F or if (outEp.direction == UsbConstants.USB_DIR_IN) 0x80 else 0x00
            val inAddress = inEp.address and 0x0F or if (inEp.direction == UsbConstants.USB_DIR_IN) 0x80 else 0x00

            conn.controlTransfer(0x02, 0x01, 0x0000, outAddress, null, 0, 500)
            conn.controlTransfer(0x02, 0x01, 0x0000, inAddress, null, 0, 500)

            usbInterface?.let {
                conn.releaseInterface(it)
                conn.claimInterface(it, true)
            }
            return true
        } catch (_: Exception) {
            return false
        }
    }

    fun flush(timeoutMs: Int = 15): Int {
        val conn = usbConnection ?: return 0
        val ep = inEndpoint ?: return 0
        val tempBuf = ByteArray(1024)
        var totalFlushed = 0
        while (true) {
            val r = conn.bulkTransfer(ep, tempBuf, tempBuf.size, timeoutMs)
            if (r > 0) totalFlushed += r else break
        }
        return totalFlushed
    }

    suspend fun blastBromHandshakeSync(maxAttempts: Int = 60): Boolean = withContext(Dispatchers.IO) {
        val conn = usbConnection ?: return@withContext false
        val outEp = outEndpoint ?: return@withContext false
        val inEp = inEndpoint ?: return@withContext false

        flush(20)
        val rxBuf = ByteArray(1)
        val sendA0 = byteArrayOf(0xA0.toByte())
        var syncedA0 = false

        for (attempt in 0 until maxAttempts) {
            val w = conn.bulkTransfer(outEp, sendA0, 1, 100)
            if (w == 1) {
                val r = conn.bulkTransfer(inEp, rxBuf, 1, 100)
                if (r == 1 && rxBuf[0] == 0x5F.toByte()) {
                    syncedA0 = true
                    break
                }
            }
            delay(10)
        }

        if (!syncedA0) return@withContext false

        val send0A = byteArrayOf(0x0A.toByte())
        if (conn.bulkTransfer(outEp, send0A, 1, 150) != 1) return@withContext false
        if (conn.bulkTransfer(inEp, rxBuf, 1, 150) != 1 || rxBuf[0] != 0xF5.toByte()) return@withContext false

        val send50 = byteArrayOf(0x50.toByte())
        if (conn.bulkTransfer(outEp, send50, 1, 150) != 1) return@withContext false
        if (conn.bulkTransfer(inEp, rxBuf, 1, 150) != 1 || rxBuf[0] != 0xAF.toByte()) return@withContext false

        val send05 = byteArrayOf(0x05.toByte())
        if (conn.bulkTransfer(outEp, send05, 1, 150) != 1) return@withContext false
        if (conn.bulkTransfer(inEp, rxBuf, 1, 150) != 1 || rxBuf[0] != 0xFA.toByte()) return@withContext false

        return@withContext true
    }

    fun writeRaw(bytes: ByteArray, timeoutMs: Int = 1000): Int {
        val conn = usbConnection ?: return -1
        val ep = outEndpoint ?: return -1
        return conn.bulkTransfer(ep, bytes, bytes.size, timeoutMs)
    }

    fun readRaw(buffer: ByteArray, timeoutMs: Int = 1000): Int {
        val conn = usbConnection ?: return -1
        val ep = inEndpoint ?: return -1
        return conn.bulkTransfer(ep, buffer, buffer.size, timeoutMs)
    }

    fun controlTransfer(requestType: Int, request: Int, value: Int, index: Int, buffer: ByteArray?, length: Int, timeoutMs: Int = 1000): Int {
        val conn = usbConnection ?: return -1
        return conn.controlTransfer(requestType, request, value, index, buffer, length, timeoutMs)
    }

    fun sendWatchdogResetControl(): Boolean {
        val conn = usbConnection ?: return false
        val res = conn.controlTransfer(0x40, 0x01, 0, 0, null, 0, 1000)
        return res >= 0
    }

    fun disconnect() {
        try {
            usbInterface?.let { usbConnection?.releaseInterface(it) }
            usbConnection?.close()
        } catch (_: Exception) {}
        usbConnection = null
        usbInterface = null
        inEndpoint = null
        outEndpoint = null
        currentDevice = null
        _phoneState.value = TargetPhoneState.Disconnected
    }

    fun unregister() {
        try { context.unregisterReceiver(usbReceiver) } catch (_: Exception) {}
    }

    fun isConnected(): Boolean = usbConnection != null
}
