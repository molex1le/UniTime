package com.molex.nextpair

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.molex.nextpair.ui.listIn
import com.molex.nextpair.ui.str
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Фоновая проверка кабинета IIS: сравнивает снимок с прошлым и уведомляет о новом.
 * Первая проверка только запоминает состояние, чтобы не засыпать старыми оценками.
 */
object IisWatch {
    private const val CHANNEL = "iis_news"
    private const val REQUEST = 5

    // --- Снимки: ключ записи → её значение (для оценок — сама отметка) ---

    fun markbook(v: Any?): Map<String, String> {
        val pages = (v as? JSONObject)?.optJSONObject("markPages") ?: return emptyMap()
        val out = mutableMapOf<String, String>()
        for (sem in pages.keys()) {
            listIn(pages.optJSONObject(sem), "marks").forEach { m ->
                val mark = m.str("mark")
                if (mark.isNotBlank()) out["$sem|${m.str("subject", "fullSubject")}|${m.str("formOfControl")}"] = mark
            }
        }
        return out
    }

    /** Отметки из журнала: «предмет|дата|отметка|№», чтобы две одинаковые в один день не слились. */
    fun journal(v: Any?): Map<String, String> {
        val root = v as? JSONObject
        val subjects = listIn(root?.opt("personalRating") ?: v, "subjects", "disciplines", "personalRating")
        val out = mutableMapOf<String, String>()
        subjects.forEach { s ->
            val name = s.str("name", "discipline", "subject", "abbrev")
            val marks = mutableListOf<Pair<String, String>>()
            collect(s, marks)
            marks.groupBy { it }.forEach { (p, same) ->
                same.forEachIndexed { i, _ -> out["$name|${p.second}|${p.first}|$i"] = p.first }
            }
        }
        return out
    }

    private fun collect(v: Any?, out: MutableList<Pair<String, String>>) {
        when (v) {
            is JSONObject -> {
                if (v.has("mark")) {
                    val m = v.str("mark")
                    if (m.isNotBlank() && m.length <= 3) out += m to v.str("date", "dateString")
                }
                v.keys().forEach { k -> if (k != "deadlines") collect(v.opt(k), out) }
            }
            is JSONArray -> for (i in 0 until v.length()) collect(v.opt(i), out)
        }
    }

    fun omissions(v: Any?): Map<String, String> = listIn(v, "omissions", "omissionDtoList").associate { o ->
        "${o.str("date", "dateFrom")}|${o.str("subject")}|${o.str("lessonTypeAbbrev")}|${o.str("hours")}" to o.str("hours")
    }

    fun messages(v: Any?): Map<String, String> = listIn(v, "notifications").associate { n ->
        n.str("id").ifBlank { "${n.str("date", "createdDate")}|${n.str("title", "message").take(40)}" } to
            n.str("title", "subject", "header").ifBlank { n.str("message", "text").take(80) }
    }

    fun penalties(v: Any?): Map<String, String> = listIn(v).associate { o ->
        "${o.str("date")}|${o.str("eventTypeName")}|${o.str("reason")}" to "${o.str("eventTypeName")}: ${o.str("reason")}".trim(':', ' ')
    }

    /** Что появилось или изменилось. */
    fun diff(old: Map<String, String>?, new: Map<String, String>): List<Pair<String, String>> {
        if (old == null) return emptyList()
        return new.filter { (k, v) -> old[k] != v }.toList()
    }

    // --- Проверка ---

    fun check(ctx: Context): List<String> {
        val st = Settings(ctx)
        val iis = Iis(ctx)
        if (!iis.loggedIn) return emptyList()
        val prefs = ctx.getSharedPreferences("iis_watch", Context.MODE_PRIVATE)
        val found = mutableListOf<String>()

        fun run(key: String, enabled: Boolean, path: String, snap: (Any?) -> Map<String, String>, text: (String, String) -> String) {
            if (!enabled) return
            val r = runCatching { iis.get(path) }.getOrNull() ?: return
            if (r.code !in 200..299) return
            val parsed = runCatching { JSONTokener(r.body).nextValue() }.getOrNull() ?: return
            val now = snap(parsed)
            val old = prefs.getString(key, null)?.let { s ->
                runCatching { JSONObject(s).let { o -> o.keys().asSequence().associateWith { o.getString(it) } } }.getOrNull()
            }
            diff(old, now).forEach { (k, v) -> found += text(k, v) }
            prefs.edit().putString(key, JSONObject(now as Map<*, *>).toString()).apply()
        }

        run("markbook", st.iisMarks, "/markbook", ::markbook) { k, v -> "Зачётка: ${k.split("|").getOrElse(1) { "" }} — $v" }
        run("journal", st.iisMarks, "/personal-rating", ::journal) { k, v -> "Новая отметка: ${k.substringBefore("|")} — $v" }
        run("omissions", st.iisOmissions, "/disrespectful-omissions-by-student", ::omissions) { k, v ->
            val p = k.split("|")
            "Новый пропуск: ${p.getOrElse(1) { "" }} ${p.getOrElse(2) { "" }}, $v ч".replace("  ", " ")
        }
        run("messages", st.iisMessages, "/notifications?pageNumber=1&pageSize=30", ::messages) { _, v -> "Сообщение из IIS: $v" }
        run("penalties", st.iisPenalties, "/dormitory-queue-application/premium-penalty", ::penalties) { _, v -> v }

        prefs.edit().putLong("lastCheck", System.currentTimeMillis()).apply()
        if (found.isNotEmpty()) notify(ctx, found)
        return found
    }

    fun lastCheck(ctx: Context): Long = ctx.getSharedPreferences("iis_watch", Context.MODE_PRIVATE).getLong("lastCheck", 0)

    /** Забыть снимки — после выхода из аккаунта следующий вход начнёт с чистого листа. */
    fun reset(ctx: Context) = ctx.getSharedPreferences("iis_watch", Context.MODE_PRIVATE).edit().clear().apply()

    private fun notify(ctx: Context, items: List<String>) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Новое в личном кабинете", NotificationManager.IMPORTANCE_HIGH).apply {
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            })
        }
        val open = PendingIntent.getActivity(
            ctx, 3, Intent(ctx, MainActivity::class.java).putExtra("openAccount", true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = if (items.size == 1) items.first() else "Новое в IIS: ${items.size}"
        val text = items.take(8).joinToString("\n") + if (items.size > 8) "\nи ещё ${items.size - 8}" else ""
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_pair)
            .setContentTitle(title)
            .setContentText(if (items.size == 1) "Открыть личный кабинет" else items.first())
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setColor(0xFF2F4FD8.toInt())
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { nm.notify(10 + (System.currentTimeMillis() % 1000).toInt(), n) }
    }

    // --- Расписание проверок ---

    fun schedule(ctx: Context) {
        val st = Settings(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(ctx, REQUEST, Intent(ctx, IisWatchReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (!st.iisNotify || !Iis(ctx).loggedIn) { am.cancel(pi); return }
        val every = st.iisHours * 3600_000L
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + every, every, pi)
    }
}

class IisWatchReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            runCatching { IisWatch.check(ctx) }
            pending.finish()
        }.start()
    }
}
