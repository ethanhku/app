package app.mossling

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import app.mossling.core.Mood
import app.mossling.core.Stage

val Cream = Color(0xFFF3F1E6)
val Moss = Color(0xFF6E9C4F)
val MossLight = Color(0xFF8DBF63)
val MossDark = Color(0xFF4F7A36)
val Ink = Color(0xFF3B3A30)
val Sun = Color(0xFFE9B949)
val Blush = Color(0xFFF2A9A0)
val Night = Color(0xFF2E3550)

/** Mossy: big eyes, small body, soft colors. Grows sprouts and flowers with each stage. */
@Composable
fun Mossy(stage: Stage, mood: Mood, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "breath")
    val breath by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (mood == Mood.RESTING || mood == Mood.DROWSY) 3200 else 2000, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "breath",
    )
    Canvas(modifier) { drawMossy(stage, mood, breath) }
}

private fun DrawScope.drawMossy(stage: Stage, mood: Mood, breath: Float) {
    val w = size.width
    val h = size.height
    val growth = 0.55f + 0.09f * stage.ordinal
    val bodyW = w * 0.62f * growth
    val bodyH = bodyW * (0.78f + 0.03f * breath)
    val cx = w / 2
    val groundY = h * 0.86f
    val top = groundY - bodyH

    // Ground and shadow.
    drawOval(MossDark.copy(alpha = 0.18f), Offset(cx - bodyW * 0.7f, groundY - bodyH * 0.08f), Size(bodyW * 1.4f, bodyH * 0.2f))

    // Sprouts, more of them as Mossy grows.
    val sprouts = stage.ordinal + 1
    for (i in 0 until sprouts) {
        val f = if (sprouts == 1) 0.5f else i / (sprouts - 1f)
        val x = cx - bodyW * 0.28f + f * bodyW * 0.56f
        val base = Offset(x, top + bodyH * 0.06f)
        val tip = Offset(x + (f - 0.5f) * bodyW * 0.2f, top - bodyH * (0.16f + 0.05f * (i % 2)))
        drawLine(MossDark, base, tip, strokeWidth = bodyW * 0.025f, cap = StrokeCap.Round)
        drawOval(MossLight, tip - Offset(bodyW * 0.05f, bodyW * 0.03f), Size(bodyW * 0.1f, bodyW * 0.06f))
        if (stage >= Stage.BLOOM && i % 2 == 0) {
            drawCircle(Blush, bodyW * 0.035f, tip + Offset(0f, -bodyW * 0.04f))
            drawCircle(Sun, bodyW * 0.015f, tip + Offset(0f, -bodyW * 0.04f))
        }
    }

    // Body: a soft dome.
    val body = Path().apply {
        moveTo(cx - bodyW / 2, groundY)
        cubicTo(cx - bodyW / 2, top + bodyH * 0.1f, cx - bodyW * 0.25f, top, cx, top)
        cubicTo(cx + bodyW * 0.25f, top, cx + bodyW / 2, top + bodyH * 0.1f, cx + bodyW / 2, groundY)
        close()
    }
    drawPath(body, Moss)
    // Fuzzy moss texture.
    for (i in 0 until 14) {
        val a = i * 0.45f
        val r = bodyW * 0.3f
        drawCircle(MossLight.copy(alpha = 0.45f), bodyW * 0.03f, Offset(cx + r * kotlin.math.cos(a) * 0.9f, top + bodyH * 0.5f + r * kotlin.math.sin(a) * 0.5f))
    }

    // Face. Eyes sit low and wide, the "baby schema" proportions.
    val eyeY = top + bodyH * 0.55f
    val eyeDx = bodyW * 0.17f
    val eyeR = bodyW * 0.085f
    val stroke = Stroke(width = bodyW * 0.022f, cap = StrokeCap.Round)
    for (side in listOf(-1f, 1f)) {
        val c = Offset(cx + side * eyeDx, eyeY)
        when (mood) {
            Mood.GLOWING, Mood.CONTENT -> {
                drawCircle(Ink, eyeR, c)
                drawCircle(Color.White, eyeR * 0.35f, c + Offset(-eyeR * 0.3f, -eyeR * 0.35f))
                if (mood == Mood.GLOWING) drawCircle(Color.White, eyeR * 0.15f, c + Offset(eyeR * 0.35f, eyeR * 0.3f))
            }
            Mood.SQUINTING -> {
                drawArc(Ink, 200f, 140f, false, c - Offset(eyeR, eyeR * 0.6f), Size(eyeR * 2, eyeR * 1.4f), style = stroke)
                drawLine(Ink, c + Offset(-eyeR, -eyeR * 0.1f), c + Offset(eyeR, -eyeR * 0.1f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            Mood.DROWSY, Mood.RESTING -> {
                drawArc(Ink, 20f, 140f, false, c - Offset(eyeR, eyeR), Size(eyeR * 2, eyeR * 1.4f), style = stroke)
            }
        }
        if (mood != Mood.SQUINTING) {
            drawOval(Blush.copy(alpha = 0.7f), c + Offset(side * eyeR * 0.6f - eyeR * 0.6f, eyeR * 1.3f), Size(eyeR * 1.2f, eyeR * 0.6f))
        }
    }

    // Mouth.
    val mouthC = Offset(cx, eyeY + eyeR * 1.4f)
    when (mood) {
        Mood.GLOWING -> drawArc(Ink, 0f, 180f, true, mouthC - Offset(eyeR * 0.6f, eyeR * 0.4f), Size(eyeR * 1.2f, eyeR * 0.9f))
        Mood.CONTENT -> drawArc(Ink, 20f, 140f, false, mouthC - Offset(eyeR * 0.5f, eyeR * 0.5f), Size(eyeR, eyeR * 0.7f), style = stroke)
        Mood.SQUINTING -> drawLine(Ink, mouthC - Offset(eyeR * 0.35f, 0f), mouthC + Offset(eyeR * 0.35f, 0f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        Mood.DROWSY, Mood.RESTING -> drawCircle(Ink, eyeR * 0.22f, mouthC)
    }

    // Sleeping details.
    if (mood == Mood.RESTING) {
        val cap = Path().apply {
            moveTo(cx - bodyW * 0.32f, top + bodyH * 0.12f)
            quadraticTo(cx + bodyW * 0.05f, top - bodyH * 0.35f, cx + bodyW * 0.42f, top + bodyH * 0.02f)
            quadraticTo(cx, top - bodyH * 0.02f, cx - bodyW * 0.32f, top + bodyH * 0.12f)
        }
        drawPath(cap, Night)
        drawCircle(Color.White, bodyW * 0.045f, Offset(cx + bodyW * 0.44f, top + bodyH * 0.04f))
    }
    if (mood == Mood.RESTING || mood == Mood.DROWSY) {
        val zx = cx + bodyW * 0.45f
        val zy = top - bodyH * 0.05f - breath * bodyH * 0.08f
        for (k in 0..2) {
            val s = bodyW * (0.05f + 0.02f * k)
            val o = Offset(zx + k * s * 1.1f, zy - k * s * 1.4f)
            val zPath = Path().apply {
                moveTo(o.x, o.y)
                lineTo(o.x + s, o.y)
                lineTo(o.x, o.y + s)
                lineTo(o.x + s, o.y + s)
            }
            drawPath(zPath, Ink.copy(alpha = 0.5f - 0.12f * k), style = Stroke(width = bodyW * 0.012f, cap = StrokeCap.Round))
        }
    }
    if (mood == Mood.GLOWING) {
        for (k in 0..3) {
            val a = -2.6f + k * 0.75f
            val r = bodyW * (0.62f + 0.04f * breath)
            drawCircle(Sun.copy(alpha = 0.6f), bodyW * 0.018f, Offset(cx + r * kotlin.math.cos(a), groundY - bodyH * 0.4f + r * kotlin.math.sin(a) * 0.7f))
        }
    }
}
