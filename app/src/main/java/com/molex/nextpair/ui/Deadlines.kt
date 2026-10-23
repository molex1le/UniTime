package com.molex.nextpair.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.molex.nextpair.Deadline
import com.molex.nextpair.Schedule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@Composable
fun DeadlinesScreen(
    deadlines: List<Deadline>,
    now: LocalDateTime,
    onAdd: () -> Unit,
    onEdit: (Deadline) -> Unit,
    onToggle: (Deadline) -> Unit,
) {
    var showDone by remember { mutableStateOf(false) }
    val open = deadlines.filterNot { it.done }
    val today = now.toLocalDate()
    val groups = listOf(
        "Просрочено" to open.filter { it.due.isBefore(now) },
        "Сегодня" to open.filter { !it.due.isBefore(now) && it.due.toLocalDate() == today },
        "На неделе" to open.filter { it.due.toLocalDate().isAfter(today) && ChronoUnit.DAYS.between(today, it.due.toLocalDate()) < 7 },
        "Позже" to open.filter { ChronoUnit.DAYS.between(today, it.due.toLocalDate()) >= 7 },
    ).filter { it.second.isNotEmpty() }
    val done = deadlines.filter { it.done }.sortedByDescending { it.due }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                ScreenTitle(if (open.isEmpty()) "всё сдано" else "осталось ${open.size}", "Дедлайны")
                RoundButton(Glyph.Plus, "Новый дедлайн", onAdd)
            }
        }
        if (open.isEmpty()) item {
            EmptyCard("Дедлайнов нет", "Нажми «+», чтобы добавить лабу, реферат или зачёт. Напомню за сутки и за 2 часа.", Glyph.Flag)
        }
        groups.forEach { (title, list) ->
            item(key = "h$title") {
                Text(title.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (title == "Просрочено") C.Error else C.Muted, letterSpacing = 0.5.sp)
            }
            item(key = "l$title") { DeadlineList(list, now, onEdit, onToggle) }
        }
        if (done.isNotEmpty()) {
            item(key = "done") {
                Text(
                    (if (showDone) "Скрыть выполненные" else "Выполненные · ${done.size}"),
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.Accent,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { showDone = !showDone }.padding(vertical = 8.dp),
                )
            }
            if (showDone) item(key = "doneList") { DeadlineList(done, now, onEdit, onToggle) }
        }
    }
}

@Composable
private fun DeadlineList(list: List<Deadline>, now: LocalDateTime, onEdit: (Deadline) -> Unit, onToggle: (Deadline) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card)) {
        list.forEachIndexed { i, d ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.Divider))
            Row(
                Modifier.fillMaxWidth().clickable { onEdit(d) }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = if (d.done) "Вернуть" else "Готово") { onToggle(d) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).background(if (d.done) C.Accent else C.Card)
                            .border(2.dp, if (d.done) C.Accent else C.Line, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { if (d.done) Glyph(Glyph.Check, C.OnAccent, 16.dp) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        d.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (d.done) C.Muted else C.Ink,
                        textDecoration = if (d.done) TextDecoration.LineThrough else null, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOf(d.subject, deadlineLabel(d, now)).filter { it.isNotBlank() }.joinToString(" · "),
                        fontSize = 13.sp, color = if (!d.done && d.due.isBefore(now)) C.Error else C.Muted,
                    )
                }
            }
        }
    }
}

/** Редактор дедлайна. [existing] — правим существующий, иначе создаём новый. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeadlineEditor(
    draft: Deadline,
    existing: Boolean,
    schedule: Schedule?,
    subgroup: Int,
    onSave: (Deadline) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val now = LocalDateTime.now()
    var title by remember { mutableStateOf(draft.title) }
    var subject by remember { mutableStateOf(draft.subject) }
    var due by remember { mutableStateOf(draft.due) }
    var pickDate by remember { mutableStateOf(false) }
    val subjects = schedule?.subjects().orEmpty()
    val nextPair = if (subject.isNotBlank()) schedule?.nextOf(subject, now, subgroup) else null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Ground,
        title = { Text(if (existing) "Дедлайн" else "Новый дедлайн", fontWeight = FontWeight.Bold, color = C.Ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(title, { title = it }, "Что сдать? Например: лаба №3")
                Text("Предмет", fontSize = 13.sp, color = C.Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Choice("Без предмета", subject.isBlank()) { subject = "" }
                    subjects.forEach { (short, _) -> Choice(short, subject == short) { subject = short } }
                }
                Text("Срок", fontSize = 13.sp, color = C.Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (nextPair != null) Choice("К след. паре · ${dayName(nextPair.date, false)} ${nextPair.lesson.start.format(TIME)}", due == nextPair.startAt) { due = nextPair.startAt }
                    val tomorrow = now.toLocalDate().plusDays(1).atTime(23, 59)
                    Choice("Завтра", due == tomorrow) { due = tomorrow }
                    val week = now.toLocalDate().plusWeeks(1).atTime(23, 59)
                    Choice("Через неделю", due == week) { due = week }
                    Choice("Выбрать дату…", false) { pickDate = true }
                }
                Text("Время", fontSize = 13.sp, color = C.Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(LocalTime.of(8, 30), LocalTime.of(12, 0), LocalTime.of(18, 0), LocalTime.of(23, 59)).forEach { t ->
                        Choice(t.format(TIME), due.toLocalTime() == t) { due = due.toLocalDate().atTime(t) }
                    }
                }
                Text(
                    "Срок: ${dayTitle(due.toLocalDate())}, ${due.format(TIME)}",
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = C.Ink,
                )
                if (existing) Text(
                    "Удалить дедлайн", color = C.Error, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDelete).padding(vertical = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                { onSave(draft.copy(title = title.trim(), subject = subject, due = due)) },
                enabled = title.isNotBlank(),
            ) { Text("Сохранить", color = if (title.isNotBlank()) C.Accent else C.Muted, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Отмена", color = C.Muted) } },
    )

    if (pickDate) {
        val st = rememberDatePickerState(initialSelectedDateMillis = due.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton({
                    st.selectedDateMillis?.let {
                        val d = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        due = d.atTime(due.toLocalTime())
                    }
                    pickDate = false
                }) { Text("Готово", color = C.Accent) }
            },
            dismissButton = { TextButton({ pickDate = false }) { Text("Отмена", color = C.Muted) } },
        ) { DatePicker(st) }
    }
}

@Composable
fun Choice(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) C.OnAccent else C.Ink,
        modifier = Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(50)).background(if (on) C.Accent else C.Card)
            .border(1.dp, if (on) C.Accent else C.Line, RoundedCornerShape(50)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    )
}

fun newDeadline(subject: String = "", due: LocalDateTime? = null) = Deadline(
    id = System.currentTimeMillis(),
    title = "",
    subject = subject,
    due = due ?: LocalDate.now().plusDays(1).atTime(23, 59),
    done = false,
)
