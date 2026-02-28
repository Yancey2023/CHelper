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

#include <chelper/lexer/Lexer.h>
#include <chelper/node/NodeType.h>
#include <chelper/parser/Parser.h>
#include <chelper/resources/CPack.h>

#ifdef CHelperDebug
#define DEBUG_GET_NODE_BEGIN(node, index) size_t index = tokenReader.indexStack.size()
#else
#define DEBUG_GET_NODE_BEGIN(node, index) ;
#endif

#ifdef CHelperDebug
#define DEBUG_GET_NODE_END(node, index)                                                                       \
    do {                                                                                                      \
        if ((index) != tokenReader.indexStack.size()) [[unlikely]] {                                          \
            Profile::push("TokenReaderIndexError: {}", FORMAT_ARG(Node::getNodeTypeName((node).nodeTypeId))); \
            throw std::runtime_error("TokenReaderIndexError");                                                \
        }                                                                                                     \
    } while (0)
#else
#define DEBUG_GET_NODE_END(node, index) ;
#endif

#define CHELPER_GET_AST_NODE(v1) \
    case Node::NodeTypeId::v1:   \
        return Parser<typename Node::NodeTypeDetail<Node::NodeTypeId::v1>::Type>::getASTNode(*reinterpret_cast<const typename Node::NodeTypeDetail<Node::NodeTypeId::v1>::Type *>(node.data), tokenReader);

namespace CHelper::Parser {

    ASTNode parse(const Node::NodeWithType &node, TokenReader &tokenReader);

    ASTNode parseByChildNode(const Node::NodeWithType &node,
                             TokenReader &tokenReader,
                             const Node::NodeWithType &childNode,
                             const ASTNodeId::ASTNodeId &astNodeId = ASTNodeId::NONE) {
        ASTNode childAstNode = parse(childNode, tokenReader);
        TokensView tokens = childAstNode.tokens;
        return ASTNode::andNode(node, {std::move(childAstNode)}, tokens, nullptr, astNodeId);
    }

    template<class NodeType>
    struct Parser {
    };

    template<>
    struct Parser<Node::NodeWrapped> {
        static ASTNode getASTNodeWithIsMustAfterSpace(const Node::NodeWrapped &node, TokenReader &tokenReader, bool isMustAfterSpace) {
            //空格检测
            bool isMustAfterSpace0 = reinterpret_cast<const Node::NodeSerializable *>(node.innerNode.data)->getIsMustAfterSpace();
            if (node.innerNode.nodeTypeId != Node::NodeTypeId::REPEAT) {
                tokenReader.push();
                if ((isMustAfterSpace0 || isMustAfterSpace) && node.innerNode.nodeTypeId != Node::NodeTypeId::LF && tokenReader.skipSpace() == 0) [[unlikely]] {
                    TokensView tokens = tokenReader.collect();
                    return ASTNode::simpleNode(node, tokens, ErrorReason::requireSpace(tokens));
                }
                tokenReader.pop();
            }
            //当前节点
            tokenReader.push();
            DEBUG_GET_NODE_BEGIN(node.innerNode, index);
            ASTNode currentASTNode = parse(node.innerNode, tokenReader);
            DEBUG_GET_NODE_END(node.innerNode, index);
            if (currentASTNode.isError() || node.nextNodes.empty()) [[unlikely]] {
                return ASTNode::andNode(node, {std::move(currentASTNode)}, tokenReader.collect());
            }
            //子节点
            std::vector<ASTNode> childASTNodes;
            childASTNodes.reserve(node.nextNodes.size());
            for (const auto &item: node.nextNodes) {
                tokenReader.push();
                childASTNodes.push_back(getASTNodeWithIsMustAfterSpace(*item, tokenReader, isMustAfterSpace0));
                tokenReader.restore();
            }
            tokenReader.push();
            tokenReader.skipToLF();
            ASTNode nextASTNode = ASTNode::orNode(node, std::move(childASTNodes), tokenReader.collect());
            return ASTNode::andNode(node, {std::move(currentASTNode), std::move(nextASTNode)}, tokenReader.collect());
        }

        static ASTNode getASTNode(const Node::NodeWrapped &node, TokenReader &tokenReader) {
            return getASTNodeWithIsMustAfterSpace(node, tokenReader, false);
        }
    };

    template<>
    struct Parser<Node::NodeJsonElement> {
        static ASTNode getASTNode(const Node::NodeJsonElement &node, TokenReader &tokenReader) {
            return parseByChildNode(node, tokenReader, node.start);
        }
    };

    template<>
    struct Parser<Node::NodeJsonEntry> {
        static ASTNode getASTNode(const Node::NodeJsonEntry &node, TokenReader &tokenReader) {
            return parseByChildNode(node, tokenReader, node.nodeEntry.has_value() ? node.nodeEntry.value() : Node::NodeJsonEntry::nodeAllEntry);
        }
    };

    template<>
    struct Parser<Node::NodeJsonList> {
        static ASTNode getASTNode(const Node::NodeJsonList &node, TokenReader &tokenReader) {
            if (!node.nodeList.has_value()) [[unlikely]] {
                return parseByChildNode(node, tokenReader, CHelper::Node::NodeJsonList::nodeAllList, ASTNodeId::NODE_JSON_ALL_LIST);
            }
            tokenReader.push();
            ASTNode result1 = parse(node.nodeList.value(), tokenReader);
            if (!result1.isError()) [[likely]] {
                return ASTNode::andNode(node, {std::move(result1)}, tokenReader.collect());
            }
            size_t index1 = tokenReader.index;
            tokenReader.restore();
            tokenReader.push();
            ASTNode result2 = parseByChildNode(node, tokenReader, CHelper::Node::NodeJsonList::nodeAllList, ASTNodeId::NODE_JSON_ALL_LIST);
            size_t index2 = tokenReader.index;
            tokenReader.restore();
            tokenReader.push();
            tokenReader.index = result1.isError() ? index2 : index1;
            return ASTNode::orNode(node, {std::move(result1), std::move(result2)}, tokenReader.collect());
        }
    };

    template<>
    struct Parser<Node::NodeJsonNull> {
        static ASTNode getASTNode(const Node::NodeJsonNull &node, TokenReader &tokenReader) {
            tokenReader.push();
            auto result = tokenReader.readStringASTNode(node);
            tokenReader.pop();
            std::u16string_view str = result.tokens.string();
            if (str.empty()) [[likely]] {
                TokensView tokens = result.tokens;
                return ASTNode::andNode(node, {std::move(result)}, tokens, ErrorReason::contentError(tokens, u"null参数为空"));
            } else if (str != u"null") [[likely]] {
                TokensView tokens = result.tokens;
                return ASTNode::andNode(node, {std::move(result)}, tokens, ErrorReason::contentError(tokens, fmt::format(u"内容不是null -> {}", str)));
            }
            return result;
        }
    };

    template<>
    struct Parser<Node::NodeJsonObject> {
        static ASTNode getASTNode(const Node::NodeJsonObject &node, TokenReader &tokenReader) {
            return parseByChildNode(node, tokenReader, node.nodeList);
        }
    };

    std::pair<ASTNode, JsonUtil::ConvertResult>
    getInnerASTNode(const Node::NodeWithType &node,
                    const TokensView &tokens,
                    const std::u16string_view &content,
                    const Node::NodeWithType &mainNode) {
        auto convertResult = JsonUtil::jsonString2String(content);
        if (convertResult.errorReason != nullptr) [[unlikely]] {
            return {ASTNode::simpleNode(node, tokens, convertResult.errorReason), std::move(convertResult)};
        }
#ifdef CHelperTest
        Profile::push("start parsing: {}", FORMAT_ARG(utf8::utf16to8(convertResult.result)));
#endif
        ASTNode result = parse(convertResult.result, mainNode);
#ifdef CHelperTest
        Profile::pop();
#endif
        return {std::move(result), std::move(convertResult)};
    }

    template<>
    struct Parser<Node::NodeJsonString> {
        static ASTNode getASTNode(const Node::NodeJsonString &node, TokenReader &tokenReader) {
            tokenReader.push();
            ASTNode result = tokenReader.readStringASTNode(node);
            tokenReader.pop();
            TokensView tokens = result.tokens;
            std::u16string_view str = tokens.string();
            if (str.empty()) [[unlikely]] {
                return ASTNode::simpleNode(node, tokens, ErrorReason::incomplete(tokens, u"字符串参数内容为空"));
            } else if (str[0] != '"') [[unlikely]] {
                return ASTNode::simpleNode(node, tokens, ErrorReason::contentError(tokens, fmt::format(u"字符串参数内容应该在双引号内 -> {}", str)));
            }
            std::shared_ptr<ErrorReason> errorReason;
            if (str.size() <= 1 || str[str.size() - 1] != '"') [[likely]] {
                errorReason = ErrorReason::contentError(tokens, fmt::format(u"字符串参数内容应该在双引号内 -> {}", str));
            }
            if (!node.data.has_value() || node.data->nodes.empty()) [[likely]] {
                return ASTNode::simpleNode(node, tokens, errorReason);
            }
            ASTNode innerNode = getInnerASTNode(node, tokens, str, node.nodeData).first;
            ASTNode newResult = ASTNode::andNode(node, {std::move(innerNode)}, tokens, errorReason, ASTNodeId::NODE_STRING_INNER);
            if (errorReason == nullptr) [[unlikely]] {
                for (auto &item: newResult.errorReasons) {
                    item->start += tokens.startIndex;
                    item->end += tokens.startIndex;
                }
            }
            return newResult;
        }
    };

    template<>
    struct Parser<Node::NodeBlock> {
        static ASTNode getASTNode(const Node::NodeBlock &node, TokenReader &tokenReader) {
            tokenReader.push();
            ASTNode blockId = parseByChildNode(node, tokenReader, node.nodeBlockId, ASTNodeId::NODE_BLOCK_BLOCK_ID);
            if (node.nodeBlockType == Node::NodeBlockType::BLOCK || blockId.isError()) [[unlikely]] {
                tokenReader.pop();
                return blockId;
            }
            tokenReader.push();
            ASTNode blockStateLeftBracket = parse(CHelper::Node::NodeBlock::nodeBlockStateLeftBracket, tokenReader);
            tokenReader.restore();
            if (blockStateLeftBracket.isError()) [[likely]] {
                return ASTNode::andNode(node, {(std::move(blockId))}, tokenReader.collect(),
                                        nullptr, ASTNodeId::NODE_BLOCK_BLOCK_AND_BLOCK_STATE);
            }
            std::u16string_view str = blockId.tokens.string();
            XXH64_hash_t strHash = XXH3_64bits(str.data(), str.size() * sizeof(decltype(str)::value_type));
            std::shared_ptr<NamespaceId> currentBlock = nullptr;
            for (const auto &item: *node.blockIds->blockStateValues) {
                if (item->fastMatch(strHash) || item->getIdWithNamespace()->fastMatch(strHash)) [[unlikely]] {
                    currentBlock = item;
                    break;
                }
            }
            auto nodeBlockState = currentBlock == nullptr
                                          ? BlockId::getNodeAllBlockState()
                                          : std::static_pointer_cast<BlockId>(currentBlock)->getNode(node.blockIds->blockPropertyDescriptions);
            auto astNodeBlockState = parseByChildNode(node, tokenReader, nodeBlockState, ASTNodeId::NODE_BLOCK_BLOCK_STATE);
            return ASTNode::andNode(node, {(std::move(blockId)), (std::move(astNodeBlockState))}, tokenReader.collect(),
                                    nullptr, ASTNodeId::NODE_BLOCK_BLOCK_AND_BLOCK_STATE);
        }
    };

    template<>
    struct Parser<Node::NodeCommand> {
        static ASTNode getASTNode(const Node::NodeCommand &node, TokenReader &tokenReader) {
            tokenReader.push();
            ASTNode commandStart = parse(Node::NodeCommand::nodeCommandStart, tokenReader);
            if (commandStart.isError()) [[unlikely]] {
                tokenReader.restore();
                tokenReader.push();
            }
            ASTNode commandName = tokenReader.readStringASTNode(node, ASTNodeId::NODE_COMMAND_COMMAND_NAME);
            if (commandName.tokens.size() == 0) [[unlikely]] {
                TokensView tokens = tokenReader.collect();
                return ASTNode::andNode(node, {std::move(commandName)}, tokens, ErrorReason::contentError(tokens, u"命令名字为空"), ASTNodeId::NODE_COMMAND_COMMAND);
            }
            std::u16string_view str = commandName.tokens.string();
            const Node::NodePerCommand *currentCommand = nullptr;
            if (!commandName.isError()) [[likely]] {
                bool isBreak = false;
                for (const auto &item: *node.commands) {
                    for (const auto &item2: item.name) {
                        if (str == item2) [[unlikely]] {
                            isBreak = true;
                            break;
                        }
                    }
                    if (isBreak) [[unlikely]] {
                        currentCommand = &item;
                        break;
                    }
                }
            }
            if (currentCommand == nullptr) [[unlikely]] {
                TokensView tokens = tokenReader.collect();
                return ASTNode::andNode(node, {std::move(commandName)}, tokens, ErrorReason::contentError(tokens, fmt::format(u"命令名字不匹配，找不到名为{}的命令", str)), ASTNodeId::NODE_COMMAND_COMMAND);
            }
            ASTNode usage = parse(*currentCommand, tokenReader);
            return ASTNode::andNode(node, {std::move(commandName), std::move(usage)},
                                    tokenReader.collect(), nullptr, ASTNodeId::NODE_COMMAND_COMMAND);
        }
    };

    template<>
    struct Parser<Node::NodeCommandName> {
        static ASTNode getASTNode(const Node::NodeCommandName &node, TokenReader &tokenReader) {
            return tokenReader.readStringASTNode(node);
        }
    };

    template<>
    struct Parser<Node::NodeIntegerWithUnit> {
        static ASTNode getASTNode(const Node::NodeIntegerWithUnit &node, TokenReader &tokenReader) {
            return parseByChildNode(node, tokenReader, node.nodeIntegerMaybeHaveUnit);
        }
    };

    ASTNode getOptionalASTNode(const Node::NodeItem &node,
                               TokenReader &tokenReader,
                               bool isIgnoreChildNodesError,
                               const std::vector<Node::NodeWithType> &childNodes,
                               const ASTNodeId::ASTNodeId &astNodeId = ASTNodeId::NONE) {
        tokenReader.push();
        std::vector<ASTNode> childASTNodes;
        for (const auto &item: childNodes) {
            tokenReader.push();
            tokenReader.push();
            DEBUG_GET_NODE_BEGIN(item, index);
            ASTNode astNode = parse(item, tokenReader);
            DEBUG_GET_NODE_END(item, index);
            bool isError = astNode.isError();
            const TokensView tokens = tokenReader.collect();
            if (isError && (isIgnoreChildNodesError || tokens.isEmpty())) [[unlikely]] {
                tokenReader.restore();
                break;
            }
            childASTNodes.push_back(std::move(astNode));
            tokenReader.pop();
            if (isError) [[unlikely]] {
                break;
            }
        }
        return ASTNode::andNode(node, std::move(childASTNodes), tokenReader.collect(), nullptr, astNodeId);
    }

    template<>
    struct Parser<Node::NodeItem> {
        static ASTNode getASTNode(const Node::NodeItem &node, TokenReader &tokenReader) {
            tokenReader.push();
            ASTNode itemId = parse(node.nodeItemId, tokenReader);
            std::u16string_view str = itemId.tokens.string();
            XXH64_hash_t strHash = XXH3_64bits(str.data(), str.size() * sizeof(decltype(str)::value_type));
            std::shared_ptr<NamespaceId> currentItem = nullptr;
            for (const auto &item: *node.itemIds) {
                if (item->fastMatch(strHash) || item->getIdWithNamespace()->fastMatch(strHash)) [[unlikely]] {
                    currentItem = item;
                    break;
                }
            }
            std::vector<ASTNode> childNodes = {std::move(itemId)};
            Node::NodeWithType nodeData = currentItem == nullptr ? CHelper::Node::NodeItem::nodeAllData : std::static_pointer_cast<ItemId>(currentItem)->getNode();
            switch (node.nodeItemType) {
                case Node::NodeItemType::ITEM_GIVE:
                    childNodes.push_back(getOptionalASTNode(
                            node, tokenReader, false,
                            {Node::NodeItem::nodeCount, nodeData, node.nodeComponent}));
                    break;
                case Node::NodeItemType::ITEM_CLEAR:
                    childNodes.push_back(getOptionalASTNode(
                            node, tokenReader, false,
                            {nodeData, Node::NodeItem::nodeCount}));
                    break;
                default:
                    childNodes.push_back(getOptionalASTNode(
                            node, tokenReader, false,
                            {Node::NodeItem::nodeCount, nodeData, node.nodeComponent}));
                    break;
            }
            return ASTNode::andNode(node, std::move(childNodes), tokenReader.collect());
        }
    };

    template<>
    struct Parser<Node::NodeJson> {
        static ASTNode getASTNode(const Node::NodeJson &node, TokenReader &tokenReader) {
            //TODO 原始JSON文本的目标选择器要支持*
            return parseByChildNode(node, tokenReader, node.nodeJson);
        }
    };

    template<>
    struct Parser<Node::NodeLF> {
        static ASTNode getASTNode(const Node::NodeLF &node, TokenReader &tokenReader) {
            tokenReader.push();
            tokenReader.skipToLF();
            TokensView tokens = tokenReader.collect();
            std::shared_ptr<ErrorReason> errorReason;
            if (tokens.hasValue()) [[unlikely]] {
                errorReason = ErrorReason::excess(tokens, u"命令后面有多余部分 -> " + std::u16string(tokens.string()));
            }
            return ASTNode::simpleNode(node, tokens, errorReason);
        }
    };

    template<>
    struct Parser<Node::NodeNamespaceId> {
        static ASTNode getASTNode(const Node::NodeNamespaceId &node, TokenReader &tokenReader) {
            // namespace:id
            // 字符串中已经包含冒号，因为冒号不是结束字符
            DEBUG_GET_NODE_BEGIN(node, index);
            auto result = tokenReader.readStringASTNode(node);
            DEBUG_GET_NODE_END(node, index);
            if (result.tokens.isEmpty()) [[unlikely]] {
                TokensView tokens = result.tokens;
                return ASTNode::andNode(node, {std::move(result)}, tokens, ErrorReason::incomplete(tokens, u"命令不完整"));
            }
            if (!node.ignoreError.value_or(false)) [[unlikely]] {
                TokensView tokens = result.tokens;
                std::u16string_view str = tokens.string();
                XXH64_hash_t strHash = XXH3_64bits(str.data(), str.size() * sizeof(decltype(str)::value_type));
                if (std::ranges::all_of(*node.customContents, [&strHash](const auto &item) {
                        return !item->fastMatch(strHash) && !item->getIdWithNamespace()->fastMatch(strHash);
                    })) [[unlikely]] {
                    return ASTNode::andNode(node, {std::move(result)}, tokens, ErrorReason::incomplete(tokens, fmt::format(u"找不到含义 -> {}", str)));
                }
            }
            return result;
        }
    };

    template<>
    struct Parser<Node::NodeNormalId> {
        static ASTNode getASTNode(const Node::NodeNormalId &node, TokenReader &tokenReader) {
            tokenReader.push();
            DEBUG_GET_NODE_BEGIN(node, index);
            ASTNode result = node.getNormalIdASTNode(node, tokenReader);
            DEBUG_GET_NODE_END(node, index);
            if (node.allowMissingID) [[unlikely]] {
                if (result.isError()) [[unlikely]] {
                    tokenReader.restore();
                    tokenReader.push();
                    return ASTNode::simpleNode(node, tokenReader.collect());
                }
                tokenReader.pop();
                return result;
            }
            tokenReader.pop();
            if (result.tokens.isEmpty()) [[unlikely]] {
                TokensView tokens = result.tokens;
                return ASTNode::andNode(node, {std::move(result)}, tokens, ErrorReason::incomplete(tokens, u"命令不完整"));
            }
            if (!node.ignoreError.value_or(true)) [[unlikely]] {
                TokensView tokens = result.tokens;
                std::u16string_view str = tokens.string();
                XXH64_hash_t strHash = XXH3_64bits(str.data(), str.size() * sizeof(decltype(str)::value_type));
                if (std::ranges::all_of(*node.customContents, [&strHash](const auto &item) {
                        return !item->fastMatch(strHash);
                    })) [[unlikely]] {
                    return ASTNode::andNode(node, {std::move(result)}, tokens, ErrorReason::incomplete(tokens, fmt::format(u"找不到含义 -> {}", str)));
                }
            }
            return result;
        }
    };

    template<>
    struct Parser<Node::NodePerCommand> {
        static ASTNode getASTNode(const Node::NodePerCommand &node, TokenReader &tokenReader) {
            std::vector<ASTNode> childASTNodes;
            childASTNodes.reserve(node.startNodes.size());
            for (const auto &item: node.startNodes) {
                tokenReader.push();
                DEBUG_GET_NODE_BEGIN(*item, index);
                childASTNodes.push_back(Parser<Node::NodeWrapped>::getASTNodeWithIsMustAfterSpace(*item, tokenReader, true));
                DEBUG_GET_NODE_END(*item, index);
                tokenReader.restore();
            }
            tokenReader.push();
            tokenReader.skipToLF();
            return ASTNode::orNode(node, std::move(childASTNodes), tokenReader.collect());
        }
    };

    namespace NodeRelativeFloatType {
        enum NodeRelativeFloatType : uint8_t {
            ABSOLUTE_COORDINATE,
            RELATIVE_WORLD_COORDINATE,
            LOCAL_COORDINATE,
        };
    }// namespace NodeRelativeFloatType

    std::pair<NodeRelativeFloatType::NodeRelativeFloatType, ASTNode>
    getRelativeFloatASTNode(const Node::NodeWithType &node,
                            TokenReader &tokenReader) {
        tokenReader.push();
        std::vector<ASTNode> childNodes;
        // 0 - 绝对坐标，1 - 相对坐标，2 - 局部坐标
        NodeRelativeFloatType::NodeRelativeFloatType type;
        tokenReader.push();
        ASTNode preSymbol = parse(Node::NodeRelativeFloat::nodePreSymbol, tokenReader);
        if (preSymbol.isError()) [[unlikely]] {
            type = NodeRelativeFloatType::ABSOLUTE_COORDINATE;
            tokenReader.restore();
        } else {
            if (preSymbol.childNodes[1].isError()) [[likely]] {
                type = NodeRelativeFloatType::RELATIVE_WORLD_COORDINATE;
            } else {
                type = NodeRelativeFloatType::LOCAL_COORDINATE;
            }
            tokenReader.pop();
            //空格检测
            if (tokenReader.ready() && tokenReader.peek()->type == TokenType::SPACE) [[unlikely]] {
                childNodes.push_back(std::move(preSymbol));
                return {type, ASTNode::andNode(node, std::move(childNodes), tokenReader.collect())};
            }
            childNodes.push_back(preSymbol);
        }
        //数值部分
        tokenReader.push();
        ASTNode number = tokenReader.readFloatASTNode(node, ASTNodeId::NODE_RELATIVE_FLOAT_NUMBER);
        std::shared_ptr<ErrorReason> errorReason;
        if (!number.isError()) [[likely]] {
            tokenReader.pop();
        } else if (childNodes.empty()) [[unlikely]] {
            tokenReader.pop();
            TokensView tokens = number.tokens;
            errorReason = ErrorReason::typeError(tokens, fmt::format(u"类型不匹配，{}不是有效的坐标参数", tokens.string()));
        } else {
            tokenReader.restore();
        }
        childNodes.push_back(std::move(number));
        ASTNode result = ASTNode::andNode(node, std::move(childNodes), tokenReader.collect(), errorReason);
        // 为了获取补全提示，再嵌套一层or节点
        return {type, ASTNode::orNode(node, {std::move(result), std::move(preSymbol)}, nullptr)};
    }

    template<>
    struct Parser<Node::NodePosition> {
        static ASTNode getASTNode(const Node::NodePosition &node, TokenReader &tokenReader) {
            tokenReader.push();
            // 0 - 绝对坐标，1 - 相对坐标，2 - 局部坐标
            std::vector<ASTNode> threeChildNodes;
            threeChildNodes.reserve(3);
            NodeRelativeFloatType::NodeRelativeFloatType types[3];
            for (NodeRelativeFloatType::NodeRelativeFloatType &type: types) {
                std::pair<NodeRelativeFloatType::NodeRelativeFloatType, ASTNode> childNode = getRelativeFloatASTNode(node, tokenReader);
                if (threeChildNodes.empty() && childNode.second.isError() && !childNode.second.tokens.isEmpty()) {
                    tokenReader.pop();
                    TokensView tokens = childNode.second.tokens;
                    return ASTNode::andNode(node, {std::move(childNode.second)}, tokens, nullptr, ASTNodeId::NODE_POSITION_POSITIONS);
                }
                type = childNode.first;
                threeChildNodes.push_back(std::move(childNode.second));
            }
            //判断有没有错误
            TokensView tokens = tokenReader.collect();
            ASTNode result = ASTNode::andNode(node, std::move(threeChildNodes), tokens, nullptr, ASTNodeId::NODE_POSITION_POSITIONS);
            if (!result.isError()) [[unlikely]] {
                uint8_t count = 0;
                for (NodeRelativeFloatType::NodeRelativeFloatType item: types) {
                    if (item == NodeRelativeFloatType::LOCAL_COORDINATE) [[unlikely]] {
                        count++;
                    }
                }
                if (count == 1 || count == 2) {
                    return ASTNode::andNode(node, {std::move(result)}, tokens, nullptr, ASTNodeId::NODE_POSITION_POSITIONS_WITH_ERROR);
                }
            }
            return result;
        }
    };

    std::shared_ptr<ErrorReason> checkNumber(const TokensView &tokens, std::u16string_view str) {
        if (str.empty()) [[unlikely]] {
            return ErrorReason::contentError(tokens, u"范围的数值为空");
        }
        for (size_t i = 0; i < str.length(); ++i) {
            size_t ch = str[i];
            if ((ch < '0' || ch > '9') && (i != 0 || (ch != '-' && ch != '+'))) [[unlikely]] {
                return ErrorReason::contentError(tokens, u"范围的数值格式不正确，检测非法字符");
            }
        }
        return nullptr;
    }

    template<>
    struct Parser<Node::NodeRange> {
        static ASTNode getASTNode(const Node::NodeRange &node, TokenReader &tokenReader) {
            ASTNode result = tokenReader.readStringASTNode(node);
            std::u16string_view str = result.tokens.string();
            std::shared_ptr<ErrorReason> errorReason;
            size_t index = str.find(u"..");
            if (index == std::u16string::npos) [[likely]] {
                errorReason = checkNumber(result.tokens, str);
            } else if (index == 0) [[unlikely]] {
                errorReason = checkNumber(result.tokens, std::u16string_view(str).substr(2));
            } else {
                errorReason = checkNumber(result.tokens, std::u16string_view(str).substr(0, index));
                if (errorReason == nullptr && index + 2 < str.length()) [[unlikely]] {
                    errorReason = checkNumber(result.tokens, std::u16string_view(str).substr(index + 2));
                }
            }
            return ASTNode::simpleNode(node, result.tokens, errorReason);
        }
    };

    template<>
    struct Parser<Node::NodeRelativeFloat> {
        static ASTNode getASTNode(const Node::NodeRelativeFloat &node, TokenReader &tokenReader) {
            std::pair<NodeRelativeFloatType::NodeRelativeFloatType, ASTNode> result = getRelativeFloatASTNode(node, tokenReader);
            if (result.second.isError()) [[unlikely]] {
                return std::move(result.second);
            }
            if (!node.canUseCaretNotation && result.first == NodeRelativeFloatType::LOCAL_COORDINATE) [[unlikely]] {
                TokensView tokens = result.second.tokens;
                return ASTNode::andNode(node, {std::move(result.second)}, tokens, nullptr, ASTNodeId::NODE_RELATIVE_FLOAT_WITH_ERROR);
            }
            return std::move(result.second);
        }
    };

    template<>
    struct Parser<Node::NodeRepeat> {
        static ASTNode getASTNode(const Node::NodeRepeat &node, TokenReader &tokenReader) {
            tokenReader.push();
            std::vector<ASTNode> childNodes;
            while (true) {
                ASTNode orNode = parse(node.nodeElement, tokenReader);
                bool isAstNodeError = orNode.childNodes[0].isError();
                bool isBreakAstNodeError = orNode.childNodes[1].isError();
                if (!isBreakAstNodeError || isAstNodeError ||
                    (!tokenReader.ready() && node.repeatData->isEnd[orNode.childNodes[0].whichBest])) [[unlikely]] {
                    childNodes.push_back(std::move(orNode));
                    return ASTNode::andNode(node, std::move(childNodes), tokenReader.collect(), nullptr);
                }
                childNodes.push_back(std::move(orNode));
            }
        }
    };

    template<>
    struct Parser<Node::NodeString> {
        static ASTNode getASTNode(const Node::NodeString &node, TokenReader &tokenReader) {
            if (node.ignoreLater) [[unlikely]] {
                //后面的所有内容都算作这个字符串
                tokenReader.push();
                tokenReader.skipToLF();
                TokensView tokens = tokenReader.collect();
                if (!node.allowMissingString && tokens.isEmpty()) [[unlikely]] {
                    return ASTNode::simpleNode(node, tokens, ErrorReason::incomplete(tokens, u"字符串参数内容为空"));
                } else {
                    return ASTNode::simpleNode(node, tokens);
                }
            }
            tokenReader.push();
            ASTNode result = tokenReader.readStringASTNode(node);
            if (node.allowMissingString && result.isError()) [[unlikely]] {
                tokenReader.restore();
                tokenReader.push();
                return ASTNode::simpleNode(node, tokenReader.collect());
            }
            tokenReader.pop();
            if (!node.allowMissingString && result.tokens.isEmpty()) [[unlikely]] {
                return ASTNode::simpleNode(node, result.tokens, ErrorReason::incomplete(result.tokens, u"字符串参数内容为空"));
            }
            if (!node.canContainSpace) [[unlikely]] {
                if (result.tokens.string().find(' ') != std::u16string::npos) [[unlikely]] {
                    return ASTNode::simpleNode(node, result.tokens, ErrorReason::contentError(result.tokens, u"字符串参数内容不可以包含空格"));
                }
                return result;
            }
            std::u16string_view str = result.tokens.string();
            if (str.empty() || str[0] != '"') [[likely]] {
                return result;
            }
            auto convertResult = JsonUtil::jsonString2String(str);
            if (convertResult.errorReason != nullptr) [[unlikely]] {
                convertResult.errorReason->start += result.tokens.startIndex;
                convertResult.errorReason->end += result.tokens.startIndex;
                return ASTNode::simpleNode(node, result.tokens, convertResult.errorReason);
            }
            if (!convertResult.isComplete) [[unlikely]] {
                return ASTNode::simpleNode(node, result.tokens, ErrorReason::contentError(result.tokens, fmt::format(u"字符串参数内容双引号不封闭 -> {}", str)));
            }
            return result;
        }
    };

    template<>
    struct Parser<Node::NodeTargetSelector> {
        static ASTNode getASTNode(const Node::NodeTargetSelector &node, TokenReader &tokenReader) {
            return parseByChildNode(node, tokenReader, node.nodeTargetSelector);
        }
    };

    template<>
    struct Parser<Node::NodeText> {
        static ASTNode getASTNode(const Node::NodeText &node, TokenReader &tokenReader) {
            tokenReader.skipSpace();
            DEBUG_GET_NODE_BEGIN(node, index);
            auto result = node.getTextASTNode(node, tokenReader);
            DEBUG_GET_NODE_END(node, index);
            std::u16string_view str = result.tokens.string();
            if (str != node.data->name) [[unlikely]] {
                TokensView tokens = result.tokens;
                if (str.empty()) [[unlikely]] {
                    return ASTNode::andNode(node, {std::move(result)}, tokens, ErrorReason::contentError(tokens, u"命令不完整"));
                } else {
                    return ASTNode::andNode(node, {std::move(result)}, tokens, ErrorReason::contentError(tokens, fmt::format(u"找不到含义 -> {}", str)));
                }
            }
            return result;
        }
    };

    template<>
    struct Parser<Node::NodeAnd> {
        static ASTNode getASTNode(const Node::NodeAnd &node, TokenReader &tokenReader) {
            tokenReader.push();
            std::vector<ASTNode> childASTNodes;
            bool isMustAfterSpace = false;
            for (size_t i = 0; i < node.childNodes.size(); ++i) {
                const auto &item = node.childNodes[i];
                if (item.nodeTypeId == Node::NodeTypeId::WRAPPED) {
                    const auto *nodeWrapped = reinterpret_cast<const Node::NodeWrapped *>(item.data);
                    ASTNode childNode = Parser<Node::NodeWrapped>::getASTNodeWithIsMustAfterSpace(*nodeWrapped, tokenReader, isMustAfterSpace);
                    bool isError = childNode.isError();
                    childASTNodes.push_back(std::move(childNode));
                    if (isError) [[unlikely]] {
                        break;
                    }
                    isMustAfterSpace = reinterpret_cast<const Node::NodeSerializable *>(nodeWrapped->innerNode.data)->getIsMustAfterSpace();
                } else {
                    ASTNode childNode = parse(item, tokenReader);
                    bool isError = childNode.isError();
                    childASTNodes.push_back(std::move(childNode));
                    if (isError) [[unlikely]] {
                        break;
                    }
                    if (i < node.childNodes.size() - 1 &&
                        node.childNodes[i + 1].nodeTypeId != Node::NodeTypeId::OPTIONAL &&
                        tokenReader.ready() &&
                        tokenReader.peek()->type == TokenType::SPACE) [[unlikely]] {
                        tokenReader.push();
                        tokenReader.skip();
                        TokensView tokens = tokenReader.collect();
                        return ASTNode::andNode(node, std::move(childASTNodes), tokenReader.collect(),
                                                ErrorReason::contentError(tokens, u"意外的空格"));
                    }
                    isMustAfterSpace = false;
                }
            }
            return ASTNode::andNode(node, std::move(childASTNodes), tokenReader.collect());
        }
    };

    template<>
    struct Parser<Node::NodeAny> {
        static ASTNode getASTNode(const Node::NodeAny &node, TokenReader &tokenReader) {
            return parseByChildNode(node, tokenReader, node.nodeAny);
        }
    };

    template<>
    struct Parser<Node::NodeEntry> {
        static ASTNode getASTNode(const Node::NodeEntry &node, TokenReader &tokenReader) {
            tokenReader.push();
            std::vector<ASTNode> childNodes;
            auto key = parse(node.nodeKey, tokenReader);
            if (key.isError()) [[unlikely]] {
                childNodes.push_back(std::move(key));
                return ASTNode::andNode(node, std::move(childNodes), tokenReader.collect());
            }
            childNodes.push_back(std::move(key));
            auto separator = parse(node.nodeSeparator, tokenReader);
            if (separator.isError()) [[unlikely]] {
                childNodes.push_back(std::move(separator));
                return ASTNode::andNode(node, std::move(childNodes), tokenReader.collect());
            }
            childNodes.push_back(std::move(separator));
            childNodes.push_back(parse(node.nodeValue, tokenReader));
            return ASTNode::andNode(node, std::move(childNodes), tokenReader.collect());
        }
    };

    template<>
    struct Parser<Node::NodeEqualEntry> {
        static ASTNode getASTNode(const Node::NodeEqualEntry &node, TokenReader &tokenReader) {
            tokenReader.push();
            std::vector<ASTNode> childNodes;
            // key
            ASTNode astNodeKey = parseByChildNode(node, tokenReader, node.nodeKey);
            childNodes.push_back(astNodeKey);
            if (astNodeKey.isError()) [[unlikely]] {
                return ASTNode::andNode(node, std::move(childNodes), tokenReader.collect());
            }
            std::u16string_view key = astNodeKey.tokens.string();
            auto it = std::ranges::find_if(node.equalDatas, [&key](const auto &t) {
                return t.name == key;
            });
            // = or =!
            ASTNode astNodeSeparator = parseByChildNode(
                    node, tokenReader,
                    it == node.equalDatas.end() || it->canUseNotEqual
                            ? Node::NodeWithType(Node::NodeEqualEntry::nodeEqualOrNotEqual)
                            : Node::NodeWithType(Node::NodeEqualEntry::nodeEqual));
            childNodes.push_back(astNodeSeparator);
            if (astNodeSeparator.isError()) [[unlikely]] {
                return ASTNode::andNode(node, std::move(childNodes), tokenReader.collect());
            }
            //value
            if (it == node.equalDatas.end()) [[unlikely]] {
                childNodes.push_back(parse(Node::NodeAny::getNodeAny(), tokenReader));
            } else {
                childNodes.push_back(parse(it->nodeValue, tokenReader));
            }
            return ASTNode::andNode(node, std::move(childNodes), tokenReader.collect());
        }
    };

    template<>
    struct Parser<Node::NodeList> {
        static ASTNode getASTNode(const Node::NodeList &node, TokenReader &tokenReader) {
            //标记整个[...]，在最后进行收集
            tokenReader.push();
            ASTNode left = parse(node.nodeLeft, tokenReader);
            if (left.isError()) [[unlikely]] {
                return ASTNode::andNode(node, {std::move(left)}, tokenReader.collect());
            }
            std::vector<ASTNode> childNodes = {std::move(left)};
            {
#ifdef CHelperDebug
                size_t startIndex = tokenReader.index;
#endif
                //检测[]中间有没有内容
                tokenReader.push();
                DEBUG_GET_NODE_BEGIN(node.nodeRight, nodeRightIndex);
                auto rightBracket1 = parse(node.nodeRight, tokenReader);
                DEBUG_GET_NODE_END(node.nodeRight, nodeRightIndex);
                tokenReader.restore();
                auto elementOrRight = parse(node.nodeElementOrRight, tokenReader);
                bool flag = !rightBracket1.isError() || elementOrRight.isError();
                childNodes.push_back(std::move(elementOrRight));
                if (flag) [[unlikely]] {
                    return ASTNode::andNode(node, std::move(childNodes), tokenReader.collect());
                }
#ifdef CHelperDebug
                if (startIndex == tokenReader.index) [[unlikely]] {
                    SPDLOG_WARN("NodeList has some error");
                    return ASTNode::andNode(node, std::move(childNodes), tokenReader.collect());
                }
#endif
            }
            while (true) {
#ifdef CHelperDebug
                size_t startIndex = tokenReader.index;
#endif
                //检测是分隔符还是右括号
                tokenReader.push();
                DEBUG_GET_NODE_BEGIN(node.nodeRight, nodeRightIndex);
                auto rightBracket = parse(node.nodeRight, tokenReader);
                DEBUG_GET_NODE_END(node.nodeRight, nodeRightIndex);
                tokenReader.restore();
                DEBUG_GET_NODE_BEGIN(node.nodeSeparatorOrRight, nodeSeparatorOrRightIndex);
                auto separatorOrRight = parse(node.nodeSeparatorOrRight, tokenReader);
                DEBUG_GET_NODE_END(node.nodeSeparatorOrRight, nodeSeparatorOrRightIndex);
                bool flag = !rightBracket.isError() || separatorOrRight.isError();
                childNodes.push_back(std::move(separatorOrRight));
                if (flag) [[unlikely]] {
                    return ASTNode::andNode(node, std::move(childNodes), tokenReader.collect());
                }
                //检测是不是元素
                DEBUG_GET_NODE_BEGIN(node.nodeElement, nodeElementIndex);
                ASTNode element = parse(node.nodeElement, tokenReader);
                DEBUG_GET_NODE_END(node.nodeElement, nodeElementIndex);
                flag = element.isError();
                childNodes.push_back(std::move(element));
                if (flag) [[unlikely]] {
                    return ASTNode::andNode(node, std::move(childNodes), tokenReader.collect());
                }
#ifdef CHelperDebug
                if (startIndex == tokenReader.index) [[unlikely]] {
                    SPDLOG_WARN("NodeList has some error");
                    return ASTNode::andNode(node, std::move(childNodes), tokenReader.collect());
                }
#endif
            }
        }
    };

    template<>
    struct Parser<Node::NodeOr> {
        static ASTNode getASTNode(const Node::NodeOr &node, TokenReader &tokenReader) {
            std::vector<ASTNode> childASTNodes;
            std::vector<size_t> indexes;
            if (!node.isUseFirst) [[likely]] {
                childASTNodes.reserve(node.childNodes.size());
                indexes.reserve(node.childNodes.size());
            }
            for (const auto &item: node.childNodes) {
                tokenReader.push();
                ASTNode childNode = parse(item, tokenReader);
                bool isNodeError = childNode.isError();
                childASTNodes.push_back(std::move(childNode));
                indexes.push_back(tokenReader.index);
                tokenReader.restore();
                if (node.isUseFirst && !isNodeError) [[unlikely]] {
                    break;
                }
            }
            if (node.isAttachToEnd) [[unlikely]] {
                tokenReader.push();
                tokenReader.skipToLF();
                const TokensView tokens = tokenReader.collect();
                return ASTNode::orNode(node, std::move(childASTNodes), tokens, node.defaultErrorReason, node.nodeId);
            } else {
                ASTNode result = ASTNode::orNode(node, std::move(childASTNodes), nullptr, node.defaultErrorReason, node.nodeId);
                tokenReader.index = indexes[result.whichBest];
                return result;
            }
        }
    };

    template<>
    struct Parser<Node::NodeSingleSymbol> {
        static ASTNode getASTNode(const Node::NodeSingleSymbol &node, TokenReader &tokenReader) {
            ASTNode symbolNode = tokenReader.readSymbolASTNode(node);
            std::shared_ptr<ErrorReason> errorReason;
            if (symbolNode.isError()) [[unlikely]] {
                if (symbolNode.tokens.isEmpty()) [[likely]] {
                    return ASTNode::simpleNode(node, symbolNode.tokens, ErrorReason::incomplete(symbolNode.tokens, fmt::format(u"命令不完整，需要符号{:c}", node.symbol)));
                } else {
                    return ASTNode::simpleNode(node, symbolNode.tokens, ErrorReason::typeError(symbolNode.tokens, fmt::format(u"类型不匹配，需要符号{:c}，但当前内容为{}", node.symbol, symbolNode.tokens.string())));
                }
            }
            std::u16string_view str = symbolNode.tokens.string();
            if (str.length() == 1 && str[0] == node.symbol) [[likely]] {
                return symbolNode;
            }
            return ASTNode::simpleNode(node, symbolNode.tokens, ErrorReason::contentError(symbolNode.tokens, fmt::format(u"内容不匹配，正确的符号为{:c}，但当前内容为{}", node.symbol, str)));
        }
    };

    template<>
    struct Parser<Node::NodeOptional> {
        static ASTNode getASTNode(const Node::NodeOptional &node, TokenReader &tokenReader) {
            tokenReader.push();
            tokenReader.skipSpace();
            ASTNode astNode = parse(node.optionalNode, tokenReader);
            bool isUseOptionalNode = !astNode.isError();
            if (!isUseOptionalNode) {
                for (const auto &item: astNode.errorReasons) {
                    if (item->start > astNode.tokens.startIndex) {
                        isUseOptionalNode = true;
                        break;
                    }
                }
            }
            if (isUseOptionalNode) {
                return ASTNode::andNode(node, {(std::move(astNode))}, tokenReader.collect());
            } else {
                tokenReader.restore();
                TokensView tokens(tokenReader.lexerResult, tokenReader.index, tokenReader.index);
                return ASTNode::orNode(node, {ASTNode::simpleNode(node, tokens), (std::move(astNode))}, tokens);
            }
        }
    };

    template<bool isJson>
    struct Parser<Node::NodeTemplateBoolean<isJson>> {
        static ASTNode getASTNode(const Node::NodeTemplateBoolean<isJson> &node, TokenReader &tokenReader) {
            ASTNode astNode = tokenReader.readStringASTNode(node);
            std::u16string_view str = astNode.tokens.string();
            if (str == u"true" || str == u"false") [[likely]] {
                return astNode;
            }
            TokensView tokens = astNode.tokens;
            return ASTNode::andNode(node, {std::move(astNode)}, tokens,
                                    ErrorReason::contentError(tokens, fmt::format(u"内容不匹配，应该为布尔值，但当前内容为{}", str)));
        }
    };

    template<class T, bool isJson>
    struct Parser<Node::NodeTemplateNumber<T, isJson>> {
        static ASTNode getASTNode(const Node::NodeTemplateNumber<T, isJson> &node, TokenReader &tokenReader) {
            if constexpr (std::numeric_limits<T>::is_integer) {
                return tokenReader.readIntegerASTNode(node);
            } else {
                return tokenReader.readFloatASTNode(node);
            }
        }
    };

    ASTNode parse(const Node::NodeWithType &node, TokenReader &tokenReader) {
        switch (node.nodeTypeId) {
            CODEC_PASTE(CHELPER_GET_AST_NODE, CHELPER_NODE_TYPES)
            default:
                CHELPER_UNREACHABLE();
        }
    }

    ASTNode parse(std::u16string content, const Node::NodeWithType &mainNode) {
        TokenReader tokenReader(std::make_shared<LexerResult>(Lexer::lex(std::move(content))));
#ifdef CHelperTest
        Profile::push("start parsing: {}", FORMAT_ARG(utf8::utf16to8(tokenReader.lexerResult->content)));
#endif
        DEBUG_GET_NODE_BEGIN(mainNode, index);
        auto result = parse(mainNode, tokenReader);
        DEBUG_GET_NODE_END(mainNode, index);
#ifdef CHelperTest
        Profile::pop();
#endif
        return result;
    }

    ASTNode parse(std::u16string content, const CPack &cpack) {
        return parse(std::move(content), cpack.mainNode);
    }

}// namespace CHelper::Parser
