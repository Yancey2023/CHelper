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

#include "CHelperResourceGenerator.h"
#include <algorithm>
#include <chelper/parser/Parser.h>
#include <chelper/util/Logging.h>
#include <chrono>
#include <cstddef>
#include <exception>
#include <filesystem>
#include <fmt/chrono.h>
#include <fmt/color.h>
#include <fstream>
#include <ios>
#include <memory>
#include <regex>
#include <stdexcept>
#include <string>
#include <utf8.h>
#include <vector>

namespace {

    using PackWriter = std::filesystem::path (*)(const std::filesystem::path &, const std::filesystem::path &);

    std::vector<std::filesystem::path> outputPacks(
            PackWriter write, const std::filesystem::path &resourcesRoot, const std::filesystem::path &output) {
        std::vector<std::filesystem::path> filenames;
        for (const auto *edition: {"release", "beta", "netease"}) {
            for (const auto *branch: {"vanilla", "experiment"}) {
                SPDLOG_INFO("----- start output {}: {}-{} -----", FORMAT_ARG(output.filename().string()), FORMAT_ARG(edition), FORMAT_ARG(branch));
                const auto file = write(resourcesRoot / edition / branch, output);
                if (!file.filename().string().starts_with(fmt::format("{}-{}-", edition, branch))) {
                    throw std::runtime_error(fmt::format("invalid output for resource pack {}-{}", edition, branch));
                }
                filenames.push_back(file.filename());
            }
        }
        return filenames;
    }

    void removeStalePacks(const std::filesystem::path &output, const std::string &extension,
                          const std::vector<std::filesystem::path> &filenames) {
        const std::regex packStem(R"((release|beta|netease)-(vanilla|experiment)-[0-9]+(\.[0-9]+)+)");
        for (const auto &entry: std::filesystem::directory_iterator(output)) {
            const auto &path = entry.path();
            if (path.extension() == extension && entry.is_regular_file() &&
                std::regex_match(path.stem().string(), packStem) &&
                std::ranges::find(filenames, path.filename()) == filenames.end()) {
                std::filesystem::remove(path);
            }
        }
    }

    std::filesystem::path packPath(const CHelper::Manifest &manifest, const std::filesystem::path &output, const char *extension) {
        return output / fmt::format(
                                "{}-{}-{}.{}",
                                manifest.versionType.has_value() ? utf8::utf16to8(manifest.versionType.value()) : "",
                                manifest.branch.has_value() ? utf8::utf16to8(manifest.branch.value()) : "",
                                manifest.version.has_value() ? utf8::utf16to8(manifest.version.value()) : "",
                                extension);
    }

}// namespace

int main(int argc, char **argv) {
    // testDir();
    // testBin();
    // return 0;
    if (argc > 3) {
        SPDLOG_ERROR("Usage: CHelperResourceGenerator [resources-root] [output-root]");
        return 1;
    }
    const std::filesystem::path resourcesRoot = argc >= 2 ? std::filesystem::path(argv[1]) : std::filesystem::path(RESOURCE_DIR) / "resources";
    const std::filesystem::path outputRoot = argc >= 3 ? std::filesystem::path(argv[2]) : std::filesystem::path(RESOURCE_DIR) / "generated";
    try {
        const auto jsonPacks = outputPacks(CHelper::Test::writeSingleJson, resourcesRoot, outputRoot / "json");
        const auto binaryPacks = outputPacks(CHelper::Test::writeBinary, resourcesRoot, outputRoot / "cpack");
        if (jsonPacks.empty() || binaryPacks.empty() || !outputOld2New(resourcesRoot, outputRoot)) return 1;
        removeStalePacks(outputRoot / "json", ".json", jsonPacks);
        removeStalePacks(outputRoot / "cpack", ".cpack", binaryPacks);
        return 0;
    } catch (const std::exception &error) {
        SPDLOG_ERROR("{}", error.what());
        return 1;
    }
}

[[maybe_unused]] void testDir() {
    std::filesystem::path resourceDir(RESOURCE_DIR);
    CHelper::Test::testDir(resourceDir / "resources" / "beta" / "vanilla",
                           resourceDir / "test" / "test.txt",
                           true);
    // CHelper::Test::testDir(resourceDir / "resources" / "beta" / "vanilla",
    //                        std::vector<std::u16string>{u"execute run clear "}, false);
}

[[maybe_unused]] void testBin() {
    std::filesystem::path resourceDir(RESOURCE_DIR);
    for (const auto &cpackPath: std::filesystem::directory_iterator(resourceDir / "generated" / "cpack")) {
        std::string fileName = cpackPath.path().filename().string();
        if (fileName.find("beta-experiment-") != std::string::npos) {
            CHelper::Test::testBin(cpackPath, resourceDir / "test" / "test.txt", true);
            break;
        }
    }
}

bool outputOld2New(const std::filesystem::path &resourcesRoot, const std::filesystem::path &outputRoot) {
    // old2new
    std::filesystem::path input = resourcesRoot / "old2new" / "blockFixData.json";
    std::filesystem::path output = outputRoot / "old2new" / "old2new.dat";
    CHelper::Old2New::BlockFixData blockFixData = CHelper::Old2New::blockFixDataFromJson(input);
    std::filesystem::create_directories(output.parent_path());
    const std::string buffer = CHelper::Old2New::blockFixDataToBinary(blockFixData);
    std::ofstream ostream(output, std::ios::binary);
    ostream.write(buffer.data(), static_cast<std::streamsize>(buffer.size()));
    ostream.close();
    if (!ostream) {
        SPDLOG_ERROR("Failed to write old2new resource: {}", FORMAT_ARG(output.string()));
        return false;
    }
    return true;
}

namespace CHelper::Test {

    /**
     * 读取测试文件进行测试
     */
    [[maybe_unused]] void testDir(const std::filesystem::path &cpackPath, const std::filesystem::path &testFilePath, bool isTestTime) {
        std::vector<std::u16string> commands;
        std::ifstream fin;
        fin.open(testFilePath, std::ios::in);
        while (fin.is_open()) {
            std::string str;
            getline(fin, str);
            if (str.empty()) [[unlikely]] {
                break;
            }
            if (str[0] == '-') [[likely]] {
                continue;
            }
            if (str[str.length() - 1] == '\r') [[unlikely]] {
                str = str.substr(0, str.length() - 1);
            }
            commands.push_back(utf8::utf8to16(str));
        }
        fin.close();
        CHelper::Test::testDir(cpackPath, commands, isTestTime);
        // std::vector<std::u16string> commands1;
        // for (const auto &item: commands) {
        //     for (size_t i = 0; i < item.size(); i++) {
        //         commands1.push_back(item.substr(0, i + 1));
        //     }
        // }
        // CHelper::Test::test2(cpackPath, commands1, 10);
    }

    /**
     * 读取测试文件进行测试
     */
    [[maybe_unused]] void testBin(const std::filesystem::path &cpackPath, const std::filesystem::path &testFilePath, bool isTestTime) {
        std::vector<std::u16string> commands;
        std::ifstream fin;
        fin.open(testFilePath, std::ios::in);
        while (fin.is_open()) {
            std::string str;
            getline(fin, str);
            if (str.empty()) [[unlikely]] {
                break;
            }
            if (str[0] == '-') [[likely]] {
                continue;
            }
            if (str[str.length() - 1] == '\r') [[unlikely]] {
                str = str.substr(0, str.length() - 1);
            }
            commands.push_back(utf8::utf8to16(str));
        }
        fin.close();
        CHelper::Test::testBin(cpackPath, commands, isTestTime);
    }

    /**
     * 测试程序是否可以正常运行
     */
    [[maybe_unused]] void
    testDir(const std::filesystem::path &cpackPath, const std::vector<std::u16string> &commands, bool isTestTime) {
        CHelperCore *core = nullptr;
        try {
            core = CHelperCore::createByDirectory(cpackPath);
            fmt::print("\n");
            if (core == nullptr) [[unlikely]] {
                return;
            }
        } catch (const std::exception &e) {
            SPDLOG_ERROR("{}", e.what());
        }
        test(core, commands, isTestTime);
        delete core;
    }

    /**
     * 测试程序是否可以正常运行
     */
    [[maybe_unused]] void
    testBin(const std::filesystem::path &cpackPath, const std::vector<std::u16string> &commands, bool isTestTime) {
        CHelperCore *core = nullptr;
        try {
            core = CHelperCore::createByBinary(cpackPath);
            fmt::print("\n");
            if (core == nullptr) [[unlikely]] {
                return;
            }
        } catch (const std::exception &e) {
            SPDLOG_ERROR("{}", e.what());
        }
        test(core, commands, isTestTime);
        delete core;
    }

    /**
     * 测试程序是否可以正常运行
     */
    [[maybe_unused]] void
    test(CHelperCore *core, const std::vector<std::u16string> &commands, bool isTestTime) {
        try {
            if (core == nullptr) [[unlikely]] {
                return;
            }
            for (const auto &command: commands) {
                std::chrono::high_resolution_clock::time_point startParse, endParse,
                        startDescription, endDescription,
                        startErrorReasons, endErrorReasons,
                        startSuggestions, endSuggestions,
                        startStructure, endStructure;
                startParse = std::chrono::high_resolution_clock::now();
                std::unique_ptr<CommandContext> context(core->createContext(command));
                endParse = std::chrono::high_resolution_clock::now();
                startDescription = std::chrono::high_resolution_clock::now();
                auto description = context->getParamHint(command.length());
                endDescription = std::chrono::high_resolution_clock::now();
                startErrorReasons = std::chrono::high_resolution_clock::now();
                auto errorReasons = context->getErrorReasons();
                endErrorReasons = std::chrono::high_resolution_clock::now();
                startSuggestions = std::chrono::high_resolution_clock::now();
                auto suggestions = context->getSuggestions(command.length());
                endSuggestions = std::chrono::high_resolution_clock::now();
                startStructure = std::chrono::high_resolution_clock::now();
                auto structure = context->getStructure();
                endStructure = std::chrono::high_resolution_clock::now();
                fmt::println("parse: {}", FORMAT_ARG(utf8::utf16to8(command)));
                if (isTestTime) {
                    fmt::println("parse in {}", FORMAT_ARG(std::chrono::duration_cast<std::chrono::milliseconds>(endParse - startParse)));
                    fmt::println("get description in {}", FORMAT_ARG(std::chrono::duration_cast<std::chrono::milliseconds>(endDescription - startDescription)));
                    fmt::println("get error in {}", FORMAT_ARG(std::chrono::duration_cast<std::chrono::milliseconds>(endErrorReasons - startErrorReasons)));
                    fmt::println("get suggestions in {}", FORMAT_ARG(std::chrono::duration_cast<std::chrono::milliseconds>(endSuggestions - startSuggestions)));
                    fmt::println("get structure in {}", FORMAT_ARG(std::chrono::duration_cast<std::chrono::milliseconds>(endStructure - startStructure)));
                }
                fmt::println("structure: {}", utf8::utf16to8(structure));
                fmt::println("description: {}", utf8::utf16to8(description));
                if (errorReasons.empty()) {
                    fmt::println("no error");
                } else {
                    fmt::println("error reasons:");
                    for (size_t i = 0; i < errorReasons.size(); ++i) {
                        const auto &errorReason = errorReasons[i];
                        fmt::print("{}. {} {}\n{}{}{}\n",
                                   i,
                                   fmt::styled(utf8::utf16to8(command.substr(errorReason->start, errorReason->end - errorReason->start)), fg(fmt::color::red)),
                                   fmt::styled(utf8::utf16to8(errorReason->getMessage()), fg(fmt::color::cornflower_blue)),
                                   utf8::utf16to8(command.substr(0, errorReason->start)),
                                   fmt::styled(errorReason->start == errorReason->end ? "~" : utf8::utf16to8(command.substr(errorReason->start, errorReason->end - errorReason->start)), fg(fmt::color::red)),
                                   utf8::utf16to8(command.substr((errorReason->end))));
                    }
                }
                if (suggestions.empty()) {
                    fmt::println("no suggestion");
                } else {
                    fmt::println("{} suggestions:", suggestions.size());
                    for (size_t i = 0; i < suggestions.size(); ++i) {
                        const auto &item = suggestions[i];
                        if (i == 30) {
                            fmt::println("...");
                            break;
                        }
                        fmt::println("{}. {} {}",
                                     i,
                                     fmt::styled(utf8::utf16to8(item.content->name), fg(fmt::color::lime_green)),
                                     fmt::styled(utf8::utf16to8(item.content->description.value_or(u"")), fg(fmt::color::cornflower_blue)));
                        std::u16string result = command.substr(0, item.start)
                                                        .append(item.content->name)
                                                        .append(command.substr(item.end));
                        std::u16string greenPart(item.content->name.begin(), item.content->name.end());
                        if (item.end == command.length()) {
                            ASTNode astNode = Parser::parse(result, core->getCPack());
                            if (item.isAddSpace && astNode.isAllSpaceError()) {
                                greenPart.push_back(u' ');
                            }
                        }
                        fmt::println("{}{}{}",
                                     utf8::utf16to8(command.substr(0, item.start)),
                                     fmt::styled(utf8::utf16to8(greenPart), fg(fmt::color::lime_green)),
                                     utf8::utf16to8(command.substr(item.end)));
                    }
                }
                fmt::print("\n");
            }
        } catch (const std::exception &e) {
            SPDLOG_ERROR("{}", e.what());
        }
    }

    /**
     * 测试程序性能
     */
    [[maybe_unused]] void test2(const std::filesystem::path &cpackPath, const std::vector<std::u16string> &commands, size_t times) {
        CHelperCore *core = nullptr;
        try {
            core = CHelperCore::createByDirectory(cpackPath);
            fmt::print("\n");
            if (core == nullptr) [[unlikely]] {
                return;
            }
            std::chrono::high_resolution_clock::time_point start, end;
            start = std::chrono::high_resolution_clock::now();
            for (size_t i = 0; i < times; ++i) {
                for (const auto &command: commands) {
                    std::unique_ptr<CommandContext> context(core->createContext(command));
                    auto description = context->getParamHint(command.length());// NOLINT(*-unused-local-non-trivial-variable)
                    auto errorReasons = context->getErrorReasons();
                    (void) context->getSuggestions(command.length());
                    auto structure = context->getStructure();// NOLINT(*-unused-local-non-trivial-variable)
                }
            }
            end = std::chrono::high_resolution_clock::now();
            SPDLOG_INFO("{} commands", FORMAT_ARG(commands.size()));
            SPDLOG_INFO("run successfully ({})", FORMAT_ARG(std::chrono::duration_cast<std::chrono::milliseconds>(end - start)));
        } catch (const std::exception &e) {
            SPDLOG_ERROR("{}", e.what());
            exit(-1);
        }
        delete core;
    }

    [[maybe_unused]] bool writeDirectory(const std::u16string &input, const std::filesystem::path &output, const std::string &fileType) {
        CHelperCore *core = nullptr;
        CHelperCore *core2 = nullptr;
        try {
            core = CHelperCore::createByDirectory(input);
            if (core == nullptr) [[unlikely]] {
                return false;
            }
            const Manifest &manifest = core->getCPack().manifest;

            std::string filename = fmt::format(
                    "{}-{}-{}.{}",
                    manifest.versionType.has_value() ? utf8::utf16to8(manifest.versionType.value()) : "",
                    manifest.branch.has_value() ? utf8::utf16to8(manifest.branch.value()) : "",
                    manifest.version.has_value() ? utf8::utf16to8(manifest.version.value()) : "",
                    fileType);
            std::filesystem::path realOutput = output / filename;
            std::chrono::high_resolution_clock::time_point start, end;
            start = std::chrono::high_resolution_clock::now();
            core->getCPack().writeJsonToDirectory(realOutput);
            end = std::chrono::high_resolution_clock::now();
            SPDLOG_INFO("CPack write successfully ({})", FORMAT_ARG(std::chrono::duration_cast<std::chrono::milliseconds>(end - start)));
            core2 = CHelperCore::createByDirectory(realOutput);
        } catch (const std::exception &e) {
            SPDLOG_ERROR("{}", e.what());
            exit(-1);
        }
        bool isSuccess = core2 != nullptr;
        delete core;
        delete core2;
        return isSuccess;
    }

    std::filesystem::path writeSingleJson(const std::filesystem::path &input, const std::filesystem::path &output) {
        const std::unique_ptr<CHelperCore> core(CHelperCore::createByDirectory(input));
        if (!core) return {};
        const auto path = packPath(core->getCPack().manifest, output, "json");
        const auto start = std::chrono::high_resolution_clock::now();
        core->getCPack().writeJsonToFile(path);
        const auto end = std::chrono::high_resolution_clock::now();
        SPDLOG_INFO("CPack write successfully ({})", FORMAT_ARG(std::chrono::duration_cast<std::chrono::milliseconds>(end - start)));
        const std::unique_ptr<CHelperCore> written(CHelperCore::createByJson(path));
        return written ? path : std::filesystem::path{};
    }

    std::filesystem::path writeBinary(const std::filesystem::path &input, const std::filesystem::path &output) {
        const std::unique_ptr<CHelperCore> core(CHelperCore::createByDirectory(input));
        if (!core) return {};
        const auto path = packPath(core->getCPack().manifest, output, "cpack");
        const auto start = std::chrono::high_resolution_clock::now();
        core->getCPack().writeBinToFile(path);
        const auto end = std::chrono::high_resolution_clock::now();
        SPDLOG_INFO("run successfully ({})", FORMAT_ARG(std::chrono::duration_cast<std::chrono::milliseconds>(end - start)));
        const std::unique_ptr<CHelperCore> written(CHelperCore::createByBinary(path));
        return written ? path : std::filesystem::path{};
    }

}// namespace CHelper::Test
