package app.kitsunping

import android.content.Context

class PolicyEventStore(context: Context) {
    private val prefs = context.getSharedPreferences("policy_event", Context.MODE_PRIVATE)

    fun saveJson(payload: String) {
        prefs.edit().putString("payload", payload).apply()
    }

    fun load(): PolicyEvent {
        val payload = prefs.getString("payload", null)
        return PolicyEvent.fromJson(payload) ?: PolicyEvent.empty()
    }
}
