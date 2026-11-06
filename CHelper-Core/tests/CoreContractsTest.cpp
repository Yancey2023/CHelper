#include "CpackTestHelper.h"
#include <chelper/auto_suggestion/Suggestions.h>
#include <chelper/node/NodeInitialization.h>
#include <chelper/parser/Parser.h>
#include <chelper/serialization/SerializationImpl.h>
#include <chelper/syntax_highlight/SyntaxHighlight.h>
#include <gtest/gtest.h>

namespace CHelper::Test {
    namespace {
        class TrackedResource final : public std::pmr::memory_resource {
            std::unordered_map<void *, std::pair<size_t, size_t>> live;
            void *do_allocate(size_t bytes, size_t alignment) override {
                void *pointer = std::pmr::new_delete_resource()->allocate(bytes, alignment);
                live.emplace(pointer, std::pair{bytes, alignment});
                return pointer;
            }
            void do_deallocate(void *pointer, size_t bytes, size_t alignment) override {
                const auto found = live.find(pointer);
                EXPECT_NE(found, live.end());
                if (found == live.end()) return;
                EXPECT_EQ(found->second, (std::pair{bytes, alignment}));
                live.erase(found);
                std::pmr::new_delete_resource()->deallocate(pointer, bytes, alignment);
            }
            bool do_is_equal(const std::pmr::memory_resource &other) const noexcept override { return this == &other; }

        public:
            ~TrackedResource() override { EXPECT_TRUE(live.empty()); }
        };
        struct MemoryScope {
            std::pmr::memory_resource *previous = CPackMemoryRouter::getCurrent();
            explicit MemoryScope(std::pmr::memory_resource *resource) { CPackMemoryRouter::setCurrent(resource); }
            ~MemoryScope() { CPackMemoryRouter::setCurrent(previous); }
        };
        std::string withGrammar(std::string_view nodes) {
            auto json = makeCpackJson();
            json.insert(json.rfind('}'), ",\"grammar\":[{\"id\":\"probe\",\"type\":\"grammar\",\"content\":{\"id\":\"probe\",\"node\":[" +
                                                 std::string(nodes) + "],\"start\":\"ROOT\"}}]");
            return json;
        }
        Property stringProperty(std::u16string_view text) {
            Property result;
            result.type = PropertyType::STRING;
            result.defaultValue.string = new PropertyString(text, CPackMemoryRouter::getAllocationResource());
            result.valid.emplace(CPackMemoryRouter::getAllocationResource());
            PropertyValue value;
            value.string = new PropertyString(text, CPackMemoryRouter::getAllocationResource());
            result.valid->push_back(value);
            return result;
        }
    }// namespace

    TEST(CoreContractsTest, ScopedAllocationDoesNotChangeTheProcessDefaultOrDeallocationOwner) {
        auto *original = std::pmr::get_default_resource();
        CPackMemoryRouter::install();
        EXPECT_EQ(std::pmr::get_default_resource(), original);
        TrackedResource first, second;
        std::optional<Node::NodeAnd> node;
        std::shared_ptr<NormalId> id;
        std::optional<std::pmr::vector<int>> foreign;
        foreign.emplace(&first);
        foreign->resize(256);
        {
            MemoryScope scope(&first);
            node.emplace();
            node->childNodes.reserve(256);
            id = NormalId::make(u"name exceeding inline string storage for ownership checks");
        }
        {
            MemoryScope scope(&second);
            foreign.reset();
            node.reset();
            id.reset();
        }
        EXPECT_EQ(std::pmr::get_default_resource(), original);
    }

    TEST(CoreContractsTest, PropertyAssignmentReleasesOldStringsAndHandlesSelfAssignmentAcrossResources) {
        TrackedResource first, second;
        std::optional<Property> source, destination;
        {
            MemoryScope scope(&first);
            source.emplace(stringProperty(u"source property text exceeding inline capacity"));
        }
        {
            MemoryScope scope(&second);
            destination.emplace(stringProperty(u"destination property text exceeding inline capacity"));
            *destination = *source;
            EXPECT_EQ(*destination->defaultValue.string, *source->defaultValue.string);
            EXPECT_NE(destination->defaultValue.string, source->defaultValue.string);
            auto &self = *destination;
            *destination = self;
            *destination = std::move(self);
            EXPECT_EQ(*destination->defaultValue.string, *source->defaultValue.string);
            *destination = std::move(*source);
            EXPECT_EQ(source->type, PropertyType::BOOLEAN);
        }
        source.reset();
        destination.reset();
    }

    TEST(CoreContractsTest, OptionalPmrValuesCaptureTheConstructionResourceInEveryFormat) {
        TrackedResource resource;
        MemoryScope scope(&resource);
        const std::optional<std::pmr::u16string> source(std::in_place, u"description exceeding inline storage", &resource);
        std::optional<std::pmr::u16string> json, binary, msgpack;
        readJson(json, writeJson(source));
        std::string bytes;
        writeBinary(bytes, source);
        readBinary(binary, bytes);
        bytes.clear();
        writeMsgpack(bytes, source);
        readMsgpack(msgpack, bytes);
        for (const auto *text: {&json, &binary, &msgpack}) {
            ASSERT_TRUE(text->has_value());
            EXPECT_EQ(text->value(), source.value());
            EXPECT_EQ(text->value().get_allocator().resource(), &resource);
        }
        std::optional<std::pmr::vector<std::pmr::u16string>> values;
        readJson(values, R"(["value exceeding inline string capacity"])");
        ASSERT_TRUE(values.has_value());
        EXPECT_EQ(values->get_allocator().resource(), &resource);
        EXPECT_EQ(values->front().get_allocator().resource(), &resource);
        const auto id = NormalId::make(u"name", u"description exceeding inline capacity");
        EXPECT_EQ(id->description->get_allocator().resource(), &resource);
    }

    TEST(CoreContractsTest, SharedPmrJsonContainersCaptureResourceAndPreserveExistingOwnership) {
        TrackedResource resource;
        MemoryScope scope(&resource);
        std::shared_ptr<std::pmr::vector<std::pmr::u16string>> values;
        readJson(values, R"( ["value exceeding inline string capacity"])");
        ASSERT_NE(values, nullptr);
        EXPECT_EQ(values->get_allocator().resource(), &resource);
        EXPECT_EQ(values->front().get_allocator().resource(), &resource);
        const auto original = values;
        readJson(values, R"(["replacement exceeding inline string capacity"])");
        EXPECT_EQ(values, original);
        EXPECT_EQ(values->get_allocator().resource(), &resource);
        for (const auto input: {"nonsense", "nul", "  "}) {
            EXPECT_THROW(readJson(values, input), std::runtime_error);
        }
        readJson(values, " null ");
        EXPECT_EQ(values, nullptr);
        readJson(values, "[]");
        ASSERT_NE(values, nullptr);
        EXPECT_TRUE(values->empty());
        EXPECT_EQ(values->get_allocator().resource(), &resource);
    }

    TEST(CoreContractsTest, NodeInitializerListsCaptureResourceAndPreserveExplicitVectorOwnership) {
        TrackedResource resource, external;
        MemoryScope scope(&resource);
        Node::NodeSingleSymbol symbol(u'[', u"probe");
        Node::NodeAnd sequence({symbol, symbol});
        Node::NodeOr choice({symbol, symbol}, false);
        Node::NodeList list(symbol, symbol, symbol, symbol);
        for (const auto *children: {&sequence.childNodes, &choice.childNodes,
                                    &list.nodeElementOrRight.childNodes, &list.nodeSeparatorOrRight.childNodes}) {
            EXPECT_EQ(children->get_allocator().resource(), &resource);
            ASSERT_EQ(children->size(), 2);
            EXPECT_EQ(children->front().data, &symbol);
        }
        std::pmr::vector<Node::NodeWithType> supplied({symbol, symbol}, &external);
        const auto *buffer = supplied.data();
        Node::NodeAnd moved(std::move(supplied));
        EXPECT_EQ(moved.childNodes.get_allocator().resource(), &external);
        EXPECT_EQ(moved.childNodes.data(), buffer);
    }

    TEST(CoreContractsTest, JsonCommandNodeKeysCaptureLoadingResource) {
        // 与 CPack 加载入口一致，全局节点必须先于临时资源作用域初始化。
        Node::initializeStaticNodes();
        TrackedResource resource;
        MemoryScope scope(&resource);
        NodeReadContext ctx;
        ctx.createStage = Node::NodeCreateStage::COMMAND_PARAM_NODE;
        Node::NodePerCommand command;
        readJson(command, R"({"name":["probe"],"syntax":["/probe <argument_exceeding_inline_capacity>"],
            "node":{"<argument_exceeding_inline_capacity>":{"type":"STRING"}}})",
                 ctx);
        ASSERT_EQ(command.nodes.nodes.size(), 1);
        const auto &id = static_cast<const Node::NodeSerializable *>(command.nodes.nodes.front().data)->id;
        ASSERT_TRUE(id.has_value());
        EXPECT_EQ(*id, "<argument_exceeding_inline_capacity>");
        EXPECT_EQ(id->get_allocator().resource(), &resource);
    }

    TEST(CoreContractsTest, DescriptionAssignmentReleasesOwnedValuesAcrossResources) {
        TrackedResource first, second;
        std::optional<BlockPropertyDescription> source, destination;
        const auto fill = [](BlockPropertyDescription &description) {
            description.type = PropertyType::STRING;
            BlockPropertyValueDescription value;
            value.valueName.string = new PropertyString(u"property value exceeding inline string storage", CPackMemoryRouter::getAllocationResource());
            description.values.push_back(std::move(value));
        };
        {
            MemoryScope scope(&first);
            source.emplace();
            fill(*source);
        }
        {
            MemoryScope scope(&second);
            destination.emplace();
            fill(*destination);
            *destination = *source;
            EXPECT_NE(destination->values[0].valueName.string, source->values[0].valueName.string);
            *destination = std::move(*source);
        }
        source.reset();
        destination.reset();
    }

    TEST(CoreContractsTest, LoadingRejectsEmptyChoicesAndNonConsumingRecursion) {
        for (const auto *nodes: {
                     R"({"type":"OR","id":"ROOT","nodes":[]})",
                     R"({"type":"AND","id":"ROOT","nodes":[]})",
                     R"({"type":"OR","id":"ROOT","nodes":["ROOT"]})",
                     R"({"type":"OR","id":"ROOT","nodes":["OTHER"]},{"type":"OPTIONAL","id":"OTHER","node":"ROOT"})",
                     R"({"type":"AND","id":"ROOT","nodes":["EMPTY","ROOT"]},{"type":"STRING","id":"EMPTY","allowMissingString":true})"}) {
            EXPECT_THROW(serialization::createCPackByJson(withGrammar(nodes)), std::runtime_error) << nodes;
        }
    }

    TEST(CoreContractsTest, ConsumingRecursiveGrammarRemainsValid) {
        auto pack = serialization::createCPackByJson(withGrammar(R"(
            {"type":"OR","id":"ROOT","nodes":["NUMBER","LIST"]},
            {"type":"INTEGER","id":"NUMBER"},
            {"type":"SINGLE_SYMBOL","id":"LEFT","symbol":"["},
            {"type":"SINGLE_SYMBOL","id":"RIGHT","symbol":"]"},
            {"type":"SINGLE_SYMBOL","id":"COMMA","symbol":","},
            {"type":"LIST","id":"LIST","left":"LEFT","element":"ROOT","separator":"COMMA","right":"RIGHT"}
        )"));
        ASSERT_NE(pack->getGrammar("probe"), nullptr);
        EXPECT_FALSE(Parser::parse(u"[1,[2,3],[]]", *pack->getGrammar("probe")).isError());
    }


    TEST(CoreContractsTest, HighlightResultBorrowsCommandStorageAndChecksDebugRanges) {
        Node::NodeInteger integer;
        const auto ast = Parser::parse(u"123456789", integer);
        auto result = SyntaxHighlight::getSyntaxResult(ast);
        EXPECT_EQ(result.str, u"123456789");
        EXPECT_EQ(result.str.data(), ast.tokens.lexerResult->content.data());
#if CHelperDebug
        EXPECT_THROW(result.update(result.str.size(), SyntaxHighlight::SyntaxTokenType::ID), std::out_of_range);
        EXPECT_THROW(result.update(4, 3, SyntaxHighlight::SyntaxTokenType::ID), std::out_of_range);
        EXPECT_THROW(result.update(0, 256, SyntaxHighlight::SyntaxTokenType::ID), std::out_of_range);
#endif
    }

    TEST(CoreContractsTest, OldBlockDataCannotWrapToAnExistingMapping) {
        Old2New::BlockFixData fixes;
        fixes[u"stone"][0] = {std::nullopt, u"[\"probe\"=0]"};
        EXPECT_EQ(Old2New::old2new(fixes, u"setblock ~~~ stone 0"), u"setblock ~~~ stone[\"probe\"=0]");
        EXPECT_EQ(Old2New::old2new(fixes, u"setblock ~~~ stone -0"), u"setblock ~~~ stone[\"probe\"=0]");
        EXPECT_EQ(Old2New::old2new(fixes, u"setblock ~~~ stone +0"), u"setblock ~~~ stone[\"probe\"=0]");
        for (const auto *value: {u"4294967296", u"18446744073709551616", u"-1", u"1-2"}) {
            EXPECT_EQ(Old2New::old2new(fixes, std::u16string(u"setblock ~~~ stone ") + value), u"setblock ~~~ stone");
        }
    }
    TEST(CoreContractsTest, SuggestionsBorrowExistingResourceContentWithoutCopying) {
        std::shared_ptr<const CPack> pack = serialization::createCPackByJson(makeCpackJson("[]", "[]", R"([
            {"name":["ns"],"syntax":["/ns <id>"],"node":{"<id>":{"type":"NAMESPACE_ID","key":"probe"}}}
        ])",
                                                                                           R"({"type":"namespace","id":"probe","content":[{"name":"stone","description":"a long description exceeding inline storage"}]})"));
        CHelperCore core(pack);
        std::unique_ptr<CommandContext> context(core.createContext(u"ns sto"));
        const auto suggestions = context->getSuggestions(6);
        const auto ids = pack->getNamespaceId("probe");
        static_assert(std::is_same_v<decltype(AutoSuggestion::Suggestion::content), std::shared_ptr<NormalId>>);
        ASSERT_FALSE(suggestions.empty());
        EXPECT_EQ(suggestions.front().content.get(), ids->front().get());
        EXPECT_EQ(*suggestions.front().content->description, u"a long description exceeding inline storage");
    }

    TEST(CoreContractsTest, SuggestionDigestIncludesFieldBoundariesAndSpaceBehavior) {
        AutoSuggestion::Suggestions values;
        AutoSuggestion::Suggestion first(0, 1, false, NormalId::make(u"ab", u"c"));
        AutoSuggestion::Suggestion second(0, 1, false, NormalId::make(u"a", u"bc"));
        ASSERT_NE(first.hashCode(), second.hashCode());
        values.addIdSuggestion(std::move(first));
        values.addIdSuggestion(std::move(second));
        values.addIdSuggestion({0, 1, false, NormalId::make(u"ab", u"c")});
        values.addIdSuggestion({0, 1, true, NormalId::make(u"ab", u"c")});
        const auto result = values.collect();
        ASSERT_EQ(result.size(), 3u);
        EXPECT_EQ(result[0].content->name, u"ab");
        EXPECT_EQ(result[1].content->name, u"a");
        EXPECT_TRUE(result[2].isAddSpace);
        const std::u16string prefix(256, u'x');
        const AutoSuggestion::Suggestion longFirst(0, 1, false, NormalId::make(prefix + u"ab", u"c"));
        const AutoSuggestion::Suggestion longSecond(0, 1, false, NormalId::make(prefix + u"a", u"bc"));
        EXPECT_NE(longFirst.hashCode(), longSecond.hashCode());
        auto noDescription = NormalId::make(u"text");
        auto emptyDescription = NormalId::make(u"text");
        emptyDescription->description.emplace();
        EXPECT_NE(AutoSuggestion::Suggestion(0, 1, false, noDescription).hashCode(),
                  AutoSuggestion::Suggestion(0, 1, false, emptyDescription).hashCode());
    }

    TEST(CoreContractsTest, PublicFactoriesKeepRawPointersForExternalLifetimeManagement) {
        static_assert(std::is_same_v<decltype(CHelperCore::createByBinary(std::string_view{})), CHelperCore *>);
        static_assert(std::is_same_v<decltype(std::declval<const CHelperCore &>().createContext(u"")), CommandContext *>);
        std::unique_ptr<CHelperCore> core(CHelperCore::create([] { return serialization::createCPackByJson(makeCpackJson()); }));
        ASSERT_NE(core, nullptr);
        std::unique_ptr<CommandContext> context(core->createContext(u"unknown"));
        core.reset();
        EXPECT_FALSE(context->getErrorReasons().empty());
    }


    TEST(CoreContractsTest, MalformedInputsAreRejectedBeforePreallocatingInvalidCounts) {
        std::vector<std::string> values;
        EXPECT_THROW(readBinary(values, std::string(4, static_cast<char>(0xff))), std::runtime_error);
        EXPECT_THROW(serialization::createCPackByJson(makeCpackJson("[]", "[]", R"([
            {"name" ["list"],"syntax":["/list"],"node":{}}
        ])")),
                     CPackLoadError);
    }

}// namespace CHelper::Test
