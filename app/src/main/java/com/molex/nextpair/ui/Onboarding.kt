package com.molex.nextpair.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.molex.nextpair.Iis
import com.molex.nextpair.R
import com.molex.nextpair.Repo
import com.molex.nextpair.Target
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private enum class Step { Welcome, Login, Group, Subgroup }

/** Первый запуск: вход в IIS (группа подставится сама) или ручной выбор группы, затем подгруппа. */
@Composable
fun OnboardingScreen(repo: Repo, iis: Iis, onFinish: (group: String, subgroup: Int) -> Unit) {
    var step by remember { mutableStateOf(Step.Welcome) }
    var group by remember { mutableStateOf("") }
    var fromIis by remember { mutableStateOf(false) }
    BackHandler(enabled = step != Step.Welcome) {
        step = when (step) {
            Step.Subgroup -> if (fromIis) Step.Welcome else Step.Group
            else -> Step.Welcome
        }
    }
    when (step) {
        Step.Welcome -> Welcome(
            onIis = { step = Step.Login },
            onManual = { fromIis = false; step = Step.Group },
        )
        Step.Login -> IisStep(iis, onBack = { step = Step.Welcome }) { g ->
            fromIis = true
            if (g.isNullOrBlank()) step = Step.Group else { group = g; step = Step.Subgroup }
        }
        Step.Group -> GroupStep(repo, group, onBack = { step = Step.Welcome }) { group = it; step = Step.Subgroup }
        Step.Subgroup -> SubgroupStep(group, onBack = { step = if (fromIis) Step.Welcome else Step.Group }) { onFinish(group, it) }
    }
}

@Composable
private fun Welcome(onIis: () -> Unit, onManual: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Иконка приложения: тёмный фон + векторный рисунок (адаптивную иконку Compose целиком не рисует).
        Box(Modifier.size(104.dp).clip(RoundedCornerShape(28.dp)).background(androidx.compose.ui.graphics.Color(0xFF2B5CE6))) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(20.dp))
        Text("UniTime", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = C.Ink)
        Spacer(Modifier.height(8.dp))
        Text(
            "Расписание БГУИР, следующая пара на экране блокировки, дедлайны и личный кабинет — в одном месте.",
            fontSize = 15.sp, color = C.Muted, textAlign = TextAlign.Center, lineHeight = 21.sp,
        )
        Spacer(Modifier.height(36.dp))
        BigButton("Войти через IIS", primary = true, onIis)
        Text(
            "Группа подставится сама, а ещё будут оценки, пропуски и уведомления о новом.",
            fontSize = 12.sp, color = C.Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
        )
        BigButton("Без входа — указать группу", primary = false, onManual)
        Spacer(Modifier.height(24.dp))
        Text("made by molex1le", fontSize = 12.sp, color = C.Muted)
    }
}

@Composable
private fun BigButton(text: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(16.dp))
            .background(if (primary) C.Accent else C.Card).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (primary) C.OnAccent else C.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

/** Вход в IIS, затем группа из профиля. null — не удалось узнать, спросим вручную. */
@Composable
private fun IisStep(iis: Iis, onBack: () -> Unit, onGroup: (String?) -> Unit) {
    var fetching by remember { mutableStateOf(false) }
    if (fetching) {
        LaunchedEffect(Unit) {
            val g = withContext(Dispatchers.IO) {
                runCatching { iis.get("/auth/user").takeIf { it.code in 200..299 }?.let { JSONObject(it.body).optString("group") } }.getOrNull()
            }
            onGroup(g?.takeIf { it.isNotBlank() && it != "null" })
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(color = C.Accent)
                Text("Узнаю твою группу…", color = C.Muted, fontSize = 14.sp)
            }
        }
    } else {
        LoginScreen(iis, onBack) { fetching = true }
    }
}

@Composable
private fun GroupStep(repo: Repo, initial: String, onBack: () -> Unit, onPick: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(initial) }
    var groups by remember { mutableStateOf<List<Target>?>(null) }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { groups = withContext(Dispatchers.IO) { runCatching { repo.allGroups() }.getOrNull() } }
    val q = query.trim()
    val matches = if (q.isEmpty()) emptyList() else groups.orEmpty().filter { it.title.startsWith(q) }.take(8)

    fun pick(g: String) {
        error = null
        // Проверяем, что расписание группы существует, прежде чем идти дальше.
        checking = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { repo.fetchOther(Target("group", g, g, "")) }.isSuccess }
            checking = false
            if (ok) onPick(g) else error = "Не нашёл расписание группы $g. Проверь номер или интернет."
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { RoundButton(Glyph.Back, "Назад", onBack) }
        item { ScreenTitle("шаг 1 из 2", "Твоя группа") }
        item {
            Field(query, { query = it.filter { c -> c.isDigit() }; error = null }, "Номер группы, например 572302", number = true,
                leading = { Glyph(Glyph.Search, C.Muted, 20.dp) })
        }
        if (error != null) item { Text(error!!, color = C.Error, fontSize = 14.sp) }
        if (matches.isNotEmpty()) item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card)) {
                matches.forEachIndexed { i, t ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.Divider))
                    Row(
                        Modifier.fillMaxWidth().clickable { query = t.title; pick(t.title) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(t.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                        Text(t.sub, fontSize = 13.sp, color = C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        if (groups == null) item { Text("Загружаю список групп…", fontSize = 13.sp, color = C.Muted) }
        item {
            val ready = q.length >= 6 && !checking
            Box(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (ready) C.Accent else C.Segment).clickable(enabled = ready) { pick(q) },
                contentAlignment = Alignment.Center,
            ) {
                if (checking) CircularProgressIndicator(color = C.OnAccent, modifier = Modifier.size(22.dp))
                else Text("Дальше", color = if (ready) C.OnAccent else C.Muted, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SubgroupStep(group: String, onBack: () -> Unit, onDone: (Int) -> Unit) {
    var sub by remember { mutableIntStateOf(0) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { RoundButton(Glyph.Back, "Назад", onBack) }
        item { ScreenTitle("группа $group · шаг 2 из 2", "Подгруппа") }
        item {
            Text(
                "Лабораторные часто идут по подгруппам. Выбери свою — увидишь только свои пары. Если не знаешь, оставь «Все», поменять можно в настройках.",
                fontSize = 14.sp, color = C.Muted, lineHeight = 20.sp,
            )
        }
        item { Segmented(listOf("Все", "1", "2"), sub) { sub = it } }
        item { BigButton("Готово", primary = true) { onDone(sub) } }
    }
}
