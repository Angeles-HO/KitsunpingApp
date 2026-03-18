package app.kitsunping.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.kitsunping.ui.screens.panels.SettingsDeveloperPanel
import app.kitsunping.ui.screens.panels.SettingsModulePanel
import app.kitsunping.ui.screens.panels.SettingsPermissionsPanel
import app.kitsunping.ui.screens.panels.SettingsThemePanel
import app.kitsunping.ui.theme.AppThemeMode

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    currentThemeMode: AppThemeMode,
    reducedMotionEnabled: Boolean,
    developerMode: Boolean,
    lowNetworkSimulationEnabled: Boolean,
    ipv6CalibrationEnabled: Boolean,
    granularLatencyEnabled: Boolean,
    lowNetworkTestOffset: Int,
    bootCustomProfile: String,
    routerFiles: List<String>,
    onRequestThemeMode: (AppThemeMode) -> Unit,
    onRequestReducedMotion: (Boolean) -> Unit,
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
    onRequestRunCommand: (String, String) -> Unit,
    onRequestModuleIntegrityCheck: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsThemePanel(
            currentThemeMode = currentThemeMode,
            reducedMotionEnabled = reducedMotionEnabled,
            onRequestThemeMode = onRequestThemeMode,
            onRequestReducedMotion = onRequestReducedMotion
        )

        SettingsModulePanel(
            bootCustomProfile = bootCustomProfile,
            ipv6CalibrationEnabled = ipv6CalibrationEnabled,
            granularLatencyEnabled = granularLatencyEnabled,
            onRequestBootCustomProfile = onRequestBootCustomProfile,
            onRequestIpv6Calibration = onRequestIpv6Calibration,
            onRequestGranularLatency = onRequestGranularLatency
        )

        SettingsDeveloperPanel(
            developerMode = developerMode,
            reducedMotionEnabled = reducedMotionEnabled,
            lowNetworkSimulationEnabled = lowNetworkSimulationEnabled,
            lowNetworkTestOffset = lowNetworkTestOffset,
            routerFiles = routerFiles,
            onRequestDeveloperMode = onRequestDeveloperMode,
            onRequestLowNetworkSimulation = onRequestLowNetworkSimulation,
            onRequestLowNetworkTestOffset = onRequestLowNetworkTestOffset,
            onRequestCheckDaemonPid = onRequestCheckDaemonPid,
            onRequestRefreshRouterFiles = onRequestRefreshRouterFiles,
            onRequestReadFile = onRequestReadFile,
            onRequestViewRouterLast = onRequestViewRouterLast,
            onRequestOpenRouterLast = onRequestOpenRouterLast,
            onRequestRunCommand = onRequestRunCommand,
            onRequestModuleIntegrityCheck = onRequestModuleIntegrityCheck
        )

        SettingsPermissionsPanel(onRequestOpenAppSettings = onRequestOpenAppSettings)
    }
}
