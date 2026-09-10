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

namespace CHelper {

    // ================= 序列化辅助（JSON / MessagePack 通用） =================

    // 逐成员遍历 JSON 对象 / msgpack map（f 以 (key, ctx, it, end) 回调处理每个值）
    template<std::uint32_t Fmt, auto Opts, class F>
    void forEachObjectMember(glz::is_context auto &&ctx, auto &&it, auto &&end, F &&f) {
        if constexpr (Fmt == glz::JSON) {
            glz::skip_ws<Opts>(ctx, it, end);
            if (*it != '{') [[unlikely]] {
                ctx.error = glz::error_code::expected_brace;
                return;
            }
            ++it;
            glz::skip_ws<Opts>(ctx, it, end);
            if (*it == '}') {
                ++it;
                return;
            }
            while (true) {
                glz::skip_ws<Opts>(ctx, it, end);
                std::string key;
                glz::parse<glz::JSON>::op<Opts>(key, ctx, it, end);
                if (bool(ctx.error)) return;
                glz::skip_ws<Opts>(ctx, it, end);
                ++it;// ':'
                glz::skip_ws<Opts>(ctx, it, end);
                f(key, ctx, it, end);
                if (bool(ctx.error)) return;
                glz::skip_ws<Opts>(ctx, it, end);
                if (*it == ',') {
                    ++it;
                    continue;
                }
                if (*it == '}') {
                    ++it;
                    return;
                }
                ctx.error = glz::error_code::syntax_error;
                return;
            }
        } else {
            // MSGPACK
            if (it >= end) [[unlikely]] {
                ctx.error = glz::error_code::unexpected_end;
                return;
            }
            const uint8_t tag = static_cast<uint8_t>(*it++);
            uint32_t size = 0;
            if (tag >= 0x80 && tag <= 0x8f) {
                size = tag & 0x0f;
            } else if (tag == 0xde) {
                size = (static_cast<uint32_t>(static_cast<uint8_t>(*it++)) << 8) | static_cast<uint8_t>(*it++);
            } else if (tag == 0xdf) {
                size = (static_cast<uint32_t>(static_cast<uint8_t>(*it++)) << 24) |
                       (static_cast<uint32_t>(static_cast<uint8_t>(*it++)) << 16) |
                       (static_cast<uint32_t>(static_cast<uint8_t>(*it++)) << 8) | static_cast<uint8_t>(*it++);
            } else [[unlikely]] {
                ctx.error = glz::error_code::syntax_error;
                return;
            }
            for (uint32_t i = 0; i < size; ++i) {
                std::string key;
                glz::parse<glz::MSGPACK>::op<Opts>(key, ctx, it, end);
                if (bool(ctx.error)) return;
                f(key, ctx, it, end);
                if (bool(ctx.error)) return;
            }
        }
    }

    // 逐元素遍历 JSON 数组 / msgpack 数组
    template<std::uint32_t Fmt, auto Opts, class F>
    void forEachArrayElement(glz::is_context auto &&ctx, auto &&it, auto &&end, F &&f) {
        if constexpr (Fmt == glz::JSON) {
            glz::skip_ws<Opts>(ctx, it, end);
            if (*it != '[') [[unlikely]] {
                ctx.error = glz::error_code::expected_bracket;
                return;
            }
            ++it;
            glz::skip_ws<Opts>(ctx, it, end);
            if (*it == ']') {
                ++it;
                return;
            }
            while (true) {
                f(ctx, it, end);
                if (bool(ctx.error)) return;
                glz::skip_ws<Opts>(ctx, it, end);
                if (*it == ',') {
                    ++it;
                    continue;
                }
                if (*it == ']') {
                    ++it;
                    return;
                }
                ctx.error = glz::error_code::syntax_error;
                return;
            }
        } else {
            // MSGPACK
            if (it >= end) [[unlikely]] {
                ctx.error = glz::error_code::unexpected_end;
                return;
            }
            const uint8_t tag = static_cast<uint8_t>(*it++);
            uint32_t size = 0;
            if (tag >= 0x90 && tag <= 0x9f) {
                size = tag & 0x0f;
            } else if (tag == 0xdc) {
                size = (static_cast<uint32_t>(static_cast<uint8_t>(*it++)) << 8) | static_cast<uint8_t>(*it++);
            } else if (tag == 0xdd) {
                size = (static_cast<uint32_t>(static_cast<uint8_t>(*it++)) << 24) |
                       (static_cast<uint32_t>(static_cast<uint8_t>(*it++)) << 16) |
                       (static_cast<uint32_t>(static_cast<uint8_t>(*it++)) << 8) | static_cast<uint8_t>(*it++);
            } else [[unlikely]] {
                ctx.error = glz::error_code::syntax_error;
                return;
            }
            for (uint32_t i = 0; i < size; ++i) {
                f(ctx, it, end);
                if (bool(ctx.error)) return;
            }
        }
    }

    // 写出 PropertyValue 的原始值（由 type 决定活跃成员）
    template<std::uint32_t Fmt, auto Opts>
    void writePropertyValue(const PropertyValue &v, const PropertyType::PropertyType type,
                            glz::is_context auto &&ctx, auto &&b, auto &&ix) {
        switch (type) {
            case PropertyType::STRING:
                glz::serialize<Fmt>::template op<Opts>(*v.string, ctx, b, ix);
                break;
            case PropertyType::BOOLEAN:
                glz::serialize<Fmt>::template op<Opts>(v.boolean, ctx, b, ix);
                break;
            case PropertyType::INTEGER:
                glz::serialize<Fmt>::template op<Opts>(v.integer, ctx, b, ix);
                break;
            default:
                CHELPER_UNREACHABLE();
        }
    }

    inline void releasePropertyValue(const PropertyValue &v, const PropertyType::PropertyType type) {
        if (type == PropertyType::STRING) {
            delete v.string;
        }
    }

    // 判断当前值是否为 null（msgpack 的 obj 写出会把 nullopt optional 写成 nil）
    template<std::uint32_t Fmt, auto Opts>
    bool valueIsNull(glz::is_context auto &&ctx, auto &&it, auto &&end) {
        if constexpr (Fmt == glz::JSON) {
            glz::skip_ws<Opts>(ctx, it, end);
            return *it == 'n';
        } else {
            return static_cast<uint8_t>(*it) == 0xc0;
        }
    }

    // 消费 null 值
    template<std::uint32_t Fmt, auto Opts>
    void skipNull(glz::is_context auto &&ctx, auto &&it, auto &&end) {
        if constexpr (Fmt == glz::JSON) {
            glz::skip_ws<Opts>(ctx, it, end);
            it += 4;// "null" 长度固定为 4
        } else {
            ++it;
        }
    }

    // 读取 PropertyValue：根据值本身的类型判定
    // JSON：窥视首字符
    template<std::uint32_t Fmt, auto Opts>
    void readPropertyValue(PropertyValue &v, PropertyType::PropertyType &type,
                           glz::is_context auto &&ctx, auto &&it, auto &&end) {
        if constexpr (Fmt == glz::JSON) {
            glz::skip_ws<Opts>(ctx, it, end);
            if (*it == '"') [[likely]] {
                type = PropertyType::STRING;
                v.string = new std::u16string();
                glz::parse<glz::JSON>::op<Opts>(*v.string, ctx, it, end);
            } else if (*it == 't' || *it == 'f') [[likely]] {
                type = PropertyType::BOOLEAN;
                glz::parse<glz::JSON>::op<Opts>(v.boolean, ctx, it, end);
            } else {
                type = PropertyType::INTEGER;
                glz::parse<glz::JSON>::op<Opts>(v.integer, ctx, it, end);
            }
        }
    }

    // 二进制格式：类型已由 Property::type 确定，按类型读取对应成员
    template<auto Opts>
    void readBinaryPropertyValue(PropertyValue &v, const PropertyType::PropertyType type,
                                 glz::is_context auto &&ctx, auto &&it, auto &&end) {
        switch (type) {
            case PropertyType::STRING:
                v.string = new std::u16string();
                glz::parse<CHelper::BinaryFormat>::template op<Opts>(*v.string, ctx, it, end);
                break;
            case PropertyType::BOOLEAN:
                glz::parse<CHelper::BinaryFormat>::template op<Opts>(v.boolean, ctx, it, end);
                break;
            case PropertyType::INTEGER:
                glz::parse<CHelper::BinaryFormat>::template op<Opts>(v.integer, ctx, it, end);
                break;
            default:
                CHELPER_UNREACHABLE();
        }
    }

    // MSGPACK：按分发器已消费的 tag 判定
    template<auto Opts>
    void readPropertyValue(PropertyValue &v, PropertyType::PropertyType &type, const uint8_t tag,
                           glz::is_context auto &&ctx, auto &&it, auto &&end) {
        if ((tag >= 0xa0 && tag <= 0xbf) || tag == 0xd9 || tag == 0xda || tag == 0xdb) [[likely]] {
            type = PropertyType::STRING;
            v.string = new std::u16string();
            std::string utf8;
            glz::from<glz::MSGPACK, std::string>::op<Opts>(utf8, tag, ctx, it, end);
            *v.string = utf8::utf8to16(utf8);
        } else if (tag == 0xc2 || tag == 0xc3) [[likely]] {
            type = PropertyType::BOOLEAN;
            v.boolean = tag == 0xc3;
        } else {
            type = PropertyType::INTEGER;
            glz::from<glz::MSGPACK, int32_t>::op<Opts>(v.integer, tag, ctx, it, end);
        }
    }
}// namespace CHelper


namespace CHelper {

    // 携带类型的 PropertyValue 写出视图
    struct PropertyValueWriter {
        const PropertyValue *value;
        PropertyType::PropertyType type;
    };

    // 写出 Property（键名与旧版一致：name / defaultValue / valid）
    template<std::uint32_t Fmt, auto Opts>
    void writeProperty(const Property &t, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
        std::optional<std::vector<PropertyValueWriter>> valid;
        if (t.valid.has_value()) {
            valid.emplace();
            valid->reserve(t.valid.value().size());
            for (const auto &item: t.valid.value()) {
                valid->push_back(PropertyValueWriter{&item, t.type});
            }
        }
        auto value = glz::obj{"name", t.name, "defaultValue", PropertyValueWriter{&t.defaultValue, t.type}, "valid",
                              std::move(valid)};
        glz::serialize<Fmt>::template op<Opts>(value, ctx, b, ix);
    }

    // 读取 Property：defaultValue / valid 的类型由值本身判定
    template<std::uint32_t Fmt, auto Opts>
    void readProperty(Property &t, glz::is_context auto &&ctx, auto &&it, auto &&end) {
        t.release();
        bool hasDefaultValue = false;
        forEachObjectMember<Fmt, Opts>(ctx, it, end, [&](const std::string &key, auto &&ctx2, auto &&it2, auto &&end2) {
            if (key == "name") [[likely]] {
                glz::parse<Fmt>::template op<Opts>(t.name, ctx2, it2, end2);
            } else if (key == "defaultValue") [[likely]] {
                if constexpr (Fmt == glz::JSON) {
                    readPropertyValue<glz::JSON, Opts>(t.defaultValue, t.type, ctx2, it2, end2);
                } else {
                    const uint8_t tag = static_cast<uint8_t>(*it2++);
                    readPropertyValue<Opts>(t.defaultValue, t.type, tag, ctx2, it2, end2);
                }
                hasDefaultValue = true;
            } else if (key == "valid") {
                if (valueIsNull<Fmt, Opts>(ctx2, it2, end2)) {
                    skipNull<Fmt, Opts>(ctx2, it2, end2);
                    t.valid = std::nullopt;
                    return;
                }
                t.valid = std::make_optional<std::vector<PropertyValue>>();
                forEachArrayElement<Fmt, Opts>(ctx2, it2, end2, [&](auto &&ctx3, auto &&it3, auto &&end3) {
                    PropertyValue propertyValue;
                    PropertyType::PropertyType type = t.type;
                    if constexpr (Fmt == glz::JSON) {
                        readPropertyValue<glz::JSON, Opts>(propertyValue, type, ctx3, it3, end3);
                    } else {
                        const uint8_t tag = static_cast<uint8_t>(*it3++);
                        readPropertyValue<Opts>(propertyValue, type, tag, ctx3, it3, end3);
                    }
                    if (t.type != type) [[unlikely]] {
                        releasePropertyValue(propertyValue, type);
                        throw std::runtime_error("error block state property type");
                    }
                    t.valid.value().push_back(propertyValue);
                });
            } else {
                glz::skip_value<Fmt>::template op<Opts>(ctx2, it2, end2);
            }
        });
        if (bool(ctx.error)) return;
        if (!hasDefaultValue) [[unlikely]] {
            throw std::runtime_error("missing defaultValue in block property");
        }
    }

    // 二进制格式（非自描述）：name, type, defaultValue, [valid: bool, uint32, values...]
    template<auto Opts>
    void writeBinaryProperty(const Property &t, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
        glz::serialize<CHelper::BinaryFormat>::template op<Opts>(t.name, ctx, b, ix);
        glz::serialize<CHelper::BinaryFormat>::template op<Opts>(t.type, ctx, b, ix);
        writePropertyValue<CHelper::BinaryFormat, Opts>(t.defaultValue, t.type, ctx, b, ix);
        const bool hasValid = t.valid.has_value();
        glz::serialize<CHelper::BinaryFormat>::template op<Opts>(hasValid, ctx, b, ix);
        if (hasValid) {
            glz::serialize<CHelper::BinaryFormat>::template op<Opts>(
                    static_cast<std::uint32_t>(t.valid.value().size()), ctx, b, ix);
            for (const auto &item: t.valid.value()) {
                writePropertyValue<CHelper::BinaryFormat, Opts>(item, t.type, ctx, b, ix);
            }
        }
    }

    template<auto Opts>
    void readBinaryProperty(Property &t, glz::is_context auto &&ctx, auto &&it, auto &&end) {
        t.release();
        glz::parse<CHelper::BinaryFormat>::template op<Opts>(t.name, ctx, it, end);
        glz::parse<CHelper::BinaryFormat>::template op<Opts>(t.type, ctx, it, end);
        readBinaryPropertyValue<Opts>(t.defaultValue, t.type, ctx, it, end);
        bool hasValid = false;
        glz::parse<CHelper::BinaryFormat>::template op<Opts>(hasValid, ctx, it, end);
        if (hasValid) {
            std::uint32_t size = 0;
            glz::parse<CHelper::BinaryFormat>::template op<Opts>(size, ctx, it, end);
            t.valid = std::make_optional<std::vector<PropertyValue>>();
            t.valid.value().reserve(size);
            for (std::uint32_t i = 0; i < size; ++i) {
                PropertyValue propertyValue;
                readBinaryPropertyValue<Opts>(propertyValue, t.type, ctx, it, end);
                t.valid.value().push_back(propertyValue);
            }
        } else {
            t.valid = std::nullopt;
        }
    }
}// namespace CHelper

namespace glz {
    template<>
    struct to<JSON, CHelper::PropertyValueWriter> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
            CHelper::writePropertyValue<JSON, Opts>(*value.value, value.type, ctx, b, ix);
        }
    };

    template<>
    struct to<MSGPACK, CHelper::PropertyValueWriter> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
            CHelper::writePropertyValue<MSGPACK, Opts>(*value.value, value.type, ctx, b, ix);
        }
    };

    template<>
    struct to<JSON, CHelper::Property> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
            CHelper::writeProperty<JSON, Opts>(value, ctx, b, ix);
        }
    };

    template<>
    struct to<MSGPACK, CHelper::Property> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
            CHelper::writeProperty<MSGPACK, Opts>(value, ctx, b, ix);
        }
    };

    template<>
    struct from<JSON, CHelper::Property> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&it, auto &&end) {
            CHelper::readProperty<JSON, Opts>(value, ctx, it, end);
        }
    };

    template<>
    struct from<MSGPACK, CHelper::Property> {
        template<auto Opts>
        static void op(auto &&value, uint8_t, glz::is_context auto &&ctx, auto &&it, auto &&end) {
            // tag 已被分发器消费，回退后由 readProperty 解析 map 头
            --it;
            CHelper::readProperty<MSGPACK, Opts>(value, ctx, it, end);
        }
    };
}// namespace glz

namespace CHelper {

    // 携带类型的 BlockPropertyValueDescription 写出视图
    struct BlockPropertyValueDescriptionWriter {
        const BlockPropertyValueDescription *value;
        PropertyType::PropertyType type;
    };

    // 写出 BlockPropertyDescription（键名与旧版一致：propertyName / description / values{valueName, description}）
    template<std::uint32_t Fmt, auto Opts>
    void writeBlockPropertyDescription(const BlockPropertyDescription &t, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
        std::vector<BlockPropertyValueDescriptionWriter> values;
        values.reserve(t.values.size());
        for (const auto &item: t.values) {
            values.push_back(BlockPropertyValueDescriptionWriter{&item, t.type});
        }
        auto value = glz::obj{"propertyName", t.propertyName, "description", t.description, "values", std::move(values)};
        glz::serialize<Fmt>::template op<Opts>(value, ctx, b, ix);
    }

    // 读取 BlockPropertyDescription：type 由第一个 valueName 的类型判定
    template<std::uint32_t Fmt, auto Opts>
    void readBlockPropertyDescription(BlockPropertyDescription &t, glz::is_context auto &&ctx, auto &&it, auto &&end) {
        t.release();
        bool hasPropertyType = false;
        forEachObjectMember<Fmt, Opts>(ctx, it, end, [&](const std::string &key, auto &&ctx2, auto &&it2, auto &&end2) {
            if (key == "propertyName") [[likely]] {
                glz::parse<Fmt>::template op<Opts>(t.propertyName, ctx2, it2, end2);
            } else if (key == "description") {
                if (valueIsNull<Fmt, Opts>(ctx2, it2, end2)) {
                    skipNull<Fmt, Opts>(ctx2, it2, end2);
                    t.description = std::nullopt;
                } else {
                    t.description.emplace();
                    glz::parse<Fmt>::template op<Opts>(t.description.value(), ctx2, it2, end2);
                }
            } else if (key == "values") [[likely]] {
                forEachArrayElement<Fmt, Opts>(ctx2, it2, end2, [&](auto &&ctx3, auto &&it3, auto &&end3) {
                    BlockPropertyValueDescription blockPropertyValueDescription;
                    PropertyType::PropertyType type = t.type;
                    bool hasValueName = false;
                    forEachObjectMember<Fmt, Opts>(ctx3, it3, end3,
                                                   [&](const std::string &key2, auto &&ctx4, auto &&it4, auto &&end4) {
                                                       if (key2 == "valueName") [[likely]] {
                                                           if constexpr (Fmt == glz::JSON) {
                                                               readPropertyValue<glz::JSON, Opts>(
                                                                       blockPropertyValueDescription.valueName, type, ctx4,
                                                                       it4, end4);
                                                           } else {
                                                               const uint8_t tag = static_cast<uint8_t>(*it4++);
                                                               readPropertyValue<Opts>(
                                                                       blockPropertyValueDescription.valueName, type, tag,
                                                                       ctx4, it4, end4);
                                                           }
                                                           hasValueName = true;
                                                       } else if (key2 == "description") {
                                                           if (valueIsNull<Fmt, Opts>(ctx4, it4, end4)) {
                                                               skipNull<Fmt, Opts>(ctx4, it4, end4);
                                                               blockPropertyValueDescription.description = std::nullopt;
                                                           } else {
                                                               blockPropertyValueDescription.description.emplace();
                                                               glz::parse<Fmt>::template op<Opts>(
                                                                       blockPropertyValueDescription.description.value(),
                                                                       ctx4, it4, end4);
                                                           }
                                                       } else {
                                                           glz::skip_value<Fmt>::template op<Opts>(ctx4, it4, end4);
                                                       }
                                                   });
                    if (bool(ctx3.error)) return;
                    if (!hasValueName) [[unlikely]] {
                        throw std::runtime_error("missing valueName in block property value");
                    }
                    if (hasPropertyType) [[unlikely]] {
                        if (t.type != type) [[likely]] {
                            releasePropertyValue(blockPropertyValueDescription.valueName, type);
                            throw std::runtime_error("error block state property type");
                        }
                    } else {
                        hasPropertyType = true;
                        t.type = type;
                    }
                    t.values.push_back(std::move(blockPropertyValueDescription));
                });
            } else {
                glz::skip_value<Fmt>::template op<Opts>(ctx2, it2, end2);
            }
        });
    }

    // 二进制格式（非自描述）：propertyName, description, type, values(uint32 + {valueName, description}...)
    template<auto Opts>
    void writeBinaryBlockPropertyDescription(const BlockPropertyDescription &t, glz::is_context auto &&ctx, auto &&b,
                                             auto &&ix) {
        glz::serialize<CHelper::BinaryFormat>::template op<Opts>(t.propertyName, ctx, b, ix);
        glz::serialize<CHelper::BinaryFormat>::template op<Opts>(t.description, ctx, b, ix);
        glz::serialize<CHelper::BinaryFormat>::template op<Opts>(t.type, ctx, b, ix);
        glz::serialize<CHelper::BinaryFormat>::template op<Opts>(static_cast<std::uint32_t>(t.values.size()), ctx, b, ix);
        for (const auto &item: t.values) {
            writePropertyValue<CHelper::BinaryFormat, Opts>(item.valueName, t.type, ctx, b, ix);
            glz::serialize<CHelper::BinaryFormat>::template op<Opts>(item.description, ctx, b, ix);
        }
    }

    template<auto Opts>
    void readBinaryBlockPropertyDescription(BlockPropertyDescription &t, glz::is_context auto &&ctx, auto &&it,
                                            auto &&end) {
        t.release();
        glz::parse<CHelper::BinaryFormat>::template op<Opts>(t.propertyName, ctx, it, end);
        glz::parse<CHelper::BinaryFormat>::template op<Opts>(t.description, ctx, it, end);
        glz::parse<CHelper::BinaryFormat>::template op<Opts>(t.type, ctx, it, end);
        std::uint32_t size = 0;
        glz::parse<CHelper::BinaryFormat>::template op<Opts>(size, ctx, it, end);
        t.values.reserve(size);
        for (std::uint32_t i = 0; i < size; ++i) {
            BlockPropertyValueDescription blockPropertyValueDescription;
            readBinaryPropertyValue<Opts>(blockPropertyValueDescription.valueName, t.type, ctx, it, end);
            glz::parse<CHelper::BinaryFormat>::template op<Opts>(blockPropertyValueDescription.description, ctx, it, end);
            t.values.push_back(std::move(blockPropertyValueDescription));
        }
    }
}// namespace CHelper

namespace glz {
    template<>
    struct to<JSON, CHelper::BlockPropertyValueDescriptionWriter> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
            auto inner = glz::obj{"valueName", CHelper::PropertyValueWriter{&value.value->valueName, value.type},
                                  "description", value.value->description};
            serialize<JSON>::op<Opts>(inner, ctx, b, ix);
        }
    };

    template<>
    struct to<MSGPACK, CHelper::BlockPropertyValueDescriptionWriter> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
            auto inner = glz::obj{"valueName", CHelper::PropertyValueWriter{&value.value->valueName, value.type},
                                  "description", value.value->description};
            serialize<MSGPACK>::op<Opts>(inner, ctx, b, ix);
        }
    };

    template<>
    struct to<JSON, CHelper::BlockPropertyDescription> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
            CHelper::writeBlockPropertyDescription<JSON, Opts>(value, ctx, b, ix);
        }
    };

    template<>
    struct to<MSGPACK, CHelper::BlockPropertyDescription> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
            CHelper::writeBlockPropertyDescription<MSGPACK, Opts>(value, ctx, b, ix);
        }
    };

    template<>
    struct from<JSON, CHelper::BlockPropertyDescription> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&it, auto &&end) {
            CHelper::readBlockPropertyDescription<JSON, Opts>(value, ctx, it, end);
        }
    };

    template<>
    struct from<MSGPACK, CHelper::BlockPropertyDescription> {
        template<auto Opts>
        static void op(auto &&value, uint8_t, glz::is_context auto &&ctx, auto &&it, auto &&end) {
            // tag 已被分发器消费，回退后由 readBlockPropertyDescription 解析 map 头
            --it;
            CHelper::readBlockPropertyDescription<MSGPACK, Opts>(value, ctx, it, end);
        }
    };

    template<>
    struct to<CHelper::BinaryFormat, CHelper::PropertyValueWriter> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
            CHelper::writePropertyValue<CHelper::BinaryFormat, Opts>(*value.value, value.type, ctx, b, ix);
        }
    };

    template<>
    struct to<CHelper::BinaryFormat, CHelper::Property> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
            CHelper::writeBinaryProperty<Opts>(value, ctx, b, ix);
        }
    };

    template<>
    struct from<CHelper::BinaryFormat, CHelper::Property> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&it, auto &&end) {
            CHelper::readBinaryProperty<Opts>(value, ctx, it, end);
        }
    };

    template<>
    struct to<CHelper::BinaryFormat, CHelper::BlockPropertyDescription> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
            CHelper::writeBinaryBlockPropertyDescription<Opts>(value, ctx, b, ix);
        }
    };

    template<>
    struct from<CHelper::BinaryFormat, CHelper::BlockPropertyDescription> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&it, auto &&end) {
            CHelper::readBinaryBlockPropertyDescription<Opts>(value, ctx, it, end);
        }
    };
}// namespace glz

template<>
struct glz::meta<CHelper::PerBlockPropertyDescription> {
    using T = CHelper::PerBlockPropertyDescription;
    static constexpr auto value = glz::object(&T::blocks, &T::properties);
};

template<>
struct glz::meta<CHelper::BlockPropertyDescriptions> {
    using T = CHelper::BlockPropertyDescriptions;
    static constexpr auto value = glz::object(&T::common, &T::block);
};

template<>
struct glz::meta<CHelper::BlockId> {
    using T = CHelper::BlockId;
    static constexpr auto value = glz::object(&T::name, &T::description, &T::idNamespace, &T::properties);
};

template<>
struct glz::meta<CHelper::BlockIds> {
    using T = CHelper::BlockIds;
    static constexpr auto value = glz::object(&T::blockStateValues, &T::blockPropertyDescriptions);
};

#endif//CHELPER_BLOCKID_H
