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
#include <chelper/CHelperCore.h>
#include <chelper/parser/Parser.h>
#include <gtest/gtest.h>

namespace CHelper::Test {

    constexpr const char *JSON_NULL_NODES = R"([
    {
      "id": "nullnode",
      "start": "N",
      "node": [
        {"type": "JSON_NULL", "id": "N", "description": "null parameter"}
      ]
    }
  ])";

    constexpr const char *JSON_NULL_COMMANDS = R"([
    {
      "name": ["nullcmd"],
      "description": "null test command",
      "syntax": ["/nullcmd <v: json>"],
      "node": {"<v: json>": {"type": "JSON", "key": "nullnode"}}
    }
  ])";

    static std::shared_ptr<CHelperCore> createNullCommandCore() {
        std::unique_ptr<CPack> cpack;
        const bool success = tryCreateCpack(makeCpackJson(JSON_NULL_NODES, "[]", JSON_NULL_COMMANDS), cpack);
        EXPECT_TRUE(success);
        if (!success) {
            return nullptr;
        }
        return std::shared_ptr<CHelperCore>(new CHelperCore(std::shared_ptr<const CPack>(std::move(cpack))));
    }

    static bool hasSuggestionWithContent(const std::vector<AutoSuggestion::Suggestion> &suggestions,
                                         const std::u16string_view name) {
        for (const auto &item: suggestions) {
            if (std::u16string_view(item.content->name) == name) {
                return true;
            }
        }
        return false;
    }

    //只有当前输入是"null"的前缀时才应该建议null
    TEST(AutoSuggestionTest, NullLiteralPrefixSuggestion) {
        const auto core = createNullCommandCore();
        ASSERT_NE(core, nullptr);
        struct Case {
            std::u16string command;
            bool expectNull;
        };
        const std::vector<Case> cases = {
                {u"nullcmd ", true},
                {u"nullcmd n", true},
                {u"nullcmd nu", true},
                {u"nullcmd nul", true},
                {u"nullcmd null", true},
                {u"nullcmd x", false},
                {u"nullcmd u", false},
                {u"nullcmd ul", false},
                {u"nullcmd ll", false},
                {u"nullcmd nulx", false},
        };
        for (const auto &item: cases) {
            SCOPED_TRACE(utf8::utf16to8(item.command));
            std::unique_ptr<CommandContext> context(core->createContext(item.command));
            const auto suggestions = context->getSuggestions(item.command.length());
            EXPECT_EQ(hasSuggestionWithContent(suggestions, u"null"), item.expectNull);
        }
    }

    //null建议应该替换整个当前token
    TEST(AutoSuggestionTest, NullSuggestionReplacesToken) {
        const auto core = createNullCommandCore();
        ASSERT_NE(core, nullptr);
        const std::u16string command = u"nullcmd nul";
        std::unique_ptr<CommandContext> context(core->createContext(command));
        const auto suggestions = context->getSuggestions(command.length());
        for (size_t which = 0; which < suggestions.size(); ++which) {
            const auto &item = suggestions[which];
            if (item.content->name != u"null") {
                continue;
            }
            EXPECT_EQ(item.start, 8);
            EXPECT_EQ(item.end, 11);
            //应用建议后应该得到完整的null
            const auto applied = context->applySuggestion(command.length(), which);
            ASSERT_TRUE(applied.has_value());
            EXPECT_EQ(applied->first, u"nullcmd null");
        }
    }

    //命名空间ID的省略minecraft逻辑：显式声明"minecraft"命名空间的条目也应该生成裸名建议。
    //std::size(u"minecraft")包含结尾'\0'导致的长度错误曾使这类条目丢失裸名建议
    constexpr const char *NAMESPACE_EXTRA_IDS = R"({"type": "namespace", "id": "nsid", "content": [
      {"name": "cow", "idNamespace": "minecraft", "description": "cow entity"},
      {"name": "creeper", "description": "creeper entity"}
    ]})";

    constexpr const char *NAMESPACE_COMMANDS = R"([
    {
      "name": ["ns"],
      "description": "namespace test command",
      "syntax": ["/ns <v: id>"],
      "node": {"<v: id>": {"type": "NAMESPACE_ID", "key": "nsid"}}
    }
  ])";

    static std::shared_ptr<CHelperCore> createNamespaceCore() {
        std::unique_ptr<CPack> cpack;
        const bool success = tryCreateCpack(makeCpackJson("[]", "[]", NAMESPACE_COMMANDS, NAMESPACE_EXTRA_IDS), cpack);
        EXPECT_TRUE(success);
        if (!success) {
            return nullptr;
        }
        return std::shared_ptr<CHelperCore>(new CHelperCore(std::shared_ptr<const CPack>(std::move(cpack))));
    }

    TEST(AutoSuggestionTest, ExplicitMinecraftNamespaceOmitted) {
        const auto core = createNamespaceCore();
        ASSERT_NE(core, nullptr);
        {
            //显式"minecraft"命名空间的条目：裸名和完整名都应该被建议
            const std::u16string command = u"ns cow";
            std::unique_ptr<CommandContext> context(core->createContext(command));
            const auto suggestions = context->getSuggestions(command.length());
            EXPECT_TRUE(hasSuggestionWithContent(suggestions, u"cow"));
            EXPECT_TRUE(hasSuggestionWithContent(suggestions, u"minecraft:cow"));
        }
        {
            //未声明命名空间的条目行为不变
            const std::u16string command = u"ns creeper";
            std::unique_ptr<CommandContext> context(core->createContext(command));
            const auto suggestions = context->getSuggestions(command.length());
            EXPECT_TRUE(hasSuggestionWithContent(suggestions, u"creeper"));
            EXPECT_TRUE(hasSuggestionWithContent(suggestions, u"minecraft:creeper"));
        }
    }

}// namespace CHelper::Test
