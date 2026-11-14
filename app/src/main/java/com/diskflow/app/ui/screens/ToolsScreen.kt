package com.diskflow.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diskflow.app.domain.FormatPolicy
import com.diskflow.app.domain.model.FileSizeCheck
import com.diskflow.app.domain.model.FileSystemType
import com.diskflow.app.domain.model.UsbDeviceInfo
import com.diskflow.app.ui.FileSizeUnit
import com.diskflow.app.ui.ToolsUiState
import com.diskflow.app.ui.components.EmptyState
import com.diskflow.app.ui.components.MetadataRow
import com.diskflow.app.ui.components.NoticeCard
import com.diskflow.app.ui.components.NoticeTone
import com.diskflow.app.ui.components.PageHeader
import com.diskflow.app.ui.components.SectionTitle
import com.diskflow.app.ui.components.StatusPill
import com.diskflow.app.ui.components.TechCard
import com.diskflow.app.ui.theme.Mint
import com.diskflow.app.ui.theme.TextSecondary

@Composable
fun ToolsScreen(
    state: ToolsUiState,
    fileSizeCheck: FileSizeCheck,
    authorizedTreeUri: String?,
    preferredDeviceName: String?,
    usbDevices: List<UsbDeviceInfo>,
    usbRequests: Set<Int>,
    onSelectFileSystem: (FileSystemType) -> Unit,
    onFileSizeChange: (String) -> Unit,
    onSelectUnit: (FileSizeUnit) -> Unit,
    onAuthorizeDirectory: () -> Unit,
    onOpenAuthorizedDirectory: () -> Unit,
    onRemoveAuthorizedDirectory: () -> Unit,
    onRequestUsbPermission: (Int) -> Unit,
) {
    var expandedFileSystem by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
    ) {
        item {
            PageHeader(
                eyebrow = "Storage toolkit",
                title = "工具箱",
                subtitle = "兼容性、文件大小与外设信息，一站查清",
            )
        }
        item { SectionTitle(null, "文件系统对比") }
        items(FileSystemType.entries, key = { it.name }) { type ->
            val expanded = expandedFileSystem == type.name
            TechCard(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .animateItem(),
                onClick = { expandedFileSystem = if (expanded) null else type.name },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(type.displayName, style = MaterialTheme.typography.titleLarge, color = Mint)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            FormatPolicy.shortDescription(type),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    Icon(
                        if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.ChevronRight,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = TextSecondary,
                    )
                }
                AnimatedVisibility(visible = expanded) {
                    Column {
                        Spacer(Modifier.height(14.dp))
                        Text("平台兼容性", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            FormatPolicy.compatibility(type),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.height(10.dp))
                        MetadataRow(
                            "单文件上限",
                            FormatPolicy.maximumSingleFileBytes(type)?.let {
                                "${FormatPolicy.formatByteSize(it)}（4 GiB − 1 字节）"
                            } ?: "无 FAT32 的 4 GiB 限制",
                        )
                        FormatPolicy.cautions(type).forEach { caution ->
                            Text(
                                "•  $caution",
                                modifier = Modifier.padding(vertical = 2.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            SectionTitle(null, "大文件限制检查")
        }
        item {
            TechCard(Modifier.padding(horizontal = 20.dp)) {
                Text("文件系统", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FileSystemType.entries.forEach { type ->
                        FilterChip(
                            selected = type == state.fileSystem,
                            onClick = { onSelectFileSystem(type) },
                            label = { Text(type.displayName) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = state.fileSizeInput,
                    onValueChange = onFileSizeChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("单个文件大小") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FileSizeUnit.entries.forEach { unit ->
                        FilterChip(
                            selected = unit == state.fileSizeUnit,
                            onClick = { onSelectUnit(unit) },
                            label = { Text(unit.displayName) },
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                NoticeCard(
                    title = if (fileSizeCheck.isSupported) "大小检查通过" else "无法存入此文件系统",
                    message = fileSizeCheck.message,
                    tone = if (fileSizeCheck.isSupported) NoticeTone.SUCCESS else NoticeTone.ERROR,
                    icon = if (fileSizeCheck.isSupported) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                )
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            SectionTitle(null, "存储目录授权")
        }
        item {
            TechCard(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Storage Access Framework", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (authorizedTreeUri == null) "尚未授权目录" else "已保存一个持久目录授权",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    StatusPill(if (authorizedTreeUri == null) "未授权" else "已授权")
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "SAF 用于访问你明确选择的目录，不等于格式化权限，也不会授予整盘底层访问。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                if (authorizedTreeUri != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        authorizedTreeUri,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(14.dp))
                if (authorizedTreeUri == null) {
                    Button(onClick = onAuthorizeDirectory, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.FolderOpen, null, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(preferredDeviceName?.let { "选择目录（$it）" } ?: "选择存储目录")
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onOpenAuthorizedDirectory,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("打开目录")
                        }
                        TextButton(onClick = onRemoveAuthorizedDirectory) { Text("移除授权") }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            SectionTitle(null, "USB 硬件信息")
        }
        if (usbDevices.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.Usb,
                    title = "没有检测到 USB 设备",
                    message = "请确认 OTG 已开启且转接头、线缆正常。USB 外设不一定是存储设备，此处显示系统硬件列表。",
                )
            }
        }
        items(usbDevices, key = { it.deviceId }) { device ->
            UsbDeviceCard(
                device = device,
                isRequesting = device.deviceId in usbRequests,
                onRequestPermission = { onRequestUsbPermission(device.deviceId) },
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .animateItem(),
            )
        }
        item {
            NoticeCard(
                title = "隐私与安全",
                message = "设备扫描、方案与记录均只在本机处理。应用没有网络权限，也不包含广告或统计上传。",
                icon = Icons.Rounded.Info,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

@Composable
private fun UsbDeviceCard(
    device: UsbDeviceInfo,
    isRequesting: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TechCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    device.productName ?: "USB 设备 ${device.deviceId}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    device.manufacturerName ?: device.deviceName,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            StatusPill(if (device.hasPermission) "已授权" else "未授权")
        }
        Spacer(Modifier.height(12.dp))
        MetadataRow("VID : PID", "%04X : %04X".format(device.vendorId, device.productId))
        MetadataRow("设备类别", usbClassLabel(device.deviceClass))
        MetadataRow("子类 / 协议", "${device.deviceSubclass} / ${device.protocol}")
        MetadataRow("系统节点", device.deviceName)
        if (!device.hasPermission) {
            TextButton(
                onClick = onRequestPermission,
                enabled = !isRequesting,
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(Icons.Rounded.LockOpen, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isRequesting) "等待系统授权…" else "申请 USB 信息权限")
            }
        }
    }
}

private fun usbClassLabel(deviceClass: Int): String = when (deviceClass) {
    0 -> "按接口定义"
    1 -> "音频设备"
    3 -> "人机交互设备"
    6 -> "图像设备"
    7 -> "打印设备"
    8 -> "大容量存储"
    9 -> "USB 集线器"
    14 -> "视频设备"
    224 -> "无线控制器"
    255 -> "厂商自定义"
    else -> "类别 $deviceClass"
}
