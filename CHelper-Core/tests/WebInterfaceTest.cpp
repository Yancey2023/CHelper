#include <chelper/CHelperCore.h>
#include <cstdint>
#include <cstring>
#include <gtest/gtest.h>

extern "C" {
const uint8_t *contextGetErrorReasons(const CHelper::CommandContext *context);
}

namespace CHelper::Test {

    TEST(WebInterfaceTest, ErrorOutputPreservesRangesUtf16AndRecordAlignment) {
        const auto cpack = std::shared_ptr<const CPack>(serialization::createCPackByDirectory(
                std::filesystem::path(RESOURCE_DIR) / "resources" / "beta" / "vanilla"));
        ASSERT_NE(cpack, nullptr);
        for (const auto command: {u"say hello", u"unknown_command 中文🙂", u"give @s", uR"(give @s stone 1 0 {"minecraft:can_destroy":{"blocks":["missing","不存在🙂","other"]}})"}) {
            SCOPED_TRACE(utf8::utf16to8(std::u16string_view(command)));
            const CommandContext context(cpack, command);
            const auto errors = context.getErrorReasons();
            const auto *bytes = contextGetErrorReasons(&context);
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

}// namespace CHelper::Test
