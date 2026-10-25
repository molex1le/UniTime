package com.molex.nextpair.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/*
 * Анимации интерфейса. Всё на встроенных анимациях Compose: при «Удалить анимацию»
 * в настройках телефона они сами доигрываются мгновенно.
 */

/** Пружина для «живых» движений: подсветка вкладки, ползунок, нажатие. */
fun <T> bouncy() = spring<T>(dampingRatio = 0.62f, stiffness = 420f)

/**
 * Когда экран показан. Карточки, появившиеся в первые полсекунды, выезжают по очереди;
 * позже (прокрутка ленты) — сразу, без анимации. 0 — экран без появления (вернулись назад).
 */
val LocalEnterClock = compositionLocalOf { 0L }

fun enterClockNow() = SystemClock.uptimeMillis()

/** Плавное появление снизу; [index] — порядок на экране, задаёт задержку. */
@Composable
fun Modifier.enter(index: Int): Modifier {
    val start = LocalEnterClock.current
    val animate = remember { start != 0L && SystemClock.uptimeMillis() - start < 500 }
    if (!animate) return this
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(8) * 45L)
        p.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow))
    }
    return graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * 22.dp.toPx()
    }
}

/** Карточка чуть «проседает» под пальцем и пружинит обратно. */
@Composable
fun Modifier.pressScale(source: MutableInteractionSource, pressed: Float = 0.96f): Modifier {
    val down by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (down) pressed else 1f, bouncy(), label = "press")
    return graphicsLayer { scaleX = s; scaleY = s }
}

/** clickable + пружинка при нажатии. Ставить первым в цепочке, чтобы масштабировалась вся карточка. */
@Composable
fun Modifier.bounceClick(enabled: Boolean = true, label: String? = null, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return pressScale(source).clickable(source, LocalIndication.current, enabled = enabled, onClickLabel = label, onClick = onClick)
}

/** Точка «идёт сейчас» с расходящимся кольцом. */
@Composable
fun LiveDot(color: Color, size: Dp = 8.dp) {
    val t = rememberInfiniteTransition(label = "live")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Restart), label = "ring")
    Box(Modifier.size(size * 3), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size).graphicsLayer {
                val e = 1f - (1f - p) * (1f - p)
                scaleX = 1f + e * 1.8f; scaleY = scaleX
                alpha = (1f - p) * 0.6f
            }.background(color, CircleShape),
        )
        Box(Modifier.size(size).background(color, CircleShape))
    }
}

/**
 * Число, которое «досчитывается» от нуля при появлении: «8,42», «24 ч».
 * Если в начале строки нет числа — просто текст.
 */
@Composable
fun CountUpText(value: String, color: Color, fontSize: TextUnit, fontWeight: FontWeight, lineHeight: TextUnit) {
    val m = remember(value) { Regex("""^(\d+)(?:([.,])(\d+))?(.*)$""").find(value) }
    if (m == null) {
        Text(value, color = color, fontSize = fontSize, fontWeight = fontWeight, lineHeight = lineHeight)
        return
    }
    val (int, sep, frac, rest) = m.destructured
    val target = "$int.${frac.ifEmpty { "0" }}".toDouble()
    val p = remember(value) { Animatable(0f) }
    LaunchedEffect(value) { p.animateTo(1f, tween(900, easing = { 1f - (1f - it) * (1f - it) * (1f - it) })) }
    val v = target * p.value
    val shown = if (frac.isEmpty()) "${Math.round(v)}" else "%.${frac.length}f".format(java.util.Locale.ROOT, v).replace(".", sep)
    Text(shown + rest, color = color, fontSize = fontSize, fontWeight = fontWeight, lineHeight = lineHeight)
}
