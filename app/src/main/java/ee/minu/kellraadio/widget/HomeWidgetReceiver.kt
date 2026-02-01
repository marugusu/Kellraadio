package ee.minu.kellraadio.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import ee.minu.kellraadio.RadioService

class HomeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HomeWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // Kui vidin lisatakse, küsime kohe Service'ilt hetkeseisu
        val intent = Intent(context, RadioService::class.java).apply {
            action = RadioService.ACTION_FORCE_WIDGET_UPDATE
        }
        try {
            // Kasutame startService, sest see on taustakäsk olemasolevale teenusele
            context.startService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Lisame ka onUpdate, et olla kindel (mõnikord onEnabled ei piisa)
    override fun onUpdate(context: Context, appWidgetManager: android.appwidget.AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val intent = Intent(context, RadioService::class.java).apply {
            action = RadioService.ACTION_FORCE_WIDGET_UPDATE
        }
        try {
            context.startService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}