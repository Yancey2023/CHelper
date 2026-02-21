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

#include <chelper/auto_suggestion/AutoSuggestion.h>
#include <chelper/node/NodeType.h>

#define CHELPER_COLLECT_AUTO_SUGGESTION(v1)                                                                                                   \
    case Node::NodeTypeId::v1:                                                                                                                \
        isDirty = AutoSuggestion<typename Node::NodeTypeDetail<Node::NodeTypeId::v1>::Type>::collectSuggestions(astNode, index, suggestions); \
        break;

namespace CHelper::AutoSuggestion {

    static std::shared_ptr<NormalId> spaceId = NormalId::make(u" ", u"空格");
    static std::shared_ptr<NormalId> doubleQuoteMask = NormalId::make(u"\"", u"双引号");
    static std::shared_ptr<NormalId> rangeSymbol = NormalId::make(u"..", u"范围");

    template<class NodeType>
    struct AutoSuggestion {
        static bool collectSuggestions(const ASTNode &astNode, size_t index, Suggestions &suggestions) {
            return false;
        }
    };

    template<>
    struct AutoSuggestion<Node::NodeBlock> {
        static bool collectSuggestions(const ASTNode &astNode, size_t index, Suggestions &suggestions) {
            if (astNode.id == ASTNodeId::NODE_BLOCK_BLOCK_AND_BLOCK_STATE && !astNode.isError() &&
                astNode.childNodes.size() == 1 && index == astNode.tokens.endIndex) [[likely]] {
                suggestions.addSymbolSuggestion({index, index, false, Node::NodeBlock::nodeBlockStateLeftBracket.normalId});
            }
            return false;
        }
    };

    template<bool isJson>
    struct AutoSuggestion<Node::NodeTemplateBoolean<isJson>> {
        static bool collectSuggestions(const ASTNode &astNode, size_t index, Suggestions &suggestions) {
            const auto &node = *static_cast<const Node::NodeTemplateBoolean<isJson> *>(astNode.node.data);
            KMPMatcher kmpMatcher(astNode.tokens.string().substr(0, index - astNode.tokens.startIndex));
            if (kmpMatcher.match(u"true") != std::u16string::npos) [[unlikely]] {
                suggestions.addLiteralSuggestion({astNode.tokens, true,
                                                  NormalId::make(u"true", node.descriptionTrue)});
            }
            if (kmpMatcher.match(u"false") != std::u16string::npos) [[unlikely]] {
                suggestions.addLiteralSuggestion({astNode.tokens, true,
                                                  NormalId::make(u"false", node.descriptionFalse)});
            }
            return true;
        }
    };

    template<>
    struct AutoSuggestion<Node::NodeCommand> {
        static bool collectSuggestions(const ASTNode &astNode, size_t index, Suggestions &suggestions) {
            const auto &node = *reinterpret_cast<const Node::NodeCommand *>(astNode.node.data);
            if (astNode.id != ASTNodeId::NODE_COMMAND_COMMAND_NAME) [[unlikely]] {
                return false;
            }
            if (index == 0 && astNode.tokens.isEmpty()) [[likely]] {
                suggestions.addSymbolSuggestion({0, 0, false, Node::NodeCommand::nodeCommandStart.normalId});
            }
            std::u16string_view str = astNode.tokens.string().substr(0, index - astNode.tokens.startIndex);
            std::vector<std::shared_ptr<NormalId>> nameStartOf, nameContain, descriptionContain;
            for (const auto &item: *node.commands) {
                //通过名字进行搜索
                bool flag = false;
                for (const auto &item2: item.name) {
                    size_t index1 = item2.find(str);
                    if (index1 != std::u16string::npos) [[unlikely]] {
                        if (index1 == 0) [[unlikely]] {
                            nameStartOf.push_back(NormalId::make(item2, item.description));
                        } else {
                            nameContain.push_back(NormalId::make(item2, item.description));
                        }
                        flag = true;
                    }
                }
                if (flag) [[unlikely]] {
                    continue;
                }
                //通过介绍进行搜索
                if (item.description.has_value() &&
                    item.description.value().find(str) != std::u16string::npos) [[unlikely]] {
                    for (const auto &item2: item.name) {
                        descriptionContain.push_back(NormalId::make(item2, item.description));
                    }
                }
            }
            auto compare = [](const auto &item1, const auto &item2) {
                return item1->name < item2->name;
            };
            std::ranges::sort(nameStartOf, compare);
            std::ranges::sort(nameContain, compare);
            std::ranges::sort(descriptionContain, compare);
            size_t start = astNode.tokens.startIndex;
            size_t end = astNode.tokens.endIndex;
            suggestions.reserveIdSuggestion(nameStartOf.size() + nameContain.size() + descriptionContain.size());
            for (const auto &item: nameStartOf) {
                suggestions.addIdSuggestion({start, end, true, item});
            }
            for (const auto &item: nameContain) {
                suggestions.addIdSuggestion({start, end, true, item});
            }
            for (const auto &item: descriptionContain) {
                suggestions.addIdSuggestion({start, end, true, item});
            }
            return true;
        }
    };

    template<>
    struct AutoSuggestion<Node::NodeCommandName> {
        static bool collectSuggestions(const ASTNode &astNode, size_t index, Suggestions &suggestions) {
            const auto &node = *reinterpret_cast<const Node::NodeCommandName *>(astNode.node.data);
            std::u16string_view str = astNode.tokens.string().substr(0, index - astNode.tokens.startIndex);
            std::vector<std::shared_ptr<NormalId>> nameStartOf, nameContain, descriptionContain;
            for (const auto &item: *node.commands) {
                bool flag = false;
                for (const auto &item2: item.name) {
                    //通过名字进行搜索
                    size_t index1 = item2.find(str);
                    if (index1 != std::u16string::npos) [[unlikely]] {
                        if (index1 == 0) [[unlikely]] {
                            nameStartOf.push_back(NormalId::make(item2, item.description));
                        } else {
                            nameContain.push_back(NormalId::make(item2, item.description));
                        }
                        flag = true;
                    }
                }
                if (flag) [[unlikely]] {
                    continue;
                }
                //通过介绍进行搜索
                if (item.description.has_value() && item.description.value().find(str) != std::u16string::npos) [[unlikely]] {
                    for (const auto &item2: item.name) {
                        descriptionContain.push_back(NormalId::make(item2, item.description));
                    }
                }
            }
            auto compare = [](const auto &item1, const auto &item2) {
                return item1->name < item2->name;
            };
            std::ranges::sort(nameStartOf, compare);
            std::ranges::sort(nameContain, compare);
            std::ranges::sort(descriptionContain, compare);
            size_t start = astNode.tokens.startIndex;
            size_t end = astNode.tokens.endIndex;
            suggestions.reserveIdSuggestion(nameStartOf.size() + nameContain.size() + descriptionContain.size());
            for (const auto &item: nameStartOf) {
                suggestions.addIdSuggestion({start, end, true, item});
            }
            for (const auto &item: nameContain) {
                suggestions.addIdSuggestion({start, end, true, item});
            }
            for (const auto &item: descriptionContain) {
                suggestions.addIdSuggestion({start, end, true, item});
            }
            return true;
        }
    };

    template<>
    struct AutoSuggestion<Node::NodeNamespaceId> {
        static bool collectSuggestions(const ASTNode &astNode, size_t index, Suggestions &suggestions) {
            const auto &node = *reinterpret_cast<const Node::NodeNamespaceId *>(astNode.node.data);
            KMPMatcher kmpMatcher(astNode.tokens.string().substr(0, index - astNode.tokens.startIndex));
            std::vector<std::shared_ptr<NormalId>> nameStartOf, nameContain;
            std::vector<std::shared_ptr<NormalId>> namespaceStartOf, namespaceContain;
            std::vector<std::shared_ptr<NamespaceId>> descriptionContain;
            for (const auto &item: *node.customContents) {
                //通过名字进行搜索
                size_t index1 = kmpMatcher.match(item->getIdWithNamespace()->name);
                if (index1 != std::u16string::npos) [[unlikely]] {
                    //带有命名空间
                    if (index1 == 0) [[unlikely]] {
                        namespaceStartOf.push_back(item->getIdWithNamespace());
                    } else {
                        namespaceContain.push_back(item->getIdWithNamespace());
                    }
                    //省略minecraft命名空间
                    constexpr size_t defaultNamespaceSize = std::size(u"minecraft");
                    constexpr size_t defaultNamespacePrefixSize = std::size(u"minecraft:");
                    if (!item->idNamespace.has_value() || (item->idNamespace.value().size() == defaultNamespaceSize && item->idNamespace.value() == u"minecraft")) [[likely]] {
                        if (index1 == defaultNamespacePrefixSize) [[unlikely]] {
                            nameStartOf.push_back(item);
                        } else if (index1 > defaultNamespacePrefixSize) [[unlikely]] {
                            nameContain.push_back(item);
                        } else {
                            size_t index2 = kmpMatcher.match(item->name);
                            if (index2 == 0) [[unlikely]] {
                                nameStartOf.push_back(item);
                            } else if (index2 != std::u16string::npos) [[unlikely]] {
                                nameContain.push_back(item);
                            }
                        }
                    }
                    continue;
                }
                //通过介绍进行搜索
                if (item->description.has_value() && kmpMatcher.match(item->description.value()) != std::u16string::npos) [[unlikely]] {
                    descriptionContain.push_back(item);
                }
            }
            size_t start = astNode.tokens.startIndex;
            size_t end = astNode.tokens.endIndex;
            suggestions.reserveIdSuggestion(nameStartOf.size() + nameContain.size() +
                                            namespaceStartOf.size() + namespaceContain.size() +
                                            2 * descriptionContain.size());
            for (const auto &item: nameStartOf) {
                suggestions.addIdSuggestion({start, end, node.getIsMustAfterSpace(), item});
            }
            for (const auto &item: nameContain) {
                suggestions.addIdSuggestion({start, end, node.getIsMustAfterSpace(), item});
            }
            for (const auto &item: namespaceStartOf) {
                suggestions.addIdSuggestion({start, end, node.getIsMustAfterSpace(), item});
            }
            for (const auto &item: namespaceContain) {
                suggestions.addIdSuggestion({start, end, node.getIsMustAfterSpace(), item});
            }
            for (const auto &item: descriptionContain) {
                suggestions.addIdSuggestion({start, end, node.getIsMustAfterSpace(), item});
            }
            for (const auto &item: descriptionContain) {
                suggestions.addIdSuggestion({start, end, node.getIsMustAfterSpace(), item->getIdWithNamespace()});
            }
            return true;
        }
    };

    template<>
    struct AutoSuggestion<Node::NodeNormalId> {
        static bool collectSuggestions(const ASTNode &astNode, size_t index, Suggestions &suggestions) {
            const auto &node = *reinterpret_cast<const Node::NodeNormalId *>(astNode.node.data);
            KMPMatcher kmpMatcher(astNode.tokens.string().substr(0, index - astNode.tokens.startIndex));
            std::vector<std::shared_ptr<NormalId>> nameStartOf, nameContain, descriptionContain;
            for (const auto &item: *node.customContents) {
                //通过名字进行搜索
                size_t index1 = kmpMatcher.match(item->name);
                if (index1 != std::u16string::npos) [[unlikely]] {
                    if (index1 == 0) [[unlikely]] {
                        nameStartOf.push_back(item);
                    } else {
                        nameContain.push_back(item);
                    }
                    continue;
                }
                //通过介绍进行搜索
                if (item->description.has_value() && kmpMatcher.match(item->description.value()) != std::u16string::npos) [[unlikely]] {
                    descriptionContain.push_back(item);
                }
            }
            size_t start = astNode.tokens.startIndex;
            size_t end = astNode.tokens.endIndex;
            suggestions.reserveIdSuggestion(nameStartOf.size() + nameContain.size() + descriptionContain.size());
            for (const auto &item: nameStartOf) {
                suggestions.addIdSuggestion({start, end, node.getIsMustAfterSpace(), item});
            }
            for (const auto &item: nameContain) {
                suggestions.addIdSuggestion({start, end, node.getIsMustAfterSpace(), item});
            }
            for (const auto &item: descriptionContain) {
                suggestions.addIdSuggestion({start, end, node.getIsMustAfterSpace(), item});
            }
            return true;
        }
    };

    bool collectNodeRelativeFloatSuggestions(size_t index, Suggestions &suggestions, bool canUseCaretNotation) {
        suggestions.addSpaceSuggestion({index, index, false, spaceId});
        suggestions.addSymbolSuggestion({index, index, false, Node::NodeRelativeFloat::nodeRelativeNotation.normalId});
        if (canUseCaretNotation) [[likely]] {
            suggestions.addSymbolSuggestion({index, index, false, Node::NodeRelativeFloat::nodeCaretNotation.normalId});
        }
        return true;
    }

    template<>
    struct AutoSuggestion<Node::NodePosition> {
        static bool collectSuggestions(const ASTNode &astNode, size_t index, Suggestions &suggestions) {
            if (astNode.id != ASTNodeId::NODE_POSITION_POSITIONS) [[likely]] {
                return false;
            }
            size_t errorCount = 0;
            for (const auto &item: astNode.childNodes) {
                if (item.isError()) {
                    errorCount++;
                }
            }
            if (errorCount > 0) {
                return collectNodeRelativeFloatSuggestions(index, suggestions, true);
            }
            return true;
        }
    };

    template<>
    struct AutoSuggestion<Node::NodeRelativeFloat> {
        static bool collectSuggestions(const ASTNode &astNode, size_t index, Suggestions &suggestions) {
            const auto &node = *reinterpret_cast<const Node::NodeRelativeFloat *>(astNode.node.data);
            std::u16string_view str = astNode.tokens.string();
            size_t startIndex = astNode.tokens.startIndex;
            for (size_t i = 0; i < str.length(); ++i) {
                if (startIndex + i == index) [[unlikely]] {
                    return collectNodeRelativeFloatSuggestions(index, suggestions, node.canUseCaretNotation);
                }
                if (str[i] != ' ') {
                    return true;
                }
            }
            if (startIndex + str.length() == index) [[unlikely]] {
                return collectNodeRelativeFloatSuggestions(index, suggestions, node.canUseCaretNotation);
            }
            return true;
        }
    };

    template<>
    struct AutoSuggestion<Node::NodeString> {
        static bool collectSuggestions(const ASTNode &astNode, size_t index, Suggestions &suggestions) {
            const auto &node = *reinterpret_cast<const Node::NodeString *>(astNode.node.data);
            if (node.ignoreLater || !node.canContainSpace) [[unlikely]] {
                return true;
            }
            std::u16string_view str = astNode.tokens.string().substr(0, index - astNode.tokens.startIndex);
            if (str.empty()) [[unlikely]] {
                suggestions.addSymbolSuggestion({index, index, false, doubleQuoteMask});
                return true;
            }
            if (str[0] != '"') [[likely]] {
                return true;
            }
            auto convertResult = JsonUtil::jsonString2String(str);
            if (!convertResult.isComplete) [[likely]] {
                suggestions.addSymbolSuggestion({index, index, false, doubleQuoteMask});
            }
            return true;
        }
    };

    template<>
    struct AutoSuggestion<Node::NodeText> {
        static bool collectSuggestions(const ASTNode &astNode, size_t index, Suggestions &suggestions) {
            const auto &node = *reinterpret_cast<const Node::NodeText *>(astNode.node.data);
            std::u16string_view str = astNode.tokens.string().substr(0, index - astNode.tokens.startIndex);
            //通过名字进行搜索
            size_t index1 = node.data->name.find(str);
            if (index1 != std::u16string::npos) [[likely]] {
                suggestions.addLiteralSuggestion({astNode.tokens, node.getIsMustAfterSpace(), node.data});
                return true;
            }
            //通过介绍进行搜索
            if (node.data->description.has_value()) [[likely]] {
                size_t index2 = node.data->description.value().find(str);
                if (index2 != std::u16string::npos) [[likely]] {
                    suggestions.addLiteralSuggestion({astNode.tokens, node.getIsMustAfterSpace(), node.data});
                }
            }
            return true;
        }
    };

    template<>
    struct AutoSuggestion<Node::NodeRange> {
        static bool collectSuggestions(const ASTNode &astNode, size_t index, Suggestions &suggestions) {
            std::u16string_view str = astNode.tokens.string();
            size_t index0 = str.find(u"..");
            if (index0 != std::u16string::npos) [[unlikely]] {
                index0 += astNode.tokens.startIndex;
                if (index < index0 || index > index0 + 2) [[likely]] {
                    return true;
                }
                suggestions.addSymbolSuggestion({index0, index0 + 2, false, rangeSymbol});
                return true;
            }
            size_t index1 = str.find('.');
            if (index1 != std::u16string::npos) [[unlikely]] {
                index1 += astNode.tokens.startIndex;
                if (index < index1 || index > index1 + 1) [[likely]] {
                    return true;
                }
                suggestions.addSymbolSuggestion({index1, index1 + 1, false, rangeSymbol});
                return true;
            }
            suggestions.addSymbolSuggestion({index, index, false, rangeSymbol});
            return true;
        }
    };

    template<>
    struct AutoSuggestion<Node::NodeJsonList> {
        static bool collectSuggestions(const ASTNode &astNode, size_t index, Suggestions &suggestions) {
            return astNode.id == ASTNodeId::NODE_JSON_ALL_LIST;
        }
    };

    template<>
    struct AutoSuggestion<Node::NodeJsonNull> {
        static bool collectSuggestions(const ASTNode &astNode, size_t index, Suggestions &suggestions) {
            std::u16string_view str = astNode.tokens.string().substr(0, index - astNode.tokens.startIndex);
            if (str.find(u"null") != std::u16string::npos) [[likely]] {
                suggestions.addLiteralSuggestion({astNode.tokens, false, NormalId::make(u"null", u"null参数")});
            }
            return true;
        }
    };

    template<>
    struct AutoSuggestion<Node::NodeJsonString> {
        static bool collectSuggestions(const ASTNode &astNode, size_t index, Suggestions &suggestions) {
            if (astNode.tokens.isEmpty()) [[unlikely]] {
                suggestions.addSymbolSuggestion({index, index, false, doubleQuoteMask});
                return true;
            }
            auto convertResult = JsonUtil::jsonString2String(astNode.tokens.string());
            auto convertResult1 = JsonUtil::jsonString2String(astNode.tokens.string().substr(0, index - astNode.tokens.startIndex));
            if (convertResult1.errorReason == nullptr && astNode.id == ASTNodeId::NODE_STRING_INNER) [[unlikely]] {
                Suggestions childSuggestions = getSuggestions(astNode.childNodes[0], convertResult1.result.size());
                suggestions.combine(childSuggestions, [&convertResult, &astNode](Suggestion &suggestion) {
                    std::u16string convertStr = JsonUtil::string2jsonString(suggestion.content->name);
                    suggestion.start = convertResult.convert(suggestion.start) + astNode.tokens.startIndex;
                    suggestion.end = convertResult.convert(suggestion.end) + astNode.tokens.startIndex;
                    if (convertStr.size() != astNode.tokens.string().size()) [[unlikely]] {
                        suggestion.content = NormalId::make(convertStr, suggestion.content->description);
                    }
                    return true;
                });
                if (astNode.hasChildNode() && !astNode.childNodes[0].isError() &&
                    convertResult.errorReason == nullptr && !convertResult.isComplete) [[likely]] {
                    suggestions.addSymbolSuggestion({index, index, false, doubleQuoteMask});
                }
            } else if (convertResult.errorReason == nullptr && !convertResult.isComplete) [[likely]] {
                suggestions.addSymbolSuggestion({index, index, false, doubleQuoteMask});
            }
            return true;
        }
    };

    template<>
    struct AutoSuggestion<Node::NodeOr> {
        static bool collectSuggestions(const ASTNode &astNode, size_t index, Suggestions &suggestions) {
            const auto &node = *reinterpret_cast<const Node::NodeOr *>(astNode.node.data);
            return node.noSuggestion;
        }
    };

    template<>
    struct AutoSuggestion<Node::NodeSingleSymbol> {
        static bool collectSuggestions(const ASTNode &astNode, size_t index, Suggestions &suggestions) {
            const auto &node = *reinterpret_cast<const Node::NodeSingleSymbol *>(astNode.node.data);
            if (astNode.tokens.startIndex == index) [[unlikely]] {
                suggestions.addSymbolSuggestion({index, index, node.isAddSpace, node.normalId});
            }
            return true;
        }
    };

    template<>
    struct AutoSuggestion<Node::NodeOptional> {
        static bool collectSuggestions(const ASTNode &astNode, size_t index, Suggestions &suggestions) {
            if (astNode.mode == ASTNodeMode::OR && astNode.tokens.startIndex == index) {
                const ASTNode &childASTNode = astNode.childNodes[1];
                size_t suggestionIndex = childASTNode.tokens.startIndex;
                Suggestions childSuggestions = getSuggestions(childASTNode, suggestionIndex);
                suggestions.combine(childSuggestions, [suggestionIndex, index](Suggestion &suggestion) {
                    if (suggestion.start == suggestionIndex && suggestion.end == suggestionIndex) {
                        suggestion.start = index;
                        suggestion.end = index;
                        return true;
                    } else {
                        return false;
                    }
                });
            }
            return false;
        }
    };

    void collectSuggestions(const ASTNode &astNode, size_t index, Suggestions &suggestions) {
        if (index < astNode.tokens.startIndex || index > astNode.tokens.endIndex) [[likely]] {
            return;
        }
        if (!astNode.isAllSpaceError()) [[unlikely]] {
            bool isDirty;
#ifdef CHelperTest
            Profile::push("collect suggestions: {} {}", FORMAT_ARG(utf8::utf16to8(astNode.tokens.toString())), FORMAT_ARG(Node::getNodeTypeName(astNode.node.nodeTypeId)));
#endif
            switch (astNode.node.nodeTypeId) {
                CODEC_PASTE(CHELPER_COLLECT_AUTO_SUGGESTION, CHELPER_NODE_TYPES)
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
                    collectSuggestions(item, index, suggestions);
                }
                break;
            case ASTNodeMode::OR:
                for (const ASTNode &item: astNode.childNodes) {
                    collectSuggestions(item, index, suggestions);
                }
                break;
        }
    }

    bool canAddSpace0(const ASTNode &astNode, size_t index) {
        if (std::ranges::any_of(astNode.errorReasons, [&index](const auto &item) {
                return item->level == ErrorReasonLevel::REQUIRE_SPACE && item->start >= index && item->end <= index;
            })) {
            return true;
        }
        switch (astNode.mode) {
            case ASTNodeMode::NONE:
                return false;
            case ASTNodeMode::AND:
                return !astNode.childNodes.empty() && canAddSpace0(astNode.childNodes[astNode.childNodes.size() - 1], index);
            case ASTNodeMode::OR:
                for (const auto &item: astNode.childNodes) {
                    if (canAddSpace0(item, index)) [[unlikely]] {
                        return true;
                    }
                }
                return false;
            default:
                CHELPER_UNREACHABLE();
        }
    }

    Suggestions getSuggestions(const ASTNode &astNode, size_t index) {
        std::u16string_view str = astNode.tokens.string();
        Suggestions suggestions;
        if (canAddSpace0(astNode, index)) [[unlikely]] {
            suggestions.addSpaceSuggestion({str.length(), str.length(), false, spaceId});
        }
        collectSuggestions(astNode, index, suggestions);
        return suggestions;
    }

}// namespace CHelper::AutoSuggestion
