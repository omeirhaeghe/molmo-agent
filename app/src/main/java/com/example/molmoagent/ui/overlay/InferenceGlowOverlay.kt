package com.example.molmoagent.ui.overlay

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.molmoagent.agent.AgentState

/**
 * A full-screen transparent overlay that draws an animated glowing border
 * around the screen edges when the agent is actively running inference.
 *
 * The glow color indicates the current state:
 *   - REASONING (cyan) — model is thinking
 *   - OBSERVING (amber) — capturing screenshot
 *   - ACTING (green)   — executing an action
 *
 * A travelling highlight sweeps around the border for a "scanning" feel,
 * and the overall intensity pulses gently.
 */
@Composable
fun InferenceGlowOverlay(agentState: AgentState) {
    val isActive = agentState == AgentState.REASONING ||
            agentState == AgentState.OBSERVING ||
            agentState == AgentState.ACTING

    // Pulse animation for overall glow intensity
    val infiniteTransition = rememberInfiniteTransition(label = "glow")

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Travelling highlight that sweeps around the border (0..1 = full loop)
    val sweep by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )

    val baseColor = when (agentState) {
        AgentState.REASONING -> Color(0xFF00BCD4) // cyan
        AgentState.OBSERVING -> Color(0xFFFFA726) // amber
        AgentState.ACTING    -> Color(0xFF66BB6A) // green
        else                 -> Color(0xFF00BCD4)
    }

    if (!isActive) return

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val borderWidth = 6f
        val glowWidth = 28f

        // Outer glow (wide, faint)
        drawRect(
            color = baseColor.copy(alpha = pulseAlpha * 0.25f),
            topLeft = Offset.Zero,
            size = Size(w, h),
            style = Stroke(width = glowWidth)
        )

        // Mid glow
        drawRect(
            color = baseColor.copy(alpha = pulseAlpha * 0.45f),
            topLeft = Offset(glowWidth / 4, glowWidth / 4),
            size = Size(w - glowWidth / 2, h - glowWidth / 2),
            style = Stroke(width = glowWidth / 2)
        )

        // Sharp inner border
        drawRect(
            color = baseColor.copy(alpha = pulseAlpha * 0.7f),
            topLeft = Offset(glowWidth / 2, glowWidth / 2),
            size = Size(w - glowWidth, h - glowWidth),
            style = Stroke(width = borderWidth)
        )

        // Travelling highlight — a bright dot/segment that sweeps the perimeter
        val perimeter = 2 * (w + h)
        val highlightLen = perimeter * 0.15f // 15% of perimeter
        val highlightStart = sweep * perimeter

        // Build a path for the highlight segment
        val highlightPath = Path()
        val points = perimeterPoints(w, h, highlightStart, highlightLen, perimeter)
        if (points.isNotEmpty()) {
            highlightPath.moveTo(points.first().first, points.first().second)
            for (i in 1 until points.size) {
                highlightPath.lineTo(points[i].first, points[i].second)
            }
        }

        drawPath(
            path = highlightPath,
            color = baseColor.copy(alpha = pulseAlpha),
            style = Stroke(width = borderWidth + 4f, cap = StrokeCap.Round)
        )
        // Bright core of highlight
        drawPath(
            path = highlightPath,
            color = Color.White.copy(alpha = pulseAlpha * 0.6f),
            style = Stroke(width = borderWidth, cap = StrokeCap.Round)
        )
    }
}

/**
 * Sample points along the rectangle perimeter from [start] for [length] distance.
 * Returns a list of (x, y) pairs.
 */
private fun perimeterPoints(
    w: Float, h: Float,
    start: Float, length: Float,
    perimeter: Float
): List<Pair<Float, Float>> {
    val step = 4f // every 4px
    val count = (length / step).toInt().coerceAtLeast(2)
    return (0..count).map { i ->
        val dist = (start + i * step) % perimeter
        pointOnPerimeter(w, h, dist)
    }
}

/** Map a distance along the perimeter to an (x, y) coordinate. */
private fun pointOnPerimeter(w: Float, h: Float, dist: Float): Pair<Float, Float> {
    return when {
        dist <= w          -> Pair(dist, 0f)              // top edge
        dist <= w + h      -> Pair(w, dist - w)           // right edge
        dist <= 2 * w + h  -> Pair(w - (dist - w - h), h) // bottom edge
        else               -> Pair(0f, h - (dist - 2 * w - h)) // left edge
    }
}
