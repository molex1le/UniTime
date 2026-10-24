package com.molex.nextpair.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.molex.nextpair.Deadline
import com.molex.nextpair.Occurrence
import com.molex.nextpair.Repo
import com.molex.nextpair.Schedule
import com.molex.nextpair.Settings
import com.molex.nextpair.Target
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDateTime

@Composable
fun RoundButton(glyph: Glyph, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(C.Card).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Glyph(glyph, C.Ink, 22.dp) }
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, placeholder: String, number: Boolean = false, leading: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 50.dp).clip(RoundedCornerShape(14.dp)).background(C.Card)
            .border(1.dp, C.Line, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        leading?.invoke()
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = C.Muted, fontSize = 15.sp)
            BasicTextField(
                value, onChange, singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = C.Ink),
                cursorBrush = SolidColor(C.Accent),
                keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** «221-5 к.» → корпус 5, 2 этаж. */
fun roomPlace(room: String): Pair<String, String>? {
    val m = Regex("""(\d+)[а-яА-Я]?-(\d+)""").find(room) ?: return null
    val num = m.groupValues[1].toIntOrNull() ?: return null
    val corpus = m.groupValues[2]
    val floor = if (num >= 100) num / 100 else null
    return corpus to (floor?.let { "$it этаж" } ?: "")
}

// ---------- Карточка пары ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LessonScreen(
    o: Occurrence,
    own: Boolean,
    repo: Repo,
    st: Settings,
    deadlines: List<Deadline>,
    now: LocalDateTime,
    onBack: () -> Unit,
    onTeacher: (Target) -> Unit,
    onGroup: (Target) -> Unit,
    onRemindersChanged: () -> Unit,
    onHidden: () -> Unit,
    onAddDeadline: () -> Unit,
    onDeadline: (Deadline) -> Unit,
) {
    val ctx = LocalContext.current
    val l = o.lesson
    var note by remember(l.key) { mutableStateOf(repo.note(l.key)) }
    var remind by remember(l.key) { mutableStateOf(l.key in repo.reminders()) }
    val (_, _, typeName) = typeStyle(l.type)
    val whenText = when {
        !o.startAt.isAfter(now) && o.endAt.isAfter(now) -> "идёт, ещё ${minutesText(Duration.between(now, o.endAt).toMinutes() + 1)}"
        o.endAt.isBefore(now) -> "уже прошла"
        else -> untilText(o, now)
    }
    val mine = deadlines.filter { it.subject == l.subject && !it.done }
    val place = roomPlace(l.rooms)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { RoundButton(Glyph.Back, "Назад", onBack) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TypeChip(l.type, full = true)
                    if (l.subgroup != 0) Pill("Подгруппа ${l.subgroup}", C.Card, C.Ink, border = true)
                    Settings.pairNumber(l.start)?.let { Pill("$it пара", C.Card, C.Ink, border = true) }
                }
                Text(l.subjectFull.ifBlank { l.subject }, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = C.Ink, lineHeight = 33.sp)
                Text("${dayTitle(o.date)} · $whenText", fontSize = 15.sp, color = C.Muted)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoTile(Modifier.weight(1f), "Время", "${l.start.format(TIME)}–${l.end.format(TIME)}")
                InfoTile(Modifier.weight(1f), "Аудитория", l.rooms.ifBlank { "—" })
            }
        }
        if (place != null) item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card).clickable {
                    val q = Uri.encode("БГУИР учебный корпус ${place.first}, Минск")
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$q"))) }
                }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Glyph(Glyph.Pin, C.Accent, 22.dp)
                Column(Modifier.weight(1f)) {
                    Text(listOf("Корпус ${place.first}", place.second).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
                    Text("Открыть на карте", fontSize = 13.sp, color = C.Muted)
                }
                Glyph(Glyph.Chevron, C.Chevron, 18.dp)
            }
        }
        items(l.teachers) { t ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.Card).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Преподаватель", fontSize = 12.sp, color = C.Muted, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TeacherPhoto(t, 76.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(t.full, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = C.Ink, lineHeight = 22.sp)
                        if (t.title.isNotBlank()) Text(t.title, fontSize = 13.sp, color = C.Muted)
                    }
                }
                if (t.urlId.isNotBlank()) {
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp)).background(C.Ground)
                            .clickable { onTeacher(Target("teacher", t.urlId, t.full, t.title)) },
                        contentAlignment = Alignment.Center,
                    ) { Text("Расписание и где сейчас", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.Ink) }
                }
            }
        }
        if (!own && l.groups.isNotBlank()) item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.Card).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Группы", fontSize = 12.sp, color = C.Muted, fontWeight = FontWeight.Medium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    l.groups.split(", ").forEach { g -> Pill(g, C.Ground, C.Ink) { onGroup(Target("group", g, g, "")) } }
                }
            }
        }
        if (own) {
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.Card).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Дедлайны по предмету", fontSize = 12.sp, color = C.Muted, fontWeight = FontWeight.Medium)
                        Text(
                            "+ Добавить", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.Accent,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAddDeadline).padding(6.dp),
                        )
                    }
                    if (mine.isEmpty()) Text("Пока ничего не нужно сдавать", fontSize = 14.sp, color = C.Ink)
                    mine.forEach { d ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onDeadline(d) }.padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(d.title, fontSize = 15.sp, color = C.Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(deadlineLabel(d, now), fontSize = 13.sp, color = if (d.due.isBefore(now)) C.Error else C.Muted)
                        }
                    }
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.Card).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Заметка к паре", fontSize = 12.sp, color = C.Muted, fontWeight = FontWeight.Medium)
                    Field(note, { note = it; repo.setNote(l.key, it) }, "Например: взять ноутбук")
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Напомнить за ${st.remindMinutes} минут", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = C.Ink)
                        Text("Каждую неделю для этой пары", fontSize = 12.sp, color = C.Muted)
                    }
                    Switch(
                        remind,
                        { remind = it; repo.setReminder(l.key, it); onRemindersChanged() },
                        colors = SwitchDefaults.colors(checkedTrackColor = C.Accent, checkedThumbColor = C.OnAccent),
                    )
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card),
                ) {
                    HideRow("Скрыть эту пару", "${dayName(o.date, false)} ${l.start.format(TIME)} каждую неделю") {
                        st.hiddenSubjects = st.hiddenSubjects + l.key; onHidden()
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(C.Divider))
                    HideRow("Скрыть предмет целиком", "Все пары «${l.subject}»") {
                        st.hiddenSubjects = st.hiddenSubjects + "S:${l.subject}"; onHidden()
                    }
                }
            }
            item { Text("Скрытые пары можно вернуть в настройках.", fontSize = 12.sp, color = C.Muted) }
        }
    }
}

@Composable
private fun HideRow(title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Glyph(Glyph.EyeOff, C.Muted, 22.dp)
        Column {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = C.Ink)
            Text(sub, fontSize = 12.sp, color = C.Muted)
        }
    }
}

// ---------- Поиск ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(repo: Repo, onOpen: (Target) -> Unit, onFriends: () -> Unit) {
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    var groups by remember { mutableStateOf<List<Target>?>(null) }
    var teachers by remember { mutableStateOf<List<Target>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val favs = remember(query) { repo.favorites() }
    val recents = remember(query) { repo.recents() }
    val friends = remember { repo.friends() }

    LaunchedEffect(tab) {
        runCatching {
            if (tab == 0 && groups == null) groups = withContext(Dispatchers.IO) { repo.allGroups() }
            if (tab == 1 && teachers == null) teachers = withContext(Dispatchers.IO) { repo.allTeachers() }
            error = null
        }.onFailure { error = "Нет связи с iis.bsuir.by" }
    }

    val q = query.trim().lowercase()
    val source = if (tab == 0) groups else teachers
    val results = if (q.isEmpty()) emptyList() else source.orEmpty().filter { it.title.lowercase().contains(q) || it.sub.lowercase().contains(q) }.take(60)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenTitle(null, "Поиск") }
        item {
            Field(query, { query = it }, if (tab == 0) "Номер группы" else "Фамилия преподавателя", number = tab == 0, leading = { Glyph(Glyph.Search, C.Muted, 20.dp) })
        }
        item { Segmented(listOf("Группы", "Преподаватели"), tab) { tab = it } }
        if (error != null) item { Text(error!!, color = C.Error, fontSize = 14.sp) }
        if (q.isNotEmpty()) {
            if (source == null && error == null) item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = C.Accent) } }
            else if (results.isEmpty()) item { Text("Ничего не нашлось", color = C.Muted, fontSize = 14.sp) }
            else item { TargetList(results, onOpen) }
        } else {
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Hero).clickable(onClick = onFriends).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Glyph(Glyph.People, C.HeroBar, 26.dp)
                    Column(Modifier.weight(1f)) {
                        Text("Друзья и общие окна", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
                        Text(
                            if (friends.isEmpty()) "Узнай, когда вы оба свободны" else friends.joinToString(", ") { it.name },
                            fontSize = 13.sp, color = C.HeroMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Glyph(Glyph.Chevron, C.HeroMuted, 18.dp)
                }
            }
            if (favs.isNotEmpty()) {
                item { SectionLabel("Избранное") }
                item { TargetList(favs, onOpen) }
            }
            if (recents.isNotEmpty()) {
                item { SectionLabel("Недавние") }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        recents.forEach { t -> Pill(if (t.isGroup) t.title else shortName(t.title), C.Card, C.Ink, border = true) { onOpen(t) } }
                    }
                }
            }
        }
    }
}

fun shortName(full: String): String {
    val p = full.split(" ")
    return if (p.size >= 3) "${p[0]} ${p[1].take(1)}. ${p[2].take(1)}." else full
}

@Composable
private fun SectionLabel(text: String) =
    Text(text.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Muted, letterSpacing = 0.5.sp)

@Composable
private fun TargetList(list: List<Target>, onOpen: (Target) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card)) {
        list.forEachIndexed { i, t ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.Divider))
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(t) }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val ab = if (t.isGroup) t.title.take(2) else t.title.split(" ").let { "${it.getOrNull(1)?.take(1) ?: ""}${it[0].take(1)}" }
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if (t.isGroup) C.Ink else C.Soft),
                    contentAlignment = Alignment.Center,
                ) { Text(ab, color = if (t.isGroup) C.Ground else C.SoftInk, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(t.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (t.sub.isNotBlank()) Text(t.sub, fontSize = 13.sp, color = C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Glyph(Glyph.Chevron, C.Chevron, 18.dp)
            }
        }
    }
}

// ---------- Чужое расписание ----------

@Composable
fun OtherScreen(t: Target, repo: Repo, now: LocalDateTime, onBack: () -> Unit, onLesson: (Occurrence) -> Unit) {
    var schedule by remember(t) { mutableStateOf<Schedule?>(null) }
    var error by remember(t) { mutableStateOf<String?>(null) }
    var fav by remember(t) { mutableStateOf(repo.isFavorite(t)) }
    LaunchedEffect(t) {
        runCatching { schedule = withContext(Dispatchers.IO) { repo.fetchOther(t) } }
            .onFailure { error = "Не удалось загрузить: ${it.message}" }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RoundButton(Glyph.Back, "Назад", onBack)
            RoundButton(if (fav) Glyph.StarFilled else Glyph.Star, if (fav) "Убрать из избранного" else "В избранное") {
                repo.toggleFavorite(t); fav = !fav
            }
        }
        val s = schedule
        when {
            error != null -> Text(error!!, Modifier.padding(20.dp), color = C.Muted)
            s == null -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = C.Accent) }
            else -> ScheduleFeed(
                s, 0, now, emptyList(),
                showGroups = !t.isGroup,
                withExams = false,
                title = { ScreenTitle(if (t.isGroup) t.sub.ifBlank { null } else t.sub.ifBlank { null }, if (t.isGroup) "Группа ${t.title}" else shortName(t.title)) },
                extraCard = if (t.isGroup) null else ({
                    val (cur, next) = s.currentAndNext(now, 0)
                    val text = when {
                        cur != null -> "Сейчас в ${cur.lesson.rooms.ifBlank { "—" }} · ${cur.lesson.subject} до ${cur.lesson.end.format(TIME)}"
                        next != null -> "Сейчас пар нет · следующая ${untilText(next, now)}, ${next.lesson.rooms.ifBlank { "—" }}"
                        else -> "Пар в ближайшие недели нет"
                    }
                    Text(text, fontSize = 14.sp, color = C.Ink, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Card).padding(14.dp))
                }),
                onLesson = onLesson,
            )
        }
    }
}
