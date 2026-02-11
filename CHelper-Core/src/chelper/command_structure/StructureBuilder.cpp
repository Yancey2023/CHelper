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

#include <chelper/command_structure/StructureBuilder.h>

namespace CHelper::CommandStructure {

    StructureBuilder &StructureBuilder::appendUnknown(bool isMustHave) {
        return append(isMustHave, u"未知");
    }

    StructureBuilder &StructureBuilder::appendSymbol(char16_t ch) {
        structure.push_back(ch);
        return *this;
    }

    StructureBuilder &StructureBuilder::append(const std::u16string &str) {
        structure.append(str);
        return *this;
    }

    StructureBuilder &StructureBuilder::appendSpace() {
        if (structure.empty()) [[unlikely]] {
            return *this;
        }
        return appendSymbol(' ');
    }

    StructureBuilder &StructureBuilder::appendLeftBracket(bool isMustHave) {
        return appendSymbol(isMustHave ? u'<' : u'[');
    }

    StructureBuilder &StructureBuilder::appendRightBracket(bool isMustHave) {
        return appendSymbol(isMustHave ? u'>' : u']');
    }

    StructureBuilder &StructureBuilder::append(bool isMustHave, const std::u16string &str) {
        return appendSpace().appendLeftBracket(isMustHave).append(str).appendRightBracket(isMustHave);
    }

    std::u16string StructureBuilder::build() {
        return std::move(structure);
    }

}// namespace CHelper::CommandStructure