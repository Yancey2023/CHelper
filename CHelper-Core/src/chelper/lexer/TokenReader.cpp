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
        : lexerResult(lexerResult) {}

    bool TokenReader::ready() const {
        return index < lexerResult->allTokens.size();
    }

    const Token *TokenReader::peek() const {
        if (!ready()) [[unlikely]] {
            return nullptr;
        }
        return &lexerResult->allTokens[index];
    }

    const Token *TokenReader::read() {
        const Token *result = peek();
        if (result != nullptr) [[likely]] {
            skip();
        }
        return result;
    }

    const Token *TokenReader::next() {
        skip();
        return peek();
    }

    bool TokenReader::skip() {
        if (!ready()) [[unlikely]] {
            return false;
        }
        index++;
        return true;
    }

    size_t TokenReader::skipSpace() {
        size_t start = index;
        while (ready() && peek()->type == TokenType::SPACE) {
            skip();
        }
        return index - start;
    }

    void TokenReader::skipToLF() {
        while (ready() && peek()->type != TokenType::LF) {
            skip();
        }
    }

    /**
     * 将当前指针加入栈中
     */
    void TokenReader::push() {
        indexStack.push_back(index);
    }

    /**
     * 从栈中移除指针，不恢复指针
     */
    void TokenReader::pop() {
#ifdef CHelperDebug
        if (indexStack.empty()) {
            SPDLOG_ERROR("pop when indexStack is null");
            return;
        }
#endif
        indexStack.pop_back();
    }

    /**
     * 从栈中移除并获取最后指针，不恢复指针
     */
    size_t TokenReader::getAndPopLastIndex() {
        size_t size = indexStack.size();
        if (size == 0) [[unlikely]] {
            return 0;
        }
        size_t result = indexStack[size - 1];
        pop();
        return result;
    }

    /**
     * 从栈中移除指针，恢复指针
     */
    void TokenReader::restore() {
        index = getAndPopLastIndex();
    }

    /**
     * 收集栈中最后一个指针位置到当前指针的token，从栈中移除指针，不恢复指针
     */
    TokensView CHelper::TokenReader::collect() {
        return {lexerResult, getAndPopLastIndex(), index};
    }

    ASTNode TokenReader::readSimpleASTNode(Node::NodeWithType node,
                                           TokenType::TokenType type,
                                           const std::u16string &requireType,
                                           const ASTNodeId::ASTNodeId &astNodeId,
                                           std::shared_ptr<ErrorReason> (*check)(const std::u16string_view &str,
                                                                                 const TokensView &tokens)) {
        skipSpace();
        push();
        const Token *token = read();
        TokensView tokens = collect();
        std::shared_ptr<ErrorReason> errorReason;
        if (token == nullptr) [[unlikely]] {
            errorReason = ErrorReason::incomplete(tokens, fmt::format(u"命令不完整，需要的参数类型为{}", requireType));
        } else if (token->type != type) [[unlikely]] {
            errorReason = ErrorReason::typeError(tokens, fmt::format(u"类型不匹配，正确的参数类型为{}，但当前参数类型为{}", requireType, TokenType::getName(token->type)));
        } else {
            errorReason = check == nullptr ? nullptr : check(token->content, tokens);
        }
        return ASTNode::simpleNode(node, tokens, errorReason, astNodeId);
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
                        return ErrorReason::contentError(
                                tokens, u"类型不匹配，正确的参数类型为整数，但当前参数类型为小数");
                    }
                    //lexer会吞掉连续的0-9 . + -，这里必须校验完整的数字格式，防止1-2、1--2这类内容被当成合法数字
                    if (!isIntegerFormat(str)) [[unlikely]] {
                        return ErrorReason::contentError(tokens, fmt::format(u"数字格式错误 -> {}", str));
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
                        return ErrorReason::contentError(tokens, fmt::format(u"数字格式错误 -> {}", str));
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
        skipSpace(); // 与 readSimpleASTNode 一致：先跳过前导空格，避免空读（如 ", hasitem"）
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
        skipSpace(); // 与 readSimpleASTNode 一致：先跳过前导空格，避免空读（如 ", hasitem"）
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