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

#ifndef CHELPER_CPACK_MEMORY_H
#define CHELPER_CPACK_MEMORY_H

#include <atomic>
#include <cstddef>
#include <memory>
#include <memory_resource>
#include <mutex>
#include <optional>
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
        std::pmr::monotonic_buffer_resource resource;

    public:
        CPackMemoryResource()
            : resource(std::pmr::new_delete_resource()) {}

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

    class CPackMemoryRouter final : public std::pmr::memory_resource {
    private:
        inline static std::once_flag installFlag;
        inline static std::atomic<std::pmr::memory_resource *> upstream = nullptr;
        inline static thread_local CPackMemoryResource *current = nullptr;
        inline static thread_local size_t scopeDepth = 0;

        [[nodiscard]] static CPackMemoryRouter &instance() noexcept {
            static CPackMemoryRouter router;
            return router;
        }

        void *do_allocate(const size_t bytes, const size_t alignment) override {
            return current == nullptr ? upstream.load(std::memory_order_acquire)->allocate(bytes, alignment)
                                      : current->allocate(bytes, alignment);
        }

        void do_deallocate(void *pointer, const size_t bytes, const size_t alignment) override {
            if (current == nullptr) {
                upstream.load(std::memory_order_acquire)->deallocate(pointer, bytes, alignment);
            } else {
                current->deallocate(pointer, bytes, alignment);
            }
        }

        [[nodiscard]] bool do_is_equal(const std::pmr::memory_resource &other) const noexcept override {
            return this == &other;
        }

    public:
        static void install() {
            std::call_once(installFlag, [] {
                upstream.store(std::pmr::get_default_resource(), std::memory_order_release);
                std::pmr::set_default_resource(&instance());
            });
        }

        static void setCurrent(CPackMemoryResource *memory) noexcept {
            current = memory;
        }

        [[nodiscard]] static CPackMemoryResource *getCurrent() noexcept {
            return current;
        }

        [[nodiscard]] static size_t enter(CPackMemoryResource *memory) noexcept {
            setCurrent(memory);
            return ++scopeDepth;
        }

        static void leave(const size_t depth, CPackMemoryResource *previous) noexcept {
            if (scopeDepth == depth) {
                setCurrent(previous);
            }
            --scopeDepth;
        }

        static void leaveOwner() noexcept {
            setCurrent(nullptr);
            --scopeDepth;
        }
    };

    /**
     * Makes default-constructed PMR containers created while loading a CPack
     * use that CPack's resource. The owner scope remains active until all of
     * its PMR members have been destroyed.
     */
    class CPackMemoryScope {
    private:
        CPackMemoryResource *previous = nullptr;
        std::shared_ptr<CPackMemoryResource> memory;
        size_t depth = 0;
        bool active = false;
        bool owner = false;

    public:
        CPackMemoryScope() = default;

        explicit CPackMemoryScope(const std::shared_ptr<CPackMemoryResource> &memory)
            : previous(CPackMemoryRouter::getCurrent()), memory(memory), active(true) {
            CPackMemoryRouter::install();
            depth = CPackMemoryRouter::enter(this->memory.get());
        }

        void bindAsOwner(const std::shared_ptr<CPackMemoryResource> &ownerMemory) {
            memory = ownerMemory;
            previous = nullptr;
            active = true;
            owner = true;
            CPackMemoryRouter::install();
            depth = CPackMemoryRouter::enter(memory.get());
        }

        ~CPackMemoryScope() {
            if (active) {
                if (owner) {
                    CPackMemoryRouter::leaveOwner();
                } else {
                    CPackMemoryRouter::leave(depth, previous);
                }
            }
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
            if (!memory) [[unlikely]] {
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
        return std::allocate_shared<T>(CPackAllocator<T>(memory), std::forward<Args>(args)...);
    }

    template<class T, class... Args>
    [[nodiscard]] std::shared_ptr<T> allocateSharedFromDefault(Args &&...args) {
        return std::allocate_shared<T>(
                std::pmr::polymorphic_allocator<T>(std::pmr::get_default_resource()),
                std::forward<Args>(args)...);
    }

    template<class String>
    [[nodiscard]] std::optional<std::pmr::string> copyPmrStringOptional(const std::optional<String> &value) {
        if (!value.has_value()) {
            return std::nullopt;
        }
        return std::pmr::string(value->data(), value->size());
    }

    template<class String>
    [[nodiscard]] std::optional<std::pmr::u16string> copyPmrU16StringOptional(const std::optional<String> &value) {
        if (!value.has_value()) {
            return std::nullopt;
        }
        return std::pmr::u16string(value->data(), value->size());
    }

    template<class T>
    [[nodiscard]] std::shared_ptr<std::pmr::vector<T>> allocateSharedPmrVectorFromDefault(
            std::initializer_list<T> values = {}) {
        auto result = allocateSharedFromDefault<std::pmr::vector<T>>();
        result->insert(result->end(), values.begin(), values.end());
        return result;
    }

}// namespace CHelper

#endif// CHELPER_CPACK_MEMORY_H
