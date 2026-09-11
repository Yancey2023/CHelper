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

#ifndef CHELPER_COMMANDCONTEXT_H
#define CHELPER_COMMANDCONTEXT_H

#include <chelper/auto_suggestion/Suggestion.h>
#include <chelper/parser/ASTNode.h>
#include <chelper/resources/CPack.h>
#include <chelper/syntax_highlight/SyntaxResult.h>
#include <pch.h>

namespace CHelper {

    class CommandContextMemoryResource final : public std::pmr::memory_resource {
    private:
        std::pmr::unsynchronized_pool_resource resource;

        void *do_allocate(const size_t bytes, const size_t alignment) override {
            return resource.allocate(bytes, alignment);
        }

        void do_deallocate(void *pointer, const size_t bytes, const size_t alignment) noexcept override {
            resource.deallocate(pointer, bytes, alignment);
        }

        [[nodiscard]] bool do_is_equal(const std::pmr::memory_resource &other) const noexcept override {
            return this == &other;
        }

    public:
        CommandContextMemoryResource()
            : resource({}, std::pmr::new_delete_resource()) {
            CPackMemoryRouter::install();
            std::pmr::memory_resource *previous = CPackMemoryRouter::getCurrent();
            CPackMemoryRouter::setCurrent(nullptr);
            Node::initializeStaticNodes();
            CPackMemoryRouter::setCurrent(previous);
        }

        [[nodiscard]] std::pmr::memory_resource *getResource() noexcept {
            return this;
        }
    };

    class CommandContextMemoryScope {
    private:
        std::pmr::memory_resource *resource;
        std::pmr::memory_resource *restore = nullptr;
        size_t depth = 0;
        bool active = false;

    public:
        CommandContextMemoryScope(std::pmr::memory_resource *resource,
                                  std::pmr::memory_resource *restore)
            : resource(resource), restore(restore), active(true) {
            CPackMemoryRouter::install();
            depth = CPackMemoryRouter::enter(resource);
        }

        void release() noexcept {
            if (active) {
                CPackMemoryRouter::leave(depth, restore);
                if (restore != nullptr) {
                    CPackMemoryRouter::setCurrent(nullptr);
                }
                active = false;
            }
        }

        void prepareForDestruction() noexcept {
            if (!active) {
                CPackMemoryRouter::install();
                depth = CPackMemoryRouter::enter(resource);
                active = true;
            }
        }

        ~CommandContextMemoryScope() {
            if (active) {
                CPackMemoryRouter::leave(depth, restore);
            }
        }

        CommandContextMemoryScope(const CommandContextMemoryScope &) = delete;
        CommandContextMemoryScope &operator=(const CommandContextMemoryScope &) = delete;
    };

    /**
     * 命令上下文，持有某条命令解析好的AST
     * 与CHelperCore不同，CommandContext不保存光标等可变状态，
     * 所有操作都是只读的，位置信息由调用方通过参数传入
     * 因此：
     * 1. 同一个CommandContext可以被多个线程同时读取
     * 2. 多个CommandContext可以共享同一个CPack并行工作，适合下游多线程场景
     *
     * 必须用shared_ptr持有CPack而不能用裸指针或引用借用：
     * AST节点内部直接持有指向CPack数据的指针（如NodeCommand::commands、
     * NodeRepeat::repeatData），CPack必须活得和引用它的CommandContext一样久。
     * CPack的生命周期由内核和所有上下文共同决定，共享所有权是最直接的表达；
     * const限定保证共享之后不会被任何一方修改，这是多线程并发读取安全的前提
     */
    class CommandContext {
    private:
        std::shared_ptr<const CPack> cpack;
        CommandContextMemoryResource memory;
        CommandContextMemoryScope memoryScope;
        std::pmr::u16string command;
        ASTNode astNode;

    public:
        /**
         * 解析命令文本并生成AST
         * @param cpack   共享的资源包
         * @param command 命令文本
         */
        CommandContext(std::shared_ptr<const CPack> cpack, std::u16string command);

        ~CommandContext();

        [[nodiscard]] const CPack &getCPack() const;

        /**
         * 获取这个上下文对应的命令文本
         */
        [[nodiscard]] std::u16string_view getCommand() const;

        /**
         * 获取解析好的AST
         */
        [[nodiscard]] const ASTNode *getAstNode() const;

        /**
         * 获取命令结构
         */
        [[nodiscard]] std::u16string getStructure() const;

        /**
         * 获取指定位置的参数注释
         * @param index 光标位置
         */
        [[nodiscard]] std::u16string getParamHint(size_t index) const;

        /**
         * 获取指定位置的补全建议
         * @param index 光标位置
         */
        [[nodiscard]] std::vector<AutoSuggestion::Suggestion> getSuggestions(size_t index) const;

        /**
         * 获取语法高亮结果
         */
        [[nodiscard]] SyntaxHighlight::SyntaxResult getSyntaxResult() const;

        /**
         * 获取命令的错误原因
         */
        [[nodiscard]] std::vector<std::shared_ptr<ErrorReason>> getErrorReasons() const;

        /**
         * 获取最佳解析路径中已经匹配的命令语义节点数量
         */
        [[nodiscard]] size_t getNodeCount() const;

        /**
         * 把指定位置的第which个补全建议应用到命令文本
         * 和CHelperCore::onSuggestionClick不同，这个函数不会修改自身的状态
         * @param index 计算补全建议时的光标位置
         * @param which 第几个补全建议，从0开始
         * @return 应用后的新命令文本和新的光标位置，补全建议不存在时返回std::nullopt
         */
        [[nodiscard]] std::optional<std::pair<std::u16string, size_t>>
        applySuggestion(size_t index, size_t which) const;
    };

    /**
     * 统计最佳解析路径中已经匹配的命令语义节点数量
     * 供CommandContext和CHelperCore共同使用
     */
    [[nodiscard]] size_t countSemanticNodes(const ASTNode &astNode);

}// namespace CHelper

#endif//CHELPER_COMMANDCONTEXT_H
