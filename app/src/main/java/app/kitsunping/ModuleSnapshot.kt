package app.kitsunping

import org.json.JSONObject

data class LastEvent(
    val event: String,
    val ts: Long,
    val details: String,
    val iface: String,
    val wifiState: String,
    val wifiScore: Int
) {
    companion object {
        fun empty(): LastEvent = LastEvent(
            event = "unknown",
            ts = 0,
            details = "",
            iface = "",
            wifiState = "",
            wifiScore = 0
        )

        fun fromJson(payload: String?): LastEvent? {
            if (payload.isNullOrBlank()) return null
            return try {
                val obj = JSONObject(payload)
                LastEvent(
                    event = obj.optString("event", "unknown"),
                    ts = obj.optLong("ts", 0),
                    details = obj.optString("details", ""),
                    iface = obj.optString("iface", ""),
                    wifiState = obj.optString("wifi_state", ""),
                    wifiScore = obj.optInt("wifi_score", 0)
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

data class DaemonRuntimeStatus(
    val state: String,
    val isRunning: Boolean,
    val pid: String,
    val command: String,
    val detail: String,
    val checkedAt: Long
) {
    companion object {
        fun empty(): DaemonRuntimeStatus = DaemonRuntimeStatus(
            state = "UNKNOWN",
            isRunning = false,
            pid = "",
            command = "",
            detail = "no verification",
            checkedAt = 0
        )
    }
}

data class ModuleSnapshot(
    val policyEvent: PolicyEvent,
    val daemonState: Map<String, String>,
    val daemonRuntime: DaemonRuntimeStatus,
    val lastEvent: LastEvent,
    val resultsEnv: Map<String, String>,
    val policyCurrent: String,
    val policyTarget: String,
    val policyRequest: String,
    val targetState: String,
    val targetStateReason: String,
    val targetStateHistory: List<String>
) {
    companion object {
        fun empty(): ModuleSnapshot = ModuleSnapshot(
            policyEvent = PolicyEvent.empty(),
            daemonState = emptyMap(),
            daemonRuntime = DaemonRuntimeStatus.empty(),
            lastEvent = LastEvent.empty(),
            resultsEnv = emptyMap(),
            policyCurrent = "",
            policyTarget = "",
            policyRequest = "",
            targetState = "IDLE",
            targetStateReason = "",
            targetStateHistory = emptyList()
        )
    }
}
