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

import android.text.InputFilter;
import android.text.Spanned;

import androidx.annotation.NonNull;

/**
 * 用户过滤用户的非法操作
 */
public class MyInputFilter implements InputFilter {

    @NonNull
    public String blackList;

    public MyInputFilter(@NonNull String blackList) {
        this.blackList = blackList;
    }

    @Override
    public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int idend) {
        for (int i = 0; i < source.length(); i++) {
            char ch1 = source.charAt(i);
            for (int j = 0; j < blackList.length(); j++) {
                char ch2 = blackList.charAt(j);
                if (ch1 == ch2) {
                    return "";
                }
            }
        }
        return null;
    }

}
