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