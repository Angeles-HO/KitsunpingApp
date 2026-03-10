package app.kitsunping

import java.io.BufferedReader
import java.io.InputStreamReader

object RootFileReader {
    private const val MARKER = "__KITSUNPING_READ__"

    fun read(path: String): String? {
        return try {
            val process = ProcessBuilder("su", "-c", "cat ${shQuote(path)}").start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val exit = process.waitFor()
            if (exit == 0) output else null
        } catch (_: Exception) {
            null
        }
    }

    fun exists(path: String): Boolean {
        return try {
            val process = ProcessBuilder("su", "-c", "[ -f ${shQuote(path)} ]").start()
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    fun list(path: String, pattern: String? = null): List<String> {
        return try {
            val target = if (pattern.isNullOrBlank()) {
                shQuote(path)
            } else {
                "${shQuote(path)}/$pattern"
            }
            val process = ProcessBuilder("su", "-c", "ls -1 $target 2>/dev/null").start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            process.waitFor()
            output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun readMany(paths: List<String>): Map<String, String?> {
        if (paths.isEmpty()) return emptyMap()
        return try {
            val script = buildString {
                for (path in paths) {
                    val quoted = shQuote(path)
                    append("printf '%s\\n' '$MARKER|BEGIN|$path'\n")
                    append("if [ -f $quoted ]; then cat $quoted; printf '\\n'; fi\n")
                    append("printf '%s\\n' '$MARKER|END|$path'\n")
                }
            }
            val process = ProcessBuilder("su", "-c", script).start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val exit = process.waitFor()
            if (exit != 0) return paths.associateWith { null }

            val result = linkedMapOf<String, String?>()
            var currentPath: String? = null
            val currentContent = StringBuilder()

            fun flushCurrent() {
                val path = currentPath ?: return
                result[path] = currentContent.toString()
                currentPath = null
                currentContent.setLength(0)
            }

            output.lineSequence().forEach { line ->
                if (line.startsWith("$MARKER|BEGIN|")) {
                    flushCurrent()
                    currentPath = line.removePrefix("$MARKER|BEGIN|")
                    return@forEach
                }
                if (line.startsWith("$MARKER|END|")) {
                    flushCurrent()
                    return@forEach
                }
                if (currentPath != null) {
                    val endToken = "$MARKER|END|$currentPath"
                    val endIndex = line.indexOf(endToken)
                    if (endIndex >= 0) {
                        if (endIndex > 0) {
                            currentContent.append(line.substring(0, endIndex))
                        }
                        flushCurrent()
                        return@forEach
                    }
                    currentContent.append(line).append('\n')
                }
            }
            flushCurrent()

            paths.associateWith { path ->
                result[path]?.let { content ->
                    val normalized = content.trimEnd('\n', '\r')
                    if (normalized.isEmpty()) null else normalized
                }
            }
        } catch (_: Exception) {
            paths.associateWith { null }
        }
    }

    private fun shQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }
}
