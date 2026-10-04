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

#include "CpackTestHelper.h"
#include <chelper/node/CommandNode.h>
#include <chelper/parser/ASTNode.h>
#include <chelper/parser/Parser.h>
#include <chelper/resources/id/NormalId.h>
#include <chelper/serialization/Serialization.h>
#include <gtest/gtest.h>

namespace CHelper::Test {

    TEST(ParserTest, EntryStopsAtTheFirstErrorAndPreservesEachChildSpan) {
        Node::NodeJsonString text;
        Node::NodeSingleSymbol separator(u':', u"冒号");
        Node::NodeEntry entry(text, separator, text);
        struct Case {
            std::u16string_view input;
            size_t children;
            bool error;
            size_t end;
        };
        const Case cases[] = {
                {u"123:ignored", 1, true, 3},
                {u"\"key\"=ignored", 2, true, 6},
                {u"\"key\"", 2, true, 5},
                {u"\"key\":123", 3, true, 9},
                {u"\"key\":", 3, true, 6},
                {u"\"key\":\"value\" trailing", 3, false, 13},
        };
        for (const auto &item: cases) {
            SCOPED_TRACE(utf8::utf16to8(item.input));
            const auto ast = Parser::parse(item.input, entry);
            EXPECT_EQ(ast.isError(), item.error);
            EXPECT_EQ(ast.tokens.endIndex, item.end);
            ASSERT_EQ(ast.childNodes.size(), item.children);
            EXPECT_EQ(ast.childNodes.front().tokens.startIndex, 0u);
            EXPECT_EQ(ast.childNodes.back().tokens.endIndex, item.end);
            for (size_t i = 1; i < item.children; ++i) {
                EXPECT_EQ(ast.childNodes[i - 1].tokens.endIndex, ast.childNodes[i].tokens.startIndex);
            }
        }
    }

    TEST(ParserTest, OrBranchEndpointsRestoreTheSelectedCursorForSmallAndLargeChoices) {
        Node::NodeSingleSymbol other(u'[', u"其他分支"), selected(u']', u"选中分支"), next(u'}', u"后续符号");
        for (const size_t count: {1u, 2u, 4u, 5u, 8u}) {
            for (const bool useFirst: {false, true}) {
                for (const bool selectFirst: {false, true}) {
                    std::pmr::vector<Node::NodeWithType> choices(count, other);
                    const size_t best = selectFirst ? 0 : count - 1;
                    choices[best] = selected;
                    Node::NodeOr branch(std::move(choices), false, useFirst);
                    Node::NodeAnd sequence({branch, next});
                    const auto ast = Parser::parse(u"]} trailing", sequence);
                    EXPECT_FALSE(ast.isError());
                    EXPECT_EQ(ast.tokens.string(), u"]}");
                    ASSERT_EQ(ast.childNodes.size(), 2u);
                    EXPECT_EQ(ast.childNodes[0].whichBest, best);
                    EXPECT_EQ(ast.childNodes[1].tokens.string(), u"}");
                    EXPECT_EQ(ast.childNodes[1].tokens.startIndex, 1u);
                }
            }
        }
    }

    TEST(ParserTest, ListTerminationChecksRightBranchEvenWhenAnotherBranchSucceeds) {
        Node::NodeSingleSymbol left(u'[', u"左括号"), comma(u',', u"分隔符"), right(u']', u"右括号");
        Node::NodeJsonString string;
        Node::NodeList list(left, string, comma, right);
        for (const std::u16string_view input: {u"[] trailing", u"[\"a\"] trailing", u"[\"a\",\"b\"] trailing", u"[ \"a\" , \"b\" ] trailing"}) {
            const auto ast = Parser::parse(input, list);
            EXPECT_FALSE(ast.isError()) << utf8::utf16to8(input);
            EXPECT_EQ(ast.tokens.endIndex, input.find(u']') + 1);
            const auto &closing = ast.childNodes.back();
            ASSERT_EQ(closing.childNodes.size(), 2u);
            EXPECT_FALSE(closing.childNodes[1].isError());
        }
        for (const std::u16string_view input: {u"[", u"[\"a\"", u"[\"a\",", u"[\"a\",bad]"}) {
            const auto ast = Parser::parse(input, list);
            EXPECT_TRUE(ast.isError()) << utf8::utf16to8(input);
            for (const auto &error: ast.errorReasons) {
                EXPECT_LE(error->start, error->end);
                EXPECT_LE(error->end, input.size());
            }
        }
        Node::NodeList ambiguousElement(left, right, comma, right);
        const auto empty = Parser::parse(u"[] trailing", ambiguousElement);
        EXPECT_FALSE(empty.isError());
        EXPECT_EQ(empty.tokens.string(), u"[]");
        ASSERT_EQ(empty.childNodes.size(), 2u);
        EXPECT_EQ(empty.childNodes.back().whichBest, 0u);
        Node::NodeList ambiguousSeparator(left, string, right, right);
        const auto single = Parser::parse(u"[\"a\"] trailing", ambiguousSeparator);
        EXPECT_FALSE(single.isError());
        EXPECT_EQ(single.tokens.string(), u"[\"a\"]");
        ASSERT_EQ(single.childNodes.size(), 3u);
        EXPECT_EQ(single.childNodes.back().whichBest, 0u);
    }

    TEST(ParserTest, NotEqualSymbolsKeepTheirConsumptionOrderAndIndependentChildSpans) {
        const auto ast = Parser::parse(u"=! trailing", Node::NodeEqualEntry::nodeNotEqual);
        EXPECT_FALSE(ast.isError());
        EXPECT_EQ(ast.tokens.string(), u"=!");
        ASSERT_EQ(ast.childNodes.size(), 2u);
        EXPECT_EQ(ast.childNodes[0].tokens.string(), u"=");
        EXPECT_EQ(ast.childNodes[0].tokens.startIndex, 0u);
        EXPECT_EQ(ast.childNodes[0].tokens.endIndex, 1u);
        EXPECT_EQ(ast.childNodes[1].tokens.string(), u"!");
        EXPECT_EQ(ast.childNodes[1].tokens.startIndex, 1u);
        EXPECT_EQ(ast.childNodes[1].tokens.endIndex, 2u);
        const auto incomplete = Parser::parse(u"=", Node::NodeEqualEntry::nodeNotEqual);
        EXPECT_TRUE(incomplete.isError());
        ASSERT_EQ(incomplete.childNodes.size(), 1u);
        const auto &symbols = incomplete.childNodes[0].childNodes;
        ASSERT_EQ(symbols.size(), 2u);
        EXPECT_EQ(symbols[0].tokens.string(), u"=");
        EXPECT_TRUE(symbols[1].tokens.isEmpty());
        EXPECT_EQ(symbols[1].tokens.startIndex, 1u);
        EXPECT_EQ(symbols[1].tokens.endIndex, 1u);
    }

    TEST(ParserTest, ListsPreserveQuotedDelimitersNestedListsAndCustomSeparators) {
        Node::NodeSingleSymbol left(u'[', u"左括号"), comma(u',', u"分隔符"), right(u']', u"右括号"), pipe(u'|', u"分隔符");
        Node::NodeJsonString string;
        Node::NodeList inner(left, string, comma, right);
        Node::NodeList outer(left, inner, comma, right);
        const auto nested = Parser::parse(uR"([["a,]","b"],["c"]] trailing)", outer);
        EXPECT_FALSE(nested.isError());
        EXPECT_EQ(nested.tokens.string(), uR"([["a,]","b"],["c"]])");
        ASSERT_EQ(nested.childNodes.size(), 5u);
        EXPECT_EQ(nested.childNodes[1].getBestNode().childNodes.size(), 5u);
        EXPECT_EQ(nested.childNodes[3].childNodes.size(), 3u);

        Node::NodeList custom(left, string, pipe, right);
        const auto alternate = Parser::parse(uR"(["a,b"|"c"] trailing)", custom);
        EXPECT_FALSE(alternate.isError());
        EXPECT_EQ(alternate.tokens.string(), uR"(["a,b"|"c"])");
        EXPECT_EQ(alternate.childNodes.size(), 5u);
        const auto incomplete = Parser::parse(uR"([["a"],["b"})", outer);
        EXPECT_TRUE(incomplete.isError());
        // 第二个元素失败后立即停止，未生成外层右括号节点。
        ASSERT_EQ(incomplete.childNodes.size(), 4u);
        EXPECT_EQ(incomplete.childNodes.back().tokens.string(), uR"(["b"})");
    }

    TEST(ParserTest, RangeValidationPreservesNumericTokensAndOpenBounds) {
        Node::NodeRange range("RANGE", u"范围");
        for (const std::u16string_view input: {u"0", u"3..5", u"-3..-1", u"..5", u"3.."}) {
            const auto ast = Parser::parse(input, range);
            EXPECT_FALSE(ast.isError()) << utf8::utf16to8(input);
            EXPECT_EQ(ast.tokens.string(), input);
            EXPECT_TRUE(ast.childNodes.empty());
        }
        for (const std::u16string_view input: {u"", u"..", u"bad", u"1+2", u"1.5", u"\"1..5\""}) {
            const auto ast = Parser::parse(input, range);
            EXPECT_TRUE(ast.isError()) << utf8::utf16to8(input);
            EXPECT_EQ(ast.tokens.string(), input);
            ASSERT_EQ(ast.errorReasons.size(), 1u);
            EXPECT_EQ(ast.errorReasons.front()->level, ErrorReasonLevel::CONTENT_ERROR);
        }
    }

    TEST(ParserTest, JsonStringValidationPreservesMissingQuotesAndNonStringTokens) {
        Node::NodeJsonString node;
        for (const std::u16string_view input: {u"123", u"]", u"word", u"\"open"}) {
            const auto ast = Parser::parse(input, node);
            ASSERT_EQ(ast.errorReasons.size(), 1u);
            EXPECT_EQ(ast.errorReasons.front()->getCode(), ErrorReasonCode::QuotedStringRequired);
            EXPECT_EQ(ast.errorReasons.front()->level, ErrorReasonLevel::CONTENT_ERROR);
            EXPECT_EQ(ast.tokens.string(), input);
            EXPECT_TRUE(ast.childNodes.empty());
        }
        for (const std::u16string_view input: {u"", u"   "}) {
            const auto ast = Parser::parse(input, node);
            ASSERT_EQ(ast.errorReasons.size(), 1u);
            EXPECT_EQ(ast.errorReasons.front()->getCode(), ErrorReasonCode::EmptyString);
            EXPECT_EQ(ast.errorReasons.front()->level, ErrorReasonLevel::INCOMPLETE);
            EXPECT_EQ(ast.tokens.startIndex, input.size());
            EXPECT_EQ(ast.tokens.endIndex, input.size());
        }
        for (const std::u16string_view input: {u"\"\"", u"\"ok\""}) {
            const auto ast = Parser::parse(input, node);
            EXPECT_FALSE(ast.isError());
            EXPECT_EQ(ast.tokens.string(), input);
        }
    }

    TEST(ParserTest, SymbolDiagnosticsPreserveSpansMessagesAndTokenConsumption) {
        Node::NodeSingleSymbol symbol(u']', u"右括号");
        struct Case {
            std::u16string_view input;
            size_t start, end;
            std::optional<ErrorReasonCode> code;
            ErrorReasonLevel::ErrorReasonLevel level;
            std::u16string_view message;
        };
        const Case cases[] = {
                {u"", 0, 0, ErrorReasonCode::RequireSymbol, ErrorReasonLevel::INCOMPLETE, u"命令不完整，需要符号]"},
                {u"   ", 3, 3, ErrorReasonCode::RequireSymbol, ErrorReasonLevel::INCOMPLETE, u"命令不完整，需要符号]"},
                {u"word", 0, 4, ErrorReasonCode::SymbolTypeMismatch, ErrorReasonLevel::TYPE_ERROR, u"类型不匹配，需要符号]，但当前内容为word"},
                {u"123", 0, 3, ErrorReasonCode::SymbolTypeMismatch, ErrorReasonLevel::TYPE_ERROR, u"类型不匹配，需要符号]，但当前内容为123"},
                {u"\n", 0, 1, ErrorReasonCode::SymbolTypeMismatch, ErrorReasonLevel::TYPE_ERROR, u"类型不匹配，需要符号]，但当前内容为\n"},
                {u"中文", 0, 2, ErrorReasonCode::SymbolTypeMismatch, ErrorReasonLevel::TYPE_ERROR, u"类型不匹配，需要符号]，但当前内容为中文"},
                {u"[", 0, 1, ErrorReasonCode::SymbolContentMismatch, ErrorReasonLevel::CONTENT_ERROR, u"内容不匹配，正确的符号为]，但当前内容为["},
                {u"]", 0, 1, std::nullopt, ErrorReasonLevel::CONTENT_ERROR, u""},
                {u"  ] trailing", 2, 3, std::nullopt, ErrorReasonLevel::CONTENT_ERROR, u""},
        };
        for (const auto &item: cases) {
            SCOPED_TRACE(utf8::utf16to8(item.input));
            const auto ast = Parser::parse(item.input, symbol);
            EXPECT_EQ(ast.tokens.size(), item.start == item.end ? 0u : 1u);
            EXPECT_EQ(ast.mode, ASTNodeMode::NONE);
            EXPECT_EQ(ast.id, ASTNodeId::NONE);
            EXPECT_TRUE(ast.childNodes.empty());
            EXPECT_EQ(ast.tokens.startIndex, item.start);
            EXPECT_EQ(ast.tokens.endIndex, item.end);
            EXPECT_EQ(ast.tokens.string(), item.input.substr(item.start, item.end - item.start));
            EXPECT_EQ(ast.isError(), item.code.has_value());
            if (item.code) {
                ASSERT_EQ(ast.errorReasons.size(), 1u);
                const auto &error = ast.errorReasons.front();
                EXPECT_EQ(error->getCode(), *item.code);
                EXPECT_EQ(error->level, item.level);
                EXPECT_EQ(error->start, item.start);
                EXPECT_EQ(error->end, item.end);
                EXPECT_EQ(error->getMessage(), item.message);
                EXPECT_TRUE(error->errorReason.empty());
            }
        }
    }

    //带内层语法的JSON字符串节点：字符串内容必须是"ok"。
    //JSON_STRING直接作为start node，这样内层解析错误不会被JSON_OBJECT的allEntry回退机制吞掉
    constexpr const char *INNER_TEXT_JSON_NODES = R"([
    {
      "id": "inner",
      "start": "S",
      "node": [
        {
          "type": "JSON_STRING",
          "id": "S",
          "description": "test string",
          "data": [
            {"type": "TEXT", "id": "T", "description": "test text", "data": {"name": "ok"}}
          ]
        }
      ]
    }
  ])";

    constexpr const char *INNER_TEXT_COMMANDS = R"([
    {
      "name": ["t"],
      "description": "test command",
      "syntax": ["/t <v: json>"],
      "node": {"<v: json>": {"type": "JSON", "key": "inner"}}
    }
  ])";

    static std::shared_ptr<const CPack> createInnerTextCpack() {
        std::unique_ptr<CPack> cpack;
        const bool success = tryCreateCpack(makeCpackJson(INNER_TEXT_JSON_NODES, "[]", INNER_TEXT_COMMANDS), cpack);
        EXPECT_TRUE(success);
        return std::shared_ptr<const CPack>(std::move(cpack));
    }

    static std::vector<std::shared_ptr<ErrorReason>> parseAndGetErrors(
            const std::shared_ptr<const CPack> &cpack, const std::u16string &command) {
        return CommandContext(cpack, command).getErrorReasons();
    }

    //内层AST的错误位置必须通过indexConvertList映射回原始命令的坐标，
    //不能用简单的固定偏移(转义序列会让内外坐标相差可变长度)
    TEST(ParserTest, JsonStringInnerErrorPosition) {
        const auto cpack = createInnerTextCpack();
        ASSERT_NE(cpack, nullptr);
        struct Case {
            std::u16string command;
            size_t start;
            size_t end;
        };
        //t "bad" -> "bad"位于[3, 6)
        //t "x\"bad" -> x\"bad位于[3, 9)，\"占用2个字符但解码后只有1个
        //t "\u0041bad" -> \u0041bad位于[3, 12)，\u0041占用6个字符但解码后只有1个
        const std::vector<Case> cases = {
                {uR"(t "bad")", 3, 6},
                {uR"(t "x\"bad")", 3, 9},
                {uR"(t "\u0041bad")", 3, 12},
        };
        for (const auto &item: cases) {
            SCOPED_TRACE(utf8::utf16to8(item.command));
            const auto errorReasons = parseAndGetErrors(cpack, item.command);
            ASSERT_FALSE(errorReasons.empty());
            bool found = false;
            for (const auto &errorReason: errorReasons) {
                EXPECT_LE(errorReason->start, item.command.size());
                EXPECT_LE(errorReason->end, item.command.size()) << "error span out of command range";
                if (errorReason->start == item.start && errorReason->end == item.end) {
                    EXPECT_NE(errorReason->errorReason.find(u"找不到含义"), std::u16string::npos);
                    found = true;
                }
            }
            EXPECT_TRUE(found) << "no error at the expected converted span [" << item.start << ", " << item.end << ")";
        }
    }

    TEST(ParserTest, JsonStringInnerValidContent) {
        const auto cpack = createInnerTextCpack();
        ASSERT_NE(cpack, nullptr);
        //合法内容(含转义)不应该产生任何错误
        EXPECT_TRUE(parseAndGetErrors(cpack, uR"(t "ok")").empty());
        //\u006F解码为'o'，解码后的内容同样是合法的"ok"
        EXPECT_TRUE(parseAndGetErrors(cpack, uR"(t "\u006Fk")").empty());
    }

    // NUMBER token可能包含连续的0-9 . + -，Parser必须校验完整的数字格式
    TEST(ParserTest, NumberFormatValidation) {
        std::filesystem::path resourceDir(RESOURCE_DIR);
        std::unique_ptr<CPack> vanillaCpack = CHelper::serialization::createCPackByDirectory(resourceDir / "resources" / "beta" / "vanilla");
        const auto cpack = std::shared_ptr<const CPack>(std::move(vanillaCpack));
        ASSERT_NE(cpack, nullptr);
        const auto hasErrorWithText = [](const std::vector<std::shared_ptr<ErrorReason>> &errors,
                                         size_t start,
                                         size_t end,
                                         const std::u16string &text) {
            for (const auto &item: errors) {
                if (item->start == start && item->end == end &&
                    item->errorReason.find(text) != std::u16string::npos) {
                    return true;
                }
            }
            return false;
        };
        struct Case {
            std::u16string command;
            size_t numberStart;
            size_t numberEnd;
            std::u16string message;
        };
        //非法数字应该被标记为"数字格式错误"而不是"数值超出范围"
        const std::vector<Case> invalidCases = {
                {u"camerashake add @a 1-2 1", 19, 22, u"数字格式错误"},
                {u"camerashake add @a 1+2 1", 19, 22, u"数字格式错误"},
                {u"camerashake add @a 1--2 1", 19, 23, u"数字格式错误"},
                {u"camerashake add @a 1.2.3 1", 19, 24, u"数字格式错误"},
                {u"camerashake add @a 1..2 1", 19, 23, u"数字格式错误"},
                {u"camerashake add @a . 1", 19, 20, u"数字格式错误"},
                {u"camerashake add @a +. 1", 19, 21, u"数字格式错误"},
                {u"camerashake add @a -. 1", 19, 21, u"数字格式错误"},
                {u"give @s stone 1-2", 14, 17, u"数字格式错误"},
                {u"give @s stone 1--2", 14, 18, u"数字格式错误"},
                {u"give @s stone 1.5", 14, 17, u"类型不匹配，正确的参数类型为整数，但当前参数类型为小数"},
        };
        for (const auto &item: invalidCases) {
            SCOPED_TRACE(utf8::utf16to8(item.command));
            const auto errors = parseAndGetErrors(cpack, item.command);
            EXPECT_TRUE(hasErrorWithText(errors, item.numberStart, item.numberEnd, item.message))
                    << "malformed number was not rejected with a format error";
            EXPECT_FALSE(hasErrorWithText(errors, item.numberStart, item.numberEnd, u"数值不在范围"))
                    << "malformed number should not be reported as a range error";
        }
        //合法数字不应该产生错误
        const std::vector<std::u16string> validCases = {
                u"camerashake add @a 0 1",
                u"camerashake add @a +1 2",
                u"camerashake add @a .5 1.5",
                u"camerashake add @a 1. 2",
                u"camerashake add @a 0.5 -0.25",
                u"give @s stone 1",
                u"give @s stone 64 2",
        };
        for (const auto &command: validCases) {
            SCOPED_TRACE(utf8::utf16to8(command));
            EXPECT_TRUE(parseAndGetErrors(cpack, command).empty()) << "valid number was rejected";
        }
    }

    //orNode的子节点为空时，Debug模式下必须抛出异常，不能访问childNodes[whichBest]导致越界
    //Release下该检查被编译掉，直接测试会是未定义行为
#if CHelperDebug
    TEST(ParserTest, OrNodeWithEmptyChildNodes) {
        Node::NodeText node("OR_NODE_TEST", u"test", NormalId::make(u"x", u"test"));
        Node::NodeWithType nodeWithType(node);
        std::pmr::vector<ASTNode> childNodes;
        EXPECT_ANY_THROW(ASTNode::orNode(nodeWithType, std::move(childNodes), nullptr));
    }
#endif

}// namespace CHelper::Test
