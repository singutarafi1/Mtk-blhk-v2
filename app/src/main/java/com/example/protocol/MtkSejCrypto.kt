package com.example.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * MediaTek Security Engine for JTAG (SEJ) & HACC Cryptography ported from mtkclient
 */
object MtkSejCrypto {

    // SEJ Software AES Key & IV for SecCfg V3 / V4 Unlock Hash
    private val SW_SEC_CFG_KEY = hexToBytes("25A1763A21BC854CD569DC23B4782B63")
    private val SW_SEC_CFG_IV = hexToBytes("57325A5A125497661254976657325A5A")

    // HACC Fixed Hardware Patterns
    val G_HACC_CFG_1 = longArrayOf(
        0x9ED40400L, 0x00E884A1L, 0xE3F083BDL, 0x2F4E6D8AL,
        0xFF838E5CL, 0xE940A0E3L, 0x8D4DECC6L, 0x45FC0989L
    )

    val G_UNQ_KEY_IV = longArrayOf(
        0x6786CFBDL, 0x44B7F1E0L, 0x1544B07BL, 0x53A28EB3L,
        0xD7AB8AA2L, 0xB9E30E7EL, 0x172156E0L, 0x3064C973L
    )

    /**
     * Executes SEJ Software AES-128-CBC encryption / decryption for SecCfg payload hashes
     */
    fun sejSecCfgSw(data: ByteArray, encrypt: Boolean): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        val keySpec = SecretKeySpec(SW_SEC_CFG_KEY, "AES")
        val ivSpec = IvParameterSpec(SW_SEC_CFG_IV)
        val mode = if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE
        cipher.init(mode, keySpec, ivSpec)
        return cipher.doFinal(data)
    }

    /**
     * Computes SHA-256 digest of input byte array
     */
    fun sha256(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data)
    }

    /**
     * XOR hardware vector with 16 bytes of data
     */
    fun xorData(data: ByteArray): ByteArray {
        val result = data.copyOf()
        val buffer = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until minOf(4, G_HACC_CFG_1.size)) {
            if (i * 4 + 4 <= result.size) {
                val orig = buffer.getInt(i * 4).toLong() and 0xFFFFFFFFL
                val xored = (orig xor G_HACC_CFG_1[i]).toInt()
                buffer.putInt(i * 4, xored)
            }
        }
        return result
    }

    /**
     * Validates Luhn checksum for IMEI strings
     */
    fun isLuhnValid(imei: String): Boolean {
        if (imei.length != 15) return false
        var sum = 0
        for (i in imei.indices) {
            var digit = imei[i] - '0'
            if (digit !in 0..9) return false
            if (i % 2 != 0) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            sum += digit
        }
        return sum % 10 == 0
    }

    /**
     * Calculates the Luhn check digit for a 14-digit IMEI
     */
    fun makeLuhnChecksum(imei14: String): Int {
        if (imei14.length < 14) return 0
        var sum = 0
        for (i in 0 until 14) {
            var digit = imei14[i] - '0'
            if (i % 2 != 0) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            sum += digit
        }
        val rem = sum % 10
        return if (rem == 0) 0 else 10 - rem
    }

    /**
     * Encodes 15-digit IMEI into MediaTek 10-byte BCD array
     */
    fun encodeImei(imei: String): ByteArray {
        val cleanImei = imei.padEnd(15, '0').take(15)
        val bcd = ByteArray(10)
        bcd[0] = 0x08.toByte() // standard MTK IMEI length prefix
        for (i in 0 until 7) {
            val high = cleanImei[i * 2 + 1] - '0'
            val low = cleanImei[i * 2] - '0'
            bcd[i + 1] = ((high shl 4) or low).toByte()
        }
        val lastLow = cleanImei[14] - '0'
        bcd[8] = (0xF0 or lastLow).toByte()
        bcd[9] = 0x00.toByte()
        return bcd
    }

    /**
     * Decodes 10-byte MediaTek BCD structure back to 15-digit IMEI string
     */
    fun decodeImei(data: ByteArray): String {
        if (data.size < 9) return ""
        val sb = StringBuilder()
        for (i in 1..7) {
            val byteVal = data[i].toInt() and 0xFF
            val low = byteVal and 0x0F
            val high = (byteVal shr 4) and 0x0F
            sb.append(low)
            if (sb.length < 15) sb.append(high)
        }
        val byte8 = data[8].toInt() and 0xFF
        val lowLast = byte8 and 0x0F
        if (sb.length < 15) sb.append(lowLast)
        return sb.toString()
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) +
                    Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
