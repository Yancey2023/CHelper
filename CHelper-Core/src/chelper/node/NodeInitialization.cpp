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
#include <chelper/node/NodeInitialization.h>
#include <chelper/resources/CPack.h>
#include <chelper/serialization/Serialization.h>

#define CHELPER_INIT(v1)                                                                                                                                                        \
    case Node::NodeTypeId::v1:                                                                                                                                                  \
        NodeInitialization<typename Node::NodeTypeDetail<Node::NodeTypeId::v1>::Type>::init(*reinterpret_cast<NodeTypeDetail<Node::NodeTypeId::v1>::Type *>(node.data), cpack); \
        break;

namespace CHelper::Node {

    template<class NodeType>
    struct NodeInitialization {
        static void init(NodeType &node, const CPack &cpack) {
        }
    };

    template<>
    struct NodeInitialization<NodeBlock> {
        static void init(NodeBlock &node, const CPack &cpack) {
            node.blockIds = cpack.blockIds;
            node.nodeBlockId = NodeNamespaceId("BLOCK_ID", u"方块ID", "block", true);
            initNode(node.nodeBlockId, cpack);
        }
    };

    template<>
    struct NodeInitialization<NodeCommand> {
        static void init(NodeCommand &node, const CPack &cpack) {
            node.commands = cpack.commands.get();
        }
    };

    template<>
    struct NodeInitialization<NodeCommandName> {
        static void init(NodeCommandName &node, const CPack &cpack) {
            node.commands = cpack.commands.get();
        }
    };

    template<>
    struct NodeInitialization<NodeIntegerWithUnit> {
        static void init(NodeIntegerWithUnit &node, const CPack &cpack) {
            static NodeInteger nodeInteger("INTEGER", u"整数", std::nullopt, std::nullopt);
            node.nodeUnits = NodeNormalId("UNITS", u"单位", node.units, false);
            node.nodeIntegerWithUnit = NodeAnd({nodeInteger, node.nodeUnits});
            node.nodeIntegerMaybeHaveUnit = NodeOr({node.nodeIntegerWithUnit, nodeInteger}, false, true);
        }
    };

    template<>
    struct NodeInitialization<NodeItem> {
        static void init(NodeItem &node, const CPack &cpack) {
            node.itemIds = cpack.itemIds;
            node.nodeItemId = NodeNamespaceId("ITEM_ID", u"物品ID", "item", true);
            node.nodeComponent = NodeJson("ITEM_COMPONENT", u"物品组件", "components");
            initNode(node.nodeItemId, cpack);
            initNode(node.nodeComponent, cpack);
        }
    };

    template<>
    struct NodeInitialization<NodeJson> {

        static void init(NodeJson &node, const CPack &cpack) {
            for (const auto &item: cpack.jsonNodes) {
                if (item.id == node.key) [[unlikely]] {
                    node.nodeJson = item;
                    return;
                }
            }
            Profile::push("linking contents to {}", FORMAT_ARG(node.key));
            Profile::push("failed to find json data in the cpack -> {}", FORMAT_ARG(node.key));
            throw std::runtime_error("failed to find json data");
        }
    };

    template<>
    struct NodeInitialization<NodeNamespaceId> {
        static void init(NodeNamespaceId &node, const CPack &cpack) {
            if (node.contents.has_value()) [[likely]] {
                node.customContents = node.contents.value();
            } else if (node.key.has_value()) [[likely]] {
                node.customContents = cpack.getNamespaceId(node.key.value());
            }
            if (node.customContents == nullptr) [[unlikely]] {
                if (node.key.has_value()) [[unlikely]] {
                    Profile::push("linking contents to {}", FORMAT_ARG(node.key.value()));
                    Profile::push("failed to find namespace id in the cpack -> {}", FORMAT_ARG(node.key.value()));
                    throw std::runtime_error("failed to find namespace id");
                } else {
                    throw std::runtime_error("missing content");
                }
            }
        }
    };

    template<>
    struct NodeInitialization<NodeNormalId> {
        static void init(NodeNormalId &node, const CPack &cpack) {
            if (node.getNormalIdASTNode == nullptr) [[unlikely]] {
                node.getNormalIdASTNode = [](const NodeWithType &node, TokenReader &tokenReader) -> ASTNode {
                    return tokenReader.readUntilSpace(node);
                };
            }
            if (node.contents.has_value()) [[likely]] {
                node.customContents = node.contents.value();
            } else if (node.key.has_value()) [[likely]] {
                node.customContents = cpack.getNormalId(node.key.value());
            }
            if (node.customContents == nullptr) [[unlikely]] {
                if (node.key.has_value()) [[unlikely]] {
                    Profile::push("linking contents to {}", FORMAT_ARG(node.key.value()));
                    Profile::push("failed to find normal id in the cpack -> ", FORMAT_ARG(node.key.value()));
                    throw std::runtime_error("failed to find normal id");
                } else {
                    throw std::runtime_error("missing content");
                }
            }
        }
    };

    template<>
    struct NodeInitialization<NodePerCommand> {
        static void init(NodePerCommand &node, const CPack &cpack) {
            // init node definitions
            for (auto &definition: node.nodes.nodes) {
                Profile::push(R"(init node {}: "{}")",
                              FORMAT_ARG(getNodeTypeName(definition.nodeTypeId)),
                              FORMAT_ARG(reinterpret_cast<NodeSerializable *>(definition.data)->id.value_or("UNKNOWN")));
                initNode(definition, cpack);
                Profile::pop();
            }

            // token -> definition lookup, splitting "idA|idB" into alternatives
            std::vector<std::pair<std::string_view, NodeWithType *>> idMap;
            for (auto &item: node.nodes.nodes) {
                auto *serializable = reinterpret_cast<NodeSerializable *>(item.data);
                if (!serializable->id.has_value()) {
                    continue;
                }
                const auto &id = serializable->id.value();
                for (size_t start = 0, end; start < id.size(); start = end + 1) {
                    int findStart = start;
                    if (id[start] == '[') {
                        end = id.find(']', start);
                        if (end == std::string::npos) {
                            Profile::push(R"(unknown node id syntax: {})", FORMAT_ARG(id));
                            throw std::runtime_error("unknown node id syntax");
                        }
                        findStart = end + 1;
                    } else if (id[start] == '<') {
                        end = id.find('>', start);
                        if (end == std::string::npos) {
                            Profile::push(R"(unknown node id syntax: {})", FORMAT_ARG(id));
                            throw std::runtime_error("unknown node id syntax");
                        }
                        findStart = end + 1;
                    }
                    end = std::min(id.find('|', findStart), id.size());
                    if (end > start) {
                        idMap.emplace_back(std::string_view(id.data() + start, end - start), &item);
                    }
                }
            }

            // Sort by token length (longest first) to avoid prefix ambiguity
            std::sort(idMap.begin(), idMap.end(), [](const auto &a, const auto &b) {
                return a.first.size() > b.first.size();
            });

            // flat trie: [0]=root, [i>0] maps to wrappedNodes[i-1]
            struct TrieNode {
                NodeWithType *definition = nullptr;
                std::vector<size_t> children;
                bool needsLf = false; // can end here (syntax ends or next token is optional)
            };
            std::vector<TrieNode> trie(1);
            bool hasOptionalFirst = false;

            for (const auto &syntaxUtf16: node.syntax) {
                std::string syntax = utf8::utf16to8(syntaxUtf16);
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
                    size_t tokenStartPos = position;
                    NodeWithType *definition = nullptr;
                    for (auto &[tokenView, definitionView]: idMap) {
                        if (position + tokenView.size() <= syntax.size() && std::string_view(syntax.data() + position, tokenView.size()) == tokenView) {
                            definition = definitionView;
                            position += tokenView.size();
                            break;
                        }
                    }
                    if (!definition) {
                        Profile::push(R"(unknown syntax token: {}...)", FORMAT_ARG(std::string_view(syntax.data() + position, syntax.size() - position)));
                        throw std::runtime_error("unknown syntax token");
                    }
                    // optional token ([...]) means the predecessor can end here
                    if (syntax[tokenStartPos] == u'[' && current != 0) {
                        trie[current].needsLf = true;
                    }
                    size_t childIndex = SIZE_MAX;
                    for (auto child: trie[current].children) {
                        if (trie[child].definition == definition) {
                            childIndex = child;
                            break;
                        }
                    }
                    if (childIndex == SIZE_MAX) {
                        trie.push_back({definition});
                        childIndex = trie.size() - 1;
                        trie[current].children.push_back(childIndex);
                    }
                    current = childIndex;
                }
                if (current) {
                    trie[current].needsLf = true;
                }
            }

            // materialize wrappedNodes (trie[0] excluded)
            node.wrappedNodes.reserve(trie.size() - 1);
            for (size_t i = 1; i < trie.size(); ++i) {
                node.wrappedNodes.emplace_back(*trie[i].definition);
            }

            // connect nextNodes (1-based trie -> 0-based wrappedNodes)
            for (size_t i = 1; i < trie.size(); ++i) {
                auto &wrappedNode = node.wrappedNodes[i - 1];
                for (auto child: trie[i].children) {
                    wrappedNode.nextNodes.push_back(&node.wrappedNodes[child - 1]);
                }
                if (trie[i].needsLf) {
                    wrappedNode.nextNodes.push_back(NodeLF::getInstance());
                }
            }

            // start nodes
            node.startNodes.clear();
            for (auto child: trie[0].children) {
                node.startNodes.push_back(&node.wrappedNodes[child - 1]);
            }
            if (hasOptionalFirst) {
                node.startNodes.push_back(NodeLF::getInstance());
            }

#ifdef CHelperDebug
            for (const auto &item: node.wrappedNodes) {
                bool flag1 = item.innerNode.nodeTypeId == NodeTypeId::POSITION ||
                             item.innerNode.nodeTypeId == NodeTypeId::RELATIVE_FLOAT;
                for (const auto &item2: item.nextNodes) {
                    if (item2 == NodeLF::getInstance()) [[unlikely]] {
                        continue;
                    }
                    bool flag2 = item2->innerNode.nodeTypeId == NodeTypeId::POSITION ||
                                 item2->innerNode.nodeTypeId == NodeTypeId::RELATIVE_FLOAT;
                    if (flag1 && flag2 == item2->getNodeSerializable().isMustAfterSpace) [[unlikely]] {
                        Profile::push(R"({} should be {} in node "{}")",
                                      "isMustAfterSpace",
                                      item2->getNodeSerializable().isMustAfterSpace ? "false" : "true",
                                      item2->getNodeSerializable().id.value_or("UNKNOWN"));
                        throw std::runtime_error("value is wrong");
                    }
                }
            }
#endif
        }
    };

    template<>
    struct NodeInitialization<NodeRepeat> {
        static void init(NodeRepeat &node, const CPack &cpack) {
            const auto &it = cpack.repeatNodes.find(node.key);
            if (it != cpack.repeatNodes.end()) [[likely]] {
                node.repeatData = it->second.first;
                node.nodeElement = it->second.second;
                return;
            }
            Profile::push("link repeat data {} to content", FORMAT_ARG(node.key));
            Profile::push("fail to find repeat data by id {}", FORMAT_ARG(node.key));
            throw std::runtime_error("fail to find repeat data");
        }
    };

    template<>
    struct NodeInitialization<NodeTargetSelector> {
        static void init(NodeTargetSelector &node, const CPack &cpack) {
            std::vector<NodeWithType> nodes;
            nodes.reserve(node.isWildcard ? 3 : 2);
            if (node.isWildcard) {
                nodes.emplace_back(Node::TargetSelectorData::nodeWildcard);
            }
            nodes.emplace_back(cpack.targetSelectorData.nodeTargetSelectorVariableWithArgument);
            nodes.emplace_back(TargetSelectorData::nodePlayerName);
            node.nodeTargetSelector = NodeOr(std::move(nodes), false);
            initNode(node.nodeTargetSelector, cpack);
        }
    };

    template<>
    struct NodeInitialization<NodeText> {
        static void init(NodeText &node, const CPack &cpack) {
            node.getTextASTNode = [](const NodeWithType &node, TokenReader &tokenReader) -> ASTNode {
                return tokenReader.readUntilSpace(node);
            };
        }
    };

    template<>
    struct NodeInitialization<NodeWrapped> {
        static void init(NodeWrapped &node, const CPack &cpack) {
            initNode(node.innerNode, cpack);
        }
    };

    template<>
    struct NodeInitialization<NodeJsonEntry> {
        static void init(NodeJsonEntry &node, const CPack &cpack) {
        }
        static void init(NodeJsonEntry &node, const std::vector<NodeWithType> &dataList) {
            std::vector<NodeWithType> valueNodes;
            for (const auto &item: node.value) {
                bool notFind = true;
                for (const auto &item2: dataList) {
                    if (reinterpret_cast<const NodeSerializable *>(item2.data)->id == item) [[unlikely]] {
                        valueNodes.emplace_back(item2);
                        notFind = false;
                        break;
                    }
                }
                if (notFind) {
                    Profile::push("linking contents to {}", FORMAT_ARG(item));
                    Profile::push("failed to find node id -> {}", FORMAT_ARG(item));
                    Profile::push("unknown node id -> {} (in node \"{}\")", FORMAT_ARG(node.id.value_or("UNKNOWN")), FORMAT_ARG(item));
                    throw std::runtime_error("unknown node id");
                }
            }
            node.nodeKey = NodeText("JSON_OBJECT_ENTRY_KEY", u"JSON对象键",
                                    NormalId::make(fmt::format(u"\"{}\"", node.key), node.description));
            node.nodeValue = NodeOr(std::move(valueNodes), false);
            node.nodeEntry = NodeEntry(node.nodeKey, Node::NodeJsonEntry::nodeSeparator, node.nodeValue);
        }
    };

    template<>
    struct NodeInitialization<NodeJsonList> {
        static void init(NodeJsonList &node, const CPack &cpack) {
        }
        static void init(NodeJsonList &node, const std::vector<NodeWithType> &dataList) {
            for (const auto &item: dataList) {
                if (reinterpret_cast<const NodeSerializable *>(item.data)->id == node.data) [[unlikely]] {
                    node.nodeList = NodeList(Node::NodeJsonList::nodeLeft, item, Node::NodeJsonList::nodeSeparator, Node::NodeJsonList::nodeRight);
                    return;
                }
            }
            Profile::push("linking contents to {}", FORMAT_ARG(node.data));
            Profile::push("failed to find node id -> {}", FORMAT_ARG(node.data));
            Profile::push("unknown node id -> {} (in node \"{}\")", FORMAT_ARG(node.data), FORMAT_ARG(node.id.value_or("UNKNOWN")));
            throw std::runtime_error("unknown node id");
        }
    };

    template<>
    struct NodeInitialization<NodeJsonElement> {
        static void init(NodeJsonElement &node, const CPack &cpack) {
            Profile::push("linking startNode \"{}\" to nodes", FORMAT_ARG(node.startNodeId));
            for (const auto &item: node.nodes.nodes) {
                initNode(item, cpack);
            }
            if (node.startNodeId != "LF") [[likely]] {
                for (auto &item: node.nodes.nodes) {
                    if (reinterpret_cast<const NodeSerializable *>(item.data)->id == node.startNodeId) [[unlikely]] {
                        node.start = item;
                        break;
                    }
                }
            }
            if (node.start.data == nullptr) [[unlikely]] {
                Profile::push("unknown node id -> {}", FORMAT_ARG(node.startNodeId));
            }
            for (auto &item: node.nodes.nodes) {
                if (item.nodeTypeId == NodeTypeId::JSON_LIST) [[unlikely]] {
                    NodeInitialization<NodeJsonList>::init(*reinterpret_cast<NodeJsonList *>(item.data), node.nodes.nodes);
                } else if (item.nodeTypeId == NodeTypeId::JSON_OBJECT) [[unlikely]] {
                    for (auto &item2: reinterpret_cast<NodeJsonObject *>(item.data)->data) {
                        NodeInitialization<NodeJsonEntry>::init(item2, node.nodes.nodes);
                    }
                }
            }
            Profile::pop();
        }
    };

    template<>
    struct NodeInitialization<NodeJsonObject> {
        static void init(NodeJsonObject &node, const CPack &cpack) {
            if (node.data.empty()) [[unlikely]] {
                node.nodeElement1 = std::nullopt;
            } else {
                std::vector<NodeWithType> nodeElementData;
                nodeElementData.reserve(node.data.size());
                for (const auto &item: node.data) {
                    nodeElementData.emplace_back(item);
                }
                node.nodeElement1 = NodeOr(std::move(nodeElementData), false);
            }
            std::vector<NodeWithType> nodeElementData;
            if (node.nodeElement1.has_value()) [[likely]] {
                nodeElementData.reserve(2);
                nodeElementData.emplace_back(node.nodeElement1.value());
            }
            nodeElementData.emplace_back(NodeJsonEntry::getNodeJsonAllEntry());
            node.nodeElement2 = NodeOr(std::move(nodeElementData), false, true);
            static NodeSingleSymbol nodeListLeft(u'{', u"JSON列表左括号");
            static NodeSingleSymbol nodeListRight(u'}', u"JSON列表右括号");
            static NodeSingleSymbol nodeListSeparator(u',', u"JSON列表分隔符");
            node.nodeList = NodeList(nodeListLeft, node.nodeElement2, nodeListSeparator, nodeListRight);
        }
    };

    template<>
    struct NodeInitialization<NodeJsonString> {
        static void init(NodeJsonString &node, const CPack &cpack) {
            if (node.data.has_value()) [[unlikely]] {
                for (const auto &item: node.data.value().nodes) {
                    initNode(item, cpack);
                }
                std::vector<NodeWithType> nodeDataElement;
                nodeDataElement.reserve(node.data.value().nodes.size());
                for (const auto &item: node.data.value().nodes) {
                    nodeDataElement.push_back(item);
                }
                node.nodeData = NodeOr(std::move(nodeDataElement), false);
            }
        }
    };

    void initNode(Node::NodeWithType node, const CPack &cpack) {
        switch (node.nodeTypeId) {
            CODEC_PASTE(CHELPER_INIT, CHELPER_NODE_TYPES)
            default:
                CHELPER_UNREACHABLE();
        }
    }

}// namespace CHelper::Node
