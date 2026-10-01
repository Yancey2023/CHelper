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

namespace CHelper::Extension::detail {

    namespace {

        // 方块/物品大表合并（条目级，先到先得）：
        // - blockStateValues / item 条目：与已有（同命名空间 + 同名）条目冲突 → 忽略并记告警；
        //   新条目追加（打来源标记，供 Suggestion.packName 徽标）。
        // - blockPropertyDescriptions：按"方块集相交 + 属性名相同"先到先得，
        //   冲突属性忽略；其余按组追加。描述缺失的兜底与装配期约束见 composer.md §3.3。
        void appendBigIdFile(CPackBuilder &builder, const JsonValue &doc, const std::string &type,
                             const std::u16string &packName, std::vector<std::string> &warnings) {
            auto nsOf = [](const std::shared_ptr<NamespaceId> &item) -> const std::u16string & {
                if (item->idNamespace.has_value()) [[unlikely]] {
                    return item->idNamespace.value();
                }
                // 主包条目没有 idNamespace 即视为默认命名空间（与 CPack::getNamespaceId/匹配逻辑一致）
                static const std::u16string defaultNs = u"minecraft";
                return defaultNs;
            };
            auto sameEntry = [&nsOf](const std::shared_ptr<NamespaceId> &a, const std::shared_ptr<NamespaceId> &b) {
                return a->name == b->name && nsOf(a) == nsOf(b);
            };
            auto warnDup = [&warnings, &packName, &type](const NamespaceId &item) {
                std::u16string full = type == "block" ? u"block " : u"item ";
                if (item.idNamespace.has_value()) {
                    full += item.idNamespace.value() + u":";
                }
                full += item.name;
                warnings.emplace_back("拓展包 " + utf8::utf16to8(packName) + " 的 " + utf8::utf16to8(full) +
                                      " 与已有条目重复，已忽略（先到先得）");
            };

            if (type == "block") {
                BlockIds ext;
                serialization::Codec<BlockIds>::template from_json_member<JsonValue>(doc, "content", ext);
                if (!builder.blockIds) {
                    builder.blockIds = std::make_shared<BlockIds>();
                }
                BlockIds &target = *builder.blockIds;
                if (!target.blockStateValues) {
                    target.blockStateValues = std::make_shared<std::vector<std::shared_ptr<BlockId>>>();
                }
                if (ext.blockStateValues) {
                    for (auto &entry: *ext.blockStateValues) {
                        const auto &dup = std::ranges::find_if(*target.blockStateValues, [&](const auto &e) {
                            return sameEntry(e, entry);
                        });
                        if (dup != target.blockStateValues->end()) {
                            warnDup(*entry);
                            continue;
                        }
                        entry->packName = packName;
                        target.blockStateValues->push_back(std::move(entry));
                    }
                }
                // blockPropertyDescriptions.common：按属性名先到先得
                for (auto &p: ext.blockPropertyDescriptions.common) {
                    const auto &dup = std::ranges::find_if(target.blockPropertyDescriptions.common,
                                                           [&p](const BlockPropertyDescription &x) {
                                                               return x.propertyName == p.propertyName;
                                                           });
                    if (dup != target.blockPropertyDescriptions.common.end()) {
                        warnings.emplace_back("拓展包 " + utf8::utf16to8(packName) +
                                              " 的公共属性描述 " + utf8::utf16to8(p.propertyName) +
                                              " 已存在，已忽略（先到先得）");
                        continue;
                    }
                    target.blockPropertyDescriptions.common.push_back(std::move(p));
                }
                // blockPropertyDescriptions.block：按"方块集相交且属性名相同"先到先得
                for (auto &g: ext.blockPropertyDescriptions.block) {
                    PerBlockPropertyDescription kept;
                    kept.blocks = std::move(g.blocks);
                    for (auto &p: g.properties) {
                        bool collide = false;
                        for (const auto &g2: target.blockPropertyDescriptions.block) {
                            bool intersect = false;
                            for (const auto &b: g2.blocks) {
                                if (std::ranges::find(kept.blocks, b) != kept.blocks.end()) {
                                    intersect = true;
                                    break;
                                }
                            }
                            if (!intersect) {
                                continue;
                            }
                            if (std::ranges::find_if(g2.properties, [&p](const BlockPropertyDescription &x) {
                                    return x.propertyName == p.propertyName;
                                }) != g2.properties.end()) {
                                collide = true;
                                break;
                            }
                        }
                        if (collide) {
                            warnings.emplace_back("拓展包 " + utf8::utf16to8(packName) +
                                                  " 的属性描述 " + utf8::utf16to8(p.propertyName) +
                                                  " 已存在，已忽略（先到先得）");
                            continue;
                        }
                        kept.properties.push_back(std::move(p));
                    }
                    if (!kept.properties.empty()) {
                        target.blockPropertyDescriptions.block.push_back(std::move(kept));
                    }
                }
            } else {
                std::shared_ptr<std::vector<std::shared_ptr<ItemId>>> ext;
                serialization::Codec<decltype(ext)>::template from_json_member<JsonValue>(doc, "content", ext);
                if (!ext) {
                    return;
                }
                if (!builder.itemIds) {
                    builder.itemIds = std::make_shared<std::vector<std::shared_ptr<ItemId>>>();
                }
                for (auto &entry: *ext) {
                    const auto &dup = std::ranges::find_if(*builder.itemIds, [&](const auto &e) {
                        return sameEntry(e, entry);
                    });
                    if (dup != builder.itemIds->end()) {
                        warnDup(*entry);
                        continue;
                    }
                    entry->packName = packName;
                    builder.itemIds->push_back(std::move(entry));
                }
            }
        }

    }// namespace

    // id 表装载（normal/namespace 追加；block/item 条目级合并，见上）
    void appendIdFile(CPackBuilder &builder, const std::vector<uint8_t> &bytes,
                      const std::u16string &packName, std::vector<std::string> &warnings) {
        JsonDoc doc;
        if (!parseDoc(bytes, doc)) {
            throw std::runtime_error("invalid id file");
        }
        const std::string type = getString(doc, "type");
        const std::string id = getString(doc, "id");
        if (type == "normal") {
            std::shared_ptr<std::vector<std::shared_ptr<NormalId>>> content;
            serialization::Codec<decltype(content)>::template from_json_member<JsonValue>(doc, "content", content);
            for (auto &item: *content) {
                item->packName = packName;
            }
            auto existing = builder.normalIds.find(id);
            if (existing == builder.normalIds.end()) {
                builder.normalIds.emplace(id, std::move(content));
            } else {
                existing->second->insert(existing->second->end(), content->begin(), content->end());
            }
        } else if (type == "namespace") {
            std::shared_ptr<std::vector<std::shared_ptr<NamespaceId>>> content;
            serialization::Codec<decltype(content)>::template from_json_member<JsonValue>(doc, "content", content);
            for (auto &item: *content) {
                item->packName = packName;
            }
            auto existing = builder.namespaceIds.find(id);
            if (existing == builder.namespaceIds.end()) {
                builder.namespaceIds.emplace(id, std::move(content));
            } else {
                existing->second->insert(existing->second->end(), content->begin(), content->end());
            }
        } else if (type == "block" || type == "item") {
            appendBigIdFile(builder, doc, type, packName, warnings);
        } else {
            throw std::runtime_error("unknown id type: " + type);
        }
    }

}// namespace CHelper::Extension::detail
