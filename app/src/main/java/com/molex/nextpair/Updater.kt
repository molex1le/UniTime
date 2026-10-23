package com.molex.nextpair

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class Display(val title: String, val text: String, val progress: Pair<Int, Int>? = null)

object Updater {
    private const val CHANNEL = "next_pair"
    private const val REMIND_CHANNEL = "remind"
    private const val NOTIF_ID = 1
    private const val REMIND_ID = 2
    private const val CHANGES_ID = 3
    private const val SILENT_CHANNEL = "remind_silent"
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")
    private val RU = Locale("ru")

    /** Обновляет уведомление и виджеты; при устаревшем кэше сначала качает расписание. */
    fun refresh(ctx: Context, forceFetch: Boolean = false, done: (Throwable?) -> Unit = {}) {
        val repo = Repo(ctx)
        Thread {
            var err: Throwable? = null
            if (forceFetch || repo.isStale()) err = runCatching {
                val changes = repo.fetch()
                if (changes.isNotEmpty() && Settings(ctx).notifyChanges) postChanges(ctx, changes)
            }.exceptionOrNull()
            render(ctx, repo)
            done(err)
        }.start()
    }

    fun render(ctx: Context, repo: Repo = Repo(ctx)) {
        val schedule = repo.load()
        val now = LocalDateTime.now()
        val st = Settings(ctx)
        val d = if (schedule == null) Display("Нет расписания", "Откройте приложение, чтобы загрузить")
        else describe(schedule, now, repo.subgroup, st.notifRoom, st.notifTeacher)
        val (cur, next) = schedule?.currentAndNext(now, repo.subgroup) ?: (null to null)
        val nothingToday = schedule != null && cur == null && next?.date != now.toLocalDate()
        if (!st.notifEnabled || (st.hideAfterLast && nothingToday)) {
            ctx.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        } else {
            postNotification(ctx, d)
        }
        updateWidgets(ctx, schedule, now, repo.subgroup, st.widgetDark)
        // Если расписания нет, пробуем снова через 30 минут.
        val at = schedule?.nextChange(now, repo.subgroup) ?: now.plusMinutes(30)
        scheduleAlarm(ctx, at, PendingIntent.getBroadcast(ctx, 0, Intent(ctx, TickReceiver::class.java), FLAGS))
        scheduleReminder(ctx, schedule, now, repo, st)
        scheduleDeadlineReminder(ctx, now, repo)
    }

    fun describe(s: Schedule, now: LocalDateTime, subgroup: Int, room: Boolean = true, teacher: Boolean = true): Display {
        val (cur, next) = s.currentAndNext(now, subgroup)
        if (cur != null) {
            val title = "Сейчас: ${name(cur)} · до ${cur.lesson.end.format(TIME)}"
            val text = if (next != null) "Далее: ${name(next)} ${whenText(next, now)}" + (if (room) " · ${place(next)}" else "")
            else "Больше пар нет"
            val total = Duration.between(cur.startAt, cur.endAt).toMinutes().toInt()
            val done = Duration.between(cur.startAt, now).toMinutes().toInt()
            return Display(title, text, total to done)
        }
        if (next == null) return Display("Пар не найдено", "В ближайшие три недели пусто")
        return Display("${name(next)} ${whenText(next, now)}", details(next, room, teacher))
    }

    private fun name(o: Occurrence) =
        if (o.lesson.type.isBlank()) o.lesson.subject else "${o.lesson.subject} (${o.lesson.type})"

    private fun place(o: Occurrence) = o.lesson.rooms.ifBlank { "—" }

    private fun details(o: Occurrence, room: Boolean = true, teacher: Boolean = true) = listOf(
        "${o.lesson.start.format(TIME)}–${o.lesson.end.format(TIME)}",
        if (room) o.lesson.rooms else "",
        if (teacher) o.lesson.teacher else "",
    ).filter { it.isNotBlank() }.joinToString(" · ")

    private fun whenText(o: Occurrence, now: LocalDateTime): String {
        val t = o.lesson.start.format(TIME)
        val today = now.toLocalDate()
        return when (o.date) {
            today -> "в $t"
            today.plusDays(1) -> "завтра в $t"
            else -> "${o.date.dayOfWeek.getDisplayName(TextStyle.SHORT, RU)} ${o.date.dayOfMonth} в $t"
        }
    }

    private fun minutes(min: Long): String {
        val h = min / 60
        val m = min % 60
        return when {
            h == 0L -> "$m мин"
            m == 0L -> "$h ч"
            else -> "$h ч $m мин"
        }
    }

    private fun channels(nm: NotificationManager) {
        if (nm.getNotificationChannel(CHANNEL) == null) {
            // Обычная важность, чтобы Samsung показывал текст на экране блокировки, но без звука.
            val ch = NotificationChannel(CHANNEL, "Следующая пара", NotificationManager.IMPORTANCE_DEFAULT)
            ch.setSound(null, null)
            ch.enableVibration(false)
            ch.setShowBadge(false)
            ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(ch)
        }
        if (nm.getNotificationChannel(REMIND_CHANNEL) == null) {
            val ch = NotificationChannel(REMIND_CHANNEL, "Напоминания со звуком", NotificationManager.IMPORTANCE_HIGH)
            ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(ch)
        }
        if (nm.getNotificationChannel(SILENT_CHANNEL) == null) {
            val ch = NotificationChannel(SILENT_CHANNEL, "Напоминания без звука", NotificationManager.IMPORTANCE_DEFAULT)
            ch.setSound(null, null)
            ch.enableVibration(false)
            ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(ch)
        }
    }

    private fun openApp(ctx: Context) =
        PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    private fun postNotification(ctx: Context, d: Display) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        channels(nm)
        val b = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_pair)
            .setContentTitle(d.title)
            .setContentText(d.text)
            .setStyle(Notification.BigTextStyle().bigText(d.text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setColor(0xFF2F4FD8.toInt())
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_EVENT)
            .setContentIntent(openApp(ctx))
        d.progress?.let { (max, cur) -> b.setProgress(max, cur.coerceIn(0, max), false) }
        runCatching { nm.notify(NOTIF_ID, b.build()) }
    }

    fun postReminder(ctx: Context, title: String, text: String, id: Int = REMIND_ID) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        channels(nm)
        val n = Notification.Builder(ctx, if (Settings(ctx).remindSound) REMIND_CHANNEL else SILENT_CHANNEL)
            .setSmallIcon(R.drawable.ic_pair)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setColor(0xFF2F4FD8.toInt())
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(openApp(ctx))
            .build()
        runCatching { nm.notify(id, n) }
    }

    private fun postChanges(ctx: Context, changes: List<String>) {
        val text = changes.take(6).joinToString("\n") + if (changes.size > 6) "\nи ещё ${changes.size - 6}" else ""
        postReminder(ctx, "Расписание изменилось", text, CHANGES_ID)
    }

    /** Ближайшая пара с включённым напоминанием → будильник за N минут до неё. */
    private fun scheduleReminder(ctx: Context, s: Schedule?, now: LocalDateTime, repo: Repo, st: Settings) {
        val keys = repo.reminders()
        val before = st.remindMinutes.toLong()
        val o = s?.upcoming(now, repo.subgroup, 40)
            ?.firstOrNull { it.lesson.key in keys && it.startAt.minusMinutes(before).isAfter(now) }
        val intent = Intent(ctx, ReminderReceiver::class.java)
        if (o != null) {
            val note = repo.note(o.lesson.key)
            intent.putExtra("title", "Через $before минут: ${name(o)}")
            intent.putExtra("text", listOf(place(o), note).filter { it.isNotBlank() && it != "—" }.joinToString(" · ").ifBlank { details(o) })
        }
        val pi = PendingIntent.getBroadcast(ctx, 1, intent, FLAGS)
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (o == null) am.cancel(pi) else scheduleAlarm(ctx, o.startAt.minusMinutes(before), pi)
    }

    /** Дедлайны: напоминание за сутки, а если сутки уже прошли — за 2 часа. */
    private fun scheduleDeadlineReminder(ctx: Context, now: LocalDateTime, repo: Repo) {
        val next = repo.deadlines().filterNot { it.done }.flatMap { d ->
            listOf(d.due.minusDays(1) to "Завтра", d.due.minusHours(2) to "Через 2 часа").map { (at, label) -> Triple(at, label, d) }
        }.filter { it.first.isAfter(now) }.minByOrNull { it.first }
        val intent = Intent(ctx, ReminderReceiver::class.java)
        if (next != null) {
            val (_, label, d) = next
            intent.putExtra("title", "$label дедлайн: ${d.title}")
            intent.putExtra("text", listOf(d.subject, "срок ${d.due.format(DateTimeFormatter.ofPattern("d MMM, HH:mm", RU))}").filter { it.isNotBlank() }.joinToString(" · "))
            intent.putExtra("id", 4)
        }
        val pi = PendingIntent.getBroadcast(ctx, 2, intent, FLAGS)
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (next == null) am.cancel(pi) else scheduleAlarm(ctx, next.first, pi)
    }

    private fun updateWidgets(ctx: Context, s: Schedule?, now: LocalDateTime, subgroup: Int, dark: Boolean) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, PairWidget::class.java))
        if (ids.isEmpty()) return
        val v = RemoteViews(ctx.packageName, R.layout.widget)
        val ink = if (dark) 0xFFEDEFF3.toInt() else 0xFF11151C.toInt()
        val muted = if (dark) 0xFFA3ABB8.toInt() else 0xFF5B6472.toInt()
        v.setInt(R.id.w_root, "setBackgroundResource", if (dark) R.drawable.widget_bg_dark else R.drawable.widget_bg)
        v.setInt(R.id.w_after, "setBackgroundResource", if (dark) R.drawable.widget_chip_dark else R.drawable.widget_chip)
        listOf(R.id.w_time, R.id.w_name, R.id.w_after).forEach { v.setTextColor(it, ink) }
        listOf(R.id.w_head, R.id.w_until, R.id.w_details).forEach { v.setTextColor(it, muted) }
        v.setTextColor(R.id.w_count, if (dark) 0xFF8EA2FF.toInt() else 0xFF2F4FD8.toInt())
        v.setOnClickPendingIntent(R.id.w_root, openApp(ctx))
        val today = now.toLocalDate()
        v.setTextViewText(
            R.id.w_head,
            "${today.dayOfWeek.getDisplayName(TextStyle.SHORT, RU).replaceFirstChar { it.uppercase() }}, ${today.dayOfMonth}" +
                (s?.let { " · ${it.weekNumber(today)} неделя" } ?: ""),
        )
        val list = s?.upcoming(now, subgroup, 4).orEmpty()
        val o = list.firstOrNull()
        if (s == null || o == null) {
            v.setTextViewText(R.id.w_name, if (s == null) "Нет расписания" else "Пар не найдено")
            v.setTextViewText(R.id.w_time, "—")
            v.setTextViewText(R.id.w_until, "")
            v.setTextViewText(R.id.w_details, if (s == null) "Откройте приложение" else "В ближайшие три недели пусто")
            v.setTextViewText(R.id.w_count, "")
            v.setViewVisibility(R.id.w_after, View.GONE)
        } else {
            val ongoing = !o.startAt.isAfter(now)
            v.setTextViewText(R.id.w_name, o.lesson.subjectFull.ifBlank { o.lesson.subject })
            v.setTextViewText(R.id.w_time, o.lesson.start.format(TIME))
            v.setTextViewText(
                R.id.w_until,
                when {
                    ongoing -> "идёт, ещё ${minutes(Duration.between(now, o.endAt).toMinutes() + 1)}"
                    o.date == today -> "через ${minutes(Duration.between(now, o.startAt).toMinutes() + 1)}"
                    else -> whenText(o, now)
                },
            )
            v.setTextViewText(R.id.w_details, listOf(o.lesson.type, o.lesson.rooms, o.lesson.teacher).filter { it.isNotBlank() }.joinToString(" · "))
            val leftToday = s.lessonsOn(today, subgroup).count { it.endAt.isAfter(now) }
            v.setTextViewText(R.id.w_count, if (leftToday > 0) "сегодня ещё $leftToday" else "")
            val after = list.getOrNull(1)?.takeIf { it.date == o.date }
            if (after != null) {
                v.setViewVisibility(R.id.w_after, View.VISIBLE)
                v.setTextViewText(R.id.w_after, "Затем ${after.lesson.start.format(TIME)} · ${after.lesson.subject} ${after.lesson.type}".trim())
            } else {
                v.setViewVisibility(R.id.w_after, View.GONE)
            }
        }
        mgr.updateAppWidget(ids, v)
    }

    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    private fun scheduleAlarm(ctx: Context, at: LocalDateTime, pi: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val ms = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() + 1000
        if (android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        }
    }
}

/** Срабатывает по будильнику, при загрузке телефона и при смене времени. */
class TickReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        Updater.refresh(ctx) { pending.finish() }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: return
        Updater.postReminder(ctx, title, intent.getStringExtra("text") ?: "", intent.getIntExtra("id", 2))
        val pending = goAsync()
        Updater.refresh(ctx) { pending.finish() } // запланировать следующее напоминание
    }
}
