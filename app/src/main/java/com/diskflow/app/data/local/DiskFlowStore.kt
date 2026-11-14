package com.diskflow.app.data.local

import com.diskflow.app.domain.model.FormatPlan
import com.diskflow.app.domain.model.OperationEvent
import com.diskflow.app.domain.model.OperationEventType
import kotlinx.coroutines.flow.Flow

interface DiskFlowStore {
    val draft: Flow<FormatPlan?>
    val events: Flow<List<OperationEvent>>
    val authorizedTreeUri: Flow<String?>

    suspend fun saveDraft(plan: FormatPlan)
    suspend fun addEvent(plan: FormatPlan, type: OperationEventType, detail: String? = null)
    suspend fun deleteEvent(id: String)
    suspend fun clearEvents()
    suspend fun saveAuthorizedTreeUri(uri: String)
    suspend fun clearAuthorizedTreeUri()
}
