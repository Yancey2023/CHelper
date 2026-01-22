//
// Created by Yancey on 2024-5-21.
//

#include <chelper/CHelperCore.h>
#include <gtest/gtest.h>

void testCommand(CHelper::CHelperCore *core, const std::u16string &command) {
    core->onTextChanged(command, command.length());
    CHelper::SyntaxHighlight::SyntaxResult syntaxResult = core->getSyntaxResult();
    std::string stringBuilder;

    constexpr uint32_t NO_COLOR = 0xFFFFFFFF;
    constexpr uint32_t COLOR_PURPLE = 0xFFc586c0;
    constexpr uint32_t COLOR_ORANGE = 0xFFce9178;
    constexpr uint32_t COLOR_LIGHT_BLUE = 0xFF9cdcfe;
    constexpr uint32_t COLOR_BLUE = 0xFF179fff;
    constexpr uint32_t COLOR_LIGHT_GREEN = 0xFFb5cea8;
    constexpr uint32_t COLOR_GREEN = 0xFF4ec9b0;
    constexpr uint32_t COLOR_LIGHT_YELLOW = 0xFFdcdcaa;
    constexpr uint32_t COLOR_YELLOW = 0xFFffd700;

    constexpr uint32_t colorUnknown = NO_COLOR;
    constexpr uint32_t colorBoolean = COLOR_LIGHT_GREEN;
    constexpr uint32_t colorFloat = COLOR_LIGHT_GREEN;
    constexpr uint32_t colorInteger = COLOR_LIGHT_GREEN;
    constexpr uint32_t colorSymbol = COLOR_LIGHT_GREEN;
    constexpr uint32_t colorId = COLOR_LIGHT_YELLOW;
    constexpr uint32_t colorTargetSelector = COLOR_GREEN;
    constexpr uint32_t colorCommand = COLOR_PURPLE;
    constexpr uint32_t colorBrackets1 = COLOR_YELLOW;
    constexpr uint32_t colorBrackets2 = COLOR_PURPLE;
    constexpr uint32_t colorBrackets3 = COLOR_BLUE;
    constexpr uint32_t colorString = COLOR_ORANGE;
    constexpr uint32_t colorNull = COLOR_LIGHT_BLUE;
    constexpr uint32_t colorRange = COLOR_LIGHT_BLUE;
    constexpr uint32_t colorLiteral = COLOR_LIGHT_BLUE;

    for (size_t i = 0; i < syntaxResult.tokenTypes.size(); ++i) {
        uint32_t tokenType = syntaxResult.tokenTypes[i];
        std::u16string strChar;
        strChar.push_back(syntaxResult.str[i]);
        uint32_t color = colorUnknown;
        switch (tokenType) {
            case CHelper::SyntaxHighlight::SyntaxTokenType::UNKNOWN:
                color = colorUnknown;
                break;
            case CHelper::SyntaxHighlight::SyntaxTokenType::BOOLEAN:
                color = colorBoolean;
                break;
            case CHelper::SyntaxHighlight::SyntaxTokenType::FLOAT:
                color = colorFloat;
                break;
            case CHelper::SyntaxHighlight::SyntaxTokenType::INTEGER:
                color = colorInteger;
                break;
            case CHelper::SyntaxHighlight::SyntaxTokenType::SYMBOL:
                color = colorSymbol;
                break;
            case CHelper::SyntaxHighlight::SyntaxTokenType::ID:
                color = colorId;
                break;
            case CHelper::SyntaxHighlight::SyntaxTokenType::TARGET_SELECTOR:
                color = colorTargetSelector;
                break;
            case CHelper::SyntaxHighlight::SyntaxTokenType::COMMAND:
                color = colorCommand;
                break;
            case CHelper::SyntaxHighlight::SyntaxTokenType::BRACKET1:
                color = colorBrackets1;
                break;
            case CHelper::SyntaxHighlight::SyntaxTokenType::BRACKET2:
                color = colorBrackets2;
                break;
            case CHelper::SyntaxHighlight::SyntaxTokenType::BRACKET3:
                color = colorBrackets3;
                break;
            case CHelper::SyntaxHighlight::SyntaxTokenType::STRING:
                color = colorString;
                break;
            case CHelper::SyntaxHighlight::SyntaxTokenType::NULL_TOKEN:
                color = colorNull;
                break;
            case CHelper::SyntaxHighlight::SyntaxTokenType::RANGE:
                color = colorRange;
                break;
            case CHelper::SyntaxHighlight::SyntaxTokenType::LITERAL:
                color = colorLiteral;
                break;
            default:
                break;
        }
        stringBuilder.append(fmt::format(
                "{}", fmt::styled(utf8::utf16to8(strChar), fg(fmt::rgb(color)))));
    }
    std::cout << stringBuilder << std::endl;
}

TEST(ColorStringTest, ColorString) {
    std::filesystem::path resourceDir(RESOURCE_DIR);
    CHelper::CHelperCore *core = CHelper::CHelperCore::createByDirectory(
            resourceDir / "resources" / "beta" / "vanilla");
    auto commands = std::vector<std::u16string>{
            uR"(execute run clear )",
            uR"(give @s[hasitem=[{item=air,data=1},{item=minecraft:bed}],has_property={minecraft:is_rolled_up=true,m)",
            uR"(give @s command_block 112 12 {"minecraft:can_place_on":{"blocks":[")",
            uR"(tellraw @a {"rawtext":[{"text":"aaa","selector":"@a[type=\")",
            uR"(execute if block 12~23~)",
            uR"(give @s stone 12 21 {"minecraft:item_lock":{"mode":"l)",
            uR"(execute if block ~~~ anvil["aaa"=90.5] run g)",
            uR"(setblock ~1~2~3 stone[)",
            uR"(give @s stone 12 1)",
            uR"(tag @s add "\\\"\u1110\/\b\f\n\r\t\p\")",
            uR"(give @s command_block 12 12 {"minecraft:can_destroy":{"blocks":["minecraft:acacia_door"]}})",
            uR"(give @s s)",
            uR"(give @s stone 1 1 {)",
            uR"(give @s 石头)",
            uR"(give @s command_block 12 12 {"minecraft:can_destroy":{"blocks":[")",
            uR"(give)",
            uR"(give @)",
            uR"(give @a[x=^2,y=2,has_property={}])",
            uR"(give @a[x=^,has_property={""=!..12,="..}})",
            uR"(execute as @a run)",
            uR"(execute run)",
            uR"(execute if block ~~~ command_block run)",
            uR"(execute if block ~~~ bamboo)",
            uR"(give @s apple 12 1)",
            uR"(spreadplayers ~ ~ 0 1200000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000)",
            uR"(camerashake add @a 10000000000000000000000000000000000000000000000000000 3402823466385288598117041834845169254401)",
            uR"(setblock ~~~ candle_cake[lit=)",
            uR"(give @s repeating_command_block)",
    };
    for (const auto &command: commands) {
        testCommand(core, command);
    }
    delete core;
}
