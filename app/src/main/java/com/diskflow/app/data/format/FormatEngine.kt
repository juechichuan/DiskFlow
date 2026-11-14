package com.diskflow.app.data.format

import com.diskflow.app.domain.model.FileSystemType
import com.diskflow.app.domain.model.FormatPlan
import com.diskflow.app.domain.model.StorageDevice
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val DEFAULT_TIMEOUT_SECONDS = 30L
private const val FORMAT_TIMEOUT_SECONDS = 600L

/** Root 权限与格式化工具的实际可用情况。 */
data class FormatCapabilities(
    val hasRoot: Boolean = false,
    val availableFileSystems: Set<FileSystemType> = emptySet(),
    val message: String = "正在检测 Root 状态",
)

data class FormatOutcome(
    val success: Boolean,
    val message: String,
    val log: String = "",
)

interface FormatEngine {
    suspend fun probe(): FormatCapabilities

    suspend fun format(
        device: StorageDevice,
        plan: FormatPlan,
        onStep: (String) -> Unit,
    ): FormatOutcome
}

internal data class ShellResult(
    val exitCode: Int,
    val output: String,
)

/**
 * 纯函数形式的命令构造与输出解析，便于单元测试。
 * 只有 vold 管理的 public 卷才会被接受，避免对内部存储执行破坏性操作。
 */
object RootFormatCommands {
    private const val VOLD_PREFIX = "/dev/block/vold/"

    private val unsafeLabelChars = Regex("[^A-Za-z0-9_-]")

    fun sanitizeLabel(label: String, maxLength: Int = 11): String {
        val cleaned = label.trim().replace(unsafeLabelChars, "").ifBlank { "DISKFLOW" }
        return cleaned.take(maxLength)
    }

    /** 从 `sm list-volumes all` 输出中按 UUID 找到 public 卷标识，例如 public:179,65。 */
    fun volumeIdForUuid(listVolumesOutput: String, uuid: String): String? =
        listVolumesOutput.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .firstOrNull { line ->
                val parts = line.split(Regex("\\s+"))
                parts.size >= 3 &&
                    parts.first().startsWith("public:") &&
                    parts.last().equals(uuid, ignoreCase = true)
            }
            ?.split(Regex("\\s+"))
            ?.first()

    fun blockDevicePath(volumeId: String): String? =
        if (volumeId.startsWith("public:")) VOLD_PREFIX + volumeId else null

    fun toolCommand(
        type: FileSystemType,
        label: String,
        devicePath: String,
        tools: Set<String>,
    ): String? {
        val safeLabel = sanitizeLabel(label)
        fun available(vararg names: String): String? = names.firstOrNull { it in tools }
        return when (type) {
            FileSystemType.FAT32 -> available("mkfs.vfat", "newfs_msdos")?.let { tool ->
                when (tool) {
                    "mkfs.vfat" -> "$tool -F 32 -n $safeLabel $devicePath"
                    else -> "$tool -F 32 -L $safeLabel $devicePath"
                }
            }
            FileSystemType.EXFAT -> available("mkfs.exfat")?.let { "$it -n $safeLabel $devicePath" }
            FileSystemType.NTFS -> available("mkfs.ntfs", "mkntfs")?.let {
                "$it -f -L $safeLabel $devicePath"
            }
            FileSystemType.EXT4 -> available("mke2fs", "make_ext4fs")?.let { tool ->
                when (tool) {
                    "mke2fs" -> "$tool -t ext4 -F -L $safeLabel $devicePath"
                    else -> "$tool -L $safeLabel $devicePath"
                }
            }
        }
    }
}

/**
 * 通过 `su` 调用设备自带的 mkfs 工具执行真实格式化。
 * 免 Root 环境下 [probe] 会返回不可用，界面不得宣称执行成功。
 */
class RootFormatEngine : FormatEngine {

    override suspend fun probe(): FormatCapabilities = withContext(Dispatchers.IO) {
        val identity = runShell("id")
        if (!identity.output.contains("uid=0")) {
            return@withContext FormatCapabilities(
                hasRoot = false,
                message = "未获得 Root 权限，无法直接写入块设备。安装并授权 Root 后重新检测。",
            )
        }
        val tools = detectTools()
        val formats = FileSystemType.values().filter { type ->
            RootFormatCommands.toolCommand(type, "LABEL", "/dev/null", tools) != null
        }.toSet()
        FormatCapabilities(
            hasRoot = true,
            availableFileSystems = formats,
            message = if (formats.isEmpty()) {
                "已获得 Root，但系统未内置 mkfs 工具，无法执行格式化。"
            } else {
                "Root 已授权，可执行 " + formats.joinToString(" / ") { it.displayName }
            },
        )
    }

    override suspend fun format(
        device: StorageDevice,
        plan: FormatPlan,
        onStep: (String) -> Unit,
    ): FormatOutcome = withContext(Dispatchers.IO) {
        if (!device.isExternal || device.isEmulated) {
            return@withContext FormatOutcome(false, "目标不是可移除外置存储，已拒绝执行")
        }
        val uuid = device.uuid
            ?: return@withContext FormatOutcome(false, "无法读取该设备的卷标识，已拒绝执行")

        onStep("检查 Root 权限")
        if (!runShell("id").output.contains("uid=0")) {
            return@withContext FormatOutcome(false, "未获得 Root 权限，已取消")
        }

        onStep("定位块设备")
        val volumeId = RootFormatCommands.volumeIdForUuid(
            runShell("sm list-volumes all").output,
            uuid,
        ) ?: return@withContext FormatOutcome(false, "未能定位 $uuid 对应的可移除卷，已取消")
        val devicePath = RootFormatCommands.blockDevicePath(volumeId)
            ?: return@withContext FormatOutcome(false, "不支持的卷标识 $volumeId，已取消")
        if (runShell("ls -l $devicePath").exitCode != 0) {
            return@withContext FormatOutcome(false, "块设备不存在：$devicePath，已取消")
        }

        val label = RootFormatCommands.sanitizeLabel(plan.volumeLabel)
        val command = RootFormatCommands.toolCommand(plan.fileSystem, label, devicePath, detectTools())
            ?: return@withContext FormatOutcome(
                false,
                "系统缺少 ${plan.fileSystem.displayName} 格式化工具，已取消，未修改设备",
            )

        onStep("卸载 $volumeId")
        runShell("sm unmount $volumeId")
        runShell("umount $devicePath")

        onStep("正在写入 ${plan.fileSystem.displayName} 文件系统")
        val result = runShell(command, FORMAT_TIMEOUT_SECONDS)
        val log = result.output.takeLast(2000)

        onStep("重新挂载")
        runShell("sm mount $volumeId")

        if (result.exitCode == 0) {
            FormatOutcome(
                success = true,
                message = "已写入 ${plan.fileSystem.displayName} 文件系统，卷标 $label",
                log = log,
            )
        } else {
            FormatOutcome(
                success = false,
                message = "${plan.fileSystem.displayName} 写入失败（退出码 ${result.exitCode}）",
                log = log,
            )
        }
    }

    private fun detectTools(): Set<String> = TOOL_CANDIDATES.filter { name ->
        val result = runShell("which $name")
        result.exitCode == 0 && result.output.isNotBlank()
    }.toSet()

    private fun runShell(
        command: String,
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
    ): ShellResult = runCatching {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        val reader = Thread {
            runCatching {
                process.inputStream.bufferedReader().forEachLine { line ->
                    output.appendLine(line)
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            reader.join(1000)
            return ShellResult(-1, "执行超时：$command")
        }
        reader.join(2000)
        ShellResult(process.exitValue(), output.toString().trim())
    }.getOrElse {
        ShellResult(-1, "无法执行 su：${it.message ?: "未知错误"}")
    }

    private companion object {
        val TOOL_CANDIDATES = listOf(
            "mkfs.vfat",
            "newfs_msdos",
            "mkfs.exfat",
            "mkfs.ntfs",
            "mkntfs",
            "mke2fs",
            "make_ext4fs",
        )
    }
}
