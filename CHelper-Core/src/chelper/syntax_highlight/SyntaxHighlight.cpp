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

#include <array>
#include <chelper/node/NodeType.h>
#include <chelper/syntax_highlight/SyntaxHighlight.h>
#include <chelper/util/JsonUtil.h>

namespace CHelper::SyntaxHighlight {

    void fillSyntaxResult(const ASTNode &astNode, SyntaxResultView &syntaxResult);

    template<class NodeType>
    struct SyntaxToken {
        static bool collectSyntax(const ASTNode &astNode, SyntaxResultView &syntaxResult) {
            return false;
        }
    };

    template<>
    struct SyntaxToken<Node::NodeJsonNull> {
        static bool collectSyntax(const ASTNode &astNode, SyntaxResultView &syntaxResult) {
            syntaxResult.update(astNode.tokens, SyntaxTokenType::NULL_TOKEN);
            return true;
        }
    };

    template<>
    struct SyntaxToken<Node::NodeJsonString> {
        static bool collectSyntax(const ASTNode &astNode, SyntaxResultView &syntaxResult) {
            if (astNode.id != ASTNodeId::NODE_STRING_INNER) {
                syntaxResult.update(astNode.tokens, SyntaxTokenType::STRING);
                return false;
            }
            syntaxResult.update(astNode.tokens.startIndex, SyntaxTokenType::STRING);
            std::u16string_view str = astNode.tokens.string();
            auto convertResult = JsonUtil::DecodedStringView(str);
            if (convertResult.isComplete) {
                syntaxResult.update(astNode.tokens.endIndex - 1, SyntaxTokenType::STRING);
            }
            if (convertResult.hasDirectMapping()) {
                // 未转义内容的坐标仅差一个引号，直接写入最终结果的对应区间。
                // 保持原来的内层 UNKNOWN 初始化和独立括号深度。
                auto target = syntaxResult.tokenTypes.subspan(astNode.tokens.startIndex + 1, convertResult.string().size());
                std::ranges::fill(target, SyntaxTokenType::UNKNOWN);
                SyntaxResultView childResult(astNode.childNodes[0].tokens.lexerResult->content, target);
                fillSyntaxResult(astNode.childNodes[0], childResult);
                return true;
            }
            SyntaxResult syntaxResult1 = getSyntaxResult(astNode.childNodes[0]);
            size_t start = convertResult.convert(0);
            for (size_t i = 0; i < convertResult.string().size(); ++i) {
                size_t end = convertResult.convert(i + 1);
                syntaxResult.update(astNode.tokens.startIndex + start, astNode.tokens.startIndex + end, syntaxResult1.tokenTypes[i]);
                start = end;
            }
            return true;
        }
    };

    template<>
    struct SyntaxToken<Node::NodeCommand> {
        static bool collectSyntax(const ASTNode &astNode, SyntaxResultView &syntaxResult) {
            if (astNode.id == ASTNodeId::NODE_COMMAND_COMMAND_NAME) [[likely]] {
                syntaxResult.update(astNode.tokens, SyntaxTokenType::COMMAND);
                return true;
            }
            return false;
        }
    };

    template<>
    struct SyntaxToken<Node::NodeCommandName> {
        static bool collectSyntax(const ASTNode &astNode, SyntaxResultView &syntaxResult) {
            syntaxResult.update(astNode.tokens, SyntaxTokenType::ID);
            return true;
        }
    };

    template<>
    struct SyntaxToken<Node::NodeIntegerWithUnit> {
        static bool collectSyntax(const ASTNode &astNode, SyntaxResultView &syntaxResult) {
            syntaxResult.update(astNode.tokens, SyntaxTokenType::INTEGER);
            return true;
        }
    };

    template<>
    struct SyntaxToken<Node::NodeNamespaceId> {
        static bool collectSyntax(const ASTNode &astNode, SyntaxResultView &syntaxResult) {
            syntaxResult.update(astNode.tokens, SyntaxTokenType::ID);
            return true;
        }
    };

    template<>
    struct SyntaxToken<Node::NodeNormalId> {
        static bool collectSyntax(const ASTNode &astNode, SyntaxResultView &syntaxResult) {
            const auto &node = *reinterpret_cast<const Node::NodeNormalId *>(astNode.node.data);
            if (node.key.has_value()) {
                syntaxResult.update(astNode.tokens, SyntaxTokenType::ID);
            } else if (node.id != "TARGET_SELECTOR_VARIABLE") {
                syntaxResult.update(astNode.tokens, SyntaxTokenType::LITERAL);
            } else {
                syntaxResult.update(astNode.tokens, SyntaxTokenType::TARGET_SELECTOR);
            }
            return true;
        }
    };

    template<>
    struct SyntaxToken<Node::NodePosition> {
        static bool collectSyntax(const ASTNode &astNode, SyntaxResultView &syntaxResult) {
            if (astNode.id == ASTNodeId::NODE_RELATIVE_FLOAT_NUMBER) {
                syntaxResult.update(astNode.tokens, SyntaxTokenType::FLOAT);
                return true;
            } else {
                return false;
            }
        }
    };

    template<>
    struct SyntaxToken<Node::NodeRange> {
        static bool collectSyntax(const ASTNode &astNode, SyntaxResultView &syntaxResult) {
            std::u16string_view str = astNode.tokens.string();
            for (size_t i = 0; i < str.length(); ++i) {
                size_t ch = str[i];
                syntaxResult.update(
                        astNode.tokens.startIndex + i,
                        (ch < '0' || ch > '9') && ch != '-' && ch != '+' ? SyntaxTokenType::RANGE : SyntaxTokenType::INTEGER);
            }
            return true;
        }
    };

    template<>
    struct SyntaxToken<Node::NodeRelativeFloat> {
        static bool collectSyntax(const ASTNode &astNode, SyntaxResultView &syntaxResult) {
            if (astNode.id == ASTNodeId::NODE_RELATIVE_FLOAT_NUMBER) {
                syntaxResult.update(astNode.tokens, SyntaxTokenType::FLOAT);
                return true;
            } else {
                return false;
            }
        }
    };

    template<>
    struct SyntaxToken<Node::NodeString> {
        static bool collectSyntax(const ASTNode &astNode, SyntaxResultView &syntaxResult) {
            syntaxResult.update(astNode.tokens, SyntaxTokenType::STRING);
            return true;
        }
    };

    template<>
    struct SyntaxToken<Node::NodeText> {
        static bool collectSyntax(const ASTNode &astNode, SyntaxResultView &syntaxResult) {
            const auto &node = *reinterpret_cast<const Node::NodeText *>(astNode.node.data);
            if (node.id != "TARGET_SELECTOR_ARGUMENT_EQUAL" && node.id != "TARGET_SELECTOR_ARGUMENT_NOT_EQUAL") {
                syntaxResult.update(astNode.tokens, SyntaxTokenType::LITERAL);
            } else {
                syntaxResult.update(astNode.tokens, SyntaxTokenType::SYMBOL);
            }
            return true;
        }
    };

    template<>
    struct SyntaxToken<Node::NodeSingleSymbol> {
        static bool collectSyntax(const ASTNode &astNode, SyntaxResultView &syntaxResult) {
            syntaxResult.update(astNode.tokens, SyntaxTokenType::SYMBOL);
            return true;
        }
    };

    template<bool isJson>
    struct SyntaxToken<Node::NodeTemplateBoolean<isJson>> {
        static bool collectSyntax(const ASTNode &astNode, SyntaxResultView &syntaxResult) {
            syntaxResult.update(astNode.tokens, SyntaxTokenType::BOOLEAN);
            return true;
        }
    };

    template<class T, bool isJson>
    struct SyntaxToken<Node::NodeTemplateNumber<T, isJson>> {
        static bool collectSyntax(const ASTNode &astNode, SyntaxResultView &syntaxResult) {
            if constexpr (std::numeric_limits<T>::is_integer) {
                syntaxResult.update(astNode.tokens, SyntaxTokenType::INTEGER);
            } else {
                syntaxResult.update(astNode.tokens, SyntaxTokenType::FLOAT);
            }
            return true;
        }
    };

    void collectSyntaxResult(const ASTNode &astNode, SyntaxResultView &syntaxResult) {
        bool isDirty = Node::dispatchNodeType(astNode.node.nodeTypeId, [&]<class NodeType>() {
            return SyntaxToken<NodeType>::collectSyntax(astNode, syntaxResult);
        });
        if (isDirty) [[unlikely]] {
            return;
        }
        switch (astNode.mode) {
            case ASTNodeMode::NONE:
                return;
            case ASTNodeMode::AND:
                for (const ASTNode &item: astNode.childNodes) {
                    collectSyntaxResult(item, syntaxResult);
                }
                break;
            case ASTNodeMode::OR:
                collectSyntaxResult(astNode.childNodes[astNode.whichBest], syntaxResult);
                break;
        }
    }

    void fillSyntaxResult(const ASTNode &astNode, SyntaxResultView &syntaxResult) {
        collectSyntaxResult(astNode, syntaxResult);
        std::array<char16_t, 32> brackets;
        std::vector<char16_t> overflow;
        size_t depth = 0;
        astNode.tokens.forEach([&](const Token &token) {
            if (token.type != TokenType::SYMBOL || token.content.empty()) [[likely]] {
                return;
            }
            char16_t ch = token.content[0];
            switch (ch) {
                case '[':
                case '{': {
                    switch (depth % 3) {
                        case 0:
                            syntaxResult.update(token.pos, SyntaxTokenType::BRACKET1);
                            break;
                        case 1:
                            syntaxResult.update(token.pos, SyntaxTokenType::BRACKET2);
                            break;
                        case 2:
                            syntaxResult.update(token.pos, SyntaxTokenType::BRACKET3);
                            break;
                        default:
                            CHELPER_UNREACHABLE();
                    }
                    if (depth < brackets.size()) {
                        brackets[depth] = ch;
                    } else {
                        overflow.push_back(ch);
                    }
                    ++depth;
                } break;
                case ']':
                case '}': {
                    if (depth == 0) break;
                    const char16_t opening = depth <= brackets.size() ? brackets[depth - 1] : overflow.back();
                    if (!((opening == '[' && ch == ']') || (opening == '{' && ch == '}'))) {
                        break;
                    }
                    switch ((depth - 1) % 3) {
                        case 0:
                            syntaxResult.update(token.pos, SyntaxTokenType::BRACKET1);
                            break;
                        case 1:
                            syntaxResult.update(token.pos, SyntaxTokenType::BRACKET2);
                            break;
                        case 2:
                            syntaxResult.update(token.pos, SyntaxTokenType::BRACKET3);
                            break;
                        default:
                            CHELPER_UNREACHABLE();
                    }
                    if (depth > brackets.size()) overflow.pop_back();
                    --depth;
                } break;
                default:
                    break;
            }
        });
    }

    SyntaxResult getSyntaxResult(const ASTNode &astNode) {
        SyntaxResult result(astNode.tokens.lexerResult->content);
        auto view = result.view();
        fillSyntaxResult(astNode, view);
        return result;
    }

}// namespace CHelper::SyntaxHighlight
