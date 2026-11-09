package com.trae.watch.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Material 3 Expressive 令牌层（手表方屏适配）。
 *
 * 与普通 M3 的差别，这里逐条落地：
 *  1. 音调色阶：完整 5 档 surface container + primary/secondary/tertiary container
 *  2. 形状变形：非对称 squircle（M3E 的 shape morph 语言），而不是统一圆角
 *  3. 表达性字阶：display 收紧字距 + 大字重，label 放宽字距
 *  4. 弹簧动效：M3E 标志性的低阻尼弹簧，只用于按压回弹与转场（无循环动画）
 *  5. 按钮组：相连的分段控件，两端胶囊、内侧小圆角
 */
@Immutable
data class M3eColors(
    // 主色族（音调 40/90/30/10）
    val primary: Color = Color(0xFF6DD58C),
    val onPrimary: Color = Color(0xFF00391A),
    val primaryContainer: Color = Color(0xFF00522A),
    val onPrimaryContainer: Color = Color(0xFF8BF5A9),

    // 次色族
    val secondary: Color = Color(0xFFB8CCBB),
    val onSecondary: Color = Color(0xFF233428),
    val secondaryContainer: Color = Color(0xFF3A4B3E),
    val onSecondaryContainer: Color = Color(0xFFD4E8D6),

    // 三色族（强调/警示，M3E 常用 tertiary 做跳色）
    val tertiary: Color = Color(0xFFF0BD6B),
    val onTertiary: Color = Color(0xFF402D00),
    val tertiaryContainer: Color = Color(0xFF5C4200),
    val onTertiaryContainer: Color = Color(0xFFFFDEA6),

    // 错误
    val error: Color = Color(0xFFFFB4AB),
    val onError: Color = Color(0xFF690005),
    val errorContainer: Color = Color(0xFF93000A),
    val onErrorContainer: Color = Color(0xFFFFDAD6),

    // 5 档 surface container（M3E 的核心层次语言）
    val surfaceContainerLowest: Color = Color(0xFF0A0F0C),
    val surfaceContainerLow: Color = Color(0xFF141A16),
    val surfaceContainer: Color = Color(0xFF181F1B),
    val surfaceContainerHigh: Color = Color(0xFF232B26),
    val surfaceContainerHighest: Color = Color(0xFF2E3731),

    val onSurface: Color = Color(0xFFE0E4DF),
    val onSurfaceVariant: Color = Color(0xFFBFC9C1),
    val outline: Color = Color(0xFF89938C),
    val outlineVariant: Color = Color(0xFF3F4943),
    val inverseSurface: Color = Color(0xFFE0E4DF),
    val inverseOnSurface: Color = Color(0xFF2D322E),
) {
    // 旧字段名兼容别名，避免批量迁移遗漏
    val surface: Color get() = surfaceContainer
    val surfaceHigh: Color get() = surfaceContainerHigh
    val bg: Color get() = surfaceContainerLowest
    val textPrimary: Color get() = onSurface
    val textSecondary: Color get() = onSurfaceVariant
    val textMuted: Color get() = outline
    val divider: Color get() = outlineVariant
    val danger: Color get() = error
    val warning: Color get() = tertiary
}

@Immutable
data class M3eDimens(
    val screenW: Dp = 410.dp,
    val screenH: Dp = 502.dp,
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val pad: Dp = 14.dp,
    val touch: Dp = 48.dp,
    /** M3E squircle 主半径 */
    val squircle: Dp = 28.dp,
    /** 按钮组内侧圆角 */
    val groupInner: Dp = 6.dp,
    val pressScale: Float = 0.94f,
)

/** M3E 表达性字阶：display 收紧字距，label 放宽 */
@Immutable
data class M3eType(
    val display: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 34.sp, lineHeight = 36.sp, letterSpacing = (-1.0).sp,
    ),
    val headline: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 24.sp, lineHeight = 26.sp, letterSpacing = (-0.5).sp,
    ),
    val title: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 22.sp, letterSpacing = (-0.2).sp,
    ),
    val body: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = 0.sp,
    ),
    val bodySmall: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp,
    ),
    val label: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 19.sp, letterSpacing = 0.2.sp,
    ),
    val labelSmall: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp,
    ),
) {
    // 旧字阶别名
    val caption: TextStyle get() = labelSmall
    val bodyLarge: TextStyle get() = body
}

val LocalM3eColors = staticCompositionLocalOf { M3eColors() }
val LocalM3eDimens = staticCompositionLocalOf { M3eDimens() }
val LocalM3eType = staticCompositionLocalOf { M3eType() }

object M3e {
    val color: M3eColors @Composable get() = LocalM3eColors.current
    val dimen: M3eDimens @Composable get() = LocalM3eDimens.current
    val type: M3eType @Composable get() = LocalM3eType.current
}

/** M3E squircle：四角不等的大圆角，体现 shape morph 语言 */
val Squircle = RoundedCornerShape(28.dp)

/** 容器形变：顶部大圆角、底部收窄，用于消息块与卡片头部 */
val AsymContainer = RoundedCornerShape(
    topStart = 28.dp, topEnd = 28.dp,
    bottomStart = 28.dp, bottomEnd = 10.dp,
)

val M3eShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** M3E 弹簧：低阻尼、高刚度，按压有轻微回弹 */
fun <T> m3eSpring() = spring<T>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

private val m3eTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 36.sp, letterSpacing = (-1.0).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 26.sp, letterSpacing = (-0.5).sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 22.sp, letterSpacing = (-0.2).sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 19.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp),
)

private val m3eColorScheme = darkColorScheme(
    primary = Color(0xFF6DD58C),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF00522A),
    onPrimaryContainer = Color(0xFF8BF5A9),
    secondary = Color(0xFFB8CCBB),
    onSecondary = Color(0xFF233428),
    secondaryContainer = Color(0xFF3A4B3E),
    onSecondaryContainer = Color(0xFFD4E8D6),
    tertiary = Color(0xFFF0BD6B),
    onTertiary = Color(0xFF402D00),
    tertiaryContainer = Color(0xFF5C4200),
    onTertiaryContainer = Color(0xFFFFDEA6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0A0F0C),
    onBackground = Color(0xFFE0E4DF),
    surface = Color(0xFF181F1B),
    onSurface = Color(0xFFE0E4DF),
    surfaceVariant = Color(0xFF232B26),
    onSurfaceVariant = Color(0xFFBFC9C1),
    outline = Color(0xFF89938C),
    outlineVariant = Color(0xFF3F4943),
)

@Composable
fun TraeWatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = m3eColorScheme,
        shapes = M3eShapes,
        typography = m3eTypography,
    ) {
        CompositionLocalProvider(
            LocalM3eColors provides M3eColors(),
            LocalM3eDimens provides M3eDimens(),
            LocalM3eType provides M3eType(),
        ) {
            content()
        }
    }
}

// 兼容旧调用点
val TraeTheme = M3e
typealias TraeColors = M3eColors
typealias TraeDimens = M3eDimens
typealias TraeType = M3eType
