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

#ifndef CHELPER_BENCHHELPER_H
#define CHELPER_BENCHHELPER_H

#include <chelper/CHelperCore.h>
#include <chelper/serialization/Serialization.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <functional>
#include <intrin.h>
#include <memory>
#include <new>
#include <string_view>
#include <vector>
#ifndef _MSC_VER
#include <malloc.h>
#else
#define NOMINMAX
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#endif

/**
 * 性能基准工具：测量 CPack 加载、卸载与单次命令请求各阶段的耗时和堆分配
 *
 * 不是正确性测试，而是给优化提供可复现的量化依据：
 * 1. 每个阶段不只报平均耗时，还报 p50/p95/max，避免被偶发毛刺误导
 * 2. 同时报该阶段调用 operator new 的次数与净增字节数，区分"慢在分配"还是"慢在计算"
 * 3. 样例取自真实资源包，不使用人造微基准
 *
 * 用例名统一以 Bench. 开头，可以单独运行：
 * CHelperTest --gtest_filter=Bench.*
 */

namespace CHelper::Test {

    // ================= 堆分配计数 =================
    // 计数开关关闭时只有两次 relaxed 原子读，不会明显改变被测量阶段本身的耗时
    namespace Detail {
        inline std::atomic<bool> gCounting{false};
        inline std::atomic<int64_t> gAllocCalls{0};
        inline std::atomic<int64_t> gNetBytes{0};
    }// namespace Detail

    struct AllocSnapshot {
        /// 该阶段调用 operator new 的次数（分配频次）
        int64_t allocCalls = 0;
        /// 该阶段净新增的堆字节数（分配减释放，即常驻增量；销毁阶段为负）
        int64_t netBytes = 0;
    };

    inline void startAllocCounting() {
        Detail::gAllocCalls.store(0, std::memory_order_relaxed);
        Detail::gNetBytes.store(0, std::memory_order_relaxed);
        Detail::gCounting.store(true, std::memory_order_relaxed);
    }

    inline AllocSnapshot stopAllocCounting() {
        Detail::gCounting.store(false, std::memory_order_relaxed);
        return AllocSnapshot{Detail::gAllocCalls.load(std::memory_order_relaxed),
                             Detail::gNetBytes.load(std::memory_order_relaxed)};
    }

    // ================= 统计工具 =================

    struct Stats {
        std::string_view name;
        size_t count = 0;
        double totalMs = 0;
        double minMs = 0;
        double maxMs = 0;
        int64_t allocCalls = 0;
        int64_t netBytes = 0;
        std::vector<double> samplesMs;

        void add(double ms, const AllocSnapshot &allocs) {
            if (count == 0 || ms < minMs) {
                minMs = ms;
            }
            if (count == 0 || ms > maxMs) {
                maxMs = ms;
            }
            totalMs += ms;
            allocCalls += allocs.allocCalls;
            netBytes += allocs.netBytes;
            samplesMs.push_back(ms);
            ++count;
        }

        [[nodiscard]] double percentile(double p) const {
            if (samplesMs.empty()) {
                return 0;
            }
            std::vector<double> sorted = samplesMs;
            std::sort(sorted.begin(), sorted.end());
            const size_t index = std::min(sorted.size() - 1, size_t(double(sorted.size() - 1) * p));
            return sorted[index];
        }

        void print() const {
            std::printf("%-52s n=%-5zu avg %8.4f ms  p50 %8.4f  p95 %8.4f  max %8.4f  new %8.1f  net %8.1f KiB\n",
                        std::string(name).c_str(), count, count ? totalMs / double(count) : 0.0,
                        percentile(0.50), percentile(0.95), maxMs, count ? double(allocCalls) / double(count) : 0.0,
                        count ? double(netBytes) / double(count) / 1024.0 : 0.0);
        }

        void reset() {
            *this = Stats{name};
        }
    };

    /**
     * 预热若干次后重复测量；预热用来排除首次调用的一次性开销（惰性哈希、缓存、分配器预热）
     */
    inline void benchmark(Stats &stats, size_t repeat, const std::function<void()> &body) {
        for (size_t i = 0; i < std::max<size_t>(repeat / 10, 1); ++i) {
            body();
        }
        for (size_t i = 0; i < repeat; ++i) {
            startAllocCounting();
            const auto start = std::chrono::steady_clock::now();
            body();
            const auto end = std::chrono::steady_clock::now();
            stats.add(std::chrono::duration<double, std::milli>(end - start).count(), stopAllocCounting());
        }
    }

    inline std::vector<char> readBinaryFile(const std::filesystem::path &path) {
        std::ifstream stream(path, std::ios::binary | std::ios::ate);
        const auto size = static_cast<size_t>(stream.tellg());
        stream.seekg(0);
        std::vector<char> buffer(size);
        stream.read(buffer.data(), static_cast<std::streamsize>(size));
        return buffer;
    }

    // ================= 存活分配归属分析 =================
    /**
     * 按分配点记录每个存活指针；销毁时统计出"哪类分配点贡献了最多的 free
     * 次数与字节数"，用数据决定池化该覆盖哪些对象，而不是靠猜
     *
     * 内部必须零分配：它挂在 operator new/delete 上，一旦自己再分配就会递归。
     * 因此全部用启动时一次性分配的定长开放寻址表，不使用任何容器
     */
    namespace HeapProfile {

        constexpr size_t LIVE_CAPACITY = size_t(1) << 19;
        constexpr size_t SITE_CAPACITY = 512;
        constexpr uintptr_t EMPTY_KEY = 0;

        struct Tables {
            /// 指针 -> 分配点与大小
            std::unique_ptr<uintptr_t[]> ptrKey{new uintptr_t[LIVE_CAPACITY]()};
            std::unique_ptr<uintptr_t[]> ptrCallSite{new uintptr_t[LIVE_CAPACITY]()};
            std::unique_ptr<size_t[]> ptrSize{new size_t[LIVE_CAPACITY]()};

            /// 分配点 -> (净存活字节数, 净存活次数)
            std::unique_ptr<uintptr_t[]> siteKey{new uintptr_t[SITE_CAPACITY]()};
            std::unique_ptr<size_t[]> siteBytes{new size_t[SITE_CAPACITY]()};
            std::unique_ptr<size_t[]> siteCount{new size_t[SITE_CAPACITY]()};

            /// 释放点 -> (累计释放字节数, 累计释放次数)
            std::unique_ptr<uintptr_t[]> freeSiteKey{new uintptr_t[SITE_CAPACITY]()};
            std::unique_ptr<size_t[]> freeSiteBytes{new size_t[SITE_CAPACITY]()};
            std::unique_ptr<size_t[]> freeSiteCount{new size_t[SITE_CAPACITY]()};
        };

        inline bool enabled = false;
        inline Tables tables;
        inline size_t liveCount = 0;

        [[nodiscard]] inline size_t slotOf(const uintptr_t *keys, size_t capacity, uintptr_t key) {
            size_t slot = (key >> 4) & (capacity - 1);
            while (keys[slot] != EMPTY_KEY && keys[slot] != key) {
                slot = (slot + 1) & (capacity - 1);
            }
            return slot;
        }

        inline void addSite(uintptr_t *keys, size_t *bytes, size_t *counts, uintptr_t site, size_t size) {
            const size_t slot = slotOf(keys, SITE_CAPACITY, site);
            if (keys[slot] == EMPTY_KEY) {
                keys[slot] = site;
            }
            bytes[slot] += size;
            counts[slot] += 1;
        }

        inline void onAlloc(void *p, size_t size, const void *callSite) {
            if (!enabled) {
                return;
            }
            const auto key = reinterpret_cast<uintptr_t>(p);
            const size_t slot = slotOf(tables.ptrKey.get(), LIVE_CAPACITY, key);
            if (tables.ptrKey[slot] == EMPTY_KEY) {
                tables.ptrKey[slot] = key;
                ++liveCount;
            }
            tables.ptrCallSite[slot] = reinterpret_cast<uintptr_t>(callSite);
            tables.ptrSize[slot] = size;
            addSite(tables.siteKey.get(), tables.siteBytes.get(), tables.siteCount.get(),
                    reinterpret_cast<uintptr_t>(callSite), size);
        }

        inline void onFree(void *p, size_t blockSize, const void *callSite) {
            if (!enabled) {
                return;
            }
            const auto key = reinterpret_cast<uintptr_t>(p);
            const size_t slot = slotOf(tables.ptrKey.get(), LIVE_CAPACITY, key);
            if (tables.ptrKey[slot] != EMPTY_KEY) {
                // 从分配点计数里减掉该块，得到"净存活"
                const auto allocSite = tables.ptrCallSite[slot];
                const size_t allocSlot = slotOf(tables.siteKey.get(), SITE_CAPACITY, allocSite);
                if (tables.siteKey[allocSlot] != EMPTY_KEY) {
                    tables.siteBytes[allocSlot] -= tables.ptrSize[slot];
                    tables.siteCount[allocSlot] -= 1;
                }
                tables.ptrKey[slot] = EMPTY_KEY;
                --liveCount;
            }
            addSite(tables.freeSiteKey.get(), tables.freeSiteBytes.get(), tables.freeSiteCount.get(),
                    reinterpret_cast<uintptr_t>(callSite), blockSize);
        }

        inline void resetCounters() {
            std::fill_n(tables.ptrKey.get(), LIVE_CAPACITY, EMPTY_KEY);
            std::fill_n(tables.siteKey.get(), SITE_CAPACITY, EMPTY_KEY);
            std::fill_n(tables.siteBytes.get(), SITE_CAPACITY, size_t(0));
            std::fill_n(tables.siteCount.get(), SITE_CAPACITY, size_t(0));
            std::fill_n(tables.freeSiteKey.get(), SITE_CAPACITY, EMPTY_KEY);
            std::fill_n(tables.freeSiteBytes.get(), SITE_CAPACITY, size_t(0));
            std::fill_n(tables.freeSiteCount.get(), SITE_CAPACITY, size_t(0));
            liveCount = 0;
        }

        inline void printSites(const char *title, const uintptr_t *keys, const size_t *bytes, const size_t *counts,
                               size_t top) {
            // 输出 RVA 而不是绝对地址：ASLR 下配合运行时镜像基址即可换算成绝对地址
            const auto imageBase = reinterpret_cast<uintptr_t>(GetModuleHandleW(nullptr));
            std::printf("runtime image base: %llX\n", static_cast<unsigned long long>(imageBase));
            std::vector<std::pair<uintptr_t, std::pair<size_t, size_t>>> sorted;
            for (size_t i = 0; i < SITE_CAPACITY; ++i) {
                if (keys[i] != EMPTY_KEY && (counts[i] != 0 || bytes[i] != 0)) {
                    sorted.emplace_back(keys[i] - imageBase, std::make_pair(bytes[i], counts[i]));
                }
            }
            std::sort(sorted.begin(), sorted.end(), [](const auto &a, const auto &b) {
                return a.second.second > b.second.second;
            });
            std::printf("%s\n%-16s %12s %14s\n", title, "rva", "#", "bytes");
            for (size_t i = 0; i < std::min(top, sorted.size()); ++i) {
                std::printf("%-16llX %12zu %14zu\n", static_cast<unsigned long long>(sorted[i].first),
                            sorted[i].second.second, sorted[i].second.first);
            }
            std::fflush(stdout);
        }

        inline void print(size_t top = 20) {
            size_t liveBytes = 0;
            for (size_t i = 0; i < LIVE_CAPACITY; ++i) {
                if (tables.ptrKey[i] != EMPTY_KEY) {
                    liveBytes += tables.ptrSize[i];
                }
            }
            std::printf("live: %zu blocks, %zu bytes (%.1f KiB)\n", liveCount, liveBytes, double(liveBytes) / 1024.0);
            printSites("--- live allocations by allocation site ---", tables.siteKey.get(), tables.siteBytes.get(),
                       tables.siteCount.get(), top);
            printSites("--- frees by deallocation site ---", tables.freeSiteKey.get(), tables.freeSiteBytes.get(),
                       tables.freeSiteCount.get(), top);
        }

    }// namespace HeapProfile

    /**
     * 覆盖命令补全界面的典型输入：短命令、长 execute 链、JSON 组件、目标选择器、
     * 以及输入中途的不完整命令（补全和错误提示的主要场景）
     */
    inline const std::vector<std::u16string> &benchCommands() {
        static const std::vector<std::u16string> commands{
                uR"(list)",
                uR"(give @s stone)",
                uR"(give @s )",
                uR"(give )",
                uR"(give @s command_block 12 12 {"minecraft:can_destroy":{"blocks":["minecraft:acacia_door"]}})",
                uR"(give @s repeating_command_block 1 1 {"minecraft:can_place_on":{"blocks":["minecraft:acacia_door","minecraft:acacia_fence"]}})",
                uR"(execute as @a at @s if block ~~~ stone if block ~~~ stone if block ~~~ stone run setblock ~~~ air)",
                uR"(execute as @a at @s if block ~~~ stone if block ~~~ stone run )",
                uR"(tag @e[type=zombie,r=16] add test)",
                uR"(tellraw @a {"rawtext":[{"text":"hello 世界"}]})",
                uR"(give @s s)",
                uR"(setblock ~~~ candle_cake[lit=)",
                uR"(not_a_command)"};
        return commands;
    }

}// namespace CHelper::Test

// ================= 全局 operator new / delete =================
// 定义在头文件中，由测试可执行文件提供（测试目标是本项目唯一的使用者）
inline void *chelperBenchAlloc(size_t size, const void *callSite) {
    if (CHelper::Test::Detail::gCounting.load(std::memory_order_relaxed)) {
        CHelper::Test::Detail::gAllocCalls.fetch_add(1, std::memory_order_relaxed);
        CHelper::Test::Detail::gNetBytes.fetch_add(static_cast<int64_t>(size), std::memory_order_relaxed);
    }
    void *p = std::malloc(size ? size : 1);
    if (!p) {
        throw std::bad_alloc();
    }
    CHelper::Test::HeapProfile::onAlloc(p, size, callSite);
    return p;
}

/**
 * 需要知道实际块大小才能累计释放量，用平台 API 取回，避免为每次分配额外维护一张表
 */
inline size_t chelperBenchBlockSize(void *p) {
#ifdef _MSC_VER
    return _msize(p);
#else
    return malloc_usable_size(p);
#endif
}

inline void chelperBenchFree(void *p, size_t size, const void *callSite) {
    if (p) {
        CHelper::Test::HeapProfile::onFree(p, size, callSite);
        if (CHelper::Test::Detail::gCounting.load(std::memory_order_relaxed)) {
            CHelper::Test::Detail::gNetBytes.fetch_sub(static_cast<int64_t>(size), std::memory_order_relaxed);
        }
        std::free(p);
    }
}

void *operator new(size_t size) {
    return chelperBenchAlloc(size, _ReturnAddress());
}

void *operator new[](size_t size) {
    return chelperBenchAlloc(size, _ReturnAddress());
}

void *operator new(size_t size, const std::nothrow_t &) noexcept {
    return chelperBenchAlloc(size, _ReturnAddress());
}

void *operator new[](size_t size, const std::nothrow_t &) noexcept {
    return chelperBenchAlloc(size, _ReturnAddress());
}

void operator delete(void *p) noexcept {
    chelperBenchFree(p, p ? chelperBenchBlockSize(p) : 0, _ReturnAddress());
}

void operator delete[](void *p) noexcept {
    chelperBenchFree(p, p ? chelperBenchBlockSize(p) : 0, _ReturnAddress());
}

void operator delete(void *p, size_t size) noexcept {
    chelperBenchFree(p, size, _ReturnAddress());
}

void operator delete[](void *p, size_t size) noexcept {
    chelperBenchFree(p, size, _ReturnAddress());
}

void operator delete(void *p, const std::nothrow_t &) noexcept {
    chelperBenchFree(p, p ? chelperBenchBlockSize(p) : 0, _ReturnAddress());
}

void operator delete[](void *p, const std::nothrow_t &) noexcept {
    chelperBenchFree(p, p ? chelperBenchBlockSize(p) : 0, _ReturnAddress());
}

#endif//CHELPER_BENCHHELPER_H
