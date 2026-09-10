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
#include <chelper/node/NodeType.h>
#include <chelper/resources/Manifest.h>
#include <chelper/resources/id/BlockId.h>
#include <chelper/resources/id/ItemId.h>
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


namespace CHelper {

    // CPack 的读取函数（唯一允许构建 CPack 的入口），定义在 Serialization.h
    namespace serialization {
#ifndef CHELPER_NO_FILESYSTEM
        std::unique_ptr<CPack> createCPackByDirectory(const std::filesystem::path &path);

        std::unique_ptr<CPack> createCPackByJsonFile(const std::filesystem::path &cpackPath);
#endif

        std::unique_ptr<CPack> createCPackByJson(const std::string &json);

        std::unique_ptr<CPack> createCPackByBinary(std::string_view data);
    }// namespace serialization

    class CPack {
    private:
        // 默认构造不做任何工作，成员由 Serialization.h 的读取函数填充
        CPack() = default;

#ifndef CHELPER_NO_FILESYSTEM
        friend std::unique_ptr<CPack> serialization::createCPackByDirectory(const std::filesystem::path &path);

        friend std::unique_ptr<CPack> serialization::createCPackByJsonFile(const std::filesystem::path &cpackPath);
#endif

        friend std::unique_ptr<CPack> serialization::createCPackByJson(const std::string &json);

        friend std::unique_ptr<CPack> serialization::createCPackByBinary(std::string_view data);

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
