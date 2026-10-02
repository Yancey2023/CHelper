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

#include <chelper/node/NodeWithType.h>
#include <chelper/resources/id/NamespaceId.h>
#include <pch.h>

namespace CHelper {

    namespace PropertyType {
        enum PropertyType : uint8_t {
            STRING,
            BOOLEAN,
            INTEGER
        };
    }

    union PropertyValue {
        std::pmr::u16string *string;
        bool boolean = true;
        int32_t integer;
    };

    class Property {
    public:
        PropertyType::PropertyType type = PropertyType::BOOLEAN;
        std::pmr::u16string name;
        PropertyValue defaultValue;
        std::optional<std::pmr::vector<PropertyValue>> valid;

        Property() = default;

        Property(const Property &aProperty) noexcept;

        Property(Property &&aProperty) noexcept;

        Property &operator=(const Property &aProperty) noexcept;

        Property &operator=(Property &&aProperty) noexcept;

        ~Property();

        void release();
    };

    class BlockPropertyValueDescription {
    public:
        PropertyValue valueName;
        std::optional<std::pmr::u16string> description;
    };

    class BlockPropertyDescription {
    public:
        PropertyType::PropertyType type = PropertyType::BOOLEAN;
        std::pmr::u16string propertyName;
        std::optional<std::pmr::u16string> description;
        std::pmr::vector<BlockPropertyValueDescription> values;

        BlockPropertyDescription() = default;

        BlockPropertyDescription(const BlockPropertyDescription &aBlockPropertyDescription) noexcept;

        BlockPropertyDescription(BlockPropertyDescription &&aBlockPropertyDescription) noexcept;

        BlockPropertyDescription &operator=(const BlockPropertyDescription &aBlockPropertyDescription) noexcept;

        BlockPropertyDescription &operator=(BlockPropertyDescription &&aBlockPropertyDescription) noexcept;

        ~BlockPropertyDescription();

        void release();
    };

    class PerBlockPropertyDescription {
    public:
        std::pmr::vector<std::pmr::u16string> blocks;
        std::pmr::vector<BlockPropertyDescription> properties;
    };

    class BlockPropertyDescriptions {
    public:
        std::pmr::vector<BlockPropertyDescription> common;
        std::pmr::vector<PerBlockPropertyDescription> block;

        [[nodiscard]] const BlockPropertyDescription &getPropertyDescription(
                std::u16string_view blockIdWithNamespace,
                std::u16string_view blockId,
                std::u16string_view propertyName) const;

        //按条目顺序收集包含该方块 ID 的属性表（同一方块 ID 通常只属于一个条目）。
        //供 getPropertyDescription 与 BlockId::getNode 共用：条目解析一次，属性查找在条目内进行
        void collectEntryProperties(std::u16string_view blockIdWithNamespace, std::u16string_view blockId,
                                    std::vector<const std::pmr::vector<BlockPropertyDescription> *> &entries) const;
    };

    // 仅在整包初始化期间使用；索引引用源描述，源数据在使用期间不得修改。
    // 保留资源条目顺序与重复属性的首匹配规则，不向 CPack 添加永久缓存。
    class BlockPropertyDescriptionIndex {
        struct CommonEntry {
            std::u16string_view key;
            size_t order;
            const BlockPropertyDescription *value;
        };
        struct BlockEntry {
            std::u16string_view key;
            size_t order;
            const std::pmr::vector<BlockPropertyDescription> *values;
        };
        std::vector<CommonEntry> common;
        std::vector<BlockEntry> block;

    public:
        explicit BlockPropertyDescriptionIndex(const BlockPropertyDescriptions &descriptions);

        [[nodiscard]] const BlockPropertyDescription &getPropertyDescription(
                std::u16string_view blockIdWithNamespace, std::u16string_view blockId, std::u16string_view propertyName) const;
    };

    class BlockId : public NamespaceId {
    public:
        std::optional<std::pmr::vector<Property>> properties;

    private:
        Node::FreeableNodeWithTypes nodeChildren;
        std::optional<Node::NodeWithType> node;

    public:
        const Node::NodeWithType &getNode(const BlockPropertyDescriptions &blockPropertyDescriptions,
                                          const BlockPropertyDescriptionIndex *index = nullptr);

        static Node::NodeWithType getNodeAllBlockState();
    };

    class BlockIds {
    public:
        std::shared_ptr<std::pmr::vector<std::shared_ptr<BlockId>>> blockStateValues;
        BlockPropertyDescriptions blockPropertyDescriptions;
    };


}// namespace CHelper
