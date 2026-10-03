#include "BenchHelper.h"
#include <array>
#include <barrier>
#include <chelper/parser/ErrorReason.h>
#include <gtest/gtest.h>
#include <thread>

namespace CHelper::Test {

    TEST(ErrorReasonTest, SharedBlocksOutliveScopeAndReleaseWithWeakPointers) {
        std::vector<std::shared_ptr<ErrorReason>> errors;
        std::weak_ptr<ErrorReason> weak;
        {
            ErrorReasonMemoryScope scope;
            for (size_t i = 0; i < 2000; ++i) {
                errors.push_back(ErrorReason::incomplete(i, i + 1, u"缺少参数，保留每个错误的位置与文本"));
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
            outer = ErrorReason::requireSpace(0, 0);
            {
                ErrorReasonMemoryScope nested;
                inner = ErrorReason::requireSpace(1, 1);
            }
            after = ErrorReason::requireSpace(2, 2);
        }
        inner = inner->materializedCopy();
        auto moved = std::move(inner->errorReason);
        outer.reset();
        inner.reset();
        after.reset();
        EXPECT_EQ(moved, u"命令不完整，缺少空格");
        moved.append(100000, u'参');
        const auto fallback = ErrorReason::requireSpace(3, 3);
        EXPECT_EQ(fallback->errorReason.get_allocator().resource(), std::pmr::new_delete_resource());
    }

    TEST(ErrorReasonTest, IndependentTextsCanGrowConcurrentlyAfterScope) {
        constexpr size_t count = 4;
        std::array<std::shared_ptr<ErrorReason>, count> errors;
        {
            ErrorReasonMemoryScope scope;
            for (auto &error: errors) error = ErrorReason::requireSpace(0, 0);
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
            const auto error = ErrorReason::formatted(ErrorReasonLevel::CONTENT_ERROR, 3, 9,
                                                      u"错误 [{:c}] [{:.2f}] -> {}", u']', 1.25, value);
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
            const auto error = ErrorReason::requireSpace(0, 0);
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
            error = ErrorReason::diagnostic(ErrorReasonLevel::CONTENT_ERROR, 3, 9,
                                            ErrorReasonCode::UnknownMeaning, {std::u16string_view(input)});
            EXPECT_TRUE(error->errorReason.empty());
            input.assign(4096, u'变');
        }
        EXPECT_EQ(error->getCode(), ErrorReasonCode::UnknownMeaning);
        ASSERT_EQ(error->getArguments().size(), 1);
        EXPECT_EQ(std::get<std::u16string_view>(error->getArguments()[0]), std::u16string(4096, u'参'));
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
            const auto error = ErrorReason::diagnostic(ErrorReasonLevel::ID_ERROR, 0, 1,
                                                       ErrorReasonCode::NumberOutOfRange,
                                                       {min, max, std::u16string_view(u"bad")});
            EXPECT_TRUE(error->errorReason.empty());
            EXPECT_EQ(error->getMessage(), fmt::format(u"数值不在范围[{}, {}]内 -> {}", min, max, u"bad"));
        };
        check(0.1f, std::numeric_limits<float>::max());
        check(0.1, std::numeric_limits<double>::max());
        check(std::numeric_limits<int32_t>::min(), std::numeric_limits<int32_t>::max());
        check(std::numeric_limits<int64_t>::min(), std::numeric_limits<int64_t>::max());
    }

    TEST(ErrorReasonTest, DeferredEqualityPreservesTextBasedDeduplication) {
        auto structured = ErrorReason::diagnostic(ErrorReasonLevel::INCOMPLETE, 2, 4,
                                                  ErrorReasonCode::UnknownMeaning, {std::u16string_view(u"abc")});
        auto sameText = ErrorReason::contentError(2, 4, u"找不到含义 -> abc");
        EXPECT_EQ(*structured, *sameText);
        auto differentLevel = ErrorReason::diagnostic(ErrorReasonLevel::CONTENT_ERROR, 2, 4,
                                                      ErrorReasonCode::UnknownMeaning, {std::u16string_view(u"abc")});
        EXPECT_EQ(*structured, *differentLevel);
        differentLevel->end = 5;
        EXPECT_NE(*structured, *differentLevel);
        auto differentArgs = ErrorReason::diagnostic(ErrorReasonLevel::INCOMPLETE, 2, 4,
                                                     ErrorReasonCode::UnknownMeaning, {std::u16string_view(u"def")});
        EXPECT_NE(*structured, *differentArgs);
        const auto left = ErrorReason::diagnostic(ErrorReasonLevel::TYPE_ERROR, 0, 1, ErrorReasonCode::TypeMismatch,
                                                  {std::u16string_view(u"a，但当前参数类型为b"), std::u16string_view(u"c")});
        const auto right = ErrorReason::diagnostic(ErrorReasonLevel::TYPE_ERROR, 0, 1, ErrorReasonCode::TypeMismatch,
                                                   {std::u16string_view(u"a"), std::u16string_view(u"b，但当前参数类型为c")});
        EXPECT_EQ(*left, *right);
    }

    TEST(ErrorReasonTest, DeferredRenderingIsReadOnlyAcrossThreads) {
        std::shared_ptr<ErrorReason> error;
        {
            ErrorReasonMemoryScope scope;
            error = ErrorReason::diagnostic(ErrorReasonLevel::TYPE_ERROR, 1, 2, ErrorReasonCode::SymbolTypeMismatch,
                                            {u']', std::u16string_view(u"中文🙂")});
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
        ErrorReason copied(ErrorReasonLevel::INCOMPLETE, 0, 0, u"");
        {
            ErrorReasonMemoryScope scope;
            const auto error = ErrorReason::diagnostic(ErrorReasonLevel::TYPE_ERROR, 3, 9,
                                                       ErrorReasonCode::SymbolTypeMismatch, {u']', std::u16string_view(u"中文🙂")});
            ErrorReason constructed(*error);
            copied = constructed;
        }
        EXPECT_TRUE(copied.errorReason.empty());
        EXPECT_EQ(copied.getMessage(), u"类型不匹配，需要符号]，但当前内容为中文🙂");
        auto output = copied.materializedCopy();
        output->errorReason.clear();
        EXPECT_TRUE(output->getMessage().empty());
    }

    TEST(ErrorReasonTest, DeferredEscapeMessagesPreserveCharactersAndBackslashes) {
        const std::u16string sequence = u"0中AB";
        const auto incomplete = ErrorReason::diagnostic(ErrorReasonLevel::CONTENT_ERROR, 0, 1,
                                                        ErrorReasonCode::IncompleteUnicodeEscape, {std::u16string_view(sequence)});
        EXPECT_EQ(incomplete->getMessage(), fmt::format(u"字符串转义缺失后半部分 -> \\u{}", sequence));
        const auto invalid = ErrorReason::diagnostic(ErrorReasonLevel::INCOMPLETE, 0, 1,
                                                     ErrorReasonCode::InvalidUnicodeEscapeCharacter, {u'中', std::u16string_view(sequence)});
        EXPECT_EQ(invalid->getMessage(), fmt::format(u"字符串转义出现非法字符{} -> \\u{}", u'中', sequence));
        const auto unknown = ErrorReason::diagnostic(ErrorReasonLevel::CONTENT_ERROR, 0, 1,
                                                     ErrorReasonCode::UnknownEscape, {u'中'});
        EXPECT_EQ(unknown->getMessage(), fmt::format(u"未知的转义字符 -> \\{:c}", u'中'));
    }

}// namespace CHelper::Test
