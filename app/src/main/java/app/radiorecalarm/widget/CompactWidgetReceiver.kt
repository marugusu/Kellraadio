package app.radiorecalarm.widget

import androidx.glance.appwidget.GlanceAppWidget

class CompactWidgetReceiver : BaseRadioWidgetReceiver("CompactWidgetReceiver") {
    override val glanceAppWidget: GlanceAppWidget = CompactWidget()
}
