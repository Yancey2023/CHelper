#include "BenchHelper.h"
#include <gtest/gtest.h>
#include <xxhash.h>
#ifdef _WIN32
#include <Windows.h>
#include <psapi.h>
#else
#include <sys/resource.h>
#endif

using namespace CHelper;
using namespace CHelper::Test;

namespace {

    uint64_t longBenchPageFaults() {
#ifdef _WIN32
        PROCESS_MEMORY_COUNTERS counters{};
        if (!GetProcessMemoryInfo(GetCurrentProcess(), &counters, sizeof(counters))) {
            throw std::runtime_error("failed to read benchmark page fault counter");
        }
        return counters.PageFaultCount;
#else
        rusage usage{};
        if (getrusage(RUSAGE_SELF, &usage) != 0) {
            throw std::runtime_error("failed to read benchmark page fault counter");
        }
        return uint64_t(usage.ru_minflt) + uint64_t(usage.ru_majflt);
#endif
    }

    uint64_t longBenchThreadCycles() {
#ifdef _WIN32
        ULONG64 cycles = 0;
        if (!QueryThreadCycleTime(GetCurrentThread(), &cycles)) {
            throw std::runtime_error("failed to read benchmark thread cycle counter");
        }
        return cycles;
#else
        return 0;
#endif
    }

    struct LongBenchSample {
        int64_t startNs, endNs;
        uint64_t threadCycles, faults;
    };

    // 展示阶段也计入错误查询，包含所有输出文本的格式化。
    template<class Errors>
    size_t longBenchMessageSize(const Errors &errors, bool retainMessages) {
        size_t characters = 0;
        if (retainMessages) {
            // 网页输出先计算长度再写入，临时消息需要共同存活到输出结束。
            std::vector<std::u16string> messages;
            messages.reserve(errors.size());
            for (const auto &error: errors) {
                messages.push_back(error->getMessage());
                characters += messages.back().size();
            }
            return characters;
        }
        for (const auto &error: errors) {
            characters += error->getMessage().size();
        }
        return characters;
    }

    std::optional<std::string> longBenchEnvironment(const char *name) {
#ifdef _MSC_VER
        char *value = nullptr;
        size_t length = 0;
        const auto error = _dupenv_s(&value, &length, name);
        std::unique_ptr<char, decltype(&std::free)> owned(value, &std::free);
        if (error != 0) throw std::runtime_error("failed to read benchmark environment");
#else
        const char *value = std::getenv(name);
#endif
        return value ? std::optional<std::string>(value) : std::nullopt;
    }

    size_t longBenchOption(const char *name, size_t fallback) {
        const auto value = longBenchEnvironment(name);
        return value ? std::stoull(*value) : fallback;
    }

    std::u16string longBenchCommand(std::string_view kind, size_t count) {
        std::u16string command;
        if (kind == "flat") {
            command = u"execute ";
            for (size_t i = 0; i < count; ++i) command += u"if block ~~~ stone ";
            command += u"run say done";
        } else if (kind == "nested") {
            for (size_t i = 0; i < count; ++i) command += u"execute as @s run ";
            command += u"say done";
        } else if (kind == "selector") {
            command = u"tag @e[";
            for (size_t i = 0; i < count; ++i) {
                if (i) command += u",";
                command += u"tag=benchmark";
            }
            command += u"] add done";
        } else if (kind == "components" || kind == "components_alternating" || kind == "components_unique") {
            command = uR"(give @s stone 1 0 {"minecraft:can_destroy":{"blocks":[)";
            for (size_t i = 0; i < count; ++i) {
                if (i) command += u",";
                if (kind == "components_unique") command += fmt::format(u"\"minecraft:benchmark_{}\"", i);
                else if (kind == "components_alternating" && i % 2)
                    command += uR"("minecraft:dirt")";
                else
                    command += uR"("minecraft:stone")";
            }
            command += u"]}}";
        } else {
            command = uR"(tellraw @a {"rawtext":[)";
            for (size_t i = 0; i < count; ++i) {
                if (i) command += u",";
                command += uR"({"text":"hello 世界"})";
            }
            command += u"]}";
        }
        return command;
    }

    XXH64_hash_t longBenchResults(const CommandContext &context, size_t cursor) {
        XXH3_state_t state;
        XXH3_64bits_reset(&state);
        const auto scalar = [&](size_t value) { XXH3_64bits_update(&state, &value, sizeof(value)); };
        const auto string = [&](std::u16string_view value) {
            scalar(value.size());
            XXH3_64bits_update(&state, value.data(), value.size() * sizeof(char16_t));
        };
        const auto error = [&](const auto &value) {
            scalar(value->level);
            scalar(value->start);
            scalar(value->end);
            string(value->getMessage());
        };
        size_t nodes = 0, singleErrors = 0, multipleErrors = 0, errorReferences = 0;
        const auto ast = [&](const auto &self, const ASTNode &node) -> void {
            ++nodes;
            singleErrors += node.errorReasons.size() == 1;
            multipleErrors += node.errorReasons.size() > 1;
            errorReferences += node.errorReasons.size();
            scalar(node.mode);
            scalar(node.node.nodeTypeId);
            scalar(node.id);
            scalar(node.whichBest);
            scalar(node.tokens.startIndex);
            scalar(node.tokens.endIndex);
            scalar(node.childNodes.size());
            scalar(node.errorReasons.size());
            for (const auto &reason: node.errorReasons) error(reason);
            for (const auto &child: node.childNodes) self(self, child);
        };
        ast(ast, *context.getAstNode());
        std::printf("long tree nodes=%zu single_errors=%zu multiple_errors=%zu error_refs=%zu\n",
                    nodes, singleErrors, multipleErrors, errorReferences);
        string(context.getCommand());
        string(context.getStructure());
        string(context.getParamHint(cursor));
        scalar(context.getNodeCount());
        const auto suggestions = context.getSuggestions(cursor);
        scalar(suggestions.size());
        for (const auto &suggestion: suggestions) {
            scalar(suggestion.start);
            scalar(suggestion.end);
            scalar(suggestion.isAddSpace);
            string(suggestion.content->name);
            scalar(suggestion.content->description.has_value());
            if (suggestion.content->description) string(*suggestion.content->description);
        }
        const auto syntax = context.getSyntaxResult();
        scalar(syntax.tokenTypes.size());
        for (const auto type: syntax.tokenTypes) scalar(type);
        const auto errors = context.getErrorReasons();
        scalar(errors.size());
        for (const auto &reason: errors) error(reason);
        return XXH3_64bits_digest(&state);
    }
}// namespace

// LONG_KIND=flat/json/selector/components/components_alternating/components_unique/nested。
// LONG_COUNT 控制重复段数；LONG_APPLY=1 额外计时补全应用并校验结果。
// 长命令独立运行，避免把短命令混合均值当作重型输入的性能收益。
TEST(Bench, LongCommand) {
    const std::string kind = longBenchEnvironment("LONG_KIND").value_or("flat");
    const size_t count = longBenchOption("LONG_COUNT", 2048);
    const size_t repeat = longBenchOption("LONG_REPEAT", 5);
    const size_t warmup = longBenchOption("LONG_WARMUP", 1);
    const bool trackPageFaults = longBenchOption("LONG_TRACK_PAGE_FAULTS", 1) != 0;
    const bool recordSamples = longBenchOption("LONG_RECORD_SAMPLES", 0) != 0;
    const auto command = longBenchCommand(kind, count);
    const size_t cursor = longBenchOption("LONG_CURSOR", command.size());
    const bool includeApply = longBenchOption("LONG_APPLY", 0) != 0;
    const bool formatOutput = longBenchOption("LONG_FORMAT_OUTPUT", 1) != 0;
    const bool retainMessages = longBenchOption("LONG_RETAIN_MESSAGES", 0) != 0;
    size_t messageCharacters = 0;
    CHelperCore core(serialization::createCPackByDirectory(
            std::filesystem::path(RESOURCE_DIR) / "resources" / "beta" / "vanilla"));
    std::printf("long input kind=%s count=%zu chars=%zu cursor=%zu hash=%016llx\n", kind.c_str(), count,
                command.size(), cursor, static_cast<unsigned long long>(XXH3_64bits(command.data(), command.size() * sizeof(char16_t))));
    std::printf("long layout ast=%zu tokens=%zu diagnostic=%zu\n", sizeof(ASTNode), sizeof(TokensView), sizeof(ErrorReason));
    std::fflush(stdout);
    Stats total{"long total"}, create{"long construct"}, destroy{"long destroy"};
    Stats suggestions{"long suggestions"}, hint{"long hint"}, structure{"long structure"};
    Stats syntax{"long syntax"}, errors{"long errors"};
    Stats apply{"long apply"};
    Stats faultFree{"long fault-free total"}, faulting{"long faulting total"};
    for (auto *stats: {&total, &create, &suggestions, &hint, &structure, &syntax, &errors, &destroy, &apply, &faultFree, &faulting}) {
        stats->samplesMs.reserve(repeat);
    }
    uint64_t measuredFaults = 0;
    std::vector<LongBenchSample> samples;
    if (recordSamples) samples.reserve(repeat);
    for (size_t iteration = 0; iteration < warmup + repeat; ++iteration) {
        const bool measured = iteration >= warmup;
        AllocSnapshot allocations;
        const auto measure = [&](Stats &stats, auto &&body) {
            if (measured) startAllocCounting();
            const auto start = std::chrono::steady_clock::now();
            body();
            const auto end = std::chrono::steady_clock::now();
            if (measured) {
                const auto snapshot = stopAllocCounting();
                allocations.allocCalls += snapshot.allocCalls;
                allocations.netBytes += snapshot.netBytes;
                stats.add(std::chrono::duration<double, std::milli>(end - start).count(), snapshot);
            }
        };
        std::unique_ptr<CommandContext> context;
        // Counter queries stay outside the total timing window. Reserve sample
        // storage above so benchmark bookkeeping does not fault in that window.
        const auto faultsBefore = measured && trackPageFaults ? longBenchPageFaults() : 0;
        const auto cyclesBefore = measured && recordSamples ? longBenchThreadCycles() : 0;
        const auto start = std::chrono::steady_clock::now();
        measure(create, [&] { context.reset(core.createContext(command)); });
        measure(suggestions, [&] { (void) context->getSuggestions(cursor); });
        measure(hint, [&] { (void) context->getParamHint(cursor); });
        measure(structure, [&] { (void) context->getStructure(); });
        measure(syntax, [&] { (void) context->getSyntaxResult(); });
        measure(errors, [&] {
            const auto reasons = context->getErrorReasons();
            if (formatOutput) messageCharacters += longBenchMessageSize(reasons, retainMessages);
        });
        if (includeApply) measure(apply, [&] { (void) context->applySuggestion(cursor, 0); });
        measure(destroy, [&] { context.reset(); });
        const auto end = std::chrono::steady_clock::now();
        const auto cyclesAfter = measured && recordSamples ? longBenchThreadCycles() : 0;
        const auto faultsAfter = measured && trackPageFaults ? longBenchPageFaults() : 0;
        if (measured) {
            const double elapsed = std::chrono::duration<double, std::milli>(end - start).count();
            total.add(elapsed, allocations);
            uint64_t faults = 0;
            if (trackPageFaults) {
#ifdef _WIN32
                faults = DWORD(faultsAfter - faultsBefore);
#else
                faults = faultsAfter - faultsBefore;
#endif
                measuredFaults += faults;
                (faults == 0 ? faultFree : faulting).add(elapsed, allocations);
            }
            if (recordSamples) {
                samples.push_back({std::chrono::duration_cast<std::chrono::nanoseconds>(start.time_since_epoch()).count(),
                                   std::chrono::duration_cast<std::chrono::nanoseconds>(end.time_since_epoch()).count(),
                                   cyclesAfter - cyclesBefore, faults});
            }
        }
    }
#ifdef _WIN32
    constexpr bool threadCyclesSupported = true;
#else
    constexpr bool threadCyclesSupported = false;
#endif
    if (recordSamples) {
        std::printf("long samples count=%zu clock=steady_nanoseconds thread_cycles_supported=%d faults_tracked=%d\n",
                    samples.size(), threadCyclesSupported, trackPageFaults);
        for (size_t i = 0; i < samples.size(); ++i) {
            const auto &sample = samples[i];
            std::printf("long sample index=%zu start_ns=%lld end_ns=%lld thread_cycles=%llu faults=%llu "
                        "total_ms=%.9f construct_ms=%.9f suggestions_ms=%.9f hint_ms=%.9f structure_ms=%.9f "
                        "syntax_ms=%.9f errors_ms=%.9f destroy_ms=%.9f apply_ms=%.9f\n",
                        i, static_cast<long long>(sample.startNs), static_cast<long long>(sample.endNs),
                        static_cast<unsigned long long>(sample.threadCycles), static_cast<unsigned long long>(sample.faults),
                        total.samplesMs[i], create.samplesMs[i], suggestions.samplesMs[i], hint.samplesMs[i],
                        structure.samplesMs[i], syntax.samplesMs[i], errors.samplesMs[i], destroy.samplesMs[i],
                        includeApply ? apply.samplesMs[i] : 0.0);
        }
    }
    for (const auto *stats: {&total, &create, &suggestions, &hint, &structure, &syntax, &errors, &destroy}) stats->print();
    if (includeApply) apply.print();
    std::printf("long page_faults tracking=%d warmup=%zu measured=%zu fault_free=%zu faulting=%zu total_faults=%llu\n",
                trackPageFaults, warmup, total.count, faultFree.count, faulting.count,
                static_cast<unsigned long long>(measuredFaults));
    if (faultFree.count) faultFree.print();
    if (faulting.count) faulting.print();
    std::printf("long output format=%d characters=%zu\n", formatOutput, messageCharacters);
    std::fflush(stdout);
    if (longBenchOption("LONG_VERIFY", 1)) {
        const std::unique_ptr<CommandContext> context(core.createContext(command));
        std::printf("long results hash=%016llx\n", static_cast<unsigned long long>(longBenchResults(*context, cursor)));
        if (includeApply) {
            const auto applied = context->applySuggestion(cursor, 0);
            ASSERT_TRUE(applied.has_value());
            const auto repeated = context->applySuggestion(cursor, 0);
            EXPECT_EQ(applied, repeated);
            EXPECT_EQ(context->getCommand(), command);
            const std::u16string_view text(applied->first);
            std::printf("long apply hash=%016llx cursor=%zu chars=%zu\n",
                        static_cast<unsigned long long>(XXH3_64bits(text.data(), text.size() * sizeof(char16_t))),
                        applied->second, text.size());
        }
    }
}
