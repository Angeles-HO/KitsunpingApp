package app.kitsunping

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    private const val CHANNEL_ID_STATUS = "kitsunping_status"
    private const val CHANNEL_ID_EVENTS = "kitsunping_events"
    private const val CHANNEL_ID_CHANNEL_AVAILABLE = "kitsunping_channel_available"  // M4
    private const val NOTIF_ID_STATUS = 1001
    private const val NOTIF_ID_CHANNEL_AVAILABLE = 1002  // M4
    private const val PREFS_NAME = "notification_state"

    fun handleSnapshot(context: Context, snapshot: ModuleSnapshot) {
        if (!canPostNotifications(context)) return
        ensureChannels(context)
        updateStatusNotification(context, snapshot)
        maybeNotifyEvents(context, snapshot)
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return true
    }

    private fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val statusChannel = NotificationChannel(
            CHANNEL_ID_STATUS,
            "Kitsunping Status",
            NotificationManager.IMPORTANCE_LOW
        )
        val eventsChannel = NotificationChannel(
            CHANNEL_ID_EVENTS,
            "Kitsunping Events",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val channelAvailableChannel = NotificationChannel(
            CHANNEL_ID_CHANNEL_AVAILABLE,
            "Better Wi-Fi Channel",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifications when a better Wi-Fi channel is available"
        }
        manager.createNotificationChannel(statusChannel)
        manager.createNotificationChannel(eventsChannel)
        manager.createNotificationChannel(channelAvailableChannel)
    }

    private fun updateStatusNotification(context: Context, snapshot: ModuleSnapshot) {
        val transport = snapshot.daemonState["transport"].orEmpty().ifBlank { "unknown" }
        val profile = snapshot.policyCurrent.ifBlank {
            snapshot.daemonState["profile"].orEmpty().ifBlank { "unknown" }
        }
        val score = snapshot.daemonState["composite_score"].orEmpty().ifBlank { "-" }

        val content = "Profile: $profile | Transport: $transport | Score: $score"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_STATUS)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Kitsunping")
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_STATUS, notification)
        }
    }

    private fun maybeNotifyEvents(context: Context, snapshot: ModuleSnapshot) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val currentProfile = snapshot.policyCurrent.ifBlank {
            snapshot.daemonState["profile"].orEmpty()
        }
        val lastProfile = prefs.getString("profile", "")
        if (currentProfile.isNotBlank() && currentProfile != lastProfile) {
            notifyEvent(context, "Profile changed", "New profile: $currentProfile")
            prefs.edit().putString("profile", currentProfile).apply()
        }

        val calibState = snapshot.policyEvent.calibrateState
        val lastCalib = prefs.getString("calib", "")
        if (calibState != lastCalib) {
            when {
                calibState == "running" -> notifyEvent(context, "Calibration", "Calibration iniciada")
                lastCalib == "running" -> notifyEvent(context, "Calibration", "Calibration finalizada")
            }
            prefs.edit().putString("calib", calibState).apply()
        }

        val event = snapshot.lastEvent.event.ifBlank { snapshot.policyEvent.event }
        val details = snapshot.lastEvent.details
        val eventTs = snapshot.lastEvent.ts
        val eventKey = "$event|$details|$eventTs"
        val lastEventKey = prefs.getString("last_event_key", "")

        if (event.isNotBlank() && eventKey != lastEventKey) {
            val mapped = mapEventNotification(event, details)
            if (mapped != null) {
                notifyEvent(context, mapped.first, mapped.second)
            }
            prefs.edit()
                .putString("last_event", event)
                .putString("last_event_key", eventKey)
                .apply()
        }
    }

    private fun mapEventNotification(event: String, details: String): Pair<String, String>? {
        val upper = event.uppercase()
        return when {
            upper == "WIFI_LEFT" || upper.contains("DISCONNECTED") || upper.contains("LOST") -> {
                "Conexion" to "Wi-Fi desconectado"
            }
            upper == "WIFI_JOINED" || upper.contains("RECONNECTED") -> {
                "Conexion" to "Wi-Fi reconectado"
            }
            upper == "ROUTER_DNI_CHANGED" -> {
                "Router" to "Router detected/changed (DNI updated)"
            }
            upper == "ROUTER_CAPS_DETECTED" -> {
                val summary = details.substringBefore(" ").ifBlank { "Router capabilities updated" }
                "Router" to summary
            }
            upper == "PROFILE_CHANGED" -> {
                "Profile" to "Network profile updated"
            }
            upper == "ROUTER_PAIRED" -> {
                "Router" to "Router paired"
            }
            upper == "ROUTER_UNPAIRED" -> {
                "Router" to "Router unpaired"
            }
            else -> null
        }
    }

    private fun notifyEvent(context: Context, title: String, text: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_EVENTS)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify((System.currentTimeMillis() % 100000).toInt(), notification)
        }
    }

    // M4: Show notification when better WiFi channel is available
    fun showChannelAvailableNotification(
        context: Context,
        recommendedChannel: String,
        currentChannel: String,
        scoreGap: Int,
        band: String
    ) {
        if (!canPostNotifications(context)) return
        ensureChannels(context)

        val bandDisplay = when (band) {
            "2g" -> "2.4 GHz"
            "5g" -> "5 GHz"
            else -> band
        }

        val title = "Better Wi-Fi channel available"
        val text = "Channel $recommendedChannel (improvement of +$scoreGap points) - $bandDisplay"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_CHANNEL_AVAILABLE)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(NOTIF_ID_CHANNEL_AVAILABLE, notification)
        }
    }
}
