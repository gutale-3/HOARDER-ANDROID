package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun NovelHoarderTheme(
  appTheme: AppTheme = AppTheme.IMMERSIVE_UI,
  content: @Composable () -> Unit,
) {
  val colorScheme = ThemeConfig.getColorScheme(appTheme)

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
