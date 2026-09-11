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

#ifndef CHELPER_NORMALID_H
#define CHELPER_NORMALID_H

#include <chelper/util/CPackMemory.h>
#include <pch.h>

namespace CHelper {

    class NormalId {
    public:
        std::pmr::u16string name;
        std::optional<std::pmr::u16string> description;

    private:
        XXH64_hash_t nameHash = 0;
        std::optional<XXH3_state_t> hashState;

    public:
        NormalId() = default;

        virtual ~NormalId() = default;

        void buildHash();

        [[nodiscard]] bool fastMatch(XXH64_hash_t strHash);

        [[nodiscard]] XXH3_state_t *getHashState();

        static std::shared_ptr<NormalId> make(std::u16string_view name, std::u16string_view description);

        static std::shared_ptr<NormalId> make(std::u16string_view name) {
            return make(name, std::u16string_view());
        }

        static std::shared_ptr<NormalId> make(std::u16string_view name, std::nullopt_t) {
            return make(name);
        }

        template<class String>
        static std::shared_ptr<NormalId> make(std::u16string_view name, const std::optional<String> &description) {
            auto result = allocateSharedFromDefault<NormalId>();
            result->name.assign(name.data(), name.size());
            if (description.has_value()) {
                result->description.emplace(description->data(), description->size());
            }
            return result;
        }
    };

}// namespace CHelper


#endif//CHELPER_NORMALID_H
