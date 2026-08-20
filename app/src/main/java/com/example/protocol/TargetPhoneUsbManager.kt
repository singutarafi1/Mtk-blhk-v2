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
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class UsbDeviceMode(val label: String, val description: String) {
    BROM("MTK BROM", "MediaTek BootROM Mode (Flash & Service Ready)"),
    PRELOADER("Preloader", "MTK Preloader / DA VCOM Port"),
    FASTBOOT("Fastboot", "Android Fastboot Bootloader Mode"),
    ADB("ADB Debugging", "Android USB Debugging Interface"),
    MTP("MTP / File Transfer", "Media Transfer Protocol (Normal Android Boot)"),
    CDC_SERIAL("CDC Serial", "Serial / UART Bridge Device"),
    UNKNOWN("USB Device", "Connected USB Peripheral"),
    NONE("Unplugged", "No USB Device Connected")
}

sealed class TargetPhoneState {
    object Disconnected : TargetPhoneState()
    data class RequestingPermission(
        val deviceName: String,
        val mode: UsbDeviceMode,
        val vidPid: String
    ) : TargetPhoneState()
    data class Connected(
        val deviceName: String,
        val mode: UsbDeviceMode,
        val isBromMode: Boolean,
        val vidPid: String,
        val fileDescriptor: Int = -1
    ) : TargetPhoneState()
    data class Error(val message: String) : TargetPhoneState()
}

class TargetPhoneUsbManager(
    private val context: Context
) {
    companion object {
        const val ACTION_USB_PHONE_PERMISSION = "com.example.mtkbridge.USB_PHONE_PERMISSION"
        const val MTK_VID = 0x0E8D // MediaTek Inc
        const val MTK_PID_BROM = 0x0003 // MTK USB Port (BROM Mode)
        const val MTK_PID_PRELOADER = 0x2000 // MTK DA / Preloader USB VCOM Port
        const val MTK_PID_PRELOADER_2 = 0x2001
        const val MTK_PID_CDC = 0x2004
        const val MTK_PID_DEBUG = 0x2005
        const val MTK_PID_BOOTROM_GENERIC = 0x0001
        const val MTK_PID_DA_HIGH_SPEED = 0x0002
        const val MTK_PID_PRELOADER_ALT = 0x0005

        // Known USB Vendor IDs used by various MTK devices & flashing cables
        val SUPPORTED_VIDS = setOf(
            0x0E8D, // MediaTek Inc
            0x1004, // LG Electronics MTK
            0x0BB4, // HTC MTK
            0x2A45, // Meizu MTK
            0x1782, // Spreadtrum/MTK fallback
            0x1A86, // CH340 / USB Serial converter (if using OTG bridge)
            0x10C4, // CP210x Serial (if using testpoint jig)
            0x0403, // FTDI Serial
            0x18D1, // Google / Fastboot
            0x2717  // Xiaomi Fastboot
        )
    }

    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var usbConnection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var inEndpoint: UsbEndpoint? = null
    private var outEndpoint: UsbEndpoint? = null
    var currentDevice: UsbDevice? = null
        private set
    private val scope = CoroutineScope(Dispatchers.IO)

    var onDeviceAutoConnectedListener: ((TargetPhoneState.Connected) -> Unit)? = null

    // When true, USB auto-connect and scanning will STRICTLY accept BROM mode devices only and ignore all other modes (Preloader, ADB, Fastboot, MTP, CDC, Charging, etc.)
    @Volatile
    var strictBromOnlyMode: Boolean = false

    private val _phoneState = MutableStateFlow<TargetPhoneState>(TargetPhoneState.Disconnected)
    val phoneState: StateFlow<TargetPhoneState> = _phoneState.asStateFlow()

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
                        if (strictBromOnlyMode && !isBromDevice(device)) {
                            return
                        }
                        scope.launch {
                            connectDevice(device)
                        }
                    } else {
                        _phoneState.value = TargetPhoneState.Error("USB Permission was not granted.")
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
                        if (strictBromOnlyMode && !isBromDevice(device)) {
                            // In strict BROM mode, do not connect or request permission for non-BROM devices!
                            return
                        }
                        scope.launch {
                            if (usbManager.hasPermission(device)) {
                                connectDevice(device)
                            } else {
                                requestDevicePermission(device)
                            }
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
        if (device.vendorId == MTK_VID) {
            return device.productId in listOf(
                MTK_PID_BROM,               // 0x0003
                MTK_PID_BOOTROM_GENERIC,    // 0x0001
                MTK_PID_DA_HIGH_SPEED,      // 0x0002
                MTK_PID_PRELOADER_ALT       // 0x0005
            )
        }
        return false
    }

    fun detectDeviceMode(device: UsbDevice): UsbDeviceMode {
        val vid = device.vendorId
        val pid = device.productId

        // 1. Check MTK BROM
        if (isBromDevice(device)) {
            return UsbDeviceMode.BROM
        }
        // 2. Check MTK Preloader
        if (vid == MTK_VID && (pid == MTK_PID_PRELOADER || pid == MTK_PID_PRELOADER_2 || pid == MTK_PID_CDC || pid == MTK_PID_DEBUG)) {
            return UsbDeviceMode.PRELOADER
        }

        // 3. Check Interfaces for Fastboot / ADB / MTP / CDC
        var hasFastboot = false
        var hasAdb = false
        var hasMtp = false
        var hasCdc = false

        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            val cls = iface.interfaceClass
            val subCls = iface.interfaceSubclass
            val proto = iface.interfaceProtocol

            // Fastboot: Vendor Specific (0xFF), Subclass 0x42, Protocol 0x03
            if (cls == UsbConstants.USB_CLASS_VENDOR_SPEC && subCls == 0x42 && proto == 0x03) {
                hasFastboot = true
            }
            // ADB: Vendor Specific (0xFF), Subclass 0x42, Protocol 0x01
            if (cls == UsbConstants.USB_CLASS_VENDOR_SPEC && subCls == 0x42 && proto == 0x01) {
                hasAdb = true
            }
            // MTP: Still Image (0x06) or 0xFF/0xFF/0x00
            if (cls == UsbConstants.USB_CLASS_STILL_IMAGE || (cls == 0xFF && subCls == 0xFF && proto == 0x00)) {
                hasMtp = true
            }
            // CDC / Serial
            if (cls == UsbConstants.USB_CLASS_CDC_DATA || cls == UsbConstants.USB_CLASS_COMM) {
                hasCdc = true
            }
        }

        if (hasFastboot || (vid == 0x18D1 && (pid == 0x4EE0 || pid == 0xD00D)) || (vid == 0x2717 && (pid == 0xFF40 || pid == 0xFF48))) {
            return UsbDeviceMode.FASTBOOT
        }
        if (hasAdb) {
            return UsbDeviceMode.ADB
        }
        if (vid == MTK_VID) {
            return UsbDeviceMode.PRELOADER
        }
        if (hasCdc || vid == 0x1A86 || vid == 0x10C4 || vid == 0x0403) {
            return UsbDeviceMode.CDC_SERIAL
        }
        if (hasMtp) {
            return UsbDeviceMode.MTP
        }

        return UsbDeviceMode.UNKNOWN
    }

    fun isMediaTekDevice(device: UsbDevice): Boolean {
        if (SUPPORTED_VIDS.contains(device.vendorId)) return true
        if (device.vendorId == MTK_VID) return true
        if (device.deviceClass == UsbConstants.USB_CLASS_COMM || device.deviceClass == UsbConstants.USB_CLASS_VENDOR_SPEC) return true
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA ||
                iface.interfaceClass == UsbConstants.USB_CLASS_COMM ||
                iface.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC) {
                return true
            }
        }
        return false
    }

    fun getAttachedDevices(): List<UsbDevice> = usbManager.deviceList.values.toList()

    fun isBromConnected(): Boolean {
        val dev = currentDevice ?: return false
        return isBromDevice(dev)
    }

    fun getRawDeviceCount(): Int = usbManager.deviceList.size
    
    fun getRawDeviceList(): String = usbManager.deviceList.values
        .joinToString(", ") { "VID:0x%04X PID:0x%04X".format(it.vendorId, it.productId) }

    fun requestDevicePermission(device: UsbDevice) {
        val deviceKey = "${device.vendorId}:${device.productId}:${device.deviceName}"
        val now = System.currentTimeMillis()

        // Debounce permission request so the system dialog remains steady
        if (isPermissionRequested.get() && (now - lastPermissionRequestTimestamp < 8000L) && requestedDeviceKey == deviceKey) {
            return
        }

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
            if (!isPermissionRequested.get()) {
                _phoneState.value = TargetPhoneState.Disconnected
            }
            return@withContext false
        }

        // When in BROM-only mode, STRICTLY search for BROM devices only!
        if (requireBrom) {
            var bromDevice: UsbDevice? = null
            for ((_, device) in deviceList) {
                if (isBromDevice(device)) {
                    bromDevice = device
                    break
                }
            }
            if (bromDevice == null) {
                return@withContext false
            }
            if (!usbManager.hasPermission(bromDevice)) {
                requestDevicePermission(bromDevice)
                return@withContext false
            }
            return@withContext connectDevice(bromDevice)
        }

        // Generic non-BROM mode scanning (when not executing BROM actions)
        var targetDevice: UsbDevice? = null
        for ((_, device) in deviceList) {
            if (device.vendorId == MTK_VID) {
                targetDevice = device
                break
            }
        }
        if (targetDevice == null) {
            for ((_, device) in deviceList) {
                if (isMediaTekDevice(device) || detectDeviceMode(device) != UsbDeviceMode.UNKNOWN) {
                    targetDevice = device
                    break
                }
            }
        }
        if (targetDevice == null) {
            targetDevice = deviceList.values.firstOrNull()
        }
        if (targetDevice == null) {
            if (!isPermissionRequested.get()) {
                _phoneState.value = TargetPhoneState.Disconnected
            }
            return@withContext false
        }
        if (!usbManager.hasPermission(targetDevice)) {
            requestDevicePermission(targetDevice)
            return@withContext false
        }
        return@withContext connectDevice(targetDevice)
    }

    suspend fun connectDevice(targetDevice: UsbDevice): Boolean = withContext(Dispatchers.IO) {
        try {
            val connection = usbManager.openDevice(targetDevice) ?: run {
                _phoneState.value = TargetPhoneState.Error("Failed to open target USB port (OTG connection refused).")
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
                        if (ep.direction == UsbConstants.USB_DIR_IN) tempIn = ep
                        else tempOut = ep
                    }
                }

                if (tempIn != null && tempOut != null) {
                    claimedIface = iface
                    bulkIn = tempIn
                    bulkOut = tempOut
                    break
                }
            }

            // Fallback: search across interfaces
            if (claimedIface == null || bulkIn == null || bulkOut == null) {
                for (i in 0 until targetDevice.interfaceCount) {
                    val iface = targetDevice.getInterface(i)
                    for (j in 0 until iface.endpointCount) {
                        val ep = iface.getEndpoint(j)
                        if (ep.direction == UsbConstants.USB_DIR_IN && bulkIn == null) {
                            bulkIn = ep
                            if (claimedIface == null) claimedIface = iface
                        } else if (ep.direction == UsbConstants.USB_DIR_OUT && bulkOut == null) {
                            bulkOut = ep
                            if (claimedIface == null) claimedIface = iface
                        }
                    }
                }
            }

            if (claimedIface == null || bulkIn == null || bulkOut == null) {
                connection.close()
                val mode = detectDeviceMode(targetDevice)
                val vidPidStr = String.format("0x%04X:0x%04X", targetDevice.vendorId, targetDevice.productId)
                _phoneState.value = TargetPhoneState.Connected(
                    deviceName = targetDevice.productName ?: "USB Device",
                    mode = mode,
                    isBromMode = (mode == UsbDeviceMode.BROM),
                    vidPid = "$vidPidStr [${mode.label}]",
                    fileDescriptor = -1
                )
                return@withContext true
            }

            connection.claimInterface(claimedIface, true)
            usbConnection = connection
            usbInterface = claimedIface
            inEndpoint = bulkIn
            outEndpoint = bulkOut
            currentDevice = targetDevice

            val mode = detectDeviceMode(targetDevice)
            val isBrom = (mode == UsbDeviceMode.BROM)
            val vidPidStr = String.format("0x%04X:0x%04X", targetDevice.vendorId, targetDevice.productId)
            val rawFd = connection.fileDescriptor

            val state = TargetPhoneState.Connected(
                deviceName = targetDevice.productName ?: "MediaTek Device",
                mode = mode,
                isBromMode = isBrom,
                vidPid = "$vidPidStr [${mode.label}]",
                fileDescriptor = rawFd
            )
            _phoneState.value = state
            onDeviceAutoConnectedListener?.invoke(state)
            return@withContext true
        } catch (e: Exception) {
            _phoneState.value = TargetPhoneState.Error("Target USB Error: ${e.message}")
            return@withContext false
        }
    }

    fun flush(timeoutMs: Int = 10): Int {
        val conn = usbConnection ?: return 0
        val ep = inEndpoint ?: return 0
        val tempBuf = ByteArray(1024)
        var totalFlushed = 0
        while (true) {
            val r = conn.bulkTransfer(ep, tempBuf, tempBuf.size, timeoutMs)
            if (r > 0) {
                totalFlushed += r
            } else {
                break
            }
        }
        return totalFlushed
    }

    /**
     * Executes MTK handshake sync sequence matching mtkclient python implementation:
     * 1. Flushes leftover USB buffer.
     * 2. Repeatedly sends 0xA0 until 0x5F echo is locked.
     * 3. Sends 0x0A -> expects 0xF5, 0x50 -> expects 0xAF, 0x05 -> expects 0xFA.
     */
    fun blastBromHandshakeSync(maxAttempts: Int = 60): Boolean {
        val conn = usbConnection ?: return false
        val outEp = outEndpoint ?: return false
        val inEp = inEndpoint ?: return false

        flush(15)

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
            try { Thread.sleep(10) } catch (_: Exception) {}
        }

        if (!syncedA0) {
            return false
        }

        // Send 0x0A -> 0xF5
        val send0A = byteArrayOf(0x0A.toByte())
        if (conn.bulkTransfer(outEp, send0A, 1, 150) != 1) return false
        if (conn.bulkTransfer(inEp, rxBuf, 1, 150) != 1 || rxBuf[0] != 0xF5.toByte()) return false

        // Send 0x50 -> 0xAF
        val send50 = byteArrayOf(0x50.toByte())
        if (conn.bulkTransfer(outEp, send50, 1, 150) != 1) return false
        if (conn.bulkTransfer(inEp, rxBuf, 1, 150) != 1 || rxBuf[0] != 0xAF.toByte()) return false

        // Send 0x05 -> 0xFA
        val send05 = byteArrayOf(0x05.toByte())
        if (conn.bulkTransfer(outEp, send05, 1, 150) != 1) return false
        if (conn.bulkTransfer(inEp, rxBuf, 1, 150) != 1 || rxBuf[0] != 0xFA.toByte()) return false

        return true
    }

    fun getFileDescriptor(): Int {
        return usbConnection?.fileDescriptor ?: -1
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

    fun controlTransfer(
        requestType: Int,
        request: Int,
        value: Int,
        index: Int,
        buffer: ByteArray?,
        length: Int,
        timeoutMs: Int = 1000
    ): Int {
        val conn = usbConnection ?: return -1
        return conn.controlTransfer(requestType, request, value, index, buffer, length, timeoutMs)
    }

    fun sendWatchdogResetControl(): Boolean {
        val conn = usbConnection ?: return false
        val res = conn.controlTransfer(
            0x40, // USB_TYPE_VENDOR | USB_RECIP_DEVICE | USB_DIR_OUT
            0x01, // Request
            0x0000,
            0x0000,
            null,
            0,
            1000
        )
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
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (_: Exception) {}
    }

    fun isConnected(): Boolean = (usbConnection != null)
}


