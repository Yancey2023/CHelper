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

#include <pch.h>

namespace CHelper {

    class NormalId {
    public:
        std::u16string name;
        std::optional<std::u16string> description;

    private:
        XXH64_hash_t nameHash = 0;
        std::optional<XXH3_state_t> hashState;

    public:
        NormalId() = default;

        virtual ~NormalId() = default;

        void buildHash();

        [[nodiscard]] bool fastMatch(XXH64_hash_t strHash);

        [[nodiscard]] XXH3_state_t *getHashState();

        static std::shared_ptr<NormalId> make(const std::u16string &name, const std::optional<std::u16string> &description);
    };

}// namespace CHelper

CODEC(CHelper::NormalId, name, description)

#endif//CHELPER_NORMALID_H
