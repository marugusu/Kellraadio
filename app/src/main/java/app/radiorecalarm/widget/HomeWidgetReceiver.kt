package app.radiorecalarm.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HomeWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        launchWidgetUpdate(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: android.appwidget.AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        launchWidgetUpdate(context)
    }

    internal fun launchWidgetUpdate(
        context: Context,
        updateBlock: suspend (Context) -> Unit = { HomeWidgetUpdater.update(it) }
    ) {
        // GlanceAppWidgetReceiver.onUpdate() may already claim goAsync(), causing a second goAsync() to return null.
        val pendingResult: PendingResult? = goAsync()
        val appContext = context.applicationContext ?: context
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateBlock(appContext)
            } catch (e: Exception) {
                Log.e("HomeWidgetReceiver", "Vidina uuendamise viga: ${e.message}", e)
            } finally {
                pendingResult?.finish()
            }
        }
    }
}
