package com.alexstream.tv.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.alexstream.tv.R

val SfProRounded = FontFamily(
    Font(R.font.sf_pro_rounded_regular, FontWeight.Normal),
    Font(R.font.sf_pro_rounded_medium, FontWeight.Medium),
    Font(R.font.sf_pro_rounded_semibold, FontWeight.SemiBold),
    Font(R.font.sf_pro_rounded_bold, FontWeight.Bold),
)

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = SfProRounded,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
        color = TextColor
    ),
    titleLarge = TextStyle(
        fontFamily = SfProRounded,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
        color = TextColor
    ),
    labelSmall = TextStyle(
        fontFamily = SfProRounded,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        color = BreadcrumbColor
    )
)
