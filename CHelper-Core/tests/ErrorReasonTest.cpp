#include "BenchHelper.h"
#include <array>
#include <barrier>
#include <chelper/parser/ErrorReason.h>
#include <chelper/parser/ErrorReasonFactory.h>
#include <gtest/gtest.h>
#include <thread>

namespace CHelper::Test {

    static_assert(!std::is_constructible_v<ErrorReason, ErrorReasonLevel::ErrorReasonLevel, size_t, size_t, ErrorReasonCode>);
    static_assert(!std::is_copy_constructible_v<ErrorReason>);
    static_assert(!std::is_copy_assignable_v<ErrorReason>);

    TEST(ErrorReasonTest, TypeArgumentsPreserveMessagesEqualityAndIndependentLifetimes) {
        struct Expected {
            ErrorReasonExpectedType type;
            std::u16string_view name;
        };
        const Expected expected[] = {
                {ErrorReasonExpectedType::String, u"字符串类型"},
                {ErrorReasonExpectedType::Integer, u"整数类型"},
                {ErrorReasonExpectedType::Float, u"数字类型"},
                {ErrorReasonExpectedType::Symbol, u"符号类型"},
        };
        std::vector<std::shared_ptr<ErrorReason>> retained;
        {
            ErrorReasonMemoryScope scope;
            for (const auto &item: expected) {
                const auto required = ErrorReasons::requireType(ErrorReasonLevel::INCOMPLETE, {2, 2}, item.type);
                const auto textRequired = ErrorReasons::requireType(ErrorReasonLevel::INCOMPLETE, {2, 2}, item.name);
                EXPECT_TRUE(required->errorReason.empty());
                EXPECT_EQ(required->getMessage(), textRequired->getMessage());
                EXPECT_EQ(*required, *textRequired);
                retained.push_back(required);
                for (const auto actual: {TokenType::STRING, TokenType::NUMBER, TokenType::SYMBOL, TokenType::SPACE,
                                         TokenType::LF, static_cast<TokenType::TokenType>(255)}) {
                    const auto error = ErrorReasons::typeMismatch(ErrorReasonLevel::TYPE_ERROR, {2, 4}, item.type, actual);
                    const auto name = TokenType::getName(actual);
                    const auto legacy = ErrorReasons::typeMismatch(ErrorReasonLevel::TYPE_ERROR, {2, 4}, item.name, std::u16string_view(name));
                    EXPECT_EQ(TokenType::getNameView(actual), name);
                    EXPECT_EQ(error->getMessage(), legacy->getMessage());
                    EXPECT_EQ(*error, *legacy);
                    EXPECT_EQ(*legacy, *error);
                    EXPECT_TRUE(error->errorReason.empty());
                    retained.push_back(error);
                }
            }
        }
        for (const auto &error: retained) {
            const auto copy = error->materializedCopy();
            EXPECT_EQ(copy->getCode(), error->getCode());
            EXPECT_EQ(copy->getMessage(), error->getMessage());
            EXPECT_EQ(*copy, *error);
        }
    }

    TEST(ErrorReasonTest, LastWeakReferencesCanReleaseSharedBlocksConcurrently) {
        constexpr size_t threadCount = 4, errorsPerThread = 1024;
        std::array<std::vector<std::weak_ptr<ErrorReason>>, threadCount> weak;
        std::vector<std::shared_ptr<ErrorReason>> errors;
        errors.reserve(threadCount * errorsPerThread);
        for (auto &items: weak) items.reserve(errorsPerThread);
        startAllocCounting();
        {
            ErrorReasonMemoryScope scope;
            for (size_t i = 0; i < threadCount * errorsPerThread; ++i) {
                auto error = ErrorReasons::unknownMeaning(ErrorReasonLevel::CONTENT_ERROR, {i, i + 1}, std::u16string_view(u"参数"));
                weak[i % threadCount].push_back(error);
                errors.push_back(std::move(error));
            }
        }
        errors.clear();
        const auto retained = stopAllocCounting();
        ASSERT_GT(retained.netBytes, 0);
        for (const auto &items: weak) {
            for (const auto &item: items) ASSERT_TRUE(item.expired());
        }
        std::barrier start(static_cast<ptrdiff_t>(threadCount + 1));
        std::barrier finish(static_cast<ptrdiff_t>(threadCount + 1));
        std::array<std::thread, threadCount> threads;
        for (size_t i = 0; i < threadCount; ++i) {
            threads[i] = std::thread([&, i] {
                start.arrive_and_wait();
                weak[i].clear();
                finish.arrive_and_wait();
            });
        }
        startAllocCounting();
        start.arrive_and_wait();
        finish.arrive_and_wait();
        const auto released = stopAllocCounting();
        for (auto &thread: threads) thread.join();
        EXPECT_LE(retained.netBytes + released.netBytes, 0);
    }

    TEST(ErrorReasonTest, SharedBlocksOutliveScopeAndReleaseWithWeakPointers) {
        std::vector<std::shared_ptr<ErrorReason>> errors;
        std::weak_ptr<ErrorReason> weak;
        {
            ErrorReasonMemoryScope scope;
            for (size_t i = 0; i < 2000; ++i) {
                errors.push_back(ErrorReasons::customText(ErrorReasonLevel::INCOMPLETE, {i, i + 1}, u"缺少参数，保留每个错误的位置与文本"));
            }
            weak = errors.front();
            EXPECT_EQ(errors.front()->errorReason.get_allocator().resource(), std::pmr::new_delete_resource());
        }
        for (size_t i = 0; i < errors.size(); ++i) {
            EXPECT_EQ(errors[i]->start, i);
            EXPECT_EQ(errors[i]->end, i + 1);
            EXPECT_EQ(errors[i]->errorReason, u"缺少参数，保留每个错误的位置与文本");
        }
        auto retained = errors.front();
        errors.clear();
        retained->errorReason.append(100000, u'参');
        EXPECT_EQ(retained->errorReason.size(), 100000 + std::u16string_view(u"缺少参数，保留每个错误的位置与文本").size());
        retained.reset();
        EXPECT_TRUE(weak.expired());
        std::thread release([weak = std::move(weak)]() mutable { weak.reset(); });
        release.join();
    }

    TEST(ErrorReasonTest, NestedScopesAndMovedTextsKeepIndependentLifetimes) {
        std::shared_ptr<ErrorReason> outer, inner, after;
        {
            ErrorReasonMemoryScope scope;
            outer = ErrorReasons::requireSpace(ErrorReasonLevel::REQUIRE_SPACE, {0, 0});
            {
                ErrorReasonMemoryScope nested;
                inner = ErrorReasons::requireSpace(ErrorReasonLevel::REQUIRE_SPACE, {1, 1});
            }
            after = ErrorReasons::requireSpace(ErrorReasonLevel::REQUIRE_SPACE, {2, 2});
        }
        inner = inner->materializedCopy();
        auto moved = std::move(inner->errorReason);
        outer.reset();
        inner.reset();
        after.reset();
        EXPECT_EQ(moved, u"命令不完整，缺少空格");
        moved.append(100000, u'参');
        const auto fallback = ErrorReasons::requireSpace(ErrorReasonLevel::REQUIRE_SPACE, {3, 3});
        EXPECT_EQ(fallback->errorReason.get_allocator().resource(), std::pmr::new_delete_resource());
    }

    TEST(ErrorReasonTest, IndependentTextsCanGrowConcurrentlyAfterScope) {
        constexpr size_t count = 4;
        std::array<std::shared_ptr<ErrorReason>, count> errors;
        {
            ErrorReasonMemoryScope scope;
            for (auto &error: errors) error = ErrorReasons::requireSpace(ErrorReasonLevel::REQUIRE_SPACE, {0, 0});
        }
        for (const auto &error: errors) {
            ASSERT_EQ(error->errorReason.get_allocator().resource(), errors.front()->errorReason.get_allocator().resource());
        }
        std::barrier start(static_cast<ptrdiff_t>(count));
        std::array<std::thread, count> threads;
        for (size_t i = 0; i < count; ++i) {
            threads[i] = std::thread([&, i] {
                start.arrive_and_wait();
                for (size_t j = 0; j < 16; ++j) errors[i]->errorReason.append(4096, static_cast<char16_t>(u'a' + i));
                errors[i]->start = i;
            });
        }
        for (auto &thread: threads) thread.join();
        for (size_t i = 0; i < count; ++i) {
            EXPECT_EQ(errors[i]->start, i);
            EXPECT_EQ(errors[i]->errorReason.back(), static_cast<char16_t>(u'a' + i));
        }
    }

    TEST(ErrorReasonTest, FormatsUnicodeAndLongMessagesWithoutChangingText) {
        ErrorReasonMemoryScope scope;
        for (const auto &value: {std::u16string(u"中文🙂"), std::u16string(4096, u'参')}) {
            const auto error = ErrorReasons::customText(ErrorReasonLevel::CONTENT_ERROR, {3, 9}, fmt::format(u"错误 [{:c}] [{:.2f}] -> {}", u']', 1.25, value));
            const auto expected = fmt::format(u"错误 [{:c}] [{:.2f}] -> {}", u']', 1.25, value);
            EXPECT_EQ(std::u16string_view(error->errorReason), std::u16string_view(expected));
            EXPECT_EQ(error->level, ErrorReasonLevel::CONTENT_ERROR);
            EXPECT_EQ(error->start, 3);
            EXPECT_EQ(error->end, 9);
        }
    }

    TEST(ErrorReasonTest, LastWeakReferenceReleasesRetainedBlock) {
        std::weak_ptr<ErrorReason> weak;
        startAllocCounting();
        {
            ErrorReasonMemoryScope scope;
            const auto error = ErrorReasons::requireSpace(ErrorReasonLevel::REQUIRE_SPACE, {0, 0});
            weak = error;
        }
        const auto retained = stopAllocCounting();
        ASSERT_TRUE(weak.expired());
        ASSERT_GT(retained.netBytes, 0);
        startAllocCounting();
        weak.reset();
        const auto released = stopAllocCounting();
        // 未提供大小的 delete 在一些平台按实际块大小计数，可能包含分配器填充。
        EXPECT_LE(retained.netBytes + released.netBytes, 0);
    }

    TEST(ErrorReasonTest, DeferredArgumentsOwnTheirTextAfterInputAndScopeDestruction) {
        std::shared_ptr<ErrorReason> error;
        {
            ErrorReasonMemoryScope scope;
            std::u16string input(4096, u'参');
            error = ErrorReasons::unknownMeaning(ErrorReasonLevel::CONTENT_ERROR, {3, 9}, std::u16string_view(input));
            EXPECT_TRUE(error->errorReason.empty());
            input.assign(4096, u'变');
        }
        EXPECT_EQ(error->getCode(), ErrorReasonCode::UnknownMeaning);
        EXPECT_EQ(error->getMessage(), u"找不到含义 -> " + std::u16string(4096, u'参'));
        auto output = error->materializedCopy();
        error.reset();
        EXPECT_EQ(output->getCode(), ErrorReasonCode::UnknownMeaning);
        EXPECT_EQ(output->getMessage(), u"找不到含义 -> " + std::u16string(4096, u'参'));
        auto moved = std::move(output->errorReason);
        output.reset();
        EXPECT_EQ(std::u16string_view(moved), u"找不到含义 -> " + std::u16string(4096, u'参'));
    }

    TEST(ErrorReasonTest, DeferredNumbersPreserveOriginalFormattingTypes) {
        const auto check = []<class T>(T min, T max) {
            const auto error = ErrorReasons::numberOutOfRange(ErrorReasonLevel::ID_ERROR, {0, 1}, min, max, std::u16string_view(u"bad"));
            EXPECT_TRUE(error->errorReason.empty());
            EXPECT_EQ(error->getMessage(), fmt::format(u"数值不在范围[{}, {}]内 -> {}", min, max, u"bad"));
        };
        check(0.1f, std::numeric_limits<float>::max());
        check(0.1, std::numeric_limits<double>::max());
        check(std::numeric_limits<int32_t>::min(), std::numeric_limits<int32_t>::max());
        check(std::numeric_limits<int64_t>::min(), std::numeric_limits<int64_t>::max());
        check(std::numeric_limits<uint64_t>::min(), std::numeric_limits<uint64_t>::max());
    }

    TEST(ErrorReasonTest, DeferredEqualityPreservesTextBasedDeduplication) {
        auto structured = ErrorReasons::unknownMeaning(ErrorReasonLevel::INCOMPLETE, {2, 4}, std::u16string_view(u"abc"));
        auto sameText = ErrorReasons::customText(ErrorReasonLevel::CONTENT_ERROR, {2, 4}, u"找不到含义 -> abc");
        EXPECT_EQ(*structured, *sameText);
        auto differentLevel = ErrorReasons::unknownMeaning(ErrorReasonLevel::CONTENT_ERROR, {2, 4}, std::u16string_view(u"abc"));
        EXPECT_EQ(*structured, *differentLevel);
        differentLevel->end = 5;
        EXPECT_NE(*structured, *differentLevel);
        auto differentArgs = ErrorReasons::unknownMeaning(ErrorReasonLevel::INCOMPLETE, {2, 4}, std::u16string_view(u"def"));
        EXPECT_NE(*structured, *differentArgs);
        const auto left = ErrorReasons::typeMismatch(ErrorReasonLevel::TYPE_ERROR, {0, 1}, std::u16string_view(u"a，但当前参数类型为b"), std::u16string_view(u"c"));
        const auto right = ErrorReasons::typeMismatch(ErrorReasonLevel::TYPE_ERROR, {0, 1}, std::u16string_view(u"a"), std::u16string_view(u"b，但当前参数类型为c"));
        EXPECT_EQ(*left, *right);
    }

    TEST(ErrorReasonTest, NumericEqualityUsesFormattedFloatingPointValues) {
        const auto check = []<class T>(T value) {
            const auto error = ErrorReasons::numberOutOfRange(ErrorReasonLevel::ID_ERROR, {0, 1}, value, T{1}, u"bad");
            const auto sameText = ErrorReasons::customText(ErrorReasonLevel::CONTENT_ERROR, {0, 1}, error->getMessage());
            const auto copy = ErrorReasons::copy(*error);
            EXPECT_EQ(*error, *copy);
            EXPECT_EQ(*error, *sameText);
        };
        check(std::numeric_limits<float>::quiet_NaN());
        check(std::numeric_limits<double>::infinity());
        const auto positive = ErrorReasons::numberOutOfRange(ErrorReasonLevel::ID_ERROR, {0, 1}, 0.0, 1.0, u"bad");
        const auto negative = ErrorReasons::numberOutOfRange(ErrorReasonLevel::ID_ERROR, {0, 1}, -0.0, 1.0, u"bad");
        EXPECT_NE(positive->getMessage(), negative->getMessage());
        EXPECT_NE(*positive, *negative);
    }

    TEST(ErrorReasonTest, DeferredRenderingIsReadOnlyAcrossThreads) {
        std::shared_ptr<ErrorReason> error;
        {
            ErrorReasonMemoryScope scope;
            error = ErrorReasons::symbolTypeMismatch(ErrorReasonLevel::TYPE_ERROR, {1, 2}, u']', std::u16string_view(u"中文🙂"));
        }
        std::array<std::thread, 4> threads;
        for (auto &thread: threads) {
            thread = std::thread([&] {
                for (size_t i = 0; i < 100; ++i) {
                    EXPECT_EQ(error->getMessage(), u"类型不匹配，需要符号]，但当前内容为中文🙂");
                }
            });
        }
        for (auto &thread: threads) thread.join();
        EXPECT_TRUE(error->errorReason.empty());
    }

    TEST(ErrorReasonTest, CopiesOfDeferredDiagnosticsKeepIndependentArguments) {
        std::shared_ptr<ErrorReason> copied;
        {
            ErrorReasonMemoryScope scope;
            const auto error = ErrorReasons::symbolTypeMismatch(ErrorReasonLevel::TYPE_ERROR, {3, 9}, u']', std::u16string_view(u"中文🙂"));
            const auto constructed = ErrorReasons::copy(*error);
            copied = ErrorReasons::copy(*constructed);
        }
        EXPECT_TRUE(copied->errorReason.empty());
        EXPECT_EQ(copied->getMessage(), u"类型不匹配，需要符号]，但当前内容为中文🙂");
        auto output = copied->materializedCopy();
        output->errorReason.clear();
        EXPECT_TRUE(output->getMessage().empty());
    }

    TEST(ErrorReasonTest, DeferredEscapeMessagesPreserveCharactersAndBackslashes) {
        const std::u16string sequence = u"0中AB";
        const auto incomplete = ErrorReasons::incompleteUnicodeEscape(ErrorReasonLevel::CONTENT_ERROR, {0, 1}, std::u16string_view(sequence));
        EXPECT_EQ(incomplete->getMessage(), fmt::format(u"字符串转义缺失后半部分 -> \\u{}", sequence));
        const auto invalid = ErrorReasons::invalidUnicodeEscapeCharacter(ErrorReasonLevel::INCOMPLETE, {0, 1}, u'中', std::u16string_view(sequence));
        EXPECT_EQ(invalid->getMessage(), fmt::format(u"字符串转义出现非法字符{} -> \\u{}", u'中', sequence));
        const auto unknown = ErrorReasons::unknownEscape(ErrorReasonLevel::CONTENT_ERROR, {0, 1}, u'中');
        EXPECT_EQ(unknown->getMessage(), fmt::format(u"未知的转义字符 -> \\{:c}", u'中'));
    }

}// namespace CHelper::Test
