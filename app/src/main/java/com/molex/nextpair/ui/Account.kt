package com.molex.nextpair.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.molex.nextpair.Iis
import com.molex.nextpair.IisWatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

@Composable
fun AccountScreen(iis: Iis, onBack: (() -> Unit)?) {
    val ctx = LocalContext.current
    var loggedIn by remember { mutableStateOf(iis.loggedIn) }
    var open by remember { mutableStateOf<Iis.Section?>(null) }
    val section = open
    when {
        !loggedIn -> LoginScreen(iis, onBack) { loggedIn = true }
        section != null -> SectionScreen(iis, section, onBack = { open = null }) { loggedIn = false; open = null }
        else -> CabinetHome(iis, onBack, onOpen = { open = it }) {
            Thread { iis.logout(); IisWatch.reset(ctx); IisWatch.schedule(ctx) }.start()
            loggedIn = false
        }
    }
}

@Composable
private fun LoginScreen(iis: Iis, onBack: (() -> Unit)?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var login by remember { mutableStateOf(iis.login) }
    var password by remember { mutableStateOf("") }
    var remember by remember { mutableStateOf(true) }
    var savePw by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var redirect by remember { mutableStateOf<String?>(null) }
    val ok = login.isNotBlank() && password.isNotBlank() && !busy

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (onBack != null) item { RoundButton(Glyph.Back, "Назад", onBack) }
        item { ScreenTitle("iis.bsuir.by", "Профиль") }
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Field(login, { login = it.trim() }, "Номер студенческого билета")
                PasswordField(password, { password = it })
                Toggle("Запомнить устройство", "Чтобы не входить каждый раз", remember) { remember = it }
                Toggle("Запомнить пароль", "Нужно для уведомлений о новых оценках и пропусках", savePw) { savePw = it }
                if (error != null) Text(error!!, color = C.Error, fontSize = 14.sp)
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (ok) C.Accent else C.Segment)
                        .clickable(enabled = ok) {
                            busy = true; error = null
                            scope.launch {
                                val r = withContext(Dispatchers.IO) { runCatching { iis.login(login, password, remember, savePw) } }
                                busy = false
                                r.onFailure { error = "Нет связи с iis.bsuir.by" }
                                when (val res = r.getOrNull()) {
                                    Iis.LoginResult.Ok -> {
                                        password = ""
                                        IisWatch.reset(ctx)
                                        IisWatch.schedule(ctx)
                                        // Первая проверка запоминает текущее состояние, без уведомлений.
                                        Thread { runCatching { IisWatch.check(ctx) } }.start()
                                        onDone()
                                    }
                                    is Iis.LoginResult.Redirect -> redirect = res.url
                                    is Iis.LoginResult.Failed -> error = res.message
                                    null -> {}
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (busy) CircularProgressIndicator(color = C.OnAccent, modifier = Modifier.height(22.dp))
                    else Text("Войти", color = if (ok) C.OnAccent else C.Muted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        redirect?.let { url ->
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Soft).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Сайт просит подтвердить вход", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.SoftInk)
                    Text("Открой страницу, подтверди и вернись сюда войти ещё раз.", fontSize = 14.sp, color = C.SoftInk)
                    Choice("Открыть iis.bsuir.by", true) {
                        val target = url.ifBlank { "https://iis.bsuir.by/login" }
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
                    }
                }
            }
        }
        item {
            Text(
                "Логин и пароль отправляются только на iis.bsuir.by — как при входе на сайте. " +
                    "Если включить «Запомнить пароль», он хранится на телефоне в зашифрованном виде " +
                    "(ключ в защищённом хранилище Android) и нужен, чтобы входить заново, когда сайт завершит сессию. " +
                    "«Выйти» стирает и сессию, и пароль.",
                fontSize = 12.sp, color = C.Muted, lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 50.dp).clip(RoundedCornerShape(14.dp)).background(C.Card)
            .border(1.dp, C.Line, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text("Пароль", color = C.Muted, fontSize = 15.sp)
        BasicTextField(
            value, onChange, singleLine = true,
            textStyle = TextStyle(fontSize = 15.sp, color = C.Ink),
            cursorBrush = SolidColor(C.Accent),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CabinetHome(iis: Iis, onBack: (() -> Unit)?, onOpen: (Iis.Section) -> Unit, onLogout: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (onBack != null) item { RoundButton(Glyph.Back, "Назад", onBack) }
        item { ScreenTitle("личный кабинет IIS · ${iis.login}", "Профиль") }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card)) {
                Iis.SECTIONS.forEachIndexed { i, s ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.Divider))
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(s) }.heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Glyph(glyphFor(s.kind), C.Accent, 22.dp)
                        Text(s.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = C.Ink, modifier = Modifier.weight(1f))
                        Glyph(Glyph.Chevron, C.Chevron, 18.dp)
                    }
                }
            }
        }
        item { Choice("Выйти из аккаунта", false, onLogout) }
    }
}

private fun glyphFor(k: Iis.Kind) = when (k) {
    Iis.Kind.Profile -> Glyph.User
    Iis.Kind.Markbook -> Glyph.Doc
    Iis.Kind.Performance -> Glyph.Star
    Iis.Kind.Omissions -> Glyph.Clock
    Iis.Kind.Dean -> Glyph.Cap
    Iis.Kind.Dormitory -> Glyph.Pin
    Iis.Kind.Group -> Glyph.People
    Iis.Kind.Library -> Glyph.Calendar
    Iis.Kind.Notifications -> Glyph.Bell
    Iis.Kind.Settings -> Glyph.Gear
}

@Composable
private fun SectionScreen(iis: Iis, section: Iis.Section, onBack: () -> Unit, onExpired: () -> Unit) {
    val ctx = LocalContext.current
    var results by remember(section) { mutableStateOf<List<Iis.Response?>?>(null) }
    var failed by remember(section) { mutableStateOf(false) }
    LaunchedEffect(section) {
        runCatching {
            results = withContext(Dispatchers.IO) {
                kotlinx.coroutines.coroutineScope {
                    section.paths.map { p -> async { runCatching { iis.get(p) }.getOrNull() } }.map { it.await() }
                }
            }
        }.onFailure { failed = true }
    }
    val rs = results
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { RoundButton(Glyph.Back, "Назад", onBack) }
        item { ScreenTitle(null, section.title) }
        when {
            failed || (rs != null && rs.all { it == null }) -> item { Text("Нет связи с iis.bsuir.by", color = C.Error, fontSize = 14.sp) }
            rs == null -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = C.Accent) } }
            rs.any { it?.code == 401 } || rs.all { it?.code == 403 } -> item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Сессия закончилась — нужно войти заново.", color = C.Ink, fontSize = 15.sp)
                    Choice("Войти", true, onExpired)
                }
            }
            else -> {
                // Неуспешный ответ одного из запросов просто даёт пустой кусок раздела.
                val parsed = rs.map { r -> r?.takeIf { it.code in 200..299 }?.let { runCatching { JSONTokener(it.body).nextValue() }.getOrNull() } }
                val first = parsed.firstOrNull()
                item {
                    when (section.kind) {
                        Iis.Kind.Profile -> if (first is JSONObject) ProfileView(first, iis) else Unavailable(rs)
                        Iis.Kind.Markbook -> if (first is JSONObject && first.has("markPages")) MarkbookView(first) else Unavailable(rs)
                        Iis.Kind.Performance -> if (first != null) PerformanceView(first) else Unavailable(rs)
                        Iis.Kind.Omissions -> OmissionsView(parsed.getOrNull(0), parsed.getOrNull(1))
                        Iis.Kind.Dean -> DeanView(first)
                        Iis.Kind.Dormitory -> DormitoryView(parsed.getOrNull(0), parsed.getOrNull(1), parsed.getOrNull(2))
                        Iis.Kind.Group -> if (first != null) GroupView(first) else Unavailable(rs)
                        Iis.Kind.Library -> LibraryView(first)
                        Iis.Kind.Notifications -> NotificationsView(first)
                        Iis.Kind.Settings -> AccountSettingsView(parsed.getOrNull(0), parsed.getOrNull(1), parsed.getOrNull(2))
                    }
                }
                item {
                    Text(
                        "Скопировать данные для отладки",
                        fontSize = 12.sp, color = C.Muted,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                            val text = section.paths.zip(rs).joinToString("\n\n") { (p, r) -> "$p → ${r?.code}\n${r?.body}" }
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText(section.title, text))
                            Toast.makeText(ctx, "Скопировано", Toast.LENGTH_SHORT).show()
                        }.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Unavailable(rs: List<Iis.Response?>) {
    val code = rs.firstOrNull()?.code
    EmptyCard("Раздел недоступен", if (code != null) "Сервер ответил $code — возможно, у твоего аккаунта нет этого раздела." else "Нет связи с iis.bsuir.by.")
}

private fun isEmptyJson(v: Any): Boolean = when (v) {
    is JSONArray -> v.length() == 0
    is JSONObject -> v.length() == 0
    else -> false
}

/** Понятные подписи для частых полей ответа IIS. */
private val LABELS = mapOf(
    "fio" to "ФИО", "lastName" to "Фамилия", "firstName" to "Имя", "middleName" to "Отчество",
    "username" to "Логин", "email" to "Почта", "group" to "Группа", "groupName" to "Группа",
    "course" to "Курс", "faculty" to "Факультет", "speciality" to "Специальность",
    "subject" to "Предмет", "subjectName" to "Предмет", "mark" to "Оценка", "marks" to "Оценки",
    "date" to "Дата", "dateFrom" to "С", "dateTo" to "По", "reason" to "Причина",
    "eventTypeName" to "Тип", "status" to "Статус", "hours" to "Часы", "averageMark" to "Средний балл",
    "markPages" to "Семестры", "semester" to "Семестр", "teacher" to "Преподаватель",
    "dormitoryPrivilegeCategoryName" to "Льгота", "year" to "Год", "omissionDtoList" to "Пропуски",
    "notifications" to "Уведомления", "message" to "Сообщение", "title" to "Заголовок", "text" to "Текст",
    "directiveDto" to "Приказ", "number" to "Номер", "lessonTypeAbbrev" to "Тип занятия",
    "commonMark" to "Средний по потоку", "credit" to "Зачёт", "exam" to "Экзамен", "isViewed" to "Прочитано",
    "formOfControl" to "Форма контроля", "fullSubject" to "Предмет", "credits" to "Зачётные единицы",
    "commonRetakes" to "Пересдают, %", "retakesCount" to "Твои пересдачи", "phone" to "Телефон",
    "accountType" to "Тип аккаунта", "isGroupHead" to "Староста", "ldapBanDate" to "Учётка активна до",
    "type" to "Тип", "name" to "Название", "description" to "Описание", "term" to "Семестр",
    "orderNumber" to "Номер приказа", "orderDate" to "Дата приказа", "respectful" to "Уважительный",
    "lessonType" to "Тип занятия", "cause" to "Причина", "comment" to "Комментарий", "createdDate" to "Создано",
)

private val SKIP = setOf("photoUrl", "photo", "authorities", "fullShow", "calendarId", "urlId", "hasNext", "totalElements",
    "canStudentNote", "hasNotConfirmedContact", "hasProfiling")

/** Служебные идентификаторы (id, idSubject, facultyId…) прячем. */
private fun skip(key: String) = key in SKIP || key == "id" || key.endsWith("Id") || Regex("^id[A-Z]").containsMatchIn(key)

private fun label(key: String) = LABELS[key] ?: key

private fun primitive(v: Any?): String? = when (v) {
    null, JSONObject.NULL -> null
    is Boolean -> if (v) "да" else "нет"
    is Number, is String -> v.toString().takeIf { it.isNotBlank() && it != "null" }?.let { prettyValue(it) }
    else -> null
}

@Composable
fun JsonView(v: Any, depth: Int) {
    when (v) {
        is JSONArray -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in 0 until v.length()) {
                val item = v.get(i)
                val p = primitive(item)
                if (p != null) Text("• $p", fontSize = 14.sp, color = C.Ink)
                else Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (depth % 2 == 0) C.Card else C.Ground).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) { JsonView(item, depth + 1) }
            }
        }
        is JSONObject -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val keys = v.keys().asSequence().filterNot { skip(it) }.toList()
            for (k in keys) {
                val child = v.get(k)
                val p = primitive(child)
                when {
                    p != null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(label(k), fontSize = 13.sp, color = C.Muted, modifier = Modifier.weight(0.4f))
                        Text(p, fontSize = 14.sp, color = C.Ink, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.6f))
                    }
                    child is JSONObject || child is JSONArray -> if (!isEmptyJson(child)) {
                        Text(label(k), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Ink, modifier = Modifier.padding(top = 6.dp))
                        if (depth < 6) JsonView(child, depth + 1)
                    }
                }
            }
        }
        else -> primitive(v)?.let { Text(it, fontSize = 14.sp, color = C.Ink) }
    }
}
