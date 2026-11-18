package yancey.chelper.ui.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.widget.Divider

internal class SettingsRows {
    val rows = mutableListOf<Pair<String, @Composable () -> Unit>>()

    fun item(key: String, content: @Composable () -> Unit) {
        rows += key to content
    }
}

/** Keep the existing grouped cards, composing individual rows only near the viewport. */
internal fun LazyListScope.settingsSection(
    key: String,
    title: @Composable () -> Unit,
    content: SettingsRows.() -> Unit,
) {
    val rows = SettingsRows().apply(content).rows
    item(key = "$key:title", contentType = "settings-title") { title() }
    items(rows.size, key = { "$key:${rows[it].first}" }, contentType = { "settings-row" }) { index
        ->
        val top = if (index == 0) 20.dp else 0.dp
        val bottom = if (index == rows.lastIndex) 20.dp else 0.dp
        Column(
            Modifier.fillMaxWidth()
                .padding(horizontal = 15.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = top,
                        topEnd = top,
                        bottomStart = bottom,
                        bottomEnd = bottom,
                    )
                )
                .background(CHelperTheme.colors.backgroundComponent)
        ) {
            rows[index].second()
            if (index != rows.lastIndex) Divider()
        }
    }
}
