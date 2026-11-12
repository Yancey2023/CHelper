package yancey.chelper.android.ui.ime

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.feature.conversion.ime.ImeGuideContent
import yancey.chelper.ui.feature.conversion.ime.Old2NewIMEScreen

@Preview
@Composable
fun ImeGuideLightPreview() {
    CHelperTheme(CHelperTheme.Theme.Light, null) { ImeGuideContent({}, {}) }
}

@Preview
@Composable
fun ImeGuideDarkPreview() {
    CHelperTheme(CHelperTheme.Theme.Dark, null) { ImeGuideContent({}, {}) }
}

@Preview
@Composable
fun ImeUndoLightPreview() {
    CHelperTheme(CHelperTheme.Theme.Light, null) { Old2NewIMEScreen {} }
}

@Preview
@Composable
fun ImeUndoDarkPreview() {
    CHelperTheme(CHelperTheme.Theme.Dark, null) { Old2NewIMEScreen {} }
}
