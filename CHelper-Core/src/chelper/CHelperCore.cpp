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

    /**
     * 强制 odr-use：只取地址不调用，让编译器必须为 inline 函数产出实体。
     * volatile 防止优化器把"只取地址"判定为无用而删掉
     */
    template<class T>
    T *forceEmit(T *pointer) {
        static T *volatile emitted = nullptr;
        emitted = pointer;
        return pointer;
    }

#ifndef CHELPER_NO_FILESYSTEM
    /**
     * 强制生成 inline 序列化函数的符号。本身永远不会被调用，
     * 因此必须挂 CHELPER_USED：否则 Clang 在 -O3 下会直接丢掉整个函数体，
     * 连带着不再为这些函数生成符号。MSVC 会保留未使用的非 static 函数，
     * 所以桌面构建此前没有暴露这个问题
     */
    [[CHELPER_USED]]
    void emitSerializationSymbols(const CPack &cpack, const std::filesystem::path &path) {
        std::ignore = cpack.toJson();
        cpack.writeJsonToFile(path);
        cpack.writeJsonToDirectory(path);
        cpack.writeBinToFile(path);
        std::ignore = forceEmit(&Old2New::blockFixDataFromJson);
        std::ignore = forceEmit(&Old2New::blockFixDataToBinary);
        std::ignore = forceEmit(&Old2New::blockFixDataFromBinary);
    }
#else
    [[CHELPER_USED]]
    void emitSerializationSymbols() {
        std::ignore = forceEmit(&Old2New::blockFixDataToBinary);
        std::ignore = forceEmit(&Old2New::blockFixDataFromBinary);
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
