package com.diskflow.app.ui

import android.content.Intent
import android.net.Uri
import com.diskflow.app.data.local.DiskFlowStore
import com.diskflow.app.data.storage.StorageRepository
import com.diskflow.app.domain.model.FileSystemType
import com.diskflow.app.domain.model.FormatPlan
import com.diskflow.app.domain.model.MountState
import com.diskflow.app.domain.model.OperationEvent
import com.diskflow.app.domain.model.OperationEventType
import com.diskflow.app.domain.model.StorageDevice
import com.diskflow.app.domain.model.StorageSnapshot
import com.diskflow.app.domain.model.StorageUseCase
import com.diskflow.app.domain.model.VolumeKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiskFlowViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `scan selects a real external device`() = runTest {
        val viewModel = createViewModel()
        assertEquals("usb-1", viewModel.format.value.plan.deviceId)
        assertEquals("OTG U 盘", viewModel.format.value.plan.deviceName)
        assertFalse(viewModel.devices.value.isRefreshing)
    }

    @Test
    fun `scenario and legacy switch update recommendation`() = runTest {
        val viewModel = createViewModel()
        viewModel.selectUseCase(StorageUseCase.WINDOWS)
        assertEquals(FileSystemType.NTFS, viewModel.format.value.plan.fileSystem)
        viewModel.selectUseCase(StorageUseCase.CROSS_PLATFORM)
        viewModel.setLegacySupport(true)
        assertEquals(FileSystemType.FAT32, viewModel.format.value.plan.fileSystem)
        viewModel.selectUseCase(StorageUseCase.LINUX_ANDROID)
        assertEquals(FileSystemType.EXT4, viewModel.format.value.plan.fileSystem)
    }

    @Test
    fun `invalid label cannot create plan`() = runTest {
        val store = FakeStore()
        val viewModel = createViewModel(store = store)
        viewModel.updateVolumeLabel("BAD/NAME")
        viewModel.createPlan()
        assertEquals(WizardStage.CONFIGURING, viewModel.format.value.stage)
        assertTrue(store.recordedTypes.isEmpty())
        assertTrue(viewModel.message.value?.isError == true)
    }

    @Test
    fun `plan and system jump record facts without fake success`() = runTest {
        val store = FakeStore()
        val repository = FakeStorageRepository()
        val viewModel = createViewModel(repository, store)

        viewModel.createPlan()
        assertEquals(WizardStage.READY, viewModel.format.value.stage)
        assertFalse(viewModel.format.value.completionMarked)
        assertEquals(listOf(OperationEventType.PLAN_CREATED), store.recordedTypes)

        viewModel.openSystemStorageSettings()
        assertEquals(1, repository.openSettingsCalls)
        assertFalse(viewModel.format.value.completionMarked)
        assertEquals(
            listOf(OperationEventType.PLAN_CREATED, OperationEventType.SYSTEM_SETTINGS_OPENED),
            store.recordedTypes,
        )

        viewModel.markCompletedByUser()
        viewModel.markCompletedByUser()
        assertTrue(viewModel.format.value.completionMarked)
        assertEquals(3, store.recordedTypes.size)
        assertEquals(OperationEventType.USER_MARKED_COMPLETE, store.recordedTypes.last())
    }

    @Test
    fun `cancel returns to configuration and records no device mutation`() = runTest {
        val store = FakeStore()
        val viewModel = createViewModel(store = store)
        viewModel.createPlan()
        viewModel.cancelPlan()
        assertEquals(WizardStage.CONFIGURING, viewModel.format.value.stage)
        assertEquals(OperationEventType.CANCELLED, store.recordedTypes.last())
        assertTrue(viewModel.events.value.none { it.type.name == "FORMAT_SUCCEEDED" })
    }

    @Test
    fun `no external device blocks plan creation`() = runTest {
        val store = FakeStore()
        val viewModel = createViewModel(FakeStorageRepository(StorageSnapshot()), store)
        viewModel.createPlan()
        assertEquals(WizardStage.CONFIGURING, viewModel.format.value.stage)
        assertTrue(store.recordedTypes.isEmpty())
    }

    @Test
    fun `file checker accepts decimal sizes and enforces FAT32`() = runTest {
        val viewModel = createViewModel()
        viewModel.selectToolFileSystem(FileSystemType.FAT32)
        viewModel.updateFileSizeInput("3.5")
        viewModel.selectFileSizeUnit(FileSizeUnit.GIB)
        assertTrue(viewModel.fileSizeCheck().isSupported)
        viewModel.updateFileSizeInput("4")
        assertFalse(viewModel.fileSizeCheck().isSupported)
        viewModel.selectToolFileSystem(FileSystemType.EXFAT)
        assertTrue(viewModel.fileSizeCheck().isSupported)
        viewModel.updateFileSizeInput("-1")
        assertFalse(viewModel.fileSizeCheck().isSupported)
    }

    private fun createViewModel(
        repository: FakeStorageRepository = FakeStorageRepository(),
        store: FakeStore = FakeStore(),
    ) = DiskFlowViewModel(repository, store)
}

private class FakeStorageRepository(
    private val snapshot: StorageSnapshot = StorageSnapshot(devices = listOf(testDevice())),
) : StorageRepository {
    var openSettingsCalls = 0
    var openSettingsResult = true

    override suspend fun scan(): StorageSnapshot = snapshot

    override suspend fun requestUsbPermission(deviceId: Int): Boolean = true

    override fun createOpenDocumentTreeIntent(deviceId: String?): Intent =
        throw UnsupportedOperationException("Not used in this test")

    override fun persistTreePermission(uri: Uri): Boolean = true

    override fun releaseTreePermission(uri: Uri) = Unit

    override fun openAuthorizedDirectory(uri: Uri): Boolean = true

    override fun openSystemStorageSettings(): Boolean {
        openSettingsCalls++
        return openSettingsResult
    }
}

private class FakeStore : DiskFlowStore {
    private val draftState = MutableStateFlow<FormatPlan?>(null)
    private val eventState = MutableStateFlow<List<OperationEvent>>(emptyList())
    private val treeState = MutableStateFlow<String?>(null)
    val recordedTypes = mutableListOf<OperationEventType>()

    override val draft: Flow<FormatPlan?> = draftState
    override val events: Flow<List<OperationEvent>> = eventState
    override val authorizedTreeUri: Flow<String?> = treeState

    override suspend fun saveDraft(plan: FormatPlan) {
        draftState.value = plan
    }

    override suspend fun addEvent(plan: FormatPlan, type: OperationEventType) {
        recordedTypes += type
        eventState.value = eventState.value + OperationEvent(
            id = "${type.name}-${recordedTypes.size}",
            type = type,
            deviceName = plan.deviceName,
            fileSystem = plan.fileSystem,
            timestamp = recordedTypes.size.toLong(),
            detail = "test",
        )
    }

    override suspend fun deleteEvent(id: String) {
        eventState.value = eventState.value.filterNot { it.id == id }
    }

    override suspend fun clearEvents() {
        eventState.value = emptyList()
    }

    override suspend fun saveAuthorizedTreeUri(uri: String) {
        treeState.value = uri
    }

    override suspend fun clearAuthorizedTreeUri() {
        treeState.value = null
    }
}

private fun testDevice() = StorageDevice(
    id = "usb-1",
    name = "OTG U 盘",
    kind = VolumeKind.USB,
    mountState = MountState.MOUNTED,
    rawState = "mounted",
    uuid = "usb-1",
    directoryPath = "/storage/usb",
    isRemovable = true,
    isEmulated = false,
    capacity = null,
    fileSystemDescription = "exFAT",
)
