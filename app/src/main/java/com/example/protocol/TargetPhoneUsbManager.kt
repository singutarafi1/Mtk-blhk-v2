package com.example.protocol

import android.content.Context
import android.util.Log
import com.example.model.MtkDeviceModel
import java.io.File
import java.io.InputStream

/**
 * MediaTek Native Binary Asset Resolver
 * Faithfully mirrors Python mtkclient (daconfig.py, mtk.py)
 * STRICT: Rejects exploit payloads when DA container is required.
 */
object MtkAssetManager {

    private const val TAG = "MtkAssetManager"
    private const val EXTERNAL_BASE_PATH = "/sdcard/NativeUnlockTool"
    private const val EXTERNAL_DOWNLOAD_PATH = "/sdcard/Download/MTK_Loaders"
    private const val MIN_DA_CONTAINER_SIZE = 4096 // 4KB minimum threshold

    fun getStage1Target(chipCode: String): MtkStage1Target? {
        return MtkStage1TargetCatalog.findTarget(chipCode)
    }

    fun getStage1TargetForModel(model: MtkDeviceModel): MtkStage1Target? {
        return MtkStage1TargetCatalog.findTarget(model.chipCode)
    }

    /**
     * Resolves the real MediaTek Download Agent (DA) Container for a given SoC configuration.
     * NEVER falls back to small exploit payloads (< 4KB).
     */
    fun resolveDaForChip(context: Context, config: ChipConfig?): ByteArray? {
        if (config == null) return null

        val hwCodeHex = "0x%04X".format(config.hwCode)
        val socName = config.name.replace("/", "_").lowercase()

        // 1. Priority: Universal Containers in assets/da/
        val universalCandidates = listOf(
            "MTK_DA_V5.bin",
            "MTK_DA_V6.bin",
            "MTK_AllInOne_DA.bin"
        )

        for (candidate in universalCandidates) {
            val daBytes = loadDaBytes(context, candidate)
            if (daBytes != null && daBytes.size >= MIN_DA_CONTAINER_SIZE) {
                val daList = MtkDaParser.parseAllDa(daBytes)
                if (daList.isNotEmpty()) {
                    val match = MtkDaParser.findDa(daList, config.hwCode)
                    if (match != null) {
                        Log.d(TAG, "[DA RESOLVE] Found matching DA for $hwCodeHex in $candidate")
                        return daBytes
                    }
                }
            }
        }

        // 2. Priority: Dedicated SoC DA Binaries
        val dedicatedCandidates = listOf(
            "da_$socName.bin",
            "da_${socName}_xflash.bin",
            "da_${hwCodeHex.lowercase()}.bin"
        )

        for (candidate in dedicatedCandidates) {
            val daBytes = loadDaBytes(context, candidate)
            if (daBytes != null && daBytes.size >= MIN_DA_CONTAINER_SIZE) {
                Log.d(TAG, "[DA RESOLVE] Found dedicated DA: $candidate")
                return daBytes
            }
        }

        // 3. Fallback: First valid universal container
        for (candidate in universalCandidates) {
            val daBytes = loadDaBytes(context, candidate)
            if (daBytes != null && daBytes.size >= MIN_DA_CONTAINER_SIZE) {
                Log.w(TAG, "[DA RESOLVE] Using generic fallback container: $candidate")
                return daBytes
            }
        }

        Log.e(TAG, "[DA RESOLVE ERROR] No valid DA container found for ${config.name}")
        return null
    }

    /**
     * Loads BROM Exploit Payload (mt6765_payload.bin ~ 624 bytes) for SRAM injection only.
     */
    fun loadPayloadBytes(context: Context, fileNameOrSoc: String): ByteArray? {
        val target = MtkStage1TargetCatalog.findTarget(fileNameOrSoc)
        val fileName = if (fileNameOrSoc.endsWith(".bin")) {
            fileNameOrSoc
        } else {
            target?.payloadFileName ?: "${fileNameOrSoc.lowercase()}_payload.bin"
        }

        // 1. External Storage
        val extCandidates = listOf(
            File("$EXTERNAL_BASE_PATH/payloads/$fileName"),
            File("$EXTERNAL_DOWNLOAD_PATH/payloads/$fileName"),
            File("$EXTERNAL_BASE_PATH/$fileName")
        )

        for (extFile in extCandidates) {
            if (extFile.exists() && extFile.canRead()) {
                return try {
                    extFile.readBytes()
                } catch (e: Exception) {
                    null
                }
            }
        }

        // 2. Assets (assets/payloads/<file>)
        val assetPaths = listOf(
            "payloads/$fileName",
            fileName
        )

        for (path in assetPaths) {
            try {
                context.assets.open(path).use { stream: InputStream ->
                    val bytes = stream.readBytes()
                    if (bytes.isNotEmpty()) return bytes
                }
            } catch (_: Exception) {}
        }

        return null
    }

    /**
     * Loads Download Agent Binary bytes from External Storage or Assets.
     */
    fun loadDaBytes(context: Context, daFileName: String): ByteArray? {
        val cleanName = daFileName.trim()

        // 1. External Storage
        val extCandidates = listOf(
            File("$EXTERNAL_BASE_PATH/da/$cleanName"),
            File("$EXTERNAL_BASE_PATH/loaders/$cleanName"),
            File("$EXTERNAL_DOWNLOAD_PATH/$cleanName"),
            File("$EXTERNAL_BASE_PATH/$cleanName")
        )

        for (extFile in extCandidates) {
            if (extFile.exists() && extFile.canRead()) {
                try {
                    val bytes = extFile.readBytes()
                    if (bytes.size >= MIN_DA_CONTAINER_SIZE) return bytes
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading external DA: ${e.message}")
                }
            }
        }

        // 2. APK Assets (assets/da/, assets/loaders/, assets/)
        val assetPaths = listOf(
            "da/$cleanName",
            "loaders/$cleanName",
            cleanName
        )

        for (path in assetPaths) {
            try {
                context.assets.open(path).use { stream: InputStream ->
                    val bytes = stream.readBytes()
                    if (bytes.size >= MIN_DA_CONTAINER_SIZE) return bytes
                }
            } catch (_: Exception) {}
        }

        return null
    }

    /**
     * Loads Preloader binary bytes.
     */
    fun loadPreloaderBytes(context: Context, preloaderFileName: String): ByteArray? {
        val cleanName = preloaderFileName.trim()

        val extFile = File("$EXTERNAL_BASE_PATH/preloaders/$cleanName")
        if (extFile.exists() && extFile.canRead()) {
            return try { extFile.readBytes() } catch (_: Exception) { null }
        }

        val assetPaths = listOf("preloaders/$cleanName", cleanName)
        for (path in assetPaths) {
            try {
                context.assets.open(path).use { stream: InputStream ->
                    val bytes = stream.readBytes()
                    if (bytes.isNotEmpty()) return bytes
                }
            } catch (_: Exception) {}
        }

        return null
    }
}
