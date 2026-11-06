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

#include <chelper/CHelperCore.h>
#include <chelper/serialization/Serialization.h>
#include <gtest/gtest.h>

namespace CHelper::Test {

    //provider返回nullptr时，create必须失败并返回nullptr，
    //不能产生一个内部cpack为nullptr、后续调用会解引用空指针的CHelperCore
    TEST(CHelperCoreTest, CreateWithNullCPack) {
        CHelperCore *core = CHelperCore::create([]() -> std::unique_ptr<CPack> {
            return nullptr;
        });
        EXPECT_EQ(core, nullptr);
    }

    TEST(CHelperCoreTest, CreateWithValidCPack) {
        std::filesystem::path resourceDir(RESOURCE_DIR);
        CHelperCore *core = CHelperCore::create([&resourceDir]() -> std::unique_ptr<CPack> {
            return CHelper::serialization::createCPackByDirectory(resourceDir / "resources" / "beta" / "vanilla");
        });
        ASSERT_NE(core, nullptr);
        //创建成功后core必须完全可用
        EXPECT_NO_FATAL_FAILURE(static_cast<void>(core->getCPack().getNormalId("nothing")));
        CommandContext *context = nullptr;
        EXPECT_NO_THROW({
            context = core->createContext(u"list");
        });
        ASSERT_NE(context, nullptr);
        EXPECT_TRUE(context->getErrorReasons().empty());
        CHelperCore::deleteContext(context);
        delete core;
    }

    TEST(CHelperCoreTest, TargetSelectorGrammarComesFromResource) {
        std::filesystem::path resourceDir(RESOURCE_DIR);
        auto cpack = CHelper::serialization::createCPackByDirectory(resourceDir / "resources" / "beta" / "experiment");
        ASSERT_NE(cpack, nullptr);
        auto core = std::make_unique<CHelperCore>(std::shared_ptr<const CPack>(std::move(cpack)));
        const std::vector<std::u16string> selectors{
                u"kill @e",
                u"kill @e[x=~1]",
                u"kill @e[type=minecraft:zombie]",
                u"kill @e[type=!minecraft:zombie]",
                u"kill @e[scores={test=1..10}]",
                u"kill @e[hasitem={item=minecraft:stone}]",
                u"kill @a[haspermission={camera=enabled}]"};
        for (const auto &command: selectors) {
            auto context = std::unique_ptr<CommandContext>(core->createContext(command));
            EXPECT_TRUE(context->getErrorReasons().empty()) << utf8::utf16to8(command);
        }
    }

    TEST(CHelperCoreTest, ScheduleIntegerWithUnitSurvivesJsonRoundTrip) {
        const std::filesystem::path resourceDir(RESOURCE_DIR);
        const std::vector<std::pair<std::string, std::string>> resourceVariants{
                {"release", "vanilla"},
                {"release", "experiment"},
                {"beta", "vanilla"},
                {"beta", "experiment"}};
        const std::vector<std::pair<std::string, std::string>> units{
                {"D", "D后缀代表游戏日"},
                {"S", "S后缀代表秒"},
                {"T", "T后缀或无后缀代表游戏刻"},
                {"d", "游戏日"},
                {"s", "秒"},
                {"t", "游戏刻"}};
        const std::vector<std::u16string> validCommands{
                u"schedule delay add test:scheduled 10D",
                u"schedule delay add test:scheduled 20S",
                u"schedule delay add test:scheduled 30T",
                u"schedule delay add test:scheduled 40"};
        const std::vector<std::u16string> invalidCommands{
                u"schedule delay add test:scheduled 10X",
                u"schedule delay add test:scheduled 10Dtail"};

        for (const auto &[edition, branch]: resourceVariants) {
            SCOPED_TRACE(edition + "/" + branch);
            auto source = CHelper::serialization::createCPackByDirectory(resourceDir / "resources" / edition / branch);
            ASSERT_NE(source, nullptr);

            const std::string json = source->toJson();
            EXPECT_NE(json.find("\"units\""), std::string::npos);
            for (const auto &[name, description]: units) {
                SCOPED_TRACE(name);
                EXPECT_NE(json.find(description), std::string::npos);
            }

            auto restored = CHelper::serialization::createCPackByJson(json);
            ASSERT_NE(restored, nullptr);
            const std::string roundTripJson = restored->toJson();
            EXPECT_NE(roundTripJson.find("\"units\""), std::string::npos);
            for (const auto &[name, description]: units) {
                SCOPED_TRACE(name);
                EXPECT_NE(roundTripJson.find(description), std::string::npos);
            }

            std::shared_ptr<const CPack> sharedPack(std::move(restored));
            CHelperCore core(std::move(sharedPack));
            for (const auto &command: validCommands) {
                auto context = std::unique_ptr<CommandContext>(core.createContext(command));
                ASSERT_NE(context, nullptr);
                EXPECT_TRUE(context->getErrorReasons().empty()) << utf8::utf16to8(command);
            }
            for (const auto &command: invalidCommands) {
                auto context = std::unique_ptr<CommandContext>(core.createContext(command));
                ASSERT_NE(context, nullptr);
                EXPECT_FALSE(context->getErrorReasons().empty()) << utf8::utf16to8(command);
            }
        }
    }

}// namespace CHelper::Test
