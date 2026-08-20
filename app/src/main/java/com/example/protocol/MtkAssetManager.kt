package com.example.protocol

import android.content.Context
import android.util.Log
import com.example.model.MtkDeviceModel
import java.io.File
import java.io.InputStream

/**
 * Intelligent Asset Loader and File Resolver for Payloads, DAs, Preloaders, and Targets.
 * Automatically resolves files from `app/src/main/assets/` or local SDCard fallback directory.
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
     * Loads Payload Binary bytes (e.g. mt6768_payload.bin)
     * Priority: 1. Assets folder (`payloads/mt6768_payload.bin`) -> 2. SDCard fallback
     */
    fun loadPayloadBytes(context: Context, socName: String): ByteArray? {
        val target = MtkStage1TargetCatalog.findTarget(socName)
        val fileName = target?.payloadFileName ?: "${socName.lowercase()}_payload.bin"
        
        // 1. Try Assets
        try {
            context.assets.open("payloads/$fileName").use { stream ->
                Log.d(TAG, "Loaded payload $fileName from assets (${stream.available()} bytes)")
                return stream.readBytes()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Payload $fileName not found in assets, checking external fallback...")
        }

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
     * Loads Download Agent Binary bytes (e.g. MTK_DA_V6.bin or MTK_AllInOne_DA.bin)
     */
    fun loadDaBytes(context: Context, daFileName: String): ByteArray? {
        // 1. Try Assets
        try {
            context.assets.open("da/$daFileName").use { stream ->
                return stream.readBytes()
            }
        } catch (e: Exception) {
            try {
                context.assets.open("loaders/$daFileName").use { stream ->
                    return stream.readBytes()
                }
            } catch (ignored: Exception) {}
        }

        // 2. Try External Storage
        val externalFile = File("$EXTERNAL_BASE_PATH/loaders/$daFileName")
        if (externalFile.exists() && externalFile.canRead()) {
            return try {
                externalFile.readBytes()
            } catch (e: Exception) {
                null
            }
        }

        return null
    }

    /**
     * Loads Preloader Binary bytes (e.g. preloader_wt98999_wifirow.bin)
     */
    fun loadPreloaderBytes(context: Context, preloaderFileName: String): ByteArray? {
        // 1. Try Assets
        try {
            context.assets.open("preloaders/$preloaderFileName").use { stream ->
                return stream.readBytes()
            }
        } catch (e: Exception) {}

        // 2. Try External Storage
        val externalFile = File("$EXTERNAL_BASE_PATH/preloaders/$preloaderFileName")
        if (externalFile.exists() && externalFile.canRead()) {
            return try {
                externalFile.readBytes()
            } catch (e: Exception) {
                null
            }
        }

        return null
    }

    /**
     * List all available payloads in assets/payloads or SDCard
     */
    fun listAvailablePayloads(context: Context): List<String> {
        val list = mutableSetOf<String>()
        try {
            context.assets.list("payloads")?.forEach { list.add(it) }
        } catch (e: Exception) {}
        val extDir = File("$EXTERNAL_BASE_PATH/payloads")
        if (extDir.exists() && extDir.isDirectory) {
            extDir.listFiles()?.filter { it.extension.lowercase() == "bin" }?.forEach { list.add(it.name) }
        }
        return list.sorted()
    }

    /**
     * List all available Preloaders
     */
    fun listAvailablePreloaders(context: Context): List<String> {
        val list = mutableSetOf<String>()
        try {
            context.assets.list("preloaders")?.forEach { list.add(it) }
        } catch (e: Exception) {}
        val extDir = File("$EXTERNAL_BASE_PATH/preloaders")
        if (extDir.exists() && extDir.isDirectory) {
            extDir.listFiles()?.filter { it.extension.lowercase() == "bin" }?.forEach { list.add(it.name) }
        }
        return list.sorted()
    }
}
