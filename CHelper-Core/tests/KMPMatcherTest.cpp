//
// Created by Yancey on 2024-5-21.
//

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