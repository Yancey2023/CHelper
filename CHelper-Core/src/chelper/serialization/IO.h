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

#include <chelper/serialization/BinaryFormat.h>

#include <fstream>

// ================= 通用 I/O 辅助函数（所有文件通过 pch.h 可用） =================
// 各格式（JSON / MessagePack / 自定义二进制）的读写入口与文件读取；
// 失败时抛出带定位信息的 std::runtime_error
namespace CHelper {

#ifndef CHELPER_NO_FILESYSTEM
    // 读取整个文件（资源 JSON 加载用）
    inline std::string readFileToString(const std::filesystem::path &path) {
        std::ifstream is(path, std::ios::binary);
        if (!is.is_open()) [[unlikely]] {
            throw std::runtime_error("fail to open file: " + path.string());
        }
        is.seekg(0, std::ios::end);
        const auto fileSize = is.tellg();
        if (fileSize == std::streampos(-1)) [[unlikely]] {
            throw std::runtime_error("fail to get file size: " + path.string());
        }
        std::string content(static_cast<size_t>(fileSize), '\0');
        is.seekg(0, std::ios::beg);
        if (!is) [[unlikely]] {
            throw std::runtime_error("fail to seek file: " + path.string());
        }
        if (!content.empty() && !is.read(content.data(), static_cast<std::streamsize>(content.size()))) [[unlikely]] {
            throw std::runtime_error("fail to read file: " + path.string());
        }
        return content;
    }
#endif

    // JSON 读取（失败时抛出带定位信息的异常）
    // ctx 允许调用方携带自定义反序列化上下文（如 CPack 加载阶段），不传则使用默认上下文
    template<class T, class Ctx>
        requires glz::is_context<Ctx>
    void readJson(T &value, const std::string_view buffer, Ctx &ctx) {
        const auto ec = glz::read<glz::opts{}>(value, buffer, ctx);
        if (bool(ec)) [[unlikely]] {
            throw std::runtime_error("fail to parse json: " + glz::format_error(ec, buffer));
        }
    }

    template<class T>
    void readJson(T &value, const std::string_view buffer) {
        glz::context ctx{};
        readJson(value, buffer, ctx);
    }

#ifndef CHELPER_NO_FILESYSTEM
    template<class T, class Ctx>
        requires glz::is_context<Ctx>
    void readJsonFromFile(T &value, const std::filesystem::path &path, Ctx &ctx) {
        std::string buffer = readFileToString(path);
        readJson(value, buffer, ctx);
    }

    template<class T>
    void readJsonFromFile(T &value, const std::filesystem::path &path) {
        glz::context ctx{};
        readJsonFromFile(value, path, ctx);
    }
#endif

    // JSON 写出（紧凑格式）
    template<class T>
    [[nodiscard]] std::string writeJson(const T &value) {
        std::string buffer;
        const auto ec = glz::write_json(value, buffer);
        if (bool(ec)) [[unlikely]] {
            throw std::runtime_error("fail to write json");
        }
        return buffer;
    }

    // MessagePack 读写
    template<class T>
    void writeMsgpack(std::string &buffer, const T &value) {
        const auto ec = glz::write_msgpack(value, buffer);
        if (bool(ec)) [[unlikely]] {
            throw std::runtime_error("fail to write msgpack");
        }
    }

    template<class T>
    void readMsgpack(T &value, const std::string_view buffer) {
        const auto ec = glz::read_msgpack(value, buffer);
        if (bool(ec)) [[unlikely]] {
            throw std::runtime_error("fail to parse msgpack: " + glz::format_error(ec, buffer));
        }
    }

    // 自定义二进制格式（.cpack / old2new.dat）
    template<class T>
    void writeBinary(std::string &buffer, const T &value) {
        glz::context ctx{};
        const auto ec = glz::write<glz::opts{.format = CHelper::BinaryFormat}>(value, buffer, ctx);
        if (bool(ec)) [[unlikely]] {
            throw std::runtime_error("fail to write binary");
        }
    }

    template<class T, class Ctx>
        requires glz::is_context<Ctx>
    void readBinary(T &value, const std::string_view buffer, Ctx &ctx) {
        const auto ec = glz::read<glz::opts{.format = CHelper::BinaryFormat}>(value, buffer, ctx);
        if (bool(ec)) [[unlikely]] {
            throw std::runtime_error("fail to parse binary: " + glz::format_error(ec, buffer));
        }
    }

    template<class T>
    void readBinary(T &value, const std::string_view buffer) {
        glz::context ctx{};
        readBinary(value, buffer, ctx);
    }
}// namespace CHelper
