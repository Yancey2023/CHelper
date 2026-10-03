#include "BenchHelper.h"
#include <gtest/gtest.h>
#include <xxhash.h>

using namespace CHelper;
using namespace CHelper::Test;

namespace {

    size_t longBenchOption(const char *name, size_t fallback) {
        const char *value = std::getenv(name);
        return value ? std::stoull(value) : fallback;
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
        } else if (kind == "components") {
            command = uR"(give @s stone 1 0 {"minecraft:can_destroy":{"blocks":[)";
            for (size_t i = 0; i < count; ++i) {
                if (i) command += u",";
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
            string(value->errorReason);
        };
        const auto ast = [&](const auto &self, const ASTNode &node) -> void {
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

// LONG_KIND=flat/json/selector/components/nested，LONG_COUNT 控制重复段数。
// 长命令独立运行，避免把短命令混合均值当作重型输入的性能收益。
TEST(Bench, LongCommand) {
    const char *kindOption = std::getenv("LONG_KIND");
    const std::string kind = kindOption ? kindOption : "flat";
    const size_t count = longBenchOption("LONG_COUNT", 2048);
    const size_t repeat = longBenchOption("LONG_REPEAT", 5);
    const auto command = longBenchCommand(kind, count);
    const size_t cursor = longBenchOption("LONG_CURSOR", command.size());
    CHelperCore core(serialization::createCPackByDirectory(
            std::filesystem::path(RESOURCE_DIR) / "resources" / "beta" / "vanilla"));
    std::printf("long input kind=%s count=%zu chars=%zu cursor=%zu hash=%016llx\n", kind.c_str(), count,
                command.size(), cursor, static_cast<unsigned long long>(XXH3_64bits(command.data(), command.size() * sizeof(char16_t))));
    std::fflush(stdout);
    Stats total{"long total"}, create{"long construct"}, destroy{"long destroy"};
    Stats suggestions{"long suggestions"}, hint{"long hint"}, structure{"long structure"};
    Stats syntax{"long syntax"}, errors{"long errors"};
    for (size_t iteration = 0; iteration < repeat + 1; ++iteration) {
        const bool measured = iteration != 0;
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
        const auto start = std::chrono::steady_clock::now();
        measure(create, [&] { context.reset(core.createContext(command)); });
        measure(suggestions, [&] { (void) context->getSuggestions(cursor); });
        measure(hint, [&] { (void) context->getParamHint(cursor); });
        measure(structure, [&] { (void) context->getStructure(); });
        measure(syntax, [&] { (void) context->getSyntaxResult(); });
        measure(errors, [&] { (void) context->getErrorReasons(); });
        measure(destroy, [&] { context.reset(); });
        const auto end = std::chrono::steady_clock::now();
        if (measured) total.add(std::chrono::duration<double, std::milli>(end - start).count(), allocations);
    }
    for (const auto *stats: {&total, &create, &suggestions, &hint, &structure, &syntax, &errors, &destroy}) stats->print();
    std::fflush(stdout);
    if (longBenchOption("LONG_VERIFY", 1)) {
        const std::unique_ptr<CommandContext> context(core.createContext(command));
        std::printf("long results hash=%016llx\n", static_cast<unsigned long long>(longBenchResults(*context, cursor)));
    }
}
