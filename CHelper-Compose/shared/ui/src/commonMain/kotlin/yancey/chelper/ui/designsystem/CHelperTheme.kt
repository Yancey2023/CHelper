/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Yancey
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package yancey.chelper.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.TweenSpec
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

private val LightColorPalette =
    CHelperColors(
        mainColor = Color(0xFFEA5947),
        mainColorSecondary = Color(0x6AEA5947),
        background = Color(0xFFF3F3F3),
        backgroundComponent = Color(0xAAFFFFFF),
        backgroundComponentNoTranslate = Color(0xFFFDFDFD),
        textMain = Color(0xFF000000),
        textBond = Color(0xFF000000),
        textSecondary = Color(0xFF444444),
        textHint = Color(0xFF444444),
        textErrorReason = Color(0xFFCC4444),
        underlineErrorReason = Color(0xFFCC4444),
        line = Color(0xFFF3F3F3),
        iconMain = Color(0xFF444444),
        scrollBar = Color(0x99444444),
    )

private val DarkColorPalette =
    CHelperColors(
        mainColor = Color(0xFFEA5947),
        mainColorSecondary = Color(0x6AEA5947),
        background = Color(0xFF1E1F22),
        backgroundComponent = Color(0xAA2b2d30),
        backgroundComponentNoTranslate = Color(0xFF282A2D),
        textMain = Color(0xFFDDDDDD),
        textBond = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFAAAAAA),
        textHint = Color(0xFFAAAAAA),
        textErrorReason = Color(0xFFCC4444),
        underlineErrorReason = Color(0xFFCC4444),
        line = Color(0xFF222222),
        iconMain = Color(0xFFAAAAAA),
        scrollBar = Color(0x99969696),
    )

private val LocalTheme = compositionLocalOf { CHelperTheme.Theme.Light }

private val LocalCHelperColors = compositionLocalOf { LightColorPalette }

private val LocalBackground = compositionLocalOf<ImageBitmap?> { null }

/** Keep the project's buttons and clickable icons free of default press/hover overlays. */
private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): Modifier.Node = NoIndicationNode()

    override fun equals(other: Any?) = other === this

    override fun hashCode() = -1

    private class NoIndicationNode : Modifier.Node()
}

object CHelperTheme {
    val theme: Theme
        @Composable get() = LocalTheme.current

    val colors: CHelperColors
        @Composable get() = LocalCHelperColors.current

    val backgroundBitmap: ImageBitmap?
        @Composable get() = LocalBackground.current

    /**
     * 将设置中的主题 id 映射为 Compose 主题
     *
     * @param themeId 设置中保存的主题 id
     * @param isSystemDarkMode 系统当前是否处于夜间模式，跟随系统时使用
     */
    fun themeOf(themeId: String, isSystemDarkMode: Boolean): Theme =
        when (themeId) {
            "MODE_NIGHT_NO" -> Theme.Light
            "MODE_NIGHT_YES" -> Theme.Dark
            else -> if (isSystemDarkMode) Theme.Dark else Theme.Light
        }

    enum class Theme {
        Light,
        Dark,
    }
}

@Immutable
data class CHelperColors(
    val mainColor: Color,
    val mainColorSecondary: Color,
    val background: Color,
    val backgroundComponent: Color,
    val backgroundComponentNoTranslate: Color,
    val textMain: Color,
    val textBond: Color,
    val textSecondary: Color,
    val textHint: Color,
    val textErrorReason: Color,
    val underlineErrorReason: Color,
    val line: Color,
    val iconMain: Color,
    val scrollBar: Color,
)

@Composable
fun CHelperTheme(
    theme: CHelperTheme.Theme,
    backgroundBitmap: ImageBitmap?,
    screenAlphaOverride: Float = 1.0f,
    content: @Composable () -> Unit,
) {
    val targetColor =
        when (theme) {
            CHelperTheme.Theme.Light -> LightColorPalette
            CHelperTheme.Theme.Dark -> DarkColorPalette
        }

    val animationSpec = TweenSpec<Color>(600)
    val mainColor = animateColorAsState(targetColor.mainColor, animationSpec)
    val mainColorSecondary = animateColorAsState(targetColor.mainColorSecondary, animationSpec)
    val background =
        animateColorAsState(
            targetColor.background.copy(alpha = targetColor.background.alpha * screenAlphaOverride),
            animationSpec,
        )
    val backgroundComponent =
        animateColorAsState(
            targetColor.backgroundComponent.copy(
                alpha = targetColor.backgroundComponent.alpha * screenAlphaOverride
            ),
            animationSpec,
        )
    val backgroundComponentNoTranslate =
        animateColorAsState(
            targetColor.backgroundComponentNoTranslate.copy(
                alpha = targetColor.backgroundComponentNoTranslate.alpha * screenAlphaOverride
            ),
            animationSpec,
        )
    val textMain = animateColorAsState(targetColor.textMain, animationSpec)
    val textBond = animateColorAsState(targetColor.textBond, animationSpec)
    val textSecondary = animateColorAsState(targetColor.textSecondary, animationSpec)
    val textHint = animateColorAsState(targetColor.textHint, animationSpec)
    val textErrorReason = animateColorAsState(targetColor.textErrorReason, animationSpec)
    val underlineErrorReason = animateColorAsState(targetColor.underlineErrorReason, animationSpec)
    val line = animateColorAsState(targetColor.line, animationSpec)
    val iconMain = animateColorAsState(targetColor.iconMain, animationSpec)
    val scrollBar = animateColorAsState(targetColor.scrollBar, animationSpec)

    val colors =
        CHelperColors(
            mainColor = mainColor.value,
            mainColorSecondary = mainColorSecondary.value,
            background = background.value,
            backgroundComponent = backgroundComponent.value,
            backgroundComponentNoTranslate = backgroundComponentNoTranslate.value,
            textMain = textMain.value,
            textBond = textBond.value,
            textSecondary = textSecondary.value,
            textHint = textHint.value,
            textErrorReason = textErrorReason.value,
            underlineErrorReason = underlineErrorReason.value,
            line = line.value,
            iconMain = iconMain.value,
            scrollBar = scrollBar.value,
        )
    val textSelectionColors =
        TextSelectionColors(
            handleColor = mainColor.value,
            backgroundColor = mainColorSecondary.value,
        )
    CompositionLocalProvider(
        LocalTheme provides theme,
        LocalCHelperColors provides colors,
        LocalBackground provides backgroundBitmap,
        LocalTextSelectionColors provides textSelectionColors,
        LocalIndication provides NoIndication,
    ) {
        content()
    }
}
