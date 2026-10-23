package com.molex.nextpair

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/** Чьё расписание: группа (id = номер) или преподаватель (id = urlId). */
data class Target(val kind: String, val id: String, val title: String, val sub: String) {
    val isGroup get() = kind == "group"

    fun toJson() = JSONObject().put("kind", kind).put("id", id).put("title", title).put("sub", sub)

    companion object {
        fun fromJson(o: JSONObject) =
            Target(o.getString("kind"), o.getString("id"), o.optString("title"), o.optString("sub"))
    }
}

data class Deadline(val id: Long, val title: String, val subject: String, val due: LocalDateTime, val done: Boolean) {
    fun toJson() = JSONObject().put("id", id).put("title", title).put("subject", subject).put("due", due.toString()).put("done", done)

    companion object {
        fun fromJson(o: JSONObject) = Deadline(
            o.getLong("id"), o.getString("title"), o.optString("subject"),
            LocalDateTime.parse(o.getString("due")), o.optBoolean("done"),
        )
    }
}

data class Friend(val name: String, val group: String, val subgroup: Int) {
    val target get() = Target("group", group, group, "")
    fun toJson() = JSONObject().put("name", name).put("group", group).put("subgroup", subgroup)

    companion object {
        fun fromJson(o: JSONObject) = Friend(o.getString("name"), o.getString("group"), o.optInt("subgroup"))
    }
}

/** Настройки, кэш расписания с iis.bsuir.by, заметки, избранное. */
class Repo(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("nextpair", Context.MODE_PRIVATE)
    private val cache = File(ctx.filesDir, "schedule.json")

    var group: String
        get() = prefs.getString("group", "572302")!!
        set(v) = prefs.edit().putString("group", v).apply()

    /** 0 — все подгруппы, 1 или 2 — только своя. */
    var subgroup: Int
        get() = prefs.getInt("subgroup", 0)
        set(v) = prefs.edit().putInt("subgroup", v).apply()

    val lastFetch: Long get() = prefs.getLong("fetchedAt", 0)

    fun isStale() = System.currentTimeMillis() - lastFetch > 12 * 3600_000L ||
        prefs.getString("cachedGroup", null) != group

    /**
     * Сетевой запрос — вызывать не из главного потока.
     * Возвращает описания изменений по сравнению с прошлой версией (пусто, если сравнивать не с чем).
     */
    fun fetch(): List<String> {
        val json = get(scheduleUrl(Target("group", group, group, "")))
        val week = get("$API/schedule/current-week").trim().toInt()
        val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val fresh = Schedule.parse(json, monday, week) // проверяем, что ответ разбирается, до записи в кэш
        val old = if (cache.exists() && prefs.getString("cachedGroup", null) == group)
            runCatching { Schedule.parse(cache.readText(), monday, week) }.getOrNull() else null
        cache.writeText(json)
        prefs.edit()
            .putString("anchorMonday", monday.toString())
            .putInt("anchorWeek", week)
            .putString("cachedGroup", group)
            .putLong("fetchedAt", System.currentTimeMillis())
            .apply()
        if (old == null) return emptyList()
        val a = old.signatures()
        val b = fresh.signatures()
        fun describe(sign: String, l: Lesson) =
            "${l.day?.let { DAY_SHORT[it.value - 1] } ?: l.exactDate} ${l.start} ${l.subject} ${l.type} ${l.rooms}".trim()
        return (b.keys - a.keys).map { "+ " + describe(it, b.getValue(it)) } +
            (a.keys - b.keys).map { "− " + describe(it, a.getValue(it)) }
    }

    fun load(): Schedule? {
        if (!cache.exists() || prefs.getString("cachedGroup", null) != group) return null
        val (monday, week) = anchor() ?: return null
        return runCatching { Schedule.parse(cache.readText(), monday, week) }.getOrNull()
            ?.also { it.hidden = Settings(ctx).hiddenSubjects }
    }

    private fun anchor(): Pair<LocalDate, Int>? {
        val monday = prefs.getString("anchorMonday", null) ?: return null
        return LocalDate.parse(monday) to prefs.getInt("anchorWeek", 1)
    }

    /** Чужое расписание (группа или преподаватель) — по сети, без кэша. */
    fun fetchOther(t: Target): Schedule {
        val json = get(scheduleUrl(t))
        val (monday, week) = anchor() ?: run {
            val w = get("$API/schedule/current-week").trim().toInt()
            LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) to w
        }
        return Schedule.parse(json, monday, week)
    }

    private fun scheduleUrl(t: Target) =
        if (t.isGroup) "$API/schedule?studentGroup=${enc(t.id)}" else "$API/employees/schedule/${enc(t.id)}"

    // --- Заметки и напоминания ---

    fun note(key: String): String = prefs.getString("note:$key", "")!!
    fun setNote(key: String, text: String) = prefs.edit().putString("note:$key", text).apply()

    fun reminders(): Set<String> = prefs.getStringSet("reminders", emptySet())!!
    fun setReminder(key: String, on: Boolean) {
        val s = reminders().toMutableSet()
        if (on) s += key else s -= key
        prefs.edit().putStringSet("reminders", s).apply()
    }

    // --- Дедлайны ---

    fun deadlines(): List<Deadline> {
        val arr = JSONArray(prefs.getString("deadlines", "[]"))
        return (0 until arr.length()).map { Deadline.fromJson(arr.getJSONObject(it)) }.sortedBy { it.due }
    }

    fun saveDeadline(d: Deadline) = saveDeadlines(deadlines().filterNot { it.id == d.id } + d)
    fun deleteDeadline(id: Long) = saveDeadlines(deadlines().filterNot { it.id == id })

    private fun saveDeadlines(list: List<Deadline>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("deadlines", arr.toString()).apply()
    }

    // --- Друзья ---

    fun friends(): List<Friend> {
        val arr = JSONArray(prefs.getString("friends", "[]"))
        return (0 until arr.length()).map { Friend.fromJson(arr.getJSONObject(it)) }
    }

    fun saveFriends(list: List<Friend>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("friends", arr.toString()).apply()
    }

    // --- Избранное и недавние ---

    fun favorites(): List<Target> = targets("favorites")
    fun recents(): List<Target> = targets("recents")

    fun isFavorite(t: Target) = favorites().any { it.kind == t.kind && it.id == t.id }

    fun toggleFavorite(t: Target) {
        val list = favorites().filterNot { it.kind == t.kind && it.id == t.id }
        saveTargets("favorites", if (list.size == favorites().size) list + t else list)
    }

    fun addRecent(t: Target) {
        saveTargets("recents", (listOf(t) + recents().filterNot { it.kind == t.kind && it.id == t.id }).take(8))
    }

    private fun targets(key: String): List<Target> {
        val arr = JSONArray(prefs.getString(key, "[]"))
        return (0 until arr.length()).map { Target.fromJson(arr.getJSONObject(it)) }
    }

    private fun saveTargets(key: String, list: List<Target>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    // --- Справочники для поиска (кэш на неделю) ---

    fun allGroups(): List<Target> {
        val arr = JSONArray(cached("groups.json", "$API/student-groups"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Target(
                "group", o.getString("name"), o.getString("name"),
                listOfNotNull(o.optString("facultyAbbrev"), o.optString("specialityAbbrev"), "${o.optInt("course")} курс")
                    .filter { s -> s.isNotBlank() }.joinToString(", "),
            )
        }
    }

    fun allTeachers(): List<Target> {
        val arr = JSONArray(cached("teachers.json", "$API/employees/all"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            val t = Schedule.teacher(o)
            val deps = o.optJSONArray("academicDepartment") ?: JSONArray()
            val sub = listOf(t.rank, (0 until deps.length()).joinToString(", ") { i -> deps.getString(i) })
                .filter { s -> s.isNotBlank() }.joinToString(" · ")
            Target("teacher", t.urlId, t.full, sub)
        }
    }

    private fun cached(name: String, url: String): String {
        val f = File(ctx.filesDir, name)
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < 7 * 86400_000L) return f.readText()
        return runCatching { get(url).also { f.writeText(it) } }.getOrElse { if (f.exists()) f.readText() else throw it }
    }

    companion object {
        const val API = "https://iis.bsuir.by/api/v1"
        private val DAY_SHORT = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

        fun get(url: String): String = String(getBytes(url), Charsets.UTF_8)

        fun getBytes(url: String): ByteArray {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 20000
            try {
                if (c.responseCode != 200) error("HTTP ${c.responseCode}")
                return c.inputStream.readBytes()
            } finally {
                c.disconnect()
            }
        }
    }
}
