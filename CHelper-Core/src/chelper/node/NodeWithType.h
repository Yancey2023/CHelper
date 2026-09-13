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

#include <chelper/util/CPackMemory.h>
#include <pch.h>

//所有节点类型的唯一登记处：新增节点类型时在列表末尾追加一行，并为其补充 NodeTypeDetail 特化。
//枚举项顺序即序列化格式中的类型 id（二进制格式直接写出该数值），禁止插入或重排已有项。
#define CHELPER_NODE_TYPES(X) \
    X(WRAPPED)                \
    X(BLOCK)                  \
    X(BOOLEAN)                \
    X(COMMAND)                \
    X(COMMAND_NAME)           \
    X(FLOAT)                  \
    X(INTEGER)                \
    X(INTEGER_WITH_UNIT)      \
    X(ITEM)                   \
    X(LF)                     \
    X(NAMESPACE_ID)           \
    X(NORMAL_ID)              \
    X(PER_COMMAND)            \
    X(POSITION)               \
    X(RELATIVE_FLOAT)         \
    X(REPEAT)                 \
    X(STRING)                 \
    X(TARGET_SELECTOR)        \
    X(TEXT)                   \
    X(RANGE)                  \
    X(JSON)                   \
    X(JSON_BOOLEAN)           \
    X(JSON_ELEMENT)           \
    X(JSON_ENTRY)             \
    X(JSON_FLOAT)             \
    X(JSON_INTEGER)           \
    X(JSON_LIST)              \
    X(JSON_NULL)              \
    X(JSON_OBJECT)            \
    X(JSON_STRING)            \
    X(AND)                    \
    X(ANY)                    \
    X(ENTRY)                  \
    X(EQUAL_ENTRY)            \
    X(LIST)                   \
    X(OR)                     \
    X(SINGLE_SYMBOL)          \
    X(OPTIONAL)

namespace CHelper::Node {

    namespace NodeTypeId {
        enum NodeTypeId : uint8_t {
#define CHELPER_NODE_ENUM_ENTRY(v1) v1,
            CHELPER_NODE_TYPES(CHELPER_NODE_ENUM_ENTRY)
#undef CHELPER_NODE_ENUM_ENTRY
            //哨兵项：节点类型总数，编译期遍历与运行时合法性检查都以它为界
            NodeTypeIdCount,
        };
    }// namespace NodeTypeId

    class NodeBase;

    class NodeWithType {
    public:
        //默认值只是一个占位，默认构造的NodeWithType的data是nullptr，
        //Parser::parse会在访问data之前检查nullptr，因此未初始化的nodeTypeId不会被使用
        NodeTypeId::NodeTypeId nodeTypeId = NodeTypeId::WRAPPED;
        NodeBase *data = nullptr;

        NodeWithType() = default;

        template<class NodeType>
        NodeWithType(NodeType &node);// NOLINT(*-explicit-constructor)

        NodeWithType(NodeWithType &node) = default;

        NodeWithType(const NodeWithType &node) = default;

        NodeWithType(NodeWithType &&node) = default;

        NodeWithType &operator=(const NodeWithType &node) = default;

        NodeWithType &operator=(NodeWithType &&node) = default;
    };

    class FreeableNodeWithTypes {
    public:
        std::pmr::vector<NodeWithType> nodes;

        FreeableNodeWithTypes() = default;

        ~FreeableNodeWithTypes();

        FreeableNodeWithTypes(const FreeableNodeWithTypes &node) = delete;

        FreeableNodeWithTypes(FreeableNodeWithTypes &&node) = default;

        FreeableNodeWithTypes &operator=(const FreeableNodeWithTypes &node) = delete;

        FreeableNodeWithTypes &operator=(FreeableNodeWithTypes &&node) = default;
    };

    /**
     * Initializes lazily-created shared node definitions while the global PMR
     * resource is still active, before a CPack-specific resource is installed.
     */
    void initializeStaticNodes();

}// namespace CHelper::Node
