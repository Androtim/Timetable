package com.androtim.timetable.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val ExamRed = Color(0xFFC62828)
val BadgeTp = Color(0xFF1E88E5)
val BadgeTd = Color(0xFF43A047)
val BadgeCm = Color(0xFF8E24AA)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    surface = Color(0xFF121212),
    background = Color(0xFF121212),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1A73E8),
)

@Composable
fun TimetableTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
