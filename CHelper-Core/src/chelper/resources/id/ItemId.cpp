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

#include <chelper/node/CommandNode.h>
#include <chelper/resources/id/ItemId.h>

namespace CHelper {

    const Node::NodeWithType &ItemId::getNode() {
        if (node == nullptr) [[unlikely]] {
            if (max.has_value() && max.value() < 0) [[unlikely]] {
                throw std::runtime_error("item id max data value should be a positive number");
            }
            auto nodeAllData = new Node::NodeInteger("ITEM_DATA", u"物品附加值", -1, max);
            nodeChildren.nodes.emplace_back(*nodeAllData);
            if (!descriptions.has_value()) [[unlikely]] {
                node = std::make_unique<Node::NodeWithType>(*nodeAllData);
            } else {
                std::vector<Node::NodeWithType> nodeDataChildren;
                nodeDataChildren.reserve(descriptions.value().size());
                size_t i = 0;
                for (const auto &item: descriptions.value()) {
                    auto nodeChild = new Node::NodeText(
                            "ITEM_PER_DATA", item,
                            NormalId::make(utf8::utf8to16(std::to_string(i++)), item),
                            [](const Node::NodeWithType &node1, TokenReader &tokenReader) -> ASTNode {
                                return tokenReader.readIntegerASTNode(node1);
                            });
                    nodeDataChildren.emplace_back(*nodeChild);
                    nodeChildren.nodes.emplace_back(*nodeChild);
                }
                nodeDataChildren.emplace_back(*nodeAllData);
                auto nodeOr = new Node::NodeOr(std::move(nodeDataChildren), false);
                nodeChildren.nodes.emplace_back(*nodeOr);
                node = std::make_unique<Node::NodeWithType>(*nodeOr);
            }
        }
        return *node;
    }

}// namespace CHelper