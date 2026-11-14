package com.diskflow.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.diskflow.app.domain.model.StorageDevice
import com.diskflow.app.ui.DevicesUiState
import com.diskflow.app.ui.components.EmptyState
import com.diskflow.app.ui.components.NoticeCard
import com.diskflow.app.ui.components.NoticeTone
import com.diskflow.app.ui.components.PageHeader
import com.diskflow.app.ui.components.SectionTitle
import com.diskflow.app.ui.components.StatusPill
import com.diskflow.app.ui.components.StorageDeviceCard
import com.diskflow.app.ui.components.TechCard
import com.diskflow.app.ui.theme.Mint
import com.diskflow.app.ui.theme.TextSecondary

@Composable
fun DevicesScreen(
    state: DevicesUiState,
    onRefresh: () -> Unit,
    onDeviceClick: (StorageDevice) -> Unit,
) {
    val externalDevices = state.snapshot.devices.filter { it.isExternal }
    val internalDevices = state.snapshot.devices.filterNot { it.isExternal }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 30.dp),
    ) {
        item {
            PageHeader(
                eyebrow = "Storage workspace",
                title = "我的设备",
                subtitle = "真实检测外接存储，清楚掌握每一步",
                action = {
                    IconButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = "刷新设备")
                        }
                    }
                },
            )
        }

        item {
            TechCard(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (externalDevices.isEmpty()) "等待外置存储接入" else "外置存储已就绪",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            if (externalDevices.isEmpty()) {
                                "通过 OTG 连接 U 盘，或插入 SD 卡后刷新"
                            } else {
                                "检测到 ${externalDevices.size} 个存储卷，点击查看完整信息"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }
                    StatusPill("免 Root")
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth()) {
                    OverviewMetric("存储卷", state.snapshot.devices.size.toString(), Modifier.weight(1f))
                    OverviewMetric("外置", externalDevices.size.toString(), Modifier.weight(1f))
                    OverviewMetric("USB", state.snapshot.usbDevices.size.toString(), Modifier.weight(1f))
                }
            }
        }

        state.snapshot.warning?.let { warning ->
            item {
                NoticeCard(
                    title = "部分信息不可用",
                    message = warning,
                    tone = NoticeTone.WARNING,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }

        item {
            SectionTitle(null, "外置存储")
        }

        if (externalDevices.isEmpty() && !state.isRefreshing) {
            item {
                EmptyState(
                    icon = Icons.Rounded.Cable,
                    title = "还没有外置存储",
                    message = "连接后请确认手机支持 OTG，并在系统通知中完成挂载。DiskFlow 不会显示虚假设备。",
                    action = {
                        Button(onClick = onRefresh) {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("重新检测")
                        }
                    },
                )
            }
        }

        items(externalDevices, key = { it.id }) { device ->
            StorageDeviceCard(
                device = device,
                onClick = { onDeviceClick(device) },
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .animateItem(),
            )
        }

        if (internalDevices.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                SectionTitle(null, "本机存储")
            }
            items(internalDevices, key = { it.id }) { device ->
                StorageDeviceCard(
                    device = device,
                    onClick = { onDeviceClick(device) },
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .animateItem(),
                )
            }
        }

        item {
            NoticeCard(
                title = "权限边界",
                message = "免 Root 模式可读取系统公开的存储信息并配置方案；真正格式化必须由系统或电脑工具执行。",
                icon = Icons.Rounded.Shield,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

@Composable
private fun OverviewMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            color = Mint,
            fontWeight = FontWeight.Bold,
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}
