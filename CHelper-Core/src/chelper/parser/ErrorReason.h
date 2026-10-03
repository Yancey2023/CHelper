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

#pragma once

#include <chelper/parser/TokensView.h>
#include <pch.h>
#include <span>
#include <variant>

namespace CHelper {

    namespace ErrorReasonLevel {

        enum ErrorReasonLevel : uint8_t {
            //命令后面有多余部分
            EXCESS = 0,
            //缺少空格
            REQUIRE_SPACE = 1,
            //命令不完整
            INCOMPLETE = 2,
            //类型不匹配
            TYPE_ERROR = 3,
            //内容不匹配
            CONTENT_ERROR = 4,
            //逻辑错误
            LOGIC_ERROR = 5,
            //ID错误
            ID_ERROR = 6
        };

        //最大的错误等级，在ErrorReason.cpp中有定义
        extern ErrorReasonLevel maxLevel;

    }// namespace ErrorReasonLevel

    namespace Detail {
        class ErrorReasonMemoryResource;
    }

    // 分块资源由每个错误的 shared_ptr 控制块共同持有，查询结果可独立存活。
    class ErrorReasonMemoryScope {
        friend class ErrorReason;

        ErrorReasonMemoryScope *previous;
        std::shared_ptr<Detail::ErrorReasonMemoryResource> memory;

    public:
        ErrorReasonMemoryScope();
        ~ErrorReasonMemoryScope();

        ErrorReasonMemoryScope(const ErrorReasonMemoryScope &) = delete;
        ErrorReasonMemoryScope &operator=(const ErrorReasonMemoryScope &) = delete;
    };

    // 诊断标识与参数独立于展示语言；自定义资源包文本保持原样。
    enum class ErrorReasonCode : uint8_t {
        CustomText,
        RequireSpace,
        RequireType,
        TypeMismatch,
        IntegerRequired,
        InvalidNumber,
        EmptyNull,
        InvalidNull,
        EmptyString,
        QuotedStringRequired,
        EmptyCommandName,
        UnknownCommand,
        Excess,
        Incomplete,
        UnknownMeaning,
        InvalidCoordinate,
        EmptyRange,
        InvalidRange,
        StringContainsSpace,
        UnclosedString,
        UnexpectedSpace,
        RequireSymbol,
        SymbolTypeMismatch,
        SymbolContentMismatch,
        InvalidBoolean,
        UnknownCommandName,
        UnknownId,
        MixedCoordinates,
        LocalCoordinateDisallowed,
        UnknownSelectorArgument,
        UnknownJsonArgument,
        NumberOutOfRange,
        JsonQuotesRequired,
        IncompleteEscape,
        IncompleteUnicodeEscape,
        InvalidUnicodeEscapeCharacter,
        InvalidUnicodeEscapeValue,
        UnknownEscape,
    };

    using ErrorReasonArgument = std::variant<std::u16string_view, char16_t, int32_t, int64_t, uint64_t, float, double>;

    class ErrorReason {
        ErrorReasonCode code = ErrorReasonCode::CustomText;
        bool messageReady = true;
        std::span<const ErrorReasonArgument> arguments;
        // 无解析/查询作用域时，参数由单独的缓冲区持有；作用域内由对象共享块持有。
        std::unique_ptr<std::byte[]> argumentStorage;

        static std::shared_ptr<ErrorReason> makeDiagnostic(ErrorReasonLevel::ErrorReasonLevel level,
                                                           size_t start, size_t end, ErrorReasonCode code,
                                                           std::span<const ErrorReasonArgument> arguments);
        void copyArguments(std::span<const ErrorReasonArgument> input, Detail::ErrorReasonMemoryResource *memory = nullptr);

    public:
        ErrorReasonLevel::ErrorReasonLevel level;
        size_t start, end;
        // Linter 对外返回的结果包含展示文本；解析树中的结构化诊断请使用 getMessage()。
        std::pmr::u16string errorReason;

        ErrorReasonCode getCode() const noexcept { return code; }
        std::span<const ErrorReasonArgument> getArguments() const noexcept { return arguments; }
        std::u16string getMessage() const;
        std::shared_ptr<ErrorReason> materializedCopy() const;

        static std::shared_ptr<ErrorReason> diagnostic(ErrorReasonLevel::ErrorReasonLevel level,
                                                       size_t start, size_t end, ErrorReasonCode code,
                                                       std::initializer_list<ErrorReasonArgument> arguments = {});

        static std::shared_ptr<ErrorReason> diagnostic(ErrorReasonLevel::ErrorReasonLevel level,
                                                       const TokensView &tokens, ErrorReasonCode code,
                                                       std::initializer_list<ErrorReasonArgument> arguments = {}) {
            return diagnostic(level, tokens.startIndex, tokens.endIndex, code, arguments);
        }

        ErrorReason(ErrorReasonLevel::ErrorReasonLevel level,
                    size_t start,
                    size_t end,
                    std::u16string_view errorReason);

        ErrorReason(ErrorReasonLevel::ErrorReasonLevel level,
                    const TokensView &tokens,
                    std::u16string_view errorReason);

        ErrorReason(const ErrorReason &other);
        ErrorReason &operator=(const ErrorReason &other);

        static std::shared_ptr<ErrorReason> make(ErrorReasonLevel::ErrorReasonLevel level,
                                                 size_t start, size_t end, std::u16string_view text);

        static std::shared_ptr<ErrorReason> make(ErrorReasonLevel::ErrorReasonLevel level,
                                                 const TokensView &tokens, std::u16string_view text) {
            return make(level, tokens.startIndex, tokens.endIndex, text);
        }

        template<class... Args>
        static std::shared_ptr<ErrorReason> formatted(ErrorReasonLevel::ErrorReasonLevel level,
                                                      size_t start, size_t end, const char16_t *pattern, const Args &...args) {
            // 常见错误文本直接在栈上格式化，避免先分配临时字符串再复制到共享块。
            fmt::basic_memory_buffer<char16_t, 128> text;
            fmt::format_to(std::back_inserter(text), pattern, args...);
            return make(level, start, end, std::u16string_view(text.data(), text.size()));
        }

        template<class... Args>
        static std::shared_ptr<ErrorReason> formatted(ErrorReasonLevel::ErrorReasonLevel level,
                                                      const TokensView &tokens, const char16_t *pattern, const Args &...args) {
            return formatted(level, tokens.startIndex, tokens.endIndex, pattern, args...);
        }

        //命令后面有多余部分
        [[maybe_unused]] static std::shared_ptr<ErrorReason> excess(size_t start,
                                                                    size_t end,
                                                                    std::u16string_view errorReason) {
            return make(ErrorReasonLevel::EXCESS, start, end, errorReason);
        }

        [[maybe_unused]] static std::shared_ptr<ErrorReason> excess(const TokensView &tokens,
                                                                    std::u16string_view errorReason) {
            return make(ErrorReasonLevel::EXCESS, tokens, errorReason);
        }

        //缺少空格
        [[maybe_unused]] static std::shared_ptr<ErrorReason>
        requireSpace(const TokensView &tokens) {
            return diagnostic(ErrorReasonLevel::REQUIRE_SPACE, tokens, ErrorReasonCode::RequireSpace);
        }

        [[maybe_unused]] static std::shared_ptr<ErrorReason>
        requireSpace(size_t start, size_t end) {
            return diagnostic(ErrorReasonLevel::REQUIRE_SPACE, start, end, ErrorReasonCode::RequireSpace);
        }

        //命令不完整
        [[maybe_unused]] static std::shared_ptr<ErrorReason>
        incomplete(size_t start, size_t end, std::u16string_view errorReason) {
            return make(ErrorReasonLevel::INCOMPLETE, start, end, errorReason);
        }

        [[maybe_unused]] static std::shared_ptr<ErrorReason>
        incomplete(const TokensView &tokens, std::u16string_view errorReason) {
            return make(ErrorReasonLevel::INCOMPLETE, tokens, errorReason);
        }

        //类型不匹配
        [[maybe_unused]] static std::shared_ptr<ErrorReason>
        typeError(size_t start, size_t end, std::u16string_view errorReason) {
            return make(ErrorReasonLevel::TYPE_ERROR, start, end, errorReason);
        }

        [[maybe_unused]] static std::shared_ptr<ErrorReason>
        typeError(const TokensView &tokens, std::u16string_view errorReason) {
            return make(ErrorReasonLevel::TYPE_ERROR, tokens, errorReason);
        }

        //内容不匹配
        [[maybe_unused]] static std::shared_ptr<ErrorReason>
        contentError(size_t start, size_t end, std::u16string_view errorReason) {
            return make(ErrorReasonLevel::CONTENT_ERROR, start, end, errorReason);
        }

        [[maybe_unused]] static std::shared_ptr<ErrorReason>
        contentError(const TokensView &tokens, std::u16string_view errorReason) {
            return make(ErrorReasonLevel::CONTENT_ERROR, tokens, errorReason);
        }

        //逻辑错误
        [[maybe_unused]] static std::shared_ptr<ErrorReason>
        logicError(size_t start, size_t end, std::u16string_view errorReason) {
            return make(ErrorReasonLevel::LOGIC_ERROR, start, end, errorReason);
        }

        [[maybe_unused]] static std::shared_ptr<ErrorReason>
        logicError(const TokensView &tokens, std::u16string_view errorReason) {
            return make(ErrorReasonLevel::LOGIC_ERROR, tokens, errorReason);
        }

        //ID错误
        [[maybe_unused]] static std::shared_ptr<ErrorReason>
        idError(size_t start, size_t end, std::u16string_view errorReason) {
            return make(ErrorReasonLevel::ID_ERROR, start, end, errorReason);
        }

        [[maybe_unused]] static std::shared_ptr<ErrorReason>
        idError(const TokensView &tokens, std::u16string_view errorReason) {
            return make(ErrorReasonLevel::ID_ERROR, tokens, errorReason);
        }

        bool operator==(const CHelper::ErrorReason &reason) const;
    };

}// namespace CHelper
