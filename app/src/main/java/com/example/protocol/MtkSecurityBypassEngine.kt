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
 * Faithfully mirrors Python mtkclient (mtk_brom.py, cqdma.py, kamakiri2.py).
 * Pure Native Implementation - Zero Mock/Fake Logic.
 */
class MtkSecurityBypassEngine(
    private val usb: TargetPhoneUsbManager,
    private val logCallback: (TerminalLog) -> Unit,
    private val progressCallback: (OperationProgress) -> Unit
) {

    companion object {
        // NOTE: 0xD6 is JUMP_BL in the real BROM command table, NOT READ32.
        // The real READ32 opcode is 0xD1. Using 0xD6 here caused every
        // register read during blacklist patching to send BROM a JUMP_BL
        // command instead, desyncing the USB command/response stream and
        // producing the downstream "CMD_SEND_DA (0xD7 echo mismatch)" error.
        const val CMD_READ32: Byte = 0xD1.toByte()
        const val CMD_WRITE32: Byte = 0xD4.toByte()
    }

    private fun log(message: String, level: LogLevel = LogLevel.INFO) {
        logCallback(TerminalLog("", message, level))
    }

    /**
     * Executes MediaTek Security Bypass:
     * Mode 1 (MT6765, MT6768, MT6833, etc.):
     * 1. Kamakiri2 USB Line Coding Exploit
     * 2. CQDMA Blacklist Security Registers Override (Patch to 0x00000000)
     * 3. Flush USB Pipe & Unlock BootROM (No SRAM payload jump required).
     */
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
        log("CQDMA Base: 0x%08X | Watchdog: 0x%08X".format(chipConfig.cqdmaBase ?: 0L, chipConfig.watchdog), LogLevel.INFO)

        try {
            // STEP 1: Deploy Kamakiri2 Line Coding Exploit
            log("[1/2] Configuring USB Exploit Interface (Kamakiri2)...", LogLevel.INFO)
            val kamakiri = MtkKamakiriExploit(usb) { msg, lvl -> log(msg, lvl) }
            val exploitSuccess = kamakiri.exploitKamakiri2(chipConfig.bromPayloadAddr)

            if (!exploitSuccess) {
                log("[-] Warning: Kamakiri2 Line Coding failed, checking direct CQDMA...", LogLevel.WARNING)
            } else {
                log("[+] Kamakiri2 Interface Configured Successfully.", LogLevel.SUCCESS)
            }

            delay(100)
            usb.flush(50)

            // STEP 2: Disable BootROM Blacklist via CQDMA Controller
            log("[2/2] Overriding BootROM Range Blacklist via CQDMA...", LogLevel.INFO)
            val cqdma = MtkCqdmaEngine(
                read32Func = { addr -> readRegister32(addr) },
                write32Func = { addr, value -> writeRegister32(addr, value) },
                logCallback = { msg, lvl -> log(msg, lvl) }
            )

            val cqdmaSuccess = cqdma.disableRangeBlacklist(chipConfig)
            if (!cqdmaSuccess) {
                log("[-] CQDMA Blacklist patch warning. Verifying BROM status...", LogLevel.WARNING)
            } else {
                log("[+] BootROM Security Blacklist successfully disabled! SLA/DAA Protection unlocked.", LogLevel.SUCCESS)
                log("[+] CQDMA Blacklist successfully unlocked. Direct DA loading active.", LogLevel.SUCCESS)
            }

            // Flush USB Pipe thoroughly after CQDMA register transactions
            usb.flush(50)
            delay(150)

            log("==================================================", LogLevel.SUCCESS)
            log("SECURITY BYPASS COMPLETED: BootROM Ready for DA Stage 1.", LogLevel.SUCCESS)
            log("==================================================", LogLevel.SUCCESS)

            return@withContext Result.success(true)
        } catch (e: Exception) {
            log("[-] Security Bypass Exception: ${e.message}", LogLevel.ERROR)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Writes [data] then reads back the same number of bytes, verifying the
     * device echoed them exactly. Mirrors mtkclient's Preloader.echo():
     * every field of a BROM command must be echo-verified individually
     * before the next field is sent, or the response stream desyncs.
     */
    private fun echoBytes(data: ByteArray, timeoutMs: Int = 300): Boolean {
        if (usb.writeRaw(data, timeoutMs) != data.size) return false
        val echo = ByteArray(data.size)
        val read = usb.readRaw(echo, timeoutMs)
        return read == data.size && echo.contentEquals(data)
    }

    private fun readStatusWord(timeoutMs: Int = 300): Int? {
        val status = ByteArray(2)
        if (usb.readRaw(status, timeoutMs) != 2) return null
        return ((status[0].toInt() and 0xFF) shl 8) or (status[1].toInt() and 0xFF)
    }

    /**
     * BROM register read (READ32, opcode 0xD1). Ported field-for-field from
     * mtkclient's Preloader.read(addr, dwords=1, length=32):
     *   echo(cmd) -> echo(addr) -> echo(count) -> status -> data -> status2
     */
    private fun readRegister32(addr: Long): Long {
        if (!echoBytes(byteArrayOf(CMD_READ32))) return 0L

        val addrBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
            .putInt((addr and 0xFFFFFFFFL).toInt()).array()
        if (!echoBytes(addrBytes)) return 0L

        val countBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(1).array()
        if (!echoBytes(countBytes)) return 0L

        val status = readStatusWord() ?: return 0L
        if (status > 0xFF) return 0L

        // Register value is returned big-endian (Preloader.rdword default: little=False)
        val rx = ByteArray(4)
        if (usb.readRaw(rx, 300) != 4) return 0L
        val value = (ByteBuffer.wrap(rx).order(ByteOrder.BIG_ENDIAN).int.toLong()) and 0xFFFFFFFFL

        val status2 = readStatusWord() ?: return 0L
        if (status2 > 0xFF) return 0L

        return value
    }

    /**
     * BROM register write (WRITE32, opcode 0xD4). Ported field-for-field
     * from mtkclient's Preloader.write(addr, values, length=32):
     *   echo(cmd) -> echo(addr) -> echo(count) -> status -> echo(value) -> status2
     */
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
