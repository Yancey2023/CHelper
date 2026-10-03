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

    Suggestion::Suggestion(size_t start,
                           size_t end,
                           bool isAddSpace,
                           const std::shared_ptr<NormalId> &content)
        : start(start),
          end(end),
          isAddSpace(isAddSpace),
          content(content) {}

    Suggestion::Suggestion(const TokensView &tokens,
                           bool isAddSpace,
                           const std::shared_ptr<NormalId> &content)
        : start(tokens.startIndex),
          end(tokens.endIndex),
          isAddSpace(isAddSpace),
          content(content) {}

    [[nodiscard]] XXH64_hash_t Suggestion::hashCode() const {
        // 常见 ID 和描述很短，合并到栈上后一次哈希，省去流式状态的初始化与多次更新。
        // 字节顺序与下方流式路径完全相同，保持已有去重规则。
        std::byte buffer[256];
        const size_t descriptionSize = content->description ? content->description->size() : 0;
        if (content->name.size() + descriptionSize <= (sizeof(buffer) - sizeof(start) - sizeof(end)) / sizeof(char16_t)) {
            const size_t nameBytes = content->name.size() * sizeof(char16_t);
            const size_t descriptionBytes = descriptionSize * sizeof(char16_t);
            std::memcpy(buffer, content->name.data(), nameBytes);
            if (descriptionBytes != 0) {
                std::memcpy(buffer + nameBytes, content->description->data(), descriptionBytes);
            }
            const size_t size = nameBytes + descriptionBytes;
            std::memcpy(buffer + size, &start, sizeof(start));
            std::memcpy(buffer + size + sizeof(start), &end, sizeof(end));
            return XXH3_64bits(buffer, size + sizeof(start) + sizeof(end));
        }
        XXH3_state_t hashState;
        XXH3_64bits_reset(&hashState);
        content->updateHashState(hashState);
        XXH3_64bits_update(&hashState, &start, sizeof(start));
        XXH3_64bits_update(&hashState, &end, sizeof(end));
        return XXH3_64bits_digest(&hashState);
    }

}// namespace CHelper::AutoSuggestion
