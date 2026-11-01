#include <chelper/CHelperCore.h>
#include <chelper/auto_suggestion/Suggestions.h>
#include <chelper/serialization/Serialization.h>
#include <gtest/gtest.h>

namespace CHelper::Test {

    namespace {
        template<class F>
        concept CanCombine = requires(AutoSuggestion::Suggestions &suggestions, F &&function) {
            suggestions.combine(suggestions, std::forward<F>(function));
        };

        template<class F>
        concept CanExpect = requires(TokenReader &reader, F &&function) {
            Old2New::expect(reader, std::forward<F>(function));
        };

        template<class F>
        concept CanForEach = requires(const TokensView &tokens, F &&function) {
            tokens.forEach(std::forward<F>(function));
        };

        template<class F>
        concept CanCreate = requires(F &&function) {
            CHelperCore::create(std::forward<F>(function));
        };

        static_assert(CanCombine<decltype([](AutoSuggestion::Suggestion &) { return true; })>);
        static_assert(!CanCombine<decltype([](AutoSuggestion::Suggestion &) {})>);
        static_assert(CanExpect<decltype([](const Token &) { return true; })>);
        static_assert(!CanExpect<decltype([](Token &) { return true; })>);
        static_assert(CanForEach<decltype([](const Token &) {})>);
        static_assert(CanForEach<decltype(&Token::getStartIndex)>);
        static_assert(!CanForEach<decltype([](Token &) {})>);
        static_assert(CanCreate<decltype([]() -> std::unique_ptr<CPack> { return nullptr; })>);
        static_assert(!CanCreate<decltype([] { return 42; })>);
    }// namespace

    TEST(CallbackTest, CombineBorrowsMoveOnlyCallbackAndPreservesGroups) {
        AutoSuggestion::Suggestions source, target;
        source.addSpaceSuggestion({0, 0, false, NormalId::make(u"space")});
        source.addSymbolSuggestion({0, 0, false, NormalId::make(u"symbol")});
        source.addLiteralSuggestion({0, 0, false, NormalId::make(u"literal")});
        source.addIdSuggestion({0, 0, false, NormalId::make(u"id")});
        target.addIdSuggestion({2, 2, false, NormalId::make(u"id")});
        auto filter = [calls = std::make_unique<size_t>(0)](AutoSuggestion::Suggestion &suggestion) mutable {
            ++*calls;
            suggestion.start = suggestion.end = 2;
            return suggestion.content->name != u"literal";
        };
        target.combine(source, filter);
        // 回调原地修改后仍先过滤、再去重；分组顺序不受影响。
        const auto result = target.collect();
        ASSERT_EQ(result.size(), 3u);
        EXPECT_EQ(result[0].content->name, u"space");
        EXPECT_EQ(result[1].content->name, u"symbol");
        EXPECT_EQ(result[2].content->name, u"id");
        for (const auto &suggestion: result) {
            EXPECT_EQ(suggestion.start, 2u);
            EXPECT_EQ(suggestion.end, 2u);
        }
    }

    TEST(CallbackTest, ExpectBorrowsStateAndRestoresRejectedToken) {
        TokenReader reader(Lexer::lex(u"  first second"));
        auto calls = std::make_unique<size_t>(0);
        auto *count = calls.get();
        auto check = [calls = std::move(calls)](const Token &token) mutable {
            ++*calls;
            return token.content == u"first";
        };
        EXPECT_TRUE(Old2New::expect(reader, check));
        const auto checkpoint = reader.index;
        EXPECT_FALSE(Old2New::expect(reader, check));
        EXPECT_EQ(reader.index, checkpoint);
        EXPECT_TRUE(reader.indexStack.empty());
        EXPECT_EQ(*count, 2u);
        reader.index = reader.lexerResult->allTokens.size();
        EXPECT_FALSE(Old2New::expect(reader, check));
        EXPECT_TRUE(reader.indexStack.empty());
        EXPECT_EQ(*count, 2u);
    }

    TEST(CallbackTest, ForEachBorrowsMoveOnlyCallbackAndHonorsTokenRange) {
        const auto lexer = Lexer::lex(u"first second third");
        const TokensView tokens(lexer, 1, 4);
        auto output = std::make_unique<std::u16string>();
        auto *text = output.get();
        auto append = [output = std::move(output)](const Token &token) mutable {
            output->append(token.content);
        };
        tokens.forEach(append);
        EXPECT_EQ(*text, u" second ");
        TokensView(lexer, 2, 2).forEach(append);
        EXPECT_EQ(*text, u" second ");
        tokens.forEach(std::ref(append));
        EXPECT_EQ(*text, u" second  second ");
        tokens.forEach(&Token::getStartIndex);
    }

    TEST(CallbackTest, CreateAcceptsMoveOnlyProviderAndHandlesExceptions) {
        auto provider = [cpack = serialization::createCPackByDirectory(
                                 std::filesystem::path(RESOURCE_DIR) / "resources" / "beta" / "vanilla")]() mutable {
            return std::move(cpack);
        };
        std::unique_ptr<CHelperCore> core(CHelperCore::create(std::move(provider)));
        ASSERT_NE(core, nullptr);
        std::unique_ptr<CommandContext> context(core->createContext(u"list"));
        EXPECT_TRUE(context->getErrorReasons().empty());
        EXPECT_EQ(CHelperCore::create(provider), nullptr);
        EXPECT_EQ(CHelperCore::create([]() -> std::unique_ptr<CPack> {
                      throw std::runtime_error("provider failure");
                  }),
                  nullptr);
    }

}// namespace CHelper::Test
