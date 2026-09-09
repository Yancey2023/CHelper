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
#include <pch.h>

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
        // selector/*.json V1 数据化（拓展包，内存态不落二进制；供 targetSelectorData.init 装配）
        std::vector<Node::SelectorPackVariable> selectorVariables;
        std::vector<Node::SelectorPackArgument> selectorArguments;
        // 命令名 → 来源包名（合成器挂载，内存态不落二进制；空/缺失 = 内置，供一级补全徽标）
        std::unordered_map<std::u16string, std::u16string> commandNameSources;
        std::shared_ptr<std::vector<Node::NodePerCommand>> commands = std::make_shared<std::vector<Node::NodePerCommand>>();
        Node::NodeCommand mainNode;

    private:
        // 供 CPackBuilder 从"文件集合"装载后统一物化（见 CPackBuilder.h/.cpp）
        CPack() = default;
        Node::FreeableNodeWithTypes cacheNodes;

        /**
         * 在CPack初始化完成、Parser使用之前，集中校验CPack的结构不变量，
         * 非法数据在这里fail-fast，而不是进入Parser导致越界/空指针/未定义行为
         */
        void validate() const;

        friend class CPackBuilder;

    public:
#ifndef CHELPER_NO_FILESYSTEM
        explicit CPack(const std::filesystem::path &path);
#endif

        explicit CPack(const rapidjson::GenericDocument<rapidjson::UTF8<>> &j);

        explicit CPack(std::istream &istream);

    private:
        void applyId(const rapidjson::GenericValue<rapidjson::UTF8<>> &j);

        void applyJson(const rapidjson::GenericValue<rapidjson::UTF8<>> &j);

        void applyRepeat(const rapidjson::GenericValue<rapidjson::UTF8<>> &j);

        void applyCommand(const rapidjson::GenericValue<rapidjson::UTF8<>> &j) const;

        void afterApply();

    public:
#ifndef CHELPER_NO_FILESYSTEM
        static std::unique_ptr<CPack> createByDirectory(const std::filesystem::path &path);
#endif

        static std::unique_ptr<CPack> createByJson(const rapidjson::GenericDocument<rapidjson::UTF8<>> &j);

        static std::unique_ptr<CPack> createByBinary(std::istream &istream);

#ifndef CHELPER_NO_FILESYSTEM
        void writeJsonToDirectory(const std::filesystem::path &path) const;
#endif

        [[nodiscard]] rapidjson::GenericDocument<rapidjson::UTF8<>> toJson() const;

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
