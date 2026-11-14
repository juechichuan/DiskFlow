package com.diskflow.app.ui.navigation

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.diskflow.app.ui.DiskFlowViewModel
import com.diskflow.app.ui.components.EmptyState
import com.diskflow.app.ui.screens.DeviceDetailScreen
import com.diskflow.app.ui.screens.DevicesScreen
import com.diskflow.app.ui.screens.FormatScreen
import com.diskflow.app.ui.screens.HistoryScreen
import com.diskflow.app.ui.screens.ToolsScreen
import com.diskflow.app.ui.theme.Ink
import com.diskflow.app.ui.theme.Panel

@Composable
fun DiskFlowApp(viewModel: DiskFlowViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val devicesState by viewModel.devices.collectAsStateWithLifecycle()
    val formatState by viewModel.format.collectAsStateWithLifecycle()
    val toolsState by viewModel.tools.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val authorizedTreeUri by viewModel.authorizedTreeUri.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = TopLevelDestination.entries.any { it.route == currentRoute }

    val safLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onTreeSelected(result.data?.data)
        } else {
            viewModel.onTreeSelected(null)
        }
    }

    fun launchSaf(deviceId: String?) {
        runCatching {
            safLauncher.launch(viewModel.createOpenDocumentTreeIntent(deviceId))
        }.onFailure {
            viewModel.onTreeSelectionFailed()
        }
    }

    LaunchedEffect(message?.id) {
        val current = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(current.text, duration = SnackbarDuration.Short)
        viewModel.consumeMessage()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Panel.copy(alpha = 0.45f), Ink))),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = if (message?.isError == true) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        contentColor = if (message?.isError == true) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            },
            bottomBar = {
                AnimatedVisibility(
                    visible = showBottomBar,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    DiskFlowBottomBar(navController, currentRoute)
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = TopLevelDestination.DEVICES.route,
                modifier = Modifier
                    .padding(innerPadding)
                    .statusBarsPadding(),
                enterTransition = { fadeIn(tween(180)) },
                exitTransition = { fadeOut(tween(140)) },
                popEnterTransition = { fadeIn(tween(180)) },
                popExitTransition = { fadeOut(tween(140)) },
            ) {
                composable(TopLevelDestination.DEVICES.route) {
                    DevicesScreen(
                        state = devicesState,
                        onRefresh = viewModel::refreshDevices,
                        onDeviceClick = { device ->
                            navController.navigate("device/${Uri.encode(device.id)}")
                        },
                    )
                }
                composable(TopLevelDestination.FORMAT.route) {
                    FormatScreen(
                        state = formatState,
                        devices = devicesState.snapshot.devices,
                        onSelectDevice = viewModel::selectDevice,
                        onSelectUseCase = viewModel::selectUseCase,
                        onLegacySupportChange = viewModel::setLegacySupport,
                        onSelectFileSystem = viewModel::selectFileSystem,
                        onVolumeLabelChange = viewModel::updateVolumeLabel,
                        onSelectAllocationUnit = viewModel::selectAllocationUnit,
                        onCreatePlan = viewModel::createPlan,
                        onBackToConfiguration = viewModel::backToConfiguration,
                        onOpenSystemSettings = viewModel::openSystemStorageSettings,
                        onMarkCompleted = viewModel::markCompletedByUser,
                        onCancel = viewModel::cancelPlan,
                        onExecuteFormat = viewModel::executeFormat,
                        onRefreshCapabilities = viewModel::refreshCapabilities,
                    )
                }
                composable(TopLevelDestination.TOOLS.route) {
                    ToolsScreen(
                        state = toolsState,
                        fileSizeCheck = viewModel.fileSizeCheck(),
                        authorizedTreeUri = authorizedTreeUri,
                        preferredDeviceName = formatState.plan.deviceName.ifBlank { null },
                        usbDevices = devicesState.snapshot.usbDevices,
                        usbRequests = devicesState.usbRequests,
                        onSelectFileSystem = viewModel::selectToolFileSystem,
                        onFileSizeChange = viewModel::updateFileSizeInput,
                        onSelectUnit = viewModel::selectFileSizeUnit,
                        onAuthorizeDirectory = {
                            launchSaf(formatState.plan.deviceId.ifBlank { null })
                        },
                        onOpenAuthorizedDirectory = viewModel::openAuthorizedDirectory,
                        onRemoveAuthorizedDirectory = viewModel::removeAuthorizedDirectory,
                        onRequestUsbPermission = viewModel::requestUsbPermission,
                    )
                }
                composable(TopLevelDestination.HISTORY.route) {
                    HistoryScreen(
                        events = events,
                        onDelete = viewModel::deleteEvent,
                        onClear = viewModel::clearEvents,
                    )
                }
                composable("device/{deviceId}") { entry ->
                    val id = Uri.decode(entry.arguments?.getString("deviceId").orEmpty())
                    val device = devicesState.snapshot.devices.firstOrNull { it.id == id }
                    if (device == null) {
                        MissingDevice(onBack = navController::popBackStack)
                    } else {
                        DeviceDetailScreen(
                            device = device,
                            scannedAt = devicesState.snapshot.scannedAt,
                            onBack = navController::popBackStack,
                            onConfigure = {
                                viewModel.selectDevice(device)
                                navController.navigateTopLevel(TopLevelDestination.FORMAT)
                            },
                            onOpenSystemSettings = {
                                viewModel.selectDevice(device)
                                viewModel.openSystemStorageSettings()
                            },
                            onAuthorizeDirectory = { launchSaf(device.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiskFlowBottomBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar(containerColor = Panel, tonalElevation = 0.dp) {
        TopLevelDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { navController.navigateTopLevel(destination) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(destination.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

private fun NavHostController.navigateTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun MissingDevice(onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 80.dp)) {
        EmptyState(
            icon = Icons.Rounded.Storage,
            title = "设备已断开或信息已更新",
            message = "返回设备页刷新后即可看到最新状态。",
            action = { TextButton(onClick = onBack) { Text("返回设备页") } },
        )
    }
}

private enum class TopLevelDestination(val route: String, val label: String, val icon: ImageVector) {
    DEVICES("devices", "设备", Icons.Rounded.Storage),
    FORMAT("format", "格式化", Icons.Rounded.Tune),
    TOOLS("tools", "工具", Icons.Rounded.Widgets),
    HISTORY("history", "记录", Icons.Rounded.History),
}
