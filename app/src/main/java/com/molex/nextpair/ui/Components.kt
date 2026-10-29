package com.molex.nextpair.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.molex.nextpair.Occurrence
import com.molex.nextpair.Repo
import com.molex.nextpair.Teacher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.format.DateTimeFormatter

data class Palette(
    val dark: Boolean,
    val ground: Color, val card: Color, val ink: Color, val muted: Color,
    val line: Color, val divider: Color, val segment: Color, val segOn: Color,
    val soft: Color, val softInk: Color, val hero: Color, val heroTrack: Color,
    val chevron: Color, val error: Color,
    /** Свой акцент у секретной темы — вместо выбранного в настройках. */
    val accent: Color? = null, val heroBar: Color? = null,
)

private val LIGHT = Palette(
    false, Color(0xFFF4F5F7), Color.White, Color(0xFF11151C), Color(0xFF5B6472),
    Color(0xFFE2E5EA), Color(0xFFEEF0F3), Color(0xFFE7E9EE), Color.White,
    Color(0xFFE3E8FB), Color(0xFF2B44B8), Color(0xFF141A26), Color(0xFF2A3244),
    Color(0xFF9AA2AE), Color(0xFF9B1C35),
)
/** Основа для чёрной темы; отдельной тёмной темы больше нет. */
private val DARK = Palette(
    true, Color(0xFF0F131B), Color(0xFF1A202B), Color(0xFFEDEFF3), Color(0xFFA3ABB8),
    Color(0xFF2A3140), Color(0xFF252C39), Color(0xFF1A202B), Color(0xFF323B4D),
    Color(0xFF26304A), Color(0xFFB9C4FF), Color(0xFF232C40), Color(0xFF39445C),
    Color(0xFF6B7484), Color(0xFFFF8FA3),
)
private val NEON = Palette(
    true, Color(0xFF0B0614), Color(0xFF170D28), Color(0xFFF6EEFF), Color(0xFFB3A3D1),
    Color(0xFF2E1C4A), Color(0xFF241640), Color(0xFF170D28), Color(0xFF3A2160),
    Color(0xFF2A1450), Color(0xFF7DF9FF), Color(0xFF1F0E3A), Color(0xFF3B2266),
    Color(0xFF7A6A99), Color(0xFFFF7A9C),
    accent = Color(0xFFFF4FD8), heroBar = Color(0xFF00E5FF),
)
private val AMOLED = DARK.copy(ground = Color.Black, card = Color(0xFF12151C), segment = Color(0xFF12151C), hero = Color(0xFF1A2133))

const val STYLE_CLASSIC = 0
const val STYLE_GLASS = 1
const val STYLE_COLOR = 2

/** Акценты: (светлая тема, тёмная тема). */
private val ACCENT = listOf(
    Color(0xFF2F4FD8) to Color(0xFF8EA2FF),
    Color(0xFF0F7A55) to Color(0xFF6FDDB0),
    Color(0xFFB4500B) to Color(0xFFFFB27A),
    Color(0xFF6D3FD1) to Color(0xFFC3A6FF),
)

/** Текущие цвета и опции отображения. Читаются из Compose-состояния, поэтому смена темы перерисовывает экран. */
object C {
    var palette by mutableStateOf(LIGHT)
    var accentIndex by mutableStateOf(0)
    var shortNames by mutableStateOf(true)
    var pairNumbers by mutableStateOf(true)
    /** Стиль экрана «Сегодня»: [STYLE_CLASSIC], [STYLE_GLASS] или [STYLE_COLOR]. */
    var style by mutableStateOf(STYLE_CLASSIC)

    fun apply(theme: Int, systemDark: Boolean, accent: Int, short: Boolean, numbers: Boolean, style: Int) {
        palette = when (theme) {
            1 -> LIGHT
            4 -> NEON
            // Тёмной темы больше нет: и «Авто» ночью, и старая тёмная — чёрная.
            2, 3 -> AMOLED
            else -> if (systemDark) AMOLED else LIGHT
        }
        accentIndex = accent.coerceIn(0, ACCENT.lastIndex)
        shortNames = short
        pairNumbers = numbers
        this.style = style.coerceIn(STYLE_CLASSIC, STYLE_COLOR)
    }

    val Dark get() = palette.dark
    val Ground get() = palette.ground
    /** В «Стекле» все карточки приложения — полупрозрачные поверх цветного фона. */
    val Card get() = if (style == STYLE_GLASS) glassFill() else palette.card
    /** Непрозрачная карточка — для системных поверхностей (диалоги, меню). */
    val SolidCard get() = palette.card
    val Ink get() = palette.ink
    val Muted get() = palette.muted
    val Line get() = palette.line
    val Divider get() = palette.divider
    val Segment get() = if (style == STYLE_GLASS) glassFill() else palette.segment
    val SegOn get() = palette.segOn
    val Soft get() = palette.soft
    val SoftInk get() = palette.softInk
    val Hero get() = palette.hero
    val HeroTrack get() = palette.heroTrack
    val Chevron get() = palette.chevron
    val Error get() = palette.error
    val Accent get() = palette.accent ?: ACCENT[accentIndex].let { if (palette.dark) it.second else it.first }
    val HeroBar get() = palette.heroBar ?: ACCENT[accentIndex].second
    val HeroMuted = Color(0xFFC9CED8)
    val HeroLabel = Color(0xFFB9C4FF)
    val Live = Color(0xFF7CF0B0)
    /** Текст поверх акцента. */
    val OnAccent get() = if (palette.dark) Color(0xFF0F131B) else Color.White
}

val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Название предмета с учётом настройки «коротко». */
fun com.molex.nextpair.Lesson.title() = if (C.shortNames) subject else subjectFull.ifBlank { subject }

/** Цвет метки и полное название типа занятия. */
fun typeStyle(type: String): Triple<Color, Color, String> {
    val d = C.Dark
    fun t(lb: Long, lf: Long, db: Long, df: Long, name: String) =
        if (d) Triple(Color(db), Color(df), name) else Triple(Color(lb), Color(lf), name)
    return when (type) {
        "ЛК" -> t(0xFFDCF2E8, 0xFF0E5E3E, 0xFF173A2C, 0xFF8BE3BC, "Лекция")
        "ПЗ" -> t(0xFFFDE7D6, 0xFF9A3A0B, 0xFF3E2516, 0xFFFFB98A, "Практика")
        "ЛР" -> t(0xFFE3E8FB, 0xFF2B44B8, 0xFF232D52, 0xFFB4C2FF, "Лабораторная")
        "Экз", "Экзамен" -> t(0xFFFBE2E6, 0xFF9B1C35, 0xFF44202A, 0xFFFFA3B4, "Экзамен")
        "Конс", "Консультация" -> t(0xFFEDE4FB, 0xFF5B2AA8, 0xFF2F2448, 0xFFD3BCFF, "Консультация")
        else -> Triple(C.Segment, C.Ink, type.ifBlank { "Занятие" })
    }
}

@Composable
fun TypeChip(type: String, full: Boolean = false) {
    if (type.isBlank()) return
    val (bg, fg, name) = typeStyle(type)
    Text(
        if (full) name else type,
        color = fg, fontSize = if (full) 12.sp else 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
fun Pill(text: String, bg: Color, fg: Color, border: Boolean = false, onClick: (() -> Unit)? = null) {
    var m = Modifier.clip(RoundedCornerShape(50)).background(bg)
    if (border) m = m.border(1.dp, C.Line, RoundedCornerShape(50))
    if (onClick != null) m = m.clickable(onClick = onClick)
    Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = m.padding(horizontal = 10.dp, vertical = 6.dp))
}

@Composable
fun ScreenTitle(over: String?, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (over != null) Text(over, color = C.Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(title, color = C.Ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
    }
}

@Composable
fun Segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    // Подложка выбранного пункта переезжает с пружинкой, а не перескакивает.
    BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Segment).padding(4.dp)) {
        val gap = if (labels.size > 3) 4.dp else 6.dp
        val w = (maxWidth - gap * (labels.size - 1)) / labels.size
        val x by animateDpAsState((w + gap) * selected.coerceIn(0, labels.lastIndex), bouncy(), label = "seg")
        Box(Modifier.offset(x = x).width(w).height(40.dp).clip(RoundedCornerShape(10.dp)).background(C.SegOn))
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            labels.forEachIndexed { i, label ->
                val on = i == selected
                val ink by animateColorAsState(if (on) C.Ink else C.Muted, label = "segInk")
                Box(
                    Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp)).clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    // Много пунктов (темы) — шрифт мельче, и слово никогда не переносится.
                    Text(
                        label, fontSize = if (labels.size > 3) 13.sp else 14.sp, fontWeight = FontWeight.SemiBold, color = ink,
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}

/** Полоска прогресса; при появлении плавно заполняется от нуля. */
@Composable
fun ProgressBar(fraction: Float, track: Color, bar: Color, height: Dp = 6.dp) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(fraction) { p.animateTo(fraction.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing)) }
    Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(track)) {
        Box(Modifier.fillMaxWidth(p.value).height(height).clip(RoundedCornerShape(height / 2)).background(bar))
    }
}

/**
 * Строка пары в списке: время, название, место, метка типа.
 * [styled] — строка рисуется в выбранном стиле (стекло или цвет пары).
 */
@Composable
fun LessonRow(o: Occurrence, faded: Boolean, highlighted: Boolean, showGroups: Boolean, extra: String? = null, styled: Boolean = true, onClick: () -> Unit) {
    val l = o.lesson
    val place = listOf(l.rooms, if (showGroups) l.groups else l.teacher).filter { it.isNotBlank() }.joinToString(" · ")
    val glass = styled && C.style == STYLE_GLASS
    val tone = if (styled && C.style == STYLE_COLOR) typeTone(l.type) else null
    val ink = tone?.ink ?: C.Ink
    val muted = tone?.ink?.copy(alpha = 0.7f) ?: C.Muted
    val shape = RoundedCornerShape(if (glass || tone != null) 22.dp else 18.dp)
    val surface = when {
        tone != null -> Modifier.clip(shape).background(tone.fill).border(2.dp, if (highlighted) tone.strong else tone.fill, shape)
        glass -> Modifier.glass(shape).then(if (highlighted) Modifier.border(1.5.dp, C.Accent, shape) else Modifier)
        else -> Modifier.clip(shape).background(C.Card).border(1.dp, if (highlighted) C.Accent else C.Card, shape)
    }
    Row(
        Modifier.bounceClick(onClick = onClick).fillMaxWidth().alpha(if (faded) (if (tone != null) 0.42f else 0.5f) else 1f).then(surface)
            .padding(horizontal = if (tone != null) 16.dp else 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.width(46.dp)) {
            val n = com.molex.nextpair.Settings.pairNumber(l.start)
            if (C.pairNumbers && n != null) Text("$n пара", fontSize = 11.sp, color = muted)
            Text(l.start.format(TIME), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ink)
            Text(l.end.format(TIME), fontSize = 12.sp, color = muted)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(l.title(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = if (l.subgroup != 0) "$place · п/г ${l.subgroup}" else place
            if (sub.isNotBlank()) Text(sub, fontSize = 13.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (extra != null) Text(extra, fontSize = 13.sp, color = tone?.ink ?: C.Accent, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (l.note.isNotBlank()) Text(l.note, fontSize = 13.sp, color = tone?.ink ?: C.Accent, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (tone != null) {
            // На цветной карточке метка «наизнанку»: тёмная плашка, буквы цвета карточки.
            Text(
                l.type, color = tone.fill, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(tone.ink).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        } else TypeChip(l.type)
    }
}

/** Фото преподавателя из IIS; пока грузится или если его нет — инициалы. */
@Composable
fun TeacherPhoto(t: Teacher, size: Dp) {
    val ctx = LocalContext.current
    var img by remember(t.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(t.id) {
        img = withContext(Dispatchers.IO) {
            runCatching {
                val f = File(ctx.cacheDir, "photos/${t.id}.img")
                if (!f.exists()) {
                    f.parentFile?.mkdirs()
                    f.writeBytes(Repo.getBytes(t.photoUrl))
                }
                BitmapFactory.decodeFile(f.path)?.asImageBitmap()
            }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(size * 0.32f)
    val i = img
    var full by remember { mutableStateOf(false) }
    if (i != null) {
        Image(
            i, "Фото: ${t.full}",
            Modifier.size(size).clip(shape).clickable(onClickLabel = "Открыть фото") { full = true },
            contentScale = ContentScale.Crop,
        )
        if (full) {
            // Фото на весь экран; закрывается тапом или кнопкой «назад».
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { full = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black).clickable { full = false },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(i, "Фото: ${t.full}", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                    Text(
                        t.full, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp),
                    )
                }
            }
        }
    } else {
        Box(Modifier.size(size).clip(shape).background(C.Soft), contentAlignment = Alignment.Center) {
            Text(t.initials, color = C.SoftInk, fontSize = (size.value * 0.34f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

enum class Tab(val label: String) { Today("Сегодня"), Feed("Пары"), Deadlines("Дедлайны"), Profile("Профиль"), Search("Поиск") }

@Composable
fun BottomNav(current: Tab, onSelect: (Tab) -> Unit) {
    // В «Стекле» и «Цвете пар» панель — парящая плашка со скруглёнными краями, в обычном стиле — во всю ширину.
    val floating = C.style != STYLE_CLASSIC
    val shape = RoundedCornerShape(30.dp)
    val outer = if (floating) Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp)
    else Modifier.fillMaxWidth().background(C.Card).navigationBarsPadding()
    val bar = when (C.style) {
        STYLE_GLASS -> Modifier.glass(shape, strong = true).padding(horizontal = 4.dp, vertical = 6.dp)
        STYLE_COLOR -> Modifier.clip(shape).background(C.Card).border(1.dp, C.Line, shape).padding(horizontal = 4.dp, vertical = 6.dp)
        else -> Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
    }
    // В «Цвете пар» подсветка тёмная, и иконка на ней светлая; подпись под ней остаётся обычного цвета.
    val solid = C.style == STYLE_COLOR
    Box(outer) {
        BoxWithConstraints(Modifier.fillMaxWidth().then(bar)) {
            // Подсветка под иконкой перепрыгивает на выбранную вкладку.
            val cell = maxWidth / Tab.entries.size
            val pill = 56.dp
            val x by animateDpAsState(cell * current.ordinal + (cell - pill) / 2, bouncy(), label = "nav")
            val glow = when (C.style) { STYLE_GLASS -> C.Accent.copy(alpha = 0.2f); STYLE_COLOR -> C.Ink; else -> C.Soft }
            Box(Modifier.offset(x = x, y = 4.dp).size(pill, 30.dp).clip(RoundedCornerShape(15.dp)).background(glow))
            Row(Modifier.fillMaxWidth()) {
                Tab.entries.forEach { tab ->
                    val on = tab == current
                    val color by animateColorAsState(if (on) (if (solid) C.Ink else C.Accent) else C.Muted, label = "navInk")
                    val iconColor by animateColorAsState(if (on && solid) C.Ground else color, label = "navIcon")
                    val pop = remember { Animatable(1f) }
                    LaunchedEffect(on) {
                        if (on) { pop.snapTo(0.75f); pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f)) }
                    }
                    Column(
                        Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp)).clickable { onSelect(tab) }.padding(top = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.height(30.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }, contentAlignment = Alignment.Center) {
                            Icon(tab, iconColor)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(tab.label, fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, color = color)
                    }
                }
            }
        }
    }
}
