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

#include <chelper/serialization/Serialization.h>

#include <chelper/serialization/SerializationImpl.h>

// ================= Old2New 序列化 =================
namespace CHelper::Old2New {

#ifndef CHELPER_NO_FILESYSTEM
    BlockFixData blockFixDataFromJson(const std::filesystem::path &path) {
        std::vector<BlockFixEntry> entries;
        readJsonFromFile(entries, path);
        BlockFixData blockFixData;
        {
            DenseMap<std::u16string_view, size_t> counts;
            for (const auto &entry: entries) ++counts[entry.name];
            blockFixData.reserve(counts.size());
            for (const auto &[name, count]: counts) {
                blockFixData.try_emplace(std::u16string(name)).first->second.reserve(count);
            }
        }
        for (auto &entry: entries) {
            auto &dataValueToBlockState = blockFixData.at(entry.name);
            dataValueToBlockState.insert_or_assign(entry.data,
                                                   std::make_pair(std::move(entry.newBlockId),
                                                                  std::move(entry.blockState)));
        }
        return blockFixData;
    }
#endif

    std::string blockFixDataToBinary(const BlockFixData &blockFixData) {
        std::string buffer;
        writeBinary(buffer, blockFixData);
        return buffer;
    }

    BlockFixData blockFixDataFromBinary(std::string_view buffer) {
        BlockFixData blockFixData;
        readBinary(blockFixData, buffer);
        return blockFixData;
    }
}// namespace CHelper::Old2New

// ================= CPack 写出 =================
#ifndef CHELPER_NO_FILESYSTEM
namespace CHelper {

    // value 为原始对象，写出前做 JSON 编码
    template<class T>
    inline void writeJsonToFileWithCreateDirectory(const std::filesystem::path &path, const T &value) {
        if (!std::filesystem::exists(path)) {
            std::filesystem::create_directories(path.parent_path());
        }
        std::ofstream os(path, std::ios::binary);
        if (!os.is_open()) [[unlikely]] {
            throw std::runtime_error("fail to open file: " + path.string());
        }
        os << writeJson(value);
    }

    // content 为已编码的 JSON 文本，原样写出（非模板重载优先于模板匹配 std::string）
    inline void writeJsonToFileWithCreateDirectory(const std::filesystem::path &path, const std::string &content) {
        if (!std::filesystem::exists(path)) {
            std::filesystem::create_directories(path.parent_path());
        }
        std::ofstream os(path, std::ios::binary);
        if (!os.is_open()) [[unlikely]] {
            throw std::runtime_error("fail to open file: " + path.string());
        }
        os << content;
    }

    // 收集全部 Grammar 条目；grammarNodes 中的每个节点都必须有对应的语法图
    [[nodiscard]] std::vector<GrammarEntry> collectGrammarEntries(const CPack &cpack) {
        std::vector<GrammarEntry> grammarEntries;
        grammarEntries.reserve(cpack.grammarNodes.size());
        for (const auto &[id, content]: cpack.grammarNodes) {
            (void) content;
            const auto graph = cpack.grammarGraphs.find(id);
            if (graph == cpack.grammarGraphs.end()) [[unlikely]] {
                throw std::runtime_error("missing grammar graph");
            }
            grammarEntries.emplace_back(id, "grammar", graph->second);
        }
        return grammarEntries;
    }

    void CPack::writeJsonToDirectory(const std::filesystem::path &path) const {
        writeJsonToFileWithCreateDirectory(path / "manifest.json", manifest);
        for (const auto &item: normalIds) {
            const IdEntry entry = NormalIdEntry{item.first, item.second};
            writeJsonToFileWithCreateDirectory(path / "id" / (item.first + ".json"), entry);
        }
        for (const auto &item: namespaceIds) {
            const IdEntry entry = NamespaceIdEntry{item.first, item.second};
            writeJsonToFileWithCreateDirectory(path / "id" / (item.first + ".json"), entry);
        }
        {
            const IdEntry entry = ItemIdsEntry{"item", itemIds};
            writeJsonToFileWithCreateDirectory(path / "id" / "items.json", entry);
        }
        {
            const IdEntry entry = BlockIdsEntry{"block", blockIds};
            writeJsonToFileWithCreateDirectory(path / "id" / "block.json", entry);
        }
        for (const auto &entry: collectGrammarEntries(*this)) {
            writeJsonToFileWithCreateDirectory(path / "grammer" / (entry.id + ".json"), entry);
        }
        for (const auto &item: jsonNodes) {
            writeJsonToFileWithCreateDirectory(path / "json" / (item.id.value() + ".json"), item);
        }
        for (const auto &item: repeatNodeData) {
            writeJsonToFileWithCreateDirectory(path / "repeat" / (item.id + ".json"), item);
        }
        for (const auto &item: *commands) {
            writeJsonToFileWithCreateDirectory(path / "command" / (utf8::utf16to8(item.name[0]) + ".json"), item);
        }
    }

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
        // glz::obj 持引用，条目列表须是具名局部变量而不是临时对象
        std::vector<GrammarEntry> grammarEntries = collectGrammarEntries(*this);
        // jsonNodes 不可拷贝（FreeableNodeWithTypes），通过引用写出
        auto value = glz::obj{"manifest", manifest, "id", idEntries, "grammar", grammarEntries,
                              "json", jsonNodes, "repeat", repeatNodeData, "command", *commands};
        std::string buffer;
        const auto error = glz::write_json(value, buffer);
        if (bool(error)) [[unlikely]] {
            throw std::runtime_error("fail to write cpack json: " + glz::format_error(error, buffer));
        }
        return buffer;
    }

    void CPack::writeJsonToFile(const std::filesystem::path &path) const {
        writeJsonToFileWithCreateDirectory(path, toJson());
    }

    void CPack::writeBinToFile(const std::filesystem::path &path) const {
        std::filesystem::create_directories(path.parent_path());
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
        // commands 需以 shared_ptr 形式写出（带存在标记），与 CPackData 的反射读取对应
        writeOne(jsonNodes);
        writeOne(repeatNodeData);
        writeOne(commands);
        writeOne(collectGrammarEntries(*this));
        buffer.resize(ix);
        std::ofstream ostream(path, std::ios::binary);
        if (!ostream.is_open()) [[unlikely]] {
            throw std::runtime_error("fail to open file: " + path.string());
        }
        ostream.write(buffer.data(), static_cast<std::streamsize>(buffer.size()));
        ostream.close();
    }
}// namespace CHelper
#endif

// ================= CPack 读取（唯一允许构建 CPack 的入口） =================
namespace CHelper::serialization {

#ifndef CHELPER_NO_FILESYSTEM
    std::unique_ptr<CPack> createCPackByDirectory(const std::filesystem::path &path) {
        LoadTrail trail;
        try {
            trail.push("start load CPack by DIRECTORY: {}", path.string());
            Node::initializeStaticNodes();
            auto cpackMemory = std::make_shared<CPackMemoryResource>();
            CPackMemoryScope memoryScope(cpackMemory);
            auto cpack = std::unique_ptr<CPack>(new CPack());
            cpack->cpackMemory = cpackMemory;
            cpack->destructionMemoryScope.bindAsOwner(cpackMemory);
            cpack->commands = CHelper::allocateShared<std::pmr::vector<Node::NodePerCommand>>(cpackMemory);
            // 加载阶段随上下文传递，各段落只允许反序列化自己合法的节点类型
            NodeReadContext ctx;
            ctx.createStage = Node::NodeCreateStage::NONE;
            ctx.cpackMemory = cpackMemory;
            // 每个文件解析前压入其路径身份，文件处理完成自动弹出：
            // 失败时 trail 里恰好是"出错的那个文件"
            LoadTrail::Scope manifestScope(trail, R"(file "{}")", (path / "manifest.json").string());
            readJsonFromFile(cpack->manifest, path / "manifest.json", ctx);
            for (const auto &file: std::filesystem::recursive_directory_iterator(path / "id")) {
                LoadTrail::Scope fileScope(trail, R"(file "{}")", file.path().string());
                IdEntry entry;
                readJsonFromFile(entry, file.path(), ctx);
                cpack->applyId(entry);
            }
            // Grammar 是按资源类型目录发现的，不绑定 target_selector 或任何固定文件名。
            // 资源包之间可以共享父目录中的 Grammar 资源（例如 vanilla/experiment）。
            std::vector<std::filesystem::path> grammarDirectories;
            const auto collectGrammarDirectories = [&](const std::filesystem::path &root) {
                if (!std::filesystem::exists(root)) {
                    return;
                }
                for (const auto &item: std::filesystem::recursive_directory_iterator(root)) {
                    if (item.is_directory() && item.path().filename() == "grammer") {
                        grammarDirectories.push_back(item.path());
                    }
                }
            };
            collectGrammarDirectories(path);
            for (auto root = path.parent_path(); grammarDirectories.empty() && !root.empty(); root = root.parent_path()) {
                collectGrammarDirectories(root);
                if (root == root.parent_path()) {
                    break;
                }
            }
            if (!grammarDirectories.empty()) {
                for (const auto &grammarDirectory: grammarDirectories) {
                    for (const auto &file: std::filesystem::recursive_directory_iterator(grammarDirectory)) {
                        if (!file.is_regular_file()) {
                            continue;
                        }
                        LoadTrail::Scope fileScope(trail, R"(file "{}")", file.path().string());
                        GrammarEntry entry;
                        readJsonFromFile(entry, file.path(), ctx);
                        cpack->applyGrammar(std::move(entry), trail);
                    }
                }
            }
            ctx.createStage = Node::NodeCreateStage::JSON_NODE;
            for (const auto &file: std::filesystem::recursive_directory_iterator(path / "json")) {
                LoadTrail::Scope fileScope(trail, R"(file "{}")", file.path().string());
                Node::NodeJsonElement item;
                readJsonFromFile(item, file.path(), ctx);
                cpack->applyJson(std::move(item));
            }
            ctx.createStage = Node::NodeCreateStage::REPEAT_NODE;
            for (const auto &file: std::filesystem::recursive_directory_iterator(path / "repeat")) {
                LoadTrail::Scope fileScope(trail, R"(file "{}")", file.path().string());
                Node::RepeatData item;
                readJsonFromFile(item, file.path(), ctx);
                cpack->applyRepeat(std::move(item));
            }
            ctx.createStage = Node::NodeCreateStage::COMMAND_PARAM_NODE;
            for (const auto &file: std::filesystem::recursive_directory_iterator(path / "command")) {
                LoadTrail::Scope fileScope(trail, R"(file "{}")", file.path().string());
                Node::NodePerCommand item;
                readJsonFromFile(item, file.path(), ctx);
                cpack->applyCommand(std::move(item));
            }
            cpack->afterApply(trail);
            return cpack;
        } catch (const std::exception &e) {
            throw CPackLoadError(e, trail);
        }
    }

    std::unique_ptr<CPack> createCPackByJsonFile(const std::filesystem::path &cpackPath) {
        return createCPackByJson(readFileToString(cpackPath));
    }
#endif

    std::unique_ptr<CPack> createCPackByJson(const std::string &json) {
        LoadTrail trail;
        try {
            trail.push("start load CPack by JSON");
            // 单文件格式一次性读取所有节点，JSON_NODE 阶段覆盖全部可序列化的节点类型
            Node::initializeStaticNodes();
            auto cpackMemory = std::make_shared<CPackMemoryResource>();
            CPackMemoryScope memoryScope(cpackMemory);
            // cpack 必须先于 ctx/data 构造（从而后于它们析构）：
            // ~CPack 会把路由 current 重置，若 ctx/data 晚于 cpack 析构，
            // 其 pmr 成员的池内存会在 current 失效后被错误交还给全局堆
            auto cpack = std::unique_ptr<CPack>(new CPack());
            cpack->cpackMemory = cpackMemory;
            cpack->destructionMemoryScope.bindAsOwner(cpackMemory);
            cpack->commands = CHelper::allocateShared<std::pmr::vector<Node::NodePerCommand>>(cpackMemory);
            NodeReadContext ctx;
            ctx.createStage = Node::NodeCreateStage::JSON_NODE;
            ctx.cpackMemory = cpackMemory;
            CPackJsonData data;
            readJson(data, json, ctx);
            //readJson 的 glaze 解析错误自带出错位置与 JSON 片段，无需 trail 条目；
            //apply 阶段的失败由各消息自带的 id/key 定位
            cpack->manifest = std::move(data.manifest);
            const auto normalCount = std::ranges::count_if(data.id, [](const auto &entry) { return std::holds_alternative<NormalIdEntry>(entry); });
            const auto namespaceCount = std::ranges::count_if(data.id, [](const auto &entry) { return std::holds_alternative<NamespaceIdEntry>(entry); });
            cpack->normalIds.reserve(static_cast<size_t>(normalCount));
            cpack->namespaceIds.reserve(static_cast<size_t>(namespaceCount));
            cpack->grammarGraphs.reserve(data.grammar.size());
            cpack->grammarNodes.reserve(data.grammar.size());
            for (const auto &entry: data.id) {
                cpack->applyId(entry);
            }
            for (auto &entry: data.grammar) {
                cpack->applyGrammar(std::move(entry), trail);
            }
            for (auto &item: data.json) {
                cpack->applyJson(std::move(item));
            }
            for (auto &item: data.repeat) {
                cpack->applyRepeat(std::move(item));
            }
            for (auto &item: data.command) {
                cpack->applyCommand(std::move(item));
            }
            cpack->afterApply(trail);
            return cpack;
        } catch (const std::exception &e) {
            throw CPackLoadError(e, trail);
        }
    }

    std::unique_ptr<CPack> createCPackByBinary(std::string_view data) {
        LoadTrail trail;
        try {
            trail.push("start load CPack by binary");
            // 二进制格式一次性读取所有节点，JSON_NODE 阶段覆盖全部可序列化的节点类型
            Node::initializeStaticNodes();
            auto cpackMemory = std::make_shared<CPackMemoryResource>();
            CPackMemoryScope memoryScope(cpackMemory);
            // cpack 必须先于 ctx/cpackData 构造（从而后于它们析构），理由同 createCPackByJson
            auto cpack = std::unique_ptr<CPack>(new CPack());
            cpack->cpackMemory = cpackMemory;
            cpack->destructionMemoryScope.bindAsOwner(cpackMemory);
            NodeReadContext ctx;
            ctx.createStage = Node::NodeCreateStage::JSON_NODE;
            ctx.cpackMemory = cpackMemory;
            CPackData cpackData;
            auto it = data.data();
            const auto end = it + data.size();
            //段名条目压入后随作用域自动弹出：解析失败时 trail 末尾即出错段，
            //错误消息也直接带上段名
            auto readSection = [&](auto &value, std::string_view section) {
                LoadTrail::Scope sectionScope(trail, section);
                glz::parse<CHelper::BinaryFormat>::template op<glz::opts{}>(value, ctx, it, end);
                if (bool(ctx.error)) [[unlikely]] {
                    throw std::runtime_error(fmt::format("fail to parse binary cpack section: {}", section));
                }
            };
            // 先读旧格式的公共前缀；Grammar 作为尾部资源读取，因而旧二进制仍可被识别到资源末尾。
            readSection(cpackData.manifest, "manifest");
            readSection(cpackData.normalIds, "normal id data");
            readSection(cpackData.namespaceIds, "namespace id data");
            readSection(cpackData.itemIds, "item id data");
            readSection(cpackData.blockIds, "block id data");
            readSection(cpackData.jsonNodes, "json data");
            readSection(cpackData.repeatNodeData, "repeat data");
            readSection(cpackData.commands, "command data");
            if (it < end) {
                readSection(cpackData.grammar, "grammar data");
            }
            cpack->manifest = std::move(cpackData.manifest);
            cpack->normalIds = std::move(cpackData.normalIds);
            cpack->namespaceIds = std::move(cpackData.namespaceIds);
            cpack->itemIds = std::move(cpackData.itemIds);
            cpack->blockIds = std::move(cpackData.blockIds);
            cpack->jsonNodes = std::move(cpackData.jsonNodes);
            cpack->repeatNodeData = std::move(cpackData.repeatNodeData);
            cpack->commands = std::move(cpackData.commands);
            cpack->grammarGraphs.reserve(cpackData.grammar.size());
            cpack->grammarNodes.reserve(cpackData.grammar.size());
            for (auto &entry: cpackData.grammar) {
                cpack->applyGrammar(std::move(entry), trail);
            }
            cpack->afterApply(trail);
            return cpack;
        } catch (const std::exception &e) {
            throw CPackLoadError(e, trail);
        }
    }

}// namespace CHelper::serialization
