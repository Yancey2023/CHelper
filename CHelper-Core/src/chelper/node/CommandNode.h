//
// Created by Yancey on 2023/11/10.
//

#pragma once

#ifndef CHELPER_NODEBASE_H
#define CHELPER_NODEBASE_H

#include <chelper/lexer/TokenReader.h>
#include <chelper/node/NodeWithType.h>
#include <chelper/resources/id/BlockId.h>
#include <chelper/resources/id/ItemId.h>
#include <chelper/resources/id/NormalId.h>
#include <pch.h>

namespace CHelper {

    class CPack;

    namespace Node {

        class NodeBase {
        };

        class NodeSerializable : public NodeBase {
        public:
            std::optional<std::string> id;
            std::optional<std::u16string> brief;
            std::optional<std::u16string> description;
            std::optional<bool> isMustAfterSpace;

            NodeSerializable() = default;

            NodeSerializable(const std::optional<std::string> &id,
                             const std::optional<std::u16string> &description,
                             bool isMustAfterSpace);

            [[nodiscard]] bool getIsMustAfterSpace() const;
        };

        class NodeWrapped : public NodeBase {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = NodeTypeId::WRAPPED;
            NodeWithType innerNode;
            //存储下一个节点，需要调用构造函数之后再进行添加
            std::vector<NodeWrapped *> nextNodes;

            explicit NodeWrapped(NodeWithType innerNode);

            [[nodiscard]] NodeSerializable &getNodeSerializable() const;
        };

        template<bool isJson>
        class NodeTemplateBoolean : public NodeSerializable {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = isJson ? NodeTypeId::JSON_BOOLEAN : NodeTypeId::BOOLEAN;
            std::optional<std::u16string> descriptionTrue, descriptionFalse;

            NodeTemplateBoolean() = default;

            NodeTemplateBoolean(const std::optional<std::string> &id,
                                const std::optional<std::u16string> &description,
                                const std::optional<std::u16string> &descriptionTrue,
                                const std::optional<std::u16string> &descriptionFalse)
                : NodeSerializable(id, description, false),
                  descriptionTrue(descriptionTrue),
                  descriptionFalse(descriptionFalse) {}
        };

        using NodeBoolean = NodeTemplateBoolean<false>;
        using NodeJsonBoolean = NodeTemplateBoolean<true>;

        template<class T, bool isJson>
        class NodeTemplateNumber : public NodeSerializable {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = isJson ? (std::is_same<T, int32_t>() ? NodeTypeId::JSON_INTEGER : NodeTypeId::JSON_FLOAT)
                                                                        : (std::is_same<T, int32_t>() ? NodeTypeId::INTEGER : NodeTypeId::FLOAT);
            std::optional<T> min, max;

            NodeTemplateNumber() = default;

            NodeTemplateNumber(const std::optional<std::string> &id,
                               const std::optional<std::u16string> &description,
                               const std::optional<T> &min,
                               const std::optional<T> &max)
                : NodeSerializable(id, description, false),
                  min(min),
                  max(max) {}

            static auto str2number(const std::string &str, char *&end) {
                if constexpr (std::numeric_limits<T>::is_integer) {
                    return std::strtoimax(str.c_str(), &end, 10);
                } else if constexpr (std::is_same<T, float>()) {
                    return std::strtof(str.c_str(), &end);
                } else if constexpr (std::is_same<T, double>()) {
                    return std::strtod(str.c_str(), &end);
                } else if constexpr (std::is_same<T, long double>()) {
                    return std::strtold(str.c_str(), &end);
                }
            }
        };

        using NodeFloat = NodeTemplateNumber<float, false>;
        using NodeInteger = NodeTemplateNumber<int32_t, false>;
        using NodeJsonFloat = NodeTemplateNumber<float, true>;
        using NodeJsonInteger = NodeTemplateNumber<int32_t, true>;

        class NodeAnd : public NodeBase {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = NodeTypeId::AND;
            std::vector<NodeWithType> childNodes;

            NodeAnd() = default;

            explicit NodeAnd(std::vector<NodeWithType> childNodes);
        };

        class NodeOr : public NodeBase {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = NodeTypeId::OR;
            std::vector<NodeWithType> childNodes;
            bool isAttachToEnd = false, isUseFirst = false;
            bool noSuggestion = false;
            const char16_t *defaultErrorReason = nullptr;
            ASTNodeId::ASTNodeId nodeId = ASTNodeId::NONE;

            NodeOr() = default;

            NodeOr(std::vector<NodeWithType> childNodes,
                   bool isAttachToEnd,
                   bool isUseFirst = false,
                   bool noSuggestion = false,
                   const char16_t *defaultErrorReason = nullptr,
                   ASTNodeId::ASTNodeId nodeId = ASTNodeId::NONE);
        };

        class NodeEntry : public NodeBase {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = NodeTypeId::ENTRY;
            NodeWithType nodeKey;
            NodeWithType nodeSeparator;
            NodeWithType nodeValue;

            NodeEntry() = default;

            NodeEntry(NodeWithType nodeKey,
                      NodeWithType nodeSeparator,
                      NodeWithType nodeValue);
        };

        struct EqualData {
            std::u16string name;
            std::optional<std::u16string> description;
            bool canUseNotEqual;
            const NodeWithType nodeValue;

            EqualData(std::u16string name,
                      const std::optional<std::u16string> &description,
                      bool canUseNotEqual,
                      NodeWithType nodeValue);
        };

        class NodeNormalId : public NodeSerializable {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = NodeTypeId::NORMAL_ID;
            std::optional<std::string> key;
            std::optional<std::shared_ptr<std::vector<std::shared_ptr<NormalId>>>> contents;
            std::optional<bool> ignoreError;
            bool allowMissingID = false;
            std::function<ASTNode(const NodeWithType &node, TokenReader &tokenReader)> getNormalIdASTNode;
            std::shared_ptr<std::vector<std::shared_ptr<NormalId>>> customContents;

            NodeNormalId() = default;

            NodeNormalId(
                    const std::optional<std::string> &id,
                    const std::optional<std::u16string> &description,
                    const std::string &key,
                    bool ignoreError,
                    bool allowMissingID = false,
                    const std::function<ASTNode(const NodeWithType &node, TokenReader &tokenReader)> &getNormalIdASTNode =
                            [](const NodeWithType &node, TokenReader &tokenReader) -> ASTNode {
                        return tokenReader.readStringOrNumberASTNode(node);
                    });

            NodeNormalId(
                    const std::optional<std::string> &id,
                    const std::optional<std::u16string> &description,
                    const std::shared_ptr<std::vector<std::shared_ptr<NormalId>>> &contents,
                    bool ignoreError,
                    bool allowMissingID = false,
                    const std::function<ASTNode(const NodeWithType &node, TokenReader &tokenReader)> &getNormalIdASTNode =
                            [](const NodeWithType &node, TokenReader &tokenReader) -> ASTNode {
                        return tokenReader.readStringOrNumberASTNode(node);
                    });
        };

        class NodeText : public NodeSerializable {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = NodeTypeId::TEXT;
            std::optional<std::vector<TokenType::TokenType>> tokenTypes;// TODO 这个似乎是历史遗留
            std::shared_ptr<NormalId> data;
            std::function<ASTNode(const NodeWithType &node, TokenReader &tokenReader)> getTextASTNode;

            NodeText() = default;

            NodeText(
                    const std::optional<std::string> &id,
                    const std::optional<std::u16string> &description,
                    const std::shared_ptr<NormalId> &data,
                    const std::function<ASTNode(const NodeWithType &node, TokenReader &tokenReader)> &getTextASTNode =
                            [](const NodeWithType &node, TokenReader &tokenReader) -> ASTNode {
                        return tokenReader.readStringASTNode(node);
                    });
        };

        class NodeEqualEntry : public NodeBase {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = NodeTypeId::EQUAL_ENTRY;
            static NodeText nodeEqual;
            static NodeText nodeNotEqual;
            static NodeOr nodeEqualOrNotEqual;
            std::vector<EqualData> equalDatas;
            std::shared_ptr<std::vector<std::shared_ptr<NormalId>>> nodeKeyContent;
            NodeNormalId nodeKey;

            NodeEqualEntry() = default;

            explicit NodeEqualEntry(std::vector<EqualData> equalDatas);
        };

        class NodeList : public NodeBase {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = NodeTypeId::LIST;
            NodeWithType nodeLeft;
            NodeWithType nodeElement;
            NodeWithType nodeSeparator;
            NodeWithType nodeRight;
            NodeOr nodeElementOrRight;
            NodeOr nodeSeparatorOrRight;

            NodeList() = default;

            NodeList(const NodeWithType &nodeLeft,
                     const NodeWithType &nodeElement,
                     const NodeWithType &nodeSeparator,
                     const NodeWithType &nodeRight);
        };

        class NodeAny : public NodeBase {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = NodeTypeId::ANY;
            NodeEntry nodeEntry;
            NodeList nodeObject;
            NodeList nodeList;
            NodeOr nodeAny;

        private:
            NodeAny();

        public:
            static NodeWithType getNodeAny();
        };

        class NodeSingleSymbol : public NodeBase {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = NodeTypeId::SINGLE_SYMBOL;
            char16_t symbol = ' ';
            std::shared_ptr<NormalId> normalId;
            bool isAddSpace = false;

            NodeSingleSymbol() = default;

            NodeSingleSymbol(char16_t symbol,
                             const std::optional<std::u16string> &description,
                             bool isAddSpace = true);
        };

        class NodeOptional : public NodeBase {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = NodeTypeId::OPTIONAL;
            NodeWithType optionalNode;

            NodeOptional() = default;

            explicit NodeOptional(NodeWithType optionalNode);
        };

        class NodeNamespaceId : public NodeSerializable {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = NodeTypeId::NAMESPACE_ID;
            std::optional<std::string> key;
            std::optional<std::shared_ptr<std::vector<std::shared_ptr<NamespaceId>>>> contents;
            std::optional<bool> ignoreError;
            std::shared_ptr<std::vector<std::shared_ptr<NamespaceId>>> customContents;

            NodeNamespaceId() = default;

            NodeNamespaceId(const std::optional<std::string> &id,
                            const std::optional<std::u16string> &description,
                            const std::optional<std::string> &key,
                            bool ignoreError);
        };

        namespace NodeBlockType {
            enum NodeBlockType : uint8_t {
                // <方块ID> [方块状态]
                BLOCK_WITH_BLOCK_STATE = 0,
                // <方块ID>
                BLOCK = 1
            };
        }// namespace NodeBlockType

        class NodeBlock : public NodeSerializable {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = NodeTypeId::BLOCK;
            static NodeSingleSymbol nodeBlockStateLeftBracket;
            NodeBlockType::NodeBlockType nodeBlockType = NodeBlockType::BLOCK_WITH_BLOCK_STATE;
            std::shared_ptr<BlockIds> blockIds = nullptr;
            NodeNamespaceId nodeBlockId;

            NodeBlock() = default;
        };

        class NodePerCommand : public NodeBase {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = NodeTypeId::PER_COMMAND;
            std::vector<std::u16string> name;
            std::optional<std::u16string> description;
            FreeableNodeWithTypes nodes;
            std::vector<Node::NodeWrapped> wrappedNodes;
            std::vector<Node::NodeWrapped *> startNodes;

            NodePerCommand() = default;
        };

        class NodeCommand : public NodeSerializable {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = NodeTypeId::COMMAND;
            static NodeSingleSymbol nodeCommandStart;

            std::vector<NodePerCommand> *commands = nullptr;

            NodeCommand(const std::optional<std::string> &id,
                        const std::optional<std::u16string> &description,
                        std::vector<NodePerCommand> *commands);

            NodeCommand() = default;
        };

        class NodeCommandName : public NodeSerializable {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = NodeTypeId::COMMAND_NAME;
            std::vector<NodePerCommand> *commands = nullptr;
        };

        class NodeIntegerWithUnit : public NodeSerializable {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = NodeTypeId::INTEGER_WITH_UNIT;
            std::shared_ptr<std::vector<std::shared_ptr<NormalId>>> units;
            NodeNormalId nodeUnits;
            NodeAnd nodeIntegerWithUnit;
            NodeOr nodeIntegerMaybeHaveUnit;

            NodeIntegerWithUnit() = default;
        };

        namespace NodeItemType {
            enum NodeItemType : uint8_t {
                // <物品ID> <物品数量> <附加值> [物品组件]
                ITEM_GIVE = 0,
                // <物品ID> <附加值> <物品数量>
                ITEM_CLEAR = 1
            };
        }// namespace NodeItemType

        class NodeJson : public NodeSerializable {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = NodeTypeId::JSON;
            std::string key;
            NodeWithType nodeJson;

            NodeJson(const std::optional<std::string> &id,
                     const std::optional<std::u16string> &description,
                     std::string key);

            NodeJson() = default;
        };

        class NodeItem : public NodeSerializable {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = NodeTypeId::ITEM;
            static NodeInteger nodeCount;
            static NodeInteger nodeAllData;
            NodeItemType::NodeItemType nodeItemType = NodeItemType::ITEM_GIVE;
            NodeNamespaceId nodeItemId;
            std::shared_ptr<std::vector<std::shared_ptr<ItemId>>> itemIds;
            NodeJson nodeComponent;

            NodeItem() = default;
        };

        class NodeLF : public NodeSerializable {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = NodeTypeId::LF;

        private:
            NodeLF() = default;

            NodeLF(const std::optional<std::string> &id,
                   const std::optional<std::u16string> &description);

        public:
            static NodeWrapped *getInstance();
        };

        class NodePosition : public NodeSerializable {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = NodeTypeId::POSITION;
            NodePosition(const std::optional<std::string> &id,
                         const std::optional<std::u16string> &description);

            NodePosition() = default;
        };

        class NodeRange : public NodeSerializable {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = NodeTypeId::RANGE;
            NodeRange() = default;

            NodeRange(const std::optional<std::string> &id,
                      const std::optional<std::u16string> &description);
        };

        namespace NodeRelativeFloatType {
            enum NodeRelativeFloatType : uint8_t {
                ABSOLUTE_COORDINATE,
                RELATIVE_WORLD_COORDINATE,
                LOCAL_COORDINATE,
            };
        }// namespace NodeRelativeFloatType

        class NodeRelativeFloat : public NodeSerializable {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = NodeTypeId::RELATIVE_FLOAT;
            static NodeSingleSymbol nodeRelativeNotation;
            static NodeSingleSymbol nodeCaretNotation;
            static NodeOr nodePreSymbol;

            bool canUseCaretNotation = true;

            NodeRelativeFloat() = default;

            NodeRelativeFloat(const std::optional<std::string> &id,
                              const std::optional<std::u16string> &description,
                              bool canUseCaretNotation);
        };

        struct RepeatData {
            std::string id;
            FreeableNodeWithTypes breakNodes;
            std::vector<FreeableNodeWithTypes> repeatNodes;
            std::vector<bool> isEnd;
        };

        class NodeRepeat : public NodeSerializable {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = NodeTypeId::REPEAT;
            std::string key;
            const RepeatData *repeatData = nullptr;
            NodeWithType nodeElement;

            NodeRepeat() = default;
        };

        class NodeString : public NodeSerializable {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = NodeTypeId::STRING;
            bool allowMissingString = false;
            bool canContainSpace = false;
            bool ignoreLater = false;

            NodeString() = default;

            NodeString(const std::optional<std::string> &id,
                       const std::optional<std::u16string> &description,
                       bool allowMissingString,
                       bool canContainSpace,
                       bool ignoreLater);
        };

        struct TargetSelectorData {
            static NodeString nodePlayerName;
            static NodeSingleSymbol nodeWildcard;
            static NodeSingleSymbol nodeAt;
            static NodeSingleSymbol nodeSeparator;
            static NodeString nodeString;
            static NodeBoolean nodeBoolean;
            static NodeRelativeFloat nodeRelativeFloat;
            NodeNormalId nodeTargetSelectorVariable;
            NodeNamespaceId nodeItem;
            NodeNormalId nodeFamily, nodeGameMode, nodeSlot;
            NodeNamespaceId nodeEntities;
            NodeEqualEntry nodeHasItemElement;
            NodeList nodeHasItemList1, nodeHasItemList2;
            NodeOr nodeHasItem;
            NodeEqualEntry nodeArgument;
            NodeList nodeArguments;
            NodeOptional nodeOptionalArguments;
            NodeAnd nodeTargetSelectorVariableWithArgument;

            TargetSelectorData();

            void init(const CPack &cpack);
        };

        class NodeTargetSelector : public NodeSerializable {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = NodeTypeId::TARGET_SELECTOR;
            bool isMustPlayer = false, isMustNPC = false, isOnlyOne = false, isWildcard = false;
            NodeOr nodeTargetSelector;

            NodeTargetSelector() = default;
        };

        class NodeJsonElement : public NodeSerializable {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = NodeTypeId::JSON_ELEMENT;
            FreeableNodeWithTypes nodes;
            std::string startNodeId;
            NodeWithType start;

            NodeJsonElement() = default;

            static NodeWithType getNodeJsonElement();
        };

        class NodeJsonEntry : public NodeSerializable {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = NodeTypeId::JSON_ENTRY;
            static NodeSingleSymbol nodeSeparator;
            static NodeEntry nodeAllEntry;
            std::u16string key;
            std::vector<std::string> value;
            NodeText nodeKey;
            NodeOr nodeValue;
            std::optional<NodeEntry> nodeEntry;

            NodeJsonEntry() = default;

            NodeJsonEntry(const std::optional<std::string> &id,
                          const std::optional<std::u16string> &description,
                          std::u16string key = std::u16string(),
                          std::vector<std::string> value = std::vector<std::string>());

            static NodeWithType getNodeJsonAllEntry();
        };

        class NodeJsonList : public NodeSerializable {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = NodeTypeId::JSON_LIST;
            static NodeSingleSymbol nodeLeft;
            static NodeSingleSymbol nodeRight;
            static NodeSingleSymbol nodeSeparator;
            static NodeList nodeAllList;
            std::string data;
            std::optional<NodeList> nodeList;

            NodeJsonList() = default;

            NodeJsonList(const std::optional<std::string> &id,
                         const std::optional<std::u16string> &description,
                         std::string data = std::string());
        };

        class NodeJsonNull : public NodeSerializable {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = NodeTypeId::JSON_NULL;
            NodeJsonNull() = default;

            NodeJsonNull(const std::optional<std::string> &id,
                         const std::optional<std::u16string> &description);
        };

        class NodeJsonObject : public NodeSerializable {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = NodeTypeId::JSON_OBJECT;
            std::vector<NodeJsonEntry> data;
            std::optional<NodeOr> nodeElement1;
            NodeOr nodeElement2;
            NodeList nodeList;

            NodeJsonObject() = default;

            NodeJsonObject(const std::optional<std::string> &id,
                           const std::optional<std::u16string> &description);
        };

        class NodeJsonString : public NodeSerializable {
        public:
            static constexpr NodeTypeId::NodeTypeId nodeTypeId = NodeTypeId::JSON_STRING;
            std::optional<FreeableNodeWithTypes> data;
            NodeOr nodeData;

            NodeJsonString() = default;

            NodeJsonString(const std::optional<std::string> &id,
                           const std::optional<std::u16string> &description);
        };

        template<class NodeType>
        NodeWithType::NodeWithType(NodeType &node)
            : nodeTypeId(NodeType::nodeTypeId),
              data(const_cast<NodeBase *>(static_cast<const NodeBase *>(&node))) {
            static_assert(!std::is_same_v<NodeType, NodeWithType>, "NodeWithType is not allowed to be used as NodeType");
            static_assert(!std::is_pointer_v<NodeType>, "NodeType must not be a pointer");
        }

    }// namespace Node

}// namespace CHelper

#endif//CHELPER_NODEBASE_H
