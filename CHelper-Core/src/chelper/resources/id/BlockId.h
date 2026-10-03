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
#include <chelper/util/HashContainer.h>
#include <pch.h>
#include <span>

namespace CHelper {

    namespace PropertyType {
        enum PropertyType : uint8_t {
            STRING,
            BOOLEAN,
            INTEGER
        };
    }

    // 属性字符串的对象本体与字符缓冲区一起归属 CPack 内存池。
    // 对象分配头保留实际资源；字符缓冲区仍遵循 PMR 字符串的资源生命周期。
    class PropertyString final : public std::pmr::u16string {
    public:
        using std::pmr::u16string::basic_string;
        using std::pmr::u16string::operator=;

        static void *operator new(size_t bytes) {
            return allocateCPackObject(bytes, alignof(PropertyString));
        }

        static void operator delete(void *pointer) noexcept { deallocateCPackObject(pointer); }
    };

    union PropertyValue {
        PropertyString *string;
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

    struct BlockPropertyNode {
        Node::FreeableNodeWithTypes children;
        Node::NodeWithType node;
    };

    struct BlockStateNode {
        std::pmr::vector<std::shared_ptr<BlockPropertyNode>> properties;
        Node::FreeableNodeWithTypes children;
        Node::NodeWithType node;
    };

    // 仅在初始化期间查找重复属性；源描述和 Property 必须保持稳定直到缓存销毁。
    // 节点图由每个使用它的 BlockId 共享持有，不依赖缓存或另一个方块的生命周期。
    class BlockPropertyNodeCache {
        struct Key {
            const BlockPropertyDescription *description;
            const Property *property;
        };
        struct Hash {
            using is_avalanching = void;
            uint64_t operator()(const Key &key) const noexcept;
        };
        struct Equal {
            bool operator()(const Key &left, const Key &right) const;
        };
        // 缓存只在初始化期间存在，数组扩容和销毁应立即释放内存；节点图仍由 CPack 内存池持有。
        DenseMap<Key, std::shared_ptr<BlockPropertyNode>, Hash, Equal> nodes;
        using StateKey = std::span<const std::shared_ptr<BlockPropertyNode>>;
        struct StateHash {
            using is_avalanching = void;
            uint64_t operator()(StateKey key) const noexcept;
        };
        struct StateEqual {
            bool operator()(StateKey left, StateKey right) const noexcept;
        };
        // 键引用共享图内的稳定数组；临时缓存销毁后由 BlockId 继续持有图。
        DenseMap<StateKey, std::shared_ptr<BlockStateNode>, StateHash, StateEqual> states;

    public:
        explicit BlockPropertyNodeCache(const BlockPropertyDescriptions &descriptions);
        [[nodiscard]] std::shared_ptr<BlockPropertyNode> getNode(const BlockPropertyDescription &description,
                                                                 const Property &property);
        [[nodiscard]] std::shared_ptr<BlockStateNode> getStateNode(std::pmr::vector<std::shared_ptr<BlockPropertyNode>> properties);
    };

    class BlockId : public NamespaceId {
    public:
        std::optional<std::pmr::vector<Property>> properties;

    private:
        std::shared_ptr<BlockStateNode> sharedStateNode;
        Node::FreeableNodeWithTypes nodeChildren;
        std::optional<Node::NodeWithType> node;

    public:
        const Node::NodeWithType &getNode(const BlockPropertyDescriptions &blockPropertyDescriptions,
                                          const BlockPropertyDescriptionIndex *index = nullptr,
                                          BlockPropertyNodeCache *propertyNodes = nullptr);

        static Node::NodeWithType getNodeAllBlockState();
    };

    class BlockIds {
    public:
        std::shared_ptr<std::pmr::vector<std::shared_ptr<BlockId>>> blockStateValues;
        BlockPropertyDescriptions blockPropertyDescriptions;
    };


}// namespace CHelper
