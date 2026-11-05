package com.molex.nextpair.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.molex.nextpair.Occurrence
import com.molex.nextpair.Schedule
import java.time.LocalDateTime

private val FILTERS = listOf("Все", "ЛК", "ПЗ", "ЛР")

private fun typePlural(type: String) = when (type) {
    "ЛК" -> "Лекции"
    "ПЗ" -> "Практики"
    "ЛР" -> "Лабы"
    else -> typeStyle(type).third
}

/**
 * Сколько пар осталось по каждому предмету до конца семестра: счётчики по типам,
 * по нажатию — даты. Фильтр сверху оставляет один тип (например, только лабы).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubjectsScreen(s: Schedule, subgroup: Int, now: LocalDateTime, onBack: () -> Unit, onLesson: (Occurrence) -> Unit) {
    val all = remember(s, subgroup, now.toLocalDate()) { s.remaining(now, subgroup) }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val type = FILTERS[filter].takeIf { filter > 0 }
    val shown = if (type == null) all else all.mapNotNull { subj ->
        subj.byType[type]?.let { Schedule.SubjectLeft(subj.lesson, mapOf(type to it)) }
    }
    val total = shown.sumOf { it.total }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { RoundButton(Glyph.Back, "Назад", onBack) }
        item {
            Box(Modifier.enter(0)) {
                val what = if (type == null) pairs(total) else "${pairs(total)} · ${typePlural(type).lowercase()}"
                ScreenTitle("осталось $what до конца семестра", "Предметы")
            }
        }
        item { Box(Modifier.enter(1)) { Segmented(FILTERS, filter) { filter = it } } }
        if (shown.isEmpty()) item {
            Box(Modifier.enter(2)) {
                EmptyCard(if (type == null) "Пар не осталось" else "${typePlural(type)} закончились", "По расписанию до конца семестра больше ничего нет.", Glyph.Doc)
            }
        }
        itemsIndexed(shown, key = { _, it -> it.lesson.subject }) { i, subj ->
            val expanded = open == subj.lesson.subject
            val turn by animateFloatAsState(if (expanded) 90f else 0f, bouncy(), label = "chevron")
            Column(
                Modifier.enter(2 + i).animateItem().bounceClick { open = if (expanded) null else subj.lesson.subject }
                    .fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        subj.lesson.subjectFull.ifBlank { subj.lesson.subject }, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = C.Ink, lineHeight = 20.sp, modifier = Modifier.weight(1f),
                    )
                    Box(Modifier.graphicsLayer { rotationZ = turn }) { Glyph(Glyph.Chevron, C.Chevron, 18.dp) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    subj.byType.forEach { (t, list) ->
                        val (bg, fg, _) = typeStyle(t)
                        Text(
                            "$t ${list.size}", color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                }
                AnimatedVisibility(expanded, enter = expandVertically(bouncy()) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                        subj.byType.forEach { (t, list) ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${typePlural(t)} · осталось ${pairs(list.size)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.Muted)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    list.forEach { o ->
                                        Pill("${o.date.dayOfMonth} ${month(o.date)}, ${dayName(o.date, false).lowercase()} · ${o.lesson.start.format(TIME)}", C.Soft, C.SoftInk) { onLesson(o) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
