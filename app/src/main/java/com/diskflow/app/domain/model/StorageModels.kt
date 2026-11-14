package com.diskflow.app.domain.model

import kotlinx.serialization.Serializable

enum class VolumeKind {
    INTERNAL,
    SD_CARD,
    USB,
    OTHER_REMOVABLE,
}

enum class MountState {
    MOUNTED,
    READ_ONLY,
    UNMOUNTED,
    EJECTING,
    EJECTED,
    REMOVED,
    UNAVAILABLE,
    UNKNOWN,
}

data class StorageCapacity(
    val totalBytes: Long,
    val availableBytes: Long,
) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0L)
    val usedFraction: Float
        get() = if (totalBytes > 0L) {
            (usedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
}

data class StorageDevice(
    val id: String,
    val name: String,
    val kind: VolumeKind,
    val mountState: MountState,
    val rawState: String,
    val uuid: String?,
    val directoryPath: String?,
    val isRemovable: Boolean,
    val isEmulated: Boolean,
    val capacity: StorageCapacity?,
    val fileSystemDescription: String,
) {
    val isExternal: Boolean get() = kind != VolumeKind.INTERNAL
    val isMounted: Boolean get() = mountState == MountState.MOUNTED || mountState == MountState.READ_ONLY
}

data class UsbDeviceInfo(
    val deviceId: Int,
    val deviceName: String,
    val productName: String?,
    val manufacturerName: String?,
    val vendorId: Int,
    val productId: Int,
    val deviceClass: Int,
    val deviceSubclass: Int,
    val protocol: Int,
    val hasPermission: Boolean,
)

data class StorageSnapshot(
    val devices: List<StorageDevice> = emptyList(),
    val usbDevices: List<UsbDeviceInfo> = emptyList(),
    val scannedAt: Long = 0L,
    val warning: String? = null,
)

@Serializable
enum class FileSystemType(val displayName: String) {
    FAT32("FAT32"),
    EXFAT("exFAT"),
    NTFS("NTFS"),
    EXT4("ext4"),
}

@Serializable
enum class StorageUseCase(val displayName: String, val description: String) {
    CROSS_PLATFORM("跨平台通用", "在 Android、Windows 与 macOS 之间交换文件"),
    MEDIA("影音与大文件", "保存高清视频、镜像或其他超过 4 GiB 的文件"),
    WINDOWS("Windows 主力", "主要在 Windows 电脑中使用"),
    LINUX_ANDROID("Linux / Android", "用于 Linux、服务器或支持 ext4 的 Android 设备"),
}

@Serializable
data class FormatPlan(
    val deviceId: String = "",
    val deviceName: String = "",
    val fileSystem: FileSystemType = FileSystemType.EXFAT,
    val useCase: StorageUseCase = StorageUseCase.CROSS_PLATFORM,
    val volumeLabel: String = "DISKFLOW",
    val allocationUnitKiB: Int = 0,
    val legacySupport: Boolean = false,
    val updatedAt: Long = 0L,
)

data class AllocationUnitOption(
    val sizeKiB: Int,
    val title: String,
    val description: String,
)

data class LabelValidation(
    val errorMessage: String? = null,
) {
    val isValid: Boolean get() = errorMessage == null
}

data class FileSizeCheck(
    val isSupported: Boolean,
    val message: String,
)

@Serializable
enum class OperationEventType {
    PLAN_CREATED,
    SYSTEM_SETTINGS_OPENED,
    USER_MARKED_COMPLETE,
    CANCELLED,
}

@Serializable
data class OperationEvent(
    val id: String,
    val type: OperationEventType,
    val deviceName: String,
    val fileSystem: FileSystemType? = null,
    val timestamp: Long,
    val detail: String,
)
