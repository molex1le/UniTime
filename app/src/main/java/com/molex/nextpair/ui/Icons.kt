package com.molex.nextpair.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class Glyph { Clock, Calendar, Doc, Search, Back, Bell, Star, StarFilled, Chevron, Cap, Gear, Plus, Check, People, Pin, EyeOff, Flag, Trash, User, ArrowUp }

/** Линейные иконки 24×24, как в макете. */
@Composable
fun Glyph(g: Glyph, color: Color, size: Dp = 24.dp) {
    Canvas(Modifier.size(size)) {
        val k = this.size.width / 24f
        val st = Stroke(width = 2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(vararg pts: Float, close: Boolean = false) = Path().apply {
            moveTo(pts[0] * k, pts[1] * k)
            var i = 2
            while (i < pts.size) { lineTo(pts[i] * k, pts[i + 1] * k); i += 2 }
            if (close) close()
        }
        fun DrawScope.line(vararg pts: Float, close: Boolean = false) = drawPath(p(*pts, close = close), color, style = st)
        when (g) {
            Glyph.Clock -> {
                drawCircle(color, 9f * k, Offset(12f * k, 12f * k), style = st)
                line(12f, 7f, 12f, 12f, 15f, 14f)
            }
            Glyph.Calendar -> {
                drawRoundRect(color, Offset(3f * k, 5f * k), Size(18f * k, 16f * k), CornerRadius(3f * k), style = st)
                line(3f, 10f, 21f, 10f); line(8f, 3f, 8f, 7f); line(16f, 3f, 16f, 7f)
            }
            Glyph.Doc -> {
                line(4f, 4f, 16f, 4f, 20f, 8f, 20f, 20f, 4f, 20f, close = true)
                line(8f, 12f, 16f, 12f); line(8f, 16f, 13f, 16f)
            }
            Glyph.Search -> {
                drawCircle(color, 7f * k, Offset(11f * k, 11f * k), style = st)
                line(20f, 20f, 16f, 16f)
            }
            Glyph.Back -> line(15f, 5f, 8f, 12f, 15f, 19f)
            Glyph.Chevron -> line(9f, 5f, 16f, 12f, 9f, 19f)
            Glyph.Bell -> {
                val b = Path().apply {
                    moveTo(6f * k, 16f * k); lineTo(6f * k, 9f * k)
                    cubicTo(6f * k, 1f * k, 18f * k, 1f * k, 18f * k, 9f * k)
                    lineTo(18f * k, 16f * k); lineTo(20f * k, 18f * k); lineTo(4f * k, 18f * k); close()
                }
                drawPath(b, color, style = st)
                line(10f, 21f, 14f, 21f)
            }
            Glyph.Star, Glyph.StarFilled -> {
                val s = p(12f, 3f, 14.8f, 8.8f, 21f, 9.3f, 16.3f, 13.5f, 17.7f, 19.8f, 12f, 16.5f, 6.3f, 19.8f, 7.7f, 13.5f, 3f, 9.3f, 9.2f, 8.8f, close = true)
                if (g == Glyph.StarFilled) drawPath(s, color)
                drawPath(s, color, style = st)
            }
            Glyph.Gear -> {
                drawCircle(color, 3f * k, Offset(12f * k, 12f * k), style = st)
                drawCircle(color, 8f * k, Offset(12f * k, 12f * k), style = st)
                for (a in 0 until 8) {
                    val r = Math.toRadians(a * 45.0)
                    val c = kotlin.math.cos(r).toFloat()
                    val sn = kotlin.math.sin(r).toFloat()
                    line(12f + 8f * c, 12f + 8f * sn, 12f + 10.5f * c, 12f + 10.5f * sn)
                }
            }
            Glyph.Plus -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
            Glyph.Check -> line(5f, 12.5f, 10f, 17.5f, 19f, 7f)
            Glyph.People -> {
                drawCircle(color, 3.5f * k, Offset(9f * k, 8f * k), style = st)
                drawPath(Path().apply { moveTo(2.5f * k, 20f * k); cubicTo(2.5f * k, 13f * k, 15.5f * k, 13f * k, 15.5f * k, 20f * k) }, color, style = st)
                drawCircle(color, 3f * k, Offset(17f * k, 9f * k), style = st)
                drawPath(Path().apply { moveTo(17f * k, 14.5f * k); cubicTo(20f * k, 14.5f * k, 22f * k, 16.5f * k, 22f * k, 20f * k) }, color, style = st)
            }
            Glyph.Pin -> {
                drawPath(Path().apply {
                    moveTo(12f * k, 21f * k)
                    cubicTo(5f * k, 14f * k, 5f * k, 10f * k, 5f * k, 9.5f * k)
                    cubicTo(5f * k, 0.5f * k, 19f * k, 0.5f * k, 19f * k, 9.5f * k)
                    cubicTo(19f * k, 10f * k, 19f * k, 14f * k, 12f * k, 21f * k); close()
                }, color, style = st)
                drawCircle(color, 2.5f * k, Offset(12f * k, 9.5f * k), style = st)
            }
            Glyph.EyeOff -> {
                drawPath(Path().apply {
                    moveTo(2f * k, 12f * k); cubicTo(6f * k, 5f * k, 18f * k, 5f * k, 22f * k, 12f * k)
                    cubicTo(18f * k, 19f * k, 6f * k, 19f * k, 2f * k, 12f * k); close()
                }, color, style = st)
                drawCircle(color, 3f * k, Offset(12f * k, 12f * k), style = st)
                line(4f, 4f, 20f, 20f)
            }
            Glyph.Flag -> { line(5f, 21f, 5f, 4f); line(5f, 4f, 18f, 4f, 15f, 8.5f, 18f, 13f, 5f, 13f) }
            Glyph.Trash -> {
                line(4f, 7f, 20f, 7f); line(9f, 7f, 9f, 4f, 15f, 4f, 15f, 7f)
                line(6f, 7f, 7f, 20f, 17f, 20f, 18f, 7f)
            }
            Glyph.User -> {
                drawCircle(color, 4f * k, Offset(12f * k, 8f * k), style = st)
                drawPath(Path().apply { moveTo(4f * k, 21f * k); cubicTo(4f * k, 13f * k, 20f * k, 13f * k, 20f * k, 21f * k) }, color, style = st)
            }
            Glyph.ArrowUp -> { line(12f, 19f, 12f, 5f); line(6f, 11f, 12f, 5f, 18f, 11f) }
            Glyph.Cap -> {
                line(12f, 4f, 2f, 9f, 12f, 14f, 22f, 9f, close = true)
                line(6f, 11.5f, 6f, 16f, 12f, 19f, 18f, 16f, 18f, 11.5f)
            }
        }
    }
}

@Composable
fun Icon(tab: Tab, color: Color) = Glyph(
    when (tab) {
        Tab.Today -> Glyph.Clock
        Tab.Feed -> Glyph.Calendar
        Tab.Deadlines -> Glyph.Flag
        Tab.Profile -> Glyph.User
        Tab.Search -> Glyph.Search
    },
    color,
)
