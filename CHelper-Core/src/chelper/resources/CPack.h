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
#include <chelper/util/CPackMemory.h>
#include <pch.h>

namespace CHelper {

    // id 数据条目：由 "type" 键区分（normal / namespace / block / item）
    struct NormalIdEntry {
        std::pmr::string id;
        std::shared_ptr<std::pmr::vector<std::shared_ptr<NormalId>>> content;
    };

    struct NamespaceIdEntry {
        std::pmr::string id;
        std::shared_ptr<std::pmr::vector<std::shared_ptr<NamespaceId>>> content;
    };

    struct BlockIdsEntry {
        std::optional<std::pmr::string> id;
        std::shared_ptr<BlockIds> content;
    };

    struct ItemIdsEntry {
        std::pmr::string id;
        std::shared_ptr<std::pmr::vector<std::shared_ptr<ItemId>>> content;
    };

    using IdEntry = std::variant<NormalIdEntry, NamespaceIdEntry, BlockIdsEntry, ItemIdsEntry>;

    // 单文件 JSON 格式
    struct CPackJsonData {
        Manifest manifest;
        std::pmr::vector<IdEntry> id;
        std::pmr::vector<Node::NodeJsonElement> json;
        std::pmr::vector<Node::RepeatData> repeat;
        std::pmr::vector<Node::NodePerCommand> command;
    };

    // 二进制（MessagePack）格式
    struct CPackData {
        Manifest manifest;
        std::pmr::unordered_map<std::pmr::string, std::shared_ptr<std::pmr::vector<std::shared_ptr<NormalId>>>> normalIds;
        std::pmr::unordered_map<std::pmr::string, std::shared_ptr<std::pmr::vector<std::shared_ptr<NamespaceId>>>> namespaceIds;
        std::shared_ptr<std::pmr::vector<std::shared_ptr<ItemId>>> itemIds;
        std::shared_ptr<BlockIds> blockIds;
        std::pmr::vector<Node::NodeJsonElement> jsonNodes;
        std::pmr::vector<Node::RepeatData> repeatNodeData;
        std::shared_ptr<std::pmr::vector<Node::NodePerCommand>> commands;
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
        CPackMemoryScope destructionMemoryScope;

        // 默认构造不做任何工作，成员由 Serialization.h 的读取函数填充
        CPack() = default;

        // 必须声明在 ID 容器之前，确保销毁 CPack 对象后再销毁内存资源
        std::shared_ptr<CPackMemoryResource> cpackMemory;

#ifndef CHELPER_NO_FILESYSTEM
        friend std::unique_ptr<CPack> serialization::createCPackByDirectory(const std::filesystem::path &path);

        friend std::unique_ptr<CPack> serialization::createCPackByJsonFile(const std::filesystem::path &cpackPath);
#endif

        friend std::unique_ptr<CPack> serialization::createCPackByJson(const std::string &json);

        friend std::unique_ptr<CPack> serialization::createCPackByBinary(std::string_view data);

    public:
        ~CPack() {
            CPackMemoryRouter::install();
            CPackMemoryRouter::setCurrent(cpackMemory->getResource());
        }

        Manifest manifest;
        std::pmr::unordered_map<std::pmr::string, std::shared_ptr<std::pmr::vector<std::shared_ptr<NormalId>>>> normalIds;
        std::pmr::unordered_map<std::pmr::string, std::shared_ptr<std::pmr::vector<std::shared_ptr<NamespaceId>>>> namespaceIds;
        std::shared_ptr<BlockIds> blockIds;
        std::shared_ptr<std::pmr::vector<std::shared_ptr<ItemId>>> itemIds;
        std::pmr::vector<Node::NodeJsonElement> jsonNodes;
        std::pmr::vector<Node::RepeatData> repeatNodeData;
        std::pmr::unordered_map<std::pmr::string, std::pair<const Node::RepeatData *, Node::NodeWithType>> repeatNodes;
        Node::TargetSelectorData targetSelectorData;
        std::shared_ptr<std::pmr::vector<Node::NodePerCommand>> commands;
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

        [[nodiscard]] std::shared_ptr<std::pmr::vector<std::shared_ptr<NormalId>>>
        getNormalId(std::string_view key) const;

        [[nodiscard]] std::shared_ptr<std::pmr::vector<std::shared_ptr<NamespaceId>>>
        getNamespaceId(std::string_view key) const;

        [[nodiscard]] std::pmr::memory_resource *getMemoryResource() const noexcept {
            return cpackMemory->getResource();
        }
    };

}// namespace CHelper

#endif//CHELPER_CPACK_H
