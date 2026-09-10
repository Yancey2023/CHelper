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
#include <chelper/serialization/Serialization.h>

namespace CHelper {

    // 当前 CPack 加载阶段（声明见 Serialization.h）
    Node::NodeCreateStage::NodeCreateStage currentCreateStage = Node::NodeCreateStage::NONE;

#ifndef CHELPER_NO_FILESYSTEM
    // content 为已编码的 JSON 文本
    void writeJsonToFileWithCreateDirectory(const std::filesystem::path &path, const std::string &content) {
        if (!exists(path)) {
            std::filesystem::create_directories(path.parent_path());
        }
        std::ofstream os(path, std::ios::binary);
        if (!os.is_open()) [[unlikely]] {
            throw std::runtime_error("fail to open file: " + path.string());
        }
        os << content;
    }
#endif

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

    CPack::CPack(CPackJsonData &&data) {
#if defined(CHelperDebug) && !defined(CHELPER_NO_FILESYSTEM)
        size_t stackSize = Profile::stack.size();
#endif
        currentCreateStage = Node::NodeCreateStage::NONE;
        Profile::push("loading manifest");
        manifest = std::move(data.manifest);
        Profile::next("loading id data");
        for (const auto &entry: data.id) {
            applyId(entry);
        }
        Profile::next("loading json data");
        currentCreateStage = Node::NodeCreateStage::JSON_NODE;
        for (auto &item: data.json) {
            applyJson(std::move(item));
        }
        Profile::next("loading repeat data");
        currentCreateStage = Node::NodeCreateStage::REPEAT_NODE;
        for (auto &item: data.repeat) {
            applyRepeat(std::move(item));
        }
        Profile::next("loading command data");
        currentCreateStage = Node::NodeCreateStage::COMMAND_PARAM_NODE;
        for (auto &item: data.command) {
            applyCommand(std::move(item));
        }
        Profile::next("init cpack");
        currentCreateStage = Node::NodeCreateStage::NONE;
        afterApply();
        Profile::pop();
#if defined(CHelperDebug) && !defined(CHELPER_NO_FILESYSTEM)
        if (Profile::stack.size() != stackSize) [[unlikely]] {
            SPDLOG_WARN("error profile stack after loading cpack");
        }
#endif
    }

    CPack::CPack(CPackData &&data) {
#if defined(CHelperDebug) && !defined(CHELPER_NO_FILESYSTEM)
        size_t stackSize = Profile::stack.size();
#endif
        currentCreateStage = Node::NodeCreateStage::NONE;
        Profile::push("loading manifest");
        manifest = std::move(data.manifest);
        Profile::next("loading normal id data");
        normalIds = std::move(data.normalIds);
        Profile::next("loading namespace id data");
        namespaceIds = std::move(data.namespaceIds);
        Profile::next("loading item id data");
        itemIds = std::move(data.itemIds);
        Profile::next("loading block id data");
        blockIds = std::move(data.blockIds);
        Profile::next("loading json data");
        currentCreateStage = Node::NodeCreateStage::JSON_NODE;
        jsonNodes = std::move(data.jsonNodes);
        Profile::next("loading repeat data");
        currentCreateStage = Node::NodeCreateStage::REPEAT_NODE;
        repeatNodeData = std::move(data.repeatNodeData);
        Profile::next("loading command data");
        currentCreateStage = Node::NodeCreateStage::COMMAND_PARAM_NODE;
        commands = std::move(data.commands);
        Profile::next("init cpack");
        currentCreateStage = Node::NodeCreateStage::NONE;
        afterApply();
        Profile::pop();
#if defined(CHelperDebug) && !defined(CHELPER_NO_FILESYSTEM)
        if (Profile::stack.size() != stackSize) [[unlikely]] {
            SPDLOG_WARN("error profile stack after loading cpack");
        }
#endif
    }

#ifndef CHELPER_NO_FILESYSTEM
    CPack::CPack(const std::filesystem::path &path) {
#if defined(CHelperDebug) && !defined(CHELPER_NO_FILESYSTEM)
        size_t stackSize = Profile::stack.size();
#endif
        currentCreateStage = Node::NodeCreateStage::NONE;
        Profile::push("loading manifest");
        readJsonFromFile(manifest, path / "manifest.json");
        Profile::next("loading id data");
        for (const auto &file: std::filesystem::recursive_directory_iterator(path / "id")) {
            Profile::next(R"(loading id data in path "{}")", FORMAT_ARG(file.path().string()));
            IdEntry entry;
            readJsonFromFile(entry, file.path());
            applyId(entry);
        }
        Profile::next("loading json data");
        currentCreateStage = Node::NodeCreateStage::JSON_NODE;
        for (const auto &file: std::filesystem::recursive_directory_iterator(path / "json")) {
            Profile::next(R"(loading json data in path "{}")", FORMAT_ARG(file.path().string()));
            Node::NodeJsonElement item;
            readJsonFromFile(item, file.path());
            applyJson(std::move(item));
        }
        Profile::next("loading repeat data");
        currentCreateStage = Node::NodeCreateStage::REPEAT_NODE;
        for (const auto &file: std::filesystem::recursive_directory_iterator(path / "repeat")) {
            Profile::next(R"(loading repeat data in path "{}")", FORMAT_ARG(file.path().string()));
            Node::RepeatData item;
            readJsonFromFile(item, file.path());
            applyRepeat(std::move(item));
        }
        Profile::next("loading commands");
        currentCreateStage = Node::NodeCreateStage::COMMAND_PARAM_NODE;
        for (const auto &file: std::filesystem::recursive_directory_iterator(path / "command")) {
            Profile::next(R"(loading command in path "{}")", FORMAT_ARG(file.path().string()));
            Node::NodePerCommand item;
            readJsonFromFile(item, file.path());
            applyCommand(std::move(item));
        }
        Profile::next("init cpack");
        currentCreateStage = Node::NodeCreateStage::NONE;
        afterApply();
        Profile::pop();
#if defined(CHelperDebug) && !defined(CHELPER_NO_FILESYSTEM)
        if (Profile::stack.size() != stackSize) [[unlikely]] {
            SPDLOG_WARN("error profile stack after loading cpack");
        }
#endif
    }
#endif

    void CPack::afterApply() {
        // selector nodes
        Profile::push("init selector nodes");
        targetSelectorData.init(*this);
        // json nodes
        Profile::next("init json nodes");
        for (const auto &item: jsonNodes) {
            Node::initNode(item, *this);
        }
        // repeat nodes
        Profile::next("init repeat nodes");
        for (const auto &item: repeatNodeData) {
            if (item.repeatNodes.size() != item.isEnd.size()) [[unlikely]] {
                Profile::push("checking repeat node: {}", FORMAT_ARG(item.id));
                throw std::runtime_error("fail to check repeat id because repeatNodes size not equal isEnd size");
            }
        }
        // command param nodes
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
        //命令的名字不能为空，排序和命令匹配都会访问name[0]
        for (const auto &item: *commands) {
            if (item.name.empty()) [[unlikely]] {
                Profile::push("validating command");
                Profile::push("command name cannot be empty");
                throw std::runtime_error("command name cannot be empty");
            }
            //startNodes为空会使Parser在解析命令时创建childNodes为空的OR节点
            if (item.startNodes.empty()) [[unlikely]] {
                Profile::push("validating command \"{}\"", FORMAT_ARG(utf8::utf16to8(item.name[0])));
                Profile::push("command must have at least one start node, check the syntax field");
                throw std::runtime_error("command start nodes cannot be empty");
            }
        }
        //repeatNodes为空会使Parser创建childNodes为空的OR节点
        for (const auto &item: repeatNodeData) {
            if (item.repeatNodes.empty()) [[unlikely]] {
                Profile::push("checking repeat node: {}", FORMAT_ARG(item.id));
                Profile::push("repeat node must have at least one repeat node");
                throw std::runtime_error("repeat nodes cannot be empty");
            }
        }
    }

#ifndef CHELPER_NO_FILESYSTEM
    std::unique_ptr<CPack> CPack::createByDirectory(const std::filesystem::path &path) {
        Profile::push("start load CPack by DIRECTORY: {}", FORMAT_ARG(path.string()));
        auto cpack = std::make_unique<CPack>(path);
        Profile::pop();
        return cpack;
    }

    std::unique_ptr<CPack> CPack::createByJson(const std::filesystem::path &cpackPath) {
        Profile::push("start load CPack by JSON");
        // 单文件格式一次性读取所有节点，JSON_NODE 阶段覆盖全部可序列化的节点类型
        currentCreateStage = Node::NodeCreateStage::JSON_NODE;
        CPackJsonData data;
        readJsonFromFile(data, cpackPath);
        currentCreateStage = Node::NodeCreateStage::NONE;
        auto cpack = std::make_unique<CPack>(std::move(data));
        Profile::pop();
        return cpack;
    }
#endif

    std::unique_ptr<CPack> CPack::createByJson(const std::string &json) {
        Profile::push("start load CPack by JSON");
        // 单文件格式一次性读取所有节点，JSON_NODE 阶段覆盖全部可序列化的节点类型
        currentCreateStage = Node::NodeCreateStage::JSON_NODE;
        CPackJsonData data;
        readJson(data, json);
        currentCreateStage = Node::NodeCreateStage::NONE;
        auto cpack = std::make_unique<CPack>(std::move(data));
        Profile::pop();
        return cpack;
    }

    std::unique_ptr<CPack> CPack::createByBinary(std::string_view data) {
        Profile::push("start load CPack by binary");
        // 二进制格式一次性读取所有节点，JSON_NODE 阶段覆盖全部可序列化的节点类型
        currentCreateStage = Node::NodeCreateStage::JSON_NODE;
        CPackData cpackData;
        readBinary(cpackData, data);
        currentCreateStage = Node::NodeCreateStage::NONE;
        auto cpack = std::make_unique<CPack>(std::move(cpackData));
        Profile::pop();
        return cpack;
    }

#ifndef CHELPER_NO_FILESYSTEM
    void CPack::writeJsonToDirectory(const std::filesystem::path &path) const {
        writeJsonToFileWithCreateDirectory(path / "manifest.json", writeJson(manifest));
        for (const auto &item: normalIds) {
            const IdEntry entry = NormalIdEntry{item.first, item.second};
            writeJsonToFileWithCreateDirectory(path / "id" / (item.first + ".json"), writeJson(entry));
        }
        for (const auto &item: namespaceIds) {
            const IdEntry entry = NamespaceIdEntry{item.first, item.second};
            writeJsonToFileWithCreateDirectory(path / "id" / (item.first + ".json"), writeJson(entry));
        }
        {
            const IdEntry entry = ItemIdsEntry{"item", itemIds};
            writeJsonToFileWithCreateDirectory(path / "id" / "items.json", writeJson(entry));
        }
        {
            const IdEntry entry = BlockIdsEntry{"block", blockIds};
            writeJsonToFileWithCreateDirectory(path / "id" / "block.json", writeJson(entry));
        }
        for (const auto &item: jsonNodes) {
            writeJsonToFileWithCreateDirectory(path / "json" / (item.id.value() + ".json"), writeJson(item));
        }
        for (const auto &item: repeatNodeData) {
            writeJsonToFileWithCreateDirectory(path / "repeat" / (item.id + ".json"), writeJson(item));
        }
        for (const auto &item: *commands) {
            writeJsonToFileWithCreateDirectory(path / "command" / (utf8::utf16to8(item.name[0]) + ".json"), writeJson(item));
        }
    }
#endif

    std::string CPack::toJson() const {
        std::vector<IdEntry> idEntries;
        idEntries.reserve(normalIds.size() + namespaceIds.size() + 2);
        for (const auto &item: normalIds) {
            idEntries.push_back(NormalIdEntry{item.first, item.second});
        }
        for (const auto &item: namespaceIds) {
            idEntries.push_back(NamespaceIdEntry{item.first, item.second});
        }
        idEntries.push_back(ItemIdsEntry{"item", itemIds});
        idEntries.push_back(BlockIdsEntry{"block", blockIds});
        // jsonNodes 不可拷贝（FreeableNodeWithTypes），通过引用写出
        auto value = glz::obj{"manifest", manifest, "id", idEntries, "json", jsonNodes, "repeat", repeatNodeData,
                              "command", *commands};
        return writeJson(value);
    }

#ifndef CHELPER_NO_FILESYSTEM
    void CPack::writeJsonToFile(const std::filesystem::path &path) const {
        writeJsonToFileWithCreateDirectory(path, toJson());
    }

    void CPack::writeBinToFile(const std::filesystem::path &path) const {
        std::filesystem::create_directories(path.parent_path());
        Profile::push("writing binary cpack to file: {}", FORMAT_ARG(path.string()));
        std::string buffer;
        // 按 CPackData 的 meta 成员顺序顺序写出（jsonNodes 不可拷贝，直接引用写出）
        glz::context ctx{};
        if (buffer.size() < 2 * glz::write_padding_bytes) {
            buffer.resize(2 * glz::write_padding_bytes);
        }
        size_t ix = 0;
        auto writeOne = [&](auto &&value) {
            glz::serialize<CHelper::BinaryFormat>::template op<glz::opts{}>(value, ctx, buffer, ix);
            if (bool(ctx.error)) [[unlikely]] {
                throw std::runtime_error("fail to write binary");
            }
        };
        writeOne(manifest);
        writeOne(normalIds);
        writeOne(namespaceIds);
        writeOne(itemIds);
        writeOne(blockIds);
        writeOne(jsonNodes);
        writeOne(repeatNodeData);
        // commands 需以 shared_ptr 形式写出（带存在标记），与 CPackData 的反射读取对应
        writeOne(commands);
        buffer.resize(ix);
        std::ofstream ostream(path, std::ios::binary);
        if (!ostream.is_open()) [[unlikely]] {
            Profile::pop();
            throw std::runtime_error("fail to open file: " + path.string());
        }
        ostream.write(buffer.data(), static_cast<std::streamsize>(buffer.size()));
        ostream.close();
        Profile::pop();
    }
#endif

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
