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

#include <array>
#include <atomic>
#include <chelper/parser/ErrorReasonFactory.h>
#include <chelper/parser/detail/ErrorReasonParameters.h>
#include <limits>

namespace CHelper {
    namespace Detail {
        class ErrorReasonMemoryResource {
            // 作用域持有一个引用，每次 allocate_shared 分配持有一个引用。
            // 分配器的临时复制不持有引用，弱引用存活时控制块仍保留分配引用。
            std::atomic<size_t> references{1};
            // 每个块独立共享所有权，保留一个错误不会保留整条长命令的所有错误。
            alignas(std::max_align_t) std::byte buffer[64 * 1024];
            std::pmr::monotonic_buffer_resource overflow{std::pmr::new_delete_resource()};
            size_t used = 0;

        public:
            // buffer 由分配后构造的对象写入，不需要在每个块创建时清零。
            ErrorReasonMemoryResource() {}

            void retain() noexcept {
                references.fetch_add(1, std::memory_order_relaxed);
            }

            void release() noexcept {
                if (references.fetch_sub(1, std::memory_order_acq_rel) == 1) delete this;
            }

            void *allocate(size_t bytes, size_t alignment) {
                // 仅所属线程的解析/查询作用域分配对象；跨线程释放不调用此资源。
                const size_t padding = (alignment - used % alignment) % alignment;
                if (alignment <= alignof(std::max_align_t) && padding <= sizeof(buffer) - used && bytes <= sizeof(buffer) - used - padding) [[likely]] {
                    auto *result = buffer + used + padding;
                    used += padding + bytes;
                    return result;
                }
                // 一个诊断的参数可能跨过块末尾，仍由原块持有，直到最后的弱引用释放。
                used = sizeof(buffer);
                return overflow.allocate(bytes, alignment);
            }

            bool isFull() const noexcept {
                return used >= sizeof(buffer) - 1024;
            }
        };

        template<class T>
        class ErrorReasonAllocator {
        public:
            using value_type = T;
            ErrorReasonMemoryResource *memory;

            explicit ErrorReasonAllocator(ErrorReasonMemoryResource *memory) noexcept
                : memory(memory) {}

            template<class U>
            ErrorReasonAllocator(const ErrorReasonAllocator<U> &other) noexcept
                : memory(other.memory) {}

            T *allocate(size_t count) {
                if (count > std::numeric_limits<size_t>::max() / sizeof(T)) {
                    throw std::bad_array_new_length();
                }
                if (memory == nullptr) return std::allocator<T>{}.allocate(count);
                auto *result = static_cast<T *>(memory->allocate(count * sizeof(T), alignof(T)));
                memory->retain();
                return result;
            }

            void deallocate(T *value, size_t count) noexcept {
                if (memory == nullptr) std::allocator<T>{}.deallocate(value, count);
                else
                    memory->release();
            }

            template<class U, class... Args>
            void construct(U *where, Args &&...args) {
                // placement new 在友元分配器中调用私有构造函数。
                ::new (static_cast<void *>(where)) U(std::forward<Args>(args)...);
            }

            template<class U>
            bool operator==(const ErrorReasonAllocator<U> &other) const noexcept {
                return memory == other.memory;
            }
        };

    }// namespace Detail

    namespace {
        thread_local ErrorReasonMemoryScope *currentMemoryScope = nullptr;
    }

    ErrorReasonMemoryScope::ErrorReasonMemoryScope() : previous(currentMemoryScope) { currentMemoryScope = this; }
    ErrorReasonMemoryScope::~ErrorReasonMemoryScope() {
        currentMemoryScope = previous;
        if (memory != nullptr) memory->release();
    }

    ErrorReason::ErrorReason(ErrorReasonLevel::ErrorReasonLevel level, size_t start, size_t end, ErrorReasonCode code)
        : code(code), messageReady(code == ErrorReasonCode::CustomText), level(level), start(start), end(end),
          errorReason(std::pmr::new_delete_resource()) {}

    namespace Detail {
        namespace {
            using namespace ErrorParameters;
            auto textFields(TypeNames &data) { return std::array{&data.expected, &data.actual}; }
            template<class P>
            auto textFields(P &data) {
                if constexpr (requires { data.text; }) return std::array{&data.text};
                else
                    return std::array<std::u16string_view *, 0>{};
            }
        }// namespace

        // 仅本文件定义该友元类，其他翻译单元没有可调用的构造入口。
        class ErrorReasonFactoryAccess {
        public:
            static std::shared_ptr<ErrorReason> allocate(ErrorReasonLevel::ErrorReasonLevel level, ErrorReasons::Range range, ErrorReasonCode code) {
                ErrorReasonMemoryResource *memory = nullptr;
                if (currentMemoryScope != nullptr) {
                    auto &current = currentMemoryScope->memory;
                    if (current == nullptr || current->isFull()) {
                        auto *next = new ErrorReasonMemoryResource;
                        if (current != nullptr) current->release();
                        current = next;
                    }
                    memory = current;
                }
                return std::allocate_shared<ErrorReason>(ErrorReasonAllocator<ErrorReason>(memory), level, range.start, range.end, code);
            }

            template<ErrorReasonCode Code>
            static std::shared_ptr<ErrorReason> make(ErrorReasonLevel::ErrorReasonLevel level, ErrorReasons::Range range, ErrorParameters::For<Code> data = {}) {
                auto result = allocate(level, range, Code);
                using P = ErrorParameters::For<Code>;
                if constexpr (!std::is_same_v<P, ErrorParameters::Empty>) {
                    static_assert(std::is_trivially_destructible_v<P>);
                    auto fields = textFields(data);
                    size_t bytes = sizeof(P);
                    for (const auto *text: fields) {
                        if (text->size() > (std::numeric_limits<size_t>::max() - bytes) / sizeof(char16_t)) throw std::bad_array_new_length();
                        bytes += text->size() * sizeof(char16_t);
                    }
                    std::byte *storage;
                    if (currentMemoryScope != nullptr && bytes <= 4096) {
                        storage = static_cast<std::byte *>(currentMemoryScope->memory->allocate(bytes, alignof(P)));
                    } else {
                        result->parameterStorage = std::make_unique_for_overwrite<std::byte[]>(bytes);
                        storage = result->parameterStorage.get();
                    }
                    auto *textStorage = reinterpret_cast<char16_t *>(storage + sizeof(P));
                    for (auto *text: fields) {
                        std::copy(text->begin(), text->end(), textStorage);
                        const size_t count = text->size();
                        *text = {textStorage, count};
                        textStorage += count;
                    }
                    result->parameters = std::construct_at(reinterpret_cast<P *>(storage), data);
                }
                return result;
            }

            static std::shared_ptr<ErrorReason> customText(ErrorReasonLevel::ErrorReasonLevel level, ErrorReasons::Range range, std::u16string_view text) {
                auto result = allocate(level, range, ErrorReasonCode::CustomText);
                result->errorReason.assign(text);
                return result;
            }

            static std::shared_ptr<ErrorReason> copy(const ErrorReason &source) {
                const ErrorReasons::Range range{source.start, source.end};
                std::shared_ptr<ErrorReason> result;
                switch (source.code) {
                    case ErrorReasonCode::CustomText:
                        return customText(source.level, range, source.errorReason);
                    case ErrorReasonCode::RequireSpace:
                        result = make<ErrorReasonCode::RequireSpace>(source.level, range);
                        break;
                    case ErrorReasonCode::RequireType:
                        result = make<ErrorReasonCode::RequireType>(source.level, range, source.parametersAs<ErrorParameters::For<ErrorReasonCode::RequireType>>());
                        break;
                    case ErrorReasonCode::TypeMismatch:
                        result = make<ErrorReasonCode::TypeMismatch>(source.level, range, source.parametersAs<ErrorParameters::For<ErrorReasonCode::TypeMismatch>>());
                        break;
                    case ErrorReasonCode::IntegerRequired:
                        result = make<ErrorReasonCode::IntegerRequired>(source.level, range);
                        break;
                    case ErrorReasonCode::InvalidNumber:
                        result = make<ErrorReasonCode::InvalidNumber>(source.level, range, source.parametersAs<ErrorParameters::For<ErrorReasonCode::InvalidNumber>>());
                        break;
                    case ErrorReasonCode::EmptyNull:
                        result = make<ErrorReasonCode::EmptyNull>(source.level, range);
                        break;
                    case ErrorReasonCode::InvalidNull:
                        result = make<ErrorReasonCode::InvalidNull>(source.level, range, source.parametersAs<ErrorParameters::For<ErrorReasonCode::InvalidNull>>());
                        break;
                    case ErrorReasonCode::EmptyString:
                        result = make<ErrorReasonCode::EmptyString>(source.level, range);
                        break;
                    case ErrorReasonCode::QuotedStringRequired:
                        result = make<ErrorReasonCode::QuotedStringRequired>(source.level, range, source.parametersAs<ErrorParameters::For<ErrorReasonCode::QuotedStringRequired>>());
                        break;
                    case ErrorReasonCode::EmptyCommandName:
                        result = make<ErrorReasonCode::EmptyCommandName>(source.level, range);
                        break;
                    case ErrorReasonCode::UnknownCommand:
                        result = make<ErrorReasonCode::UnknownCommand>(source.level, range, source.parametersAs<ErrorParameters::For<ErrorReasonCode::UnknownCommand>>());
                        break;
                    case ErrorReasonCode::Excess:
                        result = make<ErrorReasonCode::Excess>(source.level, range, source.parametersAs<ErrorParameters::For<ErrorReasonCode::Excess>>());
                        break;
                    case ErrorReasonCode::Incomplete:
                        result = make<ErrorReasonCode::Incomplete>(source.level, range);
                        break;
                    case ErrorReasonCode::UnknownMeaning:
                        result = make<ErrorReasonCode::UnknownMeaning>(source.level, range, source.parametersAs<ErrorParameters::For<ErrorReasonCode::UnknownMeaning>>());
                        break;
                    case ErrorReasonCode::InvalidCoordinate:
                        result = make<ErrorReasonCode::InvalidCoordinate>(source.level, range, source.parametersAs<ErrorParameters::For<ErrorReasonCode::InvalidCoordinate>>());
                        break;
                    case ErrorReasonCode::EmptyRange:
                        result = make<ErrorReasonCode::EmptyRange>(source.level, range);
                        break;
                    case ErrorReasonCode::InvalidRange:
                        result = make<ErrorReasonCode::InvalidRange>(source.level, range);
                        break;
                    case ErrorReasonCode::StringContainsSpace:
                        result = make<ErrorReasonCode::StringContainsSpace>(source.level, range);
                        break;
                    case ErrorReasonCode::UnclosedString:
                        result = make<ErrorReasonCode::UnclosedString>(source.level, range, source.parametersAs<ErrorParameters::For<ErrorReasonCode::UnclosedString>>());
                        break;
                    case ErrorReasonCode::UnexpectedSpace:
                        result = make<ErrorReasonCode::UnexpectedSpace>(source.level, range);
                        break;
                    case ErrorReasonCode::RequireSymbol:
                        result = make<ErrorReasonCode::RequireSymbol>(source.level, range, source.parametersAs<ErrorParameters::For<ErrorReasonCode::RequireSymbol>>());
                        break;
                    case ErrorReasonCode::SymbolTypeMismatch:
                        result = make<ErrorReasonCode::SymbolTypeMismatch>(source.level, range, source.parametersAs<ErrorParameters::For<ErrorReasonCode::SymbolTypeMismatch>>());
                        break;
                    case ErrorReasonCode::SymbolContentMismatch:
                        result = make<ErrorReasonCode::SymbolContentMismatch>(source.level, range, source.parametersAs<ErrorParameters::For<ErrorReasonCode::SymbolContentMismatch>>());
                        break;
                    case ErrorReasonCode::InvalidBoolean:
                        result = make<ErrorReasonCode::InvalidBoolean>(source.level, range, source.parametersAs<ErrorParameters::For<ErrorReasonCode::InvalidBoolean>>());
                        break;
                    case ErrorReasonCode::UnknownCommandName:
                        result = make<ErrorReasonCode::UnknownCommandName>(source.level, range, source.parametersAs<ErrorParameters::For<ErrorReasonCode::UnknownCommandName>>());
                        break;
                    case ErrorReasonCode::UnknownId:
                        result = make<ErrorReasonCode::UnknownId>(source.level, range, source.parametersAs<ErrorParameters::For<ErrorReasonCode::UnknownId>>());
                        break;
                    case ErrorReasonCode::MixedCoordinates:
                        result = make<ErrorReasonCode::MixedCoordinates>(source.level, range);
                        break;
                    case ErrorReasonCode::LocalCoordinateDisallowed:
                        result = make<ErrorReasonCode::LocalCoordinateDisallowed>(source.level, range);
                        break;
                    case ErrorReasonCode::UnknownSelectorArgument:
                        result = make<ErrorReasonCode::UnknownSelectorArgument>(source.level, range, source.parametersAs<ErrorParameters::For<ErrorReasonCode::UnknownSelectorArgument>>());
                        break;
                    case ErrorReasonCode::UnknownJsonArgument:
                        result = make<ErrorReasonCode::UnknownJsonArgument>(source.level, range, source.parametersAs<ErrorParameters::For<ErrorReasonCode::UnknownJsonArgument>>());
                        break;
                    case ErrorReasonCode::NumberOutOfRange:
                        result = make<ErrorReasonCode::NumberOutOfRange>(source.level, range, source.parametersAs<ErrorParameters::For<ErrorReasonCode::NumberOutOfRange>>());
                        break;
                    case ErrorReasonCode::JsonQuotesRequired:
                        result = make<ErrorReasonCode::JsonQuotesRequired>(source.level, range);
                        break;
                    case ErrorReasonCode::IncompleteEscape:
                        result = make<ErrorReasonCode::IncompleteEscape>(source.level, range);
                        break;
                    case ErrorReasonCode::IncompleteUnicodeEscape:
                        result = make<ErrorReasonCode::IncompleteUnicodeEscape>(source.level, range, source.parametersAs<ErrorParameters::For<ErrorReasonCode::IncompleteUnicodeEscape>>());
                        break;
                    case ErrorReasonCode::InvalidUnicodeEscapeCharacter:
                        result = make<ErrorReasonCode::InvalidUnicodeEscapeCharacter>(source.level, range, source.parametersAs<ErrorParameters::For<ErrorReasonCode::InvalidUnicodeEscapeCharacter>>());
                        break;
                    case ErrorReasonCode::InvalidUnicodeEscapeValue:
                        result = make<ErrorReasonCode::InvalidUnicodeEscapeValue>(source.level, range, source.parametersAs<ErrorParameters::For<ErrorReasonCode::InvalidUnicodeEscapeValue>>());
                        break;
                    case ErrorReasonCode::UnknownEscape:
                        result = make<ErrorReasonCode::UnknownEscape>(source.level, range, source.parametersAs<ErrorParameters::For<ErrorReasonCode::UnknownEscape>>());
                        break;
                    case ErrorReasonCode::NumberOutOfRangeInt32:
                        result = make<ErrorReasonCode::NumberOutOfRangeInt32>(source.level, range, source.parametersAs<ErrorParameters::For<ErrorReasonCode::NumberOutOfRangeInt32>>());
                        break;
                    case ErrorReasonCode::NumberOutOfRangeInt64:
                        result = make<ErrorReasonCode::NumberOutOfRangeInt64>(source.level, range, source.parametersAs<ErrorParameters::For<ErrorReasonCode::NumberOutOfRangeInt64>>());
                        break;
                    case ErrorReasonCode::NumberOutOfRangeUInt64:
                        result = make<ErrorReasonCode::NumberOutOfRangeUInt64>(source.level, range, source.parametersAs<ErrorParameters::For<ErrorReasonCode::NumberOutOfRangeUInt64>>());
                        break;
                    case ErrorReasonCode::NumberOutOfRangeFloat:
                        result = make<ErrorReasonCode::NumberOutOfRangeFloat>(source.level, range, source.parametersAs<ErrorParameters::For<ErrorReasonCode::NumberOutOfRangeFloat>>());
                        break;
                    case ErrorReasonCode::RequireTypeName:
                        result = make<ErrorReasonCode::RequireTypeName>(source.level, range, source.parametersAs<ErrorParameters::For<ErrorReasonCode::RequireTypeName>>());
                        break;
                    case ErrorReasonCode::TypeMismatchName:
                        result = make<ErrorReasonCode::TypeMismatchName>(source.level, range, source.parametersAs<ErrorParameters::For<ErrorReasonCode::TypeMismatchName>>());
                        break;
                }
                result->messageReady = source.messageReady;
                result->errorReason = source.errorReason;
                return result;
            }

            static void materialize(ErrorReason &result, const ErrorReason &source) {
                result.errorReason = source.getMessage();
                result.messageReady = true;
            }
        };
    }// namespace Detail

    std::shared_ptr<ErrorReason> ErrorReason::materializedCopy() const {
        auto result = ErrorReasons::copy(*this);
        Detail::ErrorReasonFactoryAccess::materialize(*result, *this);
        return result;
    }

    namespace ErrorReasons {
        std::shared_ptr<ErrorReason> customText(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text) {
            return Detail::ErrorReasonFactoryAccess::customText(level, range, text);
        }
        std::shared_ptr<ErrorReason> copy(const ErrorReason &source) { return Detail::ErrorReasonFactoryAccess::copy(source); }
        std::shared_ptr<ErrorReason> requireSpace(ErrorReasonLevel::ErrorReasonLevel level, Range range) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::RequireSpace>(level, range);
        }
        std::shared_ptr<ErrorReason> requireType(ErrorReasonLevel::ErrorReasonLevel level, Range range, ErrorReasonExpectedType expected) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::RequireType>(level, range, {expected});
        }
        std::shared_ptr<ErrorReason> typeMismatch(ErrorReasonLevel::ErrorReasonLevel level, Range range, ErrorReasonExpectedType expected, TokenType::TokenType actual) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::TypeMismatch>(level, range, {expected, actual});
        }
        std::shared_ptr<ErrorReason> integerRequired(ErrorReasonLevel::ErrorReasonLevel level, Range range) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::IntegerRequired>(level, range);
        }
        std::shared_ptr<ErrorReason> invalidNumber(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::InvalidNumber>(level, range, {text});
        }
        std::shared_ptr<ErrorReason> emptyNull(ErrorReasonLevel::ErrorReasonLevel level, Range range) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::EmptyNull>(level, range);
        }
        std::shared_ptr<ErrorReason> invalidNull(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::InvalidNull>(level, range, {text});
        }
        std::shared_ptr<ErrorReason> emptyString(ErrorReasonLevel::ErrorReasonLevel level, Range range) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::EmptyString>(level, range);
        }
        std::shared_ptr<ErrorReason> quotedStringRequired(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::QuotedStringRequired>(level, range, {text});
        }
        std::shared_ptr<ErrorReason> emptyCommandName(ErrorReasonLevel::ErrorReasonLevel level, Range range) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::EmptyCommandName>(level, range);
        }
        std::shared_ptr<ErrorReason> unknownCommand(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::UnknownCommand>(level, range, {text});
        }
        std::shared_ptr<ErrorReason> excess(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::Excess>(level, range, {text});
        }
        std::shared_ptr<ErrorReason> incomplete(ErrorReasonLevel::ErrorReasonLevel level, Range range) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::Incomplete>(level, range);
        }
        std::shared_ptr<ErrorReason> unknownMeaning(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::UnknownMeaning>(level, range, {text});
        }
        std::shared_ptr<ErrorReason> invalidCoordinate(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::InvalidCoordinate>(level, range, {text});
        }
        std::shared_ptr<ErrorReason> emptyRange(ErrorReasonLevel::ErrorReasonLevel level, Range range) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::EmptyRange>(level, range);
        }
        std::shared_ptr<ErrorReason> invalidRange(ErrorReasonLevel::ErrorReasonLevel level, Range range) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::InvalidRange>(level, range);
        }
        std::shared_ptr<ErrorReason> stringContainsSpace(ErrorReasonLevel::ErrorReasonLevel level, Range range) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::StringContainsSpace>(level, range);
        }
        std::shared_ptr<ErrorReason> unclosedString(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::UnclosedString>(level, range, {text});
        }
        std::shared_ptr<ErrorReason> unexpectedSpace(ErrorReasonLevel::ErrorReasonLevel level, Range range) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::UnexpectedSpace>(level, range);
        }
        std::shared_ptr<ErrorReason> requireSymbol(ErrorReasonLevel::ErrorReasonLevel level, Range range, char16_t character) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::RequireSymbol>(level, range, {character});
        }
        std::shared_ptr<ErrorReason> symbolTypeMismatch(ErrorReasonLevel::ErrorReasonLevel level, Range range, char16_t character, std::u16string_view text) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::SymbolTypeMismatch>(level, range, {character, text});
        }
        std::shared_ptr<ErrorReason> symbolContentMismatch(ErrorReasonLevel::ErrorReasonLevel level, Range range, char16_t character, std::u16string_view text) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::SymbolContentMismatch>(level, range, {character, text});
        }
        std::shared_ptr<ErrorReason> invalidBoolean(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::InvalidBoolean>(level, range, {text});
        }
        std::shared_ptr<ErrorReason> unknownCommandName(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::UnknownCommandName>(level, range, {text});
        }
        std::shared_ptr<ErrorReason> unknownId(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::UnknownId>(level, range, {text});
        }
        std::shared_ptr<ErrorReason> mixedCoordinates(ErrorReasonLevel::ErrorReasonLevel level, Range range) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::MixedCoordinates>(level, range);
        }
        std::shared_ptr<ErrorReason> localCoordinateDisallowed(ErrorReasonLevel::ErrorReasonLevel level, Range range) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::LocalCoordinateDisallowed>(level, range);
        }
        std::shared_ptr<ErrorReason> unknownSelectorArgument(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::UnknownSelectorArgument>(level, range, {text});
        }
        std::shared_ptr<ErrorReason> unknownJsonArgument(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::UnknownJsonArgument>(level, range, {text});
        }
        std::shared_ptr<ErrorReason> numberOutOfRange(ErrorReasonLevel::ErrorReasonLevel level, Range range, double min, double max, std::u16string_view text) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::NumberOutOfRange>(level, range, {min, max, text});
        }
        std::shared_ptr<ErrorReason> jsonQuotesRequired(ErrorReasonLevel::ErrorReasonLevel level, Range range) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::JsonQuotesRequired>(level, range);
        }
        std::shared_ptr<ErrorReason> incompleteEscape(ErrorReasonLevel::ErrorReasonLevel level, Range range) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::IncompleteEscape>(level, range);
        }
        std::shared_ptr<ErrorReason> incompleteUnicodeEscape(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::IncompleteUnicodeEscape>(level, range, {text});
        }
        std::shared_ptr<ErrorReason> invalidUnicodeEscapeCharacter(ErrorReasonLevel::ErrorReasonLevel level, Range range, char16_t character, std::u16string_view text) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::InvalidUnicodeEscapeCharacter>(level, range, {character, text});
        }
        std::shared_ptr<ErrorReason> invalidUnicodeEscapeValue(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::InvalidUnicodeEscapeValue>(level, range, {text});
        }
        std::shared_ptr<ErrorReason> unknownEscape(ErrorReasonLevel::ErrorReasonLevel level, Range range, char16_t character) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::UnknownEscape>(level, range, {character});
        }
        std::shared_ptr<ErrorReason> numberOutOfRange(ErrorReasonLevel::ErrorReasonLevel level, Range range, int32_t min, int32_t max, std::u16string_view text) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::NumberOutOfRangeInt32>(level, range, {min, max, text});
        }
        std::shared_ptr<ErrorReason> numberOutOfRange(ErrorReasonLevel::ErrorReasonLevel level, Range range, int64_t min, int64_t max, std::u16string_view text) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::NumberOutOfRangeInt64>(level, range, {min, max, text});
        }
        std::shared_ptr<ErrorReason> numberOutOfRange(ErrorReasonLevel::ErrorReasonLevel level, Range range, uint64_t min, uint64_t max, std::u16string_view text) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::NumberOutOfRangeUInt64>(level, range, {min, max, text});
        }
        std::shared_ptr<ErrorReason> numberOutOfRange(ErrorReasonLevel::ErrorReasonLevel level, Range range, float min, float max, std::u16string_view text) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::NumberOutOfRangeFloat>(level, range, {min, max, text});
        }
        std::shared_ptr<ErrorReason> requireType(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::RequireTypeName>(level, range, {text});
        }
        std::shared_ptr<ErrorReason> typeMismatch(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view expected, std::u16string_view actual) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::TypeMismatchName>(level, range, {expected, actual});
        }
    }// namespace ErrorReasons
}// namespace CHelper
