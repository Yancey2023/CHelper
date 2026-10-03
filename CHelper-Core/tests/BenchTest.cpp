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

#include <chelper/serialization/SerializationImpl.h>
#include <gtest/gtest.h>
#include <xxhash.h>

#ifdef _MSC_VER
#define CHELPER_BENCH_RETURN_ADDRESS() _ReturnAddress()
#else
#define CHELPER_BENCH_RETURN_ADDRESS() __builtin_extract_return_addr(__builtin_return_address(0))
#endif

// 只在测试可执行文件中替换全局分配器，避免把非标准 operator new/delete
// 放进头文件导致 MSVC C4595，同时保证所有测试翻译单元共用同一套计数器。
void *operator new(size_t size) {
    return chelperBenchAlloc(size, CHELPER_BENCH_RETURN_ADDRESS());
}

void *operator new[](size_t size) {
    return chelperBenchAlloc(size, CHELPER_BENCH_RETURN_ADDRESS());
}

void *operator new(size_t size, const std::nothrow_t &) noexcept {
    return chelperBenchAlloc(size, CHELPER_BENCH_RETURN_ADDRESS());
}

void *operator new[](size_t size, const std::nothrow_t &) noexcept {
    return chelperBenchAlloc(size, CHELPER_BENCH_RETURN_ADDRESS());
}

void operator delete(void *p) noexcept {
    chelperBenchFree(p, p ? chelperBenchBlockSize(p) : 0, CHELPER_BENCH_RETURN_ADDRESS());
}

void operator delete[](void *p) noexcept {
    chelperBenchFree(p, p ? chelperBenchBlockSize(p) : 0, CHELPER_BENCH_RETURN_ADDRESS());
}

void operator delete(void *p, size_t size) noexcept {
    chelperBenchFree(p, size, CHELPER_BENCH_RETURN_ADDRESS());
}

void operator delete[](void *p, size_t size) noexcept {
    chelperBenchFree(p, size, CHELPER_BENCH_RETURN_ADDRESS());
}

void operator delete(void *p, const std::nothrow_t &) noexcept {
    chelperBenchFree(p, p ? chelperBenchBlockSize(p) : 0, CHELPER_BENCH_RETURN_ADDRESS());
}

void operator delete[](void *p, const std::nothrow_t &) noexcept {
    chelperBenchFree(p, p ? chelperBenchBlockSize(p) : 0, CHELPER_BENCH_RETURN_ADDRESS());
}

void *operator new(size_t size, std::align_val_t alignment) {
    return chelperBenchAlignedAlloc(size, static_cast<size_t>(alignment), CHELPER_BENCH_RETURN_ADDRESS());
}

void *operator new[](size_t size, std::align_val_t alignment) {
    return chelperBenchAlignedAlloc(size, static_cast<size_t>(alignment), CHELPER_BENCH_RETURN_ADDRESS());
}

void *operator new(size_t size, std::align_val_t alignment, const std::nothrow_t &) noexcept {
    try {
        return chelperBenchAlignedAlloc(size, static_cast<size_t>(alignment), CHELPER_BENCH_RETURN_ADDRESS());
    } catch (...) {
        return nullptr;
    }
}

void *operator new[](size_t size, std::align_val_t alignment, const std::nothrow_t &) noexcept {
    try {
        return chelperBenchAlignedAlloc(size, static_cast<size_t>(alignment), CHELPER_BENCH_RETURN_ADDRESS());
    } catch (...) {
        return nullptr;
    }
}

void operator delete(void *p, std::align_val_t) noexcept { chelperBenchAlignedFree(p, CHELPER_BENCH_RETURN_ADDRESS()); }
void operator delete[](void *p, std::align_val_t) noexcept { chelperBenchAlignedFree(p, CHELPER_BENCH_RETURN_ADDRESS()); }
void operator delete(void *p, size_t, std::align_val_t) noexcept { chelperBenchAlignedFree(p, CHELPER_BENCH_RETURN_ADDRESS()); }
void operator delete[](void *p, size_t, std::align_val_t) noexcept { chelperBenchAlignedFree(p, CHELPER_BENCH_RETURN_ADDRESS()); }
void operator delete(void *p, std::align_val_t, const std::nothrow_t &) noexcept { chelperBenchAlignedFree(p, CHELPER_BENCH_RETURN_ADDRESS()); }
void operator delete[](void *p, std::align_val_t, const std::nothrow_t &) noexcept { chelperBenchAlignedFree(p, CHELPER_BENCH_RETURN_ADDRESS()); }

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
    // 计时窗口只包含加载本体：时间戳在 createCPack 返回处取，cpack 的析构发生在窗口之外
    const auto timedLoad = [&]<class Create>(Stats &stats, const Create &create) {
        const size_t warmups = std::max<size_t>(repeat / 10, 1);
        for (size_t i = 0; i < warmups; ++i) {
            const auto cpack = create();
            ASSERT_NE(cpack, nullptr);
        }
        for (size_t i = 0; i < repeat; ++i) {
            startAllocCounting();
            const auto start = std::chrono::steady_clock::now();
            const auto cpack = create();
            const auto end = std::chrono::steady_clock::now();
            stats.add(std::chrono::duration<double, std::milli>(end - start).count(), stopAllocCounting());
            ASSERT_NE(cpack, nullptr);
        }
        stats.print();
    };

    {
        Stats stats{"load cpack by directory (vanilla)"};
        timedLoad(stats, [] {
            return serialization::createCPackByDirectory(vanillaDir());
        });
    }
    {
        for (const auto &path: {vanillaBin(), experimentBin()}) {
            if (!std::filesystem::exists(path)) {
                continue;
            }
            const auto data = readBinaryFile(path);
            Stats stats{"load cpack by binary (" + path.stem().string() + ")"};
            timedLoad(stats, [&data] {
                return serialization::createCPackByBinary(std::string_view(data.data(), data.size()));
            });
        }
    }
}

// 单独测量二进制解码，排除 afterApply 中的缓存构建与节点初始化。
TEST(Bench, DecodeCPack) {
    Node::initializeStaticNodes();
    std::printf("sizeof(NormalId)=%zu bytes\n", sizeof(NormalId));
    for (const auto &path: {vanillaBin(), experimentBin()}) {
        if (!std::filesystem::exists(path)) continue;
        const auto data = readBinaryFile(path);
        Stats stats{"decode cpack binary (" + path.stem().string() + ")"};
        const auto read = [&](bool measure) {
            const auto memory = std::make_shared<CPackMemoryResource>();
            const CPackMemoryScope scope(memory);
            CPackData restored;
            NodeReadContext ctx;
            ctx.createStage = Node::NodeCreateStage::JSON_NODE;
            ctx.cpackMemory = memory;
            if (measure) startAllocCounting();
            const auto start = std::chrono::steady_clock::now();
            const auto error = glz::read<glz::opts{.format = BinaryFormat}>(restored, data, ctx);
            const auto end = std::chrono::steady_clock::now();
            if (measure) stats.add(std::chrono::duration<double, std::milli>(end - start).count(), stopAllocCounting());
            ASSERT_FALSE(bool(error)) << glz::format_error(error, data);
            ASSERT_NE(restored.commands, nullptr);
            ASSERT_FALSE(restored.commands->empty());
        };
        for (int i = 0; i < 2; ++i) read(false);
        for (int i = 0; i < 20; ++i) read(true);
        stats.print();
    }
}

// 单独测量方块 ID 缓存初始化；每轮先解码到新的资源池，避免把缓存命中当成初始化。
TEST(Bench, InitializeBlockIds) {
    Node::initializeStaticNodes();
    for (const auto &path: {vanillaBin(), experimentBin()}) {
        if (!std::filesystem::exists(path)) continue;
        const auto data = readBinaryFile(path);
        Stats stats{"initialize block ids (" + path.stem().string() + ")"};
        for (int iteration = 0; iteration < 22; ++iteration) {
            const auto memory = std::make_shared<CPackMemoryResource>();
            const CPackMemoryScope scope(memory);
            CPackData restored;
            NodeReadContext ctx;
            ctx.createStage = Node::NodeCreateStage::JSON_NODE;
            ctx.cpackMemory = memory;
            const auto error = glz::read<glz::opts{.format = BinaryFormat}>(restored, data, ctx);
            ASSERT_FALSE(bool(error)) << glz::format_error(error, data);
            ASSERT_NE(restored.blockIds, nullptr);
            ASSERT_NE(restored.blockIds->blockStateValues, nullptr);
            if (iteration >= 2) startAllocCounting();
            const auto start = std::chrono::steady_clock::now();
            {
                const auto &blocks = *restored.blockIds;
                const BlockPropertyDescriptionIndex index(blocks.blockPropertyDescriptions);
                BlockPropertyNodeCache nodes(blocks.blockPropertyDescriptions);
                for (const auto &block: *blocks.blockStateValues) {
                    block->buildHash();
                    block->getIdWithNamespace()->buildHash();
                    block->getNode(blocks.blockPropertyDescriptions, &index, &nodes);
                }
            }
            const auto end = std::chrono::steady_clock::now();
            if (iteration >= 2) stats.add(std::chrono::duration<double, std::milli>(end - start).count(), stopAllocCounting());
        }
        stats.print();
    }
}

/**
 * CPack 写出：目录 JSON、单文件 JSON 与二进制文件
 */
TEST(Bench, WriteCPack) {
    const size_t repeat = 20;
    // Load tracked JSON resources so this benchmark also runs before binary generation.
    const auto cpack = serialization::createCPackByDirectory(vanillaDir());
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

TEST(Bench, SerializationMemory) {
    const auto cpack = serialization::createCPackByDirectory(vanillaDir());
    ASSERT_NE(cpack, nullptr);
    ASSERT_NE(cpack->commands, nullptr);
    ASSERT_FALSE(cpack->commands->empty());
    const auto &commands = *cpack->commands;
    const auto run = [&]<std::uint32_t Format>(const char *name) {
        constexpr auto opts = glz::opts{.format = Format, .error_on_unknown_keys = false};
        std::string buffer;
        ASSERT_FALSE(bool(glz::write<opts>(commands, buffer)));
        std::printf("%s input: %zu bytes, xxh64=%016llx\n", name, buffer.size(),
                    static_cast<unsigned long long>(XXH64(buffer.data(), buffer.size(), 0)));
        using Commands = std::remove_cvref_t<decltype(commands)>;
        const auto read = [&](Stats *stats) {
            // 每轮显式拥有读取池，避免借用源 CPack 的加载作用域，也把各轮分配隔离。
            const auto memory = std::make_shared<CPackMemoryResource>();
            const CPackMemoryScope memoryScope(memory);
            Commands restored;
            NodeReadContext ctx;
            ctx.cpackMemory = memory;
            if (stats) startAllocCounting();
            const auto start = std::chrono::steady_clock::now();
            const auto error = glz::read<opts>(restored, buffer, ctx);
            const auto end = std::chrono::steady_clock::now();
            if (stats) {
                stats->add(std::chrono::duration<double, std::milli>(end - start).count(), stopAllocCounting());
            }
            ASSERT_FALSE(bool(error)) << glz::format_error(error, buffer);
            ASSERT_EQ(restored.size(), commands.size());
            for (std::size_t i = 0; i < commands.size(); ++i) {
                ASSERT_EQ(restored[i].name, commands[i].name);
                ASSERT_EQ(restored[i].nodes.nodes.size(), commands[i].nodes.nodes.size());
            }
        };
        for (int i = 0; i < 5; ++i) read(nullptr);
        Stats reads{std::string("read commands memory ") + name};
        for (int i = 0; i < 100; ++i) read(&reads);
        reads.print();
        Stats writes{std::string("write commands memory ") + name};
        benchmark(writes, 100, [&] {
            const auto error = glz::write<opts>(commands, buffer);
            if (bool(error)) throw std::runtime_error("benchmark serialization failed");
        });
        writes.print();
    };
    run.template operator()<glz::JSON>("JSON");
    run.template operator()<glz::MSGPACK>("MSGPACK");
    run.template operator()<CHelper::BinaryFormat>("BinaryFormat");
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
