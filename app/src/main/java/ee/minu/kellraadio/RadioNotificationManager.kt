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
                "Äratuse märguanne",
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
        isAlarmMode: Boolean,
        isPlaying: Boolean
    ): Notification {

        // 1. Luua Intentid nuppude jaoks
        val stopIntent = Intent(context, RadioService::class.java).apply { action = RadioService.ACTION_STOP }
        val pauseIntent = Intent(context, RadioService::class.java).apply { action = RadioService.ACTION_PAUSE }
        val playIntent = Intent(context, RadioService::class.java).apply { action = RadioService.ACTION_RESUME }
        val nextIntent = Intent(context, RadioService::class.java).apply { action = RadioService.ACTION_SKIP_NEXT }
        val prevIntent = Intent(context, RadioService::class.java).apply { action = RadioService.ACTION_SKIP_PREVIOUS }

        // 2. Teha neist PendingIntentid
        val stopPending = PendingIntent.getService(context, 10, stopIntent, PendingIntent.FLAG_IMMUTABLE)
        val pausePending = PendingIntent.getService(context, 11, pauseIntent, PendingIntent.FLAG_IMMUTABLE)
        val playPending = PendingIntent.getService(context, 12, playIntent, PendingIntent.FLAG_IMMUTABLE)
        val nextPending = PendingIntent.getService(context, 13, nextIntent, PendingIntent.FLAG_IMMUTABLE)
        val prevPending = PendingIntent.getService(context, 14, prevIntent, PendingIntent.FLAG_IMMUTABLE)

        // 3. Keskmise nupu loogika (Play vs Pause)
        val middleActionIcon: Int
        val middleActionTitle: String
        val middleActionIntent: PendingIntent

        if (isAlarmMode) {
            // Äratuse ajal on alati STOP
            middleActionIcon = R.drawable.ic_stop
            middleActionTitle = context.getString(R.string.action_stop)
            middleActionIntent = stopPending
        } else if (isPlaying) {
            // Kui mängib -> PAUS (Kasutame süsteemset ikooni, sest ic_pause puudub projektis)
            middleActionIcon = R.drawable.ic_pause
            middleActionTitle = context.getString(R.string.action_pause)
            middleActionIntent = pausePending
        } else {
            // Kui ei mängi -> MÄNGI
            middleActionIcon = R.drawable.ic_play_arrow
            middleActionTitle = context.getString(R.string.action_play)
            middleActionIntent = playPending
        }

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
            .setOngoing(isPlaying || isAlarmMode)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(priority)
            .setDefaults(if (isAlarmMode) Notification.DEFAULT_ALL else 0)
            .setContentIntent(mediaSession.sessionActivity)
            .setStyle(MediaStyleNotificationHelper.MediaStyle(mediaSession).setShowActionsInCompactView(0, 1, 2))

            // 4. Nupud koos Intentidega
            .addAction(R.drawable.ic_skip_previous, "Previous", prevPending)
            .addAction(middleActionIcon, middleActionTitle, middleActionIntent)
            .addAction(R.drawable.ic_skip_next, "Next", nextPending)

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