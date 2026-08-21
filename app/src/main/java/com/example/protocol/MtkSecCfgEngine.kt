package com.example.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Result data class for SecCfg parsing & creation
 */
data class SecCfgStatusResult(
    val isValid: Boolean,
    val version: Int,
    val isUnlocked: Boolean,
    val lockStateCode: Int,
    val hwType: String,
    val rawSize: Int,
    val message: String
)

/**
 * SecCfg V3 & V4 Parser and Payload Generator ported from mtkclient seccfg.py
 */
object MtkSecCfgEngine {

    const val MAGIC_SECCFG = 0x4D4D4D4D // "MMMM"
    const val ENDFLAG_SECCFG = 0x45454545 // "EEEE"

    const val SECCFG_STATUS_COMPLETE = 0x43434343 // "CCCC"
    const val SECCFG_STATUS_INCOMPLETE = 0x49494949 // "IIII"

    const val ATTR_LOCK = 0x6000
    const val ATTR_DEFAULT = 0x33333333 // Lock
    const val ATTR_UNLOCK = 0x44444444 // Unlock

    const val LOCK_STATE_LOCKED = 1
    const val LOCK_STATE_UNLOCKED = 3

    val INFO_HEADER_V3 = "AND_SECCFG_v\u0000\u0000\u0000\u0000".toByteArray(Charsets.ISO_8859_1)

    /**
     * Inspects given SecCfg raw partition byte buffer
     */
    fun parseSecCfg(data: ByteArray): SecCfgStatusResult {
        if (data.size < 0x40) {
            return SecCfgStatusResult(
                isValid = false,
                version = 0,
                isUnlocked = false,
                lockStateCode = -1,
                hwType = "Unknown",
                rawSize = data.size,
                message = "SecCfg partition data too small (${data.size} bytes)"
            )
        }

        // Check if V3 (starts with AND_SECCFG_v)
        val header16 = data.copyOfRange(0, 16)
        if (header16.contentEquals(INFO_HEADER_V3)) {
            return parseV3(data)
        }

        // Check if V4 (starts with 0x4D4D4D4D magic)
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buffer.getInt(0)
        if (magic == MAGIC_SECCFG) {
            return parseV4(data)
        }

        return SecCfgStatusResult(
            isValid = false,
            version = 0,
            isUnlocked = false,
            lockStateCode = -1,
            hwType = "Unknown",
            rawSize = data.size,
            message = "Unrecognized SecCfg header magic (0x%08X)".format(magic)
        )
    }

    private fun parseV4(data: ByteArray): SecCfgStatusResult {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buffer.getInt(0)
        val version = buffer.getInt(4)
        val size = buffer.getInt(8)
        val lockState = buffer.getInt(12)
        val dmVerity = buffer.getInt(16)
        val sbootRuntime = buffer.getInt(20)
        val endflag = buffer.getInt(24)

        if (magic != MAGIC_SECCFG || endflag != ENDFLAG_SECCFG) {
            return SecCfgStatusResult(
                isValid = false,
                version = version,
                isUnlocked = false,
                lockStateCode = lockState,
                hwType = "Invalid V4",
                rawSize = data.size,
                message = "Invalid V4 markers: magic=0x%08X, endflag=0x%08X".format(magic, endflag)
            )
        }

        val isUnlocked = (lockState == LOCK_STATE_UNLOCKED)
        return SecCfgStatusResult(
            isValid = true,
            version = version,
            isUnlocked = isUnlocked,
            lockStateCode = lockState,
            hwType = "V4-SEJ",
            rawSize = size,
            message = if (isUnlocked) "Bootloader UNLOCKED (V4 state=3)" else "Bootloader LOCKED (V4 state=$lockState)"
        )
    }

    private fun parseV3(data: ByteArray): SecCfgStatusResult {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buffer.getInt(16)
        val version = buffer.getInt(20)
        val size = buffer.getInt(24)

        if (magic != MAGIC_SECCFG) {
            return SecCfgStatusResult(
                isValid = false,
                version = version,
                isUnlocked = false,
                lockStateCode = -1,
                hwType = "Invalid V3",
                rawSize = data.size,
                message = "Invalid V3 magic 0x%08X".format(magic)
            )
        }

        val encLen = buffer.getInt(32)
        val isUnlocked = (encLen == 0x07F20000)

        return SecCfgStatusResult(
            isValid = true,
            version = version,
            isUnlocked = isUnlocked,
            lockStateCode = if (isUnlocked) 3 else 1,
            hwType = "V3-SEJ",
            rawSize = size,
            message = if (isUnlocked) "Bootloader UNLOCKED (V3 encLen=0x07F20000)" else "Bootloader LOCKED (V3)"
        )
    }

    /**
     * Generates a modern SecCfg V4 Unlock payload (512-byte aligned)
     */
    fun createV4Payload(unlock: Boolean, critical: Boolean = false): ByteArray {
        val lockState = if (unlock) LOCK_STATE_UNLOCKED else LOCK_STATE_LOCKED
        val dmVerityState = if (critical && unlock) 1 else 0
        val sbootRuntime = 0
        val seccfgVer = 4
        val seccfgSize = 0x200 // 512 bytes

        val headerBuffer = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN)
        headerBuffer.putInt(MAGIC_SECCFG)
        headerBuffer.putInt(seccfgVer)
        headerBuffer.putInt(seccfgSize)
        headerBuffer.putInt(lockState)
        headerBuffer.putInt(dmVerityState)
        headerBuffer.putInt(sbootRuntime)
        headerBuffer.putInt(ENDFLAG_SECCFG)
        val headerBytes = headerBuffer.array()

        // SHA-256 of header
        val shaHash = MtkSejCrypto.sha256(headerBytes)

        // Encrypt SHA-256 with SEJ SW AES-128-CBC
        val encHash = MtkSejCrypto.sejSecCfgSw(shaHash, encrypt = true)

        // Combine Header (28 bytes) + Encrypted Hash (32 bytes) + Zero padding to 512 bytes
        val totalBuffer = ByteBuffer.allocate(seccfgSize).order(ByteOrder.LITTLE_ENDIAN)
        totalBuffer.put(headerBytes)
        totalBuffer.put(encHash)
        while (totalBuffer.hasRemaining()) {
            totalBuffer.put(0x00.toByte())
        }

        return totalBuffer.array()
    }

    /**
     * Generates a legacy SecCfg V3 Unlock/Lock payload (0x1860 bytes aligned)
     */
    fun createV3Payload(unlock: Boolean): ByteArray {
        val magic = MAGIC_SECCFG
        val seccfgVer = 3
        val seccfgSize = 0x1860
        val encOffset = 0
        val encLen = if (unlock) 0x07F20000 else 0x01000000
        val swSecLockTry: Byte = 0
        val swSecLockDone: Byte = 0
        val pageSize: Short = 0
        val pageCount = 0

        val attrNew = if (unlock) ATTR_UNLOCK else ATTR_DEFAULT

        val outBuf = ByteBuffer.allocate(seccfgSize).order(ByteOrder.LITTLE_ENDIAN)
        outBuf.put(INFO_HEADER_V3)
        outBuf.putInt(magic)
        outBuf.putInt(seccfgVer)
        outBuf.putInt(seccfgSize)
        outBuf.putInt(encOffset)
        outBuf.putInt(encLen)
        outBuf.put(swSecLockTry)
        outBuf.put(swSecLockDone)
        outBuf.putShort(pageSize)
        outBuf.putInt(pageCount)

        // Inner payload encrypted with SEJ SW AES
        val innerSize = seccfgSize - 0x2C - 4
        val innerBuf = ByteBuffer.allocate(innerSize).order(ByteOrder.LITTLE_ENDIAN)
        // 20 * 0x68 imginfo zero bytes
        innerBuf.put(ByteArray(20 * 0x68))
        innerBuf.putInt(0) // siu_status
        innerBuf.putInt(SECCFG_STATUS_COMPLETE)
        innerBuf.putInt(attrNew)
        innerBuf.put(ByteArray(0x1004)) // ext
        while (innerBuf.hasRemaining()) {
            innerBuf.put(0x00.toByte())
        }

        val encryptedInner = MtkSejCrypto.sejSecCfgSw(innerBuf.array(), encrypt = true)
        outBuf.put(encryptedInner)
        outBuf.putInt(ENDFLAG_SECCFG)

        return outBuf.array()
    }
}