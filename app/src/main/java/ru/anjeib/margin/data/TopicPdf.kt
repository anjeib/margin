package ru.anjeib.margin.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import ru.anjeib.margin.R
import ru.anjeib.margin.i18n.L
import java.io.File
import java.time.LocalDate

/**
 * A topic as a PDF for someone else — a client, a colleague — made on the phone, nothing sent anywhere:
 * the title, since when and how far, then who owes what, what is next, what is done, the records and the files,
 * set like the app itself: the margin rule, days in italic on the margin, the words right of it.
 */
object TopicPdf {
    private const val W = 595 // A4 in points
    private const val H = 842
    private const val RULE = 92f
    private const val TEXT = 108f
    private const val RIGHT = 555f
    private const val TOP = 56f
    private const val BOTTOM = 790f

    private val ink = Color.rgb(0x1A, 0x1A, 0x1A)
    private val graphite = Color.rgb(0x6B, 0x66, 0x5E)
    private val faint = Color.rgb(0x9A, 0x93, 0x88)
    private val link = Color.rgb(0x1C, 0x64, 0x80)
    private val rule = Color.argb(0xD9, 0xC8, 0x64, 0x3B)
    private val paper = Color.rgb(0xF6, 0xF3, 0xEE)

    private fun flat(l: List<Subtask>): List<Subtask> = l.flatMap { listOf(it) + flat(it.children) }

    fun make(ctx: Context, t: Task): File {
        val s = L.s
        val today = LocalDate.now()
        val serif = runCatching { ResourcesCompat.getFont(ctx, R.font.playfair) }.getOrNull() ?: Typeface.SERIF
        val italic = runCatching { ResourcesCompat.getFont(ctx, R.font.playfair_italic) }.getOrNull() ?: Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        val sans = runCatching { ResourcesCompat.getFont(ctx, R.font.inter) }.getOrNull() ?: Typeface.SANS_SERIF

        fun paint(face: Typeface, size: Float, color: Int, spacing: Float = 0f) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face; textSize = size; this.color = color; letterSpacing = spacing
        }
        val pTitle = paint(serif, 24f, ink)
        val pVoice = paint(italic, 11.5f, graphite)
        val pLabel = paint(sans, 7.5f, graphite, 0.18f)
        val pBody = paint(sans, 11f, ink)
        val pSub = paint(sans, 9f, graphite)
        val pMargin = paint(italic, 10f, graphite)
        val pFoot = paint(italic, 8.5f, faint)

        val doc = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        lateinit var c: Canvas
        var y = TOP

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            c = page!!.canvas
            c.drawColor(paper)
            c.drawLine(RULE, 0f, RULE, H.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rule; strokeWidth = 1f })
            c.drawText("Margin · " + today.dayMonthYear(today) + "  ·  " + pageNo, TEXT, H - 28f, pFoot)
            y = TOP
        }
        fun layout(text: CharSequence, p: TextPaint, width: Float, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL) =
            StaticLayout.Builder.obtain(text, 0, text.length, p, width.toInt()).setAlignment(align).setLineSpacing(0f, 1.18f).build()
        fun need(h: Float) { if (y + h > BOTTOM) newPage() }
        /** The words right of the rule, «когда» on the margin on the line of their first words, a quiet line under them. */
        fun row(margin: String?, mark: String?, words: String, sub: String? = null, marginColor: Int = graphite, done: Boolean = false) {
            val body = layout(words, if (done) TextPaint(pBody).apply { color = graphite; isStrikeThruText = true } else pBody, RIGHT - TEXT - 16f)
            val under = sub?.let { layout(it, pSub, RIGHT - TEXT - 16f) }
            val m = margin?.let { layout(it, TextPaint(pMargin).apply { color = marginColor }, RULE - 14f, Layout.Alignment.ALIGN_OPPOSITE) }
            // A day and an hour on the margin can be taller than one line of words: the row takes the taller of the two.
            val words0 = body.height + (under?.height?.plus(2) ?: 0)
            val h = maxOf(words0, m?.height ?: 0) + 10f
            need(h)
            m?.let { c.save(); c.translate(6f, y + 1f); it.draw(c); c.restore() }
            val top = y
            mark?.let { c.drawText(it, TEXT - 2f, y + pBody.textSize, TextPaint(pBody).apply { color = if (done) link else graphite }) }
            c.save(); c.translate(TEXT + 14f, y); body.draw(c); c.restore()
            y += body.height
            under?.let { c.save(); c.translate(TEXT + 14f, y + 2f); it.draw(c); c.restore(); y += it.height + 2 }
            y = maxOf(y, top + (m?.height ?: 0)) + 10f
        }
        fun section(label: String) {
            need(40f)
            y += 14f
            c.drawText(label.trimEnd(':').uppercase(), TEXT, y + pLabel.textSize, pLabel)
            y += pLabel.textSize + 10f
        }
        fun day(at: String?) = at?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
        // The page is read later and by someone else: plain dates, never «сегодня» or a late day in red.
        fun date(d: LocalDate) = d.dayMonthYear(today)

        newPage()
        // The head: the title, then in Margin's voice since when and how far.
        val title = layout(t.title, pTitle, RIGHT - TEXT)
        c.save(); c.translate(TEXT, y); title.draw(c); c.restore()
        y += title.height + 6f
        val all = flat(t.subtasks)
        val since = day(t.createdAt)?.let { if (t.thought && t.subtasks.isEmpty()) s.thoughtSince(it.dayMonthYear(today)) else s.topicSince(it.dayMonthYear(today)) }
        val voice = listOfNotNull(since, all.size.takeIf { it > 0 }?.let { s.progressSolved(all.count { it.done }, it) },
            t.sphere?.let { if (it == "work") s.sphereWork else s.spherePersonal }).joinToString("  ·  ")
        if (voice.isNotBlank()) { val v = layout(voice, pVoice, RIGHT - TEXT); c.save(); c.translate(TEXT, y); v.draw(c); c.restore(); y += v.height + 8f }

        val waits = all.filter { !it.done && it.from != null }.sortedBy { it.due ?: "9999" }
        if (waits.isNotEmpty()) {
            section(s.exportWaiting)
            waits.forEach { q -> row(day(q.due)?.let { date(it) }, "←", q.title, s.promiseToYou(q.from!!)) }
        }
        val open = all.filter { !it.done && it.from == null }.sortedBy { it.due ?: "9999" }
        if (open.isNotEmpty()) {
            section(s.exportOpen)
            open.forEach { q -> row(day(q.due)?.let { date(it) + (q.time?.let { tm -> "\n$tm" } ?: "") }, "○", q.title, q.assignee) }
        }
        val done = all.filter { it.done }.sortedBy { it.doneAt ?: "" }
        if (done.isNotEmpty()) {
            section(s.exportDone)
            done.forEach { q -> row(day(q.doneAt)?.dayMonthShort(), "●", q.title, done = true) }
        }
        val records = t.entries.filter { it.text.isNotBlank() || it.items.isNotEmpty() }
        if (records.isNotEmpty()) {
            section(s.records)
            records.forEach { e ->
                val words = (listOf(e.text.trim()) + e.items.map { "• " + it.title }).filter { it.isNotBlank() }.joinToString("\n")
                row(day(e.at)?.dayMonthShort(), null, words, e.attachments.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.caption ?: it.name })
            }
        }
        val files = t.entries.flatMap { e -> e.attachments.map { e to it } }
        if (files.isNotEmpty()) {
            section(s.filesWord)
            files.forEach { (e, a) -> row(day(e.at)?.dayMonthShort(), null, a.caption ?: a.name.ifBlank { a.kind }, a.caption?.let { a.name }) }
        }
        page?.let { doc.finishPage(it) }

        val dir = File(ctx.cacheDir, "export").apply { mkdirs() }
        val name = t.title.replace(Regex("[\\\\/:*?\"<>|«»]"), "").trim().take(60).ifBlank { "Margin" }
        val file = File(dir, "$name.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
