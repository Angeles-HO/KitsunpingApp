package app.kitsunping.data.root

import java.io.IOException

/**
 * Starts privileged commands with fallback SU binary paths.
 * Some devices do not expose `su` in app PATH, which causes error=2.
 */
object SuProcessLauncher {
    private val suCandidates = listOf(
        "/system/bin/su"
    )

    fun start(command: String): Process {
        require(command.isNotBlank()) { "Command must not be blank" }
        var lastError: IOException? = null

        for (suBinary in suCandidates) {
            try {
                return ProcessBuilder(suBinary, "-c", command).start()
            } catch (e: IOException) {
                lastError = e
                if (!isRetryableSuBinaryError(e)) {
                    throw e
                }
            }
        }

        throw lastError ?: IOException(
            "Unable to execute su. Tried: ${suCandidates.joinToString(", ")}"
        )
    }

    private fun isRetryableSuBinaryError(error: IOException): Boolean {
        val message = error.message.orEmpty()
        return message.contains("error=2") ||
            message.contains("error=13") ||
            message.contains("EACCES", ignoreCase = true) ||
            message.contains("Permission denied", ignoreCase = true) ||
            message.contains("No such file or directory", ignoreCase = true)
    }
}
