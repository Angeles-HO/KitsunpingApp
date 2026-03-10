package app.kitsunping.domain.events

import android.content.Context
import android.content.Intent
import app.kitsunping.PolicyEventContract

class PolicyEventDispatcher(private val context: Context) {
    fun dispatch(event: String, payload: String?) {
        val intent = Intent(PolicyEventContract.ACTION_UPDATE)
        if (!payload.isNullOrBlank()) {
            intent.putExtra(PolicyEventContract.EXTRA_PAYLOAD, payload)
        } else {
            intent.putExtra(PolicyEventContract.EXTRA_EVENT, event)
            intent.putExtra(PolicyEventContract.EXTRA_TS, (System.currentTimeMillis() / 1000).toString())
        }
        context.sendBroadcast(intent)
    }
}
