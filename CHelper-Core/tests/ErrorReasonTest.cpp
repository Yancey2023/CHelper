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

    TEST(ErrorReasonTest, FormattedMessagesOwnTheirTextAfterDiagnosticsDestruction) {
        std::u16string message, custom;
        {
            ErrorReasonMemoryScope scope;
            std::u16string input(4096, u'参');
            const auto error = ErrorReasons::numberOutOfRange(ErrorReasonLevel::ID_ERROR, {3, 9}, 0.1, 2.5, input);
            const auto text = ErrorReasons::customText(ErrorReasonLevel::CONTENT_ERROR, {2, 8}, input);
            input.assign(4096, u'变');
            message = error->getMessage();
            custom = text->getMessage();
        }
        EXPECT_EQ(message, fmt::format(u"数值不在范围[{}, {}]内 -> {}", 0.1, 2.5, std::u16string(4096, u'参')));
        EXPECT_EQ(custom, std::u16string(4096, u'参'));
        message.append(100000, u'后');
        EXPECT_EQ(message.back(), u'后');
    }

    TEST(ErrorReasonTest, AllocatingWhileOtherThreadReleasesKeepsBlocksAndUnusedReferencesBalanced) {
        constexpr size_t rounds = 32, batch = 64;
        std::array<std::shared_ptr<ErrorReason>, batch> shared;
        std::array<std::weak_ptr<ErrorReason>, rounds> weak;
        std::barrier begin(2), finish(2);
        std::thread worker([&] {
            for (size_t round = 0; round < rounds; ++round) {
                begin.arrive_and_wait();
                for (auto &error: shared) error.reset();
                finish.arrive_and_wait();
            }
        });
        startAllocCounting();
        {
            ErrorReasonMemoryScope scope;
            for (size_t round = 0; round < rounds; ++round) {
                for (auto &error: shared) error = ErrorReasons::unknownId(ErrorReasonLevel::ID_ERROR, {0, 1}, u"参数");
                weak[round] = shared.front();
                begin.arrive_and_wait();
                for (size_t i = 0; i < batch * 2; ++i) {
                    const auto error = ErrorReasons::requireSpace(ErrorReasonLevel::REQUIRE_SPACE, {i, i});
                    EXPECT_EQ(error->start, i);
                }
                finish.arrive_and_wait();
            }
        }
        for (auto &error: weak) {
            EXPECT_TRUE(error.expired());
            error.reset();
        }
        const auto allocations = stopAllocCounting();
        worker.join();
        // 弱引用、活动作用域和未使用的批量引用释放后，所有诊断块都应归还。
        EXPECT_LE(allocations.netBytes, 0);
    }

    TEST(ErrorReasonTest, SmallParametersRemainOwnedAcrossBlockBoundaries) {
        std::vector<std::shared_ptr<ErrorReason>> retained;
        {
            ErrorReasonMemoryScope scope;
            for (size_t i = 0; i < 512; ++i) {
                const std::u16string text(517 + i % 4 * 500, u'参');
                retained.push_back(ErrorReasons::unknownMeaning(ErrorReasonLevel::INCOMPLETE, {i, i + 1}, text));
                // 交错无参数和不同对齐/长度的参数，覆盖块末尾参数的溢出路径。
                retained.push_back(ErrorReasons::requireSpace(ErrorReasonLevel::REQUIRE_SPACE, {i, i}));
                retained.push_back(ErrorReasons::numberOutOfRange(ErrorReasonLevel::ID_ERROR, {i, i + 1}, 0.1, 2.5, u"bad"));
            }
        }
        for (size_t i = 0; i < 512; ++i) {
            const std::u16string text(517 + i % 4 * 500, u'参');
            EXPECT_EQ(retained[i * 3]->getMessage(), u"找不到含义 -> " + text);
            EXPECT_EQ(retained[i * 3]->start, i);
            EXPECT_EQ(retained[i * 3]->end, i + 1);
            EXPECT_EQ(retained[i * 3 + 1]->getMessage(), u"命令不完整，缺少空格");
            EXPECT_EQ(retained[i * 3 + 2]->getMessage(), fmt::format(u"数值不在范围[{}, {}]内 -> {}", 0.1, 2.5, u"bad"));
        }
    }

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
                EXPECT_EQ(required->getMessage(), fmt::format(u"命令不完整，需要的参数类型为{}", item.name));
                retained.push_back(required);
                for (const auto actual: {TokenType::STRING, TokenType::NUMBER, TokenType::SYMBOL, TokenType::SPACE,
                                         TokenType::LF, static_cast<TokenType::TokenType>(255)}) {
                    const auto error = ErrorReasons::typeMismatch(ErrorReasonLevel::TYPE_ERROR, {2, 4}, item.type, actual);
                    const auto name = TokenType::getName(actual);
                    EXPECT_EQ(TokenType::getNameView(actual), name);
                    EXPECT_EQ(error->getMessage(), fmt::format(u"类型不匹配，正确的参数类型为{}，但当前参数类型为{}", item.name, name));
                    retained.push_back(error);
                }
            }
        }
        for (const auto &error: retained) EXPECT_FALSE(error->getMessage().empty());
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
        }
        for (size_t i = 0; i < errors.size(); ++i) {
            EXPECT_EQ(errors[i]->start, i);
            EXPECT_EQ(errors[i]->end, i + 1);
            EXPECT_EQ(errors[i]->getMessage(), u"缺少参数，保留每个错误的位置与文本");
        }
        auto retained = errors.front();
        errors.clear();
        auto display = retained->getMessage();
        display.append(100000, u'参');
        EXPECT_EQ(display.size(), 100000 + std::u16string_view(u"缺少参数，保留每个错误的位置与文本").size());
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
        auto moved = inner->getMessage();
        outer.reset();
        inner.reset();
        after.reset();
        EXPECT_EQ(moved, u"命令不完整，缺少空格");
        moved.append(100000, u'参');
        const auto fallback = ErrorReasons::requireSpace(ErrorReasonLevel::REQUIRE_SPACE, {3, 3});
        EXPECT_EQ(fallback->getMessage(), u"命令不完整，缺少空格");
    }

    TEST(ErrorReasonTest, IndependentTextsCanGrowConcurrentlyAfterScope) {
        constexpr size_t count = 4;
        std::array<std::shared_ptr<ErrorReason>, count> errors;
        {
            ErrorReasonMemoryScope scope;
            for (auto &error: errors) error = ErrorReasons::requireSpace(ErrorReasonLevel::REQUIRE_SPACE, {0, 0});
        }
        std::barrier start(static_cast<ptrdiff_t>(count));
        std::array<std::thread, count> threads;
        std::array<std::u16string, count> displays;
        for (size_t i = 0; i < count; ++i) {
            threads[i] = std::thread([&, i] {
                start.arrive_and_wait();
                displays[i] = errors[i]->getMessage();
                for (size_t j = 0; j < 16; ++j) displays[i].append(4096, static_cast<char16_t>(u'a' + i));
            });
        }
        for (auto &thread: threads) thread.join();
        for (size_t i = 0; i < count; ++i) {
            EXPECT_EQ(errors[i]->start, 0u);
            EXPECT_EQ(displays[i].back(), static_cast<char16_t>(u'a' + i));
        }
    }

    TEST(ErrorReasonTest, FormatsUnicodeAndLongMessagesWithoutChangingText) {
        ErrorReasonMemoryScope scope;
        for (const auto &value: {std::u16string(u"中文🙂"), std::u16string(4096, u'参')}) {
            const auto error = ErrorReasons::customText(ErrorReasonLevel::CONTENT_ERROR, {3, 9}, fmt::format(u"错误 [{:c}] [{:.2f}] -> {}", u']', 1.25, value));
            const auto expected = fmt::format(u"错误 [{:c}] [{:.2f}] -> {}", u']', 1.25, value);
            EXPECT_EQ(std::u16string_view(error->getMessage()), std::u16string_view(expected));
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
            input.assign(4096, u'变');
        }
        EXPECT_EQ(error->getCode(), ErrorReasonCode::UnknownMeaning);
        EXPECT_EQ(error->getMessage(), u"找不到含义 -> " + std::u16string(4096, u'参'));
        auto moved = error->getMessage();
        error.reset();
        EXPECT_EQ(std::u16string_view(moved), u"找不到含义 -> " + std::u16string(4096, u'参'));
    }

    TEST(ErrorReasonTest, DeferredNumbersPreserveOriginalFormattingTypes) {
        const auto check = []<class T>(T min, T max) {
            const auto error = ErrorReasons::numberOutOfRange(ErrorReasonLevel::ID_ERROR, {0, 1}, min, max, std::u16string_view(u"bad"));
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
    }

    TEST(ErrorReasonTest, NumericEqualityUsesFormattedFloatingPointValues) {
        const auto check = []<class T>(T value) {
            const auto error = ErrorReasons::numberOutOfRange(ErrorReasonLevel::ID_ERROR, {0, 1}, value, T{1}, u"bad");
            const auto sameText = ErrorReasons::customText(ErrorReasonLevel::CONTENT_ERROR, {0, 1}, error->getMessage());
            const auto copy = ErrorReasons::numberOutOfRange(ErrorReasonLevel::ID_ERROR, {0, 1}, value, T{1}, u"bad");
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
        EXPECT_EQ(error->getMessage(), u"类型不匹配，需要符号]，但当前内容为中文🙂");
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
