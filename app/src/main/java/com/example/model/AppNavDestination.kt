package com.example.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class MainPlatformCategory(
    val title: String,
    val shortName: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color,
    val isPlaceholder: Boolean = false
) {
    MTK(
        title = "MediaTek (MTK)",
        shortName = "MTK",
        subtitle = "BROM / Preloader / DA Engine",
        icon = Icons.Default.Bolt,
        accentColor = Color(0xFFEF4444)
    ),
    QUALCOMM(
        title = "Qualcomm (QC)",
        shortName = "Qualcomm",
        subtitle = "EDL 9008 / Firehose / Sahara",
        icon = Icons.Default.Memory,
        accentColor = Color(0xFF3B82F6)
    ),
    SPD(
        title = "Spreadtrum (SPD / Unisoc)",
        shortName = "SPD",
        subtitle = "FDL / PAC Engine (Coming Soon)",
        icon = Icons.Default.Category,
        accentColor = Color(0xFFF97316),
        isPlaceholder = true
    ),
    ISP(
        title = "Direct ISP (eMMC / UFS)",
        shortName = "ISP",
        subtitle = "Hardware Pinout (Coming Soon)",
        icon = Icons.Default.Cable,
        accentColor = Color(0xFFA855F7),
        isPlaceholder = true
    ),
    ADB_FASTBOOT(
        title = "ADB & Fastboot",
        shortName = "ADB / FB",
        subtitle = "Android Debug & Bootloader Mode",
        icon = Icons.Default.DeveloperMode,
        accentColor = Color(0xFF10B981)
    ),
    SETTINGS(
        title = "Settings",
        shortName = "Settings",
        subtitle = "USB Drivers, Theme & Options",
        icon = Icons.Default.Settings,
        accentColor = Color(0xFF64748B)
    ),
    ABOUT(
        title = "About",
        shortName = "About",
        subtitle = "App Info, Version & Credits",
        icon = Icons.Default.Info,
        accentColor = Color(0xFF06B6D4)
    )
}

enum class AppNavDestination(
    val category: MainPlatformCategory,
    val title: String,
    val shortTitle: String,
    val subtitle: String,
    val icon: ImageVector,
    val isPlaceholder: Boolean = false
) {
    // 🔴 MediaTek (MTK) Sub-tabs
    MTK_FLASH(
        category = MainPlatformCategory.MTK,
        title = "⚡ Flash Tool",
        shortTitle = "Flash",
        subtitle = "Firmware Flashing Engine & Scatter Loader",
        icon = Icons.Default.FlashOn
    ),
    MTK_BACKUP(
        category = MainPlatformCategory.MTK,
        title = "💾 Backup / Read",
        shortTitle = "Backup",
        subtitle = "Full ROM, Stable FW, NV & Custom Dump",
        icon = Icons.Default.Save
    ),
    MTK_SERVICE(
        category = MainPlatformCategory.MTK,
        title = "🔓 BROM Service",
        shortTitle = "Service",
        subtitle = "One-Click FRP, Factory Reset & BL Unlock",
        icon = Icons.Default.LockOpen
    ),

    // 🔵 Qualcomm (QC) Sub-tabs
    QC_FLASH(
        category = MainPlatformCategory.QUALCOMM,
        title = "⚡ Flash (EDL 9008)",
        shortTitle = "Flash",
        subtitle = "Rawprogram / Patch / Firehose Flasher",
        icon = Icons.Default.FlashOn
    ),
    QC_BACKUP(
        category = MainPlatformCategory.QUALCOMM,
        title = "💾 Backup (EDL Dump)",
        shortTitle = "Backup",
        subtitle = "Read Full EMMC / UFS Partitions & QCN",
        icon = Icons.Default.Save
    ),
    QC_SERVICE(
        category = MainPlatformCategory.QUALCOMM,
        title = "🔓 Service (EDL Tools)",
        shortTitle = "Service",
        subtitle = "EDL FRP Reset, Format Userdata, EDL BL",
        icon = Icons.Default.LockOpen
    ),

    // 🟠 SPD Sub-tabs (Placeholder)
    SPD_FLASH(
        category = MainPlatformCategory.SPD,
        title = "⚡ Flash (PAC / FDL)",
        shortTitle = "Flash",
        subtitle = "Spreadtrum PAC Firmware Flashing",
        icon = Icons.Default.FlashOn,
        isPlaceholder = true
    ),
    SPD_BACKUP(
        category = MainPlatformCategory.SPD,
        title = "💾 Backup (Read Flash)",
        shortTitle = "Backup",
        subtitle = "Dump Unisoc / SPD Partitions & NV",
        icon = Icons.Default.Save,
        isPlaceholder = true
    ),
    SPD_SERVICE(
        category = MainPlatformCategory.SPD,
        title = "🔓 Service (Diag Mode)",
        shortTitle = "Service",
        subtitle = "Diag Mode Repair, Wipe Userdata & FRP",
        icon = Icons.Default.LockOpen,
        isPlaceholder = true
    ),

    // 🟣 ISP Sub-tabs (Placeholder)
    ISP_FLASH(
        category = MainPlatformCategory.ISP,
        title = "⚡ Flash (Direct ISP)",
        shortTitle = "Flash",
        subtitle = "Direct eMMC / UFS Hardware Write",
        icon = Icons.Default.FlashOn,
        isPlaceholder = true
    ),
    ISP_BACKUP(
        category = MainPlatformCategory.ISP,
        title = "💾 Backup (ISP Dump)",
        shortTitle = "Backup",
        subtitle = "Direct eMMC / UFS Raw Dump",
        icon = Icons.Default.Save,
        isPlaceholder = true
    ),
    ISP_SERVICE(
        category = MainPlatformCategory.ISP,
        title = "🔓 Service (Direct ISP)",
        shortTitle = "Service",
        subtitle = "Direct Userdata Wipe & FRP Erase",
        icon = Icons.Default.LockOpen,
        isPlaceholder = true
    ),

    // 🟢 ADB & Fastboot
    ADB_MODE(
        category = MainPlatformCategory.ADB_FASTBOOT,
        title = "🔷 ADB Mode",
        shortTitle = "ADB",
        subtitle = "Device Info, Reboot to BROM/EDL/FB & Shell",
        icon = Icons.Default.Android
    ),
    FASTBOOT_MODE(
        category = MainPlatformCategory.ADB_FASTBOOT,
        title = "🟡 Fastboot Mode",
        shortTitle = "Fastboot",
        subtitle = "Getvar, BL Unlock, TWRP/Boot Flash & Erase FRP",
        icon = Icons.Default.Terminal
    ),

    // ⚙️ Settings
    SETTINGS_PAGE(
        category = MainPlatformCategory.SETTINGS,
        title = "⚙️ Tool Settings",
        shortTitle = "Settings",
        subtitle = "USB OTG Config, Baud Rate, Theme & Logging",
        icon = Icons.Default.Settings
    ),

    // ℹ️ About
    ABOUT_PAGE(
        category = MainPlatformCategory.ABOUT,
        title = "ℹ️ About Application",
        shortTitle = "About",
        subtitle = "Version, Architecture & Developer Info",
        icon = Icons.Default.Info
    );

    companion object {
        val DEFAULT = MTK_FLASH
    }
}

