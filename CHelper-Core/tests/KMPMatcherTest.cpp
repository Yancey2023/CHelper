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

#include <chelper/util/KMPMatcher.h>
#include <gtest/gtest.h>
#include <random>

TEST(KMPMatcher, KMPMatcher) {
    CHelper::KMPMatcher kmpMatcher1(u"");
    EXPECT_EQ(kmpMatcher1.match(u"a"), 0);
    EXPECT_EQ(kmpMatcher1.match(u"ab"), 0);
    EXPECT_EQ(kmpMatcher1.match(u"aa"), 0);
    EXPECT_EQ(kmpMatcher1.match(u"ba"), 0);
    EXPECT_EQ(kmpMatcher1.match(u""), 0);
    EXPECT_EQ(kmpMatcher1.match(u"@"), 0);
    EXPECT_EQ(kmpMatcher1.match(u"@a"), 0);
    CHelper::KMPMatcher kmpMatcher2(u"@");
    EXPECT_EQ(kmpMatcher2.match(u"a"), std::u16string::npos);
    EXPECT_EQ(kmpMatcher2.match(u"a@b"), 1);
    EXPECT_EQ(kmpMatcher2.match(u"aa@"), 2);
    EXPECT_EQ(kmpMatcher2.match(u"@ba"), 0);
    EXPECT_EQ(kmpMatcher2.match(u"@"), 0);
    EXPECT_EQ(kmpMatcher2.match(u"f@12"), 1);
    EXPECT_EQ(kmpMatcher2.match(u"@a"), 0);
    CHelper::KMPMatcher kmpMatcher3(u"aa");
    EXPECT_EQ(kmpMatcher3.match(u"aaa"), 0);
    EXPECT_EQ(kmpMatcher3.match(u"ababa"), std::u16string::npos);
    EXPECT_EQ(kmpMatcher3.match(u"baa"), 1);
}

TEST(KMPMatcher, MatchesStringViewFindAcrossInlineAndHeapStorage) {
    std::mt19937 random(20261004);
    for (size_t length = 0; length <= 70; ++length) {
        for (size_t iteration = 0; iteration < 24; ++iteration) {
            std::u16string pattern(length, u'\0'), text(120, u'\0');
            for (auto &ch: pattern) ch = static_cast<char16_t>(random() % 5);
            for (auto &ch: text) ch = static_cast<char16_t>(random() % 5);
            if (iteration % 2 == 0) text.replace(19, pattern.size(), pattern);
            CHelper::KMPMatcher matcher(pattern);
            EXPECT_EQ(matcher.match(text), std::u16string_view(text).find(pattern));
            EXPECT_EQ(matcher.match(pattern), 0);
            EXPECT_EQ(matcher.match(u""), pattern.empty() ? 0 : std::u16string::npos);
        }
    }
}
