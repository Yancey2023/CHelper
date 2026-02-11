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

#ifndef CHELPER_LINTER_H
#define CHELPER_LINTER_H

#include <chelper/parser/ASTNode.h>
#include <pch.h>

namespace CHelper::Linter {

    std::vector<std::shared_ptr<ErrorReason>> getErrorsExceptParseError(const ASTNode &astNode);

    std::vector<std::shared_ptr<ErrorReason>> getErrorReasons(const ASTNode &astNode);

}// namespace CHelper::Linter

#endif //CHELPER_LINTER_H
