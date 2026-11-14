package com.diskflow.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.diskflow.app.domain.FormatPolicy
import com.diskflow.app.domain.model.FileSystemType
import com.diskflow.app.domain.model.StorageDevice
import com.diskflow.app.domain.model.StorageUseCase
import com.diskflow.app.ui.FormatExecution
import com.diskflow.app.ui.FormatUiState
import com.diskflow.app.ui.WizardStage
import com.diskflow.app.ui.components.MetadataRow
import com.diskflow.app.ui.components.NoticeCard
import com.diskflow.app.ui.components.NoticeTone
import com.diskflow.app.ui.components.PageHeader
import com.diskflow.app.ui.components.SectionTitle
import com.diskflow.app.ui.components.StatusPill
import com.diskflow.app.ui.components.TechCard
import com.diskflow.app.ui.components.volumeIcon
import com.diskflow.app.ui.components.volumeKindLabel
import com.diskflow.app.ui.theme.Error
import com.diskflow.app.ui.theme.Mint
import com.diskflow.app.ui.theme.TextSecondary

@Composable
fun FormatScreen(
    state: FormatUiState,
    devices: List<StorageDevice>,
    onSelectDevice: (StorageDevice) -> Unit,
    onSelectUseCase: (StorageUseCase) -> Unit,
    onLegacySupportChange: (Boolean) -> Unit,
    onSelectFileSystem: (FileSystemType) -> Unit,
    onVolumeLabelChange: (String) -> Unit,
    onSelectAllocationUnit: (Int) -> Unit,
    onCreatePlan: () -> Unit,
    onBackToConfiguration: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onMarkCompleted: () -> Unit,
    onCancel: () -> Unit,
    onExecuteFormat: () -> Unit,
    onRefreshCapabilities: () -> Unit,
) {
    AnimatedContent(
        targetState = state.stage,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "format-stage",
    ) { stage ->
        if (stage == WizardStage.CONFIGURING) {
            FormatConfiguration(
                state = state,
                devices = devices,
                onSelectDevice = onSelectDevice,
                onSelectUseCase = onSelectUseCase,
                onLegacySupportChange = onLegacySupportChange,
                onSelectFileSystem = onSelectFileSystem,
                onVolumeLabelChange = onVolumeLabelChange,
                onSelectAllocationUnit = onSelectAllocationUnit,
                onCreatePlan = onCreatePlan,
            )
        } else {
            FormatGuide(
                state = state,
                targetConnected = devices.any { it.id == state.plan.deviceId && it.isExternal },
                onBack = onBackToConfiguration,
                onOpenSystemSettings = onOpenSystemSettings,
                onMarkCompleted = onMarkCompleted,
                onCancel = onCancel,
                onExecuteFormat = onExecuteFormat,
                onRefreshCapabilities = onRefreshCapabilities,
            )
        }
    }
}

@Composable
private fun FormatConfiguration(
    state: FormatUiState,
    devices: List<StorageDevice>,
    onSelectDevice: (StorageDevice) -> Unit,
    onSelectUseCase: (StorageUseCase) -> Unit,
    onLegacySupportChange: (Boolean) -> Unit,
    onSelectFileSystem: (FileSystemType) -> Unit,
    onVolumeLabelChange: (String) -> Unit,
    onSelectAllocationUnit: (Int) -> Unit,
    onCreatePlan: () -> Unit,
) {
    val externalDevices = devices.filter { it.isExternal }
    val plan = state.plan
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
    ) {
        item {
            PageHeader(
                eyebrow = "Format planner",
                title = "格式化方案",
                subtitle = "先选对文件系统，再交给系统安全执行",
            )
        }
        item {
            NoticeCard(
                title = "两种执行方式",
                message = "获得 Root 时可由 DiskFlow 直接调用 mkfs 写入文件系统；未获得 Root 时仅提供方案与系统入口，不直接修改分区。",
                icon = Icons.Rounded.Shield,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item { SectionTitle("1", "选择目标设备") }
        if (externalDevices.isEmpty()) {
            item {
                NoticeCard(
                    title = "尚未连接外置存储",
                    message = "你仍可浏览下面的格式知识；连接 U 盘或 SD 卡并刷新后才能生成方案。",
                    tone = NoticeTone.WARNING,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
        items(externalDevices, key = { it.id }) { device ->
            DeviceChoice(
                device = device,
                selected = device.id == plan.deviceId,
                onClick = { onSelectDevice(device) },
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .animateItem(),
            )
        }

        item {
            Spacer(Modifier.height(4.dp))
            SectionTitle("2", "主要用途")
        }
        item {
            Column(
                Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StorageUseCase.entries.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowItems.forEach { useCase ->
                            UseCaseChoice(
                                useCase = useCase,
                                selected = useCase == plan.useCase,
                                onClick = { onSelectUseCase(useCase) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
        item {
            AnimatedVisibility(
                visible = plan.useCase == StorageUseCase.CROSS_PLATFORM ||
                    plan.useCase == StorageUseCase.MEDIA,
            ) {
                TechCard(Modifier.padding(horizontal = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("兼容相机、车载等老设备", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "开启后优先推荐 FAT32；超过 4 GiB 的单个文件无法存入",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = plan.legacySupport, onCheckedChange = onLegacySupportChange)
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            SectionTitle("3", "文件系统")
        }
        item {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                FileSystemType.entries.forEach { type ->
                    FilterChip(
                        selected = type == plan.fileSystem,
                        onClick = { onSelectFileSystem(type) },
                        label = { Text(type.displayName) },
                        leadingIcon = if (type == plan.fileSystem) {
                            { Icon(Icons.Rounded.Check, null, modifier = Modifier.size(17.dp)) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
        item {
            TechCard(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        plan.fileSystem.displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Mint,
                        modifier = Modifier.weight(1f),
                    )
                    StatusPill(
                        if (plan.fileSystem == FormatPolicy.recommend(plan.useCase, plan.legacySupport)) {
                            "当前推荐"
                        } else {
                            "手动选择"
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    FormatPolicy.shortDescription(plan.fileSystem),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    FormatPolicy.compatibility(plan.fileSystem),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(12.dp))
                FormatPolicy.cautions(plan.fileSystem).forEach { caution ->
                    Text(
                        "•  $caution",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            SectionTitle("4", "卷标与分配单元")
        }
        item {
            OutlinedTextField(
                value = plan.volumeLabel,
                onValueChange = onVolumeLabelChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                label = { Text("卷标") },
                placeholder = { Text("例如 DISKFLOW") },
                singleLine = true,
                isError = state.labelError != null,
                supportingText = {
                    Text(
                        state.labelError ?: "仅配置方案；系统工具可能使用自己的卷标规则",
                    )
                },
            )
        }
        item {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                FormatPolicy.allocationUnits(plan.fileSystem).forEach { option ->
                    FilterChip(
                        selected = option.sizeKiB == plan.allocationUnitKiB,
                        onClick = { onSelectAllocationUnit(option.sizeKiB) },
                        label = { Text(option.title) },
                    )
                }
            }
        }
        item {
            val selectedUnit = FormatPolicy.allocationUnits(plan.fileSystem)
                .first { it.sizeKiB == plan.allocationUnitKiB }
            Text(
                selectedUnit.description,
                modifier = Modifier.padding(horizontal = 22.dp),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        item {
            Button(
                onClick = onCreatePlan,
                enabled = !state.isRecording,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(54.dp),
            ) {
                if (state.isRecording) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(9.dp))
                Text("保存方案并查看执行指引")
            }
        }
    }
}

@Composable
private fun FormatGuide(
    state: FormatUiState,
    targetConnected: Boolean,
    onBack: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onMarkCompleted: () -> Unit,
    onCancel: () -> Unit,
    onExecuteFormat: () -> Unit,
    onRefreshCapabilities: () -> Unit,
) {
    val plan = state.plan
    val unit = FormatPolicy.allocationUnits(plan.fileSystem)
        .first { it.sizeKiB == plan.allocationUnitKiB }
    var showConfirm by remember { mutableStateOf(false) }
    val capabilities = state.capabilities
    val rootReady = capabilities?.hasRoot == true
    val formatSupported = capabilities?.availableFileSystems?.contains(plan.fileSystem) == true

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("确认格式化 ${plan.deviceName}？") },
            text = {
                Text(
                    "将把设备重建为 ${plan.fileSystem.displayName} 文件系统，卷标" +
                        " ${plan.volumeLabel.trim()}。设备上的全部数据会被清除且无法恢复。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirm = false
                        onExecuteFormat()
                    },
                ) { Text("已备份，开始格式化") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("取消") }
            },
        )
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
    ) {
        item {
            PageHeader(
                eyebrow = "Execution guide",
                title = "执行指引",
                subtitle = "方案已保存在本机，尚未对设备做任何修改",
            )
        }
        item {
            TechCard(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("方案摘要", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    StatusPill("已保存")
                }
                Spacer(Modifier.height(10.dp))
                MetadataRow("目标设备", plan.deviceName)
                MetadataRow("文件系统", plan.fileSystem.displayName)
                MetadataRow("卷标", plan.volumeLabel.trim())
                MetadataRow("分配单元", unit.title)
                MetadataRow("执行方式", if (rootReady) "Root 直接写入" else "Android 系统存储工具")
            }
        }
        if (!targetConnected) {
            item {
                NoticeCard(
                    title = "目标设备当前未连接",
                    message = "请重新连接 ${plan.deviceName} 后再继续，避免选错其他存储设备。",
                    tone = NoticeTone.ERROR,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
        item {
            NoticeCard(
                title = "格式化会清除目标设备数据",
                message = "请先备份整个设备，并再次核对设备名称。不要只依赖卷标区分多个同型号设备。",
                tone = NoticeTone.WARNING,
                icon = Icons.Rounded.WarningAmber,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item { SectionTitle(null, "直接格式化") }
        item {
            TechCard(Modifier.padding(horizontal = 20.dp)) {
                when {
                    capabilities == null -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "正在检测 Root 状态",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                        }
                    }

                    !rootReady -> {
                        Text("未获得 Root，无法直接写入文件系统", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            capabilities.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = onRefreshCapabilities) { Text("重新检测") }
                    }

                    else -> {
                        Text(
                            capabilities.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Mint,
                        )
                        if (!formatSupported) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "系统缺少 ${plan.fileSystem.displayName} 的 mkfs 工具，可换用其他格式或改用系统工具。",
                                style = MaterialTheme.typography.bodySmall,
                                color = Error,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        when (val execution = state.execution) {
                            is FormatExecution.Running -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(execution.step, style = MaterialTheme.typography.bodyMedium)
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "执行期间请勿拔出设备",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                )
                            }

                            is FormatExecution.Finished -> {
                                Text(
                                    if (execution.success) "格式化完成" else "格式化失败",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (execution.success) Mint else Error,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    execution.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                )
                            }

                            FormatExecution.Idle -> Unit
                        }
                    }
                }
            }
        }
        item {
            Button(
                onClick = { showConfirm = true },
                enabled = rootReady && formatSupported && targetConnected && !state.isExecuting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(54.dp),
            ) {
                Icon(Icons.Rounded.Shield, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(9.dp))
                Text(
                    when {
                        state.isExecuting -> "正在格式化…"
                        !rootReady -> "需要 Root 才能直接格式化"
                        !formatSupported -> "系统缺少该格式工具"
                        else -> "直接格式化 ${plan.fileSystem.displayName}"
                    },
                )
            }
        }
        item { SectionTitle(null, "在系统中完成") }
        item {
            TechCard(Modifier.padding(horizontal = 20.dp)) {
                GuideStep(
                    number = "1",
                    title = "打开系统存储设置",
                    description = "点击下方按钮。不同品牌可能显示为“存储”“SD 卡”或“USB 存储”。",
                )
                GuideStep(
                    number = "2",
                    title = "核对设备并选择格式化",
                    description = "若系统提供文件系统、卷标或单元选项，请参考方案；没有提供的选项由系统决定。",
                )
                GuideStep(
                    number = "3",
                    title = "以系统结果为准",
                    description = "完成前不要拔出设备。DiskFlow 无法读取系统执行进度，也不会自动宣称成功。",
                    isLast = true,
                )
            }
        }
        item {
            Button(
                onClick = onOpenSystemSettings,
                enabled = targetConnected,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(54.dp),
            ) {
                Icon(Icons.Rounded.Settings, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(9.dp))
                Text("前往系统存储设置")
            }
        }
        item {
            OutlinedButton(
                onClick = onMarkCompleted,
                enabled = !state.completionMarked,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(50.dp),
            ) {
                Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(9.dp))
                Text(if (state.completionMarked) "已记录你的确认" else "我已在系统中完成操作")
            }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onBack) { Text("返回修改方案") }
                TextButton(onClick = onCancel) { Text("取消并记录") }
            }
        }
        item {
            NoticeCard(
                title = "找不到格式化选项？",
                message = "部分手机仅支持 SD 卡格式化或不支持 U 盘格式化。可使用电脑磁盘工具，并按本方案选择格式；不要对手机内部存储执行操作。",
                icon = Icons.Rounded.Info,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

@Composable
private fun DeviceChoice(
    device: StorageDevice,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) Mint.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) Mint.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(volumeIcon(device.kind), null, tint = if (selected) Mint else TextSecondary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleMedium)
                Text(volumeKindLabel(device.kind), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

@Composable
private fun UseCaseChoice(
    useCase: StorageUseCase,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 112.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) Mint.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) Mint.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    useCase.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) Mint else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (selected) Icon(Icons.Rounded.Check, null, tint = Mint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(useCase.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun GuideStep(number: String, title: String, description: String, isLast: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Text(
                number,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelLarge,
                color = Mint,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(3.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            if (!isLast) Spacer(Modifier.height(10.dp))
        }
    }
}
