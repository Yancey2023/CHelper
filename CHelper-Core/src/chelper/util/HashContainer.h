/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026 Yancey
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

#pragma once

#include <cstdint>
#include <functional>
#include <memory_resource>
#include <string>
#include <string_view>
#include <type_traits>

#include <ankerl/unordered_dense.h>
#include <xxhash.h>

namespace CHelper {

    template<class T>
    struct XXHash {
        using is_avalanching = void;

        uint64_t operator()(const T &value) const noexcept {
            static_assert(std::is_integral_v<T> || std::is_enum_v<T>);
            return XXH3_64bits(&value, sizeof(value));
        }
    };

    template<class Char, class Traits>
    struct XXHash<std::basic_string_view<Char, Traits>> {
        using is_avalanching = void;
        using is_transparent = void;

        uint64_t operator()(std::basic_string_view<Char, Traits> value) const noexcept {
            return XXH3_64bits(value.data(), value.size() * sizeof(Char));
        }
    };

    template<class Char, class Traits, class Allocator>
    struct XXHash<std::basic_string<Char, Traits, Allocator>> : XXHash<std::basic_string_view<Char, Traits>> {};

    // Suggestion 的键已经是 XXH3 摘要，直接使用全部 64 位，避免再次哈希。
    struct XXHashDigest {
        using is_avalanching = void;

        uint64_t operator()(uint64_t value) const noexcept { return value; }
    };

    template<class Key, class Value, class Hash = XXHash<Key>, class Equal = std::equal_to<>>
    using DenseMap = ankerl::unordered_dense::map<Key, Value, Hash, Equal>;

    template<class Key, class Hash = XXHash<Key>, class Equal = std::equal_to<>>
    using DenseSet = ankerl::unordered_dense::set<Key, Hash, Equal>;

    template<class Key, class Value, class Hash = XXHash<Key>, class Equal = std::equal_to<>>
    using PmrDenseMap = ankerl::unordered_dense::pmr::map<Key, Value, Hash, Equal>;

}// namespace CHelper
