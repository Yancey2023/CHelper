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
#include <chelper/FragmentContext.h>
#include <chelper/extension/Composer.h>
#include <chelper/extension/MainPack.h>
#include <gtest/gtest.h>

namespace CHelper::Test {

    namespace {

        std::unique_ptr<Extension::MainPack> openMainPack() {
            std::filesystem::path resourceDir(RESOURCE_DIR);
            Extension::MainPackSource source;
            source.kind = Extension::MainPackSource::Kind::Directory;
            source.directory = resourceDir / "main-pack";
            return Extension::MainPack::open(std::move(source));
        }

        std::shared_ptr<const CPack> composedBetaVanilla() {
            static std::shared_ptr<const CPack> cpack;
            if (!cpack) {
                auto pack = openMainPack();
                auto seg = pack->loadSegment("beta", "vanilla");
                cpack = Extension::compose(seg, {}).cpack;
            }
            return cpack;
        }

        bool hasSuggestion(const FragmentContext &ctx, const std::u16string &text, size_t index) {
            for (const auto &s: ctx.getSuggestions(index)) {
                if (s.content->name == text) {
                    return true;
                }
            }
            return false;
        }

    }// namespace

    TEST(FragmentContextTest, TargetSelectorEmptyXValueSingleError) {
        auto ctx = FragmentContext::createTargetSelector(composedBetaVanilla(), u"@p[x=");
        ASSERT_NE(ctx, nullptr);
        auto errors = ctx->getErrorReasons();
        ASSERT_EQ(errors.size(), 1u);
        EXPECT_EQ(errors[0]->level, ErrorReasonLevel::TYPE_ERROR);
    }

    TEST(FragmentContextTest, TargetSelectorArguments) {
        auto ctx = FragmentContext::createTargetSelector(composedBetaVanilla(), u"@p[");
        ASSERT_NE(ctx, nullptr);
        const size_t index = ctx->getContent().size();
        EXPECT_TRUE(hasSuggestion(*ctx, u"x", index));
        EXPECT_TRUE(hasSuggestion(*ctx, u"type", index));
        EXPECT_TRUE(hasSuggestion(*ctx, u"family", index));
        EXPECT_TRUE(hasSuggestion(*ctx, u"hasitem", index));
    }

    TEST(FragmentContextTest, TargetSelectorEntityValue) {
        auto ctx = FragmentContext::createTargetSelector(composedBetaVanilla(), u"@p[type=");
        ASSERT_NE(ctx, nullptr);
        const size_t index = ctx->getContent().size();
        EXPECT_TRUE(hasSuggestion(*ctx, u"minecraft:zombie", index));
    }

    TEST(FragmentContextTest, TranslateKeyCompletion) {
        auto ctx = FragmentContext::createId(composedBetaVanilla(), "translate", u"item.diamond.na");
        ASSERT_NE(ctx, nullptr);
        const size_t index = ctx->getContent().size();
        EXPECT_TRUE(hasSuggestion(*ctx, u"item.diamond.name", index));
    }

}// namespace CHelper::Test
