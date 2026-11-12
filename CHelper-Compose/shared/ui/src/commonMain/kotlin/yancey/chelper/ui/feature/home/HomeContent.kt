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

package yancey.chelper.ui.feature.home

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import org.jetbrains.compose.resources.*
import yancey.chelper.resources.*
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.layout.Collection
import yancey.chelper.ui.designsystem.layout.CollectionName
import yancey.chelper.ui.designsystem.layout.Copyright
import yancey.chelper.ui.designsystem.layout.NameAndAction
import yancey.chelper.ui.designsystem.layout.RootView
import yancey.chelper.ui.designsystem.widget.Divider
import yancey.chelper.ui.designsystem.widget.Text

enum class HomeAction {
    Completion,
    FloatingWindow,
    Settings,
    Old2New,
    Old2NewIMEGuide,
    Enumeration,
    LibraryMain,
    LoongFlow,
    Rawtext,
    About,
}

@Composable
fun HomeContent(platformActions: Set<HomeAction> = emptySet(), onAction: (HomeAction) -> Unit) {
    RootView {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .background(CHelperTheme.colors.backgroundComponent)
                            .padding(horizontal = 25.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(Res.drawable.pack_icon),
                        contentDescription = stringResource(Res.string.app_name),
                        modifier = Modifier.size(width = 70.dp, height = 70.dp),
                    )
                    Column(modifier = Modifier.padding(start = 20.dp)) {
                        Text(
                            text = stringResource(Res.string.layout_home_app_name),
                            style =
                                TextStyle(
                                    color = CHelperTheme.colors.textBond,
                                    fontSize = 25.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                        )
                        Text(
                            text = stringResource(Res.string.layout_home_app_description),
                            style =
                                TextStyle(color = CHelperTheme.colors.textBond, fontSize = 18.sp),
                        )
                    }
                }
                CollectionName(stringResource(Res.string.layout_home_command_completion))
                Collection {
                    NameAndAction(
                        name = stringResource(Res.string.layout_home_command_completion_app_mode),
                        onClick = { onAction(HomeAction.Completion) },
                    )
                    if (HomeAction.FloatingWindow in platformActions) {
                        Divider()
                        NameAndAction(
                            name =
                                stringResource(
                                    Res.string.layout_home_command_completion_floating_window_mode
                                ),
                            onClick = { onAction(HomeAction.FloatingWindow) },
                        )
                    }
                    Divider()
                    NameAndAction(
                        stringResource(Res.string.layout_home_command_completion_settings)
                    ) {
                        onAction(HomeAction.Settings)
                    }
                }
                CollectionName(stringResource(Res.string.layout_home_old2new))
                Collection {
                    NameAndAction(stringResource(Res.string.layout_home_old2new_app_mode)) {
                        onAction(HomeAction.Old2New)
                    }
                    if (HomeAction.Old2NewIMEGuide in platformActions) {
                        Divider()
                        NameAndAction(stringResource(Res.string.layout_home_old2new_ime_mode)) {
                            onAction(HomeAction.Old2NewIMEGuide)
                        }
                    }
                }
                CollectionName(stringResource(Res.string.layout_home_enumeration))
                Collection {
                    NameAndAction(stringResource(Res.string.layout_home_enumeration_app_mode)) {
                        onAction(HomeAction.Enumeration)
                    }
                }
                CollectionName(stringResource(Res.string.layout_home_experimental_feature))
                Collection {
                    NameAndAction("命令库") { onAction(HomeAction.LibraryMain) }
                    if (HomeAction.LoongFlow in platformActions) {
                        Divider()
                        NameAndAction("游龙导出") { onAction(HomeAction.LoongFlow) }
                    }
                    Divider()
                    NameAndAction(
                        stringResource(Res.string.layout_home_experimental_feature_raw_json_studio)
                    ) {
                        onAction(HomeAction.Rawtext)
                    }
                }
                CollectionName(stringResource(Res.string.layout_home_about))
                Collection {
                    NameAndAction(stringResource(Res.string.layout_home_about_app_mode)) {
                        onAction(HomeAction.About)
                    }
                }
            }
            Copyright(Modifier.align(Alignment.CenterHorizontally))
        }
    }
}
