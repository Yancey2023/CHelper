#include <chelper/CHelperCore.h>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <filesystem>
#include <gtest/gtest.h>
#include <memory>
#include <string>
#include <string_view>
#include <utf8.h>

namespace CHelper {
    class WebCommandContext;
}

extern "C" {
CHelper::WebCommandContext *createCommandContext(const CHelper::CHelperCore *core, const char16_t *command);
void releaseCommandContext(CHelper::WebCommandContext *context);
const uint8_t *contextGetErrorReasons(const CHelper::WebCommandContext *context);
size_t contextGetSuggestionSize(CHelper::WebCommandContext *context, size_t index);
const uint8_t *contextGetSuggestion(CHelper::WebCommandContext *context, size_t index, size_t which);
}

namespace CHelper::Test {

    TEST(WebInterfaceTest, ErrorOutputPreservesRangesUtf16AndRecordAlignment) {
        const auto cpack = std::shared_ptr<const CPack>(serialization::createCPackByDirectory(
                std::filesystem::path(RESOURCE_DIR) / "resources" / "beta" / "vanilla"));
        ASSERT_NE(cpack, nullptr);
        CHelperCore core(cpack);
        for (const auto command: {u"say hello", u"unknown_command 中文🙂", u"give @s", uR"(give @s stone 1 0 {"minecraft:can_destroy":{"blocks":["missing","不存在🙂","other"]}})"}) {
            SCOPED_TRACE(utf8::utf16to8(std::u16string_view(command)));
            const CommandContext context(cpack, command);
            const auto errors = context.getErrorReasons();
            const std::unique_ptr<WebCommandContext, decltype(&releaseCommandContext)> webContext(
                    createCommandContext(&core, command), releaseCommandContext);
            ASSERT_NE(webContext, nullptr);
            const auto *bytes = contextGetErrorReasons(webContext.get());
            ASSERT_NE(bytes, nullptr);
            size_t offset = (4 - reinterpret_cast<uintptr_t>(bytes) % 4) % 4;
            const auto read = [&] {
                EXPECT_EQ(reinterpret_cast<uintptr_t>(bytes + offset) % 4, 0u);
                uint32_t value;
                std::memcpy(&value, bytes + offset, sizeof(value));
                offset += sizeof(value);
                return value;
            };
            ASSERT_EQ(read(), errors.size());
            for (const auto &error: errors) {
                EXPECT_EQ(read(), error->start);
                EXPECT_EQ(read(), error->end);
                const auto length = read();
                std::u16string message(length, u'\0');
                std::memcpy(message.data(), bytes + offset, length * sizeof(char16_t));
                EXPECT_EQ(message, error->getMessage());
                offset = (offset + length * sizeof(char16_t) + 3) & ~size_t(3);
            }
        }
        EXPECT_EQ(contextGetErrorReasons(nullptr), nullptr);
    }

    TEST(WebInterfaceTest, SuggestionQueriesFollowEachContextAndCursor) {
        const auto cpack = std::shared_ptr<const CPack>(serialization::createCPackByDirectory(
                std::filesystem::path(RESOURCE_DIR) / "resources" / "beta" / "vanilla"));
        ASSERT_NE(cpack, nullptr);
        CHelperCore core(cpack);
        const std::u16string command = u"give @s ";
        const CommandContext reference(cpack, command);
        const std::unique_ptr<WebCommandContext, decltype(&releaseCommandContext)> first(
                createCommandContext(&core, command.c_str()), releaseCommandContext);
        const std::unique_ptr<WebCommandContext, decltype(&releaseCommandContext)> second(
                createCommandContext(&core, u"say hello"), releaseCommandContext);
        ASSERT_NE(first, nullptr);
        ASSERT_NE(second, nullptr);

        const size_t startCount = reference.getSuggestions(0).size();
        const size_t endCount = reference.getSuggestions(command.size()).size();
        ASSERT_GT(endCount, startCount);
        const auto secondCount = CommandContext(cpack, u"say hello").getSuggestions(0).size();
        ASSERT_NE(secondCount, endCount);

        EXPECT_EQ(contextGetSuggestionSize(first.get(), command.size()), endCount);
        EXPECT_EQ(contextGetSuggestionSize(second.get(), 0), secondCount);
        EXPECT_NE(contextGetSuggestion(first.get(), command.size(), endCount - 1), nullptr);
        EXPECT_EQ(contextGetSuggestion(first.get(), 0, endCount - 1), nullptr);
        EXPECT_EQ(contextGetSuggestionSize(first.get(), 0), startCount);
        EXPECT_NE(contextGetSuggestion(first.get(), command.size(), endCount - 1), nullptr);
    }

}// namespace CHelper::Test
