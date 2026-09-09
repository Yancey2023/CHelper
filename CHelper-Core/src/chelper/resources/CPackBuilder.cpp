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

#include <chelper/resources/CPackBuilder.h>
#include <chelper/serialization/Serialization.h>
#include <chelper/util/JsonUtil.h>
#include <stdexcept>

namespace CHelper {

    void CPackBuilder::applyManifest(const JsonValueType &j) {
        serialization::Codec<decltype(manifest)>::template from_json<JsonValueType>(j, manifest);
    }

    void CPackBuilder::applyId(const JsonValueType &j) {
        std::u16string type;
        serialization::Codec<decltype(type)>::template from_json_member<JsonValueType>(j, "type", type);
        if (type == u"normal") {
            std::string id;
            serialization::Codec<decltype(id)>::template from_json_member<JsonValueType>(j, "id", id);
            std::shared_ptr<std::vector<std::shared_ptr<NormalId>>> content;
            serialization::Codec<decltype(content)>::template from_json_member<JsonValueType>(j, "content", content);
            normalIds.emplace(std::move(id), std::move(content));
        } else if (type == u"namespace") {
            std::string id;
            serialization::Codec<decltype(id)>::template from_json_member<JsonValueType>(j, "id", id);
            std::shared_ptr<std::vector<std::shared_ptr<NamespaceId>>> content;
            serialization::Codec<decltype(content)>::template from_json_member<JsonValueType>(j, "content", content);
            namespaceIds.emplace(std::move(id), std::move(content));
        } else if (type == u"block") {
            serialization::Codec<decltype(blockIds)>::template from_json_member<JsonValueType>(j, "content", blockIds);
        } else if (type == u"item") {
            serialization::Codec<decltype(itemIds)>::template from_json_member<JsonValueType>(j, "content", itemIds);
        } else {
            Profile::push("unknown id type -> {}", FORMAT_ARG(utf8::utf16to8(type)));
            throw std::runtime_error("unknown id type");
        }
    }

    void CPackBuilder::applyJson(const JsonValueType &j) {
        Node::NodeJsonElement item;
        serialization::Codec<decltype(item)>::template from_json<JsonValueType>(j, item);
        jsonNodes.push_back(std::move(item));
    }

    void CPackBuilder::applyRepeat(const JsonValueType &j) {
        Node::RepeatData item;
        serialization::Codec<decltype(item)>::template from_json<JsonValueType>(j, item);
        repeatNodeData.push_back(std::move(item));
    }

    void CPackBuilder::applyCommand(const JsonValueType &j) {
        Node::NodePerCommand item;
        serialization::Codec<decltype(item)>::template from_json<JsonValueType>(j, item);
        commands.push_back(std::move(item));
    }

    void CPackBuilder::applyFile(const std::string &relPath, const std::vector<uint8_t> &bytes) {
        // 规范化相对路径：统一 `/`，去掉前导分隔符
        std::string p = relPath;
        for (char &ch: p) {
            if (ch == '\\') {
                ch = '/';
            }
        }
        while (!p.empty() && p.front() == '/') {
            p.erase(p.begin());
        }
        if (p.empty()) {
            throw std::runtime_error("empty file path in pack");
        }

        rapidjson::GenericDocument<rapidjson::UTF8<>> doc;
        if (!JsonUtil::parseJsonWithComments(doc, reinterpret_cast<const char *>(bytes.data()), bytes.size()) || !doc.IsObject()) {
            throw std::runtime_error("invalid json in pack file: " + relPath);
        }

        const Node::NodeCreateStage::NodeCreateStage prev = currentCreateStage;
        if (p == "manifest.json") {
            currentCreateStage = Node::NodeCreateStage::NONE;
            applyManifest(doc);
        } else if (p.rfind("command/", 0) == 0) {
            currentCreateStage = Node::NodeCreateStage::COMMAND_PARAM_NODE;
            applyCommand(doc);
        } else if (p.rfind("id/", 0) == 0) {
            applyId(doc);
        } else if (p.rfind("json/", 0) == 0) {
            currentCreateStage = Node::NodeCreateStage::JSON_NODE;
            applyJson(doc);
        } else if (p.rfind("repeat/", 0) == 0) {
            currentCreateStage = Node::NodeCreateStage::REPEAT_NODE;
            applyRepeat(doc);
        } else {
            // 非装载对象（如 text/ 等数据文件）：忽略，供上层按需读取，不进 CPack
            currentCreateStage = prev;
            return;
        }
        currentCreateStage = prev;
    }

    std::shared_ptr<const CPack> CPackBuilder::build() {
        auto c = std::shared_ptr<CPack>(new CPack());
        c->manifest = std::move(manifest);
        c->normalIds = std::move(normalIds);
        c->namespaceIds = std::move(namespaceIds);
        c->blockIds = std::move(blockIds);
        c->itemIds = std::move(itemIds);
        c->jsonNodes = std::move(jsonNodes);
        c->repeatNodeData = std::move(repeatNodeData);
        c->selectorVariables = std::move(selectorVariables);
        c->selectorArguments = std::move(selectorArguments);
        c->commandNameSources = std::move(commandNameSources);
        c->commands = std::make_shared<std::vector<Node::NodePerCommand>>(std::move(commands));
        c->afterApply();
        return c;
    }

}// namespace CHelper
