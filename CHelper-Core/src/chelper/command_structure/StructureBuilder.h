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

#pragma once

#ifndef CHELPER_STRUCTUREBUILDER_H
#define CHELPER_STRUCTUREBUILDER_H

#include <pch.h>

namespace CHelper::CommandStructure {

    class StructureBuilder {
    private:
        std::u16string structure;

    public:
        StructureBuilder &appendUnknown(bool isMustHave);

        StructureBuilder &appendSymbol(char16_t ch);

        StructureBuilder &appendString(const std::u16string_view &str);

        StructureBuilder &appendSpace();

        StructureBuilder &appendLeftBracket(bool isMustHave);

        StructureBuilder &appendRightBracket(bool isMustHave);

        StructureBuilder &appendStringWithBracket(bool isMustHave, const std::u16string_view &str);

        std::u16string build();
    };

}// namespace CHelper::CommandStructure

#endif//CHELPER_STRUCTUREBUILDER_H
