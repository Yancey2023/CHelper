/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026 Yancey
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

#pragma once

#include <algorithm>
#include <chelper/util/HashContainer.h>
#include <utility>

namespace CHelper {

    // 一个解析过程或错误查询内复用匹配结果；集合身份区分不同 ID 类型。
    // 不持有资源包或 AST，也不复用带有位置的错误对象。
    class IdMatchCache {
        struct Key {
            const void *contents;
            XXH64_hash_t nameHash;

            bool operator==(const Key &) const = default;
        };

        struct Hash {
            using is_avalanching = void;

            uint64_t operator()(const Key &key) const noexcept {
                const uint64_t values[]{reinterpret_cast<uintptr_t>(key.contents), key.nameHash};
                return XXH3_64bits(values, sizeof(values));
            }
        };

        DenseMap<Key, bool, Hash> matches;
        Key lastKey{};
        bool lastResult = false;
        bool hasLast = false;

    public:
        template<class Contents, class Match>
        bool contains(const Contents &contents, XXH64_hash_t nameHash, Match &&match) {
            const Key key{contents.get(), nameHash};
            if (hasLast && key == lastKey) return lastResult;
            const auto found = matches.find(key);
            if (found != matches.end()) {
                lastKey = key;
                hasLast = true;
                return lastResult = found->second;
            }
            const bool result = std::ranges::any_of(*contents, std::forward<Match>(match));
            matches.emplace(key, result);
            lastKey = key;
            hasLast = true;
            lastResult = result;
            return result;
        }
    };

}// namespace CHelper
