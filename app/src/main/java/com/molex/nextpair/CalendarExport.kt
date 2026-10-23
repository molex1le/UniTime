package com.molex.nextpair

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.CalendarContract
import java.time.LocalDate
import java.time.ZoneId

/** Пары в календарь телефона (обычно это Google Календарь, он сам синхронизирует). */
object CalendarExport {
    private const val MARK = "[NextPair]"

    /** Календарь для записи: основной Google-аккаунт, иначе любой доступный на запись. */
    private fun calendarId(ctx: Context): Long? {
        val c = CalendarContract.Calendars.CONTENT_URI
        val cols = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        ctx.contentResolver.query(c, cols, "${CalendarContract.Calendars.VISIBLE} = 1", null, null)?.use { cur ->
            var fallback: Long? = null
            while (cur.moveToNext()) {
                val id = cur.getLong(0)
                val writable = cur.getInt(3) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
                if (!writable) continue
                if (cur.getString(1) == "com.google" && cur.getInt(2) == 1) return id
                if (fallback == null) fallback = id
            }
            return fallback
        }
        return null
    }

    /** Удаляет всё, что добавили раньше. Возвращает число удалённых событий. */
    fun remove(ctx: Context): Int = ctx.contentResolver.delete(
        CalendarContract.Events.CONTENT_URI,
        "${CalendarContract.Events.DESCRIPTION} LIKE ?",
        arrayOf("%$MARK%"),
    )

    /** Перезаписывает пары с сегодняшнего дня до конца семестра и экзамены. Возвращает число событий. */
    fun export(ctx: Context, s: Schedule, subgroup: Int): Int {
        val cal = calendarId(ctx) ?: error("Нет календаря, доступного для записи")
        remove(ctx)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val end = s.termEnd ?: today.plusWeeks(16)
        val all = generateSequence(today) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }
            .flatMap { s.lessonsOn(it, subgroup) }.toList() + s.exams.filter { !it.date.isBefore(today) }
        val ops = ArrayList<ContentProviderOperation>()
        for (o in all) {
            val l = o.lesson
            ops += ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
                .withValue(CalendarContract.Events.CALENDAR_ID, cal)
                .withValue(CalendarContract.Events.TITLE, if (l.type.isBlank()) l.subjectFull.ifBlank { l.subject } else "${l.subjectFull.ifBlank { l.subject }} (${l.type})")
                .withValue(CalendarContract.Events.EVENT_LOCATION, l.rooms)
                .withValue(CalendarContract.Events.DESCRIPTION, listOf(l.teacher, if (l.subgroup != 0) "Подгруппа ${l.subgroup}" else "", MARK).filter { it.isNotBlank() }.joinToString("\n"))
                .withValue(CalendarContract.Events.DTSTART, o.startAt.atZone(zone).toInstant().toEpochMilli())
                .withValue(CalendarContract.Events.DTEND, o.endAt.atZone(zone).toInstant().toEpochMilli())
                .withValue(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
                .build()
        }
        // Пачками, чтобы не упереться в лимит транзакции.
        ops.chunked(200).forEach { ctx.contentResolver.applyBatch(CalendarContract.AUTHORITY, ArrayList(it)) }
        return ops.size
    }
}
