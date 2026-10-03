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

#include <chelper/lexer/TokenReader.h>

namespace CHelper {

    namespace {

        //整数格式: [+-]?[0-9]+
        bool isIntegerFormat(const std::u16string_view &str) {
            size_t i = 0;
            if (i < str.size() && (str[i] == u'+' || str[i] == u'-')) [[likely]] {
                ++i;
            }
            if (i >= str.size()) [[unlikely]] {
                return false;
            }
            for (; i < str.size(); ++i) {
                if (str[i] < u'0' || str[i] > u'9') [[unlikely]] {
                    return false;
                }
            }
            return true;
        }

        //小数格式: [+-]?([0-9]+(\.[0-9]*)?|\.[0-9]+)
        bool isFloatFormat(const std::u16string_view &str) {
            size_t i = 0;
            if (i < str.size() && (str[i] == u'+' || str[i] == u'-')) [[likely]] {
                ++i;
            }
            size_t integerDigits = 0;
            while (i < str.size() && str[i] >= u'0' && str[i] <= u'9') [[likely]] {
                ++i;
                ++integerDigits;
            }
            if (i < str.size() && str[i] == u'.') [[unlikely]] {
                ++i;
                size_t fractionDigits = 0;
                while (i < str.size() && str[i] >= u'0' && str[i] <= u'9') [[likely]] {
                    ++i;
                    ++fractionDigits;
                }
                //"."、"+."、"-"这种只有符号和小数点的不是合法数字
                return integerDigits + fractionDigits > 0 && i == str.size();
            }
            return integerDigits > 0 && i == str.size();
        }

    }// namespace

    TokenReader::TokenReader(const std::shared_ptr<LexerResult> &lexerResult)
        : lexerResult(lexerResult) {
        // 解析分支会反复回退并跳到行尾，预先记录换行，避免重复扫描长命令。
        for (size_t i = 0; i < lexerResult->allTokens.size(); ++i) {
            if (lexerResult->allTokens[i].type == TokenType::LF) {
                lineFeedIndexes.push_back(i);
            }
        }
    }

    void TokenReader::skipToLF() {
        if (ready()) {
            const auto nextLineFeed = std::ranges::lower_bound(lineFeedIndexes, index);
            index = nextLineFeed == lineFeedIndexes.end() ? lexerResult->allTokens.size() : *nextLineFeed;
        }
    }

    ASTNode TokenReader::readSimpleASTNode(Node::NodeWithType node,
                                           TokenType::TokenType type,
                                           std::u16string_view requireType,
                                           const ASTNodeId::ASTNodeId &astNodeId,
                                           std::shared_ptr<ErrorReason> (*check)(const std::u16string_view &str,
                                                                                 const TokensView &tokens)) {
        skipSpace();
        push();
        const Token *token = read();
        TokensView tokens = collect();
        std::shared_ptr<ErrorReason> errorReason;
        if (token == nullptr) [[unlikely]] {
            errorReason = ErrorReason::diagnostic(ErrorReasonLevel::INCOMPLETE, tokens, ErrorReasonCode::RequireType, {requireType});
        } else if (token->type != type) [[unlikely]] {
            errorReason = ErrorReason::diagnostic(ErrorReasonLevel::TYPE_ERROR, tokens, ErrorReasonCode::TypeMismatch, {requireType, TokenType::getName(token->type)});
        } else {
            errorReason = check == nullptr ? nullptr : check(token->content, tokens);
        }
        return ASTNode::simpleNode(node, std::move(tokens), errorReason, astNodeId);
    }

    ASTNode TokenReader::readStringASTNode(const Node::NodeWithType &node,
                                           const ASTNodeId::ASTNodeId &astNodeId) {
        return readSimpleASTNode(node, TokenType::STRING, u"字符串类型", astNodeId);
    }

    ASTNode TokenReader::readIntegerASTNode(const Node::NodeWithType &node,
                                            const ASTNodeId::ASTNodeId &astNodeId) {
        return readSimpleASTNode(
                node, TokenType::NUMBER, u"整数类型", astNodeId,
                [](const std::u16string_view &str, const TokensView &tokens) -> std::shared_ptr<ErrorReason> {
                    if (str.find(u'.') != std::u16string_view::npos) [[unlikely]] {
                        return ErrorReason::diagnostic(ErrorReasonLevel::CONTENT_ERROR,
                                                       tokens, ErrorReasonCode::IntegerRequired);
                    }
                    //lexer会吞掉连续的0-9 . + -，这里必须校验完整的数字格式，防止1-2、1--2这类内容被当成合法数字
                    if (!isIntegerFormat(str)) [[unlikely]] {
                        return ErrorReason::diagnostic(ErrorReasonLevel::CONTENT_ERROR, tokens, ErrorReasonCode::InvalidNumber, {str});
                    }
                    return nullptr;
                });
    }

    ASTNode TokenReader::readFloatASTNode(const Node::NodeWithType &node,
                                          const ASTNodeId::ASTNodeId &astNodeId) {
        return readSimpleASTNode(
                node, TokenType::NUMBER, u"数字类型", astNodeId,
                [](const std::u16string_view &str, const TokensView &tokens) -> std::shared_ptr<ErrorReason> {
                    if (!isFloatFormat(str)) [[unlikely]] {
                        return ErrorReason::diagnostic(ErrorReasonLevel::CONTENT_ERROR, tokens, ErrorReasonCode::InvalidNumber, {str});
                    }
                    return nullptr;
                });
    }

    ASTNode TokenReader::readSymbolASTNode(const Node::NodeWithType &node,
                                           const ASTNodeId::ASTNodeId &astNodeId) {
        return readSimpleASTNode(node, TokenType::SYMBOL, u"符号类型", astNodeId);
    }

    ASTNode TokenReader::readUntilSpace(const Node::NodeWithType &node,
                                        const ASTNodeId::ASTNodeId &astNodeId) {
        push();
        while (ready()) {
            TokenType::TokenType tokenType = peek()->type;
            if (tokenType == TokenType::SPACE || tokenType == TokenType::LF) [[unlikely]] {
                break;
            }
            skip();
        }
        return ASTNode::simpleNode(node, collect(), nullptr, astNodeId);
    }

    ASTNode TokenReader::readStringOrNumberASTNode(const Node::NodeWithType &node,
                                                   const ASTNodeId::ASTNodeId &astNodeId) {
        push();
        while (ready()) {
            TokenType::TokenType tokenType = peek()->type;
            if (tokenType == TokenType::SYMBOL || tokenType == TokenType::SPACE || tokenType == TokenType::LF) [[unlikely]] {
                break;
            }
            skip();
        }
        return ASTNode::simpleNode(node, collect(), nullptr, astNodeId);
    }

}// namespace CHelper
