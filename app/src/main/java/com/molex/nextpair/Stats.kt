package com.molex.nextpair

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Статистика семестра для секретного экрана. */
data class SemesterStats(
    val from: LocalDate,
    val to: LocalDate,
    val pairs: Int,
    val hours: Double,
    val hoursDone: Double,
    val bySubject: List<Pair<String, Double>>,
    val topTeacher: Pair<Teacher, Int>?,
    val topRoom: Pair<String, Int>?,
    val hardestDay: Pair<DayOfWeek, Double>?,
    val earlyPairs: Int,
    val longestDay: Pair<LocalDate, Duration>?,
    val windowHours: Double,
    val freeDays: Int,
)

object Stats {
    fun semester(s: Schedule, subgroup: Int, now: LocalDateTime): SemesterStats {
        val today = now.toLocalDate()
        val from = s.termStart ?: today.withDayOfMonth(1)
        val to = s.termEnd ?: today.plusWeeks(16)
        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
        val byDay = days.associateWith { s.lessonsOn(it, subgroup) }
        val all = byDay.values.flatten()
        fun h(o: Occurrence) = Duration.between(o.startAt, o.endAt).toMinutes() / 60.0

        val teachers = all.flatMap { o -> o.lesson.teachers }.groupBy { it.id }.values
            .maxByOrNull { it.size }?.let { it.first() to it.size }
        val rooms = all.map { it.lesson.rooms }.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.toPair()
        val weekday = all.groupBy { it.date.dayOfWeek }.mapValues { (_, l) -> l.sumOf(::h) }
        val weeks = (days.size / 7.0).coerceAtLeast(1.0)
        val longest = byDay.filterValues { it.isNotEmpty() }
            .map { (d, l) -> d to Duration.between(l.minOf { it.startAt }, l.maxOf { it.endAt }) }
            .maxByOrNull { it.second }
        val windows = byDay.values.sumOf { l ->
            l.sortedBy { it.startAt }.zipWithNext { a, b -> Duration.between(a.endAt, b.startAt).toMinutes() }
                .filter { it > 30 }.sum() / 60.0
        }

        return SemesterStats(
            from = from,
            to = to,
            pairs = all.size,
            hours = all.sumOf(::h),
            hoursDone = all.filter { it.endAt.isBefore(now) }.sumOf(::h),
            bySubject = all.groupBy { it.lesson.subjectFull.ifBlank { it.lesson.subject } }
                .mapValues { (_, l) -> l.sumOf(::h) }.toList().sortedByDescending { it.second }.take(5),
            topTeacher = teachers,
            topRoom = rooms,
            hardestDay = weekday.maxByOrNull { it.value }?.let { it.key to it.value / weeks },
            earlyPairs = all.count { it.lesson.start == LocalTime.of(8, 30) },
            longestDay = longest,
            windowHours = windows,
            freeDays = days.count { it.dayOfWeek != DayOfWeek.SUNDAY && byDay[it].isNullOrEmpty() },
        )
    }
}
