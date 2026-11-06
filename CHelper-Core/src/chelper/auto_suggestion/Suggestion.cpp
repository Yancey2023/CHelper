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

#include <chelper/CHelperCore.h>
#include <chelper/auto_suggestion/Suggestion.h>

namespace CHelper::AutoSuggestion {

    Suggestion::Suggestion(size_t start, size_t end, bool isAddSpace, const std::shared_ptr<NormalId> &content) noexcept
        : start(start), end(end), isAddSpace(isAddSpace), content(content) {}

    Suggestion::Suggestion(size_t start, size_t end, bool isAddSpace, std::shared_ptr<NormalId> &&content) noexcept
        : start(start), end(end), isAddSpace(isAddSpace), content(std::move(content)) {}

    Suggestion::Suggestion(const TokensView &tokens, bool isAddSpace, const std::shared_ptr<NormalId> &content) noexcept
        : Suggestion(tokens.startIndex, tokens.endIndex, isAddSpace, content) {}

    Suggestion::Suggestion(const TokensView &tokens, bool isAddSpace, std::shared_ptr<NormalId> &&content) noexcept
        : Suggestion(tokens.startIndex, tokens.endIndex, isAddSpace, std::move(content)) {}

    [[nodiscard]] XXH64_hash_t Suggestion::hashCode() const {
        // 字段边界在最终摘要中混合，不扩大缓冲区或改变短字符串的快速路径。
        // 去重只保存摘要，不复制 Suggestion 或增加 content 的引用计数。
        std::byte buffer[256];
        constexpr size_t rangeBytes = sizeof(start) + sizeof(end);
        const uint8_t flags = uint8_t(content->description.has_value()) | (uint8_t(isAddSpace) << 1);
        // 原摘要已覆盖文本和范围；字段边界只需编码长度与两位标志。
        // 字段区分不需要另做两次乘法混合。
        const uint64_t fieldHash = (uint64_t(content->name.size()) << 2) | flags;
        const size_t descriptionSize = content->description ? content->description->size() : 0;
        if (content->name.size() + descriptionSize <= (sizeof(buffer) - rangeBytes) / sizeof(char16_t)) {
            const size_t nameBytes = content->name.size() * sizeof(char16_t);
            const size_t descriptionBytes = descriptionSize * sizeof(char16_t);
            std::memcpy(buffer, content->name.data(), nameBytes);
            if (descriptionBytes != 0) {
                std::memcpy(buffer + nameBytes, content->description->data(), descriptionBytes);
            }
            const size_t size = nameBytes + descriptionBytes;
            std::memcpy(buffer + size, &start, sizeof(start));
            std::memcpy(buffer + size + sizeof(start), &end, sizeof(end));
            return XXH3_64bits(buffer, size + rangeBytes) ^ fieldHash;
        }
        const std::array<size_t, 2> fields{start, end};
        XXH3_state_t hashState;
        XXH3_64bits_reset(&hashState);
        content->updateHashState(hashState);
        XXH3_64bits_update(&hashState, fields.data(), sizeof(fields));
        return XXH3_64bits_digest(&hashState) ^ fieldHash;
    }

}// namespace CHelper::AutoSuggestion
