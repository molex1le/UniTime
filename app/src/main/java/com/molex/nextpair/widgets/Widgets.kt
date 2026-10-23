package com.molex.nextpair.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.molex.nextpair.Deadline
import com.molex.nextpair.MainActivity
import com.molex.nextpair.R
import com.molex.nextpair.Repo
import com.molex.nextpair.Schedule
import com.molex.nextpair.Updater
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Все виджеты обновляются через Updater.refresh — тот же цикл, что и уведомление. */
abstract class BaseWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Updater.refresh(ctx) { pending.finish() }
    }
}

class SmallWidget : BaseWidget()
class DayWidget : BaseWidget()
class DeadlinesWidget : BaseWidget()
class ExamsWidget : BaseWidget()

object Widgets {
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")
    private val RU = Locale("ru")
    private val MONTHS = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")

    private class Colors(dark: Boolean) {
        val ink = if (dark) 0xFFEDEFF3.toInt() else 0xFF11151C.toInt()
        val muted = if (dark) 0xFFA3ABB8.toInt() else 0xFF5B6472.toInt()
        val accent = if (dark) 0xFF8EA2FF.toInt() else 0xFF2F4FD8.toInt()
        val bg = if (dark) R.drawable.widget_bg_dark else R.drawable.widget_bg
    }

    private fun ids(ctx: Context, cls: Class<*>) =
        AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, cls))

    private fun base(ctx: Context, layout: Int, c: Colors): RemoteViews {
        val v = RemoteViews(ctx.packageName, layout)
        v.setInt(R.id.w_root, "setBackgroundResource", c.bg)
        v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        return v
    }

    private fun minutes(m: Long) = if (m >= 60) "${m / 60} ч ${m % 60} мин".replace(" 0 мин", "") else "$m мин"

    fun renderAll(ctx: Context, s: Schedule?, now: LocalDateTime, subgroup: Int, dark: Boolean) {
        val c = Colors(dark)
        val mgr = AppWidgetManager.getInstance(ctx)
        ids(ctx, SmallWidget::class.java).takeIf { it.isNotEmpty() }?.let { mgr.updateAppWidget(it, small(ctx, s, now, subgroup, c)) }
        ids(ctx, DayWidget::class.java).takeIf { it.isNotEmpty() }?.let { mgr.updateAppWidget(it, day(ctx, s, now, subgroup, c)) }
        ids(ctx, DeadlinesWidget::class.java).takeIf { it.isNotEmpty() }?.let { mgr.updateAppWidget(it, deadlines(ctx, Repo(ctx).deadlines(), now, c)) }
        ids(ctx, ExamsWidget::class.java).takeIf { it.isNotEmpty() }?.let { mgr.updateAppWidget(it, exams(ctx, s, now, c)) }
    }

    private fun small(ctx: Context, s: Schedule?, now: LocalDateTime, sub: Int, c: Colors): RemoteViews {
        val v = base(ctx, R.layout.widget_small, c)
        val o = s?.upcoming(now, sub, 1)?.firstOrNull()
        v.setTextColor(R.id.w_time, c.ink); v.setTextColor(R.id.w_room, c.accent); v.setTextColor(R.id.w_name, c.muted)
        if (o == null) {
            v.setTextViewText(R.id.w_time, "—"); v.setTextViewText(R.id.w_room, ""); v.setTextViewText(R.id.w_name, if (s == null) "Нет расписания" else "Пар нет")
            return v
        }
        val ongoing = !o.startAt.isAfter(now)
        v.setTextViewText(R.id.w_time, if (ongoing) "до ${o.lesson.end.format(TIME)}" else o.lesson.start.format(TIME))
        v.setTextViewText(R.id.w_room, o.lesson.rooms.removeSuffix(" к.").ifBlank { "" })
        val whenTxt = when {
            ongoing -> "сейчас"
            o.date == now.toLocalDate() -> "через ${minutes(Duration.between(now, o.startAt).toMinutes() + 1)}"
            o.date == now.toLocalDate().plusDays(1) -> "завтра"
            else -> o.date.dayOfWeek.getDisplayName(TextStyle.SHORT, RU)
        }
        v.setTextViewText(R.id.w_name, "${o.lesson.subject} ${o.lesson.type} · $whenTxt".replace("  ", " "))
        return v
    }

    private val ROWS = listOf(R.id.row0, R.id.row1, R.id.row2, R.id.row3, R.id.row4, R.id.row5)
    private val T = listOf(R.id.t0, R.id.t1, R.id.t2, R.id.t3, R.id.t4, R.id.t5)
    private val N = listOf(R.id.n0, R.id.n1, R.id.n2, R.id.n3, R.id.n4, R.id.n5)
    private val R_ = listOf(R.id.r0, R.id.r1, R.id.r2, R.id.r3, R.id.r4, R.id.r5)

    private fun day(ctx: Context, s: Schedule?, now: LocalDateTime, sub: Int, c: Colors): RemoteViews {
        val v = base(ctx, R.layout.widget_day, c)
        v.setTextColor(R.id.w_head, c.ink); v.setTextColor(R.id.w_count, c.accent); v.setTextColor(R.id.w_empty, c.muted)
        val today = now.toLocalDate()
        // Сегодня, пока есть пары; вечером — ближайший день с парами.
        val (date, list) = (0..7).asSequence().map { today.plusDays(it.toLong()) }
            .map { d -> d to (s?.lessonsOn(d, sub).orEmpty().filter { d != today || it.endAt.isAfter(now) }) }
            .firstOrNull { it.second.isNotEmpty() } ?: (today to emptyList())
        val label = when (date) {
            today -> "Сегодня"
            today.plusDays(1) -> "Завтра"
            else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, RU).replaceFirstChar { it.uppercase() }
        }
        v.setTextViewText(R.id.w_head, "$label · ${date.dayOfMonth} ${MONTHS[date.monthValue - 1]}" + (s?.let { " · ${it.weekNumber(date)} нед." } ?: ""))
        v.setTextViewText(R.id.w_count, if (list.isNotEmpty()) "${list.size} пар" .let { if (list.size in 2..4) "${list.size} пары" else if (list.size == 1) "1 пара" else it } else "")
        v.setViewVisibility(R.id.w_empty, if (list.isEmpty()) View.VISIBLE else View.GONE)
        v.setTextViewText(R.id.w_empty, if (s == null) "Откройте приложение, чтобы загрузить расписание" else "На неделю вперёд пар нет")
        ROWS.forEachIndexed { i, row ->
            val o = list.getOrNull(i)
            v.setViewVisibility(row, if (o == null) View.GONE else View.VISIBLE)
            if (o != null) {
                val ongoing = !o.startAt.isAfter(now) && o.endAt.isAfter(now)
                v.setTextViewText(T[i], o.lesson.start.format(TIME))
                v.setTextViewText(N[i], "${o.lesson.subject} ${o.lesson.type}".trim())
                v.setTextViewText(R_[i], o.lesson.rooms.removeSuffix(" к."))
                v.setTextColor(T[i], if (ongoing) c.accent else c.ink)
                v.setTextColor(N[i], if (ongoing) c.accent else c.ink)
                v.setTextColor(R_[i], c.muted)
            }
        }
        return v
    }

    private fun deadlines(ctx: Context, all: List<Deadline>, now: LocalDateTime, c: Colors): RemoteViews {
        val v = base(ctx, R.layout.widget_deadlines, c)
        v.setTextColor(R.id.w_head, c.ink); v.setTextColor(R.id.w_count, c.accent); v.setTextColor(R.id.w_empty, c.muted)
        val open = all.filterNot { it.done }.sortedBy { it.due }
        v.setTextViewText(R.id.w_count, if (open.isNotEmpty()) "${open.size}" else "")
        v.setViewVisibility(R.id.w_empty, if (open.isEmpty()) View.VISIBLE else View.GONE)
        v.setTextViewText(R.id.w_empty, "Всё сдано")
        ROWS.take(4).forEachIndexed { i, row ->
            val d = open.getOrNull(i)
            v.setViewVisibility(row, if (d == null) View.GONE else View.VISIBLE)
            if (d != null) {
                val days = ChronoUnit.DAYS.between(now.toLocalDate(), d.due.toLocalDate())
                val label = when {
                    d.due.isBefore(now) -> "просрочен"
                    days == 0L -> "сегодня ${d.due.format(TIME)}"
                    days == 1L -> "завтра"
                    days < 7 -> d.due.dayOfWeek.getDisplayName(TextStyle.SHORT, RU)
                    else -> "${d.due.dayOfMonth} ${MONTHS[d.due.monthValue - 1].take(3)}"
                }
                v.setTextViewText(N[i], listOf(d.subject, d.title).filter { it.isNotBlank() }.joinToString(" · "))
                v.setTextViewText(R_[i], label)
                v.setTextColor(N[i], c.ink)
                v.setTextColor(R_[i], if (d.due.isBefore(now)) 0xFFD23B57.toInt() else if (days <= 1) c.accent else c.muted)
            }
        }
        return v
    }

    private fun exams(ctx: Context, s: Schedule?, now: LocalDateTime, c: Colors): RemoteViews {
        val v = base(ctx, R.layout.widget_exams, c)
        v.setTextColor(R.id.w_days, c.ink); v.setTextColor(R.id.w_label, c.muted); v.setTextColor(R.id.w_sub, c.muted)
        val today = now.toLocalDate()
        val start = s?.examsStart
        val nextExam = s?.exams?.firstOrNull { it.endAt.isAfter(now) }
        when {
            start == null -> { v.setTextViewText(R.id.w_days, "—"); v.setTextViewText(R.id.w_label, "сессия"); v.setTextViewText(R.id.w_sub, "даты пока нет") }
            today.isBefore(start) -> {
                v.setTextViewText(R.id.w_days, "${ChronoUnit.DAYS.between(today, start)}")
                v.setTextViewText(R.id.w_label, "дней до сессии")
                v.setTextViewText(R.id.w_sub, "с ${start.dayOfMonth} ${MONTHS[start.monthValue - 1]}")
            }
            nextExam != null -> {
                v.setTextViewText(R.id.w_days, "${ChronoUnit.DAYS.between(today, nextExam.date)}")
                v.setTextViewText(R.id.w_label, "дн. до экзамена")
                v.setTextViewText(R.id.w_sub, nextExam.lesson.subject)
            }
            else -> { v.setTextViewText(R.id.w_days, "✓"); v.setTextViewText(R.id.w_label, "сессия позади"); v.setTextViewText(R.id.w_sub, "") }
        }
        return v
    }
}
