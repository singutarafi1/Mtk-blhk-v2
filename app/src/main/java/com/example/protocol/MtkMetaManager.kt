package com.example.protocol

import com.example.model.LogLevel
import java.security.MessageDigest
import java.util.Locale

/**
 * MediaTek MAUI / Advanced META Mode Manager
 * Handles handshake with Preloader VCOM port to boot device into META/Fastboot/Factory test modes,
 * including Tecno / Infinix / Itel Challenge response hashes (MD5/SHA256).
 */
class MtkMetaManager(
    private val usbManager: TargetPhoneUsbManager,
    private val logCallback: (String, LogLevel) -> Unit
) {

    enum class MetaMode(val tag: ByteArray, val label: String) {
        FASTBOOT("FASTBOOT".toByteArray(Charsets.US_ASCII), "Fastboot Mode"),
        META("METAMETA".toByteArray(Charsets.US_ASCII), "MAUI META Mode"),
        EMETA("ADVEMETA".toByteArray(Charsets.US_ASCII), "Advanced META Mode (EMETA)"),
        FACTORY("FACTFACT".toByteArray(Charsets.US_ASCII), "Factory Menu Mode"),
        ATE("FACTORYM".toByteArray(Charsets.US_ASCII), "ATE Signaling Test Mode")
    }

    private val INFINIX_SECRET_TABLE = byteArrayOf(
        0x7C.toByte(), 0x34.toByte(), 0xE1.toByte(), 0x89.toByte(), 0x12.toByte(), 0xE1.toByte(), 0xCD.toByte(), 0x3D.toByte(),
        0x56.toByte(), 0x31.toByte(), 0xAD.toByte(), 0xB2.toByte(), 0x24.toByte(), 0x76.toByte(), 0xD3.toByte(), 0x12.toByte(),
        0x34.toByte(), 0xE2.toByte(), 0xCA.toByte(), 0xFD.toByte(), 0x13.toByte(), 0x12.toByte(), 0x3D.toByte(), 0x2B.toByte(),
        0x3B.toByte(), 0x13.toByte(), 0xE1.toByte(), 0x57.toByte(), 0x22.toByte(), 0xAD.toByte(), 0xC1.toByte(), 0x1D.toByte(),
        0x3D.toByte(), 0x34.toByte(), 0xFD.toByte(), 0x3D.toByte(), 0x1A.toByte(), 0x57.toByte(), 0x46.toByte(), 0x1A.toByte(),
        0x35.toByte(), 0x13.toByte(), 0xC4.toByte(), 0xAF.toByte(), 0x5A.toByte(), 0x86.toByte(), 0x22.toByte(), 0x45.toByte(),
        0x9D.toByte(), 0x3D.toByte(), 0xD1.toByte(), 0x46.toByte(), 0x72.toByte(), 0x41.toByte(), 0x4F.toByte(), 0xAD.toByte(),
        0x46.toByte(), 0xAD.toByte(), 0x53.toByte(), 0x11.toByte(), 0xC2.toByte(), 0x3B.toByte(), 0x3D.toByte(), 0x2D.toByte(),
        0x1A.toByte(), 0x2F.toByte(), 0x3D.toByte(), 0xFA.toByte(), 0xDF.toByte(), 0x35.toByte(), 0x57.toByte(), 0x24.toByte(),
        0xA7.toByte(), 0x4D.toByte(), 0x5E.toByte(), 0x4F.toByte(), 0x34.toByte(), 0xD3.toByte(), 0x4F.toByte(), 0x2D.toByte(),
        0xDF.toByte(), 0x1F.toByte(), 0x13.toByte(), 0xD3.toByte(), 0xB2.toByte(), 0x91.toByte(), 0x41.toByte(), 0x3D.toByte(),
        0x4F.toByte(), 0xD1.toByte(), 0x5D.toByte(), 0x91.toByte(), 0xFD.toByte(), 0x2E.toByte(), 0x4D.toByte(), 0x6F.toByte(),
        0x3D.toByte(), 0x41.toByte(), 0x34.toByte(), 0x7F.toByte(), 0x45.toByte(), 0xF3.toByte(), 0x8A.toByte(), 0x26.toByte(),
        0x1A.toByte(), 0x33.toByte(), 0x4F.toByte(), 0x3E.toByte(), 0x5E.toByte(), 0x64.toByte(), 0x36.toByte(), 0x8A.toByte(),
        0xD1.toByte(), 0xF6.toByte(), 0x9F.toByte(), 0x35.toByte(), 0x6A.toByte(), 0x96.toByte(), 0x2A.toByte(), 0x5D.toByte()
    )

    private val INFINIX_MD5_SECRET = byteArrayOf(
        0xC4.toByte(), 0x92.toByte(), 0xAD.toByte(), 0x3A.toByte(), 0x61.toByte(), 0xF9.toByte(),
        0xCE.toByte(), 0xC3.toByte(), 0x13.toByte(), 0x7F.toByte(), 0xA9.toByte(), 0xCB.toByte()
    )

    private val TECNO_ITEL_MD5_SECRET = byteArrayOf(
        0x4C.toByte(), 0xEE.toByte(), 0xCB.toByte(), 0x1C.toByte(), 0xB4.toByte(), 0xB1.toByte(),
        0x1D.toByte(), 0x2B.toByte(), 0x43.toByte(), 0x18.toByte(), 0x84.toByte(), 0x3F.toByte()
    )

    /**
     * Executes MediaTek Preloader META Mode Handshake & SLA Challenge negotiation
     */
    fun enterMetaMode(mode: MetaMode): Boolean {
        logCallback("Initiating META Handshake: Requesting ${mode.label}...", LogLevel.INFO)
        if (!usbManager.isConnected()) {
            logCallback("Device not connected via USB OTG", LogLevel.ERROR)
            return false
        }

        val rx = ByteArray(1024)
        val r = usbManager.readRaw(rx, 500)
        val respStr = if (r > 0) String(rx, 0, r, Charsets.US_ASCII) else ""

        if (respStr.contains("READY")) {
            logCallback("Preloader responded with READY. Sending META Mode Token: ${String(mode.tag)}", LogLevel.INFO)
            usbManager.writeRaw(mode.tag, 500)

            val nextBuf = ByteArray(1024)
            val nLen = usbManager.readRaw(nextBuf, 500)
            val nextStr = if (nLen > 0) String(nextBuf, 0, nLen, Charsets.US_ASCII) else ""

            if (nextStr.contains("METASLA")) {
                logCallback("Device requires META SLA Authentication. Sending SLASTART...", LogLevel.WARNING)
                usbManager.writeRaw("SLASTART".toByteArray(Charsets.US_ASCII), 500)

                val challengeBuf = ByteArray(1024)
                val cLen = usbManager.readRaw(challengeBuf, 500)
                if (cLen >= 10) {
                    val challengeStr = String(challengeBuf, 0, cLen, Charsets.US_ASCII)
                    val isExt = challengeStr.contains("EXT")
                    val isSha = challengeStr.contains("SHA")
                    val timeval = challengeBuf.copyOfRange(6, 10)

                    val responseHash = if (isSha) {
                        val keyId = if (cLen >= 17) (challengeBuf[13].toInt() and 0xFF) else 0
                        val keyOffset = (0x0C * keyId).coerceIn(0, INFINIX_SECRET_TABLE.size - 12)
                        val keySlice = INFINIX_SECRET_TABLE.copyOfRange(keyOffset, keyOffset + 12)
                        val md = MessageDigest.getInstance("SHA-256")
                        md.update(timeval)
                        md.update(keySlice)
                        md.digest()
                    } else {
                        val secret = if (isExt) TECNO_ITEL_MD5_SECRET else INFINIX_MD5_SECRET
                        val md = MessageDigest.getInstance("MD5")
                        md.update(timeval)
                        md.update(secret)
                        md.digest()
                    }

                    logCallback("SLA Handshake response generated (${responseHash.size} bytes). Sending to device...", LogLevel.INFO)
                    usbManager.writeRaw(responseHash, 500)
                }
            }

            usbManager.writeRaw("DISCONNECT".toByteArray(Charsets.US_ASCII), 500)
            logCallback("Target phone successfully commanded into ${mode.label}.", LogLevel.SUCCESS)
            return true
        }

        logCallback("Handshake completed.", LogLevel.SUCCESS)
        return true
    }
}
