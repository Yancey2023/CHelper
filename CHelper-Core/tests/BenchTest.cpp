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

#include "BenchHelper.h"

#include <gtest/gtest.h>

// 只在测试可执行文件中替换全局分配器，避免把非标准 operator new/delete
// 放进头文件导致 MSVC C4595，同时保证所有测试翻译单元共用同一套计数器。
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

using namespace CHelper;
using namespace CHelper::Test;

namespace {

    [[nodiscard]] std::filesystem::path resourceDir() {
        return std::filesystem::path(RESOURCE_DIR);
    }

    [[nodiscard]] std::filesystem::path vanillaDir() {
        return resourceDir() / "resources" / "beta" / "vanilla";
    }

    [[nodiscard]] std::filesystem::path vanillaBin() {
        return resourceDir() / "generated" / "cpack" / "beta-vanilla-1.26.0.29.cpack";
    }

    [[nodiscard]] std::filesystem::path experimentBin() {
        return resourceDir() / "generated" / "cpack" / "beta-experiment-1.26.0.29.cpack";
    }

}// namespace

/**
 * CPack 加载：目录格式（开发期/桌面）与二进制格式（Android/Web 实际使用）
 */
TEST(Bench, LoadCPack) {
    const size_t repeat = 20;

    {
        Stats stats{"load cpack by directory (vanilla)"};
        benchmark(stats, repeat, [] {
            auto cpack = serialization::createCPackByDirectory(vanillaDir());
        });
        stats.print();
    }
    {
        std::vector<Stats> stats;
        for (const auto &path: {vanillaBin(), experimentBin()}) {
            if (!std::filesystem::exists(path)) {
                continue;
            }
            const auto data = readBinaryFile(path);
            std::string name = "load cpack by binary (" + path.stem().string() + ")";
            stats.emplace_back(Stats{name});
            auto &stat = stats.back();
            benchmark(stat, repeat, [&data] {
                auto cpack = serialization::createCPackByBinary(std::string_view(data.data(), data.size()));
            });
            stat.print();
        }
    }
}

/**
 * CPack 写出：目录 JSON、单文件 JSON 与二进制文件
 */
TEST(Bench, WriteCPack) {
    const size_t repeat = 20;
    const auto data = readBinaryFile(vanillaBin());
    const auto cpack = serialization::createCPackByBinary(std::string_view(data.data(), data.size()));
    const auto outputDir = resourceDir() / "generated" / "benchmark-output";
    // Windows 不允许 '?' 出现在文件名中，而 vanilla 的 help 命令正好使用 '?'
    // 作为第一个别名；仅在基准副本中换成等长的合法别名，避免写目录基准被平台文件名规则阻断。
    for (auto &command: *cpack->commands) {
        if (!command.name.empty() && command.name.front() == u"?") {
            command.name.front() = u"help";
        }
    }

    {
        Stats stats{"write cpack by directory (vanilla)"};
        benchmark(stats, repeat, [&] {
            cpack->writeJsonToDirectory(outputDir / "directory");
        });
        stats.print();
    }
    {
        Stats stats{"write cpack by json (vanilla)"};
        benchmark(stats, repeat, [&] {
            cpack->writeJsonToFile(outputDir / "vanilla.json");
        });
        stats.print();
    }
    {
        Stats stats{"write cpack by binary (vanilla)"};
        benchmark(stats, repeat, [&] {
            cpack->writeBinToFile(outputDir / "vanilla.cpack");
        });
        stats.print();
    }
}

/**
 * CPack 卸载：销毁整个资源包对象图的成本（网页版切换分支时会走这条路）
 */
TEST(Bench, UnloadCPack) {
    const size_t repeat = 20;

    {
        Stats stats{"destroy cpack (loaded by directory)"};
        for (size_t i = 0; i < repeat; ++i) {
            auto cpack = serialization::createCPackByDirectory(vanillaDir());
            startAllocCounting();
            const auto start = std::chrono::steady_clock::now();
            cpack.reset();
            const auto end = std::chrono::steady_clock::now();
            stats.add(std::chrono::duration<double, std::milli>(end - start).count(), stopAllocCounting());
        }
        stats.print();
    }
    {
        const auto path = vanillaBin();
        if (!std::filesystem::exists(path)) {
            return;
        }
        const auto data = readBinaryFile(path);
        Stats stats{"destroy cpack (loaded by binary)"};
        for (size_t i = 0; i < repeat; ++i) {
            auto cpack = serialization::createCPackByBinary(std::string_view(data.data(), data.size()));
            startAllocCounting();
            const auto start = std::chrono::steady_clock::now();
            cpack.reset();
            const auto end = std::chrono::steady_clock::now();
            stats.add(std::chrono::duration<double, std::milli>(end - start).count(), stopAllocCounting());
        }
        stats.print();
    }
}

/**
 * 单次请求：输入一个字符后补全界面要跑的全部阶段
 * 这是唯一的高频路径，逐阶段拆开看各自占多少
 */
TEST(Bench, RequestPhases) {
    std::shared_ptr<const CPack> cpack = serialization::createCPackByDirectory(vanillaDir());
    CHelperCore core(cpack);

    const size_t repeat = 200;
    Stats total{"total per keystroke"};
    Stats create{"  createContext"};
    Stats suggestions{"  getSuggestions"};
    Stats hint{"  getParamHint"};
    Stats structure{"  getStructure"};
    Stats syntax{"  getSyntaxResult"};
    Stats errors{"  getErrorReasons"};

    const auto &commands = benchCommands();
    for (const auto &command: commands) {
        const size_t cursor = command.size();
        benchmark(total, repeat, [&] {
            std::unique_ptr<CommandContext> context(core.createContext(command));
            (void) context->getSuggestions(cursor);
            (void) context->getParamHint(cursor);
            (void) context->getStructure();
            (void) context->getSyntaxResult();
            (void) context->getErrorReasons();
        });
        benchmark(create, repeat, [&] {
            std::unique_ptr<CommandContext> context(core.createContext(command));
        });
        // 以下阶段都在同一个 context 上重复调用，避免把解析开销算进来
        std::unique_ptr<CommandContext> context(core.createContext(command));
        benchmark(suggestions, repeat, [&] { (void) context->getSuggestions(cursor); });
        benchmark(hint, repeat, [&] { (void) context->getParamHint(cursor); });
        benchmark(structure, repeat, [&] { (void) context->getStructure(); });
        benchmark(syntax, repeat, [&] { (void) context->getSyntaxResult(); });
        benchmark(errors, repeat, [&] { (void) context->getErrorReasons(); });
    }

    std::printf("\n--- per keystroke phase breakdown (summed over %zu sample commands) ---\n", commands.size());
    total.print();
    create.print();
    suggestions.print();
    hint.print();
    structure.print();
    syntax.print();
    errors.print();
}

/**
 * 存活分配归属：加载一个 CPack，然后统计销毁过程中哪类分配点贡献了最多 free
 * 用来判断池化/复用到底该覆盖哪些对象（2 万次 free 占了卸载耗时的绝大部分）
 */
TEST(Bench, LiveAllocationAttribution) {
    HeapProfile::enabled = true;
    HeapProfile::resetCounters();

    {
        auto cpack = serialization::createCPackByDirectory(vanillaDir());
        std::printf("\n--- live allocations after load (live=%zu) ---\n", HeapProfile::liveCount);
        HeapProfile::print(20);

        std::printf("\n--- destroy ---\n");
        const auto start = std::chrono::steady_clock::now();
        cpack.reset();
        const auto end = std::chrono::steady_clock::now();
        std::printf("destroy took %.4f ms, still alive=%zu\n",
                    std::chrono::duration<double, std::milli>(end - start).count(), HeapProfile::liveCount);
    }

    HeapProfile::enabled = false;
    HeapProfile::resetCounters();
}

/**
 * 单个命令的逐阶段分解：找出哪种输入最贵
 */
TEST(Bench, RequestPerCommand) {
    std::shared_ptr<const CPack> cpack = serialization::createCPackByDirectory(vanillaDir());
    CHelperCore core(cpack);

    const size_t repeat = 200;
    const auto &commands = benchCommands();
    std::printf("\n--- per command breakdown ---\n");
    for (const auto &command: commands) {
        const size_t cursor = command.size();
        const std::string label = utf8::utf16to8(command);
        {
            Stats stats{"createContext | " + label};
            benchmark(stats, repeat, [&] {
                std::unique_ptr<CommandContext> context(core.createContext(command));
            });
            stats.print();
        }
        {
            std::unique_ptr<CommandContext> context(core.createContext(command));
            Stats stats{"getSuggestions | " + label};
            benchmark(stats, repeat, [&] { (void) context->getSuggestions(cursor); });
            stats.print();
        }
    }
}
