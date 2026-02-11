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

#ifndef CHELPER_BLOCKID_H
#define CHELPER_BLOCKID_H

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
        std::u16string *string;
        bool boolean = true;
        int32_t integer;
    };

    class Property {
    public:
        PropertyType::PropertyType type = PropertyType::BOOLEAN;
        std::u16string name;
        PropertyValue defaultValue;
        std::optional<std::vector<PropertyValue>> valid;

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
        std::optional<std::u16string> description;
    };

    class BlockPropertyDescription {
    public:
        PropertyType::PropertyType type = PropertyType::BOOLEAN;
        std::u16string propertyName;
        std::optional<std::u16string> description;
        std::vector<BlockPropertyValueDescription> values;

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
        std::vector<std::u16string> blocks;
        std::vector<BlockPropertyDescription> properties;
    };

    class BlockPropertyDescriptions {
    public:
        std::vector<BlockPropertyDescription> common;
        std::vector<PerBlockPropertyDescription> block;

        [[nodiscard]] const BlockPropertyDescription &getPropertyDescription(
                const std::u16string &blockIdWithNamespace,
                const std::u16string &blockId,
                const std::u16string &propertyName) const;
    };

    class BlockId : public NamespaceId {
    public:
        std::optional<std::vector<Property>> properties;

    private:
        Node::FreeableNodeWithTypes nodeChildren;
        std::optional<Node::NodeWithType> node;

    public:
        const Node::NodeWithType &getNode(const BlockPropertyDescriptions &blockPropertyDescriptions);

        static Node::NodeWithType getNodeAllBlockState();
    };

    class BlockIds {
    public:
        std::shared_ptr<std::vector<std::shared_ptr<BlockId>>> blockStateValues;
        BlockPropertyDescriptions blockPropertyDescriptions;
    };

}// namespace CHelper

template<>
struct serialization::Codec<CHelper::PropertyValue> {

    using Type = CHelper::PropertyValue;

    constexpr static bool enable = true;

    template<class JsonValueType>
    static void to_json(typename JsonValueType::AllocatorType &allocator,
                        JsonValueType &jsonValue,
                        const Type &t,
                        const CHelper::PropertyType::PropertyType &propertyType) {
        switch (propertyType) {
            case CHelper::PropertyType::PropertyType::STRING:
                Codec<std::remove_pointer_t<decltype(t.string)>>::template to_json<JsonValueType>(allocator, jsonValue, *t.string);
                break;
            case CHelper::PropertyType::PropertyType::BOOLEAN:
                Codec<decltype(t.boolean)>::template to_json<JsonValueType>(allocator, jsonValue, t.boolean);
                break;
            case CHelper::PropertyType::PropertyType::INTEGER:
                Codec<decltype(t.integer)>::template to_json<JsonValueType>(allocator, jsonValue, t.integer);
                break;
            default:
                CHELPER_UNREACHABLE();
        }
    }

    template<class JsonValueType>
    static CHelper::PropertyType::PropertyType
    from_json(const JsonValueType &jsonValue,
              Type &t) {
        if (jsonValue.IsString()) [[likely]] {
            t.string = new std::u16string();
            Codec<std::remove_pointer_t<decltype(t.string)>>::template from_json<JsonValueType>(jsonValue, *t.string);
            return CHelper::PropertyType::PropertyType::STRING;
        }
        if (jsonValue.IsBool()) [[likely]] {
            Codec<decltype(t.boolean)>::template from_json<JsonValueType>(jsonValue, t.boolean);
            return CHelper::PropertyType::PropertyType::BOOLEAN;
        }
        if (jsonValue.IsInt()) [[likely]] {
            Codec<decltype(t.integer)>::template from_json<JsonValueType>(jsonValue, t.integer);
            return CHelper::PropertyType::PropertyType::INTEGER;
        }
        throw exceptions::JsonSerializationTypeException("int32 or boolean or string", getJsonTypeStr(jsonValue.GetType()));
    }

    template<class JsonValueType>
    static void to_json_member(typename JsonValueType::AllocatorType &allocator,
                               JsonValueType &jsonValue,
                               const typename JsonValueType::Ch *key,
                               const Type &t,
                               const CHelper::PropertyType::PropertyType &propertyType) {
        assert(jsonValue.IsObject());
        typename JsonValueType::ValueType value;
        Codec<Type>::template to_json<typename JsonValueType::ValueType>(allocator, value, t, propertyType);
        assert(!value.IsNull());
        jsonValue.AddMember(JsonValueType(key, allocator), value, allocator);
    }

    template<class JsonValueType>
    static CHelper::PropertyType::PropertyType
    from_json_member(const JsonValueType &jsonValue,
                     const typename JsonValueType::Ch *key,
                     Type &t) {
        static_assert(Codec<Type>::enable, "fail to find impl of Codec");
        assert(jsonValue.IsObject());
        return Codec<Type>::template from_json<JsonValueType>(serialization::find_member_or_throw(jsonValue, key), t);
    }

    template<bool isNeedConvert>
    static void to_binary(std::ostream &ostream,
                          const Type &t,
                          const CHelper::PropertyType::PropertyType &propertyType) {
        switch (propertyType) {
            case CHelper::PropertyType::PropertyType::STRING:
                Codec<std::remove_pointer_t<decltype(t.string)>>::template to_binary<isNeedConvert>(ostream, *t.string);
                break;
            case CHelper::PropertyType::PropertyType::BOOLEAN:
                Codec<decltype(t.boolean)>::template to_binary<isNeedConvert>(ostream, t.boolean);
                break;
            case CHelper::PropertyType::PropertyType::INTEGER:
                Codec<decltype(t.integer)>::template to_binary<isNeedConvert>(ostream, t.integer);
                break;
            default:
                CHELPER_UNREACHABLE();
        }
    }

    template<bool isNeedConvert>
    static void from_binary(std::istream &istream,
                            Type &t,
                            const CHelper::PropertyType::PropertyType &propertyType) {
        switch (propertyType) {
            case CHelper::PropertyType::PropertyType::STRING:
                t.string = new std::u16string();
                Codec<std::remove_pointer_t<decltype(t.string)>>::template from_binary<isNeedConvert>(istream, *t.string);
                break;
            case CHelper::PropertyType::PropertyType::BOOLEAN:
                Codec<decltype(t.boolean)>::template from_binary<isNeedConvert>(istream, t.boolean);
                break;
            case CHelper::PropertyType::PropertyType::INTEGER:
                Codec<decltype(t.integer)>::template from_binary<isNeedConvert>(istream, t.integer);
                break;
            default:
                throw std::runtime_error("error block state property type");
        }
    }
};

CODEC_ENUM(CHelper::PropertyType::PropertyType, uint8_t);

CODEC_REGISTER_JSON_KEY(CHelper::Property, name, defaultValue, valid);

template<>
struct serialization::Codec<CHelper::Property> : BaseCodec<CHelper::Property> {

    using Type = CHelper::Property;

    constexpr static bool enable = true;

    template<class JsonValueType>
    static void to_json(typename JsonValueType::AllocatorType &allocator,
                        JsonValueType &jsonValue,
                        const Type &t) {
        jsonValue.SetObject();
        Codec<decltype(t.name)>::template to_json_member<JsonValueType>(allocator, jsonValue, details::JsonKey<CHelper::Property, typename JsonValueType::Ch>::name_(), t.name);
        Codec<decltype(t.defaultValue)>::template to_json_member<JsonValueType>(allocator, jsonValue, details::JsonKey<CHelper::Property, typename JsonValueType::Ch>::defaultValue_(), t.defaultValue, t.type);
        if (t.valid.has_value()) {
            rapidjson::GenericValue<typename JsonValueType::EncodingType> valid;
            valid.SetArray();
            valid.Reserve(static_cast<rapidjson::SizeType>(t.valid.value().size()), allocator);
            for (const auto &item: t.valid.value()) {
                rapidjson::GenericValue<typename JsonValueType::EncodingType> perValid;
                Codec<CHelper::PropertyValue>::template to_json<typename JsonValueType::ValueType>(allocator, perValid, item, t.type);
                valid.PushBack(std::move(perValid), allocator);
            }
            jsonValue.AddMember(JsonValueType(details::JsonKey<CHelper::Property, typename JsonValueType::Ch>::valid_(), allocator), std::move(valid), allocator);
        }
    }

    template<class JsonValueType>
    static void from_json(const JsonValueType &jsonValue,
                          Type &t) {
        if (!jsonValue.IsObject()) [[unlikely]] {
            throw exceptions::JsonSerializationTypeException("object", getJsonTypeStr(jsonValue.GetType()));
        }
        t.release();
        Codec<decltype(t.name)>::template from_json_member<JsonValueType>(jsonValue, details::JsonKey<CHelper::Property, typename JsonValueType::Ch>::name_(), t.name);
        t.type = Codec<decltype(t.defaultValue)>::template from_json_member<JsonValueType>(jsonValue, details::JsonKey<CHelper::Property, typename JsonValueType::Ch>::defaultValue_(), t.defaultValue);
        const typename JsonValueType::ConstMemberIterator &it = jsonValue.FindMember(details::JsonKey<CHelper::Property, typename JsonValueType::Ch>::valid_());
        if (it == jsonValue.MemberEnd()) [[likely]] {
            t.valid = std::nullopt;
        } else {
            t.valid = std::make_optional<std::vector<CHelper::PropertyValue>>();
            if (!it->value.IsArray()) [[unlikely]] {
                throw exceptions::JsonSerializationTypeException("array", getJsonTypeStr(jsonValue.GetType()));
            }
            t.valid.value().reserve(it->value.GetArray().Size());
            for (const auto &item: it->value.GetArray()) {
                CHelper::PropertyValue propertyValue;
                CHelper::PropertyType::PropertyType type = Codec<decltype(propertyValue)>::template from_json<typename JsonValueType::ValueType>(item, propertyValue);
                if (t.type != type) [[unlikely]] {
                    if (type == CHelper::PropertyType::PropertyType::STRING) {
                        delete propertyValue.string;
                    }
                    throw std::runtime_error("error block state property type");
                }
                t.valid.value().push_back(propertyValue);
            }
        }
    }

    template<bool isNeedConvert>
    static void to_binary(std::ostream &ostream,
                          const Type &t) {
        Codec<decltype(t.name)>::template to_binary<isNeedConvert>(ostream, t.name);
#ifdef CHelperDebug
        if (t.type != CHelper::PropertyType::BOOLEAN && t.type != CHelper::PropertyType::STRING && t.type != CHelper::PropertyType::INTEGER) [[unlikely]] {
            throw std::runtime_error("error block state property type");
        }
#endif
        Codec<decltype(t.type)>::template to_binary<isNeedConvert>(ostream, t.type);
        Codec<decltype(t.defaultValue)>::template to_binary<isNeedConvert>(ostream, t.defaultValue, t.type);
        Codec<bool>::template to_binary<isNeedConvert>(ostream, t.valid.has_value());
        if (t.valid.has_value()) [[unlikely]] {
            Codec<uint32_t>::template to_binary<isNeedConvert>(ostream, static_cast<uint32_t>(t.valid.value().size()));
            for (const auto &item: t.valid.value()) {
                Codec<CHelper::PropertyValue>::template to_binary<isNeedConvert>(ostream, item, t.type);
            }
        }
    }

    template<bool isNeedConvert>
    static void from_binary(std::istream &istream,
                            Type &t) {
        t.release();
        Codec<decltype(t.name)>::template from_binary<isNeedConvert>(istream, t.name);
        Codec<decltype(t.type)>::template from_binary<isNeedConvert>(istream, t.type);
#ifdef CHelperDebug
        if (t.type != CHelper::PropertyType::BOOLEAN && t.type != CHelper::PropertyType::STRING && t.type != CHelper::PropertyType::INTEGER) [[unlikely]] {
            throw std::runtime_error("error block state property type");
        }
#endif
        Codec<decltype(t.defaultValue)>::template from_binary<isNeedConvert>(istream, t.defaultValue, t.type);
        bool validHasValue;
        Codec<decltype(validHasValue)>::template from_binary<isNeedConvert>(istream, validHasValue);
        if (validHasValue) [[unlikely]] {
            t.valid = std::make_optional<std::vector<CHelper::PropertyValue>>();
            uint32_t size;
            Codec<uint32_t>::template from_binary<isNeedConvert>(istream, size);
            t.valid.value().reserve(static_cast<size_t>(size));
            for (size_t i = 0; i < size; ++i) {
                CHelper::PropertyValue propertyValue;
                Codec<decltype(propertyValue)>::template from_binary<isNeedConvert>(istream, propertyValue, t.type);
                t.valid.value().push_back(propertyValue);
            }
        } else {
            t.valid = std::nullopt;
        }
    }
};

CODEC_REGISTER_JSON_KEY(CHelper::BlockPropertyDescription, propertyName, description, values, valueName);

template<>
struct serialization::Codec<CHelper::BlockPropertyDescription> : BaseCodec<CHelper::BlockPropertyDescription> {

    using Type = CHelper::BlockPropertyDescription;

    constexpr static bool enable = true;

    template<class JsonValueType>
    static void to_json(typename JsonValueType::AllocatorType &allocator,
                        JsonValueType &jsonValue,
                        const Type &t) {
        jsonValue.SetObject();
        Codec<decltype(t.propertyName)>::template to_json_member<JsonValueType>(allocator, jsonValue, details::JsonKey<CHelper::BlockPropertyDescription, typename JsonValueType::Ch>::propertyName_(), t.propertyName);
        Codec<decltype(t.description)>::template to_json_member<JsonValueType>(allocator, jsonValue, details::JsonKey<CHelper::BlockPropertyDescription, typename JsonValueType::Ch>::description_(), t.description);
        rapidjson::GenericValue<typename JsonValueType::EncodingType> values;
        values.SetArray();
        values.Reserve(static_cast<rapidjson::SizeType>(t.values.size()), allocator);
        for (const auto &item: t.values) {
            rapidjson::GenericValue<typename JsonValueType::EncodingType> perValue;
            perValue.SetObject();
            Codec<decltype(item.valueName)>::template to_json_member<JsonValueType>(allocator, perValue, details::JsonKey<CHelper::BlockPropertyDescription, typename JsonValueType::Ch>::valueName_(), item.valueName, t.type);
            Codec<decltype(item.description)>::template to_json_member<JsonValueType>(allocator, perValue, details::JsonKey<CHelper::BlockPropertyDescription, typename JsonValueType::Ch>::description_(), item.description);
            values.PushBack(std::move(perValue), allocator);
        }
        jsonValue.AddMember(JsonValueType(details::JsonKey<CHelper::BlockPropertyDescription, typename JsonValueType::Ch>::values_(), allocator), std::move(values), allocator);
    }

    template<class JsonValueType>
    static void from_json(const JsonValueType &jsonValue,
                          Type &t) {
        if (!jsonValue.IsObject()) [[unlikely]] {
            throw exceptions::JsonSerializationTypeException("object", getJsonTypeStr(jsonValue.GetType()));
        }
        t.release();
        Codec<decltype(t.propertyName)>::template from_json_member<JsonValueType>(jsonValue, details::JsonKey<CHelper::BlockPropertyDescription, typename JsonValueType::Ch>::propertyName_(), t.propertyName);
        Codec<decltype(t.description)>::template from_json_member<JsonValueType>(jsonValue, details::JsonKey<CHelper::BlockPropertyDescription, typename JsonValueType::Ch>::description_(), t.description);
        bool hasPropertyType = false;
        for (const auto &item: serialization::find_array_member_or_throw(jsonValue, details::JsonKey<CHelper::BlockPropertyDescription, typename JsonValueType::Ch>::values_())) {
            CHelper::BlockPropertyValueDescription blockPropertyValueDescription;
            CHelper::PropertyType::PropertyType type = Codec<decltype(blockPropertyValueDescription.valueName)>::template from_json_member<typename JsonValueType::ValueType>(item, details::JsonKey<CHelper::BlockPropertyDescription, typename JsonValueType::Ch>::valueName_(), blockPropertyValueDescription.valueName);
            if (hasPropertyType) [[unlikely]] {
                if (t.type != type) [[likely]] {
                    if (type == CHelper::PropertyType::STRING) {
                        delete blockPropertyValueDescription.valueName.string;
                    }
                    throw std::runtime_error("error block state property type");
                }
            } else {
                hasPropertyType = true;
                t.type = type;
            }
            Codec<decltype(blockPropertyValueDescription.description)>::template from_json_member<typename JsonValueType::ValueType>(item, details::JsonKey<CHelper::BlockPropertyDescription, typename JsonValueType::Ch>::description_(), blockPropertyValueDescription.description);
            t.values.push_back(std::move(blockPropertyValueDescription));
        }
    }

    template<bool isNeedConvert>
    static void to_binary(std::ostream &ostream,
                          const Type &t) {
        Codec<decltype(t.propertyName)>::template to_binary<isNeedConvert>(ostream, t.propertyName);
        Codec<decltype(t.description)>::template to_binary<isNeedConvert>(ostream, t.description);
        Codec<decltype(t.type)>::template to_binary<isNeedConvert>(ostream, t.type);
        Codec<uint32_t>::template to_binary<isNeedConvert>(ostream, static_cast<uint32_t>(t.values.size()));
        for (const auto &item: t.values) {
            Codec<decltype(item.valueName)>::template to_binary<isNeedConvert>(ostream, item.valueName, t.type);
            Codec<decltype(item.description)>::template to_binary<isNeedConvert>(ostream, item.description);
        }
    }

    template<bool isNeedConvert>
    static void from_binary(std::istream &istream,
                            Type &t) {
        t.release();
        Codec<decltype(t.propertyName)>::template from_binary<isNeedConvert>(istream, t.propertyName);
        Codec<decltype(t.description)>::template from_binary<isNeedConvert>(istream, t.description);
        Codec<decltype(t.type)>::template from_binary<isNeedConvert>(istream, t.type);
        uint32_t size;
        Codec<decltype(size)>::template from_binary<isNeedConvert>(istream, size);
        t.values.reserve(static_cast<size_t>(size));
        for (size_t i = 0; i < size; ++i) {
            CHelper::BlockPropertyValueDescription blockPropertyValueDescription;
            Codec<decltype(blockPropertyValueDescription.valueName)>::template from_binary<isNeedConvert>(istream, blockPropertyValueDescription.valueName, t.type);
            Codec<decltype(blockPropertyValueDescription.description)>::template from_binary<isNeedConvert>(istream, blockPropertyValueDescription.description);
            t.values.push_back(std::move(blockPropertyValueDescription));
        }
    }
};

CODEC(CHelper::PerBlockPropertyDescription, blocks, properties)

CODEC(CHelper::BlockPropertyDescriptions, common, block)

CODEC_WITH_PARENT(CHelper::BlockId, CHelper::NamespaceId, properties)

CODEC(CHelper::BlockIds, blockStateValues, blockPropertyDescriptions)

#endif//CHELPER_BLOCKID_H
