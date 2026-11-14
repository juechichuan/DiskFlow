package com.diskflow.app.data.storage

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.diskflow.app.domain.model.MountState
import com.diskflow.app.domain.model.StorageCapacity
import com.diskflow.app.domain.model.StorageDevice
import com.diskflow.app.domain.model.StorageSnapshot
import com.diskflow.app.domain.model.UsbDeviceInfo
import com.diskflow.app.domain.model.VolumeKind
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class AndroidStorageRepository(context: Context) : StorageRepository {
    private val applicationContext = context.applicationContext

    override suspend fun scan(): StorageSnapshot = withContext(Dispatchers.IO) {
        val warnings = mutableListOf<String>()
        val usbDevices = runCatching { readUsbDevices() }
            .onFailure { warnings += "USB 设备信息暂时不可用" }
            .getOrDefault(emptyList())
        val mountEntries = readMountEntries()
        val storageManager = applicationContext.getSystemService(StorageManager::class.java)
        val devices = runCatching {
            storageManager?.storageVolumes.orEmpty().map { volume ->
                mapStorageVolume(volume, mountEntries)
            }
        }.onFailure {
            warnings += "无法读取存储卷，请检查系统存储状态后重试"
        }.getOrDefault(emptyList())

        StorageSnapshot(
            devices = devices,
            usbDevices = usbDevices,
            scannedAt = System.currentTimeMillis(),
            warning = warnings.takeIf { it.isNotEmpty() }?.joinToString("\n"),
        )
    }

    override suspend fun requestUsbPermission(deviceId: Int): Boolean {
        val manager = applicationContext.getSystemService(UsbManager::class.java) ?: return false
        val device = manager.deviceList.values.firstOrNull { it.deviceId == deviceId } ?: return false
        if (manager.hasPermission(device)) return true

        return withTimeoutOrNull(USB_PERMISSION_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if (intent?.action != ACTION_USB_PERMISSION) return
                        val changedDevice = IntentCompat.getParcelableExtra(
                            intent,
                            UsbManager.EXTRA_DEVICE,
                            UsbDevice::class.java,
                        )
                        if (changedDevice?.deviceId != deviceId) return
                        runCatching { applicationContext.unregisterReceiver(this) }
                        if (continuation.isActive) {
                            continuation.resume(
                                intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false),
                            )
                        }
                    }
                }
                val filter = IntentFilter(ACTION_USB_PERMISSION)
                ContextCompat.registerReceiver(
                    applicationContext,
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                continuation.invokeOnCancellation {
                    runCatching { applicationContext.unregisterReceiver(receiver) }
                }
                val permissionIntent = PendingIntent.getBroadcast(
                    applicationContext,
                    deviceId,
                    Intent(ACTION_USB_PERMISSION).setPackage(applicationContext.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                runCatching { manager.requestPermission(device, permissionIntent) }
                    .onFailure {
                        runCatching { applicationContext.unregisterReceiver(receiver) }
                        if (continuation.isActive) continuation.resume(false)
                    }
            }
        } ?: false
    }

    override fun createOpenDocumentTreeIntent(deviceId: String?): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && deviceId != null) {
            val volume = findVolume(deviceId)
            if (volume != null) {
                runCatching { volume.createOpenDocumentTreeIntent() }.getOrNull()?.let { return it }
            }
        }
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
        }
    }

    override fun persistTreePermission(uri: Uri): Boolean {
        val resolver = applicationContext.contentResolver
        val readWrite = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        return runCatching {
            resolver.takePersistableUriPermission(uri, readWrite)
            true
        }.getOrElse {
            runCatching {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                true
            }.getOrDefault(false)
        }
    }

    override fun releaseTreePermission(uri: Uri) {
        runCatching {
            applicationContext.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.onFailure {
            runCatching {
                applicationContext.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }

    override fun openAuthorizedDirectory(uri: Uri): Boolean {
        return runCatching {
            val documentId = DocumentsContract.getTreeDocumentId(uri)
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(documentUri, DocumentsContract.Document.MIME_TYPE_DIR)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            applicationContext.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    override fun openSystemStorageSettings(): Boolean {
        val specific = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (startActivity(specific)) return true
        val fallback = Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startActivity(fallback)
    }

    private fun startActivity(intent: Intent): Boolean = runCatching {
        applicationContext.startActivity(intent)
        true
    }.getOrDefault(false)

    private fun findVolume(id: String): StorageVolume? {
        val manager = applicationContext.getSystemService(StorageManager::class.java) ?: return null
        return manager.storageVolumes.firstOrNull { volume ->
            runCatching { volume.uuid ?: PRIMARY_VOLUME_ID }.getOrNull() == id
        }
    }

    private fun mapStorageVolume(
        volume: StorageVolume,
        mountEntries: List<MountEntry>,
    ): StorageDevice {
        val uuid = runCatching { volume.uuid }.getOrNull()
        val id = uuid ?: PRIMARY_VOLUME_ID
        val description = runCatching { volume.getDescription(applicationContext) }
            .getOrNull().orEmpty().ifBlank { "存储设备" }
        val rawState = runCatching { volume.state }.getOrDefault("unknown")
        val removable = runCatching { volume.isRemovable }.getOrDefault(false)
        val emulated = runCatching { volume.isEmulated }.getOrDefault(false)
        val directory = storageDirectory(volume)
        val mountState = mapMountState(rawState)
        val capacity = if (directory != null && mountState in setOf(MountState.MOUNTED, MountState.READ_ONLY)) {
            readCapacity(directory)
        } else {
            null
        }
        val kind = when {
            !removable && emulated -> VolumeKind.INTERNAL
            description.contains("usb", ignoreCase = true) ||
                directory?.path?.contains("usb", ignoreCase = true) == true -> VolumeKind.USB
            removable -> VolumeKind.SD_CARD
            else -> VolumeKind.OTHER_REMOVABLE
        }
        return StorageDevice(
            id = id,
            name = description,
            kind = kind,
            mountState = mountState,
            rawState = rawState,
            uuid = uuid,
            directoryPath = directory?.absolutePath,
            isRemovable = removable,
            isEmulated = emulated,
            capacity = capacity,
            fileSystemDescription = detectFileSystem(directory, mountState, mountEntries),
        )
    }

    private fun storageDirectory(volume: StorageVolume): File? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return runCatching { volume.directory }.getOrNull()
        }
        return runCatching {
            val path = volume.javaClass.getMethod("getPath").invoke(volume) as? String
            path?.let(::File)
        }.getOrNull()
    }

    private fun readCapacity(directory: File): StorageCapacity? = runCatching {
        val stats = StatFs(directory.absolutePath)
        val total = stats.blockCountLong * stats.blockSizeLong
        val available = stats.availableBlocksLong * stats.blockSizeLong
        if (total > 0L && available in 0L..total) StorageCapacity(total, available) else null
    }.getOrNull()

    private fun readUsbDevices(): List<UsbDeviceInfo> {
        val manager = applicationContext.getSystemService(UsbManager::class.java) ?: return emptyList()
        return manager.deviceList.values.map { device ->
            UsbDeviceInfo(
                deviceId = device.deviceId,
                deviceName = device.deviceName,
                productName = runCatching { device.productName }.getOrNull(),
                manufacturerName = runCatching { device.manufacturerName }.getOrNull(),
                vendorId = device.vendorId,
                productId = device.productId,
                deviceClass = device.deviceClass,
                deviceSubclass = device.deviceSubclass,
                protocol = device.deviceProtocol,
                hasPermission = manager.hasPermission(device),
            )
        }.sortedBy { it.deviceName }
    }

    private fun readMountEntries(): List<MountEntry> = runCatching {
        File("/proc/mounts").useLines { lines ->
            lines.mapNotNull { line ->
                val columns = line.split(Regex("\\s+"))
                if (columns.size >= 3) {
                    MountEntry(unescapeMountPath(columns[1]), columns[2])
                } else {
                    null
                }
            }.toList()
        }
    }.getOrDefault(emptyList())

    private fun detectFileSystem(
        directory: File?,
        mountState: MountState,
        entries: List<MountEntry>,
    ): String {
        if (directory == null || !mountState.let { it == MountState.MOUNTED || it == MountState.READ_ONLY }) {
            return "挂载后可识别"
        }
        val path = runCatching { directory.canonicalPath.trimEnd('/') }.getOrDefault(directory.absolutePath)
        val fileSystem = entries.firstOrNull { entry ->
            val entryPath = runCatching { File(entry.path).canonicalPath.trimEnd('/') }.getOrDefault(entry.path)
            entryPath == path
        }?.type
        return when (fileSystem?.lowercase()) {
            "vfat" -> "FAT（vfat，具体版本未知）"
            "exfat" -> "exFAT"
            "ntfs", "ntfs3" -> "NTFS"
            "ext4" -> "ext4"
            "f2fs" -> "F2FS"
            else -> "由 Android 系统管理"
        }
    }

    private fun mapMountState(state: String): MountState = when (state) {
        Environment.MEDIA_MOUNTED -> MountState.MOUNTED
        Environment.MEDIA_MOUNTED_READ_ONLY -> MountState.READ_ONLY
        Environment.MEDIA_UNMOUNTED -> MountState.UNMOUNTED
        Environment.MEDIA_EJECTING -> MountState.EJECTING
        "ejected" -> MountState.EJECTED
        Environment.MEDIA_REMOVED -> MountState.REMOVED
        Environment.MEDIA_UNMOUNTABLE,
        Environment.MEDIA_BAD_REMOVAL,
        Environment.MEDIA_NOFS,
        Environment.MEDIA_SHARED,
        -> MountState.UNAVAILABLE
        else -> MountState.UNKNOWN
    }

    private fun unescapeMountPath(path: String): String = path
        .replace("\\040", " ")
        .replace("\\011", "\t")
        .replace("\\012", "\n")
        .replace("\\134", "\\")

    private data class MountEntry(val path: String, val type: String)

    private companion object {
        const val PRIMARY_VOLUME_ID = "primary"
        const val USB_PERMISSION_TIMEOUT_MS = 30_000L
        const val ACTION_USB_PERMISSION = "com.diskflow.app.action.USB_PERMISSION"
    }
}
