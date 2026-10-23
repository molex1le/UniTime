package com.molex.nextpair.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.molex.nextpair.Schedule
import com.molex.nextpair.Stats
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

private fun h(d: Double) = if (d >= 10) "${d.toInt()}" else String.format(Locale.US, "%.1f", d).removeSuffix(".0")

private fun hoursWord(n: Int) = when {
    n % 10 == 1 && n % 100 != 11 -> "час"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "часа"
    else -> "часов"
}

@Composable
fun SecretStatsScreen(s: Schedule, subgroup: Int, now: LocalDateTime, onBack: () -> Unit) {
    val st = remember(s, subgroup) { Stats.semester(s, subgroup, now) }
    val ru = Locale("ru")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { RoundButton(Glyph.Back, "Назад", onBack) }
        item { ScreenTitle("ты нашёл пасхалку", "Твой семестр") }
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.Hero).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(h(st.hours), color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 56.sp)
                    Text("${hoursWord(st.hours.toInt())} на парах\nза семестр", color = C.HeroMuted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp, modifier = Modifier.padding(bottom = 6.dp))
                }
                ProgressBar((st.hoursDone / st.hours.coerceAtLeast(1.0)).toFloat(), C.HeroTrack, C.HeroBar)
                Text(
                    "Позади ${h(st.hoursDone)} ч, осталось ${h(st.hours - st.hoursDone)} ч · всего ${pairs(st.pairs)}",
                    color = C.HeroMuted, fontSize = 13.sp,
                )
                Text("Это ${h(st.hours / 24)} суток без перерыва.", color = C.HeroLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        st.topTeacher?.let { (t, n) ->
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    TeacherPhoto(t, 60.dp)
                    Column(Modifier.weight(1f)) {
                        Text("Чаще всех ты видишь", fontSize = 12.sp, color = C.Muted)
                        Text(t.full, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.Ink, lineHeight = 20.sp)
                        Text("${pairs(n)} за семестр — почти родственник", fontSize = 13.sp, color = C.Accent, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FunTile(Modifier.weight(1f), "Второй дом", st.topRoom?.first ?: "—", st.topRoom?.let { "${pairs(it.second)} там" } ?: "")
                FunTile(
                    Modifier.weight(1f), "Самый тяжёлый день",
                    st.hardestDay?.first?.getDisplayName(TextStyle.FULL, ru)?.replaceFirstChar { it.uppercase() } ?: "—",
                    st.hardestDay?.let { "≈ ${h(it.second)} ч в неделю" } ?: "",
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FunTile(Modifier.weight(1f), "Пар в 8:30", "${st.earlyPairs}", if (st.earlyPairs == 0) "везунчик" else "держись")
                FunTile(
                    Modifier.weight(1f), "Самый длинный день",
                    st.longestDay?.let { (d, _) -> "${d.dayOfMonth} ${month(d)}" } ?: "—",
                    st.longestDay?.let { (_, dur) -> "${minutesText(dur.toMinutes())} в универе" } ?: "",
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FunTile(Modifier.weight(1f), "Окна за семестр", "${h(st.windowHours)} ч", if (st.windowHours < 1) "ни одного — расписание мечты" else "можно было выучить японский")
                FunTile(Modifier.weight(1f), "Дней без пар", "${st.freeDays}", "кроме воскресений")
            }
        }
        if (st.bySubject.isNotEmpty()) {
            item { SectionTitle("Куда уходит время") }
            item {
                val max = st.bySubject.first().second
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    st.bySubject.forEach { (name, hours) ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(name, fontSize = 14.sp, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text("${h(hours)} ч", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                            }
                            ProgressBar((hours / max).toFloat(), C.Ground, C.Accent, 5.dp)
                        }
                    }
                }
            }
        }
        item {
            Text("Подсказка: в «Парах» спрятана ещё одна пасхалка. Попробуй поиграть с неделями.", fontSize = 12.sp, color = C.Muted, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun FunTile(modifier: Modifier, label: String, value: String, sub: String) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(C.Card).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label, fontSize = 12.sp, color = C.Muted)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = C.Accent)
    }
}
