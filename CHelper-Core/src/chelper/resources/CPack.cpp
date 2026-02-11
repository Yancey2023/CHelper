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

#include <chelper/node/NodeInitialization.h>
#include <chelper/node/NodeType.h>
#include <chelper/resources/CPack.h>
#include <chelper/resources/Manifest.h>
#include <chelper/serialization/Serialization.h>

namespace CHelper {

#ifndef CHELPER_NO_FILESYSTEM
    CPack::CPack(const std::filesystem::path &path) {
#if defined(CHelperDebug) && !defined(CHELPER_NO_FILESYSTEM)
        size_t stackSize = Profile::stack.size();
#endif
        currentCreateStage = Node::NodeCreateStage::NONE;
        Profile::push("loading manifest");
        auto jsonManifest = serialization::get_json_from_file(path / "manifest.json");
        serialization::Codec<Manifest>::from_json(jsonManifest, manifest);
        Profile::next("loading id data");
        for (const auto &file: std::filesystem::recursive_directory_iterator(path / "id")) {
            Profile::next(R"(loading id data in path "{}")", FORMAT_ARG(file.path().string()));
            applyId(serialization::get_json_from_file(file));
        }
        Profile::next("loading json data");
        currentCreateStage = Node::NodeCreateStage::JSON_NODE;
        for (const auto &file: std::filesystem::recursive_directory_iterator(path / "json")) {
            Profile::next(R"(loading json data in path "{}")", FORMAT_ARG(file.path().string()));
            applyJson(serialization::get_json_from_file(file));
        }
        Profile::next("loading repeat data");
        currentCreateStage = Node::NodeCreateStage::REPEAT_NODE;
        for (const auto &file: std::filesystem::recursive_directory_iterator(path / "repeat")) {
            Profile::next(R"(loading repeat data in path "{}")", FORMAT_ARG(file.path().string()));
            applyRepeat(serialization::get_json_from_file(file));
        }
        Profile::next("loading commands");
        currentCreateStage = Node::NodeCreateStage::COMMAND_PARAM_NODE;
        for (const auto &file: std::filesystem::recursive_directory_iterator(path / "command")) {
            Profile::next(R"(loading command in path "{}")", FORMAT_ARG(file.path().string()));
            applyCommand(serialization::get_json_from_file(file));
        }
        Profile::next("init cpack");
        currentCreateStage = Node::NodeCreateStage::NONE;
        afterApply();
        Profile::pop();
#if defined(CHelperDebug) && !defined(CHELPER_NO_FILESYSTEM)
        if (Profile::stack.size() != stackSize) [[unlikely]] {
            SPDLOG_WARN("error profile stack after loading cpack");
        }
#endif
    }
#endif

    CPack::CPack(const rapidjson::GenericDocument<rapidjson::UTF8<>> &j) {
        using JsonValueType = rapidjson::GenericDocument<rapidjson::UTF8<>>;
#if defined(CHelperDebug) && !defined(CHELPER_NO_FILESYSTEM)
        size_t stackSize = Profile::stack.size();
#endif
        currentCreateStage = Node::NodeCreateStage::NONE;
        Profile::push("loading manifest");
        serialization::Codec<Manifest>::template from_json_member<JsonValueType>(j, "manifest", manifest);
        Profile::next("loading id data");
        for (const auto &item: serialization::find_array_member_or_throw(j, "id")) {
            applyId(item);
        }
        Profile::next("loading json data");
        currentCreateStage = Node::NodeCreateStage::JSON_NODE;
        for (const auto &item: serialization::find_array_member_or_throw(j, "json")) {
            applyJson(item);
        }
        Profile::next("loading repeat data");
        currentCreateStage = Node::NodeCreateStage::REPEAT_NODE;
        for (const auto &item: serialization::find_array_member_or_throw(j, "repeat")) {
            applyRepeat(item);
        }
        Profile::next("loading command data");
        currentCreateStage = Node::NodeCreateStage::COMMAND_PARAM_NODE;
        for (const auto &item: serialization::find_array_member_or_throw(j, "command")) {
            applyCommand(item);
        }
        Profile::next("init cpack");
        currentCreateStage = Node::NodeCreateStage::NONE;
        afterApply();
        Profile::pop();
#if defined(CHelperDebug) && !defined(CHELPER_NO_FILESYSTEM)
        if (Profile::stack.size() != stackSize) [[unlikely]] {
            SPDLOG_WARN("error profile stack after loading cpack");
        }
#endif
    }

    CPack::CPack(std::istream &istream) {
#if defined(CHelperDebug) && !defined(CHELPER_NO_FILESYSTEM)
        size_t stackSize = Profile::stack.size();
#endif
        currentCreateStage = Node::NodeCreateStage::NONE;
        Profile::push("loading manifest");
        serialization::from_binary(istream, manifest);
        Profile::next("loading normal id data");
        serialization::from_binary(istream, normalIds);
        Profile::next("loading namespace id data");
        serialization::from_binary(istream, namespaceIds);
        Profile::next("loading item id data");
        serialization::from_binary(istream, itemIds);
        Profile::next("loading block id data");
        serialization::from_binary(istream, blockIds);
        Profile::next("loading json data");
        currentCreateStage = Node::NodeCreateStage::JSON_NODE;
        serialization::from_binary(istream, jsonNodes);
        Profile::next("loading repeat data");
        currentCreateStage = Node::NodeCreateStage::REPEAT_NODE;
        serialization::from_binary(istream, repeatNodeData);
        Profile::next("loading command data");
        currentCreateStage = Node::NodeCreateStage::COMMAND_PARAM_NODE;
        serialization::from_binary(istream, commands);
        Profile::next("init cpack");
        currentCreateStage = Node::NodeCreateStage::NONE;
        afterApply();
        Profile::pop();
#if defined(CHelperDebug) && !defined(CHELPER_NO_FILESYSTEM)
        if (Profile::stack.size() != stackSize) [[unlikely]] {
            SPDLOG_WARN("error profile stack after loading cpack");
        }
#endif
    }

    void CPack::applyId(const rapidjson::GenericValue<rapidjson::UTF8<>> &j) {
        using JsonValueType = rapidjson::GenericValue<rapidjson::UTF8<>>;
        std::u16string type;
        serialization::Codec<decltype(type)>::template from_json_member<JsonValueType>(j, "type", type);
        if (type == u"normal") [[likely]] {
            std::string id;
            serialization::Codec<decltype(id)>::template from_json_member<JsonValueType>(j, "id", id);
            std::shared_ptr<std::vector<std::shared_ptr<NormalId>>> content;
            serialization::Codec<decltype(content)>::template from_json_member<JsonValueType>(j, "content", content);
            normalIds.emplace(std::move(id), std::move(content));
        } else if (type == u"namespace") [[likely]] {
            std::string id;
            serialization::Codec<decltype(id)>::template from_json_member<JsonValueType>(j, "id", id);
            std::shared_ptr<std::vector<std::shared_ptr<NamespaceId>>> content;
            serialization::Codec<decltype(content)>::template from_json_member<JsonValueType>(j, "content", content);
            namespaceIds.emplace(std::move(id), std::move(content));
        } else if (type == u"block") [[likely]] {
            serialization::Codec<decltype(blockIds)>::template from_json_member<JsonValueType>(j, "content", blockIds);
        } else if (type == u"item") [[likely]] {
            serialization::Codec<decltype(itemIds)>::template from_json_member<JsonValueType>(j, "content", itemIds);
        } else {
            Profile::push("unknown id type -> {}", FORMAT_ARG(utf8::utf16to8(type)));
            throw std::runtime_error("unknown id type");
        }
    }

    void CPack::applyJson(const rapidjson::GenericValue<rapidjson::UTF8<>> &j) {
        using JsonValueType = rapidjson::GenericValue<rapidjson::UTF8<>>;
        Node::NodeJsonElement item;
        serialization::Codec<decltype(item)>::template from_json<JsonValueType>(j, item);
        jsonNodes.push_back(std::move(item));
    }

    void CPack::applyRepeat(const rapidjson::GenericValue<rapidjson::UTF8<>> &j) {
        using JsonValueType = rapidjson::GenericValue<rapidjson::UTF8<>>;
        Node::RepeatData item;
        serialization::Codec<decltype(item)>::template from_json<JsonValueType>(j, item);
        repeatNodeData.push_back(std::move(item));
    }

    void CPack::applyCommand(const rapidjson::GenericValue<rapidjson::UTF8<>> &j) const {
        using JsonValueType = rapidjson::GenericValue<rapidjson::UTF8<>>;
        Node::NodePerCommand item;
        serialization::Codec<decltype(item)>::template from_json<JsonValueType>(j, item);
        commands->push_back(std::move(item));
    }

    void CPack::afterApply() {
        // selector nodes
        Profile::push("init selector nodes");
        targetSelectorData.init(*this);
        // json nodes
        Profile::next("init json nodes");
        for (const auto &item: jsonNodes) {
            Node::initNode(item, *this);
        }
        // repeat nodes
        Profile::next("init repeat nodes");
        for (const auto &item: repeatNodeData) {
            if (item.repeatNodes.size() != item.isEnd.size()) [[unlikely]] {
                Profile::push("checking repeat node: {}", FORMAT_ARG(item.id));
                throw std::runtime_error("fail to check repeat id because repeatNodes size not equal isEnd size");
            }
        }
        // command param nodes
        for (const auto &item: repeatNodeData) {
            std::vector<Node::NodeWithType> content;
            content.reserve(item.repeatNodes.size());
            for (const auto &item2: item.repeatNodes) {
                std::vector<Node::NodeWithType> perContent;
                perContent.reserve(item2.nodes.size());
                for (const auto &item3: item2.nodes) {
                    auto nodeWrapped = new Node::NodeWrapped(item3);
                    perContent.emplace_back(*nodeWrapped);
                    cacheNodes.nodes.emplace_back(*nodeWrapped);
                }
                auto node = new Node::NodeAnd(perContent);
                content.emplace_back(*node);
                cacheNodes.nodes.emplace_back(*node);
            }
            std::vector<Node::NodeWithType> breakChildNodes;
            breakChildNodes.reserve(item.breakNodes.nodes.size());
            for (const auto &item2: item.breakNodes.nodes) {
                auto nodeWrapped = new Node::NodeWrapped(item2);
                breakChildNodes.emplace_back(*nodeWrapped);
                cacheNodes.nodes.emplace_back(*nodeWrapped);
            }
            auto unBreakNode = new Node::NodeOr(content, false);
            auto breakNode = new Node::NodeAnd(breakChildNodes);
            auto orNode = new Node::NodeOr({*unBreakNode, *breakNode}, false);
            repeatNodes.emplace(item.id, std::make_pair<const Node::RepeatData *, Node::NodeWithType>(&item, *orNode));
            cacheNodes.nodes.emplace_back(*unBreakNode);
            cacheNodes.nodes.emplace_back(*breakNode);
            cacheNodes.nodes.emplace_back(*orNode);
        }
        for (const auto &item: repeatNodeData) {
            for (const auto &item2: item.repeatNodes) {
                for (const auto &item3: item2.nodes) {
                    Node::initNode(item3, *this);
                }
            }
            for (const auto &item2: item.breakNodes.nodes) {
                Node::initNode(item2, *this);
            }
        }
        for (const auto &item: *commands) {
            Profile::next(R"(init command: "{}")", FORMAT_ARG(utf8::utf16to8(fmt::format(u"{}", StringUtil::join(item.name, u",")))));
            Node::initNode(item, *this);
        }
        Profile::next("sort command nodes");
        std::ranges::sort(*commands, [](const auto &item1, const auto &item2) {
            return item1.name[0] < item2.name[0];
        });
        Profile::next("create main node");
        mainNode = Node::NodeCommand("MAIN_NODE", u"欢迎使用命令助手(作者：Yancey)", commands.get());
        Profile::pop();
    }

#ifndef CHELPER_NO_FILESYSTEM
    std::unique_ptr<CPack> CPack::createByDirectory(const std::filesystem::path &path) {
        Profile::push("start load CPack by DIRECTORY: {}", FORMAT_ARG(path.string()));
        std::unique_ptr<CPack> cpack = std::make_unique<CPack>(path);
        Profile::pop();
        return cpack;
    }
#endif

    std::unique_ptr<CPack> CPack::createByJson(const rapidjson::GenericDocument<rapidjson::UTF8<>> &j) {
        Profile::push("start load CPack by JSON");
        auto cpack = std::make_unique<CPack>(j);
        Profile::pop();
        return cpack;
    }

    std::unique_ptr<CPack> CPack::createByBinary(std::istream &istream) {
        Profile::push("start load CPack by binary");
        std::unique_ptr<CPack> cpack = std::make_unique<CPack>(istream);
        Profile::pop();
        return cpack;
    }

#ifndef CHELPER_NO_FILESYSTEM
    template<class JsonType>
    void writeJsonToFileWithCreateDirectory(const std::filesystem::path &path, const JsonType &j) {
        if (!exists(path)) {
            std::filesystem::create_directories(path.parent_path());
        }
        serialization::template write_json_to_file<JsonType>(path, j);
    }
#endif

#ifndef CHELPER_NO_FILESYSTEM
    void CPack::writeJsonToDirectory(const std::filesystem::path &path) const {
        using JsonValueType = rapidjson::GenericDocument<rapidjson::UTF8<>>;
        {
            JsonValueType j;
            serialization::Codec<decltype(manifest)>::template to_json<JsonValueType>(j.GetAllocator(), j, manifest);
            writeJsonToFileWithCreateDirectory<JsonValueType>(path / "manifest.json", j);
        }
        for (const auto &item: normalIds) {
            JsonValueType j;
            j.SetObject();
            j.MemberReserve(3, j.GetAllocator());
            serialization::Codec<decltype(item.first)>::template to_json_member<JsonValueType>(j.GetAllocator(), j, "id", item.first);
            serialization::Codec<std::string>::template to_json_member<JsonValueType>(j.GetAllocator(), j, "type", "normal");
            serialization::Codec<decltype(item.second)>::template to_json_member<JsonValueType>(j.GetAllocator(), j, "content", item.second);
            writeJsonToFileWithCreateDirectory<JsonValueType>(path / "id" / (item.first + ".json"), j);
        }
        for (const auto &item: namespaceIds) {
            JsonValueType j;
            j.SetObject();
            j.MemberReserve(3, j.GetAllocator());
            serialization::Codec<decltype(item.first)>::template to_json_member<JsonValueType>(j.GetAllocator(), j, "id", item.first);
            serialization::Codec<std::string>::template to_json_member<JsonValueType>(j.GetAllocator(), j, "type", "namespace");
            serialization::Codec<decltype(item.second)>::template to_json_member<JsonValueType>(j.GetAllocator(), j, "content", item.second);
            writeJsonToFileWithCreateDirectory<JsonValueType>(path / "id" / (item.first + ".json"), j);
        }
        {
            JsonValueType j;
            j.SetObject();
            j.MemberReserve(3, j.GetAllocator());
            serialization::Codec<std::string>::template to_json_member<JsonValueType>(j.GetAllocator(), j, "id", "item");
            serialization::Codec<std::string>::template to_json_member<JsonValueType>(j.GetAllocator(), j, "type", "item");
            serialization::Codec<decltype(itemIds)>::template to_json_member<JsonValueType>(j.GetAllocator(), j, "content", itemIds);
            writeJsonToFileWithCreateDirectory<JsonValueType>(path / "id" / "items.json", j);
        }
        {
            JsonValueType j;
            j.SetObject();
            j.MemberReserve(3, j.GetAllocator());
            serialization::Codec<std::string>::template to_json_member<JsonValueType>(j.GetAllocator(), j, "id", "block");
            serialization::Codec<std::string>::template to_json_member<JsonValueType>(j.GetAllocator(), j, "type", "block");
            serialization::Codec<decltype(blockIds)>::template to_json_member<JsonValueType>(j.GetAllocator(), j, "content", blockIds);
            writeJsonToFileWithCreateDirectory<JsonValueType>(path / "id" / "block.json", j);
        }
        for (const auto &item: jsonNodes) {
            JsonValueType j;
            serialization::Codec<decltype(item)>::template to_json<JsonValueType>(j.GetAllocator(), j, item);
            writeJsonToFileWithCreateDirectory<JsonValueType>(path / "json" / (item.id.value() + ".json"), j);
        }
        for (const auto &item: repeatNodeData) {
            JsonValueType j;
            serialization::Codec<decltype(item)>::template to_json<JsonValueType>(j.GetAllocator(), j, item);
            writeJsonToFileWithCreateDirectory<JsonValueType>(path / "repeat" / (item.id + ".json"), j);
        }
        for (const auto &item: *commands) {
            JsonValueType j;
            serialization::Codec<decltype(item)>::template to_json<JsonValueType>(j.GetAllocator(), j, item);
            writeJsonToFileWithCreateDirectory<JsonValueType>(path / "command" / (utf8::utf16to8(item.name[0]) + ".json"), j);
        }
    }
#endif

    [[nodiscard]] rapidjson::GenericDocument<rapidjson::UTF8<>> CPack::toJson() const {
        using JsonValueType = rapidjson::GenericDocument<rapidjson::UTF8<>>;
        rapidjson::GenericDocument<rapidjson::UTF8<>> result;
        result.SetObject();
        serialization::Codec<decltype(manifest)>::template to_json_member<JsonValueType>(result.GetAllocator(), result, "manifest", manifest);
        JsonValueType::ValueType idJson;
        idJson.SetArray();
        idJson.Reserve(static_cast<rapidjson::SizeType>(normalIds.size() + namespaceIds.size() + 2), result.GetAllocator());
        for (const auto &item: normalIds) {
            JsonValueType::ValueType j;
            j.SetObject();
            serialization::Codec<decltype(item.first)>::template to_json_member<typename JsonValueType::ValueType>(result.GetAllocator(), j, "id", item.first);
            serialization::Codec<std::string>::template to_json_member<typename JsonValueType::ValueType>(result.GetAllocator(), j, "type", "normal");
            serialization::Codec<decltype(item.second)>::template to_json_member<typename JsonValueType::ValueType>(result.GetAllocator(), j, "content", item.second);
            idJson.PushBack(std::move(j), result.GetAllocator());
        }
        for (const auto &item: namespaceIds) {
            JsonValueType::ValueType j;
            j.SetObject();
            serialization::Codec<decltype(item.first)>::template to_json_member<typename JsonValueType::ValueType>(result.GetAllocator(), j, "id", item.first);
            serialization::Codec<std::string>::template to_json_member<typename JsonValueType::ValueType>(result.GetAllocator(), j, "type", "namespace");
            serialization::Codec<decltype(item.second)>::template to_json_member<typename JsonValueType::ValueType>(result.GetAllocator(), j, "content", item.second);
            idJson.PushBack(std::move(j), result.GetAllocator());
        }
        {
            JsonValueType::ValueType j;
            j.SetObject();
            serialization::Codec<std::string>::template to_json_member<typename JsonValueType::ValueType>(result.GetAllocator(), j, "id", "item");
            serialization::Codec<std::string>::template to_json_member<typename JsonValueType::ValueType>(result.GetAllocator(), j, "type", "item");
            serialization::Codec<decltype(itemIds)>::template to_json_member<typename JsonValueType::ValueType>(result.GetAllocator(), j, "content", itemIds);
            idJson.PushBack(std::move(j), result.GetAllocator());
        }
        {
            JsonValueType::ValueType j;
            j.SetObject();
            serialization::Codec<std::string>::template to_json_member<typename JsonValueType::ValueType>(result.GetAllocator(), j, "id", "block");
            serialization::Codec<std::string>::template to_json_member<typename JsonValueType::ValueType>(result.GetAllocator(), j, "type", "block");
            serialization::Codec<decltype(blockIds)>::template to_json_member<typename JsonValueType::ValueType>(result.GetAllocator(), j, "content", blockIds);
            idJson.PushBack(std::move(j), result.GetAllocator());
        }
        result.AddMember(rapidjson::GenericValue<rapidjson::UTF8<>>("id"), std::move(idJson), result.GetAllocator());
        serialization::Codec<decltype(jsonNodes)>::template to_json_member<typename JsonValueType::ValueType>(result.GetAllocator(), result, "json", jsonNodes);
        serialization::Codec<decltype(repeatNodeData)>::template to_json_member<typename JsonValueType::ValueType>(result.GetAllocator(), result, "repeat", repeatNodeData);
        serialization::Codec<decltype(commands)>::template to_json_member<typename JsonValueType::ValueType>(result.GetAllocator(), result, "command", commands);
        return result;
    }

#ifndef CHELPER_NO_FILESYSTEM
    void CPack::writeJsonToFile(const std::filesystem::path &path) const {
        writeJsonToFileWithCreateDirectory<rapidjson::GenericDocument<rapidjson::UTF8<>>>(path, toJson());
    }

    void CPack::writeBinToFile(const std::filesystem::path &path) const {
        std::filesystem::create_directories(path.parent_path());
        Profile::push("writing binary cpack to file: {}", FORMAT_ARG(path.string()));
        std::ofstream ostream(path, std::ios::binary);
        //manifest
        serialization::to_binary(ostream, manifest);
        //normal id
        serialization::to_binary(ostream, normalIds);
        //namespace id
        serialization::to_binary(ostream, namespaceIds);
        //item id
        serialization::to_binary(ostream, itemIds);
        //block id
        serialization::to_binary(ostream, blockIds);
        //json node
        serialization::to_binary(ostream, jsonNodes);
        //repeat node
        serialization::to_binary(ostream, repeatNodeData);
        //command
        serialization::to_binary(ostream, commands);

        ostream.close();
        Profile::pop();
    }
#endif

    std::shared_ptr<std::vector<std::shared_ptr<NormalId>>>
    CPack::getNormalId(const std::string &key) const {
        auto it = normalIds.find(key);
        if (it == normalIds.end()) [[unlikely]] {
#ifdef CHelperDebug
            SPDLOG_WARN(R"(fail to find normal ids by key: "{}")", FORMAT_ARG(key));
#endif
            return nullptr;
        }
        return it->second;
    }

    std::shared_ptr<std::vector<std::shared_ptr<NamespaceId>>>
    CPack::getNamespaceId(const std::string &key) const {
        if (key == "block") [[unlikely]] {
            return std::reinterpret_pointer_cast<std::vector<std::shared_ptr<NamespaceId>>>(blockIds->blockStateValues);
        } else if (key == "item") [[unlikely]] {
            return std::reinterpret_pointer_cast<std::vector<std::shared_ptr<NamespaceId>>>(itemIds);
        }
        auto it = namespaceIds.find(key);
        if (it == namespaceIds.end()) [[unlikely]] {
#ifdef CHelperDebug
            SPDLOG_WARN(R"(fail to find namespace ids by key: "{}")", FORMAT_ARG(key));
#endif
            return nullptr;
        }
        return it->second;
    }

}// namespace CHelper
