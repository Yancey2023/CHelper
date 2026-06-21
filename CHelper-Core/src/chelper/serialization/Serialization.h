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

#ifndef CHELPER_SERIALIZATION_H
#define CHELPER_SERIALIZATION_H

#include <chelper/node/CommandNode.h>
#include <chelper/node/NodeType.h>

#define CODEC_NODE(CodecType, ...) \
    CODEC_WITH_PARENT(CodecType, CHelper::Node::NodeSerializable, __VA_ARGS__)

#define CODEC_NODE_NONE(CodecType) \
    CODEC_NONE_WITH_PARENT(CodecType, CHelper::Node::NodeSerializable)

#define CHELPER_CODEC_NODE_TO_JSON(v1)                                                                      \
    case CHelper::Node::NodeTypeId::v1:                                                                     \
        NodeCodec<CHelper::Node::NodeTypeId::v1>::template to_json<JsonValueType>(allocator, jsonValue, t); \
        break;

#define CHELPER_CODEC_NODE_FROM_JSON(v1)                                                           \
    case CHelper::Node::NodeTypeId::v1:                                                            \
        NodeCodec<CHelper::Node::NodeTypeId::v1>::template from_json<JsonValueType>(jsonValue, t); \
        break;

#define CHELPER_CODEC_NODE_TO_BINARY(v1)                                                         \
    case CHelper::Node::NodeTypeId::v1:                                                          \
        NodeCodec<CHelper::Node::NodeTypeId::v1>::template to_binary<isNeedConvert>(ostream, t); \
        break;

#define CHELPER_CODEC_NODE_FROM_BINARY(v1)                                                         \
    case CHelper::Node::NodeTypeId::v1:                                                            \
        NodeCodec<CHelper::Node::NodeTypeId::v1>::template from_binary<isNeedConvert>(istream, t); \
        break;


CODEC(CHelper::Node::NodeSerializable, id, brief, description, isMustAfterSpace)

template<>
struct serialization::Codec<CHelper::Node::NodeWithType> : BaseCodec<CHelper::Node::NodeWithType> {
    using Type = CHelper::Node::NodeWithType;

    constexpr static bool enable = true;

    template<class JsonValueType>
    static void to_json(typename JsonValueType::AllocatorType &allocator,
                        JsonValueType &jsonValue,
                        const Type &t);

    template<class JsonValueType>
    static void from_json(const JsonValueType &jsonValue,
                          Type &t);

    template<bool isNeedConvert>
    static void to_binary(std::ostream &ostream,
                          const Type &t);

    template<bool isNeedConvert>
    static void from_binary(std::istream &istream,
                            Type &t);
};

template<>
struct serialization::Codec<CHelper::Node::FreeableNodeWithTypes> : BaseCodec<CHelper::Node::FreeableNodeWithTypes> {

    using Type = CHelper::Node::FreeableNodeWithTypes;

    constexpr static bool enable = true;

    template<class JsonValueType>
    static void to_json(typename JsonValueType::AllocatorType &allocator,
                        JsonValueType &jsonValue,
                        const Type &t) {
        Codec<decltype(t.nodes)>::template to_json<JsonValueType>(allocator, jsonValue, t.nodes);
    }

    template<class JsonValueType>
    static void from_json(const JsonValueType &jsonValue,
                          Type &t) {
        Codec<decltype(t.nodes)>::template from_json<JsonValueType>(jsonValue, t.nodes);
    }

    template<bool isNeedConvert>
    static void to_binary(std::ostream &ostream,
                          const Type &t) {
        Codec<decltype(t.nodes)>::template to_binary<isNeedConvert>(ostream, t.nodes);
    }

    template<bool isNeedConvert>
    static void from_binary(std::istream &istream,
                            Type &t) {
        Codec<decltype(t.nodes)>::template from_binary<isNeedConvert>(istream, t.nodes);
    }
};// namespace serialization

CODEC_REGISTER_JSON_KEY(CHelper::Node::NodePerCommand, name, description, syntax, node);
CODEC_REGISTER_JSON_KEY(CHelper::Node::NodeJsonElement, id, node, start);

template<>
struct serialization::Codec<CHelper::Node::NodeJsonElement> : BaseCodec<CHelper::Node::NodeJsonElement> {

    using Type = CHelper::Node::NodeJsonElement;

    constexpr static bool enable = true;

    template<class JsonValueType>
    static void to_json(typename JsonValueType::AllocatorType &allocator,
                        JsonValueType &jsonValue,
                        const Type &t) {
        jsonValue.SetObject();
        Codec<std::string>::template to_json_member<JsonValueType>(allocator, jsonValue, details::JsonKey<Type, typename JsonValueType::Ch>::id_(), t.id.value());
        Codec<decltype(t.nodes)>::template to_json_member<JsonValueType>(allocator, jsonValue, details::JsonKey<Type, typename JsonValueType::Ch>::node_(), t.nodes);
        Codec<decltype(t.startNodeId)>::template to_json_member<JsonValueType>(allocator, jsonValue, details::JsonKey<Type, typename JsonValueType::Ch>::start_(), t.startNodeId);
    }

    template<class JsonValueType>
    static void from_json(const JsonValueType &jsonValue,
                          Type &t) {
        if (!jsonValue.IsObject()) [[unlikely]] {
            throw exceptions::JsonSerializationTypeException("object", getJsonTypeStr(jsonValue.GetType()));
        }
        CHelper::Profile::push("loading id");
        t.id = std::make_optional<std::string>();
        Codec<std::string>::template from_json_member<JsonValueType>(jsonValue, details::JsonKey<Type, typename JsonValueType::Ch>::id_(), t.id.value());
        CHelper::Profile::next("loading nodes");
        Codec<decltype(t.nodes)>::template from_json_member<JsonValueType>(jsonValue, details::JsonKey<Type, typename JsonValueType::Ch>::node_(), t.nodes);
        CHelper::Profile::next("loading start nodes");
        Codec<decltype(t.startNodeId)>::template from_json_member<JsonValueType>(jsonValue, details::JsonKey<Type, typename JsonValueType::Ch>::start_(), t.startNodeId);
        CHelper::Profile::pop();
    }

    template<bool isNeedConvert>
    static void to_binary(std::ostream &ostream,
                          const Type &t) {
        Codec<std::string>::template to_binary<isNeedConvert>(ostream, t.id.value());
        Codec<decltype(t.nodes)>::template to_binary<isNeedConvert>(ostream, t.nodes);
        Codec<decltype(t.startNodeId)>::template to_binary<isNeedConvert>(ostream, t.startNodeId);
    }

    template<bool isNeedConvert>
    static void from_binary(std::istream &istream,
                            Type &t) {
        t.id = std::make_optional<std::string>();
        Codec<std::string>::template from_binary<isNeedConvert>(istream, t.id.value());
        Codec<decltype(t.nodes)>::template from_binary<isNeedConvert>(istream, t.nodes);
        Codec<decltype(t.startNodeId)>::template from_binary<isNeedConvert>(istream, t.startNodeId);
    }
};// namespace serialization

template<>
struct serialization::Codec<CHelper::Node::NodePerCommand> : BaseCodec<CHelper::Node::NodePerCommand> {

    using Type = CHelper::Node::NodePerCommand;

    constexpr static bool enable = true;

    template<class JsonValueType>
    static void to_json(typename JsonValueType::AllocatorType &allocator,
                        JsonValueType &jsonValue,
                        const Type &t) {
        jsonValue.SetObject();
        //name
        Codec<decltype(t.name)>::template to_json_member<JsonValueType>(allocator, jsonValue, details::JsonKey<Type, typename JsonValueType::Ch>::name_(), t.name);
        //description
        Codec<decltype(t.description)>::template to_json_member<JsonValueType>(allocator, jsonValue, details::JsonKey<Type, typename JsonValueType::Ch>::description_(), t.description);
        //syntax
        Codec<decltype(t.syntax)>::template to_json_member<JsonValueType>(allocator, jsonValue, details::JsonKey<Type, typename JsonValueType::Ch>::syntax_(), t.syntax);
        //node (as JSON object keyed by node id)
        typename JsonValueType::ValueType nodeObject;
        nodeObject.SetObject();
        nodeObject.MemberReserve(static_cast<rapidjson::SizeType>(t.wrappedNodes.size()), allocator);
        for (const auto &wrappedNode: t.wrappedNodes) {
            const auto &nodeId = wrappedNode.getNodeSerializable().id;
            if (!nodeId.has_value()) [[unlikely]] {
                continue;
            }
            typename JsonValueType::ValueType nodeValue;
            Codec<decltype(wrappedNode.innerNode)>::template to_json<typename JsonValueType::ValueType>(allocator, nodeValue, wrappedNode.innerNode);
            nodeValue.RemoveMember("id");
            typename JsonValueType::ValueType key(nodeId.value().c_str(), allocator);
            nodeObject.AddMember(std::move(key), std::move(nodeValue), allocator);
        }
        jsonValue.AddMember(typename JsonValueType::ValueType(details::JsonKey<Type, typename JsonValueType::Ch>::node_(), allocator), std::move(nodeObject), allocator);
    }

    template<class JsonValueType>
    static void from_json(const JsonValueType &jsonValue,
                          Type &t) {
        if (!jsonValue.IsObject()) [[unlikely]] {
            throw exceptions::JsonSerializationTypeException("object", getJsonTypeStr(jsonValue.GetType()));
        }
        //name
        CHelper::Profile::push("loading node name");
        Codec<decltype(t.name)>::template from_json_member<JsonValueType>(jsonValue, details::JsonKey<Type, typename JsonValueType::Ch>::name_(), t.name);
        //description
        CHelper::Profile::next("loading node description");
        Codec<decltype(t.description)>::template from_json_member<JsonValueType>(jsonValue, details::JsonKey<Type, typename JsonValueType::Ch>::description_(), t.description);
        //syntax
        CHelper::Profile::next("loading node syntax");
        Codec<decltype(t.syntax)>::template from_json_member<JsonValueType>(jsonValue, details::JsonKey<Type, typename JsonValueType::Ch>::syntax_(), t.syntax);
        //node (as JSON object keyed by node id)
        const typename JsonValueType::ConstMemberIterator nodeIter = jsonValue.FindMember(details::JsonKey<Type, typename JsonValueType::Ch>::node_());
        if (nodeIter == jsonValue.MemberEnd()) [[unlikely]] {
            throw exceptions::JsonSerializationKeyException(details::JsonKey<Type, typename JsonValueType::Ch>::node_());
        }
        if (!nodeIter->value.IsObject()) [[unlikely]] {
            throw exceptions::JsonSerializationTypeException("object", getJsonTypeStr(jsonValue.GetType()));
        }
        const auto &nodeObject = nodeIter->value;
        t.nodes.nodes.reserve(nodeObject.MemberCount());
        for (auto it = nodeObject.MemberBegin(); it != nodeObject.MemberEnd(); ++it) {
            std::string nodeId;
            Codec<std::string>::template from_json<typename JsonValueType::ValueType>(it->name, nodeId);
            t.nodes.nodes.emplace_back();
            Codec<CHelper::Node::NodeWithType>::template from_json<typename JsonValueType::ValueType>(it->value, t.nodes.nodes.back());
            reinterpret_cast<CHelper::Node::NodeSerializable *>(t.nodes.nodes.back().data)->id = std::make_optional(std::move(nodeId));
        }
        //build wrappedNodes/startNodes from syntax strings (only for JSON — binary stores pre-parsed)
        if (!t.syntax.empty()) {
            CHelper::Profile::next("building syntax trie");
            //id map: token string -> node definition
            std::vector<std::pair<std::string_view, CHelper::Node::NodeWithType *>> idMap;
            for (auto &item: t.nodes.nodes) {
                auto *serializable = reinterpret_cast<CHelper::Node::NodeSerializable *>(item.data);
                if (!serializable->id.has_value()) {
                    continue;
                }
                const auto &id = serializable->id.value();
                for (size_t start = 0, end; start < id.size(); start = end + 1) {
                    const size_t findStart = (id[start] == '[' || id[start] == '<') ? id.find(id[start] == '[' ? ']' : '>', start) + 1 : start;
                    end = std::min(id.find('|', findStart), id.size());
                    if (end > start) {
                        idMap.emplace_back(std::string_view(id.data() + start, end - start), &item);
                    }
                }
            }
            std::sort(idMap.begin(), idMap.end(), [](const auto &a, const auto &b) {
                return a.first.size() > b.first.size();
            });
            //flat trie: [0]=root, [i>0] maps to wrappedNodes[i-1]
            struct TrieNode {
                CHelper::Node::NodeWithType *definition = nullptr;
                std::vector<size_t> children;
                bool needsLf = false;
            };
            std::vector<TrieNode> trie(1);
            bool hasOptionalFirst = false;
            for (const auto &syntaxUtf16: t.syntax) {
                const std::string syntax = utf8::utf16to8(syntaxUtf16);
                size_t position = syntax.find(u' ');
                if (position == std::string::npos) {
                    continue;
                }
                hasOptionalFirst |= position + 1 < syntax.size() && syntax[position + 1] == u'[';
                size_t current = 0;
                while (position < syntax.size()) {
                    while (position < syntax.size() && syntax[position] == u' ') {
                        ++position;
                    }
                    if (position >= syntax.size()) {
                        break;
                    }
                    const size_t tokenStartPos = position;
                    CHelper::Node::NodeWithType *definition = nullptr;
                    for (auto &[tokenView, definitionView]: idMap) {
                        if (position + tokenView.size() <= syntax.size() &&
                            std::string_view(syntax.data() + position, tokenView.size()) == tokenView) {
                            definition = definitionView;
                            position += tokenView.size();
                            break;
                        }
                    }
                    if (!definition) [[unlikely]] {
                        throw std::runtime_error("unknown syntax token");
                    }
                    if (syntax[tokenStartPos] == u'[' && current != 0) {
                        trie[current].needsLf = true;
                    }
                    size_t childIndex = SIZE_MAX;
                    for (const auto child: trie[current].children) {
                        if (trie[child].definition == definition) {
                            childIndex = child;
                            break;
                        }
                    }
                    if (childIndex == SIZE_MAX) {
                        trie.emplace_back(definition);
                        childIndex = trie.size() - 1;
                        trie[current].children.push_back(childIndex);
                    }
                    current = childIndex;
                }
                if (current) {
                    trie[current].needsLf = true;
                }
            }
            //materialize wrappedNodes (trie[0] excluded)
            t.wrappedNodes.reserve(trie.size() - 1);
            for (size_t i = 1; i < trie.size(); ++i) {
                t.wrappedNodes.emplace_back(*trie[i].definition);
            }
            //connect nextNodes
            for (size_t i = 1; i < trie.size(); ++i) {
                auto &wrappedNode = t.wrappedNodes[i - 1];
                for (const auto child: trie[i].children) {
                    wrappedNode.nextNodes.push_back(&t.wrappedNodes[child - 1]);
                }
                if (trie[i].needsLf) {
                    wrappedNode.nextNodes.push_back(CHelper::Node::NodeLF::getInstance());
                }
            }
            //populate startNodes
            t.startNodes.clear();
            for (const auto child: trie[0].children) {
                t.startNodes.push_back(&t.wrappedNodes[child - 1]);
            }
            if (hasOptionalFirst) {
                t.startNodes.push_back(CHelper::Node::NodeLF::getInstance());
            }
        }
        CHelper::Profile::pop();
    }

    template<bool isNeedConvert>
    static void to_binary(std::ostream &ostream,
                          const Type &t) {
        //name
        Codec<decltype(t.name)>::template to_binary<isNeedConvert>(ostream, t.name);
        //description
        Codec<decltype(t.description)>::template to_binary<isNeedConvert>(ostream, t.description);
        //syntax
        Codec<decltype(t.syntax)>::template to_binary<isNeedConvert>(ostream, t.syntax);
        //node
        Codec<decltype(t.nodes)>::template to_binary<isNeedConvert>(ostream, t.nodes);
        //pre-parsed wrappedNodes graph (definition index + nextNodes indices)
        const uint32_t wrappedCount = static_cast<uint32_t>(t.wrappedNodes.size());
        Codec<uint32_t>::template to_binary<isNeedConvert>(ostream, wrappedCount);
        for (const auto &wrappedNode: t.wrappedNodes) {
            int32_t defIdx = -1;
            for (size_t i = 0; i < t.nodes.nodes.size(); ++i) {
                if (t.nodes.nodes[i].data == wrappedNode.innerNode.data) {
                    defIdx = static_cast<int32_t>(i);
                    break;
                }
            }
            Codec<int32_t>::template to_binary<isNeedConvert>(ostream, defIdx);
            const uint32_t nextCount = static_cast<uint32_t>(wrappedNode.nextNodes.size());
            Codec<uint32_t>::template to_binary<isNeedConvert>(ostream, nextCount);
            for (const auto *next: wrappedNode.nextNodes) {
                if (next == CHelper::Node::NodeLF::getInstance()) {
                    Codec<uint32_t>::template to_binary<isNeedConvert>(ostream, UINT32_MAX);
                } else {
                    const auto idx = static_cast<uint32_t>(next - t.wrappedNodes.data());
                    Codec<uint32_t>::template to_binary<isNeedConvert>(ostream, idx);
                }
            }
        }
        //startNodes indices
        const uint32_t startCount = static_cast<uint32_t>(t.startNodes.size());
        Codec<uint32_t>::template to_binary<isNeedConvert>(ostream, startCount);
        for (const auto *start: t.startNodes) {
            if (start == CHelper::Node::NodeLF::getInstance()) {
                Codec<uint32_t>::template to_binary<isNeedConvert>(ostream, UINT32_MAX);
            } else {
                const auto idx = static_cast<uint32_t>(start - t.wrappedNodes.data());
                Codec<uint32_t>::template to_binary<isNeedConvert>(ostream, idx);
            }
        }
    }

    template<bool isNeedConvert>
    static void from_binary(std::istream &istream,
                            Type &t) {
        //name
        Codec<decltype(t.name)>::template from_binary<isNeedConvert>(istream, t.name);
        if (t.name.empty()) [[unlikely]] {
            throw std::runtime_error("command size cannot be zero");
        }
        //description
        Codec<decltype(t.description)>::template from_binary<isNeedConvert>(istream, t.description);
        //syntax (preserved for JSON round-trip)
        Codec<decltype(t.syntax)>::template from_binary<isNeedConvert>(istream, t.syntax);
        //node definitions
        Codec<decltype(t.nodes)>::template from_binary<isNeedConvert>(istream, t.nodes);
        //pre-parsed wrappedNodes graph
        uint32_t wrappedCount;
        Codec<uint32_t>::template from_binary<isNeedConvert>(istream, wrappedCount);
        struct WrappedEntry {
            int32_t defIdx;
            std::vector<uint32_t> nextIndices;
        };
        std::vector<WrappedEntry> entries(wrappedCount);
        for (uint32_t i = 0; i < wrappedCount; ++i) {
            Codec<int32_t>::template from_binary<isNeedConvert>(istream, entries[i].defIdx);
            uint32_t nextCount;
            Codec<uint32_t>::template from_binary<isNeedConvert>(istream, nextCount);
            entries[i].nextIndices.resize(nextCount);
            for (uint32_t j = 0; j < nextCount; ++j) {
                Codec<uint32_t>::template from_binary<isNeedConvert>(istream, entries[i].nextIndices[j]);
            }
        }
        //startNodes indices
        uint32_t startCount;
        Codec<uint32_t>::template from_binary<isNeedConvert>(istream, startCount);
        std::vector<uint32_t> startIndices(startCount);
        for (uint32_t i = 0; i < startCount; ++i) {
            Codec<uint32_t>::template from_binary<isNeedConvert>(istream, startIndices[i]);
        }
        //build wrappedNodes (reserve to prevent reallocation during pointer fixup)
        t.wrappedNodes.reserve(wrappedCount);
        for (uint32_t i = 0; i < wrappedCount; ++i) {
            if (entries[i].defIdx < 0 || static_cast<size_t>(entries[i].defIdx) >= t.nodes.nodes.size()) [[unlikely]] {
                throw std::runtime_error("invalid node definition index");
            }
            t.wrappedNodes.emplace_back(t.nodes.nodes[entries[i].defIdx]);
        }
        //resolve nextNodes pointers
        for (uint32_t i = 0; i < wrappedCount; ++i) {
            auto &wrappedNode = t.wrappedNodes[i];
            for (auto targetIdx: entries[i].nextIndices) {
                if (targetIdx == UINT32_MAX) {
                    wrappedNode.nextNodes.push_back(CHelper::Node::NodeLF::getInstance());
                } else {
                    if (targetIdx >= wrappedCount) [[unlikely]] {
                        throw std::runtime_error("invalid wrapped node index");
                    }
                    wrappedNode.nextNodes.push_back(&t.wrappedNodes[targetIdx]);
                }
            }
        }
        //resolve startNodes pointers
        t.startNodes.clear();
        t.startNodes.reserve(startCount);
        for (auto idx: startIndices) {
            if (idx == UINT32_MAX) {
                t.startNodes.push_back(CHelper::Node::NodeLF::getInstance());
            } else {
                if (idx >= wrappedCount) [[unlikely]] {
                    throw std::runtime_error("invalid start node index");
                }
                t.startNodes.push_back(&t.wrappedNodes[idx]);
            }
        }
    }
};

class NodeTypeHelper {
public:
    template<class JsonValueType>
    static void to_json(CHelper::Node::NodeTypeId::NodeTypeId id,
                        typename JsonValueType::AllocatorType &allocator,
                        JsonValueType &jsonValue,
                        const CHelper::Node::NodeWithType &t);

    template<class JsonValueType>
    static void from_json(CHelper::Node::NodeTypeId::NodeTypeId id,
                          const JsonValueType &jsonValue,
                          CHelper::Node::NodeWithType &t);

    template<bool isNeedConvert>
    static void to_binary(CHelper::Node::NodeTypeId::NodeTypeId id,
                          std::ostream &ostream,
                          const CHelper::Node::NodeWithType &t);

    template<bool isNeedConvert>
    static void from_binary(CHelper::Node::NodeTypeId::NodeTypeId id,
                            std::istream &istream,
                            CHelper::Node::NodeWithType &t);
};

static CHelper::Node::NodeCreateStage::NodeCreateStage currentCreateStage;

template<CHelper::Node::NodeTypeId::NodeTypeId nodeTypeId>
struct NodeCodec {

    using Type = typename CHelper::Node::NodeTypeDetail<nodeTypeId>::Type;
    static constexpr auto nodeCreateStage = CHelper::Node::NodeTypeDetail<nodeTypeId>::nodeCreateStage;
    static constexpr auto name = CHelper::Node::NodeTypeDetail<nodeTypeId>::name;
    static constexpr auto isMustAfterSpace = CHelper::Node::NodeTypeDetail<nodeTypeId>::isMustAfterSpace;

    template<class JsonValueType>
    static void to_json(typename JsonValueType::AllocatorType &allocator,
                        JsonValueType &jsonValue,
                        const CHelper::Node::NodeWithType &t) {
        if constexpr (serialization::Codec<Type>::enable) {
            serialization::Codec<Type>::template to_json<JsonValueType>(allocator, jsonValue, *static_cast<const Type *>(t.data));
        }
    }

    template<class JsonValueType>
    static void from_json(const JsonValueType &jsonValue,
                          CHelper::Node::NodeWithType &t) {
        if constexpr (!serialization::Codec<Type>::enable || nodeCreateStage.empty()) {
            CHelper::Profile::push("unknown node type -> {}", FORMAT_ARG(name));
            throw std::runtime_error("unknown node type");
        } else {
            if (std::find(nodeCreateStage.begin(), nodeCreateStage.end(), currentCreateStage) == nodeCreateStage.end()) {
                CHelper::Profile::push("unknown node type -> {}", FORMAT_ARG(name));
                throw std::runtime_error("unknown node type");
            }
            Type *node = new Type();
            serialization::Codec<Type>::template from_json<JsonValueType>(jsonValue, *node);
            if (!node->isMustAfterSpace.has_value()) [[unlikely]] {
                node->isMustAfterSpace = isMustAfterSpace;
            }
            t.nodeTypeId = nodeTypeId;
            t.data = node;
        }
    }

    template<bool isNeedConvert>
    static void to_binary(std::ostream &ostream,
                          const CHelper::Node::NodeWithType &t) {
        if constexpr (serialization::Codec<Type>::enable) {
            serialization::Codec<Type>::template to_binary<isNeedConvert>(ostream, *static_cast<const Type *>(t.data));
        }
    }

    template<bool isNeedConvert>
    static void from_binary(std::istream &istream,
                            CHelper::Node::NodeWithType &t) {
        if constexpr (!serialization::Codec<Type>::enable || nodeCreateStage.empty()) {
            CHelper::Profile::push("unknown node type -> {}", FORMAT_ARG(name));
            throw std::runtime_error("unknown node type");
        } else {
            if (std::find(nodeCreateStage.begin(), nodeCreateStage.end(), currentCreateStage) == nodeCreateStage.end()) {
                CHelper::Profile::push("unknown node type -> {}", FORMAT_ARG(name));
                throw std::runtime_error("unknown node type");
            }
            Type *node = new Type();
            serialization::Codec<Type>::template from_binary<isNeedConvert>(istream, *node);
            if (!node->isMustAfterSpace.has_value()) [[unlikely]] {
                node->isMustAfterSpace = isMustAfterSpace;
            }
            t.nodeTypeId = nodeTypeId;
            t.data = node;
        }
    }
};

CODEC_ENUM(CHelper::Node::NodeBlockType::NodeBlockType, uint8_t);
CODEC_NODE(CHelper::Node::NodeBlock, nodeBlockType)
CODEC_NODE_NONE(CHelper::Node::NodeCommand)
CODEC_NODE_NONE(CHelper::Node::NodeCommandName)
CODEC_NODE(CHelper::Node::NodeIntegerWithUnit, units)
CODEC_ENUM(CHelper::Node::NodeItemType::NodeItemType, uint8_t);
CODEC_NODE(CHelper::Node::NodeItem, nodeItemType)
CODEC_NODE(CHelper::Node::NodeJson, key)
CODEC_NODE(CHelper::Node::NodeNamespaceId, key, ignoreError, contents)
CODEC_NODE(CHelper::Node::NodeNormalId, key, ignoreError, contents)
CODEC_NODE_NONE(CHelper::Node::NodePosition)
CODEC_NODE_NONE(CHelper::Node::NodeRange)
CODEC_NODE(CHelper::Node::NodeRelativeFloat, canUseCaretNotation)
CODEC(CHelper::Node::RepeatData, id, breakNodes, repeatNodes, isEnd)
CODEC_NODE(CHelper::Node::NodeRepeat, key)
CODEC_NODE(CHelper::Node::NodeString, canContainSpace, ignoreLater)
CODEC_NODE(CHelper::Node::NodeTargetSelector, isMustPlayer, isMustNPC, isOnlyOne, isWildcard)
CODEC_NODE(CHelper::Node::NodeText, data)
CODEC_NODE(CHelper::Node::NodeBoolean, descriptionTrue, descriptionFalse)
CODEC_NODE(CHelper::Node::NodeJsonBoolean, descriptionTrue, descriptionFalse)
CODEC_NODE(CHelper::Node::NodeFloat, min, max)
CODEC_NODE(CHelper::Node::NodeInteger, min, max)
CODEC_NODE(CHelper::Node::NodeJsonInteger, min, max)
CODEC_NODE(CHelper::Node::NodeJsonFloat, min, max)
CODEC_NODE(CHelper::Node::NodeJsonList, data)
CODEC_NODE_NONE(CHelper::Node::NodeJsonNull)
CODEC_NODE(CHelper::Node::NodeJsonEntry, key, value)
CODEC_NODE(CHelper::Node::NodeJsonObject, data)
CODEC_NODE(CHelper::Node::NodeJsonString, data)

template<class JsonValueType>
void NodeTypeHelper::to_json(CHelper::Node::NodeTypeId::NodeTypeId id,
                             typename JsonValueType::AllocatorType &allocator,
                             JsonValueType &jsonValue,
                             const CHelper::Node::NodeWithType &t) {
    switch (id) {
        CODEC_PASTE(CHELPER_CODEC_NODE_TO_JSON, CHELPER_NODE_TYPES)
        default:
            CHELPER_UNREACHABLE();
    }
}

template<class JsonValueType>
void NodeTypeHelper::from_json(CHelper::Node::NodeTypeId::NodeTypeId id,
                               const JsonValueType &jsonValue,
                               CHelper::Node::NodeWithType &t) {
    switch (id) {
        CODEC_PASTE(CHELPER_CODEC_NODE_FROM_JSON, CHELPER_NODE_TYPES)
        default:
            CHELPER_UNREACHABLE();
    }
}

template<bool isNeedConvert>
void NodeTypeHelper::to_binary(CHelper::Node::NodeTypeId::NodeTypeId id,
                               std::ostream &ostream,
                               const CHelper::Node::NodeWithType &t) {
    switch (id) {
        CODEC_PASTE(CHELPER_CODEC_NODE_TO_BINARY, CHELPER_NODE_TYPES)
        default:
            CHELPER_UNREACHABLE();
    }
}

template<bool isNeedConvert>
void NodeTypeHelper::from_binary(CHelper::Node::NodeTypeId::NodeTypeId id,
                                 std::istream &istream,
                                 CHelper::Node::NodeWithType &t) {
    switch (id) {
        CODEC_PASTE(CHELPER_CODEC_NODE_FROM_BINARY, CHELPER_NODE_TYPES)
        default:
            throw std::runtime_error("unknown node type");
    }
}

CODEC_ENUM(CHelper::Node::NodeTypeId::NodeTypeId, uint8_t);

template<class JsonValueType>
void serialization::Codec<CHelper::Node::NodeWithType>::to_json(
        typename JsonValueType::AllocatorType &allocator,
        JsonValueType &jsonValue,
        const Type &t) {
    std::string nodeIdName = CHelper::Node::getNodeTypeName(t.nodeTypeId);
    NodeTypeHelper::template to_json<JsonValueType>(t.nodeTypeId, allocator, jsonValue, t);
    assert(jsonValue.IsObject());
    Codec<decltype(nodeIdName)>::template to_json_member<JsonValueType>(allocator, jsonValue, "type", nodeIdName);
}

template<class JsonValueType>
void serialization::Codec<CHelper::Node::NodeWithType>::from_json(
        const JsonValueType &jsonValue,
        Type &t) {
    if (!jsonValue.IsObject()) [[unlikely]] {
        throw exceptions::JsonSerializationTypeException("object", getJsonTypeStr(jsonValue.GetType()));
    }
    CHelper::Profile::push("loading type");
    std::string type;
    Codec<decltype(type)>::template from_json_member<JsonValueType>(jsonValue, "type", type);
    std::optional<std::u16string> id;
    Codec<decltype(id)>::template from_json_member<JsonValueType>(jsonValue, "id", id);
    CHelper::Profile::next("loading node {}", FORMAT_ARG(type));
    if (id.has_value()) [[likely]] {
        CHelper::Profile::next("loading node {} with id \"{}\"", FORMAT_ARG(type), FORMAT_ARG(utf8::utf16to8(id.value())));
    } else {
        CHelper::Profile::next("loading node {} without id", FORMAT_ARG(type));
    }
    std::optional<CHelper::Node::NodeTypeId::NodeTypeId> nodeTypeId = CHelper::Node::getNodeTypeIdByName(type);
    if (!nodeTypeId.has_value()) [[unlikely]] {
        CHelper::Profile::next("unknown node type -> {}", FORMAT_ARG(type));
        throw std::runtime_error("unknown node type");
    }
    NodeTypeHelper::template from_json<JsonValueType>(nodeTypeId.value(), jsonValue, t);
    CHelper::Profile::pop();
}

template<bool isNeedConvert>
void serialization::Codec<CHelper::Node::NodeWithType>::to_binary(
        std::ostream &ostream,
        const Type &t) {
    Codec<decltype(t.nodeTypeId)>::template to_binary<isNeedConvert>(ostream, t.nodeTypeId);
    NodeTypeHelper::template to_binary<isNeedConvert>(t.nodeTypeId, ostream, t);
}

template<bool isNeedConvert>
void serialization::Codec<CHelper::Node::NodeWithType>::from_binary(
        std::istream &istream,
        Type &t) {
    CHelper::Node::NodeTypeId::NodeTypeId nodeTypeId;
    Codec<decltype(nodeTypeId)>::template from_binary<isNeedConvert>(istream, nodeTypeId);
    NodeTypeHelper::template from_binary<isNeedConvert>(nodeTypeId, istream, t);
}

#endif//CHELPER_SERIALIZATION_H
