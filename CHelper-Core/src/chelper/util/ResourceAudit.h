// Allocation instrumentation is compiled only into dedicated resource audits.
#pragma once

#include <memory_resource>

namespace CHelper {
#if defined(CHELPER_RESOURCE_AUDIT)
    class MonotonicResource final : public std::pmr::memory_resource {
        std::pmr::monotonic_buffer_resource storage;
        size_t allocations = 0;
        size_t requestedBytes = 0;

        void *do_allocate(size_t bytes, size_t alignment) override {
            auto *result = storage.allocate(bytes, alignment);
            ++allocations;
            requestedBytes += bytes;
            return result;
        }
        void do_deallocate(void *pointer, size_t bytes, size_t alignment) override {
            storage.deallocate(pointer, bytes, alignment);
        }
        bool do_is_equal(const std::pmr::memory_resource &other) const noexcept override { return this == &other; }

    public:
        explicit MonotonicResource(std::pmr::memory_resource *upstream) : storage(upstream) {}
        MonotonicResource(void *buffer, size_t bytes, std::pmr::memory_resource *upstream) : storage(buffer, bytes, upstream) {}
        struct Snapshot {
            size_t allocations;
            size_t requestedBytes;
            bool operator==(const Snapshot &) const = default;
        };
        [[nodiscard]] Snapshot snapshot() const noexcept { return {allocations, requestedBytes}; }
    };
#else
    using MonotonicResource = std::pmr::monotonic_buffer_resource;
#endif
}// namespace CHelper
