package com.example.protocol

import android.content.Context
import android.util.Log
import com.example.model.MtkDeviceModel
import java.io.File

/**
 * Intelligent Asset Loader and File Resolver for Payloads, DAs, Preloaders, and Targets.
 * Ported to handle authentic mtkclient payload and DA binaries from `app/src/main/assets/` or SDCard.
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
     * Looks in:
     * 1. assets/da/<loader> or assets/loaders/<loader> or /sdcard/NativeUnlockTool/loaders/<loader>
     * 2. assets/da/<chip_name>_da.bin / assets/da/MTK_AllInOne_DA.bin
     * 3. assets/payloads/<loader> (e.g. mt6765_payload.bin / mt6833_payload.bin)
     * 4. /sdcard/NativeUnlockTool/da/ or /sdcard/NativeUnlockTool/loaders/
     */
    fun resolveDaForChip(context: Context, config: ChipConfig?): ByteArray? {
        if (config == null) return null

        // 1. Try chip loader name directly if specified (e.g. mt6765_payload.bin)
        if (config.loader.isNotEmpty()) {
            val daBytes = loadDaBytes(context, config.loader)
            if (daBytes != null && daBytes.isNotEmpty()) {
                return daBytes
            }
            val payloadBytes = loadPayloadBytes(context, config.loader)
            if (payloadBytes != null && payloadBytes.isNotEmpty()) {
                return payloadBytes
            }
        }

        // 2. Try chip name based DA (e.g. da/da_mt6765.bin)
        val cleanName = config.name.replace("/", "_").lowercase()
        val specificDa = loadDaBytes(context, "da_$cleanName.bin")
        if (specificDa != null && specificDa.isNotEmpty()) {
            return specificDa
        }

        // 3. Try MTK_AllInOne_DA.bin or DA_PL.bin
        val allInOne = loadDaBytes(context, "MTK_AllInOne_DA.bin") ?: loadDaBytes(context, "DA_PL.bin")
        if (allInOne != null && allInOne.isNotEmpty()) {
            return allInOne
        }

        // 4. Try payload file matching chip name
        return loadPayloadBytes(context, config.name)
    }

    /**
     * Loads Payload Binary bytes (e.g. mt6833_payload.bin)
     * Priority: 1. Assets folder (`assets/payloads/<file>`) -> 2. External Storage (`/sdcard/NativeUnlockTool/payloads/<file>`)
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
                Log.d(TAG, "Loaded payload $fileName from assets/payloads (${stream.available()} bytes)")
                return stream.readBytes()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Payload $fileName not found in assets/payloads, checking external storage fallback...")
        }

        // 2. Try external SDCard directory fallback (/sdcard/NativeUnlockTool/payloads/<file>)
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
     * Loads Download Agent Binary bytes (e.g. MTK_AllInOne_DA.bin or DA_PL.bin)
     * Priority:
     * 1. assets/da/<file>
     * 2. assets/loaders/<file>
     * 3. External storage /sdcard/NativeUnlockTool/loaders/<file> or /sdcard/NativeUnlockTool/da/<file>
     */
    fun loadDaBytes(context: Context, daFileName: String): ByteArray? {
        // 1. Try assets/da/<file> first
        try {
            context.assets.open("da/$daFileName").use { stream ->
                Log.d(TAG, "Loaded DA $daFileName from assets/da (${stream.available()} bytes)")
                return stream.readBytes()
            }
        } catch (_: Exception) {}

        // 2. Try assets/loaders/<file>
        try {
            context.assets.open("loaders/$daFileName").use { stream ->
                Log.d(TAG, "Loaded DA $daFileName from assets/loaders (${stream.available()} bytes)")
                return stream.readBytes()
            }
        } catch (_: Exception) {}

        // 3. Try assets/payloads/<file> or raw assets/<file>
        for (subPath in listOf("payloads/$daFileName", daFileName)) {
            try {
                context.assets.open(subPath).use { stream ->
                    return stream.readBytes()
                }
            } catch (_: Exception) {}
        }

        // 4. Try External Storage (/sdcard/NativeUnlockTool/loaders/<file> first, then da/, payloads/)
        val extCandidates = listOf(
            File("$EXTERNAL_BASE_PATH/loaders/$daFileName"),
            File("$EXTERNAL_BASE_PATH/da/$daFileName"),
            File("$EXTERNAL_BASE_PATH/payloads/$daFileName"),
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
     * Priority:
     * 1. assets/preloaders/<file>
     * 2. External storage /sdcard/NativeUnlockTool/preloaders/<file>
     */
    fun loadPreloaderBytes(context: Context, preloaderFileName: String): ByteArray? {
        // 1. Try Assets (assets/preloaders/<file>)
        try {
            context.assets.open("preloaders/$preloaderFileName").use { stream ->
                Log.d(TAG, "Loaded preloader $preloaderFileName from assets/preloaders (${stream.available()} bytes)")
                return stream.readBytes()
            }
        } catch (_: Exception) {}

        // 2. Try External Storage (/sdcard/NativeUnlockTool/preloaders/<file>)
        val externalFile = File("$EXTERNAL_BASE_PATH/preloaders/$preloaderFileName")
        if (externalFile.exists() && externalFile.canRead()) {
            return try {
                externalFile.readBytes()
            } catch (e: Exception) {
                Log.e(TAG, "Failed reading external preloader $preloaderFileName: ${e.message}")
                null
            }
        }

        return null
    }

    /**
     * List all available payloads in assets/payloads and SDCard /sdcard/NativeUnlockTool/payloads
     */
    fun listAvailablePayloads(context: Context): List<String> {
        val list = mutableSetOf<String>()
        try {
            context.assets.list("payloads")?.filter { it.endsWith(".bin") || it.endsWith(".payload") }?.forEach { list.add(it) }
        } catch (_: Exception) {}
        val extDir = File("$EXTERNAL_BASE_PATH/payloads")
        if (extDir.exists() && extDir.isDirectory) {
            extDir.listFiles()?.filter { it.extension.lowercase() in listOf("bin", "payload") }?.forEach { list.add(it.name) }
        }
        return list.sorted()
    }

    /**
     * List all available Preloaders in assets/preloaders and SDCard /sdcard/NativeUnlockTool/preloaders
     */
    fun listAvailablePreloaders(context: Context): List<String> {
        val list = mutableSetOf<String>()
        try {
            context.assets.list("preloaders")?.filter { it.endsWith(".bin") }?.forEach { list.add(it) }
        } catch (_: Exception) {}
        val extDir = File("$EXTERNAL_BASE_PATH/preloaders")
        if (extDir.exists() && extDir.isDirectory) {
            extDir.listFiles()?.filter { it.extension.lowercase() == "bin" }?.forEach { list.add(it.name) }
        }
        return list.sorted()
    }
}
