/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026 Yancey
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

#pragma once

#include <chelper/parser/ErrorReason.h>
#include <chelper/util/CPackMemory.h>
#include <cstdint>

namespace CHelper {

    // 空/单项列表不分配，多项的数量和容量放在数组头中，与元素共同分配。
    // 资源指针的两个对齐位保存状态，避免每个 AST 再持有一个 size_t 计数。
    class ErrorReasonList {
    public:
        using value_type = std::shared_ptr<ErrorReason>;
        using allocator_type = std::pmr::polymorphic_allocator<value_type>;

    private:
        enum Mode : uintptr_t { Empty = 0,
                                Single = 1,
                                Multiple = 2 };
        static constexpr uintptr_t modeMask = 3;
        static_assert(alignof(std::pmr::memory_resource) > modeMask);

        struct alignas(value_type) Array {
            size_t count, capacity;
            value_type *data() noexcept { return reinterpret_cast<value_type *>(this + 1); }
            const value_type *data() const noexcept { return reinterpret_cast<const value_type *>(this + 1); }
        };
        union Storage {
            value_type single;
            Array *array;
            // Empty 时没有活动成员；Single/Multiple 的生命周期由状态转换显式管理。
            Storage() {}
            ~Storage() {}
        } storage;
        uintptr_t resourceAndMode;

        [[nodiscard]] Mode mode() const noexcept { return static_cast<Mode>(resourceAndMode & modeMask); }
        void setMode(Mode value) noexcept { resourceAndMode = (resourceAndMode & ~modeMask) | value; }
        [[nodiscard]] std::pmr::memory_resource *resource() const noexcept {
            return reinterpret_cast<std::pmr::memory_resource *>(resourceAndMode & ~modeMask);
        }
        static constexpr size_t maximumCapacity = (SIZE_MAX - sizeof(Array)) / sizeof(value_type);
        [[nodiscard]] Array *allocateArray(size_t capacity) {
            if (capacity > maximumCapacity) throw std::length_error("too many error reasons");
            auto *memory = resource()->allocate(sizeof(Array) + capacity * sizeof(value_type), alignof(Array));
            return std::construct_at(static_cast<Array *>(memory), Array{0, capacity});
        }
        void releaseArray(Array *array) noexcept {
            const auto bytes = sizeof(Array) + array->capacity * sizeof(value_type);
            std::destroy_n(array->data(), array->count);
            std::destroy_at(array);
            resource()->deallocate(array, bytes, alignof(Array));
        }
        void take(ErrorReasonList &other) noexcept {
            const auto otherMode = other.mode();
            if (otherMode == Multiple) {
                std::construct_at(&storage.array, other.storage.array);
                std::destroy_at(&other.storage.array);
            } else if (otherMode == Single) {
                std::construct_at(&storage.single, std::move(other.storage.single));
                std::destroy_at(&other.storage.single);
            }
            setMode(otherMode);
            other.setMode(Empty);
        }

    public:
        explicit ErrorReasonList(std::pmr::memory_resource *resource = CPackMemoryRouter::getAllocationResource()) noexcept
            : resourceAndMode(reinterpret_cast<uintptr_t>(resource)) {}

        ErrorReasonList(std::initializer_list<value_type> values,
                        std::pmr::memory_resource *resource = CPackMemoryRouter::getAllocationResource())
            : ErrorReasonList(resource) {
            for (const auto &value: values) push_back(value);
        }

        ErrorReasonList(const ErrorReasonList &other,
                        std::pmr::memory_resource *resource = CPackMemoryRouter::getAllocationResource())
            : ErrorReasonList(resource) {
            if (other.mode() == Multiple) {
                auto *array = allocateArray(other.size());
                std::uninitialized_copy(other.begin(), other.end(), array->data());
                array->count = other.size();
                std::construct_at(&storage.array, array);
            } else if (other.mode() == Single) {
                std::construct_at(&storage.single, other.storage.single);
            }
            setMode(other.mode());
        }

        ErrorReasonList(ErrorReasonList &&other) noexcept
            : ErrorReasonList(other.resource()) {
            take(other);
        }

        ~ErrorReasonList() {
            if (mode() == Multiple) releaseArray(storage.array);
            else if (mode() == Single)
                std::destroy_at(&storage.single);
        }

        ErrorReasonList &operator=(const ErrorReasonList &other) {
            if (this != &other) {
                ErrorReasonList copy(other, resource());
                clear();
                take(copy);
            }
            return *this;
        }

        ErrorReasonList &operator=(ErrorReasonList &&other) {
            if (this != &other) {
                if (resource() == other.resource()) {
                    clear();
                    take(other);
                } else {
                    *this = other;
                    other.clear();
                }
            }
            return *this;
        }

        ErrorReasonList &operator=(std::initializer_list<value_type> values) {
            ErrorReasonList copy(values, resource());
            clear();
            take(copy);
            return *this;
        }

        [[nodiscard]] allocator_type get_allocator() const noexcept { return allocator_type(resource()); }
        [[nodiscard]] size_t size() const noexcept { return mode() == Multiple ? storage.array->count : static_cast<size_t>(mode()); }
        [[nodiscard]] bool empty() const noexcept { return mode() == Empty; }
        [[nodiscard]] value_type *begin() noexcept { return mode() == Multiple ? storage.array->data() : &storage.single; }
        [[nodiscard]] const value_type *begin() const noexcept { return mode() == Multiple ? storage.array->data() : &storage.single; }
        [[nodiscard]] value_type *end() noexcept { return begin() + size(); }
        [[nodiscard]] const value_type *end() const noexcept { return begin() + size(); }
        [[nodiscard]] value_type &operator[](size_t index) noexcept { return begin()[index]; }
        [[nodiscard]] const value_type &operator[](size_t index) const noexcept { return begin()[index]; }
        [[nodiscard]] value_type &front() noexcept { return *begin(); }
        [[nodiscard]] const value_type &front() const noexcept { return *begin(); }

        void clear() noexcept {
            if (mode() == Multiple) {
                releaseArray(storage.array);
                std::destroy_at(&storage.array);
            } else if (mode() == Single) {
                std::destroy_at(&storage.single);
            }
            setMode(Empty);
        }

        // 按值接收保证自身元素追加及扩容时的所有权安全，包括空 shared_ptr。
        void push_back(value_type value) {
            switch (mode()) {
                case Empty:
                    std::construct_at(&storage.single, std::move(value));
                    setMode(Single);
                    return;
                case Single: {
                    auto *array = allocateArray(2);
                    std::construct_at(array->data() + 1, std::move(value));
                    std::construct_at(array->data(), std::move(storage.single));
                    array->count = 2;
                    std::destroy_at(&storage.single);
                    std::construct_at(&storage.array, array);
                    setMode(Multiple);
                    return;
                }
                case Multiple: {
                    auto *array = storage.array;
                    if (array->count == array->capacity) {
                        if (array->count == maximumCapacity) throw std::length_error("too many error reasons");
                        const auto capacity = array->count > maximumCapacity / 2 ? maximumCapacity : array->count * 2;
                        auto *next = allocateArray(capacity);
                        std::construct_at(next->data() + array->count, std::move(value));
                        std::uninitialized_move_n(array->data(), array->count, next->data());
                        next->count = array->count + 1;
                        releaseArray(array);
                        storage.array = next;
                    } else {
                        std::construct_at(array->data() + array->count, std::move(value));
                        ++array->count;
                    }
                    return;
                }
            }
        }
    };

}// namespace CHelper
