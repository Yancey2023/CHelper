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
#include <algorithm>
#include <memory>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <vector>

namespace CHelper::Extension::detail {

    namespace {

        // selector/*.json V1 支持的值类型（NORMAL_ID/NAMESPACE_ID 需 key 引用表）
        constexpr std::u16string_view supportedSelectorValueTypes[] = {
                u"BOOLEAN", u"INTEGER", u"FLOAT", u"RELATIVE_FLOAT", u"STRING", u"RANGE",
                u"NORMAL_ID", u"NAMESPACE_ID"};

    }// namespace

    // 解析单个 selector/*.json 并做结构校验（内容不合法 → 整包拒绝）
    LoadedSelector parseSelectorFile(const std::vector<uint8_t> &bytes, const std::string &rel,
                                     std::vector<std::string> &warnings,
                                     const std::u16string &packName) {
        JsonDoc doc;
        if (!parseDoc(bytes, doc)) {
            throw std::runtime_error("invalid selector file: " + rel);
        }
        LoadedSelector out;
        const std::string id = getString(doc, "id");
        if (id.empty()) {
            throw std::runtime_error("selector file missing id: " + rel);
        }
        out.id = id;

        auto it = doc.FindMember("variables");
        if (it != doc.MemberEnd() && it->value.IsArray()) {
            for (const auto &v: it->value.GetArray()) {
                if (!v.IsObject()) {
                    throw std::runtime_error("invalid selector variable in " + rel);
                }
                const std::string name = getString(v, "name");
                if (name.empty() || name[0] != '@') {
                    throw std::runtime_error("selector variable name must start with '@': " + rel);
                }
                Node::SelectorPackVariable variable;
                variable.name = utf8::utf8to16(name);
                variable.packName = packName; // 来源徽标（同包新增变量）
                const std::string description = getString(v, "description");
                if (!description.empty()) {
                    variable.description = utf8::utf8to16(description);
                }
                // 白名单参数目前不生效（V1 子集：自定义变量与内置变量共用全局参数表），仅告警
                auto argIt = v.FindMember("arguments");
                if (argIt != v.MemberEnd() && argIt->value.IsArray() && !argIt->value.GetArray().Empty()) {
                    warnings.emplace_back("selector " + rel + " 变量 " + name + " 的参数白名单暂不生效（V1 子集），已忽略");
                }
                out.variables.push_back(std::move(variable));
            }
        }

        auto argDefs = doc.FindMember("arguments");
        if (argDefs != doc.MemberEnd() && argDefs->value.IsArray()) {
            for (const auto &a: argDefs->value.GetArray()) {
                if (!a.IsObject()) {
                    throw std::runtime_error("invalid selector argument in " + rel);
                }
                Node::SelectorPackArgument argument;
                const std::string name = getString(a, "name");
                if (name.empty()) {
                    throw std::runtime_error("selector argument missing name in " + rel);
                }
                argument.name = utf8::utf8to16(name);
                const std::string description = getString(a, "description");
                if (description.empty()) {
                    throw std::runtime_error("selector argument missing description in " + rel + " (" + name + ")");
                }
                argument.description = utf8::utf8to16(description);
                const std::string op = getString(a, "operator");
                argument.canUseNotEqual = op.find('!') != std::string::npos;
                argument.packName = packName; // 来源徽标（同包新增参数）
                const std::string vt = getString(a, "valueType");
                if (vt.empty()) {
                    throw std::runtime_error("selector argument missing valueType in " + rel + " (" + name + ")");
                }
                bool knownType = false;
                for (const auto &t: supportedSelectorValueTypes) {
                    if (utf8::utf16to8(std::u16string(t)) == vt) {
                        knownType = true;
                        break;
                    }
                }
                if (!knownType) {
                    throw std::runtime_error("selector argument unsupported valueType '" + vt + "' in " + rel + " (" + name + ")");
                }
                argument.valueType = vt;
                if (vt == "NORMAL_ID" || vt == "NAMESPACE_ID") {
                    const std::string key = getString(a, "key");
                    if (key.empty()) {
                        throw std::runtime_error("selector argument missing key in " + rel + " (" + name + ")");
                    }
                    argument.key = key;
                    if (a.HasMember("contents")) {
                        warnings.emplace_back(
                                "selector " + rel + " 参数 " + name + " 的 contents 暂不支持（V1 子集仅支持 key），已忽略");
                    }
                }
                out.arguments.push_back(std::move(argument));
            }
        }
        return out;
    }

    // 汇总多个 selector 文件到 builder：同 id 后文件覆盖前文件；变量/参数名冲突先到先得
    void flattenSelectors(CPackBuilder &builder, std::vector<LoadedSelector> &files,
                          std::vector<std::string> &warnings) {
        // 内置名单（单一来源：引擎导出；保留变量整包拒绝、内置参数先到先得忽略）
        const auto reservedVariables = Node::TargetSelectorData::builtinVariableNames();
        const auto reservedArguments = Node::TargetSelectorData::builtinArgumentNames();
        std::unordered_map<std::string, size_t> lastById;
        for (size_t i = 0; i < files.size(); ++i) {
            lastById[files[i].id] = i;
        }
        std::vector<LoadedSelector> merged;
        for (size_t i = 0; i < files.size(); ++i) {
            if (lastById[files[i].id] == i) {
                merged.push_back(std::move(files[i]));
            }
        }
        for (auto &file: merged) {
            for (auto &v: file.variables) {
                if (std::ranges::find(reservedVariables, v.name) != reservedVariables.end()) {
                    throw std::runtime_error("selector variable " + utf8::utf16to8(v.name) +
                                             " conflicts with builtin selector variable (整包拒绝)");
                }
                bool dup = false;
                for (const auto &x: builder.selectorVariables) {
                    if (x.name == v.name) {
                        dup = true;
                        break;
                    }
                }
                if (!dup) {
                    builder.selectorVariables.push_back(std::move(v));
                }
            }
        }
        for (auto &file: merged) {
            for (auto &a: file.arguments) {
                if (std::ranges::find(reservedArguments, a.name) != reservedArguments.end()) {
                    warnings.emplace_back("selector 参数 " + utf8::utf16to8(a.name) + " 与内置参数同名，已忽略（先到先得）");
                    continue;
                }
                bool dup = false;
                for (const auto &x: builder.selectorArguments) {
                    if (x.name == a.name) {
                        dup = true;
                        break;
                    }
                }
                if (!dup) {
                    builder.selectorArguments.push_back(std::move(a));
                }
            }
        }
    }

}// namespace CHelper::Extension::detail
