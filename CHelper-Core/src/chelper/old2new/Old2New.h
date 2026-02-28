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

#ifndef CHELPER_OLD2NEW_H
#define CHELPER_OLD2NEW_H

#include <chelper/lexer/Lexer.h>
#include <chelper/lexer/TokenReader.h>
#include <pch.h>

namespace CHelper::Old2New {

    using BlockFixData = std::unordered_map<std::u16string, std::unordered_map<uint32_t, std::pair<std::optional<std::u16string>, std::optional<std::u16string>>>>;

    class DataFix {
    public:
        size_t start, end;
        std::u16string content;

        DataFix(size_t start, size_t anEnd, std::u16string content);

        DataFix(const TokensView &tokens, std::u16string content);
    };

    bool expect(TokenReader &tokenReader, const std::function<bool(const Token &token)> &check);

    bool expectString(TokenReader &tokenReader);

    bool expectString(TokenReader &tokenReader, const std::u16string &str);

    bool expectSymbol(TokenReader &tokenReader, char16_t ch);

    bool expectNumber(TokenReader &tokenReader);

    bool expectTargetSelector(TokenReader &tokenReader);

    bool expectRelativeFloat(TokenReader &tokenReader);

    bool expectPosition(TokenReader &tokenReader);

    std::u16string blockOld2New(const BlockFixData &blockFixData, const TokensView &blockIdToken, const TokensView &dataValueToken);

    bool expectCommandExecute(const BlockFixData &blockFixData, TokenReader &tokenReader, std::vector<DataFix> &dataFixList, size_t depth);

    bool expectCommandExecuteRepeat(const BlockFixData &blockFixData, TokenReader &tokenReader, std::vector<DataFix> &dataFixList);

    bool expectCommandSummon(TokenReader &tokenReader, std::vector<DataFix> &dataFixList);

    bool expectCommandStructure(TokenReader &tokenReader, std::vector<DataFix> &dataFixList);

    bool expectCommandSetBlock(const BlockFixData &blockFixData, TokenReader &tokenReader, std::vector<DataFix> &dataFixList);

    bool expectCommandFill(const BlockFixData &blockFixData, TokenReader &tokenReader, std::vector<DataFix> &dataFixList);

    bool expectCommandTestForSetBlock(const BlockFixData &blockFixData, TokenReader &tokenReader, std::vector<DataFix> &dataFixList);

    bool expectCommand(const BlockFixData &blockFixData, TokenReader &tokenReader, std::vector<DataFix> &dataFixList);

    std::u16string old2new(const BlockFixData &blockFixData, std::u16string old);

    BlockFixData blockFixDataFromJson(const rapidjson::GenericDocument<rapidjson::UTF8<>> &j);

}// namespace CHelper::Old2New

#endif//CHELPER_OLD2NEW_H
