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

#include <chelper/util/CPackMemory.h>
#include <pch.h>

namespace CHelper {

    class NormalId {
    public:
        std::pmr::u16string name;
        std::optional<std::pmr::u16string> description;

    private:
        // 名称匹配只缓存摘要；流式状态属于一次补全请求，不随每个 ID 常驻。
        std::optional<XXH64_hash_t> nameHash;

    public:
        NormalId() = default;

        virtual ~NormalId() = default;

        void buildHash();

        [[nodiscard]] bool fastMatch(XXH64_hash_t strHash);

        // 追加名称和描述，调用方负责初始化并持有流式状态。
        void updateHashState(XXH3_state_t &state) const;

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
