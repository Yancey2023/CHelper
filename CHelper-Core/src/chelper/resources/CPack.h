//
// Created by Yancey on 2023/11/7.
//

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
        std::shared_ptr<std::vector<Node::NodePerCommand>> commands = std::make_shared<std::vector<Node::NodePerCommand>>();
        Node::NodeCommand mainNode;

    private:
        Node::FreeableNodeWithTypes cacheNodes;

    public:
#ifndef CHELPER_NO_FILESYSTEM
        explicit CPack(const std::filesystem::path &path);
#endif

        explicit CPack(const rapidjson::GenericDocument<rapidjson::UTF8<>> &j);

        explicit CPack(std::istream &binaryReader);

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

        static std::unique_ptr<CPack> createByBinary(std::istream &binaryReader);

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
