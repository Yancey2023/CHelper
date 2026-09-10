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

#include <chelper/CHelperCore.h>
#include <chelper/serialization/Serialization.h>
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
                result += std::to_string(errorReason->start) + "," + std::to_string(errorReason->end) + "," + utf8::utf16to8(errorReason->errorReason) + ";";
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

        EXPECT_EQ(context.getCommand(), command);
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
            EXPECT_EQ(context->getCommand(), command) << utf8::utf16to8(command);
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

}// namespace CHelper::Test
