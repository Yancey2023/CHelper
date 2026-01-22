//
// Created by Yancey on 2023/12/10.
//

#pragma once

#ifndef CHELPER_NODETYPE_H
#define CHELPER_NODETYPE_H

#include <chelper/node/CommandNode.h>
#include <pch.h>

namespace CHelper {

    class CPack;

    namespace Node {

        class NodeSerializable;

        namespace NodeCreateStage {
            enum NodeCreateStage : uint8_t {
                NONE,
                NODE_TYPE,
                JSON_NODE,
                REPEAT_NODE,
                COMMAND_PARAM_NODE
            };
        }// namespace NodeCreateStage

        template<NodeTypeId::NodeTypeId nodeTypeId>
        struct NodeTypeDetail {
            static constexpr auto name = "UNKNOWN";
        };

        struct CommandParamNodeTypeDetail {
            static constexpr std::array<NodeCreateStage::NodeCreateStage, 3> nodeCreateStage = {
                    NodeCreateStage::JSON_NODE,
                    NodeCreateStage::REPEAT_NODE,
                    NodeCreateStage::COMMAND_PARAM_NODE};
            static constexpr bool isMustAfterSpace = true;
        };

        struct JsonNodeTypeDetail {
            static constexpr std::array<NodeCreateStage::NodeCreateStage, 1> nodeCreateStage = {
                    NodeCreateStage::JSON_NODE};
            static constexpr bool isMustAfterSpace = false;
        };

        struct UnserializableNodeTypeDetail {
            static constexpr std::array<NodeCreateStage::NodeCreateStage, 0> nodeCreateStage = {};
            static constexpr bool isMustAfterSpace = false;
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::WRAPPED> : UnserializableNodeTypeDetail {
            using Type = NodeWrapped;
            static_assert(Type::nodeTypeId == NodeTypeId::WRAPPED, "nodTypeId not equal");
            static constexpr auto name = "WRAPPED";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::BLOCK> : CommandParamNodeTypeDetail {
            using Type = NodeBlock;
            static_assert(Type::nodeTypeId == NodeTypeId::BLOCK, "nodTypeId not equal");
            static constexpr auto name = "BLOCK";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::BOOLEAN> : CommandParamNodeTypeDetail {
            using Type = NodeBoolean;
            static_assert(Type::nodeTypeId == NodeTypeId::BOOLEAN, "nodTypeId not equal");
            static constexpr auto name = "BOOLEAN";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::COMMAND> : CommandParamNodeTypeDetail {
            using Type = NodeCommand;
            static_assert(Type::nodeTypeId == NodeTypeId::COMMAND, "nodTypeId not equal");
            static constexpr auto name = "COMMAND";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::COMMAND_NAME> : CommandParamNodeTypeDetail {
            using Type = NodeCommandName;
            static_assert(Type::nodeTypeId == NodeTypeId::COMMAND_NAME, "nodTypeId not equal");
            static constexpr auto name = "COMMAND_NAME";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::FLOAT> : CommandParamNodeTypeDetail {
            using Type = NodeFloat;
            static_assert(Type::nodeTypeId == NodeTypeId::FLOAT, "nodTypeId not equal");
            static constexpr auto name = "FLOAT";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::INTEGER> : CommandParamNodeTypeDetail {
            using Type = NodeInteger;
            static_assert(Type::nodeTypeId == NodeTypeId::INTEGER, "nodTypeId not equal");
            static constexpr auto name = "INTEGER";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::INTEGER_WITH_UNIT> : CommandParamNodeTypeDetail {
            using Type = NodeIntegerWithUnit;
            static_assert(Type::nodeTypeId == NodeTypeId::INTEGER_WITH_UNIT, "nodTypeId not equal");
            static constexpr auto name = "INTEGER_WITH_UNIT";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::ITEM> : CommandParamNodeTypeDetail {
            using Type = NodeItem;
            static_assert(Type::nodeTypeId == NodeTypeId::ITEM, "nodTypeId not equal");
            static constexpr auto name = "ITEM";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::LF> : UnserializableNodeTypeDetail {
            using Type = NodeLF;
            static_assert(Type::nodeTypeId == NodeTypeId::LF, "nodTypeId not equal");
            static constexpr auto name = "LF";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::NAMESPACE_ID> : CommandParamNodeTypeDetail {
            using Type = NodeNamespaceId;
            static_assert(Type::nodeTypeId == NodeTypeId::NAMESPACE_ID, "nodTypeId not equal");
            static constexpr auto name = "NAMESPACE_ID";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::NORMAL_ID> : CommandParamNodeTypeDetail {
            using Type = NodeNormalId;
            static_assert(Type::nodeTypeId == NodeTypeId::NORMAL_ID, "nodTypeId not equal");
            static constexpr auto name = "NORMAL_ID";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::PER_COMMAND> : UnserializableNodeTypeDetail {
            using Type = NodePerCommand;
            static_assert(Type::nodeTypeId == NodeTypeId::PER_COMMAND, "nodTypeId not equal");
            static constexpr auto name = "PER_COMMAND";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::POSITION> : CommandParamNodeTypeDetail {
            using Type = NodePosition;
            static_assert(Type::nodeTypeId == NodeTypeId::POSITION, "nodTypeId not equal");
            static constexpr auto name = "POSITION";
            static constexpr bool isMustAfterSpace = false;
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::RELATIVE_FLOAT> : CommandParamNodeTypeDetail {
            using Type = NodeRelativeFloat;
            static_assert(Type::nodeTypeId == NodeTypeId::RELATIVE_FLOAT, "nodTypeId not equal");
            static constexpr auto name = "RELATIVE_FLOAT";
            static constexpr bool isMustAfterSpace = false;
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::REPEAT> : CommandParamNodeTypeDetail {
            using Type = NodeRepeat;
            static_assert(Type::nodeTypeId == NodeTypeId::REPEAT, "nodTypeId not equal");
            static constexpr auto name = "REPEAT";
            static constexpr bool isMustAfterSpace = false;
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::STRING> : CommandParamNodeTypeDetail {
            using Type = NodeString;
            static_assert(Type::nodeTypeId == NodeTypeId::STRING, "nodTypeId not equal");
            static constexpr auto name = "STRING";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::TARGET_SELECTOR> : CommandParamNodeTypeDetail {
            using Type = NodeTargetSelector;
            static_assert(Type::nodeTypeId == NodeTypeId::TARGET_SELECTOR, "nodTypeId not equal");
            static constexpr auto name = "TARGET_SELECTOR";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::TEXT> : CommandParamNodeTypeDetail {
            using Type = NodeText;
            static_assert(Type::nodeTypeId == NodeTypeId::TEXT, "nodTypeId not equal");
            static constexpr auto name = "TEXT";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::RANGE> : CommandParamNodeTypeDetail {
            using Type = NodeRange;
            static_assert(Type::nodeTypeId == NodeTypeId::RANGE, "nodTypeId not equal");
            static constexpr auto name = "RANGE";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::JSON> : CommandParamNodeTypeDetail {
            using Type = NodeJson;
            static_assert(Type::nodeTypeId == NodeTypeId::JSON, "nodTypeId not equal");
            static constexpr auto name = "JSON";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::JSON_BOOLEAN> : JsonNodeTypeDetail {
            using Type = NodeJsonBoolean;
            static_assert(Type::nodeTypeId == NodeTypeId::JSON_BOOLEAN, "nodTypeId not equal");
            static constexpr auto name = "JSON_BOOLEAN";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::JSON_ELEMENT> : UnserializableNodeTypeDetail {
            using Type = NodeJsonElement;
            static_assert(Type::nodeTypeId == NodeTypeId::JSON_ELEMENT, "nodTypeId not equal");
            static constexpr auto name = "JSON_ELEMENT";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::JSON_ENTRY> : UnserializableNodeTypeDetail {
            using Type = NodeJsonEntry;
            static_assert(Type::nodeTypeId == NodeTypeId::JSON_ENTRY, "nodTypeId not equal");
            static constexpr auto name = "JSON_ENTRY";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::JSON_FLOAT> : JsonNodeTypeDetail {
            using Type = NodeJsonFloat;
            static_assert(Type::nodeTypeId == NodeTypeId::JSON_FLOAT, "nodTypeId not equal");
            static constexpr auto name = "JSON_FLOAT";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::JSON_INTEGER> : JsonNodeTypeDetail {
            using Type = NodeJsonInteger;
            static_assert(Type::nodeTypeId == NodeTypeId::JSON_INTEGER, "nodTypeId not equal");
            static constexpr auto name = "JSON_INTEGER";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::JSON_LIST> : JsonNodeTypeDetail {
            using Type = NodeJsonList;
            static_assert(Type::nodeTypeId == NodeTypeId::JSON_LIST, "nodTypeId not equal");
            static constexpr auto name = "JSON_LIST";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::JSON_NULL> : JsonNodeTypeDetail {
            using Type = NodeJsonNull;
            static_assert(Type::nodeTypeId == NodeTypeId::JSON_NULL, "nodTypeId not equal");
            static constexpr auto name = "JSON_NULL";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::JSON_OBJECT> : JsonNodeTypeDetail {
            using Type = NodeJsonObject;
            static_assert(Type::nodeTypeId == NodeTypeId::JSON_OBJECT, "nodTypeId not equal");
            static constexpr auto name = "JSON_OBJECT";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::JSON_STRING> : JsonNodeTypeDetail {
            using Type = NodeJsonString;
            static_assert(Type::nodeTypeId == NodeTypeId::JSON_STRING, "nodTypeId not equal");
            static constexpr auto name = "JSON_STRING";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::AND> : UnserializableNodeTypeDetail {
            using Type = NodeAnd;
            static_assert(Type::nodeTypeId == NodeTypeId::AND, "nodTypeId not equal");
            static constexpr auto name = "AND";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::ANY> : UnserializableNodeTypeDetail {
            using Type = NodeAny;
            static_assert(Type::nodeTypeId == NodeTypeId::ANY, "nodTypeId not equal");
            static constexpr auto name = "ANY";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::ENTRY> : UnserializableNodeTypeDetail {
            using Type = NodeEntry;
            static_assert(Type::nodeTypeId == NodeTypeId::ENTRY, "nodTypeId not equal");
            static constexpr auto name = "ENTRY";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::EQUAL_ENTRY> : UnserializableNodeTypeDetail {
            using Type = NodeEqualEntry;
            static_assert(Type::nodeTypeId == NodeTypeId::EQUAL_ENTRY, "nodTypeId not equal");
            static constexpr auto name = "EQUAL_ENTRY";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::LIST> : UnserializableNodeTypeDetail {
            using Type = NodeList;
            static_assert(Type::nodeTypeId == NodeTypeId::LIST, "nodTypeId not equal");
            static constexpr auto name = "LIST";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::OR> : UnserializableNodeTypeDetail {
            using Type = NodeOr;
            static_assert(Type::nodeTypeId == NodeTypeId::OR, "nodTypeId not equal");
            static constexpr auto name = "OR";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::SINGLE_SYMBOL> : UnserializableNodeTypeDetail {
            using Type = NodeSingleSymbol;
            static_assert(Type::nodeTypeId == NodeTypeId::SINGLE_SYMBOL, "nodTypeId not equal");
            static constexpr auto name = "SINGLE_SYMBOL";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::OPTIONAL> : UnserializableNodeTypeDetail {
            using Type = NodeOptional;
            static_assert(Type::nodeTypeId == NodeTypeId::OPTIONAL, "nodTypeId not equal");
            static constexpr auto name = "OPTIONAL";
        };

        const char *getNodeTypeName(const CHelper::Node::NodeTypeId::NodeTypeId id);

        std::optional<NodeTypeId::NodeTypeId> getNodeTypeIdByName(const std::string_view &id);

    }// namespace Node

}// namespace CHelper

#endif//CHELPER_NODETYPE_H
