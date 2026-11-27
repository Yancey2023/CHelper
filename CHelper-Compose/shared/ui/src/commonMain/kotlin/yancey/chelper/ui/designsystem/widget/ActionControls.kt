package yancey.chelper.ui.designsystem.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import yancey.chelper.resources.Res
import yancey.chelper.resources.square
import yancey.chelper.resources.square_check
import yancey.chelper.ui.designsystem.CHelperTheme

@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun SelectionItem(
    headlineContent: @Composable () -> Unit,
    leadingContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leadingContent()
        Box(Modifier.weight(1f)) { headlineContent() }
    }
}

@Composable
fun Checkbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val color = if (checked) CHelperTheme.colors.mainColor else CHelperTheme.colors.iconMain
    Box(
        modifier
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .size(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            id = if (checked) Res.drawable.square_check else Res.drawable.square,
            modifier = Modifier.size(20.dp),
            tint = color,
        )
    }
}
