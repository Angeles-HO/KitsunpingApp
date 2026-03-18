package app.kitsunping.ui.screens.panels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.kitsunping.ConflictStatus
import app.kitsunping.ui.animation.AppMotion
import app.kitsunping.ui.components.CardHeaderWithInfo
import app.kitsunping.ui.components.HighlightCard
import java.util.Locale

@Composable
fun HomeDaemonStatusPanel(
    highlightPulse: Boolean,
    reducedMotionEnabled: Boolean,
    lowNetworkSimulationEnabled: Boolean,
    lowNetDivisor: Int,
    daemonStateLabel: String,
    daemonPid: String,
    policyEventName: String,
    daemonUpdated: String,
    calibrateState: String,
    daemonCheckedAt: String,
    onRequestStart: () -> Unit,
    onRequestRestart: () -> Unit
) {
    HighlightCard(modifier = Modifier.fillMaxWidth(), highlight = highlightPulse) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(
                visible = lowNetworkSimulationEnabled,
                enter = expandVertically(AppMotion.visibilityExpandSpec(reducedMotionEnabled)) +
                    fadeIn(AppMotion.visibilityFadeInSpec(reducedMotionEnabled)),
                exit = shrinkVertically(AppMotion.visibilityShrinkSpec(reducedMotionEnabled)) +
                    fadeOut(AppMotion.visibilityFadeOutSpec(reducedMotionEnabled))
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
            Text(text = daemonStateLabel, style = MaterialTheme.typography.bodyMedium)
            if (daemonPid.isNotBlank()) {
                Text(text = "PID: $daemonPid", style = MaterialTheme.typography.bodySmall)
            }
            Text(text = "Last event: $policyEventName", style = MaterialTheme.typography.bodySmall)
            Text(text = "Last update: $daemonUpdated", style = MaterialTheme.typography.bodySmall)
            Text(text = "Calibration status: $calibrateState", style = MaterialTheme.typography.bodySmall)
            Text(text = "Process verification: $daemonCheckedAt", style = MaterialTheme.typography.bodySmall)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRequestStart, modifier = Modifier.weight(1f)) { Text("Start") }
                Button(onClick = onRequestRestart, modifier = Modifier.weight(1f)) { Text("Restart") }
            }
        }
    }
}

@Composable
fun HomeQualityProfilePanel(
    transport: String,
    profileCurrent: String,
    profileTarget: String,
    gaugeContent: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CardHeaderWithInfo(
                title = "Quality and connection states",
                infoText = "Network mode, current score, applied profile, and next target profile."
            )
            gaugeContent()
            Text("Transport: ${transport.ifBlank { "unknown" }}", style = MaterialTheme.typography.bodySmall)
            Text("Applied profile: $profileCurrent", style = MaterialTheme.typography.bodySmall)
            Text("Next profile (target): $profileTarget", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun HomeConflictPanel(conflictStatus: ConflictStatus) {
    val highestConflictRisk = conflictStatus.highestRisk.lowercase()
    val conflictRiskColor = riskColor(highestConflictRisk)
    val conflictRiskLabel = when (highestConflictRisk) {
        "high" -> "HIGH"
        "medium" -> "MEDIUM"
        "low" -> "LOW"
        else -> "NONE"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, conflictRiskColor.copy(alpha = 0.4f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CardHeaderWithInfo(
                title = "Module conflicts",
                infoText = "Shows active modules that may overlap with Kitsunping network control."
            )
            Text(
                text = "Highest risk: $conflictRiskLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = conflictRiskColor
            )
            Text(
                text = "Scanned: ${conflictStatus.modulesScanned} | high: ${conflictStatus.highModules} | medium: ${conflictStatus.mediumModules} | low: ${conflictStatus.lowModules}",
                style = MaterialTheme.typography.bodySmall
            )

            if (conflictStatus.topModules.isEmpty()) {
                Text(
                    text = "No conflicting module entries found.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                conflictStatus.topModules.forEach { hit ->
                    val moduleRiskLabel = hit.risk.ifBlank { "low" }.uppercase(Locale.ROOT)
                    Text(
                        text = "${hit.module}: $moduleRiskLabel (high=${hit.highHits}, medium=${hit.mediumHits})",
                        style = MaterialTheme.typography.bodySmall,
                        color = riskColor(hit.risk)
                    )
                }
            }
        }
    }
}

@Composable
fun HomeActiveTransportPanel(transport: String) {
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

@Composable
fun HomeQuickActionsPanel(
    onRequestCalibrate: () -> Unit,
    onRequestProfile: (String) -> Unit
) {
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
                    Button(onClick = onRequestCalibrate, modifier = Modifier.fillMaxWidth()) {
                        Text(text = "Calibrate now")
                    }
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

@Composable
private fun riskColor(risk: String): Color {
    return when (risk.lowercase()) {
        "high" -> MaterialTheme.colorScheme.error
        "medium" -> MaterialTheme.colorScheme.tertiary
        "low" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
