//
// Created by Yancey on 2023/11/5.
//

#include "CHelperResourceGenerator.h"
#include <chelper/parser/Parser.h>

int main() {
    // testDir();
    // testBin();
    // return 0;
    bool isSuccess = true;
    isSuccess = outputFile(CHelper::Test::writeSingleJson, "json") && isSuccess;
    isSuccess = outputFile(CHelper::Test::writeBinary, "cpack") && isSuccess;
    isSuccess = outputOld2New() && isSuccess;
    return isSuccess ? 0 : -1;
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

[[maybe_unused]] bool outputFile(
        bool function(const std::filesystem::path &input, const std::filesystem::path &output, const std::string &fileType),
        const std::string &fileType) {
    std::filesystem::path projectDir(RESOURCE_DIR);
    bool isSuccess = true;
    for (const auto &branchDir: std::filesystem::directory_iterator(projectDir / "resources" / "release")) {
        SPDLOG_INFO("----- start output {}: {}-{} -----", FORMAT_ARG(fileType), FORMAT_ARG("release"), FORMAT_ARG(branchDir.path().filename().string()));
        isSuccess = function(branchDir, projectDir / "generated" / fileType, fileType) && isSuccess;
    }
    for (const auto &branchDir: std::filesystem::directory_iterator(projectDir / "resources" / "beta")) {
        SPDLOG_INFO("----- start output {}: {}-{} -----", FORMAT_ARG(fileType), FORMAT_ARG("beta"), FORMAT_ARG(branchDir.path().filename().string()));
        isSuccess = function(branchDir, projectDir / "generated" / fileType, fileType) && isSuccess;
    }
    for (const auto &branchDir: std::filesystem::directory_iterator(projectDir / "resources" / "netease")) {
        SPDLOG_INFO("----- start output {}: {}-{} -----", FORMAT_ARG(fileType), FORMAT_ARG("netease"), FORMAT_ARG(branchDir.path().filename().string()));
        isSuccess = function(branchDir, projectDir / "generated" / fileType, fileType) && isSuccess;
    }
    return isSuccess;
}

bool outputOld2New() {
    // old2new
    std::filesystem::path resourceDir(RESOURCE_DIR);
    std::filesystem::path input = resourceDir / "resources" / "old2new" / "blockFixData.json";
    std::filesystem::path output = resourceDir / "generated" / "old2new" / "old2new.dat";
    CHelper::Old2New::BlockFixData blockFixData = CHelper::Old2New::blockFixDataFromJson(serialization::get_json_from_file(input));
    std::filesystem::create_directories(output.parent_path());
    std::ofstream ostream(output, std::ios::binary);
    serialization::Codec<decltype(blockFixData)>::to_binary<false>(ostream, blockFixData);
    ostream.close();
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
            Profile::printAndClear(e);
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
            Profile::printAndClear(e);
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
                core->onTextChanged(command, command.length());
                endParse = std::chrono::high_resolution_clock::now();
                startDescription = std::chrono::high_resolution_clock::now();
                auto description = core->getParamHint();
                endDescription = std::chrono::high_resolution_clock::now();
                startErrorReasons = std::chrono::high_resolution_clock::now();
                auto errorReasons = core->getErrorReasons();
                endErrorReasons = std::chrono::high_resolution_clock::now();
                startSuggestions = std::chrono::high_resolution_clock::now();
                auto suggestions = core->getSuggestions();
                endSuggestions = std::chrono::high_resolution_clock::now();
                startStructure = std::chrono::high_resolution_clock::now();
                auto structure = core->getStructure();
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
                        const auto& errorReason = errorReasons[i];
                        fmt::print("{}. {} {}\n{}{}{}\n",
                                   i,
                                   fmt::styled(utf8::utf16to8(command.substr(errorReason->start, errorReason->end - errorReason->start)), fg(fmt::color::red)),
                                   fmt::styled(utf8::utf16to8(errorReason->errorReason), fg(fmt::color::cornflower_blue)),
                                   utf8::utf16to8(command.substr(0, errorReason->start)),
                                   fmt::styled(errorReason->start == errorReason->end ? "~" : utf8::utf16to8(command.substr(errorReason->start, errorReason->end - errorReason->start)), fg(fmt::color::red)),
                                   utf8::utf16to8(command.substr((errorReason->end))));
                    }
                }
                if (suggestions->empty()) {
                    fmt::println("no suggestion");
                } else {
                    fmt::println("{} suggestions:", suggestions->size());
                    for (size_t i = 0; i < suggestions->size(); ++i) {
                        const auto &item = (*suggestions)[i];
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
                        std::u16string greenPart = item.content->name;
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
            Profile::printAndClear(e);
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
                    core->onTextChanged(command, command.length());
                    auto description = core->getParamHint();// NOLINT(*-unused-local-non-trivial-variable)
                    auto errorReasons = core->getErrorReasons();
                    core->getSuggestions();
                    auto structure = core->getStructure();// NOLINT(*-unused-local-non-trivial-variable)
                }
            }
            end = std::chrono::high_resolution_clock::now();
            SPDLOG_INFO("{} commands", FORMAT_ARG(commands.size()));
            SPDLOG_INFO("run successfully({})", FORMAT_ARG(std::chrono::duration_cast<std::chrono::milliseconds>(end - start)));
        } catch (const std::exception &e) {
            Profile::printAndClear(e);
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
            SPDLOG_INFO("CPack write successfully({})", FORMAT_ARG(std::chrono::duration_cast<std::chrono::milliseconds>(end - start)));
            core2 = CHelperCore::createByDirectory(realOutput);
        } catch (const std::exception &e) {
            Profile::printAndClear(e);
            exit(-1);
        }
        bool isSuccess = core2 != nullptr;
        delete core;
        delete core2;
        return isSuccess;
    }

    [[maybe_unused]] bool writeSingleJson(const std::filesystem::path &input, const std::filesystem::path &output, const std::string &fileType) {
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
            core->getCPack().writeJsonToFile(realOutput);
            end = std::chrono::high_resolution_clock::now();
            SPDLOG_INFO("CPack write successfully({})", FORMAT_ARG(std::chrono::duration_cast<std::chrono::milliseconds>(end - start)));
            core2 = CHelperCore::createByJson(realOutput);
        } catch (const std::exception &e) {
            Profile::printAndClear(e);
            exit(-1);
        }
        bool isSuccess = core2 != nullptr;
        delete core;
        delete core2;
        return isSuccess;
    }

    [[maybe_unused]] bool writeBinary(const std::filesystem::path &input, const std::filesystem::path &output, const std::string &fileType) {
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
            core->getCPack().writeBinToFile(realOutput);
            end = std::chrono::high_resolution_clock::now();
            SPDLOG_INFO("run successfully({})", FORMAT_ARG(std::chrono::duration_cast<std::chrono::milliseconds>(end - start)));
            core2 = CHelperCore::createByBinary(realOutput);
        } catch (const std::exception &e) {
            Profile::printAndClear(e);
            exit(-1);
        }
        bool isSuccess = core2 != nullptr;
        delete core;
        delete core2;
        return isSuccess;
    }

}// namespace CHelper::Test
