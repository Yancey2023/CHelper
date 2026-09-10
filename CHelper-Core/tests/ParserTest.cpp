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
#include <chelper/parser/ASTNode.h>
#include <chelper/parser/Parser.h>
#include <chelper/resources/id/NormalId.h>
#include <chelper/serialization/Serialization.h>
#include <gtest/gtest.h>

namespace CHelper::Test {

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
#ifdef CHelperDebug
    TEST(ParserTest, OrNodeWithEmptyChildNodes) {
        Node::NodeText node("OR_NODE_TEST", u"test", NormalId::make(u"x", u"test"));
        Node::NodeWithType nodeWithType(node);
        std::vector<ASTNode> childNodes;
        EXPECT_ANY_THROW(ASTNode::orNode(nodeWithType, std::move(childNodes), nullptr));
    }
#endif

}// namespace CHelper::Test
