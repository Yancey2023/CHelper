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

#include <chelper/extension/MainPack.h>
#include <chelper/util/JsonUtil.h>
#include <fstream>
#include <iterator>
#include <stdexcept>

namespace CHelper::Extension {

    namespace {

        std::string normalizeRel(std::string rel) {
            for (char &ch: rel) {
                if (ch == '\\') {
                    ch = '/';
                }
            }
            while (!rel.empty() && rel.front() == '/') {
                rel.erase(rel.begin());
            }
            return rel;
        }

        // 去掉层前缀：如 "versions/beta/shared/command/a.json" → "command/a.json"
        std::string stripPrefix(std::string rel, const std::string &prefix) {
            if (rel.rfind(prefix, 0) == 0) {
                rel.erase(0, prefix.size());
            }
            return rel;
        }

        std::string readString(const rapidjson::GenericValue<rapidjson::UTF8<>> &v) {
            return v.IsString() ? v.GetString() : std::string();
        }

    }// namespace

    std::unique_ptr<MainPack> MainPack::open(MainPackSource source) {
        auto pack = std::unique_ptr<MainPack>(new MainPack());

        // 1) 收集文件字节
        if (source.kind == MainPackSource::Kind::Files) {
            pack->files_ = std::move(source.files);
        } else {
#ifndef CHELPER_NO_FILESYSTEM
            if (!std::filesystem::exists(source.directory)) {
                throw std::runtime_error("main pack directory not found");
            }
            std::function<void(const std::filesystem::path &, const std::string &)> walk =
                    [&pack, &walk](const std::filesystem::path &dir, const std::string &relPrefix) {
                        for (const auto &entry: std::filesystem::directory_iterator(dir)) {
                            if (entry.is_directory()) {
                                const std::string child = relPrefix.empty() ? entry.path().filename().string()
                                                                            : relPrefix + "/" + entry.path().filename().string();
                                walk(entry.path(), child);
                            } else if (entry.is_regular_file()) {
                                const std::string rel = relPrefix.empty() ? entry.path().filename().string()
                                                                          : relPrefix + "/" + entry.path().filename().string();
                                std::ifstream in(entry.path(), std::ios::binary);
                                auto bytes = std::make_shared<std::vector<uint8_t>>(
                                        (std::istreambuf_iterator<char>(in)), std::istreambuf_iterator<char>());
                                pack->files_.push_back({normalizeRel(rel), bytes});
                            }
                        }
                    };
            walk(source.directory, "");
#else
            throw std::runtime_error("Directory source requires filesystem support");
#endif
        }

        // 1.5) 统一归一化并剔除目录/空名条目：平台层 zip 解压可能把目录条目
        //      （zip 内以 '\' 或 '/' 结尾）当作文件传入，空路径会破坏分层索引与合成。
        std::vector<PackFile> cleaned;
        cleaned.reserve(pack->files_.size());
        for (auto &f: pack->files_) {
            std::string rel = normalizeRel(f.relPath);
            if (rel.empty() || rel.back() == '/') {
                continue;
            }
            cleaned.push_back({std::move(rel), std::move(f.bytes)});
        }
        pack->files_ = std::move(cleaned);

        // 2) 解析聚合 manifest（包根 manifest.json，仅用于元数据；不在分层索引中）
        for (const auto &f: pack->files_) {
            if (f.relPath != "manifest.json") {
                continue;
            }
            rapidjson::GenericDocument<rapidjson::UTF8<>> doc;
            if (f.bytes && JsonUtil::parseJsonWithComments(doc, reinterpret_cast<const char *>(f.bytes->data()), f.bytes->size()) && doc.IsObject()) {
                auto getStr = [&doc](const char *key) -> std::string {
                    auto it = doc.FindMember(key);
                    return it != doc.MemberEnd() ? readString(it->value) : std::string();
                };
                pack->packId_ = getStr("packId");
                pack->layout_ = getStr("layout");
                if (pack->layout_.empty()) {
                    pack->layout_ = "flat";
                }
                if (doc.HasMember("segments") && doc["segments"].IsObject()) {
                    for (auto vtIt = doc["segments"].MemberBegin(); vtIt != doc["segments"].MemberEnd(); ++vtIt) {
                        if (!vtIt->value.IsObject()) {
                            continue;
                        }
                        for (auto brIt = vtIt->value.MemberBegin(); brIt != vtIt->value.MemberEnd(); ++brIt) {
                            auto getStr = [](const auto &v, const char *key) -> std::string {
                                auto it = v.FindMember(key);
                                return it != v.MemberEnd() ? readString(it->value) : std::string();
                            };
                            SegmentMeta meta;
                            meta.id = std::string(vtIt->name.GetString()) + "/" + brIt->name.GetString();
                            meta.version = getStr(brIt->value, "version");
                            meta.packId = getStr(brIt->value, "packId");
                            meta.name = utf8::utf8to16(getStr(brIt->value, "name"));
                            pack->segments_.push_back(std::move(meta));
                        }
                    }
                }
            }
            break;
        }

        // 3) 建立分层索引
        pack->buildIndex();
        return pack;
    }

    const std::string &MainPack::packId() const {
        return packId_;
    }

    const std::string &MainPack::layout() const {
        return layout_;
    }

    const std::vector<MainPack::SegmentMeta> &MainPack::listSegments() const {
        return segments_;
    }

    void MainPack::indexFile(const std::string &relPath, size_t fileIndex) {
        static const std::string kShared = "shared/";
        static const std::string kVersions = "versions/";
        if (relPath.rfind(kShared, 0) == 0) {
            shared_[stripPrefix(relPath, kShared)] = fileIndex;
            return;
        }
        if (relPath.rfind(kVersions, 0) != 0) {
            return; // 忽略包根其它文件（如聚合 manifest.json）
        }
        // versions/<vt>/shared/<rel> 或 versions/<vt>/<branch>/<rel>
        const std::string rest = stripPrefix(relPath, kVersions);
        const size_t slash1 = rest.find('/');
        if (slash1 == std::string::npos) {
            return;
        }
        const std::string vt = rest.substr(0, slash1);
        const std::string rest2 = rest.substr(slash1 + 1);
        const size_t slash2 = rest2.find('/');
        if (slash2 == std::string::npos) {
            return;
        }
        const std::string layer = rest2.substr(0, slash2);
        const std::string inner = rest2.substr(slash2 + 1);
        if (layer == "shared") {
            vtShared_[vt][inner] = fileIndex;
        } else {
            branch_[vt + "/" + layer][inner] = fileIndex;
        }
    }

    void MainPack::buildIndex() {
        for (size_t i = 0; i < files_.size(); ++i) {
            indexFile(normalizeRel(files_[i].relPath), i);
        }
    }

    SegmentData MainPack::loadSegment(std::string_view versionType, std::string_view branch) const {
        const std::string vt(versionType);
        const std::string br(branch);
        const std::string segmentId = vt + "/" + br;

        std::unordered_map<std::string, size_t> view; // rel -> files_ 下标
        for (const auto &[rel, idx]: shared_) {
            view[rel] = idx;
        }
        if (auto it = vtShared_.find(vt); it != vtShared_.end()) {
            for (const auto &[rel, idx]: it->second) {
                view[rel] = idx;
            }
        }
        if (auto it = branch_.find(segmentId); it != branch_.end()) {
            for (const auto &[rel, idx]: it->second) {
                view[rel] = idx;
            }
        }
        if (view.empty()) {
            throw std::runtime_error("segment not found in main pack: " + segmentId);
        }

        SegmentData result;
        result.segmentId = segmentId;
        result.files.reserve(view.size());
        for (const auto &[rel, idx]: view) {
            result.files.push_back({rel, files_[idx].bytes});
        }
        // 段 manifest 必须存在（该段差异层内）
        if (!view.contains("manifest.json")) {
            throw std::runtime_error("segment manifest missing: " + segmentId);
        }
        return result;
    }

}// namespace CHelper::Extension
