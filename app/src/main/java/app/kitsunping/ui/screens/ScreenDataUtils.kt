package app.kitsunping.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import java.util.Locale
import kotlin.math.abs

data class EmaPoint(
    val tsMs: Long,
    val value: Float
)

data class LatencyUi(
    val label: String,
    val source: String
)

@Composable
fun rememberLatencyUi(rawLatency: String?, rawLatencyEma: String?): LatencyUi {
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
fun rememberEmaHistory(
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
        if (last != null && abs(last.value - normalized) < 0.0001f) {
            return@LaunchedEffect
        }

        history.add(EmaPoint(tsMs = now, value = normalized))
        while (history.size > maxPoints) {
            history.removeAt(0)
        }
    }

    return history
}

fun parseDetails(details: String): Map<String, String> {
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

fun extractCapabilities(details: String): Set<String> {
    val capsValue = parseDetails(details)["caps"].orEmpty().lowercase()
    if (capsValue.isBlank()) return emptySet()
    return capsValue
        .split(',', '|', ';')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()
}
