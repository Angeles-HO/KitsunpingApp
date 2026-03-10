package app.kitsunping

import android.content.Context
import org.json.JSONObject

class ModuleRepository(context: Context) {
    private val store = PolicyEventStore(context)

    fun loadSnapshot(): ModuleSnapshot {
        val reads = RootFileReader.readMany(
            listOf(
                PATH_POLICY_EVENT,
                PATH_DAEMON_STATE,
                PATH_LAST_EVENT,
                PATH_RESULTS_ENV,
                PATH_POLICY_CURRENT,
                PATH_POLICY_TARGET,
                PATH_POLICY_REQUEST,
                PATH_TARGET_STATE_HISTORY,
                PATH_PROC_NET_ROUTE
            )
        )

        val policyEventJson = reads[PATH_POLICY_EVENT]
        val policyEvent = PolicyEvent.fromJson(policyEventJson) ?: store.load()

        val daemonState = parseKeyValue(reads[PATH_DAEMON_STATE]).toMutableMap()
        enrichGatewayFields(daemonState, reads[PATH_PROC_NET_ROUTE].orEmpty())
        val lastEvent = LastEvent.fromJson(reads[PATH_LAST_EVENT]) ?: LastEvent.empty()
        val resultsEnv = parseKeyValue(reads[PATH_RESULTS_ENV])

        val policyCurrent = reads[PATH_POLICY_CURRENT]?.trim().orEmpty()
        val policyTarget = reads[PATH_POLICY_TARGET]?.trim().orEmpty()
        val policyRequest = reads[PATH_POLICY_REQUEST]?.trim().orEmpty()
        val targetState = resolveTargetState(daemonState, policyEventJson)
        val targetStateReason = resolveTargetStateReason(daemonState, policyEventJson)
        val targetStateHistory = reads[PATH_TARGET_STATE_HISTORY]
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toList()
            ?.takeLast(20)
            ?.reversed()
            ?: emptyList()

        return ModuleSnapshot(
            policyEvent = policyEvent,
            daemonState = daemonState,
            daemonRuntime = DaemonRuntimeStatus.empty(),
            lastEvent = lastEvent,
            resultsEnv = resultsEnv,
            policyCurrent = policyCurrent,
            policyTarget = policyTarget,
            policyRequest = policyRequest,
            targetState = targetState,
            targetStateReason = targetStateReason,
            targetStateHistory = targetStateHistory
        )
    }

    private fun resolveTargetState(
        daemonState: Map<String, String>,
        policyEventJson: String?
    ): String {
        val rawState = daemonState["target.state"].orEmpty().trim().ifBlank {
            extractJsonString(policyEventJson, "target_state")
        }
        return normalizeTargetState(rawState)
    }

    private fun resolveTargetStateReason(
        daemonState: Map<String, String>,
        policyEventJson: String?
    ): String {
        return daemonState["target.state.reason"].orEmpty().trim().ifBlank {
            extractJsonString(policyEventJson, "target_state_reason")
        }
    }

    private fun extractJsonString(payload: String?, key: String): String {
        if (payload.isNullOrBlank()) return ""
        return runCatching {
            JSONObject(payload).optString(key, "").trim()
        }.getOrDefault("")
    }

    private fun normalizeTargetState(rawState: String): String {
        return when (rawState.trim().uppercase()) {
            "IDLE", "APP_OVERRIDE", "NETWORK_DECISION", "POLICY_APPLIED" -> rawState.trim().uppercase()
            else -> "IDLE"
        }
    }

    private fun enrichGatewayFields(daemonState: MutableMap<String, String>, procRoute: String) {
        val currentGateway = daemonState["gateway"].orEmpty().trim()
        if (currentGateway.isNotEmpty()) return
        if (procRoute.isBlank()) return

        val wifiIface = daemonState["wifi.iface"].orEmpty().trim().ifBlank { null }
        val gateway = parseDefaultGateway(procRoute, wifiIface)
            ?: parseDefaultGateway(procRoute, null)
            ?: return

        daemonState.putIfAbsent("gateway", gateway)
        daemonState.putIfAbsent("gw", gateway)
        daemonState.putIfAbsent("router.ip", gateway)
        daemonState.putIfAbsent("router_ip", gateway)

        if (!wifiIface.isNullOrBlank()) {
            daemonState.putIfAbsent("wifi.gateway", gateway)
            daemonState.putIfAbsent("wifi.gw", gateway)
        }
    }

    private fun parseDefaultGateway(procRoute: String, preferredIface: String?): String? {
        val lines = procRoute.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return null

        for (line in lines.drop(1)) {
            val cols = line.split(Regex("\\s+"))
            if (cols.size < 3) continue

            val iface = cols[0]
            val destination = cols[1]
            val gatewayHex = cols[2]

            if (!preferredIface.isNullOrBlank() && iface != preferredIface) continue
            if (!destination.equals("00000000", ignoreCase = true)) continue

            val parsed = parseLittleEndianIpv4(gatewayHex)
            if (!parsed.isNullOrBlank()) return parsed
        }

        return null
    }

    private fun parseLittleEndianIpv4(hexValue: String): String? {
        val clean = hexValue.trim()
        if (!clean.matches(Regex("^[0-9A-Fa-f]{8}$"))) return null

        val bytes = clean.chunked(2)
        if (bytes.size != 4) return null

        val parts = bytes.reversed().mapNotNull { it.toIntOrNull(16) }
        if (parts.size != 4) return null

        if (parts.any { it !in 0..255 }) return null
        return parts.joinToString(".")
    }

    private fun parseKeyValue(payload: String?): Map<String, String> {
        if (payload.isNullOrBlank()) return emptyMap()
        val map = mutableMapOf<String, String>()
        payload.lineSequence().forEach { line ->
            val idx = line.indexOf('=')
            if (idx <= 0) return@forEach
            val key = line.substring(0, idx).trim()
            val value = line.substring(idx + 1).trim()
            if (key.isNotEmpty()) {
                map[key] = value
            }
        }
        return map
    }

    companion object {
        private const val MODULE_ROOT = "/data/adb/modules/Kitsunping"
        private const val PATH_CACHE = "$MODULE_ROOT/cache"
        private const val PATH_LOGS = "$MODULE_ROOT/logs"
        private const val PATH_PROC_NET_ROUTE = "/proc/net/route"

        const val PATH_POLICY_EVENT = "$PATH_CACHE/policy.event.json"
        const val PATH_DAEMON_STATE = "$PATH_CACHE/daemon.state"
        const val PATH_LAST_EVENT = "$PATH_CACHE/event.last.json"
        const val PATH_POLICY_CURRENT = "$PATH_CACHE/policy.current"
        const val PATH_POLICY_TARGET = "$PATH_CACHE/policy.target"
        const val PATH_POLICY_REQUEST = "$PATH_CACHE/policy.request"
        const val PATH_TARGET_STATE_HISTORY = "$PATH_CACHE/target.state.history"
        const val PATH_RESULTS_ENV = "$PATH_LOGS/results.env"
    }
}
