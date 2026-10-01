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

#pragma once

#ifndef CHELPER_CPACKBUILDER_H
#define CHELPER_CPACKBUILDER_H

#include <chelper/resources/CPack.h>

namespace CHelper {

    /**
     * CPack 装载器：把"文件集合"（主包启用段视图 + 拓展包文件）累积装载后统一物化。
     * 供 Composer / 段装载器使用；装载语义与 CPack 现有目录/JSON 构造路径一致，
     * 区别是数据来源为内存中的文件（relPath → 字节），不依赖文件系统。
     *
     * 非线程安全：一次性构建对象，build() 后不可复用（数据已移入 CPack）。
     */
    class CPackBuilder {
    public:
        Manifest manifest;
        std::unordered_map<std::string, std::shared_ptr<std::vector<std::shared_ptr<NormalId>>>> normalIds;
        std::unordered_map<std::string, std::shared_ptr<std::vector<std::shared_ptr<NamespaceId>>>> namespaceIds;
        std::shared_ptr<BlockIds> blockIds;
        std::shared_ptr<std::vector<std::shared_ptr<ItemId>>> itemIds;
        std::vector<Node::NodeJsonElement> jsonNodes;
        std::vector<Node::RepeatData> repeatNodeData;
        std::vector<Node::NodePerCommand> commands;
        // selector/*.json V1（拓展包数据化；由合成器装载后物化时挂到 CPack）
        std::vector<Node::SelectorPackVariable> selectorVariables;
        std::vector<Node::SelectorPackArgument> selectorArguments;
        // 命令别名 → 来源包名（合成器在装载拓展包命令时登记；物化时挂到 CPack）
        std::unordered_map<std::u16string, std::u16string> commandNameSources;

        /**
         * 按包内相对路径装载单个 json 文件。
         * relPath 支持 `/` 或 `\` 分隔；识别 manifest.json 与 command、id、json、repeat 四个数据目录。
         * json 解析失败、路径不识别 → 抛 std::runtime_error（整包拒绝）。
         */
        void applyFile(const std::string &relPath, const std::vector<uint8_t> &bytes);

        /**
         * 物化：把装载数据移入新 CPack 并执行 afterApply()，返回只读共享 CPack。
         */
        std::shared_ptr<const CPack> build();

    private:
        using JsonValueType = rapidjson::GenericValue<rapidjson::UTF8<>>;

        void applyManifest(const JsonValueType &j);

        void applyId(const JsonValueType &j);

        void applyJson(const JsonValueType &j);

        void applyRepeat(const JsonValueType &j);

        void applyCommand(const JsonValueType &j);
    };

}// namespace CHelper

#endif//CHELPER_CPACKBUILDER_H
