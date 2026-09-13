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

#include <chelper/node/CommandNode.h>
#include <chelper/util/TypeList.h>
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
                COMMAND_PARAM_NODE,
                GRAMMAR_NODE
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

        struct GrammarNodeTypeDetail {
            static constexpr std::array<NodeCreateStage::NodeCreateStage, 1> nodeCreateStage = {
                    NodeCreateStage::GRAMMAR_NODE};
            static constexpr bool isMustAfterSpace = false;
        };

        struct CommandParamGrammarNodeTypeDetail {
            static constexpr std::array<NodeCreateStage::NodeCreateStage, 4> nodeCreateStage = {
                    NodeCreateStage::JSON_NODE,
                    NodeCreateStage::REPEAT_NODE,
                    NodeCreateStage::COMMAND_PARAM_NODE,
                    NodeCreateStage::GRAMMAR_NODE};
            static constexpr bool isMustAfterSpace = true;
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
        struct NodeTypeDetail<NodeTypeId::BOOLEAN> : CommandParamGrammarNodeTypeDetail {
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
        struct NodeTypeDetail<NodeTypeId::FLOAT> : CommandParamGrammarNodeTypeDetail {
            using Type = NodeFloat;
            static_assert(Type::nodeTypeId == NodeTypeId::FLOAT, "nodTypeId not equal");
            static constexpr auto name = "FLOAT";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::INTEGER> : CommandParamGrammarNodeTypeDetail {
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
        struct NodeTypeDetail<NodeTypeId::NAMESPACE_ID> : CommandParamGrammarNodeTypeDetail {
            using Type = NodeNamespaceId;
            static_assert(Type::nodeTypeId == NodeTypeId::NAMESPACE_ID, "nodTypeId not equal");
            static constexpr auto name = "NAMESPACE_ID";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::NORMAL_ID> : CommandParamGrammarNodeTypeDetail {
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
        struct NodeTypeDetail<NodeTypeId::RELATIVE_FLOAT> : CommandParamGrammarNodeTypeDetail {
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
        struct NodeTypeDetail<NodeTypeId::STRING> : CommandParamGrammarNodeTypeDetail {
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
        struct NodeTypeDetail<NodeTypeId::RANGE> : CommandParamGrammarNodeTypeDetail {
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
        struct NodeTypeDetail<NodeTypeId::AND> : GrammarNodeTypeDetail {
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
        struct NodeTypeDetail<NodeTypeId::EQUAL_ENTRY> : GrammarNodeTypeDetail {
            using Type = NodeEqualEntry;
            static_assert(Type::nodeTypeId == NodeTypeId::EQUAL_ENTRY, "nodTypeId not equal");
            static constexpr auto name = "EQUAL_ENTRY";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::LIST> : GrammarNodeTypeDetail {
            using Type = NodeList;
            static_assert(Type::nodeTypeId == NodeTypeId::LIST, "nodTypeId not equal");
            static constexpr auto name = "LIST";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::OR> : GrammarNodeTypeDetail {
            using Type = NodeOr;
            static_assert(Type::nodeTypeId == NodeTypeId::OR, "nodTypeId not equal");
            static constexpr auto name = "OR";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::SINGLE_SYMBOL> : GrammarNodeTypeDetail {
            using Type = NodeSingleSymbol;
            static_assert(Type::nodeTypeId == NodeTypeId::SINGLE_SYMBOL, "nodTypeId not equal");
            static constexpr auto name = "SINGLE_SYMBOL";
        };

        template<>
        struct NodeTypeDetail<NodeTypeId::OPTIONAL> : GrammarNodeTypeDetail {
            using Type = NodeOptional;
            static_assert(Type::nodeTypeId == NodeTypeId::OPTIONAL, "nodTypeId not equal");
            static constexpr auto name = "OPTIONAL";
        };

        // ============ 节点类型注册表 ============
        // AllNodeTypes 是"全部节点类型"的编译期列表，逐项由 NodeTypeDetail 特化推导，
        // 顺序与 NodeTypeId 枚举一致。新增节点类型只需要：
        //   1. 在 NodeWithType.h 的 CHELPER_NODE_TYPES 列表末尾追加枚举项；
        //   2. 实现节点类（带 static constexpr nodeTypeId 成员）；
        //   3. 为其新增 NodeTypeDetail 特化（其中的 static_assert 校验 Type::nodeTypeId 一致）。
        // 此处与各分发点都不需要再手工登记类型。
        namespace detail {
            template<std::size_t... Is>
            constexpr auto makeNodeTypeList(std::index_sequence<Is...>)
                    -> Meta::TypeList<typename NodeTypeDetail<static_cast<NodeTypeId::NodeTypeId>(Is)>::Type...>;
        }

        using AllNodeTypes = decltype(detail::makeNodeTypeList(
                std::make_index_sequence<static_cast<std::size_t>(NodeTypeId::NodeTypeIdCount)>{}));

        static_assert(Meta::typeListSize<AllNodeTypes> == static_cast<std::size_t>(NodeTypeId::NodeTypeIdCount),
                      "NodeTypeDetail 未覆盖全部 NodeTypeId 枚举项");

        //运行时 nodeTypeId -> 具体节点类型的统一分发，替代各模块重复的 switch(NodeTypeId)。
        //onMatch 以 <class NodeType> 模板形参接收 id 对应的节点类型；
        //各分支返回类型必须一致（与手写 switch 的返回约束相同）。
        //两参版本：id 非法（数据损坏）时 Debug 直接抛异常，Release 走 CHELPER_UNREACHABLE，
        //与旧 switch 的 default: CHELPER_UNREACHABLE() 行为一致；
        //三参版本：id 非法时调用 onMiss，用于反序列化等不可信输入场景。
        namespace detail {
#define CHELPER_NODE_DISPATCH_CASE(v1) \
    case NodeTypeId::v1:               \
        return std::forward<F>(onMatch).template operator()<NodeTypeDetail<NodeTypeId::v1>::Type>();

            template<class F>
            CHELPER_FORCEINLINE decltype(auto) dispatchNodeType(NodeTypeId::NodeTypeId id, F &&onMatch) {
                switch (id) {
                    CHELPER_NODE_TYPES(CHELPER_NODE_DISPATCH_CASE)
                    default:
#if CHelperDebug
                        throw std::runtime_error("invalid nodeTypeId");
#else
                        CHELPER_UNREACHABLE();
#endif
                }
            }

            template<class F, class G>
            CHELPER_FORCEINLINE decltype(auto) dispatchNodeType(NodeTypeId::NodeTypeId id, F &&onMatch, G &&onMiss) {
                switch (id) {
                    CHELPER_NODE_TYPES(CHELPER_NODE_DISPATCH_CASE)
                    default:
                        return std::forward<G>(onMiss)();
                }
            }

#undef CHELPER_NODE_DISPATCH_CASE
        }// namespace detail

        template<class F>
        CHELPER_FORCEINLINE decltype(auto) dispatchNodeType(NodeTypeId::NodeTypeId id, F &&onMatch) {
            return detail::dispatchNodeType(id, std::forward<F>(onMatch));
        }

        template<class F, class G>
        CHELPER_FORCEINLINE decltype(auto) dispatchNodeType(NodeTypeId::NodeTypeId id, F &&onMatch, G &&onMiss) {
            return detail::dispatchNodeType(id, std::forward<F>(onMatch), std::forward<G>(onMiss));
        }

        //编译期遍历全部节点类型：对每个类型 T 调用一次 f.template operator()<T>()
        template<class F>
        constexpr void forEachNodeType(F &&f) {
            Meta::forEachType<AllNodeTypes>(std::forward<F>(f));
        }

        //带短路的编译期遍历：f<T>() 返回 true 时停止，返回是否发生过命中
        template<class F>
        constexpr bool anyNodeType(F &&f) {
            return Meta::anyType<AllNodeTypes>(std::forward<F>(f));
        }

        const char *getNodeTypeName(NodeTypeId::NodeTypeId id);

        std::optional<NodeTypeId::NodeTypeId> getNodeTypeIdByName(const std::string_view &name);

    }// namespace Node

}// namespace CHelper
