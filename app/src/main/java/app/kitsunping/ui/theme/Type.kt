package app.kitsunping.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val TitleFont = FontFamily.SansSerif
private val TechnicalFont = FontFamily.Monospace

val Typography = Typography(
	headlineLarge = TextStyle(
		fontFamily = TitleFont,
		fontWeight = FontWeight.SemiBold,
		fontSize = 30.sp,
		lineHeight = 36.sp,
		letterSpacing = (-0.4).sp
	),
	headlineMedium = TextStyle(
		fontFamily = TitleFont,
		fontWeight = FontWeight.SemiBold,
		fontSize = 26.sp,
		lineHeight = 32.sp,
		letterSpacing = (-0.3).sp
	),
	titleLarge = TextStyle(
		fontFamily = TitleFont,
		fontWeight = FontWeight.SemiBold,
		fontSize = 22.sp,
		lineHeight = 28.sp,
		letterSpacing = (-0.2).sp
	),
	titleMedium = TextStyle(
		fontFamily = TitleFont,
		fontWeight = FontWeight.Medium,
		fontSize = 18.sp,
		lineHeight = 24.sp
	),
	titleSmall = TextStyle(
		fontFamily = TitleFont,
		fontWeight = FontWeight.Medium,
		fontSize = 16.sp,
		lineHeight = 22.sp,
		letterSpacing = 0.1.sp
	),
	bodyLarge = TextStyle(
		fontFamily = TitleFont,
		fontWeight = FontWeight.Normal,
		fontSize = 16.sp,
		lineHeight = 24.sp,
		letterSpacing = 0.15.sp
	),
	bodyMedium = TextStyle(
		fontFamily = TitleFont,
		fontWeight = FontWeight.Normal,
		fontSize = 14.sp,
		lineHeight = 21.sp,
		letterSpacing = 0.1.sp
	),
	bodySmall = TextStyle(
		fontFamily = TechnicalFont,
		fontWeight = FontWeight.Normal,
		fontSize = 12.sp,
		lineHeight = 18.sp,
		letterSpacing = 0.2.sp
	),
	labelLarge = TextStyle(
		fontFamily = TitleFont,
		fontWeight = FontWeight.Medium,
		fontSize = 14.sp,
		lineHeight = 18.sp,
		letterSpacing = 0.15.sp
	),
	labelMedium = TextStyle(
		fontFamily = TitleFont,
		fontWeight = FontWeight.Medium,
		fontSize = 12.sp,
		lineHeight = 16.sp,
		letterSpacing = 0.35.sp
	),
	labelSmall = TextStyle(
		fontFamily = TechnicalFont,
		fontWeight = FontWeight.Medium,
		fontSize = 11.sp,
		lineHeight = 16.sp,
		letterSpacing = 0.35.sp
	)
)
