package br.com.medidor.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF116678),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4F4F4),
    secondary = Color(0xFF496775),
    background = Color(0xFFF6F8FA),
    surface = Color.White
)
private val Dark = darkColorScheme(
    primary = Color(0xFF80D6E9),
    onPrimary = Color(0xFF003740),
    primaryContainer = Color(0xFF164F5C),
    secondary = Color(0xFFAFC7D1),
    background = Color(0xFF10191D),
    surface = Color(0xFF182328)
)

@Composable
fun MedidorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
