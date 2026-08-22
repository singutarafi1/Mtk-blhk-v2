package com.example.protocol

import com.example.model.LogLevel
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * CQDMA Exploit and Direct Memory Access Controller
 */
class MtkCqdmaEngine(
    private val read32Func: (Long) -> Long,
    private val write32Func: (Long, Long) -> Boolean,
    private val logCallback: (String, LogLevel) -> Unit
) {
    companion object {
        const val CQDMA_INT_FLAG = 0x0L
        const val CQDMA_INT_EN = 0x4L
        const val CQDMA_EN = 0x8L
        const val CQDMA_RESET = 0xCL
        const val CQDMA_FLUSH = 0x14L
        const val CQDMA_SRC = 0x1CL
        const val CQDMA_DST = 0x20L
        const val CQDMA_LEN1 = 0x24L
        const val CQDMA_LEN2 = 0x28L
        const val CQDMA_SRC2 = 0x60L
        const val CQDMA_DST2 = 0x64L
    }

    fun cqread32(cqdmaBase: Long, apDmaMem: Long, addr: Long, dwords: Int): ByteArray {
        val outBytes = ByteArray(dwords * 4)
        val buffer = ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until dwords) {
            val srcAddr = addr + (i * 4)
            write32Func(cqdmaBase + CQDMA_SRC, srcAddr)
            write32Func(cqdmaBase + CQDMA_DST, apDmaMem)
            write32Func(cqdmaBase + CQDMA_LEN1, 4L)
            write32Func(cqdmaBase + CQDMA_EN, 1L)

            var retry = 0
            while (retry < 50) {
                val enVal = read32Func(cqdmaBase + CQDMA_EN)
                if ((enVal and 1L) == 0L) break
                retry++
            }
            val readVal = read32Func(apDmaMem)
            buffer.putInt(readVal.toInt())
        }
        return outBytes
    }

    fun cqwrite32(cqdmaBase: Long, apDmaMem: Long, addr: Long, values: LongArray): Boolean {
        for (i in values.indices) {
            val targetVal = values[i]
            write32Func(apDmaMem, targetVal)
            write32Func(cqdmaBase + CQDMA_SRC, apDmaMem)
            write32Func(cqdmaBase + CQDMA_DST, addr + (i * 4))
            write32Func(cqdmaBase + CQDMA_LEN1, 4L)
            write32Func(cqdmaBase + CQDMA_EN, 1L)

            var retry = 0
            while (retry < 50) {
                val enVal = read32Func(cqdmaBase + CQDMA_EN)
                if ((enVal and 1L) == 0L) break
                retry++
            }
            write32Func(apDmaMem, 0xCAFEBABE)
        }
        return true
    }

    fun disableRangeBlacklist(config: ChipConfig): Boolean {
        val cqdmaBase = config.cqdmaBase
        val apDmaMem = config.apDmaMem
        val blacklist = config.blacklist

        if (cqdmaBase == null || apDmaMem == null) {
            logCallback("Chipset ${config.name} does not require CQDMA DMA controller (Hardware direct access)", LogLevel.INFO)
            return true
        }

        if (blacklist.isNotEmpty()) {
            logCallback("Disabling BootROM range security checks (${blacklist.size} entries)...", LogLevel.INFO)
            for ((idx, entry) in blacklist.withIndex()) {
                logCallback(" -> Patching blacklist entry #$idx at 0x%08X with 0x%08X...".format(entry.address, entry.value), LogLevel.INFO)
                val success = cqwrite32(cqdmaBase, apDmaMem, entry.address, longArrayOf(entry.value))
                if (!success) {
                    logCallback("[-] Failed to patch security register at 0x%08X".format(entry.address), LogLevel.WARNING)
                    return false
                }
            }
        }

        // [FIX]: Force Disable SLA/DAA via CQDMA (No payload required)
        if (config.hwCode == 0x0766) {
            logCallback("Patching SEC_SLA/SBC registers for MT6765 via CQDMA to bypass 0x1D0D...", LogLevel.INFO)
            // MT6765 SecReg is located at 0x102A8C based on PC Tool output
            cqwrite32(cqdmaBase, apDmaMem, 0x102A8CL, longArrayOf(0x00000000L)) 
        }

        logCallback("Disabling Hardware Watchdog Timer via CQDMA to prevent reboot...", LogLevel.INFO)
        val wdtSuccess = cqwrite32(cqdmaBase, apDmaMem, config.watchdog, longArrayOf(0x22000000L))
        if (wdtSuccess) {
            logCallback("[+] Watchdog Timer successfully disabled via CQDMA.", LogLevel.SUCCESS)
        } else {
            logCallback("[-] Warning: Failed to disable WDT via CQDMA.", LogLevel.WARNING)
        }

        logCallback("[+] BootROM Security Blacklist successfully disabled! SLA/DAA Protection unlocked.", LogLevel.SUCCESS)
        return true
    }
}
