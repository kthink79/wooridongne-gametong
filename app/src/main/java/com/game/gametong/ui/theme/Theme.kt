package com.game.gametong.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 90년대 문방구·놀이터 색. 크라프트지 바탕에 딱지의 원색. */
object Inks {
    val Kraft = Color(0xFFF1DFBC)
    val KraftDeep = Color(0xFFE3C79A)
    val Paper = Color(0xFFFFF7E6)
    val Wood = Color(0xFF4A3320)
    val WoodLight = Color(0xFF7C5A38)
    val Red = Color(0xFFD1483B)
    val Blue = Color(0xFF2E5EA8)
    val Green = Color(0xFF3E8F58)
    val Gold = Color(0xFFE0A02A)
    val Purple = Color(0xFF7A5AB6)
    val Teal = Color(0xFF2C8B85)
    val Orange = Color(0xFFD8742C)
    val Ink = Color(0xFF3B2B19)
    val SoftInk = Color(0xFF6B5540)
    val White = Color(0xFFFFFDF7)
    val Gray = Color(0xFF8A8378)
}

val CardShape = RoundedCornerShape(18.dp)

private val gametongScheme = lightColorScheme(
    primary = Inks.Red,
    onPrimary = Inks.White,
    secondary = Inks.Blue,
    onSecondary = Inks.White,
    background = Inks.Kraft,
    onBackground = Inks.Ink,
    surface = Inks.Paper,
    onSurface = Inks.Ink,
)

private val gametongTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 30.sp,
        color = Inks.Ink,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 24.sp,
        color = Inks.Ink,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        color = Inks.Ink,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        color = Inks.Ink,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        color = Inks.SoftInk,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = Inks.Ink,
    ),
)

@Composable
fun GametongTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = gametongScheme,
        typography = gametongTypography,
        content = content,
    )
}
