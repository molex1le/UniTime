package com.molex.nextpair.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.molex.nextpair.Iis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

private fun JSONObject.s(key: String): String =
    if (!has(key) || isNull(key)) "" else optString(key).takeIf { it != "null" } ?: ""

private fun num(v: String): Double? = v.replace(',', '.').toDoubleOrNull()

private fun fmt1(d: Double) = String.format(Locale.US, "%.1f", d).removeSuffix(".0")

// ---------- Профиль ----------

@Composable
fun ProfileView(o: JSONObject, iis: Iis) {
    var photo by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(Unit) {
        photo = withContext(Dispatchers.IO) {
            runCatching {
                val url = o.s("photoUrl")
                val bytes = if (url.startsWith("http")) com.molex.nextpair.Repo.getBytes(url) else null
                bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    val fio = o.s("fio").ifBlank { listOf(o.s("lastName"), o.s("firstName"), o.s("middleName")).joinToString(" ").trim() }
    val initials = fio.split(" ").take(2).joinToString("") { it.take(1) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.Hero).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val p = photo
                if (p != null) Image(p, "Фото", Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)), contentScale = ContentScale.Crop)
                else Box(Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(C.HeroTrack), contentAlignment = Alignment.Center) {
                    Text(initials, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(fio, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp)
                    Text(
                        listOf(o.s("group").let { if (it.isNotBlank()) "Группа $it" else "" }, if (o.s("accountType") == "STUDENT") "студент" else "")
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        color = C.HeroMuted, fontSize = 14.sp,
                    )
                }
            }
            if (o.optBoolean("isGroupHead")) Pill("Староста", C.HeroTrack, Color.White)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoLine("Студенческий / логин", o.s("username"))
            InfoLine("Почта", o.s("email"))
            InfoLine("Телефон", o.s("phone"))
            val ban = o.s("ldapBanDate")
            if (ban.isNotBlank()) InfoLine("Учётная запись активна до", prettyValue(ban))
        }
        if (o.optBoolean("hasNotConfirmedContact")) Text("На сайте есть неподтверждённый контакт — подтверди его на iis.bsuir.by.", fontSize = 13.sp, color = C.Error)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    if (value.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 12.sp, color = C.Muted)
        Text(value, fontSize = 15.sp, color = C.Ink, fontWeight = FontWeight.Medium)
    }
}

// ---------- Зачётка ----------

@Composable
fun MarkbookView(o: JSONObject) {
    val pages = o.optJSONObject("markPages") ?: JSONObject()
    val sems = pages.keys().asSequence().toList().sortedBy { it.toIntOrNull() ?: 99 }
    // По умолчанию — последний семестр, где уже есть оценки.
    val lastWithMarks = sems.lastOrNull { k ->
        val arr = pages.optJSONObject(k)?.optJSONArray("marks") ?: JSONArray()
        (0 until arr.length()).any { arr.getJSONObject(it).s("mark").isNotBlank() }
    } ?: sems.firstOrNull()
    var sel by remember { mutableStateOf(lastWithMarks) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.Hero).padding(20.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(o.s("averageMark").ifBlank { "—" }, color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 48.sp)
            Column(Modifier.padding(bottom = 6.dp)) {
                Text("средний балл", color = C.HeroMuted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (o.s("number").isNotBlank()) Text("зачётка № ${o.s("number")}", color = C.HeroMuted, fontSize = 12.sp)
            }
        }
        if (sems.isEmpty()) {
            EmptyCard("Зачётка пустая", "Оценок пока нет.")
            return@Column
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            sems.forEach { k -> Choice("$k семестр", sel == k) { sel = k } }
        }
        val page = sel?.let { pages.optJSONObject(it) } ?: return@Column
        val marks = page.optJSONArray("marks") ?: JSONArray()
        val items = (0 until marks.length()).map { marks.getJSONObject(it) }
        val withMark = items.count { it.s("mark").isNotBlank() }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoTile(Modifier.weight(1f), "Средний за семестр", page.s("averageMark").ifBlank { "—" })
            InfoTile(Modifier.weight(1f), "Сдано", "$withMark из ${items.size}")
        }
        if (withMark == 0 && items.isNotEmpty()) {
            Text("Семестр ещё не закрыт — вот что предстоит сдавать.", fontSize = 13.sp, color = C.Muted)
        }
        val order = listOf("Экзамен", "Дифф. зачет", "Зачет")
        items.sortedBy { order.indexOf(it.s("formOfControl")).let { i -> if (i < 0) 9 else i } }.forEach { SubjectMark(it) }
    }
}

@Composable
private fun SubjectMark(m: JSONObject) {
    val form = m.s("formOfControl")
    val (bg, fg) = when {
        form.startsWith("Экзамен") -> typeStyle("Экз").let { it.first to it.second }
        form.startsWith("Дифф") -> typeStyle("ЛР").let { it.first to it.second }
        else -> typeStyle("ЛК").let { it.first to it.second }
    }
    val mark = m.s("mark")
    val common = num(m.s("commonMark"))
    val retakesPct = num(m.s("commonRetakes"))
    val myRetakes = m.optInt("retakesCount", 0)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(m.s("fullSubject").ifBlank { m.s("subject") }, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.Ink, lineHeight = 20.sp)
                if (form.isNotBlank()) Text(
                    form, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(if (mark.isBlank()) C.Ground else C.Soft),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when {
                        mark.isBlank() -> "—"
                        mark.equals("зачтено", true) || mark == "+" -> "✓"
                        else -> mark
                    },
                    fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = if (mark.isBlank()) C.Muted else C.SoftInk,
                )
            }
        }
        val meta = listOfNotNull(
            m.s("date").ifBlank { null }?.let { prettyValue(it) },
            m.s("teacher").ifBlank { null },
        )
        if (meta.isNotEmpty()) Text(meta.joinToString(" · "), fontSize = 13.sp, color = C.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (common != null && common > 0) Stat("Средний по потоку", fmt1(common))
            if (retakesPct != null) Stat("Пересдают", "${fmt1(retakesPct)}%")
            if (myRetakes > 0) Stat("Твои пересдачи", "$myRetakes")
        }
        val hours = num(m.s("hours"))
        val credits = num(m.s("credits"))
        if (hours != null || credits != null) Text(
            listOfNotNull(hours?.let { "${fmt1(it)} ч" }, credits?.let { "${fmt1(it)} з.е." }).joinToString(" · "),
            fontSize = 12.sp, color = C.Muted,
        )
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(Modifier.clip(RoundedCornerShape(10.dp)).background(C.Ground).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.Ink)
        Text(label, fontSize = 11.sp, color = C.Muted)
    }
}

// ---------- Разбивка по семестрам для остальных разделов ----------

private val DATE_KEYS = listOf("date", "dateFrom", "startDate", "createdDate", "dateStart", "orderDate", "dateOrder")

/** Первая дата, найденная в объекте (в том числе во вложенных объектах вроде приказа). */
private fun findDate(o: JSONObject): LocalDate? {
    for (k in DATE_KEYS) if (o.has(k)) parseDate(o.s(k))?.let { return it }
    for (k in o.keys()) (o.opt(k) as? JSONObject)?.let { findDate(it) }?.let { return it }
    return null
}

fun parseDate(s: String): LocalDate? {
    if (s.isBlank()) return null
    s.toLongOrNull()?.takeIf { it > 1_000_000_000_000L }?.let {
        return java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    }
    Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(s)?.let { return LocalDate.of(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt()) }
    Regex("""^(\d{2})\.(\d{2})\.(\d{4})""").find(s)?.let { return LocalDate.of(it.groupValues[3].toInt(), it.groupValues[2].toInt(), it.groupValues[1].toInt()) }
    return null
}

fun prettyValue(s: String): String = parseDate(s)?.let { "${it.dayOfMonth} ${month(it)} ${it.year}" } ?: s

/** Осень: сентябрь–январь (с сессией), весна: февраль–август. */
fun semesterOf(d: LocalDate): String = when {
    d.monthValue >= 9 -> "Осень ${d.year}"
    d.monthValue == 1 -> "Осень ${d.year - 1}"
    else -> "Весна ${d.year}"
}

private fun semesterSortKey(label: String): Int {
    val year = label.substringAfter(' ').toIntOrNull() ?: 0
    return year * 2 + if (label.startsWith("Осень")) 1 else 0
}

/** Ищет главный список в ответе: сам массив или первый массив объектов внутри. */
private fun mainList(v: Any): JSONArray? = when (v) {
    is JSONArray -> v
    is JSONObject -> v.keys().asSequence().mapNotNull { v.opt(it) as? JSONArray }.firstOrNull { it.length() > 0 && it.opt(0) is JSONObject }
        ?: v.keys().asSequence().mapNotNull { (v.opt(it) as? JSONObject)?.let { o -> mainList(o) } }.firstOrNull()
    else -> null
}

/** Список записей, разложенный по семестрам; если дат нет — обычный вид. */
@Composable
fun BySemesterView(v: Any, render: @Composable (Any, Int) -> Unit) {
    val list = mainList(v)
    val items = list?.let { a -> (0 until a.length()).mapNotNull { a.opt(it) as? JSONObject } }.orEmpty()
    val dated = items.map { it to findDate(it) }
    if (items.isEmpty() || dated.all { it.second == null }) {
        render(v, 0)
        return
    }
    val groups = dated.groupBy { it.second?.let(::semesterOf) ?: "Без даты" }
        .toList().sortedByDescending { semesterSortKey(it.first) }
    var sel by remember { mutableStateOf(groups.first().first) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            groups.forEach { (label, g) -> Choice("$label · ${g.size}", sel == label) { sel = label } }
        }
        groups.firstOrNull { it.first == sel }?.second?.sortedByDescending { it.second }?.forEach { (o, _) ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Card).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) { render(o, 1) }
        }
        Box(Modifier.height(4.dp))
    }
}
