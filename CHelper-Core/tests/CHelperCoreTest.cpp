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

}// namespace CHelper::Test
