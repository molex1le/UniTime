package com.molex.nextpair.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

// ---------- Общие помощники ----------

internal fun JSONObject.str(vararg keys: String): String {
    for (k in keys) {
        if (!has(k) || isNull(k)) continue
        val v = opt(k)
        val s = when (v) {
            is JSONObject -> v.str("name", "title", "value", "fio", "number")
            else -> v.toString()
        }
        if (s.isNotBlank() && s != "null") return s
    }
    return ""
}

internal fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { opt(it) as? JSONObject }

/** Первый массив объектов в ответе (сам ответ, если это массив). */
internal fun listIn(v: Any?, vararg prefer: String): List<JSONObject> = when (v) {
    is JSONArray -> v.objects()
    is JSONObject -> prefer.firstNotNullOfOrNull { v.optJSONArray(it)?.objects()?.takeIf { l -> l.isNotEmpty() } }
        ?: v.keys().asSequence().mapNotNull { v.opt(it) as? JSONArray }.map { it.objects() }.firstOrNull { it.isNotEmpty() }
        ?: emptyList()
    else -> emptyList()
}

private fun hoursOf(o: JSONObject): Double = o.optString("hours").replace(',', '.').toDoubleOrNull() ?: 0.0

private fun fmtNum(d: Double) = String.format(Locale.US, "%.1f", d).removeSuffix(".0")
private fun fmt2(d: Double) = String.format(Locale.US, "%.2f", d)

@Composable
internal fun SectionTitle(text: String, right: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C.Ink)
        if (right != null) Text(right, fontSize = 13.sp, color = C.Muted)
    }
}

@Composable
internal fun StatusChip(text: String) {
    if (text.isBlank()) return
    val t = text.lowercase()
    val (bg, fg) = when {
        listOf("одобр", "выдан", "готов", "обработ", "поощр", "преми", "благодар", "подписан").any { it in t } -> typeStyle("ЛК").let { it.first to it.second }
        listOf("отклон", "взыск", "выгов", "замеч", "просроч", "отказ").any { it in t } -> typeStyle("Экз").let { it.first to it.second }
        else -> typeStyle("ПЗ").let { it.first to it.second }
    }
    Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 4.dp))
}

/** Карточка записи: заголовок, плашка статуса, строка дат и пара деталей. */
@Composable
internal fun RecordCard(title: String, chip: String = "", meta: String = "", lines: List<Pair<String, String>> = emptyList()) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title.ifBlank { "Запись" }, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.Ink, modifier = Modifier.weight(1f), lineHeight = 19.sp)
            StatusChip(chip)
        }
        if (meta.isNotBlank()) Text(meta, fontSize = 13.sp, color = C.Muted)
        lines.filter { it.second.isNotBlank() }.forEach { (k, v) ->
            if (k.isBlank()) { Text(v, fontSize = 14.sp, color = C.Ink, lineHeight = 19.sp); return@forEach }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(k, fontSize = 13.sp, color = C.Muted, modifier = Modifier.weight(0.42f))
                Text(v, fontSize = 13.sp, color = C.Ink, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.58f))
            }
        }
    }
}

private fun period(o: JSONObject): String {
    val a = prettyValue(o.str("dateFrom", "startDate"))
    val b = prettyValue(o.str("dateTo", "endDate"))
    return when {
        a.isNotBlank() && b.isNotBlank() && a != b -> "$a – $b"
        a.isNotBlank() -> a
        else -> prettyValue(o.str("date", "createdDate", "applicationDate", "dateOrder"))
    }
}

/** Переключатель семестров для списка записей с датами. */
@Composable
internal fun SemesterTabs(items: List<JSONObject>, content: @Composable (List<JSONObject>) -> Unit) {
    val groups = items.groupBy { o ->
        listOf("date", "dateFrom", "startDate", "createdDate", "applicationDate", "dateOrder").firstNotNullOfOrNull { k -> parseDate(o.str(k)) }
            ?.let(::semesterOf) ?: "Без даты"
    }.toList().sortedByDescending { (label, _) -> (label.substringAfter(' ').toIntOrNull() ?: 0) * 2 + if (label.startsWith("Осень")) 1 else 0 }
    if (groups.size <= 1) { content(items); return }
    var sel by remember { mutableStateOf(groups.first().first) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            groups.forEach { (label, g) -> Choice("$label · ${g.size}", sel == label) { sel = label } }
        }
        content(groups.firstOrNull { it.first == sel }?.second.orEmpty())
    }
}

@Composable
private fun BigNumber(value: String, label: String, sub: String? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.Hero).padding(20.dp),
        verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CountUpText(value, Color.White, 44.sp, FontWeight.ExtraBold, 44.sp)
        Column(Modifier.padding(bottom = 5.dp)) {
            Text(label, color = C.HeroMuted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (sub != null) Text(sub, color = C.HeroMuted, fontSize = 12.sp)
        }
    }
}

// ---------- Пропуски ----------

@Composable
fun OmissionsView(respectful: Any?, disrespectful: Any?) {
    val docs = listIn(respectful, "omissionDtoList")
    val bad = listIn(disrespectful, "omissions", "omissionDtoList")
    val total = bad.sumOf { hoursOf(it) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BigNumber("${fmtNum(total)} ч", "всего пропущено", "по неуважительной причине")
        SectionTitle("По неуважительной причине", if (bad.isNotEmpty()) "${bad.size} записей" else null)
        if (bad.isEmpty()) Text("Нет — так держать.", fontSize = 14.sp, color = C.Muted)
        else SemesterTabs(bad) { list ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("За семестр: ${fmtNum(list.sumOf { hoursOf(it) })} ч", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Accent)
                list.sortedByDescending { parseDate(it.str("date", "dateFrom")) }.forEach { o ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Card).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(o.str("subject", "discipline", "subjectName").ifBlank { "Занятие" }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
                            Text(listOf(period(o), o.str("note")).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 13.sp, color = C.Muted)
                        }
                        TypeChip(o.str("lessonTypeAbbrev"))
                        Text("${fmtNum(hoursOf(o))} ч", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                    }
                }
            }
        }
        SectionTitle("По уважительной причине", if (docs.isNotEmpty()) "${docs.size} документов" else null)
        if (docs.isEmpty()) Text("Справок нет.", fontSize = 14.sp, color = C.Muted)
        else SemesterTabs(docs) { list ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                list.sortedByDescending { parseDate(it.str("dateFrom", "date")) }.forEach { o ->
                    RecordCard(
                        o.str("name", "documentType", "type", "omissionCertificateType").ifBlank { "Справка" },
                        meta = period(o),
                        lines = listOf("Примечание" to o.str("note", "description")),
                    )
                }
            }
        }
    }
}

// ---------- Успеваемость (журнал) ----------

/** Все отметки внутри предмета, где бы они ни лежали. */
private fun collectMarks(v: Any?, out: MutableList<Pair<String, String>>) {
    when (v) {
        is JSONObject -> {
            val m = v.str("mark", "value")
            if (m.isNotBlank() && m.length <= 3 && (v.has("mark"))) out += m to prettyValue(v.str("date", "dateString"))
            v.keys().forEach { k -> if (k != "deadlines") collectMarks(v.opt(k), out) }
        }
        is JSONArray -> for (i in 0 until v.length()) collectMarks(v.opt(i), out)
    }
}

private fun collectOmissionHours(v: Any?, inOmissions: Boolean = false): Double = when (v) {
    is JSONObject -> v.keys().asSequence().sumOf { k ->
        val inside = inOmissions || k.contains("omission", true) || k.contains("absence", true)
        if (inside && k == "hours") hoursOf(v) else collectOmissionHours(v.opt(k), inside)
    }
    is JSONArray -> (0 until v.length()).sumOf { collectOmissionHours(v.opt(it), inOmissions) }
    else -> 0.0
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PerformanceView(v: Any?) {
    val root = v as? JSONObject
    val subjects = listIn(root?.opt("personalRating") ?: v, "subjects", "disciplines", "personalRating")
    if (subjects.isEmpty()) { JsonView(v ?: JSONObject(), 0); return }
    // Общий средний — как в зачётке: сумма всех отметок по всем предметам, делённая на их количество.
    val all = subjects.flatMap { s ->
        mutableListOf<Pair<String, String>>().also { collectMarks(s, it) }.mapNotNull { it.first.replace(',', '.').toDoubleOrNull() }
    }.filter { it > 0 }
    val withMarks = subjects.count { s -> mutableListOf<Pair<String, String>>().also { collectMarks(s, it) }.any { it.first.replace(',', '.').toDoubleOrNull() != null } }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BigNumber(
            if (all.isEmpty()) "—" else fmt2(all.average()),
            "средний балл",
            if (all.isEmpty()) "отметок пока нет" else "${all.size} отметок по $withMarks предметам из ${subjects.size}",
        )
        subjects.sortedBy { it.str("name", "discipline", "abbrev") }.forEach { s ->
            val marks = mutableListOf<Pair<String, String>>().also { collectMarks(s, it) }
            val numeric = marks.mapNotNull { it.first.replace(',', '.').toDoubleOrNull() }
            val avg = s.str("average").toDoubleOrNull() ?: numeric.takeIf { it.isNotEmpty() }?.average()
            val absent = collectOmissionHours(s)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(s.str("name", "discipline", "subject", "abbrev"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.Ink, modifier = Modifier.weight(1f))
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(if (avg == null) C.Ground else C.Soft), contentAlignment = Alignment.Center) {
                        Text(avg?.let { fmtNum(it) } ?: "—", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = if (avg == null) C.Muted else C.SoftInk)
                    }
                }
                if (marks.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    marks.forEach { (m, _) ->
                        Text(m, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Ink,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(C.Ground).padding(horizontal = 9.dp, vertical = 5.dp))
                    }
                } else Text("Отметок пока нет", fontSize = 13.sp, color = C.Muted)
                val dl = listIn(s.opt("deadlines"))
                val info = listOfNotNull(
                    if (absent > 0) "Пропуски: ${fmtNum(absent)} ч" else null,
                    if (dl.isNotEmpty()) "Дедлайнов: ${dl.size}" else null,
                )
                if (info.isNotEmpty()) Text(info.joinToString(" · "), fontSize = 12.sp, color = C.Muted)
            }
        }
    }
}

// ---------- Деканат: справки ----------

@Composable
fun DeanView(v: Any?) {
    val list = listIn(v, "certificateRequests", "certificates")
    if (list.isEmpty()) { EmptyCard("Заявок нет", "Справки заказываются на iis.bsuir.by в разделе «Деканат»."); return }
    SemesterTabs(list) { items ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.sortedByDescending { parseDate(it.str("createdDate", "dateOrder", "applicationDate", "date")) }.forEach { o ->
                RecordCard(
                    o.str("certificateType", "type", "name").ifBlank { "Справка" },
                    chip = o.str("status"),
                    meta = prettyValue(o.str("createdDate", "dateOrder", "applicationDate", "date")),
                    lines = listOf(
                        "Куда" to o.str("provisionPlace", "place"),
                        "Номер" to o.str("number"),
                        "Количество" to o.str("count", "quantity"),
                        "Выдана" to prettyValue(o.str("issueDate")),
                        "Причина отказа" to o.str("rejectionReason"),
                    ),
                )
            }
        }
    }
}

// ---------- Общежитие ----------

@Composable
fun DormitoryView(applications: Any?, privileges: Any?, premiumPenalty: Any?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val pp = listIn(premiumPenalty)
        SectionTitle("Поощрения и взыскания", if (pp.isNotEmpty()) "${pp.size}" else null)
        if (pp.isEmpty()) Text("Нет записей.", fontSize = 14.sp, color = C.Muted)
        else SemesterTabs(pp) { items ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.sortedByDescending { parseDate(it.str("date")) }.forEach { o ->
                    val dir = o.optJSONObject("directiveDto")
                    RecordCard(
                        o.str("reason").ifBlank { o.str("eventTypeName") },
                        chip = o.str("eventTypeName"),
                        meta = prettyValue(o.str("date")),
                        lines = listOf(
                            "Статус" to o.str("status"),
                            "Приказ" to listOfNotNull(dir?.str("number", "name")?.ifBlank { null }?.let { "№ $it" }, dir?.str("date")?.ifBlank { null }?.let { "от ${prettyValue(it)}" }).joinToString(" "),
                        ),
                    )
                }
            }
        }
        val pr = listIn(privileges)
        SectionTitle("Льготы", if (pr.isNotEmpty()) "${pr.size}" else null)
        if (pr.isEmpty()) Text("Льгот нет.", fontSize = 14.sp, color = C.Muted)
        pr.forEach { o -> RecordCard(o.str("dormitoryPrivilegeCategoryName", "name"), meta = o.str("year").let { if (it.isNotBlank()) "$it год" else "" }) }
        val ap = listIn(applications)
        SectionTitle("Заявки на общежитие", if (ap.isNotEmpty()) "${ap.size}" else null)
        if (ap.isEmpty()) Text("Заявок нет.", fontSize = 14.sp, color = C.Muted)
        ap.forEach { o ->
            RecordCard(
                o.str("dormitory", "dormitoryName", "name").ifBlank { "Заявка" },
                chip = o.str("status"),
                meta = prettyValue(o.str("applicationDate", "createdDate", "date")),
                lines = listOf("Номер в очереди" to o.str("queueNumber", "number"), "Комментарий" to o.str("comment", "note")),
            )
        }
    }
}

// ---------- Группа ----------

@Composable
fun GroupView(v: Any?) {
    val o = v as? JSONObject
    val students = listIn(o, "students", "groupInfoStudentDto")
    val head = o?.optJSONObject("headmanData")?.str("fio") ?: ""
    if (students.isEmpty()) { JsonView(v ?: JSONObject(), 0); return }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BigNumber("${students.size}", "студентов в группе", if (head.isNotBlank()) "староста: $head" else null)
        val bySub = students.groupBy { it.optInt("subGroup", 0) }.toSortedMap()
        bySub.forEach { (sub, list) ->
            SectionTitle(if (sub == 0) "Без подгруппы" else "Подгруппа $sub", "${list.size}")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card)) {
                list.sortedBy { it.str("fio") }.forEachIndexed { i, s ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.Divider))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", fontSize = 13.sp, color = C.Muted, modifier = Modifier.size(width = 28.dp, height = 18.dp))
                        Text(s.str("fio", "name"), fontSize = 15.sp, color = C.Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (s.str("fio") == head && head.isNotBlank()) StatusChip("Староста")
                    }
                }
            }
        }
    }
}

// ---------- Библиотека ----------

@Composable
fun LibraryView(v: Any?) {
    val books = listIn(v, "books")
    if (books.isEmpty()) { EmptyCard("Книг на руках нет", "Здесь появятся книги, взятые в библиотеке БГУИР."); return }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        books.forEach { b ->
            val due = parseDate(b.str("returnDate", "dateTo", "deadline", "dateReturn"))
            RecordCard(
                b.str("title", "name", "bookName", "description"),
                chip = if (due != null && due.isBefore(java.time.LocalDate.now())) "Просрочена" else "",
                meta = b.str("author", "authors"),
                lines = listOf("Выдана" to prettyValue(b.str("issueDate", "dateFrom", "date")), "Вернуть до" to (due?.let { prettyValue(it.toString()) } ?: "")),
            )
        }
    }
}

// ---------- Настройки аккаунта ----------

@Composable
fun AccountSettingsView(lastChange: Any?, attempts: Any?, contacts: Any?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val last = when (lastChange) {
            is JSONObject -> lastChange.str("lastPasswordChange", "date", "lastChangeDate")
            null -> ""
            else -> lastChange.toString()
        }
        val att = attempts as? JSONObject
        RecordCard(
            "Пароль",
            lines = listOf(
                "Последняя смена" to prettyValue(last),
                "Осталось попыток входа" to (att?.str("passwordAttempts") ?: ""),
                "Блокировка до" to prettyValue(att?.str("passwordBanExpiredTime") ?: ""),
            ),
        )
        val list = listIn(contacts, "contactDtoList")
        SectionTitle("Контакты")
        if (list.isEmpty()) Text("Контактов нет.", fontSize = 14.sp, color = C.Muted)
        list.forEach { c ->
            RecordCard(
                c.str("contactValue", "value"),
                chip = if (c.optBoolean("confirmed")) "Подтверждён" else "Не подтверждён",
                meta = c.str("contactType", "type", "contactTypeName"),
            )
        }
        Text("Сменить пароль и контакты можно на iis.bsuir.by → Настройки.", fontSize = 12.sp, color = C.Muted)
    }
}

// ---------- Уведомления ----------

@Composable
fun NotificationsView(v: Any?) {
    val list = listIn(v, "notifications")
    if (list.isEmpty()) { EmptyCard("Уведомлений нет", "Здесь будут сообщения из IIS."); return }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        list.forEach { n ->
            RecordCard(
                n.str("title", "subject", "header").ifBlank { n.str("message", "text").take(60) },
                chip = if (n.has("isViewed") && !n.optBoolean("isViewed")) "Новое" else "",
                meta = prettyValue(n.str("date", "createdDate", "creationDate")),
                lines = listOf("" to n.str("message", "text", "content").takeIf { n.str("title", "subject", "header").isNotBlank() }.orEmpty()),
            )
        }
    }
}
