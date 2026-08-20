package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiDiagnosticAdvisor
import com.example.model.AppNavDestination
import com.example.model.BackupMode
import com.example.model.BridgeStatus
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
import com.example.model.TransportType
import com.example.parser.ScatterParser
import com.example.protocol.MtkAssetManager
import com.example.protocol.MtkBromProtocolEngine
import com.example.protocol.MtkStage1TargetCatalog
import com.example.protocol.TargetPhoneState
import com.example.protocol.TargetPhoneUsbManager
import com.example.storage.BackupStorageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MtkBridgeViewModel(application: Application) : AndroidViewModel(application) {

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    val storageManager = BackupStorageManager(application)
    val targetPhoneUsb = TargetPhoneUsbManager(application)
    private val aiAdvisor = GeminiDiagnosticAdvisor()

    // UI States
    private val _selectedTransportType = MutableStateFlow(TransportType.USB_OTG_DIRECT)
    val selectedTransportType: StateFlow<TransportType> = _selectedTransportType.asStateFlow()

    private val _bridgeStatus = MutableStateFlow(
        BridgeStatus(
            isConnected = false,
            transportType = TransportType.USB_OTG_DIRECT,
            deviceName = "Direct USB OTG Host"
        )
    )
    val bridgeStatus: StateFlow<BridgeStatus> = _bridgeStatus.asStateFlow()

    val targetPhoneState: StateFlow<TargetPhoneState> = targetPhoneUsb.phoneState

    private val _chipInfo = MutableStateFlow(MtkChipInfo())
    val chipInfo: StateFlow<MtkChipInfo> = _chipInfo.asStateFlow()

    private val _selectedBrand = MutableStateFlow<MtkBrand>(MtkDeviceDatabase.getDefaultBrand())
    val selectedBrand: StateFlow<MtkBrand> = _selectedBrand.asStateFlow()

    private val _selectedModel = MutableStateFlow<MtkDeviceModel>(MtkDeviceDatabase.getDefaultModel())
    val selectedModel: StateFlow<MtkDeviceModel> = _selectedModel.asStateFlow()

    private val _scatterPlatform = MutableStateFlow("MT6761")
    val scatterPlatform: StateFlow<String> = _scatterPlatform.asStateFlow()

    private val _partitions = MutableStateFlow<List<PartitionEntry>>(emptyList())
    val partitions: StateFlow<List<PartitionEntry>> = _partitions.asStateFlow()

    private val _selectedPartitionIndex = MutableStateFlow(2) // Defaults to nvram
    val selectedPartitionIndex: StateFlow<Int> = _selectedPartitionIndex.asStateFlow()

    private val _selectedServiceFunction = MutableStateFlow(ServiceFunction.READ_INFO)
    val selectedServiceFunction: StateFlow<ServiceFunction> = _selectedServiceFunction.asStateFlow()

    private val _currentDestination = MutableStateFlow(AppNavDestination.MTK_FLASH)
    val currentDestination: StateFlow<AppNavDestination> = _currentDestination.asStateFlow()

    private val _flashOptions = MutableStateFlow(FlashOptions())
    val flashOptions: StateFlow<FlashOptions> = _flashOptions.asStateFlow()

    private val _backupMode = MutableStateFlow(BackupMode.FULL_FIRMWARE)
    val backupMode: StateFlow<BackupMode> = _backupMode.asStateFlow()

    private val _isDryRun = MutableStateFlow(false)
    val isDryRun: StateFlow<Boolean> = _isDryRun.asStateFlow()

    private val _autoNvBackup = MutableStateFlow(true)
    val autoNvBackup: StateFlow<Boolean> = _autoNvBackup.asStateFlow()

    private val _autoReboot = MutableStateFlow(true)
    val autoReboot: StateFlow<Boolean> = _autoReboot.asStateFlow()

    private val _backupLocation = MutableStateFlow(storageManager.getBackupDirectory().absolutePath)
    val backupLocation: StateFlow<String> = _backupLocation.asStateFlow()

    private val _operationProgress = MutableStateFlow(OperationProgress())
    val operationProgress: StateFlow<OperationProgress> = _operationProgress.asStateFlow()

    private val _logs = MutableStateFlow<List<TerminalLog>>(emptyList())
    val logs: StateFlow<List<TerminalLog>> = _logs.asStateFlow()

    private val _aiAnalysis = MutableStateFlow<String?>(null)
    val aiAnalysis: StateFlow<String?> = _aiAnalysis.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    // File selection paths
    val daAgentPath = MutableStateFlow("Built-in Universal DA (MTK All-in-One)")
    val customDaPath = daAgentPath // alias
    val authFilePath = MutableStateFlow("")
    val preloaderPath = MutableStateFlow("")
    val scatterPath = MutableStateFlow("")
    val scatterFileName: StateFlow<String> = scatterPath.asStateFlow()

    private lateinit var protocolEngine: MtkBromProtocolEngine
    private var activeJob: kotlinx.coroutines.Job? = null

    init {
        protocolEngine = MtkBromProtocolEngine(
            targetPhoneUsb = targetPhoneUsb,
            storageManager = storageManager,
            logCallback = { log -> addLog(log) },
            progressCallback = { prog -> _operationProgress.value = prog }
        )

        // Start with empty partition table (professional GSM tool behavior)
        _scatterPlatform.value = "Unknown / Auto"
        _partitions.value = emptyList()

        addLog(TerminalLog(now(), "MTK Standalone USB OTG Flasher Initialized.", LogLevel.SUCCESS))
        addLog(TerminalLog(now(), "Partitions Table is empty. Connect device or load Scatter file.", LogLevel.INFO))
        addLog(TerminalLog(now(), "Backup Directory: ${_backupLocation.value}", LogLevel.INFO))

        observeTargetPhoneState()
    }

    private fun now(): String = timeFormat.format(Date())

    private fun observeTargetPhoneState() {
        viewModelScope.launch {
            var wasConnected = false
            targetPhoneUsb.phoneState.collectLatest { state ->
                when (state) {
                    is TargetPhoneState.Connected -> {
                        val isFirstConnection = !wasConnected
                        wasConnected = true
                        _bridgeStatus.value = _bridgeStatus.value.copy(
                            isConnected = true,
                            fileDescriptor = state.fileDescriptor,
                            isBromMode = state.isBromMode,
                            targetVidPid = state.vidPid,
                            deviceName = state.deviceName
                        )
                        addLog(TerminalLog(now(), "Direct USB Connected: ${state.deviceName} [${state.vidPid}] FD:${state.fileDescriptor}", LogLevel.SUCCESS))
                        if (isFirstConnection) {
                            com.example.audio.ToolSoundManager.playUsbConnected()
                        }
                    }
                    is TargetPhoneState.Disconnected -> {
                        val wasPreviouslyConnected = wasConnected
                        wasConnected = false
                        _bridgeStatus.value = _bridgeStatus.value.copy(isConnected = false, fileDescriptor = -1)
                        if (wasPreviouslyConnected) {
                            addLog(TerminalLog(now(), "Direct USB Disconnected / Removed.", LogLevel.WARNING))
                            com.example.audio.ToolSoundManager.playUsbDisconnected()
                        }
                    }
                    is TargetPhoneState.Error -> {
                        addLog(TerminalLog(now(), "USB Error: ${state.message}", LogLevel.ERROR))
                        com.example.audio.ToolSoundManager.playOperationStop()
                    }
                    is TargetPhoneState.RequestingPermission -> {
                        addLog(TerminalLog(now(), "Requesting USB OTG Permission for ${state.deviceName} [${state.mode.label} - ${state.vidPid}]...", LogLevel.WARNING))
                    }
                }
            }
        }
    }

    fun addLog(log: TerminalLog) {
        val current = _logs.value.toMutableList()
        current.add(log)
        if (current.size > 500) {
            current.removeAt(0)
        }
        _logs.value = current
    }

    fun clearLogs() {
        _logs.value = emptyList()
        addLog(TerminalLog(now(), "Terminal log cleared.", LogLevel.INFO))
    }

    fun toggleAutoReboot(enabled: Boolean) {
        _autoReboot.value = enabled
        addLog(TerminalLog(now(), "Post-Operation Auto Reboot: ${if (enabled) "ENABLED" else "DISABLED"}", LogLevel.INFO))
    }

    fun toggleAutoNvBackup(enabled: Boolean) {
        _autoNvBackup.value = enabled
        addLog(TerminalLog(now(), "Auto NV Data Backup Policy: ${if (enabled) "ENABLED (Backup will be created)" else "DISABLED (Backup will be skipped)"}", LogLevel.INFO))
    }

    fun setCustomBackupLocation(path: String) {
        storageManager.setCustomBackupPath(path)
        _backupLocation.value = storageManager.getBackupDirectory().absolutePath
        addLog(TerminalLog(now(), "Backup output path set to: ${_backupLocation.value}", LogLevel.SUCCESS))
    }

    fun setTransportType(type: TransportType) {
        if (_selectedTransportType.value == type) return
        _selectedTransportType.value = type
        if (type == TransportType.SIMULATION) {
            _isDryRun.value = true
            addLog(TerminalLog(now(), "Switched to Dry-Run / Simulation Mode", LogLevel.INFO))
        } else {
            _isDryRun.value = false
            addLog(TerminalLog(now(), "Switched to Direct USB OTG Host Mode", LogLevel.SUCCESS))
        }
    }

    fun scanTargetPhone() {
        viewModelScope.launch {
            addLog(TerminalLog(now(), "Scanning USB Host for MediaTek BROM/Preloader ports...", LogLevel.INFO))
            targetPhoneUsb.scanAndConnect()
            runBromHandshake()
        }
    }

    fun selectBrand(brand: MtkBrand) {
        _selectedBrand.value = brand
        val firstModel = brand.models.firstOrNull() ?: return
        selectModel(firstModel)
    }

    fun selectModel(model: MtkDeviceModel) {
        _selectedModel.value = model
        _scatterPlatform.value = model.chipCode
        addLog(TerminalLog(now(), "Selected Device: ${_selectedBrand.value.brandName} -> ${model.modelName} [${model.chipset}]", LogLevel.SUCCESS))
        
        val target = MtkStage1TargetCatalog.findTarget(model.chipCode)
        if (target != null) {
            addLog(TerminalLog(now(), "[HARDWARE TARGET MATCH] SoC: ${target.socName.uppercase()} | Mode: ${target.mode} | SecReg: 0x${target.secReg.toString(16)} | BlAddr: 0x${target.bladdr.toString(16)} | UART: 0x${target.uartReg0.toString(16)}", LogLevel.SUCCESS))
            addLog(TerminalLog(now(), "[EXPLOIT CONFIG] Stage1 Payload: ${target.payloadFileName} (Auto-bound)", LogLevel.INFO))
        }
        val chipConfig = com.example.protocol.MtkChipConfigDatabase.findConfigByName(model.chipCode)
        if (chipConfig != null) {
            addLog(TerminalLog(now(), "[CHIP CONFIG] HW Code: 0x${chipConfig.hwCode.toString(16).uppercase()} | WDT: 0x${chipConfig.watchdog.toString(16).uppercase()} | UART: 0x${chipConfig.uart.toString(16).uppercase()} | DA Mode: ${chipConfig.damode}", LogLevel.INFO))
        }
        addLog(TerminalLog(now(), "BROM Connection Guide: ${model.bromInstruction}", LogLevel.INFO))
    }

    fun setScatterPlatform(chipName: String) {
        _scatterPlatform.value = chipName
        addLog(TerminalLog(now(), "Target Architecture Set: $chipName", LogLevel.INFO))
    }

    fun loadScatterContent(content: String, sourceFileName: String) {
        val parsed = ScatterParser.parseScatter(content)
        _scatterPlatform.value = parsed.first
        _partitions.value = parsed.second
        scatterPath.value = sourceFileName
        if (parsed.second.isNotEmpty()) {
            addLog(TerminalLog(now(), "Successfully loaded scatter: $sourceFileName (${parsed.first} - ${parsed.second.size} Partitions)", LogLevel.SUCCESS))
        } else {
            addLog(TerminalLog(now(), "Scatter file '$sourceFileName' contains no valid partition entries.", LogLevel.WARNING))
        }
    }

    fun togglePartitionSelection(index: Int, isSelected: Boolean = true) {
        val list = _partitions.value.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(isSelectedForFlashing = isSelected)
            _partitions.value = list
        }
    }

    fun selectPartition(index: Int) {
        selectPartitionIndex(index)
    }

    fun selectAllPartitions(selectAll: Boolean) {
        val list = _partitions.value.map { it.copy(isSelectedForFlashing = selectAll) }
        _partitions.value = list
        addLog(TerminalLog(now(), if (selectAll) "Selected all partitions for flashing." else "Deselected all partitions.", LogLevel.INFO))
    }

    fun toggleSelectAllPartitions(selectAll: Boolean) {
        selectAllPartitions(selectAll)
    }

    fun selectPartitionIndex(index: Int) {
        if (index in _partitions.value.indices) {
            _selectedPartitionIndex.value = index
        }
    }

    fun selectServiceFunction(func: ServiceFunction) {
        _selectedServiceFunction.value = func
        addLog(TerminalLog(now(), "Selected service function: ${func.title}", LogLevel.INFO))
    }

    fun toggleDryRun(enabled: Boolean) {
        _isDryRun.value = enabled
        if (enabled) {
            setTransportType(TransportType.SIMULATION)
            addLog(TerminalLog(now(), "Dry-Run / Simulation Mode ENABLED. Safe testing active.", LogLevel.SUCCESS))
        } else {
            setTransportType(TransportType.USB_OTG_DIRECT)
            addLog(TerminalLog(now(), "Dry-Run Mode DISABLED. Real Direct USB OTG active.", LogLevel.WARNING))
        }
    }

    fun cancelCurrentOperation() {
        if (activeJob?.isActive == true) {
            activeJob?.cancel()
            _operationProgress.value = OperationProgress(isRunning = false, title = "Cancelled", percentage = 0f)
            addLog(TerminalLog(now(), "[ABORTED] Operation stopped by user.", LogLevel.ERROR))
            com.example.audio.ToolSoundManager.playOperationStop()
        }
    }

    fun navigateTo(destination: AppNavDestination) {
        _currentDestination.value = destination
        addLog(TerminalLog(now(), "Navigated to: ${destination.title}", LogLevel.INFO))
    }

    fun setBackupMode(mode: BackupMode) {
        _backupMode.value = mode
        addLog(TerminalLog(now(), "Selected Backup Mode: ${mode.title} (${mode.description})", LogLevel.INFO))
    }

    fun toggleFlashReadNvData(enabled: Boolean) {
        _flashOptions.value = _flashOptions.value.copy(readNvData = enabled)
        _autoNvBackup.value = enabled
        addLog(TerminalLog(now(), "Flash Action [Read NV Data]: ${if (enabled) "ENABLED" else "DISABLED"}", LogLevel.INFO))
    }

    fun toggleFlashAutoReboot(enabled: Boolean) {
        _flashOptions.value = _flashOptions.value.copy(autoReboot = enabled)
        _autoReboot.value = enabled
        addLog(TerminalLog(now(), "Flash Action [Auto Reboot]: ${if (enabled) "ENABLED" else "DISABLED"}", LogLevel.INFO))
    }

    fun toggleFlashAfterBlUnlock(enabled: Boolean) {
        _flashOptions.value = _flashOptions.value.copy(flashAfterBlUnlock = enabled)
        addLog(TerminalLog(now(), "Flash Action [Flash After BL Unlock]: ${if (enabled) "ENABLED (seccfg patch before flash)" else "DISABLED"}", LogLevel.INFO))
    }

    fun toggleFlashDaDlChecksum(enabled: Boolean) {
        _flashOptions.value = _flashOptions.value.copy(daDlChecksum = enabled)
        addLog(TerminalLog(now(), "Flash Action [DA DL Checksum]: ${if (enabled) "ENABLED (Integrity check active)" else "DISABLED"}", LogLevel.INFO))
    }

    fun toggleFlashAutoSign(enabled: Boolean) {
        _flashOptions.value = _flashOptions.value.copy(autoSignFlash = enabled)
        addLog(TerminalLog(now(), "Flash Action [Auto Sign Flash]: ${if (enabled) "ENABLED (Signature bypass active)" else "DISABLED"}", LogLevel.INFO))
    }

    fun toggleFlashFormatAll(enabled: Boolean) {
        _flashOptions.value = _flashOptions.value.copy(formatAllDownload = enabled)
        addLog(TerminalLog(now(), "Flash Action [Format All + Download]: ${if (enabled) "ENABLED (Warning: Full erase before flash)" else "DISABLED"}", LogLevel.WARNING))
    }

    fun executeFlashOperation() {
        if (_operationProgress.value.isRunning) {
            cancelCurrentOperation()
            return
        }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            val isSim = _isDryRun.value
            val chip = _scatterPlatform.value
            val parts = _partitions.value
            val opts = _flashOptions.value

            if (parts.none { it.isSelectedForFlashing }) {
                addLog(TerminalLog(now(), "No partitions selected for flashing. Please check at least one partition in the table.", LogLevel.WARNING))
                com.example.audio.ToolSoundManager.playOperationStop()
                return@launch
            }

            _operationProgress.value = OperationProgress(
                isRunning = true,
                title = "Flashing ${_selectedModel.value.modelName}",
                detail = "Initializing MTK Protocol Pipeline...",
                percentage = 0f
            )

            com.example.audio.ToolSoundManager.playOperationStart()
            addLog(TerminalLog(now(), "Starting Batch Flash for '${_selectedBrand.value.brandName} -> ${_selectedModel.value.modelName}'...", LogLevel.INFO))
            try {
                val res = protocolEngine.batchFlash(
                    chipPlatform = chip,
                    partitions = parts,
                    isSimulation = isSim,
                    autoNvBackup = opts.readNvData,
                    autoReboot = opts.autoReboot,
                    flashAfterBlUnlock = opts.flashAfterBlUnlock,
                    daDlChecksum = opts.daDlChecksum,
                    autoSignFlash = opts.autoSignFlash,
                    formatAllDownload = opts.formatAllDownload
                )
                if (res.isSuccess) {
                    com.example.audio.ToolSoundManager.playOperationDone()
                } else {
                    com.example.audio.ToolSoundManager.playOperationStop()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                com.example.audio.ToolSoundManager.playOperationStop()
            } catch (e: Exception) {
                addLog(TerminalLog(now(), "Flash Error: ${e.message}", LogLevel.ERROR))
                com.example.audio.ToolSoundManager.playOperationStop()
            } finally {
                _operationProgress.value = OperationProgress(isRunning = false, percentage = 0f)
            }
        }
    }

    fun executeBackupOperation() {
        if (_operationProgress.value.isRunning) {
            cancelCurrentOperation()
            return
        }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            val isSim = _isDryRun.value
            val chip = if (_scatterPlatform.value == "Unknown / Auto" || _scatterPlatform.value.isEmpty()) _selectedModel.value.chipCode else _scatterPlatform.value
            var parts = _partitions.value
            if (parts.isEmpty()) {
                val gptParts = protocolEngine.readDeviceGpt(isSim, chip)
                if (gptParts.isNotEmpty()) {
                    _partitions.value = gptParts
                    parts = gptParts
                } else {
                    addLog(TerminalLog(now(), "[-] [STORAGE UNINITIALIZED]: Device partition table could not be read from eMMC/UFS. DA (Download Agent) or BootROM Security Exploit required before flash memory read/write.", LogLevel.ERROR))
                    _operationProgress.value = OperationProgress(isRunning = false, percentage = 0f)
                    com.example.audio.ToolSoundManager.playOperationStop()
                    return@launch
                }
            }
            val mode = _backupMode.value
            val autoReboot = _autoReboot.value

            _operationProgress.value = OperationProgress(
                isRunning = true,
                title = "Backup: ${mode.title}",
                detail = "Connecting to device storage...",
                percentage = 0f
            )

            com.example.audio.ToolSoundManager.playOperationStart()
            addLog(TerminalLog(now(), "Initiating Backup Session: ${mode.title} for ${_selectedBrand.value.brandName} [${_selectedModel.value.modelName}]", LogLevel.INFO))

            try {
                val res: Result<*> = when (mode) {
                    BackupMode.FULL_FIRMWARE -> {
                        protocolEngine.dumpAllPartitions(chip, parts, isSim)
                    }
                    BackupMode.STABLE_FIRMWARE -> {
                        protocolEngine.dumpStablePartitions(parts, isSim)
                    }
                    BackupMode.NV_DATA -> {
                        protocolEngine.backupNvram(chip, parts, isSim)
                    }
                    BackupMode.CUSTOM_PARTITIONS -> {
                        protocolEngine.dumpCustomPartitions(parts, isSim)
                    }
                }

                if (res.isSuccess) {
                    if (autoReboot) {
                        protocolEngine.rebootDevice("Android System", isSim)
                    }
                    com.example.audio.ToolSoundManager.playOperationDone()
                } else {
                    val err = res.exceptionOrNull()?.message ?: "Backup failed"
                    addLog(TerminalLog(now(), "[-] [BACKUP FAIL]: $err. Pipeline halted.", LogLevel.ERROR))
                    com.example.audio.ToolSoundManager.playOperationStop()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                com.example.audio.ToolSoundManager.playOperationStop()
            } catch (e: Exception) {
                addLog(TerminalLog(now(), "Backup Error: ${e.message}", LogLevel.ERROR))
                com.example.audio.ToolSoundManager.playOperationStop()
            } finally {
                _operationProgress.value = OperationProgress(isRunning = false, percentage = 0f)
            }
        }
    }

    fun executeServiceFunctionDirect(func: ServiceFunction) {
        _selectedServiceFunction.value = func
        executeActiveServiceFunction()
    }

    fun runMemoryTest() {
        if (_operationProgress.value.isRunning) {
            cancelCurrentOperation()
            return
        }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _operationProgress.value = OperationProgress(
                isRunning = true,
                title = "Hardware Memory Test",
                detail = "Running RAM & Storage Diagnostic...",
                percentage = 0f
            )
            com.example.audio.ToolSoundManager.playOperationStart()
            try {
                val res = protocolEngine.runMemoryTest(_isDryRun.value)
                if (res.isSuccess) {
                    com.example.audio.ToolSoundManager.playOperationDone()
                } else {
                    com.example.audio.ToolSoundManager.playOperationStop()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                com.example.audio.ToolSoundManager.playOperationStop()
            } catch (e: Exception) {
                com.example.audio.ToolSoundManager.playOperationStop()
            } finally {
                _operationProgress.value = OperationProgress(isRunning = false, percentage = 0f)
            }
        }
    }

    fun executeActiveServiceFunction() {
        if (_operationProgress.value.isRunning) {
            cancelCurrentOperation()
            return
        }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            val func = _selectedServiceFunction.value
            val isSim = _isDryRun.value
            val chip = if (_scatterPlatform.value == "Unknown / Auto" || _scatterPlatform.value.isEmpty()) _selectedModel.value.chipCode else _scatterPlatform.value
            var parts = _partitions.value
            if (parts.isEmpty() && func != ServiceFunction.READ_INFO && func != ServiceFunction.BYPASS_AUTH && func != ServiceFunction.CRASH_TO_BROM) {
                val gptParts = protocolEngine.readDeviceGpt(isSim, chip)
                if (gptParts.isNotEmpty()) {
                    _partitions.value = gptParts
                    parts = gptParts
                } else {
                    addLog(TerminalLog(now(), "[-] [STORAGE UNINITIALIZED]: Device partition table could not be read. DA or Auth Bypass required for ${func.title}.", LogLevel.ERROR))
                    _operationProgress.value = OperationProgress(isRunning = false, percentage = 0f)
                    com.example.audio.ToolSoundManager.playOperationStop()
                    return@launch
                }
            }
            val autoReboot = _autoReboot.value
            val autoNvBackup = _autoNvBackup.value

            _operationProgress.value = OperationProgress(
                isRunning = true,
                title = func.title,
                detail = "Executing ${func.title}...",
                percentage = 0f
            )

            com.example.audio.ToolSoundManager.playOperationStart()
            try {
                val res: Result<*>? = when (func) {
                    ServiceFunction.READ_INFO -> {
                        protocolEngine.executeBromHandshake(isSim)
                    }
                    ServiceFunction.WRITE_PARTITION -> {
                        val part = parts.getOrNull(_selectedPartitionIndex.value)
                        if (part != null) {
                            protocolEngine.writePartition(part, null, isSim, autoNvBackup, autoReboot)
                        } else {
                            addLog(TerminalLog(now(), "Please select a valid partition to write.", LogLevel.ERROR))
                            Result.failure<Boolean>(IllegalArgumentException("No partition selected"))
                        }
                    }
                    ServiceFunction.BATCH_FLASH -> {
                        executeFlashOperation()
                        null
                    }
                    ServiceFunction.READ_PARTITION -> {
                        val part = parts.getOrNull(_selectedPartitionIndex.value)
                        if (part != null) {
                            protocolEngine.readPartition(part, isSim)
                        } else {
                            addLog(TerminalLog(now(), "Please select a valid partition to read.", LogLevel.ERROR))
                            Result.failure<String>(IllegalArgumentException("No partition selected"))
                        }
                    }
                    ServiceFunction.DUMP_ALL_PARTITIONS -> {
                        protocolEngine.dumpAllPartitions(chip, parts, isSim)
                    }
                    ServiceFunction.DUMP_STABLE_PARTITIONS -> {
                        protocolEngine.dumpStablePartitions(parts, isSim)
                    }
                    ServiceFunction.READ_PRELOADER -> {
                        protocolEngine.readPreloader(isSim)
                    }
                    ServiceFunction.READ_GPT_SCATTER -> {
                        protocolEngine.readGptAndGenerateScatter(chip, parts, isSim)
                    }
                    ServiceFunction.READ_RPMB -> {
                        protocolEngine.readRpmb(isSim)
                    }
                    ServiceFunction.BACKUP_NVRAM -> {
                        protocolEngine.backupNvram(chip, parts, isSim)
                    }
                    ServiceFunction.RESTORE_NVRAM -> {
                        addLog(TerminalLog(now(), "Restoring saved NV calibration archive...", LogLevel.INFO))
                        val nvPart = parts.find { it.partitionName.lowercase() == "nvdata" } ?: parts.getOrNull(2)
                        if (nvPart != null) {
                            protocolEngine.writePartition(nvPart, null, isSim, autoNvBackup = false, autoReboot = autoReboot)
                        } else {
                            Result.failure<Boolean>(IllegalStateException("No NV partition found"))
                        }
                    }
                    ServiceFunction.BYPASS_AUTH -> {
                        protocolEngine.bypassAuth(isSim)
                    }
                    ServiceFunction.UNLOCK_BOOTLOADER -> {
                        protocolEngine.unlockBootloader(parts, isSim, autoReboot)
                    }
                    ServiceFunction.LOCK_BOOTLOADER -> {
                        protocolEngine.lockBootloader(parts, isSim, autoReboot)
                    }
                    ServiceFunction.ERASE_FRP -> {
                        protocolEngine.eraseFrp(chip, parts, isSim, autoNvBackup, autoReboot)
                    }
                    ServiceFunction.FACTORY_RESET -> {
                        protocolEngine.factoryReset(chip, parts, isSim, autoNvBackup, autoReboot)
                    }
                    ServiceFunction.DISABLE_MI_ACCOUNT -> {
                        protocolEngine.disableMiAccount(chip, parts, isSim, autoNvBackup, autoReboot)
                    }
                    ServiceFunction.MEMORY_TEST -> {
                        protocolEngine.runMemoryTest(isSim)
                    }
                    ServiceFunction.FORMAT_PARTITION -> {
                        val part = parts.getOrNull(_selectedPartitionIndex.value)
                        if (part != null) {
                            protocolEngine.formatPartition(chip, part, parts, isSim, autoNvBackup, autoReboot)
                        } else {
                            Result.failure<Boolean>(IllegalArgumentException("No partition selected"))
                        }
                    }
                    ServiceFunction.CRASH_TO_BROM -> {
                        protocolEngine.crashToBrom(isSim)
                    }
                    ServiceFunction.REBOOT_SYSTEM -> {
                        protocolEngine.rebootDevice("Android System", isSim)
                    }
                    ServiceFunction.REBOOT_FASTBOOT -> {
                        protocolEngine.rebootDevice("Fastboot Mode", isSim)
                    }
                    ServiceFunction.REBOOT_RECOVERY -> {
                        protocolEngine.rebootDevice("Recovery Mode", isSim)
                    }
                }

                if (res != null) {
                    if (res.isSuccess) {
                        com.example.audio.ToolSoundManager.playOperationDone()
                    } else {
                        val err = res.exceptionOrNull()?.message ?: "Operation failed"
                        addLog(TerminalLog(now(), "[-] [SERVICE FAIL]: ${func.title} failed: $err. Pipeline halted.", LogLevel.ERROR))
                        com.example.audio.ToolSoundManager.playOperationStop()
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                com.example.audio.ToolSoundManager.playOperationStop()
            } catch (e: Exception) {
                addLog(TerminalLog(now(), "Service Error: ${e.message}", LogLevel.ERROR))
                com.example.audio.ToolSoundManager.playOperationStop()
            } finally {
                _operationProgress.value = OperationProgress(isRunning = false, percentage = 0f)
            }
        }
    }

    fun batchFlashSelectedPartitions() {
        viewModelScope.launch {
            protocolEngine.batchFlash(_scatterPlatform.value, _partitions.value, _isDryRun.value, _autoNvBackup.value, _autoReboot.value)
        }
    }

    fun runBromHandshake() {
        viewModelScope.launch {
            val result = protocolEngine.executeBromHandshake(_isDryRun.value)
            if (result.isSuccess) {
                val info = result.getOrNull()!!
                _chipInfo.value = info
                if (_scatterPlatform.value == "Unknown / Auto" || _scatterPlatform.value.isEmpty()) {
                    _scatterPlatform.value = info.chipIdHex
                }
                protocolEngine.validateChipMatch(info, _scatterPlatform.value)

                // Read live device GPT from phone storage dynamically
                val liveGpt = protocolEngine.readDeviceGpt(_isDryRun.value, info.chipIdHex)
                if (liveGpt.isNotEmpty()) {
                    _partitions.value = liveGpt
                    addLog(TerminalLog(now(), "Live Storage GPT Loaded into Partitions Table (${liveGpt.size} Partitions).", LogLevel.SUCCESS))
                }
            }
        }
    }

    fun sendWatchdogReset() {
        viewModelScope.launch {
            addLog(TerminalLog(now(), "Sending USB Control Transfer Watchdog Reset...", LogLevel.INFO))
            if (!_isDryRun.value && targetPhoneUsb.isConnected()) {
                val ok = targetPhoneUsb.sendWatchdogResetControl()
                addLog(TerminalLog(now(), "USB Control Transfer Reset: ${if (ok) "SUCCESS" else "SENT"}", LogLevel.SUCCESS))
            } else {
                addLog(TerminalLog(now(), "Simulated USB Watchdog Reset Triggered.", LogLevel.SUCCESS))
            }
        }
    }

    // ADB & Fastboot state
    private val _adbDeviceInfo = MutableStateFlow<String>("")
    val adbDeviceInfo: StateFlow<String> = _adbDeviceInfo.asStateFlow()

    private val _fastbootDeviceInfo = MutableStateFlow<String>("")
    val fastbootDeviceInfo: StateFlow<String> = _fastbootDeviceInfo.asStateFlow()

    private val _isAdbBusy = MutableStateFlow(false)
    val isAdbBusy: StateFlow<Boolean> = _isAdbBusy.asStateFlow()

    private val _isFastbootBusy = MutableStateFlow(false)
    val isFastbootBusy: StateFlow<Boolean> = _isFastbootBusy.asStateFlow()

    fun runAdbCommand(label: String, command: String, onComplete: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            _isAdbBusy.value = true
            addLog(TerminalLog(now(), ">>> [ADB CMD] $label: adb shell \"$command\"", LogLevel.INFO))
            val dev = targetPhoneUsb.currentDevice
            if (dev == null && !_isDryRun.value) {
                addLog(TerminalLog(now(), "[-] ADB Error: No USB Device connected. Please connect with USB Debugging enabled.", LogLevel.ERROR))
                _isAdbBusy.value = false
                return@launch
            }

            if (_isDryRun.value || dev == null) {
                // Dry run response
                kotlinx.coroutines.delay(600)
                val mockOutput = when {
                    command.contains("getprop ro.product.model") -> "Redmi Note 12 Pro (MT6877)"
                    command.contains("getprop") -> "[ro.product.model]: [Redmi Note 12 Pro]\n[ro.build.version.release]: [13]\n[ro.build.version.security_patch]: [2024-05-01]\n[ro.board.platform]: [mt6877]"
                    command.contains("reboot bootloader") -> "Rebooting target device into Bootloader (Fastboot)..."
                    command.contains("reboot recovery") -> "Rebooting target device into Recovery..."
                    command.contains("reboot") -> "Device reboot signal sent."
                    command.contains("settings put global setup_wizard_has_run") -> "FRP setup wizard flags bypassed."
                    else -> "Success: Command executed."
                }
                addLog(TerminalLog(now(), "[ADB Response]\n$mockOutput", LogLevel.SUCCESS))
                onComplete?.invoke(mockOutput)
                _isAdbBusy.value = false
                return@launch
            }

            val client = com.example.protocol.AdbProtocolClient(targetPhoneUsb.usbManager, dev)
            val opened = client.open()
            if (!opened) {
                addLog(TerminalLog(now(), "[-] ADB Error: Failed to open USB ADB Interface (Ensure USB Debugging is ON).", LogLevel.ERROR))
                _isAdbBusy.value = false
                return@launch
            }

            val connected = client.connect()
            if (!connected) {
                addLog(TerminalLog(now(), "[!] ADB Warning: ADB Handshake not accepted. Check phone screen for Authorization prompt.", LogLevel.WARNING))
            }

            val output = client.executeShell(command)
            client.close()

            if (output.isNotBlank()) {
                addLog(TerminalLog(now(), "[ADB Response]\n$output", LogLevel.SUCCESS))
                onComplete?.invoke(output)
            } else {
                addLog(TerminalLog(now(), "[+] ADB Command executed successfully.", LogLevel.SUCCESS))
                onComplete?.invoke("OK")
            }
            _isAdbBusy.value = false
        }
    }

    fun runAdbReadInfo() {
        runAdbCommand("Read Device Info", "getprop ro.product.model && getprop ro.product.brand && getprop ro.build.version.release && getprop ro.build.version.security_patch && getprop ro.board.platform") { res ->
            _adbDeviceInfo.value = res
        }
    }

    fun runAdbReboot(mode: String) {
        val cmd = when (mode.lowercase()) {
            "fastboot", "bootloader" -> "reboot bootloader"
            "recovery" -> "reboot recovery"
            "edl", "brom" -> "reboot edl || reboot brom"
            else -> "reboot"
        }
        runAdbCommand("Reboot to ${mode.uppercase()}", cmd)
    }

    fun runAdbBypassFrp() {
        runAdbCommand(
            "Bypass Setup Wizard (FRP)",
            "settings put global setup_wizard_has_run 1 && settings put secure user_setup_complete 1 && settings put global device_provisioned 1 && am start -c android.intent.category.HOME -a android.intent.action.MAIN"
        )
    }

    fun runAdbEnableLanguages() {
        runAdbCommand("Enable All Languages", "pm grant jp.co.c_lis.ccl.morelocale android.permission.CHANGE_CONFIGURATION")
    }

    fun runAdbRemoveBloatware(packageNames: List<String>) {
        val cmds = packageNames.joinToString(" && ") { "pm uninstall -k --user 0 $it" }
        runAdbCommand("Remove Bloatware (${packageNames.size} apps)", cmds)
    }

    fun runFastbootCommand(label: String, command: String, onComplete: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            _isFastbootBusy.value = true
            addLog(TerminalLog(now(), ">>> [FASTBOOT CMD] $label: fastboot $command", LogLevel.INFO))
            val dev = targetPhoneUsb.currentDevice
            if (dev == null && !_isDryRun.value) {
                addLog(TerminalLog(now(), "[-] Fastboot Error: No USB Device connected in Fastboot mode.", LogLevel.ERROR))
                _isFastbootBusy.value = false
                return@launch
            }

            if (_isDryRun.value || dev == null) {
                kotlinx.coroutines.delay(600)
                val mockResult = when {
                    command.contains("getvar:all") || command.contains("getvar all") -> 
                        "product: ruby_pro\nversion-bootloader: MT6877_V1.0\nsecure: yes\nunlocked: no\noff-mode-charge: 1\ncharger-screen-enabled: 1\nbattery-voltage: 4120mV"
                    command.contains("unlock") -> "OKAY [ 0.054s ]\nUnlocked bootloader successfully."
                    command.contains("lock") -> "OKAY [ 0.048s ]\nLocked bootloader successfully."
                    command.contains("erase frp") -> "Erasing 'frp' ... OKAY [ 0.012s ]\nFinished."
                    command.contains("erase userdata") -> "Erasing 'userdata' ... OKAY [ 0.231s ]\nFinished."
                    command.contains("reboot") -> "Rebooting device ... OKAY"
                    else -> "OKAY [ 0.020s ]"
                }
                addLog(TerminalLog(now(), "[Fastboot Output]\n$mockResult", LogLevel.SUCCESS))
                onComplete?.invoke(mockResult)
                _isFastbootBusy.value = false
                return@launch
            }

            val client = com.example.protocol.FastbootProtocolClient(targetPhoneUsb.usbManager, dev)
            val opened = client.open()
            if (!opened) {
                addLog(TerminalLog(now(), "[-] Fastboot Error: Failed to claim USB Fastboot Interface.", LogLevel.ERROR))
                _isFastbootBusy.value = false
                return@launch
            }

            val res = client.executeCommand(command)
            client.close()

            if (res.isSuccess) {
                val output = res.info.ifEmpty { "OKAY" }
                addLog(TerminalLog(now(), "[Fastboot Output]\n$output", LogLevel.SUCCESS))
                onComplete?.invoke(output)
            } else {
                val err = res.error.ifEmpty { res.info.ifEmpty { "Command failed" } }
                addLog(TerminalLog(now(), "[-] Fastboot Failed: $err", LogLevel.ERROR))
                onComplete?.invoke("ERROR: $err")
            }
            _isFastbootBusy.value = false
        }
    }

    fun runFastbootReadAllVars() {
        runFastbootCommand("Get All Variables", "getvar:all") { out ->
            _fastbootDeviceInfo.value = out
        }
    }

    fun runFastbootUnlockBootloader() {
        runFastbootCommand("Flashing Unlock", "flashing unlock")
    }

    fun runFastbootLockBootloader() {
        runFastbootCommand("Flashing Lock", "flashing lock")
    }

    fun runFastbootEraseFrp() {
        runFastbootCommand("Erase FRP Partition", "erase:frp")
    }

    fun runFastbootFormatUserdata() {
        runFastbootCommand("Format Userdata (Wipe)", "erase:userdata")
    }

    fun runFastbootReboot(mode: String) {
        val cmd = when (mode.lowercase()) {
            "recovery" -> "reboot-recovery"
            "fastbootd" -> "reboot-fastboot"
            "edl" -> "oem edl"
            "bootloader" -> "reboot-bootloader"
            else -> "reboot"
        }
        runFastbootCommand("Reboot to ${mode.uppercase()}", cmd)
    }

    fun requestAiDiagnosis() {
        viewModelScope.launch {
            _isAiLoading.value = true
            addLog(TerminalLog(now(), "Requesting Gemini AI Diagnostic Analysis...", LogLevel.INFO))
            val recentLogs = _logs.value.takeLast(20).joinToString("\n") { "[${it.timestamp}] ${it.message}" }
            val selectedPart = _partitions.value.getOrNull(_selectedPartitionIndex.value)?.partitionName ?: "nvram"
            val diagnosis = aiAdvisor.analyzeMtkLogsAndSuggestFix(
                chipInfo = _chipInfo.value.chipIdHex,
                scatterPlatform = _scatterPlatform.value,
                recentLogs = recentLogs,
                selectedPartition = selectedPart
            )
            _aiAnalysis.value = diagnosis
            _isAiLoading.value = false
            addLog(TerminalLog(now(), "Gemini AI Diagnosis received.", LogLevel.AI))
        }
    }

    fun requestAiDiagnostics() {
        requestAiDiagnosis()
    }

    fun requestAiLogAnalysis() {
        requestAiDiagnosis()
    }

    fun toggleAllPartitions(selectAll: Boolean) {
        selectAllPartitions(selectAll)
    }

    fun dismissAiSheet() {
        _aiAnalysis.value = null
    }
}
