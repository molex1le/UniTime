package com.molex.nextpair.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/*
 * Стили экрана «Сегодня»: «Стекло» и «Цвет пар». Цвета подобраны под каждую из тем:
 * светлую, чёрную и «Неон».
 */

private enum class Look { Light, Black, Neon }

/** Своя акцентная пара есть только у «Неона». */
private val look get() = when {
    !C.Dark -> Look.Light
    C.palette.accent != null -> Look.Neon
    else -> Look.Black
}

// ---------- Стекло ----------

/** Цветное пятно под стеклом: центр и радиус в долях ширины экрана, [dx]/[dy] — откуда оно приплывает. */
private class Spot(val color: Color, val alpha: Float, val x: Float, val y: Float, val r: Float, val dx: Float, val dy: Float)

private fun spots(): List<Spot> = when (look) {
    Look.Light -> listOf(
        Spot(Color(0xFFA48BFF), 0.55f, 0.10f, 0.30f, 0.80f, -0.10f, 0.05f),
        Spot(Color(0xFFFF9CCB), 0.45f, 0.98f, 0.45f, 0.70f, 0.10f, -0.05f),
        Spot(Color(0xFF7FDCEB), 0.50f, 0.15f, 1.05f, 0.85f, -0.08f, 0.08f),
        Spot(Color(0xFF9DB2FF), 0.45f, 0.95f, 1.55f, 0.75f, 0.08f, 0.10f),
    )
    Look.Black -> listOf(
        Spot(Color(0xFF5B3BFF), 0.55f, 0.08f, 0.30f, 0.85f, -0.10f, 0.05f),
        Spot(Color(0xFFD1338A), 0.38f, 1.00f, 0.50f, 0.70f, 0.10f, -0.05f),
        Spot(Color(0xFF0AA0B4), 0.40f, 0.12f, 1.10f, 0.85f, -0.08f, 0.08f),
        Spot(Color(0xFF2F55FF), 0.40f, 0.95f, 1.60f, 0.80f, 0.08f, 0.10f),
    )
    Look.Neon -> listOf(
        Spot(Color(0xFFFF4FD8), 0.45f, 0.08f, 0.30f, 0.85f, -0.10f, 0.05f),
        Spot(Color(0xFF7B2CFF), 0.55f, 1.00f, 0.55f, 0.80f, 0.10f, -0.05f),
        Spot(Color(0xFF00E5FF), 0.32f, 0.12f, 1.15f, 0.85f, -0.08f, 0.08f),
        Spot(Color(0xFFFF4FD8), 0.25f, 0.95f, 1.65f, 0.75f, 0.08f, 0.10f),
    )
}

/**
 * Фон под стеклянными карточками. Пятна один раз мягко подплывают на место при появлении экрана
 * и дальше стоят: бесконечная анимация на весь экран ела бы батарею.
 * Сверху и снизу фон растворяется в обычном цвете, чтобы не было шва со строкой состояния и панелью вкладок.
 */
@Composable
fun GlassBackdrop() {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(1400, easing = FastOutSlowInEasing)) }
    val list = spots()
    val ground = C.Ground
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val v = p.value
        list.forEach { s ->
            val center = Offset((s.x + s.dx * (1f - v)) * w, (s.y + s.dy * (1f - v)) * w)
            val r = s.r * w
            drawCircle(
                Brush.radialGradient(
                    0f to s.color.copy(alpha = s.alpha * v),
                    0.55f to s.color.copy(alpha = s.alpha * v * 0.45f),
                    1f to Color.Transparent,
                    center = center, radius = r,
                ),
                radius = r, center = center,
            )
        }
        val fade = 56.dp.toPx()
        drawRect(Brush.verticalGradient(listOf(ground, ground.copy(alpha = 0f)), startY = 0f, endY = fade))
        drawRect(Brush.verticalGradient(listOf(ground.copy(alpha = 0f), ground), startY = size.height - fade * 1.5f, endY = size.height))
    }
}

/** Заливка стекла; [strong] — для главной карточки и панели вкладок. */
fun glassFill(strong: Boolean = false): Color = when (look) {
    Look.Light -> Color.White.copy(alpha = if (strong) 0.78f else 0.6f)
    Look.Black -> Color.White.copy(alpha = if (strong) 0.12f else 0.075f)
    Look.Neon -> Color(0xFFF0D9FF).copy(alpha = if (strong) 0.12f else 0.07f)
}

/** Кромка стекла: светлее сверху, будто на неё падает свет. */
private fun glassEdge(): Brush = when (look) {
    Look.Light -> Brush.verticalGradient(listOf(Color.White, Color(0x1A11151C)))
    else -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.06f)))
}

fun Modifier.glass(shape: Shape, strong: Boolean = false): Modifier =
    clip(shape).background(glassFill(strong)).border(1.dp, glassEdge(), shape)

/** Зелёный «идёт сейчас», который читается и на светлом стекле. */
val glassLive: Color get() = if (C.Dark) C.Live else Color(0xFF0E8A4F)

// ---------- Цвет пар ----------

/** Цвета пары по типу: [fill] и [ink] — карточка в списке, [strong] — главная карточка с белым текстом. */
class Tone(val fill: Color, val ink: Color, val strong: Color)

private fun tone(fill: Long, ink: Long, strong: Long) = Tone(Color(fill), Color(ink), Color(strong))

/** null — тип неизвестен, карточка остаётся обычной. */
fun typeTone(type: String): Tone? = when (look) {
    Look.Light -> when (type) {
        "ЛК" -> tone(0xFF9BE8BF, 0xFF0B3A22, 0xFF0E8A4F)
        "ПЗ" -> tone(0xFFFFC58F, 0xFF4A2400, 0xFFD45A08)
        "ЛР" -> tone(0xFFB3C6FF, 0xFF0E2366, 0xFF3A55E0)
        "Экз", "Экзамен" -> tone(0xFFFFB3C0, 0xFF5A0F1F, 0xFFC92A47)
        "Конс", "Консультация" -> tone(0xFFD7C2FF, 0xFF2E1366, 0xFF7046F0)
        else -> null
    }
    // На чёрном фоне пастель режет глаза — цвета чуть плотнее и темнее.
    Look.Black -> when (type) {
        "ЛК" -> tone(0xFF5FD39A, 0xFF052615, 0xFF0E8A4F)
        "ПЗ" -> tone(0xFFFFA45C, 0xFF2E1400, 0xFFD45A08)
        "ЛР" -> tone(0xFF8FA8FF, 0xFF08154A, 0xFF3A55E0)
        "Экз", "Экзамен" -> tone(0xFFFF8A9C, 0xFF3A0712, 0xFFC92A47)
        "Конс", "Консультация" -> tone(0xFFB897FF, 0xFF1E0B47, 0xFF7046F0)
        else -> null
    }
    Look.Neon -> when (type) {
        "ЛК" -> tone(0xFF3DFFB4, 0xFF00261A, 0xFF009660)
        "ПЗ" -> tone(0xFFFFB547, 0xFF2E1700, 0xFFE55A00)
        "ЛР" -> tone(0xFF2DE6FF, 0xFF00262B, 0xFF0068E0)
        "Экз", "Экзамен" -> tone(0xFFFF5C9A, 0xFF2E0014, 0xFFE0005A)
        "Конс", "Консультация" -> tone(0xFFFF6FE0, 0xFF2E0026, 0xFFB000FF)
        else -> null
    }
}
