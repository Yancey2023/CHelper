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

#include <chelper/extension/ComposerDetail.h>
#include <optional>
#include <stdexcept>
#include <unordered_set>
#include <vector>

namespace CHelper::Extension {

    // 通用小工具与装载器（parseDoc/getString/normalizeRel…）与分拆职责实现见
    // ComposerDetail.h / IdTableMerge.cpp（id 表） / SelectorData.cpp（selector/*.json）。
    using namespace detail;

    namespace {

        // 收集命令文件里的 name 别名
        std::vector<std::string> commandNamesOf(const JsonDoc &doc) {
            std::vector<std::string> names;
            auto it = doc.FindMember("name");
            if (it != doc.MemberEnd() && it->value.IsArray()) {
                for (const auto &v: it->value.GetArray()) {
                    if (v.IsString()) {
                        names.emplace_back(v.GetString());
                    }
                }
            }
            return names;
        }

        // execute 片段：把 branches 追加进对应 RepeatData 的 repeatNodes/isEnd
        void applyExecuteFragment(CPackBuilder &builder, const std::vector<uint8_t> &bytes, std::vector<std::string> &warnings) {
            (void) warnings;
            JsonDoc doc;
            if (!parseDoc(bytes, doc)) {
                throw std::runtime_error("invalid extension fragment");
            }
            auto nodeIt = doc.FindMember("node");
            if (nodeIt == doc.MemberEnd() || !nodeIt->value.IsObject()) {
                throw std::runtime_error("fragment missing node");
            }
            for (auto it = nodeIt->value.MemberBegin(); it != nodeIt->value.MemberEnd(); ++it) {
                const JsonValue &v = it->value;
                if (getString(v, "type") != "REPEAT" || !v.HasMember("branches")) {
                    continue;
                }
                const std::string key = getString(v, "key");
                Node::RepeatData *target = nullptr;
                for (auto &rd: builder.repeatNodeData) {
                    if (rd.id == key) {
                        target = &rd;
                        break;
                    }
                }
                if (target == nullptr) {
                    throw std::runtime_error("fragment target repeat not found: " + key);
                }
                const JsonValue &branches = v["branches"];
                if (!branches.IsArray()) {
                    throw std::runtime_error("fragment branches must be array");
                }
                const Node::NodeCreateStage::NodeCreateStage prev = currentCreateStage;
                currentCreateStage = Node::NodeCreateStage::COMMAND_PARAM_NODE;
                for (const auto &b: branches.GetArray()) {
                    Node::FreeableNodeWithTypes branchNodes;
                    serialization::Codec<decltype(branchNodes)>::template from_json<JsonValue>(b["nodes"], branchNodes);
                    const bool isEnd = b.HasMember("isEnd") && b["isEnd"].IsBool() ? b["isEnd"].GetBool() : true;
                    target->repeatNodes.push_back(std::move(branchNodes));
                    target->isEnd.push_back(isEnd);
                }
                currentCreateStage = prev;
            }
        }

        // text/translate.json：翻译键表（对象 {键: 中文}）→ normalIds["translate"]，供翻译键片段补全
        void loadTranslateTable(CPackBuilder &builder, const std::vector<uint8_t> &bytes) {
            JsonDoc doc;
            if (!parseDoc(bytes, doc) || !doc.IsObject()) {
                throw std::runtime_error("invalid text/translate.json");
            }
            auto table = std::make_shared<std::vector<std::shared_ptr<NormalId>>>();
            table->reserve(doc.MemberCount());
            for (auto it = doc.MemberBegin(); it != doc.MemberEnd(); ++it) {
                const std::string key(it->name.GetString(), it->name.GetStringLength());
                std::optional<std::u16string> zh;
                if (it->value.IsString()) {
                    zh = utf8::utf8to16(std::string(it->value.GetString(), it->value.GetStringLength()));
                }
                table->push_back(NormalId::make(utf8::utf8to16(key), zh));
            }
            builder.normalIds.emplace("translate", std::move(table));
        }

    }// namespace

    ComposeResult compose(const SegmentData &segment,
                          const std::vector<ExtensionPackData> &packs,
                          const ComposeOptions &options) {
        (void) options;
        ComposeResult result;

        CPackBuilder builder;
        // 1) 主包段（text/translate.json 特殊处理为翻译键表，其余按文件装载）
        for (const auto &f: segment.files) {
            if (!f.bytes) {
                continue;
            }
            const std::string rel = normalizeRel(f.relPath);
            if (rel == "text/translate.json") {
                loadTranslateTable(builder, *f.bytes);
            } else {
                builder.applyFile(rel, *f.bytes);
            }
        }

        // 已注册命令别名（主包段优先）
        std::unordered_set<std::string> commandNames;
        for (const auto &cmd: builder.commands) {
            for (const auto &name: cmd.name) {
                const std::string nameUtf8 = utf8::utf16to8(name);
                commandNames.insert(nameUtf8);
                result.commandSources[nameUtf8] = u""; // 空 = 内置
            }
        }

        // 2) 拓展包（按传入顺序，先到先得）
        std::vector<LoadedSelector> selectorFiles; // selector/*.json（同 id 后装载覆盖先装载）
        for (const auto &pack: packs) {
            // 解析拓展包 manifest 名称（用于来源标注）
            std::u16string packName;
            for (const auto &f: pack.files) {
                if (normalizeRel(f.relPath) == "manifest.json" && f.bytes) {
                    JsonDoc m;
                    if (parseDoc(*f.bytes, m)) {
                        packName = utf8::utf8to16(getString(m, "name"));
                    }
                    break;
                }
            }
            for (const auto &f: pack.files) {
                if (!f.bytes) {
                    continue;
                }
                const std::string rel = normalizeRel(f.relPath);
                if (rel == "manifest.json") {
                    continue; // 拓展包 manifest 不并入 CPack（来源元数据已读）
                }
                if (startsWithIgnoreCase(rel, "command/")) {
                    JsonDoc doc;
                    if (!parseDoc(*f.bytes, doc)) {
                        throw std::runtime_error("invalid command file: " + rel);
                    }
                    const auto names = commandNamesOf(doc);
                    bool conflict = false;
                    for (const auto &n: names) {
                        if (commandNames.contains(n)) {
                            conflict = true;
                            break;
                        }
                    }
                    if (conflict) {
                        result.overriddenCommands.insert(result.overriddenCommands.end(), names.begin(), names.end());
                        continue;
                    }
                    builder.applyFile(rel, *f.bytes);
                    for (const auto &n: names) {
                        commandNames.insert(n);
                        result.commandSources[n] = packName;
                        if (!packName.empty()) {
                            builder.commandNameSources[utf8::utf8to16(n)] = packName;
                        }
                    }
                } else if (startsWithIgnoreCase(rel, "id/")) {
                    appendIdFile(builder, *f.bytes, packName, result.warnings);
                } else if (startsWithIgnoreCase(rel, "json/")) {
                    builder.applyFile(rel, *f.bytes);
                } else if (startsWithIgnoreCase(rel, "extensions/")) {
                    applyExecuteFragment(builder, *f.bytes, result.warnings);
                } else if (startsWithIgnoreCase(rel, "selector/")) {
                    selectorFiles.push_back(parseSelectorFile(*f.bytes, rel, result.warnings, packName));
                }
                // 其它（text/ 等数据文件）忽略
            }
        }
        // selector/*.json 汇总（同 id 覆盖 + 与内置变量/参数的冲突校验在 flatten 内完成）
        flattenSelectors(builder, selectorFiles, result.warnings);

        result.cpack = builder.build();
        return result;
    }

}// namespace CHelper::Extension
