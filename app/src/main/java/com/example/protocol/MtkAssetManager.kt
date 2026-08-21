package com.example.protocol

import android.content.Context
import android.util.Log
import com.example.model.MtkDeviceModel
import java.io.File

/**
 * Intelligent Asset Loader and File Resolver for Payloads, DAs, Preloaders, and Targets.
 */
object MtkAssetManager {

    private const val TAG = "MtkAssetManager"
    private const val EXTERNAL_BASE_PATH = "/sdcard/NativeUnlockTool"

    fun getStage1Target(chipCode: String): MtkStage1Target? {
        return MtkStage1TargetCatalog.findTarget(chipCode)
    }

    fun getStage1TargetForModel(model: MtkDeviceModel): MtkStage1Target? {
        return MtkStage1TargetCatalog.findTarget(model.chipCode)
    }

    /**
     * Resolves and loads best matching DA binary bytes for given chip configuration.
     */
    fun resolveDaForChip(context: Context, config: ChipConfig?): ByteArray? {
        if (config == null) return null

        // 1. Priority: AllInOne DA Containers in assets/da/
        val v5Da = loadDaBytes(context, "MTK_DA_V5.bin")
        if (v5Da != null && v5Da.size > 1024) return v5Da

        val v6Da = loadDaBytes(context, "MTK_DA_V6.bin")
        if (v6Da != null && v6Da.size > 1024) return v6Da

        val allInOne = loadDaBytes(context, "MTK_AllInOne_DA.bin")
        if (allInOne != null && allInOne.size > 1024) return allInOne

        // 2. Specific chip DA (e.g. da_mt6765.bin)
        val cleanName = config.name.replace("/", "_").lowercase()
        val specificDa = loadDaBytes(context, "da_$cleanName.bin")
        if (specificDa != null && specificDa.size > 1024) return specificDa

        // 3. Fallback to payload binary
        return loadPayloadBytes(context, config.loader.ifEmpty { config.name })
    }

    /**
     * Loads Payload Binary bytes (e.g. mt6765_payload.bin)
     */
    fun loadPayloadBytes(context: Context, fileNameOrSoc: String): ByteArray? {
        val target = MtkStage1TargetCatalog.findTarget(fileNameOrSoc)
        val fileName = if (fileNameOrSoc.endsWith(".bin")) {
            fileNameOrSoc
        } else {
            target?.payloadFileName ?: "${fileNameOrSoc.lowercase()}_payload.bin"
        }

        // 1. Try Assets (assets/payloads/<file>)
        try {
            context.assets.open("payloads/$fileName").use { stream ->
                val bytes = stream.readBytes()
                Log.d(TAG, "Loaded payload $fileName from assets/payloads (${bytes.size} bytes)")
                return bytes
            }
        } catch (_: Exception) {}

        // 2. Try external SDCard directory fallback
        val externalFile = File("$EXTERNAL_BASE_PATH/payloads/$fileName")
        if (externalFile.exists() && externalFile.canRead()) {
            return try {
                externalFile.readBytes()
            } catch (e: Exception) {
                Log.e(TAG, "Failed reading external payload $fileName: ${e.message}")
                null
            }
        }

        return null
    }

    /**
     * Loads Download Agent Binary bytes (e.g. MTK_DA_V5.bin)
     */
    fun loadDaBytes(context: Context, daFileName: String): ByteArray? {
        // 1. Try assets/da/<file> first
        try {
            context.assets.open("da/$daFileName").use { stream ->
                val bytes = stream.readBytes()
                Log.d(TAG, "Loaded DA $daFileName from assets/da (${bytes.size} bytes)")
                return bytes
            }
        } catch (_: Exception) {}

        // 2. Try assets/loaders/<file>
        try {
            context.assets.open("loaders/$daFileName").use { stream ->
                val bytes = stream.readBytes()
                Log.d(TAG, "Loaded DA $daFileName from assets/loaders (${bytes.size} bytes)")
                return bytes
            }
        } catch (_: Exception) {}

        // 3. Try direct assets/<file>
        try {
            context.assets.open(daFileName).use { stream ->
                val bytes = stream.readBytes()
                return bytes
            }
        } catch (_: Exception) {}

        // 4. Try External Storage
        val extCandidates = listOf(
            File("$EXTERNAL_BASE_PATH/da/$daFileName"),
            File("$EXTERNAL_BASE_PATH/loaders/$daFileName"),
            File("$EXTERNAL_BASE_PATH/$daFileName")
        )
        for (extFile in extCandidates) {
            if (extFile.exists() && extFile.canRead()) {
                return try {
                    extFile.readBytes()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed reading external DA file ${extFile.absolutePath}: ${e.message}")
                    null
                }
            }
        }

        return null
    }

    /**
     * Loads Preloader Binary bytes (e.g. preloader_k6833v1_64.bin)
     */
    fun loadPreloaderBytes(context: Context, preloaderFileName: String): ByteArray? {
        try {
            context.assets.open("preloaders/$preloaderFileName").use { stream ->
                return stream.readBytes()
            }
        } catch (_: Exception) {}

        val externalFile = File("$EXTERNAL_BASE_PATH/preloaders/$preloaderFileName")
        if (externalFile.exists() && externalFile.canRead()) {
            return try {
                externalFile.readBytes()
            } catch (_: Exception) {
                null
            }
        }

        return null
    }
}
