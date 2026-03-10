package app.kitsunping.feature.speedtest

enum class SpeedTestPhase {
    IDLE,
    SELECTING_SERVER,
    MEASURING_LATENCY,
    MEASURING_DOWNLOAD,
    MEASURING_UPLOAD,
    COMPLETED,
    FAILED
}

data class SpeedTestUiState(
    val isRunning: Boolean = false,
    val phase: SpeedTestPhase = SpeedTestPhase.IDLE,
    val statusMessage: String = "Ready to measure",
    val parallelStreams: Int = 3,
    val testDurationSec: Int = 10,
    val serverLabel: String = "",
    val serverId: String = "",
    val serverHost: String = "",
    val serverCountry: String = "",
    val countryHint: String = "",
    val operatorHint: String = "",
    val networkTypeLabel: String = "",
    val selectionSource: String = "",
    val pingMs: Double? = null,
    val jitterMs: Double? = null,
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
    val errorMessage: String? = null,
    val lastUpdatedAtMs: Long? = null
)

data class SpeedTestRunConfig(
    val parallelStreams: Int = 3,
    val testDurationSec: Int = 10
)

internal data class SpeedTestServer(
    val id: String,
    val label: String,
    val sponsor: String,
    val name: String,
    val country: String,
    val cc: String,
    val host: String,
    val port: Int,
    val distanceKm: Double?,
    val latencyUrl: String,
    val downloadUrl: String,
    val uploadUrl: String
)

internal enum class SpeedTestSelectionSource {
    CACHE,
    DISCOVERY,
    FALLBACK_PUBLIC
}

internal data class SpeedTestSelectionContext(
    val simCountry: String,
    val simCountryName: String,
    val operatorName: String,
    val networkType: String,
    val wifiId: String
)

internal data class SpeedTestProbeMetrics(
    val medianMs: Double,
    val jitterMs: Double,
    val failCount: Int,
    val failRatePercent: Double,
    val sampleCount: Int
)

internal data class SpeedTestSelectionResult(
    val server: SpeedTestServer,
    val source: SpeedTestSelectionSource,
    val pingMs: Double,
    val jitterMs: Double
)

internal object SpeedTestServerCatalog {
    fun fallbackPublic(): SpeedTestServer {
        return SpeedTestServer(
            id = "fallback-cloudflare",
            label = "Cloudflare Public Edge",
            sponsor = "Cloudflare",
            name = "Public Edge",
            country = "",
            cc = "",
            host = "speed.cloudflare.com",
            port = 443,
            distanceKm = null,
            latencyUrl = "https://speed.cloudflare.com/cdn-cgi/trace",
            downloadUrl = "https://speed.cloudflare.com/__down?bytes=25000000",
            uploadUrl = "https://speed.cloudflare.com/__up"
        )
    }
}