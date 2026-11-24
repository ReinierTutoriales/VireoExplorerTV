package com.reiniertutoriales.vireoexplorertv

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.darkColorScheme

internal val VireoCardShape = RoundedCornerShape(24.dp)
internal val VireoInputShape = RoundedCornerShape(16.dp)
internal val VireoPillShape = CircleShape

private val AmoledColors = darkColorScheme(
    primary = Color(0xFF80DCC0),
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF124B3C),
    onPrimaryContainer = Color(0xFFA6F5DC),
    secondary = Color(0xFFB3CCC2),
    onSecondary = Color(0xFF1E352E),
    secondaryContainer = Color(0xFF1D2925),
    onSecondaryContainer = Color(0xFFD6E6DF),
    inverseSurface = Color(0xFF80DCC0),
    inverseOnSurface = Color(0xFF00382B),
    background = Color.Black,
    onBackground = Color(0xFFF2F4F3),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFF2F4F3),
    surfaceVariant = Color(0xFF1E2422),
    onSurfaceVariant = Color(0xFFBFC9C4),
    border = Color(0xFF35423E),
    borderVariant = Color(0xFF242D2A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun VireoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AmoledColors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            colors = SurfaceDefaults.colors(
                containerColor = AmoledColors.background,
                contentColor = AmoledColors.onBackground
            )
        ) {
            content()
        }
    }
}

@Composable
internal fun VireoProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val clamped = progress.coerceIn(0f, 1f)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val activeColor = MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = trackColor, cornerRadius = radius)
        if (clamped > 0f) {
            val fillWidth = (size.width * clamped).coerceAtLeast(size.height.coerceAtMost(size.width))
            drawRoundRect(
                color = activeColor,
                size = Size(fillWidth, size.height),
                cornerRadius = radius
            )
        }
    }
}
