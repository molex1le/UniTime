package com.molex.nextpair

import com.molex.nextpair.ui.commonWindows
import com.molex.nextpair.ui.describeKey
import com.molex.nextpair.ui.listIn
import com.molex.nextpair.ui.str
import com.molex.nextpair.ui.parseDate
import com.molex.nextpair.ui.semesterOf
import com.molex.nextpair.ui.roomPlace
import com.molex.nextpair.ui.windowBefore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class FeaturesTest {
    private val json = javaClass.getResource("/schedule.json")!!.readText()
    private fun fresh() = Schedule.parse(json, LocalDate.of(2026, 10, 19), 4)
    private val mon = LocalDate.of(2026, 10, 26) // 1 неделя: Философия 12:00, ФизК 13:35
    private val fri = LocalDate.of(2026, 10, 30)

    @Test fun hideSingleLessonAndWholeSubject() {
        val s = fresh()
        val physed = s.lessonsOn(mon, 0).first { it.lesson.subject == "ФизК" }.lesson
        s.hidden = setOf(physed.key)
        assertEquals(listOf("Философия"), s.lessonsOn(mon, 0).map { it.lesson.subject })
        s.hidden = setOf("S:Философия")
        assertTrue(s.lessonsOn(mon, 0).none { it.lesson.subject == "Философия" })
        assertTrue(s.lessonsOn(LocalDate.of(2026, 10, 28), 0).none { it.lesson.subject == "Философия" }) // и ПЗ в среду
    }

    @Test fun nextOfSubject() {
        val s = fresh()
        val o = s.nextOf("КС", LocalDateTime.of(2026, 10, 26, 9, 0), 1)
        assertNotNull(o)
        assertEquals(fri, o!!.date)
        assertEquals(LocalTime.of(10, 5), o.lesson.start)
    }

    @Test fun windowsBetweenPairs() {
        val s = fresh()
        val tue = s.lessonsOn(LocalDate.of(2026, 10, 27), 0) // 12:00, 13:35, 15:30
        assertEquals(null, windowBefore(tue, 1)) // перемена 10 мин
        assertEquals(null, windowBefore(tue, 2)) // перемена 30 мин — обычная
    }

    @Test fun commonFreeTime() {
        val s = fresh()
        // Пятница п/г 1 и п/г 2: утренние лекции общие, потом лабы параллельно → окон нет, свободны после 15:00.
        val c = commonWindows(s.lessonsOn(fri, 1), s.lessonsOn(fri, 2))
        assertTrue(c.windows.isEmpty())
        assertEquals(LocalTime.of(15, 0), c.freeAfter)
        // Понедельник против пустого дня: свободное время = только то, что вне пар.
        val c2 = commonWindows(s.lessonsOn(mon, 0), emptyList())
        assertEquals(LocalTime.of(15, 0), c2.freeAfter)
        assertTrue(commonWindows(emptyList(), emptyList()).bothFree)
    }

    @Test fun roomParsing() {
        assertEquals("5" to "2 этаж", roomPlace("221-5 к."))
        assertEquals("5" to "9 этаж", roomPlace("902-5 к."))
        assertEquals("3" to "5 этаж", roomPlace("504-3 к."))
        assertEquals(null, roomPlace(""))
    }

    @Test fun hiddenKeyDescription() {
        val k = fresh().lessonsOn(fri, 1).first { it.lesson.type == "ЛР" }.lesson.key
        assertEquals("КС ЛР · п/г 1 · пт 12:00", describeKey(k))
    }

    @Test fun scheduleDiff() {
        val a = fresh().signatures()
        val changed = json.replaceFirst("\"221-5 к.\"", "\"999-5 к.\"")
        val b = Schedule.parse(changed, LocalDate.of(2026, 10, 19), 4).signatures()
        assertEquals(1, (b.keys - a.keys).size)
        assertEquals(1, (a.keys - b.keys).size)
    }

    @Test fun cabinetDatesAndSemesters() {
        assertEquals(LocalDate.of(2025, 12, 26), parseDate("26.12.2025"))
        assertEquals(LocalDate.of(2026, 1, 19), parseDate("2026-01-19T00:00:00.000+03:00"))
        assertEquals("Осень 2025", semesterOf(LocalDate.of(2025, 12, 26)))
        assertEquals("Осень 2025", semesterOf(LocalDate.of(2026, 1, 15))) // январская сессия — ещё осенний семестр
        assertEquals("Весна 2026", semesterOf(LocalDate.of(2026, 4, 2)))
    }

    @Test fun cabinetListsAndFields() {
        val resp = org.json.JSONObject("""{"faculty":"ИЭФ","omissionDtoList":[{"subject":"КС","hours":2.0},{"subject":"БД","hours":"4"}]}""")
        val list = listIn(resp, "omissionDtoList")
        assertEquals(2, list.size)
        assertEquals(6.0, list.sumOf { it.optString("hours").toDouble() }, 0.001)
        val pp = org.json.JSONObject("""{"eventTypeName":"Взыскание","directiveDto":{"number":"123"},"reason":null}""")
        assertEquals("", pp.str("reason"))
        assertEquals("123", pp.str("directiveDto"))
    }

    @Test fun semesterStats() {
        val st = Stats.semester(fresh(), 1, LocalDateTime.of(2026, 10, 30, 10, 52))
        assertTrue(st.pairs > 50)
        assertTrue(st.hoursDone in 1.0..st.hours)
        assertNotNull(st.topTeacher)
        assertNotNull(st.topRoom)
        println("STATS: ${st.pairs} пар, ${st.hours} ч, позади ${st.hoursDone} ч; препод ${st.topTeacher?.first?.short} ×${st.topTeacher?.second}; " +
            "аудитория ${st.topRoom}; тяжёлый день ${st.hardestDay}; 8:30 — ${st.earlyPairs}; самый длинный ${st.longestDay}; окна ${st.windowHours} ч; без пар ${st.freeDays}")
    }
}
