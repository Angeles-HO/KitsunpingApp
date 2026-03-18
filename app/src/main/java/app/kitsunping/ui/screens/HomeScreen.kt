package app.kitsunping.ui.screens

import android.R as AndroidR
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kitsunping.ModuleSnapshot
import app.kitsunping.ui.animation.AppMotion
import app.kitsunping.ui.components.CardHeaderWithInfo
import app.kitsunping.ui.screens.panels.HomeActiveTransportPanel
import app.kitsunping.ui.screens.panels.HomeConflictPanel
import app.kitsunping.ui.screens.panels.HomeDaemonStatusPanel
import app.kitsunping.ui.screens.panels.HomeQualityProfilePanel
import app.kitsunping.ui.screens.panels.HomeQuickActionsPanel
import app.kitsunping.ui.utils.formatEpoch
import java.util.Locale

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    snapshot: ModuleSnapshot = ModuleSnapshot.empty(),
    highlightPulse: Boolean = false,
    reducedMotionEnabled: Boolean = false,
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
    val conflictStatus = snapshot.conflictStatus

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HomeDaemonStatusPanel(
            highlightPulse = highlightPulse,
            reducedMotionEnabled = reducedMotionEnabled,
            lowNetworkSimulationEnabled = lowNetworkSimulationEnabled,
            lowNetDivisor = lowNetDivisor,
            daemonStateLabel = daemonRuntime.state,
            daemonPid = daemonRuntime.pid,
            policyEventName = policy.event,
            daemonUpdated = daemonUpdated,
            calibrateState = calibrateState,
            daemonCheckedAt = daemonCheckedAt,
            onRequestStart = onRequestStart,
            onRequestRestart = onRequestRestart
        )

        HomeQualityProfilePanel(
            transport = transport,
            profileCurrent = profileCurrent,
            profileTarget = profileTarget,
            gaugeContent = {
                AdaptiveQualityGauge(
                    transport = transport,
                    wifiScore = wifiScoreUi,
                    compositeScore = compositeScoreUi,
                    compact = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )

        HomeConflictPanel(conflictStatus = conflictStatus)

        AnimatedVisibility(
            visible = transport == "wifi",
            enter = fadeIn(AppMotion.visibilityFadeInSpec(reducedMotionEnabled)) +
                expandVertically(
                    animationSpec = AppMotion.visibilityExpandSpec(reducedMotionEnabled),
                    expandFrom = Alignment.Top
                ),
            exit = fadeOut(AppMotion.visibilityFadeOutSpec(reducedMotionEnabled)) +
                shrinkVertically(
                    animationSpec = AppMotion.visibilityShrinkSpec(reducedMotionEnabled),
                    shrinkTowards = Alignment.Top
                )
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
            enter = fadeIn(AppMotion.visibilityFadeInSpec(reducedMotionEnabled)) +
                expandVertically(
                    animationSpec = AppMotion.visibilityExpandSpec(reducedMotionEnabled),
                    expandFrom = Alignment.Top
                ),
            exit = fadeOut(AppMotion.visibilityFadeOutSpec(reducedMotionEnabled)) +
                shrinkVertically(
                    animationSpec = AppMotion.visibilityShrinkSpec(reducedMotionEnabled),
                    shrinkTowards = Alignment.Top
                )
        ) {
            HomeActiveTransportPanel(transport = transport)
        }

        HomeQuickActionsPanel(
            onRequestCalibrate = onRequestCalibrate,
            onRequestProfile = onRequestProfile
        )
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
