/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026 Yancey
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

#pragma once

#include <chelper/parser/ErrorReason.h>
#include <chelper/util/CPackMemory.h>

namespace CHelper {

    // AST 的错误列表通常为空或只有一项。单项直接存放在节点内，多项才使用 PMR 数组。
    // 资源随列表保存，复制到另一个解析作用域时重新分配，释放不依赖当前线程路由。
    class ErrorReasonList {
    public:
        using value_type = std::shared_ptr<ErrorReason>;
        using allocator_type = std::pmr::polymorphic_allocator<value_type>;

    private:
        struct Array {
            value_type *data;
            size_t capacity;
        };
        union Storage {
            value_type single;
            Array array;
            Storage() : single() {}
            ~Storage() {}
        } storage;
        size_t count = 0;
        std::pmr::memory_resource *resource;

        void take(ErrorReasonList &other) noexcept {
            if (other.count > 1) {
                std::destroy_at(&storage.single);
                std::construct_at(&storage.array, other.storage.array);
                std::destroy_at(&other.storage.array);
                std::construct_at(&other.storage.single);
            } else {
                storage.single = std::move(other.storage.single);
            }
            count = std::exchange(other.count, 0);
        }

    public:
        explicit ErrorReasonList(std::pmr::memory_resource *resource = CPackMemoryRouter::getAllocationResource()) noexcept
            : resource(resource) {}

        ErrorReasonList(std::initializer_list<value_type> values,
                        std::pmr::memory_resource *resource = CPackMemoryRouter::getAllocationResource())
            : ErrorReasonList(resource) {
            for (const auto &value: values) push_back(value);
        }

        ErrorReasonList(const ErrorReasonList &other,
                        std::pmr::memory_resource *resource = CPackMemoryRouter::getAllocationResource())
            : ErrorReasonList(resource) {
            if (other.count <= 1) {
                storage.single = other.storage.single;
            } else {
                auto *data = get_allocator().allocate(other.count);
                std::uninitialized_copy(other.begin(), other.end(), data);
                std::destroy_at(&storage.single);
                std::construct_at(&storage.array, Array{data, other.count});
            }
            count = other.count;
        }

        ErrorReasonList(ErrorReasonList &&other) noexcept
            : ErrorReasonList(other.resource) {
            take(other);
        }

        ~ErrorReasonList() {
            // 析构无需恢复空列表状态，避免为每个 AST 写回空指针、计数和联合成员。
            if (count > 1) {
                std::destroy_n(storage.array.data, count);
                get_allocator().deallocate(storage.array.data, storage.array.capacity);
            } else {
                std::destroy_at(&storage.single);
            }
        }

        ErrorReasonList &operator=(const ErrorReasonList &other) {
            if (this != &other) {
                ErrorReasonList copy(other, resource);
                clear();
                take(copy);
            }
            return *this;
        }

        ErrorReasonList &operator=(ErrorReasonList &&other) {
            if (this != &other) {
                if (resource == other.resource) {
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
            ErrorReasonList copy(values, resource);
            clear();
            take(copy);
            return *this;
        }

        [[nodiscard]] allocator_type get_allocator() const noexcept { return allocator_type(resource); }
        [[nodiscard]] size_t size() const noexcept { return count; }
        [[nodiscard]] bool empty() const noexcept { return count == 0; }
        [[nodiscard]] value_type *begin() noexcept { return count > 1 ? storage.array.data : &storage.single; }
        [[nodiscard]] const value_type *begin() const noexcept { return count > 1 ? storage.array.data : &storage.single; }
        [[nodiscard]] value_type *end() noexcept { return begin() + count; }
        [[nodiscard]] const value_type *end() const noexcept { return begin() + count; }
        [[nodiscard]] value_type &operator[](size_t index) noexcept { return begin()[index]; }
        [[nodiscard]] const value_type &operator[](size_t index) const noexcept { return begin()[index]; }
        [[nodiscard]] value_type &front() noexcept { return *begin(); }
        [[nodiscard]] const value_type &front() const noexcept { return *begin(); }

        void clear() noexcept {
            if (count > 1) {
                std::destroy_n(storage.array.data, count);
                get_allocator().deallocate(storage.array.data, storage.array.capacity);
                std::destroy_at(&storage.array);
                std::construct_at(&storage.single);
            } else {
                storage.single.reset();
            }
            count = 0;
        }

        void push_back(const value_type &value) {
            if (count == 0) {
                storage.single = value;
            } else if (count == 1) {
                auto *data = get_allocator().allocate(2);
                // 先复制追加项，允许 value 引用本列表内的元素。
                std::construct_at(data + 1, value);
                std::construct_at(data, std::move(storage.single));
                std::destroy_at(&storage.single);
                std::construct_at(&storage.array, Array{data, 2});
            } else if (count == storage.array.capacity) {
                const auto maximum = std::allocator_traits<allocator_type>::max_size(get_allocator());
                if (count == maximum) throw std::length_error("too many error reasons");
                const auto capacity = count > maximum / 2 ? maximum : count * 2;
                auto *data = get_allocator().allocate(capacity);
                std::construct_at(data + count, value);
                for (size_t i = 0; i < count; ++i) std::construct_at(data + i, std::move(storage.array.data[i]));
                std::destroy_n(storage.array.data, count);
                get_allocator().deallocate(storage.array.data, storage.array.capacity);
                storage.array = {data, capacity};
            } else {
                std::construct_at(storage.array.data + count, value);
            }
            ++count;
        }
    };

}// namespace CHelper
