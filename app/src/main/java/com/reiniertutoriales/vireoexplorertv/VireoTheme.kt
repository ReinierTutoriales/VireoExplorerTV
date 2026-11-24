package com.reiniertutoriales.vireoexplorertv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.darkColorScheme

internal val VireoCardShape = RoundedCornerShape(20.dp)
internal val VireoPillShape = CircleShape

private val AmoledColors = darkColorScheme(
    primary = Color(0xFF80DCC0),
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF124B3C),
    onPrimaryContainer = Color(0xFFA1F5DA),
    inverseSurface = Color(0xFF80DCC0),
    inverseOnSurface = Color(0xFF00382B),
    background = Color.Black,
    onBackground = Color(0xFFF2F4F3),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFF2F4F3),
    surfaceVariant = Color(0xFF242826),
    onSurfaceVariant = Color(0xFFC3CCC7),
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
