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

package yancey.chelper.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.TweenSpec
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.rememberPlatformOverscrollFactory
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

private val LightColorPalette = CHelperColors(
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
    overscrollGlowColor = Color(0xFFFFFFFF),
//    syntaxHighlightBoolean = Color(0xFF4FAD63),
//    syntaxHighlightFloat = Color(0xFF4FAD63),
//    syntaxHighlightInteger = Color(0xFF4FAD63),
//    syntaxHighlightSymbol = Color(0xFF4FAD63),
//    syntaxHighlightId = Color(0xFFD4AC0D),
//    syntaxHighlightTargetSelector = Color(0xFF07C160),
//    syntaxHighlightCommand = Color(0xFF9F20A7),
//    syntaxHighlightBrackets1 = Color(0xFF836C0A),
//    syntaxHighlightBrackets2 = Color(0xFF9F20A7),
//    syntaxHighlightBrackets3 = Color(0xFF4571E1),
//    syntaxHighlightString = Color(0xFFD95A53),
//    syntaxHighlightNull = Color(0xFF0FA0C8),
//    syntaxHighlightRange = Color(0xFF0FA0C8),
//    syntaxHighlightLiteral = Color(0xFF0FA0C8),
)

private val DarkColorPalette = CHelperColors(
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
    overscrollGlowColor = Color(0xFF000000),
//    syntaxHighlightBoolean = Color(0xFFB5CEA8),
//    syntaxHighlightFloat = Color(0xFFB5CEA8),
//    syntaxHighlightInteger = Color(0xFFB5CEA8),
//    syntaxHighlightSymbol = Color(0xFFB5CEA8),
//    syntaxHighlightId = Color(0xFFDCDCAA),
//    syntaxHighlightTargetSelector = Color(0xFF4EC9B0),
//    syntaxHighlightCommand = Color(0xFFC586C0),
//    syntaxHighlightBrackets1 = Color(0xFFFFD700),
//    syntaxHighlightBrackets2 = Color(0xFFC586C0),
//    syntaxHighlightBrackets3 = Color(0xFF179FFF),
//    syntaxHighlightString = Color(0xFFCE9178),
//    syntaxHighlightNull = Color(0xFF9CDCFE),
//    syntaxHighlightRange = Color(0xFF9CDCFE),
//    syntaxHighlightLiteral = Color(0xFF9CDCFE),
)

private val LocalTheme = compositionLocalOf {
    CHelperTheme.Theme.Light
}

private val LocalCHelperColors = compositionLocalOf {
    LightColorPalette
}

private val LocalBackground = compositionLocalOf<ImageBitmap?> {
    null
}

object CHelperTheme {
    val theme: Theme
        @Composable
        get() = LocalTheme.current
    val colors: CHelperColors
        @Composable
        get() = LocalCHelperColors.current
    val backgroundBitmap: ImageBitmap?
        @Composable
        get() = LocalBackground.current

    enum class Theme {
        Light, Dark
    }
}

class CHelperColors(
    mainColor: Color,
    mainColorSecondary: Color,
    background: Color,
    backgroundComponent: Color,
    backgroundComponentNoTranslate: Color,
    textMain: Color,
    textBond: Color,
    textSecondary: Color,
    textHint: Color,
    textErrorReason: Color,
    underlineErrorReason: Color,
    line: Color,
    iconMain: Color,
    scrollBar: Color,
    overscrollGlowColor: Color,
//    syntaxHighlightBoolean: Color,
//    syntaxHighlightFloat: Color,
//    syntaxHighlightInteger: Color,
//    syntaxHighlightSymbol: Color,
//    syntaxHighlightId: Color,
//    syntaxHighlightTargetSelector: Color,
//    syntaxHighlightCommand: Color,
//    syntaxHighlightBrackets1: Color,
//    syntaxHighlightBrackets2: Color,
//    syntaxHighlightBrackets3: Color,
//    syntaxHighlightString: Color,
//    syntaxHighlightNull: Color,
//    syntaxHighlightRange: Color,
//    syntaxHighlightLiteral: Color,
) {
    var mainColor: Color by mutableStateOf(mainColor)
        private set
    var mainColorSecondary: Color by mutableStateOf(mainColorSecondary)
        private set
    var background: Color by mutableStateOf(background)
        private set
    var backgroundComponent: Color by mutableStateOf(backgroundComponent)
        private set
    var backgroundComponentNoTranslate: Color by mutableStateOf(backgroundComponentNoTranslate)
        private set
    var textMain: Color by mutableStateOf(textMain)
        private set
    var textBond: Color by mutableStateOf(textBond)
        private set
    var textSecondary: Color by mutableStateOf(textSecondary)
        private set
    var textHint: Color by mutableStateOf(textHint)
        private set
    var textErrorReason: Color by mutableStateOf(textErrorReason)
        private set
    var underlineErrorReason: Color by mutableStateOf(underlineErrorReason)
        private set
    var line: Color by mutableStateOf(line)
        private set
    var iconMain: Color by mutableStateOf(iconMain)
        private set
    var scrollBar: Color by mutableStateOf(scrollBar)
        private set
    var overscrollGlowColor: Color by mutableStateOf(overscrollGlowColor)
        private set
//    var syntaxHighlightBoolean: Color by mutableStateOf(syntaxHighlightBoolean)
//        private set
//    var syntaxHighlightFloat: Color by mutableStateOf(syntaxHighlightFloat)
//        private set
//    var syntaxHighlightInteger: Color by mutableStateOf(syntaxHighlightInteger)
//        private set
//    var syntaxHighlightSymbol: Color by mutableStateOf(syntaxHighlightSymbol)
//        private set
//    var syntaxHighlightId: Color by mutableStateOf(syntaxHighlightId)
//        private set
//    var syntaxHighlightTargetSelector: Color by mutableStateOf(syntaxHighlightTargetSelector)
//        private set
//    var syntaxHighlightCommand: Color by mutableStateOf(syntaxHighlightCommand)
//        private set
//    var syntaxHighlightBrackets1: Color by mutableStateOf(syntaxHighlightBrackets1)
//        private set
//    var syntaxHighlightBrackets2: Color by mutableStateOf(syntaxHighlightBrackets2)
//        private set
//    var syntaxHighlightBrackets3: Color by mutableStateOf(syntaxHighlightBrackets3)
//        private set
//    var syntaxHighlightString: Color by mutableStateOf(syntaxHighlightString)
//        private set
//    var syntaxHighlightNull: Color by mutableStateOf(syntaxHighlightNull)
//        private set
//    var syntaxHighlightRange: Color by mutableStateOf(syntaxHighlightRange)
//        private set
//    var syntaxHighlightLiteral: Color by mutableStateOf(syntaxHighlightLiteral)
//        private set
}

private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource) =
        NoIndicationInstance()

    override fun equals(other: Any?) = other === this

    override fun hashCode() = -1

    class NoIndicationInstance : Modifier.Node()
}

@Composable
fun CHelperTheme(
    theme: CHelperTheme.Theme,
    backgroundBitmap: ImageBitmap?,
    content: @Composable () -> Unit
) {
    val targetColor = when (theme) {
        CHelperTheme.Theme.Light -> LightColorPalette
        CHelperTheme.Theme.Dark -> DarkColorPalette
    }

    val animationSpec = TweenSpec<Color>(600)
    val mainColor = animateColorAsState(targetColor.mainColor, animationSpec)
    val mainColorSecondary =
        animateColorAsState(targetColor.mainColorSecondary, animationSpec)
    val background = animateColorAsState(targetColor.background, animationSpec)
    val backgroundComponent = animateColorAsState(targetColor.backgroundComponent, animationSpec)
    val backgroundComponentNoTranslate =
        animateColorAsState(targetColor.backgroundComponentNoTranslate, animationSpec)
    val textMain = animateColorAsState(targetColor.textMain, animationSpec)
    val textBond = animateColorAsState(targetColor.textBond, animationSpec)
    val textSecondary = animateColorAsState(targetColor.textSecondary, animationSpec)
    val textHint = animateColorAsState(targetColor.textHint, animationSpec)
    val textErrorReason = animateColorAsState(targetColor.textErrorReason, animationSpec)
    val underlineErrorReason = animateColorAsState(targetColor.underlineErrorReason, animationSpec)
    val line = animateColorAsState(targetColor.line, animationSpec)
    val iconMain = animateColorAsState(targetColor.iconMain, animationSpec)
    val scrollBar = animateColorAsState(targetColor.scrollBar, animationSpec)
    val overscrollGlowColor = animateColorAsState(targetColor.overscrollGlowColor, animationSpec)
//    val syntaxHighlightBoolean =
//        animateColorAsState(targetColor.syntaxHighlightBoolean, animationSpec)
//    val syntaxHighlightFloat = animateColorAsState(targetColor.syntaxHighlightFloat, animationSpec)
//    val syntaxHighlightInteger =
//        animateColorAsState(targetColor.syntaxHighlightInteger, animationSpec)
//    val syntaxHighlightSymbol =
//        animateColorAsState(targetColor.syntaxHighlightSymbol, animationSpec)
//    val syntaxHighlightId = animateColorAsState(targetColor.syntaxHighlightId, animationSpec)
//    val syntaxHighlightTargetSelector =
//        animateColorAsState(targetColor.syntaxHighlightTargetSelector, animationSpec)
//    val syntaxHighlightCommand =
//        animateColorAsState(targetColor.syntaxHighlightCommand, animationSpec)
//    val syntaxHighlightBrackets1 =
//        animateColorAsState(targetColor.syntaxHighlightBrackets1, animationSpec)
//    val syntaxHighlightBrackets2 =
//        animateColorAsState(targetColor.syntaxHighlightBrackets2, animationSpec)
//    val syntaxHighlightBrackets3 =
//        animateColorAsState(targetColor.syntaxHighlightBrackets3, animationSpec)
//    val syntaxHighlightString =
//        animateColorAsState(targetColor.syntaxHighlightString, animationSpec)
//    val syntaxHighlightNull = animateColorAsState(targetColor.syntaxHighlightNull, animationSpec)
//    val syntaxHighlightRange = animateColorAsState(targetColor.syntaxHighlightRange, animationSpec)
//    val syntaxHighlightLiteral =
//        animateColorAsState(targetColor.syntaxHighlightLiteral, animationSpec)

    val colors = CHelperColors(
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
        overscrollGlowColor = overscrollGlowColor.value,
//        syntaxHighlightBoolean = syntaxHighlightBoolean.value,
//        syntaxHighlightFloat = syntaxHighlightFloat.value,
//        syntaxHighlightInteger = syntaxHighlightInteger.value,
//        syntaxHighlightSymbol = syntaxHighlightSymbol.value,
//        syntaxHighlightId = syntaxHighlightId.value,
//        syntaxHighlightTargetSelector = syntaxHighlightTargetSelector.value,
//        syntaxHighlightCommand = syntaxHighlightCommand.value,
//        syntaxHighlightBrackets1 = syntaxHighlightBrackets1.value,
//        syntaxHighlightBrackets2 = syntaxHighlightBrackets2.value,
//        syntaxHighlightBrackets3 = syntaxHighlightBrackets3.value,
//        syntaxHighlightString = syntaxHighlightString.value,
//        syntaxHighlightNull = syntaxHighlightNull.value,
//        syntaxHighlightRange = syntaxHighlightRange.value,
//        syntaxHighlightLiteral = syntaxHighlightLiteral.value,
    )
    val textSelectionColors = TextSelectionColors(
        handleColor = mainColor.value,
        backgroundColor = mainColorSecondary.value
    )
    val overscrollFactory = rememberPlatformOverscrollFactory(overscrollGlowColor.value)
    CompositionLocalProvider(
        LocalTheme provides theme,
        LocalCHelperColors provides colors,
        LocalBackground provides backgroundBitmap,
        LocalTextSelectionColors provides textSelectionColors,
        LocalIndication provides NoIndication,
        LocalOverscrollFactory provides overscrollFactory,
    ) {
        content()
    }
}




