package app.kitsunping.ui.utils

import androidx.compose.ui.graphics.Color
import app.kitsunping.ui.theme.AppThemeMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Map<String, String>.valueOrNaText(key: String): String {
    val raw = this[key].orEmpty().trim()
    if (raw.isBlank() || raw == "-") return "N/A"
    return raw
}

fun Map<String, String>.formatDaemonValue(key: String): String {
    val raw = this[key].orEmpty().trim()
    if (raw.isBlank() || raw == "-") return "N/A"

    val number = raw.toDoubleOrNull()
    val formatted = if (number != null) formatDecimal(number, 0) else raw
    return when (key) {
        "wifi.rssi_dbm", "rsrp_dbm" -> "${formatted} dBm"
        "sinr_db" -> "${formatted} dB"
        else -> formatted
    }
}

fun Map<String, String>.isWifiActive(): Boolean {
    val state = this["wifi.state"].orEmpty().trim().lowercase(Locale.ROOT)
    val linkUp = this["wifi.link"].orEmpty().trim().equals("UP", ignoreCase = true)
    val hasIp = this["wifi.ip"].orEmpty().trim() == "1"
    val iface = this["wifi.iface"].orEmpty().trim().lowercase(Locale.ROOT)
    val looksWifiIface = iface.isNotBlank() && iface != "none" &&
        (iface.startsWith("wl") || iface.contains("wifi"))
    return state == "connected" || (linkUp && hasIp) || (looksWifiIface && (linkUp || hasIp))
}

fun Map<String, String>.isMobileActive(): Boolean {
    val linkUp = this["mobile.link"].orEmpty().trim().equals("UP", ignoreCase = true)
    val hasIp = this["mobile.ip"].orEmpty().trim() == "1"
    val iface = this["mobile.iface"].orEmpty().trim().lowercase(Locale.ROOT)
    val looksMobileIface = iface.isNotBlank() && iface != "none" && (
        iface.startsWith("rmnet") ||
            iface.startsWith("ccmni") ||
            iface.startsWith("pdp") ||
            iface.startsWith("wwan") ||
            iface.startsWith("usb")
        )
    return (linkUp && hasIp) || (looksMobileIface && (linkUp || hasIp))
}

fun qualityColor(value: String): Color {
    return when (value.lowercase(Locale.ROOT)) {
        "good", "excelente", "excellent" -> Color(0xFF00FF41)
        "limbo", "fair", "regular" -> Color(0xFFF4B860)
        "bad", "malo", "poor" -> Color(0xFFF87171)
        else -> Color.Unspecified
    }
}

fun AppThemeMode.displayName(): String {
    return when (this) {
        AppThemeMode.SYSTEM -> "System"
        AppThemeMode.LIGHT -> "OpenWrt Modern"
        AppThemeMode.DARK -> "Deep Space"
        AppThemeMode.AMOLED -> "Cyber-Stealth"
        AppThemeMode.TERMINAL -> "Terminal Green"
        AppThemeMode.CRIMSON -> "Crimson Night"
        AppThemeMode.SOLARIZED -> "Solarized Dark"
        AppThemeMode.ARCTIC -> "Arctic Light"
        AppThemeMode.ROSE -> "Rose Bloom"
        AppThemeMode.MODERN -> "Modern Outline"
        AppThemeMode.MODERN_INVERTED -> "Modern Inverted"
        AppThemeMode.FOREST -> "Forest Night"
        AppThemeMode.OCEAN -> "Ocean Deep"
        AppThemeMode.SUNSET -> "Sunset Glow"
        AppThemeMode.MONOCHROME -> "Monochrome"
        AppThemeMode.PASTEL -> "Pastel Soft"
        AppThemeMode.DRACULA -> "Dracula"
        AppThemeMode.NORD -> "Nord"
        AppThemeMode.MONO_BLUEPRINT -> "Mono Blueprint"
        AppThemeMode.KITSUNPING -> "Kitsunping"
    }
}

fun formatEpoch(epochSeconds: Long): String {
    if (epochSeconds <= 0) return "no data"
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(epochSeconds * 1000))
}

private fun formatDecimal(value: Double, decimals: Int): String {
    return String.format(Locale.US, "%.${decimals}f", value)
}
