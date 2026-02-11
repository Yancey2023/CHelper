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

#ifndef CHELPER_AUTOSUGGESTION_H
#define CHELPER_AUTOSUGGESTION_H

#include <chelper/auto_suggestion/Suggestions.h>
#include <chelper/parser/ASTNode.h>

namespace CHelper::AutoSuggestion {

    Suggestions getSuggestions(const ASTNode &astNode, size_t index);

}// namespace CHelper::AutoSuggestion

#endif//CHELPER_AUTOSUGGESTION_H
