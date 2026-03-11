package com.aspect.braveyoutube.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Custom loading spinner that avoids Material3's CircularProgressIndicator
 * (which triggers a KeyframesSpec binary incompatibility crash).
 */
@Composable
fun LoadingSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 4.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spinner")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 30f,
        targetValue = 270f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sweep"
    )

    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(
            width = strokeWidth.toPx(),
            cap = StrokeCap.Round
        )
        val arcSize = Size(
            this.size.width - stroke.width,
            this.size.height - stroke.width
        )
        val topLeft = Offset(stroke.width / 2, stroke.width / 2)

        drawArc(
            color = color,
            startAngle = rotation,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke
        )
    }
}
