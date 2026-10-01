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

#ifndef CHELPER_COMPOSER_H
#define CHELPER_COMPOSER_H

#include <chelper/extension/MainPack.h>
#include <chelper/resources/CPackBuilder.h>

namespace CHelper::Extension {

    // 一个已启用拓展包（平台层已解压的文件集合，含 manifest.json / command / ID / json / extensions）
    struct ExtensionPackData {
        std::vector<PackFile> files;
    };

    struct ComposeOptions {
        // 预留：如是否启用来源标注、冲突策略开关
    };

    struct ComposeResult {
        std::shared_ptr<const CPack> cpack;
        // 被更高优先级包覆盖（忽略）的命令别名
        std::vector<std::string> overriddenCommands;
        // 非致命告警（如不支持合并的数据、缺来源字段等）
        std::vector<std::string> warnings;
        // 命令来源索引：命令别名(UTF-8) → 来源包名（缺省/空 = 内置）
        std::unordered_map<std::string, std::u16string> commandSources;
    };

    /**
     * 合成器：把"主包启用段视图 + 已启用拓展包"合成单个只读 CPack。
     * 装载顺序：主包段 → 拓展包（按传入顺序，先到先得）；同名命令冲突时后包被忽略。
     * 合并规则见 composer.md §3；P0 范围：command/id(json)/json/repeat(execute 分支)，block/selector 暂不支持（告警）。
     */
    ComposeResult compose(const SegmentData &segment,
                          const std::vector<ExtensionPackData> &packs,
                          const ComposeOptions &options = {});

}// namespace CHelper::Extension

#endif//CHELPER_COMPOSER_H
