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

#include <chelper/lexer/LexerResult.h>
#include <chelper/lexer/Token.h>
#include <chelper/node/NodeWithType.h>
#include <chelper/parser/ASTNode.h>
#include <chelper/parser/TokensView.h>
#include <chelper/util/IdMatchCache.h>
#include <pch.h>

namespace CHelper {

    class TokenReader {
    private:
        std::pmr::vector<size_t> lineFeedIndexes;

    public:
        const std::shared_ptr<LexerResult> lexerResult;
        size_t index = 0;
        std::pmr::vector<size_t> indexStack;
        IdMatchCache idMatches;

        explicit TokenReader(const std::shared_ptr<LexerResult> &lexerResult);

        [[nodiscard]] bool ready() const {
            return index < lexerResult->allTokens.size();
        }

        [[nodiscard]] const Token *peek() const {
            if (!ready()) [[unlikely]] {
                return nullptr;
            }
            return &lexerResult->allTokens[index];
        }

        [[nodiscard]] const Token *read() {
            const Token *result = peek();
            if (result != nullptr) [[likely]] {
                skip();
            }
            return result;
        }

        [[nodiscard]] const Token *next() {
            skip();
            return peek();
        }

        bool skip() {
            if (!ready()) [[unlikely]] {
                return false;
            }
            index++;
            return true;
        }

        size_t skipSpace() {
            size_t start = index;
            while (ready() && peek()->type == TokenType::SPACE) {
                skip();
            }
            return index - start;
        }

        // 读取一个非空格 token 的视图，不改变用于语法分支回溯的 indexStack。
        [[nodiscard]] TokensView readTokenView() {
            skipSpace();
            const size_t start = index;
            skip();
            return {lexerResult, start, index};
        }

        void skipToLF();

        // 将当前指针加入栈中。
        void push() {
            indexStack.push_back(index);
        }

        // 从栈中移除指针，不恢复指针。
        void pop() {
#if CHelperDebug
            if (indexStack.empty()) {
                SPDLOG_ERROR("pop when indexStack is null");
                return;
            }
#endif
            indexStack.pop_back();
        }

        // 从栈中移除并获取最后指针，不恢复指针。
        [[nodiscard]] size_t getAndPopLastIndex() {
            size_t size = indexStack.size();
            if (size == 0) [[unlikely]] {
                return 0;
            }
            size_t result = indexStack[size - 1];
            pop();
            return result;
        }

        // 从栈中移除指针，恢复指针。
        void restore() {
            index = getAndPopLastIndex();
        }

        // 收集栈中最后指针到当前指针的 token，并移除栈中指针。
        [[nodiscard]] TokensView collect() {
            return {lexerResult, getAndPopLastIndex(), index};
        }

        ASTNode readSimpleASTNode(Node::NodeWithType node,
                                  TokenType::TokenType type,
                                  std::u16string_view requireType,
                                  const ASTNodeId::ASTNodeId &astNodeId = ASTNodeId::NONE,
                                  std::shared_ptr<ErrorReason> (*check)(const std::u16string_view &str,
                                                                        const TokensView &tokens) = nullptr);

        ASTNode readStringASTNode(const Node::NodeWithType &node,
                                  const ASTNodeId::ASTNodeId &astNodeId = ASTNodeId::NONE);

        ASTNode readIntegerASTNode(const Node::NodeWithType &node,
                                   const ASTNodeId::ASTNodeId &astNodeId = ASTNodeId::NONE);

        ASTNode readFloatASTNode(const Node::NodeWithType &node,
                                 const ASTNodeId::ASTNodeId &astNodeId = ASTNodeId::NONE);

        ASTNode readSymbolASTNode(const Node::NodeWithType &node,
                                  const ASTNodeId::ASTNodeId &astNodeId = ASTNodeId::NONE);

        ASTNode readUntilSpace(const Node::NodeWithType &node,
                               const ASTNodeId::ASTNodeId &astNodeId = ASTNodeId::NONE);

        ASTNode readStringOrNumberASTNode(const Node::NodeWithType &node,
                                          const ASTNodeId::ASTNodeId &astNodeId = ASTNodeId::NONE);
    };

}// namespace CHelper
