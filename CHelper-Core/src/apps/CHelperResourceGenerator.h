//
// Created by Yancey on 2023/11/6.
//

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
