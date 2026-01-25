package ee.minu.kellraadio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper

@OptIn(UnstableApi::class)
class RadioNotificationManager(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID_RADIO = "PLAYING_RADIO_CHANNEL_v21"
        const val CHANNEL_ID_ALARM = "ALARM_ALERT_CHANNEL_v21"
        const val NOTIFICATION_ID = 1
        const val ALARM_NOTIFICATION_ID = 100
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val radioChannel = NotificationChannel(
                CHANNEL_ID_RADIO,
                "Raadio",
                NotificationManager.IMPORTANCE_LOW
            )
            val alarmChannel = NotificationChannel(
                CHANNEL_ID_ALARM,
                "Äratuse märguanne", // Võib olla ka string resource, aga see on kanali nimi süsteemis
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannels(listOf(radioChannel, alarmChannel))
        }
    }

    fun buildNotification(
        mediaSession: MediaSession,
        stationName: String,
        trackTitle: String,
        trackArtist: String,
        bitmap: Bitmap?,
        isAlarmMode: Boolean
    ): Notification {

        // Peatamise nupp (PendingIntent)
        val stopPendingIntent = PendingIntent.getService(
            context, 2,
            Intent(context, RadioService::class.java).apply { action = RadioService.ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        // Tekstide loogika (Täpselt sama, mis sul enne oli)
        val title = if (isAlarmMode) context.getString(R.string.notification_alarm)
        else (if (trackTitle.isNotBlank()) trackTitle else stationName)

        val text = if (isAlarmMode) context.getString(R.string.notification_playing, stationName)
        else (if (trackArtist.isNotBlank()) trackArtist else stationName)

        val priority = if (isAlarmMode) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW
        val icon = if (isAlarmMode) android.R.drawable.ic_lock_idle_alarm else R.drawable.ic_radio_notification

        return NotificationCompat.Builder(context, CHANNEL_ID_RADIO)
            .setSmallIcon(icon)
            .setLargeIcon(bitmap)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(priority)
            .setDefaults(if (isAlarmMode) Notification.DEFAULT_ALL else 0)
            .setContentIntent(mediaSession.sessionActivity)
            // See rida ühendab teavituse MediaSessioniga (nupud lukuekraanil jne)
            .setStyle(MediaStyleNotificationHelper.MediaStyle(mediaSession).setShowActionsInCompactView(0, 1, 2))

            // Nupud
            .addAction(R.drawable.ic_skip_previous, "Previous", null)
            .addAction(R.drawable.ic_stop, context.getString(R.string.action_stop), stopPendingIntent)
            .addAction(R.drawable.ic_skip_next, "Next", null)

            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    fun createAlarmNotification(stationName: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, CHANNEL_ID_ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.notification_alarm))
            .setContentText(context.getString(R.string.notification_playing, stationName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)
            .build()
    }

    fun notify(id: Int, notification: Notification) {
        notificationManager.notify(id, notification)
    }

    fun cancel(id: Int) {
        notificationManager.cancel(id)
    }
}