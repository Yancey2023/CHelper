/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Yancey
 * 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https:></https:>//www.gnu.org/licenses/>.
 */
package yancey.chelper.android.common.util

import java.io.File

class PolicyGrantManager private constructor(privacyPolicy: String,
                                             private val lastReadContentFile: File
) {
    enum class State {
        NOT_READ,
        UPDATED,
        AGREE
    }

    private val privacyPolicyHashStr: String = privacyPolicy.hashCode().toString()
    var state: State
        private set

    init {
        val lastRead = lastReadContentFile.readBytes().decodeToString()
        this.state = State.AGREE
        if (!lastReadContentFile.exists()) {
            this.state = State.NOT_READ
        } else {
            if (privacyPolicyHashStr != lastRead) {
                this.state = State.UPDATED
            }
        }
    }

    fun agree() {
        if (state != State.AGREE) {
            lastReadContentFile.outputStream().write(privacyPolicyHashStr.toByteArray())
            state = State.AGREE
            MonitorUtil.onAgreePolicyGrant()
        }
    }

    companion object {
        @JvmField
        var INSTANCE: PolicyGrantManager? = null
        fun init(privacyPolicy: String, lastReadContentFile: File) {
            INSTANCE = PolicyGrantManager(privacyPolicy, lastReadContentFile)
        }
    }
}
