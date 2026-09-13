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

#include <chelper/parser/ErrorReason.h>
#include <chelper/util/JsonUtil.h>
#include <gtest/gtest.h>

namespace CHelper::Test {

    //JSON层面的转义写法用原始字符串表示，例如uR"("a\nb")"是字面包含反斜杠和字母n的JSON字符串，
    //解码后应该包含真实换行符

    TEST(JsonUtilTest, DecodeEscapeSequences) {
        struct Case {
            std::u16string_view json;
            std::u16string_view expected;
        };
        const std::vector<Case> cases = {
                {uR"("a\nb")", u"a\nb"},// \n -> 真实换行符
                {uR"("a\rb")", u"a\rb"},
                {uR"("a\tb")", u"a\tb"},
                {uR"("a\bb")", u"a\bb"},
                {uR"("a\fb")", u"a\fb"},
                {uR"("a\"b")", u"a\"b"},
                {uR"("a\\b")", u"a\\b"},
                {uR"("a\/b")", u"a/b"},
                {uR"("abc")", u"abc"},
        };
        for (const auto &item: cases) {
            SCOPED_TRACE(utf8::utf16to8(std::u16string(item.json)));
            const auto result = JsonUtil::jsonString2String(item.json);
            EXPECT_EQ(result.errorReason, nullptr);
            EXPECT_TRUE(result.isComplete);
            EXPECT_EQ(result.result, item.expected);
        }
    }

    TEST(JsonUtilTest, DecodeUnicodeEscape) {
        {
            const auto result = JsonUtil::jsonString2String(uR"("\u0041")");
            EXPECT_EQ(result.errorReason, nullptr);
            EXPECT_TRUE(result.isComplete);
            EXPECT_EQ(result.result, u"A");
        }
        {
            //\u0000是合法的JSON转义，不能因为值为0而拒绝
            const auto result = JsonUtil::jsonString2String(uR"("a\u0000b")");
            EXPECT_EQ(result.errorReason, nullptr);
            EXPECT_TRUE(result.isComplete);
            ASSERT_EQ(result.result.size(), 3);
            EXPECT_EQ(result.result[0], u'a');
            EXPECT_EQ(result.result[1], u'\0');
            EXPECT_EQ(result.result[2], u'b');
        }
        {
            //大写十六进制
            const auto result = JsonUtil::jsonString2String(uR"("\u00E9")");
            EXPECT_EQ(result.errorReason, nullptr);
            EXPECT_EQ(result.result, u"é");
        }
        {
            //编码U+0000 -> \u0000
            const auto encoded = JsonUtil::string2jsonString(std::u16string(u"a\0b", 3));
            EXPECT_EQ(encoded, uR"(a\u0000b)");
        }
    }

    TEST(JsonUtilTest, DecodeErrors) {
        //未知的转义字符
        {
            const auto result = JsonUtil::jsonString2String(uR"("a\qb")");
            EXPECT_NE(result.errorReason, nullptr);
            //错误位置应该指向q
            EXPECT_EQ(result.errorReason->start, 3);
            EXPECT_EQ(result.errorReason->end, 4);
        }
        //未闭合的字符串
        {
            const auto result = JsonUtil::jsonString2String(uR"("abc)");
            EXPECT_EQ(result.errorReason, nullptr);
            EXPECT_FALSE(result.isComplete);
            EXPECT_EQ(result.result, u"abc");
        }
        //不是以引号开头
        {
            const auto result = JsonUtil::jsonString2String(u"abc");
            EXPECT_NE(result.errorReason, nullptr);
        }
    }

    TEST(JsonUtilTest, DecodeIndexConvertList) {
        //"a\nb"的原始字符: " a \ n b "，解码后: a \n b
        //解码字符0('a')来自原始下标1，解码字符1(换行)来自转义序列起始下标2，
        //解码字符2('b')来自原始下标4，最后一位映射到结束引号下标5
        {
            const auto result = JsonUtil::jsonString2String(uR"("a\nb")");
            ASSERT_EQ(result.indexConvertList.size(), 4);
            EXPECT_EQ(result.convert(0), 1);
            EXPECT_EQ(result.convert(1), 2);
            EXPECT_EQ(result.convert(2), 4);
            EXPECT_EQ(result.convert(3), 5);
        }
        //"\u0041"的原始字符: " \ u 0 0 4 1 "，解码后: A
        {
            const auto result = JsonUtil::jsonString2String(uR"("\u0041")");
            ASSERT_EQ(result.indexConvertList.size(), 2);
            EXPECT_EQ(result.convert(0), 1);
            EXPECT_EQ(result.convert(1), 7);
        }
        //无转义时坐标一一对应
        {
            const auto result = JsonUtil::jsonString2String(uR"("xyz")");
            ASSERT_EQ(result.indexConvertList.size(), 4);
            EXPECT_EQ(result.convert(0), 1);
            EXPECT_EQ(result.convert(1), 2);
            EXPECT_EQ(result.convert(2), 3);
            EXPECT_EQ(result.convert(3), 4);
        }
        //Debug模式下越界访问convert会直接抛出异常定位问题，Release下不检查
#if CHelperDebug
        {
            const auto result = JsonUtil::jsonString2String(uR"("ab")");
            EXPECT_ANY_THROW(result.convert(100));
        }
#endif
    }

    TEST(JsonUtilTest, EncodeControlCharacters) {
        struct Case {
            std::u16string_view input;
            std::u16string_view expected;
        };
        //string2jsonString只负责转义内容，不包含外层的双引号
        const std::vector<Case> cases = {
                {u"a\nb", uR"(a\nb)"},
                {u"a\rb", uR"(a\rb)"},
                {u"a\tb", uR"(a\tb)"},
                {u"a\bb", uR"(a\bb)"},
                {u"a\fb", uR"(a\fb)"},
                {u"a\"b", uR"(a\"b)"},
                {u"a\\b", uR"(a\\b)"},
                {u"a/b", uR"(a\/b)"},
                {u"abc", uR"(abc)"},
                {u"", uR"()"},
        };
        for (const auto &item: cases) {
            SCOPED_TRACE(utf8::utf16to8(std::u16string(item.input)));
            const auto result = JsonUtil::string2jsonString(item.input);
            EXPECT_EQ(result, item.expected);
        }
    }

    TEST(JsonUtilTest, EncodeDecodeRoundTrip) {
        const std::vector<std::u16string> inputs = {
                u"",
                u"abc",
                u"a\nb\rc\td\be\ff\"g\\h/i",
                u"中文测试",
                u"line1\nline2",
                u"tab\there",
                u"quote\"inside",
                u"back\\slash",
                std::u16string(u"null\0char", 9),
        };
        for (const auto &input: inputs) {
            SCOPED_TRACE(utf8::utf16to8(input));
            const auto encoded = JsonUtil::string2jsonString(input);
            const auto quoted = std::u16string(u"\"").append(encoded).append(u"\"");
            const auto decoded = JsonUtil::jsonString2String(quoted);
            EXPECT_EQ(decoded.errorReason, nullptr);
            EXPECT_TRUE(decoded.isComplete);
            //生成的必须是合法JSON字符串，且解码后还原出原始内容
            EXPECT_EQ(decoded.result, input);
        }
    }

}// namespace CHelper::Test
