package com.trae.watch.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme

/**
 * M3 Expressive 令牌层。
 *
 * 手表端不使用圆形边缘贴合组件，只复用 M3E 的令牌语言：
 *  - 形状：更夸张的圆角与胶囊，对应 M3E 的 shape morph 意图
 *  - 字体：M3E 的手表字号阶梯，最小可读 14sp
 *  - 颜色：纯黑底 + 单强调色，省电且在小屏上对比强
 *  - 动效：只用弹簧曲线，且只用于转场与按压反馈
 */
@Immutable
data class TraeColors(
    val bg: Color = Color(0xFF000000),
    val surface: Color = Color(0xFF14181A),
    val surfaceHigh: Color = Color(0xFF1F2629),
    val primary: Color = Color(0xFF3DDC84),
    val onPrimary: Color = Color(0xFF062414),
    val danger: Color = Color(0xFFFF5C5C),
    val warning: Color = Color(0xFFFFC24B),
    val textPrimary: Color = Color(0xFFF2F5F4),
    val textSecondary: Color = Color(0xFF9AA5A0),
    val textMuted: Color = Color(0xFF5E6864),
    val divider: Color = Color(0xFF2A3235),
)

@Immutable
data class TraeDimens(
    /** 屏幕基准宽度，410x502 为设计稿 */
    val screenW: Dp = 410.dp,
    val screenH: Dp = 502.dp,
    val gapXs: Dp = 4.dp,
    val gapS: Dp = 8.dp,
    val gapM: Dp = 12.dp,
    val gapL: Dp = 16.dp,
    val padScreen: Dp = 14.dp,
    val rowMinHeight: Dp = 56.dp,
    val touchMin: Dp = 48.dp,
    val cardRadius: Dp = 22.dp,
    val buttonRadius: Dp = 26.dp,
)

@Immutable
data class TraeType(
    val title: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 24.sp,
    ),
    val body: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 20.sp,
    ),
    val bodySmall: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 17.sp,
    ),
    val label: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 18.sp,
    ),
    val caption: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 11.sp, lineHeight = 14.sp,
    ),
)

val LocalTraeColors = staticCompositionLocalOf { TraeColors() }
val LocalTraeDimens = staticCompositionLocalOf { TraeDimens() }
val LocalTraeType = staticCompositionLocalOf { TraeType() }

object TraeTheme {
    val colors: TraeColors
        @Composable get() = LocalTraeColors.current
    val dimens: TraeDimens
        @Composable get() = LocalTraeDimens.current
    val type: TraeType
        @Composable get() = LocalTraeType.current
}

/** M3E 形状体系：胶囊 + 大圆角，方屏上用全圆角替代圆形边缘 */
val TraeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

private val traeTypography = Typography(
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 14.sp),
)

private val traeColorScheme = darkColorScheme(
    primary = Color(0xFF3DDC84),
    onPrimary = Color(0xFF062414),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F5F4),
    surface = Color(0xFF14181A),
    onSurface = Color(0xFFF2F5F4),
    surfaceVariant = Color(0xFF1F2629),
    onSurfaceVariant = Color(0xFF9AA5A0),
    error = Color(0xFFFF5C5C),
    outline = Color(0xFF2A3235),
)

@Composable
fun TraeWatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = traeColorScheme,
        shapes = TraeShapes,
        typography = traeTypography,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalTraeColors provides TraeColors(),
            LocalTraeDimens provides TraeDimens(),
            LocalTraeType provides TraeType(),
        ) {
            content()
        }
    }
}
