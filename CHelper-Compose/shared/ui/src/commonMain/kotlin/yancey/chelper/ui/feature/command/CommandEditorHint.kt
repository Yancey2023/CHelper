/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Yancey
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package yancey.chelper.ui.feature.command

const val COMMAND_EDITOR_HINT_NODE_THRESHOLD = 18

fun shouldShowCommandEditorHint(
    nodeCount: Int,
    isCommandEditorMode: Boolean,
    isScreenVisible: Boolean,
): Boolean =
    nodeCount > COMMAND_EDITOR_HINT_NODE_THRESHOLD && !isCommandEditorMode && isScreenVisible
