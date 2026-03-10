package app.kitsunping.feature.speedtest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kitsunping.ui.components.CardHeaderWithInfo
import app.kitsunping.ui.utils.formatEpoch
import java.util.Locale

@Composable
fun SpeedTestScreen(
    contentPadding: PaddingValues,
    state: SpeedTestUiState,
    onRequestRunSpeedTest: (SpeedTestRunConfig) -> Unit
) {
    var showConfigDialog by rememberSaveable { mutableStateOf(false) }
    var selectedParallelStreams by rememberSaveable { mutableStateOf(state.parallelStreams.coerceIn(1, 6)) }
    var selectedDurationSec by rememberSaveable { mutableStateOf(state.testDurationSec.coerceIn(5, 20)) }

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
                    title = "Speed test",
                    infoText = "Feature decoupled from the root module. It uses public transport from the app and should not be interpreted as a direct proof of module impact."
                )
                Text(
                    text = state.statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (state.isRunning) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator()
                            Text(text = phaseLabel(state.phase), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Button(
                    onClick = { showConfigDialog = true },
                    enabled = !state.isRunning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.isRunning) "Measuring..." else "Executing speed test")
                }
                Text(
                    text = "Current config: ${state.parallelStreams} parallel streams · ${state.testDurationSec}s",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CardHeaderWithInfo(
                    title = "Server",
                    infoText = "The app chooses a server in rounds with local cache and final validation. This remains decoupled from the root module."
                )
                MetricRow(label = "Provider", value = state.serverLabel.ifBlank { "Pending" })
                MetricRow(label = "ID", value = state.serverId.ifBlank { "Pending" })
                MetricRow(label = "Host", value = state.serverHost.ifBlank { "Pending" })
                MetricRow(label = "Server country", value = state.serverCountry.ifBlank { "no data" })
                MetricRow(label = "SIM country", value = state.countryHint.ifBlank { "no hint" })
                MetricRow(label = "Carrier", value = state.operatorHint.ifBlank { "no data" })
                MetricRow(label = "Network", value = state.networkTypeLabel.ifBlank { "no data" })
                MetricRow(label = "Source", value = selectionSourceLabel(state.selectionSource))
                MetricRow(label = "Parallel streams", value = state.parallelStreams.toString())
                MetricRow(label = "Duration", value = "${state.testDurationSec}s")
                MetricRow(
                    label = "Last run",
                    value = state.lastUpdatedAtMs?.let { formatEpoch(it / 1000) } ?: "no data"
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CardHeaderWithInfo(
                    title = "Results",
                    infoText = "The app shows the four metrics users usually expect: ping, jitter, download, and upload. These are transport metrics observed by the app, not a direct verdict of module impact."
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = "Reference: ping comes from TCP validation against the selected server. Jitter reflects variation between consecutive samples from the same server.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                MetricsStrip(
                    pingMs = state.pingMs,
                    jitterMs = state.jitterMs,
                    downloadMbps = state.downloadMbps,
                    uploadMbps = state.uploadMbps
                )
                state.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }

    if (showConfigDialog) {
        SpeedTestConfigDialog(
            selectedParallelStreams = selectedParallelStreams,
            selectedDurationSec = selectedDurationSec,
            onDismiss = { showConfigDialog = false },
            onConfirm = { parallelStreams, durationSec ->
                selectedParallelStreams = parallelStreams
                selectedDurationSec = durationSec
                showConfigDialog = false
                onRequestRunSpeedTest(SpeedTestRunConfig(parallelStreams, durationSec))
            }
        )
    }
}

@Composable
private fun SpeedTestConfigDialog(
    selectedParallelStreams: Int,
    selectedDurationSec: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var parallelStreams by rememberSaveable { mutableStateOf(selectedParallelStreams) }
    var durationSec by rememberSaveable { mutableStateOf(selectedDurationSec) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onConfirm(parallelStreams, durationSec) }) {
                Text("Iniciar")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = { Text("Configurar speed test") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Parallel streams")
                    OptionRow(
                        options = listOf(1, 2, 3, 4, 5, 6),
                        selected = parallelStreams,
                        label = { it.toString() },
                        onSelected = { parallelStreams = it }
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Final test duration")
                    OptionRow(
                        options = listOf(5, 10, 15, 20),
                        selected = durationSec,
                        label = { "${it}s" },
                        onSelected = { durationSec = it }
                    )
                }
            }
        }
    )
}

@Composable
private fun <T> OptionRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(3).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowOptions.forEach { option ->
                    val buttonModifier = Modifier.weight(1f)
                    if (option == selected) {
                        Button(onClick = { onSelected(option) }, modifier = buttonModifier) {
                            Text(label(option))
                        }
                    } else {
                        OutlinedButton(onClick = { onSelected(option) }, modifier = buttonModifier) {
                            Text(label(option))
                        }
                    }
                }
                repeat(3 - rowOptions.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MetricsStrip(
    pingMs: Double?,
    jitterMs: Double?,
    downloadMbps: Double?,
    uploadMbps: Double?
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                label = "Ping",
                value = pingMs?.let { formatMs(it) } ?: "-",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "Jitter",
                value = jitterMs?.let { formatMs(it) } ?: "-",
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(label = "Down", value = downloadMbps?.let { formatMbps(it) } ?: "-", modifier = Modifier.weight(1f))
            MetricCard(label = "Up", value = uploadMbps?.let { formatMbps(it) } ?: "-", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, supporting: String? = null, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Box(modifier = Modifier.padding(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = label, style = MaterialTheme.typography.labelMedium)
                Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                supporting?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

private fun phaseLabel(phase: SpeedTestPhase): String {
    return when (phase) {
        SpeedTestPhase.IDLE -> "Listo"
        SpeedTestPhase.SELECTING_SERVER -> "Selecting server"
        SpeedTestPhase.MEASURING_LATENCY -> "Midiendo latencia"
        SpeedTestPhase.MEASURING_DOWNLOAD -> "Midiendo descarga"
        SpeedTestPhase.MEASURING_UPLOAD -> "Midiendo subida"
        SpeedTestPhase.COMPLETED -> "Completado"
        SpeedTestPhase.FAILED -> "With error"
    }
}

private fun formatMs(value: Double): String {
    return String.format(Locale.US, "%.0f ms", value)
}

private fun formatMbps(value: Double): String {
    return String.format(Locale.US, "%.2f Mbps", value)
}

private fun selectionSourceLabel(source: String): String {
    return when (source.lowercase(Locale.US)) {
        "cache" -> "cache"
        "discovery" -> "discovery"
        "fallback_public" -> "fallback publico"
        else -> source.ifBlank { "no data" }
    }
}