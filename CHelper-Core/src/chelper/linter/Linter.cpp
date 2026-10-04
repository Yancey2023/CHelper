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

#include <chelper/linter/Linter.h>
#include <chelper/node/NodeType.h>
#include <chelper/parser/ErrorReasonFactory.h>
#include <chelper/util/IdMatchCache.h>
#include <chelper/util/JsonUtil.h>

namespace CHelper::Linter {

    using QueryState = IdMatchCache;

    std::vector<std::shared_ptr<ErrorReason>> getErrorsExceptParseError(const ASTNode &astNode, QueryState &state);

    template<class NodeType>
    struct Linter {};

    template<>
    struct Linter<Node::NodeJsonString> {
        static bool lint(const ASTNode &astNode, std::vector<std::shared_ptr<ErrorReason>> &errorReasons, QueryState &state) {
            if (astNode.id == ASTNodeId::NODE_STRING_INNER) [[unlikely]] {
                auto convertResult = JsonUtil::DecodedStringView(astNode.tokens.string());
                for (const auto &item: getErrorsExceptParseError(astNode.childNodes[0], state)) {
                    item->start = convertResult.convert(item->start) + astNode.tokens.startIndex;
                    item->end = convertResult.convert(item->end) + astNode.tokens.startIndex;
                    errorReasons.push_back(item);
                }
            }
            return true;
        }
    };

    template<>
    struct Linter<Node::NodeCommandName> {
        static bool lint(const ASTNode &astNode, std::vector<std::shared_ptr<ErrorReason>> &errorReasons, QueryState &state) {
            if (astNode.isError()) [[unlikely]] {
                return true;
            }
            const auto &node = *reinterpret_cast<const Node::NodeCommandName *>(astNode.node.data);
            std::u16string_view str = astNode.tokens.string();
            for (const auto &command: *node.commands) {
                for (const auto &name: command.name) {
                    if (str == name) [[unlikely]] {
                        return true;
                    }
                }
            }
            errorReasons.push_back(ErrorReasons::unknownCommandName(ErrorReasonLevel::ID_ERROR, astNode.tokens, str));
            return true;
        }
    };

    template<>
    struct Linter<Node::NodeNamespaceId> {
        static bool lint(const ASTNode &astNode, std::vector<std::shared_ptr<ErrorReason>> &errorReasons, QueryState &state) {
            if (astNode.isError()) [[unlikely]] {
                return true;
            }
            const auto &node = *reinterpret_cast<const Node::NodeNamespaceId *>(astNode.node.data);
            std::u16string_view str = astNode.tokens.string();
            XXH64_hash_t strHash = XXH3_64bits(str.data(), str.size() * sizeof(decltype(str)::value_type));
            if (!state.contains(node.customContents, strHash, [strHash](const auto &item) {
                    return item->fastMatch(strHash) || item->getIdWithNamespace()->fastMatch(strHash);
                })) [[unlikely]] {
                errorReasons.push_back(ErrorReasons::unknownId(ErrorReasonLevel::ID_ERROR, astNode.tokens, str));
            }
            return true;
        }
    };

    template<>
    struct Linter<Node::NodeNormalId> {
        static bool lint(const ASTNode &astNode, std::vector<std::shared_ptr<ErrorReason>> &errorReasons, QueryState &state) {
            if (astNode.isError()) [[unlikely]] {
                return true;
            }
            const auto &node = *reinterpret_cast<const Node::NodeNormalId *>(astNode.node.data);
            std::u16string_view str = astNode.tokens.string();
            XXH64_hash_t strHash = XXH3_64bits(str.data(), str.size() * sizeof(decltype(str)::value_type));
            if (!state.contains(node.customContents, strHash, [strHash](const auto &item) {
                    return item->fastMatch(strHash);
                })) [[unlikely]] {
                errorReasons.push_back(ErrorReasons::unknownId(ErrorReasonLevel::ID_ERROR, astNode.tokens, str));
            }
            return true;
        }
    };

    template<>
    struct Linter<Node::NodePosition> {
        static bool lint(const ASTNode &astNode, std::vector<std::shared_ptr<ErrorReason>> &errorReasons, QueryState &state) {
            if (!astNode.isError() && astNode.id == ASTNodeId::NODE_POSITION_POSITIONS_WITH_ERROR) [[unlikely]] {
                errorReasons.push_back(ErrorReasons::mixedCoordinates(ErrorReasonLevel::LOGIC_ERROR, astNode.tokens));
                return true;
            } else {
                return false;
            }
        }
    };

    template<>
    struct Linter<Node::NodeRelativeFloat> {
        static bool lint(const ASTNode &astNode, std::vector<std::shared_ptr<ErrorReason>> &errorReasons, QueryState &state) {
            if (!astNode.isError() && astNode.id == ASTNodeId::NODE_RELATIVE_FLOAT_WITH_ERROR) [[unlikely]] {
                errorReasons.push_back(ErrorReasons::localCoordinateDisallowed(ErrorReasonLevel::LOGIC_ERROR, astNode.tokens));
                return true;
            } else {
                return false;
            }
        }
    };

    template<>
    struct Linter<Node::NodeEqualEntry> {
        static bool lint(const ASTNode &astNode, std::vector<std::shared_ptr<ErrorReason>> &errorReasons, QueryState &state) {
            if (astNode.childNodes.size() == 3 && astNode.childNodes[2].node.data == Node::NodeAny::getNodeAny().data) {
                errorReasons.push_back(ErrorReasons::unknownSelectorArgument(ErrorReasonLevel::ID_ERROR, astNode.tokens, astNode.childNodes[0].tokens.string()));
                return true;
            } else {
                return false;
            }
        }
    };

    template<>
    struct Linter<Node::NodeJsonList> {
        static bool lint(const ASTNode &astNode, std::vector<std::shared_ptr<ErrorReason>> &errorReasons, QueryState &state) {
            if (!astNode.isError() && astNode.id == ASTNodeId::NODE_JSON_ALL_LIST) [[unlikely]] {
                errorReasons.push_back(ErrorReasons::unknownJsonArgument(ErrorReasonLevel::ID_ERROR, astNode.tokens, astNode.tokens.string()));
                return true;
            } else {
                return false;
            }
        }
    };

    template<>
    struct Linter<Node::NodeJsonEntry> {
        static bool lint(const ASTNode &astNode, std::vector<std::shared_ptr<ErrorReason>> &errorReasons, QueryState &state) {
            if (!reinterpret_cast<Node::NodeJsonEntry *>(astNode.node.data)->nodeEntry.has_value()) [[unlikely]] {
                errorReasons.push_back(ErrorReasons::unknownJsonArgument(ErrorReasonLevel::ID_ERROR, astNode.tokens, astNode.tokens.string()));
                return true;
            } else {
                return false;
            }
        }
    };

    template<class T, bool isJson>
    struct Linter<Node::NodeTemplateNumber<T, isJson>> {
        static bool lint(const ASTNode &astNode, std::vector<std::shared_ptr<ErrorReason>> &errorReasons, QueryState &state) {
            if (astNode.isError()) [[unlikely]] {
                return true;
            }
            auto &node = *static_cast<const Node::NodeTemplateNumber<T, isJson> *>(astNode.node.data);
            std::string str = utf8::utf16to8(astNode.tokens.string());
            char *end;
            auto value = Node::NodeTemplateNumber<T, isJson>::str2number(str, end);
            if (end == str.c_str() || *end != '\0' ||
                (std::numeric_limits<T>::has_infinity &&
                 (value == std::numeric_limits<T>::infinity() ||
                  value == -std::numeric_limits<T>::infinity())) ||
                value < node.min.value_or(std::numeric_limits<T>::lowest()) ||
                value > node.max.value_or(std::numeric_limits<T>::max())) [[unlikely]] {
                errorReasons.push_back(ErrorReasons::numberOutOfRange(ErrorReasonLevel::ID_ERROR, astNode.tokens, node.min.value_or(std::numeric_limits<T>::lowest()), node.max.value_or(std::numeric_limits<T>::max()), astNode.tokens.string()));
            }
            return true;
        }
    };

    void lint(const ASTNode &astNode, std::vector<std::shared_ptr<ErrorReason>> &errorReasons, QueryState &state) {
        const ASTNode *next = &astNode;
        while (true) {
            const auto &current = *next;
            bool isDirty = Node::dispatchNodeType(current.node.nodeTypeId, [&]<class NodeType>() {
                if constexpr (requires { Linter<NodeType>::lint(current, errorReasons, state); }) {
                    // 容器、分支和符号等节点没有语义检查，不读取其结构诊断列表。
                    return !current.isAllSpaceError() && Linter<NodeType>::lint(current, errorReasons, state);
                } else {
                    return false;
                }
            });
            if (isDirty) [[unlikely]]
                return;
            // 单子节点和 OR 最佳分支继续在本层检查；多子节点仍按原顺序递归。
            switch (current.mode) {
                case ASTNodeMode::AND:
                    if (current.childNodes.size() == 1) {
                        next = &current.childNodes.front();
                        continue;
                    }
                    for (const auto &item: current.childNodes) lint(item, errorReasons, state);
                    return;
                case ASTNodeMode::OR:
                    next = &current.getBestNode();
                    continue;
                default:
                    return;
            }
        }
    }

    std::vector<std::shared_ptr<ErrorReason>> sortByLevel(std::vector<std::shared_ptr<ErrorReason>> &&input) {
        if ((input.empty() || input.front()->level <= ErrorReasonLevel::maxLevel) &&
            std::ranges::is_sorted(input, [](const auto &left, const auto &right) {
                return left->level > right->level;
            })) {
            return std::move(input);
        }
        // 错误等级是固定的7个桶；按桶扫描保持同等级错误的原有顺序，时间复杂度为O(7n)=O(n)。
        std::vector<std::shared_ptr<ErrorReason>> output;
        output.reserve(input.size());
        uint8_t level = ErrorReasonLevel::maxLevel;
        while (true) {
            for (auto &item: input) {
                if (item->level == level) [[unlikely]] {
                    output.push_back(item);
                }
            }
            if (level == 0) [[unlikely]] {
                break;
            }
            --level;
        }
        return output;
    }

    std::vector<std::shared_ptr<ErrorReason>> getErrorsExceptParseError(const ASTNode &astNode, QueryState &state) {
        std::vector<std::shared_ptr<ErrorReason>> input;
        lint(astNode, input, state);
        return sortByLevel(std::move(input));
    }

    std::vector<std::shared_ptr<ErrorReason>> getErrorsExceptParseError(const ASTNode &astNode) {
        ErrorReasonMemoryScope errorMemory;
        QueryState state;
        auto result = getErrorsExceptParseError(astNode, state);
        for (auto &item: result) item = item->materializedCopy();
        return result;
    }

    std::vector<std::shared_ptr<ErrorReason>> getErrorReasons(const ASTNode &astNode) {
        ErrorReasonMemoryScope errorMemory;
        QueryState state;
        std::vector<std::shared_ptr<ErrorReason>> result;
        result.reserve(astNode.errorReasons.size());
        result.insert(result.end(), astNode.errorReasons.begin(), astNode.errorReasons.end());
        lint(astNode, result, state);
        result = sortByLevel(std::move(result));
        for (auto &item: result) item = item->materializedCopy();
        return result;
    }

}// namespace CHelper::Linter
