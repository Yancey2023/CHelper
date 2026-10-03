#include <chelper/lexer/Lexer.h>
#include <chelper/lexer/TokenReader.h>
#include <gtest/gtest.h>

namespace CHelper::Test {

    TEST(TokenReaderTest, SkipToLineFeedPreservesPositionAcrossRewinds) {
        for (const auto command: {u"", u"say hello", u"\n", u"say a\n\nsay b\n", u"say a\r\nsay b"}) {
            const auto tokens = Lexer::lex(command);
            TokenReader reader(tokens);
            for (size_t start = 0; start <= tokens->allTokens.size() + 1; ++start) {
                size_t expected = start;
                while (expected < tokens->allTokens.size() && tokens->allTokens[expected].type != TokenType::LF) {
                    ++expected;
                }
                reader.index = start;
                reader.push();
                reader.skipToLF();
                EXPECT_EQ(reader.index, expected);
                reader.skipToLF();
                EXPECT_EQ(reader.index, expected);
                reader.restore();
                EXPECT_EQ(reader.index, start);
                reader.skipToLF();
                EXPECT_EQ(reader.index, expected);
            }
        }
    }

}// namespace CHelper::Test
