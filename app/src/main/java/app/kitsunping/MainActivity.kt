package app.kitsunping

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.core.app.NotificationManagerCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.kitsunping.data.files.ModuleFileGateway
import app.kitsunping.data.root.RootCommandExecutor
import app.kitsunping.data.settings.UiSettingsStore
import app.kitsunping.domain.events.PolicyEventDispatcher
import app.kitsunping.feature.speedtest.SpeedTestRunConfig
import app.kitsunping.feature.speedtest.SpeedTestRunner
import app.kitsunping.feature.speedtest.SpeedTestUiState
import app.kitsunping.ui.model.AdvancedDialog
import app.kitsunping.ui.screens.KitsunpingApp
import app.kitsunping.ui.theme.AppThemeMode
import app.kitsunping.ui.theme.KitsunpingTheme
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private lateinit var eventStore: PolicyEventStore
    private lateinit var repository: ModuleRepository
    private lateinit var uiSettingsStore: UiSettingsStore
    private lateinit var rootCommandExecutor: RootCommandExecutor
    private lateinit var moduleFileGateway: ModuleFileGateway
    private lateinit var targetPolicyModelStore: TargetPolicyModelStore
    private lateinit var policyRepository: PolicyRepository
    private lateinit var appScanner: AppScanner
    private lateinit var policyEventDispatcher: PolicyEventDispatcher
    private lateinit var speedTestRunner: SpeedTestRunner
    private var latestSnapshot by mutableStateOf(ModuleSnapshot.empty())
    private var routerFiles by mutableStateOf<List<String>>(emptyList())
    private var advancedDialog by mutableStateOf<AdvancedDialog?>(null)
    private var highlightPulse by mutableStateOf(false)
    private var appThemeMode by mutableStateOf(AppThemeMode.KITSUNPING)
    private var developerMode by mutableStateOf(false)
    private var routerPairing by mutableStateOf(RouterPairingState())
    private var routerSignatureUi by mutableStateOf(RouterSignatureUi())
    private var installedApps by mutableStateOf<List<InstalledAppEntry>>(emptyList())
    private var appPolicies by mutableStateOf<Map<String, TargetPolicyRule>>(emptyMap())
    private var runtimePolicies by mutableStateOf<Map<String, TargetPolicyRule>>(emptyMap())
    private var policySyncSummary by mutableStateOf<List<String>>(emptyList())
    private var policyApplyInProgress by mutableStateOf(false)
    private var policyApplyQueued = false
    private var pendingPolicySyncRunnable: Runnable? = null
    private var scannedPairPayload by mutableStateOf("")
    private var lowNetworkSimulationEnabled by mutableStateOf(false)
    private var ipv6CalibrationEnabled by mutableStateOf(false)
    private var granularLatencyEnabled by mutableStateOf(false)
    private var lowNetworkTestOffset by mutableStateOf(0)
    private var bootCustomProfile by mutableStateOf("none")
    private var speedTestState by mutableStateOf(SpeedTestUiState())
    private var notificationsDialogShown = false
    private var notificationPermissionRequested = false
    private var lastRouterSyncMs: Long = 0L
    private var lastRouterSyncPayloadHash: Int = 0
    private var routerStatusSeq: Long = 0L

    private val clearPulse = Runnable { highlightPulse = false }
    private val refreshHandler = Handler(Looper.getMainLooper())
    private var lastBroadcastMs: Long = 0L

    private val refreshRunnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val elapsed = now - lastBroadcastMs
            if (elapsed >= REFRESH_INTERVAL_MS) {
                refreshSnapshot()
                refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS)
            } else {
                val delay = (REFRESH_INTERVAL_MS - elapsed).coerceAtLeast(500L)
                refreshHandler.postDelayed(this, delay)
            }
        }
    }

    private val qrScanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data
        val scanResult = data?.getStringExtra("SCAN_RESULT")
            ?: data?.dataString
            ?: ""
        if (scanResult.isNotBlank()) {
            handlePairPayload(scanResult)
        }
    }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                openQrScannerInternal()
            } else {
                Toast.makeText(this, "Camera permission is required to scan QR", Toast.LENGTH_LONG).show()
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                notificationsDialogShown = false
            } else {
                promptEnableNotificationsIfNeeded(force = true)
            }
        }

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != PolicyEventContract.ACTION_UPDATE) return
            lastBroadcastMs = System.currentTimeMillis()
            val payload = intent.getStringExtra(PolicyEventContract.EXTRA_PAYLOAD)
            if (!payload.isNullOrBlank()) {
                eventStore.saveJson(payload)
            } else {
                val event = intent.getStringExtra(PolicyEventContract.EXTRA_EVENT) ?: "unknown"
                val ts = intent.getStringExtra(PolicyEventContract.EXTRA_TS)?.toLongOrNull()
                    ?: (System.currentTimeMillis() / 1000)
                eventStore.saveJson(
                    PolicyEvent(
                        ts = ts,
                        target = "unknown",
                        appliedProfile = 0,
                        propsApplied = 0,
                        propsFailed = 0,
                        propsFailedList = emptyList(),
                        calibrateState = "unknown",
                        calibrateTs = 0,
                        event = event
                    ).toJsonString()
                )
            }
            Thread {
                val snapshot = repository.loadSnapshot()
                NotificationHelper.handleSnapshot(this@MainActivity, snapshot)
            }.start()
            refreshSnapshot()
        }
    }

    // M4: Receiver for channel availability notifications
    private val channelNotificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != "com.kitsunping.ACTION_CHANNEL_AVAILABLE") return
            
            val recommendedChannel = intent.getStringExtra("recommended_channel") ?: return
            val currentChannel = intent.getStringExtra("current_channel") ?: return
            val scoreGap = intent.getIntExtra("score_gap", 0)
            val band = intent.getStringExtra("band") ?: "unknown"
            
            NotificationHelper.showChannelAvailableNotification(
                context,
                recommendedChannel,
                currentChannel,
                scoreGap,
                band
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        eventStore = PolicyEventStore(this)
        repository = ModuleRepository(this)
        uiSettingsStore = UiSettingsStore(this)
        rootCommandExecutor = RootCommandExecutor()
        moduleFileGateway = ModuleFileGateway(MODULE_ROOT)
        targetPolicyModelStore = TargetPolicyModelStore(this)
        policyRepository = PolicyRepository(targetPolicyModelStore, moduleFileGateway, rootCommandExecutor)
        appScanner = AppScanner(this)
        policyEventDispatcher = PolicyEventDispatcher(this)
        speedTestRunner = SpeedTestRunner(applicationContext) { state ->
            speedTestState = state
        }
        appThemeMode = uiSettingsStore.loadThemeMode()
        developerMode = uiSettingsStore.loadDeveloperMode()
        routerPairing = loadRouterPairingState()
        routerStatusSeq = loadRouterStatusSeq()
        handleIncomingPairIntent(intent)
        latestSnapshot = repository.loadSnapshot()
        lowNetworkSimulationEnabled = loadLowNetworkSimulationState()
        ipv6CalibrationEnabled = loadIpv6CalibrationState()
        granularLatencyEnabled = loadGranularLatencyState()
        lowNetworkTestOffset = loadLowNetworkTestOffset()
        bootCustomProfile = loadBootCustomProfileState()
        requestNotificationPermissionIfNeeded()
        // requestCameraPermissionIfNeeded() // TODO: disabled for now, QR camera flow not in active use.

        setContent {
            KitsunpingTheme(themeMode = appThemeMode) {
                KitsunpingApp(
                    snapshot = latestSnapshot,
                    highlightPulse = highlightPulse,
                    routerFiles = routerFiles,
                    advancedDialog = advancedDialog,
                    currentThemeMode = appThemeMode,
                    developerMode = developerMode,
                    routerPaired = routerPairing.paired,
                    routerIp = routerPairing.routerIp,
                    routerId = routerPairing.routerId,
                    routerHasToken = routerPairing.token.isNotBlank(),
                    routerSignatureBssid = routerSignatureUi.bssid,
                    routerSignatureBand = routerSignatureUi.band,
                    routerSignatureChannel = routerSignatureUi.channel,
                    routerSignatureWidth = routerSignatureUi.width,
                    scannedPairPayload = scannedPairPayload,
                    installedApps = installedApps,
                    appPolicies = appPolicies,
                    runtimePolicies = runtimePolicies,
                    policySyncSummary = policySyncSummary,
                    policyApplyInProgress = policyApplyInProgress,
                    speedTestState = speedTestState,
                    onDismissDialog = { advancedDialog = null },
                    onRequestCalibrate = {
                        sendPolicyEvent("user_requested_calibrate", null)
                        runRootCommands(
                            listOf(
                                "resetprop persist.kitsunping.user_event user_requested_calibrate || setprop persist.kitsunping.user_event user_requested_calibrate"
                            )
                        )
                    },
                    onRequestStart = {
                        startDaemon()
                    },
                    onRequestRestart = {
                        sendPolicyEvent("user_requested_restart", null)
                        runRootCommands(
                            listOf(
                                "resetprop persist.kitsunping.user_event user_requested_restart || setprop persist.kitsunping.user_event user_requested_restart"
                            )
                        )
                        requestSnapshotBurst()
                    },
                    onRequestCheckDaemonPid = {
                        runRootCommandCapture(
                            "Verificar PID daemon",
                            """
                            PID_FILE=/data/adb/modules/Kitsunping/cache/daemon.pid
                            if [ ! -f "${'$'}PID_FILE" ]; then
                                echo "PID file does not exist: ${'$'}PID_FILE"
                                exit 1
                            fi
                            PID=${'$'}(cat "${'$'}PID_FILE" 2>/dev/null)
                            if ! echo "${'$'}PID" | grep -Eq '^[0-9]+${'$'}'; then
                                echo "Invalid PID: ${'$'}PID"
                                exit 1
                            fi
                            if [ ! -d "/proc/${'$'}PID" ]; then
                                echo "PID is not running: ${'$'}PID"
                                exit 1
                            fi
                            CMD=${'$'}(tr '\000' ' ' < "/proc/${'$'}PID/cmdline" 2>/dev/null)
                            [ -z "${'$'}CMD" ] && CMD=${'$'}(cat "/proc/${'$'}PID/comm" 2>/dev/null)
                            echo "pid=${'$'}PID"
                            echo "cmdline=${'$'}CMD"
                            if echo "${'$'}CMD" | grep -Eiq 'daemon|kitsunping'; then
                                echo "OK: daemon inicializado"
                                exit 0
                            fi
                            echo "WARN: PID activo pero nombre no coincide con daemon/kitsunping"
                            exit 2
                            """.trimIndent()
                        )
                    },
                    onRequestProfile = { profile ->
                        val payload = "{\"target\":\"$profile\",\"ts\":${System.currentTimeMillis() / 1000}}"
                        sendPolicyEvent("request_profile", payload)
                        runRootCommands(
                            listOf(
                                "resetprop persist.kitsunping.user_event_data $profile || setprop persist.kitsunping.user_event_data $profile",
                                "resetprop persist.kitsunping.user_event request_profile || setprop persist.kitsunping.user_event request_profile"
                            )
                        )
                    },
                    onRequestThemeMode = { updateThemeMode(it) },
                    onRequestDeveloperMode = { updateDeveloperMode(it) },
                    lowNetworkSimulationEnabled = lowNetworkSimulationEnabled,
                    onRequestLowNetworkSimulation = { updateLowNetworkSimulation(it) },
                    ipv6CalibrationEnabled = ipv6CalibrationEnabled,
                    onRequestIpv6Calibration = { updateIpv6Calibration(it) },
                    granularLatencyEnabled = granularLatencyEnabled,
                    onRequestGranularLatency = { updateGranularLatency(it) },
                    lowNetworkTestOffset = lowNetworkTestOffset,
                    onRequestLowNetworkTestOffset = { updateLowNetworkTestOffset(it) },
                    bootCustomProfile = bootCustomProfile,
                    onRequestBootCustomProfile = { updateBootCustomProfile(it) },
                    onRequestPairRouter = { ip, code -> pairRouter(ip, code) },
                    onRequestUnpairRouter = { unpairRouter() },
                    onRequestScanQr = { launchQrScanner() },
                    onRequestChannelScan = {
                        sendPolicyEvent("request_channel_scan", null)
                        runRootCommands(
                            listOf(
                                "resetprop persist.kitsuneping.user_event_data 2.4GHz || setprop persist.kitsuneping.user_event_data 2.4GHz",
                                "resetprop persist.kitsuneping.user_event request_channel_scan || setprop persist.kitsuneping.user_event request_channel_scan",
                                "resetprop persist.kitsunping.user_event_data 2.4GHz || setprop persist.kitsunping.user_event_data 2.4GHz",
                                "resetprop persist.kitsunping.user_event request_channel_scan || setprop persist.kitsunping.user_event request_channel_scan"
                            )
                        )
                    },
                    onReadChannelCache = {
                        try {
                            // Read from /sdcard which is writable
                            val cachePath = "/sdcard/kitsunping_cache/router_channel_recommendation.json"
                            val result = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat $cachePath")).inputStream.bufferedReader().use { it.readText() }
                            if (result.isNotBlank()) result else null
                        } catch (e: Exception) {
                            null
                        }
                    },
                    onApplyChannelChange = { channel, band ->
                        sendPolicyEvent("request_channel_change", "$channel:$band")
                        runRootCommands(
                            listOf(
                                "resetprop persist.kitsuneping.user_event_data $channel:$band || setprop persist.kitsuneping.user_event_data $channel:$band",
                                "resetprop persist.kitsuneping.user_event channel_change_request || setprop persist.kitsuneping.user_event channel_change_request"
                            )
                        )
                    },
                    onReadChannelApplyStatus = {
                        try {
                            val statusPath = "/sdcard/kitsunping_cache/router_channel_apply_response.json"
                            val result = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat $statusPath")).inputStream.bufferedReader().use { it.readText() }
                            if (result.isNotBlank()) result else null
                        } catch (e: Exception) {
                            null
                        }
                    },
                    onRequestOpenAppSettings = { openAppSettings() },
                    onRequestRefreshRouterFiles = { refreshRouterFiles() },
                    onRequestReadFile = { title, path -> openRootFile(title, path) },
                    onRequestViewRouterLast = { showRouterLastView() },
                    onRequestOpenRouterLast = { openRouterLastTarget() },
                    onRequestRunCommand = { title, cmd -> runRootCommandCapture(title, cmd) },
                    onRequestRunSpeedTest = { config: SpeedTestRunConfig -> speedTestRunner.start(config) },
                    onRequestSaveAppPolicy = { packageName, profile, priority, enabled ->
                        saveAppPolicy(packageName, profile, priority, enabled)
                    },
                    onRequestApplyPolicies = {
                        applyPolicyModelToRuntime()
                    }
                )
            }
        }

        refreshSnapshot()
        refreshRouterFiles()
        Thread {
            refreshPolicyState()
            syncTargetPolicyModelToRuntime()
            refreshPolicyState()
        }.start()
    }

    override fun onStart() {
        super.onStart()
        if (!notificationPermissionRequested) {
            promptEnableNotificationsIfNeeded()
        }
        val filter = IntentFilter(PolicyEventContract.ACTION_UPDATE)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(updateReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(updateReceiver, filter)
        }
        
        // M4: Register channel availability receiver
        val channelFilter = IntentFilter("com.kitsunping.ACTION_CHANNEL_AVAILABLE")
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(channelNotificationReceiver, channelFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(channelNotificationReceiver, channelFilter)
        }
        
        refreshSnapshot()
        refreshRouterFiles()
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS)
    }

    override fun onStop() {
        refreshHandler.removeCallbacks(clearPulse)
        refreshHandler.removeCallbacks(refreshRunnable)
        pendingPolicySyncRunnable?.let { refreshHandler.removeCallbacks(it) }
        pendingPolicySyncRunnable = null
        unregisterReceiver(updateReceiver)
        unregisterReceiver(channelNotificationReceiver)  // M4: Unregister channel receiver
        super.onStop()
    }

    override fun onDestroy() {
        speedTestRunner.shutdown()
        super.onDestroy()
    }

    private fun refreshSnapshot() {
        Thread {
            val snapshot = repository.loadSnapshot().copy(
                daemonRuntime = inspectDaemonRuntime()
            )
            pushModuleStatusToRouter(snapshot)
            syncRouterPairingFromModule()
            runOnUiThread {
                val previousTs = latestSnapshot.policyEvent.ts
                latestSnapshot = snapshot
                if (snapshot.policyEvent.ts > 0 && snapshot.policyEvent.ts != previousTs) {
                    highlightPulse = true
                    refreshHandler.removeCallbacks(clearPulse)
                    refreshHandler.postDelayed(clearPulse, HIGHLIGHT_DURATION_MS)
                }
            }
        }.start()
    }

    private fun inspectDaemonRuntime(): DaemonRuntimeStatus {
        val output = rootCommandExecutor.runCapture(
            """
            MODDIR=/data/adb/modules/Kitsunping
            PID_FILE="${'$'}MODDIR/cache/daemon.pid"
            pid=""
            cmd=""
            detail="no_pid_file"
            state="STOPPED"
            if [ -f "${'$'}PID_FILE" ]; then
                pid=$(cat "${'$'}PID_FILE" 2>/dev/null | tr -d '\\r\\n')
                detail="pid_file_present"
            fi
            if echo "${'$'}pid" | grep -Eq '^[0-9]+${'$'}'; then
                if [ -d "/proc/${'$'}pid" ]; then
                    cmd=$(tr '\\000' ' ' < "/proc/${'$'}pid/cmdline" 2>/dev/null)
                    [ -z "${'$'}cmd" ] && cmd=$(cat "/proc/${'$'}pid/comm" 2>/dev/null)
                    if echo "${'$'}cmd" | grep -Eiq 'kitsunping|daemon'; then
                        state="RUNNING"
                        detail="process_alive"
                    else
                        state="STALE"
                        detail="pid_reused"
                    fi
                else
                    state="STOPPED"
                    detail="pid_not_running"
                fi
            fi
            echo "state=${'$'}state"
            echo "pid=${'$'}pid"
            echo "detail=${'$'}detail"
            echo "cmd=${'$'}cmd"
            echo "checked_at=$(date +%s 2>/dev/null || echo 0)"
            """.trimIndent()
        )

        val fields = parseKeyValueOutput(output)
        val state = fields["state"].orEmpty().ifBlank { "UNKNOWN" }.uppercase()
        val pid = fields["pid"].orEmpty()
        val command = fields["cmd"].orEmpty()
        val detail = fields["detail"].orEmpty().ifBlank { "no verification" }
        val checkedAt = fields["checked_at"]?.toLongOrNull() ?: 0L

        return DaemonRuntimeStatus(
            state = state,
            isRunning = state == "RUNNING",
            pid = pid,
            command = command,
            detail = detail,
            checkedAt = checkedAt
        )
    }

    private fun parseKeyValueOutput(output: String): Map<String, String> {
        return output
            .lineSequence()
            .map { line -> if (line.startsWith("out:")) line.removePrefix("out:") else line }
            .map { it.trim() }
            .filter { it.contains('=') }
            .mapNotNull { line ->
                val idx = line.indexOf('=')
                if (idx <= 0) return@mapNotNull null
                line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            }
            .toMap(LinkedHashMap())
    }

    private fun startDaemon() {
        sendPolicyEvent("user_requested_start", null)
        Thread {
            val output = rootCommandExecutor.runCapture(
                """
                MODDIR=/data/adb/modules/Kitsunping
                PID_FILE="${'$'}MODDIR/cache/daemon.pid"
                start_script() {
                    script="${'$'}1"
                    [ -f "${'$'}script" ] || return 1
                    chmod 0755 "${'$'}script" 2>/dev/null || true
                    sh "${'$'}script" >/dev/null 2>&1 &
                    return 0
                }
                resetprop persist.kitsunping.user_event user_requested_start >/dev/null 2>&1 || setprop persist.kitsunping.user_event user_requested_start >/dev/null 2>&1 || true
                started_path=""
                for candidate in \
                    "${'$'}MODDIR/installer/service.sh" \
                    "${'$'}MODDIR/service.sh"
                do
                    if start_script "${'$'}candidate"; then
                        started_path="${'$'}candidate"
                        break
                    fi
                done
                sleep 2
                pid=""
                if [ -f "${'$'}PID_FILE" ]; then
                    pid=$(cat "${'$'}PID_FILE" 2>/dev/null | tr -d '\\r\\n')
                fi
                if ! echo "${'$'}pid" | grep -Eq '^[0-9]+${'$'}' || [ ! -d "/proc/${'$'}pid" ]; then
                    for candidate in \
                        "${'$'}MODDIR/addon/daemon/daemon.sh" \
                        "${'$'}MODDIR/addon/daemon.sh"
                    do
                        if start_script "${'$'}candidate"; then
                            started_path="${'$'}candidate"
                            break
                        fi
                    done
                fi
                sleep 2
                pid=""
                cmd=""
                if [ -f "${'$'}PID_FILE" ]; then
                    pid=$(cat "${'$'}PID_FILE" 2>/dev/null | tr -d '\\r\\n')
                fi
                if echo "${'$'}pid" | grep -Eq '^[0-9]+${'$'}' && [ -d "/proc/${'$'}pid" ]; then
                    cmd=$(tr '\\000' ' ' < "/proc/${'$'}pid/cmdline" 2>/dev/null)
                    [ -z "${'$'}cmd" ] && cmd=$(cat "/proc/${'$'}pid/comm" 2>/dev/null)
                    echo "status=RUNNING"
                    echo "pid=${'$'}pid"
                    echo "started_path=${'$'}started_path"
                    echo "cmd=${'$'}cmd"
                    exit 0
                fi
                echo "status=STOPPED"
                echo "pid=${'$'}pid"
                echo "started_path=${'$'}started_path"
                exit 1
                """.trimIndent()
            )

            requestSnapshotBurst()
            val parsed = parseKeyValueOutput(output)
            val status = parsed["status"].orEmpty().ifBlank { "UNKNOWN" }
            val pid = parsed["pid"].orEmpty().ifBlank { "no pid" }
            val startedPath = parsed["started_path"].orEmpty().ifBlank { "no script" }

            runOnUiThread {
                Toast.makeText(
                    this,
                    "Daemon $status | pid=$pid | script=$startedPath",
                    Toast.LENGTH_LONG
                ).show()
            }
        }.start()
    }

    private fun requestSnapshotBurst() {
        refreshSnapshot()
        refreshHandler.postDelayed({ refreshSnapshot() }, 1500L)
        refreshHandler.postDelayed({ refreshSnapshot() }, 4500L)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionRequested = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                notificationsDialogShown = false
            }
        }
    }

    private fun promptEnableNotificationsIfNeeded(force: Boolean = false) {
        val enabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
        if (enabled) {
            notificationsDialogShown = false
            return
        }
        if (!force && notificationsDialogShown) return
        notificationsDialogShown = true

        AlertDialog.Builder(this)
            .setTitle("Notifications disabled")
            .setMessage("Enable Kitsunping notifications to see Wi-Fi and profile changes in real time.")
            .setPositiveButton("Open settings") { _, _ ->
                runCatching {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    }
                    startActivity(intent)
                }
            }
            .setNegativeButton("Not now", null)
            .show()
    }

    private fun requestCameraPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) return
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingPairIntent(intent)
    }

    private fun loadLowNetworkSimulationState(): Boolean {
        val raw = rootCommandExecutor.runCapture("getprop persist.kitsunping.dev_score_sim_enable 2>/dev/null | tr -d '\\r\\n'")
        val token = raw.lineSequence().lastOrNull()?.trim().orEmpty()
        return token == "1" || token.equals("true", ignoreCase = true) || token.equals("on", ignoreCase = true)
    }

    private fun updateLowNetworkSimulation(enabled: Boolean) {
        lowNetworkSimulationEnabled = enabled
        val enableValue = if (enabled) "1" else "0"
        val commands = listOf(
            "resetprop persist.kitsunping.dev_score_sim_enable $enableValue || setprop persist.kitsunping.dev_score_sim_enable $enableValue",
            "resetprop persist.kitsunping.dev_score_divisor 2 || setprop persist.kitsunping.dev_score_divisor 2"
        )
        runRootCommands(commands)
    }

    private fun loadLowNetworkTestOffset(): Int {
        val raw = rootCommandExecutor.runCapture(
            "if [ -f /data/local/tmp/lownet.test ]; then cat /data/local/tmp/lownet.test; " +
                "elif [ -f /debug_ramdisk/.magisk/modules/Kitsunping/cache/lownet.test ]; then cat /debug_ramdisk/.magisk/modules/Kitsunping/cache/lownet.test; " +
                "elif [ -f /data/adb/modules/Kitsunping/cache/lownet.test ]; then cat /data/adb/modules/Kitsunping/cache/lownet.test; else echo 0; fi | tr -d '\\r\\n'"
        )
        val token = raw.lineSequence().lastOrNull()?.trim().orEmpty()
        return token.toIntOrNull()?.coerceAtLeast(0) ?: 0
    }

    private fun loadIpv6CalibrationState(): Boolean {
        val raw = rootCommandExecutor.runCapture("getprop persist.kitsunping.calibrate_ipv6_enable 2>/dev/null | tr -d '\\r\\n'")
        val token = raw.lineSequence().lastOrNull()?.trim().orEmpty()
        return token == "1" || token.equals("true", ignoreCase = true) || token.equals("on", ignoreCase = true)
    }

    private fun updateIpv6Calibration(enabled: Boolean) {
        ipv6CalibrationEnabled = enabled
        val value = if (enabled) "1" else "0"
        runRootCommands(
            listOf(
                "resetprop persist.kitsunping.calibrate_ipv6_enable $value || setprop persist.kitsunping.calibrate_ipv6_enable $value"
            )
        )
    }

    private fun loadGranularLatencyState(): Boolean {
        val raw = rootCommandExecutor.runCapture("getprop persist.kitsunping.calibrate_granular_latency_enable 2>/dev/null | tr -d '\\r\\n'")
        val token = raw.lineSequence().lastOrNull()?.trim().orEmpty()
        return token == "1" || token.equals("true", ignoreCase = true) || token.equals("on", ignoreCase = true)
    }

    private fun updateGranularLatency(enabled: Boolean) {
        granularLatencyEnabled = enabled
        val value = if (enabled) "1" else "0"
        runRootCommands(
            listOf(
                "resetprop persist.kitsunping.calibrate_granular_latency_enable $value || setprop persist.kitsunping.calibrate_granular_latency_enable $value"
            )
        )
    }

    private fun updateLowNetworkTestOffset(value: Int) {
        val safeValue = value.coerceAtLeast(0)
        lowNetworkTestOffset = safeValue
        lowNetworkSimulationEnabled = false
        val commands = listOf(
            "mkdir -p /data/local/tmp",
            "echo $safeValue > /data/local/tmp/lownet.test",
            "mkdir -p /data/adb/modules/Kitsunping/cache",
            "mkdir -p /debug_ramdisk/.magisk/modules/Kitsunping/cache",
            "resetprop persist.kitsunping.dev_score_sim_enable 0 || setprop persist.kitsunping.dev_score_sim_enable 0",
            "echo $safeValue > /data/adb/modules/Kitsunping/cache/lownet.test || true",
            "echo $safeValue > /debug_ramdisk/.magisk/modules/Kitsunping/cache/lownet.test || true"
        )
        runRootCommands(commands)
    }

    private fun loadBootCustomProfileState(): String {
        val raw = rootCommandExecutor.runCapture("getprop persist.kitsunping.boot_profile 2>/dev/null | tr -d '\\r\\n'")
        return normalizeBootCustomProfile(raw.lineSequence().lastOrNull()?.trim().orEmpty())
    }

    private fun normalizeBootCustomProfile(value: String): String {
        return when (value.trim().lowercase()) {
            "stable", "speed", "gaming", "benchmark", "benchmark_gaming", "benchmark_speed", "benchmarks" -> value.trim().lowercase()
            else -> "none"
        }
    }

    private fun updateBootCustomProfile(value: String) {
        val normalized = normalizeBootCustomProfile(value)
        bootCustomProfile = normalized
        val propValue = when (normalized) {
            "benchmarks", "benchmark" -> "benchmark_gaming"
            else -> normalized
        }
        runRootCommands(
            listOf(
                "resetprop persist.kitsunping.boot_profile $propValue || setprop persist.kitsunping.boot_profile $propValue"
            )
        )
    }

    private fun sendPolicyEvent(event: String, payload: String?) {
        try {
            policyEventDispatcher.dispatch(event, payload)
            Toast.makeText(this, "Enviado: $event", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Fallo al enviar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun runRootCommands(cmds: List<String>) {
        Thread {
            val message = rootCommandExecutor.runCommands(cmds)
            runOnUiThread {
                Toast.makeText(this, message.lines().take(6).joinToString("\n"), Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun openRootFile(title: String, path: String) {
        Thread {
            val content = moduleFileGateway.read(path)
            runOnUiThread { advancedDialog = AdvancedDialog(title, if (content.isBlank()) "no data" else content) }
        }.start()
    }

    private fun runRootCommandCapture(title: String, cmd: String) {
        Thread {
            val output = rootCommandExecutor.runCapture(cmd)
            runOnUiThread { advancedDialog = AdvancedDialog(title, output.ifBlank { "no output" }) }
        }.start()
    }

    private fun showRouterLastView() {
        Thread {
            val content = moduleFileGateway.readRouterLastView()
            runOnUiThread { advancedDialog = AdvancedDialog("router.last", content.ifBlank { "no data" }) }
        }.start()
    }

    private fun openRouterLastTarget() {
        Thread {
            val content = moduleFileGateway.readRouterLastTarget()
            runOnUiThread { advancedDialog = AdvancedDialog("router.last target", content.ifBlank { "no data" }) }
        }.start()
    }

    private fun refreshRouterFiles() {
        Thread {
            val files = moduleFileGateway.listRouterFiles()
            val signature = moduleFileGateway.readRouterSignatureRaw()
            val signatureUi = parseRouterSignature(signature)
            runOnUiThread {
                routerFiles = files
                routerSignatureUi = signatureUi
            }
        }.start()
    }

    private fun parseRouterSignature(signature: String): RouterSignatureUi {
        if (signature.isBlank()) return RouterSignatureUi()
        val parts = signature.split('|')
        val bssid = parts.getOrNull(0).orEmpty().trim()
        val band = parts.getOrNull(1).orEmpty().trim()
        val channel = parts.getOrNull(2).orEmpty().trim()
        val width = parts.getOrNull(4).orEmpty().trim()
        return RouterSignatureUi(
            bssid = bssid,
            band = band,
            channel = channel,
            width = width
        )
    }

    private fun clearRouterCacheArtifacts() {
        val cmd = "rm -f $MODULE_CACHE_DIR/router.last $MODULE_CACHE_DIR/router.dni $MODULE_CACHE_DIR/router_*.info"
        rootCommandExecutor.runCapture(cmd)
    }

    private fun loadRouterPairingState(): RouterPairingState {
        val moduleRaw = moduleFileGateway.read(ROUTER_PAIRING_CACHE_PATH)
        if (moduleRaw.isNotBlank()) {
            val moduleState = parseRouterPairingStateFromJson(moduleRaw)
            if (moduleState != null) {
                saveRouterPairingState(moduleState, persistToModule = false)
                return moduleState
            }
        }

        val prefs = getSharedPreferences(PREFS_ROUTER, MODE_PRIVATE)
        val localState = RouterPairingState(
            routerIp = prefs.getString(KEY_ROUTER_IP, "").orEmpty(),
            token = prefs.getString(KEY_ROUTER_TOKEN, "").orEmpty(),
            routerId = prefs.getString(KEY_ROUTER_ID, "").orEmpty(),
            paired = prefs.getBoolean(KEY_ROUTER_PAIRED, false)
        )

        if (localState.paired || localState.routerIp.isNotBlank() || localState.token.isNotBlank()) {
            return localState
        }

        val moduleState = loadRouterPairingStateFromModule()
        if (moduleState.paired || moduleState.routerIp.isNotBlank() || moduleState.token.isNotBlank()) {
            saveRouterPairingState(moduleState, persistToModule = false)
            return moduleState
        }

        return localState
    }

    private fun saveRouterPairingState(state: RouterPairingState, persistToModule: Boolean = true) {
        getSharedPreferences(PREFS_ROUTER, MODE_PRIVATE)
            .edit()
            .putString(KEY_ROUTER_IP, state.routerIp)
            .putString(KEY_ROUTER_TOKEN, state.token)
            .putString(KEY_ROUTER_ID, state.routerId)
            .putBoolean(KEY_ROUTER_PAIRED, state.paired)
            .apply()
        routerPairing = state
        if (persistToModule) {
            Thread { saveRouterPairingStateToModule(state) }.start()
        }
    }

    private fun loadRouterPairingStateFromModule(): RouterPairingState {
        val raw = moduleFileGateway.read(ROUTER_PAIRING_CACHE_PATH)
        if (raw.isBlank()) return RouterPairingState()
        return parseRouterPairingStateFromJson(raw) ?: RouterPairingState()
    }

    private fun parseRouterPairingStateFromJson(raw: String): RouterPairingState? {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        return RouterPairingState(
            routerIp = json.optString("router_ip").orEmpty(),
            token = json.optString("token").orEmpty(),
            routerId = json.optString("router_id").orEmpty(),
            paired = json.optBoolean("paired", false)
        )
    }

    private fun syncRouterPairingFromModule() {
        val raw = moduleFileGateway.read(ROUTER_PAIRING_CACHE_PATH)
        if (raw.isBlank()) return
        val moduleState = parseRouterPairingStateFromJson(raw) ?: return
        if (moduleState == routerPairing) return
        runOnUiThread {
            saveRouterPairingState(moduleState, persistToModule = false)
        }
    }

    private fun saveRouterPairingStateToModule(state: RouterPairingState) {
        val payload = JSONObject()
            .put("router_ip", state.routerIp)
            .put("token", state.token)
            .put("router_id", state.routerId)
            .put("paired", state.paired)
            .put("updated_ts", System.currentTimeMillis() / 1000)
            .toString()

        val cmd = "mkdir -p ${shQuote(MODULE_CACHE_DIR)} && printf %s ${shQuote(payload)} > ${shQuote(ROUTER_PAIRING_CACHE_PATH)} && chmod 600 ${shQuote(ROUTER_PAIRING_CACHE_PATH)}"
        rootCommandExecutor.runCapture(cmd)
    }

    private fun shQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    private fun loadRouterStatusSeq(): Long {
        val prefs = getSharedPreferences(PREFS_ROUTER, MODE_PRIVATE)
        return prefs.getLong(KEY_ROUTER_STATUS_SEQ, 0L).coerceAtLeast(0L)
    }

    @Synchronized
    private fun nextRouterStatusSeq(): Long {
        routerStatusSeq = (routerStatusSeq + 1L).coerceAtLeast(1L)
        getSharedPreferences(PREFS_ROUTER, MODE_PRIVATE)
            .edit()
            .putLong(KEY_ROUTER_STATUS_SEQ, routerStatusSeq)
            .apply()
        return routerStatusSeq
    }

    private fun readPolicyVersionFromModule(): Long {
        val raw = moduleFileGateway.read(MODULE_POLICY_VERSION_PATH)
        return raw.trim().toLongOrNull()?.coerceAtLeast(1L) ?: 1L
    }

    private fun syncTargetPolicyModelToRuntime() {
        val model = policyRepository.loadPolicies()
        if (model.isEmpty()) return
        policyRepository.syncToRuntime(model)
    }

    private fun refreshPolicyState() {
        val apps = appScanner.getLaunchableApps()
        val storedPolicies = policyRepository.loadPolicies()
        val runtimeTargetRaw = moduleFileGateway.read(MODULE_TARGET_PROP_PATH)
        val runtimeModel = targetPolicyModelStore.importFromTargetProp(runtimeTargetRaw)
        runOnUiThread {
            installedApps = apps
            appPolicies = storedPolicies
            runtimePolicies = runtimeModel
        }
    }

    private fun saveAppPolicy(packageName: String, profile: String, priority: String, enabled: Boolean) {
        val pkg = packageName.trim().lowercase()
        if (pkg.isBlank()) return
        val updated = appPolicies.toMutableMap().apply {
            this[pkg] = TargetPolicyRule(profile = profile, priority = priority, enabled = enabled)
        }
        appPolicies = updated
        Thread {
            policyRepository.savePolicies(updated)
            val reloaded = policyRepository.loadPolicies()
            runOnUiThread {
                appPolicies = reloaded
                schedulePolicyRuntimeSync()
            }
        }.start()
    }

    private fun applyPolicyModelToRuntime() {
        pendingPolicySyncRunnable?.let { refreshHandler.removeCallbacks(it) }
        pendingPolicySyncRunnable = null
        if (policyApplyInProgress) {
            policyApplyQueued = true
            return
        }
        val model = appPolicies
        if (model.isEmpty()) return
        policyApplyInProgress = true
        Thread {
            val result = syncPoliciesToRuntime(model)
            runOnUiThread {
                policyApplyInProgress = false
                when (result) {
                    is SyncResult.Success -> {
                        runtimePolicies = result.runtimeModel
                        policySyncSummary = result.summary
                        Toast.makeText(this, "Runtime synced", Toast.LENGTH_SHORT).show()
                    }

                    is SyncResult.RouterFailed -> {
                        runtimePolicies = result.runtimeModel
                        policySyncSummary = result.summary
                        Toast.makeText(this, "Runtime synced, router not connected", Toast.LENGTH_SHORT).show()
                    }

                    is SyncResult.RuntimeFailed -> {
                        policySyncSummary = listOf("✗ runtime sync failed: ${result.reason}")
                        Toast.makeText(this, "Runtime sync failed", Toast.LENGTH_SHORT).show()
                    }
                }
                if (policyApplyQueued) {
                    policyApplyQueued = false
                    schedulePolicyRuntimeSync(delayMs = POLICY_SYNC_DEBOUNCE_MS)
                }
            }
        }.start()
    }

    private fun schedulePolicyRuntimeSync(delayMs: Long = POLICY_SYNC_DEBOUNCE_MS) {
        pendingPolicySyncRunnable?.let { refreshHandler.removeCallbacks(it) }
        val runnable = Runnable {
            pendingPolicySyncRunnable = null
            if (policyApplyInProgress) {
                policyApplyQueued = true
                return@Runnable
            }
            val model = appPolicies
            if (model.isEmpty()) return@Runnable
            policyApplyInProgress = true
            Thread {
                val result = syncPoliciesToRuntime(model)
                runOnUiThread {
                    when (result) {
                        is SyncResult.Success -> {
                            runtimePolicies = result.runtimeModel
                            policySyncSummary = result.summary
                        }

                        is SyncResult.RouterFailed -> {
                            runtimePolicies = result.runtimeModel
                            policySyncSummary = result.summary
                        }

                        is SyncResult.RuntimeFailed -> {
                            policySyncSummary = listOf("✗ runtime sync failed: ${result.reason}")
                        }
                    }
                    policyApplyInProgress = false
                    if (policyApplyQueued) {
                        policyApplyQueued = false
                        schedulePolicyRuntimeSync(delayMs = POLICY_SYNC_DEBOUNCE_MS)
                    }
                }
            }.start()
        }
        pendingPolicySyncRunnable = runnable
        refreshHandler.postDelayed(runnable, delayMs.coerceAtLeast(150L))
    }

    private fun syncPoliciesToRuntime(model: Map<String, TargetPolicyRule>): SyncResult {
        return runCatching {
            policyRepository.syncToRuntime(model)
            val runtimeTargetRaw = moduleFileGateway.read(MODULE_TARGET_PROP_PATH)
            val runtimeModel = targetPolicyModelStore.importFromTargetProp(runtimeTargetRaw)
            val runtimeMatches = model.all { (pkg, rule) ->
                val runtimeRule = runtimeModel[pkg]
                if (rule.enabled) {
                    runtimeRule != null &&
                        runtimeRule.profile == rule.profile &&
                        runtimeRule.priority == rule.priority
                } else {
                    runtimeRule == null
                }
            }
            if (!runtimeMatches) {
                return SyncResult.RuntimeFailed("runtime model mismatch")
            }
            val summary = mutableListOf(
                "✓ target.prop reloaded",
                "✓ runtime updated"
            )
            if (routerPairing.paired) {
                summary += "✓ router QoS apply queued"
                SyncResult.Success(runtimeModel, summary)
            } else {
                summary += "✗ router not connected"
                SyncResult.RouterFailed(runtimeModel, summary)
            }
        }.getOrElse {
            SyncResult.RuntimeFailed(it.message ?: "unknown error")
        }
    }

    private fun extractPairCode(rawInput: String): String {
        val raw = rawInput.trim()
        if (raw.isBlank()) return ""
        if (!raw.startsWith("kitsunping://pair")) return raw
        return Uri.parse(raw).getQueryParameter("pc").orEmpty()
    }

    private fun extractRouterIdFromPairUri(rawInput: String): String {
        val raw = rawInput.trim()
        if (raw.isBlank() || !raw.startsWith("kitsunping://pair")) return ""
        return Uri.parse(raw).getQueryParameter("rid").orEmpty()
    }

    private fun extractRouterIpFromPairUri(rawInput: String): String {
        val raw = rawInput.trim()
        if (raw.isBlank() || !raw.startsWith("kitsunping://pair")) return ""
        return Uri.parse(raw).getQueryParameter("ip").orEmpty()
    }

    private fun handlePairPayload(rawInput: String) {
        val payload = rawInput.trim()
        if (payload.isBlank()) return
        scannedPairPayload = payload

        val routerIpFromQr = extractRouterIpFromPairUri(payload)
        if (routerIpFromQr.isNotBlank() && routerPairing.routerIp != routerIpFromQr) {
            saveRouterPairingState(routerPairing.copy(routerIp = routerIpFromQr), persistToModule = false)
        }
    }

    private fun handleIncomingPairIntent(intent: Intent?) {
        val dataUri = intent?.data ?: return
        if (!dataUri.toString().startsWith("kitsunping://pair")) return
        handlePairPayload(dataUri.toString())
        Toast.makeText(this, "QR de emparejamiento recibido", Toast.LENGTH_SHORT).show()
    }

    private fun isValidRouterToken(token: String): Boolean {
        if (token.length != 32) return false
        return token.all { it in '0'..'9' || it in 'a'..'f' }
    }

    private fun postPairValidate(endpoint: String, payload: JSONObject): Pair<Int, String> {
        return runCatching {
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 3000
                readTimeout = 3000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(payload.toString().toByteArray()) }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            code to body
        }.getOrElse { -1 to (it.message ?: "network_error") }
    }

    private fun buildRouterBases(routerInput: String): List<String> {
        val raw = routerInput.trim().removeSuffix("/")
        if (raw.isBlank()) return emptyList()

        val bases = linkedSetOf<String>()
        fun addBase(scheme: String, host: String, port: Int? = null) {
            if (host.isBlank()) return
            val normalizedScheme = scheme.lowercase()
            val normalizedHost = host.trim().trim('/').removePrefix("//")
            if (normalizedHost.isBlank()) return
            val base = if (port != null && port > 0) {
                "$normalizedScheme://$normalizedHost:$port"
            } else {
                "$normalizedScheme://$normalizedHost"
            }
            bases += base
        }

        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            val parsed = Uri.parse(raw)
            val scheme = parsed.scheme ?: "http"
            val host = parsed.host.orEmpty()
            val explicitPort = parsed.port.takeIf { it > 0 }
            addBase(scheme, host, explicitPort)
            if (explicitPort == null) {
                addBase("http", host)
                addBase("http", host, 8080)
                addBase("https", host)
                addBase("https", host, 8443)
            }
        } else {
            val hostPort = raw.removePrefix("//")
            val portText = hostPort.substringAfter(':', "")
            val hasSingleColon = hostPort.count { it == ':' } == 1
            val hasExplicitPort = hasSingleColon && portText.all { it.isDigit() }
            if (hasExplicitPort) {
                val host = hostPort.substringBefore(':')
                val port = portText.toIntOrNull()
                addBase("http", host, port)
                addBase("https", host, port)
            } else {
                addBase("http", hostPort)
                addBase("http", hostPort, 8080)
                addBase("https", hostPort)
                addBase("https", hostPort, 8443)
            }
        }

        return bases.toList()
    }

    private fun buildRouterEndpoints(routerIp: String, paths: List<String>): List<String> {
        val endpoints = mutableListOf<String>()
        for (base in buildRouterBases(routerIp)) {
            for (path in paths) {
                endpoints += "$base$path"
            }
        }
        return endpoints.distinct()
    }

    private fun pairValidateWithFallback(routerIp: String, payload: JSONObject): Triple<String, Int, String> {
        val endpoints = buildRouterEndpoints(
            routerIp,
            listOf("/cgi-bin/router-pair-validate", "/router_agent/pair_validate")
        )

        if (endpoints.isEmpty()) {
            return Triple("", -1, "router_ip_invalid")
        }

        var lastEndpoint = endpoints.first()
        var lastResponse: Pair<Int, String> = -1 to "network_error"
        for (endpoint in endpoints) {
            val response = postPairValidate(endpoint, payload)
            lastEndpoint = endpoint
            lastResponse = response
            val code = response.first
            if (code in 200..299) {
                break
            }
            if (code in listOf(400, 401, 403, 409, 410, 415)) {
                break
            }
        }
        return Triple(lastEndpoint, lastResponse.first, lastResponse.second)
    }

    private fun setPairedProperty(value: Boolean) {
        val propValue = if (value) "1" else "0"
        rootCommandExecutor.runCapture(
            "resetprop persist.kitsunrouter.paired $propValue || setprop persist.kitsunrouter.paired $propValue"
        )
    }

    private fun pushModuleStatusToRouter(snapshot: ModuleSnapshot) {
        val state = routerPairing
        if (!state.paired) return
        val routerIp = state.routerIp.trim()
        val token = state.token.trim().lowercase()
        if (routerIp.isBlank() || !isValidRouterToken(token)) return

        val daemon = snapshot.daemonState
        val policyVersion = readPolicyVersionFromModule()
        val payload = JSONObject()
            .put("event", "MODULE_STATUS")
            .put("router_id", state.routerId)
            .put("version", policyVersion)
            .put("paired", state.paired)
            .put("profile_current", snapshot.policyCurrent)
            .put("profile_target", snapshot.policyTarget)
            .put("profile_request", snapshot.policyRequest)
            .put("transport", daemon["transport"].orEmpty())
            .put("iface", daemon["iface"].orEmpty())
            .put("wifi_state", daemon["wifi.state"].orEmpty())
            .put("wifi_score", daemon["wifi.score"].orEmpty())
            .put("mobile_score", daemon["mobile.score"].orEmpty())
            .put("bssid", daemon["wifi.bssid"].orEmpty())
            .put("band", daemon["wifi.band"].orEmpty())
            .put("width", daemon["wifi.width"].orEmpty())
            .put("last_event", snapshot.lastEvent.event)
            .put("last_event_ts", snapshot.lastEvent.ts)
            .put("app_ts", System.currentTimeMillis() / 1000)

        val payloadText = payload.toString()
        val payloadHash = payloadText.hashCode()
        val now = System.currentTimeMillis()
        val isSamePayload = payloadHash == lastRouterSyncPayloadHash
        if (isSamePayload && now - lastRouterSyncMs < ROUTER_STATUS_PUSH_INTERVAL_MS) {
            return
        }

        val endpoints = buildRouterEndpoints(
            routerIp,
            listOf("/cgi-bin/router-event", "/router_agent/router-event")
        )
        if (endpoints.isEmpty()) return

        for (endpoint in endpoints) {
            val statusSeq = nextRouterStatusSeq()
            payload.put("seq", statusSeq)
            val payloadTextWithSeq = payload.toString()
            val response = runCatching {
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 2500
                    readTimeout = 2500
                    doOutput = true
                    setRequestProperty("X-Auth-Token", token)
                    setRequestProperty("Content-Type", "application/json")
                }
                conn.outputStream.use { it.write(payloadTextWithSeq.toByteArray()) }
                conn.responseCode
            }.getOrElse { -1 }

            if (response in 200..299) {
                lastRouterSyncMs = now
                lastRouterSyncPayloadHash = payloadHash
                return
            }

            if (response in listOf(400, 401, 403, 415)) {
                return
            }
        }
    }

    private fun pairRouter(routerIp: String, qrOrCode: String) {
        val ip = routerIp.trim().ifBlank { extractRouterIpFromPairUri(qrOrCode) }
        val pairCode = extractPairCode(qrOrCode)
        val routerIdFromQr = extractRouterIdFromPairUri(qrOrCode)
        if (ip.isBlank()) {
            runOnUiThread { Toast.makeText(this, "router_ip is required", Toast.LENGTH_LONG).show() }
            return
        }
        if (pairCode.isBlank()) {
            runOnUiThread { Toast.makeText(this, "codigo/QR invalido", Toast.LENGTH_LONG).show() }
            return
        }

        Thread {
            val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                ?: Build.MODEL ?: "android"
            val payload = JSONObject()
                .put("pair_code", pairCode)
                .put("device_id", deviceId)

            val response = pairValidateWithFallback(ip, payload)

            runOnUiThread {
                val (endpointUsed, code, body) = response
                if (code !in 200..299) {
                    val detail = body.trim()
                    val errorMsg = if (code == -1 && detail.contains("cleartext", ignoreCase = true)) {
                        "pair failed: Android blocked cleartext HTTP. Reinstall the app with cleartext enabled."
                    } else {
                        "pair failed ($code) [$endpointUsed]: ${detail.take(160)}"
                    }
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }

                val json = runCatching { JSONObject(body) }.getOrNull()
                val status = json?.optString("status").orEmpty()
                if (status == "PAIR_OK") {
                    val token = json?.optString("token").orEmpty().trim().lowercase()
                    if (!isValidRouterToken(token)) {
                        Toast.makeText(this, "pair failed: invalid token received", Toast.LENGTH_LONG).show()
                        return@runOnUiThread
                    }
                    val routerId = json?.optString("router_id").orEmpty().ifBlank { routerIdFromQr }
                    saveRouterPairingState(
                        RouterPairingState(
                            routerIp = ip,
                            token = token,
                            routerId = routerId,
                            paired = true
                        )
                    )
                    Thread {
                        clearRouterCacheArtifacts()
                        setPairedProperty(true)
                        refreshRouterFiles()
                    }.start()
                    Toast.makeText(this, "PAIR_OK: paired", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "pair invalido: ${status.ifBlank { body }}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun unpairRouter() {
        saveRouterPairingState(
            RouterPairingState(
                routerIp = routerPairing.routerIp,
                token = "",
                routerId = "",
                paired = false
            )
        )
        Thread {
            clearRouterCacheArtifacts()
            setPairedProperty(false)
            refreshRouterFiles()
        }.start()
        Toast.makeText(this, "Router unpaired", Toast.LENGTH_SHORT).show()
    }

    private fun openPairingDialog() {
        val ipInput = EditText(this).apply {
            hint = "Router IP (ej: 192.168.1.1)"
            inputType = InputType.TYPE_CLASS_TEXT
            setText(routerPairing.routerIp)
        }
        val qrInput = EditText(this).apply {
            hint = "QR URI o pair_code"
            inputType = InputType.TYPE_CLASS_TEXT
            setText(scannedPairPayload)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 0)
            addView(ipInput)
            addView(qrInput)
        }

        val statusText = if (routerPairing.paired) {
            "Current state: paired (${routerPairing.routerIp})"
        } else {
            "Current state: unpaired"
        }

        AlertDialog.Builder(this)
            .setTitle("KitsunRouter Pairing")
            .setMessage(statusText)
            .setView(container)
            .setPositiveButton("Pair") { _, _ ->
                pairRouter(ipInput.text?.toString().orEmpty(), qrInput.text?.toString().orEmpty())
            }
            .setNeutralButton("Unpair") { _, _ ->
                unpairRouter()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateThemeMode(mode: AppThemeMode) {
        appThemeMode = mode
        uiSettingsStore.saveThemeMode(mode)
    }

    private fun updateDeveloperMode(enabled: Boolean) {
        developerMode = enabled
        uiSettingsStore.saveDeveloperMode(enabled)
    }

    private fun launchQrScanner() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        ) {
            requestCameraPermissionIfNeeded()
            return
        }
        openQrScannerInternal()
    }

    private fun openQrScannerInternal() {
        val intent = Intent(this, QrScanActivity::class.java)
        runCatching {
            qrScanLauncher.launch(intent)
        }.onFailure {
            Toast.makeText(this, "Could not open QR scanner", Toast.LENGTH_LONG).show()
        }
    }

    private fun openAppSettings() {
        runCatching {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        }.onFailure {
            Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 10000L
        private const val HIGHLIGHT_DURATION_MS = 700L
        private const val ROUTER_STATUS_PUSH_INTERVAL_MS = 15000L
        private const val POLICY_SYNC_DEBOUNCE_MS = 750L
        private const val MODULE_ROOT = "/data/adb/modules/Kitsunping"
        private const val MODULE_CACHE_DIR = "$MODULE_ROOT/cache"
        private const val MODULE_TARGET_PROP_PATH = "$MODULE_ROOT/target.prop"
        private const val MODULE_TARGET_MODEL_JSON_PATH = "$MODULE_CACHE_DIR/target.model.json"
        private const val MODULE_POLICY_VERSION_PATH = "$MODULE_CACHE_DIR/policy.version"
        private const val ROUTER_PAIRING_CACHE_PATH = "$MODULE_CACHE_DIR/router.pairing.json"
        private const val PREFS_ROUTER = "kitsunrouter_pairing"
        private const val KEY_ROUTER_IP = "router_ip"
        private const val KEY_ROUTER_TOKEN = "router_token"
        private const val KEY_ROUTER_ID = "router_id"
        private const val KEY_ROUTER_PAIRED = "router_paired"
        private const val KEY_ROUTER_STATUS_SEQ = "router_status_seq"
    }
}

private sealed class SyncResult {
    data class Success(
        val runtimeModel: Map<String, TargetPolicyRule>,
        val summary: List<String>
    ) : SyncResult()

    data class RouterFailed(
        val runtimeModel: Map<String, TargetPolicyRule>,
        val summary: List<String>
    ) : SyncResult()

    data class RuntimeFailed(val reason: String) : SyncResult()
}

private data class RouterPairingState(
    val routerIp: String = "",
    val token: String = "",
    val routerId: String = "",
    val paired: Boolean = false
)

private data class RouterSignatureUi(
    val bssid: String = "",
    val band: String = "",
    val channel: String = "",
    val width: String = ""
)
