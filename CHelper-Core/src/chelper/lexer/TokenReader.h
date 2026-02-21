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

#ifndef CHELPER_TOKENREADER_H
#define CHELPER_TOKENREADER_H

#include <chelper/lexer/LexerResult.h>
#include <chelper/lexer/Token.h>
#include <chelper/node/NodeWithType.h>
#include <chelper/parser/ASTNode.h>
#include <chelper/parser/TokensView.h>
#include <pch.h>

namespace CHelper {

    class TokenReader {
    public:
        const std::shared_ptr<LexerResult> lexerResult;
        size_t index = 0;
        std::vector<size_t> indexStack;

        explicit TokenReader(const std::shared_ptr<LexerResult> &lexerResult);

        [[nodiscard]] bool ready() const;

        [[nodiscard]] const Token *peek() const;

        [[nodiscard]] const Token *read();

        [[nodiscard]] const Token *next();

        bool skip();

        size_t skipSpace();

        void skipToLF();

        void push();

        void pop();

        [[nodiscard]] size_t getAndPopLastIndex();

        void restore();

        [[nodiscard]] TokensView collect();

        ASTNode readSimpleASTNode(Node::NodeWithType node,
                                  TokenType::TokenType type,
                                  const std::u16string &requireType,
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

#endif//CHELPER_TOKENREADER_H
