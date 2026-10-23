package com.molex.nextpair.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.molex.nextpair.Deadline
import com.molex.nextpair.Occurrence
import com.molex.nextpair.Schedule
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val RU = Locale("ru")

private val MONTHS = listOf(
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря",
)

fun month(d: LocalDate) = MONTHS[d.monthValue - 1]

fun dayName(d: LocalDate, full: Boolean = true) =
    d.dayOfWeek.getDisplayName(if (full) TextStyle.FULL else TextStyle.SHORT, RU).replaceFirstChar { it.uppercase() }

fun dayTitle(d: LocalDate) = "${dayName(d)}, ${d.dayOfMonth} ${month(d)}"

fun minutesText(min: Long): String {
    val h = min / 60
    val m = min % 60
    return when {
        h == 0L -> "$m мин"
        m == 0L -> "$h ч"
        else -> "$h ч $m мин"
    }
}

fun untilText(o: Occurrence, now: LocalDateTime): String {
    val today = now.toLocalDate()
    val t = o.lesson.start.format(TIME)
    return when (o.date) {
        today -> "через ${minutesText(Duration.between(now, o.startAt).toMinutes() + 1)}"
        today.plusDays(1) -> "завтра в $t"
        else -> "${dayName(o.date, false)}, ${o.date.dayOfMonth} ${month(o.date)} в $t"
    }
}

fun pairs(n: Int) = when {
    n % 10 == 1 && n % 100 != 11 -> "$n пара"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "$n пары"
    else -> "$n пар"
}

fun mondayOf(d: LocalDate): LocalDate =
    // В воскресенье показываем уже следующую неделю.
    if (d.dayOfWeek == DayOfWeek.SUNDAY) d.plusDays(1) else d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** Минуты между концом предыдущей пары и началом этой (может быть ≤ 0 у параллельных подгрупп). */
fun gapBefore(list: List<Occurrence>, i: Int): Long? =
    if (i == 0) null else Duration.between(list[i - 1].endAt, list[i].startAt).toMinutes()

/** Свободное время между парами длиннее обычной перемены (30 мин у БГУИР). */
fun windowBefore(list: List<Occurrence>, i: Int): Long? = gapBefore(list, i)?.takeIf { it > 30 }

/** Что рисовать между парами: обычную перемену, окно или ничего. */
@Composable
fun Between(gap: Long?) {
    when {
        gap == null || gap <= 0 -> {}
        gap > 30 -> WindowRow(gap)
        else -> BreakRow(gap)
    }
}

@Composable
fun BreakRow(minutes: Long) {
    Text(
        "перемена $minutes мин",
        fontSize = 11.sp, color = C.Muted, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    )
}

@Composable
fun EmptyCard(title: String, text: String, glyph: Glyph = Glyph.Calendar) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.Card).padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(C.Ground), contentAlignment = Alignment.Center) {
            Glyph(glyph, C.Muted, 28.dp)
        }
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C.Ink, textAlign = TextAlign.Center)
        Text(text, fontSize = 14.sp, color = C.Muted, textAlign = TextAlign.Center, lineHeight = 20.sp)
    }
}

@Composable
fun WindowRow(minutes: Long) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(C.Line))
        Text("Окно ${minutesText(minutes)}", fontSize = 12.sp, color = C.Muted, fontWeight = FontWeight.SemiBold)
        Box(Modifier.weight(1f).height(1.dp).background(C.Line))
    }
}

fun deadlineLabel(d: Deadline, now: LocalDateTime): String {
    val days = ChronoUnit.DAYS.between(now.toLocalDate(), d.due.toLocalDate())
    val t = d.due.format(TIME)
    return when {
        d.due.isBefore(now) -> "просрочен"
        days == 0L -> "сегодня до $t"
        days == 1L -> "завтра до $t"
        days < 7 -> "${dayName(d.due.toLocalDate(), false).lowercase()}, до $t"
        else -> "${d.due.dayOfMonth} ${month(d.due.toLocalDate())}"
    }
}

// ---------- Сегодня ----------

@Composable
fun TodayScreen(
    s: Schedule,
    subgroup: Int,
    group: String,
    now: LocalDateTime,
    deadlines: List<Deadline>,
    onSettings: () -> Unit,
    onAccount: () -> Unit,
    onDeadlines: () -> Unit,
    onLesson: (Occurrence) -> Unit,
) {
    val today = now.toLocalDate()
    val monday = mondayOf(today)
    var selected by remember { mutableStateOf(if (today.dayOfWeek == DayOfWeek.SUNDAY) monday else today) }
    val lessons = s.lessonsOn(selected, subgroup)
    val (cur, next) = s.currentAndNext(now, subgroup)
    val soon = deadlines.filter { !it.done && it.due.isBefore(now.plusDays(4)) }.take(3)

    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                ScreenTitle(dayTitle(today), "Сегодня")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoundButton(Glyph.User, "Личный кабинет", onAccount)
                    RoundButton(Glyph.Gear, "Настройки", onSettings)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("${s.weekNumber(selected)} неделя", C.Soft, C.SoftInk)
                Pill(if (subgroup == 0) group else "$group · п/г $subgroup", C.Card, C.Ink, border = true, onClick = onSettings)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0..5) {
                    val d = monday.plusDays(i.toLong())
                    val on = d == selected
                    Column(
                        Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (on) C.Ink else C.Card)
                            .border(1.dp, if (on) C.Ink else C.Line, RoundedCornerShape(14.dp))
                            .clickable { selected = d }.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(dayName(d, false), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (on) C.Ground.copy(alpha = 0.75f) else C.Muted)
                        Text("${d.dayOfMonth}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (on) C.Ground else C.Ink)
                    }
                }
            }
        }
        if (selected == today) {
            val hero = cur ?: next
            if (hero != null) item { HeroCard(hero, isNow = cur != null, now = now, onClick = { onLesson(hero) }) }
        }
        if (soon.isNotEmpty()) item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card).clickable(onClick = onDeadlines).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Glyph(Glyph.Flag, C.Accent, 18.dp)
                    Text("Скоро дедлайны", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                }
                soon.forEach { d ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(d.title, fontSize = 14.sp, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(deadlineLabel(d, now), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (d.due.isBefore(now)) C.Error else C.Muted)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(if (selected == today) "Пары на сегодня" else dayTitle(selected), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                if (lessons.isNotEmpty()) {
                    Text("${pairs(lessons.size)} · до ${lessons.maxOf { it.lesson.end }.format(TIME)}", fontSize = 13.sp, color = C.Muted)
                }
            }
        }
        if (lessons.isEmpty()) item { EmptyCard("Пар нет", "Можно выдохнуть. Дальше — на вкладке «Пары».") }
        if (lessons.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                lessons.forEachIndexed { i, o ->
                    Between(gapBefore(lessons, i))
                    val dl = deadlines.count { !it.done && it.subject == o.lesson.subject && !it.due.isBefore(now) }
                    LessonRow(
                        o, faded = o.endAt.isBefore(now), highlighted = cur == o, showGroups = false,
                        extra = if (dl > 0) "Дедлайнов по предмету: $dl" else null,
                    ) { onLesson(o) }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(o: Occurrence, isNow: Boolean, now: LocalDateTime, onClick: () -> Unit) {
    val l = o.lesson
    val (_, _, typeName) = typeStyle(l.type)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.Hero).clickable(onClick = onClick).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (isNow) C.Live else C.HeroBar))
                Text(if (isNow) "Идёт сейчас" else "Следующая пара", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.HeroLabel)
            }
            Text(
                if (isNow) "ещё ${minutesText(Duration.between(now, o.endAt).toMinutes() + 1)}" else untilText(o, now),
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.HeroMuted,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(l.subjectFull.ifBlank { l.subject }, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, lineHeight = 26.sp)
            Text(listOf(typeName, l.rooms, l.teacher).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 14.sp, color = C.HeroMuted)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val total = Duration.between(o.startAt, o.endAt).toMinutes().coerceAtLeast(1)
            val done = if (isNow) Duration.between(o.startAt, now).toMinutes() else 0
            ProgressBar(done.toFloat() / total, C.HeroTrack, C.HeroBar)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(l.start.format(TIME), fontSize = 12.sp, color = C.HeroMuted)
                Text(l.end.format(TIME), fontSize = 12.sp, color = C.HeroMuted)
            }
        }
    }
}

// ---------- Лента «Пары» до конца семестра ----------

private sealed interface FeedItem {
    val key: String
    data class WeekHead(val monday: LocalDate, val n: Int) : FeedItem { override val key = "w$monday" }
    data class Day(val date: LocalDate, val list: List<Occurrence>) : FeedItem { override val key = "d$date" }
    data class Free(val date: LocalDate) : FeedItem { override val key = "f$date" }
    data class Section(val title: String) : FeedItem { override val key = "s$title" }
}

private fun buildFeed(s: Schedule, subgroup: Int, today: LocalDate, withExams: Boolean): List<FeedItem> {
    val out = mutableListOf<FeedItem>()
    var monday = mondayOf(today)
    val last = listOfNotNull(s.termEnd, if (withExams) s.examsEnd else null).maxOrNull()
        ?.takeIf { it.isAfter(today) } ?: today.plusWeeks(4)
    val examDays = if (withExams) s.exams.map { it.date }.toSet() else emptySet()
    while (!monday.isAfter(last)) {
        out += FeedItem.WeekHead(monday, s.weekNumber(monday))
        for (i in 0..6) {
            val d = monday.plusDays(i.toLong())
            if (d.isBefore(today) || d.isAfter(last)) continue
            val list = s.lessonsOn(d, subgroup) + s.exams.filter { withExams && it.date == d }
            when {
                list.isNotEmpty() -> out += FeedItem.Day(d, list.sortedBy { it.startAt })
                d.dayOfWeek != DayOfWeek.SUNDAY && d !in examDays -> out += FeedItem.Free(d)
            }
        }
        monday = monday.plusWeeks(1)
    }
    return out
}

@Composable
fun FeedScreen(s: Schedule, subgroup: Int, now: LocalDateTime, deadlines: List<Deadline>, onLesson: (Occurrence) -> Unit) {
    ScheduleFeed(s, subgroup, now, deadlines, showGroups = false, withExams = true, onLesson = onLesson)
}

/** Бесконечная лента с переключателем недель 1–4; используется и для чужих расписаний. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScheduleFeed(
    s: Schedule,
    subgroup: Int,
    now: LocalDateTime,
    deadlines: List<Deadline>,
    showGroups: Boolean,
    withExams: Boolean,
    title: (@Composable () -> Unit)? = null,
    extraCard: (@Composable () -> Unit)? = null,
    onLesson: (Occurrence) -> Unit,
) {
    val today = now.toLocalDate()
    val feed = remember(s, subgroup, today) { buildFeed(s, subgroup, today, withExams) }
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Сколько элементов идёт до ленты (заголовок, extra) — считаем по ключам.
    val headIndex = remember(feed) { feed.withIndex().filter { it.value is FeedItem.WeekHead }.map { it.index to (it.value as FeedItem.WeekHead) } }
    val prefix = (if (title != null) 1 else 0) + if (extraCard != null) 1 else 0
    val visibleWeek by remember(feed) {
        derivedStateOf {
            val i = state.firstVisibleItemIndex - prefix
            headIndex.lastOrNull { it.first <= i }?.second?.n ?: s.weekNumber(today)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
                Segmented((1..4).map { "$it нед." }, visibleWeek - 1) { pick ->
                    val target = headIndex.firstOrNull { it.second.n == pick + 1 }
                    if (target != null) scope.launch { state.animateScrollToItem(target.first + prefix) }
                }
            }
            LazyColumn(
                Modifier.fillMaxSize(), state = state,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (title != null) item(key = "title") { Box(Modifier.padding(top = 8.dp)) { title() } }
                if (extraCard != null) item(key = "extra") { extraCard() }
                if (feed.none { it is FeedItem.Day }) item { EmptyCard("Пар не найдено", "Похоже, каникулы или расписание ещё не опубликовали.") }
                feed.forEach { f ->
                    when (f) {
                        is FeedItem.WeekHead -> stickyHeader(key = f.key) {
                            val end = f.monday.plusDays(5)
                            Text(
                                "${f.n} неделя · ${f.monday.dayOfMonth} ${month(f.monday)} – ${end.dayOfMonth} ${month(end)}",
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.SoftInk,
                                modifier = Modifier.fillMaxWidth().background(C.Ground).padding(vertical = 8.dp)
                                    .clip(RoundedCornerShape(10.dp)).background(C.Soft).padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                        is FeedItem.Free -> item(key = f.key) {
                            Text(
                                "${dayName(f.date, false)}, ${f.date.dayOfMonth} ${month(f.date)} · пар нет",
                                fontSize = 13.sp, color = C.Muted, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                        }
                        is FeedItem.Section -> item(key = f.key) { Text(f.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C.Ink) }
                        is FeedItem.Day -> item(key = f.key) { DayBlock(f.date, f.list, now, deadlines, showGroups, onLesson) }
                    }
                }
            }
        }
        val away by remember { derivedStateOf { state.firstVisibleItemIndex > 3 } }
        if (away) {
            Text(
                "К сегодня",
                color = C.OnAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).clip(RoundedCornerShape(50)).background(C.Accent)
                    .clickable { scope.launch { state.animateScrollToItem(0) } }.padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun DayBlock(date: LocalDate, list: List<Occurrence>, now: LocalDateTime, deadlines: List<Deadline>, showGroups: Boolean, onLesson: (Occurrence) -> Unit) {
    val isToday = date == now.toLocalDate()
    val due = deadlines.filter { !it.done && it.due.toLocalDate() == date }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(
                "${dayName(date)}, ${date.dayOfMonth} ${month(date)}" + if (isToday) " · сегодня" else "",
                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (isToday) C.Accent else C.Ink,
            )
            Text("${pairs(list.size)} · ${list.first().lesson.start.format(TIME)}–${list.maxOf { it.lesson.end }.format(TIME)}", fontSize = 12.sp, color = C.Muted)
        }
        due.forEach { d ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Soft).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Glyph(Glyph.Flag, C.SoftInk, 16.dp)
                Text("${d.title} · до ${d.due.format(TIME)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.SoftInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Card)) {
            list.forEachIndexed { i, o ->
                val gap = gapBefore(list, i)
                when {
                    gap == null -> {}
                    gap <= 0 -> Box(Modifier.fillMaxWidth().height(1.dp).background(C.Divider))
                    else -> Box(Modifier.padding(vertical = 2.dp)) { Between(gap) }
                }
                CompactRow(o, showGroups, faded = o.endAt.isBefore(now)) { onLesson(o) }
            }
        }
    }
}

@Composable
private fun CompactRow(o: Occurrence, showGroups: Boolean, faded: Boolean, onClick: () -> Unit) {
    val l = o.lesson
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.width(46.dp), verticalArrangement = Arrangement.Center) {
            Text(l.start.format(TIME), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (faded) C.Muted else C.Ink)
            Text(l.end.format(TIME), fontSize = 12.sp, color = C.Muted)
        }
        Column(Modifier.weight(1f)) {
            Text(l.title(), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (faded) C.Muted else C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val n = com.molex.nextpair.Settings.pairNumber(l.start)
            val sub = listOfNotNull(
                if (C.pairNumbers && n != null) "$n пара" else null,
                if (showGroups) l.groups.ifBlank { null } else null,
                if (l.subgroup != 0) "п/г ${l.subgroup}" else null,
            ).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = C.Muted, maxLines = 1)
        }
        Text(l.rooms.removeSuffix(" к.").ifBlank { "—" }, fontSize = 12.sp, color = C.Muted)
        TypeChip(l.type)
    }
}

// ---------- Сессия ----------

@Composable
fun ExamsScreen(s: Schedule, now: LocalDateTime, onLesson: (Occurrence) -> Unit) {
    val today = now.toLocalDate()
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ScreenTitle(s.termStart?.let { "Семестр ${it.year}" }, "Сессия") }
        val start = s.examsStart
        if (start != null) item {
            val days = ChronoUnit.DAYS.between(today, start)
            val ts = s.termStart
            val te = s.termEnd
            val frac = if (ts != null && te != null) ChronoUnit.DAYS.between(ts, today).toFloat() / ChronoUnit.DAYS.between(ts, te).coerceAtLeast(1) else 0f
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.Hero).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (days > 0) "$days" else "0", fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, lineHeight = 64.sp)
                    Text(if (days > 0) "дней до сессии" else "сессия идёт", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = C.HeroMuted, modifier = Modifier.padding(bottom = 8.dp))
                }
                if (ts != null && te != null) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProgressBar(frac, C.HeroTrack, C.HeroBar)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${ts.dayOfMonth} ${month(ts).take(3)}", fontSize = 12.sp, color = C.HeroMuted)
                        Text("пройдено ${(frac.coerceIn(0f, 1f) * 100).toInt()}%", fontSize = 12.sp, color = C.HeroMuted)
                        Text("${te.dayOfMonth} ${month(te).take(3)}", fontSize = 12.sp, color = C.HeroMuted)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val es = s.examsStart
                val ee = s.examsEnd
                InfoTile(Modifier.weight(1f), "Сессия", if (es != null && ee != null) "${es.dayOfMonth} ${month(es).take(3)} – ${ee.dayOfMonth} ${month(ee).take(3)}" else "—")
                InfoTile(Modifier.weight(1f), "Последняя пара", s.termEnd?.let { "${it.dayOfMonth} ${month(it)}" } ?: "—")
            }
        }
        if (s.exams.isEmpty()) {
            item { EmptyCard("Экзаменов пока нет", "Как только деканат опубликует расписание сессии, оно появится здесь.") }
        } else {
            items(s.exams) { o ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(dayTitle(o.date), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                    LessonRow(o, faded = o.endAt.isBefore(now), highlighted = false, showGroups = false) { onLesson(o) }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
fun InfoTile(modifier: Modifier, label: String, value: String) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(C.Card).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, fontSize = 12.sp, color = C.Muted, fontWeight = FontWeight.Medium)
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C.Ink)
    }
}
