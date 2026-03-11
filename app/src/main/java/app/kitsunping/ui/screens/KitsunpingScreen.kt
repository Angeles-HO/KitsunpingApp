package app.kitsunping.ui.screens

import android.R as AndroidR
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject
import app.kitsunping.R
import app.kitsunping.InstalledAppEntry
import app.kitsunping.LastEvent
import app.kitsunping.ModuleSnapshot
import app.kitsunping.TargetPolicyRule
import app.kitsunping.feature.speedtest.SpeedTestRunConfig
import app.kitsunping.feature.speedtest.SpeedTestScreen
import app.kitsunping.feature.speedtest.SpeedTestUiState
import app.kitsunping.ui.components.CardHeaderWithInfo
import app.kitsunping.ui.components.HighlightCard
import app.kitsunping.ui.model.AdvancedDialog
import app.kitsunping.ui.theme.AppThemeMode
import app.kitsunping.ui.theme.KitsunpingTheme
import app.kitsunping.ui.utils.displayName
import app.kitsunping.ui.utils.formatEpoch
import java.util.Locale

private data class EmaPoint(
    val tsMs: Long,
    val value: Float
)

private data class LatencyUi(
    val label: String,
    val source: String
)

private data class AppRuleUiModel(
    val packageName: String,
    val appLabel: String,
    val iconBitmap: Bitmap?,
    val profile: String,
    val priority: String,
    val enabled: Boolean,
    val state: RuleStatus,
    val routerConnected: Boolean
)

private data class ChannelRecommendation(
    val channel: Int,
    val score: Int,
    val scoreGap: Int,
    val rfModel: String,
    val band: String,
    val currentChannel: Int?
)

private fun JSONObject.optIntFlexible(vararg keys: String, defaultValue: Int = 0): Int {
    for (key in keys) {
        if (!this.has(key)) continue
        val raw = this.opt(key)
        when (raw) {
            is Number -> return raw.toInt()
            is String -> raw.toIntOrNull()?.let { return it }
        }
    }
    return defaultValue
}

private fun JSONObject.optStringFlexible(vararg keys: String, defaultValue: String = "unknown"): String {
    for (key in keys) {
        val value = this.optString(key, "")
        if (value.isNotBlank()) return value
    }
    return defaultValue
}

private fun parseChannelRecommendation(raw: String): ChannelRecommendation? {
    val root = JSONObject(raw)
    val candidate = root.optJSONObject("data") ?: root

    val channel = candidate.optIntFlexible(
        "recommended_channel",
        "recommendedChannel",
        "channel",
        defaultValue = 0
    )
    if (channel <= 0) return null

    // Extract score from candidates array if available
    var score = candidate.optIntFlexible("score", "recommendation_score", defaultValue = 0)
    if (score == 0) {
        val candidates = candidate.optJSONArray("candidates")
        if (candidates != null) {
            for (i in 0 until candidates.length()) {
                val cand = candidates.optJSONObject(i) ?: continue
                if (cand.optInt("channel", -1) == channel) {
                    score = cand.optInt("score", 0)
                    break
                }
            }
        }
    }

    val scoreGap = candidate.optIntFlexible("score_gap", "scoreGap", "gap", defaultValue = 0)
    
    // Extract RF model from scan_method or confidence field
    var rfModel = candidate.optStringFlexible("rf_model", "rfModel", "model", defaultValue = "")
    if (rfModel.isEmpty()) {
        val scanMethod = candidate.optString("scan_method", "")
        val confidence = candidate.optString("confidence", "")
        rfModel = when {
            scanMethod.isNotEmpty() -> scanMethod
            confidence.isNotEmpty() -> confidence
            else -> "unknown"
        }
    }
    
    val band = candidate.optStringFlexible("band", "wifi_band", defaultValue = "unknown")
    val currentChannelRaw = candidate.optIntFlexible(
        "current_channel",
        "currentChannel",
        defaultValue = 0
    )
    val currentChannel = currentChannelRaw.takeIf { it > 0 }

    return ChannelRecommendation(
        channel = channel,
        score = score,
        scoreGap = scoreGap,
        rfModel = rfModel,
        band = band,
        currentChannel = currentChannel
    )
}

private enum class MainTab { HOME, ROUTER, APPS, SPEED, SETTINGS }

private enum class RuleStatus { RUNTIME_SYNCED, PENDING_SYNC, ROUTER_FAILED }

private enum class EditorMode { ADD, EDIT }

private val mainTabsOrder = listOf(MainTab.HOME, MainTab.ROUTER, MainTab.APPS, MainTab.SPEED, MainTab.SETTINGS)
private val appIconFallbackCache = mutableMapOf<String, Bitmap?>()

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

private fun resolveRuleIconBitmap(
    context: android.content.Context,
    packageName: String,
    preloaded: Bitmap?
): Bitmap? {
    if (preloaded != null) return preloaded
    val cacheKey = packageName.lowercase(Locale.ROOT)
    if (appIconFallbackCache.containsKey(cacheKey)) {
        return appIconFallbackCache[cacheKey]
    }

    val loaded = runCatching {
        val drawable = context.packageManager.getApplicationIcon(packageName)
        convertDrawableToBitmap(drawable, width = 96, height = 96)
    }.getOrNull()

    appIconFallbackCache[cacheKey] = loaded
    return loaded
}

private fun convertDrawableToBitmap(drawable: Drawable, width: Int, height: Int): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        val bitmap = drawable.bitmap
        return if (bitmap.width != width || bitmap.height != height) {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        } else {
            bitmap
        }
    }

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitsunpingApp(
    snapshot: ModuleSnapshot,
    highlightPulse: Boolean,
    routerFiles: List<String> = emptyList(),
    advancedDialog: AdvancedDialog? = null,
    currentThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
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
    speedTestState: SpeedTestUiState = SpeedTestUiState(),
    onDismissDialog: () -> Unit = {},
    onRequestCalibrate: () -> Unit = {},
    onRequestStart: () -> Unit = {},
    onRequestRestart: () -> Unit = {},
    onRequestCheckDaemonPid: () -> Unit = {},
    onRequestProfile: (String) -> Unit = {},
    onRequestThemeMode: (AppThemeMode) -> Unit = {},
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
    onRequestRunSpeedTest: (SpeedTestRunConfig) -> Unit = {},
    onRequestSaveAppPolicy: (String, String, String, Boolean) -> Unit = { _, _, _, _ -> },
    onRequestApplyPolicies: () -> Unit = {}
) {
    var currentTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var hideIp by rememberSaveable { mutableStateOf(true) }

    val swipeEnabled = currentTab != MainTab.APPS && advancedDialog == null
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
                transitionSpec = {
                    val targetIdx = mainTabsOrder.indexOf(targetState)
                    val initialIdx = mainTabsOrder.indexOf(initialState)
                    if (targetIdx > initialIdx) {
                        (slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(220))).togetherWith(
                            slideOutHorizontally(tween(220)) { -it / 3 } + fadeOut(tween(180))
                        )
                    } else {
                        (slideInHorizontally(tween(280)) { -it / 3 } + fadeIn(tween(220))).togetherWith(
                            slideOutHorizontally(tween(220)) { it / 3 } + fadeOut(tween(180))
                        )
                    }
                },
                label = "tabTransition"
            ) { tab ->
            when (tab) {
                MainTab.HOME -> HomeScreen(
                    contentPadding = padding,
                    snapshot = snapshot,
                    highlightPulse = highlightPulse,
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
                    applyInProgress = policyApplyInProgress,
                    onRequestSaveAppPolicy = onRequestSaveAppPolicy,
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
                    developerMode = developerMode,
                    lowNetworkSimulationEnabled = lowNetworkSimulationEnabled,
                    ipv6CalibrationEnabled = ipv6CalibrationEnabled,
                    granularLatencyEnabled = granularLatencyEnabled,
                    lowNetworkTestOffset = lowNetworkTestOffset,
                    bootCustomProfile = bootCustomProfile,
                    routerFiles = routerFiles,
                    onRequestThemeMode = onRequestThemeMode,
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
                    onRequestRunCommand = onRequestRunCommand
                )
            }
            } // AnimatedContent
        }
    }
}

@Composable
private fun AppPrioritiesScreen(
    contentPadding: PaddingValues,
    installedApps: List<InstalledAppEntry>,
    appPolicies: Map<String, TargetPolicyRule>,
    runtimePolicies: Map<String, TargetPolicyRule>,
    syncSummary: List<String>,
    targetState: String,
    targetStateReason: String,
    targetStateHistory: List<String>,
    policyRequest: String,
    routerPaired: Boolean,
    applyInProgress: Boolean,
    onRequestSaveAppPolicy: (String, String, String, Boolean) -> Unit,
    onRequestApplyPolicies: () -> Unit
) {
    var editorVisible by rememberSaveable { mutableStateOf(false) }
    var editorMode by rememberSaveable { mutableStateOf(EditorMode.ADD) }
    var syncLogExpanded by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var selectedPackage by rememberSaveable { mutableStateOf("") }
    var selectedProfile by rememberSaveable { mutableStateOf("stable") }
    var selectedPriority by rememberSaveable { mutableStateOf("medium") }
    var selectedEnabled by rememberSaveable { mutableStateOf(true) }

    val normalizedSearch = search.trim().lowercase()
    val appEntryByPackage = remember(installedApps) {
        installedApps.associateBy { it.packageName.lowercase() }
    }
    val appLabelByPackage = remember(installedApps) {
        installedApps.associate { it.packageName.lowercase() to it.label }
    }
    val filteredApps = remember(installedApps, normalizedSearch) {
        installedApps
            .filter {
                if (normalizedSearch.isBlank()) return@filter true
                it.label.lowercase().contains(normalizedSearch) ||
                    it.packageName.lowercase().contains(normalizedSearch)
            }
            .take(12)
    }

    val appCards = appPolicies.entries.sortedWith(
        compareBy<Map.Entry<String, TargetPolicyRule>> {
            appLabelByPackage[it.key.lowercase()].orEmpty().ifBlank { it.key }
        }.thenBy { it.key }
    ).map { (pkg, rule) ->
        val normalizedPackage = pkg.lowercase()
        val appEntry = appEntryByPackage[normalizedPackage]
        val label = appEntry?.label.orEmpty().ifBlank { pkg }
        AppRuleUiModel(
            packageName = pkg,
            appLabel = label,
            iconBitmap = appEntry?.iconBitmap,
            profile = rule.profile,
            priority = rule.priority,
            enabled = rule.enabled,
            state = resolveRuleState(
                routerConnected = routerPaired,
                rule = rule,
                runtimeRule = runtimePolicies[pkg]
            ),
            routerConnected = routerPaired
        )
    }

    val pendingChanges = appCards.count { it.state == RuleStatus.PENDING_SYNC }
    val normalizedTargetState = normalizeTargetTransitionState(targetState)
    val normalizedTargetReason = targetStateReason.ifBlank { "none" }
    val terminalPolicyRequest = policyRequest.ifBlank { "none" }
    val terminalSyncStatus = syncSummary.firstOrNull().orEmpty().ifBlank { "waiting_sync" }
    val parsedTransitionHistory = remember(targetStateHistory) {
        targetStateHistory.mapNotNull { parseTargetTransitionEntry(it) }
    }
    val cardEntries = appCards.map { it }
    val cardRows = remember(cardEntries) {
        cardEntries.chunked(2)
    }
    val stickyVisible = applyInProgress || pendingChanges > 0

    fun openAddEditor() {
        editorMode = EditorMode.ADD
        search = ""
        selectedPackage = ""
        selectedProfile = "stable"
        selectedPriority = "medium"
        selectedEnabled = true
        editorVisible = true
    }

    fun openEditEditor(packageName: String, rule: TargetPolicyRule) {
        editorMode = EditorMode.EDIT
        search = ""
        val normalizedPackage = packageName.trim().lowercase()
        selectedPackage = normalizedPackage
        selectedProfile = rule.profile
        selectedPriority = rule.priority
        selectedEnabled = rule.enabled
        editorVisible = true
    }

    Box(
        modifier = Modifier
            .padding(contentPadding)
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = if (stickyVisible) 84.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CardHeaderWithInfo(
                        title = "Profiles Per App",
                        infoText = "Runtime synchronization is global, not per individual app."
                    )
                    Text(
                        text = if (applyInProgress) "Runtime: Syncing..." else "Runtime: Synced",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = if (routerPaired) "Router: Connected" else "Router: Not Connected ⚠",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (routerPaired) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                    Text(
                        text = "Saved Rules: ${appPolicies.size}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Text("Runtime match", style = MaterialTheme.typography.labelSmall)
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiary)
                            )
                            Text("Pending", style = MaterialTheme.typography.labelSmall)
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error)
                            )
                            Text("Router failed", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CardHeaderWithInfo(
                        title = "Target transition",
                        infoText = "Runtime target.prop states detected by the daemon."
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Last transitions",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (parsedTransitionHistory.isEmpty()) {
                                Text(
                                    text = "No transitions captured yet",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                parsedTransitionHistory.take(4).forEachIndexed { index, entry ->
                                    val isCurrent = index == 0
                                    val accentColor = if (isCurrent) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(accentColor)
                                        )
                                        Text(
                                            text = "${targetTransitionDisplayLabel(entry.fromState)} -> ${targetTransitionDisplayLabel(entry.toState)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isCurrent) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        Card(
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "terminal",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "> target.state=${normalizedTargetState}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "> policy.request=${terminalPolicyRequest}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "> sync=${terminalSyncStatus}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "> transitions=${parsedTransitionHistory.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "> reason=${normalizedTargetReason}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CardHeaderWithInfo(
                        title = "Cards",
                        infoText = "Visual status per rule and router infrastructure state."
                    )
                    if (appCards.isEmpty()) {
                        Text("No rules configured", style = MaterialTheme.typography.bodySmall)
                    }

                    cardRows.forEach { rowCards ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowCards.forEach { cardEntry ->
                                AppRuleCard(
                                    rule = cardEntry,
                                    onEdit = {
                                        openEditEditor(
                                            cardEntry.packageName,
                                            TargetPolicyRule(
                                                profile = cardEntry.profile,
                                                priority = cardEntry.priority,
                                                enabled = cardEntry.enabled
                                            )
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowCards.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            if (syncSummary.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .animateContentSize(tween(280)),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CardHeaderWithInfo(
                            title = "Last sync",
                            infoText = "Evidence of runtime/router synchronization and application."
                        )
                        Button(
                            onClick = { syncLogExpanded = !syncLogExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (syncLogExpanded) "Hide details" else "View details")
                        }
                        if (syncLogExpanded) {
                            syncSummary.forEach { line ->
                                Text(line, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    
                }
            }
        }

        if (editorVisible) {
            AlertDialog(
                onDismissRequest = { editorVisible = false },
                modifier = Modifier.fillMaxWidth(0.96f),
                properties = DialogProperties(usePlatformDefaultWidth = false),
                title = {
                    Text(if (editorMode == EditorMode.ADD) "Add profile" else "Edit profile")
                },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (editorMode == EditorMode.ADD) {
                                "Select app + profile + priority and save."
                            } else {
                                "Edit profile, priority, and state for this package."
                            },
                            style = MaterialTheme.typography.bodySmall
                        )

                        if (editorMode == EditorMode.ADD) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = search,
                                        onValueChange = { search = it },
                                        label = { Text("Search app") },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Outlined.Search,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    if (filteredApps.isEmpty()) {
                                        Text(
                                            text = "No apps were found for this search.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Column(
                                            modifier = Modifier.heightIn(max = 240.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            filteredApps.take(6).forEach { entry ->
                                                val pkg = entry.packageName.lowercase()
                                                val isSelected = selectedPackage.equals(pkg, ignoreCase = true)
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            val current = appPolicies[pkg]
                                                            selectedPackage = pkg
                                                            selectedProfile = current?.profile ?: selectedProfile
                                                            selectedPriority = current?.priority ?: selectedPriority
                                                            selectedEnabled = current?.enabled ?: true
                                                        },
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (isSelected) {
                                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                                        } else {
                                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                                        }
                                                    ),
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = if (isSelected) {
                                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                                        } else {
                                                            MaterialTheme.colorScheme.surface
                                                        }
                                                    ),
                                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        Card(
                                                            shape = CircleShape,
                                                            colors = CardDefaults.cardColors(
                                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                            ),
                                                            border = BorderStroke(
                                                                1.dp,
                                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                                            )
                                                        ) {
                                                            if (entry.iconBitmap != null) {
                                                                Image(
                                                                    bitmap = entry.iconBitmap.asImageBitmap(),
                                                                    contentDescription = null,
                                                                    modifier = Modifier
                                                                        .size(28.dp)
                                                                        .clip(CircleShape),
                                                                    contentScale = ContentScale.Crop
                                                                )
                                                            } else {
                                                                Text(
                                                                    text = entry.label.take(2).uppercase(),
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                                                )
                                                            }
                                                        }

                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = entry.label,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                text = entry.packageName,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }

                                                        if (isSelected) {
                                                            Icon(
                                                                Icons.Outlined.CheckCircle,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (selectedPackage.isNotBlank()) {
                                Text(
                                    text = "Paquete seleccionado: $selectedPackage",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            OutlinedTextField(
                                value = selectedPackage,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Package") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        SelectBoxField(
                            label = "Profile",
                            selectedValue = selectedProfile,
                            options = listOf("stable", "speed", "gaming", "benchmark_gaming", "benchmark_speed"),
                            optionDescriptions = mapOf(
                                "stable" to "Balanced for daily use with consistent priority.",
                                "speed" to "Favorece mayor velocidad y throughput en descargas.",
                                "gaming" to "Prioritizes lower latency for real-time gaming.",
                                "benchmark_gaming" to "Extreme benchmark for the lowest possible ping, with higher risk and instability.",
                                "benchmark_speed" to "Extreme benchmark for maximum throughput, even if ping and power usage increase."
                            ),
                            onSelect = { selectedProfile = it }
                        )

                        SelectBoxField(
                            label = "Prioridad",
                            selectedValue = selectedPriority,
                            options = listOf("low", "medium", "high"),
                            optionDescriptions = mapOf(
                                "low" to "Lower priority to avoid displacing critical traffic.",
                                "medium" to "Medium priority recommended for most users.",
                                "high" to "Maximum priority for responsiveness and sensitivity."
                            ),
                            onSelect = { selectedPriority = it }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Enabled", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = selectedEnabled, onCheckedChange = { selectedEnabled = it })
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onRequestSaveAppPolicy(selectedPackage, selectedProfile, selectedPriority, selectedEnabled)
                            editorVisible = false
                        },
                        enabled = selectedPackage.isNotBlank()
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    Button(onClick = { editorVisible = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        Card(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = if (stickyVisible) 94.dp else 20.dp)
                .size(56.dp)
                .clickable(onClick = { openAddEditor() }),
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        if (stickyVisible) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        if (applyInProgress) "Syncing runtime..." else "$pendingChanges Changes Pending",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = onRequestApplyPolicies,
                        enabled = pendingChanges > 0 && !applyInProgress,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (applyInProgress) "Applying..." else "Apply Now")
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectBoxField(
    label: String,
    selectedValue: String,
    options: List<String>,
    optionDescriptions: Map<String, String> = emptyMap(),
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var infoVisible by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (optionDescriptions.isNotEmpty()) {
                Text(
                    text = if (infoVisible) "(i) hide" else "(i)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { infoVisible = !infoVisible }
                )
            }
        }

        if (infoVisible && optionDescriptions.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    options.forEach { option ->
                        val description = optionDescriptions[option].orEmpty()
                        Text(
                            text = "${profileDisplayName(option)}: $description",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Text(
                    text = profileDisplayName(selectedValue),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("▼", style = MaterialTheme.typography.labelSmall)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(profileDisplayName(option)) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

private fun profileDisplayName(value: String): String = when (value.lowercase()) {
    "benchmark", "benchmark_gaming" -> "Bench Ping"
    "benchmark_speed" -> "Bench Speed"
    else -> value
        .replace('_', ' ')
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
}

@Composable
private fun AppRuleCard(
    rule: AppRuleUiModel,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val iconBitmap = remember(rule.packageName, rule.iconBitmap) {
        resolveRuleIconBitmap(context, rule.packageName, rule.iconBitmap)
    }

    val profileContainer = when (rule.profile.lowercase()) {
        "gaming" -> MaterialTheme.colorScheme.tertiaryContainer
        "benchmark", "benchmark_gaming" -> MaterialTheme.colorScheme.secondaryContainer
        "benchmark_speed" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
        "speed" -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val profileContent = when (rule.profile.lowercase()) {
        "gaming" -> MaterialTheme.colorScheme.onTertiaryContainer
        "benchmark", "benchmark_gaming" -> MaterialTheme.colorScheme.onSecondaryContainer
        "benchmark_speed" -> MaterialTheme.colorScheme.onPrimaryContainer
        "speed" -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    val priorityBorderColor = when (rule.priority.lowercase()) {
        "high" -> MaterialTheme.colorScheme.error
        "medium" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }
    val priorityTextColor = when (rule.priority.lowercase()) {
        "high" -> MaterialTheme.colorScheme.error
        "medium" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val chipStatus = when {
        !rule.routerConnected -> RuleStatus.ROUTER_FAILED
        else -> rule.state
    }

    Card(
        modifier = modifier.heightIn(min = 224.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RuleStatusChip(status = chipStatus)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "Edit profile",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Card(
                    shape = CircleShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap.asImageBitmap(),
                            contentDescription = "App icon",
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = rule.appLabel.take(2).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
                        )
                    }
                }

                Text(
                    text = rule.appLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = rule.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!rule.enabled) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("• OFF", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = profileContainer)
                ) {
                    Text(
                        text = profileDisplayName(rule.profile),
                        style = MaterialTheme.typography.labelMedium,
                        color = profileContent,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }
                Card(
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, priorityBorderColor),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        text = rule.priority.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelMedium,
                        color = priorityTextColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }
            }

        }
    }
}

@Composable
private fun RuleStatusChip(
    status: RuleStatus,
    modifier: Modifier = Modifier
) {
    val (chipColor, chipText) = when (status) {
        RuleStatus.RUNTIME_SYNCED -> MaterialTheme.colorScheme.primary to "Runtime Synced"
        RuleStatus.PENDING_SYNC -> MaterialTheme.colorScheme.tertiary to "Pending Sync"
        RuleStatus.ROUTER_FAILED -> MaterialTheme.colorScheme.error to "Router Failed"
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(chipColor.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(chipColor)
        )
        Text(
            text = chipText,
            color = chipColor,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun resolveRuleState(
    routerConnected: Boolean,
    rule: TargetPolicyRule,
    runtimeRule: TargetPolicyRule?
): RuleStatus {
    if (!routerConnected) return RuleStatus.ROUTER_FAILED

    val applied = if (rule.enabled) {
        runtimeRule != null && runtimeRule.profile == rule.profile && runtimeRule.priority == rule.priority
    } else {
        runtimeRule == null
    }
    return if (applied) RuleStatus.RUNTIME_SYNCED else RuleStatus.PENDING_SYNC
}

private fun normalizeTargetTransitionState(state: String): String {
    return when (state.trim().uppercase(Locale.ROOT)) {
        "IDLE", "APP_OVERRIDE", "NETWORK_DECISION", "POLICY_APPLIED" -> state.trim().uppercase(Locale.ROOT)
        else -> "IDLE"
    }
}

private fun targetTransitionDisplayLabel(state: String): String {
    return when (normalizeTargetTransitionState(state)) {
        "IDLE" -> "IDLE"
        "APP_OVERRIDE" -> "APP_OVERRIDE"
        "NETWORK_DECISION" -> "NETWORK_DECISION"
        "POLICY_APPLIED" -> "POLICY_APPLIED"
        else -> "IDLE"
    }
}

private data class TargetTransitionEntry(
    val tsSec: Long,
    val fromState: String,
    val toState: String,
    val reason: String
)

private fun parseTargetTransitionEntry(raw: String): TargetTransitionEntry? {
    val parts = raw.split('|')
    if (parts.size < 4) return null
    val ts = parts[0].trim().toLongOrNull() ?: 0L
    val from = normalizeTargetTransitionState(parts[1])
    val to = normalizeTargetTransitionState(parts[2])
    val reason = parts.drop(3).joinToString("|").trim().ifBlank { "none" }
    return TargetTransitionEntry(tsSec = ts, fromState = from, toState = to, reason = reason)
}

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    snapshot: ModuleSnapshot = ModuleSnapshot.empty(),
    highlightPulse: Boolean = false,
    lowNetworkSimulationEnabled: Boolean = false,
    onRequestStart: () -> Unit = {},
    onRequestRestart: () -> Unit = {},
    onRequestCalibrate: () -> Unit = {},
    onRequestProfile: (String) -> Unit = {}
) {
    val daemon = snapshot.daemonState
    val policy = snapshot.policyEvent
    val daemonRuntime = snapshot.daemonRuntime
    val transport = daemon["transport"].orEmpty()
    val wifiScore = daemon["wifi.score"]?.toIntOrNull()
    val compositeScore = daemon["composite_score"]?.toFloatOrNull()?.toInt()
    val lowNetDivisor = 2
    val wifiScoreUi = applyLowNetSimulation(wifiScore, lowNetworkSimulationEnabled, lowNetDivisor)
    val compositeScoreUi = applyLowNetSimulation(compositeScore, lowNetworkSimulationEnabled, lowNetDivisor)
    val probeOk = daemon["wifi.probe_ok"].orEmpty() == "1"
    val rssiDbm = daemon["wifi.rssi_dbm"].orEmpty().ifBlank { "-" }
    val latencyUi = rememberLatencyUi(
        rawLatency = daemon["wifi.latency_ms"],
        rawLatencyEma = daemon["wifi.latency_ema_ms"]
    )
    val wifiState = daemon["wifi.state"].orEmpty().ifBlank { "unknown" }
    val profileCurrent = snapshot.policyCurrent.ifBlank { daemon["profile"].orEmpty().ifBlank { "unknown" } }
    val profileTarget = snapshot.policyTarget.ifBlank { snapshot.policyRequest.ifBlank { "unknown" } }
    val calibrateState = snapshot.policyEvent.calibrateState.ifBlank { "unknown" }
    val daemonUpdated = formatEpoch(snapshot.policyEvent.ts)
    val daemonCheckedAt = formatEpoch(daemonRuntime.checkedAt)
    val caps = extractCapabilities(snapshot.lastEvent.details)
    val hasHardwareOptimized = caps.any { it in setOf("mu-mimo", "mumimo", "bss", "bss-coloring", "bsscoloring") }
    val emaHistory = rememberEmaHistory(sampleRaw = daemon["composite_ema"], windowMs = 60_000L, maxPoints = 120)
    val ema = emaHistory.lastOrNull()?.value
    val connectionAccentColor = rememberConnectionAccentColor(
        transport = transport,
        wifiScore = wifiScoreUi,
        compositeScore = compositeScoreUi
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HighlightCard(modifier = Modifier.fillMaxWidth(), highlight = highlightPulse) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnimatedVisibility(
                    visible = lowNetworkSimulationEnabled,
                    enter = expandVertically(tween(280)) + fadeIn(tween(220)),
                    exit = shrinkVertically(tween(220)) + fadeOut(tween(180))
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = "SIM LOW-NET Active (score/divisor=$lowNetDivisor)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                CardHeaderWithInfo(
                    title = "Daemon status",
                    infoText = "Real process operational status. The app verifies PID and process liveness on each refresh, approximately every 10s."
                )
                Text(
                    text = daemonRuntime.state,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (daemonRuntime.pid.isNotBlank()) {
                    Text(text = "PID: ${daemonRuntime.pid}", style = MaterialTheme.typography.bodySmall)
                }
                Text(text = "Last event: ${policy.event}", style = MaterialTheme.typography.bodySmall)
                Text(text = "Last update: $daemonUpdated", style = MaterialTheme.typography.bodySmall)
                Text(text = "Calibration status: $calibrateState", style = MaterialTheme.typography.bodySmall)
                Text(text = "Process verification: $daemonCheckedAt", style = MaterialTheme.typography.bodySmall)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRequestStart, modifier = Modifier.weight(1f)) { Text("Start") }
                    Button(onClick = onRequestRestart, modifier = Modifier.weight(1f)) { Text("Restart") }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CardHeaderWithInfo(
                    title = "Quality and connection states",
                    infoText = "Network mode, current score, applied profile, and next target profile."
                )
                AdaptiveQualityGauge(
                    transport = transport,
                    wifiScore = wifiScoreUi,
                    compositeScore = compositeScoreUi,
                    compact = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Applied profile: $profileCurrent", style = MaterialTheme.typography.bodySmall)
                Text("Next profile (target): $profileTarget", style = MaterialTheme.typography.bodySmall)
            }
        }

        AnimatedVisibility(
            visible = transport == "wifi",
            enter = fadeIn(tween(300)) + expandVertically(tween(300)),
            exit = fadeOut(tween(220)) + shrinkVertically(tween(220))
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
                ),
                border = BorderStroke(1.dp, connectionAccentColor.copy(alpha = 0.34f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CardHeaderWithInfo(
                        title = "Wi-Fi details",
                        infoText = "Card prioritized when the active transport is Wi-Fi."
                    )
                    if (hasHardwareOptimized) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Text(
                                text = "Hardware Optimized",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                    Text("RSSI: $rssiDbm dBm", style = MaterialTheme.typography.bodyMedium)
                    Text("Network state: $wifiState", style = MaterialTheme.typography.bodySmall)
                    Text("Wi-Fi latency: ${latencyUi.label}", style = MaterialTheme.typography.bodySmall)
                    Text("Source: ${latencyUi.source}", style = MaterialTheme.typography.labelSmall)
                    ProbeSemaphore(probeOk = probeOk, activeColor = connectionAccentColor)
                    if (caps.isNotEmpty()) {
                        CapabilityRow(caps)
                    }
                    Text(
                        text = "EMA: ${ema?.let { String.format(Locale.US, "%.1f", it) } ?: "-"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    EmaSparkline(
                        points = emaHistory,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(88.dp)
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = transport != "wifi",
            enter = fadeIn(tween(300)) + expandVertically(tween(300)),
            exit = fadeOut(tween(220)) + shrinkVertically(tween(220))
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CardHeaderWithInfo(
                        title = "Active Transport",
                        infoText = "Summary of the transport currently detected by the daemon."
                    )
                    Text(text = transport.ifBlank { "none" }, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CardHeaderWithInfo(
                    title = "Quick Actions",
                    infoText = "Daily module operations: calibration and profile switching."
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Maintenance", style = MaterialTheme.typography.labelMedium)
                        Button(
                            onClick = onRequestCalibrate,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(text = "Calibrate now") }
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Profiles", style = MaterialTheme.typography.labelMedium)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onRequestProfile("stable") },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) {
                                Text(text = "Stable")
                            }
                            OutlinedButton(
                                onClick = { onRequestProfile("speed") },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) {
                                Text(text = "Speed")
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onRequestProfile("gaming") },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) {
                                Text(text = "Gaming")
                            }
                            OutlinedButton(
                                onClick = { onRequestProfile("benchmark_gaming") },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) {
                                Text(text = "Bench Ping")
                            }
                        }
                        OutlinedButton(
                            onClick = { onRequestProfile("benchmark_speed") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Text(text = "Bench Speed")
                        }
                    }
                }
                Text(
                    text = "Bench Ping targets the lowest possible RTT; Bench Speed maximizes throughput even if ping increases.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

    }
}

@Composable
private fun rememberLatencyUi(rawLatency: String?, rawLatencyEma: String?): LatencyUi {
    var lastKnownMs by rememberSaveable { mutableStateOf<Float?>(null) }

    val instant = rawLatency?.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f }
    val ema = rawLatencyEma?.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f }

    LaunchedEffect(instant, ema) {
        val candidate = instant ?: ema
        if (candidate != null) {
            lastKnownMs = candidate.coerceIn(1f, 9999f)
        }
    }

    return when {
        instant != null -> LatencyUi(label = "${String.format(Locale.US, "%.0f", instant)} ms", source = "probe")
        ema != null -> LatencyUi(label = "${String.format(Locale.US, "%.0f", ema)} ms", source = "ema")
        lastKnownMs != null -> LatencyUi(
            label = "${String.format(Locale.US, "%.0f", lastKnownMs)} ms",
            source = "last valid"
        )
        else -> LatencyUi(label = "-", source = "no data")
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
fun RouterScreen(
    contentPadding: PaddingValues,
    daemonState: Map<String, String> = emptyMap(),
    lastEvent: LastEvent = LastEvent.empty(),
    signatureBssid: String = "",
    signatureBand: String = "",
    signatureChannel: String = "",
    signatureWidth: String = "",
    paired: Boolean = false,
    policyEventName: String = "",
    suggestedRouterIp: String = "",
    routerIp: String = "",
    routerId: String = "",
    hasToken: Boolean = false,
    scannedPairPayload: String = "",
    hideIp: Boolean = true,
    onToggleHideIp: () -> Unit = {},
    onRequestPairRouter: (String, String) -> Unit = { _, _ -> },
    onRequestUnpairRouter: () -> Unit = {},
    onRequestScanQr: () -> Unit = {},
    onRequestChannelScan: () -> Unit = {},
    onReadChannelCache: () -> String? = { null },
    onApplyChannelChange: (Int, String) -> Unit = { _, _ -> },
    onReadChannelApplyStatus: () -> String? = { null }
) {
    var showChannelDialog by rememberSaveable { mutableStateOf(false) }
    var showIpInput by rememberSaveable { mutableStateOf(false) }
    var showCodeInput by rememberSaveable { mutableStateOf(false) }
    
    val detailsMap = parseDetails(lastEvent.details)
    val bssid = detailsMap["bssid"].orEmpty()
        .ifBlank { daemonState["wifi.bssid"].orEmpty() }
        .ifBlank { signatureBssid }
    val band = detailsMap["band"].orEmpty()
        .ifBlank { daemonState["wifi.band"].orEmpty() }
        .ifBlank { signatureBand }
    val channel = detailsMap["chan"].orEmpty()
        .ifBlank { daemonState["wifi.chan"].orEmpty() }
        .ifBlank { signatureChannel }
    val rawWidth = detailsMap["width"].orEmpty()
        .ifBlank { daemonState["wifi.width"].orEmpty() }
        .ifBlank { signatureWidth }
    val rawWidthSource = detailsMap["width_source"].orEmpty().ifBlank { daemonState["wifi.width_source"].orEmpty() }
    val is2gBand = band.equals("2g", ignoreCase = true) || band.contains("2.4", ignoreCase = true)
    val width = if (rawWidth.isBlank() && is2gBand) "20" else rawWidth
    val widthSource = if (rawWidthSource.isBlank() && rawWidth.isBlank() && is2gBand) "inferred" else rawWidthSource
    val widthIsInferred = widthSource.equals("inferred", ignoreCase = true)
    var ipInput by rememberSaveable { mutableStateOf("") }
    var codeInput by rememberSaveable(scannedPairPayload) { mutableStateOf(scannedPairPayload) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CardHeaderWithInfo(
                    title = "Router status",
                    infoText = "Pairing, IP, token, and base bridge data with router."
                )
                Text(text = if (paired) "Paired" else "Unpaired", style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (hideIp) "IP: Hidden" else "IP: ${routerIp.ifBlank { "-" }}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onToggleHideIp) {
                        Icon(
                            imageVector = if (hideIp) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = if (hideIp) "Show sensitive data" else "Hide sensitive data"
                        )
                    }
                }
                Text(
                    text = if (hideIp) "Router ID: Hidden" else "Router ID: ${routerId.ifBlank { "-" }}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(text = "Token: ${if (hasToken) "OK" else "No"}", style = MaterialTheme.typography.bodySmall)
                Text(text = "Daemon event: $policyEventName", style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CardHeaderWithInfo(
                    title = "Pairing",
                    infoText = "Enter IP and pair_code/URI to pair the router."
                )
                OutlinedTextField(
                    value = ipInput,
                    onValueChange = { ipInput = it },
                    label = { Text("Router IP") },
                    visualTransformation = if (showIpInput) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showIpInput = !showIpInput }) {
                            Icon(
                                imageVector = if (showIpInput) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showIpInput) "Hide IP" else "Show IP"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = {
                        if (suggestedRouterIp.isNotBlank()) {
                            ipInput = suggestedRouterIp
                            showIpInput = false
                        }
                    }) {
                        Text("Auto-detect")
                    }
                }
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it },
                    label = { Text("QR URI o pair_code") },
                    visualTransformation = if (showCodeInput) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showCodeInput = !showCodeInput }) {
                                Icon(
                                    imageVector = if (showCodeInput) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showCodeInput) "Hide pair code" else "Show pair code"
                                )
                            }
                            IconButton(onClick = onRequestScanQr) {
                                Icon(
                                    painter = painterResource(id = AndroidR.drawable.ic_menu_camera),
                                    contentDescription = "Escanear QR"
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onRequestUnpairRouter,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Unpair")
                    }
                    Button(onClick = { onRequestPairRouter(ipInput.ifBlank { routerIp }, codeInput) }, modifier = Modifier.weight(1f)) {
                        Text("Pair")
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CardHeaderWithInfo(
                    title = "Network signature",
                    infoText = "Readable summary of the network signature detected by the daemon."
                )
                Text(
                    "ID (BSSID): ${if (hideIp) "Hidden" else bssid.ifBlank { "-" }}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("Band: ${band.ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall)
                Text("Channel: ${channel.ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Width: ${width.ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall)
                    if (widthIsInferred) {
                        Icon(
                            painter = painterResource(id = AndroidR.drawable.ic_menu_view),
                            contentDescription = "Inferred width",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text("inferred", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CardHeaderWithInfo(
                    title = "Channel analysis",
                    infoText = "Request recommendation of the best available channel from the router."
                )
                Button(
                    onClick = {
                        showChannelDialog = true
                        onRequestChannelScan()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = paired
                ) {
                    Icon(Icons.Outlined.Wifi, contentDescription = null)
                    Text("  Analyze Channels")
                }
                if (!paired) {
                    Text(
                        "Pair the router to enable channel analysis",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

    }

    ChannelAnalysisDialog(
        isOpen = showChannelDialog,
        onDismiss = { showChannelDialog = false },
        onReadCache = onReadChannelCache,
        currentChannelHint = channel,
        onApplyChannelChange = onApplyChannelChange,
        onReadApplyStatus = onReadChannelApplyStatus
    )
}

@Composable
fun PairingScreen(
    contentPadding: PaddingValues,
    paired: Boolean,
    policyEventName: String,
    suggestedRouterIp: String,
    routerIp: String,
    routerId: String,
    hasToken: Boolean,
    scannedPairPayload: String,
    hideIp: Boolean,
    onToggleHideIp: () -> Unit,
    onRequestPairRouter: (String, String) -> Unit,
    onRequestUnpairRouter: () -> Unit,
    onRequestScanQr: () -> Unit
) {
    var ipInput by rememberSaveable { mutableStateOf("") }
    var codeInput by rememberSaveable(scannedPairPayload) { mutableStateOf(scannedPairPayload) }
    var showIpInput by rememberSaveable { mutableStateOf(false) }
    var showCodeInput by rememberSaveable { mutableStateOf(false) }
    val daemonPairConfirmed = policyEventName == "ROUTER_PAIRED"
    val pulse = rememberInfiniteTransition(label = "pairPulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pairScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CardHeaderWithInfo(
                    title = "Current status",
                    infoText = "Dedicated pairing flow via IP + QR URI/pair_code."
                )
                if (daemonPairConfirmed && paired) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = "Pairing confirmed",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(64.dp)
                            .graphicsLayer {
                                scaleX = pulseScale
                                scaleY = pulseScale
                            }
                            .align(Alignment.CenterHorizontally)
                    )
                    Text(
                        text = "Pairing confirmed by daemon",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(text = if (paired) "Paired" else "Unpaired", style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (hideIp) "Saved IP: Hidden" else "IP guardada: ${routerIp.ifBlank { "-" }}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onToggleHideIp) {
                        Icon(
                            imageVector = if (hideIp) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = if (hideIp) "Show sensitive data" else "Hide sensitive data"
                        )
                    }
                }
                Text(
                    text = if (hideIp) "Router ID: Hidden" else "Router ID: ${routerId.ifBlank { "-" }}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(text = "Token: ${if (hasToken) "OK" else "No"}", style = MaterialTheme.typography.bodySmall)
                Text(text = "Daemon event: $policyEventName", style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CardHeaderWithInfo(
                    title = "Pairing",
                    infoText = "Enter router IP and QR URI or pair_code to complete pairing."
                )
                OutlinedTextField(
                    value = ipInput,
                    onValueChange = { ipInput = it },
                    label = { Text("Router IP") },
                    visualTransformation = if (showIpInput) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showIpInput = !showIpInput }) {
                            Icon(
                                imageVector = if (showIpInput) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showIpInput) "Hide IP" else "Show IP"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = {
                            if (suggestedRouterIp.isNotBlank()) {
                                ipInput = suggestedRouterIp
                                showIpInput = false
                            }
                        }
                    ) {
                        Text("Auto-detect")
                    }
                }
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it },
                    label = { Text("QR URI o pair_code") },
                    visualTransformation = if (showCodeInput) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showCodeInput = !showCodeInput }) {
                                Icon(
                                    imageVector = if (showCodeInput) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showCodeInput) "Hide pair code" else "Show pair code"
                                )
                            }
                            IconButton(onClick = onRequestScanQr) {
                                Icon(
                                    painter = painterResource(id = AndroidR.drawable.ic_menu_camera),
                                    contentDescription = "Escanear QR"
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onRequestUnpairRouter,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Unpair")
                    }
                    Button(onClick = { onRequestPairRouter(ipInput.ifBlank { routerIp }, codeInput) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Settings, contentDescription = null)
                        Text(" Pair")
                    }
                }
            }
        }
    }
}

private fun applyLowNetSimulation(score: Int?, enabled: Boolean, divisor: Int): Int? {
    if (score == null) return null
    val safeDivisor = if (divisor <= 0) 2 else divisor
    val base = score.coerceIn(0, 100)
    return if (enabled) {
        (base / safeDivisor.toFloat()).toInt().coerceIn(0, 100)
    } else {
        base
    }
}

@Composable
private fun rememberConnectionAccentColor(
    transport: String,
    wifiScore: Int?,
    compositeScore: Int?
): Color {
    val selectedScore = if (transport == "wifi") wifiScore else compositeScore
    val scoreValue = selectedScore?.coerceIn(0, 100)
    return when {
        scoreValue == null -> MaterialTheme.colorScheme.onSurfaceVariant
        scoreValue > 75 -> MaterialTheme.colorScheme.primary
        scoreValue >= 40 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
}

@Composable
private fun AdaptiveQualityGauge(
    transport: String,
    wifiScore: Int?,
    compositeScore: Int?,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val selectedScore = if (transport == "wifi") wifiScore else compositeScore
    val scoreValue = selectedScore?.coerceIn(0, 100)
    val color = rememberConnectionAccentColor(
        transport = transport,
        wifiScore = wifiScore,
        compositeScore = compositeScore
    )

    val gaugeSize = if (compact) 86.dp else 108.dp
    val stroke = if (compact) 9.dp else 11.dp

    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (transport == "wifi") "Modo Wi-Fi" else "Modo Mobile",
                style = MaterialTheme.typography.labelLarge
            )
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { (scoreValue ?: 0) / 100f },
                    modifier = Modifier.size(gaugeSize),
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeWidth = stroke,
                    strokeCap = StrokeCap.Round
                )
                Text(
                    text = scoreValue?.toString() ?: "--",
                    style = MaterialTheme.typography.headlineSmall,
                    color = color
                )
            }
            Text(
                text = when {
                    scoreValue == null -> "N/A"
                    scoreValue > 75 -> "Speed"
                    scoreValue >= 40 -> "Stable"
                    else -> "Bad"
                },
                style = MaterialTheme.typography.bodySmall,
                color = color
            )
        }
    }
}

@Composable
private fun ProbeSemaphore(probeOk: Boolean) {
    val signalColor = if (probeOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    ProbeSemaphore(probeOk = probeOk, activeColor = signalColor)
}

@Composable
private fun ProbeSemaphore(probeOk: Boolean, activeColor: Color) {
    val signalColor = if (probeOk) activeColor else MaterialTheme.colorScheme.error
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(12.dp)) {
            drawCircle(color = signalColor)
        }
        Text(
            text = if (probeOk) "Internet active" else "Limbo / no output",
            color = signalColor,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    currentThemeMode: AppThemeMode,
    developerMode: Boolean,
    lowNetworkSimulationEnabled: Boolean,
    ipv6CalibrationEnabled: Boolean,
    granularLatencyEnabled: Boolean,
    lowNetworkTestOffset: Int,
    bootCustomProfile: String,
    routerFiles: List<String>,
    onRequestThemeMode: (AppThemeMode) -> Unit,
    onRequestDeveloperMode: (Boolean) -> Unit,
    onRequestLowNetworkSimulation: (Boolean) -> Unit,
    onRequestIpv6Calibration: (Boolean) -> Unit,
    onRequestGranularLatency: (Boolean) -> Unit,
    onRequestLowNetworkTestOffset: (Int) -> Unit,
    onRequestBootCustomProfile: (String) -> Unit,
    onRequestOpenAppSettings: () -> Unit,
    onRequestCheckDaemonPid: () -> Unit,
    onRequestRefreshRouterFiles: () -> Unit,
    onRequestReadFile: (String, String) -> Unit,
    onRequestViewRouterLast: () -> Unit,
    onRequestOpenRouterLast: () -> Unit,
    onRequestRunCommand: (String, String) -> Unit
) {
    var showDeveloperTools by rememberSaveable { mutableStateOf(false) }
    var lowNetOffsetInput by rememberSaveable { mutableStateOf(lowNetworkTestOffset.toString()) }
    var themeMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val darkThemes = listOf(
        AppThemeMode.KITSUNPING,
        AppThemeMode.DARK,
        AppThemeMode.AMOLED,
        AppThemeMode.TERMINAL,
        AppThemeMode.CRIMSON,
        AppThemeMode.SOLARIZED,
        AppThemeMode.MODERN,
        AppThemeMode.FOREST,
        AppThemeMode.OCEAN,
        AppThemeMode.SUNSET,
        AppThemeMode.MONOCHROME,
        AppThemeMode.DRACULA,
        AppThemeMode.NORD
    )
    val lightThemes = listOf(
        AppThemeMode.LIGHT,
        AppThemeMode.ARCTIC,
        AppThemeMode.ROSE,
        AppThemeMode.MODERN_INVERTED,
        AppThemeMode.PASTEL
    )
    val experimentalThemes = listOf(
        AppThemeMode.SYSTEM,
        AppThemeMode.MONO_BLUEPRINT
    )

    LaunchedEffect(lowNetworkTestOffset) {
        lowNetOffsetInput = lowNetworkTestOffset.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CardHeaderWithInfo(
                    title = "Theme",
                    infoText = "Select the app visual theme."
                )
                Text(text = "Current theme: ${currentThemeMode.displayName()}", style = MaterialTheme.typography.bodySmall)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { themeMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = currentThemeMode.displayName())
                    }
                    DropdownMenu(
                        expanded = themeMenuExpanded,
                        onDismissRequest = { themeMenuExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.92f)
                    ) {
                        listOf(
                            "Oscuros" to darkThemes,
                            "Claros" to lightThemes,
                            "Experimentales" to experimentalThemes
                        ).forEachIndexed { index, (label, themes) ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            themes.forEach { themeMode ->
                                DropdownMenuItem(
                                    text = { Text(themeMode.displayName()) },
                                    onClick = {
                                        onRequestThemeMode(themeMode)
                                        themeMenuExpanded = false
                                    }
                                )
                            }
                            if (index != 2) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CardHeaderWithInfo(
                    title = "Developer mode",
                    infoText = "Shows or hides advanced debugging controls in Router."
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = AndroidR.drawable.ic_menu_manage),
                            contentDescription = null
                        )
                        Text(text = if (developerMode) "Enabled" else "Disabled")
                    }
                    Switch(checked = developerMode, onCheckedChange = onRequestDeveloperMode)
                }
            }
        }

        if (developerMode) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .animateContentSize(tween(300)),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CardHeaderWithInfo(
                        title = "Herramientas desarrollador",
                        infoText = "Advanced controls moved from Router to avoid mixed views."
                    )

                    Button(
                        onClick = { showDeveloperTools = !showDeveloperTools },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (showDeveloperTools) "Hide tools" else "Show tools")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Simular red baja (legacy divisor=2)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = lowNetworkSimulationEnabled,
                            onCheckedChange = onRequestLowNetworkSimulation
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "IPv6 calibration (persist)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = ipv6CalibrationEnabled,
                            onCheckedChange = onRequestIpv6Calibration
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Metrica granular (P90/P99)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = granularLatencyEnabled,
                            onCheckedChange = onRequestGranularLatency
                        )
                    }

                    OutlinedTextField(
                        value = lowNetOffsetInput,
                        onValueChange = { raw ->
                            lowNetOffsetInput = raw.filter { it.isDigit() }
                        },
                        label = { Text("LowNet test (resta)") },
                        supportingText = {
                            Text("Saves cache/lownet.test and the module subtracts this value when publishing state")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    SelectBoxField(
                        label = "Profile on startup",
                        selectedValue = when (bootCustomProfile) {
                            "benchmark" -> "benchmark_gaming"
                            else -> bootCustomProfile
                        },
                        options = listOf("none", "stable", "speed", "gaming", "benchmark_gaming", "benchmark_speed"),
                        optionDescriptions = mapOf(
                            "none" to "No profile applied on startup",
                            "stable" to "Fuerza stable al inicio",
                            "speed" to "Fuerza speed al inicio",
                            "gaming" to "Fuerza gaming al inicio",
                            "benchmark_gaming" to "Fuerza benchmark_gaming al inicio",
                            "benchmark_speed" to "Fuerza benchmark_speed al inicio"
                        ),
                        onSelect = onRequestBootCustomProfile
                    )

                    Button(
                        onClick = {
                            val value = lowNetOffsetInput.toIntOrNull() ?: 0
                            onRequestLowNetworkTestOffset(value.coerceAtLeast(0))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Aplicar LowNet test")
                    }

                    if (!showDeveloperTools) {
                        Text(
                            text = "Seccion colapsada",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onRequestCheckDaemonPid, modifier = Modifier.weight(1f)) { Text("Ver PID") }
                            Button(onClick = onRequestRefreshRouterFiles, modifier = Modifier.weight(1f)) { Text("Refrescar") }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    onRequestRunCommand(
                                        "Test Wi-Fi parsing",
                                        "sh /data/adb/modules/Kitsunping/tools/test_wifi_parsing.sh"
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Test parsing") }
                            Button(
                                onClick = { onRequestReadFile("daemon.state", "/data/adb/modules/Kitsunping/cache/daemon.state") },
                                modifier = Modifier.weight(1f)
                            ) { Text("daemon.state") }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onRequestViewRouterLast, modifier = Modifier.weight(1f)) { Text("router.last") }
                            Button(onClick = onRequestOpenRouterLast, modifier = Modifier.weight(1f)) { Text("target") }
                        }

                        if (routerFiles.isEmpty()) {
                            Text(text = "No router files", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                routerFiles.forEach { path ->
                                    val name = path.substringAfterLast('/')
                                    Button(
                                        onClick = {
                                            if (name == "router.last") onRequestOpenRouterLast() else onRequestReadFile(name, path)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(text = "Open $name")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CardHeaderWithInfo(
                    title = "Permissions",
                    infoText = "Quick access to app permissions and system settings."
                )
                Button(onClick = onRequestOpenAppSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Open app settings")
                }
            }
        }
    }
}

@Composable
private fun ScoreGauge(
    title: String,
    score: Int?,
    modifier: Modifier = Modifier
) {
    val scoreValue = score?.coerceIn(0, 100)
    val color = when {
        scoreValue == null -> MaterialTheme.colorScheme.onSurfaceVariant
        scoreValue >= 75 -> MaterialTheme.colorScheme.primary
        scoreValue >= 45 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.labelLarge)
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { (scoreValue ?: 0) / 100f },
                    modifier = Modifier.size(72.dp),
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeWidth = 8.dp,
                    strokeCap = StrokeCap.Round
                )
                Text(
                    text = scoreValue?.toString() ?: "--",
                    style = MaterialTheme.typography.titleMedium,
                    color = color
                )
            }
            Text(
                text = when {
                    scoreValue == null -> "N/A"
                    scoreValue >= 75 -> "Speed"
                    scoreValue >= 45 -> "Stable"
                    else -> "Bad"
                },
                style = MaterialTheme.typography.bodySmall,
                color = color
            )
        }
    }
}

@Composable
private fun CapabilityRow(caps: Set<String>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (caps.contains("beamforming")) {
            CapabilityChip("Beamforming")
        }
        if (caps.contains("mu-mimo") || caps.contains("mumimo")) {
            CapabilityChip("MU-MIMO")
        }
        if (caps.contains("bss") || caps.contains("bss-coloring") || caps.contains("bsscoloring")) {
            CapabilityChip("BSS Coloring")
        }
    }
}

@Composable
private fun CapabilityChip(label: String) {
    Card {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                painter = painterResource(id = AndroidR.drawable.ic_menu_info_details),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(text = label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun EmaSparkline(points: List<EmaPoint>, modifier: Modifier = Modifier) {
    val sanitized = points
        .asSequence()
        .filter { it.value.isFinite() }
        .map { point -> point.copy(value = point.value.coerceIn(0f, 100f)) }
        .toList()

    val lineColor = when {
        sanitized.lastOrNull()?.value?.let { it >= 75f } == true -> MaterialTheme.colorScheme.primary
        sanitized.lastOrNull()?.value?.let { it >= 45f } == true -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Card(modifier = modifier) {
        if (sanitized.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Not enough data", style = MaterialTheme.typography.bodySmall)
            }
            return@Card
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            val w = size.width
            val h = size.height
            drawLine(
                color = trackColor,
                start = Offset(0f, h),
                end = Offset(w, h),
                strokeWidth = 2f
            )
            drawLine(
                color = trackColor.copy(alpha = 0.6f),
                start = Offset(0f, h / 2f),
                end = Offset(w, h / 2f),
                strokeWidth = 1f
            )

            if (sanitized.size == 1) {
                val y = h - (sanitized.first().value / 100f) * h
                val p = Offset(w, y)
                drawCircle(color = lineColor, radius = 5f, center = p)
                return@Canvas
            }

            val minTs = sanitized.first().tsMs
            val maxTs = sanitized.last().tsMs
            val spanTs = (maxTs - minTs).coerceAtLeast(1L)
            val path = Path()
            val firstX = ((sanitized.first().tsMs - minTs).toFloat() / spanTs.toFloat()) * w
            val firstY = h - (sanitized.first().value / 100f) * h
            path.moveTo(firstX, firstY)
            var lastPoint = Offset(firstX, firstY)

            for (i in 1 until sanitized.size) {
                val x = ((sanitized[i].tsMs - minTs).toFloat() / spanTs.toFloat()) * w
                val y = h - (sanitized[i].value / 100f) * h
                path.lineTo(x, y)
                lastPoint = Offset(x, y)
            }

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )
            drawCircle(color = lineColor, radius = 5f, center = lastPoint)
        }
    }
}

@Composable
private fun rememberEmaHistory(
    sampleRaw: String?,
    windowMs: Long = 60_000L,
    maxPoints: Int = 120
): List<EmaPoint> {
    val history = rememberSaveable(
        saver = listSaver(
            save = {
                it.map { point -> "${point.tsMs}:${point.value}" }
            },
            restore = { saved ->
                saved.mapNotNull { raw ->
                    val idx = raw.indexOf(':')
                    if (idx <= 0) return@mapNotNull null
                    val ts = raw.substring(0, idx).toLongOrNull() ?: return@mapNotNull null
                    val value = raw.substring(idx + 1).toFloatOrNull() ?: return@mapNotNull null
                    EmaPoint(ts, value)
                }.toMutableStateList()
            }
        )
    ) { mutableStateListOf<EmaPoint>() }

    LaunchedEffect(sampleRaw) {
        val parsed = sampleRaw?.toFloatOrNull() ?: return@LaunchedEffect
        if (!parsed.isFinite()) return@LaunchedEffect
        val normalized = parsed.coerceIn(0f, 100f)
        val now = System.currentTimeMillis()

        while (history.isNotEmpty() && now - history.first().tsMs > windowMs) {
            history.removeAt(0)
        }

        val last = history.lastOrNull()
        if (last != null && kotlin.math.abs(last.value - normalized) < 0.0001f) {
            return@LaunchedEffect
        }

        history.add(EmaPoint(tsMs = now, value = normalized))
        while (history.size > maxPoints) {
            history.removeAt(0)
        }
    }

    return history
}

private fun parseDetails(details: String): Map<String, String> {
    if (details.isBlank()) return emptyMap()
    val out = mutableMapOf<String, String>()
    details.trim().split(' ').forEach { token ->
        val idx = token.indexOf('=')
        if (idx <= 0) return@forEach
        val key = token.substring(0, idx).trim()
        val value = token.substring(idx + 1).trim()
        if (key.isNotBlank()) {
            out[key] = value
        }
    }
    return out
}

private fun extractCapabilities(details: String): Set<String> {
    val capsValue = parseDetails(details)["caps"].orEmpty().lowercase()
    if (capsValue.isBlank()) return emptySet()
    return capsValue
        .split(',', '|', ';')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()
}

@Composable
private fun ChannelAnalysisDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onReadCache: () -> String?,
    onApplyChannelChange: (channel: Int, band: String) -> Unit = { _, _ -> },
    onReadApplyStatus: () -> String? = { null },
    currentChannelHint: String = "",
) {
    var recommendation by remember { mutableStateOf<ChannelRecommendation?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showConfirmation by remember { mutableStateOf(false) }
    var isRefreshingApplyStatus by remember { mutableStateOf(false) }
    var applyStatusText by remember { mutableStateOf<String?>(null) }
    var applyStatusIsError by remember { mutableStateOf(false) }
    var applyStatusTrigger by remember { mutableStateOf(0) }

    fun readApplyStatus() {
        isRefreshingApplyStatus = true
        val raw = onReadApplyStatus()
        if (raw.isNullOrBlank()) {
            applyStatusText = "No recent channel-change status"
            applyStatusIsError = false
            isRefreshingApplyStatus = false
            return
        }

        try {
            val json = JSONObject(raw)
            val status = json.optString("status", "unknown")
            if (status == "ok") {
                val channel = json.optInt("channel", -1)
                val band = json.optString("band", "-")
                val old = json.optString("old_channel", "-")
                applyStatusText = "Applied: channel $channel ($band), before: $old"
                applyStatusIsError = false
            } else {
                val reason = json.optString("reason", "error_desconocido")
                val detail = json.optString("detail", "")
                applyStatusText = if (detail.isNotBlank()) {
                    "Error al aplicar: $reason ($detail)"
                } else {
                    "Error al aplicar: $reason"
                }
                applyStatusIsError = true
            }
        } catch (_: Exception) {
            applyStatusText = "Unreadable state: ${raw.take(100)}"
            applyStatusIsError = true
        }
        isRefreshingApplyStatus = false
    }

    LaunchedEffect(isOpen) {
        if (isOpen) {
            recommendation = null
            isLoading = true
            errorMessage = null
            
            // Polling every 2 seconds for max 2 minutes (120s)
            var attempts = 0
            while (isOpen && attempts < 60 && recommendation == null) {
                kotlinx.coroutines.delay(2000)

                val cacheContent = onReadCache()
                if (!cacheContent.isNullOrBlank()) {
                    try {
                        val parsed = parseChannelRecommendation(cacheContent)
                        if (parsed != null) {
                            recommendation = parsed
                            isLoading = false
                        }
                    } catch (e: Exception) {
                        // Continue polling
                    }
                }
                attempts++
            }
            
            if (recommendation == null && attempts >= 60) {
                errorMessage = "Tiempo de espera agotado. Intenta de nuevo."
                isLoading = false
            }
        } else {
            // Reset when closed
            recommendation = null
            isLoading = true
            errorMessage = null
            isRefreshingApplyStatus = false
            applyStatusText = null
            applyStatusIsError = false
            applyStatusTrigger = 0
        }
    }

    val hintedCurrentChannel = currentChannelHint.trim().toIntOrNull()?.takeIf { it > 0 }
    val activeCurrentChannel = recommendation?.currentChannel ?: hintedCurrentChannel
    val alreadyOnRecommended = recommendation != null &&
        activeCurrentChannel != null &&
        activeCurrentChannel == recommendation!!.channel
    val canChangeChannel = recommendation != null && !alreadyOnRecommended

    LaunchedEffect(applyStatusTrigger) {
        if (applyStatusTrigger > 0) {
            // Router applies and reloads WiFi, give it a small window before reading status.
            kotlinx.coroutines.delay(12000)
            readApplyStatus()
        }
    }

    if (isOpen) {
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canChangeChannel) {
                        Button(
                            onClick = { showConfirmation = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (recommendation!!.scoreGap >= 5) "Change Channel" else "Change Anyway")
                        }
                    }
                    Button(onClick = onDismiss) {
                        Text(if (recommendation != null || errorMessage != null) "Close" else "Cancel")
                    }
                }
            },
            title = {
                Text(if (recommendation != null) "Channel recommendation" else "Analyzing channels...")
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when {
                        recommendation != null -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Recommended channel:",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        Text(
                                            "${recommendation!!.channel}",
                                            style = MaterialTheme.typography.headlineMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Current channel:", style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            activeCurrentChannel?.toString() ?: "-",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    HorizontalDivider()
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Score:", style = MaterialTheme.typography.bodySmall)
                                        Text("${recommendation!!.score}", style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Mejora:", style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            "+${recommendation!!.scoreGap}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (recommendation!!.scoreGap >= 15) 
                                                MaterialTheme.colorScheme.primary 
                                            else 
                                                MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Band:", style = MaterialTheme.typography.bodySmall)
                                        Text("${recommendation!!.band}", style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("RF Model:", style = MaterialTheme.typography.bodySmall)
                                        Text("${recommendation!!.rfModel}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    
                                    if (recommendation!!.scoreGap >= 15) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "⚡ Mejora significativa disponible",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Center
                                        )
                                    }

                                    if (alreadyOnRecommended) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "You are on the recommended channel",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Center
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = { readApplyStatus() },
                                        enabled = !isRefreshingApplyStatus,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (isRefreshingApplyStatus) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Refrescando estado...")
                                        } else {
                                            Text("Refrescar estado")
                                        }
                                    }

                                    applyStatusText?.let { statusText ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (applyStatusIsError) {
                                                    MaterialTheme.colorScheme.errorContainer
                                                } else {
                                                    MaterialTheme.colorScheme.secondaryContainer
                                                }
                                            )
                                        ) {
                                            Text(
                                                text = statusText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (applyStatusIsError) {
                                                    MaterialTheme.colorScheme.onErrorContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSecondaryContainer
                                                },
                                                modifier = Modifier.padding(10.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        errorMessage != null -> {
                            Icon(
                                painter = painterResource(id = AndroidR.drawable.ic_dialog_alert),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                errorMessage!!,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        isLoading -> {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(60.dp)
                                    .padding(16.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Requesting router analysis...",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        )
    }
    
    // P4: Confirmation dialog for channel change
    if (showConfirmation && recommendation != null) {
        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            title = {
                Text("Change Wi-Fi channel?", style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "The router Wi-Fi channel will be changed to ${recommendation!!.channel} (band ${recommendation!!.band}).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = AndroidR.drawable.ic_dialog_alert),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    "Warning",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    "Your Wi-Fi connection will be lost for ~10 seconds on both bands (2.4GHz + 5GHz).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    Text(
                        "Do you confirm the change?",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onApplyChannelChange(recommendation!!.channel, recommendation!!.band)
                        showConfirmation = false
                        applyStatusText = "Change requested. Waiting for router confirmation..."
                        applyStatusIsError = false
                        applyStatusTrigger += 1
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
                ) {
                    Text("Yes, change")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun detectGatewayIp(daemonState: Map<String, String>, fallback: String): String {
    val candidates = listOf(
        daemonState["router.ip"],
        daemonState["router_ip"],
        daemonState["gateway"],
        daemonState["gw"],
        daemonState["wifi.gateway"],
        daemonState["wifi.gw"],
        daemonState["mobile.gateway"],
        daemonState["mobile.gw"],
        fallback
    )

    return candidates
        .map { it.orEmpty().trim() }
        .firstOrNull { it.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")) }
        .orEmpty()
}

@Preview(showBackground = true)
@Composable
fun KitsunpingPreview() {
    KitsunpingTheme {
        KitsunpingApp(ModuleSnapshot.empty(), false)
    }
}
