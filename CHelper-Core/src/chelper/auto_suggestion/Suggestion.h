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

#ifndef CHELPER_SUGGESTION_H
#define CHELPER_SUGGESTION_H

#include <chelper/parser/TokensView.h>
#include <chelper/resources/id/NormalId.h>

namespace CHelper {

    class CHelperCore;

    namespace AutoSuggestion {

        class Suggestion {
        public:
            //要被替换的位置
            size_t start, end;
            //如果光标在最后面，是否需要在最后加空格
            bool isAddSpace;
            //内容
            std::shared_ptr<NormalId> content;

        public:
            Suggestion(size_t start, size_t end, bool isAddSpace, const std::shared_ptr<NormalId> &content);

            Suggestion(const TokensView &tokens, bool isAddSpace, const std::shared_ptr<NormalId> &content);

            [[nodiscard]] XXH64_hash_t hashCode() const;
        };

    }// namespace AutoSuggestion

}// namespace CHelper

#endif//CHELPER_SUGGESTION_H
