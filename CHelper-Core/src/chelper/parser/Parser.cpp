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
#include <chelper/parser/ErrorReasonFactory.h>
#include <chelper/parser/Parser.h>
#include <chelper/resources/CPack.h>
#include <chelper/util/JsonUtil.h>

namespace CHelper::Parser {

    namespace {
        // 内层字符串经常是重复的短 ID。只共享已完成的词法结果，每次仍单独解析 AST 和诊断。
        // 缓存限定在一次顶层解析内，不保留输入视图，也不跨上下文或线程共享可变状态。
        struct InnerLexerCache {
            std::pmr::memory_resource *resource;
            std::array<std::shared_ptr<LexerResult>, 4> recent;
            size_t next = 0;

            explicit InnerLexerCache(std::pmr::memory_resource *resource) : resource(resource) {}
        };

        thread_local InnerLexerCache *currentInnerLexerCache = nullptr;

        class InnerLexerScope {
            std::optional<InnerLexerCache> local;
            InnerLexerCache *previous = currentInnerLexerCache;

        public:
            InnerLexerScope() {
                auto *resource = CPackMemoryRouter::getAllocationResource();
                // 自定义节点可在解析中切换分配作用域；这些结果不能借用父作用域的存储。
                if (previous == nullptr || previous->resource != resource) currentInnerLexerCache = &local.emplace(resource);
            }

            ~InnerLexerScope() { currentInnerLexerCache = previous; }

            InnerLexerScope(const InnerLexerScope &) = delete;
            InnerLexerScope &operator=(const InnerLexerScope &) = delete;

            std::shared_ptr<LexerResult> lex(std::u16string_view content) {
                // 顶层输入不缓存；最多四项且各有长度上限，不随命令长度增加。
                if (local.has_value() || content.size() > 256) return Lexer::lex(content);
                for (const auto &recent: previous->recent) {
                    if (recent != nullptr && std::u16string_view(recent->content) == content) return recent;
                }
                auto result = Lexer::lex(content);
                previous->recent[previous->next] = result;
                previous->next = (previous->next + 1) % previous->recent.size();
                return result;
            }
        };

        //Debug 下校验解析过程没有丢失或重复消费 token；Release 编译为空
        void debugCheckTokenIndex([[maybe_unused]] const Node::NodeWithType &node,
                                  [[maybe_unused]] size_t index,
                                  [[maybe_unused]] TokenReader &tokenReader) {
#if CHelperDebug
            if (index != tokenReader.indexStack.size()) [[unlikely]] {
                throw std::runtime_error(
                        fmt::format("TokenReaderIndexError: {}", Node::getNodeTypeName(node.nodeTypeId)));
            }
#endif
        }
    }// namespace

    ASTNode parse(const Node::NodeWithType &node, TokenReader &tokenReader);

    namespace {
        // 调用前收集诊断参数，进入函数后才移动子树，避免参数求值顺序影响 token 视图。
        ASTNode wrapWithError(const Node::NodeWithType &node, ASTNode &&child,
                              std::shared_ptr<ErrorReason> errorReason) {
            TokensView tokens = child.tokens;
            return ASTNode::andNode(node, ASTNode::children(std::move(child)), std::move(tokens), std::move(errorReason));
        }

        ASTNode replaceWithError(const Node::NodeWithType &node, ASTNode &&result,
                                 std::shared_ptr<ErrorReason> errorReason) {
            return ASTNode::simpleNode(node, std::move(result.tokens), std::move(errorReason));
        }
    }// namespace

    ASTNode parseByChildNode(const Node::NodeWithType &node,
                             TokenReader &tokenReader,
                             const Node::NodeWithType &childNode,
                             const ASTNodeId::ASTNodeId &astNodeId = ASTNodeId::NONE) {
        ASTNode childAstNode = parse(childNode, tokenReader);
        TokensView tokens = childAstNode.tokens;
        return ASTNode::andNode(node, ASTNode::children(std::move(childAstNode)), std::move(tokens), nullptr, astNodeId);
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
                    auto errorReason = ErrorReasons::requireSpace(ErrorReasonLevel::REQUIRE_SPACE, tokens);
                    return ASTNode::simpleNode(node, std::move(tokens), std::move(errorReason));
                }
                tokenReader.pop();
            }
            //当前节点
            tokenReader.push();
            size_t index = tokenReader.indexStack.size();
            ASTNode currentASTNode = parse(node.innerNode, tokenReader);
            debugCheckTokenIndex(node.innerNode, index, tokenReader);
            if (currentASTNode.isError() || node.nextNodes.empty()) [[unlikely]] {
                return ASTNode::andNode(node, ASTNode::children(std::move(currentASTNode)), tokenReader.collect());
            }
            //子节点
            std::pmr::vector<ASTNode> childASTNodes(getASTMemoryResource());
            childASTNodes.reserve(node.nextNodes.size());
            for (const auto &item: node.nextNodes) {
                tokenReader.push();
                childASTNodes.push_back(getASTNodeWithIsMustAfterSpace(*item, tokenReader, isMustAfterSpace0));
                tokenReader.restore();
            }
            tokenReader.push();
            tokenReader.skipToLF();
            ASTNode nextASTNode = ASTNode::orNode(node, std::move(childASTNodes), tokenReader.collect());
            return ASTNode::andNode(node, ASTNode::children(std::move(currentASTNode), std::move(nextASTNode)), tokenReader.collect());
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
                return ASTNode::andNode(node, ASTNode::children(std::move(result1)), tokenReader.collect());
            }
            size_t index1 = tokenReader.index;
            tokenReader.restore();
            tokenReader.push();
            ASTNode result2 = parseByChildNode(node, tokenReader, CHelper::Node::NodeJsonList::nodeAllList, ASTNodeId::NODE_JSON_ALL_LIST);
            size_t index2 = tokenReader.index;
            tokenReader.restore();
            tokenReader.push();
            tokenReader.index = result1.isError() ? index2 : index1;
            return ASTNode::orNode(node, ASTNode::children(std::move(result1), std::move(result2)), tokenReader.collect());
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
                return wrapWithError(node, std::move(result), ErrorReasons::emptyNull(ErrorReasonLevel::CONTENT_ERROR, result.tokens));
            } else if (str != u"null") [[likely]] {
                return wrapWithError(node, std::move(result), ErrorReasons::invalidNull(ErrorReasonLevel::CONTENT_ERROR, result.tokens, str));
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

    std::pair<ASTNode, JsonUtil::DecodedStringView>
    getInnerASTNode(const Node::NodeWithType &node,
                    const TokensView &tokens,
                    const std::u16string_view &content,
                    const Node::NodeWithType &mainNode) {
        auto convertResult = JsonUtil::DecodedStringView(content);
        if (convertResult.errorReason != nullptr) [[unlikely]] {
            //jsonString2String返回的错误位置是相对content的坐标，转换成命令里的绝对坐标
            convertResult.errorReason->start += tokens.startIndex;
            convertResult.errorReason->end += tokens.startIndex;
            return {ASTNode::simpleNode(node, tokens, convertResult.errorReason), std::move(convertResult)};
        }
        ASTNode result = parse(convertResult.string(), mainNode);
        return {std::move(result), std::move(convertResult)};
    }

    template<>
    struct Parser<Node::NodeJsonString> {
        static ASTNode getASTNode(const Node::NodeJsonString &node, TokenReader &tokenReader) {
            // 最终错误只由 JSON 字符串规则决定，不需要临时的通用字符串 AST。
            TokensView tokens = tokenReader.readTokenView();
            std::u16string_view str = tokens.string();
            if (str.empty()) [[unlikely]] {
                auto errorReason = ErrorReasons::emptyString(ErrorReasonLevel::INCOMPLETE, tokens);
                return ASTNode::simpleNode(node, std::move(tokens), std::move(errorReason));
            } else if (str[0] != '"') [[unlikely]] {
                auto errorReason = ErrorReasons::quotedStringRequired(ErrorReasonLevel::CONTENT_ERROR, tokens, str);
                return ASTNode::simpleNode(node, std::move(tokens), std::move(errorReason));
            }
            std::shared_ptr<ErrorReason> errorReason;
            if (str.size() <= 1 || str[str.size() - 1] != '"') [[likely]] {
                errorReason = ErrorReasons::quotedStringRequired(ErrorReasonLevel::CONTENT_ERROR, tokens, str);
            }
            if (!node.data.has_value() || node.data->nodes.empty()) [[likely]] {
                return ASTNode::simpleNode(node, std::move(tokens), std::move(errorReason));
            }
            std::pair<ASTNode, JsonUtil::DecodedStringView> innerResult = getInnerASTNode(node, tokens, str, node.nodeData);
            ASTNode innerNode = std::move(innerResult.first);
            //内层AST的位置是解码后字符串的坐标，原始JSON字符串里的转义序列(\n \" \\ \uXXXX等)
            //会让内外坐标不再相差固定偏移，必须用indexConvertList换算回原始命令的坐标
            const size_t startIndex = tokens.startIndex;
            ASTNode newResult = ASTNode::andNode(node, ASTNode::children(std::move(innerNode)), std::move(tokens), errorReason, ASTNodeId::NODE_STRING_INNER);
            if (errorReason == nullptr && innerResult.second.errorReason == nullptr) {
                for (auto &item: newResult.errorReasons) {
                    item->start = innerResult.second.convert(item->start) + startIndex;
                    item->end = innerResult.second.convert(item->end) + startIndex;
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
                return ASTNode::andNode(node, ASTNode::children(std::move(blockId)), tokenReader.collect(),
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
            return ASTNode::andNode(node, ASTNode::children(std::move(blockId), std::move(astNodeBlockState)), tokenReader.collect(),
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
                return ASTNode::andNode(node, ASTNode::children(std::move(commandName)), tokens, ErrorReasons::emptyCommandName(ErrorReasonLevel::CONTENT_ERROR, tokens), ASTNodeId::NODE_COMMAND_COMMAND);
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
                return ASTNode::andNode(node, ASTNode::children(std::move(commandName)), tokens, ErrorReasons::unknownCommand(ErrorReasonLevel::CONTENT_ERROR, tokens, str), ASTNodeId::NODE_COMMAND_COMMAND);
            }
            ASTNode usage = parse(*currentCommand, tokenReader);
            return ASTNode::andNode(node, ASTNode::children(std::move(commandName), std::move(usage)),
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
                               const std::initializer_list<Node::NodeWithType> childNodes,
                               const ASTNodeId::ASTNodeId &astNodeId = ASTNodeId::NONE) {
        tokenReader.push();
        std::pmr::vector<ASTNode> childASTNodes(getASTMemoryResource());
        for (const auto &item: childNodes) {
            tokenReader.push();
            tokenReader.push();
            size_t index = tokenReader.indexStack.size();
            ASTNode astNode = parse(item, tokenReader);
            debugCheckTokenIndex(item, index, tokenReader);
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
            std::pmr::vector<ASTNode> childNodes = ASTNode::children(std::move(itemId));
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
                errorReason = ErrorReasons::excess(ErrorReasonLevel::EXCESS, tokens, tokens.string());
            }
            return ASTNode::simpleNode(node, std::move(tokens), std::move(errorReason));
        }
    };

    template<>
    struct Parser<Node::NodeNamespaceId> {
        static ASTNode getASTNode(const Node::NodeNamespaceId &node, TokenReader &tokenReader) {
            // namespace:id
            // 字符串中已经包含冒号，因为冒号不是结束字符
            size_t index = tokenReader.indexStack.size();
            auto result = tokenReader.readStringASTNode(node);
            debugCheckTokenIndex(node, index, tokenReader);
            if (result.tokens.isEmpty()) [[unlikely]] {
                return wrapWithError(node, std::move(result), ErrorReasons::incomplete(ErrorReasonLevel::INCOMPLETE, result.tokens));
            }
            if (!node.ignoreError.value_or(false)) [[unlikely]] {
                const TokensView &tokens = result.tokens;
                std::u16string_view str = tokens.string();
                XXH64_hash_t strHash = XXH3_64bits(str.data(), str.size() * sizeof(decltype(str)::value_type));
                if (!tokenReader.idMatches.containsId(node.customContents, strHash)) [[unlikely]] {
                    return wrapWithError(node, std::move(result), ErrorReasons::unknownMeaning(ErrorReasonLevel::INCOMPLETE, tokens, str));
                }
            }
            return result;
        }
    };

    template<>
    struct Parser<Node::NodeNormalId> {
        static ASTNode getASTNode(const Node::NodeNormalId &node, TokenReader &tokenReader) {
            tokenReader.push();
            size_t index = tokenReader.indexStack.size();
            ASTNode result = node.getNormalIdASTNode(node, tokenReader);
            debugCheckTokenIndex(node, index, tokenReader);
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
                return wrapWithError(node, std::move(result), ErrorReasons::incomplete(ErrorReasonLevel::INCOMPLETE, result.tokens));
            }
            if (!node.ignoreError.value_or(true)) [[unlikely]] {
                const TokensView &tokens = result.tokens;
                std::u16string_view str = tokens.string();
                XXH64_hash_t strHash = XXH3_64bits(str.data(), str.size() * sizeof(decltype(str)::value_type));
                if (!tokenReader.idMatches.containsId(node.customContents, strHash)) [[unlikely]] {
                    return wrapWithError(node, std::move(result), ErrorReasons::unknownMeaning(ErrorReasonLevel::INCOMPLETE, tokens, str));
                }
            }
            return result;
        }
    };

    template<>
    struct Parser<Node::NodePerCommand> {
        static ASTNode getASTNode(const Node::NodePerCommand &node, TokenReader &tokenReader) {
            std::pmr::vector<ASTNode> childASTNodes(getASTMemoryResource());
            childASTNodes.reserve(node.startNodes.size());
            for (const auto &item: node.startNodes) {
                tokenReader.push();
                size_t index = tokenReader.indexStack.size();
                childASTNodes.push_back(Parser<Node::NodeWrapped>::getASTNodeWithIsMustAfterSpace(*item, tokenReader, true));
                debugCheckTokenIndex(*item, index, tokenReader);
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
        std::pmr::vector<ASTNode> childNodes(getASTMemoryResource());
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
            // 有前缀且没有在空格处结束时，最终必定保留前缀和数值两个子节点。
            childNodes.reserve(2);
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
            const TokensView &tokens = number.tokens;
            errorReason = ErrorReasons::invalidCoordinate(ErrorReasonLevel::TYPE_ERROR, tokens, tokens.string());
        } else {
            tokenReader.restore();
        }
        childNodes.push_back(std::move(number));
        ASTNode result = ASTNode::andNode(node, std::move(childNodes), tokenReader.collect(), std::move(errorReason));
        // 为了获取补全提示，再嵌套一层or节点
        return {type, ASTNode::orNode(node, ASTNode::children(std::move(result), std::move(preSymbol)), nullptr)};
    }

    template<>
    struct Parser<Node::NodePosition> {
        static ASTNode getASTNode(const Node::NodePosition &node, TokenReader &tokenReader) {
            tokenReader.push();
            // 0 - 绝对坐标，1 - 相对坐标，2 - 局部坐标
            std::pmr::vector<ASTNode> threeChildNodes(getASTMemoryResource());
            threeChildNodes.reserve(3);
            NodeRelativeFloatType::NodeRelativeFloatType types[3];
            for (NodeRelativeFloatType::NodeRelativeFloatType &type: types) {
                std::pair<NodeRelativeFloatType::NodeRelativeFloatType, ASTNode> childNode = getRelativeFloatASTNode(node, tokenReader);
                if (threeChildNodes.empty() && childNode.second.isError() && !childNode.second.tokens.isEmpty()) {
                    tokenReader.pop();
                    TokensView tokens = childNode.second.tokens;
                    return ASTNode::andNode(node, ASTNode::children(std::move(childNode.second)), std::move(tokens), nullptr, ASTNodeId::NODE_POSITION_POSITIONS);
                }
                type = childNode.first;
                threeChildNodes.push_back(std::move(childNode.second));
            }
            //判断有没有错误
            TokensView tokens = tokenReader.collect();
            ASTNode result = ASTNode::andNode(node, std::move(threeChildNodes), std::move(tokens), nullptr, ASTNodeId::NODE_POSITION_POSITIONS);
            if (!result.isError()) [[unlikely]] {
                uint8_t count = 0;
                for (NodeRelativeFloatType::NodeRelativeFloatType item: types) {
                    if (item == NodeRelativeFloatType::LOCAL_COORDINATE) [[unlikely]] {
                        count++;
                    }
                }
                if (count == 1 || count == 2) {
                    TokensView outerTokens = result.tokens;
                    return ASTNode::andNode(node, ASTNode::children(std::move(result)), std::move(outerTokens), nullptr, ASTNodeId::NODE_POSITION_POSITIONS_WITH_ERROR);
                }
            }
            return result;
        }
    };

    std::shared_ptr<ErrorReason> checkNumber(const TokensView &tokens, std::u16string_view str) {
        if (str.empty()) [[unlikely]] {
            return ErrorReasons::emptyRange(ErrorReasonLevel::CONTENT_ERROR, tokens);
        }
        for (size_t i = 0; i < str.length(); ++i) {
            size_t ch = str[i];
            if ((ch < '0' || ch > '9') && (i != 0 || (ch != '-' && ch != '+'))) [[unlikely]] {
                return ErrorReasons::invalidRange(ErrorReasonLevel::CONTENT_ERROR, tokens);
            }
        }
        return nullptr;
    }

    template<>
    struct Parser<Node::NodeRange> {
        static ASTNode getASTNode(const Node::NodeRange &node, TokenReader &tokenReader) {
            TokensView tokens = tokenReader.readTokenView();
            std::u16string_view str = tokens.string();
            std::shared_ptr<ErrorReason> errorReason;
            size_t index = str.find(u"..");
            if (index == std::u16string::npos) [[likely]] {
                errorReason = checkNumber(tokens, str);
            } else if (index == 0) [[unlikely]] {
                errorReason = checkNumber(tokens, std::u16string_view(str).substr(2));
            } else {
                errorReason = checkNumber(tokens, std::u16string_view(str).substr(0, index));
                if (errorReason == nullptr && index + 2 < str.length()) [[unlikely]] {
                    errorReason = checkNumber(tokens, std::u16string_view(str).substr(index + 2));
                }
            }
            return ASTNode::simpleNode(node, std::move(tokens), std::move(errorReason));
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
                return ASTNode::andNode(node, ASTNode::children(std::move(result.second)), std::move(tokens), nullptr, ASTNodeId::NODE_RELATIVE_FLOAT_WITH_ERROR);
            }
            return std::move(result.second);
        }
    };

    template<>
    struct Parser<Node::NodeRepeat> {
        static ASTNode getASTNode(const Node::NodeRepeat &node, TokenReader &tokenReader) {
            tokenReader.push();
            std::pmr::vector<ASTNode> childNodes(getASTMemoryResource());
            while (true) {
                //记录本次迭代的起始位置，防止element解析成功但没有消费任何token导致死循环
                const size_t iterationStartIndex = tokenReader.index;
                ASTNode orNode = parse(node.nodeElement, tokenReader);
                bool isAstNodeError = orNode.childNodes[0].isError();
                bool isBreakAstNodeError = orNode.childNodes[1].isError();
                //isEnd的查找要在orNode被move之前完成
                bool isEnd = node.repeatData->isEnd[orNode.childNodes[0].whichBest];
                childNodes.push_back(std::move(orNode));
                if (!isBreakAstNodeError || isAstNodeError || (!tokenReader.ready() && isEnd)) [[unlikely]] {
                    return ASTNode::andNode(node, std::move(childNodes), tokenReader.collect(), nullptr);
                }
                if (tokenReader.index == iterationStartIndex) [[unlikely]] {
                    //element没有消费任何token，继续循环只会无限重复相同的结果
                    return ASTNode::andNode(node, std::move(childNodes), tokenReader.collect(), nullptr);
                }
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
                    auto errorReason = ErrorReasons::emptyString(ErrorReasonLevel::INCOMPLETE, tokens);
                    return ASTNode::simpleNode(node, std::move(tokens), std::move(errorReason));
                } else {
                    return ASTNode::simpleNode(node, std::move(tokens));
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
                return replaceWithError(node, std::move(result), ErrorReasons::emptyString(ErrorReasonLevel::INCOMPLETE, result.tokens));
            }
            if (!node.canContainSpace) [[unlikely]] {
                if (result.tokens.string().find(' ') != std::u16string::npos) [[unlikely]] {
                    return replaceWithError(node, std::move(result), ErrorReasons::stringContainsSpace(ErrorReasonLevel::CONTENT_ERROR, result.tokens));
                }
                return result;
            }
            std::u16string_view str = result.tokens.string();
            if (str.empty() || str[0] != '"') [[likely]] {
                return result;
            }
            auto convertResult = JsonUtil::DecodedStringView(str);
            if (convertResult.errorReason != nullptr) [[unlikely]] {
                convertResult.errorReason->start += result.tokens.startIndex;
                convertResult.errorReason->end += result.tokens.startIndex;
                return replaceWithError(node, std::move(result), std::move(convertResult.errorReason));
            }
            if (!convertResult.isComplete) [[unlikely]] {
                return replaceWithError(node, std::move(result), ErrorReasons::unclosedString(ErrorReasonLevel::CONTENT_ERROR, result.tokens, str));
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
            size_t index = tokenReader.indexStack.size();
            auto result = node.getTextASTNode(node, tokenReader);
            debugCheckTokenIndex(node, index, tokenReader);
            std::u16string_view str = result.tokens.string();
            if (str != node.data->name) [[unlikely]] {
                if (str.empty()) [[unlikely]] {
                    return wrapWithError(node, std::move(result), ErrorReasons::incomplete(ErrorReasonLevel::CONTENT_ERROR, result.tokens));
                } else {
                    return wrapWithError(node, std::move(result), ErrorReasons::unknownMeaning(ErrorReasonLevel::CONTENT_ERROR, result.tokens, str));
                }
            }
            return result;
        }
    };

    template<>
    struct Parser<Node::NodeAnd> {
        static ASTNode getASTNode(const Node::NodeAnd &node, TokenReader &tokenReader) {
            tokenReader.push();
            std::pmr::vector<ASTNode> childASTNodes(getASTMemoryResource());
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
                                                ErrorReasons::unexpectedSpace(ErrorReasonLevel::CONTENT_ERROR, tokens));
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
            auto key = parse(node.nodeKey, tokenReader);
            if (key.isError()) [[unlikely]] {
                return ASTNode::andNode(node, ASTNode::children(std::move(key)), tokenReader.collect());
            }
            auto separator = parse(node.nodeSeparator, tokenReader);
            if (separator.isError()) [[unlikely]] {
                return ASTNode::andNode(node, ASTNode::children(std::move(key), std::move(separator)), tokenReader.collect());
            }
            auto value = parse(node.nodeValue, tokenReader);
            // 完成解析后才确定一/二/三个子节点，数组仅按实际数量分配一次。
            return ASTNode::andNode(node, ASTNode::children(std::move(key), std::move(separator), std::move(value)), tokenReader.collect());
        }
    };

    template<>
    struct Parser<Node::NodeEqualEntry> {
        static ASTNode getASTNode(const Node::NodeEqualEntry &node, TokenReader &tokenReader) {
            tokenReader.push();
            // key
            ASTNode astNodeKey = parseByChildNode(node, tokenReader, node.nodeKey);
            const bool keyIsError = astNodeKey.isError();
            std::u16string_view key = astNodeKey.tokens.string();
            if (keyIsError) [[unlikely]] {
                return ASTNode::andNode(node, ASTNode::children(std::move(astNodeKey)), tokenReader.collect());
            }
            auto it = std::ranges::find_if(node.equalDatas, [&key](const auto &t) {
                return t.name == key;
            });
            // = or =!
            ASTNode astNodeSeparator = parseByChildNode(
                    node, tokenReader,
                    it == node.equalDatas.end() || it->canUseNotEqual
                            ? Node::NodeWithType(Node::NodeEqualEntry::nodeEqualOrNotEqual)
                            : Node::NodeWithType(Node::NodeEqualEntry::nodeEqual));
            const bool separatorIsError = astNodeSeparator.isError();
            if (separatorIsError) [[unlikely]] {
                return ASTNode::andNode(node, ASTNode::children(std::move(astNodeKey), std::move(astNodeSeparator)), tokenReader.collect());
            }
            //value
            auto value = parse(it == node.equalDatas.end() ? Node::NodeAny::getNodeAny() : it->nodeValue, tokenReader);
            return ASTNode::andNode(node, ASTNode::children(std::move(astNodeKey), std::move(astNodeSeparator), std::move(value)), tokenReader.collect());
        }
    };

    template<>
    struct Parser<Node::NodeList> {
        static std::optional<size_t> countChildren(const Node::NodeList &node, const TokenReader &reader) {
            if (node.nodeLeft.nodeTypeId != Node::NodeTypeId::SINGLE_SYMBOL ||
                node.nodeRight.nodeTypeId != Node::NodeTypeId::SINGLE_SYMBOL ||
                node.nodeSeparator.nodeTypeId != Node::NodeTypeId::SINGLE_SYMBOL) return std::nullopt;
            const auto left = static_cast<const Node::NodeSingleSymbol *>(node.nodeLeft.data)->symbol;
            const auto right = static_cast<const Node::NodeSingleSymbol *>(node.nodeRight.data)->symbol;
            const auto separator = static_cast<const Node::NodeSingleSymbol *>(node.nodeSeparator.data)->symbol;
            if (!((left == u'[' && right == u']') || (left == u'{' && right == u'}')) || separator != u',') return std::nullopt;

            // 首项已成功，剩余每个顶层逗号对应分隔符和元素两项，最后是右括号。
            // 引号内的逗号和括号是 STRING token，不参与计数。异常括号或深层嵌套
            // 保留原来的增长路径；不按输入字符数预估容量，也不改变解析游标。
            std::array<char16_t, 32> openings;
            size_t depth = 0, separators = 0;
            const auto &tokens = reader.lexerResult->allTokens;
            for (size_t i = reader.index; i < tokens.size(); ++i) {
                const auto &token = tokens[i];
                if (token.type == TokenType::LF) return std::nullopt;
                if (token.type != TokenType::SYMBOL) continue;
                const auto ch = token.content[0];
                if (depth == 0 && ch == right) return separators * 2 + 3;
                if (ch == u'[' || ch == u'{') {
                    if (depth == openings.size()) return std::nullopt;
                    openings[depth++] = ch;
                } else if (ch == u']' || ch == u'}') {
                    if (depth == 0 || (openings[depth - 1] == u'[' ? ch != u']' : ch != u'}')) return std::nullopt;
                    --depth;
                } else if (depth == 0 && ch == separator) {
                    ++separators;
                }
            }
            return std::nullopt;
        }

        static ASTNode getASTNode(const Node::NodeList &node, TokenReader &tokenReader) {
            //标记整个[...]，在最后进行收集
            tokenReader.push();
            ASTNode left = parse(node.nodeLeft, tokenReader);
            if (left.isError()) [[unlikely]] {
                return ASTNode::andNode(node, ASTNode::children(std::move(left)), tokenReader.collect());
            }
            std::pmr::vector<ASTNode> childNodes(getASTMemoryResource());
            {
#if CHelperDebug
                size_t startIndex = tokenReader.index;
#endif
                //检测[]中间有没有内容
                auto elementOrRight = parse(node.nodeElementOrRight, tokenReader);
                // 两个内置 OR 始终保留 [内容/分隔符, 右括号] 两个分支。
                // 复用右括号分支，避免每个元素重复解析并构造随即丢弃的诊断。
                // 即使内容分支也成功，右括号成功仍应终止列表。
                bool flag = !elementOrRight.childNodes[1].isError() || elementOrRight.isError();
                if (flag) [[unlikely]] {
                    return ASTNode::andNode(node, ASTNode::children(std::move(left), std::move(elementOrRight)), tokenReader.collect());
                }
                childNodes.reserve(countChildren(node, tokenReader).value_or(2));
                childNodes.push_back(std::move(left));
                childNodes.push_back(std::move(elementOrRight));
#if CHelperDebug
                if (startIndex == tokenReader.index) [[unlikely]] {
                    SPDLOG_WARN("NodeList has some error");
                    return ASTNode::andNode(node, std::move(childNodes), tokenReader.collect());
                }
#endif
            }
            while (true) {
#if CHelperDebug
                size_t startIndex = tokenReader.index;
#endif
                //检测是分隔符还是右括号
                size_t nodeSeparatorOrRightIndex = tokenReader.indexStack.size();
                auto separatorOrRight = parse(node.nodeSeparatorOrRight, tokenReader);
                debugCheckTokenIndex(node.nodeSeparatorOrRight, nodeSeparatorOrRightIndex, tokenReader);
                bool flag = !separatorOrRight.childNodes[1].isError() || separatorOrRight.isError();
                childNodes.push_back(std::move(separatorOrRight));
                if (flag) [[unlikely]] {
                    return ASTNode::andNode(node, std::move(childNodes), tokenReader.collect());
                }
                //检测是不是元素
                size_t nodeElementIndex = tokenReader.indexStack.size();
                ASTNode element = parse(node.nodeElement, tokenReader);
                debugCheckTokenIndex(node.nodeElement, nodeElementIndex, tokenReader);
                flag = element.isError();
                childNodes.push_back(std::move(element));
                if (flag) [[unlikely]] {
                    return ASTNode::andNode(node, std::move(childNodes), tokenReader.collect());
                }
#if CHelperDebug
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
            std::pmr::vector<ASTNode> childASTNodes(getASTMemoryResource());
            // 常见 OR 只有几个分支，终点暂存在栈上；大分支集仍按实际节点数分配。
            // isAttachToEnd 不使用各分支终点，不需要收集它们。
            std::array<size_t, 4> localIndexes;
            std::pmr::vector<size_t> indexes(getASTMemoryResource());
            const bool useLocalIndexes = node.childNodes.size() <= localIndexes.size();
            if (!node.isUseFirst) [[likely]] {
                childASTNodes.reserve(node.childNodes.size());
                if (!node.isAttachToEnd && !useLocalIndexes) indexes.reserve(node.childNodes.size());
            }
            for (const auto &item: node.childNodes) {
                tokenReader.push();
                ASTNode childNode = parse(item, tokenReader);
                bool isNodeError = childNode.isError();
                childASTNodes.push_back(std::move(childNode));
                if (!node.isAttachToEnd) {
                    if (useLocalIndexes) localIndexes[childASTNodes.size() - 1] = tokenReader.index;
                    else
                        indexes.push_back(tokenReader.index);
                }
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
                tokenReader.index = useLocalIndexes ? localIndexes[result.whichBest] : indexes[result.whichBest];
                return result;
            }
        }
    };

    template<>
    struct Parser<Node::NodeSingleSymbol> {
        static ASTNode getASTNode(const Node::NodeSingleSymbol &node, TokenReader &tokenReader) {
            // 直接生成符号诊断，避免先构造通用类型错误的 AST，再用符号错误替换。
            TokensView tokens = tokenReader.readTokenView();
            const Token *token = tokens.isEmpty() ? nullptr : &tokens[0];
            std::shared_ptr<ErrorReason> errorReason;
            if (token == nullptr) [[unlikely]] {
                errorReason = ErrorReasons::requireSymbol(ErrorReasonLevel::INCOMPLETE, tokens, node.symbol);
            } else if (token->type != TokenType::SYMBOL) [[unlikely]] {
                errorReason = ErrorReasons::symbolTypeMismatch(ErrorReasonLevel::TYPE_ERROR, tokens, node.symbol, token->content);
            } else if (token->content.size() != 1 || token->content[0] != node.symbol) [[unlikely]] {
                errorReason = ErrorReasons::symbolContentMismatch(ErrorReasonLevel::CONTENT_ERROR, tokens, node.symbol, token->content);
            }
            return ASTNode::simpleNode(node, std::move(tokens), std::move(errorReason));
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
                return ASTNode::andNode(node, ASTNode::children(std::move(astNode)), tokenReader.collect());
            } else {
                tokenReader.restore();
                TokensView tokens(tokenReader.lexerResult, tokenReader.index, tokenReader.index);
                return ASTNode::orNode(node, ASTNode::children(ASTNode::simpleNode(node, tokens), std::move(astNode)), tokens);
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
            return wrapWithError(node, std::move(astNode), ErrorReasons::invalidBoolean(ErrorReasonLevel::CONTENT_ERROR, astNode.tokens, str));
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
#if CHelperDebug
        //正常情况下data不会为nullptr，未正确初始化的节点应当在CPack加载阶段被拦截，
        //这里是Debug模式下的最后一道防线，防止分发到nullptr的节点数据
        if (node.data == nullptr) [[unlikely]] {
            throw std::runtime_error("node data is null");
        }
#endif
        return Node::dispatchNodeType(node.nodeTypeId, [&]<class NodeType>() {
            return Parser<NodeType>::getASTNode(*reinterpret_cast<const NodeType *>(node.data), tokenReader);
        });
    }

    ASTNode parse(const std::u16string_view content, const Node::NodeWithType &mainNode) {
        ErrorReasonMemoryScope errorMemory;
        InnerLexerScope lexerScope;
        TokenReader tokenReader(lexerScope.lex(content));
        size_t index = tokenReader.indexStack.size();
        auto result = parse(mainNode, tokenReader);
        debugCheckTokenIndex(mainNode, index, tokenReader);
        return result;
    }

    ASTNode parse(const std::u16string_view content, const CPack &cpack) {
        return parse(content, cpack.mainNode);
    }

}// namespace CHelper::Parser
