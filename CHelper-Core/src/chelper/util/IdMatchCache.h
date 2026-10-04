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

        struct IdIndex {
            size_t distinctQueries = 0;
            bool ready = false;
            DenseSet<XXH64_hash_t, XXHashDigest> names;
        };
        DenseMap<const void *, IdIndex, std::hash<const void *>> indexes;

    public:
        // 少量不同名称仍直接扫描；多次查询同一集合后建立查询内索引。
        // 索引只保存名称摘要，生命周期与解析或错误查询相同。
        template<class Contents>
        bool containsId(const Contents &contents, XXH64_hash_t nameHash) {
            const Key key{contents.get(), nameHash};
            if (hasLast && key == lastKey) return lastResult;
            auto &index = indexes.try_emplace(contents.get()).first->second;
            bool result;
            if (index.ready) {
                result = index.names.contains(nameHash);
            } else if (const auto found = matches.find(key); found != matches.end()) {
                result = found->second;
            } else if (++index.distinctQueries < 8) {
                result = std::ranges::any_of(*contents, [nameHash](const auto &item) {
                    if (item->fastMatch(nameHash)) return true;
                    if constexpr (requires { item->getIdWithNamespace(); }) {
                        return item->getIdWithNamespace()->fastMatch(nameHash);
                    } else {
                        return false;
                    }
                });
                matches.emplace(key, result);
            } else {
                DenseSet<XXH64_hash_t, XXHashDigest> names;
                names.reserve(contents->size());
                for (const auto &item: *contents) {
                    names.insert(item->getNameHash());
                    if constexpr (requires { item->getIdWithNamespace(); }) {
                        names.insert(item->getIdWithNamespace()->getNameHash());
                    }
                }
                index.names = std::move(names);
                index.ready = true;
                result = index.names.contains(nameHash);
            }
            lastKey = key;
            hasLast = true;
            return lastResult = result;
        }

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
