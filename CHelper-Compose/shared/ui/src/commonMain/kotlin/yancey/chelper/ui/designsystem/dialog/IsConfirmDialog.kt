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

package yancey.chelper.ui.designsystem.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import yancey.chelper.resources.*
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.widget.Divider
import yancey.chelper.ui.designsystem.widget.DividerVertical
import yancey.chelper.ui.designsystem.widget.Text

@Composable
fun IsConfirmDialog(
    onDismissRequest: () -> Unit,
    title: String = stringResource(Res.string.dialog_is_confirm_title),
    content: String,
    cancelText: String = stringResource(Res.string.dialog_is_confirm_cancel),
    confirmText: String = stringResource(Res.string.dialog_is_confirm_confirm),
    onCancel: () -> Unit = {},
    onConfirm: () -> Unit = {},
    contentLinksEnabled: Boolean = false,
    onContentLinkClick: ((String) -> Unit)? = null,
) {
    val linkColor = CHelperTheme.colors.mainColor
    val renderedContent =
        remember(content, contentLinksEnabled, linkColor, onContentLinkClick) {
            if (contentLinksEnabled) {
                annotateHttpLinks(content, linkColor, onContentLinkClick)
            } else {
                AnnotatedString(content)
            }
        }
    AppDialog(onDismissRequest = onDismissRequest) {
        DialogContainer(backgroundNoTranslate = true) {
            Column {
                Column(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    Text(
                        modifier = Modifier.fillMaxWidth().padding(0.dp, 10.dp),
                        text = title,
                        style = TextStyle(fontSize = 18.sp, textAlign = TextAlign.Center),
                    )
                    Text(
                        modifier =
                            Modifier.padding(20.dp, 10.dp)
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                        text = renderedContent,
                        style = TextStyle(textAlign = TextAlign.Center),
                    )
                }
                Divider(0.dp)
                Row(Modifier.height(45.dp)) {
                    Box(
                        Modifier.fillMaxHeight().weight(1f).clickable(role = Role.Button) {
                            onDismissRequest()
                            onCancel()
                        }
                    ) {
                        Text(
                            modifier = Modifier.align(Alignment.Center),
                            text = cancelText,
                            style =
                                TextStyle(
                                    fontSize = 18.sp,
                                    color = CHelperTheme.colors.mainColor,
                                    textAlign = TextAlign.Center,
                                ),
                        )
                    }
                    DividerVertical(0.dp)
                    Box(
                        Modifier.fillMaxHeight().weight(1f).clickable(role = Role.Button) {
                            onDismissRequest()
                            onConfirm()
                        }
                    ) {
                        Text(
                            modifier = Modifier.align(Alignment.Center),
                            text = confirmText,
                            style =
                                TextStyle(
                                    fontSize = 18.sp,
                                    color = CHelperTheme.colors.mainColor,
                                    textAlign = TextAlign.Center,
                                ),
                        )
                    }
                }
            }
        }
    }
}

fun annotateHttpLinks(
    text: String,
    linkColor: Color,
    onLinkClick: ((String) -> Unit)? = null,
): AnnotatedString = buildAnnotatedString {
    val interactionListener = onLinkClick?.let { callback ->
        LinkInteractionListener { link -> (link as? LinkAnnotation.Url)?.url?.let(callback) }
    }
    var contentStart = 0
    HTTP_URL_REGEX.findAll(text).forEach { match ->
        val url = match.value.trimEnd('.', ',', ';', ':', '!', ')', ']')
        if (url.isEmpty()) return@forEach

        append(text.substring(contentStart, match.range.first))
        withLink(
            LinkAnnotation.Url(
                url = url,
                styles =
                    TextLinkStyles(
                        style =
                            SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                    ),
                linkInteractionListener = interactionListener,
            )
        ) {
            append(url)
        }
        contentStart = match.range.first + url.length
    }
    append(text.substring(contentStart))
}

private val HTTP_URL_REGEX =
    Regex(
        pattern =
            """https?://(?:localhost|(?:\d{1,3}\.){3}\d{1,3}|(?:[A-Za-z0-9-]+\.)+[A-Za-z0-9-]{2,})(?::\d{1,5})?(?:[/?#][A-Za-z0-9\-._~:/?#\[\]@!&()*+,;=%]*)?""",
        option = RegexOption.IGNORE_CASE,
    )
