package app.kitsunping.feature.speedtest

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.max

class SpeedTestRunner(
    private val context: Context,
    private val onStateChange: (SpeedTestUiState) -> Unit
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serverSelector = SpeedTestServerSelector(
        context = context,
        openConnection = ::openConnection,
        sleep = Thread::sleep,
        elapsedRealtimeMs = SystemClock::elapsedRealtime
    )

    @Volatile
    private var running = false

    fun start(config: SpeedTestRunConfig = SpeedTestRunConfig()) {
        if (running) return
        running = true
        executor.execute {
            var state = SpeedTestUiState(
                isRunning = true,
                phase = SpeedTestPhase.SELECTING_SERVER,
                statusMessage = "Selecting public server",
                parallelStreams = config.parallelStreams,
                testDurationSec = config.testDurationSec
            )
            publish(state)

            try {
                val selectionContext = serverSelector.buildSelectionContext()
                val selection = serverSelector.selectBestServer(selectionContext) { progress ->
                    publish(state.copy(statusMessage = progress))
                }
                val server = selection.server
                state = state.copy(
                    serverLabel = server.label,
                    serverId = server.id,
                    serverHost = server.host,
                    serverCountry = server.country,
                    countryHint = selectionContext.simCountry.ifBlank { selectionContext.simCountryName },
                    operatorHint = selectionContext.operatorName,
                    networkTypeLabel = selectionContext.networkType,
                    selectionSource = selection.source.name.lowercase(),
                    phase = SpeedTestPhase.MEASURING_DOWNLOAD,
                    statusMessage = "Server ready, starting download",
                    parallelStreams = config.parallelStreams,
                    testDurationSec = config.testDurationSec,
                    pingMs = selection.pingMs,
                    jitterMs = selection.jitterMs
                )
                publish(state)

                val downloadMbps = measureDownloadMbps(server.downloadUrl, config)
                state = state.copy(
                    phase = SpeedTestPhase.MEASURING_UPLOAD,
                    statusMessage = "Descarga medida, iniciando subida",
                    downloadMbps = downloadMbps
                )
                publish(state)

                val uploadMbps = measureUploadMbps(server.uploadUrl, config)
                state = state.copy(
                    isRunning = false,
                    phase = SpeedTestPhase.COMPLETED,
                    statusMessage = "Measurement completed",
                    uploadMbps = uploadMbps,
                    errorMessage = null,
                    lastUpdatedAtMs = System.currentTimeMillis()
                )
                publish(state)
            } catch (error: Exception) {
                publish(
                    state.copy(
                        isRunning = false,
                        phase = SpeedTestPhase.FAILED,
                        statusMessage = "Measurement failed",
                        errorMessage = error.message ?: "Unexpected error",
                        lastUpdatedAtMs = System.currentTimeMillis()
                    )
                )
            } finally {
                running = false
            }
        }
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    private fun publish(state: SpeedTestUiState) {
        mainHandler.post { onStateChange(state) }
    }

    private fun measureDownloadMbps(url: String, config: SpeedTestRunConfig): Double {
        val startedAt = SystemClock.elapsedRealtime()
        val durationMs = config.testDurationSec.coerceIn(MIN_TEST_DURATION_SEC, MAX_TEST_DURATION_SEC) * 1000L
        val deadlineMs = startedAt + durationMs
        val totalBytes = runParallelTransfer(config.parallelStreams) {
            downloadWorker(url, deadlineMs)
        }
        val elapsedMs = max(SystemClock.elapsedRealtime() - startedAt, 1L)
        return bytesToMbps(totalBytes, elapsedMs)
    }

    private fun measureUploadMbps(url: String, config: SpeedTestRunConfig): Double {
        val payload = ByteArray(UPLOAD_CHUNK_SIZE_BYTES) { index -> ((index * 31) and 0xFF).toByte() }
        val startedAt = SystemClock.elapsedRealtime()
        val durationMs = config.testDurationSec.coerceIn(MIN_TEST_DURATION_SEC, MAX_TEST_DURATION_SEC) * 1000L
        val deadlineMs = startedAt + durationMs
        val totalBytes = runParallelTransfer(config.parallelStreams) {
            uploadWorker(url, payload, deadlineMs)
        }
        val elapsedMs = max(SystemClock.elapsedRealtime() - startedAt, 1L)
        return bytesToMbps(totalBytes, elapsedMs)
    }

    private fun downloadWorker(url: String, deadlineMs: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0L
        while (SystemClock.elapsedRealtime() < deadlineMs) {
            try {
                withConnection(url, method = "GET") { connection ->
                    connection.connect()
                    connection.inputStream.use { input ->
                        while (SystemClock.elapsedRealtime() < deadlineMs) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            totalBytes += read.toLong()
                        }
                    }
                }
            } catch (_: Exception) {
                break
            }
        }
        return totalBytes
    }

    private fun uploadWorker(url: String, payload: ByteArray, deadlineMs: Long): Long {
        var totalBytes = 0L
        while (SystemClock.elapsedRealtime() < deadlineMs) {
            try {
                withConnection(url, method = "POST") { connection ->
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/octet-stream")
                    connection.setFixedLengthStreamingMode(payload.size)
                    connection.connect()
                    connection.outputStream.use { output ->
                        output.write(payload)
                        output.flush()
                    }
                    totalBytes += payload.size.toLong()
                    val response = if (connection.responseCode >= 400) connection.errorStream else connection.inputStream
                    response?.use { input ->
                        val sink = ByteArrayOutputStream()
                        val buffer = ByteArray(1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            sink.write(buffer, 0, read)
                        }
                    }
                }
            } catch (_: Exception) {
                break
            }
        }
        return totalBytes
    }

    private fun runParallelTransfer(parallelStreams: Int, worker: () -> Long): Long {
        val streamCount = parallelStreams.coerceIn(MIN_PARALLEL_STREAMS, MAX_PARALLEL_STREAMS)
        val pool = Executors.newFixedThreadPool(streamCount)
        return try {
            val futures = (0 until streamCount).map {
                pool.submit(Callable<Long> { worker() })
            }
            futures.sumOf { future -> runCatching { future.get() }.getOrDefault(0L) }
        } finally {
            pool.shutdownNow()
            pool.awaitTermination(1, TimeUnit.SECONDS)
        }
    }

    private fun bytesToMbps(bytes: Long, elapsedMs: Long): Double {
        val bits = bytes * 8.0
        val seconds = elapsedMs / 1000.0
        return (bits / seconds) / 1_000_000.0
    }

    private fun <T> withConnection(
        url: String,
        method: String,
        block: (HttpURLConnection) -> T
    ): T {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            instanceFollowRedirects = true
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Pragma", "no-cache")
            setRequestProperty("User-Agent", USER_AGENT)
        }

        return try {
            block(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String, method: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            instanceFollowRedirects = true
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Pragma", "no-cache")
            setRequestProperty("User-Agent", USER_AGENT)
        }
    }

    private companion object {
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val MIN_PARALLEL_STREAMS = 1
        private const val MAX_PARALLEL_STREAMS = 6
        private const val MIN_TEST_DURATION_SEC = 5
        private const val MAX_TEST_DURATION_SEC = 20
        private const val UPLOAD_CHUNK_SIZE_BYTES = 512 * 1024
        private const val USER_AGENT = "Kitsunping-SpeedTest/1.0"
    }
}