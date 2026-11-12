package yancey.chelper.ui.platform

import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable expect fun platformOverscrollFactory(color: Color): OverscrollFactory?
