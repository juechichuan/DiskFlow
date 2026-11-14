package com.diskflow.app.domain

import com.diskflow.app.domain.model.AllocationUnitOption
import com.diskflow.app.domain.model.FileSizeCheck
import com.diskflow.app.domain.model.FileSystemType
import com.diskflow.app.domain.model.LabelValidation
import com.diskflow.app.domain.model.StorageUseCase

object FormatPolicy {
    const val FAT32_MAX_FILE_BYTES: Long = 4L * 1024L * 1024L * 1024L - 1L
    private const val KIB = 1024L
    private const val GIB = 1024L * 1024L * 1024L

    fun recommend(
        useCase: StorageUseCase,
        legacySupport: Boolean = false,
        largestFileBytes: Long? = null,
    ): FileSystemType = when {
        legacySupport &&
            (useCase == StorageUseCase.CROSS_PLATFORM || useCase == StorageUseCase.MEDIA) &&
            (largestFileBytes == null || largestFileBytes <= FAT32_MAX_FILE_BYTES) ->
            FileSystemType.FAT32
        useCase == StorageUseCase.WINDOWS -> FileSystemType.NTFS
        useCase == StorageUseCase.LINUX_ANDROID -> FileSystemType.EXT4
        else -> FileSystemType.EXFAT
    }

    fun shortDescription(type: FileSystemType): String = when (type) {
        FileSystemType.FAT32 -> "兼容性最广，适合相机、车载设备与小文件交换"
        FileSystemType.EXFAT -> "跨平台大文件首选，适合 U 盘、SD 卡与影音文件"
        FileSystemType.NTFS -> "Windows 体验完整，其他系统的写入支持因平台而异"
        FileSystemType.EXT4 -> "适合 Linux 与支持它的 Android 设备，桌面兼容性较弱"
    }

    fun compatibility(type: FileSystemType): String = when (type) {
        FileSystemType.FAT32 -> "Android、Windows、macOS、相机、车载及游戏设备通常均可读写"
        FileSystemType.EXFAT -> "现代 Android、Windows 与 macOS 通常可读写，老旧设备可能不支持"
        FileSystemType.NTFS -> "Windows 可读写；Android 与 macOS 的写入能力取决于系统或附加驱动"
        FileSystemType.EXT4 -> "Linux 可读写；Windows 与 macOS 通常需要额外工具，Android 支持因厂商而异"
    }

    fun cautions(type: FileSystemType): List<String> = when (type) {
        FileSystemType.FAT32 -> listOf(
            "单个文件最大为 4 GiB − 1 字节",
            "不适合存放高清长视频、大型镜像或超大压缩包",
            "系统格式化工具可能不允许为超大容量设备创建 FAT32",
        )
        FileSystemType.EXFAT -> listOf(
            "没有 FAT32 的 4 GiB 单文件限制",
            "部分老相机、车载系统和游戏机可能无法识别",
            "重要文件仍需保留其他副本，exFAT 不提供 NTFS 那样的日志保护",
        )
        FileSystemType.NTFS -> listOf(
            "Android 手机可能只能读取或完全无法识别",
            "macOS 默认通常只能读取",
            "在 Windows 中请使用安全弹出，降低未完成写入导致的风险",
        )
        FileSystemType.EXT4 -> listOf(
            "Windows 与 macOS 通常不能直接读写",
            "并非所有 Android 手机都支持挂载 ext4 外置存储",
            "权限与所有者信息可能让不同设备上的访问表现不同",
        )
    }

    fun maximumSingleFileBytes(type: FileSystemType): Long? = when (type) {
        FileSystemType.FAT32 -> FAT32_MAX_FILE_BYTES
        else -> null
    }

    fun validateVolumeLabel(type: FileSystemType, rawLabel: String): LabelValidation {
        val label = rawLabel.trim()
        if (label.isEmpty()) return LabelValidation("请输入卷标")
        if (label.any { it.isISOControl() }) return LabelValidation("卷标不能包含控制字符")

        val forbidden = when (type) {
            FileSystemType.EXT4 -> setOf('/')
            else -> setOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')
        }
        val invalidCharacter = label.firstOrNull { it in forbidden }
        if (invalidCharacter != null) {
            return LabelValidation("卷标不能包含字符 $invalidCharacter")
        }

        val overLength = when (type) {
            FileSystemType.FAT32, FileSystemType.EXFAT -> label.length > 11
            FileSystemType.NTFS -> label.length > 32
            FileSystemType.EXT4 -> label.toByteArray(Charsets.UTF_8).size > 16
        }
        if (overLength) {
            val requirement = if (type == FileSystemType.EXT4) "16 个 UTF-8 字节" else {
                if (type == FileSystemType.NTFS) "32 个字符" else "11 个字符"
            }
            return LabelValidation("${type.displayName} 卷标最多 $requirement")
        }
        return LabelValidation()
    }

    fun allocationUnits(type: FileSystemType): List<AllocationUnitOption> {
        val default = AllocationUnitOption(0, "系统默认", "由系统格式化工具选择，普通用途推荐")
        val options = when (type) {
            FileSystemType.FAT32, FileSystemType.EXFAT -> listOf(
                AllocationUnitOption(32, "32 KiB", "兼顾小文件空间利用率"),
                AllocationUnitOption(64, "64 KiB", "适合照片与一般媒体文件"),
                AllocationUnitOption(128, "128 KiB", "适合较大的影音文件"),
                AllocationUnitOption(256, "256 KiB", "大文件顺序读写，小文件更浪费空间"),
            )
            FileSystemType.NTFS -> listOf(
                AllocationUnitOption(4, "4 KiB", "NTFS 常用默认值，适合混合文件"),
                AllocationUnitOption(64, "64 KiB", "适合大型媒体文件，小文件会占用更多空间"),
            )
            FileSystemType.EXT4 -> listOf(
                AllocationUnitOption(1, "1 KiB", "小文件较多时可减少部分空间浪费"),
                AllocationUnitOption(2, "2 KiB", "特殊用途，需确认系统工具支持"),
                AllocationUnitOption(4, "4 KiB", "ext4 常用块大小"),
            )
        }
        return listOf(default) + options
    }

    fun checkFileSize(type: FileSystemType, sizeBytes: Long): FileSizeCheck {
        if (sizeBytes < 0L) return FileSizeCheck(false, "文件大小不能为负数")
        val maximum = maximumSingleFileBytes(type)
        return if (maximum != null && sizeBytes > maximum) {
            FileSizeCheck(false, "超出 FAT32 单文件上限，建议选择 exFAT 或 NTFS")
        } else if (type == FileSystemType.FAT32 && sizeBytes > 3L * GIB) {
            FileSizeCheck(true, "可以存入，但已接近 FAT32 的 4 GiB 单文件上限")
        } else {
            FileSizeCheck(true, "文件大小在 ${type.displayName} 的单文件限制内")
        }
    }

    fun formatByteSize(bytes: Long): String {
        if (bytes < KIB) return "$bytes B"
        val units = listOf("KiB", "MiB", "GiB", "TiB", "PiB")
        var value = bytes.toDouble()
        var unit = "B"
        for (nextUnit in units) {
            if (value < KIB && unit != "B") break
            value /= KIB
            unit = nextUnit
            if (value < KIB) break
        }
        return if (value >= 100.0) {
            "%.0f %s".format(value, unit)
        } else {
            "%.1f %s".format(value, unit)
        }
    }
}
