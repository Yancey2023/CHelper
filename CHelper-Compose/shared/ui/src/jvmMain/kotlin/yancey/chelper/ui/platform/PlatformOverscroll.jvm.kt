package yancey.chelper.ui.platform

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
actual fun platformOverscrollFactory(color: Color): OverscrollFactory? =
    LocalOverscrollFactory.current
