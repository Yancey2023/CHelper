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

#ifndef CHELPER_TYPELIST_H
#define CHELPER_TYPELIST_H

#include <cstddef>
#include <type_traits>
#include <utility>

namespace CHelper::Meta {

    //编译期类型列表，用于把"一组类型"作为单一实体做遍历与判断
    template<class... Ts>
    struct TypeList {};

    template<class List>
    inline constexpr std::size_t typeListSize = 0;

    template<class... Ts>
    inline constexpr std::size_t typeListSize<TypeList<Ts...>> = sizeof...(Ts);

    //列表中是否存在指定类型
    template<class T, class List>
    inline constexpr bool typeListContains = false;

    template<class T, class... Ts>
    inline constexpr bool typeListContains<T, TypeList<Ts...>> = (... || std::is_same_v<T, Ts>);

    //对 List 中每个类型 T 依次调用一次 f.template operator()<T>()，顺序为列表声明顺序，不短路
    template<class List, class F>
    constexpr void forEachType(F &&f) {
        []<class... Ts>(TypeList<Ts...>, F &&fn) {
            (std::forward<F>(fn).template operator()<Ts>(), ...);
        }(List{}, std::forward<F>(f));
    }

    //对 List 中每个类型 T 依次调用 f.template operator()<T>()，返回 true 时短路停止；
    //返回是否发生过命中
    template<class List, class F>
    constexpr bool anyType(F &&f) {
        return []<class... Ts>(TypeList<Ts...>, F &&fn) {
            return (... || std::forward<F>(fn).template operator()<Ts>());
        }(List{}, std::forward<F>(f));
    }

}// namespace CHelper::Meta

#endif//CHELPER_TYPELIST_H
