package com.diskflow.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.diskflow.app.domain.model.FormatPlan
import com.diskflow.app.domain.model.OperationEvent
import com.diskflow.app.domain.model.OperationEventType
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.diskFlowDataStore by preferencesDataStore(name = "diskflow")

class DiskFlowDataStore(context: Context) : DiskFlowStore {
    private val dataStore = context.applicationContext.diskFlowDataStore
    private val json = Json { ignoreUnknownKeys = true }

    override val draft: Flow<FormatPlan?> = dataStore.data.safe().map { preferences ->
        preferences[Keys.DRAFT]?.let { encoded ->
            runCatching { json.decodeFromString<FormatPlan>(encoded) }.getOrNull()
        }
    }

    override val events: Flow<List<OperationEvent>> = dataStore.data.safe().map { preferences ->
        preferences[Keys.EVENTS]?.let { encoded ->
            runCatching { json.decodeFromString<List<OperationEvent>>(encoded) }
                .getOrDefault(emptyList())
        }.orEmpty().sortedByDescending { it.timestamp }
    }

    override val authorizedTreeUri: Flow<String?> = dataStore.data.safe().map { preferences ->
        preferences[Keys.TREE_URI]
    }

    override suspend fun saveDraft(plan: FormatPlan) {
        dataStore.edit { preferences ->
            preferences[Keys.DRAFT] = json.encodeToString(
                FormatPlan.serializer(),
                plan.copy(updatedAt = System.currentTimeMillis()),
            )
        }
    }

    override suspend fun addEvent(plan: FormatPlan, type: OperationEventType, detail: String?) {
        val text = detail ?: when (type) {
            OperationEventType.PLAN_CREATED -> "已保存 ${plan.fileSystem.displayName} 方案，尚未执行格式化"
            OperationEventType.SYSTEM_SETTINGS_OPENED -> "已打开系统存储设置，后续操作由系统管理"
            OperationEventType.USER_MARKED_COMPLETE -> "用户确认已在系统工具中完成操作"
            OperationEventType.CANCELLED -> "已取消方案，未修改存储设备"
            OperationEventType.FORMAT_SUCCEEDED -> "Root 格式化命令执行成功"
            OperationEventType.FORMAT_FAILED -> "Root 格式化命令执行失败"
        }
        val event = OperationEvent(
            id = UUID.randomUUID().toString(),
            type = type,
            deviceName = plan.deviceName.ifBlank { "未指定设备" },
            fileSystem = plan.fileSystem,
            timestamp = System.currentTimeMillis(),
            detail = text,
        )
        dataStore.edit { preferences ->
            val current = preferences[Keys.EVENTS]?.let { encoded ->
                runCatching { json.decodeFromString<List<OperationEvent>>(encoded) }
                    .getOrDefault(emptyList())
            }.orEmpty()
            val updated = (listOf(event) + current).take(MAX_EVENTS)
            preferences[Keys.EVENTS] = json.encodeToString(updated)
        }
    }

    override suspend fun deleteEvent(id: String) {
        dataStore.edit { preferences ->
            val current = preferences[Keys.EVENTS]?.let { encoded ->
                runCatching { json.decodeFromString<List<OperationEvent>>(encoded) }
                    .getOrDefault(emptyList())
            }.orEmpty()
            preferences[Keys.EVENTS] = json.encodeToString(current.filterNot { it.id == id })
        }
    }

    override suspend fun clearEvents() {
        dataStore.edit { preferences -> preferences.remove(Keys.EVENTS) }
    }

    override suspend fun saveAuthorizedTreeUri(uri: String) {
        dataStore.edit { preferences -> preferences[Keys.TREE_URI] = uri }
    }

    override suspend fun clearAuthorizedTreeUri() {
        dataStore.edit { preferences -> preferences.remove(Keys.TREE_URI) }
    }

    private fun Flow<androidx.datastore.preferences.core.Preferences>.safe() =
        catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }

    private object Keys {
        val DRAFT = stringPreferencesKey("format_draft")
        val EVENTS = stringPreferencesKey("operation_events")
        val TREE_URI = stringPreferencesKey("authorized_tree_uri")
    }

    private companion object {
        const val MAX_EVENTS = 100
    }
}
