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
            // 只有分配作用域线程使用剩余份额；跨线程释放仍逐项原子减引用。
            size_t unusedReferences = 0;
            // 每个块独立共享所有权，保留一个错误不会保留整条长命令的所有错误。
            alignas(std::max_align_t) std::byte buffer[64 * 1024];
            std::pmr::monotonic_buffer_resource overflow{std::pmr::new_delete_resource()};
            size_t used = 0;

        public:
            // buffer 由分配后构造的对象写入，不需要在每个块创建时清零。
            ErrorReasonMemoryResource() {}

            void retain() noexcept {
                if (unusedReferences == 0) {
                    constexpr size_t batch = 64;
                    references.fetch_add(batch, std::memory_order_relaxed);
                    unusedReferences = batch;
                }
                --unusedReferences;
            }

            void release() noexcept {
                if (references.fetch_sub(1, std::memory_order_acq_rel) == 1) delete this;
            }

            void releaseOwner() noexcept {
                // 移除作用域引用和未使用的份额，留下所有活动强/弱控制块的引用。
                const size_t count = unusedReferences + 1;
                if (references.fetch_sub(count, std::memory_order_acq_rel) == count) delete this;
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
        if (memory != nullptr) memory->releaseOwner();
    }

    ErrorReason::ErrorReason(ErrorReasonLevel::ErrorReasonLevel level, size_t start, size_t end, ErrorReasonCode code)
        : code(code), level(level), start(start), end(end) {}

    namespace Detail {
        namespace {
            using namespace ErrorParameters;
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
                        if (current != nullptr) current->releaseOwner();
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
        };
    }// namespace Detail

    namespace ErrorReasons {
        std::shared_ptr<ErrorReason> customText(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text) {
            return Detail::ErrorReasonFactoryAccess::make<ErrorReasonCode::CustomText>(level, range, {text});
        }
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
    }// namespace ErrorReasons
}// namespace CHelper
