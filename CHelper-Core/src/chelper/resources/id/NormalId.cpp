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

#include <chelper/resources/id/NormalId.h>

namespace CHelper {

    void NormalId::buildHash() {
        if (!nameHash.has_value()) {
            nameHash = XXH3_64bits(name.data(), name.size() * sizeof(char16_t));
        }
    }

    [[nodiscard]] bool NormalId::fastMatch(XXH64_hash_t strHash) {
        buildHash();
        return *nameHash == strHash;
    }

    void NormalId::updateHashState(XXH3_state_t &state) const {
        XXH3_64bits_update(&state, name.data(), name.size() * sizeof(char16_t));
        if (description.has_value()) {
            XXH3_64bits_update(&state, description->data(), description->size() * sizeof(char16_t));
        }
    }

    std::shared_ptr<NormalId> NormalId::make(const std::u16string_view name, const std::u16string_view description) {
        auto result = allocateSharedFromDefault<NormalId>();
        result->name.assign(name.data(), name.size());
        if (!description.empty()) {
            result->description.emplace(description.data(), description.size());
        }
        return result;
    }

}// namespace CHelper
