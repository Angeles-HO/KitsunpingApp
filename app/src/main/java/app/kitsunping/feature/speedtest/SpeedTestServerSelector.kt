package app.kitsunping.feature.speedtest

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.Locale
import kotlin.math.abs

internal class SpeedTestServerSelector(
    private val context: Context,
    private val openConnection: (String, String) -> HttpURLConnection,
    private val sleep: (Long) -> Unit,
    private val elapsedRealtimeMs: () -> Long
) {
    private val cache = SpeedTestServerCache(context)

    fun buildSelectionContext(): SpeedTestSelectionContext {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
        val simCountry = telephony?.simCountryIso.orEmpty().trim().lowercase(Locale.US)
        val simCountryName = if (simCountry.length == 2) {
            Locale("", simCountry.uppercase(Locale.US)).displayCountry.orEmpty()
        } else {
            ""
        }
        val operatorName = sequenceOf(
            telephony?.simOperatorName,
            telephony?.networkOperatorName
        ).map { it.orEmpty().trim() }.firstOrNull { it.isNotBlank() }.orEmpty()

        return SpeedTestSelectionContext(
            simCountry = simCountry,
            simCountryName = simCountryName,
            operatorName = operatorName,
            networkType = resolveNetworkType(),
            wifiId = resolveWifiId()
        )
    }

    fun selectBestServer(
        selectionContext: SpeedTestSelectionContext,
        onProgress: (String) -> Unit
    ): SpeedTestSelectionResult {
        cache.load(selectionContext)?.let { cached ->
            onProgress("Validating cached server")
            val validation = validateServer(cached)
            if (passesFinalThresholds(validation) && hasUsableHttps(cached)) {
                return SpeedTestSelectionResult(
                    server = cached,
                    source = SpeedTestSelectionSource.CACHE,
                    pingMs = validation.medianMs,
                    jitterMs = validation.jitterMs
                )
            }
        }

        onProgress("Discovering nearby servers")
        val discoveredServers = discoverServers(selectionContext)
        if (discoveredServers.isEmpty()) {
            return fallbackSelection(onProgress)
        }

        onProgress("Ronda 1: descarte rapido")
        val round1 = discoveredServers.mapNotNull { server ->
            val metrics = probeServer(server, ROUND1_SAMPLE_COUNT)
            if (passesRound1Thresholds(metrics)) {
                ScoredCandidate(server, metrics, computeRoundScore(server, metrics, selectionContext))
            } else {
                null
            }
        }.sortedBy { it.score }.take(ROUND1_KEEP_COUNT)

        if (round1.isEmpty()) {
            return fallbackSelection(onProgress)
        }

        onProgress("Ronda 2: estabilidad de candidatos")
        val round2 = round1.mapNotNull { candidate ->
            val metrics = probeServer(candidate.server, ROUND2_SAMPLE_COUNT)
            if (passesRound2Thresholds(metrics)) {
                ScoredCandidate(candidate.server, metrics, computeRoundScore(candidate.server, metrics, selectionContext))
            } else {
                null
            }
        }.sortedBy { it.score }.take(ROUND2_KEEP_COUNT)

        if (round2.isEmpty()) {
            return fallbackSelection(onProgress)
        }

        onProgress("Validando ganador")
        val finalCandidates = round2.mapNotNull { candidate ->
            val metrics = validateServer(candidate.server)
            if (passesFinalThresholds(metrics) && hasUsableHttps(candidate.server)) {
                ScoredCandidate(candidate.server, metrics, computeFinalScore(candidate.server, metrics, selectionContext))
            } else {
                null
            }
        }.sortedBy { it.score }

        val winner = finalCandidates.firstOrNull() ?: return fallbackSelection(onProgress)
        cache.save(selectionContext, winner.server, winner.metrics, winner.score)
        return SpeedTestSelectionResult(
            server = winner.server,
            source = SpeedTestSelectionSource.DISCOVERY,
            pingMs = winner.metrics.medianMs,
            jitterMs = winner.metrics.jitterMs
        )
    }

    private fun fallbackSelection(onProgress: (String) -> Unit): SpeedTestSelectionResult {
        onProgress("Usando fallback publico")
        val server = SpeedTestServerCatalog.fallbackPublic()
        val metrics = validateServer(server)
        return SpeedTestSelectionResult(
            server = server,
            source = SpeedTestSelectionSource.FALLBACK_PUBLIC,
            pingMs = metrics.medianMs,
            jitterMs = metrics.jitterMs
        )
    }

    private fun discoverServers(selectionContext: SpeedTestSelectionContext): List<SpeedTestServer> {
        val rawServers = fetchServersJson()
        if (rawServers.isEmpty()) return emptyList()

        val strict = rawServers.filter { server ->
            selectionContext.simCountry.isNotBlank() && server.cc.equals(selectionContext.simCountry, ignoreCase = true)
        }
        val source = if (strict.size >= MIN_DISCOVERY_SERVER_COUNT) strict else rawServers

        val deduped = LinkedHashMap<String, SpeedTestServer>()
        source.asSequence()
            .filter { it.host.isNotBlank() && it.latencyUrl.isNotBlank() && it.downloadUrl.isNotBlank() && it.uploadUrl.isNotBlank() }
            .sortedBy { computePreScore(it, selectionContext) }
            .forEach { server ->
                val key = "${server.host.lowercase(Locale.US)}|${server.sponsor.lowercase(Locale.US)}"
                deduped.putIfAbsent(key, server)
            }

        return deduped.values.take(CANDIDATE_LIMIT).toList()
    }

    private fun fetchServersJson(): List<SpeedTestServer> {
        val primary = fetchServersFromUrl(PRIMARY_DISCOVERY_URL)
        if (primary.isNotEmpty()) return primary
        return fetchServersFromUrl(FALLBACK_DISCOVERY_URL)
    }

    private fun fetchServersFromUrl(url: String): List<SpeedTestServer> {
        val connection = openConnection(url, "GET")
        return try {
            connection.connect()
            if (connection.responseCode !in 200..299) return emptyList()
            val payload = connection.inputStream.use { input ->
                BufferedReader(InputStreamReader(input)).readText()
            }
            parseServers(payload)
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    private fun parseServers(payload: String): List<SpeedTestServer> {
        val array = runCatching { JSONArray(payload) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val uploadUrl = buildServerUrl(item) ?: continue
                val uploadEndpoint = URL(uploadUrl)
                val basePath = uploadEndpoint.path.substringBeforeLast('/', missingDelimiterValue = uploadEndpoint.path)
                val host = uploadEndpoint.host
                val port = if (uploadEndpoint.port > 0) uploadEndpoint.port else uploadEndpoint.defaultPort.takeIf { it > 0 } ?: 443
                val secureBase = buildString {
                    append("https://")
                    append(host)
                    if (port > 0 && port != 443) {
                        append(':')
                        append(port)
                    }
                    append(basePath)
                }

                add(
                    SpeedTestServer(
                        id = item.optString("id"),
                        label = buildServerLabel(item),
                        sponsor = item.optString("sponsor"),
                        name = item.optString("name"),
                        country = item.optString("country"),
                        cc = item.optString("cc"),
                        host = host,
                        port = port,
                        distanceKm = item.optDouble("distance").takeUnless { it.isNaN() },
                        latencyUrl = "$secureBase/latency.txt",
                        downloadUrl = "$secureBase/random4000x4000.jpg",
                        uploadUrl = "$secureBase/upload.php"
                    )
                )
            }
        }
    }

    private fun buildServerUrl(item: JSONObject): String? {
        val rawUrl = item.optString("url").trim()
        if (rawUrl.isBlank()) return null
        val parsed = runCatching { URL(rawUrl) }.getOrNull() ?: return null
        val canonicalHostPort = item.optString("host").trim()
        val canonicalHost = canonicalHostPort.substringBefore(':').trim()
        val host = canonicalHost.takeIf { it.isNotBlank() } ?: parsed.host.takeIf { it.isNotBlank() } ?: return null
        val port = if (parsed.port > 0) parsed.port else parsed.defaultPort.takeIf { it > 0 } ?: 8080
        val secureScheme = if (item.optInt("https_functional", 0) == 1) "https" else parsed.protocol
        return buildString {
            append(secureScheme)
            append("://")
            append(host)
            if (port > 0 && !((secureScheme == "https" && port == 443) || (secureScheme == "http" && port == 80))) {
                append(':')
                append(port)
            }
            append(parsed.path)
        }
    }

    private fun buildServerLabel(item: JSONObject): String {
        val sponsor = item.optString("sponsor").trim()
        val name = item.optString("name").trim()
        return when {
            sponsor.isNotBlank() && name.isNotBlank() -> "$sponsor - $name"
            sponsor.isNotBlank() -> sponsor
            name.isNotBlank() -> name
            else -> item.optString("host").trim()
        }
    }

    private fun computePreScore(server: SpeedTestServer, selectionContext: SpeedTestSelectionContext): Double {
        var score = server.distanceKm ?: DEFAULT_DISTANCE_KM
        if (selectionContext.simCountry.isNotBlank() && server.cc.equals(selectionContext.simCountry, ignoreCase = true)) {
            score -= PRE_SCORE_SAME_COUNTRY_BONUS
        }
        if (selectionContext.operatorName.isNotBlank() && server.sponsor.contains(selectionContext.operatorName, ignoreCase = true)) {
            score -= if (selectionContext.networkType == NETWORK_TYPE_MOBILE) {
                PRE_SCORE_OPERATOR_BONUS_MOBILE
            } else {
                PRE_SCORE_OPERATOR_BONUS_WIFI
            }
        }
        val cachedId = cache.peekServerId()
        if (cachedId != null && cachedId == server.id) {
            score -= PRE_SCORE_CACHE_BONUS
        }
        return score
    }

    private fun computeRoundScore(
        server: SpeedTestServer,
        metrics: SpeedTestProbeMetrics,
        selectionContext: SpeedTestSelectionContext
    ): Double {
        val preScore = computePreScore(server, selectionContext)
        return metrics.medianMs + (0.7 * metrics.jitterMs) + (40.0 * metrics.failCount) + (0.15 * preScore)
    }

    private fun computeFinalScore(
        server: SpeedTestServer,
        metrics: SpeedTestProbeMetrics,
        selectionContext: SpeedTestSelectionContext
    ): Double {
        val preScore = computePreScore(server, selectionContext)
        return metrics.medianMs + (0.7 * metrics.jitterMs) + (1.2 * metrics.failRatePercent) + (0.15 * preScore)
    }

    private fun probeServer(server: SpeedTestServer, samples: Int): SpeedTestProbeMetrics {
        val values = mutableListOf<Double>()
        var failCount = 0
        repeat(samples) { index ->
            val measurement = measureTcpConnect(server.host, server.port)
            if (measurement != null) {
                values += measurement
            } else {
                failCount += 1
            }
            if (index < samples - 1) {
                sleep(PROBE_PAUSE_MS)
            }
        }
        return toProbeMetrics(values, failCount, samples)
    }

    private fun validateServer(server: SpeedTestServer): SpeedTestProbeMetrics {
        val values = mutableListOf<Double>()
        var failCount = 0
        val startedAt = elapsedRealtimeMs()
        while (elapsedRealtimeMs() - startedAt < FINAL_VALIDATION_WINDOW_MS) {
            val measurement = measureTcpConnect(server.host, server.port)
            if (measurement != null) {
                values += measurement
            } else {
                failCount += 1
            }
            sleep(FINAL_VALIDATION_PROBE_INTERVAL_MS)
        }
        return toProbeMetrics(values, failCount, values.size + failCount)
    }

    private fun toProbeMetrics(values: List<Double>, failCount: Int, totalAttempts: Int): SpeedTestProbeMetrics {
        val medianMs = median(values)
        val jitterMs = computeJitter(values)
        val failRate = if (totalAttempts <= 0) 100.0 else (failCount * 100.0) / totalAttempts.toDouble()
        return SpeedTestProbeMetrics(
            medianMs = medianMs,
            jitterMs = jitterMs,
            failCount = failCount,
            failRatePercent = failRate,
            sampleCount = values.size
        )
    }

    private fun measureTcpConnect(host: String, port: Int): Double? {
        val startedAt = elapsedRealtimeMs()
        return try {
            Socket().use { socket ->
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            }
            (elapsedRealtimeMs() - startedAt).toDouble()
        } catch (_: Exception) {
            null
        }
    }

    private fun hasUsableHttps(server: SpeedTestServer): Boolean {
        val connection = openConnection(server.latencyUrl, "GET")
        return try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                false
            } else {
                connection.inputStream.use { input ->
                    input.read(ByteArray(64))
                }
                true
            }
        } catch (_: Exception) {
            false
        } finally {
            connection.disconnect()
        }
    }

    private fun passesRound1Thresholds(metrics: SpeedTestProbeMetrics): Boolean {
        return metrics.sampleCount > 0 &&
            metrics.medianMs <= ROUND1_MAX_MEDIAN_MS &&
            metrics.jitterMs <= ROUND1_MAX_JITTER_MS &&
            metrics.failCount <= ROUND1_MAX_FAILS
    }

    private fun passesRound2Thresholds(metrics: SpeedTestProbeMetrics): Boolean {
        return metrics.sampleCount > 0 &&
            metrics.medianMs <= ROUND2_MAX_MEDIAN_MS &&
            metrics.jitterMs <= ROUND2_MAX_JITTER_MS &&
            metrics.failCount <= ROUND2_MAX_FAILS
    }

    private fun passesFinalThresholds(metrics: SpeedTestProbeMetrics): Boolean {
        return metrics.sampleCount > 0 &&
            metrics.medianMs <= FINAL_MAX_MEDIAN_MS &&
            metrics.jitterMs <= FINAL_MAX_JITTER_MS &&
            metrics.failRatePercent <= FINAL_MAX_FAIL_RATE_PERCENT
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return FINAL_MAX_MEDIAN_MS + 999.0
        val sorted = values.sorted()
        if (sorted.size == 1) return sorted.first()
        return sorted[sorted.size / 2]
    }

    private fun computeJitter(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val deltas = buildList {
            for (index in 1 until values.size) {
                add(abs(values[index] - values[index - 1]))
            }
        }
        return deltas.average()
    }

    private fun resolveNetworkType(): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val capabilities = connectivityManager?.getNetworkCapabilities(connectivityManager.activeNetwork)
        return when {
            capabilities == null -> NETWORK_TYPE_UNKNOWN
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NETWORK_TYPE_WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NETWORK_TYPE_MOBILE
            else -> NETWORK_TYPE_UNKNOWN
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveWifiId(): String {
        if (resolveNetworkType() != NETWORK_TYPE_WIFI) return ""
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val info = runCatching { wifiManager?.connectionInfo }.getOrNull()
        val bssid = info?.bssid.orEmpty().trim()
        if (bssid.isNotBlank() && !bssid.equals("02:00:00:00:00:00", ignoreCase = true)) {
            return bssid
        }
        val ssid = info?.ssid.orEmpty().trim().trim('"')
        return ssid.takeUnless { it.equals("<unknown ssid>", ignoreCase = true) }.orEmpty()
    }

    private data class ScoredCandidate(
        val server: SpeedTestServer,
        val metrics: SpeedTestProbeMetrics,
        val score: Double
    )

    private companion object {
        private const val PRIMARY_DISCOVERY_URL = "https://www.speedtest.net/api/js/servers?engine=js&https=true&search="
        private const val FALLBACK_DISCOVERY_URL = "https://www.speedtest.net/api/js/servers?engine=js&platform=cli"
        private const val CONNECT_TIMEOUT_MS = 2_000
        private const val CANDIDATE_LIMIT = 12
        private const val MIN_DISCOVERY_SERVER_COUNT = 5
        private const val ROUND1_SAMPLE_COUNT = 3
        private const val ROUND2_SAMPLE_COUNT = 5
        private const val ROUND1_KEEP_COUNT = 4
        private const val ROUND2_KEEP_COUNT = 2
        private const val PROBE_PAUSE_MS = 150L
        private const val FINAL_VALIDATION_WINDOW_MS = 10_000L
        private const val FINAL_VALIDATION_PROBE_INTERVAL_MS = 1_000L
        private const val DEFAULT_DISTANCE_KM = 999.0
        private const val PRE_SCORE_SAME_COUNTRY_BONUS = 12.0
        private const val PRE_SCORE_OPERATOR_BONUS_WIFI = 2.0
        private const val PRE_SCORE_OPERATOR_BONUS_MOBILE = 8.0
        private const val PRE_SCORE_CACHE_BONUS = 6.0
        private const val ROUND1_MAX_MEDIAN_MS = 120.0
        private const val ROUND1_MAX_JITTER_MS = 35.0
        private const val ROUND1_MAX_FAILS = 1
        private const val ROUND2_MAX_MEDIAN_MS = 90.0
        private const val ROUND2_MAX_JITTER_MS = 25.0
        private const val ROUND2_MAX_FAILS = 1
        private const val FINAL_MAX_MEDIAN_MS = 85.0
        private const val FINAL_MAX_JITTER_MS = 22.0
        private const val FINAL_MAX_FAIL_RATE_PERCENT = 20.0
        private const val NETWORK_TYPE_WIFI = "wifi"
        private const val NETWORK_TYPE_MOBILE = "mobile"
        private const val NETWORK_TYPE_UNKNOWN = "unknown"
    }
}

internal class SpeedTestServerCache(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun peekServerId(): String? {
        return preferences.getString(KEY_SERVER_ID, null)
    }

    fun load(selectionContext: SpeedTestSelectionContext): SpeedTestServer? {
        val expiresAt = preferences.getLong(KEY_EXPIRES_AT_EPOCH, 0L)
        if (expiresAt <= System.currentTimeMillis() / 1000) return null
        if (preferences.getString(KEY_NETWORK_TYPE, null) != selectionContext.networkType) return null

        val cachedCountry = preferences.getString(KEY_SIM_COUNTRY, "").orEmpty()
        if (selectionContext.simCountry.isNotBlank() && cachedCountry != selectionContext.simCountry) return null

        if (selectionContext.networkType == "wifi") {
            val cachedWifiId = preferences.getString(KEY_WIFI_ID, "").orEmpty()
            if (selectionContext.wifiId.isNotBlank() && cachedWifiId.isNotBlank() && cachedWifiId != selectionContext.wifiId) {
                return null
            }
        }

        val id = preferences.getString(KEY_SERVER_ID, null) ?: return null
        val label = preferences.getString(KEY_SERVER_LABEL, null) ?: return null
        val sponsor = preferences.getString(KEY_SERVER_SPONSOR, null) ?: return null
        val name = preferences.getString(KEY_SERVER_NAME, null) ?: return null
        val country = preferences.getString(KEY_SERVER_COUNTRY, null) ?: return null
        val cc = preferences.getString(KEY_SERVER_CC, null) ?: return null
        val host = preferences.getString(KEY_SERVER_HOST, null) ?: return null
        val port = preferences.getInt(KEY_SERVER_PORT, 0)
        val latencyUrl = preferences.getString(KEY_LATENCY_URL, null) ?: return null
        val downloadUrl = preferences.getString(KEY_DOWNLOAD_URL, null) ?: return null
        val uploadUrl = preferences.getString(KEY_UPLOAD_URL, null) ?: return null

        return SpeedTestServer(
            id = id,
            label = label,
            sponsor = sponsor,
            name = name,
            country = country,
            cc = cc,
            host = host,
            port = port,
            distanceKm = preferences.getString(KEY_DISTANCE_KM, null)?.toDoubleOrNull(),
            latencyUrl = latencyUrl,
            downloadUrl = downloadUrl,
            uploadUrl = uploadUrl
        )
    }

    fun save(
        selectionContext: SpeedTestSelectionContext,
        server: SpeedTestServer,
        metrics: SpeedTestProbeMetrics,
        finalScore: Double
    ) {
        val ttlSeconds = if (selectionContext.networkType == "mobile") MOBILE_TTL_SEC else WIFI_TTL_SEC
        val nowEpoch = System.currentTimeMillis() / 1000
        preferences.edit()
            .putLong(KEY_CREATED_AT_EPOCH, nowEpoch)
            .putLong(KEY_EXPIRES_AT_EPOCH, nowEpoch + ttlSeconds)
            .putString(KEY_NETWORK_TYPE, selectionContext.networkType)
            .putString(KEY_SIM_COUNTRY, selectionContext.simCountry)
            .putString(KEY_SIM_COUNTRY_NAME, selectionContext.simCountryName)
            .putString(KEY_OPERATOR_NAME, selectionContext.operatorName)
            .putString(KEY_WIFI_ID, selectionContext.wifiId)
            .putString(KEY_SERVER_ID, server.id)
            .putString(KEY_SERVER_LABEL, server.label)
            .putString(KEY_SERVER_SPONSOR, server.sponsor)
            .putString(KEY_SERVER_NAME, server.name)
            .putString(KEY_SERVER_COUNTRY, server.country)
            .putString(KEY_SERVER_CC, server.cc)
            .putString(KEY_SERVER_HOST, server.host)
            .putInt(KEY_SERVER_PORT, server.port)
            .putString(KEY_DISTANCE_KM, server.distanceKm?.toString().orEmpty())
            .putString(KEY_LATENCY_URL, server.latencyUrl)
            .putString(KEY_DOWNLOAD_URL, server.downloadUrl)
            .putString(KEY_UPLOAD_URL, server.uploadUrl)
            .putString(KEY_FINAL_SCORE, finalScore.toString())
            .putString(KEY_FINAL_MEDIAN_MS, metrics.medianMs.toString())
            .putString(KEY_FINAL_JITTER_MS, metrics.jitterMs.toString())
            .putString(KEY_FINAL_FAIL_RATE_PCT, metrics.failRatePercent.toString())
            .apply()
    }

    private companion object {
        private const val PREFERENCES_NAME = "speedtest_server_cache"
        private const val WIFI_TTL_SEC = 1_800L
        private const val MOBILE_TTL_SEC = 3_600L
        private const val KEY_CREATED_AT_EPOCH = "created_at_epoch"
        private const val KEY_EXPIRES_AT_EPOCH = "expires_at_epoch"
        private const val KEY_NETWORK_TYPE = "network_type"
        private const val KEY_SIM_COUNTRY = "sim_country"
        private const val KEY_SIM_COUNTRY_NAME = "sim_country_name"
        private const val KEY_OPERATOR_NAME = "operator_name"
        private const val KEY_WIFI_ID = "wifi_id"
        private const val KEY_SERVER_ID = "server_id"
        private const val KEY_SERVER_LABEL = "server_label"
        private const val KEY_SERVER_SPONSOR = "server_sponsor"
        private const val KEY_SERVER_NAME = "server_name"
        private const val KEY_SERVER_COUNTRY = "server_country"
        private const val KEY_SERVER_CC = "server_cc"
        private const val KEY_SERVER_HOST = "server_host"
        private const val KEY_SERVER_PORT = "server_port"
        private const val KEY_DISTANCE_KM = "distance_km"
        private const val KEY_LATENCY_URL = "latency_url"
        private const val KEY_DOWNLOAD_URL = "download_url"
        private const val KEY_UPLOAD_URL = "upload_url"
        private const val KEY_FINAL_SCORE = "final_score"
        private const val KEY_FINAL_MEDIAN_MS = "final_median_ms"
        private const val KEY_FINAL_JITTER_MS = "final_jitter_ms"
        private const val KEY_FINAL_FAIL_RATE_PCT = "final_fail_rate_pct"
    }
}