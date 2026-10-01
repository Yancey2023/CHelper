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

#ifndef CHELPER_COMPOSER_DETAIL_H
#define CHELPER_COMPOSER_DETAIL_H

#include <chelper/extension/Composer.h>
#include <chelper/resources/CPackBuilder.h>
#include <chelper/serialization/Serialization.h>
#include <chelper/util/JsonUtil.h>
#include <cctype>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

namespace CHelper::Extension::detail {

    using JsonDoc = rapidjson::GenericDocument<rapidjson::UTF8<>>;
    using JsonValue = rapidjson::GenericValue<rapidjson::UTF8<>>;

    /** 包内相对路径规范化：统一 `/`、去掉前导分隔符 */
    inline std::string normalizeRel(std::string rel) {
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

    /** ASCII 大小写不敏感前缀匹配（目录书写允许 ID/ 或 id/） */
    inline bool startsWithIgnoreCase(const std::string &s, const char *prefix) {
        const size_t n = std::strlen(prefix);
        if (s.size() < n) {
            return false;
        }
        for (size_t i = 0; i < n; ++i) {
            if (std::tolower(static_cast<unsigned char>(s[i])) != std::tolower(static_cast<unsigned char>(prefix[i]))) {
                return false;
            }
        }
        return true;
    }

    /** JSON 文档解析（支持注释）；空字节或非对象返回 false */
    inline bool parseDoc(const std::vector<uint8_t> &bytes, JsonDoc &doc) {
        if (bytes.empty()) {
            return false;
        }
        return JsonUtil::parseJsonWithComments(doc, reinterpret_cast<const char *>(bytes.data()), bytes.size()) && doc.IsObject();
    }

    /** 取对象字符串字段（缺失/非字符串返回空串） */
    inline std::string getString(const JsonValue &v, const char *key) {
        auto it = v.FindMember(key);
        return (it != v.MemberEnd() && it->value.IsString()) ? it->value.GetString() : std::string();
    }

    // ---- 由各职责文件实现 ----

    /** id 表装载（normal/namespace 追加；block/item 大表条目级合并） */
    void appendIdFile(CPackBuilder &builder, const std::vector<uint8_t> &bytes,
                      const std::u16string &packName, std::vector<std::string> &warnings);

    /** 单个 selector 文件装载结果（id 为叠加键） */
    struct LoadedSelector {
        std::string id;
        std::vector<Node::SelectorPackVariable> variables;
        std::vector<Node::SelectorPackArgument> arguments;
    };

    /** 解析单个 selector/*.json 并做结构校验（内容不合法 → 整包拒绝） */
    LoadedSelector parseSelectorFile(const std::vector<uint8_t> &bytes, const std::string &rel,
                                     std::vector<std::string> &warnings, const std::u16string &packName);

    /** 汇总多个 selector 文件到 builder：同 id 后文件覆盖前文件；变量/参数名冲突先到先得 */
    void flattenSelectors(CPackBuilder &builder, std::vector<LoadedSelector> &files,
                          std::vector<std::string> &warnings);

}// namespace CHelper::Extension::detail

#endif//CHELPER_COMPOSER_DETAIL_H
