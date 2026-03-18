package app.kitsunping.ui.screens.router

import android.R as AndroidR
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONObject

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

    // Extract score from candidates array if available.
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

    // RF model may come under different keys depending on backend version.
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

@Composable
fun ChannelAnalysisDialog(
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

            // Poll every 2 seconds for up to 2 minutes.
            var attempts = 0
            while (isOpen && attempts < 60 && recommendation == null) {
                delay(2000)

                val cacheContent = onReadCache()
                if (!cacheContent.isNullOrBlank()) {
                    try {
                        val parsed = parseChannelRecommendation(cacheContent)
                        if (parsed != null) {
                            recommendation = parsed
                            isLoading = false
                        }
                    } catch (_: Exception) {
                        // Keep polling until timeout.
                    }
                }
                attempts++
            }

            if (recommendation == null && attempts >= 60) {
                errorMessage = "Tiempo de espera agotado. Intenta de nuevo."
                isLoading = false
            }
        } else {
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
            // Router applies and reloads WiFi; wait before reading status.
            delay(12000)
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
                            modifier = Modifier
                                .padding(12.dp),
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
                        fontWeight = FontWeight.Bold
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
