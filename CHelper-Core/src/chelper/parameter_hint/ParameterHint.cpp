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
#include <chelper/parameter_hint/ParameterHint.h>

#define CHELPER_GET_PARAMETER_HINT(v1)                                                                              \
    case Node::NodeTypeId::v1:                                                                                      \
        parameterHint = ParameterHint<typename Node::NodeTypeDetail<Node::NodeTypeId::v1>::Type>::getHint(astNode); \
        break;

namespace CHelper::ParameterHint {

    template<class NodeType, class = void>
    struct ParameterHint {
        static std::optional<std::u16string> getHint(const ASTNode &astNode) {
            return std::nullopt;
        }
    };

    template<class NodeType>
    struct ParameterHint<NodeType, std::enable_if_t<std::is_base_of_v<Node::NodeSerializable, NodeType>>> {
        static std::optional<std::u16string> getHint(const ASTNode &astNode) {
            return static_cast<const NodeType *>(astNode.node.data)->description;
        }
    };

    template<>
    struct ParameterHint<Node::NodeBlock> {
        static std::optional<std::u16string> getHint(const ASTNode &astNode) {
            if (astNode.id == ASTNodeId::NODE_BLOCK_BLOCK_ID) {
                return u"方块ID";
            } else if (astNode.id == ASTNodeId::NODE_BLOCK_BLOCK_STATE) {
                return u"方块状态";
            } else {
                return std::nullopt;
            }
        }
    };

    template<>
    struct ParameterHint<Node::NodeCommand> {
        static std::optional<std::u16string> getHint(const ASTNode &astNode) {
            if (astNode.id == ASTNodeId::NODE_COMMAND_COMMAND_NAME) {
                return u"命令的名字";
            } else {
                return std::nullopt;
            }
        }
    };

    template<>
    struct ParameterHint<Node::NodeItem> {
        static std::optional<std::u16string> getHint(const ASTNode &astNode) {
            return std::nullopt;
        }
    };

    template<>
    struct ParameterHint<Node::NodeRepeat> {
        static std::optional<std::u16string> getHint(const ASTNode &astNode) {
            if (astNode.tokens.isEmpty()) [[unlikely]] {
                return reinterpret_cast<const Node::NodeRepeat *>(astNode.node.data)->description;
            } else {
                return std::nullopt;
            }
        }
    };

    std::optional<std::u16string> getParameterHint(const ASTNode &astNode, size_t index) {
        if (index < astNode.tokens.startIndex || index > astNode.tokens.endIndex) [[unlikely]] {
            return std::nullopt;
        }
        if (!astNode.isAllSpaceError()) [[unlikely]] {
#ifdef CHelperTest
            Profile::push("get parameter hint: {} {}", FORMAT_ARG(utf8::utf16to8(astNode.tokens.toString())), FORMAT_ARG(Node::getNodeTypeName(astNode.node.nodeTypeId)));
#endif
            std::optional<std::u16string> parameterHint;
            switch (astNode.node.nodeTypeId) {
                CODEC_PASTE(CHELPER_GET_PARAMETER_HINT, CHELPER_NODE_TYPES)
                default:
                    CHELPER_UNREACHABLE();
            }
#ifdef CHelperTest
            Profile::pop();
#endif
            if (parameterHint.has_value()) {
                return parameterHint;
            }
        }
        switch (astNode.mode) {
            case ASTNodeMode::NONE:
                return std::nullopt;
            case ASTNodeMode::AND:
                for (const ASTNode &item: astNode.childNodes) {
                    auto parameterHint = getParameterHint(item, index);
                    if (parameterHint.has_value()) [[unlikely]] {
                        return parameterHint;
                    }
                }
                return std::nullopt;
            case ASTNodeMode::OR:
                return getParameterHint(astNode.getBestNode(), index);
            default:
                CHELPER_UNREACHABLE();
        }
    }

}// namespace CHelper::ParameterHint
