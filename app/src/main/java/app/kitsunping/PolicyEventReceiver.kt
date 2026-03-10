package app.kitsunping

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PolicyEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PolicyEventContract.ACTION_UPDATE) return
        val pendingResult = goAsync()
        Thread {
            try {
                handleIntent(context, intent)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun handleIntent(context: Context, intent: Intent) {
        val payload = intent.getStringExtra(PolicyEventContract.EXTRA_PAYLOAD)
        val store = PolicyEventStore(context)
        if (!payload.isNullOrBlank()) {
            store.saveJson(payload)
        } else {
            val event = intent.getStringExtra(PolicyEventContract.EXTRA_EVENT) ?: "unknown"
            val ts = intent.getStringExtra(PolicyEventContract.EXTRA_TS)?.toLongOrNull() ?: 0
            val fallback = PolicyEvent(
                ts = ts,
                target = "unknown",
                appliedProfile = 0,
                propsApplied = 0,
                propsFailed = 0,
                propsFailedList = emptyList(),
                calibrateState = "unknown",
                calibrateTs = 0,
                event = event
            )
            store.saveJson(fallback.toJsonString())
        }

        val repository = ModuleRepository(context)
        val snapshot = repository.loadSnapshot()
        NotificationHelper.handleSnapshot(context, snapshot)
    }
}
