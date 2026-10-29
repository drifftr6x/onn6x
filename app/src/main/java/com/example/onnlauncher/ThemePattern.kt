package com.example.onnlauncher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Plain shapes drawn behind the home screen for themes that set a [LauncherTheme.pattern]:
 * "bricks" (sky, clouds and a brick floor), "lattice" (a faint diamond grid with a golden glow),
 * "meadow" (sun, far mountains and rolling hills) and "swirl" (glowing spiral arms). Original drawings made of circles, lines and rectangles.
 */
@Composable
fun ThemePattern(pattern: String, modifier: Modifier = Modifier) {
    if (pattern.isEmpty()) return
    Canvas(modifier.fillMaxSize()) {
        when (pattern) {
            "bricks" -> {
                val brickH = 34.dp.toPx()
                val brickW = 68.dp.toPx()
                var y = size.height * 0.88f
                var row = 0
                while (y < size.height) {
                    var x = if (row % 2 == 0) 0f else -brickW / 2
                    while (x < size.width) {
                        drawRoundRect(
                            color = Color(0xFFB8501A).copy(alpha = 0.45f),
                            topLeft = Offset(x + 2f, y + 2f),
                            size = Size(brickW - 4f, brickH - 4f),
                            cornerRadius = CornerRadius(4f),
                        )
                        x += brickW
                    }
                    y += brickH
                    row++
                }
                val cloud = Color.White.copy(alpha = 0.8f)
                listOf(Triple(0.14f, 0.2f, 1f), Triple(0.55f, 0.12f, 1.3f), Triple(0.86f, 0.3f, 0.8f)).forEach { (fx, fy, k) ->
                    val r = 24.dp.toPx() * k
                    val cx = size.width * fx
                    val cy = size.height * fy
                    drawCircle(cloud, r, Offset(cx, cy))
                    drawCircle(cloud, r * 1.35f, Offset(cx + r * 1.3f, cy - r * 0.25f))
                    drawCircle(cloud, r, Offset(cx + r * 2.6f, cy))
                    drawRoundRect(cloud, Offset(cx - r, cy), Size(r * 4.6f, r), CornerRadius(r))
                }
            }
            "lattice" -> {
                val gold = Color(0xFFE6C34A)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(gold.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.5f, 0f),
                        radius = size.height * 0.9f,
                    ),
                    radius = size.height * 0.9f,
                    center = Offset(size.width * 0.5f, 0f),
                )
                val step = 90.dp.toPx()
                val line = gold.copy(alpha = 0.07f)
                var x = -size.height
                while (x < size.width + size.height) {
                    drawLine(line, Offset(x, 0f), Offset(x + size.height, size.height), strokeWidth = 2f)
                    drawLine(line, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 2f)
                    x += step
                }
            }
            "meadow" -> {
                val w = size.width
                val h = size.height
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFFFFF2B0).copy(alpha = 0.85f), Color.Transparent),
                        center = Offset(w * 0.86f, h * 0.14f),
                        radius = h * 0.4f,
                    ),
                    radius = h * 0.4f,
                    center = Offset(w * 0.86f, h * 0.14f),
                )
                val far = Path().apply {
                    moveTo(0f, h * 0.78f)
                    lineTo(w * 0.12f, h * 0.60f)
                    lineTo(w * 0.22f, h * 0.72f)
                    lineTo(w * 0.36f, h * 0.55f)
                    lineTo(w * 0.52f, h * 0.76f)
                    lineTo(w * 0.68f, h * 0.62f)
                    lineTo(w * 0.82f, h * 0.74f)
                    lineTo(w, h * 0.64f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(far, Color(0xFF9CC3D8).copy(alpha = 0.5f))
                fun hill(base: Float, amp: Float, color: Color) {
                    val path = Path().apply {
                        moveTo(0f, h)
                        lineTo(0f, h * base)
                        cubicTo(w * 0.25f, h * (base - amp), w * 0.4f, h * (base + amp), w * 0.6f, h * base)
                        cubicTo(w * 0.78f, h * (base - amp), w * 0.9f, h * (base + amp * 0.5f), w, h * (base - amp * 0.3f))
                        lineTo(w, h)
                        close()
                    }
                    drawPath(path, color)
                }
                hill(0.82f, 0.05f, Color(0xFF7BB661).copy(alpha = 0.55f))
                hill(0.91f, 0.04f, Color(0xFF4E9A4B).copy(alpha = 0.6f))
            }
            "swirl" -> {
                val green = Color(0xFF7CFF4F)
                val c = Offset(size.width * 0.78f, size.height * 0.42f)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(green.copy(alpha = 0.4f), Color.Transparent),
                        center = c,
                        radius = size.height * 0.6f,
                    ),
                    radius = size.height * 0.6f,
                    center = c,
                )
                for (arm in 0 until 3) {
                    val path = Path()
                    for (i in 0..300) {
                        val t = i / 300f
                        val angle = t * 13f + arm * 2.094f
                        val radius = t * size.height * 0.7f
                        val p = Offset(c.x + cos(angle) * radius, c.y + sin(angle) * radius * 0.9f)
                        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                    }
                    drawPath(path, green.copy(alpha = 0.2f), style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round))
                }
            }
        }
    }
}
