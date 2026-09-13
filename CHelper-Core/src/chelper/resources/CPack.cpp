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

#include <chelper/node/NodeInitialization.h>
#include <chelper/node/NodeType.h>
#include <chelper/resources/CPack.h>

namespace CHelper {

    void CPack::applyId(const IdEntry &entry) {
        std::visit([&](const auto &item) {
            using T = std::decay_t<decltype(item)>;
            if constexpr (std::is_same_v<T, NormalIdEntry>) {
                normalIds.emplace(item.id, item.content);
            } else if constexpr (std::is_same_v<T, NamespaceIdEntry>) {
                namespaceIds.emplace(item.id, item.content);
            } else if constexpr (std::is_same_v<T, BlockIdsEntry>) {
                blockIds = item.content;
            } else {
                itemIds = item.content;
            }
        },
                   entry);
    }

    void CPack::applyGrammar(GrammarEntry &&entry, LoadTrail &trail) {
        if (entry.type != "grammar") [[unlikely]] {
            throw std::runtime_error("invalid grammar resource type");
        }
        if (entry.id.empty()) [[unlikely]] {
            throw std::runtime_error("grammar resource id cannot be empty");
        }
        if (entry.content == nullptr) [[unlikely]] {
            throw std::runtime_error(fmt::format("grammar resource content is missing: {}", entry.id));
        }
        if (grammarNodes.contains(entry.id) || grammarGraphs.contains(entry.id)) [[unlikely]] {
            throw std::runtime_error(fmt::format("duplicate grammar resource id: {}", entry.id));
        }
        //节点初始化失败时，trail 里的 grammar 身份让资源包作者能定位到具体的 grammar 资源；
        //加载成功时条目随作用域弹出，只有失败的 grammar 会被保留到异常消息里
        LoadTrail::Scope grammarScope(trail, fmt::format(R"(grammar "{}")", entry.id));
        Node::initNode(*entry.content, *this);
        if (entry.content->start.data == nullptr) [[unlikely]] {
            throw std::runtime_error(fmt::format("grammar resource content has no start node: {}", entry.id));
        }
        const auto root = entry.content->start;
        const auto graph = std::move(entry.content);
        grammarGraphs.emplace(entry.id, graph);
        grammarNodes.emplace(std::move(entry.id), root);
    }

    void CPack::applyJson(Node::NodeJsonElement &&item) {
        if (!item.id.has_value() || item.id.value().empty()) [[unlikely]] {
            throw std::runtime_error("json element id cannot be empty");
        }
        jsonNodes.push_back(std::move(item));
    }

    void CPack::applyRepeat(Node::RepeatData &&item) {
        repeatNodeData.push_back(std::move(item));
    }

    void CPack::applyCommand(Node::NodePerCommand &&item) const {
        commands->push_back(std::move(item));
    }

    void CPack::afterApply(LoadTrail &trail) {
        for (const auto &item: jsonNodes) {
            LoadTrail::Scope jsonScope(trail, fmt::format(R"(json "{}")", item.id.value_or("?")));
            Node::initNode(item, *this);
        }
        for (const auto &item: repeatNodeData) {
            if (item.repeatNodes.size() != item.isEnd.size()) [[unlikely]] {
                throw std::runtime_error(fmt::format(
                        "fail to check repeat id {} because repeatNodes size not equal isEnd size", item.id));
            }
        }
        for (const auto &item: repeatNodeData) {
            std::pmr::vector<Node::NodeWithType> content;
            content.reserve(item.repeatNodes.size());
            for (const auto &item2: item.repeatNodes) {
                std::pmr::vector<Node::NodeWithType> perContent;
                perContent.reserve(item2.nodes.size());
                for (const auto &item3: item2.nodes) {
                    auto nodeWrapped = new Node::NodeWrapped(item3);
                    perContent.emplace_back(*nodeWrapped);
                    cacheNodes.nodes.emplace_back(*nodeWrapped);
                }
                auto node = new Node::NodeAnd(perContent);
                content.emplace_back(*node);
                cacheNodes.nodes.emplace_back(*node);
            }
            std::pmr::vector<Node::NodeWithType> breakChildNodes;
            breakChildNodes.reserve(item.breakNodes.nodes.size());
            for (const auto &item2: item.breakNodes.nodes) {
                auto nodeWrapped = new Node::NodeWrapped(item2);
                breakChildNodes.emplace_back(*nodeWrapped);
                cacheNodes.nodes.emplace_back(*nodeWrapped);
            }
            auto unBreakNode = new Node::NodeOr(content, false);
            auto breakNode = new Node::NodeAnd(breakChildNodes);
            auto orNode = new Node::NodeOr({*unBreakNode, *breakNode}, false);
            repeatNodes.emplace(item.id, std::make_pair<const Node::RepeatData *, Node::NodeWithType>(&item, *orNode));
            cacheNodes.nodes.emplace_back(*unBreakNode);
            cacheNodes.nodes.emplace_back(*breakNode);
            cacheNodes.nodes.emplace_back(*orNode);
        }
        for (const auto &item: repeatNodeData) {
            LoadTrail::Scope repeatScope(trail, fmt::format(R"(repeat "{}")", item.id));
            for (const auto &item2: item.repeatNodes) {
                for (const auto &item3: item2.nodes) {
                    Node::initNode(item3, *this);
                }
            }
            for (const auto &item2: item.breakNodes.nodes) {
                Node::initNode(item2, *this);
            }
        }
        for (const auto &item: *commands) {
            LoadTrail::Scope commandScope(trail, fmt::format(
                                                         R"(command "{}")", utf8::utf16to8(fmt::format(u"{}", fmt::join(item.name, u",")))));
            Node::initNode(item, *this);
        }
        // 解析阶段只读共享的 CPack。提前完成所有惰性缓存，避免第一次解析时
        // 把 CPack 内部节点或 ID 缓存分配到调用方的临时内存资源中。
        for (const auto &[key, values]: normalIds) {
            (void) key;
            for (const auto &item: *values) {
                item->buildHash();
            }
        }
        for (const auto &[key, values]: namespaceIds) {
            (void) key;
            for (const auto &item: *values) {
                item->buildHash();
                item->getIdWithNamespace()->buildHash();
            }
        }
        if (itemIds != nullptr) {
            for (const auto &item: *itemIds) {
                item->buildHash();
                item->getIdWithNamespace()->buildHash();
                item->getNode();
            }
        }
        if (blockIds != nullptr && blockIds->blockStateValues != nullptr) {
            for (const auto &item: *blockIds->blockStateValues) {
                item->buildHash();
                item->getIdWithNamespace()->buildHash();
                item->getNode(blockIds->blockPropertyDescriptions);
            }
        }
        validate();
        std::ranges::sort(*commands, [](const auto &item1, const auto &item2) {
            return item1.name[0] < item2.name[0];
        });
        mainNode = Node::NodeCommand("MAIN_NODE", u"欢迎使用命令助手(作者：Yancey)", commands.get());
    }

    const Node::NodeWithType *CPack::getGrammar(const std::string_view key) const {
        const std::pmr::string searchKey(key.data(), key.size(), grammarNodes.get_allocator().resource());
        const auto it = grammarNodes.find(searchKey);
        return it == grammarNodes.end() ? nullptr : &it->second;
    }

    void CPack::validate() const {
        for (const auto &item: *commands) {
            if (item.name.empty()) [[unlikely]] {
                throw std::runtime_error("command name cannot be empty");
            }
            if (item.startNodes.empty()) [[unlikely]] {
                throw std::runtime_error(fmt::format(
                        R"(command "{}" must have at least one start node, check the syntax field)",
                        utf8::utf16to8(item.name[0])));
            }
        }
        for (const auto &item: repeatNodeData) {
            if (item.repeatNodes.empty()) [[unlikely]] {
                throw std::runtime_error(fmt::format(
                        "repeat node {} must have at least one repeat node", item.id));
            }
        }
    }

    std::shared_ptr<std::pmr::vector<std::shared_ptr<NormalId>>>
    CPack::getNormalId(const std::string_view key) const {
        const std::pmr::string searchKey(key.data(), key.size(), normalIds.get_allocator().resource());
        auto it = normalIds.find(searchKey);
        if (it == normalIds.end()) [[unlikely]] {
#if CHelperDebug
            SPDLOG_WARN(R"(fail to find normal ids by key: "{}")", FORMAT_ARG(key));
#endif
            return nullptr;
        }
        return it->second;
    }

    std::shared_ptr<std::pmr::vector<std::shared_ptr<NamespaceId>>>
    CPack::getNamespaceId(const std::string_view key) const {
        if (key == "block") [[unlikely]] {
            return std::reinterpret_pointer_cast<std::pmr::vector<std::shared_ptr<NamespaceId>>>(blockIds->blockStateValues);
        } else if (key == "item") [[unlikely]] {
            return std::reinterpret_pointer_cast<std::pmr::vector<std::shared_ptr<NamespaceId>>>(itemIds);
        }
        const std::pmr::string searchKey(key.data(), key.size(), namespaceIds.get_allocator().resource());
        auto it = namespaceIds.find(searchKey);
        if (it == namespaceIds.end()) [[unlikely]] {
#if CHelperDebug
            SPDLOG_WARN(R"(fail to find namespace ids by key: "{}")", FORMAT_ARG(key));
#endif
            return nullptr;
        }
        return it->second;
    }

}// namespace CHelper
