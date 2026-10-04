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
        class ErrorReasonFactoryAccess;
        template<class T>
        class ErrorReasonAllocator;
    }// namespace Detail

    // 作用域和每个错误的 shared_ptr 分配共同持有分块资源，查询结果可独立存活。
    class ErrorReasonMemoryScope {
        friend class Detail::ErrorReasonFactoryAccess;

        ErrorReasonMemoryScope *previous;
        Detail::ErrorReasonMemoryResource *memory = nullptr;

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
        NumberOutOfRangeInt32,
        NumberOutOfRangeInt64,
        NumberOutOfRangeUInt64,
        NumberOutOfRangeFloat,
    };

    // 类型名称按标识保存，展示时才转换为文本，避免为每个备选分支复制固定名称。
    enum class ErrorReasonExpectedType : uint8_t {
        String,
        Integer,
        Float,
        Symbol,
    };

    class ErrorReason {
        friend class Detail::ErrorReasonFactoryAccess;
        template<class T>
        friend class Detail::ErrorReasonAllocator;

        ErrorReasonCode code;

    public:
        // 字节大小的状态相邻，避免 level 单独产生一个指针对齐的填充区。
        ErrorReasonLevel::ErrorReasonLevel level;

    private:
        const void *parameters = nullptr;
        // 小参数与 shared_ptr 共用分块；长参数及无作用域的参数独立持有。
        std::unique_ptr<std::byte[]> parameterStorage;

        ErrorReason(ErrorReasonLevel::ErrorReasonLevel level, size_t start, size_t end, ErrorReasonCode code);
        template<class T>
        const T &parametersAs() const noexcept {
            return *static_cast<const T *>(parameters);
        }

    public:
        ErrorReason(const ErrorReason &) = delete;
        ErrorReason &operator=(const ErrorReason &) = delete;

        size_t start, end;
        ErrorReasonCode getCode() const noexcept { return code; }
        std::u16string getMessage() const;
        bool operator==(const ErrorReason &reason) const;
    };

}// namespace CHelper
