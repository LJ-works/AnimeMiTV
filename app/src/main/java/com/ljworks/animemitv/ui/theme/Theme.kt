package com.ljworks.animemitv.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AnimeMiTVTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = PrimaryColor,
            secondary = SecondaryColor,
            tertiary = TertiaryColor,
            background = BackgroundStart,
            surface = SurfaceColor,
            surfaceVariant = SurfaceVariantColor,
            onBackground = TextColor,
            onSurface = TextColor,
            onSurfaceVariant = TextColor,
        ),
        typography = Typography,
        content = content,
    )
}
