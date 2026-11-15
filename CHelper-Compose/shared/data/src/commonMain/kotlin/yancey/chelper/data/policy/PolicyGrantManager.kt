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

package yancey.chelper.data.policy

import okio.FileSystem
import okio.Path
import yancey.chelper.data.local.readText
import yancey.chelper.data.local.writeText

class PolicyGrantManager
private constructor(
    privacyPolicy: String,
    private val lastReadContentFile: Path,
    private val onAgree: () -> Unit,
    private val fileSystem: FileSystem,
) {
    enum class State {
        NOT_READ,
        UPDATED,
        AGREE,
    }

    private val privacyPolicyHashStr: String = privacyPolicy.hashCode().toString()
    var state: State
        private set

    init {
        try {
            if (!fileSystem.exists(lastReadContentFile)) {
                this.state = State.NOT_READ
            } else {
                val lastRead = fileSystem.readText(lastReadContentFile)
                if (privacyPolicyHashStr != lastRead) {
                    this.state = State.UPDATED
                } else {
                    this.state = State.AGREE
                }
            }
        } catch (_: Throwable) {
            this.state = State.NOT_READ
        }
    }

    fun agree() {
        if (state != State.AGREE) {
            fileSystem.writeText(lastReadContentFile, privacyPolicyHashStr)
            state = State.AGREE
            onAgree()
        }
    }

    companion object {
        lateinit var INSTANCE: PolicyGrantManager

        fun init(
            privacyPolicy: String,
            lastReadContentFile: Path,
            onAgree: () -> Unit = {},
            fileSystem: FileSystem = FileSystem.SYSTEM,
        ) {
            INSTANCE = PolicyGrantManager(privacyPolicy, lastReadContentFile, onAgree, fileSystem)
        }
    }
}
