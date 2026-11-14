package com.diskflow.app.data.storage

import android.content.Intent
import android.net.Uri
import com.diskflow.app.domain.model.StorageSnapshot

interface StorageRepository {
    suspend fun scan(): StorageSnapshot
    suspend fun requestUsbPermission(deviceId: Int): Boolean
    fun createOpenDocumentTreeIntent(deviceId: String?): Intent
    fun persistTreePermission(uri: Uri): Boolean
    fun releaseTreePermission(uri: Uri)
    fun openAuthorizedDirectory(uri: Uri): Boolean
    fun openSystemStorageSettings(): Boolean
}
