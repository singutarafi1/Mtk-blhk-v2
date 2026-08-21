package com.example.model

enum class TransportType(val displayName: String) {
    USB_OTG_DIRECT("Direct USB OTG (Host)")
}

data class BridgeStatus(
    val isConnected: Boolean = false,
    val transportType: TransportType = TransportType.USB_OTG_DIRECT,
    val deviceName: String = "MTK Direct USB Host",
    val fileDescriptor: Int = -1,
    val isBromMode: Boolean = true,
    val targetVidPid: String = "0x0E8D:0x0003",
    val endpointIn: Int = 0x81,
    val endpointOut: Int = 0x01
)

data class TriggerConfig(
    val durationMs: Int = 100,
    val pulseCount: Int = 1,
    val activeLow: Boolean = true
)

data class MtkChipInfo(
    val chipIdHex: String = "",
    val hwCodeHex: String = "",
    val hwSubcodeHex: String = "",
    val hwVersionHex: String = "",
    val swVersionHex: String = "",
    val secureBootEnabled: Boolean = false,
    val daLoaded: Boolean = false,
    val bromState: String = ""
)

data class PartitionEntry(
    val partitionIndex: Int,
    val partitionName: String,
    val fileName: String,
    val linearStartAddrHex: String,
    val physicalStartAddrHex: String,
    val partitionSizeHex: String,
    val sizeBytes: Long,
    val region: String = "EMMC_USER",
    val isDownload: Boolean = true,
    val isProtectedNv: Boolean = false,
    val isSelectedForFlashing: Boolean = true,
    val boundFilePath: String = ""
) {
    val startLinearAddress: Long
        get() {
            val clean = linearStartAddrHex.removePrefix("0x").removePrefix("0X")
            return clean.toLongOrNull(16) ?: 0L
        }

    val formattedSize: String
        get() {
            val kb = sizeBytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format("%.2f GB", gb)
                mb >= 1.0 -> String.format("%.1f MB", mb)
                kb >= 1.0 -> String.format("%.0f KB", kb)
                else -> "$sizeBytes B"
            }
        }
}

enum class BackupMode(val title: String, val shortLabel: String, val description: String) {
    FULL_FIRMWARE("Full ROM Dump", "Full ROM", "Dump all active GPT partitions into a complete ROM archive"),
    STABLE_FIRMWARE("Stable FW Backup", "Stable FW", "Dump essential boot partitions (boot, recovery, vbmeta, dtbo, spmfw, lk, super)"),
    NV_DATA("NV Data / IMEI Backup", "NV Data", "Dump calibration & IMEI partitions (nvram, nvdata, persist, protect1/2, proinfo)"),
    CUSTOM_PARTITIONS("Custom Partition Dump", "Custom GPT", "Dump only the user-checked partitions in the GPT partition table")
}

data class FlashOptions(
    val readNvData: Boolean = true,
    val autoReboot: Boolean = true,
    val flashAfterBlUnlock: Boolean = false,
    val daDlChecksum: Boolean = true,
    val autoSignFlash: Boolean = true,
    val formatAllDownload: Boolean = false
)

enum class ServiceFunction(val title: String, val subtitle: String, val isWrite: Boolean) {
    READ_INFO("Read Chip Info", "Detect MediaTek chipset, HW code, registers & security state via USB", false),
    WRITE_PARTITION("Write / Flash Selected Partition", "Flash partition image (Mandatory Auto-Backup & SHA-256 verify)", true),
    BATCH_FLASH("Batch Flash (All Selected)", "Flash all checked partitions in sequence via USB OTG", true),
    READ_PARTITION("Read / Dump Selected Partition", "Dump single partition from device to local storage", false),
    DUMP_ALL_PARTITIONS("Full ROM Dump (All Partitions)", "Dump entire flash memory partitions to storage archive", false),
    DUMP_STABLE_PARTITIONS("Stable Boot Partitions Dump", "Dump essential partitions required to power on", false),
    READ_PRELOADER("Read Preloader / Bootloader", "Dump preloader.bin and lk bootloader images", false),
    READ_GPT_SCATTER("Read GPT & Generate Scatter", "Query GPT partition table and generate scatter.txt", false),
    READ_RPMB("Read RPMB Partition", "Dump RPMB keys and security region", false),
    BACKUP_NVRAM("Backup NVRAM / NVDATA", "Safely dump nvram, nvdata, protect1, protect2, secro, and nvcfg", false),
    RESTORE_NVRAM("Restore NVRAM / NVDATA", "Write back saved NV calibration archive with verification", true),
    BYPASS_AUTH("Bypass SLA / DAA / SBC Auth", "Execute USB Control Transfer exploit to disable SLA/DAA security", true),
    UNLOCK_BOOTLOADER("Unlock Bootloader (seccfg)", "Write unlock payload to seccfg partition", true),
    LOCK_BOOTLOADER("Lock Bootloader (seccfg)", "Relock bootloader security state", true),
    ERASE_FRP("Erase FRP (Google Account)", "Zero-out FRP partition to remove FRP lock", true),
    FACTORY_RESET("Factory Reset (Wipe Userdata)", "Wipe userdata, metadata, and cache partitions", true),
    DISABLE_MI_ACCOUNT("Disable Mi Account (Xiaomi)", "Patch persist and frp to disable Mi Cloud lock", true),
    MEMORY_TEST("Memory / Storage Health Test", "Perform RAM pattern test, eMMC/UFS health & CID diagnostic", false),
    FORMAT_PARTITION("Format Partition", "Erase selected partition range (wipe to zeros)", true),
    CRASH_TO_BROM("Crash Preloader to BROM", "Send USB Control Transfer command to force preloader into BROM", false),
    REBOOT_SYSTEM("Reboot to System", "Send DA reboot command to boot Android OS", false),
    REBOOT_FASTBOOT("Reboot to Bootloader (Fastboot)", "Send DA reboot command to enter fastboot mode", false),
    REBOOT_RECOVERY("Reboot to Recovery", "Send DA reboot command to enter recovery mode", false)
}

enum class LogLevel {
    INFO, SUCCESS, WARNING, ERROR, RAW, AI, ACCENT, CYAN, MAGENTA
}

data class TerminalLog(
    val timestamp: String,
    val message: String,
    val level: LogLevel = LogLevel.INFO,
    val isBold: Boolean = false
)

data class OperationProgress(
    val isRunning: Boolean = false,
    val title: String = "",
    val detail: String = "",
    val percentage: Float = 0f,
    val bytesProcessed: Long = 0,
    val totalBytes: Long = 0,
    val speedKbPerSec: Double = 0.0,
    val estimatedSecondsRemaining: Int = 0
)