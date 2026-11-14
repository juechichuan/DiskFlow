package com.diskflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.diskflow.app.data.format.RootFormatEngine
import com.diskflow.app.data.local.DiskFlowDataStore
import com.diskflow.app.data.storage.AndroidStorageRepository
import com.diskflow.app.ui.DiskFlowViewModel
import com.diskflow.app.ui.navigation.DiskFlowApp
import com.diskflow.app.ui.theme.DiskFlowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(8, 13, 20)),
        )
        val storageRepository = AndroidStorageRepository(applicationContext)
        val dataStore = DiskFlowDataStore(applicationContext)
        val formatEngine = RootFormatEngine()
        val viewModel by viewModels<DiskFlowViewModel> {
            DiskFlowViewModel.Factory(storageRepository, dataStore, formatEngine)
        }
        setContent {
            DiskFlowTheme {
                DiskFlowApp(viewModel)
            }
        }
    }
}
