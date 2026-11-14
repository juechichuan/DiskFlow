package com.diskflow.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.diskflow.app.domain.FormatPolicy
import com.diskflow.app.domain.model.StorageDevice
import com.diskflow.app.ui.components.MetadataRow
import com.diskflow.app.ui.components.NoticeCard
import com.diskflow.app.ui.components.NoticeTone
import com.diskflow.app.ui.components.SectionTitle
import com.diskflow.app.ui.components.StatusPill
import com.diskflow.app.ui.components.TechCard
import com.diskflow.app.ui.components.formatEventTime
import com.diskflow.app.ui.components.mountStateColor
import com.diskflow.app.ui.components.mountStateLabel
import com.diskflow.app.ui.components.volumeKindLabel

@Composable
fun DeviceDetailScreen(
    device: StorageDevice,
    scannedAt: Long,
    onBack: () -> Unit,
    onConfigure: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onAuthorizeDirectory: () -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 30.dp),
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                }
                Text("设备详情", style = MaterialTheme.typography.titleLarge)
            }
        }
        item {
            TechCard(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        device.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    StatusPill(
                        mountStateLabel(device.mountState),
                        color = mountStateColor(device.mountState),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    volumeKindLabel(device.kind),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SectionTitle(null, "存储信息")
        }
        item {
            TechCard(Modifier.padding(horizontal = 20.dp)) {
                MetadataRow("挂载状态", "${mountStateLabel(device.mountState)} (${device.rawState})")
                MetadataRow("文件系统", device.fileSystemDescription)
                MetadataRow(
                    "总容量",
                    device.capacity?.let { FormatPolicy.formatByteSize(it.totalBytes) } ?: "不可用",
                )
                MetadataRow(
                    "可用空间",
                    device.capacity?.let { FormatPolicy.formatByteSize(it.availableBytes) } ?: "不可用",
                )
                MetadataRow("卷 UUID", device.uuid ?: "由系统分配")
                MetadataRow("挂载目录", device.directoryPath ?: "系统未公开")
                MetadataRow("可移除", if (device.isRemovable) "是" else "否")
                MetadataRow("模拟存储", if (device.isEmulated) "是" else "否")
                MetadataRow("最近扫描", formatEventTime(scannedAt))
            }
        }
        item {
            NoticeCard(
                title = "识别说明",
                message = "容量来自系统挂载目录。Android 未公开或无法可靠判断的文件系统会显示为未知/系统管理，不做猜测。",
                icon = Icons.Rounded.Info,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        if (device.isExternal) {
            item {
                Button(
                    onClick = onConfigure,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(52.dp),
                ) {
                    Icon(Icons.Rounded.Tune, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("为此设备配置格式化方案")
                }
            }
            item {
                OutlinedButton(
                    onClick = onAuthorizeDirectory,
                    enabled = device.isMounted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(50.dp),
                ) {
                    Icon(Icons.Rounded.FolderOpen, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("通过 SAF 授权目录访问")
                }
            }
        }
        item {
            OutlinedButton(
                onClick = onOpenSystemSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(50.dp),
            ) {
                Icon(Icons.Rounded.Settings, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("打开系统存储设置")
            }
        }
        item {
            NoticeCard(
                title = "操作前请备份",
                message = "系统设置中的格式化入口和可选格式由手机厂商提供。DiskFlow 不会拦截、伪造或确认格式化结果。",
                tone = NoticeTone.WARNING,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}
