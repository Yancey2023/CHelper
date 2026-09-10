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

    void CPack::applyJson(Node::NodeJsonElement &&item) {
        if (!item.id.has_value() || item.id.value().empty()) [[unlikely]] {
            Profile::push("loading json element");
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

    void CPack::afterApply() {
        Profile::push("init selector nodes");
        targetSelectorData.init(*this);
        Profile::next("init json nodes");
        for (const auto &item: jsonNodes) {
            Node::initNode(item, *this);
        }
        Profile::next("init repeat nodes");
        for (const auto &item: repeatNodeData) {
            if (item.repeatNodes.size() != item.isEnd.size()) [[unlikely]] {
                Profile::push("checking repeat node: {}", FORMAT_ARG(item.id));
                throw std::runtime_error("fail to check repeat id because repeatNodes size not equal isEnd size");
            }
        }
        for (const auto &item: repeatNodeData) {
            std::vector<Node::NodeWithType> content;
            content.reserve(item.repeatNodes.size());
            for (const auto &item2: item.repeatNodes) {
                std::vector<Node::NodeWithType> perContent;
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
            std::vector<Node::NodeWithType> breakChildNodes;
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
            Profile::next(R"(init command: "{}")", FORMAT_ARG(utf8::utf16to8(fmt::format(u"{}", fmt::join(item.name, u",")))));
            Node::initNode(item, *this);
        }
        Profile::next("sort command nodes");
        validate();
        std::ranges::sort(*commands, [](const auto &item1, const auto &item2) {
            return item1.name[0] < item2.name[0];
        });
        Profile::next("create main node");
        mainNode = Node::NodeCommand("MAIN_NODE", u"欢迎使用命令助手(作者：Yancey)", commands.get());
        Profile::pop();
    }

    void CPack::validate() const {
        for (const auto &item: *commands) {
            if (item.name.empty()) [[unlikely]] {
                Profile::push("validating command");
                Profile::push("command name cannot be empty");
                throw std::runtime_error("command name cannot be empty");
            }
            if (item.startNodes.empty()) [[unlikely]] {
                Profile::push("validating command \"{}\"", FORMAT_ARG(utf8::utf16to8(item.name[0])));
                Profile::push("command must have at least one start node, check the syntax field");
                throw std::runtime_error("command start nodes cannot be empty");
            }
        }
        for (const auto &item: repeatNodeData) {
            if (item.repeatNodes.empty()) [[unlikely]] {
                Profile::push("checking repeat node: {}", FORMAT_ARG(item.id));
                Profile::push("repeat node must have at least one repeat node");
                throw std::runtime_error("repeat nodes cannot be empty");
            }
        }
    }

    std::shared_ptr<std::vector<std::shared_ptr<NormalId>>>
    CPack::getNormalId(const std::string &key) const {
        auto it = normalIds.find(key);
        if (it == normalIds.end()) [[unlikely]] {
#ifdef CHelperDebug
            SPDLOG_WARN(R"(fail to find normal ids by key: "{}")", FORMAT_ARG(key));
#endif
            return nullptr;
        }
        return it->second;
    }

    std::shared_ptr<std::vector<std::shared_ptr<NamespaceId>>>
    CPack::getNamespaceId(const std::string &key) const {
        if (key == "block") [[unlikely]] {
            return std::reinterpret_pointer_cast<std::vector<std::shared_ptr<NamespaceId>>>(blockIds->blockStateValues);
        } else if (key == "item") [[unlikely]] {
            return std::reinterpret_pointer_cast<std::vector<std::shared_ptr<NamespaceId>>>(itemIds);
        }
        auto it = namespaceIds.find(key);
        if (it == namespaceIds.end()) [[unlikely]] {
#ifdef CHelperDebug
            SPDLOG_WARN(R"(fail to find namespace ids by key: "{}")", FORMAT_ARG(key));
#endif
            return nullptr;
        }
        return it->second;
    }

}// namespace CHelper
