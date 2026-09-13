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

#include <exception>
#include <fmt/format.h>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

namespace CHelper {

    /**
     * 资源包加载轨迹：记录一次加载过程"当前正在处理"的元素身份链（文件 -> grammar ->
     * 命令 -> ...），用于给资源包作者输出可读性强的报错信息。
     *
     * 轨迹由每次加载操作（createCPackByDirectory/Json/Binary）局部拥有并显式传递，
     * 不存在任何跨操作共享的状态，因此多线程并发创建 CPack 天然安全；
     * 只有遇到异常时才在 API 边界把整条轨迹格式化进 CPackLoadError，成功路径
     * 只有少量字符串条目的开销。
     */
    struct LoadTrail {
        std::vector<std::string> entries;

        void push(std::string_view label) {
            entries.emplace_back(label);
        }

        template<typename... Args>
        void push(const fmt::format_string<Args...> fmt, Args &&...args) {
            entries.push_back(fmt::vformat(fmt.str, fmt::vargs<Args...>{{args...}}));
        }

        /**
         * 作用域条目：构造时压入，所在作用域正常结束时自动弹出，异常展开时保留。
         * 因此轨迹在任何时刻都恰好等于"当前调用链"：同层级的兄弟元素依次进出、
         * 只留最后一次；嵌套调用形成完整链路；抛出异常的那一层保留到最后。
         * 这取代了旧 Profile 需要手工 push/pop 配对的脆弱写法。
         */
        class Scope {
        public:
            Scope(LoadTrail &trail, std::string_view label)
                : trail_(trail), uncaughtOnEntry_(std::uncaught_exceptions()) {
                trail_.push(label);
            }

            template<typename... Args>
            Scope(LoadTrail &trail, const fmt::format_string<Args...> fmt, Args &&...args)
                : trail_(trail), uncaughtOnEntry_(std::uncaught_exceptions()) {
                trail_.push(fmt, std::forward<Args>(args)...);
            }

            ~Scope() {
                //退出时若无异常在传播，说明该元素处理完成，弹出条目；
                //有异常在传播则保留，让轨迹记录到出错位置
                if (uncaughtOnEntry_ == std::uncaught_exceptions()) {
                    trail_.entries.pop_back();
                }
            }

            Scope(const Scope &) = delete;
            Scope &operator=(const Scope &) = delete;
            Scope(Scope &&) = delete;
            Scope &operator=(Scope &&) = delete;

        private:
            LoadTrail &trail_;
            int uncaughtOnEntry_;
        };
    };

    /**
     * 资源包加载失败异常：message 内嵌原始错误与完整加载轨迹。
     * 继承 std::runtime_error，现有的 catch 与校验逻辑无需改动。
     */
    struct CPackLoadError : std::runtime_error {
        CPackLoadError(const std::exception &e, const LoadTrail &trail)
            : std::runtime_error(fmt::format("{}\nload trail:\n  - {}", e.what(), fmt::join(trail.entries, "\n  - "))) {}
    };

}// namespace CHelper
