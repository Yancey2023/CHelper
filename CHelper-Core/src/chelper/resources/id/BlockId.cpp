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

#include <chelper/node/CommandNode.h>
#include <chelper/resources/id/BlockId.h>

namespace CHelper {

    static Node::NodeSingleSymbol nodeBlockStateLeftBracket('[', u"方块状态左括号");
    static Node::NodeString nodeBlockStateEntryKey("BLOCK_STATE_ENTRY_KEY", u"方块状态键值对的键", false, false, false);
    static Node::NodeSingleSymbol nodeBlockStateEntrySeparator('=', u"方块状态键值对分隔符");
    static Node::NodeBoolean nodeBlockStateEntryValueBoolean("BLOCK_STATE_ENTRY_VALUE_BOOLEAN", u"方块状态键值对的值（布尔值）", std::nullopt, std::nullopt);
    static Node::NodeInteger nodeBlockStateEntryValueInteger("BLOCK_STATE_ENTRY_VALUE_INTEGER", u"方块状态键值对的值（整数）", std::nullopt, std::nullopt);
    static Node::NodeFloat nodeBlockStateEntryValueFloat("BLOCK_STATE_ENTRY_VALUE_FLOAT", u"方块状态键值对的值（小数）", std::nullopt, std::nullopt);
    static Node::NodeString nodeBlockStateEntryValueString("BLOCK_STATE_ENTRY_VALUE_STRING", u"方块状态键值对的值（字符串）", false, true, false);
    static Node::NodeOr nodeBlockStateEntryAllValue(
            {nodeBlockStateEntryValueBoolean, nodeBlockStateEntryValueInteger,
             nodeBlockStateEntryValueFloat, nodeBlockStateEntryValueString},
            false, false, true,
            u"类型不匹配，当前内容不是有效的方块状态值");
    static Node::NodeEntry nodeBlockStateAllEntry(nodeBlockStateEntryKey, nodeBlockStateEntrySeparator, nodeBlockStateEntryAllValue);
    static Node::NodeSingleSymbol nodeBlockStateSeparator(',', u"方块状态分隔符");
    static Node::NodeSingleSymbol nodeBlockStateRightBracket(']', u"方块状态右括号");
    static Node::NodeList nodeAllBlockState(
            nodeBlockStateLeftBracket, nodeBlockStateAllEntry,
            nodeBlockStateSeparator, nodeBlockStateRightBracket);

    Property::Property(const Property &aProperty) noexcept {
        type = aProperty.type;
        name = aProperty.name;
        if (type == PropertyType::STRING) {
            defaultValue.string = new std::pmr::u16string(*aProperty.defaultValue.string);
            if (aProperty.valid.has_value()) {
                size_t size = aProperty.valid.value().size();
                valid = std::pmr::vector<PropertyValue>(size);
                for (size_t i = 0; i < size; ++i) {
                    valid.value()[i].string = new std::pmr::u16string(*aProperty.valid.value()[i].string);
                }
            } else {
                valid = std::nullopt;
            }
        } else {
            defaultValue = aProperty.defaultValue;
            valid = aProperty.valid;
        }
    }

    Property::Property(Property &&aProperty) noexcept {
        type = aProperty.type;
        aProperty.type = PropertyType::BOOLEAN;
        name = std::move(aProperty.name);
        defaultValue = aProperty.defaultValue;
        aProperty.defaultValue.boolean = true;
        valid = std::move(aProperty.valid);
    }

    Property &Property::operator=(const Property &aProperty) noexcept {
        type = aProperty.type;
        name = aProperty.name;
        if (type == PropertyType::STRING) {
            defaultValue.string = new std::pmr::u16string(*aProperty.defaultValue.string);
            if (aProperty.valid.has_value()) {
                size_t size = aProperty.valid.value().size();
                valid = std::pmr::vector<PropertyValue>(size);
                for (size_t i = 0; i < size; ++i) {
                    valid.value()[i].string = new std::pmr::u16string(*aProperty.valid.value()[i].string);
                }
            } else {
                valid = std::nullopt;
            }
        } else {
            defaultValue = aProperty.defaultValue;
            valid = aProperty.valid;
        }
        return *this;
    }

    Property &Property::operator=(Property &&aProperty) noexcept {
        type = aProperty.type;
        aProperty.type = PropertyType::BOOLEAN;
        name = std::move(aProperty.name);
        defaultValue = aProperty.defaultValue;
        aProperty.defaultValue.boolean = true;
        valid = std::move(aProperty.valid);
        return *this;
    }

    Property::~Property() {
        if (type == PropertyType::STRING) {
            delete defaultValue.string;
            if (valid.has_value()) {
                for (const auto &item: valid.value()) {
                    delete item.string;
                }
            }
        }
    }

    void Property::release() {
        if (type == PropertyType::STRING) {
            delete defaultValue.string;
            if (valid.has_value()) {
                for (const auto &item: valid.value()) {
                    delete item.string;
                }
            }
        }
        type = PropertyType::BOOLEAN;
        defaultValue.boolean = true;
        valid = std::nullopt;
    }

    BlockPropertyDescription::BlockPropertyDescription(const BlockPropertyDescription &aBlockPropertyDescription) noexcept {
        type = aBlockPropertyDescription.type;
        propertyName = aBlockPropertyDescription.propertyName;
        description = aBlockPropertyDescription.description;
        if (type == PropertyType::STRING) {
            size_t size = aBlockPropertyDescription.values.size();
            values.resize(size);
            for (size_t i = 0; i < size; ++i) {
                BlockPropertyValueDescription &t1 = values[i];
                const BlockPropertyValueDescription &t2 = aBlockPropertyDescription.values[i];
                t1.valueName.string = new std::pmr::u16string(*t2.valueName.string);
                t1.description = t2.description;
            }
        } else {
            values = aBlockPropertyDescription.values;
        }
    }

    BlockPropertyDescription::BlockPropertyDescription(BlockPropertyDescription &&aBlockPropertyDescription) noexcept {
        type = aBlockPropertyDescription.type;
        aBlockPropertyDescription.type = PropertyType::BOOLEAN;
        propertyName = std::move(aBlockPropertyDescription.propertyName);
        description = std::move(aBlockPropertyDescription.description);
        values = std::move(aBlockPropertyDescription.values);
    }

    BlockPropertyDescription &BlockPropertyDescription::operator=(const BlockPropertyDescription &aBlockPropertyDescription) noexcept {
        type = aBlockPropertyDescription.type;
        propertyName = aBlockPropertyDescription.propertyName;
        description = aBlockPropertyDescription.description;
        if (type == PropertyType::STRING) {
            size_t size = aBlockPropertyDescription.values.size();
            values.resize(size);
            for (size_t i = 0; i < size; ++i) {
                BlockPropertyValueDescription &t1 = values[i];
                const BlockPropertyValueDescription &t2 = aBlockPropertyDescription.values[i];
                t1.valueName.string = new std::pmr::u16string(*t2.valueName.string);
                t1.description = t2.description;
            }
        } else {
            values = aBlockPropertyDescription.values;
        }
        return *this;
    }

    BlockPropertyDescription &BlockPropertyDescription::operator=(BlockPropertyDescription &&aBlockPropertyDescription) noexcept {
        type = aBlockPropertyDescription.type;
        aBlockPropertyDescription.type = PropertyType::BOOLEAN;
        propertyName = std::move(aBlockPropertyDescription.propertyName);
        description = std::move(aBlockPropertyDescription.description);
        values = std::move(aBlockPropertyDescription.values);
        return *this;
    }

    BlockPropertyDescription::~BlockPropertyDescription() {
        if (type == PropertyType::STRING) {
            for (const auto &item: values) {
                delete item.valueName.string;
            }
        }
    }
    void BlockPropertyDescription::release() {
        if (type == PropertyType::STRING) {
            for (const auto &item: values) {
                delete item.valueName.string;
            }
        }
        type = PropertyType::BOOLEAN;
        values.clear();
    }

    void BlockPropertyDescriptions::collectEntryProperties(
            const std::u16string_view blockIdWithNamespace, const std::u16string_view blockId,
            std::vector<const std::pmr::vector<BlockPropertyDescription> *> &entries) const {
        for (const auto &item: block) {
            if (std::ranges::find(item.blocks, blockId) != item.blocks.end() ||
                std::ranges::find(item.blocks, blockIdWithNamespace) != item.blocks.end()) {
                entries.push_back(&item.properties);
            }
        }
    }

    const BlockPropertyDescription &BlockPropertyDescriptions::getPropertyDescription(
            const std::u16string_view blockIdWithNamespace,
            const std::u16string_view blockId,
            const std::u16string_view propertyName) const {
        std::vector<const std::pmr::vector<BlockPropertyDescription> *> entries;
        collectEntryProperties(blockIdWithNamespace, blockId, entries);
        for (const auto *properties: entries) {
            const auto &it = std::ranges::find_if(*properties, [&propertyName](const BlockPropertyDescription &item1) -> bool {
                return item1.propertyName == propertyName;
            });
            if (it != properties->end()) [[likely]] {
                return *it;
            }
        }
        const auto &it = std::ranges::find_if(common, [&propertyName](const BlockPropertyDescription &item1) -> bool {
            return item1.propertyName == propertyName;
        });
        if (it != common.end()) [[likely]] {
            return *it;
        }
        throw std::runtime_error(fmt::format(
                "fail to find block property value by block id {} and property name {}",
                utf8::utf16to8(blockIdWithNamespace),
                utf8::utf16to8(propertyName)));
    }

    BlockPropertyDescriptionIndex::BlockPropertyDescriptionIndex(const BlockPropertyDescriptions &descriptions) {
        common.reserve(descriptions.common.size());
        for (size_t i = 0; i < descriptions.common.size(); ++i) {
            const auto &entry = descriptions.common[i];
            common.push_back({entry.propertyName, i, &entry});
        }
        size_t blockCount = 0;
        for (const auto &entry: descriptions.block) blockCount += entry.blocks.size();
        block.reserve(blockCount);
        for (size_t i = 0; i < descriptions.block.size(); ++i) {
            const auto &entry = descriptions.block[i];
            for (const auto &id: entry.blocks) block.push_back({id, i, &entry.properties});
        }
        const auto less = [](const auto &left, const auto &right) {
            return left.key != right.key ? left.key < right.key : left.order < right.order;
        };
        std::sort(common.begin(), common.end(), less);
        std::sort(block.begin(), block.end(), less);
        block.erase(std::unique(block.begin(), block.end(), [](const BlockEntry &left, const BlockEntry &right) {
                        return left.key == right.key && left.order == right.order;
                    }),
                    block.end());
    }

    const BlockPropertyDescription &BlockPropertyDescriptionIndex::getPropertyDescription(
            const std::u16string_view blockIdWithNamespace, const std::u16string_view blockId,
            const std::u16string_view propertyName) const {
        const auto findBlock = [&](const std::u16string_view id) {
            return std::lower_bound(block.begin(), block.end(), id,
                                    [](const BlockEntry &entry, const std::u16string_view key) { return entry.key < key; });
        };
        auto plain = findBlock(blockId);
        auto qualified = findBlock(blockIdWithNamespace);
        const auto matches = [&](const auto &it, const std::u16string_view id) {
            return it != block.end() && it->key == id;
        };
        // 两种 ID 可能命中不同条目，按源顺序合并，并去掉同时匹配两种 ID 的同一条目。
        while (matches(plain, blockId) || matches(qualified, blockIdWithNamespace)) {
            const BlockEntry *entry;
            if (matches(plain, blockId) && (!matches(qualified, blockIdWithNamespace) || plain->order <= qualified->order)) {
                entry = &*plain++;
                if (matches(qualified, blockIdWithNamespace) && qualified->order == entry->order) ++qualified;
            } else {
                entry = &*qualified++;
            }
            const auto property = std::ranges::find_if(*entry->values,
                                                       [&](const auto &value) { return value.propertyName == propertyName; });
            if (property != entry->values->end()) return *property;
        }
        const auto property = std::lower_bound(common.begin(), common.end(), propertyName,
                                               [](const CommonEntry &entry, const std::u16string_view key) { return entry.key < key; });
        if (property != common.end() && property->key == propertyName) return *property->value;
        throw std::runtime_error(fmt::format(
                "fail to find block property value by block id {} and property name {}",
                utf8::utf16to8(blockIdWithNamespace), utf8::utf16to8(propertyName)));
    }

    template<class String>
    static std::shared_ptr<NormalId> makeQuotedId(const std::u16string_view name, const std::optional<String> &description) {
        auto result = allocateSharedFromDefault<NormalId>();
        result->name.reserve(name.size() + 2);
        result->name.push_back(u'"');
        result->name.append(name);
        result->name.push_back(u'"');
        result->description = copyPmrU16StringOptional(description);
        return result;
    }

    Node::NodeText *getBlockStateValueNode(
            const BlockPropertyValueDescription &blockPropertyValueDescription,
            const PropertyType::PropertyType &type,
            const std::optional<std::pmr::u16string> &defaultDescription,
            bool isDefaultValue,
            bool isInvalid) {
        std::optional<std::u16string_view> description;
        if (blockPropertyValueDescription.description.has_value()) {
            description = std::u16string_view(blockPropertyValueDescription.description.value());
        } else if (defaultDescription.has_value()) {
            description = std::u16string_view(defaultDescription.value());
        }
        std::pmr::u16string annotatedDescription;
        if (isDefaultValue || isInvalid) {
            annotatedDescription.reserve(description.value_or(u"").size() + (isDefaultValue ? 5 : 0) + (isInvalid ? 4 : 0));
            if (isInvalid) annotatedDescription.append(u"（无效）");
            if (isDefaultValue) annotatedDescription.append(u"（默认值）");
            if (description.has_value()) annotatedDescription.append(*description);
            description = std::u16string_view(annotatedDescription);
        }
        switch (type) {
            case PropertyType::STRING:
                return new Node::NodeText(
                        "BLOCK_STATE_ENTRY_VALUE_STRING", u"方块状态键值对的键（字符串）",
                        makeQuotedId(*blockPropertyValueDescription.valueName.string, description));
            case PropertyType::INTEGER:
                return new Node::NodeText(
                        "BLOCK_STATE_ENTRY_VALUE_INTEGER", u"方块状态键值对的键（整数）",
                        NormalId::make(::CHelper::U16Conv::toU16(std::to_string(blockPropertyValueDescription.valueName.integer)), description),
                        [](const Node::NodeWithType &node1, TokenReader &tokenReader) -> ASTNode {
                            return tokenReader.readIntegerASTNode(node1);
                        });
            case PropertyType::BOOLEAN:
                return new Node::NodeText(
                        "BLOCK_STATE_ENTRY_VALUE_BOOLEAN", u"方块状态键值对的键（布尔值）",
                        NormalId::make(blockPropertyValueDescription.valueName.boolean ? u"true" : u"false", description));
            default:
                CHELPER_UNREACHABLE();
        }
    }

    Node::NodeEntry *getBlockStateNode(
            std::pmr::vector<Node::NodeWithType> &nodeChildren,
            const BlockPropertyDescription &blockPropertyDescription,
            PropertyValue defaultValue,
            const std::optional<std::pmr::vector<PropertyValue>> &valid) {
        std::pmr::vector<Node::NodeWithType> valueNodes;
        valueNodes.reserve(blockPropertyDescription.values.size());
        for (auto &item: blockPropertyDescription.values) {
            bool isDefaultValue;
            switch (blockPropertyDescription.type) {
                case PropertyType::STRING:
                    isDefaultValue = *item.valueName.string == *defaultValue.string;
                    break;
                case PropertyType::BOOLEAN:
                    isDefaultValue = item.valueName.boolean == defaultValue.boolean;
                    break;
                case PropertyType::INTEGER:
                    isDefaultValue = item.valueName.integer == defaultValue.integer;
                    break;
                default:
                    CHELPER_UNREACHABLE();
            }
            bool isInvalid;
            if (valid.has_value()) {
                isInvalid = true;
                switch (blockPropertyDescription.type) {
                    case PropertyType::STRING:
                        for (const auto &item1: valid.value()) {
                            if (*item.valueName.string == *item1.string) {
                                isInvalid = false;
                                break;
                            }
                        }
                        break;
                    case PropertyType::BOOLEAN:
                        for (const auto &item1: valid.value()) {
                            if (item.valueName.boolean == item1.boolean) {
                                isInvalid = false;
                                break;
                            }
                        }
                        break;
                    case PropertyType::INTEGER:
                        for (const auto &item1: valid.value()) {
                            if (item.valueName.integer == item1.integer) {
                                isInvalid = false;
                                break;
                            }
                        }
                        break;
                    default:
                        CHELPER_UNREACHABLE();
                }
            } else {
                isInvalid = false;
            }
            Node::NodeText *node = getBlockStateValueNode(
                    item, blockPropertyDescription.type,
                    blockPropertyDescription.description, isDefaultValue, isInvalid);
            valueNodes.emplace_back(*node);
            nodeChildren.emplace_back(*node);
        }
        //key = value
        auto nodeKey = new Node::NodeText(
                "BLOCK_STATE_ENTRY_KEY", u"方块状态键值对的键",
                makeQuotedId(blockPropertyDescription.propertyName, blockPropertyDescription.description));
        auto nodeValue = new Node::NodeOr(std::move(valueNodes), false);
        auto result = new Node::NodeEntry(*nodeKey, nodeBlockStateEntrySeparator, *nodeValue);
        nodeChildren.emplace_back(*nodeKey);
        nodeChildren.emplace_back(*nodeValue);
        return result;
    }

    const Node::NodeWithType &BlockId::getNode(const BlockPropertyDescriptions &blockPropertyDescriptions,
                                               const BlockPropertyDescriptionIndex *index) {
        if (!node.has_value()) {
            std::pmr::vector<Node::NodeWithType> blockStateEntryChildNode2;
            //已知的方块状态
            if (properties.has_value()) [[likely]] {
                blockStateEntryChildNode2.reserve(2);
                std::pmr::vector<Node::NodeWithType> blockStateEntryChildNode1;
                blockStateEntryChildNode1.reserve(properties.value().size());
                //所属条目只解析一次，避免每属性重复线性扫全部条目
                std::vector<const std::pmr::vector<BlockPropertyDescription> *> entryProperties;
                if (index == nullptr) {
                    blockPropertyDescriptions.collectEntryProperties(getIdWithNamespace()->name, name, entryProperties);
                }
                const auto findDescription = [&](const std::u16string_view propertyName) -> const BlockPropertyDescription & {
                    if (index != nullptr) {
                        return index->getPropertyDescription(getIdWithNamespace()->name, name, propertyName);
                    }
                    for (const auto *entry: entryProperties) {
                        const auto &it = std::ranges::find_if(*entry, [&propertyName](const BlockPropertyDescription &item1) -> bool {
                            return item1.propertyName == propertyName;
                        });
                        if (it != entry->end()) [[likely]] {
                            return *it;
                        }
                    }
                    const auto &it = std::ranges::find_if(blockPropertyDescriptions.common,
                                                          [&propertyName](const BlockPropertyDescription &item1) -> bool {
                                                              return item1.propertyName == propertyName;
                                                          });
                    if (it != blockPropertyDescriptions.common.end()) [[likely]] {
                        return *it;
                    }
                    throw std::runtime_error(fmt::format(
                            "fail to find block property value by block id {} and property name {}",
                            utf8::utf16to8(getIdWithNamespace()->name),
                            utf8::utf16to8(propertyName)));
                };
                std::ranges::transform(
                        properties.value(),
                        std::back_inserter(blockStateEntryChildNode1),
                        [&](const auto &item) -> Node::NodeWithType {
                            const BlockPropertyDescription &blockPropertyDescription = findDescription(item.name);
                            Node::NodeEntry *result = getBlockStateNode(
                                    nodeChildren.nodes, blockPropertyDescription,
                                    item.defaultValue, item.valid);
                            nodeChildren.nodes.emplace_back(*result);
                            return *result;
                        });
                auto nodeChild = new Node::NodeOr(std::move(blockStateEntryChildNode1), false);
                blockStateEntryChildNode2.emplace_back(*nodeChild);
                nodeChildren.nodes.emplace_back(*nodeChild);
            }
            //其他未知的方块状态
            blockStateEntryChildNode2.emplace_back(nodeBlockStateAllEntry);
            //把所有方块状态拼在一起
            auto nodeValue = new Node::NodeOr(std::move(blockStateEntryChildNode2), false, true);
            auto result = new Node::NodeList(
                    nodeBlockStateLeftBracket,
                    *nodeValue,
                    nodeBlockStateSeparator,
                    nodeBlockStateRightBracket);
            node = *result;
            nodeChildren.nodes.emplace_back(*result);
            nodeChildren.nodes.emplace_back(*nodeValue);
        }
        return node.value();
    }

    Node::NodeWithType BlockId::getNodeAllBlockState() {
        return nodeAllBlockState;
    }

}// namespace CHelper
