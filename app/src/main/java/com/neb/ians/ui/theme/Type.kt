package com.neb.ians.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.neb.ians.R

val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

val NEBiansTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.W700,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.W700,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.4).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.W700,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.W700,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.W600,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.W600,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.W600,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.W600,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.W600,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.W400,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.W400,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.W400,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.25.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.W600,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.W600,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.W600,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp,
    ),
)