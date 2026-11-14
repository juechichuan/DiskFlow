package com.diskflow.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diskflow.app.domain.FormatPolicy
import com.diskflow.app.domain.model.MountState
import com.diskflow.app.domain.model.StorageDevice
import com.diskflow.app.domain.model.VolumeKind
import com.diskflow.app.ui.theme.Error
import com.diskflow.app.ui.theme.Mint
import com.diskflow.app.ui.theme.PanelRaised
import com.diskflow.app.ui.theme.TextSecondary
import com.diskflow.app.ui.theme.Warning
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PageHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = eyebrow.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
            )
            Spacer(Modifier.height(5.dp))
            Text(title, style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        if (action != null) {
            Spacer(Modifier.width(12.dp))
            action()
        }
    }
}

@Composable
fun SectionTitle(
    step: String?,
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (step != null) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(step, style = MaterialTheme.typography.labelMedium, color = Mint)
            }
            Spacer(Modifier.width(10.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun TechCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(18.dp), content = content)
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(18.dp), content = content)
        }
    }
}

enum class NoticeTone { INFO, SUCCESS, WARNING, ERROR }

@Composable
fun NoticeCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    tone: NoticeTone = NoticeTone.INFO,
    icon: ImageVector = Icons.Rounded.Info,
) {
    val accent = when (tone) {
        NoticeTone.INFO -> MaterialTheme.colorScheme.secondary
        NoticeTone.SUCCESS -> Mint
        NoticeTone.WARNING -> Warning
        NoticeTone.ERROR -> Error
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = accent)
                Spacer(Modifier.height(4.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Mint,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = color.copy(alpha = 0.11f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
fun StorageDeviceCard(
    device: StorageDevice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TechCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(PanelRaised),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = volumeIcon(device.kind),
                    contentDescription = null,
                    tint = if (device.isExternal) Mint else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(25.dp),
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    device.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    volumeKindLabel(device.kind),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            StatusPill(mountStateLabel(device.mountState), color = mountStateColor(device.mountState))
            Icon(Icons.Rounded.ChevronRight, null, tint = TextSecondary)
        }
        Spacer(Modifier.height(18.dp))
        val capacity = device.capacity
        if (capacity != null) {
            val progress by animateFloatAsState(
                targetValue = capacity.usedFraction,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "capacity",
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(CircleShape),
                color = if (capacity.usedFraction > 0.9f) Warning else Mint,
                trackColor = PanelRaised,
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "已用 ${FormatPolicy.formatByteSize(capacity.usedBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Text(
                    "可用 ${FormatPolicy.formatByteSize(capacity.availableBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
        } else {
            Text(
                if (device.isMounted) "容量信息不可用" else "设备当前未挂载",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            device.fileSystemDescription,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

@Composable
fun MetadataRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.width(20.dp))
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(PanelRaised),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Mint, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            action()
        }
    }
}

fun volumeIcon(kind: VolumeKind): ImageVector = when (kind) {
    VolumeKind.INTERNAL -> Icons.Rounded.PhoneAndroid
    VolumeKind.SD_CARD -> Icons.Rounded.SdStorage
    VolumeKind.USB -> Icons.Rounded.Usb
    VolumeKind.OTHER_REMOVABLE -> Icons.Rounded.Storage
}

fun volumeKindLabel(kind: VolumeKind): String = when (kind) {
    VolumeKind.INTERNAL -> "手机内部存储"
    VolumeKind.SD_CARD -> "SD / microSD 卡"
    VolumeKind.USB -> "USB / OTG 设备"
    VolumeKind.OTHER_REMOVABLE -> "可移动存储"
}

fun mountStateLabel(state: MountState): String = when (state) {
    MountState.MOUNTED -> "已挂载"
    MountState.READ_ONLY -> "只读"
    MountState.UNMOUNTED -> "未挂载"
    MountState.EJECTING -> "正在弹出"
    MountState.EJECTED -> "已弹出"
    MountState.REMOVED -> "已移除"
    MountState.UNAVAILABLE -> "不可用"
    MountState.UNKNOWN -> "状态未知"
}

@Composable
fun mountStateColor(state: MountState): Color = when (state) {
    MountState.MOUNTED -> Mint
    MountState.READ_ONLY -> MaterialTheme.colorScheme.secondary
    MountState.UNAVAILABLE -> Error
    MountState.EJECTING,
    MountState.EJECTED,
    MountState.UNMOUNTED,
    MountState.REMOVED,
    MountState.UNKNOWN,
    -> Warning
}

fun formatEventTime(timestamp: Long): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(timestamp))
