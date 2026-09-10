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
#include <chelper/old2new/Old2New.h>
#include <chelper/serialization/Serialization.h>

namespace CHelper {

#ifndef CHELPER_NO_FILESYSTEM
    // 下游只 include CHelperCore.h，而序列化函数以 inline 形式定义在 Serialization.h；
    // 编译器不会为未被本翻译单元使用的 inline 函数生成符号，链接时会出现未解析符号。
    // 外部链接的函数一定会被生成，其函数体对这些函数的调用会强制编译器
    // 在本目标文件中同时生成它们的符号。此函数本身永远不会被调用
    void emitSerializationSymbols(const CPack &cpack, const std::filesystem::path &path) {
        std::ignore = cpack.toJson();
        cpack.writeJsonToFile(path);
        cpack.writeJsonToDirectory(path);
        cpack.writeBinToFile(path);
        std::ignore = Old2New::blockFixDataFromJson(path);
        std::ignore = Old2New::blockFixDataToBinary(Old2New::blockFixDataFromBinary({}));
    }
#else
    void emitSerializationSymbols() {
        std::ignore = Old2New::blockFixDataToBinary(Old2New::blockFixDataFromBinary({}));
    }
#endif

    CHelperCore::CHelperCore(std::shared_ptr<const CPack> cpack)
        : cpack(std::move(cpack)) {}

    CHelperCore *CHelperCore::create(const std::function<std::unique_ptr<CPack>()> &getCPack) {
        try {
#if SPDLOG_ACTIVE_LEVEL <= SPDLOG_LEVEL_INFO
            const auto start = std::chrono::high_resolution_clock::now();
#endif
            std::unique_ptr<CPack> cPack = getCPack();
#if SPDLOG_ACTIVE_LEVEL <= SPDLOG_LEVEL_INFO
            const auto end = std::chrono::high_resolution_clock::now();
#endif
            //provider返回nullptr说明资源包加载失败，不允许产生内部cpack为nullptr的CHelperCore，
            //否则后续getCPack()/createContext()会解引用空指针
            if (cPack == nullptr) [[unlikely]] {
                Profile::push("getCPack returned nullptr");
                throw std::runtime_error("getCPack returned nullptr");
            }
            SPDLOG_INFO("CPack load successfully ({})", FORMAT_ARG(std::chrono::duration_cast<std::chrono::milliseconds>(end - start)));
            return new CHelperCore(std::move(cPack));
        } catch (const std::exception &e) {
            SPDLOG_ERROR("CPack load failed");
            Profile::printAndClear(e);
            return nullptr;
        }
    }

#ifndef CHELPER_NO_FILESYSTEM
    CHelperCore *CHelperCore::createByDirectory(const std::filesystem::path &cpackPath) {
        return create([&cpackPath]() {
            return serialization::createCPackByDirectory(cpackPath);
        });
    }

    CHelperCore *CHelperCore::createByJson(const std::filesystem::path &cpackPath) {
        return create([&cpackPath]() {
            return serialization::createCPackByJsonFile(cpackPath);
        });
    }

    CHelperCore *CHelperCore::createByBinary(const std::filesystem::path &cpackPath) {
        return create([&cpackPath]() {
            // 检查文件名后缀
            std::string fileType = ".cpack";
            std::string cpackPathStr = cpackPath.string();
            if (cpackPathStr.size() < fileType.size() || cpackPathStr.substr(cpackPathStr.length() - fileType.size()) != fileType) [[unlikely]] {
                Profile::push("error file type -> {}", FORMAT_ARG(cpackPathStr));
                throw std::runtime_error("error file type");
            }
            // 读取文件
            std::string buffer = readFileToString(cpackPath);
            return serialization::createCPackByBinary(buffer);
        });
    }
#endif

    CHelperCore *CHelperCore::createByBinary(std::string_view data) {
        return create([&data]() {
            return serialization::createCPackByBinary(data);
        });
    }

    const CPack &CHelperCore::getCPack() const {
        return *cpack;
    }

    CommandContext *CHelperCore::createContext(std::u16string command) const {
        return new CommandContext(cpack, std::move(command));
    }

    void CHelperCore::deleteContext(CommandContext *context) {
        delete context;
    }

    std::u16string CHelperCore::old2new(const Old2New::BlockFixData &blockFixData, std::u16string old) {
        return Old2New::old2new(blockFixData, std::move(old));
    }
}// namespace CHelper
