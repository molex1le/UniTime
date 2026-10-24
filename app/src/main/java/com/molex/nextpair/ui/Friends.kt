package com.molex.nextpair.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.molex.nextpair.Friend
import com.molex.nextpair.Occurrence
import com.molex.nextpair.Repo
import com.molex.nextpair.Schedule
import com.molex.nextpair.Target
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/** Общее свободное время двух людей за день. */
data class Common(val windows: List<Pair<LocalTime, LocalTime>>, val freeAfter: LocalTime?, val bothFree: Boolean)

/** Окно — перерыв длиннее обычной перемены (больше [breakMinutes]). */
fun commonWindows(a: List<Occurrence>, b: List<Occurrence>, breakMinutes: Long = 30): Common {
    val busy = (a + b).map { it.lesson.start to it.lesson.end }.sortedBy { it.first }
    if (busy.isEmpty()) return Common(emptyList(), null, true)
    // Склеиваем пересекающиеся пары в общие «занятые» отрезки.
    val merged = mutableListOf<Pair<LocalTime, LocalTime>>()
    for (iv in busy) {
        val last = merged.lastOrNull()
        if (last != null && !iv.first.isAfter(last.second)) merged[merged.lastIndex] = last.first to maxOf(last.second, iv.second)
        else merged += iv
    }
    val windows = merged.zipWithNext { x, y -> x.second to y.first }
        .filter { Duration.between(it.first, it.second).toMinutes() > breakMinutes }
    return Common(windows, merged.last().second, false)
}

@Composable
fun FriendsScreen(repo: Repo, mine: Schedule, mySubgroup: Int, now: LocalDateTime, onBack: () -> Unit, onOpen: (Target) -> Unit) {
    var friends by remember { mutableStateOf(repo.friends()) }
    var adding by remember { mutableStateOf(false) }
    val schedules = remember { mutableStateMapOf<String, Schedule?>() }
    val errors = remember { mutableStateMapOf<String, Boolean>() }
    val today = now.toLocalDate()
    val days = (0..7).map { today.plusDays(it.toLong()) }.filter { it.dayOfWeek != DayOfWeek.SUNDAY }.take(6)
    var day by remember { mutableStateOf(days.first()) }

    friends.map { it.group }.distinct().forEach { g ->
        LaunchedEffect(g) {
            if (g !in schedules) {
                runCatching { schedules[g] = withContext(Dispatchers.IO) { repo.fetchOther(Target("group", g, g, "")) } }
                    .onFailure { errors[g] = true }
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                RoundButton(Glyph.Back, "Назад", onBack)
                RoundButton(Glyph.Plus, "Добавить друга") { adding = true }
            }
        }
        item { ScreenTitle("когда вы оба свободны", "Друзья") }
        if (friends.isEmpty()) {
            item { EmptyCard("Добавь первого друга", "Нажми сюда — укажи имя и группу, и я покажу, где он сейчас и когда у вас общие окна.", Glyph.People) { adding = true } }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    days.forEach { d ->
                        val on = d == day
                        Column(
                            Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp)).background(if (on) C.Ink else C.Card)
                                .border(1.dp, if (on) C.Ink else C.Line, RoundedCornerShape(14.dp)).clickable { day = d }.padding(vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(if (d == today) "Сег" else dayName(d, false), fontSize = 12.sp, color = if (on) C.Ground.copy(alpha = 0.75f) else C.Muted)
                            Text("${d.dayOfMonth}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (on) C.Ground else C.Ink)
                        }
                    }
                }
            }
            items(friends, key = { it.name + it.group }) { f ->
                val s = schedules[f.group]
                FriendCard(
                    f, s, errors[f.group] == true, mine, mySubgroup, now, day,
                    onOpen = { onOpen(f.target) },
                    onRemove = { friends = friends - f; repo.saveFriends(friends) },
                )
            }
        }
    }

    if (adding) AddFriendDialog(onDismiss = { adding = false }) { f ->
        friends = friends + f
        repo.saveFriends(friends)
        adding = false
    }
}

@Composable
private fun FriendCard(
    f: Friend, s: Schedule?, failed: Boolean, mine: Schedule, mySubgroup: Int,
    now: LocalDateTime, day: java.time.LocalDate, onOpen: () -> Unit, onRemove: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.Card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(C.Soft), contentAlignment = Alignment.Center) {
                Text(f.name.take(1).uppercase(), color = C.SoftInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
                Text(f.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                Text("${f.group}" + if (f.subgroup != 0) " · п/г ${f.subgroup}" else "", fontSize = 13.sp, color = C.Muted)
            }
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable(onClickLabel = "Удалить", onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) { Glyph(Glyph.Trash, C.Muted, 20.dp) }
        }
        when {
            failed -> Text("Не удалось загрузить расписание группы", fontSize = 14.sp, color = C.Error)
            s == null -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = C.Accent, modifier = Modifier.size(24.dp)) }
            else -> {
                if (day == now.toLocalDate()) {
                    val (cur, next) = s.currentAndNext(now, f.subgroup)
                    val status = when {
                        cur != null -> "Сейчас на паре: ${cur.lesson.subject} ${cur.lesson.type} в ${cur.lesson.rooms.ifBlank { "—" }}, до ${cur.lesson.end.format(TIME)}"
                        next != null && next.date == day -> "Сейчас свободен · следующая пара в ${next.lesson.start.format(TIME)}"
                        else -> "Сегодня пары закончились"
                    }
                    Text(status, fontSize = 14.sp, color = C.Ink)
                }
                val my = mine.lessonsOn(day, mySubgroup)
                val their = s.lessonsOn(day, f.subgroup)
                val c = commonWindows(my, their)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Ground).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Общие окна", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = C.Muted)
                    when {
                        c.bothFree -> Text("Оба свободны весь день", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = C.Accent)
                        else -> {
                            if (c.windows.isEmpty()) Text("Между парами общих окон нет", fontSize = 14.sp, color = C.Ink)
                            c.windows.forEach { (a, b) ->
                                Text(
                                    "${a.format(TIME)}–${b.format(TIME)} · ${minutesText(Duration.between(a, b).toMinutes())}",
                                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = C.Accent,
                                )
                            }
                            c.freeAfter?.let { Text("Оба свободны после ${it.format(TIME)}", fontSize = 14.sp, color = C.Ink) }
                        }
                    }
                    Text(
                        "У тебя: ${if (my.isEmpty()) "пар нет" else "${my.first().lesson.start.format(TIME)}–${my.maxOf { it.lesson.end }.format(TIME)}"} · " +
                            "у ${f.name}: ${if (their.isEmpty()) "пар нет" else "${their.first().lesson.start.format(TIME)}–${their.maxOf { it.lesson.end }.format(TIME)}"}",
                        fontSize = 12.sp, color = C.Muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun AddFriendDialog(onDismiss: () -> Unit, onAdd: (Friend) -> Unit) {
    var name by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("") }
    var sub by remember { mutableIntStateOf(0) }
    val ok = name.isNotBlank() && group.length >= 6
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Ground,
        title = { Text("Добавить друга", fontWeight = FontWeight.Bold, color = C.Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(name, { name = it }, "Имя")
                Field(group, { group = it.filter { c -> c.isDigit() } }, "Номер группы", number = true)
                Text("Подгруппа", fontSize = 13.sp, color = C.Muted)
                Segmented(listOf("Все", "1", "2"), sub) { sub = it }
            }
        },
        confirmButton = {
            TextButton({ onAdd(Friend(name.trim(), group, sub)) }, enabled = ok) {
                Text("Добавить", color = if (ok) C.Accent else C.Muted, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Отмена", color = C.Muted) } },
    )
}
