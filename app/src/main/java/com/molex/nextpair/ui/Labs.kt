package com.molex.nextpair.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.molex.nextpair.Occurrence
import com.molex.nextpair.Schedule
import java.time.LocalDateTime

/** Сколько лабораторных осталось по каждому предмету и когда они. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LabsScreen(s: Schedule, subgroup: Int, now: LocalDateTime, onBack: () -> Unit, onLesson: (Occurrence) -> Unit) {
    val labs = remember(s, subgroup, now.toLocalDate()) { s.remainingLabs(now, subgroup) }
    val total = labs.sumOf { it.second.size }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { RoundButton(Glyph.Back, "Назад", onBack) }
        item {
            Box(Modifier.enter(0)) {
                ScreenTitle(if (total == 0) "до конца семестра" else "всего ${labsCount(total)} до конца семестра", "Лабораторные")
            }
        }
        if (labs.isEmpty()) item { Box(Modifier.enter(1)) { EmptyCard("Лаб не осталось", "По расписанию лабораторных до конца семестра больше нет.", Glyph.Doc) } }
        itemsIndexed(labs, key = { _, it -> it.first.subject }) { i, (l, list) ->
            Column(
                Modifier.enter(1 + i).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(l.subjectFull.ifBlank { l.subject }, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.Ink, lineHeight = 20.sp)
                        Text("Лабораторная работа · осталось ${pairs(list.size)}", fontSize = 14.sp, color = C.Muted)
                    }
                    Text(
                        "${list.size}", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = C.Accent,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    list.forEach { o ->
                        Pill("${o.date.dayOfMonth} ${month(o.date)}, ${dayName(o.date, false).lowercase()} · ${o.lesson.start.format(TIME)}", C.Soft, C.SoftInk) { onLesson(o) }
                    }
                }
            }
        }
    }
}

/** «1 лаба», «3 лабы», «7 лаб». */
fun labsCount(n: Int): String {
    val word = when {
        n % 100 in 11..14 -> "лаб"
        n % 10 == 1 -> "лаба"
        n % 10 in 2..4 -> "лабы"
        else -> "лаб"
    }
    return "$n $word"
}
