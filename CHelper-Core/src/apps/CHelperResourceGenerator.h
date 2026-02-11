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

#pragma once

#ifndef CHELPER_CHELPERCMD_H
#define CHELPER_CHELPERCMD_H

#include <chelper/CHelperCore.h>
#include <pch.h>

int main();

#if CHelperOnlyReadBinary != true

[[maybe_unused]] void testDir();

[[maybe_unused]] void testBin();

[[maybe_unused]] bool outputFile(
        bool function(const std::filesystem::path &input, const std::filesystem::path &output, const std::string &fileType),
        const std::string &fileType);

[[maybe_unused]] bool outputOld2New();

namespace CHelper::Test {

    [[maybe_unused]] void testDir(const std::filesystem::path &cpackPath, const std::filesystem::path &testFilePath, bool isTestTime);

    [[maybe_unused]] void testBin(const std::filesystem::path &cpackPath, const std::filesystem::path &testFilePath, bool isTestTime);

    [[maybe_unused]] void testDir(const std::filesystem::path &cpackPath, const std::vector<std::u16string> &commands, bool isTestTime);

    [[maybe_unused]] void testBin(const std::filesystem::path &cpackPath, const std::vector<std::u16string> &commands, bool isTestTime);

    [[maybe_unused]] void test(CHelperCore *core, const std::vector<std::u16string> &commands, bool isTestTime);

    [[maybe_unused]] void test2(const std::filesystem::path &cpackPath, const std::vector<std::u16string> &commands, size_t times);

    [[maybe_unused]] bool writeDirectory(const std::filesystem::path &input, const std::filesystem::path &output, const std::string &fileType);

    [[maybe_unused]] bool writeSingleJson(const std::filesystem::path &input, const std::filesystem::path &output, const std::string &fileType);

    [[maybe_unused]] bool writeBinary(const std::filesystem::path &input, const std::filesystem::path &output, const std::string &fileType);

}// namespace CHelper::Test

#endif

#endif//CHELPER_CHELPERCMD_H
