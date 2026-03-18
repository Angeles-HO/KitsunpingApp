package app.kitsunping.ui.screens

import android.R as AndroidR
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.kitsunping.R
import app.kitsunping.InstalledAppEntry
import app.kitsunping.ModuleSnapshot
import app.kitsunping.TargetPolicyRule
import app.kitsunping.feature.speedtest.SpeedTestRunConfig
import app.kitsunping.feature.speedtest.SpeedTestScreen
import app.kitsunping.feature.speedtest.SpeedTestUiState
import app.kitsunping.ui.animation.AppMotion
import app.kitsunping.ui.model.AdvancedDialog
import app.kitsunping.ui.screens.router.RouterScreen
import app.kitsunping.ui.screens.router.detectGatewayIp
import app.kitsunping.ui.theme.AppThemeMode
import app.kitsunping.ui.theme.KitsunpingTheme

private enum class MainTab { HOME, ROUTER, APPS, SPEED, SETTINGS }

private val mainTabsOrder = listOf(MainTab.HOME, MainTab.ROUTER, MainTab.APPS, MainTab.SPEED, MainTab.SETTINGS)

private fun nextMainTab(current: MainTab): MainTab {
    val index = mainTabsOrder.indexOf(current)
    if (index == -1) return current
    return mainTabsOrder.getOrElse(index + 1) { current }
}

private fun previousMainTab(current: MainTab): MainTab {
    val index = mainTabsOrder.indexOf(current)
    if (index == -1) return current
    return mainTabsOrder.getOrElse(index - 1) { current }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitsunpingApp(
    snapshot: ModuleSnapshot,
    highlightPulse: Boolean,
    routerFiles: List<String> = emptyList(),
    advancedDialog: AdvancedDialog? = null,
    currentThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    reducedMotionEnabled: Boolean = false,
    developerMode: Boolean = false,
    routerPaired: Boolean = false,
    routerIp: String = "",
    routerId: String = "",
    routerHasToken: Boolean = false,
    routerSignatureBssid: String = "",
    routerSignatureBand: String = "",
    routerSignatureChannel: String = "",
    routerSignatureWidth: String = "",
    scannedPairPayload: String = "",
    installedApps: List<InstalledAppEntry> = emptyList(),
    appPolicies: Map<String, TargetPolicyRule> = emptyMap(),
    runtimePolicies: Map<String, TargetPolicyRule> = emptyMap(),
    policySyncSummary: List<String> = emptyList(),
    policyApplyInProgress: Boolean = false,
    moduleIntegrityViewVisible: Boolean = false,
    moduleIntegrityViewLoading: Boolean = false,
    moduleIntegrityViewReport: String = "",
    speedTestState: SpeedTestUiState = SpeedTestUiState(),
    onDismissDialog: () -> Unit = {},
    onDismissModuleIntegrityView: () -> Unit = {},
    onRequestCalibrate: () -> Unit = {},
    onRequestStart: () -> Unit = {},
    onRequestRestart: () -> Unit = {},
    onRequestCheckDaemonPid: () -> Unit = {},
    onRequestProfile: (String) -> Unit = {},
    onRequestThemeMode: (AppThemeMode) -> Unit = {},
    onRequestReducedMotion: (Boolean) -> Unit = {},
    onRequestDeveloperMode: (Boolean) -> Unit = {},
    lowNetworkSimulationEnabled: Boolean = false,
    onRequestLowNetworkSimulation: (Boolean) -> Unit = {},
    ipv6CalibrationEnabled: Boolean = false,
    onRequestIpv6Calibration: (Boolean) -> Unit = {},
    granularLatencyEnabled: Boolean = false,
    onRequestGranularLatency: (Boolean) -> Unit = {},
    lowNetworkTestOffset: Int = 0,
    onRequestLowNetworkTestOffset: (Int) -> Unit = {},
    bootCustomProfile: String = "none",
    onRequestBootCustomProfile: (String) -> Unit = {},
    onRequestPairRouter: (String, String) -> Unit = { _, _ -> },
    onRequestUnpairRouter: () -> Unit = {},
    onRequestScanQr: () -> Unit = {},
    onRequestChannelScan: () -> Unit = {},
    onReadChannelCache: () -> String? = { null },
    onApplyChannelChange: (Int, String) -> Unit = { _, _ -> },
    onReadChannelApplyStatus: () -> String? = { null },
    onRequestOpenAppSettings: () -> Unit = {},
    onRequestRefreshRouterFiles: () -> Unit = {},
    onRequestReadFile: (String, String) -> Unit = { _, _ -> },
    onRequestViewRouterLast: () -> Unit = {},
    onRequestOpenRouterLast: () -> Unit = {},
    onRequestRunCommand: (String, String) -> Unit = { _, _ -> },
    onRequestModuleIntegrityCheck: () -> Unit = {},
    onRequestRunSpeedTest: (SpeedTestRunConfig) -> Unit = {},
    onRequestSaveAppPolicy: (String, String, String, Boolean) -> Unit = { _, _, _, _ -> },
    onRequestDeleteAppPolicy: (String) -> Unit = {},
    onRequestApplyPolicies: () -> Unit = {}
) {
    var currentTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var hideIp by rememberSaveable { mutableStateOf(true) }

    // Close integrity overlay if user changes section, to avoid stale modal hanging across tabs.
    LaunchedEffect(moduleIntegrityViewVisible, currentTab) {
        if (moduleIntegrityViewVisible && currentTab != MainTab.SETTINGS) {
            onDismissModuleIntegrityView()
        }
    }

    if (moduleIntegrityViewVisible) {
        BackHandler(onBack = onDismissModuleIntegrityView)
    }

    val swipeEnabled = currentTab != MainTab.APPS && advancedDialog == null && !moduleIntegrityViewVisible
    val swipeModifier = if (swipeEnabled) {
        Modifier.pointerInput(currentTab) {
            var totalHorizontalDrag = 0f
            detectHorizontalDragGestures(
                onHorizontalDrag = { change, dragAmount ->
                    totalHorizontalDrag += dragAmount
                    change.consume()
                },
                onDragEnd = {
                    when {
                        totalHorizontalDrag <= -96f -> {
                            currentTab = nextMainTab(currentTab)
                        }

                        totalHorizontalDrag >= 96f -> {
                            currentTab = previousMainTab(currentTab)
                        }
                    }
                    totalHorizontalDrag = 0f
                },
                onDragCancel = {
                    totalHorizontalDrag = 0f
                }
            )
        }
    } else {
        Modifier
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    val title = when (currentTab) {
                        MainTab.HOME -> "Kitsunping"
                        MainTab.ROUTER -> "Router"
                        MainTab.APPS -> "Prioridades"
                        MainTab.SPEED -> "Speed test"
                        MainTab.SETTINGS -> "Ajustes"
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.kitsunping_logo),
                            contentDescription = "Kitsunping",
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = title)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == MainTab.HOME,
                    onClick = { currentTab = MainTab.HOME },
                    icon = { Icon(Icons.Outlined.Home, contentDescription = "Inicio") },
                    label = { Text("Inicio") }
                )
                NavigationBarItem(
                    selected = currentTab == MainTab.ROUTER,
                    onClick = { currentTab = MainTab.ROUTER },
                    icon = { Icon(Icons.Outlined.Wifi, contentDescription = "Router") },
                    label = { Text("Router") }
                )
                NavigationBarItem(
                    selected = currentTab == MainTab.APPS,
                    onClick = { currentTab = MainTab.APPS },
                    icon = { Icon(Icons.Outlined.CheckCircle, contentDescription = "Apps") },
                    label = { Text("Apps") }
                )
                NavigationBarItem(
                    selected = currentTab == MainTab.SPEED,
                    onClick = { currentTab = MainTab.SPEED },
                    icon = { Icon(Icons.Outlined.Search, contentDescription = "Speed test") },
                    label = { Text("Test") }
                )
                NavigationBarItem(
                    selected = currentTab == MainTab.SETTINGS,
                    onClick = { currentTab = MainTab.SETTINGS },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = "Ajustes") },
                    label = { Text("Ajustes") }
                )
            }
        }
    ) { padding ->
        if (advancedDialog != null) {
            AlertDialog(
                onDismissRequest = onDismissDialog,
                confirmButton = {
                    Button(onClick = onDismissDialog) { Text(text = "Close") }
                },
                title = { Text(text = advancedDialog.title) },
                text = {
                    Text(
                        text = advancedDialog.content,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(swipeModifier)
        ) {
            AnimatedContent(
                targetState = currentTab,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val targetIdx = mainTabsOrder.indexOf(targetState)
                    val initialIdx = mainTabsOrder.indexOf(initialState)
                    if (targetIdx > initialIdx) {
                        (slideInHorizontally(
                            animationSpec = AppMotion.tabSlideInSpec(reducedMotionEnabled)
                        ) { fullWidth -> AppMotion.tabEnterOffsetX(fullWidth, reducedMotionEnabled, forward = true) } + fadeIn(
                            animationSpec = AppMotion.tabFadeInSpec(reducedMotionEnabled)
                        )).togetherWith(
                            slideOutHorizontally(
                                animationSpec = AppMotion.tabSlideOutSpec(reducedMotionEnabled)
                            ) { fullWidth -> AppMotion.tabExitOffsetX(fullWidth, reducedMotionEnabled, forward = true) } + fadeOut(
                                animationSpec = AppMotion.tabFadeOutSpec(reducedMotionEnabled)
                            )
                        )
                    } else {
                        (slideInHorizontally(
                            animationSpec = AppMotion.tabSlideInSpec(reducedMotionEnabled)
                        ) { fullWidth -> AppMotion.tabEnterOffsetX(fullWidth, reducedMotionEnabled, forward = false) } + fadeIn(
                            animationSpec = AppMotion.tabFadeInSpec(reducedMotionEnabled)
                        )).togetherWith(
                            slideOutHorizontally(
                                animationSpec = AppMotion.tabSlideOutSpec(reducedMotionEnabled)
                            ) { fullWidth -> AppMotion.tabExitOffsetX(fullWidth, reducedMotionEnabled, forward = false) } + fadeOut(
                                animationSpec = AppMotion.tabFadeOutSpec(reducedMotionEnabled)
                            )
                        )
                    }
                        .using(SizeTransform(clip = false))
                },
                label = "tabTransition"
            ) { tab ->
            when (tab) {
                MainTab.HOME -> HomeScreen(
                    contentPadding = padding,
                    snapshot = snapshot,
                    highlightPulse = highlightPulse,
                    reducedMotionEnabled = reducedMotionEnabled,
                    lowNetworkSimulationEnabled = lowNetworkSimulationEnabled,
                    onRequestStart = onRequestStart,
                    onRequestRestart = onRequestRestart,
                    onRequestCalibrate = onRequestCalibrate,
                    onRequestProfile = onRequestProfile
                )

                MainTab.ROUTER -> RouterScreen(
                    contentPadding = padding,
                    daemonState = snapshot.daemonState,
                    lastEvent = snapshot.lastEvent,
                    signatureBssid = routerSignatureBssid,
                    signatureBand = routerSignatureBand,
                    signatureChannel = routerSignatureChannel,
                    signatureWidth = routerSignatureWidth,
                    paired = routerPaired,
                    policyEventName = snapshot.policyEvent.event,
                    suggestedRouterIp = detectGatewayIp(snapshot.daemonState, routerIp),
                    routerIp = routerIp,
                    routerId = routerId,
                    hasToken = routerHasToken,
                    scannedPairPayload = scannedPairPayload,
                    hideIp = hideIp,
                    onToggleHideIp = { hideIp = !hideIp },
                    onRequestPairRouter = onRequestPairRouter,
                    onRequestUnpairRouter = onRequestUnpairRouter,
                    onRequestScanQr = onRequestScanQr,
                    onRequestChannelScan = onRequestChannelScan,
                    onReadChannelCache = onReadChannelCache,
                    onApplyChannelChange = onApplyChannelChange,
                    onReadChannelApplyStatus = onReadChannelApplyStatus
                )

                MainTab.APPS -> AppPrioritiesScreen(
                    contentPadding = padding,
                    installedApps = installedApps,
                    appPolicies = appPolicies,
                    runtimePolicies = runtimePolicies,
                    syncSummary = policySyncSummary,
                    targetState = snapshot.targetState,
                    targetStateReason = snapshot.targetStateReason,
                    targetStateHistory = snapshot.targetStateHistory,
                    policyRequest = snapshot.policyRequest,
                    routerPaired = routerPaired,
                    reducedMotionEnabled = reducedMotionEnabled,
                    applyInProgress = policyApplyInProgress,
                    onRequestSaveAppPolicy = onRequestSaveAppPolicy,
                    onRequestDeleteAppPolicy = onRequestDeleteAppPolicy,
                    onRequestApplyPolicies = onRequestApplyPolicies
                )

                MainTab.SPEED -> SpeedTestScreen(
                    contentPadding = padding,
                    state = speedTestState,
                    onRequestRunSpeedTest = onRequestRunSpeedTest
                )

                MainTab.SETTINGS -> SettingsScreen(
                    contentPadding = padding,
                    currentThemeMode = currentThemeMode,
                    reducedMotionEnabled = reducedMotionEnabled,
                    developerMode = developerMode,
                    lowNetworkSimulationEnabled = lowNetworkSimulationEnabled,
                    ipv6CalibrationEnabled = ipv6CalibrationEnabled,
                    granularLatencyEnabled = granularLatencyEnabled,
                    lowNetworkTestOffset = lowNetworkTestOffset,
                    bootCustomProfile = bootCustomProfile,
                    routerFiles = routerFiles,
                    onRequestThemeMode = onRequestThemeMode,
                    onRequestReducedMotion = onRequestReducedMotion,
                    onRequestDeveloperMode = onRequestDeveloperMode,
                    onRequestLowNetworkSimulation = onRequestLowNetworkSimulation,
                    onRequestIpv6Calibration = onRequestIpv6Calibration,
                    onRequestGranularLatency = onRequestGranularLatency,
                    onRequestLowNetworkTestOffset = onRequestLowNetworkTestOffset,
                    onRequestBootCustomProfile = onRequestBootCustomProfile,
                    onRequestOpenAppSettings = onRequestOpenAppSettings,
                    onRequestCheckDaemonPid = onRequestCheckDaemonPid,
                    onRequestRefreshRouterFiles = onRequestRefreshRouterFiles,
                    onRequestReadFile = onRequestReadFile,
                    onRequestViewRouterLast = onRequestViewRouterLast,
                    onRequestOpenRouterLast = onRequestOpenRouterLast,
                    onRequestRunCommand = onRequestRunCommand,
                    onRequestModuleIntegrityCheck = onRequestModuleIntegrityCheck
                )
            }
            } // AnimatedContent

            if (moduleIntegrityViewVisible) {
                ModuleIntegrityOverlay(
                    loading = moduleIntegrityViewLoading,
                    report = moduleIntegrityViewReport,
                    onClose = onDismissModuleIntegrityView,
                    onRefresh = onRequestModuleIntegrityCheck
                )
            }
        }
    }
}

@Composable
private fun ModuleIntegrityOverlay(
    loading: Boolean,
    report: String,
    onClose: () -> Unit,
    onRefresh: () -> Unit
) {
    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Dim background to keep user focused on integrity output.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onClose() })
                }
        )
        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Integrity Check", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onClose) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close integrity view")
                    }
                }
                if (loading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                        Text(text = "Executing verification...")
                    }
                }
                Text(
                    text = if (report.isBlank()) "No report" else report,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = onRefresh, modifier = Modifier.weight(1f), enabled = !loading) {
                        Text("Recheck")
                    }
                    Button(onClick = onClose, modifier = Modifier.weight(1f)) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun KitsunpingPreview() {
    KitsunpingTheme {
        KitsunpingApp(ModuleSnapshot.empty(), false)
    }
}
