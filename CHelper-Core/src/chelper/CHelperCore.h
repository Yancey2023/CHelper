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

#include "old2new/Old2New.h"
#include <chelper/CommandContext.h>
#include <chelper/resources/CPack.h>
#include <concepts>
#include <functional>
#include <pch.h>

namespace CHelper {

    /**
     * 软件内核，负责持有资源包(CPack)
     * 所有和命令相关的功能都在CommandContext上执行：
     * 通过createContext把命令文本解析成AST生成命令上下文，
     * 然后在CommandContext上获取命令结构、参数注释、补全建议、语法高亮等
     * CHelperCore本身没有可变状态，可以被多个线程同时使用
     */
    class CHelperCore {
    private:
        // 使用shared_ptr持有资源包，这样CHelperCore创建的CommandContext
        // 可以共享资源包，且CommandContext的生命周期可以独立于CHelperCore
        std::shared_ptr<const CPack> cpack;

    public:
        explicit CHelperCore(std::shared_ptr<const CPack> cpack);

        template<class Provider>
            requires requires(Provider &getCPack) {
                { std::invoke(getCPack) } -> std::convertible_to<std::unique_ptr<CPack>>;
            }
        static CHelperCore *create(Provider &&getCPack) {
            try {
#if SPDLOG_ACTIVE_LEVEL <= SPDLOG_LEVEL_INFO
                const auto start = std::chrono::high_resolution_clock::now();
#endif
                std::unique_ptr<CPack> cPack = std::invoke(getCPack);
#if SPDLOG_ACTIVE_LEVEL <= SPDLOG_LEVEL_INFO
                const auto end = std::chrono::high_resolution_clock::now();
#endif
                //provider返回nullptr说明资源包加载失败，不允许产生内部cpack为nullptr的CHelperCore，
                //否则后续getCPack()/createContext()会解引用空指针
                if (cPack == nullptr) [[unlikely]] {
                    throw std::runtime_error("getCPack returned nullptr");
                }
                SPDLOG_INFO("CPack load successfully ({})", FORMAT_ARG(std::chrono::duration_cast<std::chrono::milliseconds>(end - start)));
                return new CHelperCore(std::move(cPack));
            } catch ([[maybe_unused]] const std::exception &e) {
                //CPackLoadError 的 what() 已内嵌原始错误与完整加载轨迹，整体打印给资源包作者
                SPDLOG_ERROR("{}", e.what());
                return nullptr;
            }
        }

#ifndef CHELPER_NO_FILESYSTEM
        static CHelperCore *createByDirectory(const std::filesystem::path &cpackPath);

        static CHelperCore *createByJson(const std::filesystem::path &cpackPath);

        static CHelperCore *createByBinary(const std::filesystem::path &cpackPath);
#endif

        static CHelperCore *createByBinary(std::string_view data);

        [[nodiscard]] const CPack &getCPack() const;

        /**
         * 把命令文本解析成AST，生成独立的命令上下文
         * 适用于下游多线程并行的场景：
         * 同一个CHelperCore可以创建任意多个CommandContext，
         * 这些CommandContext可以在不同线程中同时使用
         * @param command 命令文本
         * @return 创建的CommandContext指针，用完后需要用deleteContext释放
         */
        [[nodiscard]] CommandContext *createContext(std::u16string_view command) const;

        /**
         * 释放createContext创建的命令上下文
         * @param context 命令上下文，为nullptr时什么也不做
         */
        static void deleteContext(CommandContext *context);

        static std::u16string old2new(const Old2New::BlockFixData &blockFixData, std::u16string old);
    };

}// namespace CHelper
