package com.example.protocol

import android.content.Context
import android.util.Log
import com.example.model.MtkDeviceModel
import java.io.File
import java.io.InputStream

/**
 * MediaTek Native Binary Asset & Payload Manager
 * Faithfully handles BROM Payloads, Download Agents (DA_PL / DA_EXT / XFLASH), and Preloaders.
 * Direct Kotlin port of mtkclient file resolution logic (daconfig.py, mtk.py).
 * 100% Real Hardware Protocol Compliant - No Mock Data.
 */
object MtkAssetManager {

    private const val TAG = "MtkAssetManager"
    
    // External storage paths for custom/updated DA and Payload drops
    private const val EXTERNAL_BASE_PATH = "/sdcard/NativeUnlockTool"
    private const val EXTERNAL_DOWNLOAD_PATH = "/sdcard/Download/MTK_Loaders"

    // Minimum valid DA binary container size (4KB threshold to reject exploit shellcodes)
    private const val MIN_DA_CONTAINER_SIZE = 4096

    fun getStage1Target(chipCode: String): MtkStage1Target? {
        return MtkStage1TargetCatalog.findTarget(chipCode)
    }

    fun getStage1TargetForModel(model: MtkDeviceModel): MtkStage1Target? {
        return MtkStage1TargetCatalog.findTarget(model.chipCode)
    }

    /**
     * Resolves the real MediaTek Download Agent (DA) Container for a given SoC configuration.
     * Strictly verifies container validity (> 4KB) and NEVER falls back to small exploit payloads.
     */
    fun resolveDaForChip(context: Context, config: ChipConfig?): ByteArray? {
        if (config == null) return null

        val hwCodeHex = "0x%04X".format(config.hwCode)
        val socName = config.name.replace("/", "_").lowercase()

        // 1. First Priority: All-In-One Universal DA containers in assets or external storage
        val universalDaCandidates = listOf(
            "MTK_AllInOne_DA.bin",
            "MTK_DA_V5.bin",
            "MTK_DA_V6.bin",
            "DA_PL.bin"
        )

        for (candidate in universalDaCandidates) {
            val daBytes = loadDaBytes(context, candidate)
            if (daBytes != null && daBytes.size >= MIN_DA_CONTAINER_SIZE) {
                // Verify if container contains the matching HW code
                val daList = MtkDaParser.parseAllDa(daBytes)
                if (daList.isNotEmpty()) {
                    val match = MtkDaParser.findDa(daList, config.hwCode)
                    if (match != null) {
                        Log.d(TAG, "[DA RESOLVE] Found matching DA for $hwCodeHex (${config.name}) in $candidate")
                        return daBytes
                    }
                }
            }
        }

        // 2. Second Priority: Chip-Specific DA Binary (e.g. da_mt6765.bin, da_0x0766.bin)
        val chipSpecificCandidates = listOf(
            "da_$socName.bin",
            "da_${socName}_xflash.bin",
            "da_${hwCodeHex.lowercase()}.bin",
            "DA_$socName.bin"
        )

        for (candidate in chipSpecificCandidates) {
            val daBytes = loadDaBytes(context, candidate)
            if (daBytes != null && daBytes.size >= MIN_DA_CONTAINER_SIZE) {
                Log.d(TAG, "[DA RESOLVE] Found dedicated DA binary: $candidate (${daBytes.size} bytes)")
                return daBytes
            }
        }

        // 3. Third Priority: First available valid All-In-One container
        for (candidate in universalDaCandidates) {
            val daBytes = loadDaBytes(context, candidate)
            if (daBytes != null && daBytes.size >= MIN_DA_CONTAINER_SIZE) {
                Log.w(TAG, "[DA RESOLVE] Dedicated match not found. Using universal fallback: $candidate")
                return daBytes
            }
        }

        Log.e(TAG, "[DA RESOLVE ERROR] No valid DA container found for ${config.name} ($hwCodeHex). Place MTK_AllInOne_DA.bin in assets/da/ or $EXTERNAL_BASE_PATH/da/")
        return null
    }

    /**
     * Loads BROM Exploit Shellcode Payload (e.g. mt6765_payload.bin ~ 624 bytes).
     * Used exclusively for Kamakiri/SRAM register patching, NOT for flashing.
     */
    fun loadPayloadBytes(context: Context, fileNameOrSoc: String): ByteArray? {
        val target = MtkStage1TargetCatalog.findTarget(fileNameOrSoc)
        val fileName = if (fileNameOrSoc.endsWith(".bin")) {
            fileNameOrSoc
        } else {
            target?.payloadFileName ?: "${fileNameOrSoc.lowercase()}_payload.bin"
        }

        // 1. Check External Storage Custom Drops first
        val externalCandidates = listOf(
            File("$EXTERNAL_BASE_PATH/payloads/$fileName"),
            File("$EXTERNAL_DOWNLOAD_PATH/payloads/$fileName"),
            File("$EXTERNAL_BASE_PATH/$fileName")
        )

        for (extFile in externalCandidates) {
            if (extFile.exists() && extFile.canRead()) {
                return try {
                    val bytes = extFile.readBytes()
                    Log.d(TAG, "[PAYLOAD LOAD] Loaded external payload: ${extFile.absolutePath} (${bytes.size} bytes)")
                    bytes
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading external payload $fileName: ${e.message}")
                    null
                }
            }
        }

        // 2. Check Android App Assets (assets/payloads/<file> or assets/<file>)
        val assetPaths = listOf(
            "payloads/$fileName",
            fileName
        )

        for (path in assetPaths) {
            try {
                context.assets.open(path).use { stream: InputStream ->
                    val bytes = stream.readBytes()
                    if (bytes.isNotEmpty()) {
                        Log.d(TAG, "[PAYLOAD LOAD] Loaded asset payload: $path (${bytes.size} bytes)")
                        return bytes
                    }
                }
            } catch (_: Exception) {
                // Continue searching
            }
        }

        Log.w(TAG, "[PAYLOAD LOAD] Payload binary not found: $fileName")
        return null
    }

    /**
     * Loads MediaTek Download Agent (DA) Container Binary bytes.
     * Searches external storage followed by internal APK assets.
     */
    fun loadDaBytes(context: Context, daFileName: String): ByteArray? {
        val cleanName = daFileName.trim()

        // 1. External Storage Check
        val externalCandidates = listOf(
            File("$EXTERNAL_BASE_PATH/da/$cleanName"),
            File("$EXTERNAL_BASE_PATH/loaders/$cleanName"),
            File("$EXTERNAL_DOWNLOAD_PATH/$cleanName"),
            File("$EXTERNAL_BASE_PATH/$cleanName")
        )

        for (extFile in externalCandidates) {
            if (extFile.exists() && extFile.canRead()) {
                try {
                    val bytes = extFile.readBytes()
                    if (bytes.size >= MIN_DA_CONTAINER_SIZE) {
                        Log.d(TAG, "[DA LOAD] Loaded external DA: ${extFile.absolutePath} (${bytes.size} bytes)")
                        return bytes
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading external DA file ${extFile.absolutePath}: ${e.message}")
                }
            }
        }

        // 2. APK Assets Check (assets/da/, assets/loaders/, assets/)
        val assetPaths = listOf(
            "da/$cleanName",
            "loaders/$cleanName",
            cleanName
        )

        for (path in assetPaths) {
            try {
                context.assets.open(path).use { stream: InputStream ->
                    val bytes = stream.readBytes()
                    if (bytes.size >= MIN_DA_CONTAINER_SIZE) {
                        Log.d(TAG, "[DA LOAD] Loaded asset DA: $path (${bytes.size} bytes)")
                        return bytes
                    }
                }
            } catch (_: Exception) {
                // Continue searching
            }
        }

        return null
    }

    /**
     * Loads Preloader Binary bytes for initial EMI initialization or BROM crash.
     */
    fun loadPreloaderBytes(context: Context, preloaderFileName: String): ByteArray? {
        val cleanName = preloaderFileName.trim()

        // 1. External Storage
        val externalCandidates = listOf(
            File("$EXTERNAL_BASE_PATH/preloaders/$cleanName"),
            File("$EXTERNAL_BASE_PATH/$cleanName")
        )

        for (extFile in externalCandidates) {
            if (extFile.exists() && extFile.canRead()) {
                return try {
                    extFile.readBytes()
                } catch (_: Exception) {
                    null
                }
            }
        }

        // 2. Assets
        val assetPaths = listOf(
            "preloaders/$cleanName",
            cleanName
        )

        for (path in assetPaths) {
            try {
                context.assets.open(path).use { stream: InputStream ->
                    val bytes = stream.readBytes()
                    if (bytes.isNotEmpty()) {
                        return bytes
                    }
                }
            } catch (_: Exception) {}
        }

        return null
    }
}
