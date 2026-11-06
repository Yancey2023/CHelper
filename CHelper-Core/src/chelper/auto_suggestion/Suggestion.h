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
            // 内容直接引用资源；本结果及其复制引用须先于来源 CommandContext 销毁。
            // 沿用原 API 的指针类型；发布后只读由调用方约定保证。
            std::shared_ptr<NormalId> content;

        public:
            Suggestion(size_t start, size_t end, bool isAddSpace, const std::shared_ptr<NormalId> &content) noexcept;

            Suggestion(size_t start, size_t end, bool isAddSpace, std::shared_ptr<NormalId> &&content) noexcept;

            Suggestion(const TokensView &tokens, bool isAddSpace, const std::shared_ptr<NormalId> &content) noexcept;

            Suggestion(const TokensView &tokens, bool isAddSpace, std::shared_ptr<NormalId> &&content) noexcept;

            [[nodiscard]] XXH64_hash_t hashCode() const;
        };

    }// namespace AutoSuggestion

}// namespace CHelper
