#include <chelper/lexer/Lexer.h>
#include <chelper/linter/Linter.h>
#include <chelper/node/NodeType.h>
#include <chelper/parser/ErrorReasonFactory.h>
#include <chelper/resources/id/NamespaceId.h>
#include <chelper/util/IdMatchCache.h>
#include <gtest/gtest.h>

namespace CHelper::Test {

    TEST(LinterTest, IdCacheReusesHitsAndMissesAcrossCollections) {
        auto first = std::make_shared<std::pmr::vector<int>>(std::initializer_list<int>{1});
        auto second = std::make_shared<std::pmr::vector<int>>(std::initializer_list<int>{2});
        IdMatchCache cache;
        size_t checked = 0;
        const auto matches = [&](int value) { ++checked; return value == 1; };
        EXPECT_TRUE(cache.contains(first, 17, matches));
        EXPECT_TRUE(cache.contains(first, 17, matches));
        EXPECT_FALSE(cache.contains(second, 17, matches));
        EXPECT_FALSE(cache.contains(second, 17, matches));
        EXPECT_TRUE(cache.contains(first, 17, matches));
        EXPECT_EQ(checked, 2u);
        EXPECT_TRUE(cache.contains(first, 18, matches));
        EXPECT_EQ(checked, 3u);
    }

    TEST(LinterTest, IndexedIdLookupPreservesAliasesCollectionsAndQueryLifetime) {
        const auto hash = [](std::u16string_view name) {
            return XXH3_64bits(name.data(), name.size() * sizeof(char16_t));
        };
        auto normal = std::make_shared<std::pmr::vector<std::shared_ptr<NormalId>>>();
        auto namespaced = std::make_shared<std::pmr::vector<std::shared_ptr<NamespaceId>>>();
        normal->push_back(NormalId::make(u"stone"));
        auto stone = std::make_shared<NamespaceId>();
        stone->name = u"stone";
        namespaced->push_back(stone);
        auto custom = std::make_shared<NamespaceId>();
        custom->name = u"custom";
        custom->idNamespace = u"example";
        namespaced->push_back(custom);
        IdMatchCache cache;
        for (size_t i = 0; i < 32; ++i) {
            const auto missing = fmt::format(u"missing_{}", i);
            EXPECT_FALSE(cache.containsId(normal, hash(missing)));
            EXPECT_FALSE(cache.containsId(namespaced, hash(missing)));
            EXPECT_TRUE(cache.containsId(normal, hash(u"stone")));
            EXPECT_TRUE(cache.containsId(namespaced, hash(u"stone")));
            EXPECT_TRUE(cache.containsId(namespaced, hash(u"minecraft:stone")));
            EXPECT_FALSE(cache.containsId(normal, hash(u"minecraft:stone")));
            EXPECT_TRUE(cache.containsId(namespaced, hash(u"custom")));
            EXPECT_TRUE(cache.containsId(namespaced, hash(u"example:custom")));
            EXPECT_FALSE(cache.containsId(namespaced, hash(u"minecraft:custom")));
        }
        normal->push_back(NormalId::make(u"new"));
        IdMatchCache nextQuery;
        EXPECT_TRUE(nextQuery.containsId(normal, hash(u"new")));
        EXPECT_FALSE(nextQuery.containsId(namespaced, hash(u"new")));
        EXPECT_EQ(stone->getNameHash(), hash(u"stone"));
        EXPECT_TRUE(stone->fastMatch(hash(u"stone")));
        EXPECT_EQ(stone->getNameHash(), hash(u"stone"));
    }

    TEST(LinterTest, SpaceErrorsSkipTheParentCheckButStillVisitItsSemanticChildren) {
        const auto lexer = Lexer::lex(u"missing");
        Node::NodeNormalId id;
        id.customContents = std::make_shared<std::pmr::vector<std::shared_ptr<NormalId>>>();
        for (const Node::NodeWithType parent: {Node::NodeWithType(id), Node::NodeAny::getNodeAny()}) {
            const auto ast = ASTNode::andNode(parent,
                                              ASTNode::children(ASTNode::simpleNode(id, TokensView(lexer, 0, 1))),
                                              TokensView(lexer, 0, 1), ErrorReasons::requireSpace(ErrorReasonLevel::REQUIRE_SPACE, {0, 0}));
            const auto errors = Linter::getErrorsExceptParseError(ast);
            ASSERT_EQ(errors.size(), 1u);
            EXPECT_EQ(errors[0]->getCode(), ErrorReasonCode::UnknownId);
            EXPECT_EQ(errors[0]->start, 0u);
            EXPECT_EQ(errors[0]->end, 7u);
            EXPECT_EQ(errors[0]->getMessage(), u"找不到ID -> missing");
        }
    }

    TEST(LinterTest, NestedStringErrorsPreserveMappingStableOrderAndAstOwnership) {
        const auto outerLexer = Lexer::lex(uR"("\u0022\u006Dissing\u0022")");
        const auto middleLexer = Lexer::lex(uR"("missing")");
        const auto innerLexer = Lexer::lex(u"missing");
        Node::NodeNormalId id;
        id.customContents = std::make_shared<std::pmr::vector<std::shared_ptr<NormalId>>>();
        Node::NodeJsonString string;
        const auto makeNested = [&] {
            auto leaf = ASTNode::simpleNode(id, TokensView(innerLexer, 0, innerLexer->allTokens.size()));
            auto middle = ASTNode::andNode(string, ASTNode::children(std::move(leaf)),
                                           TokensView(middleLexer, 0, middleLexer->allTokens.size()), nullptr,
                                           ASTNodeId::NODE_STRING_INNER);
            return ASTNode::andNode(string, ASTNode::children(std::move(middle)),
                                    TokensView(outerLexer, 0, outerLexer->allTokens.size()), nullptr,
                                    ASTNodeId::NODE_STRING_INNER);
        };
        const auto parseError = ErrorReasons::incomplete(ErrorReasonLevel::INCOMPLETE, {0, 0});
        const auto ast = ASTNode::andNode(Node::NodeAny::getNodeAny(), ASTNode::children(makeNested(), makeNested()),
                                          TokensView(outerLexer, 0, outerLexer->allTokens.size()), parseError);
        for (size_t i = 0; i < 2; ++i) {
            auto errors = Linter::getErrorReasons(ast);
            ASSERT_EQ(errors.size(), 3u);
            EXPECT_EQ(errors[0]->getCode(), ErrorReasonCode::UnknownId);
            EXPECT_EQ(errors[1]->getCode(), ErrorReasonCode::UnknownId);
            EXPECT_EQ(errors[0]->start, 7u);
            EXPECT_EQ(errors[0]->end, 19u);
            EXPECT_EQ(errors[1]->start, errors[0]->start);
            EXPECT_EQ(errors[1]->end, errors[0]->end);
            EXPECT_NE(errors[0].get(), errors[1].get());
            EXPECT_EQ(errors[2].get(), parseError.get());
            EXPECT_EQ(errors[2]->getCode(), ErrorReasonCode::Incomplete);
            EXPECT_EQ(parseError->getMessage(), u"命令不完整");
        }
    }

    TEST(LinterTest, RepeatedIdsKeepDistinctErrorsAndCollections) {
        const auto lexer = Lexer::lex(u"stone stone missing missing");
        Node::NodeNormalId first, second;
        first.customContents = std::make_shared<std::pmr::vector<std::shared_ptr<NormalId>>>();
        second.customContents = std::make_shared<std::pmr::vector<std::shared_ptr<NormalId>>>();
        first.customContents->push_back(NormalId::make(u"stone"));
        second.customContents->push_back(NormalId::make(u"dirt"));
        const auto ast = ASTNode::andNode(
                Node::NodeAny::getNodeAny(),
                ASTNode::children(ASTNode::simpleNode(first, TokensView(lexer, 0, 1)),
                                  ASTNode::simpleNode(second, TokensView(lexer, 2, 3)),
                                  ASTNode::simpleNode(first, TokensView(lexer, 4, 5)),
                                  ASTNode::simpleNode(first, TokensView(lexer, 6, 7))),
                TokensView(lexer, 0, lexer->allTokens.size()));
        for (size_t i = 0; i < 2; ++i) {
            const auto errors = Linter::getErrorReasons(ast);
            ASSERT_EQ(errors.size(), 3);
            EXPECT_EQ(errors[0]->start, 6);
            EXPECT_EQ(errors[0]->end, 11);
            EXPECT_EQ(errors[1]->start, 12);
            EXPECT_EQ(errors[1]->end, 19);
            EXPECT_EQ(errors[2]->start, 20);
            EXPECT_EQ(errors[2]->end, 27);
            EXPECT_NE(errors[1], errors[2]);
            EXPECT_EQ(errors[1]->getMessage(), errors[2]->getMessage());
        }
        first.customContents->push_back(NormalId::make(u"missing"));
        const auto errors = Linter::getErrorReasons(ast);
        ASSERT_EQ(errors.size(), 1);
        EXPECT_EQ(errors[0]->start, 6);
    }

}// namespace CHelper::Test
