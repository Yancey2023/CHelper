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

#ifndef CHELPER_MAINPACK_H
#define CHELPER_MAINPACK_H

#include <chelper/resources/CPack.h>

namespace CHelper::Extension {

    // 一个包内文件：包内相对路径（`/` 分隔，如 "command/summon.json"）+ 内容（共享所有权）
    struct PackFile {
        std::string relPath;
        std::shared_ptr<const std::vector<uint8_t>> bytes;
    };

    // 启用段装载结果：该段完整数据（manifest.json + command/** + id/** + json/** + repeat/** + text/** …）
    struct SegmentData {
        std::string segmentId;          // "beta/vanilla"
        std::vector<PackFile> files;    // 已含该段 manifest.json

        [[nodiscard]] size_t totalBytes() const {
            size_t sum = 0;
            for (const auto &f: files) {
                sum += f.bytes ? f.bytes->size() : 0;
            }
            return sum;
        }
    };

    // 主包源：Files（平台层已解压 zip / 外部预展开）或 Directory（分层目录，开发/调试）。
    // 说明：core 不内置 zip 解压——安卓由 Kotlin 用 ZipInputStream 解包后以 Files 传入；
    //       桌面/测试用 Directory 直接读分层目录。
    struct MainPackSource {
        enum class Kind : uint8_t { Files, Directory };

        Kind kind = Kind::Files;
        std::vector<PackFile> files;
#ifndef CHELPER_NO_FILESYSTEM
        std::filesystem::path directory;
#endif
    };

    /**
     * 主包（内置补全包）只读装载器。打开一次、长期驻留；
     * loadSegment 按 `shared → versions/<vt>/shared → versions/<vt>/<branch>` 组装启用段完整视图。
     * 只读、无副作用，loadSegment 可并发调用。
     */
    class MainPack {
    public:
        struct SegmentMeta {
            std::string id;             // "beta/vanilla"
            std::string version;        // 段版本（如 "1.26.0.29"）
            std::string packId;
            std::u16string name;
        };

        static std::unique_ptr<MainPack> open(MainPackSource source);

        [[nodiscard]] const std::string &packId() const;

        [[nodiscard]] const std::string &layout() const;

        [[nodiscard]] const std::vector<SegmentMeta> &listSegments() const;

        // 组装启用段视图（分层合并，后层覆盖同名）；段不存在/损坏 → 抛 std::runtime_error
        SegmentData loadSegment(std::string_view versionType, std::string_view branch) const;

    private:
        MainPack() = default;

        std::string packId_;
        std::string layout_ = "flat";
        std::vector<SegmentMeta> segments_;

        // 常驻字节（分层文件 + 聚合 manifest），分层索引指向这里
        std::vector<PackFile> files_;
        // 各层：包内相对路径（不含层前缀）→ files_ 下标
        std::unordered_map<std::string, size_t> shared_;
        std::unordered_map<std::string, std::unordered_map<std::string, size_t>> vtShared_;
        std::unordered_map<std::string, std::unordered_map<std::string, size_t>> branch_;

        void indexFile(const std::string &relPath, size_t fileIndex);

        void buildIndex();
    };

}// namespace CHelper::Extension

#endif//CHELPER_MAINPACK_H
