package yancey.chelper.ui.feature.library.mcd

import androidx.compose.ui.graphics.Color
import yancey.chelper.domain.library.LineType

val LineType.color: Color
    get() =
        when (this) {
            LineType.META -> Color(0xFF7B1FA2)
            LineType.COMMENT -> Color(0xFF757575)
            LineType.COMMAND -> Color(0xFF2E7D32)
            LineType.CHAIN_SEPARATOR -> Color(0xFF1565C0)
            LineType.STATE_LINE -> Color(0xFFE65100)
            LineType.MARKER -> Color(0xFF9E9E9E)
            LineType.AMBIGUOUS -> Color(0xFFD32F2F)
        }
