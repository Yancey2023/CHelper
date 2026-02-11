/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Yancey
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package yancey.chelper.android.common.util;

import java.io.File;
import java.util.Objects;

public class PolicyGrantManager {

    public enum State {
        NOT_READ,
        UPDATED,
        AGREE
    }

    public static PolicyGrantManager INSTANCE;
    private final String privacyPolicy;
    private final String privacyPolicyHashStr;
    private final File lastReadContentFile;
    private State state;

    private PolicyGrantManager(String privacyPolicy, File lastReadContentFile) {
        this.privacyPolicy = privacyPolicy;
        this.lastReadContentFile = lastReadContentFile;
        this.privacyPolicyHashStr = String.valueOf(privacyPolicy.hashCode());
        String lastRead = FileUtil.readString(lastReadContentFile);
        this.state = State.AGREE;
        if (!lastReadContentFile.exists()) {
            this.state = State.NOT_READ;
        } else {
            if (!Objects.equals(privacyPolicyHashStr, lastRead)) {
                this.state = State.UPDATED;
            }
        }
    }

    public static void init(String privacyPolicy, File lastReadContentFile) {
        INSTANCE = new PolicyGrantManager(privacyPolicy, lastReadContentFile);
    }

    public State getState() {
        return state;
    }

    public String getPrivatePolicy() {
        return privacyPolicy;
    }

    public void agree() {
        if (state != State.AGREE) {
            FileUtil.writeString(lastReadContentFile, privacyPolicyHashStr);
            state = State.AGREE;
            MonitorUtil.onAgreePolicyGrant();
        }
    }

}
