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

#ifndef CHELPER_NODEWITHTYPE_H
#define CHELPER_NODEWITHTYPE_H

#include <chelper/util/CPackMemory.h>
#include <pch.h>

#define CHELPER_NODE_TYPES WRAPPED,           \
                           BLOCK,             \
                           BOOLEAN,           \
                           COMMAND,           \
                           COMMAND_NAME,      \
                           FLOAT,             \
                           INTEGER,           \
                           INTEGER_WITH_UNIT, \
                           ITEM,              \
                           LF,                \
                           NAMESPACE_ID,      \
                           NORMAL_ID,         \
                           PER_COMMAND,       \
                           POSITION,          \
                           RELATIVE_FLOAT,    \
                           REPEAT,            \
                           STRING,            \
                           TARGET_SELECTOR,   \
                           TEXT,              \
                           RANGE,             \
                           JSON,              \
                           JSON_BOOLEAN,      \
                           JSON_ELEMENT,      \
                           JSON_ENTRY,        \
                           JSON_FLOAT,        \
                           JSON_INTEGER,      \
                           JSON_LIST,         \
                           JSON_NULL,         \
                           JSON_OBJECT,       \
                           JSON_STRING,       \
                           AND,               \
                           ANY,               \
                           ENTRY,             \
                           EQUAL_ENTRY,       \
                           LIST,              \
                           OR,                \
                           SINGLE_SYMBOL,     \
                           OPTIONAL

namespace CHelper::Node {

    namespace NodeTypeId {
        enum NodeTypeId : uint8_t {
            CHELPER_NODE_TYPES
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

#endif//CHELPER_NODEWITHTYPE_H
