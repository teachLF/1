package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BrightViolet
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.IceCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.PhantomMagenta

@Composable
fun VortexLogo(
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    incognito: Boolean = false,
    animated: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "vortex_spin")
    val spinAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )
    val glowPulse by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val primaryGlow = if (incognito) PhantomMagenta else ElectricCyan
    val secondaryGlow = if (incognito) BrightViolet else NeonViolet

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w / 2f, h / 2f)
        val radius = minOf(w, h) / 2f

        // Outer cosmic neon halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryGlow.copy(alpha = 0.28f * glowPulse),
                    secondaryGlow.copy(alpha = 0.14f * glowPulse),
                    Color.Transparent
                ),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )

        val rotation = if (animated) spinAngle else 0f

        rotate(degrees = rotation, pivot = center) {
            // Sweeping outer cyclone arcs
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        secondaryGlow,
                        primaryGlow,
                        Color.Transparent
                    ),
                    center = center
                ),
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.78f, center.y - radius * 0.78f),
                size = androidx.compose.ui.geometry.Size(radius * 1.56f, radius * 1.56f),
                style = Stroke(width = radius * 0.14f, cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        primaryGlow,
                        secondaryGlow,
                        Color.Transparent
                    ),
                    center = center
                ),
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.78f, center.y - radius * 0.78f),
                size = androidx.compose.ui.geometry.Size(radius * 1.56f, radius * 1.56f),
                style = Stroke(width = radius * 0.14f, cap = StrokeCap.Round)
            )
        }

        rotate(degrees = -rotation * 1.3f, pivot = center) {
            drawArc(
                color = IceCyan.copy(alpha = if (incognito) 0.55f else 0.85f),
                startAngle = 80f,
                sweepAngle = 115f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.52f, center.y - radius * 0.52f),
                size = androidx.compose.ui.geometry.Size(radius * 1.04f, radius * 1.04f),
                style = Stroke(width = radius * 0.10f, cap = StrokeCap.Round)
            )
            drawArc(
                color = BrightViolet.copy(alpha = 0.88f),
                startAngle = 260f,
                sweepAngle = 115f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.52f, center.y - radius * 0.52f),
                size = androidx.compose.ui.geometry.Size(radius * 1.04f, radius * 1.04f),
                style = Stroke(width = radius * 0.10f, cap = StrokeCap.Round)
            )
        }

        // Sharp static "V" prism core
        val vPath = Path().apply {
            moveTo(center.x - radius * 0.28f, center.y - radius * 0.22f)
            lineTo(center.x, center.y + radius * 0.30f)
            lineTo(center.x + radius * 0.28f, center.y - radius * 0.22f)
            lineTo(center.x + radius * 0.15f, center.y - radius * 0.22f)
            lineTo(center.x, center.y + radius * 0.08f)
            lineTo(center.x - radius * 0.15f, center.y - radius * 0.22f)
            close()
        }
        drawPath(
            path = vPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color.White, primaryGlow)
            )
        )
    }
}
