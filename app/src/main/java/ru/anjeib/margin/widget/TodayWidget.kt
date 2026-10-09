package ru.anjeib.margin.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.anjeib.margin.MainActivity
import ru.anjeib.margin.R
import ru.anjeib.margin.data.Prefs
import ru.anjeib.margin.data.Store
import ru.anjeib.margin.data.listTasks
import ru.anjeib.margin.i18n.L
import ru.anjeib.margin.i18n.resolveLang
import java.time.LocalDate
import java.time.format.TextStyle as DateStyle
import java.util.Locale

/**
 * «Сегодня» on the home screen, in the paper look: the day, up to six matters for today (overdue first, then by the hour),
 * and two ways in — «Записать» (the field with the keyboard up) and the microphone. A widget can't hold a text field;
 * a tap opens the app straight into it.
 */
class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        L.lang = resolveLang(Prefs.get(context).settings.value.lang)
        val data = Store.get(context).data.value
        val today = LocalDate.now()
        val due = data.listTasks().filter { !it.done && (it.dueDate ?: it.startDate)?.let { d -> !d.isAfter(today) } == true }
            .sortedWith(compareBy({ (it.dueDate ?: it.startDate)?.isBefore(today) != true }, { it.time ?: "99" }))
        val s = L.s
        val locale = if (L.lang == ru.anjeib.margin.i18n.Lang.Ru) Locale.forLanguageTag("ru") else Locale.ENGLISH
        val dayName = today.dayOfWeek.getDisplayName(DateStyle.FULL_STANDALONE, locale).replaceFirstChar { it.uppercase() }
        val dayDate = today.dayOfMonth.toString() + " " + today.month.getDisplayName(DateStyle.SHORT, locale).trimEnd('.')
        provideContent {
            Body(context, dayName, dayDate, due.take(6).map { (it.time?.let { t -> "$t  " } ?: "") + it.title to ((it.dueDate ?: it.startDate)?.isBefore(today) == true) },
                (due.size - 6).coerceAtLeast(0), s.tileWrite, s.widgetFree, s.moreWord)
        }
    }

    @Composable
    private fun Body(ctx: Context, dayName: String, dayDate: String, rows: List<Pair<String, Boolean>>, more: Int, write: String, free: String, moreWord: String) {
        val bg = ColorProvider(day = Color(0xFFF6F3EE), night = Color(0xFF141210))
        val ink = ColorProvider(day = Color(0xFF1A1A1A), night = Color(0xFFEDE7DD))
        val graphite = ColorProvider(day = Color(0xFF6B665E), night = Color(0xFFADA596))
        val accent = ColorProvider(day = Color(0xFFC8643B), night = Color(0xFFD9774E))
        val chip = ColorProvider(day = Color(0xFFECE6DC), night = Color(0xFF2A2622))
        fun open(action: String?) = actionStartActivity(Intent(ctx, MainActivity::class.java).apply { if (action != null) setAction(action) }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        Column(GlanceModifier.fillMaxSize().background(bg).cornerRadius(20.dp).padding(14.dp)) {
            Row(GlanceModifier.fillMaxWidth().clickable(open(null)), verticalAlignment = Alignment.Bottom) {
                Text(dayName, style = TextStyle(color = ink, fontSize = 20.sp, fontFamily = FontFamily.Serif))
                Spacer(GlanceModifier.width(8.dp))
                Text(dayDate, style = TextStyle(color = graphite, fontSize = 13.sp))
            }
            Spacer(GlanceModifier.height(8.dp))
            Column(GlanceModifier.defaultWeight().fillMaxWidth().clickable(open(null))) {
                if (rows.isEmpty()) Text(free, style = TextStyle(color = graphite, fontSize = 14.sp))
                rows.forEach { (line, late) ->
                    Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        // A thin terracotta margin rule, as in the app; late matters marked by its colour.
                        Box(GlanceModifier.width(2.dp).height(16.dp).background(if (late) accent else chip)) {}
                        Spacer(GlanceModifier.width(8.dp))
                        Text(line, maxLines = 1, style = TextStyle(color = ink, fontSize = 14.sp))
                    }
                }
                if (more > 0) Text("$moreWord $more", style = TextStyle(color = graphite, fontSize = 12.sp), modifier = GlanceModifier.padding(start = 10.dp, top = 2.dp))
            }
            Row(GlanceModifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.defaultWeight().height(40.dp).background(chip).cornerRadius(20.dp).padding(horizontal = 14.dp).clickable(open(MainActivity.ACTION_WRITE)),
                    contentAlignment = Alignment.CenterStart) {
                    Text(write + "…", style = TextStyle(color = graphite, fontSize = 14.sp))
                }
                Spacer(GlanceModifier.width(8.dp))
                Box(GlanceModifier.size(40.dp).background(accent).cornerRadius(20.dp).clickable(open(MainActivity.ACTION_DICTATE)), contentAlignment = Alignment.Center) {
                    Image(ImageProvider(R.drawable.ic_mic_white), contentDescription = null, modifier = GlanceModifier.size(20.dp))
                }
            }
        }
    }

    companion object {
        private var pending: Job? = null
        /** Called after every save: the widget follows the records, at most a couple of times a second. */
        fun refresh(ctx: Context) {
            val app = ctx.applicationContext
            pending?.cancel()
            pending = CoroutineScope(Dispatchers.Default).launch {
                delay(400)
                runCatching { if (GlanceAppWidgetManager(app).getGlanceIds(TodayWidget::class.java).isNotEmpty()) TodayWidget().updateAll(app) }
            }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
