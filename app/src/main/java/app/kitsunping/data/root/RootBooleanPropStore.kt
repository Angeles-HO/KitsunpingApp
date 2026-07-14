package app.kitsunping.data.root

data class BooleanRootPropSpec(
    val persistProp: String,
    val runtimeProp: String? = null,
    val defaultValue: Boolean = false,
    val syncSystemProp: Boolean = false
)

class RootBooleanPropStore(
    private val rootCommandExecutor: RootCommandExecutor,
    private val moduleRoot: String
) {
    private val systemPropPath = "$moduleRoot/system.prop"

    fun read(spec: BooleanRootPropSpec): Boolean {
        val tagged = rootCommandExecutor.runCapture(buildReadCommand(spec))
        val token = extractTaggedValue(tagged).orEmpty()
        return parseBooleanToken(token, spec.defaultValue)
    }

    fun writeCommands(spec: BooleanRootPropSpec, enabled: Boolean): List<String> {
        val value = if (enabled) "1" else "0"
        val commands = mutableListOf<String>()
        commands += "resetprop ${spec.persistProp} $value || setprop ${spec.persistProp} $value"
        if (!spec.runtimeProp.isNullOrBlank()) {
            commands += "resetprop ${spec.runtimeProp} $value || setprop ${spec.runtimeProp} $value"
        }
        if (spec.syncSystemProp) {
            commands += buildSystemPropSyncCommand(spec, value)
        }
        return commands
    }

    private fun buildReadCommand(spec: BooleanRootPropSpec): String {
        val persist = shQuote(spec.persistProp)
        val runtime = shQuote(spec.runtimeProp.orEmpty())
        val systemProp = shQuote(systemPropPath)
        return """
            persist_key=$persist
            runtime_key=$runtime
            prop_file=$systemProp

            read_prop() {
                key="${'$'}1"
                [ -n "${'$'}key" ] || return 0
                getprop "${'$'}key" 2>/dev/null | tr -d '\r\n'
            }

            read_from_file() {
                key="${'$'}1"
                [ -n "${'$'}key" ] || return 0
                [ -f "${'$'}prop_file" ] || return 0
                awk -F= -v k="${'$'}key" 'index($0, k"=")==1 {print substr($0, length(k)+2); exit}' "${'$'}prop_file" 2>/dev/null | tr -d '\r\n'
            }

            value="${'$'}(read_prop "${'$'}persist_key")"
            [ -z "${'$'}value" ] && value="${'$'}(read_prop "${'$'}runtime_key")"
            [ -z "${'$'}value" ] && value="${'$'}(read_from_file "${'$'}persist_key")"
            [ -z "${'$'}value" ] && value="${'$'}(read_from_file "${'$'}runtime_key")"
            printf '__KBOOL__:%s\n' "${'$'}value"
        """.trimIndent()
    }

    private fun buildSystemPropSyncCommand(spec: BooleanRootPropSpec, value: String): String {
        val persist = shQuote(spec.persistProp)
        val runtime = shQuote(spec.runtimeProp.orEmpty())
        val systemProp = shQuote(systemPropPath)
        val valueQuoted = shQuote(value)
        return """
            prop_file=$systemProp
            value=$valueQuoted
            persist_key=$persist
            runtime_key=$runtime

            update_prop_file() {
                key="${'$'}1"
                [ -n "${'$'}key" ] || return 0
                [ -f "${'$'}prop_file" ] || return 0
                tmp="${'$'}{prop_file}.tmp.${'$'}${'$'}"
                awk -v k="${'$'}key" -v v="${'$'}value" 'BEGIN{u=0} index($0, k"=")==1 {if(!u){print k"="v;u=1}; next} {print} END{if(!u) print k"="v}' "${'$'}prop_file" > "${'$'}tmp" 2>/dev/null || return 0
                cat "${'$'}tmp" > "${'$'}prop_file" 2>/dev/null || true
                rm -f "${'$'}tmp" 2>/dev/null || true
            }

            update_prop_file "${'$'}persist_key"
            update_prop_file "${'$'}runtime_key"
        """.trimIndent()
    }

    private fun extractTaggedValue(raw: String): String? {
        return raw
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("__KBOOL__:") }
            ?.removePrefix("__KBOOL__:")
            ?.trim()
    }

    private fun parseBooleanToken(token: String, defaultValue: Boolean): Boolean {
        return when (token.trim().lowercase()) {
            "1", "true", "yes", "on" -> true
            "0", "false", "no", "off" -> false
            else -> defaultValue
        }
    }

    private fun shQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }
}