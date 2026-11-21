package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DeepViolet
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonPurpleBright
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.VoidBlack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Sleek 1.5-second animated intro overlay (no loading bar) with a glowing neon katana slash,
 * "Katana Slash Canvas" title, and "Ronin Motion" studio subtitle that fades directly into the canvas.
 */
@Composable
fun KatanaIntroOverlay(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val slashProgress = remember { Animatable(0f) }
    val titleAlpha = remember { Animatable(0f) }
    val overlayAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        val slashJob = launch {
            slashProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 680, easing = FastOutSlowInEasing)
            )
        }
        val titleJob = launch {
            delay(180)
            titleAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
            )
        }
        slashJob.join()
        titleJob.join()
        delay(450)
        overlayAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 350, easing = LinearEasing)
        )
        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha.value }
            .background(VoidBlack)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onFinished
            )
            .testTag("katana_intro_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val progress = slashProgress.value

            // Ambient radial purple energy behind the slash
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        NeonPurple.copy(alpha = 0.28f * progress),
                        DeepViolet.copy(alpha = 0.12f * progress),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.5f, h * 0.45f),
                    radius = w * 0.65f
                ),
                radius = w * 0.65f,
                center = Offset(w * 0.5f, h * 0.45f)
            )

            // Diagonal glowing katana slash curve across center
            val fullPath = Path().apply {
                moveTo(w * 0.10f, h * 0.64f)
                quadraticTo(
                    w * 0.48f, h * 0.28f,
                    w * 0.90f, h * 0.34f
                )
            }
            val measure = PathMeasure().apply { setPath(fullPath, false) }
            val segment = Path()
            val tailStart = ((progress - 0.15f).coerceAtLeast(0f)) * measure.length * 0.15f
            measure.getSegment(tailStart, measure.length * progress, segment, true)

            // Outer neon purple aura
            drawPath(
                path = segment,
                color = NeonPurple.copy(alpha = 0.32f),
                style = Stroke(width = 34f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            // Mid cyan-violet bloom
            drawPath(
                path = segment,
                brush = Brush.linearGradient(
                    colors = listOf(NeonPurpleBright, CyberCyan),
                    start = Offset(w * 0.10f, h * 0.64f),
                    end = Offset(w * 0.90f, h * 0.34f)
                ),
                style = Stroke(width = 13f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            // Sharp white katana core
            drawPath(
                path = segment,
                color = Color.White.copy(alpha = 0.95f),
                style = Stroke(width = 4.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Leading slash tip spark
            if (progress in 0.03f..0.98f) {
                val tip = measure.getPosition(measure.length * progress)
                drawCircle(
                    color = CyberCyan.copy(alpha = 0.7f),
                    radius = 18f * (1f - progress * 0.35f),
                    center = tip
                )
                drawCircle(
                    color = Color.White,
                    radius = 6f,
                    center = tip
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .graphicsLayer {
                    alpha = titleAlpha.value
                    translationY = (1f - titleAlpha.value) * 18f
                }
        ) {
            Spacer(Modifier.height(72.dp))
            Text(
                text = "Katana Slash Canvas",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "RONIN MOTION",
                style = MaterialTheme.typography.labelLarge.copy(
                    letterSpacing = 3.5.sp
                ),
                color = CyberCyan,
                textAlign = TextAlign.Center
            )
        }
    }
}
