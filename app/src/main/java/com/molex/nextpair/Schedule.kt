package com.molex.nextpair

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class Teacher(
    val id: Int,
    val urlId: String,
    val lastName: String,
    val firstName: String,
    val middleName: String,
    val degree: String,
    val rank: String,
) {
    val short get() = "$lastName ${firstName.take(1)}. ${middleName.take(1)}.".trim()
    val full get() = "$lastName $firstName $middleName".trim()
    val initials get() = "${firstName.take(1)}${lastName.take(1)}"
    val photoUrl get() = "https://iis.bsuir.by/api/v1/employees/photo/$id"
    val title get() = listOf(degree, rank).filter { it.isNotBlank() }.joinToString(", ")
}

data class Lesson(
    val day: DayOfWeek?,
    val start: LocalTime,
    val end: LocalTime,
    val subject: String,
    val subjectFull: String,
    val type: String,
    val rooms: String,
    val teachers: List<Teacher>,
    val groups: String,
    val weeks: Set<Int>,
    val subgroup: Int,
    val from: LocalDate?,
    val to: LocalDate?,
    val exactDate: LocalDate?,
) {
    val teacher get() = teachers.joinToString(", ") { it.short }

    /** Ключ для заметок и напоминаний: одна и та же пара каждую неделю. */
    val key get() = "$subject|$type|$subgroup|$day|$start"
}

/** Пара, привязанная к конкретной дате. */
data class Occurrence(val date: LocalDate, val lesson: Lesson) {
    val startAt: LocalDateTime get() = date.atTime(lesson.start)
    val endAt: LocalDateTime get() = date.atTime(lesson.end)
}

class Schedule(
    private val lessons: List<Lesson>,
    val exams: List<Occurrence>,
    /** Понедельник недели, номер которой известен из API, и сам номер (1..4). */
    private val anchorMonday: LocalDate,
    private val anchorWeek: Int,
    val termStart: LocalDate?,
    val termEnd: LocalDate?,
    val examsStart: LocalDate?,
    val examsEnd: LocalDate?,
    val title: String,
) {
    /** Скрытые пары: ключ [Lesson.key] или "S:<предмет>" для всего предмета. */
    var hidden: Set<String> = emptySet()

    fun isHidden(l: Lesson) = l.key in hidden || "S:${l.subject}" in hidden

    /** Отпечатки всех пар — для сравнения старого и нового расписания. */
    fun signatures(): Map<String, Lesson> = lessons.associateBy {
        "${it.day}|${it.start}|${it.subject}|${it.type}|${it.rooms}|${it.weeks.sorted()}|${it.subgroup}|${it.exactDate}"
    }

    /** Предметы для выбора в дедлайнах: короткое → полное название. */
    fun subjects(): List<Pair<String, String>> =
        lessons.map { it.subject to it.subjectFull.ifBlank { it.subject } }.distinctBy { it.first }.sortedBy { it.second }

    /** Ближайшая пара по предмету после [now]. */
    fun nextOf(subject: String, now: LocalDateTime, subgroup: Int): Occurrence? = sequence {
        for (i in 0..60) yieldAll(lessonsOn(now.toLocalDate().plusDays(i.toLong()), subgroup))
    }.firstOrNull { it.lesson.subject == subject && it.startAt.isAfter(now) }

    fun weekNumber(date: LocalDate): Int {
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val diff = ChronoUnit.WEEKS.between(anchorMonday, monday).toInt()
        return Math.floorMod(anchorWeek - 1 + diff, 4) + 1
    }

    fun lessonsOn(date: LocalDate, subgroup: Int): List<Occurrence> {
        val week = weekNumber(date)
        return lessons.filter { l ->
            if (l.exactDate != null) return@filter l.exactDate == date && subgroupOk(l, subgroup)
            l.day == date.dayOfWeek &&
                (l.weeks.isEmpty() || week in l.weeks) &&
                (l.from == null || !date.isBefore(l.from)) &&
                (l.to == null || !date.isAfter(l.to)) &&
                subgroupOk(l, subgroup)
        }.filterNot { isHidden(it) }.sortedBy { it.start }.map { Occurrence(date, it) }
    }

    private fun subgroupOk(l: Lesson, subgroup: Int) =
        subgroup == 0 || l.subgroup == 0 || l.subgroup == subgroup

    fun upcoming(now: LocalDateTime, subgroup: Int, count: Int): List<Occurrence> = sequence {
        for (i in 0..21) yieldAll(lessonsOn(now.toLocalDate().plusDays(i.toLong()), subgroup))
    }.filter { it.endAt.isAfter(now) }.take(count).toList()

    /** Текущая пара (если идёт) и следующая после неё. */
    fun currentAndNext(now: LocalDateTime, subgroup: Int): Pair<Occurrence?, Occurrence?> {
        val upcoming = upcoming(now, subgroup, 3)
        val current = upcoming.firstOrNull { !it.startAt.isAfter(now) }
        val next = upcoming.firstOrNull { it.startAt.isAfter(now) }
        return current to next
    }

    /** Ближайший момент, когда текст уведомления должен поменяться. */
    fun nextChange(now: LocalDateTime, subgroup: Int): LocalDateTime {
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
        val (cur, next) = currentAndNext(now, subgroup)
        // Будим телефон только когда меняется текст: начало/конец пары и полночь.
        val candidates = listOfNotNull(cur?.endAt, next?.startAt, midnight).filter { it.isAfter(now) }
        return candidates.min()
    }

    companion object {
        private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        private val DAYS = mapOf(
            "Понедельник" to DayOfWeek.MONDAY, "Вторник" to DayOfWeek.TUESDAY,
            "Среда" to DayOfWeek.WEDNESDAY, "Четверг" to DayOfWeek.THURSDAY,
            "Пятница" to DayOfWeek.FRIDAY, "Суббота" to DayOfWeek.SATURDAY,
            "Воскресенье" to DayOfWeek.SUNDAY,
        )

        private fun date(s: String?): LocalDate? =
            if (s.isNullOrBlank() || s == "null") null else runCatching { LocalDate.parse(s, DATE) }.getOrNull()

        private fun JSONObject.str(key: String): String? =
            if (!has(key) || isNull(key)) null else optString(key)

        fun parse(json: String, anchorMonday: LocalDate, anchorWeek: Int): Schedule {
            val root = JSONObject(json)
            val lessons = mutableListOf<Lesson>()
            val schedules = root.optJSONObject("schedules") ?: JSONObject()
            for (dayName in schedules.keys()) {
                val day = DAYS[dayName] ?: continue
                val arr = schedules.getJSONArray(dayName)
                for (i in 0 until arr.length()) lessons += parseLesson(arr.getJSONObject(i), day)
            }
            val examsArr = root.optJSONArray("exams") ?: JSONArray()
            val exams = (0 until examsArr.length()).mapNotNull {
                val l = parseLesson(examsArr.getJSONObject(it), null)
                l.exactDate?.let { d -> Occurrence(d, l) }
            }.sortedBy { it.startAt }
            val title = root.optJSONObject("studentGroupDto")?.optString("name")
                ?: root.optJSONObject("employeeDto")?.let { teacher(it).short }
                ?: ""
            return Schedule(
                lessons, exams, anchorMonday, anchorWeek,
                date(root.str("startDate")), date(root.str("endDate")),
                date(root.str("startExamsDate")), date(root.str("endExamsDate")),
                title,
            )
        }

        fun teacher(e: JSONObject) = Teacher(
            id = e.optInt("id"),
            urlId = e.str("urlId") ?: "",
            lastName = e.str("lastName") ?: "",
            firstName = e.str("firstName") ?: "",
            middleName = e.str("middleName") ?: "",
            degree = e.str("degreeAbbrev") ?: e.str("degree") ?: "",
            rank = e.str("rank") ?: "",
        )

        private fun parseLesson(o: JSONObject, day: DayOfWeek?): Lesson {
            val weeks = o.optJSONArray("weekNumber") ?: JSONArray()
            val emps = o.optJSONArray("employees") ?: JSONArray()
            val rooms = o.optJSONArray("auditories") ?: JSONArray()
            val groups = o.optJSONArray("studentGroups") ?: JSONArray()
            return Lesson(
                day = day,
                start = LocalTime.parse(o.getString("startLessonTime")),
                end = LocalTime.parse(o.getString("endLessonTime")),
                subject = o.str("subject") ?: o.str("note") ?: "Мероприятие",
                subjectFull = o.str("subjectFullName") ?: o.str("subject") ?: "",
                type = o.str("lessonTypeAbbrev") ?: "",
                rooms = (0 until rooms.length()).joinToString(", ") { rooms.getString(it) },
                teachers = (0 until emps.length()).map { teacher(emps.getJSONObject(it)) },
                groups = (0 until groups.length()).joinToString(", ") { groups.getJSONObject(it).optString("name") },
                weeks = (0 until weeks.length()).map { weeks.getInt(it) }.toSet(),
                subgroup = o.optInt("numSubgroup", 0),
                from = date(o.str("startLessonDate")),
                to = date(o.str("endLessonDate")),
                exactDate = date(o.str("dateLesson")),
            )
        }
    }
}
