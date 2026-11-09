package com.trae.watch.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 主题：只注入官方 MaterialTheme 的颜色角色，不自创令牌、不覆盖字阶与形状。
 *
 * 依据：
 *  - 颜色角色 https://m3.material.io/styles/color/roles
 *  - 语义分工 https://m3.material.io/styles/color/system/overview
 *
 * 品牌色为绿色 tonal palette（primary 系列），其余角色按 M3 深色主题
 * 的层次关系重新对齐：surface 是主背景，surfaceContainer* 由低到高逐级变亮，
 * 用于表达区域层级，而不是用阴影。
 *
 * 手表省电要求背景接近纯黑，所以 surfaceContainerLowest 取 #000000，
 * surface 取 #0E130F，形成肉眼可辨但功耗极低的层次。
 */
private val WatchColorScheme = darkColorScheme(
    // 主色：唯一的高强调来源，只用于主操作与选中态
    primary = Color(0xFF8BF5A9),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF00522A),
    onPrimaryContainer = Color(0xFF8BF5A9),
    inversePrimary = Color(0xFF1B6C3A),

    // 次色：支撑性强调，用于次级容器
    secondary = Color(0xFFB8CCBB),
    onSecondary = Color(0xFF233428),
    secondaryContainer = Color(0xFF3A4B3E),
    onSecondaryContainer = Color(0xFFD4E8D6),

    // 第三色：少量点缀（如云端会话标记）
    tertiary = Color(0xFFFFDEA6),
    onTertiary = Color(0xFF402D00),
    tertiaryContainer = Color(0xFF5C4200),
    onTertiaryContainer = Color(0xFFFFDEA6),

    // 错误：仅用于真实错误与破坏性操作
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    // 背景层
    background = Color(0xFF0E130F),
    onBackground = Color(0xFFE0E4DF),
    surface = Color(0xFF0E130F),
    onSurface = Color(0xFFE0E4DF),
    surfaceVariant = Color(0xFF1E251F),
    onSurfaceVariant = Color(0xFFBFC9C1),

    // surface 容器层次：低到高逐级变亮
    surfaceDim = Color(0xFF0A0F0B),
    surfaceBright = Color(0xFF343A35),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF161B17),
    surfaceContainer = Color(0xFF1A201B),
    surfaceContainerHigh = Color(0xFF252B26),
    surfaceContainerHighest = Color(0xFF303631),

    // 反色层：用于 Snackbar 等浮层
    inverseSurface = Color(0xFFE0E4DF),
    inverseOnSurface = Color(0xFF2B322D),

    outline = Color(0xFF89938C),
    outlineVariant = Color(0xFF3F4943),
    scrim = Color(0xFF000000),
)

@Composable
fun TraeWatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WatchColorScheme,
        // 字阶与形状全部使用 M3 官方默认值，不覆盖
        content = content,
    )
}
