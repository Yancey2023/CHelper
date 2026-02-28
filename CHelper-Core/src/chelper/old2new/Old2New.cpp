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

#include <chelper/node/CommandNode.h>
#include <chelper/old2new/Old2New.h>

CODEC_REGISTER_JSON_KEY(CHelper::Old2New::DataFix, name, data, newBlockId, blockState)

namespace CHelper::Old2New {

    DataFix::DataFix(size_t start,
                     size_t anEnd,
                     std::u16string content)
        : start(start),
          end(anEnd),
          content(std::move(content)) {}

    DataFix::DataFix(const TokensView &tokens,
                     std::u16string content)
        : start(tokens.startIndex),
          end(tokens.endIndex),
          content(std::move(content)) {}

    std::u16string trip(std::u16string str) {
        str.erase(0, str.find_first_not_of(' '));
        str.erase(str.find_last_not_of(' ') + 1);
        return str;
    }

    bool expect(TokenReader &tokenReader, const std::function<bool(const Token &token)> &check) {
        tokenReader.push();
        tokenReader.skipSpace();
        const Token *token = tokenReader.read();
        if (token == nullptr || !check(*token)) {
            tokenReader.restore();
            return false;
        }
        tokenReader.pop();
        return true;
    }

    bool expectString(TokenReader &tokenReader) {
        return expect(tokenReader, [](const Token &token) {
            return token.type == TokenType::STRING;
        });
    }

    bool expectString(TokenReader &tokenReader, const std::u16string &str) {
        return expect(tokenReader, [&str](const Token &token) {
            return token.type == TokenType::STRING && token.content == str;
        });
    }

    bool expectSymbol(TokenReader &tokenReader, char16_t ch) {
        return expect(tokenReader, [&ch](const Token &token) {
            return token.type == TokenType::SYMBOL && token.content.length() == 1 && token.content[0] == ch;
        });
    }

    bool expectNumber(TokenReader &tokenReader) {
        return expect(tokenReader, [](const Token &token) {
            return token.type == TokenType::NUMBER;
        });
    }

    bool expectList(TokenReader &tokenReader, char16_t leftSymbol, char16_t rightSymbol) {
        tokenReader.push();
        if (!expectSymbol(tokenReader, leftSymbol)) {
            tokenReader.restore();
            return false;
        }
        size_t left = 1;
        size_t right = 0;
        while (true) {
            if (left == right) {
                tokenReader.pop();
                return true;
            }
            if (expectSymbol(tokenReader, leftSymbol)) {
                left++;
                continue;
            }
            if (expectSymbol(tokenReader, rightSymbol)) {
                right++;
                continue;
            }
            if (!tokenReader.skip()) {
                tokenReader.restore();
                return false;
            }
        }
    }

    bool expectTargetSelector(TokenReader &tokenReader) {
        if (expectString(tokenReader)) {
            return true;
        }
        tokenReader.push();
        if (!expectSymbol(tokenReader, u'@')) {
            tokenReader.restore();
            return false;
        }
        if (!expectString(tokenReader)) {
            tokenReader.restore();
            return false;
        }
        expectList(tokenReader, u'[', u']');
        return true;
    }

    bool expectRelativeFloat(TokenReader &tokenReader) {
        tokenReader.push();
        if (expectSymbol(tokenReader, u'~') || expectSymbol(tokenReader, u'^')) {
            tokenReader.push();
            if (!tokenReader.skipSpace()) {
                tokenReader.pop();
                expectNumber(tokenReader);
            } else {
                tokenReader.restore();
            }
            tokenReader.pop();
            return true;
        }
        if (!expectNumber(tokenReader)) {
            tokenReader.restore();
            return false;
        } else {
            tokenReader.pop();
            return true;
        }
    }

    bool expectPosition(TokenReader &tokenReader) {
        tokenReader.push();
        if (!expectRelativeFloat(tokenReader)) {
            tokenReader.restore();
            return false;
        }
        if (!expectRelativeFloat(tokenReader)) {
            tokenReader.restore();
            return false;
        }
        if (!expectRelativeFloat(tokenReader)) {
            tokenReader.restore();
            return false;
        }
        tokenReader.pop();
        return true;
    }

    std::u16string blockOld2New(const BlockFixData &blockFixData, const TokensView &blockIdToken, const TokensView &dataValueToken) {
        // get block id
        auto blockId = std::u16string(blockIdToken.string());
        // get block data value
        char *end;
        std::string dataValueStr = utf8::utf16to8(dataValueToken.string());
        std::intmax_t dataValue = std::strtoimax(dataValueStr.c_str(), &end, 10);
        if (end == dataValueStr.c_str() || *end != '\0' ||
            dataValue == HUGE_VALF || dataValue == -HUGE_VALF ||
            dataValue < 0) [[unlikely]] {
            // if it is not an integer or in range, return block id directly
            return blockId;
        }
        // get key
        std::u16string front = u"minecraft:";
        std::u16string key = trip(blockId);
        if (key.size() > front.size() && key.starts_with(front)) {
            key = key.substr(front.size());
        }
        // find fixed block state by key
        auto blockIdIter = blockFixData.find(key);
        if (blockIdIter == blockFixData.end()) {
            return blockId;
        }
        const auto &dataValueToBlockState = blockIdIter->second;
        auto dataValueIter = dataValueToBlockState.find(static_cast<uint32_t>(dataValue));
        if (dataValueIter == dataValueToBlockState.end()) {
            return blockId;
        }
        const auto &blockIdWithBlockState = dataValueIter->second;
        if (blockIdWithBlockState.first.has_value()) {
            return front + blockIdWithBlockState.first.value() + blockIdWithBlockState.second.value_or(u"");
        } else {
            return blockIdWithBlockState.first.value_or(blockId) + blockIdWithBlockState.second.value_or(u"");
        }
    }

    /**
     * execute命令转为新语法
     * 旧语法例子：execute @e ~~~ detect ~~-1~ stone 0 run setblock command_block ~~~
     * 新语法例子：execute as @e at @s positioned ~~~ if block ~~-1~ stone setblock command_block ~~~
     */
    bool expectCommandExecute(const BlockFixData &blockFixData, TokenReader &tokenReader, std::vector<DataFix> &dataFixList, size_t depth) {
        tokenReader.push();
        // execute
        tokenReader.push();
        if (!expectString(tokenReader, u"execute")) {
            tokenReader.restore();
            tokenReader.restore();
            return false;
        }
        TokensView tokens1 = tokenReader.collect();
        // @e
        tokenReader.push();
        if (!expectTargetSelector(tokenReader)) {
            tokenReader.restore();
            tokenReader.restore();
            return false;
        }
        TokensView tokens2 = tokenReader.collect();
        // ~~~
        tokenReader.push();
        if (!expectPosition(tokenReader)) {
            tokenReader.restore();
            tokenReader.restore();
            return false;
        }
        TokensView tokens3 = tokenReader.collect();
        // end?
        tokenReader.pop();
        if (depth > 0) {
            dataFixList.emplace_back(tokens1, u"");
        }
        auto targetSelector = std::u16string(tokens2.string());
        if (trip(targetSelector) != u"@s") {
            dataFixList.emplace_back(tokens2, u" as" + targetSelector + u" at @s");
        } else {
            dataFixList.emplace_back(tokens2, u"");
        }
        bool isHavePosition = false;
        tokens3.forEach([&isHavePosition](const Token &token) {
            if (token.type == TokenType::NUMBER) {
                isHavePosition = true;
            }
        });
        if (isHavePosition) {
            dataFixList.emplace_back(tokens3, u" positioned" + std::u16string(tokens3.string()));
        } else {
            dataFixList.emplace_back(tokens3, u"");
        }
        tokenReader.push();
        // detect
        tokenReader.push();
        if (!expectString(tokenReader, u"detect")) {
            tokenReader.pop();
            return true;
        }
        TokensView tokens4 = tokenReader.collect();
        // ~~-1~
        if (!expectPosition(tokenReader)) {
            tokenReader.restore();
            return true;
        }
        // stone
        tokenReader.push();
        if (!expectString(tokenReader)) {
            tokenReader.restore();
            tokenReader.restore();
            return true;
        }
        TokensView tokens5 = tokenReader.collect();
        // 0
        tokenReader.push();
        if (expectNumber(tokenReader)) {
            TokensView tokens6 = tokenReader.collect();
            dataFixList.emplace_back(tokens4, u" if block");
            dataFixList.emplace_back(tokens5, blockOld2New(blockFixData, tokens5, tokens6));
            dataFixList.emplace_back(tokens6, u"");
        } else if (expectSymbol(tokenReader, u'[')) {
            tokenReader.restore();
            tokenReader.push();
            expectList(tokenReader, u'[', u']');
            TokensView tokens6 = tokenReader.collect();
            dataFixList.emplace_back(tokens4, u" if block");
            tokens6.forEach([&dataFixList](const Token &token) {
                if (token.type == TokenType::SYMBOL && token.content == u":") {
                    dataFixList.emplace_back(token.getStartIndex(), token.getEndIndex(), u"=");
                }
            });
        } else {
            tokenReader.restore();
            tokenReader.restore();
            return true;
        }
        // end
        tokenReader.pop();
        return true;
    }

    bool expectCommandExecuteRepeat(const BlockFixData &blockFixData, TokenReader &tokenReader, std::vector<DataFix> &dataFixList) {
        size_t depth = 0;
        while (true) {
            tokenReader.push();
            expectSymbol(tokenReader, u'/');
            if (!expectCommandExecute(blockFixData, tokenReader, dataFixList, depth)) {
                tokenReader.restore();
                break;
            }
            tokenReader.pop();
            depth++;
        }
        if (depth == 0) {
            return false;
        }
        tokenReader.push();
        tokenReader.skipSpace();
        TokensView tokens = tokenReader.collect();
        dataFixList.emplace_back(tokens, u" run ");
        return true;
    }

    /**
     * summon命令转为新语法
     * 旧语法例子：summon creeper ~ ~ ~ minecraft:become_charged "充能苦力怕"
     * 新语法例子：summon creeper ~ ~ ~ ~ ~ minecraft:become_charged "充能苦力怕"
     */
    bool expectCommandSummon(TokenReader &tokenReader, std::vector<DataFix> &dataFixList) {
        tokenReader.push();
        // summon
        if (!expectString(tokenReader, u"summon")) {
            tokenReader.restore();
            return false;
        }
        // creeper
        if (!expectString(tokenReader)) {
            tokenReader.restore();
            return false;
        }
        // end?
        tokenReader.pop();
        // ~~~
        if (!expectPosition(tokenReader)) {
            return true;
        }
        // end?
        tokenReader.push();
        TokensView tokens1 = tokenReader.collect();
        dataFixList.emplace_back(tokens1, u" 0 0");
        // minecraft:become_charged
        if (!expectString(tokenReader)) {
            return true;
        }
        // "充能苦力怕"
        if (!expectString(tokenReader)) {
            return true;
        }
        // end
        return true;
    }

    /**
     * structure命令转为新语法
     * 旧语法例子：structure load <name: string> <to:x y z> [rotation: Rotation] [mirror: Mirror] [includeEntities: Boolean] [includeBlocks: Boolean] [integrity: float] [seed: string]
     * 新语法例子：structure load <name: string> <to:x y z> [rotation: Rotation] [mirror: Mirror] [includeEntities: Boolean] [includeBlocks: Boolean] [waterlogged: Boolean] [integrity: float] [seed: string]
     */
    bool expectCommandStructure(TokenReader &tokenReader, std::vector<DataFix> &dataFixList) {
        tokenReader.push();
        // structure
        if (!expectString(tokenReader, u"structure")) {
            tokenReader.restore();
            return false;
        }
        // load
        if (!expectString(tokenReader, u"load")) {
            tokenReader.restore();
            return false;
        }
        // end?
        tokenReader.pop();
        // <name: string>
        if (!expectString(tokenReader)) {
            return false;
        }
        // <to:x y z>
        if (!expectPosition(tokenReader)) {
            return false;
        }
        // [rotation: Rotation]
        if (!expectNumber(tokenReader)) {
            return true;
        }
        if (!expectString(tokenReader)) {
            return true;
        }
        // [mirror: Mirror]
        if (!expectString(tokenReader)) {
            return true;
        }
        // [includeEntities: Boolean]
        if (!expectString(tokenReader)) {
            return true;
        }
        // [includeBlocks: Boolean]
        if (!expectString(tokenReader)) {
            return true;
        }
        // end?
        tokenReader.push();
        TokensView tokens1 = tokenReader.collect();
        dataFixList.emplace_back(tokens1, u" true");
        // [integrity: float]
        if (!expectNumber(tokenReader)) {
            return true;
        }
        // [seed: string]
        if (!expectString(tokenReader)) {
            return true;
        }
        // end
        return true;
    }

    /**
     * setblock命令转为新语法
     * 旧语法例子：setblock ~~~ stone 1 replace
     * 新语法例子：setblock ~~~ stone["stone_type":"granite"] replace
     */
    bool expectCommandSetBlock(const BlockFixData &blockFixData, TokenReader &tokenReader, std::vector<DataFix> &dataFixList) {
        tokenReader.push();
        // setblock
        if (!expectString(tokenReader, u"setblock")) {
            tokenReader.restore();
            return false;
        }
        // ~~~
        if (!expectPosition(tokenReader)) {
            tokenReader.restore();
            return false;
        }
        // stone
        tokenReader.push();
        if (!expectString(tokenReader)) {
            tokenReader.restore();
            tokenReader.restore();
            return true;
        }
        TokensView tokens1 = tokenReader.collect();
        // 0
        tokenReader.push();
        if (expectNumber(tokenReader)) {
            TokensView tokens2 = tokenReader.collect();
            dataFixList.emplace_back(tokens1, blockOld2New(blockFixData, tokens1, tokens2));
            dataFixList.emplace_back(tokens2, u"");
        } else if (expectSymbol(tokenReader, u'[')) {
            tokenReader.restore();
            tokenReader.push();
            expectList(tokenReader, u'[', u']');
            TokensView tokens2 = tokenReader.collect();
            tokens2.forEach([&dataFixList](const Token &token) {
                if (token.type == TokenType::SYMBOL && token.content == u":") {
                    dataFixList.emplace_back(token.getStartIndex(), token.getEndIndex(), u"=");
                }
            });
        } else {
            tokenReader.restore();
            tokenReader.restore();
            return true;
        }
        // replace
        tokenReader.skipToLF();
        // end
        tokenReader.pop();
        return true;
    }

    /**
     * fill命令转为新语法
     * 旧语法例子：fill ~~~~~~ stone 1 replace stone 2
     * 新语法例子：fill ~~~~~~ stone["stone_type":"granite"] replace stone["stone_type":"granite_smooth"]
     */
    bool expectCommandFill(const BlockFixData &blockFixData, TokenReader &tokenReader, std::vector<DataFix> &dataFixList) {
        tokenReader.push();
        // fill
        if (!expectString(tokenReader, u"fill")) {
            tokenReader.restore();
            return false;
        }
        // ~~~
        if (!expectPosition(tokenReader)) {
            tokenReader.restore();
            return false;
        }
        // ~~~
        if (!expectPosition(tokenReader)) {
            tokenReader.restore();
            return false;
        }
        // stone
        tokenReader.push();
        if (!expectString(tokenReader)) {
            tokenReader.restore();
            tokenReader.restore();
            return true;
        }
        TokensView tokens1 = tokenReader.collect();
        // end?
        tokenReader.pop();
        // 0
        tokenReader.push();
        if (expectNumber(tokenReader)) {
            TokensView tokens2 = tokenReader.collect();
            dataFixList.emplace_back(tokens1, blockOld2New(blockFixData, tokens1, tokens2));
            dataFixList.emplace_back(tokens2, u"");
        } else if (expectSymbol(tokenReader, u'[')) {
            tokenReader.restore();
            tokenReader.push();
            expectList(tokenReader, u'[', u']');
            TokensView tokens2 = tokenReader.collect();
            tokens2.forEach([&dataFixList](const Token &token) {
                if (token.type == TokenType::SYMBOL && token.content == u":") {
                    dataFixList.emplace_back(token.getStartIndex(), token.getEndIndex(), u"=");
                }
            });
        } else {
            tokenReader.restore();
            tokenReader.restore();
            return true;
        }
        // replace
        if (!expectString(tokenReader, u"replace")) {
            return true;
        }
        // stone
        tokenReader.push();
        if (!expectString(tokenReader)) {
            tokenReader.pop();
            return true;
        }
        TokensView tokens3 = tokenReader.collect();
        // 0
        tokenReader.push();
        if (expectNumber(tokenReader)) {
            TokensView tokens4 = tokenReader.collect();
            dataFixList.emplace_back(tokens3, blockOld2New(blockFixData, tokens3, tokens4));
            dataFixList.emplace_back(tokens4, u"");
        } else if (expectSymbol(tokenReader, u'[')) {
            tokenReader.restore();
            tokenReader.push();
            expectList(tokenReader, u'[', u']');
            TokensView tokens4 = tokenReader.collect();
            tokens4.forEach([&dataFixList](const Token &token) {
                if (token.type == TokenType::SYMBOL && token.content == u":") {
                    dataFixList.emplace_back(token.getStartIndex(), token.getEndIndex(), u"=");
                }
            });
        } else {
            tokenReader.restore();
            tokenReader.restore();
            return true;
        }
        return true;
    }

    /**
     * testforblock命令转为新语法
     * 旧语法例子：testforblock ~~~ stone 1
     * 新语法例子：testforblock ~~~ stone["stone_type":"granite"]
     */
    bool expectCommandTestForSetBlock(const BlockFixData &blockFixData, TokenReader &tokenReader, std::vector<DataFix> &dataFixList) {
        tokenReader.push();
        // testforblock
        if (!expectString(tokenReader, u"testforblock")) {
            tokenReader.restore();
            return false;
        }
        // ~~~
        if (!expectPosition(tokenReader)) {
            tokenReader.restore();
            return false;
        }
        // stone
        tokenReader.push();
        if (!expectString(tokenReader)) {
            tokenReader.restore();
            tokenReader.restore();
            return true;
        }
        TokensView tokens1 = tokenReader.collect();
        // 0
        tokenReader.push();
        if (!expectNumber(tokenReader)) {
            tokenReader.pop();
            tokenReader.pop();
            return true;
        }
        TokensView tokens2 = tokenReader.collect();
        // end
        dataFixList.emplace_back(tokens1, blockOld2New(blockFixData, tokens1, tokens2));
        dataFixList.emplace_back(tokens2, u"");
        tokenReader.pop();
        return true;
    }

    bool expectCommand(const BlockFixData &blockFixData, TokenReader &tokenReader, std::vector<DataFix> &dataFixList) {
        if (!tokenReader.ready()) {
            return false;
        }
        expectCommandExecuteRepeat(blockFixData, tokenReader, dataFixList);
        expectSymbol(tokenReader, u'/');
        expectCommandFill(blockFixData, tokenReader, dataFixList);
        expectCommandSetBlock(blockFixData, tokenReader, dataFixList);
        expectCommandSummon(tokenReader, dataFixList);
        expectCommandStructure(tokenReader, dataFixList);
        expectCommandTestForSetBlock(blockFixData, tokenReader, dataFixList);
        tokenReader.skipToLF();
        return true;
    }

    std::u16string old2new(const BlockFixData &blockFixData, std::u16string old) {
        TokenReader tokenReader(std::make_shared<LexerResult>(Lexer::lex(std::move(old))));
        std::vector<DataFix> dataFixList;
        expectCommand(blockFixData, tokenReader, dataFixList);
        std::ranges::sort(dataFixList, [](const DataFix &dataFix1, const DataFix &dataFix2) {
            return dataFix1.start < dataFix2.start;
        });
        std::u16string result;
        size_t index = 0;
        for (const auto &item: dataFixList) {
            if (item.start > index) {
                result.append(tokenReader.lexerResult->content.substr(index, item.start - index));
            }
            result.append(item.content);
            index = item.end;
        }
        result.append(tokenReader.lexerResult->content.substr(index));
        return result;
    }

    BlockFixData blockFixDataFromJson(const rapidjson::GenericDocument<rapidjson::UTF8<>> &j) {
        using JsonValueType = rapidjson::GenericDocument<rapidjson::UTF8<>>;
        if (!j.IsArray()) [[unlikely]] {
            throw serialization::exceptions::JsonSerializationTypeException("array", serialization::getJsonTypeStr(j.GetType()));
        }
        BlockFixData blockFixData;
        for (const auto &item: j.GetArray()) {
            if (!item.IsObject()) [[unlikely]] {
                throw serialization::exceptions::JsonSerializationTypeException("object", serialization::getJsonTypeStr(j.GetType()));
            }
            std::u16string name;
            serialization::Codec<decltype(name)>::template from_json_member<typename JsonValueType::ValueType>(item, serialization::details::JsonKey<DataFix, JsonValueType::Ch>::name_(), name);
            uint32_t data;
            serialization::Codec<decltype(data)>::template from_json_member<typename JsonValueType::ValueType>(item, serialization::details::JsonKey<DataFix, JsonValueType::Ch>::data_(), data);
            std::optional<std::u16string> newBlockId;
            serialization::Codec<decltype(newBlockId)>::template from_json_member<typename JsonValueType::ValueType>(item, serialization::details::JsonKey<DataFix, JsonValueType::Ch>::newBlockId_(), newBlockId);
            std::optional<std::u16string> blockState;
            serialization::Codec<decltype(blockState)>::template from_json_member<typename JsonValueType::ValueType>(item, serialization::details::JsonKey<DataFix, JsonValueType::Ch>::blockState_(), blockState);
            const auto &iter = blockFixData.try_emplace(std::move(name)).first;
            iter->second.insert({data, {std::move(newBlockId), std::move(blockState)}});
        }
        return blockFixData;
    }

}// namespace CHelper::Old2New
