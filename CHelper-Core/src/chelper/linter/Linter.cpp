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

#define CHELPER_LINT(v1)                                                                                          \
    case Node::NodeTypeId::v1:                                                                                    \
        isDirty = Linter<typename Node::NodeTypeDetail<Node::NodeTypeId::v1>::Type>::lint(astNode, errorReasons); \
        break;

namespace CHelper::Linter {

    template<class NodeType>
    struct Linter {
        static bool lint(const ASTNode &astNode, std::vector<std::shared_ptr<ErrorReason>> &errorReasons) {
            return false;
        }
    };

    template<>
    struct Linter<Node::NodeJsonString> {
        static bool lint(const ASTNode &astNode, std::vector<std::shared_ptr<ErrorReason>> &errorReasons) {
            if (astNode.id == ASTNodeId::NODE_STRING_INNER) [[unlikely]] {
                auto convertResult = JsonUtil::jsonString2String(astNode.tokens.string());
                for (const auto &item: getErrorsExceptParseError(astNode.childNodes[0])) {
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
        static bool lint(const ASTNode &astNode, std::vector<std::shared_ptr<ErrorReason>> &errorReasons) {
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
            errorReasons.push_back(ErrorReason::idError(astNode.tokens, fmt::format(u"找不到命令名 -> {}", str)));
            return true;
        }
    };

    template<>
    struct Linter<Node::NodeNamespaceId> {
        static bool lint(const ASTNode &astNode, std::vector<std::shared_ptr<ErrorReason>> &errorReasons) {
            if (astNode.isError()) [[unlikely]] {
                return true;
            }
            const auto &node = *reinterpret_cast<const Node::NodeNamespaceId *>(astNode.node.data);
            std::u16string_view str = astNode.tokens.string();
            XXH64_hash_t strHash = XXH3_64bits(str.data(), str.size() * sizeof(decltype(str)::value_type));
            if (std::ranges::all_of(*node.customContents, [&strHash](const auto &item) {
                    return !item->fastMatch(strHash) && !item->getIdWithNamespace()->fastMatch(strHash);
                })) [[unlikely]] {
                errorReasons.push_back(ErrorReason::idError(astNode.tokens, fmt::format(u"找不到ID -> {}", str)));
            }
            return true;
        }
    };

    template<>
    struct Linter<Node::NodeNormalId> {
        static bool lint(const ASTNode &astNode, std::vector<std::shared_ptr<ErrorReason>> &errorReasons) {
            if (astNode.isError()) [[unlikely]] {
                return true;
            }
            const auto &node = *reinterpret_cast<const Node::NodeNormalId *>(astNode.node.data);
            std::u16string_view str = astNode.tokens.string();
            XXH64_hash_t strHash = XXH3_64bits(str.data(), str.size() * sizeof(decltype(str)::value_type));
            if (std::ranges::all_of(*node.customContents, [&strHash](const auto &item) {
                    return !item->fastMatch(strHash);
                })) [[unlikely]] {
                errorReasons.push_back(ErrorReason::idError(astNode.tokens, fmt::format(u"找不到ID -> {}", str)));
            }
            return true;
        }
    };

    template<>
    struct Linter<Node::NodePosition> {
        static bool lint(const ASTNode &astNode, std::vector<std::shared_ptr<ErrorReason>> &errorReasons) {
            if (!astNode.isError() && astNode.id == ASTNodeId::NODE_POSITION_POSITIONS_WITH_ERROR) [[unlikely]] {
                errorReasons.push_back(ErrorReason::logicError(astNode.tokens, u"绝对坐标和相对坐标不能与局部坐标混用"));
                return true;
            } else {
                return false;
            }
        }
    };

    template<>
    struct Linter<Node::NodeRelativeFloat> {
        static bool lint(const ASTNode &astNode, std::vector<std::shared_ptr<ErrorReason>> &errorReasons) {
            if (!astNode.isError() && astNode.id == ASTNodeId::NODE_RELATIVE_FLOAT_WITH_ERROR) [[unlikely]] {
                errorReasons.push_back(ErrorReason::logicError(astNode.tokens, u"不能使用局部坐标"));
                return true;
            } else {
                return false;
            }
        }
    };

    template<>
    struct Linter<Node::NodeEqualEntry> {
        static bool lint(const ASTNode &astNode, std::vector<std::shared_ptr<ErrorReason>> &errorReasons) {
            if (astNode.childNodes.size() == 3 && astNode.childNodes[2].node.data == Node::NodeAny::getNodeAny().data) {
                errorReasons.push_back(ErrorReason::idError(astNode.tokens, fmt::format(u"未知的目标选择器参数 -> {}", astNode.childNodes[0].tokens.string())));
                return true;
            } else {
                return false;
            }
        }
    };

    template<>
    struct Linter<Node::NodeJsonList> {
        static bool lint(const ASTNode &astNode, std::vector<std::shared_ptr<ErrorReason>> &errorReasons) {
            if (!astNode.isError() && astNode.id == ASTNodeId::NODE_JSON_ALL_LIST) [[unlikely]] {
                errorReasons.push_back(ErrorReason::idError(astNode.tokens, fmt::format(u"未知的json参数 -> {}", astNode.tokens.string())));
                return true;
            } else {
                return false;
            }
        }
    };

    template<>
    struct Linter<Node::NodeJsonEntry> {
        static bool lint(const ASTNode &astNode, std::vector<std::shared_ptr<ErrorReason>> &errorReasons) {
            if (!reinterpret_cast<Node::NodeJsonEntry *>(astNode.node.data)->nodeEntry.has_value()) [[unlikely]] {
                errorReasons.push_back(ErrorReason::idError(astNode.tokens, fmt::format(u"未知的json参数 -> {}", astNode.tokens.string())));
                return true;
            } else {
                return false;
            }
        }
    };

    template<class T, bool isJson>
    struct Linter<Node::NodeTemplateNumber<T, isJson>> {
        static bool lint(const ASTNode &astNode, std::vector<std::shared_ptr<ErrorReason>> &errorReasons) {
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
                errorReasons.push_back(ErrorReason::idError(
                        astNode.tokens,
                        fmt::format(
                                u"数值不在范围[{}, {}]内 -> {}",
                                node.min.value_or(std::numeric_limits<T>::lowest()),
                                node.max.value_or(std::numeric_limits<T>::max()),
                                astNode.tokens.string())));
            }
            return true;
        }
    };

    void lint(const ASTNode &astNode, std::vector<std::shared_ptr<ErrorReason>> &errorReasons) {
        if (!astNode.isAllSpaceError()) [[unlikely]] {
#ifdef CHelperTest
            Profile::push("collect id errors: {}", FORMAT_ARG(Node::getNodeTypeName(astNode.node.nodeTypeId)));
#endif
            bool isDirty = false;
            switch (astNode.node.nodeTypeId) {
                CODEC_PASTE(CHELPER_LINT, CHELPER_NODE_TYPES)
                default:
                    CHELPER_UNREACHABLE();
            }
#ifdef CHelperTest
            Profile::pop();
#endif
            if (isDirty) [[unlikely]] {
                return;
            }
        }
        switch (astNode.mode) {
            case ASTNodeMode::NONE:
                break;
            case ASTNodeMode::AND:
                for (const ASTNode &item: astNode.childNodes) {
                    lint(item, errorReasons);
                }
                break;
            case ASTNodeMode::OR:
                lint(astNode.getBestNode(), errorReasons);
                break;
        }
    }

    std::vector<std::shared_ptr<ErrorReason>> sortByLevel(const std::vector<std::shared_ptr<ErrorReason>> &input) {
        std::vector<std::shared_ptr<ErrorReason>> output;
        uint8_t i = ErrorReasonLevel::maxLevel;
        while (true) {
            for (const auto &item: input) {
                if (item->level == i) [[unlikely]] {
                    output.push_back(item);
                }
            }
            if (i == 0) [[unlikely]] {
                break;
            }
            --i;
        }
        return output;
    }

    std::vector<std::shared_ptr<ErrorReason>> getErrorsExceptParseError(const ASTNode &astNode) {
        std::vector<std::shared_ptr<ErrorReason>> input;
#ifdef CHelperTest
        Profile::push("start get errors except parse error: {} {}", FORMAT_ARG(utf8::utf16to8(astNode.tokens.toString())), FORMAT_ARG(Node::getNodeTypeName(astNode.node.nodeTypeId)));
#endif
        lint(astNode, input);
#ifdef CHelperTest
        Profile::pop();
#endif
        return sortByLevel(input);
    }

    std::vector<std::shared_ptr<ErrorReason>> getErrorReasons(const ASTNode &astNode) {
        std::vector<std::shared_ptr<ErrorReason>> result = astNode.errorReasons;
#ifdef CHelperTest
        Profile::push("start getting error reasons: {} {}", FORMAT_ARG(utf8::utf16to8(astNode.tokens.toString())), FORMAT_ARG(Node::getNodeTypeName(astNode.node.nodeTypeId)));
#endif
        lint(astNode, result);
#ifdef CHelperTest
        Profile::pop();
#endif
        return sortByLevel(result);
    }

}// namespace CHelper::Linter
