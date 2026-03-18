package app.kitsunping.ui.screens.router

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.kitsunping.LastEvent
import app.kitsunping.ui.screens.panels.RouterChannelAnalysisPanel
import app.kitsunping.ui.screens.panels.RouterNetworkSignaturePanel
import app.kitsunping.ui.screens.panels.RouterPairingPanel
import app.kitsunping.ui.screens.panels.RouterStatusPanel
import app.kitsunping.ui.screens.parseDetails

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
    val bssid = daemonState["wifi.bssid"].orEmpty()
        .ifBlank { signatureBssid }
        .ifBlank { detailsMap["bssid"].orEmpty() }
    val band = daemonState["wifi.band"].orEmpty()
        .ifBlank { signatureBand }
        .ifBlank { detailsMap["band"].orEmpty() }
    val channel = daemonState["wifi.chan"].orEmpty()
        .ifBlank { signatureChannel }
        .ifBlank { detailsMap["chan"].orEmpty() }
    val rawWidth = daemonState["wifi.width"].orEmpty()
        .ifBlank { signatureWidth }
        .ifBlank { detailsMap["width"].orEmpty() }
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
        RouterStatusPanel(
            paired = paired,
            hideIp = hideIp,
            routerIp = routerIp,
            routerId = routerId,
            hasToken = hasToken,
            policyEventName = policyEventName,
            onToggleHideIp = onToggleHideIp
        )

        RouterPairingPanel(
            ipInput = ipInput,
            onIpInputChange = { ipInput = it },
            showIpInput = showIpInput,
            onToggleShowIpInput = { showIpInput = !showIpInput },
            suggestedRouterIp = suggestedRouterIp,
            onAutoDetect = {
                if (suggestedRouterIp.isNotBlank()) {
                    ipInput = suggestedRouterIp
                    showIpInput = false
                }
            },
            codeInput = codeInput,
            onCodeInputChange = { codeInput = it },
            showCodeInput = showCodeInput,
            onToggleShowCodeInput = { showCodeInput = !showCodeInput },
            onRequestScanQr = onRequestScanQr,
            onRequestUnpairRouter = onRequestUnpairRouter,
            onRequestPairRouter = { onRequestPairRouter(ipInput.ifBlank { routerIp }, codeInput) }
        )

        RouterNetworkSignaturePanel(
            hideIp = hideIp,
            bssid = bssid,
            band = band,
            channel = channel,
            width = width,
            widthIsInferred = widthIsInferred
        )

        RouterChannelAnalysisPanel(
            paired = paired,
            onRequestChannelScan = {
                showChannelDialog = true
                onRequestChannelScan()
            }
        )
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
