//
// Created by Yancey on 2024/2/13.
//

#pragma once

#ifndef CHELPER_STRINGUTIL_H
#define CHELPER_STRINGUTIL_H

#include <pch.h>

namespace CHelper::StringUtil {

    template<typename It, typename Sentinel, typename Char>
    auto join(It begin, Sentinel end, fmt::basic_string_view<Char> sep)
            -> fmt::join_view<It, Sentinel, Char> {
        return {begin, end, sep};
    }

    template<typename It, typename Sentinel, typename Char>
    auto join(It begin, Sentinel end, const Char *sep)
            -> fmt::join_view<It, Sentinel, Char> {
        return {begin, end, fmt::basic_string_view<Char>(sep)};
    }

    template<typename Range, typename Char, FMT_ENABLE_IF(!fmt::is_tuple_like<Range>::value)>
    auto join(Range &&range, fmt::basic_string_view<Char> sep)
            -> fmt::join_view<decltype(std::begin(range)), decltype(std::end(range)), Char> {
        return join(std::begin(range), std::end(range), fmt::basic_string_view<Char>(sep));
    }

    template<typename Range, typename Char, FMT_ENABLE_IF(!fmt::is_tuple_like<Range>::value)>
    auto join(Range &&range, const Char *sep)
            -> fmt::join_view<decltype(std::begin(range)), decltype(std::end(range)), Char> {
        return join(std::begin(range), std::end(range), fmt::basic_string_view<Char>(sep));
    }

    template<typename T, typename Char>
    auto join(std::initializer_list<T> list, fmt::basic_string_view<Char> sep)
            -> fmt::join_view<const T *, const T *, Char> {
        return join(std::begin(list), std::end(list), sep);
    }

    template<typename T, typename Char>
    auto join(std::initializer_list<T> list, const Char *sep)
            -> fmt::join_view<const T *, const T *, Char> {
        return join(std::begin(list), fmt::basic_string_view<Char>(std::end(list), sep));
    }

    template<typename Tuple, typename Char, FMT_ENABLE_IF(fmt::is_tuple_like<Tuple>::value)>
    auto join(const Tuple &tuple, fmt::basic_string_view<Char> sep)
            -> fmt::tuple_join_view<Tuple, Char> {
        return {tuple, sep};
    }

    template<typename Tuple, typename Char, FMT_ENABLE_IF(fmt::is_tuple_like<Tuple>::value)>
    auto join(const Tuple &tuple, const Char *sep)
            -> fmt::tuple_join_view<Tuple, Char> {
        return {tuple, fmt::basic_string_view<Char>(sep)};
    }

}// namespace CHelper::StringUtil

#endif//CHELPER_STRINGUTIL_H