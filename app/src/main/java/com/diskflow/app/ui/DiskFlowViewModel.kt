package com.diskflow.app.ui

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.diskflow.app.data.format.FormatCapabilities
import com.diskflow.app.data.format.FormatEngine
import com.diskflow.app.data.format.FormatOutcome
import com.diskflow.app.data.local.DiskFlowStore
import com.diskflow.app.data.storage.StorageRepository
import com.diskflow.app.domain.FormatPolicy
import com.diskflow.app.domain.model.FileSizeCheck
import com.diskflow.app.domain.model.FileSystemType
import com.diskflow.app.domain.model.FormatPlan
import com.diskflow.app.domain.model.OperationEvent
import com.diskflow.app.domain.model.OperationEventType
import com.diskflow.app.domain.model.StorageDevice
import com.diskflow.app.domain.model.StorageSnapshot
import com.diskflow.app.domain.model.StorageUseCase
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class WizardStage {
    CONFIGURING,
    READY,
}

data class DevicesUiState(
    val isRefreshing: Boolean = true,
    val snapshot: StorageSnapshot = StorageSnapshot(),
    val usbRequests: Set<Int> = emptySet(),
)

sealed interface FormatExecution {
    data object Idle : FormatExecution
    data class Running(val step: String) : FormatExecution
    data class Finished(val success: Boolean, val message: String) : FormatExecution
}

data class FormatUiState(
    val plan: FormatPlan = FormatPlan(),
    val stage: WizardStage = WizardStage.CONFIGURING,
    val isRecording: Boolean = false,
    val completionMarked: Boolean = false,
    val capabilities: FormatCapabilities? = null,
    val execution: FormatExecution = FormatExecution.Idle,
) {
    val labelError: String?
        get() = FormatPolicy.validateVolumeLabel(plan.fileSystem, plan.volumeLabel).errorMessage

    val isExecuting: Boolean get() = execution is FormatExecution.Running
}

data class ToolsUiState(
    val fileSystem: FileSystemType = FileSystemType.EXFAT,
    val fileSizeInput: String = "4",
    val fileSizeUnit: FileSizeUnit = FileSizeUnit.GIB,
)

enum class FileSizeUnit(val displayName: String, val multiplier: Long) {
    KIB("KiB", 1024L),
    MIB("MiB", 1024L * 1024L),
    GIB("GiB", 1024L * 1024L * 1024L),
}

data class UiMessage(
    val id: Long,
    val text: String,
    val isError: Boolean = false,
)

class DiskFlowViewModel(
    private val storageRepository: StorageRepository,
    private val dataStore: DiskFlowStore,
    private val formatEngine: FormatEngine,
) : ViewModel() {
    private val refreshMutex = Mutex()

    private val _devices = MutableStateFlow(DevicesUiState())
    val devices: StateFlow<DevicesUiState> = _devices.asStateFlow()

    private val _format = MutableStateFlow(FormatUiState())
    val format: StateFlow<FormatUiState> = _format.asStateFlow()

    private val _tools = MutableStateFlow(ToolsUiState())
    val tools: StateFlow<ToolsUiState> = _tools.asStateFlow()

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    val events: StateFlow<List<OperationEvent>> = dataStore.events.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        emptyList(),
    )

    val authorizedTreeUri: StateFlow<String?> = dataStore.authorizedTreeUri.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        null,
    )

    init {
        viewModelScope.launch {
            dataStore.draft.first()?.let { restored ->
                _format.update { it.copy(plan = restored) }
            }
            refreshDevices()
            refreshCapabilities()
        }
    }

    fun refreshCapabilities() {
        viewModelScope.launch {
            val capabilities = runCatching { formatEngine.probe() }.getOrElse {
                FormatCapabilities(hasRoot = false, message = "无法检测 Root 状态：${it.message ?: "未知错误"}")
            }
            _format.update { it.copy(capabilities = capabilities) }
        }
    }

    fun executeFormat() {
        val state = _format.value
        if (state.isExecuting) return
        val device = _devices.value.snapshot.devices.firstOrNull { it.id == state.plan.deviceId }
        when {
            device == null -> notify("目标设备未连接，请刷新后重试", isError = true)
            state.labelError != null -> notify(state.labelError!!, isError = true)
            state.capabilities?.hasRoot != true -> notify("未获得 Root 权限，无法直接格式化", isError = true)
            else -> viewModelScope.launch {
                _format.update { it.copy(execution = FormatExecution.Running("准备中")) }
                val outcome = runCatching {
                    formatEngine.format(device, state.plan) { step ->
                        _format.update { it.copy(execution = FormatExecution.Running(step)) }
                    }
                }.getOrElse {
                    FormatOutcome(false, "执行失败：${it.message ?: "未知错误"}")
                }
                _format.update {
                    it.copy(execution = FormatExecution.Finished(outcome.success, outcome.message))
                }
                runCatching {
                    dataStore.addEvent(
                        state.plan,
                        if (outcome.success) {
                            OperationEventType.FORMAT_SUCCEEDED
                        } else {
                            OperationEventType.FORMAT_FAILED
                        },
                        outcome.message,
                    )
                }
                refreshDevices()
                notify(outcome.message, isError = !outcome.success)
            }
        }
    }

    fun refreshDevices() {
        viewModelScope.launch {
            refreshMutex.withLock {
                _devices.update { it.copy(isRefreshing = true) }
                val snapshot = runCatching { storageRepository.scan() }.getOrElse {
                    StorageSnapshot(warning = "设备扫描失败，请稍后重试")
                }
                _devices.update { it.copy(isRefreshing = false, snapshot = snapshot) }
                selectFirstAvailableExternalDevice(snapshot.devices)
            }
        }
    }

    fun selectDevice(device: StorageDevice) {
        updatePlan { copy(deviceId = device.id, deviceName = device.name) }
    }

    fun selectUseCase(useCase: StorageUseCase) {
        updatePlan {
            copy(
                useCase = useCase,
                fileSystem = FormatPolicy.recommend(useCase, legacySupport),
                allocationUnitKiB = 0,
            )
        }
    }

    fun setLegacySupport(enabled: Boolean) {
        updatePlan {
            copy(
                legacySupport = enabled,
                fileSystem = FormatPolicy.recommend(useCase, enabled),
                allocationUnitKiB = 0,
            )
        }
    }

    fun selectFileSystem(type: FileSystemType) {
        updatePlan { copy(fileSystem = type, allocationUnitKiB = 0) }
    }

    fun selectAllocationUnit(sizeKiB: Int) {
        updatePlan { copy(allocationUnitKiB = sizeKiB) }
    }

    fun updateVolumeLabel(label: String) {
        updatePlan { copy(volumeLabel = label) }
    }

    fun createPlan() {
        val state = _format.value
        val deviceConnected = _devices.value.snapshot.devices.any {
            it.id == state.plan.deviceId && it.isExternal
        }
        val labelError = state.labelError
        when {
            !deviceConnected -> notify("请先连接并选择目标外置存储设备", isError = true)
            labelError != null -> notify(labelError, isError = true)
            else -> viewModelScope.launch {
                _format.update { it.copy(isRecording = true) }
                runCatching {
                    dataStore.saveDraft(state.plan)
                    dataStore.addEvent(state.plan, OperationEventType.PLAN_CREATED)
                }.onSuccess {
                    _format.update {
                        it.copy(
                            stage = WizardStage.READY,
                            completionMarked = false,
                            execution = FormatExecution.Idle,
                        )
                    }
                }.onFailure {
                    notify("方案保存失败，请重试", isError = true)
                }
                _format.update { it.copy(isRecording = false) }
            }
        }
    }

    fun backToConfiguration() {
        _format.update {
            it.copy(
                stage = WizardStage.CONFIGURING,
                completionMarked = false,
                execution = FormatExecution.Idle,
            )
        }
    }

    fun cancelPlan() {
        val plan = _format.value.plan
        viewModelScope.launch {
            runCatching { dataStore.addEvent(plan, OperationEventType.CANCELLED) }
            _format.update {
                it.copy(
                    stage = WizardStage.CONFIGURING,
                    completionMarked = false,
                    execution = FormatExecution.Idle,
                )
            }
            notify("已取消执行指引，未修改存储设备")
        }
    }

    fun openSystemStorageSettings() {
        val plan = _format.value.plan
        if (storageRepository.openSystemStorageSettings()) {
            viewModelScope.launch {
                runCatching {
                    dataStore.addEvent(plan, OperationEventType.SYSTEM_SETTINGS_OPENED)
                }
            }
            notify("已跳转系统设置。格式化是否可用及实际结果以系统提示为准")
        } else {
            notify("无法打开系统存储设置，请在系统设置中手动搜索“存储”", isError = true)
        }
    }

    fun markCompletedByUser() {
        if (_format.value.completionMarked) return
        val plan = _format.value.plan
        viewModelScope.launch {
            runCatching {
                dataStore.addEvent(plan, OperationEventType.USER_MARKED_COMPLETE)
            }.onSuccess {
                _format.update { it.copy(completionMarked = true) }
                notify("已记录你的确认；DiskFlow 未检测或验证系统格式化结果")
            }.onFailure {
                notify("记录保存失败，请重试", isError = true)
            }
        }
    }

    fun deleteEvent(id: String) {
        viewModelScope.launch {
            runCatching { dataStore.deleteEvent(id) }
                .onFailure { notify("删除失败，请重试", isError = true) }
        }
    }

    fun clearEvents() {
        viewModelScope.launch {
            runCatching { dataStore.clearEvents() }
                .onSuccess { notify("记录已清空") }
                .onFailure { notify("清空失败，请重试", isError = true) }
        }
    }

    fun requestUsbPermission(deviceId: Int) {
        if (deviceId in _devices.value.usbRequests) return
        _devices.update { it.copy(usbRequests = it.usbRequests + deviceId) }
        viewModelScope.launch {
            val granted = runCatching { storageRepository.requestUsbPermission(deviceId) }
                .getOrDefault(false)
            _devices.update { it.copy(usbRequests = it.usbRequests - deviceId) }
            refreshDevices()
            notify(if (granted) "USB 权限已授予" else "未获得 USB 权限，仍可使用系统存储信息")
        }
    }

    fun createOpenDocumentTreeIntent(deviceId: String?): Intent =
        storageRepository.createOpenDocumentTreeIntent(deviceId)

    fun onTreeSelected(uri: Uri?) {
        if (uri == null) {
            notify("未选择存储目录")
            return
        }
        viewModelScope.launch {
            val persisted = runCatching { storageRepository.persistTreePermission(uri) }
                .getOrDefault(false)
            if (persisted) {
                runCatching { dataStore.saveAuthorizedTreeUri(uri.toString()) }
                    .onSuccess { notify("存储目录授权已保存") }
                    .onFailure { notify("目录已授权，但状态保存失败", isError = true) }
            } else {
                notify("系统未允许持久保存此目录授权", isError = true)
            }
        }
    }

    fun onTreeSelectionFailed() {
        notify("无法打开系统目录选择器", isError = true)
    }

    fun openAuthorizedDirectory() {
        val value = authorizedTreeUri.value ?: return
        val opened = runCatching { storageRepository.openAuthorizedDirectory(Uri.parse(value)) }
            .getOrDefault(false)
        if (!opened) notify("未找到可打开此目录的文件管理器", isError = true)
    }

    fun removeAuthorizedDirectory() {
        val value = authorizedTreeUri.value
        viewModelScope.launch {
            if (value != null) {
                runCatching { storageRepository.releaseTreePermission(Uri.parse(value)) }
            }
            runCatching { dataStore.clearAuthorizedTreeUri() }
                .onSuccess { notify("已移除保存的目录授权") }
                .onFailure { notify("授权状态更新失败，请重试", isError = true) }
        }
    }

    fun selectToolFileSystem(type: FileSystemType) {
        _tools.update { it.copy(fileSystem = type) }
    }

    fun updateFileSizeInput(value: String) {
        _tools.update { it.copy(fileSizeInput = value) }
    }

    fun selectFileSizeUnit(unit: FileSizeUnit) {
        _tools.update { it.copy(fileSizeUnit = unit) }
    }

    fun fileSizeCheck(): FileSizeCheck {
        val state = _tools.value
        val size = state.fileSizeInput.toBigDecimalOrNull()
        if (size == null || size < BigDecimal.ZERO) {
            return FileSizeCheck(false, "请输入有效的非负文件大小")
        }
        val bytes = runCatching {
            size.multiply(state.fileSizeUnit.multiplier.toBigDecimal()).longValueExact()
        }.getOrNull() ?: return FileSizeCheck(false, "输入的数值过大")
        return FormatPolicy.checkFileSize(state.fileSystem, bytes)
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun updatePlan(transform: FormatPlan.() -> FormatPlan) {
        _format.update { state -> state.copy(plan = state.plan.transform()) }
        val plan = _format.value.plan
        viewModelScope.launch {
            runCatching { dataStore.saveDraft(plan) }
        }
    }

    private fun selectFirstAvailableExternalDevice(devices: List<StorageDevice>) {
        val externalDevices = devices.filter { it.isExternal }
        _format.update { state ->
            val stillConnected = externalDevices.any { it.id == state.plan.deviceId }
            if (stillConnected) {
                state
            } else {
                val first = externalDevices.firstOrNull()
                state.copy(
                    plan = state.plan.copy(
                        deviceId = first?.id.orEmpty(),
                        deviceName = first?.name.orEmpty(),
                    ),
                )
            }
        }
    }

    private fun notify(text: String, isError: Boolean = false) {
        _message.value = UiMessage(System.nanoTime(), text, isError)
    }

    class Factory(
        private val storageRepository: StorageRepository,
        private val dataStore: DiskFlowStore,
        private val formatEngine: FormatEngine,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DiskFlowViewModel::class.java))
            return DiskFlowViewModel(storageRepository, dataStore, formatEngine) as T
        }
    }
}
