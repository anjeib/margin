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
import androidx.glance.layout.fillMaxHeight
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
import ru.anjeib.margin.book.BookStore
import ru.anjeib.margin.book.Kind
import ru.anjeib.margin.book.lastDay
import ru.anjeib.margin.book.page
import ru.anjeib.margin.data.Prefs
import ru.anjeib.margin.i18n.Lang
import ru.anjeib.margin.i18n.resolveLang
import ru.anjeib.margin.paper.Words
import ru.anjeib.margin.paper.WordsEn
import ru.anjeib.margin.paper.WordsRu
import java.time.LocalDate

/**
 * Today's page on the home screen, in miniature: the weekday and the date, the open lines with their notes on a little
 * margin and their marks on the rule (tails first, in red ink), and two ways in — the field and the microphone. A widget
 * can't hold a text field; a tap opens the notebook straight into it. The numbers come from the same page function.
 */
class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val w = if (resolveLang(Prefs.get(context).settings.value.lang) == Lang.Ru) WordsRu else WordsEn
        val book = BookStore.get(context).book.value
        val today = LocalDate.now()
        val page = book.page(today, today)
        val rows = (page.tails.map { it to true } + (page.timed + page.rest).filter { (it.isTodo && it.open) || it.kind == Kind.Event }.map { it to false })
            .map { (l, tail) ->
                Row(note = if (tail) w.tailSince(l.lastDay() ?: today) else (l.time ?: l.part?.let { w.part(it) } ?: ""),
                    mark = when (l.kind) { Kind.Event -> "○"; Kind.Note -> "–"; else -> "•" }, text = l.text, tail = tail)
            }
        provideContent { Body(context, w.weekday(today), w.dayMonth(today), rows.take(6), (rows.size - 6).coerceAtLeast(0), w) }
    }

    private data class Row(val note: String, val mark: String, val text: String, val tail: Boolean)

    @Composable
    private fun Body(ctx: Context, dayName: String, dayDate: String, rows: List<Row>, more: Int, w: Words) {
        val paper = ColorProvider(day = Color(0xFFF6F3EE), night = Color(0xFF141210))
        val ink = ColorProvider(day = Color(0xFF1A1A1A), night = Color(0xFFEDE7DD))
        val graphite = ColorProvider(day = Color(0xFF6B665E), night = Color(0xFFADA596))
        val red = ColorProvider(day = Color(0xFFA9502C), night = Color(0xFFE08A62))
        val redFill = ColorProvider(day = Color(0xFFC8643B), night = Color(0xFFD9774E))
        val rule = ColorProvider(day = Color(0xFFDFAC94), night = Color(0xFF76442F))
        val sheet = ColorProvider(day = Color(0xFFFCFAF7), night = Color(0xFF1E1B18))
        fun open(action: String?) = actionStartActivity(Intent(ctx, MainActivity::class.java).apply { if (action != null) setAction(action) }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        val margin = 54.dp
        Column(GlanceModifier.fillMaxSize().background(paper).cornerRadius(20.dp).padding(vertical = 12.dp)) {
            Row(GlanceModifier.fillMaxWidth().clickable(open(null)), verticalAlignment = Alignment.Bottom) {
                Box(GlanceModifier.width(margin).padding(end = 8.dp), contentAlignment = Alignment.BottomEnd) {
                    Text(dayDate, style = TextStyle(color = graphite, fontSize = 12.sp))
                }
                Box(GlanceModifier.width(1.dp).height(26.dp).background(rule)) {}
                Spacer(GlanceModifier.width(10.dp))
                Text(dayName, style = TextStyle(color = ink, fontSize = 20.sp, fontFamily = FontFamily.Serif))
            }
            Column(GlanceModifier.defaultWeight().fillMaxWidth().clickable(open(null))) {
                if (rows.isEmpty()) Row(GlanceModifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(GlanceModifier.width(margin))
                    Box(GlanceModifier.width(1.dp).fillMaxHeight().background(rule)) {}
                    Spacer(GlanceModifier.width(10.dp))
                    Text(w.emptyDay, style = TextStyle(color = graphite, fontSize = 14.sp, fontStyle = androidx.glance.text.FontStyle.Italic, fontFamily = FontFamily.Serif))
                }
                rows.forEach { r ->
                    Row(GlanceModifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
                        // The margin: the hour, or since when a tail has waited — in red.
                        Box(GlanceModifier.width(margin).padding(end = 6.dp), contentAlignment = Alignment.CenterEnd) {
                            Text(r.note, maxLines = 1, style = TextStyle(color = if (r.tail) red else graphite, fontSize = 11.sp,
                                fontStyle = androidx.glance.text.FontStyle.Italic, fontFamily = FontFamily.Serif))
                        }
                        Box(GlanceModifier.width(1.dp).fillMaxHeight().background(rule)) {}
                        Spacer(GlanceModifier.width(5.dp))
                        Text(r.mark, style = TextStyle(color = ink, fontSize = 12.sp))
                        Spacer(GlanceModifier.width(6.dp))
                        Text(r.text, maxLines = 1, style = TextStyle(color = ink, fontSize = 14.sp))
                    }
                }
                if (more > 0) Row(GlanceModifier.fillMaxWidth().height(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(GlanceModifier.width(margin))
                    Box(GlanceModifier.width(1.dp).fillMaxHeight().background(rule)) {}
                    Spacer(GlanceModifier.width(10.dp))
                    Text(w.more(more), style = TextStyle(color = graphite, fontSize = 12.sp, fontStyle = androidx.glance.text.FontStyle.Italic, fontFamily = FontFamily.Serif))
                }
            }
            Row(GlanceModifier.fillMaxWidth().padding(start = margin - 4.dp, end = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.defaultWeight().height(40.dp).background(sheet).cornerRadius(20.dp).padding(horizontal = 14.dp).clickable(open(MainActivity.ACTION_WRITE)),
                    contentAlignment = Alignment.CenterStart) {
                    Text(w.composerHint, maxLines = 1, style = TextStyle(color = graphite, fontSize = 14.sp))
                }
                Spacer(GlanceModifier.width(8.dp))
                Box(GlanceModifier.size(40.dp).background(redFill).cornerRadius(20.dp).clickable(open(MainActivity.ACTION_DICTATE)), contentAlignment = Alignment.Center) {
                    Image(ImageProvider(R.drawable.ic_mic_white), contentDescription = w.a11yMic, modifier = GlanceModifier.size(20.dp))
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
