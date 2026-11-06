#if defined(CHELPER_RESOURCE_AUDIT)

#include <chelper/CHelperCore.h>
#include <chelper/serialization/IO.h>
#include <chelper/serialization/Serialization.h>
#include <gtest/gtest.h>

namespace CHelper::Test {
    namespace {
        struct TemporaryBinary {
            std::filesystem::path path = std::filesystem::temp_directory_path() /
                                         ("chelper-resource-scope-" + std::to_string(std::chrono::steady_clock::now().time_since_epoch().count()) + ".cpack");
            ~TemporaryBinary() {
                std::error_code error;
                std::filesystem::remove(path, error);
            }
        };
        const std::vector<std::u16string> commands{
                u"", u"give ", u"give @s minecraft:stone 1 0", u"setblock ~~~ minecraft:oak_stairs [\"upside_down_bit\"=true]",
                u"execute as @e[tag=test,type=minecraft:pig] at @s run say hello", u"give @s stone 1 0 {\"minecraft:can_destroy\":{\"blocks\":[\"minecraft:stone\",\"invalid\"]}}",
                u"tellraw @a {\"rawtext\":[{\"text\":\"hello 世界\"}]}", u"unknown invalid", u"tp @s ~ ~ ~", u"summon minecraft:pig ~~~"};

        auto snapshot(const CPack &pack) {
            return static_cast<const MonotonicResource *>(pack.getMemoryResource())->snapshot();
        }

        void audit(std::unique_ptr<CPack> pack) {
            ASSERT_NE(pack, nullptr);
            CHelperCore core(std::move(pack));
            const auto loaded = snapshot(core.getCPack());
            auto *route = CPackMemoryRouter::getCurrent();
            for (size_t repetition = 0; repetition < 4; ++repetition) {
                for (const auto &command: commands) {
                    SCOPED_TRACE(utf8::utf16to8(command));
                    std::unique_ptr<CommandContext> context(core.createContext(command));
                    ASSERT_EQ(snapshot(core.getCPack()), loaded) << "createContext allocated in the CPack arena";
                    ASSERT_EQ(CPackMemoryRouter::getCurrent(), route);
                    const auto parsed = context->getMemorySnapshot();
                    const auto unchanged = [&] {
                        EXPECT_EQ(snapshot(core.getCPack()), loaded);
                        EXPECT_EQ(context->getMemorySnapshot(), parsed);
                        EXPECT_EQ(CPackMemoryRouter::getCurrent(), route);
                    };
                    for (size_t cursor = 0; cursor <= command.size(); ++cursor) {
                        // Keep results alive through the checks: returning a result must not
                        // reserve storage in either long-lived arena, even on the first query.
                        const auto suggestions = context->getSuggestions(cursor);
                        {
                            SCOPED_TRACE("getSuggestions");
                            unchanged();
                        }
                        const auto hint = context->getParamHint(cursor);
                        {
                            SCOPED_TRACE("getParamHint");
                            unchanged();
                        }
                        for (const auto which: {size_t(0), suggestions.size()}) {
                            const auto applied = context->applySuggestion(cursor, which);
                            SCOPED_TRACE("applySuggestion");
                            unchanged();
                        }
                    }
                    for (size_t query = 0; query < 4; ++query) {
                        const auto structure = context->getStructure();
                        {
                            SCOPED_TRACE("getStructure");
                            unchanged();
                        }
                        const auto syntax = context->getSyntaxResult();
                        {
                            SCOPED_TRACE("getSyntaxResult");
                            unchanged();
                        }
                        const auto errors = context->getErrorReasons();
                        for (const auto &error: errors) (void) error->getMessage();
                        {
                            SCOPED_TRACE("getErrorReasons and formatting");
                            unchanged();
                        }
                        (void) context->getNodeCount();
                        (void) context->getCommand();
                        (void) context->getAstNode();
                        {
                            SCOPED_TRACE("accessors");
                            unchanged();
                        }
                    }
                }
                EXPECT_EQ(snapshot(core.getCPack()), loaded) << "destroying contexts grew the CPack arena";
            }
            const auto json = core.getCPack().toJson();
            EXPECT_EQ(snapshot(core.getCPack()), loaded) << "serialization grew the CPack arena";
        }
    }// namespace

    TEST(ResourceScopeTest, ContextConstructionAndEveryQueryKeepBothArenasUnchanged) {
        const auto resource = std::filesystem::path(RESOURCE_DIR);
        for (const auto channel: {"beta", "release", "netease"}) {
            for (const auto kind: {"vanilla", "experiment"}) {
                const auto path = resource / "resources" / channel / kind;
                SCOPED_TRACE(path.string());
                auto pack = serialization::createCPackByDirectory(path);
                const auto loaded = snapshot(*pack);
                TemporaryBinary binary;
                pack->writeBinToFile(binary.path);
                EXPECT_EQ(snapshot(*pack), loaded) << "binary serialization grew the CPack arena";
                const auto bytes = readFileToString(binary.path);
                audit(std::move(pack));
                audit(serialization::createCPackByBinary(bytes));
            }
        }
        const auto generated = resource / "generated" / "cpack";
        if (std::filesystem::exists(generated))
            for (const auto &entry: std::filesystem::directory_iterator(generated)) {
                if (entry.path().extension() != ".cpack") continue;
                SCOPED_TRACE(entry.path().string());
                const auto bytes = readFileToString(entry.path());
                audit(serialization::createCPackByBinary(bytes));
            }
        const auto pack = serialization::createCPackByDirectory(resource / "resources" / "beta" / "vanilla");
        const auto json = pack->toJson();
        audit(serialization::createCPackByJson(json));
    }
}// namespace CHelper::Test

#endif
