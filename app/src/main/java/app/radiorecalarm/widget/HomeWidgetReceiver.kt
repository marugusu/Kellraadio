package app.radiorecalarm.widget

import androidx.glance.appwidget.GlanceAppWidget

class HomeWidgetReceiver : BaseRadioWidgetReceiver("HomeWidgetReceiver") {
    override val glanceAppWidget: GlanceAppWidget = HomeWidget()
}
