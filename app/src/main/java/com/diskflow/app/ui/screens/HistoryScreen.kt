package com.diskflow.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.diskflow.app.domain.model.OperationEvent
import com.diskflow.app.domain.model.OperationEventType
import com.diskflow.app.ui.components.EmptyState
import com.diskflow.app.ui.components.NoticeCard
import com.diskflow.app.ui.components.PageHeader
import com.diskflow.app.ui.components.StatusPill
import com.diskflow.app.ui.components.TechCard
import com.diskflow.app.ui.components.formatEventTime
import com.diskflow.app.ui.theme.Error
import com.diskflow.app.ui.theme.Mint
import com.diskflow.app.ui.theme.TextSecondary
import com.diskflow.app.ui.theme.Warning

@Composable
fun HistoryScreen(
    events: List<OperationEvent>,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
) {
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
    ) {
        item {
            PageHeader(
                eyebrow = "Local activity",
                title = "操作记录",
                subtitle = "只记录真实发生的动作，不推断系统结果",
                action = {
                    IconButton(
                        onClick = { showClearConfirmation = true },
                        enabled = events.isNotEmpty(),
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "清空记录")
                    }
                },
            )
        }
        item {
            NoticeCard(
                title = "如何理解这些记录",
                message = "“用户标记完成”仅表示你手动确认，不代表 DiskFlow 验证了格式化结果。所有内容仅保存在本机。",
                icon = Icons.Rounded.Info,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        if (events.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.History,
                    title = "暂无操作记录",
                    message = "生成格式化方案、跳转系统设置或取消方案后，相关事实会显示在这里。",
                )
            }
        }
        items(events, key = { it.id }) { event ->
            TechCard(
                Modifier
                    .padding(horizontal = 20.dp)
                    .animateItem(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        eventIcon(event.type),
                        contentDescription = null,
                        tint = eventColor(event.type),
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    Text(
                        eventTitle(event.type),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    event.fileSystem?.let { StatusPill(it.displayName) }
                    IconButton(onClick = { onDelete(event.id) }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "删除此记录")
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(event.deviceName, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Text(event.detail, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                Text(
                    formatEventTime(event.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            icon = { Icon(Icons.Rounded.DeleteOutline, null) },
            title = { Text("清空全部记录？") },
            text = { Text("此操作只删除 DiskFlow 的本地事件记录，不会影响任何存储设备。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClear()
                        showClearConfirmation = false
                    },
                ) {
                    Text("确认清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) { Text("取消") }
            },
        )
    }
}

private fun eventTitle(type: OperationEventType): String = when (type) {
    OperationEventType.PLAN_CREATED -> "方案已创建"
    OperationEventType.SYSTEM_SETTINGS_OPENED -> "已打开系统设置"
    OperationEventType.USER_MARKED_COMPLETE -> "用户标记完成"
    OperationEventType.CANCELLED -> "方案已取消"
}

private fun eventIcon(type: OperationEventType): ImageVector = when (type) {
    OperationEventType.PLAN_CREATED -> Icons.Rounded.Description
    OperationEventType.SYSTEM_SETTINGS_OPENED -> Icons.Rounded.Settings
    OperationEventType.USER_MARKED_COMPLETE -> Icons.Rounded.CheckCircle
    OperationEventType.CANCELLED -> Icons.Rounded.Cancel
}

@Composable
private fun eventColor(type: OperationEventType) = when (type) {
    OperationEventType.PLAN_CREATED -> MaterialTheme.colorScheme.secondary
    OperationEventType.SYSTEM_SETTINGS_OPENED -> Warning
    OperationEventType.USER_MARKED_COMPLETE -> Mint
    OperationEventType.CANCELLED -> Error
}
