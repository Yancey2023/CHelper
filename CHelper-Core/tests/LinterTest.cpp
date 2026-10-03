#include <chelper/lexer/Lexer.h>
#include <chelper/linter/Linter.h>
#include <chelper/node/NodeType.h>
#include <gtest/gtest.h>

namespace CHelper::Test {

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
            EXPECT_EQ(errors[1]->errorReason, errors[2]->errorReason);
        }
        first.customContents->push_back(NormalId::make(u"missing"));
        const auto errors = Linter::getErrorReasons(ast);
        ASSERT_EQ(errors.size(), 1);
        EXPECT_EQ(errors[0]->start, 6);
    }

}// namespace CHelper::Test
