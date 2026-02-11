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

package yancey.chelper.core;

/**
 * 语法高亮主题
 */
public class Theme {

    public static Theme THEME_DAY = new Theme();
    public static Theme THEME_NIGHT = new Theme();

    public int colorBoolean;
    public int colorFloat;
    public int colorInteger;
    public int colorSymbol;
    public int colorId;
    public int colorTargetSelector;
    public int colorCommand;
    public int colorBrackets1;
    public int colorBrackets2;
    public int colorBrackets3;
    public int colorString;
    public int colorNull;
    public int colorRange;
    public int colorLiteral;

    static {
        int COLOR_PURPLE = 0xFF9f20a7;
        int COLOR_ORANGE = 0xFFd95a53;
        int COLOR_LIGHT_BLUE = 0xFF0fa0c8;
        int COLOR_BLUE = 0xFF4571e1;
        int COLOR_LIGHT_GREEN = 0xFF4fad63;
        int COLOR_GREEN = 0xFF07c160;
        int COLOR_LIGHT_YELLOW = 0xFFd4ac0d;
        int COLOR_YELLOW = 0xFF836c0a;

        THEME_DAY.colorBoolean = COLOR_LIGHT_GREEN;
        THEME_DAY.colorFloat = COLOR_LIGHT_GREEN;
        THEME_DAY.colorInteger = COLOR_LIGHT_GREEN;
        THEME_DAY.colorSymbol = COLOR_LIGHT_GREEN;
        THEME_DAY.colorId = COLOR_LIGHT_YELLOW;
        THEME_DAY.colorTargetSelector = COLOR_GREEN;
        THEME_DAY.colorCommand = COLOR_PURPLE;
        THEME_DAY.colorBrackets1 = COLOR_YELLOW;
        THEME_DAY.colorBrackets2 = COLOR_PURPLE;
        THEME_DAY.colorBrackets3 = COLOR_BLUE;
        THEME_DAY.colorString = COLOR_ORANGE;
        THEME_DAY.colorNull = COLOR_LIGHT_BLUE;
        THEME_DAY.colorRange = COLOR_LIGHT_BLUE;
        THEME_DAY.colorLiteral = COLOR_LIGHT_BLUE;
    }

    static {
        int COLOR_PURPLE = 0xFFc586c0;
        int COLOR_ORANGE = 0xFFce9178;
        int COLOR_LIGHT_BLUE = 0xFF9cdcfe;
        int COLOR_BLUE = 0xFF179fff;
        int COLOR_LIGHT_GREEN = 0xFFb5cea8;
        int COLOR_GREEN = 0xFF4ec9b0;
        int COLOR_LIGHT_YELLOW = 0xFFdcdcaa;
        int COLOR_YELLOW = 0xFFffd700;

        THEME_NIGHT.colorBoolean = COLOR_LIGHT_GREEN;
        THEME_NIGHT.colorFloat = COLOR_LIGHT_GREEN;
        THEME_NIGHT.colorInteger = COLOR_LIGHT_GREEN;
        THEME_NIGHT.colorSymbol = COLOR_LIGHT_GREEN;
        THEME_NIGHT.colorId = COLOR_LIGHT_YELLOW;
        THEME_NIGHT.colorTargetSelector = COLOR_GREEN;
        THEME_NIGHT.colorCommand = COLOR_PURPLE;
        THEME_NIGHT.colorBrackets1 = COLOR_YELLOW;
        THEME_NIGHT.colorBrackets2 = COLOR_PURPLE;
        THEME_NIGHT.colorBrackets3 = COLOR_BLUE;
        THEME_NIGHT.colorString = COLOR_ORANGE;
        THEME_NIGHT.colorNull = COLOR_LIGHT_BLUE;
        THEME_NIGHT.colorRange = COLOR_LIGHT_BLUE;
        THEME_NIGHT.colorLiteral = COLOR_LIGHT_BLUE;
    }

    public int getColorByToken(int token, int normalColor) {
        return switch (token) {
            case 1 -> colorBoolean;
            case 2 -> colorFloat;
            case 3 -> colorInteger;
            case 4 -> colorSymbol;
            case 5 -> colorId;
            case 6 -> colorTargetSelector;
            case 7 -> colorCommand;
            case 8 -> colorBrackets1;
            case 9 -> colorBrackets2;
            case 10 -> colorBrackets3;
            case 11 -> colorString;
            case 12 -> colorNull;
            case 13 -> colorRange;
            case 14 -> colorLiteral;
            default -> normalColor;
        };
    }
}
