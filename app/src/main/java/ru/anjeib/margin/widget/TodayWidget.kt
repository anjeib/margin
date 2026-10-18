package ru.anjeib.margin.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.anjeib.margin.ui.openWindow
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
import ru.anjeib.margin.data.dayMonthShort
import androidx.glance.text.TextAlign
import androidx.glance.text.FontStyle
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
        val due = data.listTasks().filter { !it.done && ((it.dueDate ?: it.startDate)?.let { d -> !d.isAfter(today) } == true || it.openWindow(today)) }
            // The day's order as in the feed (0.85): late first, then the matters with no hour, then by the hour.
            .sortedWith(compareBy({ (it.dueDate ?: it.startDate)?.isBefore(today) != true }, { it.time ?: "" }))
        val s = L.s
        val locale = if (L.lang == ru.anjeib.margin.i18n.Lang.Ru) Locale.forLanguageTag("ru") else Locale.ENGLISH
        val dayName = today.dayOfWeek.getDisplayName(DateStyle.FULL_STANDALONE, locale).replaceFirstChar { it.uppercase() }
        val dayDate = today.dayOfMonth.toString() + " " + today.month.getDisplayName(DateStyle.SHORT, locale).trimEnd('.')
        // «когда» grows with the phone's font size, as on the app's margin.
        val whenW = (60f * context.resources.configuration.fontScale.coerceIn(1f, 2f)).dp
        provideContent {
            // v8: «когда» left of a thin rule, as on the app's margin — the hour, or «с 8 окт» when late.
            Body(context, dayName, dayDate, due.take(6).map { t ->
                val late = (t.dueDate ?: t.startDate)?.isBefore(today) == true
                Triple(if (late) s.sinceWord + " " + (t.dueDate ?: t.startDate)!!.dayMonthShort() else t.time.orEmpty(), t.title, late)
            },
                (due.size - 6).coerceAtLeast(0), s.tileWrite, s.widgetFree, s.moreWord, whenW)
        }
    }

    @Composable
    private fun Body(ctx: Context, dayName: String, dayDate: String, rows: List<Triple<String, String, Boolean>>, more: Int, write: String, free: String, moreWord: String,
                     whenW: androidx.compose.ui.unit.Dp = 60.dp) {
        val bg = ColorProvider(day = Color(0xFFF6F3EE), night = Color(0xFF141210))
        val ink = ColorProvider(day = Color(0xFF1A1A1A), night = Color(0xFFEDE7DD))
        val inkSoft = ColorProvider(day = Color(0xCC1A1A1A), night = Color(0xCCEDE7DD))
        val graphite = ColorProvider(day = Color(0xFF6B665E), night = Color(0xFFADA596))
        val accent = ColorProvider(day = Color(0xFFC8643B), night = Color(0xFFD9774E))
        val chip = ColorProvider(day = Color(0xFFECE6DC), night = Color(0xFF2A2622))
        val card = ColorProvider(day = Color(0xFFFCFAF7), night = Color(0xFF1E1B18))
        // As the app now (0.87): the rule in ink, the voice button in sea; terracotta only for what is late.
        val rule = ColorProvider(day = Color(0x661A1A1A), night = Color(0x66EDE7DD))
        val sea = ColorProvider(day = Color(0xFF2B6E72), night = Color(0xFF4F9A9E))
        fun open(action: String?) = actionStartActivity(Intent(ctx, MainActivity::class.java).apply { if (action != null) setAction(action) }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        Column(GlanceModifier.fillMaxSize().background(bg).cornerRadius(20.dp).padding(14.dp)) {
            Row(GlanceModifier.fillMaxWidth().clickable(open(null)), verticalAlignment = Alignment.Bottom) {
                Text(dayName, style = TextStyle(color = ink, fontSize = 20.sp, fontFamily = FontFamily.Serif))
                Spacer(GlanceModifier.width(8.dp))
                Text(dayDate, style = TextStyle(color = graphite, fontSize = 13.sp))
            }
            Spacer(GlanceModifier.height(8.dp))
            // The day's matters on one panel, as on «Сегодня».
            Column(GlanceModifier.defaultWeight().fillMaxWidth().background(card).cornerRadius(14.dp).padding(vertical = 6.dp).clickable(open(null))) {
                if (rows.isEmpty()) Text(free, style = TextStyle(color = graphite, fontSize = 14.sp, fontStyle = FontStyle.Italic), modifier = GlanceModifier.padding(horizontal = 12.dp, vertical = 4.dp))
                rows.forEach { (whenText, line, late) ->
                    Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        // The same «когда» as on the app's margin: larger, medium, in ink a touch softer than the words.
                        Text(whenText, maxLines = 1, style = TextStyle(color = if (late) accent else inkSoft, fontSize = 13.5.sp,
                            textAlign = TextAlign.End), modifier = GlanceModifier.width(whenW))
                        Spacer(GlanceModifier.width(8.dp))
                        Box(GlanceModifier.width(1.dp).height(18.dp).background(rule)) {}
                        Spacer(GlanceModifier.width(10.dp))
                        Text(line, maxLines = 1, style = TextStyle(color = ink, fontSize = 14.sp))
                    }
                }
                if (more > 0) Text("$moreWord $more", style = TextStyle(color = graphite, fontSize = 12.sp, fontStyle = FontStyle.Italic), modifier = GlanceModifier.padding(start = whenW + 19.dp, top = 2.dp))
            }
            Row(GlanceModifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.defaultWeight().height(40.dp).background(card).cornerRadius(20.dp).padding(horizontal = 14.dp).clickable(open(MainActivity.ACTION_WRITE)),
                    contentAlignment = Alignment.CenterStart) {
                    Text(write + "…", style = TextStyle(color = graphite, fontSize = 14.sp))
                }
                Spacer(GlanceModifier.width(8.dp))
                Box(GlanceModifier.size(40.dp).background(sea).cornerRadius(20.dp).clickable(open(MainActivity.ACTION_DICTATE)), contentAlignment = Alignment.Center) {
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
