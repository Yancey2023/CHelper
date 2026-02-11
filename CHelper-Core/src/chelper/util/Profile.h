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

#ifndef CHELPER_PROFILE_H
#define CHELPER_PROFILE_H

/**
 * 跟踪代码的运行，为了在遇到bug的时候方便排查错误的位置
 */
namespace CHelper::Profile {

#ifndef CHELPER_NO_FILESYSTEM
    extern std::vector<std::string> stack;
#endif

    template<typename... T>
    void push(const fmt::format_string<T...> fmt, T &&...args) {
#ifndef CHELPER_NO_FILESYSTEM
        stack.push_back(fmt::vformat(fmt.str, fmt::vargs<T...>{{args...}}));
#endif
    }

    void pop();

    template<typename... T>
    void next(const fmt::format_string<T...> fmt, T &&...args) {
#ifndef CHELPER_NO_FILESYSTEM
        pop();
        stack.push_back(fmt::vformat(fmt.str, fmt::vargs<T...>{{args...}}));
#endif
    }

    void clear();

    void printAndClear(const std::exception &e);

}// namespace CHelper::Profile

#endif//CHELPER_PROFILE_H
