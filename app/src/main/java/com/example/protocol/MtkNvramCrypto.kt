package com.example.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Ported MediaTek NVRAM / NVDATA Cryptographic Engine & Calibration Checksums from mtk_crypto.py
 */
object MtkNvramCrypto {

    val NVRAM_CUSTOM_KEY = "12abcdef".toByteArray(Charsets.ISO_8859_1)

    val NVRAM_KEYS = mapOf(
        "mtk" to hexToBytes("0102030405060708090A0B0C0D0E0F1011120B1415161718191A1B1C00000000"),
        "mtkv2" to hexToBytes("425431988FD5AFE5EA6ACD443F382EFEFB6124B5814C376B759F21B484213B8F"),
        "samsung" to hexToBytes("C1A2B1D9B1DDC1F621436F6E666964656E7469616C53414D53554E4700000000")
    )

    private val NVSW_KGEN = hexToBytes(
        "BE410C67394D98017256AA3C8F21BB42CE75601B8F7BC3078216362B151F7F0196E9EB0431739C7438E4920CB18F0961" +
                "956BE82D9D68403207B07A3687351302C718AD6B10EB571DCB8CFD250BAA0D55987C19528445B2728BFC252189FEF974" +
                "46765F5C803309566DB380251A7CE31EB4751A06DBB2B0037B2F391D72B7266D14004905ED85E35901D9E12FE275A920" +
                "7C01A76183EF175BF894282212EB9266B462B44F3079BB2EC37A9C4749CE9C7DCDE1FB60CB2A177ED103B07F95FAA84C" +
                "DB156F1B9C90AD25A0A4B6217392886D20D65F182CA1DC42FD908262674CBF74ACD4E5186A44030881C8A213604A001F" +
                "45F7B30BFCF7DB30D301270C59F7FC10"
    )

    private val SECOND_SEED = hexToBytes("8F9C6151DC86B9163A37506D9DFF7753464BA73E5EDEF3625BA18D481235805B")

    /**
     * Scramble NVRAM Key Source (Pairwise swap and XOR with second seed)
     */
    fun scrambleNvramKeySource(iv: ByteArray, buffer: ByteArray): Pair<ByteArray, ByteArray> {
        val ivCopy = iv.copyOf()
        val bufCopy = buffer.copyOf()

        for (i in 0 until 0x20 step 2) {
            val tmp = ivCopy[i + 1]
            ivCopy[i + 1] = ivCopy[i]
            ivCopy[i] = tmp
        }

        for (i in 0 until 0x20 step 2) {
            val tmp = bufCopy[i + 1]
            bufCopy[i + 1] = bufCopy[i]
            bufCopy[i] = tmp
        }

        for (i in 0 until 0x20) {
            val bv1 = (ivCopy[i].toInt() xor SECOND_SEED[i].toInt()).toByte()
            ivCopy[i] = bv1
            bufCopy[i] = (bv1.toInt() xor bufCopy[i].toInt()).toByte()
        }

        return Pair(ivCopy, bufCopy)
    }

    /**
     * Derives MTK NVRAM Software Key
     */
    fun getNvramSwKey(ivInput: ByteArray, keyLength: Int): ByteArray {
        val baseKey = hexToBytes("3523325342455424438668347856341278563412438668344245542435233253")
        val (scrambledIv, scrambledKey) = scrambleNvramKeySource(ivInput, baseKey)

        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        val keySpec = SecretKeySpec(scrambledKey.copyOf(16), "AES")
        val ivSpec = IvParameterSpec(scrambledIv.copyOf(16))
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)

        val derived = cipher.doFinal(NVSW_KGEN)
        return derived.copyOf(keyLength)
    }

    /**
     * Decrypts standard NVRAM / NVDATA NVITEM using derived AES Key
     */
    fun decryptNvitem(data: ByteArray, customSeed: ByteArray? = null): ByteArray {
        val seed = customSeed ?: NVRAM_KEYS["mtk"]!!
        val nvramKey = getNvramSwKey(seed, 16)

        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        val keySpec = SecretKeySpec(nvramKey, "AES")
        cipher.init(Cipher.DECRYPT_MODE, keySpec)
        return cipher.doFinal(data)
    }

    /**
     * Encrypts standard NVRAM / NVDATA NVITEM using derived AES Key
     */
    fun encryptNvitem(data: ByteArray, customSeed: ByteArray? = null): ByteArray {
        val seed = customSeed ?: NVRAM_KEYS["mtk"]!!
        val nvramKey = getNvramSwKey(seed, 16)

        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        val keySpec = SecretKeySpec(nvramKey, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        return cipher.doFinal(data)
    }

    /**
     * RC4 Cipher implementation for NVRAM stream decryption
     */
    fun nvramRc4Cipher(key: ByteArray, buffer: ByteArray, length: Int, initVector: Int): ByteArray {
        val endpos = length + initVector
        val keybuf = ByteArray(256) { it.toByte() }
        val outBuf = buffer.copyOf()

        var j = 0
        for (i in 0 until 256) {
            val keyByte = key[i % key.size].toInt() and 0xFF
            j = (j + (keybuf[i].toInt() and 0xFF) + keyByte) and 0xFF
            val tmp = keybuf[i]
            keybuf[i] = keybuf[j]
            keybuf[j] = tmp
        }

        var i = 0
        j = 0
        var pos = 0
        while (pos != endpos) {
            i = (i + 1) and 0xFF
            val tmp1 = keybuf[i].toInt() and 0xFF
            j = (j + tmp1) and 0xFF
            keybuf[i] = keybuf[j]
            keybuf[j] = tmp1.toByte()

            if (pos >= initVector && (-initVector + pos) < outBuf.size) {
                val kIndex = ((keybuf[i].toInt() and 0xFF) + (keybuf[j].toInt() and 0xFF)) and 0xFF
                outBuf[-initVector + pos] = (outBuf[-initVector + pos].toInt() xor (keybuf[kIndex].toInt() and 0xFF)).toByte()
            }
            pos++
        }
        return outBuf
    }

    /**
     * Calculates MD5-based 8-byte Folded Checksum for NVRAM LID items
     */
    fun checksum8b(data: ByteArray, itemSize: Int): ByteArray {
        val digest = MessageDigest.getInstance("MD5")
        val limit = minOf(itemSize, data.size)
        digest.update(data, 0, limit)
        val md5 = digest.digest()

        val folded = ByteArray(8)
        for (i in 0 until 8) {
            folded[i] = (md5[i].toInt() xor md5[i + 8].toInt()).toByte()
        }
        return folded
    }

    /**
     * 2-byte Checksum for Wi-Fi and Bluetooth calibration structures
     */
    fun checksum2b(data: ByteArray): Short {
        var value = 0
        for (i in data.indices) {
            val b = data[i].toInt() and 0xFF
            value = if (i % 2 == 0) {
                (value + b) and 0xFF
            } else {
                value xor b
            }
        }
        val chk = (0xAA shl 8) or (value and 0xFF)
        return chk.toShort()
    }

    /**
     * Whole NVRAM Image XOR + Additive Checksum
     */
    fun checksumNvram(data: ByteArray): Int {
        var sum = 0L
        var tempNum = 0L
        val size = data.size

        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until (size / 4) * 4 step 4) {
            val value = buf.getInt(i).toLong() and 0xFFFFFFFFL
            sum = if ((i / 4) % 2 == 0) {
                (sum xor value) and 0xFFFFFFFFL
            } else {
                (sum + value) and 0xFFFFFFFFL
            }
        }

        if (size % 4 != 0) {
            val remStart = (size / 4) * 4
            var remVal = 0L
            for (j in 0 until (size % 4)) {
                remVal = remVal or ((data[remStart + j].toLong() and 0xFFL) shl (j * 8))
            }
            tempNum = remVal
        }

        val total = ((sum + tempNum) and 0xFFFFFFFFL).toInt()
        return total xor size
    }

    /**
     * Helper to parse hex string into byte array
     */
    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.replace(" ", "").trim()
        val len = clean.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(clean[i], 16) shl 4) +
                    Character.digit(clean[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
