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
#include <fmt/args.h>
#include <limits>

namespace CHelper {

    namespace Detail {

        class ErrorReasonMemoryResource {
            // 每个块独立共享所有权，保留一个错误不会保留整条长命令的所有错误。
            alignas(std::max_align_t) std::byte buffer[64 * 1024];
            std::pmr::monotonic_buffer_resource resource{buffer, sizeof(buffer), std::pmr::new_delete_resource()};
            size_t used = 0;

        public:
            // buffer 由分配后构造的对象写入，不需要在每个块创建时清零。
            ErrorReasonMemoryResource() {}

            void *allocate(size_t bytes, size_t alignment) {
                // 仅所属线程的解析/查询作用域分配对象；跨线程释放不调用此资源。
                void *result = resource.allocate(bytes, alignment);
                used += bytes + alignment - 1;
                return result;
            }

            bool isFull() const noexcept {
                return used >= sizeof(buffer) - 1024;
            }
        };

        template<class T>
        class ErrorReasonAllocator {
        public:
            using value_type = T;
            std::shared_ptr<ErrorReasonMemoryResource> memory;

            explicit ErrorReasonAllocator(std::shared_ptr<ErrorReasonMemoryResource> memory) noexcept
                : memory(std::move(memory)) {}

            template<class U>
            ErrorReasonAllocator(const ErrorReasonAllocator<U> &other) noexcept
                : memory(other.memory) {}

            T *allocate(size_t count) {
                if (count > std::numeric_limits<size_t>::max() / sizeof(T)) {
                    throw std::bad_array_new_length();
                }
                return static_cast<T *>(memory->allocate(count * sizeof(T), alignof(T)));
            }

            void deallocate(T *, size_t) noexcept {}

            template<class U>
            bool operator==(const ErrorReasonAllocator<U> &other) const noexcept {
                return memory == other.memory;
            }
        };

    }// namespace Detail

    namespace {
        thread_local ErrorReasonMemoryScope *currentMemoryScope = nullptr;
    }

    ErrorReasonMemoryScope::ErrorReasonMemoryScope()
        : previous(currentMemoryScope) {
        currentMemoryScope = this;
    }

    ErrorReasonMemoryScope::~ErrorReasonMemoryScope() {
        currentMemoryScope = previous;
    }

    std::shared_ptr<ErrorReason> ErrorReason::make(ErrorReasonLevel::ErrorReasonLevel level,
                                                   size_t start, size_t end, std::u16string_view text) {
        if (currentMemoryScope == nullptr) {
            return std::make_shared<ErrorReason>(level, start, end, text);
        }
        auto &memory = currentMemoryScope->memory;
        if (!memory || memory->isFull()) {
            memory = std::make_shared<Detail::ErrorReasonMemoryResource>();
        }
        return std::allocate_shared<ErrorReason>(Detail::ErrorReasonAllocator<ErrorReason>(memory),
                                                 level, start, end, text);
    }

    namespace ErrorReasonLevel {

        ErrorReasonLevel maxLevel = ID_ERROR;

    }// namespace ErrorReasonLevel

    ErrorReason::ErrorReason(ErrorReasonLevel::ErrorReasonLevel level,
                             size_t start,
                             size_t end,
                             std::u16string_view errorReason)
        : level(level),
          start(start),
          end(end),
          errorReason(errorReason.data(), errorReason.size(), std::pmr::new_delete_resource()) {}

    ErrorReason::ErrorReason(ErrorReasonLevel::ErrorReasonLevel level,
                             const TokensView &tokens,
                             std::u16string_view errorReason)
        : level(level),
          start(tokens.startIndex),
          end(tokens.endIndex),
          errorReason(errorReason.data(), errorReason.size(), std::pmr::new_delete_resource()) {}

    ErrorReason::ErrorReason(const ErrorReason &other)
        : ErrorReason(other.level, other.start, other.end, other.errorReason) {
        code = other.code;
        messageReady = other.messageReady;
        copyArguments(other.arguments);
    }

    ErrorReason &ErrorReason::operator=(const ErrorReason &other) {
        if (this == &other) return *this;
        ErrorReason copy(other);
        std::swap(code, copy.code);
        std::swap(messageReady, copy.messageReady);
        std::swap(arguments, copy.arguments);
        argumentStorage.swap(copy.argumentStorage);
        std::swap(level, copy.level);
        std::swap(start, copy.start);
        std::swap(end, copy.end);
        errorReason.swap(copy.errorReason);
        return *this;
    }

    namespace {
        std::u16string_view messagePattern(ErrorReasonCode code) {
            switch (code) {
                case ErrorReasonCode::RequireSpace:
                    return u"命令不完整，缺少空格";
                case ErrorReasonCode::RequireType:
                    return u"命令不完整，需要的参数类型为{}";
                case ErrorReasonCode::TypeMismatch:
                    return u"类型不匹配，正确的参数类型为{}，但当前参数类型为{}";
                case ErrorReasonCode::IntegerRequired:
                    return u"类型不匹配，正确的参数类型为整数，但当前参数类型为小数";
                case ErrorReasonCode::InvalidNumber:
                    return u"数字格式错误 -> {}";
                case ErrorReasonCode::EmptyNull:
                    return u"null参数为空";
                case ErrorReasonCode::InvalidNull:
                    return u"内容不是null -> {}";
                case ErrorReasonCode::EmptyString:
                    return u"字符串参数内容为空";
                case ErrorReasonCode::QuotedStringRequired:
                    return u"字符串参数内容应该在双引号内 -> {}";
                case ErrorReasonCode::EmptyCommandName:
                    return u"命令名字为空";
                case ErrorReasonCode::UnknownCommand:
                    return u"命令名字不匹配，找不到名为{}的命令";
                case ErrorReasonCode::Excess:
                    return u"命令后面有多余部分 -> {}";
                case ErrorReasonCode::Incomplete:
                    return u"命令不完整";
                case ErrorReasonCode::UnknownMeaning:
                    return u"找不到含义 -> {}";
                case ErrorReasonCode::InvalidCoordinate:
                    return u"类型不匹配，{}不是有效的坐标参数";
                case ErrorReasonCode::EmptyRange:
                    return u"范围的数值为空";
                case ErrorReasonCode::InvalidRange:
                    return u"范围的数值格式不正确，检测非法字符";
                case ErrorReasonCode::StringContainsSpace:
                    return u"字符串参数内容不可以包含空格";
                case ErrorReasonCode::UnclosedString:
                    return u"字符串参数内容双引号不封闭 -> {}";
                case ErrorReasonCode::UnexpectedSpace:
                    return u"意外的空格";
                case ErrorReasonCode::RequireSymbol:
                    return u"命令不完整，需要符号{:c}";
                case ErrorReasonCode::SymbolTypeMismatch:
                    return u"类型不匹配，需要符号{:c}，但当前内容为{}";
                case ErrorReasonCode::SymbolContentMismatch:
                    return u"内容不匹配，正确的符号为{:c}，但当前内容为{}";
                case ErrorReasonCode::InvalidBoolean:
                    return u"内容不匹配，应该为布尔值，但当前内容为{}";
                case ErrorReasonCode::UnknownCommandName:
                    return u"找不到命令名 -> {}";
                case ErrorReasonCode::UnknownId:
                    return u"找不到ID -> {}";
                case ErrorReasonCode::MixedCoordinates:
                    return u"绝对坐标和相对坐标不能与局部坐标混用";
                case ErrorReasonCode::LocalCoordinateDisallowed:
                    return u"不能使用局部坐标";
                case ErrorReasonCode::UnknownSelectorArgument:
                    return u"未知的目标选择器参数 -> {}";
                case ErrorReasonCode::UnknownJsonArgument:
                    return u"未知的json参数 -> {}";
                case ErrorReasonCode::NumberOutOfRange:
                    return u"数值不在范围[{}, {}]内 -> {}";
                case ErrorReasonCode::JsonQuotesRequired:
                    return u"json字符串必须在双引号内";
                case ErrorReasonCode::IncompleteEscape:
                    return u"转义字符缺失后半部分";
                case ErrorReasonCode::IncompleteUnicodeEscape:
                    return u"字符串转义缺失后半部分 -> \\u{}";
                case ErrorReasonCode::InvalidUnicodeEscapeCharacter:
                    return u"字符串转义出现非法字符{} -> \\u{}";
                case ErrorReasonCode::InvalidUnicodeEscapeValue:
                    return u"字符串转义的Unicode值无效 -> \\u{}";
                case ErrorReasonCode::UnknownEscape:
                    return u"未知的转义字符 -> \\{:c}";
                case ErrorReasonCode::CustomText:
                    return {};
            }
            throw std::invalid_argument("Unknown error reason code");
        }

        struct MessageParts {
            std::array<std::u16string_view, 7> parts;
            size_t size = 0;

            void append(std::u16string_view text) {
                if (!text.empty()) parts[size++] = text;
            }
        };

        // 解析诊断只有字符串和字符参数。逐段比较其最终表示，不生成中间文本；
        // 同时支持自定义文本与结构化消息相等、参数内含模板分隔文本等情况。
        std::optional<MessageParts> messageParts(std::u16string_view pattern,
                                                 std::span<const ErrorReasonArgument> arguments) {
            MessageParts result;
            size_t position = 0, index = 0;
            while (position < pattern.size()) {
                size_t opening = pattern.find(u'{', position);
                if (opening == std::u16string_view::npos) {
                    result.append(pattern.substr(position));
                    break;
                }
                if (index >= arguments.size() || index >= 3) return std::nullopt;
                result.append(pattern.substr(position, opening - position));
                size_t closing = pattern.find(u'}', opening);
                if (closing == std::u16string_view::npos) return std::nullopt;
                auto placeholder = pattern.substr(opening, closing - opening + 1);
                const auto &argument = arguments[index++];
                if (const auto *text = std::get_if<std::u16string_view>(&argument); text && placeholder == u"{}") {
                    result.append(*text);
                } else if (const auto *character = std::get_if<char16_t>(&argument);
                           character && (placeholder == u"{}" || placeholder == u"{:c}")) {
                    result.append({character, 1});
                } else {
                    return std::nullopt;
                }
                position = closing + 1;
            }
            if (index != arguments.size()) return std::nullopt;
            return result;
        }

        bool equalParts(const MessageParts &left, const MessageParts &right) {
            size_t leftIndex = 0, rightIndex = 0, leftOffset = 0, rightOffset = 0;
            while (leftIndex < left.size && rightIndex < right.size) {
                const auto a = left.parts[leftIndex], b = right.parts[rightIndex];
                size_t count = std::min(a.size() - leftOffset, b.size() - rightOffset);
                if (std::char_traits<char16_t>::compare(a.data() + leftOffset, b.data() + rightOffset, count) != 0) return false;
                leftOffset += count;
                rightOffset += count;
                if (leftOffset == a.size()) {
                    ++leftIndex;
                    leftOffset = 0;
                }
                if (rightOffset == b.size()) {
                    ++rightIndex;
                    rightOffset = 0;
                }
            }
            return leftIndex == left.size && rightIndex == right.size;
        }
    }// namespace

    std::shared_ptr<ErrorReason> ErrorReason::diagnostic(ErrorReasonLevel::ErrorReasonLevel level,
                                                         size_t start, size_t end, ErrorReasonCode code,
                                                         std::initializer_list<ErrorReasonArgument> input) {
        return makeDiagnostic(level, start, end, code, {input.begin(), input.size()});
    }

    std::shared_ptr<ErrorReason> ErrorReason::makeDiagnostic(ErrorReasonLevel::ErrorReasonLevel level,
                                                             size_t start, size_t end, ErrorReasonCode code,
                                                             std::span<const ErrorReasonArgument> input) {
        if (code == ErrorReasonCode::CustomText) {
            throw std::invalid_argument("Custom error text must use ErrorReason::make");
        }
        auto result = make(level, start, end, u"");
        result->code = code;
        result->messageReady = false;
        result->copyArguments(input, currentMemoryScope == nullptr ? nullptr : currentMemoryScope->memory.get());
        return result;
    }

    void ErrorReason::copyArguments(std::span<const ErrorReasonArgument> input, Detail::ErrorReasonMemoryResource *memory) {
        if (input.empty()) return;
        size_t bytes = input.size() * sizeof(ErrorReasonArgument);
        for (const auto &argument: input) {
            if (const auto *text = std::get_if<std::u16string_view>(&argument)) {
                if (text->size() > (std::numeric_limits<size_t>::max() - bytes) / sizeof(char16_t)) {
                    throw std::bad_array_new_length();
                }
                bytes += text->size() * sizeof(char16_t);
            }
        }
        std::byte *storage;
        if (memory != nullptr && bytes <= 4096) {
            // make() 已选定当前块；控制块持有该块，参数与对象拥有相同的生命周期。
            storage = static_cast<std::byte *>(memory->allocate(bytes, alignof(ErrorReasonArgument)));
        } else {
            // 长尾参数单独分配，避免一个参数填满共享块后留下大段空闲空间。
            argumentStorage = std::make_unique_for_overwrite<std::byte[]>(bytes);
            storage = argumentStorage.get();
        }
        static_assert(std::is_trivially_destructible_v<ErrorReasonArgument>);
        auto *copied = reinterpret_cast<ErrorReasonArgument *>(storage);
        auto *textStorage = reinterpret_cast<char16_t *>(storage + input.size() * sizeof(ErrorReasonArgument));
        size_t index = 0;
        for (const auto &argument: input) {
            if (const auto *text = std::get_if<std::u16string_view>(&argument)) {
                std::copy(text->begin(), text->end(), textStorage);
                std::construct_at(copied + index, std::u16string_view(textStorage, text->size()));
                textStorage += text->size();
            } else {
                std::construct_at(copied + index, argument);
            }
            ++index;
        }
        arguments = {copied, input.size()};
    }

    std::u16string ErrorReason::getMessage() const {
        if (messageReady || !errorReason.empty()) {
            return {errorReason.data(), errorReason.size()};
        }
        auto pattern = messagePattern(code);
        if (arguments.empty()) return std::u16string(pattern);
        fmt::dynamic_format_arg_store<fmt::buffered_context<char16_t>> store;
        store.reserve(arguments.size(), 0);
        for (const auto &argument: arguments) {
            std::visit([&](const auto &value) {
                // basic_string_view 由诊断自身持有；fmt 无需为参数再次复制文本。
                if constexpr (std::is_same_v<std::decay_t<decltype(value)>, std::u16string_view>) {
                    store.push_back(fmt::basic_string_view<char16_t>(value.data(), value.size()));
                } else {
                    store.push_back(value);
                }
            },
                       argument);
        }
        return fmt::vformat(fmt::basic_string_view<char16_t>(pattern.data(), pattern.size()), store);
    }

    std::shared_ptr<ErrorReason> ErrorReason::materializedCopy() const {
        if (code == ErrorReasonCode::CustomText) return make(level, start, end, errorReason);
        auto result = makeDiagnostic(level, start, end, code, arguments);
        result->errorReason = getMessage();
        result->messageReady = true;
        return result;
    }

    bool ErrorReason::operator==(const ErrorReason &reason) const {
        if (start != reason.start || end != reason.end) return false;
        const bool ready = messageReady || !errorReason.empty();
        const bool otherReady = reason.messageReady || !reason.errorReason.empty();
        if (ready && otherReady) return errorReason == reason.errorReason;
        if (!ready && !otherReady && code == reason.code && std::ranges::equal(arguments, reason.arguments)) return true;
        const auto split = [](const ErrorReason &error, bool ready) -> std::optional<MessageParts> {
            if (!ready) return messageParts(messagePattern(error.code), error.arguments);
            MessageParts result;
            result.append(error.errorReason);
            return result;
        };
        const auto left = split(*this, ready), right = split(reason, otherReady);
        if (left && right) {
            return equalParts(*left, *right);
        }
        // 带数字范围的语义诊断不参与解析分支去重；对外比较仍保持 fmt 的数值语义。
        return getMessage() == reason.getMessage();
    }

}// namespace CHelper
