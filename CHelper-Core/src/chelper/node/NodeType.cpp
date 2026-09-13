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

namespace CHelper::Node {

    const char *getNodeTypeName(const NodeTypeId::NodeTypeId id) {
        return dispatchNodeType(
                id,
                [&]<class NodeType>() { return NodeTypeDetail<NodeType::nodeTypeId>::name; },
                [] { return "UNKNOWN"; });
    }

    std::optional<NodeTypeId::NodeTypeId> getNodeTypeIdByName(const std::string_view &name) {
        std::optional<NodeTypeId::NodeTypeId> result;
        anyNodeType([&]<class NodeType>() {
            if (NodeTypeDetail<NodeType::nodeTypeId>::name != name) {
                return false;
            }
            result = NodeType::nodeTypeId;
            return true;
        });
        return result;
    }

}// namespace CHelper::Node
