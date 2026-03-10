package app.kitsunping

import org.json.JSONArray
import org.json.JSONObject

data class PolicyEvent(
    val ts: Long,
    val target: String,
    val appliedProfile: Int,
    val propsApplied: Int,
    val propsFailed: Int,
    val propsFailedList: List<String>,
    val calibrateState: String,
    val calibrateTs: Long,
    val event: String
) {
    fun toJsonString(): String {
        val obj = JSONObject()
        obj.put("ts", ts)
        obj.put("target", target)
        obj.put("applied_profile", appliedProfile)
        obj.put("props_applied", propsApplied)
        obj.put("props_failed", propsFailed)
        obj.put("props_failed_list", JSONArray(propsFailedList))
        obj.put("calibrate_state", calibrateState)
        obj.put("calibrate_ts", calibrateTs)
        obj.put("event", event)
        return obj.toString()
    }

    companion object {
        fun empty(): PolicyEvent = PolicyEvent(
            ts = 0,
            target = "unknown",
            appliedProfile = 0,
            propsApplied = 0,
            propsFailed = 0,
            propsFailedList = emptyList(),
            calibrateState = "unknown",
            calibrateTs = 0,
            event = "unknown"
        )

        fun fromJson(payload: String?): PolicyEvent? {
            if (payload.isNullOrBlank()) return null
            return try {
                val obj = JSONObject(payload)
                val propsFailedList = mutableListOf<String>()
                val arr = obj.optJSONArray("props_failed_list") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    propsFailedList.add(arr.optString(i))
                }
                PolicyEvent(
                    ts = obj.optLong("ts", 0),
                    target = obj.optString("target", "unknown"),
                    appliedProfile = obj.optInt("applied_profile", 0),
                    propsApplied = obj.optInt("props_applied", 0),
                    propsFailed = obj.optInt("props_failed", 0),
                    propsFailedList = propsFailedList,
                    calibrateState = obj.optString("calibrate_state", "unknown"),
                    calibrateTs = obj.optLong("calibrate_ts", 0),
                    event = obj.optString("event", "unknown")
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
