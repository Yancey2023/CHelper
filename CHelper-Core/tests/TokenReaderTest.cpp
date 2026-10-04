#include <chelper/lexer/Lexer.h>
#include <chelper/lexer/TokenReader.h>
#include <gtest/gtest.h>

namespace CHelper::Test {

    TEST(TokenReaderTest, LexerPreservesEscapedStringsNumberSpansAndIncompleteQuotes) {
        const auto lexer = Lexer::lex(u"1-2 . +a -1.5 \"a,]\\\"b\" 名\\,字\n");
        const std::pair<TokenType::TokenType, std::u16string_view> expected[] = {
                {TokenType::NUMBER, u"1-2"},
                {TokenType::SPACE, u" "},
                {TokenType::NUMBER, u"."},
                {TokenType::SPACE, u" "},
                {TokenType::SYMBOL, u"+"},
                {TokenType::STRING, u"a"},
                {TokenType::SPACE, u" "},
                {TokenType::NUMBER, u"-1.5"},
                {TokenType::SPACE, u" "},
                {TokenType::STRING, uR"("a,]\"b")"},
                {TokenType::SPACE, u" "},
                {TokenType::STRING, uR"(名\,字)"},
                {TokenType::LF, u"\n"}};
        ASSERT_EQ(lexer->allTokens.size(), std::size(expected));
        size_t position = 0;
        for (size_t i = 0; i < std::size(expected); ++i) {
            EXPECT_EQ(lexer->allTokens[i].type, expected[i].first);
            EXPECT_EQ(lexer->allTokens[i].content, expected[i].second);
            EXPECT_EQ(lexer->allTokens[i].pos, position);
            position += expected[i].second.size();
        }
        EXPECT_EQ(position, lexer->content.size());
        const auto incomplete = Lexer::lex(u"\"unfinished\\");
        ASSERT_EQ(incomplete->allTokens.size(), 1u);
        EXPECT_EQ(incomplete->allTokens[0].content, incomplete->content);
        EXPECT_EQ(incomplete->allTokens[0].type, TokenType::STRING);
        EXPECT_TRUE(Lexer::lex(u"")->allTokens.empty());
    }

    TEST(TokenReaderTest, SingleTokenViewsPreserveRewindsAndDoNotSkipLineFeeds) {
        const auto lexer = Lexer::lex(u"  abc \n]  ");
        TokenReader reader(lexer);
        reader.push();
        auto word = reader.readTokenView();
        EXPECT_EQ(word.string(), u"abc");
        EXPECT_EQ(word.startIndex, 2u);
        EXPECT_EQ(word.endIndex, 5u);
        EXPECT_EQ(reader.indexStack.size(), 1u);
        reader.restore();
        EXPECT_EQ(reader.index, 0u);
        EXPECT_EQ(reader.readTokenView().string(), u"abc");
        reader.push();
        const auto checkpoint = reader.index;
        auto lineFeed = reader.readTokenView();
        EXPECT_EQ(lineFeed.string(), u"\n");
        EXPECT_EQ(lineFeed[0].type, TokenType::LF);
        EXPECT_EQ(reader.readTokenView().string(), u"]");
        const auto end = reader.readTokenView();
        EXPECT_TRUE(end.isEmpty());
        EXPECT_EQ(end.startIndex, lexer->content.size());
        EXPECT_EQ(end.endIndex, lexer->content.size());
        EXPECT_EQ(reader.indexStack.size(), 1u);
        reader.restore();
        EXPECT_EQ(reader.index, checkpoint);
        EXPECT_EQ(reader.readTokenView().string(), u"\n");
    }

    TEST(TokenReaderTest, SingleTokenViewsKeepTheirLexerAlive) {
        std::weak_ptr<LexerResult> weak;
        std::optional<TokensView> retained;
        {
            const auto lexer = Lexer::lex(u"  owned text");
            weak = lexer;
            TokenReader reader(lexer);
            retained.emplace(reader.readTokenView());
        }
        EXPECT_FALSE(weak.expired());
        EXPECT_EQ(retained->string(), u"owned");
        retained.reset();
        EXPECT_TRUE(weak.expired());
    }

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
