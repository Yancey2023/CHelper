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
        XXH3_state_t hashState;
        XXH3_copyState(&hashState, content->getHashState());
        XXH3_64bits_update(&hashState, &start, sizeof(start));
        XXH3_64bits_update(&hashState, &end, sizeof(end));
        return XXH3_64bits_digest(&hashState);
    }

}// namespace CHelper