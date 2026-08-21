package com.example.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ARM32 (ARMv7-A) and AArch64 (ARMv8-A) Binary Disassembly & Runtime Patching Engine
 * Ported from mtkclient ArchTools, ArmTools, Aarch64Tools
 */
class MtkArchTools(
    private val data: ByteArray,
    private val baseAddr: Long
) {

    fun vaToOffset(va: Long): Int? {
        val off = (va - baseAddr).toInt()
        return if (off in 0 until data.size) off else null
    }

    fun offsetToVa(offset: Int): Long? {
        return if (offset in 0 until data.size) baseAddr + offset else null
    }

    fun readU32(offset: Int): Long? {
        if (offset + 4 > data.size || offset < 0) return null
        val buf = ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN)
        return buf.int.toLong() and 0xFFFFFFFFL
    }

    fun readU64(offset: Int): Long? {
        if (offset + 8 > data.size || offset < 0) return null
        val buf = ByteBuffer.wrap(data, offset, 8).order(ByteOrder.LITTLE_ENDIAN)
        return buf.long
    }

    /**
     * Patches function at offset with immediate return value (MOV R0/X0, #value; BX LR / RET)
     */
    fun forceReturn(offset: Int, value: Int, isThumb: Boolean = false, is64Bit: Boolean = false): Boolean {
        if (offset < 0) return false

        if (is64Bit) {
            // AArch64: MOV X0, #value (0xD2800000 | ((value & 0xFFFF) << 5)), RET (0xD65F03C0)
            if (offset + 8 > data.size) return false
            val movX0 = 0xD2800000L or ((value and 0xFFFF).toLong() shl 5)
            val ret = 0xD65F03C0L

            val buf = ByteBuffer.wrap(data, offset, 8).order(ByteOrder.LITTLE_ENDIAN)
            buf.putInt(movX0.toInt())
            buf.putInt(ret.toInt())
            return true
        } else if (isThumb) {
            // Thumb-2: MOV R0, #value (0x2000 | (value & 0xFF)), BX LR (0x4770)
            if (offset + 4 > data.size) return false
            val movR0 = 0x2000 or (value and 0xFF)
            val bxLr = 0x4770

            val buf = ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN)
            buf.putShort(movR0.toShort())
            buf.putShort(bxLr.toShort())
            return true
        } else {
            // ARM32: MOV R0, #value (0xE3A00000 | (value & 0xFF)), BX LR (0xE12FFF1E)
            if (offset + 8 > data.size) return false
            val movR0 = 0xE3A00000L or (value and 0xFF).toLong() or (((value shl 4) and 0xF00).toLong())
            val bxLr = 0xE12FFF1EL

            val buf = ByteBuffer.wrap(data, offset, 8).order(ByteOrder.LITTLE_ENDIAN)
            buf.putInt(movR0.toInt())
            buf.putInt(bxLr.toInt())
            return true
        }
    }

    /**
     * Decodes ARM32 Branch with Link (BL) or Unconditional Branch (B)
     */
    fun decodeArm32Bl(instr: Long, pc: Long): Long? {
        val opcode = instr and 0xFF000000L
        if (opcode != 0xEB000000L && opcode != 0xEA000000L) return null
        var imm24 = instr and 0x00FFFFFFL
        if ((imm24 and 0x00800000L) != 0L) {
            imm24 -= 0x01000000L // Sign extend
        }
        val armPc = pc + 8
        return armPc + (imm24 * 4)
    }

    /**
     * Decodes AArch64 ADRP (PC-relative Page calculation)
     */
    fun decodeAarch64Adrp(instr: Long, pc: Long): Pair<Long, Int>? {
        if ((instr and 0x9F000000L) != 0x90000000L) return null
        val rd = (instr and 0x1FL).toInt()
        val immlo = (instr shr 29) and 0x3L
        val immhi = (instr shr 5) and 0x7FFFFL
        var imm = (immhi shl 2) or immlo
        if ((imm and 0x100000L) != 0L) {
            imm -= 0x200000L
        }
        val page = pc and -0x1000L
        return Pair(page + (imm shl 12), rd)
    }

    /**
     * Searches for string in binary data and finds xref instruction offset
     */
    fun findStringXref(str: String): Int? {
        val target = str.toByteArray(Charsets.UTF_8)
        var strOffset = -1

        // Search null-terminated
        val withNull = target + byteArrayOf(0)
        for (i in 0..data.size - withNull.size) {
            var match = true
            for (j in withNull.indices) {
                if (data[i + j] != withNull[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                strOffset = i
                break
            }
        }

        if (strOffset == -1) {
            for (i in 0..data.size - target.size) {
                var match = true
                for (j in target.indices) {
                    if (data[i + j] != target[j]) {
                        match = false
                        break
                    }
                }
                if (match) {
                    strOffset = i
                    break
                }
            }
        }

        if (strOffset == -1) return null
        val strVa = baseAddr + strOffset
        val low16 = strVa and 0xFFFFL
        val high16 = (strVa shr 16) and 0xFFFFL

        // Scan for MOVW/MOVT in ARM32
        for (off in 0 until data.size - 8 step 4) {
            val instr = readU32(off) ?: continue
            // MOVW check
            if ((instr and 0x0FF00000L) == 0x03000000L) {
                val imm4 = (instr shr 16) and 0xFL
                val imm12 = instr and 0xFFFL
                val imm16 = (imm4 shl 12) or imm12
                if (imm16 == low16) {
                    return off
                }
            }
        }

        return strOffset
    }
}
