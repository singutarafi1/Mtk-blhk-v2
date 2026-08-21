package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppNavDestination
import com.example.model.BackupMode
import com.example.model.FlashOptions
import com.example.model.LogLevel
import com.example.model.MtkBrand
import com.example.model.MtkChipInfo
import com.example.model.MtkDeviceDatabase
import com.example.model.MtkDeviceModel
import com.example.model.OperationProgress
import com.example.model.PartitionEntry
import com.example.model.ServiceFunction
import com.example.model.TerminalLog
import com.example.protocol.TargetPhoneState
import com.example.ui.theme.TerminalTimestamp
import com.example.viewmodel.MtkBridgeViewModel
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun UnlockToolFlashScreen(
    viewModel: MtkBridgeViewModel,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentDestination by viewModel.currentDestination.collectAsState()
    val chipInfo by viewModel.chipInfo.collectAsState()
    val targetPhoneState by viewModel.targetPhoneState.collectAsState()
    val autoNvBackup by viewModel.autoNvBackup.collectAsState()
    val autoReboot by viewModel.autoReboot.collectAsState()
    val partitions by viewModel.partitions.collectAsState()
    val progress by viewModel.operationProgress.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val flashOptions by viewModel.flashOptions.collectAsState()
    val backupMode by viewModel.backupMode.collectAsState()
    val selectedBrand by viewModel.selectedBrand.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val scatterFileName by viewModel.scatterFileName.collectAsState()

    var showStopConfirmDialog by remember { mutableStateOf(false) }

    if (showStopConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showStopConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Stop, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                    Text("Stop Operation?", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF1F5F9))
                }
            },
            text = {
                Text(
                    "Warning: Interrupting active partition flashing or formatting may risk corrupting device storage.",
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1)
                )
            },
            containerColor = Color(0xFF1E293B),
            confirmButton = {
                Button(
                    onClick = {
                        showStopConfirmDialog = false
                        viewModel.cancelCurrentOperation()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Yes, Stop", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showStopConfirmDialog = false },
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color(0xFF475569))
                ) {
                    Text("Cancel", fontSize = 11.sp, color = Color(0xFF94A3B8))
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
    ) {
        TopCompactStatusBar(
            currentDestination = currentDestination,
            chipInfo = chipInfo,
            targetPhoneState = targetPhoneState,
            onOpenDrawer = onOpenDrawer
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.32f)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Crossfade(targetState = currentDestination, label = "tab_crossfade") { dest ->
                when (dest) {
                    AppNavDestination.MTK_FLASH -> {
                        MtkFlashDeck(
                            viewModel = viewModel,
                            selectedBrand = selectedBrand,
                            selectedModel = selectedModel,
                            scatterFileName = scatterFileName,
                            flashOptions = flashOptions,
                            partitions = partitions,
                            progress = progress,
                            onStopClick = { showStopConfirmDialog = true }
                        )
                    }
                    AppNavDestination.MTK_BACKUP -> {
                        MtkBackupDeck(
                            viewModel = viewModel,
                            selectedBrand = selectedBrand,
                            selectedModel = selectedModel,
                            backupMode = backupMode,
                            progress = progress,
                            onStopClick = { showStopConfirmDialog = true }
                        )
                    }
                    AppNavDestination.MTK_SERVICE -> {
                        MtkServiceDeck(
                            viewModel = viewModel,
                            selectedBrand = selectedBrand,
                            selectedModel = selectedModel,
                            autoReboot = autoReboot,
                            autoNvBackup = autoNvBackup,
                            progress = progress,
                            onStopClick = { showStopConfirmDialog = true }
                        )
                    }

                    AppNavDestination.QC_FLASH -> {
                        QcFlashDeck(
                            viewModel = viewModel,
                            progress = progress,
                            onStopClick = { showStopConfirmDialog = true }
                        )
                    }
                    AppNavDestination.QC_BACKUP -> {
                        QcBackupDeck(
                            viewModel = viewModel,
                            progress = progress,
                            onStopClick = { showStopConfirmDialog = true }
                        )
                    }
                    AppNavDestination.QC_SERVICE -> {
                        QcServiceDeck(
                            viewModel = viewModel,
                            progress = progress,
                            onStopClick = { showStopConfirmDialog = true }
                        )
                    }

                    AppNavDestination.SPD_FLASH, AppNavDestination.SPD_BACKUP, AppNavDestination.SPD_SERVICE -> {
                        PlatformPlaceholderDeck(
                            platformTitle = "Spreadtrum (SPD / Unisoc)",
                            subTabTitle = dest.title,
                            subtitle = "FDL & PAC Engine under active integration."
                        )
                    }

                    AppNavDestination.ISP_FLASH, AppNavDestination.ISP_BACKUP, AppNavDestination.ISP_SERVICE -> {
                        PlatformPlaceholderDeck(
                            platformTitle = "Direct ISP (eMMC / UFS)",
                            subTabTitle = dest.title,
                            subtitle = "Direct Hardware Pinout Engine under active integration."
                        )
                    }

                    AppNavDestination.ADB_MODE -> {
                        AdbDeck(
                            viewModel = viewModel,
                            progress = progress
                        )
                    }
                    AppNavDestination.FASTBOOT_MODE -> {
                        FastbootDeck(
                            viewModel = viewModel,
                            progress = progress
                        )
                    }

                    AppNavDestination.SETTINGS_PAGE -> {
                        SettingsDeck(viewModel = viewModel)
                    }
                    AppNavDestination.ABOUT_PAGE -> {
                        AboutDeck()
                    }
                }
            }
        }

        CompactOperationFooter(progress = progress)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.68f)
                .padding(start = 6.dp, end = 6.dp, bottom = 4.dp)
        ) {
            FullTerminalConsole(
                logs = logs,
                onClear = { viewModel.clearLogs() }
            )
        }
    }
}

// =============================================================================
// 1️⃣ MTK FLASH DECK
// =============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MtkFlashDeck(
    viewModel: MtkBridgeViewModel,
    selectedBrand: MtkBrand,
    selectedModel: MtkDeviceModel,
    scatterFileName: String,
    flashOptions: FlashOptions,
    partitions: List<PartitionEntry>,
    progress: OperationProgress,
    onStopClick: () -> Unit
) {
    val context = LocalContext.current
    val scatterPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val content = reader.readText()
                reader.close()
                val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "scatter.txt"
                viewModel.loadScatterContent(content, fileName)
            } catch (e: Exception) {
                viewModel.addLog(TerminalLog(nowTime(), "Failed to load scatter: ${e.message}", LogLevel.ERROR))
            }
        }
    }

    Card(
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            BrandModelSelectorRow(
                selectedBrand = selectedBrand,
                selectedModel = selectedModel,
                onBrandSelect = { viewModel.selectBrand(it) },
                onModelSelect = { viewModel.selectModel(it) }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (scatterFileName.isNotEmpty()) Color(0xFF064E3B) else Color(0xFF1E293B),
                    border = BorderStroke(1.dp, if (scatterFileName.isNotEmpty()) Color(0xFF10B981) else Color(0xFF334155)),
                    modifier = Modifier
                        .weight(1.4f)
                        .clickable { scatterPickerLauncher.launch("*/*") }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(13.dp))
                        Text(
                            text = if (scatterFileName.isEmpty()) "Load Scatter File" else scatterFileName,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (scatterFileName.isNotEmpty()) Color(0xFF4ADE80) else Color(0xFFE2E8F0),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier
                        .weight(0.8f)
                        .clickable { viewModel.selectAllPartitions(true) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "${partitions.count { it.isSelectedForFlashing }}/${partitions.size} Parts",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                QuickOptionTogglePill(label = "NV Backup", checked = flashOptions.readNvData, onToggle = { viewModel.toggleFlashReadNvData(it) })
                QuickOptionTogglePill(label = "Auto Reboot", checked = flashOptions.autoReboot, onToggle = { viewModel.toggleFlashAutoReboot(it) })
                QuickOptionTogglePill(label = "Checksum", checked = flashOptions.daDlChecksum, onToggle = { viewModel.toggleFlashDaDlChecksum(it) })
                QuickOptionTogglePill(label = "BL Unlock 1st", checked = flashOptions.flashAfterBlUnlock, onToggle = { viewModel.toggleFlashAfterBlUnlock(it) })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = { viewModel.scanTargetPhone() },
                    enabled = !progress.isRunning,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f).height(30.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF38BDF8))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Scan & BROM", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF1F5F9))
                }

                val hasPartitions = partitions.any { it.isSelectedForFlashing }
                Button(
                    onClick = {
                        if (progress.isRunning) onStopClick()
                        else viewModel.executeFlashOperation()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            progress.isRunning -> Color(0xFFDC2626)
                            hasPartitions -> Color(0xFF16A34A)
                            else -> Color(0xFF1E293B)
                        }
                    ),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1.3f).height(30.dp).testTag("start_flash_button")
                ) {
                    Icon(
                        imageVector = if (progress.isRunning) Icons.Default.Stop else Icons.Default.FlashOn,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = when {
                            progress.isRunning -> "STOP FLASH"
                            hasPartitions -> "FLASH (${partitions.count { it.isSelectedForFlashing }})"
                            else -> "FLASH (Direct USB)"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// =============================================================================
// 2️⃣ MTK BACKUP DECK
// =============================================================================
@Composable
private fun MtkBackupDeck(
    viewModel: MtkBridgeViewModel,
    selectedBrand: MtkBrand,
    selectedModel: MtkDeviceModel,
    backupMode: BackupMode,
    progress: OperationProgress,
    onStopClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            BrandModelSelectorRow(
                selectedBrand = selectedBrand,
                selectedModel = selectedModel,
                onBrandSelect = { viewModel.selectBrand(it) },
                onModelSelect = { viewModel.selectModel(it) }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BackupMode.values().forEach { mode ->
                    val isSelected = backupMode == mode
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isSelected) Color(0xFF1D4ED8) else Color(0xFF1E293B),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155)),
                        modifier = Modifier.clickable { viewModel.setBackupMode(mode) }
                    ) {
                        Text(
                            text = mode.shortLabel,
                            fontSize = 9.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else Color(0xFF94A3B8),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = { viewModel.scanTargetPhone() },
                    enabled = !progress.isRunning,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f).height(30.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF38BDF8))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Scan Port", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF1F5F9))
                }

                Button(
                    onClick = {
                        if (progress.isRunning) onStopClick()
                        else viewModel.executeBackupOperation()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (progress.isRunning) Color(0xFFDC2626) else Color(0xFF0284C7)
                    ),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1.3f).height(30.dp)
                ) {
                    Icon(if (progress.isRunning) Icons.Default.Stop else Icons.Default.Save, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (progress.isRunning) "STOP DUMP" else "START ${backupMode.shortLabel.uppercase()}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// =============================================================================
// 3️⃣ MTK SERVICE DECK
// =============================================================================
@Composable
private fun MtkServiceDeck(
    viewModel: MtkBridgeViewModel,
    selectedBrand: MtkBrand,
    selectedModel: MtkDeviceModel,
    autoReboot: Boolean,
    autoNvBackup: Boolean,
    progress: OperationProgress,
    onStopClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            BrandModelSelectorRow(
                selectedBrand = selectedBrand,
                selectedModel = selectedModel,
                onBrandSelect = { viewModel.selectBrand(it) },
                onModelSelect = { viewModel.selectModel(it) }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ServiceQuickButton(
                    label = "Erase FRP",
                    color = Color(0xFF16A34A),
                    icon = Icons.Default.LockOpen,
                    enabled = !progress.isRunning,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.executeServiceFunctionDirect(ServiceFunction.ERASE_FRP) }
                )
                ServiceQuickButton(
                    label = "Factory Reset",
                    color = Color(0xFFDC2626),
                    icon = Icons.Default.LockReset,
                    enabled = !progress.isRunning,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.executeServiceFunctionDirect(ServiceFunction.FACTORY_RESET) }
                )
                ServiceQuickButton(
                    label = "Unlock BL",
                    color = Color(0xFFD97706),
                    icon = Icons.Default.Security,
                    enabled = !progress.isRunning,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.executeServiceFunctionDirect(ServiceFunction.UNLOCK_BOOTLOADER) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ServiceQuickButton(
                    label = "Relock BL",
                    color = Color(0xFF475569),
                    icon = Icons.Default.Security,
                    enabled = !progress.isRunning,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.executeServiceFunctionDirect(ServiceFunction.LOCK_BOOTLOADER) }
                )
                ServiceQuickButton(
                    label = "Disable Mi Acc",
                    color = Color(0xFFEA580C),
                    icon = Icons.Default.Security,
                    enabled = !progress.isRunning,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.executeServiceFunctionDirect(ServiceFunction.DISABLE_MI_ACCOUNT) }
                )
                ServiceQuickButton(
                    label = "Read Chip Info",
                    color = Color(0xFF2563EB),
                    icon = Icons.Default.Info,
                    enabled = !progress.isRunning,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.executeServiceFunctionDirect(ServiceFunction.READ_INFO) }
                )
            }
        }
    }
}

// =============================================================================
// 4️⃣ QUALCOMM (QC) DECKS
// =============================================================================
@Composable
private fun QcFlashDeck(
    viewModel: MtkBridgeViewModel,
    progress: OperationProgress,
    onStopClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔵 Qualcomm Snapdragon (EDL 9008)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF60A5FA))
                Text("Sahara / Firehose", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF94A3B8))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.weight(1f).clickable { viewModel.addLog(TerminalLog(nowTime(), "Select rawprogram0.xml...", LogLevel.INFO)) }
                ) {
                    Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("rawprogram0.xml", fontSize = 9.sp, color = Color(0xFFE2E8F0))
                    }
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.weight(1f).clickable { viewModel.addLog(TerminalLog(nowTime(), "Select prog_firehose_xxxx.elf...", LogLevel.INFO)) }
                ) {
                    Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Memory, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Firehose ELF", fontSize = 9.sp, color = Color(0xFFE2E8F0))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = { viewModel.addLog(TerminalLog(nowTime(), "Scanning for Qualcomm HS-USB QDLoader 9008 port...", LogLevel.INFO)) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f).height(30.dp)
                ) {
                    Text("Scan EDL 9008", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Button(
                    onClick = { viewModel.addLog(TerminalLog(nowTime(), "Starting Qualcomm Firehose XML Flashing...", LogLevel.SUCCESS)) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1.2f).height(30.dp)
                ) {
                    Text("FLASH (EDL)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun QcBackupDeck(
    viewModel: MtkBridgeViewModel,
    progress: OperationProgress,
    onStopClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text("🔵 Qualcomm EDL Partition Dump", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF60A5FA))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ServiceQuickButton(label = "Dump EFS / QCN", color = Color(0xFF0284C7), icon = Icons.Default.Save, enabled = true, modifier = Modifier.weight(1f)) {
                    viewModel.addLog(TerminalLog(nowTime(), "Reading modemst1, modemst2, fsg, fsc partitions...", LogLevel.INFO))
                }
                ServiceQuickButton(label = "Dump Boot / Recovery", color = Color(0xFF0284C7), icon = Icons.Default.Save, enabled = true, modifier = Modifier.weight(1f)) {
                    viewModel.addLog(TerminalLog(nowTime(), "Reading boot, recovery, abl, xbl partitions...", LogLevel.INFO))
                }
                ServiceQuickButton(label = "Full EDL Dump", color = Color(0xFF2563EB), icon = Icons.Default.Save, enabled = true, modifier = Modifier.weight(1f)) {
                    viewModel.addLog(TerminalLog(nowTime(), "Starting full Qualcomm storage read...", LogLevel.INFO))
                }
            }
        }
    }
}

@Composable
private fun QcServiceDeck(
    viewModel: MtkBridgeViewModel,
    progress: OperationProgress,
    onStopClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text("🔵 Qualcomm EDL Service & Unlock Tools", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF60A5FA))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ServiceQuickButton(label = "EDL Erase FRP", color = Color(0xFF16A34A), icon = Icons.Default.LockOpen, enabled = true, modifier = Modifier.weight(1f)) {
                    viewModel.addLog(TerminalLog(nowTime(), "[EDL] Erasing config & frp partitions...", LogLevel.SUCCESS))
                }
                ServiceQuickButton(label = "EDL Factory Reset", color = Color(0xFFDC2626), icon = Icons.Default.LockReset, enabled = true, modifier = Modifier.weight(1f)) {
                    viewModel.addLog(TerminalLog(nowTime(), "[EDL] Formatting userdata & cache partitions...", LogLevel.WARNING))
                }
                ServiceQuickButton(label = "EDL BL Unlock", color = Color(0xFFD97706), icon = Icons.Default.Security, enabled = true, modifier = Modifier.weight(1f)) {
                    viewModel.addLog(TerminalLog(nowTime(), "[EDL] Patching devinfo partition to unlock bootloader...", LogLevel.INFO))
                }
            }
        }
    }
}

// =============================================================================
// 5️⃣ PLACEHOLDER DECK (SPD / ISP)
// =============================================================================
@Composable
private fun PlatformPlaceholderDeck(
    platformTitle: String,
    subTabTitle: String,
    subtitle: String
) {
    Card(
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF334155),
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Text(
                    text = "UNDER ACTIVE INTEGRATION",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Text(text = "$platformTitle -> $subTabTitle", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(text = subtitle, fontSize = 9.sp, color = Color(0xFF94A3B8))
        }
    }
}

// =============================================================================
// 6️⃣ ADB & FASTBOOT DECKS
// =============================================================================
@Composable
private fun AdbDeck(
    viewModel: MtkBridgeViewModel,
    progress: OperationProgress
) {
    Card(
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔷 ADB Mode (Android USB Debugging)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                Text("adb server / shell", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF94A3B8))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ServiceQuickButton(label = "Read Props", color = Color(0xFF2563EB), icon = Icons.Default.Info, enabled = true, modifier = Modifier.weight(1f)) {
                    viewModel.runAdbReadInfo()
                }
                ServiceQuickButton(label = "Reboot BROM", color = Color(0xFFEF4444), icon = Icons.Default.Refresh, enabled = true, modifier = Modifier.weight(1f)) {
                    viewModel.runAdbReboot("brom")
                }
                ServiceQuickButton(label = "Reboot EDL", color = Color(0xFF3B82F6), icon = Icons.Default.Refresh, enabled = true, modifier = Modifier.weight(1f)) {
                    viewModel.runAdbReboot("edl")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ServiceQuickButton(label = "Reboot Fastboot", color = Color(0xFFEAB308), icon = Icons.Default.Refresh, enabled = true, modifier = Modifier.weight(1f)) {
                    viewModel.runAdbReboot("fastboot")
                }
                ServiceQuickButton(label = "Reboot Recovery", color = Color(0xFFA855F7), icon = Icons.Default.Refresh, enabled = true, modifier = Modifier.weight(1f)) {
                    viewModel.runAdbReboot("recovery")
                }
                ServiceQuickButton(label = "Reboot System", color = Color(0xFF10B981), icon = Icons.Default.Refresh, enabled = true, modifier = Modifier.weight(1f)) {
                    viewModel.runAdbReboot("system")
                }
            }
        }
    }
}

@Composable
private fun FastbootDeck(
    viewModel: MtkBridgeViewModel,
    progress: OperationProgress
) {
    Card(
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🟡 Fastboot Mode (Bootloader Interface)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFBBF24))
                Text("fastboot getvar", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF94A3B8))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ServiceQuickButton(label = "Getvar All", color = Color(0xFF2563EB), icon = Icons.Default.Info, enabled = true, modifier = Modifier.weight(1f)) {
                    viewModel.runFastbootReadAllVars()
                }
                ServiceQuickButton(label = "Unlock BL", color = Color(0xFFD97706), icon = Icons.Default.LockOpen, enabled = true, modifier = Modifier.weight(1f)) {
                    viewModel.runFastbootUnlockBootloader()
                }
                ServiceQuickButton(label = "Erase FRP", color = Color(0xFF16A34A), icon = Icons.Default.LockOpen, enabled = true, modifier = Modifier.weight(1f)) {
                    viewModel.runFastbootEraseFrp()
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ServiceQuickButton(label = "Reboot BROM", color = Color(0xFFEF4444), icon = Icons.Default.Refresh, enabled = true, modifier = Modifier.weight(1f)) {
                    viewModel.runFastbootReboot("brom")
                }
                ServiceQuickButton(label = "Reboot EDL", color = Color(0xFF3B82F6), icon = Icons.Default.Refresh, enabled = true, modifier = Modifier.weight(1f)) {
                    viewModel.runFastbootReboot("edl")
                }
                ServiceQuickButton(label = "Reboot System", color = Color(0xFF10B981), icon = Icons.Default.Refresh, enabled = true, modifier = Modifier.weight(1f)) {
                    viewModel.runFastbootReboot("system")
                }
            }
        }
    }
}

// =============================================================================
// 7️⃣ SETTINGS & ABOUT DECKS
// =============================================================================
@Composable
private fun SettingsDeck(
    viewModel: MtkBridgeViewModel
) {
    Card(
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text("⚙️ Tool Configuration & Options", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = { viewModel.clearLogs() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f).height(28.dp)
                ) {
                    Text("Clear Logs", fontSize = 9.sp, color = Color(0xFFF1F5F9))
                }
                Button(
                    onClick = { viewModel.sendWatchdogReset() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f).height(28.dp)
                ) {
                    Text("Watchdog Reset", fontSize = 9.sp, color = Color(0xFFF1F5F9))
                }
            }
        }
    }
}

@Composable
private fun AboutDeck() {
    Card(
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("📱 Native GSM Unlock & Flashing Engine", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Android Native Direct USB OTG Host Edition", fontSize = 9.5.sp, color = Color(0xFF38BDF8))
            Spacer(modifier = Modifier.height(4.dp))
            Text("Supports MediaTek BROM / Preloader, Qualcomm EDL 9008, ADB & Fastboot", fontSize = 8.5.sp, color = Color(0xFF94A3B8))
        }
    }
}

// =============================================================================
// 8️⃣ FULL TERMINAL CONSOLE
// =============================================================================
@Composable
private fun FullTerminalConsole(
    logs: List<TerminalLog>,
    onClear: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val logListState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            logListState.animateScrollToItem(logs.size - 1)
        }
    }

    Card(
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0F1D)),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(shape = CircleShape, color = Color(0xFF10B981), modifier = Modifier.size(6.dp)) {}
                    Text("LIVE LOG CONSOLE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8), fontFamily = FontFamily.Monospace)
                    Text("(${logs.size})", fontSize = 9.sp, color = Color(0xFF64748B), fontFamily = FontFamily.Monospace)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val text = logs.joinToString("\n") { "[${it.timestamp}] [${it.level}] ${it.message}" }
                            clipboardManager.setText(AnnotatedString(text))
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF94A3B8), modifier = Modifier.size(11.dp))
                    }

                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(11.dp))
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 4.dp))

            LazyColumn(
                state = logListState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                items(logs) { log ->
                    val color = when (log.level) {
                        LogLevel.SUCCESS -> Color(0xFF10B981)
                        LogLevel.ERROR -> Color(0xFFF87171)
                        LogLevel.WARNING -> Color(0xFFFBBF24)
                        LogLevel.AI -> Color(0xFFA78BFA)
                        LogLevel.ACCENT -> Color(0xFF38BDF8)
                        LogLevel.CYAN -> Color(0xFF22D3EE)
                        LogLevel.MAGENTA -> Color(0xFFF472B6)
                        LogLevel.RAW -> Color(0xFF94A3B8)
                        LogLevel.INFO -> Color(0xFFF1F5F9)
                    }
                    Row(verticalAlignment = Alignment.Top) {
                        Text("[${log.timestamp}] ", fontSize = 10.sp, color = TerminalTimestamp, fontFamily = FontFamily.Monospace)
                        Text(log.message, fontSize = 10.sp, color = color, fontFamily = FontFamily.Monospace, lineHeight = 13.sp)
                    }
                }
            }
        }
    }
}

// =============================================================================
// REUSABLE HELPER COMPONENTS
// =============================================================================
@Composable
private fun ServiceQuickButton(
    label: String,
    color: Color,
    icon: ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(4.dp),
        modifier = modifier.height(32.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.White)
        Spacer(modifier = Modifier.width(3.dp))
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
    }
}

@Composable
private fun QuickOptionTogglePill(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(3.dp),
        color = if (checked) Color(0xFF1D4ED8) else Color(0xFF1E293B),
        border = BorderStroke(1.dp, if (checked) Color(0xFF38BDF8) else Color(0xFF334155)),
        modifier = Modifier.clickable { onToggle(!checked) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = if (checked) Icons.Default.Check else Icons.Default.Clear,
                contentDescription = null,
                tint = if (checked) Color.White else Color(0xFF64748B),
                modifier = Modifier.size(9.dp)
            )
            Text(
                text = label,
                fontSize = 8.5.sp,
                fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
                color = if (checked) Color.White else Color(0xFF94A3B8)
            )
        }
    }
}

@Composable
private fun BrandModelSelectorRow(
    selectedBrand: MtkBrand,
    selectedModel: MtkDeviceModel,
    onBrandSelect: (MtkBrand) -> Unit,
    onModelSelect: (MtkDeviceModel) -> Unit
) {
    var brandExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF1E293B),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth().clickable { brandExpanded = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Brand: ${selectedBrand.brandName}", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                    Text("▼", fontSize = 8.sp, color = Color(0xFF94A3B8))
                }
            }
            DropdownMenu(
                expanded = brandExpanded,
                onDismissRequest = { brandExpanded = false },
                modifier = Modifier.background(Color(0xFF1E293B))
            ) {
                MtkDeviceDatabase.brands.forEach { brand ->
                    DropdownMenuItem(
                        text = { Text(brand.brandName, fontSize = 11.sp, color = Color.White) },
                        onClick = {
                            onBrandSelect(brand)
                            brandExpanded = false
                        }
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1.2f)) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF1E293B),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth().clickable { modelExpanded = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(selectedModel.modelName, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("▼", fontSize = 8.sp, color = Color(0xFF94A3B8))
                }
            }
            DropdownMenu(
                expanded = modelExpanded,
                onDismissRequest = { modelExpanded = false },
                modifier = Modifier.background(Color(0xFF1E293B))
            ) {
                selectedBrand.models.forEach { model ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(model.modelName, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("${model.chipset} (${model.chipCode})", fontSize = 9.sp, color = Color(0xFF94A3B8))
                            }
                        },
                        onClick = {
                            onModelSelect(model)
                            modelExpanded = false
                        }
                    )
                }
            }
        }
    }
}

// =============================================================================
// COMPACT TOP STATUS BAR (no simulation)
// =============================================================================
@Composable
private fun TopCompactStatusBar(
    currentDestination: AppNavDestination,
    chipInfo: MtkChipInfo,
    targetPhoneState: TargetPhoneState,
    onOpenDrawer: () -> Unit
) {
    val isConnected = targetPhoneState is TargetPhoneState.Connected

    val (bannerBg, bannerBorder, bannerIconTint, bannerTitle) = when {
        isConnected -> {
            val isBrom = (targetPhoneState as TargetPhoneState.Connected).isBromMode
            val modeName = if (isBrom) "BROM Mode" else "Preloader Mode"
            arrayOf(
                Color(0xFF064E3B),
                Color(0xFF059669),
                Color(0xFF34D399),
                "USB: $modeName"
            )
        }
        else -> arrayOf(
            Color(0xFF1E1B4B),
            Color(0xFF4338CA),
            Color(0xFF818CF8),
            "USB OTG HOST READY"
        )
    }

    Surface(
        color = Color(0xFF020617),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.clickable { onOpenDrawer() }
            ) {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu Drawer", tint = Color.White, modifier = Modifier.size(17.dp))
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = currentDestination.category.accentColor.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, currentDestination.category.accentColor)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(currentDestination.icon, contentDescription = null, tint = currentDestination.category.accentColor, modifier = Modifier.size(12.dp))
                        Text(
                            text = "[${currentDestination.category.shortName}] ${currentDestination.shortTitle}",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(3.dp),
                color = bannerBg as Color,
                border = BorderStroke(1.dp, bannerBorder as Color)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Usb, contentDescription = null, tint = bannerIconTint as Color, modifier = Modifier.size(10.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = bannerTitle as String,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// =============================================================================
// COMPACT OPERATION FOOTER
// =============================================================================
@Composable
private fun CompactOperationFooter(
    progress: OperationProgress
) {
    Surface(
        color = Color(0xFF0F172A),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (progress.isRunning) progress.title else "Direct USB OTG Host Ready",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (progress.isRunning) Color(0xFF38BDF8) else Color(0xFF64748B),
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                val speedText = if (progress.speedKbPerSec > 1024) {
                    "%.2f MB/s".format(progress.speedKbPerSec / 1024.0)
                } else if (progress.speedKbPerSec > 0) {
                    "%.1f KB/s".format(progress.speedKbPerSec)
                } else {
                    "-- MB/s"
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (progress.isRunning) {
                        Text(
                            text = speedText,
                            fontSize = 8.5.sp,
                            color = Color(0xFF94A3B8),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = "${String.format("%.1f", progress.percentage)}%",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (progress.isRunning) Color(0xFF4ADE80) else Color(0xFF64748B),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            LinearProgressIndicator(
                progress = { (progress.percentage / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp)),
                color = if (progress.isRunning) Color(0xFF38BDF8) else Color(0xFF334155),
                trackColor = Color(0xFF0F172A)
            )
        }
    }
}

private fun nowTime(): String = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())