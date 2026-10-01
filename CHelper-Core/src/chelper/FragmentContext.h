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

#ifndef CHELPER_FRAGMENTCONTEXT_H
#define CHELPER_FRAGMENTCONTEXT_H

#include <chelper/auto_suggestion/Suggestion.h>
#include <chelper/parser/ASTNode.h>
#include <chelper/resources/CPack.h>

namespace CHelper {

    /**
     * 片段补全上下文：不依赖完整命令，直接对"一段独立语法单元"（如目标选择器）做解析与补全。
     * 与 CommandContext 使用同一套 Parser / AutoSuggestion / Linter 管道；
     * 根节点实例由本对象按值持有（对象经 unique_ptr 持有、地址稳定），
     * AST 数据引用 CPack 成员/静态节点，生命周期纪律与 CommandContext 一致（shared_ptr<const CPack> 保活）。
     *
     * 当前支持：
     *   - 目标选择器片段：createTargetSelector("@p[tag=a,x="...)
     *   - ID 表片段：createId("translate", "item.diamond.na")（翻译键等键表补全）
     *
     * 不可拷贝、不可移动。
     */
    class FragmentContext {
    public:
        // 目标选择器片段（rawtext 选择器字段等）
        static std::unique_ptr<FragmentContext> createTargetSelector(
                std::shared_ptr<const CPack> cpack, std::u16string content);

        // ID 表片段：key 引用合成 CPack 的候选表（如 "translate"）
        static std::unique_ptr<FragmentContext> createId(
                std::shared_ptr<const CPack> cpack, std::string key, std::u16string content);

        [[nodiscard]] const CPack &getCPack() const;

        [[nodiscard]] const std::u16string &getContent() const;

        [[nodiscard]] const ASTNode *getAstNode() const;

        [[nodiscard]] std::vector<AutoSuggestion::Suggestion> getSuggestions(size_t index) const;

        [[nodiscard]] std::vector<std::shared_ptr<ErrorReason>> getErrorReasons() const;

        // 应用第 which 个补全建议，返回（新文本, 新光标）；建议不存在返回 std::nullopt
        [[nodiscard]] std::optional<std::pair<std::u16string, size_t>>
        applySuggestion(size_t index, size_t which) const;

    private:
        std::shared_ptr<const CPack> cpack;
        std::u16string content;
        // 根节点实例（按需使用其一）与指向它的 NodeWithType
        Node::NodeTargetSelector selectorNode;
        Node::NodeNormalId idNode;
        Node::NodeWithType root;
        std::unique_ptr<ASTNode> astNode;

        FragmentContext() = default;
    };

}// namespace CHelper

#endif//CHELPER_FRAGMENTCONTEXT_H
