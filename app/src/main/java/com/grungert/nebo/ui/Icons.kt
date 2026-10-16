package com.grungert.nebo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A small hand-drawn line icon set, drawn on a 24-unit grid with round strokes. */
enum class NeboIcon { Back, Journal, Stats, Settings, Mic, Play, Star, StarFilled, Cloud, Steps, Bell, Moon, Sun, EyeClosed, Similar, Breath, Silence, Week, Pen, Check, Search, Share, Pause, Stop, Close, Trash, Points, Heart, Body, Urge }

@Composable
fun Icon(icon: NeboIcon, color: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        val st = Stroke(width = 1.8f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(vararg pts: Float) = Offset(pts[0] * k, pts[1] * k)
        fun path(block: Path.() -> Unit) = Path().apply(block)
        fun Path.m(x: Float, y: Float) = moveTo(x * k, y * k)
        fun Path.l(x: Float, y: Float) = lineTo(x * k, y * k)
        fun Path.q(cx: Float, cy: Float, x: Float, y: Float) = quadraticTo(cx * k, cy * k, x * k, y * k)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, p(x1, y1), p(x2, y2), st.width, StrokeCap.Round)
        fun circle(x: Float, y: Float, r: Float, fill: Boolean = false) =
            if (fill) drawCircle(color, r * k, p(x, y)) else drawCircle(color, r * k, p(x, y), style = st)
        when (icon) {
            NeboIcon.Back -> drawPath(path { m(15f, 5f); l(8f, 12f); l(15f, 19f) }, color, style = st)
            NeboIcon.Journal -> {
                drawPath(path { m(5f, 4.5f); l(16f, 4.5f); q(19f, 4.5f, 19f, 7.5f); l(19f, 19.5f); l(8f, 19.5f); q(5f, 19.5f, 5f, 16.5f); close() }, color, style = st)
                line(9f, 9f, 15f, 9f); line(9f, 12.5f, 15f, 12.5f)
            }
            NeboIcon.Stats -> { line(5f, 19f, 5f, 13f); line(10f, 19f, 10f, 8f); line(15f, 19f, 15f, 11f); line(20f, 19f, 20f, 5f) }
            NeboIcon.Settings -> {
                line(4f, 7f, 20f, 7f); line(4f, 12f, 20f, 12f); line(4f, 17f, 20f, 17f)
                circle(9f, 7f, 2.2f, true); circle(15f, 12f, 2.2f, true); circle(7f, 17f, 2.2f, true)
            }
            NeboIcon.Mic -> {
                drawRoundRect(color, p(9f, 3.5f), Size(6f * k, 11f * k), androidx.compose.ui.geometry.CornerRadius(3f * k), style = st)
                drawPath(path { m(5.5f, 11.5f); q(5.5f, 17.5f, 12f, 17.5f); q(18.5f, 17.5f, 18.5f, 11.5f) }, color, style = st)
                line(12f, 17.5f, 12f, 21f)
            }
            NeboIcon.Play -> drawPath(path { m(8f, 5f); l(19f, 12f); l(8f, 19f); close() }, color)
            NeboIcon.Star, NeboIcon.StarFilled -> {
                val s = path {
                    for (i in 0 until 10) {
                        val a = -Math.PI / 2 + i * Math.PI / 5
                        val r = if (i % 2 == 0) 8.5f else 3.8f
                        val x = 12f + (r * Math.cos(a)).toFloat(); val y = 12.5f + (r * Math.sin(a)).toFloat()
                        if (i == 0) m(x, y) else l(x, y)
                    }
                    close()
                }
                if (icon == NeboIcon.StarFilled) drawPath(s, color) else drawPath(s, color, style = st)
            }
            NeboIcon.Cloud -> drawPath(path {
                m(6.5f, 18f); q(3f, 18f, 3f, 14.8f); q(3f, 11.8f, 6.2f, 11.6f); q(6.8f, 6.5f, 11.8f, 6.5f)
                q(15.6f, 6.5f, 16.8f, 10f); q(21f, 10.2f, 21f, 14f); q(21f, 18f, 17f, 18f); close()
            }, color, style = st)
            NeboIcon.Steps -> {
                drawOval(color, p(5f, 9f), Size(4.5f * k, 7f * k), style = st); drawOval(color, p(14.5f, 4f), Size(4.5f * k, 7f * k), style = st)
                circle(7.2f, 19f, 1.6f, true); circle(16.8f, 14f, 1.6f, true)
            }
            NeboIcon.Bell -> {
                drawPath(path { m(6f, 17f); l(6f, 11f); q(6f, 5f, 12f, 5f); q(18f, 5f, 18f, 11f); l(18f, 17f); close() }, color, style = st)
                line(4f, 17f, 20f, 17f); circle(12f, 20f, 1.3f, true)
            }
            NeboIcon.Moon -> drawPath(path { m(15f, 4f); q(7f, 5f, 7f, 12f); q(7f, 20f, 16f, 20f); q(11f, 17f, 11f, 12f); q(11f, 7f, 15f, 4f); close() }, color, style = st)
            NeboIcon.Sun -> {
                circle(12f, 12f, 4f)
                for (i in 0 until 8) {
                    val a = i * Math.PI / 4
                    line(12f + 7f * Math.cos(a).toFloat(), 12f + 7f * Math.sin(a).toFloat(), 12f + 9.5f * Math.cos(a).toFloat(), 12f + 9.5f * Math.sin(a).toFloat())
                }
            }
            NeboIcon.EyeClosed -> {
                drawPath(path { m(3f, 10f); q(12f, 18f, 21f, 10f) }, color, style = st)
                line(6.5f, 13.6f, 5f, 16f); line(12f, 15.2f, 12f, 18f); line(17.5f, 13.6f, 19f, 16f)
            }
            NeboIcon.Similar -> { circle(9f, 12f, 5.5f); circle(15f, 12f, 5.5f) }
            NeboIcon.Breath -> { circle(12f, 12f, 8f); circle(12f, 12f, 3.5f) }
            NeboIcon.Silence -> { circle(12f, 12f, 8f); line(8f, 12f, 16f, 12f) }
            NeboIcon.Week -> {
                drawRoundRect(color, p(4f, 5.5f), Size(16f * k, 14f * k), androidx.compose.ui.geometry.CornerRadius(3f * k), style = st)
                line(4f, 10f, 20f, 10f); line(8.5f, 3.5f, 8.5f, 7f); line(15.5f, 3.5f, 15.5f, 7f)
            }
            NeboIcon.Pen -> { drawPath(path { m(5f, 19f); l(6f, 15f); l(16f, 5f); l(19f, 8f); l(9f, 18f); close() }, color, style = st) }
            NeboIcon.Pause -> { drawRoundRect(color, p(7f, 5f), Size(3.5f * k, 14f * k), androidx.compose.ui.geometry.CornerRadius(1.5f * k)); drawRoundRect(color, p(13.5f, 5f), Size(3.5f * k, 14f * k), androidx.compose.ui.geometry.CornerRadius(1.5f * k)) }
            NeboIcon.Stop -> drawRoundRect(color, p(6.5f, 6.5f), Size(11f * k, 11f * k), androidx.compose.ui.geometry.CornerRadius(2.5f * k))
            NeboIcon.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
            NeboIcon.Trash -> {
                line(4.5f, 7f, 19.5f, 7f); drawPath(path { m(9.5f, 7f); l(9.5f, 4.5f); l(14.5f, 4.5f); l(14.5f, 7f) }, color, style = st)
                drawPath(path { m(6.5f, 7f); l(7.5f, 19.5f); l(16.5f, 19.5f); l(17.5f, 7f) }, color, style = st)
            }
            NeboIcon.Check -> drawPath(path { m(5f, 12.5f); l(10f, 17.5f); l(19f, 7f) }, color, style = st)
            NeboIcon.Search -> { circle(10.5f, 10.5f, 6f); line(15f, 15f, 20f, 20f) }
            NeboIcon.Points -> {
                // four points, each joined to the others
                line(6f, 6f, 18f, 6f); line(6f, 18f, 18f, 18f); line(6f, 6f, 6f, 18f); line(18f, 6f, 18f, 18f); line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f)
                circle(6f, 6f, 2.6f, fill = true); circle(18f, 6f, 2.6f, fill = true); circle(6f, 18f, 2.6f, fill = true); circle(18f, 18f, 2.6f, fill = true)
            }
            NeboIcon.Heart -> drawPath(path {
                m(12f, 19.5f); q(4f, 14f, 4.5f, 9f); q(5.2f, 5f, 9f, 5.2f); q(11f, 5.4f, 12f, 7.5f)
                q(13f, 5.4f, 15f, 5.2f); q(18.8f, 5f, 19.5f, 9f); q(20f, 14f, 12f, 19.5f); close()
            }, color, style = st)
            NeboIcon.Body -> {
                circle(12f, 5.5f, 2.4f)
                drawPath(path { m(12f, 8.5f); l(12f, 14.5f); m(6.5f, 10.5f); q(12f, 12.5f, 17.5f, 10.5f); m(12f, 14.5f); l(8.5f, 20f); m(12f, 14.5f); l(15.5f, 20f) }, color, style = st)
            }
            NeboIcon.Urge -> {
                drawPath(path { m(4f, 12f); q(9f, 12f, 12f, 12f); l(18f, 12f) }, color, style = st)
                drawPath(path { m(14.5f, 7.5f); l(19f, 12f); l(14.5f, 16.5f) }, color, style = st)
                line(4f, 7.5f, 8f, 7.5f); line(4f, 16.5f, 8f, 16.5f)
            }
            NeboIcon.Share -> {
                drawPath(path { m(8f, 9f); l(5.5f, 9f); l(5.5f, 20f); l(18.5f, 20f); l(18.5f, 9f); l(16f, 9f) }, color, style = st)
                line(12f, 3.5f, 12f, 14f); drawPath(path { m(8.5f, 7f); l(12f, 3.5f); l(15.5f, 7f) }, color, style = st)
            }
        }
    }
}

