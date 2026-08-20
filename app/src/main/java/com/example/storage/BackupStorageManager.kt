package com.example.storage

import android.content.Context
import android.os.Environment
import com.example.model.PartitionEntry
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupStorageManager(private val context: Context) {

    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    private var customBackupPath: String? = null

    fun setCustomBackupPath(path: String) {
        customBackupPath = path
    }

    fun getBackupDirectory(): File {
        if (!customBackupPath.isNullOrBlank()) {
            val customDir = File(customBackupPath!!)
            if (!customDir.exists()) {
                customDir.mkdirs()
            }
            return customDir
        }

        // Try standard Public Download/MTK_Backups so user can easily see in File Manager
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val candidate = File(publicDownloads, "MTK_Backups")
        if (candidate.exists() || candidate.mkdirs()) {
            return candidate
        }

        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        val backupDir = File(baseDir, "MTK_Backups")
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }
        return backupDir
    }

    fun savePartitionDump(
        partitionName: String,
        data: ByteArray,
        sha256: String,
        sessionFolder: String? = null
    ): String {
        val baseDir = getBackupDirectory()
        val targetDir = if (!sessionFolder.isNullOrBlank()) {
            val sub = File(baseDir, sessionFolder)
            if (!sub.exists()) sub.mkdirs()
            sub
        } else {
            baseDir
        }

        val fileName = "${partitionName}.bin"
        val outFile = File(targetDir, fileName)

        FileOutputStream(outFile).use { fos ->
            fos.write(data)
        }

        // Save checksum file alongside
        val checksumFile = File(targetDir, "${fileName}.sha256")
        checksumFile.writeText("$sha256  $fileName\n")

        return outFile.absolutePath
    }

    fun generateScatterFile(
        platform: String,
        partitions: List<PartitionEntry>,
        sessionFolder: String? = null
    ): String {
        val baseDir = getBackupDirectory()
        val targetDir = if (!sessionFolder.isNullOrBlank()) {
            val sub = File(baseDir, sessionFolder)
            if (!sub.exists()) sub.mkdirs()
            sub
        } else {
            baseDir
        }

        val fileName = "${platform}_Android_scatter.txt"
        val scatterFile = File(targetDir, fileName)

        val sb = StringBuilder()
        sb.append("############################################################################################################\n")
        sb.append("#\n")
        sb.append("#  General Setting\n")
        sb.append("#\n")
        sb.append("############################################################################################################\n")
        sb.append("- general: MTK_PLATFORM_CFG\n")
        sb.append("  info:\n")
        sb.append("    - config_version: V1.1.2\n")
        sb.append("      platform: $platform\n")
        sb.append("      project: ${platform}_Android\n")
        sb.append("      storage: EMMC\n")
        sb.append("      boot_channel: MSDC_0\n")
        sb.append("      block_size: 0x20000\n\n")
        sb.append("############################################################################################################\n")
        sb.append("#\n")
        sb.append("#  Layout Setting\n")
        sb.append("#\n")
        sb.append("############################################################################################################\n")

        for (p in partitions) {
            sb.append("- partition_index: SYS${p.partitionIndex}\n")
            sb.append("  partition_name: ${p.partitionName}\n")
            sb.append("  file_name: ${p.fileName.ifBlank { "NONE" }}\n")
            sb.append("  is_download: ${if (p.isDownload) "true" else "false"}\n")
            sb.append("  type: NORMAL_ROM\n")
            sb.append("  linear_start_addr: ${p.linearStartAddrHex}\n")
            sb.append("  physical_start_addr: ${p.physicalStartAddrHex}\n")
            sb.append("  partition_size: ${p.partitionSizeHex}\n")
            sb.append("  region: ${p.region}\n")
            sb.append("  storage: HW_STORAGE_EMMC\n")
            sb.append("  boundary_check: true\n")
            sb.append("  is_reserved: false\n")
            sb.append("  operation_type: UPDATE\n")
            sb.append("  reserve: 0x00\n\n")
        }

        scatterFile.writeText(sb.toString())
        return scatterFile.absolutePath
    }

    fun listBackups(): List<File> {
        val dir = getBackupDirectory()
        return dir.walkTopDown().filter { it.extension == "bin" || it.extension == "txt" }.toList()
            .sortedByDescending { it.lastModified() }
    }
}
