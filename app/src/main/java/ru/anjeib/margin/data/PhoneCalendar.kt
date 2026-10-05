package ru.anjeib.margin.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** A meeting from the phone calendar (read-only). */
data class CalEvent(val title: String, val start: LocalDateTime, val end: LocalDateTime, val allDay: Boolean, val location: String?)

object PhoneCalendar {
    fun allowed(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Events overlapping [from]..[days] ahead, sorted by start. Empty when not permitted. */
    fun events(ctx: Context, from: LocalDate = LocalDate.now(), days: Long = 7): List<CalEvent> {
        if (!allowed(ctx)) return emptyList()
        val zone = ZoneId.systemDefault()
        val begin = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = from.plusDays(days).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin); ContentUris.appendId(it, end)
        }.build()
        val cols = arrayOf(
            CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.STATUS,
        )
        return runCatching {
            ctx.contentResolver.query(uri, cols, null, null, CalendarContract.Instances.BEGIN)?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        if (c.getInt(5) == CalendarContract.Instances.STATUS_CANCELED) continue
                        val allDay = c.getInt(3) == 1
                        // All-day events are stored in UTC midnight.
                        val z = if (allDay) ZoneId.of("UTC") else zone
                        add(CalEvent(
                            title = c.getString(0)?.ifBlank { null } ?: "—",
                            start = LocalDateTime.ofInstant(Instant.ofEpochMilli(c.getLong(1)), z),
                            end = LocalDateTime.ofInstant(Instant.ofEpochMilli(c.getLong(2)), z),
                            allDay = allDay,
                            location = c.getString(4)?.ifBlank { null },
                        ))
                    }
                }
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    /** Lines for the AI context. */
    fun contextLines(ev: List<CalEvent>): String = ev.joinToString("\n") { e ->
        val day = e.start.toLocalDate()
        if (e.allDay) "- $day all day: ${e.title}"
        else "- $day ${e.start.toLocalTime().toString().take(5)}–${e.end.toLocalTime().toString().take(5)}: ${e.title}" + (e.location?.let { " ($it)" } ?: "")
    }
}
