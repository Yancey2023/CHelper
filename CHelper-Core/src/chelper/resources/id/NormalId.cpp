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
        if (!hashState.has_value()) {
            hashState = std::make_optional<XXH3_state_t>();
            XXH3_64bits_reset(&hashState.value());
            XXH3_64bits_update(&hashState.value(), name.data(), name.size() * sizeof(decltype(name)::value_type));
            nameHash = XXH3_64bits_digest(&hashState.value());
            if (description.has_value()) {
                XXH3_64bits_update(&hashState.value(), description.value().data(), description.value().size() * sizeof(decltype(description)::value_type::value_type));
            }
        }
    }

    [[nodiscard]] bool NormalId::fastMatch(XXH64_hash_t strHash) {
        buildHash();
        return nameHash == strHash;
    }

    [[nodiscard]] XXH3_state_t *NormalId::getHashState() {
        buildHash();
        return &hashState.value();
    }

    std::shared_ptr<NormalId> NormalId::make(const std::u16string &name, const std::optional<std::u16string> &description) {
        auto result = std::make_shared<NormalId>();
        result->name = name;
        result->description = description;
        return result;
    }

}// namespace CHelper