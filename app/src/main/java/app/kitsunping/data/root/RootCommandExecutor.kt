package app.kitsunping.data.root

class RootCommandExecutor {
    fun runCommands(cmds: List<String>): String {
        val normalized = cmds.map { it.trim() }.filter { it.isNotEmpty() }
        if (normalized.isEmpty()) return "No output"

        val script = buildString {
            append("set +e\n")
            normalized.forEachIndexed { index, cmd ->
                append("echo '__KITSUN_CMD_BEGIN_${index}__'\n")
                append(cmd).append('\n')
                append("echo '__KITSUN_CMD_RC_${index}:$?'\n")
                append("echo '__KITSUN_CMD_END_${index}__'\n")
            }
        }

        return runSingle(script, includeCmdLabel = false).trim().ifBlank { "No output" }
    }

    fun runCapture(cmd: String): String {
        return runSingle(cmd, includeCmdLabel = false).trim().ifBlank { "no output" }
    }

    private fun runSingle(cmd: String, includeCmdLabel: Boolean): String {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val out = proc.inputStream.bufferedReader().use { it.readText() }
            val err = proc.errorStream.bufferedReader().use { it.readText() }
            val rc = proc.waitFor()
            buildString {
                if (includeCmdLabel) {
                    append("cmd=").append(cmd).append(' ')
                }
                append("rc=").append(rc).append('\n')
                if (out.isNotBlank()) append("out:").append(out).append('\n')
                if (err.isNotBlank()) append("err:").append(err).append('\n')
            }.trim()
        } catch (e: Exception) {
            if (includeCmdLabel) {
                "cmd=$cmd exception:${e.message}"
            } else {
                "error: ${e.message}"
            }
        }
    }
}
