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

#include <chelper/node/NodeType.h>

#define CHELPER_GET_NAME(v1) \
    case NodeTypeId::v1:     \
        return NodeTypeDetail<CHelper::Node::NodeTypeId::v1>::name;

#define CHELPER_GET_NODE_TYPE_BY_NAME(v1)                                             \
    if (CHelper::Node::NodeTypeDetail<CHelper::Node::NodeTypeId::v1>::name == type) { \
        return CHelper::Node::NodeTypeId::v1;                                         \
    }

namespace CHelper::Node {

    const char *getNodeTypeName(const CHelper::Node::NodeTypeId::NodeTypeId id) {
        switch (id) {
            CODEC_PASTE(CHELPER_GET_NAME, CHELPER_NODE_TYPES)
            default:
                return "UNKNOWN";
        }
    }

    std::optional<CHelper::Node::NodeTypeId::NodeTypeId> getNodeTypeIdByName(const std::string_view &type) {
        CODEC_PASTE(CHELPER_GET_NODE_TYPE_BY_NAME, CHELPER_NODE_TYPES)
        return std::nullopt;
    }

}// namespace CHelper::Node
