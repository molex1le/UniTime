package com.molex.nextpair.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.molex.nextpair.Repo
import com.molex.nextpair.Schedule
import com.molex.nextpair.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    repo: Repo,
    st: Settings,
    schedule: Schedule?,
    onBack: () -> Unit,
    onLookChanged: () -> Unit,
    onGroupSaved: () -> Unit,
    onNotifChanged: () -> Unit,
    onBattery: () -> Unit,
    onRefresh: () -> Unit,
    onCalendar: (add: Boolean) -> Unit,
) {
    fun changed(look: Boolean = false, notif: Boolean = false) {
        if (look) onLookChanged()
        if (notif) onNotifChanged()
    }
    var group by remember { mutableStateOf(repo.group) }
    var sub by remember { mutableIntStateOf(repo.subgroup) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { RoundButton(Glyph.Back, "Назад", onBack) }
        item { ScreenTitle(null, "Настройки") }

        item { Section("Группа") }
        item {
            Card {
                Field(group, { group = it.filter { c -> c.isDigit() } }, "Номер группы", number = true)
                Text("Подгруппа", fontSize = 13.sp, color = C.Muted)
                Segmented(listOf("Все", "1", "2"), sub) { sub = it }
                if (group != repo.group || sub != repo.subgroup) {
                    Choice("Сохранить и обновить", true) {
                        repo.group = group.ifBlank { repo.group }
                        repo.subgroup = sub
                        onGroupSaved()
                        changed()
                    }
                }
            }
        }

        item { Section("Внешний вид") }
        item {
            Card {
                Text("Тема", fontSize = 13.sp, color = C.Muted)
                Segmented(listOf("Авто", "Светлая", "Тёмная", "Чёрная"), st.theme) { st.theme = it; changed(look = true) }
                Text("Акцент", fontSize = 13.sp, color = C.Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Settings.ACCENTS.forEachIndexed { i, name -> Choice(name, st.accent == i) { st.accent = i; changed(look = true) } }
                }
                Toggle("Короткие названия", "«КС» вместо «Компьютерные сети»", st.shortNames) { st.shortNames = it; changed(look = true) }
                Toggle("Номера пар", "«3 пара» рядом со временем", st.pairNumbers) { st.pairNumbers = it; changed(look = true) }
                Text("Открывать при запуске", fontSize = 13.sp, color = C.Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tab.entries.forEachIndexed { i, t -> Choice(t.label, st.startTab == i) { st.startTab = i; changed() } }
                }
            }
        }

        item { Section("Скрытые пары") }
        item {
            Card {
                Text("Нажми на предмет, чтобы скрыть его целиком", fontSize = 13.sp, color = C.Muted)
                val hidden = st.hiddenSubjects
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    schedule?.subjects()?.forEach { (short, _) ->
                        val key = "S:$short"
                        Choice(short, key in hidden) {
                            st.hiddenSubjects = if (key in hidden) hidden - key else hidden + key
                            changed(look = true, notif = true)
                        }
                    }
                }
                val single = hidden.filterNot { it.startsWith("S:") }
                if (single.isNotEmpty()) {
                    Text("Отдельные пары (скрыты из карточки пары)", fontSize = 13.sp, color = C.Muted)
                    single.forEach { key ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(describeKey(key), fontSize = 14.sp, color = C.Ink, modifier = Modifier.weight(1f))
                            Text(
                                "Вернуть", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.Accent,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                                    st.hiddenSubjects = st.hiddenSubjects - key
                                    changed(look = true, notif = true)
                                }.padding(8.dp),
                            )
                        }
                    }
                }
            }
        }

        item { Section("Уведомление в шторке") }
        item {
            Card {
                Toggle("Показывать следующую пару", "Постоянное уведомление и значок в строке", st.notifEnabled) { st.notifEnabled = it; changed(notif = true) }
                Toggle("Прятать, когда пары кончились", "И в выходные", st.hideAfterLast) { st.hideAfterLast = it; changed(notif = true) }
                Toggle("Аудитория", null, st.notifRoom) { st.notifRoom = it; changed(notif = true) }
                Toggle("Преподаватель", null, st.notifTeacher) { st.notifTeacher = it; changed(notif = true) }
            }
        }

        item { Section("Напоминания") }
        item {
            Card {
                Text("За сколько минут до пары", fontSize = 13.sp, color = C.Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Settings.REMIND_OPTIONS.forEach { m -> Choice("$m мин", st.remindMinutes == m) { st.remindMinutes = m; changed(notif = true) } }
                }
                Toggle("Звук и вибрация", "Для пар и дедлайнов", st.remindSound) { st.remindSound = it; changed() }
                Toggle("Если расписание изменилось", "Сравниваю при каждом обновлении", st.notifyChanges) { st.notifyChanges = it; changed() }
                Text("Включить напоминание для пары — в её карточке.", fontSize = 12.sp, color = C.Muted)
            }
        }

        item { Section("Виджет") }
        item { Card { Toggle("Тёмный виджет", null, st.widgetDark) { st.widgetDark = it; changed(notif = true) } } }

        item { Section("Календарь") }
        item {
            Card {
                Text("Добавить все пары до конца семестра в Google Календарь телефона", fontSize = 14.sp, color = C.Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("Добавить", true) { onCalendar(true) }
                    Choice("Убрать оттуда", false) { onCalendar(false) }
                }
            }
        }

        item { Section("Данные") }
        item {
            Card {
                val last = repo.lastFetch
                Text(
                    if (last == 0L) "Расписание ещё не загружалось" else "Обновлено ${SimpleDateFormat("d MMM, HH:mm", Locale("ru")).format(Date(last))}",
                    fontSize = 14.sp, color = C.Ink,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("Обновить сейчас", false, onRefresh)
                    Choice("Работа в фоне", false, onBattery)
                }
            }
        }

        item {
            Text(
                "Следующая пара · данные с iis.bsuir.by\nНеофициальное приложение, не связано с БГУИР.",
                fontSize = 12.sp, color = C.Muted, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Ключ отдельной пары «КС|ЛР|1|FRIDAY|12:00» → «КС ЛР · п/г 1 · Пт 12:00». */
fun describeKey(key: String): String {
    val p = key.split("|")
    if (p.size < 5) return key
    val day = runCatching { java.time.DayOfWeek.valueOf(p[3]).getDisplayName(java.time.format.TextStyle.SHORT, Locale("ru")) }.getOrDefault("")
    return listOf("${p[0]} ${p[1]}".trim(), if (p[2] != "0") "п/г ${p[2]}" else "", "$day ${p[4]}".trim())
        .filter { it.isNotBlank() }.joinToString(" · ")
}

@Composable
private fun Section(title: String) =
    Text(title.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Muted, letterSpacing = 0.5.sp, modifier = Modifier.padding(top = 8.dp))

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
fun Toggle(title: String, sub: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp)).clickable { onChange(!on) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = C.Ink)
            if (sub != null) Text(sub, fontSize = 12.sp, color = C.Muted)
        }
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = C.Accent, checkedThumbColor = C.OnAccent))
    }
}
