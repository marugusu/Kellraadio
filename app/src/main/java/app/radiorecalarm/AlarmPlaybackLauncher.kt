package app.radiorecalarm

import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Õmblus (seam) raadioteenuse käivitamiseks äratuse režiimis.
 */
interface AlarmPlaybackLauncher {
    fun launchPlaybackService(streamUrl: String, stationName: String)
}

class AndroidAlarmPlaybackLauncher(private val context: Context) : AlarmPlaybackLauncher {
    override fun launchPlaybackService(streamUrl: String, stationName: String) {
        val serviceIntent = Intent(context, RadioService::class.java).apply {
            putExtra("STREAM_URL", streamUrl)
            putExtra("STATION_NAME", stationName)
            putExtra("TRIGGERED_BY", "ALARM")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
