package com.molex.nextpair

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ScheduleTest {
    private val json = javaClass.getResource("/schedule.json")!!.readText()
    // 23.10.2026 — 4-я учебная неделя (по API current-week)
    private val s = Schedule.parse(json, LocalDate.of(2026, 10, 19), 4)

    @Test fun weekNumbers() {
        assertEquals(4, s.weekNumber(LocalDate.of(2026, 10, 23)))
        assertEquals(1, s.weekNumber(LocalDate.of(2026, 10, 26)))
        assertEquals(1, s.weekNumber(LocalDate.of(2026, 9, 1)))
    }

    @Test fun printSamples() {
        for (t in listOf("2026-10-23T22:45", "2026-10-26T07:00", "2026-10-26T09:00", "2026-10-26T12:00")) {
            val now = LocalDateTime.parse(t)
            val d = Updater.describe(s, now, 0)
            println("$t -> ${d.title} | ${d.text} | next change ${s.nextChange(now, 0)}")
        }
        for (o in s.lessonsOn(LocalDate.of(2026, 10, 26), 0)) println("  пн 26.10: ${o.lesson.start} ${o.lesson.subject} ${o.lesson.type} ${o.lesson.rooms} подгр.${o.lesson.subgroup}")
    }
}
