//
// Created by Yancey on 2024/2/22.
//

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
