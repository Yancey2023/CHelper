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

#if CHelperDebug
#include <chelper/node/NodeType.h>
#endif

namespace CHelper::Node {

    NodeSerializable::NodeSerializable(const std::optional<std::string> &id,
                                       const std::optional<std::u16string> &description,
                                       bool isMustAfterSpace)
        : id(copyPmrStringOptional(id)),
          description(copyPmrU16StringOptional(description)),
          isMustAfterSpace(isMustAfterSpace) {}

    bool NodeSerializable::getIsMustAfterSpace() const {
        return isMustAfterSpace.value_or(false);
    }

    NodeSingleSymbol NodeBlock::nodeBlockStateLeftBracket(u'[', u"方块状态左括号");

    NodeSingleSymbol NodeCommand::nodeCommandStart(u'/', u"命令开始字符");

    NodeCommand::NodeCommand(const std::optional<std::string> &id,
                             const std::optional<std::u16string> &description,
                             std::pmr::vector<Node::NodePerCommand> *commands)
        : NodeSerializable(id, description, false),
          commands(commands) {}

    NodeInteger NodeItem::nodeCount("ITEM_COUNT", u"物品数量", 0, std::nullopt);
    NodeInteger NodeItem::nodeAllData("ITEM_DATA", u"物品附加值", -1, std::nullopt);

    NodeInteger NodeIntegerWithUnit::nodeInteger("INTEGER", u"整数", std::nullopt, std::nullopt);

    NodeJson::NodeJson(const std::optional<std::string> &id,
                       const std::optional<std::u16string> &description,
                       const std::string_view key)
        : NodeSerializable(id, description, false),
          key(key.data(), key.size()) {}

    NodeLF::NodeLF(const std::optional<std::string> &id,
                   const std::optional<std::u16string> &description)
        : NodeSerializable(id, description, false) {}

    NodeWrapped *NodeLF::getInstance() {
        static NodeLF INSTANCE("LF", u"命令终止");
        static NodeWrapped INSTANCE_WRAPPED(INSTANCE);
        return &INSTANCE_WRAPPED;
    }

    NodeNamespaceId::NodeNamespaceId(const std::optional<std::string> &id,
                                     const std::optional<std::u16string> &description,
                                     const std::optional<std::string> &key,
                                     bool ignoreError)
        : NodeSerializable(id, description, false),
          key(copyPmrStringOptional(key)),
          ignoreError(ignoreError) {}

    NodeNormalId::NodeNormalId(
            const std::optional<std::string> &id,
            const std::optional<std::u16string> &description,
            const std::string &key,
            bool ignoreError,
            bool allowMissingID,
            const std::function<ASTNode(const NodeWithType &node, TokenReader &tokenReader)> &getNormalIdASTNode)
        : NodeSerializable(id, description, false),
          key(std::pmr::string(key.data(), key.size())),
          ignoreError(ignoreError),
          allowMissingID(allowMissingID),
          getNormalIdASTNode(getNormalIdASTNode) {}

    NodeNormalId::NodeNormalId(
            const std::optional<std::string> &id,
            const std::optional<std::u16string> &description,
            const std::shared_ptr<std::pmr::vector<std::shared_ptr<NormalId>>> &contents,
            bool ignoreError,
            bool allowMissingID,
            const std::function<ASTNode(const NodeWithType &node, TokenReader &tokenReader)> &getNormalIdASTNode)
        : NodeSerializable(id, description, false),
          contents(contents),
          ignoreError(ignoreError),
          allowMissingID(allowMissingID),
          getNormalIdASTNode(getNormalIdASTNode) {
#if CHelperDebug
        if (contents == nullptr) [[unlikely]] {
            throw std::runtime_error("contents should not be nullptr");
        }
#endif
        customContents = contents;
    }

    NodePosition::NodePosition(const std::optional<std::string> &id,
                               const std::optional<std::u16string> &description)
        : NodeSerializable(id, description, false) {}

    NodeRange::NodeRange(const std::optional<std::string> &id,
                         const std::optional<std::u16string> &description)
        : NodeSerializable(id, description, false) {}

    NodeSingleSymbol NodeRelativeFloat::nodeRelativeNotation(u'~', u"相对坐标（~x ~y ~z）", false);
    NodeSingleSymbol NodeRelativeFloat::nodeCaretNotation(u'^', u"局部坐标（^左 ^上 ^前）", false);
    NodeOr NodeRelativeFloat::nodePreSymbol({nodeRelativeNotation, nodeCaretNotation}, false);

    NodeRelativeFloat::NodeRelativeFloat(const std::optional<std::string> &id,
                                         const std::optional<std::u16string> &description,
                                         bool canUseCaretNotation)
        : NodeSerializable(id, description, false),
          canUseCaretNotation(canUseCaretNotation) {}

    NodeString::NodeString(const std::optional<std::string> &id,
                           const std::optional<std::u16string> &description,
                           bool allowMissingString,
                           const bool canContainSpace,
                           const bool ignoreLater)
        : NodeSerializable(id, description, false),
          allowMissingString(allowMissingString),
          canContainSpace(canContainSpace),
          ignoreLater(ignoreLater) {}


    NodeText::NodeText(const std::optional<std::string> &id,
                       const std::u16string_view description,
                       const std::shared_ptr<NormalId> &data,
                       const std::function<ASTNode(const NodeWithType &node, TokenReader &tokenReader)> &getTextASTNode)
        : NodeSerializable(id, std::optional<std::u16string>(std::in_place, description), false),
          data(data),
          getTextASTNode(getTextASTNode) {}

    NodeAnd::NodeAnd(std::pmr::vector<NodeWithType> childNodes)
        : childNodes(std::move(childNodes)) {
#if CHelperDebug
        if (this->childNodes.empty()) {
            throw std::runtime_error("childNodes is empty");
        }
        for (const auto &item: this->childNodes) {
            if (item.data == nullptr) [[unlikely]] {
                throw std::runtime_error("null node in node or");
            }
        }
#endif
    }

    NodeAny::NodeAny() {
        static NodeSingleSymbol nodeLeft1(u'{', u"左括号");
        static NodeSingleSymbol nodeRight1(u'}', u"右括号");
        static NodeSingleSymbol nodeLeft2(u'[', u"左括号");
        static NodeSingleSymbol nodeRight2(u']', u"右括号");
        static NodeSingleSymbol nodeSeparator(u',', u"分隔符");
        static NodeString nodeString("STRING", u"字符串", true, true, false);
        static NodeBoolean nodeBoolean("BOOLEAN", u"布尔值", std::nullopt, std::nullopt);
        static NodeRelativeFloat nodeRelativeFloat("RELATIVE_FLOAT", u"相对坐标", false);
        static NodeRange nodeRange("RANGE", u"范围");
        nodeEntry = NodeEntry(nodeString, NodeEqualEntry::nodeEqualOrNotEqual, *this);
        nodeObject = NodeList(nodeLeft1, nodeEntry, nodeSeparator, nodeRight1);
        nodeList = NodeList(nodeLeft2, *this, nodeSeparator, nodeRight2);
        nodeAny = NodeOr({nodeRelativeFloat, nodeBoolean, nodeString, nodeObject, nodeRange, nodeList}, false);
    }

    NodeWithType NodeAny::getNodeAny() {
        static NodeAny node;
        return node;
    }

    NodeEntry::NodeEntry(NodeWithType nodeKey,
                         NodeWithType nodeSeparator,
                         NodeWithType nodeValue)
        : nodeKey(nodeKey),
          nodeSeparator(nodeSeparator),
          nodeValue(nodeValue) {}

    NodeText NodeEqualEntry::nodeEqual(
            "TARGET_SELECTOR_ARGUMENT_EQUAL", u"等于",
            NormalId::make(u"=", u"等于"),
            [](const NodeWithType &node, TokenReader &tokenReader) -> ASTNode {
                return tokenReader.readSymbolASTNode(node);
            });
    NodeText NodeEqualEntry::nodeNotEqual(
            "TARGET_SELECTOR_ARGUMENT_NOT_EQUAL", u"不等于",
            NormalId::make(u"=!", u"不等于"),
            [](const NodeWithType &node, TokenReader &tokenReader) -> ASTNode {
                tokenReader.push();
                auto childNodes = {tokenReader.readSymbolASTNode(node), tokenReader.readSymbolASTNode(node)};
                return ASTNode::andNode(node, childNodes, tokenReader.collect());
            });
    NodeOr NodeEqualEntry::nodeEqualOrNotEqual({nodeEqual, nodeNotEqual}, false);

    NodeList::NodeList(const NodeWithType &nodeLeft,
                       const NodeWithType &nodeElement,
                       const NodeWithType &nodeSeparator,
                       const NodeWithType &nodeRight)
        : nodeLeft(nodeLeft),
          nodeElement(nodeElement),
          nodeSeparator(nodeSeparator),
          nodeRight(nodeRight),
          nodeElementOrRight({nodeElement, nodeRight}, false),
          nodeSeparatorOrRight({nodeSeparator, nodeRight}, false) {
#if CHelperDebug
        if (nodeLeft.data == nullptr || nodeElement.data == nullptr || nodeSeparator.data == nullptr || nodeRight.data == nullptr) [[unlikely]] {
            throw std::runtime_error("NodeOr has a null child node");
        }
#endif
    }

    NodeOr::NodeOr(std::pmr::vector<NodeWithType> childNodes,
                   bool isAttachToEnd,
                   bool isUseFirst,
                   bool noSuggestion,
                   const char16_t *defaultErrorReason,
                   ASTNodeId::ASTNodeId nodeId)
        : childNodes(std::move(childNodes)),
          isAttachToEnd(isAttachToEnd),
          isUseFirst(isUseFirst),
          noSuggestion(noSuggestion),
          defaultErrorReason(defaultErrorReason),
          nodeId(nodeId) {
#if CHelperDebug
        if (this->childNodes.empty()) {
            throw std::runtime_error("childNodes is empty");
        }
        for (const auto &item: this->childNodes) {
            if (item.data == nullptr) [[unlikely]] {
                throw std::runtime_error("null node in node or");
            }
        }
#endif
    }

    static std::shared_ptr<NormalId> getNormalId(char16_t symbol, const std::optional<std::u16string> &description) {
        std::pmr::u16string name;
        name.push_back(symbol);
        return NormalId::make(name, description);
    }

    NodeSingleSymbol::NodeSingleSymbol(char16_t symbol,
                                       const std::optional<std::u16string> &description,
                                       bool isAddSpace)
        : NodeSerializable(std::nullopt, description, false),
          symbol(symbol),
          normalId(getNormalId(symbol, description)),
          isAddSpace(isAddSpace) {}

    NodeOptional::NodeOptional(NodeWithType optionalNode)
        : optionalNode(optionalNode) {}

#if CHelperDebug
    bool isNodeSerializable(NodeWithType innerNode) {
        return Node::dispatchNodeType(
                innerNode.nodeTypeId,
                [&]<class NodeType>() { return std::is_base_of_v<NodeSerializable, NodeType>; },
                [] { return false; });
    }
#endif

    NodeWrapped::NodeWrapped(NodeWithType innerNode)
        : innerNode(innerNode) {
#if CHelperDebug
        if (!isNodeSerializable(innerNode)) {
            throw std::runtime_error("invalid innerNode in NodeWrapped");
        }
#endif
    }

    void NodeWrapped::pushNextNode(NodeWrapped *node) {
        nextNodes.push_back(node);
        if (node->innerNode.nodeTypeId == NodeTypeId::LF) {
            hasNextLF = true;
        }
    }

    [[nodiscard]] NodeSerializable &NodeWrapped::getNodeSerializable() const {
        return *reinterpret_cast<NodeSerializable *>(innerNode.data);
    }

    NodeWithType NodeJsonElement::getNodeJsonElement() {
        static NodeJsonString jsonString("JSON_STRING", u"JSON字符串");
        static NodeJsonInteger jsonInteger("JSON_INTEGER", u"JSON整数", std::nullopt, std::nullopt);
        static NodeJsonFloat jsonFloat("JSON_FLOAT", u"JSON小数", std::nullopt, std::nullopt);
        static NodeJsonNull jsonNull("JSON_NULL", u"JSON空值");
        static NodeJsonBoolean jsonBoolean("JSON_BOOLEAN", u"JSON布尔值", std::nullopt, std::nullopt);
        static NodeJsonList jsonList("JSON_LIST", u"JSON列表");
        static NodeJsonObject jsonObject("JSON_OBJECT", u"JSON对象");
        static NodeOr jsonElement(
                {jsonBoolean, jsonFloat, jsonInteger, jsonNull, jsonString, jsonList, jsonObject},
                false, false, true,
                u"类型不匹配，当前内容不是有效的JSON元素");
        return jsonElement;
    }

    NodeSingleSymbol NodeJsonObject::nodeListLeft(u'{', u"JSON列表左括号");
    NodeSingleSymbol NodeJsonObject::nodeListRight(u'}', u"JSON列表右括号");
    NodeSingleSymbol NodeJsonObject::nodeListSeparator(u',', u"JSON列表分隔符");

    NodeSingleSymbol NodeJsonEntry::nodeSeparator(u':', u"冒号");
    static NodeJsonString jsonString("JSON_STRING", u"JSON字符串");

    NodeEntry NodeJsonEntry::nodeAllEntry(jsonString, nodeSeparator, NodeJsonElement::getNodeJsonElement());

    NodeJsonEntry::NodeJsonEntry(const std::optional<std::string> &id,
                                 const std::optional<std::u16string> &description,
                                 const std::u16string_view key,
                                 std::pmr::vector<std::pmr::string> value)
        : NodeSerializable(id, description, false),
          key(key.data(), key.size()),
          value(std::move(value)) {}

    NodeWithType NodeJsonEntry::getNodeJsonAllEntry() {
        static NodeJsonEntry nodeJsonAllEntry("NODE_JSON_ENTRY", u"JSON对象键值对");
        return nodeJsonAllEntry;
    }

    NodeSingleSymbol NodeJsonList::nodeLeft(u'[', u"JSON列表左括号");
    NodeSingleSymbol NodeJsonList::nodeRight(u']', u"JSON列表右括号");
    NodeSingleSymbol NodeJsonList::nodeSeparator(u',', u"JSON列表分隔符");
    NodeList NodeJsonList::nodeAllList(nodeLeft, NodeJsonElement::getNodeJsonElement(), nodeSeparator, nodeRight);

    NodeJsonList::NodeJsonList(const std::optional<std::string> &id,
                               const std::optional<std::u16string> &description,
                               const std::string_view data)
        : NodeSerializable(id, description, false),
          data(data.data(), data.size()) {}

    NodeJsonNull::NodeJsonNull(const std::optional<std::string> &id,
                               const std::optional<std::u16string> &description)
        : NodeSerializable(id, description, false) {}

    NodeJsonObject::NodeJsonObject(const std::optional<std::string> &id,
                                   const std::optional<std::u16string> &description)
        : NodeSerializable(id, description, false) {
        nodeElement1 = std::nullopt;
        nodeElement2 = NodeOr({NodeJsonEntry::getNodeJsonAllEntry()}, false, true);
        nodeList = NodeList(NodeJsonObject::nodeListLeft,
                            nodeElement2,
                            NodeJsonObject::nodeListSeparator,
                            NodeJsonObject::nodeListRight);
    }

    NodeJsonString::NodeJsonString(const std::optional<std::string> &id,
                                   const std::optional<std::u16string> &description)
        : NodeSerializable(id, description, false) {}

}// namespace CHelper::Node
