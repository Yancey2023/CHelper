/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Yancey
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

#pragma once

#include <chelper/util/ResourceAudit.h>

#include <algorithm>
#include <atomic>
#include <cstddef>
#include <limits>
#include <memory>
#include <memory_resource>
#include <mutex>
#include <new>
#include <optional>
#include <stdexcept>
#include <string>
#include <utility>

namespace CHelper {

    /**
     * Memory owned by one CPack. Objects whose lifetime is tied to a CPack can
     * share this resource and be reclaimed in one operation when the CPack is
     * destroyed.
     */
    class CPackMemoryResource {
    private:
        MonotonicResource resource{std::pmr::new_delete_resource()};

    public:
        CPackMemoryResource() = default;

        void *allocate(const size_t bytes, const size_t alignment) {
            return resource.allocate(bytes, alignment);
        }

        void deallocate(void *pointer, const size_t bytes, const size_t alignment) noexcept {
            resource.deallocate(pointer, bytes, alignment);
        }

        [[nodiscard]] std::pmr::memory_resource *getResource() noexcept {
            return &resource;
        }
    };

    // Only selects a resource at construction. Every allocator stores that actual
    // resource; the process-wide PMR default is never replaced.
    class CPackMemoryRouter {
        inline static thread_local std::pmr::memory_resource *current = nullptr;
        inline static thread_local size_t scopeDepth = 0;

    public:
        static void install() noexcept {}// Compatibility for existing scoped callers.
        static void setCurrent(std::pmr::memory_resource *memory) noexcept { current = memory; }
        [[nodiscard]] static std::pmr::memory_resource *getCurrent() noexcept { return current; }
        [[nodiscard]] static std::pmr::memory_resource *getAllocationResource() noexcept {
            return current != nullptr ? current : std::pmr::get_default_resource();
        }
        [[nodiscard]] static size_t enter(std::pmr::memory_resource *memory) noexcept {
            current = memory;
            return ++scopeDepth;
        }
        static void leave(size_t depth, std::pmr::memory_resource *previous) noexcept {
            if (scopeDepth == depth || scopeDepth == 0) current = previous;
            if (scopeDepth != 0) --scopeDepth;
        }
    };

    namespace Detail {
        struct CPackObjectAllocation {
            std::pmr::memory_resource *resource;
            void *allocation;
            size_t bytes;
            size_t alignment;
        };
    }// namespace Detail

    // 节点本体与其 PMR 成员一样使用当前池。分配头记录实际资源，delete 不依赖
    // 当时的线程路由；池资源必须活到节点析构完毕，与节点内 PMR 成员的生命周期一致。
    [[nodiscard]] inline void *allocateCPackObject(const size_t bytes, size_t alignment) {
        using Header = Detail::CPackObjectAllocation;
        alignment = std::max(alignment, alignof(Header));
        const size_t prefix = (sizeof(Header) + alignment - 1) & ~(alignment - 1);
        if (bytes > std::numeric_limits<size_t>::max() - prefix) [[unlikely]] {
            throw std::bad_alloc();
        }
        auto *resource = CPackMemoryRouter::getCurrent();
        const size_t total = prefix + bytes;
        void *storage;
        if (resource != nullptr) {
            storage = resource->allocate(total, alignment);
        } else if (alignment > __STDCPP_DEFAULT_NEW_ALIGNMENT__) {
            storage = ::operator new(total, std::align_val_t(alignment));
        } else {
            storage = ::operator new(total);
        }
        auto *allocation = static_cast<std::byte *>(storage);
        auto *object = allocation + prefix;
        std::construct_at(reinterpret_cast<Header *>(object - sizeof(Header)),
                          Header{resource, allocation, total, alignment});
        return object;
    }

    [[nodiscard]] inline void *allocateCPackObjectNothrow(const size_t bytes, const size_t alignment) noexcept {
        try {
            return allocateCPackObject(bytes, alignment);
        } catch (...) {
            return nullptr;
        }
    }

    inline void deallocateCPackObject(void *pointer) noexcept {
        if (pointer == nullptr) return;
        auto *header = reinterpret_cast<Detail::CPackObjectAllocation *>(
                static_cast<std::byte *>(pointer) - sizeof(Detail::CPackObjectAllocation));
        const auto allocation = *header;
        std::destroy_at(header);
        if (allocation.resource != nullptr) {
            allocation.resource->deallocate(allocation.allocation, allocation.bytes, allocation.alignment);
        } else if (allocation.alignment > __STDCPP_DEFAULT_NEW_ALIGNMENT__) {
            ::operator delete(allocation.allocation, std::align_val_t(allocation.alignment));
        } else {
            ::operator delete(allocation.allocation);
        }
    }

    // Selects the resource captured by project containers during construction.
    // Destruction always uses the captured allocator, without re-entering a scope.
    class CPackMemoryScope {
    private:
        std::pmr::memory_resource *previous = nullptr;
        std::shared_ptr<CPackMemoryResource> memory;
        size_t depth = 0;
        bool active = false;

    public:
        CPackMemoryScope() = default;

        explicit CPackMemoryScope(const std::shared_ptr<CPackMemoryResource> &memory)
            : previous(CPackMemoryRouter::getCurrent()), memory(memory), active(true) {
            depth = CPackMemoryRouter::enter(this->memory->getResource());
        }


        void release() noexcept {
            if (active) {
                CPackMemoryRouter::leave(depth, previous);
                active = false;
            }
        }


        ~CPackMemoryScope() {
            release();
        }

        CPackMemoryScope(const CPackMemoryScope &) = delete;
        CPackMemoryScope &operator=(const CPackMemoryScope &) = delete;
    };

    template<class T>
    class CPackAllocator {
    public:
        using value_type = T;
        using size_type = size_t;
        using difference_type = ptrdiff_t;

        template<class U>
        struct rebind {
            using other = CPackAllocator<U>;
        };

        std::shared_ptr<CPackMemoryResource> memory;

        CPackAllocator() = default;

        explicit CPackAllocator(std::shared_ptr<CPackMemoryResource> memory) noexcept
            : memory(std::move(memory)) {}

        template<class U>
        CPackAllocator(const CPackAllocator<U> &other) noexcept
            : memory(other.memory) {}

        [[nodiscard]] T *allocate(const size_type count) {
            if (!memory || count > std::numeric_limits<size_type>::max() / sizeof(T)) [[unlikely]] {
                throw std::bad_alloc();
            }
            return static_cast<T *>(memory->allocate(count * sizeof(T), alignof(T)));
        }

        void deallocate(T *, size_type) noexcept {}

        template<class U>
        [[nodiscard]] bool operator==(const CPackAllocator<U> &other) const noexcept {
            return memory == other.memory;
        }

        template<class U>
        [[nodiscard]] bool operator!=(const CPackAllocator<U> &other) const noexcept {
            return !(*this == other);
        }
    };

    template<class T, class... Args>
    [[nodiscard]] std::shared_ptr<T> allocateShared(const std::shared_ptr<CPackMemoryResource> &memory,
                                                    Args &&...args) {
        if constexpr (sizeof...(Args) == 0 && std::uses_allocator_v<T, std::pmr::polymorphic_allocator<std::byte>>) {
            return std::allocate_shared<T>(CPackAllocator<T>(memory), memory->getResource());
        } else {
            return std::allocate_shared<T>(CPackAllocator<T>(memory), std::forward<Args>(args)...);
        }
    }

    template<class T, class... Args>
    [[nodiscard]] std::shared_ptr<T> allocateSharedFromDefault(Args &&...args) {
        return std::allocate_shared<T>(
                std::pmr::polymorphic_allocator<T>(CPackMemoryRouter::getAllocationResource()),
                std::forward<Args>(args)...);
    }

    template<class T>
    T &emplaceScopedOptional(std::optional<T> &value) {
        if constexpr (std::uses_allocator_v<T, std::pmr::polymorphic_allocator<std::byte>>) {
            return value.emplace(CPackMemoryRouter::getAllocationResource());
        } else {
            return value.emplace();
        }
    }

    template<class String>
    [[nodiscard]] std::optional<std::pmr::string> copyPmrStringOptional(const std::optional<String> &value) {
        if (!value.has_value()) {
            return std::nullopt;
        }
        return std::pmr::string(value->data(), value->size(), CPackMemoryRouter::getAllocationResource());
    }

    template<class String>
    [[nodiscard]] std::optional<std::pmr::u16string> copyPmrU16StringOptional(const std::optional<String> &value) {
        if (!value.has_value()) {
            return std::nullopt;
        }
        return std::pmr::u16string(value->data(), value->size(), CPackMemoryRouter::getAllocationResource());
    }

    template<class T>
    [[nodiscard]] std::shared_ptr<std::pmr::vector<T>> allocateSharedPmrVectorFromDefault(
            std::initializer_list<T> values = {}) {
        auto result = allocateSharedFromDefault<std::pmr::vector<T>>();
        result->insert(result->end(), values.begin(), values.end());
        return result;
    }

}// namespace CHelper
