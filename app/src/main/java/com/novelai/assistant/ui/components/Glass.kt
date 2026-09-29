package com.novelai.assistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import androidx.compose.material3.MaterialTheme

/**
 * 毛玻璃封装：内容区标记 glassSource，悬浮面板标记 glassEffect 即可产生背景实时模糊。
 */
class GlassStateHolder {
    val state: HazeState = HazeState()
}

@Composable
fun rememberGlassState(): HazeState = remember { HazeState() }

fun Modifier.glassSource(state: HazeState): Modifier = this.hazeSource(state)

/** 悬浮玻璃面板（底部导航 / 顶栏 / 抽屉通用） */
@Composable
fun Modifier.glassEffect(
    state: HazeState,
    tint: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
    blurRadius: Dp = 24.dp,
    shape: Shape = RoundedCornerShape(0.dp)
): Modifier = this
    .clip(shape)
    .hazeEffect(state = state) {
        style = HazeStyle(
            backgroundColor = Color.Transparent,
            tints = listOf(HazeTint(tint)),
            blurRadius = blurRadius
        )
    }
    .border(
        width = 0.5.dp,
        brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.06f))
        ),
        shape = shape
    )

/** 无 Haze 上下文时的静态玻璃卡（透明度 + 高光描边） */
fun Modifier.staticGlass(tint: Color, shape: Shape): Modifier = this
    .clip(shape)
    .background(tint, shape)
    .border(
        width = 0.5.dp,
        brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.05f))
        ),
        shape = shape
    )
