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

#ifndef CHELPER_NAMESPACEID_H
#define CHELPER_NAMESPACEID_H

#include <chelper/resources/id/NormalId.h>
#include <pch.h>

namespace CHelper {

    class NamespaceId : public NormalId {
    public:
        std::optional<std::u16string> idNamespace;

    private:
        std::shared_ptr<NormalId> idWithNamespace;

    public:
        std::shared_ptr<NormalId> &getIdWithNamespace();

        /**
         * 是否允许省略命名空间前缀（即短名合法）：仅缺省（视为 minecraft）
         * 或显式 minecraft 的条目可省；其它命名空间（demo:xxx）必须带前缀。
         */
        [[nodiscard]] bool canOmitNamespace() const {
            return !idNamespace.has_value() || idNamespace.value() == u"minecraft";
        }

        /**
         * 输入 token 命中判定（统一语义，Parser/Linter 共用）：
         * 全名（demo:xxx 等）命中，或短名命中且该条目允许省略命名空间。
         */
        bool matchesToken(XXH64_hash_t tokenHash) {
            return getIdWithNamespace()->fastMatch(tokenHash) ||
                   (canOmitNamespace() && fastMatch(tokenHash));
        }
    };

}// namespace CHelper

CODEC_WITH_PARENT(CHelper::NamespaceId, CHelper::NormalId, idNamespace)

#endif//CHELPER_NAMESPACEID_H
