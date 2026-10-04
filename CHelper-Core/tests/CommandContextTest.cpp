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

#include <barrier>
#include <chelper/CHelperCore.h>
#include <chelper/command_structure/CommandStructure.h>
#include <chelper/lexer/Lexer.h>
#include <chelper/node/NodeType.h>
#include <chelper/parser/ErrorReasonFactory.h>
#include <chelper/serialization/Serialization.h>
#include <chelper/syntax_highlight/SyntaxHighlight.h>
#include <chelper/util/JsonUtil.h>
#include <gtest/gtest.h>
#include <mutex>
#include <thread>

namespace CHelper::Test {

    namespace {

        std::shared_ptr<const CPack> loadCPack() {
            std::filesystem::path resourceDir(RESOURCE_DIR);
            std::shared_ptr<const CPack> cpack = CHelper::serialization::createCPackByDirectory(resourceDir / "resources" / "beta" / "vanilla");
            EXPECT_TRUE(cpack != nullptr);
            return cpack;
        }

        const std::vector<std::u16string> &getTestCommands() {
            static const std::vector<std::u16string> commands{
                    uR"(list)",
                    uR"(give @s stone 12 1)",
                    uR"(execute if block ~~~ anvil["aaa"=90.5] run g)",
                    uR"(tellraw @a {"rawtext":[{"text":"aaa"}]})",
                    uR"(give @s 石头)",
                    uR"(give @s stone 1 0 {"minecraft:can_destroy":{"blocks":["minecraft:stone","minecraft:dirt","minecraft:stone","minecraft:\u0073tone"]}})",
            };
            return commands;
        }

        /**
         * 对一个上下文执行所有只读操作，把结果序列化成字符串用于比较
         */
        std::string collectAllResults(const CommandContext &context, size_t hintIndex, size_t suggestionIndex) {
            std::string result;
            result += utf8::utf16to8(context.getStructure());
            result += "|";
            result += utf8::utf16to8(context.getParamHint(hintIndex));
            result += "|";
            result += utf8::utf16to8(context.getCommand());
            result += "|";
            for (const auto &errorReason: context.getErrorReasons()) {
                result += std::to_string(errorReason->start) + "," + std::to_string(errorReason->end) + "," + utf8::utf16to8(errorReason->getMessage()) + ";";
            }
            result += "|";
            for (const auto &suggestion: context.getSuggestions(suggestionIndex)) {
                result += std::to_string(suggestion.start) + "," + std::to_string(suggestion.end) + "," + utf8::utf16to8(suggestion.content->name) + ";";
            }
            result += "|";
            for (const auto tokenType: context.getSyntaxResult().tokenTypes) {
                result += std::to_string(static_cast<int>(tokenType)) + ",";
            }
            result += "|";
            result += std::to_string(context.getNodeCount());
            return result;
        }

    }// namespace

    TEST(CommandContextTest, ContextProvidesAllOperations) {
        std::shared_ptr<const CPack> cpack = loadCPack();
        const std::u16string command = uR"(give @s stone 12 1)";
        CommandContext context(cpack, command);

        EXPECT_EQ(context.getCommand(), std::u16string_view(command));
        EXPECT_FALSE(context.getStructure().empty());
        EXPECT_FALSE(context.getParamHint(5).empty());
        // 命令完整，不应有错误原因
        EXPECT_TRUE(context.getErrorReasons().empty());
        // 完整的命令在末尾没有补全建议
        EXPECT_TRUE(context.getSuggestions(command.length()).empty());
        // 语法高亮的token数量和命令字符数量一致
        EXPECT_EQ(context.getSyntaxResult().tokenTypes.size(), command.length());
        EXPECT_GT(context.getNodeCount(), 0);

        // 没有输入完成的命令在末尾有补全建议
        const std::u16string incomplete = uR"(give @s sto)";
        CommandContext incompleteContext(cpack, incomplete);
        EXPECT_FALSE(incompleteContext.getSuggestions(incomplete.length()).empty());
    }

    TEST(CommandContextTest, ContextsFromSharedCore) {
        std::shared_ptr<const CPack> cpack = loadCPack();
        // 基于同一个内核为多条命令创建上下文，这些上下文互相独立
        CHelperCore core(cpack);
        for (const auto &command: getTestCommands()) {
            std::unique_ptr<CommandContext> context(core.createContext(command));
            EXPECT_EQ(context->getCommand(), std::u16string_view(command)) << utf8::utf16to8(command);
            EXPECT_FALSE(context->getStructure().empty()) << utf8::utf16to8(command);
        }
    }

    TEST(CommandContextTest, ApplySuggestionDoesNotMutateContext) {
        std::shared_ptr<const CPack> cpack = loadCPack();
        const std::u16string command = uR"(gi)";
        CommandContext context(cpack, command);

        std::vector<AutoSuggestion::Suggestion> suggestions = context.getSuggestions(command.length());
        ASSERT_FALSE(suggestions.empty());
        // 找一个内容非空的补全建议
        size_t which = suggestions.size();
        for (size_t i = 0; i < suggestions.size(); ++i) {
            if (!suggestions[i].content->name.empty() && suggestions[i].content->name != u" ") {
                which = i;
                break;
            }
        }
        if (which == suggestions.size()) {
            GTEST_SKIP() << "没有可用的非空补全建议";
        }

        std::optional<std::pair<std::u16string, size_t>> result = context.applySuggestion(command.length(), which);
        ASSERT_TRUE(result.has_value());
        // 应用补全后命令文本应当变长
        EXPECT_GT(result->first.length(), command.length());
        // 上下文本身没有被修改，同样的操作可以重复执行
        std::optional<std::pair<std::u16string, size_t>> result2 = context.applySuggestion(command.length(), which);
        EXPECT_EQ(result->first, result2->first);
        EXPECT_EQ(result->second, result2->second);
        // 越界的补全建议返回std::nullopt
        EXPECT_FALSE(context.applySuggestion(command.length(), suggestions.size()).has_value());
    }

    TEST(CommandContextTest, AppliesSuggestionAfterALongConditionChainWithoutMutatingContext) {
        const auto cpack = loadCPack();
        std::u16string command = u"execute ";
        for (size_t i = 0; i < 1024; ++i) command.append(u"if block ~~~ stone ");
        command.append(u"run g");
        const CommandContext context(cpack, command);
        const auto suggestions = context.getSuggestions(command.size());
        const auto found = std::ranges::find_if(suggestions, [](const auto &suggestion) { return suggestion.content->name == u"give"; });
        ASSERT_NE(found, suggestions.end());
        const auto which = static_cast<size_t>(found - suggestions.begin());
        const auto applied = context.applySuggestion(command.size(), which);
        ASSERT_TRUE(applied.has_value());
        const auto expected = command.substr(0, command.size() - 1) + u"give ";
        EXPECT_EQ(applied->first, expected);
        EXPECT_EQ(applied->second, expected.size());
        EXPECT_EQ(context.getCommand(), command);
        EXPECT_EQ(context.applySuggestion(command.size(), which), applied);
        EXPECT_FALSE(context.applySuggestion(command.size(), suggestions.size()).has_value());
    }

    TEST(CommandContextTest, ParallelContextsOnSharedCPack) {
        std::shared_ptr<const CPack> cpack = loadCPack();

        // 先串行执行一遍，记录每个命令的标准结果
        std::vector<std::string> expected;
        for (const auto &command: getTestCommands()) {
            CommandContext context(cpack, command);
            expected.push_back(collectAllResults(context, command.length() / 2, command.length()));
        }

        // 多线程并行：每个线程创建自己的CommandContext，共享同一个CPack
        constexpr size_t threadCount = 4;
        constexpr size_t rounds = 3;
        std::vector<std::thread> threads;
        std::mutex mutex;
        std::vector<std::string> failures;
        for (size_t t = 0; t < threadCount; ++t) {
            threads.emplace_back([&cpack, &expected, &mutex, &failures]() {
                for (size_t r = 0; r < rounds; ++r) {
                    for (size_t i = 0; i < getTestCommands().size(); ++i) {
                        try {
                            CommandContext context(cpack, getTestCommands()[i]);
                            std::string result = collectAllResults(context, getTestCommands()[i].length() / 2, getTestCommands()[i].length());
                            std::lock_guard<std::mutex> lock(mutex);
                            if (result != expected[i]) {
                                failures.push_back("thread result mismatch on command " + std::to_string(i));
                            }
                        } catch (const std::exception &e) {
                            std::lock_guard<std::mutex> lock(mutex);
                            failures.push_back(std::string("exception: ") + e.what());
                        }
                    }
                }
            });
        }
        // 同时在主线程也持续使用一个上下文，验证同一个上下文可以并发读取
        threads.emplace_back([&cpack, &mutex, &failures]() {
            try {
                CommandContext context(cpack, uR"(execute as @a run say hi)");
                std::string structure = utf8::utf16to8(context.getStructure());
                for (size_t i = 0; i < 16; ++i) {
                    if (utf8::utf16to8(context.getStructure()) != structure) {
                        std::lock_guard<std::mutex> lock(mutex);
                        failures.push_back("same context is not stable under concurrent reads");
                    }
                }
            } catch (const std::exception &e) {
                std::lock_guard<std::mutex> lock(mutex);
                failures.push_back(std::string("exception: ") + e.what());
            }
        });
        for (auto &thread: threads) {
            thread.join();
        }
        EXPECT_TRUE(failures.empty()) << (failures.empty() ? "" : failures.front());
    }

    TEST(CommandContextTest, ContextOutlivesCore) {
        std::shared_ptr<const CPack> cpack = loadCPack();
        std::unique_ptr<CommandContext> context;
        {
            CHelperCore core(cpack);
            context.reset(core.createContext(uR"(list)"));
        }
        // core已经销毁，但context持有CPack的共享引用，依然可用
        EXPECT_FALSE(context->getStructure().empty());
        EXPECT_EQ(context->getNodeCount(), 1);
        CHelperCore::deleteContext(context.release());
    }

    TEST(CommandContextTest, ErrorReasonsOutliveContext) {
        std::shared_ptr<const CPack> cpack = loadCPack();
        std::vector<std::shared_ptr<const ErrorReason>> errorReasons;
        {
            CommandContext context(cpack, uR"(give @s)");
            errorReasons = context.getErrorReasons();
            ASSERT_FALSE(errorReasons.empty());
        }

        ASSERT_FALSE(errorReasons.empty());
        EXPECT_FALSE(errorReasons.front()->getMessage().empty());
    }

    TEST(CommandContextTest, ErrorQuerySharesReadOnlyDiagnosticsWithoutChangingAST) {
        const auto cpack = loadCPack();
        CommandContext context(cpack, u"unknown_command 中文");
        const auto &parsed = context.getAstNode()->errorReasons;
        ASSERT_FALSE(parsed.empty());
        ASSERT_EQ(parsed.front()->getCode(), ErrorReasonCode::UnknownCommand);
        const auto message = parsed.front()->getMessage();
        const auto first = context.getErrorReasons();
        static_assert(std::is_const_v<decltype(first)::value_type::element_type>);
        ASSERT_FALSE(first.empty());
        EXPECT_EQ(first.front().get(), parsed.front().get());
        auto display = first.front()->getMessage();
        display = u"修改展示结果";
        const auto second = context.getErrorReasons();
        ASSERT_FALSE(second.empty());
        EXPECT_EQ(second.front()->getMessage(), message);
        EXPECT_EQ(second.front()->start, parsed.front()->start);
        EXPECT_EQ(parsed.front()->getMessage(), message);
    }

    TEST(CommandContextTest, CopiesTemporaryInputAndResultsOutliveContext) {
        const auto cpack = loadCPack();
        std::vector<AutoSuggestion::Suggestion> suggestions;
        std::optional<std::pair<std::u16string, size_t>> applied;
        std::u16string structure, hint;
        {
            std::u16string input = u"gi";
            CommandContext context(cpack, input);
            input.assign(1024, u'x');
            EXPECT_EQ(context.getCommand(), u"gi");
            suggestions = context.getSuggestions(2);
            structure = context.getStructure();
            hint = context.getParamHint(2);
            applied = context.applySuggestion(2, 0);
        }
        ASSERT_FALSE(suggestions.empty());
        EXPECT_FALSE(suggestions.front().content->name.empty());
        EXPECT_FALSE(structure.empty());
        EXPECT_FALSE(hint.empty());
        ASSERT_TRUE(applied);
        EXPECT_EQ(applied->first, u"give ");
        EXPECT_EQ(applied->second, 5);
        CommandContext temporary(cpack, std::u16string(u"list"));
        EXPECT_EQ(temporary.getCommand(), u"list");
    }

    TEST(CommandContextTest, ConcurrentReadsAndSuggestionApplicationOnSameContext) {
        const auto cpack = loadCPack();
        const std::u16string command = uR"(execute as @a run give @s sto)";
        const CommandContext context(cpack, command);
        const auto expected = collectAllResults(context, command.size() / 2, command.size());
        const auto expectedApplied = context.applySuggestion(command.size(), 0);
        std::barrier ready(4);
        std::array<std::string, 4> failures;
        std::vector<std::thread> threads;
        for (size_t i = 0; i < failures.size(); ++i) {
            threads.emplace_back([&, i] {
                ready.arrive_and_wait();
                try {
                    for (size_t iteration = 0; iteration < 16; ++iteration) {
                        if (collectAllResults(context, command.size() / 2, command.size()) != expected ||
                            context.applySuggestion(command.size(), 0) != expectedApplied) {
                            failures[i] = "concurrent result mismatch";
                        }
                    }
                } catch (const std::exception &error) {
                    failures[i] = error.what();
                }
            });
        }
        for (auto &thread: threads) thread.join();
        for (const auto &failure: failures) EXPECT_TRUE(failure.empty()) << failure;
    }

    TEST(CommandContextTest, HighlightsPlainAndEscapedInnerStringsInTheirOriginalCoordinates) {
        const Node::NodeNamespaceId innerType;
        const Node::NodeJsonString outerType;
        const auto innerLexer = Lexer::lex(u"[x]");
        const std::array inputs{uR"("[x]")", uR"("\u005Bx]")", uR"("[x])"};
        for (const auto *input: inputs) {
            const auto outerLexer = Lexer::lex(input);
            auto inner = ASTNode::simpleNode(innerType, TokensView(innerLexer, 0, innerLexer->allTokens.size()));
            auto outer = ASTNode::andNode(outerType, ASTNode::children(std::move(inner)),
                                          TokensView(outerLexer, 0, outerLexer->allTokens.size()), nullptr,
                                          ASTNodeId::NODE_STRING_INNER);
            const auto syntax = SyntaxHighlight::getSyntaxResult(outer);
            const auto mapped = JsonUtil::jsonString2String(input);
            ASSERT_EQ(mapped.result, u"[x]");
            EXPECT_EQ(syntax.tokenTypes[0], SyntaxHighlight::SyntaxTokenType::STRING);
            if (mapped.isComplete) {
                EXPECT_EQ(syntax.tokenTypes.back(), SyntaxHighlight::SyntaxTokenType::STRING);
            }
            const std::array colors{SyntaxHighlight::SyntaxTokenType::BRACKET1, SyntaxHighlight::SyntaxTokenType::ID,
                                    SyntaxHighlight::SyntaxTokenType::BRACKET1};
            for (size_t index = 0; index < colors.size(); ++index) {
                for (size_t position = mapped.convert(index); position < mapped.convert(index + 1); ++position) {
                    EXPECT_EQ(syntax.tokenTypes[position], colors[index]);
                }
            }
        }
    }

    TEST(CommandContextTest, HighlightsDeepAndMismatchedBrackets) {
        constexpr size_t depth = 40;
        const std::u16string text = std::u16string(depth, u'[') + u"}" + std::u16string(depth, u']') + u"[]";
        const auto lexer = Lexer::lex(text);
        const auto ast = ASTNode::simpleNode(Node::NodeAny::getNodeAny(), TokensView(lexer, 0, lexer->allTokens.size()));
        const auto syntax = SyntaxHighlight::getSyntaxResult(ast);
        const std::array colors{SyntaxHighlight::SyntaxTokenType::BRACKET1,
                                SyntaxHighlight::SyntaxTokenType::BRACKET2,
                                SyntaxHighlight::SyntaxTokenType::BRACKET3};
        for (size_t i = 0; i < depth; ++i) {
            EXPECT_EQ(syntax.tokenTypes[i], colors[i % colors.size()]);
            EXPECT_EQ(syntax.tokenTypes[depth + 1 + i], colors[(depth - 1 - i) % colors.size()]);
        }
        EXPECT_EQ(syntax.tokenTypes[text.size() - 2], colors[0]);
        EXPECT_EQ(syntax.tokenTypes[text.size() - 1], colors[0]);
    }

    TEST(CommandContextTest, StructureCountsUnenteredParametersAndLongUnicodeBriefs) {
        Node::initializeStaticNodes();
        const auto lexer = Lexer::lex(u"");
        const TokensView tokens(lexer, 0, 0);
        const std::vector<std::u16string> briefs{
                u"", u"参数", u"中文🙂", std::u16string(u"a\0b", 3), std::u16string(2048, u'参')};
        for (const auto &brief: briefs) {
            Node::NodeString first, second;
            first.brief.emplace(brief);
            second.brief.emplace(u"尾参数");
            Node::NodeWrapped secondWrapped(second), firstWrapped(first);
            secondWrapped.pushNextNode(Node::NodeLF::getInstance());
            firstWrapped.pushNextNode(&secondWrapped);
            const auto ast = ASTNode::simpleNode(firstWrapped, tokens, ErrorReasons::requireSpace(ErrorReasonLevel::REQUIRE_SPACE, tokens));
            const auto result = CommandStructure::getStructure(ast);
            EXPECT_EQ(result, u"<" + brief + u"> <尾参数>");
            EXPECT_EQ(result.size(), brief.size() + 8);
        }
    }

}// namespace CHelper::Test
