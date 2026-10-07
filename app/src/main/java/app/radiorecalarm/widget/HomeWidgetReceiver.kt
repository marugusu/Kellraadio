package app.radiorecalarm.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HomeWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                HomeWidgetUpdater.update(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: android.appwidget.AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                HomeWidgetUpdater.update(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
