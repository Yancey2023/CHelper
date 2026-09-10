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

#ifndef CHELPER_CPACK_H
#define CHELPER_CPACK_H

#include <chelper/node/CommandNode.h>
#include <chelper/resources/Manifest.h>
#include <chelper/resources/id/BlockId.h>
#include <chelper/resources/id/ItemId.h>
#include <chelper/serialization/Serialization.h>
#include <pch.h>

namespace CHelper {

    // id 数据条目：由 "type" 键区分（normal / namespace / block / item）
    struct NormalIdEntry {
        std::string id;
        std::shared_ptr<std::vector<std::shared_ptr<NormalId>>> content;
    };

    struct NamespaceIdEntry {
        std::string id;
        std::shared_ptr<std::vector<std::shared_ptr<NamespaceId>>> content;
    };

    struct BlockIdsEntry {
        std::optional<std::string> id;
        std::shared_ptr<BlockIds> content;
    };

    struct ItemIdsEntry {
        std::string id;
        std::shared_ptr<std::vector<std::shared_ptr<ItemId>>> content;
    };

    using IdEntry = std::variant<NormalIdEntry, NamespaceIdEntry, BlockIdsEntry, ItemIdsEntry>;

    // 单文件 JSON 格式
    struct CPackJsonData {
        Manifest manifest;
        std::vector<IdEntry> id;
        std::vector<Node::NodeJsonElement> json;
        std::vector<Node::RepeatData> repeat;
        std::vector<Node::NodePerCommand> command;
    };

    // 二进制（MessagePack）格式
    struct CPackData {
        Manifest manifest;
        std::unordered_map<std::string, std::shared_ptr<std::vector<std::shared_ptr<NormalId>>>> normalIds;
        std::unordered_map<std::string, std::shared_ptr<std::vector<std::shared_ptr<NamespaceId>>>> namespaceIds;
        std::shared_ptr<std::vector<std::shared_ptr<ItemId>>> itemIds;
        std::shared_ptr<BlockIds> blockIds;
        std::vector<Node::NodeJsonElement> jsonNodes;
        std::vector<Node::RepeatData> repeatNodeData;
        std::shared_ptr<std::vector<Node::NodePerCommand>> commands;
    };

}// namespace CHelper

template<>
struct glz::meta<CHelper::NormalIdEntry> {
    using T = CHelper::NormalIdEntry;
    static constexpr auto value = glz::object(&T::id, &T::content);
};

template<>
struct glz::meta<CHelper::NamespaceIdEntry> {
    using T = CHelper::NamespaceIdEntry;
    static constexpr auto value = glz::object(&T::id, &T::content);
};

template<>
struct glz::meta<CHelper::BlockIdsEntry> {
    using T = CHelper::BlockIdsEntry;
    static constexpr auto value = glz::object(&T::id, &T::content);
};

template<>
struct glz::meta<CHelper::ItemIdsEntry> {
    using T = CHelper::ItemIdsEntry;
    static constexpr auto value = glz::object(&T::id, &T::content);
};

template<>
struct glz::meta<CHelper::IdEntry> {
    static constexpr std::string_view tag = "type";
    static constexpr auto ids = std::array{"normal", "namespace", "block", "item"};
};

template<>
struct glz::meta<CHelper::CPackJsonData> {
    using T = CHelper::CPackJsonData;
    static constexpr auto value = glz::object(&T::manifest, &T::id, &T::json, &T::repeat, &T::command);
};

template<>
struct glz::meta<CHelper::CPackData> {
    using T = CHelper::CPackData;
    static constexpr auto value = glz::object(&T::manifest, &T::normalIds, &T::namespaceIds, &T::itemIds, &T::blockIds, &T::jsonNodes, &T::repeatNodeData, &T::commands);
};

// IdEntry 变体的二进制格式支持：uint8 备选索引 + 备选值（非自描述）
template<>
struct glz::to<CHelper::BinaryFormat, CHelper::IdEntry> {
    template<auto Opts>
    static void op(auto &&value, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
        const std::uint8_t index = static_cast<std::uint8_t>(value.index());
        glz::serialize<CHelper::BinaryFormat>::template op<Opts>(index, ctx, b, ix);
        std::visit([&](const auto &alt) {
            glz::serialize<CHelper::BinaryFormat>::template op<Opts>(alt, ctx, b, ix);
        },
                   value);
    }
};

template<>
struct glz::from<CHelper::BinaryFormat, CHelper::IdEntry> {
    template<auto Opts>
    static void op(auto &&value, glz::is_context auto &&ctx, auto &&it, auto &&end) {
        std::uint8_t index = 0;
        glz::parse<CHelper::BinaryFormat>::template op<Opts>(index, ctx, it, end);
        if (bool(ctx.error)) return;
        switch (index) {
            case 0:
                value.template emplace<0>();
                break;
            case 1:
                value.template emplace<1>();
                break;
            case 2:
                value.template emplace<2>();
                break;
            case 3:
                value.template emplace<3>();
                break;
            default:
                ctx.error = glz::error_code::no_matching_variant_type;
                return;
        }
        std::visit([&](auto &alt) {
            glz::parse<CHelper::BinaryFormat>::template op<Opts>(alt, ctx, it, end);
        },
                   value);
    }
};

namespace CHelper {

    class CPack {
    public:
        Manifest manifest;
        std::unordered_map<std::string, std::shared_ptr<std::vector<std::shared_ptr<NormalId>>>> normalIds;
        std::unordered_map<std::string, std::shared_ptr<std::vector<std::shared_ptr<NamespaceId>>>> namespaceIds;
        std::shared_ptr<BlockIds> blockIds;
        std::shared_ptr<std::vector<std::shared_ptr<ItemId>>> itemIds;
        std::vector<Node::NodeJsonElement> jsonNodes;
        std::vector<Node::RepeatData> repeatNodeData;
        std::unordered_map<std::string, std::pair<const Node::RepeatData *, Node::NodeWithType>> repeatNodes;
        Node::TargetSelectorData targetSelectorData;
        std::shared_ptr<std::vector<Node::NodePerCommand>> commands = std::make_shared<std::vector<Node::NodePerCommand>>();
        Node::NodeCommand mainNode;

    private:
        Node::FreeableNodeWithTypes cacheNodes;

        /**
         * 在CPack初始化完成、Parser使用之前，集中校验CPack的结构不变量，
         * 非法数据在这里fail-fast，而不是进入Parser导致越界/空指针/未定义行为
         */
        void validate() const;

        void applyId(const IdEntry &entry);

        void applyJson(Node::NodeJsonElement &&item);

        void applyRepeat(Node::RepeatData &&item);

        void applyCommand(Node::NodePerCommand &&item) const;

        void afterApply();

    public:
#ifndef CHELPER_NO_FILESYSTEM
        explicit CPack(const std::filesystem::path &path);
#endif

        explicit CPack(CPackJsonData &&data);

        explicit CPack(CPackData &&data);

#ifndef CHELPER_NO_FILESYSTEM
        static std::unique_ptr<CPack> createByDirectory(const std::filesystem::path &path);

        static std::unique_ptr<CPack> createByJson(const std::filesystem::path &cpackPath);
#endif

        static std::unique_ptr<CPack> createByBinary(std::string_view data);

        static std::unique_ptr<CPack> createByJson(const std::string &json);

#ifndef CHELPER_NO_FILESYSTEM
        void writeJsonToDirectory(const std::filesystem::path &path) const;
#endif

        [[nodiscard]] std::string toJson() const;

#ifndef CHELPER_NO_FILESYSTEM
        void writeJsonToFile(const std::filesystem::path &path) const;

        void writeBinToFile(const std::filesystem::path &path) const;
#endif

        [[nodiscard]] std::shared_ptr<std::vector<std::shared_ptr<NormalId>>>
        getNormalId(const std::string &key) const;

        [[nodiscard]] std::shared_ptr<std::vector<std::shared_ptr<NamespaceId>>>
        getNamespaceId(const std::string &key) const;
    };

}// namespace CHelper

#endif//CHELPER_CPACK_H
