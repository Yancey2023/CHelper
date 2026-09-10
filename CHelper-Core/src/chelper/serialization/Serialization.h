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

#ifndef CHELPER_SERIALIZATION_H
#define CHELPER_SERIALIZATION_H

#include <chelper/node/CommandNode.h>
#include <chelper/node/NodeType.h>
#include <chelper/old2new/Old2New.h>
#include <chelper/resources/CPack.h>
#include <chelper/serialization/BinaryFormat.h>
#include <glaze/containers/ordered_small_map.hpp>

namespace CHelper {

    // 当前 CPack 加载阶段，用于限制哪些节点类型允许被反序列化
    extern Node::NodeCreateStage::NodeCreateStage currentCreateStage;
}// namespace CHelper

// ================= std::u16string 支持（JSON / MessagePack，UTF-8 转换） =================
namespace glz {
    template<>
    struct from<JSON, std::u16string> {
        template<auto Opts>
        static void op(std::u16string &value, glz::is_context auto &&ctx, auto &&it, auto &&end) {
            std::string utf8;
            parse<JSON>::op<Opts>(utf8, ctx, it, end);
            value = utf8::utf8to16(utf8);
        }
    };

    template<>
    struct to<JSON, std::u16string> {
        template<auto Opts>
        static void op(const std::u16string &value, glz::is_context auto &&ctx, auto &&b, auto &&ix) noexcept {
            serialize<JSON>::op<Opts>(utf8::utf16to8(value), ctx, b, ix);
        }
    };

    template<>
    struct from<MSGPACK, std::u16string> {
        template<auto Opts>
        static void op(std::u16string &value, uint8_t tag, glz::is_context auto &&ctx, auto &&it, auto &&end) {
            // tag 已由分发器消费，直接交给 std::string 读取
            std::string utf8;
            from<MSGPACK, std::string>::op<Opts>(utf8, tag, ctx, it, end);
            value = utf8::utf8to16(utf8);
        }
    };

    template<>
    struct to<MSGPACK, std::u16string> {
        template<auto Opts>
        static void op(const std::u16string &value, glz::is_context auto &&ctx, auto &&b, auto &&ix) noexcept {
            serialize<MSGPACK>::op<Opts>(utf8::utf16to8(value), ctx, b, ix);
        }
    };

    // 其余 glaze 自带格式的 u16string 支持（均以 UTF-8 字符串承载）
#define CHELPER_GLZ_U16STRING_FORMAT(Fmt)                                                                       \
    template<>                                                                                                  \
    struct from<Fmt, std::u16string> {                                                                          \
        template<auto Opts>                                                                                     \
        static void op(std::u16string &value, glz::is_context auto &&ctx, auto &&it, auto &&end) {              \
            std::string utf8;                                                                                   \
            parse<Fmt>::template op<Opts>(utf8, ctx, it, end);                                                  \
            value = utf8::utf8to16(utf8);                                                                       \
        }                                                                                                       \
    };                                                                                                          \
    template<>                                                                                                  \
    struct to<Fmt, std::u16string> {                                                                            \
        template<auto Opts>                                                                                     \
        static void op(const std::u16string &value, glz::is_context auto &&ctx, auto &&b, auto &&ix) noexcept { \
            serialize<Fmt>::template op<Opts>(utf8::utf16to8(value), ctx, b, ix);                               \
        }                                                                                                       \
    };

    CHELPER_GLZ_U16STRING_FORMAT(BEVE)
    CHELPER_GLZ_U16STRING_FORMAT(CBOR)
    template<>
    struct to<BSON, std::u16string> {
        template<auto Opts>
        static void op(const std::u16string &value, glz::is_context auto &&ctx, auto &&b, auto &&ix) noexcept {
            serialize<BSON>::template op<Opts>(utf8::utf16to8(value), ctx, b, ix);
        }
    };

    template<>
    struct from<BSON, std::u16string> {
        template<auto Opts>
        static void op(std::u16string &value, uint8_t tag, glz::is_context auto &&ctx, auto &&it, auto &&end) {
            std::string utf8;
            from<BSON, std::string>::template op<Opts>(utf8, tag, ctx, it, end);
            value = utf8::utf8to16(utf8);
        }
    };

#undef CHELPER_GLZ_U16STRING_FORMAT

    // 修复 glaze msgpack 读取器对 nullable 类型直接调用 emplace() 的问题
    // （v8.3.0 起，shared_ptr / unique_ptr 没有 emplace，见官方 msgpack_test 中被跳过的用例）
    template<class T>
    struct from<MSGPACK, std::shared_ptr<T>> {
        template<auto Opts>
        static void op(std::shared_ptr<T> &value, uint8_t tag, glz::is_context auto &&ctx, auto &&it, auto &&end) {
            if (tag == msgpack::nil) {
                value.reset();
                return;
            }
            if (!value) {
                value = std::make_shared<T>();
            }
            from<MSGPACK, T>::template op<Opts>(*value, tag, ctx, it, end);
        }
    };

    template<class T>
    struct from<MSGPACK, std::unique_ptr<T>> {
        template<auto Opts>
        static void op(std::unique_ptr<T> &value, uint8_t tag, glz::is_context auto &&ctx, auto &&it, auto &&end) {
            if (tag == msgpack::nil) {
                value.reset();
                return;
            }
            if (!value) {
                value = std::make_unique<T>();
            }
            from<MSGPACK, T>::template op<Opts>(*value, tag, ctx, it, end);
        }
    };
}// namespace glz

// ================= 节点序列化 =================

// 可序列化的节点类型列表（不含 WRAPPED / LF / PER_COMMAND / JSON_ELEMENT / JSON_ENTRY 等运行期类型）
#define CHELPER_SERIALIZABLE_NODE_TYPES                                                                                  \
    BLOCK, BOOLEAN, COMMAND, COMMAND_NAME, FLOAT, INTEGER, INTEGER_WITH_UNIT, ITEM, JSON, JSON_BOOLEAN, JSON_FLOAT,      \
            JSON_INTEGER, JSON_LIST, JSON_NULL, JSON_ENTRY, JSON_OBJECT, JSON_STRING, NAMESPACE_ID, NORMAL_ID, POSITION, \
            RANGE, RELATIVE_FLOAT, REPEAT, STRING, TARGET_SELECTOR, TEXT

// 各节点类型的特有字段（写出用；键名与成员名一致，和旧版 CODEC_REGISTER_JSON_KEY 相同）
#define CHELPER_NODE_FIELDS_BLOCK(n) , "nodeBlockType", n.nodeBlockType
#define CHELPER_NODE_FIELDS_BOOLEAN(n) , "descriptionTrue", n.descriptionTrue, "descriptionFalse", n.descriptionFalse
#define CHELPER_NODE_FIELDS_COMMAND(n)
#define CHELPER_NODE_FIELDS_COMMAND_NAME(n)
#define CHELPER_NODE_FIELDS_FLOAT(n) , "min", n.min, "max", n.max
#define CHELPER_NODE_FIELDS_INTEGER(n) , "min", n.min, "max", n.max
#define CHELPER_NODE_FIELDS_INTEGER_WITH_UNIT(n) , "units", n.units
#define CHELPER_NODE_FIELDS_ITEM(n) , "nodeItemType", n.nodeItemType
#define CHELPER_NODE_FIELDS_JSON(n) , "key", n.key
#define CHELPER_NODE_FIELDS_JSON_BOOLEAN(n) , "descriptionTrue", n.descriptionTrue, "descriptionFalse", n.descriptionFalse
#define CHELPER_NODE_FIELDS_JSON_FLOAT(n) , "min", n.min, "max", n.max
#define CHELPER_NODE_FIELDS_JSON_INTEGER(n) , "min", n.min, "max", n.max
#define CHELPER_NODE_FIELDS_JSON_LIST(n) , "data", n.data
#define CHELPER_NODE_FIELDS_JSON_NULL(n)
#define CHELPER_NODE_FIELDS_JSON_ENTRY(n) , "key", n.key, "value", n.value
#define CHELPER_NODE_FIELDS_JSON_OBJECT(n) , "data", n.data
#define CHELPER_NODE_FIELDS_JSON_STRING(n) , "data", n.data
#define CHELPER_NODE_FIELDS_NAMESPACE_ID(n) , "key", n.key, "ignoreError", n.ignoreError, "contents", n.contents
#define CHELPER_NODE_FIELDS_NORMAL_ID(n) , "key", n.key, "ignoreError", n.ignoreError, "contents", n.contents
#define CHELPER_NODE_FIELDS_POSITION(n)
#define CHELPER_NODE_FIELDS_RANGE(n)
#define CHELPER_NODE_FIELDS_RELATIVE_FLOAT(n) , "canUseCaretNotation", n.canUseCaretNotation
#define CHELPER_NODE_FIELDS_REPEAT(n) , "key", n.key
#define CHELPER_NODE_FIELDS_STRING(n) , "canContainSpace", n.canContainSpace, "ignoreLater", n.ignoreLater
#define CHELPER_NODE_FIELDS_TARGET_SELECTOR(n) \
    , "isMustPlayer", n.isMustPlayer, "isMustNPC", n.isMustNPC, "isOnlyOne", n.isOnlyOne, "isWildcard", n.isWildcard
#define CHELPER_NODE_FIELDS_TEXT(n) , "data", n.data


namespace CHelper::Node {
    // glz::meta 定义：反序列化时按成员名读取（含 NodeSerializable 基类字段）
    template<class T>
    concept NodeSerializableType = std::derived_from<T, NodeSerializable>;
}// namespace CHelper::Node

#define CHELPER_GLZ_NODE_META(Type, ...)                                                                                  \
    template<>                                                                                                            \
    struct glz::meta<Type> {                                                                                              \
        using T = Type;                                                                                                   \
        static constexpr auto value = glz::object(&T::id, &T::brief, &T::description, &T::isMustAfterSpace, __VA_ARGS__); \
    };

#define CHELPER_GLZ_NODE_META_NONE(Type)                                                                     \
    template<>                                                                                               \
    struct glz::meta<Type> {                                                                                 \
        using T = Type;                                                                                      \
        static constexpr auto value = glz::object(&T::id, &T::brief, &T::description, &T::isMustAfterSpace); \
    };

#define CHELPER_GLZ_NODE_WRITE(NodeType, Id)                                                                                     \
    template<std::uint32_t Fmt, auto Opts, class Ctx, class B>                                                                   \
    inline void nodeWriteValue_##Id(const Node::NodeWithType &t, Ctx &ctx, B &b, size_t &ix) {                                   \
        static_assert(std::is_same_v<NodeType, Node::NodeTypeDetail<Node::NodeTypeId::Id>::Type>);                               \
        const auto &n = *static_cast<const NodeType *>(t.data);                                                                  \
        auto value = glz::obj{"type", Node::NodeTypeDetail<Node::NodeTypeId::Id>::name, "id", n.id, "brief", n.brief,            \
                              "description", n.description, "isMustAfterSpace", n.isMustAfterSpace CHELPER_NODE_FIELDS_##Id(n)}; \
        glz::serialize<Fmt>::template op<Opts>(value, ctx, b, ix);                                                               \
    }

// 节点类型元数据（读取用；glz::meta 特化须在全局作用域，使用全限定名）
CHELPER_GLZ_NODE_META(CHelper::Node::NodeBlock, &CHelper::Node::NodeBlock::nodeBlockType)
CHELPER_GLZ_NODE_META(CHelper::Node::NodeBoolean, &CHelper::Node::NodeBoolean::descriptionTrue, &CHelper::Node::NodeBoolean::descriptionFalse)
CHELPER_GLZ_NODE_META_NONE(CHelper::Node::NodeCommand)
CHELPER_GLZ_NODE_META_NONE(CHelper::Node::NodeCommandName)
CHELPER_GLZ_NODE_META(CHelper::Node::NodeFloat, &CHelper::Node::NodeFloat::min, &CHelper::Node::NodeFloat::max)
CHELPER_GLZ_NODE_META(CHelper::Node::NodeInteger, &CHelper::Node::NodeInteger::min, &CHelper::Node::NodeInteger::max)
CHELPER_GLZ_NODE_META(CHelper::Node::NodeIntegerWithUnit, &CHelper::Node::NodeIntegerWithUnit::units)
CHELPER_GLZ_NODE_META(CHelper::Node::NodeItem, &CHelper::Node::NodeItem::nodeItemType)
CHELPER_GLZ_NODE_META(CHelper::Node::NodeJson, &CHelper::Node::NodeJson::key)
CHELPER_GLZ_NODE_META(CHelper::Node::NodeJsonBoolean, &CHelper::Node::NodeJsonBoolean::descriptionTrue, &CHelper::Node::NodeJsonBoolean::descriptionFalse)
CHELPER_GLZ_NODE_META(CHelper::Node::NodeJsonFloat, &CHelper::Node::NodeJsonFloat::min, &CHelper::Node::NodeJsonFloat::max)
CHELPER_GLZ_NODE_META(CHelper::Node::NodeJsonInteger, &CHelper::Node::NodeJsonInteger::min, &CHelper::Node::NodeJsonInteger::max)
CHELPER_GLZ_NODE_META(CHelper::Node::NodeJsonList, &CHelper::Node::NodeJsonList::data)
CHELPER_GLZ_NODE_META_NONE(CHelper::Node::NodeJsonNull)
CHELPER_GLZ_NODE_META(CHelper::Node::NodeJsonEntry, &CHelper::Node::NodeJsonEntry::key, &CHelper::Node::NodeJsonEntry::value)
CHELPER_GLZ_NODE_META(CHelper::Node::NodeJsonObject, &CHelper::Node::NodeJsonObject::data)
CHELPER_GLZ_NODE_META(CHelper::Node::NodeJsonString, &CHelper::Node::NodeJsonString::data)
CHELPER_GLZ_NODE_META(CHelper::Node::NodeNamespaceId, &CHelper::Node::NodeNamespaceId::key, &CHelper::Node::NodeNamespaceId::ignoreError, &CHelper::Node::NodeNamespaceId::contents)
CHELPER_GLZ_NODE_META(CHelper::Node::NodeNormalId, &CHelper::Node::NodeNormalId::key, &CHelper::Node::NodeNormalId::ignoreError, &CHelper::Node::NodeNormalId::contents)
CHELPER_GLZ_NODE_META_NONE(CHelper::Node::NodePosition)
CHELPER_GLZ_NODE_META_NONE(CHelper::Node::NodeRange)
CHELPER_GLZ_NODE_META(CHelper::Node::NodeRelativeFloat, &CHelper::Node::NodeRelativeFloat::canUseCaretNotation)
CHELPER_GLZ_NODE_META(CHelper::Node::NodeRepeat, &CHelper::Node::NodeRepeat::key)
CHELPER_GLZ_NODE_META(CHelper::Node::NodeString, &CHelper::Node::NodeString::canContainSpace, &CHelper::Node::NodeString::ignoreLater)
CHELPER_GLZ_NODE_META(CHelper::Node::NodeTargetSelector, &CHelper::Node::NodeTargetSelector::isMustPlayer, &CHelper::Node::NodeTargetSelector::isMustNPC, &CHelper::Node::NodeTargetSelector::isOnlyOne, &CHelper::Node::NodeTargetSelector::isWildcard)
CHELPER_GLZ_NODE_META(CHelper::Node::NodeText, &CHelper::Node::NodeText::data)

namespace CHelper {
    // 节点类型写出函数（写出用，"type" 位于首位）
    CHELPER_GLZ_NODE_WRITE(Node::NodeBlock, BLOCK)
    CHELPER_GLZ_NODE_WRITE(Node::NodeBoolean, BOOLEAN)
    CHELPER_GLZ_NODE_WRITE(Node::NodeCommand, COMMAND)
    CHELPER_GLZ_NODE_WRITE(Node::NodeCommandName, COMMAND_NAME)
    CHELPER_GLZ_NODE_WRITE(Node::NodeFloat, FLOAT)
    CHELPER_GLZ_NODE_WRITE(Node::NodeInteger, INTEGER)
    CHELPER_GLZ_NODE_WRITE(Node::NodeIntegerWithUnit, INTEGER_WITH_UNIT)
    CHELPER_GLZ_NODE_WRITE(Node::NodeItem, ITEM)
    CHELPER_GLZ_NODE_WRITE(Node::NodeJson, JSON)
    CHELPER_GLZ_NODE_WRITE(Node::NodeJsonBoolean, JSON_BOOLEAN)
    CHELPER_GLZ_NODE_WRITE(Node::NodeJsonFloat, JSON_FLOAT)
    CHELPER_GLZ_NODE_WRITE(Node::NodeJsonInteger, JSON_INTEGER)
    CHELPER_GLZ_NODE_WRITE(Node::NodeJsonList, JSON_LIST)
    CHELPER_GLZ_NODE_WRITE(Node::NodeJsonNull, JSON_NULL)
    CHELPER_GLZ_NODE_WRITE(Node::NodeJsonEntry, JSON_ENTRY)
    CHELPER_GLZ_NODE_WRITE(Node::NodeJsonObject, JSON_OBJECT)
    CHELPER_GLZ_NODE_WRITE(Node::NodeJsonString, JSON_STRING)
    CHELPER_GLZ_NODE_WRITE(Node::NodeNamespaceId, NAMESPACE_ID)
    CHELPER_GLZ_NODE_WRITE(Node::NodeNormalId, NORMAL_ID)
    CHELPER_GLZ_NODE_WRITE(Node::NodePosition, POSITION)
    CHELPER_GLZ_NODE_WRITE(Node::NodeRange, RANGE)
    CHELPER_GLZ_NODE_WRITE(Node::NodeRelativeFloat, RELATIVE_FLOAT)
    CHELPER_GLZ_NODE_WRITE(Node::NodeRepeat, REPEAT)
    CHELPER_GLZ_NODE_WRITE(Node::NodeString, STRING)
    CHELPER_GLZ_NODE_WRITE(Node::NodeTargetSelector, TARGET_SELECTOR)
    CHELPER_GLZ_NODE_WRITE(Node::NodeText, TEXT)
}// namespace CHelper

namespace CHelper {

#define CHELPER_NODE_WRITE_CASE(v1)                    \
    case Node::NodeTypeId::v1:                         \
        nodeWriteValue_##v1<Fmt, Opts>(t, ctx, b, ix); \
        break;

    // 把节点对象（含 "type" 键）写入缓冲区
    template<std::uint32_t Fmt, auto Opts, class Ctx, class B>
    inline void writeNodeValue(const Node::NodeWithType &t, Ctx &ctx, B &b, size_t &ix) {
        switch (t.nodeTypeId) {
            CHELPER_PASTE(CHELPER_NODE_WRITE_CASE, CHELPER_SERIALIZABLE_NODE_TYPES)
            default:
                ctx.error = glz::error_code::no_matching_variant_type;
                break;
        }
    }


    // 二进制节点读写函数（按成员声明顺序紧凑读写，无键名）
    template<auto Opts, class Ctx, class B>
    inline void nodeWriteBinary_BLOCK(const Node::NodeWithType &t, Ctx &ctx, B &b, size_t &ix) {
        const auto &n = *static_cast<const Node::NodeBlock *>(t.data);
        auto write = [&](auto &&...args) {
            (glz::serialize<CHelper::BinaryFormat>::template op<Opts>(args, ctx, b, ix), ...);
        };
        write(n.id, n.brief, n.description, n.isMustAfterSpace, n.nodeBlockType);
    }
    template<auto Opts, class Ctx, class B>
    inline void nodeWriteBinary_BOOLEAN(const Node::NodeWithType &t, Ctx &ctx, B &b, size_t &ix) {
        const auto &n = *static_cast<const Node::NodeBoolean *>(t.data);
        auto write = [&](auto &&...args) {
            (glz::serialize<CHelper::BinaryFormat>::template op<Opts>(args, ctx, b, ix), ...);
        };
        write(n.id, n.brief, n.description, n.isMustAfterSpace, n.descriptionTrue, n.descriptionFalse);
    }
    template<auto Opts, class Ctx, class B>
    inline void nodeWriteBinary_COMMAND(const Node::NodeWithType &t, Ctx &ctx, B &b, size_t &ix) {
        const auto &n = *static_cast<const Node::NodeCommand *>(t.data);
        auto write = [&](auto &&...args) {
            (glz::serialize<CHelper::BinaryFormat>::template op<Opts>(args, ctx, b, ix), ...);
        };
        write(n.id, n.brief, n.description, n.isMustAfterSpace);
    }
    template<auto Opts, class Ctx, class B>
    inline void nodeWriteBinary_COMMAND_NAME(const Node::NodeWithType &t, Ctx &ctx, B &b, size_t &ix) {
        const auto &n = *static_cast<const Node::NodeCommandName *>(t.data);
        auto write = [&](auto &&...args) {
            (glz::serialize<CHelper::BinaryFormat>::template op<Opts>(args, ctx, b, ix), ...);
        };
        write(n.id, n.brief, n.description, n.isMustAfterSpace);
    }
    template<auto Opts, class Ctx, class B>
    inline void nodeWriteBinary_FLOAT(const Node::NodeWithType &t, Ctx &ctx, B &b, size_t &ix) {
        const auto &n = *static_cast<const Node::NodeFloat *>(t.data);
        auto write = [&](auto &&...args) {
            (glz::serialize<CHelper::BinaryFormat>::template op<Opts>(args, ctx, b, ix), ...);
        };
        write(n.id, n.brief, n.description, n.isMustAfterSpace, n.min, n.max);
    }
    template<auto Opts, class Ctx, class B>
    inline void nodeWriteBinary_INTEGER(const Node::NodeWithType &t, Ctx &ctx, B &b, size_t &ix) {
        const auto &n = *static_cast<const Node::NodeInteger *>(t.data);
        auto write = [&](auto &&...args) {
            (glz::serialize<CHelper::BinaryFormat>::template op<Opts>(args, ctx, b, ix), ...);
        };
        write(n.id, n.brief, n.description, n.isMustAfterSpace, n.min, n.max);
    }
    template<auto Opts, class Ctx, class B>
    inline void nodeWriteBinary_INTEGER_WITH_UNIT(const Node::NodeWithType &t, Ctx &ctx, B &b, size_t &ix) {
        const auto &n = *static_cast<const Node::NodeIntegerWithUnit *>(t.data);
        auto write = [&](auto &&...args) {
            (glz::serialize<CHelper::BinaryFormat>::template op<Opts>(args, ctx, b, ix), ...);
        };
        write(n.id, n.brief, n.description, n.isMustAfterSpace, n.units);
    }
    template<auto Opts, class Ctx, class B>
    inline void nodeWriteBinary_ITEM(const Node::NodeWithType &t, Ctx &ctx, B &b, size_t &ix) {
        const auto &n = *static_cast<const Node::NodeItem *>(t.data);
        auto write = [&](auto &&...args) {
            (glz::serialize<CHelper::BinaryFormat>::template op<Opts>(args, ctx, b, ix), ...);
        };
        write(n.id, n.brief, n.description, n.isMustAfterSpace, n.nodeItemType);
    }
    template<auto Opts, class Ctx, class B>
    inline void nodeWriteBinary_JSON(const Node::NodeWithType &t, Ctx &ctx, B &b, size_t &ix) {
        const auto &n = *static_cast<const Node::NodeJson *>(t.data);
        auto write = [&](auto &&...args) {
            (glz::serialize<CHelper::BinaryFormat>::template op<Opts>(args, ctx, b, ix), ...);
        };
        write(n.id, n.brief, n.description, n.isMustAfterSpace, n.key);
    }
    template<auto Opts, class Ctx, class B>
    inline void nodeWriteBinary_JSON_BOOLEAN(const Node::NodeWithType &t, Ctx &ctx, B &b, size_t &ix) {
        const auto &n = *static_cast<const Node::NodeJsonBoolean *>(t.data);
        auto write = [&](auto &&...args) {
            (glz::serialize<CHelper::BinaryFormat>::template op<Opts>(args, ctx, b, ix), ...);
        };
        write(n.id, n.brief, n.description, n.isMustAfterSpace, n.descriptionTrue, n.descriptionFalse);
    }
    template<auto Opts, class Ctx, class B>
    inline void nodeWriteBinary_JSON_FLOAT(const Node::NodeWithType &t, Ctx &ctx, B &b, size_t &ix) {
        const auto &n = *static_cast<const Node::NodeJsonFloat *>(t.data);
        auto write = [&](auto &&...args) {
            (glz::serialize<CHelper::BinaryFormat>::template op<Opts>(args, ctx, b, ix), ...);
        };
        write(n.id, n.brief, n.description, n.isMustAfterSpace, n.min, n.max);
    }
    template<auto Opts, class Ctx, class B>
    inline void nodeWriteBinary_JSON_INTEGER(const Node::NodeWithType &t, Ctx &ctx, B &b, size_t &ix) {
        const auto &n = *static_cast<const Node::NodeJsonInteger *>(t.data);
        auto write = [&](auto &&...args) {
            (glz::serialize<CHelper::BinaryFormat>::template op<Opts>(args, ctx, b, ix), ...);
        };
        write(n.id, n.brief, n.description, n.isMustAfterSpace, n.min, n.max);
    }
    template<auto Opts, class Ctx, class B>
    inline void nodeWriteBinary_JSON_LIST(const Node::NodeWithType &t, Ctx &ctx, B &b, size_t &ix) {
        const auto &n = *static_cast<const Node::NodeJsonList *>(t.data);
        auto write = [&](auto &&...args) {
            (glz::serialize<CHelper::BinaryFormat>::template op<Opts>(args, ctx, b, ix), ...);
        };
        write(n.id, n.brief, n.description, n.isMustAfterSpace, n.data);
    }
    template<auto Opts, class Ctx, class B>
    inline void nodeWriteBinary_JSON_NULL(const Node::NodeWithType &t, Ctx &ctx, B &b, size_t &ix) {
        const auto &n = *static_cast<const Node::NodeJsonNull *>(t.data);
        auto write = [&](auto &&...args) {
            (glz::serialize<CHelper::BinaryFormat>::template op<Opts>(args, ctx, b, ix), ...);
        };
        write(n.id, n.brief, n.description, n.isMustAfterSpace);
    }
    template<auto Opts, class Ctx, class B>
    inline void nodeWriteBinary_JSON_ENTRY(const Node::NodeWithType &t, Ctx &ctx, B &b, size_t &ix) {
        const auto &n = *static_cast<const Node::NodeJsonEntry *>(t.data);
        auto write = [&](auto &&...args) {
            (glz::serialize<CHelper::BinaryFormat>::template op<Opts>(args, ctx, b, ix), ...);
        };
        write(n.id, n.brief, n.description, n.isMustAfterSpace, n.key, n.value);
    }
    template<auto Opts, class Ctx, class B>
    inline void nodeWriteBinary_JSON_OBJECT(const Node::NodeWithType &t, Ctx &ctx, B &b, size_t &ix) {
        const auto &n = *static_cast<const Node::NodeJsonObject *>(t.data);
        auto write = [&](auto &&...args) {
            (glz::serialize<CHelper::BinaryFormat>::template op<Opts>(args, ctx, b, ix), ...);
        };
        write(n.id, n.brief, n.description, n.isMustAfterSpace, n.data);
    }
    template<auto Opts, class Ctx, class B>
    inline void nodeWriteBinary_JSON_STRING(const Node::NodeWithType &t, Ctx &ctx, B &b, size_t &ix) {
        const auto &n = *static_cast<const Node::NodeJsonString *>(t.data);
        auto write = [&](auto &&...args) {
            (glz::serialize<CHelper::BinaryFormat>::template op<Opts>(args, ctx, b, ix), ...);
        };
        write(n.id, n.brief, n.description, n.isMustAfterSpace, n.data);
    }
    template<auto Opts, class Ctx, class B>
    inline void nodeWriteBinary_NAMESPACE_ID(const Node::NodeWithType &t, Ctx &ctx, B &b, size_t &ix) {
        const auto &n = *static_cast<const Node::NodeNamespaceId *>(t.data);
        auto write = [&](auto &&...args) {
            (glz::serialize<CHelper::BinaryFormat>::template op<Opts>(args, ctx, b, ix), ...);
        };
        write(n.id, n.brief, n.description, n.isMustAfterSpace, n.key, n.ignoreError, n.contents);
    }
    template<auto Opts, class Ctx, class B>
    inline void nodeWriteBinary_NORMAL_ID(const Node::NodeWithType &t, Ctx &ctx, B &b, size_t &ix) {
        const auto &n = *static_cast<const Node::NodeNormalId *>(t.data);
        auto write = [&](auto &&...args) {
            (glz::serialize<CHelper::BinaryFormat>::template op<Opts>(args, ctx, b, ix), ...);
        };
        write(n.id, n.brief, n.description, n.isMustAfterSpace, n.key, n.ignoreError, n.contents);
    }
    template<auto Opts, class Ctx, class B>
    inline void nodeWriteBinary_POSITION(const Node::NodeWithType &t, Ctx &ctx, B &b, size_t &ix) {
        const auto &n = *static_cast<const Node::NodePosition *>(t.data);
        auto write = [&](auto &&...args) {
            (glz::serialize<CHelper::BinaryFormat>::template op<Opts>(args, ctx, b, ix), ...);
        };
        write(n.id, n.brief, n.description, n.isMustAfterSpace);
    }
    template<auto Opts, class Ctx, class B>
    inline void nodeWriteBinary_RANGE(const Node::NodeWithType &t, Ctx &ctx, B &b, size_t &ix) {
        const auto &n = *static_cast<const Node::NodeRange *>(t.data);
        auto write = [&](auto &&...args) {
            (glz::serialize<CHelper::BinaryFormat>::template op<Opts>(args, ctx, b, ix), ...);
        };
        write(n.id, n.brief, n.description, n.isMustAfterSpace);
    }
    template<auto Opts, class Ctx, class B>
    inline void nodeWriteBinary_RELATIVE_FLOAT(const Node::NodeWithType &t, Ctx &ctx, B &b, size_t &ix) {
        const auto &n = *static_cast<const Node::NodeRelativeFloat *>(t.data);
        auto write = [&](auto &&...args) {
            (glz::serialize<CHelper::BinaryFormat>::template op<Opts>(args, ctx, b, ix), ...);
        };
        write(n.id, n.brief, n.description, n.isMustAfterSpace, n.canUseCaretNotation);
    }
    template<auto Opts, class Ctx, class B>
    inline void nodeWriteBinary_REPEAT(const Node::NodeWithType &t, Ctx &ctx, B &b, size_t &ix) {
        const auto &n = *static_cast<const Node::NodeRepeat *>(t.data);
        auto write = [&](auto &&...args) {
            (glz::serialize<CHelper::BinaryFormat>::template op<Opts>(args, ctx, b, ix), ...);
        };
        write(n.id, n.brief, n.description, n.isMustAfterSpace, n.key);
    }
    template<auto Opts, class Ctx, class B>
    inline void nodeWriteBinary_STRING(const Node::NodeWithType &t, Ctx &ctx, B &b, size_t &ix) {
        const auto &n = *static_cast<const Node::NodeString *>(t.data);
        auto write = [&](auto &&...args) {
            (glz::serialize<CHelper::BinaryFormat>::template op<Opts>(args, ctx, b, ix), ...);
        };
        write(n.id, n.brief, n.description, n.isMustAfterSpace, n.canContainSpace, n.ignoreLater);
    }
    template<auto Opts, class Ctx, class B>
    inline void nodeWriteBinary_TARGET_SELECTOR(const Node::NodeWithType &t, Ctx &ctx, B &b, size_t &ix) {
        const auto &n = *static_cast<const Node::NodeTargetSelector *>(t.data);
        auto write = [&](auto &&...args) {
            (glz::serialize<CHelper::BinaryFormat>::template op<Opts>(args, ctx, b, ix), ...);
        };
        write(n.id, n.brief, n.description, n.isMustAfterSpace, n.isMustPlayer, n.isMustNPC, n.isOnlyOne, n.isWildcard);
    }
    template<auto Opts, class Ctx, class B>
    inline void nodeWriteBinary_TEXT(const Node::NodeWithType &t, Ctx &ctx, B &b, size_t &ix) {
        const auto &n = *static_cast<const Node::NodeText *>(t.data);
        auto write = [&](auto &&...args) {
            (glz::serialize<CHelper::BinaryFormat>::template op<Opts>(args, ctx, b, ix), ...);
        };
        write(n.id, n.brief, n.description, n.isMustAfterSpace, n.data);
    }
#define CHELPER_NODE_WRITE_BINARY_CASE(v1)         \
    case Node::NodeTypeId::v1:                     \
        nodeWriteBinary_##v1<Opts>(t, ctx, b, ix); \
        break;
    // 二进制格式写出节点：uint8 类型 ID + 成员（无键名）
    template<auto Opts, class Ctx, class B>
    inline void writeNodeBinary(const Node::NodeWithType &t, Ctx &ctx, B &b, size_t &ix) {
        const std::uint8_t typeId = static_cast<std::uint8_t>(t.nodeTypeId);
        glz::serialize<CHelper::BinaryFormat>::template op<Opts>(typeId, ctx, b, ix);
        switch (t.nodeTypeId) {
            CHELPER_PASTE(CHELPER_NODE_WRITE_BINARY_CASE, CHELPER_SERIALIZABLE_NODE_TYPES)
            default:
                CHELPER_UNREACHABLE();
        }
    }
    template<auto Opts, class Ctx, class It, class End>
    inline void nodeReadBinary_BLOCK(Node::NodeWithType &t, Ctx &ctx, It &it, End &end) {
        const auto &nodeCreateStage = Node::NodeTypeDetail<Node::NodeTypeId::BLOCK>::nodeCreateStage;
        if (nodeCreateStage.empty() || std::find(nodeCreateStage.begin(), nodeCreateStage.end(), currentCreateStage) == nodeCreateStage.end()) [[unlikely]] {
            ctx.error = glz::error_code::no_matching_variant_type;
            return;
        }
        auto *node = new Node::NodeBlock();
        auto &n = *node;
        auto read = [&](auto &&...args) {
            (glz::parse<CHelper::BinaryFormat>::template op<Opts>(args, ctx, it, end), ...);
        };
        read(n.id, n.brief, n.description, n.isMustAfterSpace, n.nodeBlockType);
        if (bool(ctx.error)) [[unlikely]] {
            delete node;
            return;
        }
        if (!n.isMustAfterSpace.has_value()) [[unlikely]] {
            n.isMustAfterSpace = Node::NodeTypeDetail<Node::NodeTypeId::BLOCK>::isMustAfterSpace;
        }
        t.nodeTypeId = Node::NodeTypeId::BLOCK;
        t.data = node;
    }
    template<auto Opts, class Ctx, class It, class End>
    inline void nodeReadBinary_BOOLEAN(Node::NodeWithType &t, Ctx &ctx, It &it, End &end) {
        const auto &nodeCreateStage = Node::NodeTypeDetail<Node::NodeTypeId::BOOLEAN>::nodeCreateStage;
        if (nodeCreateStage.empty() || std::find(nodeCreateStage.begin(), nodeCreateStage.end(), currentCreateStage) == nodeCreateStage.end()) [[unlikely]] {
            ctx.error = glz::error_code::no_matching_variant_type;
            return;
        }
        auto *node = new Node::NodeBoolean();
        auto &n = *node;
        auto read = [&](auto &&...args) {
            (glz::parse<CHelper::BinaryFormat>::template op<Opts>(args, ctx, it, end), ...);
        };
        read(n.id, n.brief, n.description, n.isMustAfterSpace, n.descriptionTrue, n.descriptionFalse);
        if (bool(ctx.error)) [[unlikely]] {
            delete node;
            return;
        }
        if (!n.isMustAfterSpace.has_value()) [[unlikely]] {
            n.isMustAfterSpace = Node::NodeTypeDetail<Node::NodeTypeId::BOOLEAN>::isMustAfterSpace;
        }
        t.nodeTypeId = Node::NodeTypeId::BOOLEAN;
        t.data = node;
    }
    template<auto Opts, class Ctx, class It, class End>
    inline void nodeReadBinary_COMMAND(Node::NodeWithType &t, Ctx &ctx, It &it, End &end) {
        const auto &nodeCreateStage = Node::NodeTypeDetail<Node::NodeTypeId::COMMAND>::nodeCreateStage;
        if (nodeCreateStage.empty() || std::find(nodeCreateStage.begin(), nodeCreateStage.end(), currentCreateStage) == nodeCreateStage.end()) [[unlikely]] {
            ctx.error = glz::error_code::no_matching_variant_type;
            return;
        }
        auto *node = new Node::NodeCommand();
        auto &n = *node;
        auto read = [&](auto &&...args) {
            (glz::parse<CHelper::BinaryFormat>::template op<Opts>(args, ctx, it, end), ...);
        };
        read(n.id, n.brief, n.description, n.isMustAfterSpace);
        if (bool(ctx.error)) [[unlikely]] {
            delete node;
            return;
        }
        if (!n.isMustAfterSpace.has_value()) [[unlikely]] {
            n.isMustAfterSpace = Node::NodeTypeDetail<Node::NodeTypeId::COMMAND>::isMustAfterSpace;
        }
        t.nodeTypeId = Node::NodeTypeId::COMMAND;
        t.data = node;
    }
    template<auto Opts, class Ctx, class It, class End>
    inline void nodeReadBinary_COMMAND_NAME(Node::NodeWithType &t, Ctx &ctx, It &it, End &end) {
        const auto &nodeCreateStage = Node::NodeTypeDetail<Node::NodeTypeId::COMMAND_NAME>::nodeCreateStage;
        if (nodeCreateStage.empty() || std::find(nodeCreateStage.begin(), nodeCreateStage.end(), currentCreateStage) == nodeCreateStage.end()) [[unlikely]] {
            ctx.error = glz::error_code::no_matching_variant_type;
            return;
        }
        auto *node = new Node::NodeCommandName();
        auto &n = *node;
        auto read = [&](auto &&...args) {
            (glz::parse<CHelper::BinaryFormat>::template op<Opts>(args, ctx, it, end), ...);
        };
        read(n.id, n.brief, n.description, n.isMustAfterSpace);
        if (bool(ctx.error)) [[unlikely]] {
            delete node;
            return;
        }
        if (!n.isMustAfterSpace.has_value()) [[unlikely]] {
            n.isMustAfterSpace = Node::NodeTypeDetail<Node::NodeTypeId::COMMAND_NAME>::isMustAfterSpace;
        }
        t.nodeTypeId = Node::NodeTypeId::COMMAND_NAME;
        t.data = node;
    }
    template<auto Opts, class Ctx, class It, class End>
    inline void nodeReadBinary_FLOAT(Node::NodeWithType &t, Ctx &ctx, It &it, End &end) {
        const auto &nodeCreateStage = Node::NodeTypeDetail<Node::NodeTypeId::FLOAT>::nodeCreateStage;
        if (nodeCreateStage.empty() || std::find(nodeCreateStage.begin(), nodeCreateStage.end(), currentCreateStage) == nodeCreateStage.end()) [[unlikely]] {
            ctx.error = glz::error_code::no_matching_variant_type;
            return;
        }
        auto *node = new Node::NodeFloat();
        auto &n = *node;
        auto read = [&](auto &&...args) {
            (glz::parse<CHelper::BinaryFormat>::template op<Opts>(args, ctx, it, end), ...);
        };
        read(n.id, n.brief, n.description, n.isMustAfterSpace, n.min, n.max);
        if (bool(ctx.error)) [[unlikely]] {
            delete node;
            return;
        }
        if (!n.isMustAfterSpace.has_value()) [[unlikely]] {
            n.isMustAfterSpace = Node::NodeTypeDetail<Node::NodeTypeId::FLOAT>::isMustAfterSpace;
        }
        t.nodeTypeId = Node::NodeTypeId::FLOAT;
        t.data = node;
    }
    template<auto Opts, class Ctx, class It, class End>
    inline void nodeReadBinary_INTEGER(Node::NodeWithType &t, Ctx &ctx, It &it, End &end) {
        const auto &nodeCreateStage = Node::NodeTypeDetail<Node::NodeTypeId::INTEGER>::nodeCreateStage;
        if (nodeCreateStage.empty() || std::find(nodeCreateStage.begin(), nodeCreateStage.end(), currentCreateStage) == nodeCreateStage.end()) [[unlikely]] {
            ctx.error = glz::error_code::no_matching_variant_type;
            return;
        }
        auto *node = new Node::NodeInteger();
        auto &n = *node;
        auto read = [&](auto &&...args) {
            (glz::parse<CHelper::BinaryFormat>::template op<Opts>(args, ctx, it, end), ...);
        };
        read(n.id, n.brief, n.description, n.isMustAfterSpace, n.min, n.max);
        if (bool(ctx.error)) [[unlikely]] {
            delete node;
            return;
        }
        if (!n.isMustAfterSpace.has_value()) [[unlikely]] {
            n.isMustAfterSpace = Node::NodeTypeDetail<Node::NodeTypeId::INTEGER>::isMustAfterSpace;
        }
        t.nodeTypeId = Node::NodeTypeId::INTEGER;
        t.data = node;
    }
    template<auto Opts, class Ctx, class It, class End>
    inline void nodeReadBinary_INTEGER_WITH_UNIT(Node::NodeWithType &t, Ctx &ctx, It &it, End &end) {
        const auto &nodeCreateStage = Node::NodeTypeDetail<Node::NodeTypeId::INTEGER_WITH_UNIT>::nodeCreateStage;
        if (nodeCreateStage.empty() || std::find(nodeCreateStage.begin(), nodeCreateStage.end(), currentCreateStage) == nodeCreateStage.end()) [[unlikely]] {
            ctx.error = glz::error_code::no_matching_variant_type;
            return;
        }
        auto *node = new Node::NodeIntegerWithUnit();
        auto &n = *node;
        auto read = [&](auto &&...args) {
            (glz::parse<CHelper::BinaryFormat>::template op<Opts>(args, ctx, it, end), ...);
        };
        read(n.id, n.brief, n.description, n.isMustAfterSpace, n.units);
        if (bool(ctx.error)) [[unlikely]] {
            delete node;
            return;
        }
        if (!n.isMustAfterSpace.has_value()) [[unlikely]] {
            n.isMustAfterSpace = Node::NodeTypeDetail<Node::NodeTypeId::INTEGER_WITH_UNIT>::isMustAfterSpace;
        }
        t.nodeTypeId = Node::NodeTypeId::INTEGER_WITH_UNIT;
        t.data = node;
    }
    template<auto Opts, class Ctx, class It, class End>
    inline void nodeReadBinary_ITEM(Node::NodeWithType &t, Ctx &ctx, It &it, End &end) {
        const auto &nodeCreateStage = Node::NodeTypeDetail<Node::NodeTypeId::ITEM>::nodeCreateStage;
        if (nodeCreateStage.empty() || std::find(nodeCreateStage.begin(), nodeCreateStage.end(), currentCreateStage) == nodeCreateStage.end()) [[unlikely]] {
            ctx.error = glz::error_code::no_matching_variant_type;
            return;
        }
        auto *node = new Node::NodeItem();
        auto &n = *node;
        auto read = [&](auto &&...args) {
            (glz::parse<CHelper::BinaryFormat>::template op<Opts>(args, ctx, it, end), ...);
        };
        read(n.id, n.brief, n.description, n.isMustAfterSpace, n.nodeItemType);
        if (bool(ctx.error)) [[unlikely]] {
            delete node;
            return;
        }
        if (!n.isMustAfterSpace.has_value()) [[unlikely]] {
            n.isMustAfterSpace = Node::NodeTypeDetail<Node::NodeTypeId::ITEM>::isMustAfterSpace;
        }
        t.nodeTypeId = Node::NodeTypeId::ITEM;
        t.data = node;
    }
    template<auto Opts, class Ctx, class It, class End>
    inline void nodeReadBinary_JSON(Node::NodeWithType &t, Ctx &ctx, It &it, End &end) {
        const auto &nodeCreateStage = Node::NodeTypeDetail<Node::NodeTypeId::JSON>::nodeCreateStage;
        if (nodeCreateStage.empty() || std::find(nodeCreateStage.begin(), nodeCreateStage.end(), currentCreateStage) == nodeCreateStage.end()) [[unlikely]] {
            ctx.error = glz::error_code::no_matching_variant_type;
            return;
        }
        auto *node = new Node::NodeJson();
        auto &n = *node;
        auto read = [&](auto &&...args) {
            (glz::parse<CHelper::BinaryFormat>::template op<Opts>(args, ctx, it, end), ...);
        };
        read(n.id, n.brief, n.description, n.isMustAfterSpace, n.key);
        if (bool(ctx.error)) [[unlikely]] {
            delete node;
            return;
        }
        if (!n.isMustAfterSpace.has_value()) [[unlikely]] {
            n.isMustAfterSpace = Node::NodeTypeDetail<Node::NodeTypeId::JSON>::isMustAfterSpace;
        }
        t.nodeTypeId = Node::NodeTypeId::JSON;
        t.data = node;
    }
    template<auto Opts, class Ctx, class It, class End>
    inline void nodeReadBinary_JSON_BOOLEAN(Node::NodeWithType &t, Ctx &ctx, It &it, End &end) {
        const auto &nodeCreateStage = Node::NodeTypeDetail<Node::NodeTypeId::JSON_BOOLEAN>::nodeCreateStage;
        if (nodeCreateStage.empty() || std::find(nodeCreateStage.begin(), nodeCreateStage.end(), currentCreateStage) == nodeCreateStage.end()) [[unlikely]] {
            ctx.error = glz::error_code::no_matching_variant_type;
            return;
        }
        auto *node = new Node::NodeJsonBoolean();
        auto &n = *node;
        auto read = [&](auto &&...args) {
            (glz::parse<CHelper::BinaryFormat>::template op<Opts>(args, ctx, it, end), ...);
        };
        read(n.id, n.brief, n.description, n.isMustAfterSpace, n.descriptionTrue, n.descriptionFalse);
        if (bool(ctx.error)) [[unlikely]] {
            delete node;
            return;
        }
        if (!n.isMustAfterSpace.has_value()) [[unlikely]] {
            n.isMustAfterSpace = Node::NodeTypeDetail<Node::NodeTypeId::JSON_BOOLEAN>::isMustAfterSpace;
        }
        t.nodeTypeId = Node::NodeTypeId::JSON_BOOLEAN;
        t.data = node;
    }
    template<auto Opts, class Ctx, class It, class End>
    inline void nodeReadBinary_JSON_FLOAT(Node::NodeWithType &t, Ctx &ctx, It &it, End &end) {
        const auto &nodeCreateStage = Node::NodeTypeDetail<Node::NodeTypeId::JSON_FLOAT>::nodeCreateStage;
        if (nodeCreateStage.empty() || std::find(nodeCreateStage.begin(), nodeCreateStage.end(), currentCreateStage) == nodeCreateStage.end()) [[unlikely]] {
            ctx.error = glz::error_code::no_matching_variant_type;
            return;
        }
        auto *node = new Node::NodeJsonFloat();
        auto &n = *node;
        auto read = [&](auto &&...args) {
            (glz::parse<CHelper::BinaryFormat>::template op<Opts>(args, ctx, it, end), ...);
        };
        read(n.id, n.brief, n.description, n.isMustAfterSpace, n.min, n.max);
        if (bool(ctx.error)) [[unlikely]] {
            delete node;
            return;
        }
        if (!n.isMustAfterSpace.has_value()) [[unlikely]] {
            n.isMustAfterSpace = Node::NodeTypeDetail<Node::NodeTypeId::JSON_FLOAT>::isMustAfterSpace;
        }
        t.nodeTypeId = Node::NodeTypeId::JSON_FLOAT;
        t.data = node;
    }
    template<auto Opts, class Ctx, class It, class End>
    inline void nodeReadBinary_JSON_INTEGER(Node::NodeWithType &t, Ctx &ctx, It &it, End &end) {
        const auto &nodeCreateStage = Node::NodeTypeDetail<Node::NodeTypeId::JSON_INTEGER>::nodeCreateStage;
        if (nodeCreateStage.empty() || std::find(nodeCreateStage.begin(), nodeCreateStage.end(), currentCreateStage) == nodeCreateStage.end()) [[unlikely]] {
            ctx.error = glz::error_code::no_matching_variant_type;
            return;
        }
        auto *node = new Node::NodeJsonInteger();
        auto &n = *node;
        auto read = [&](auto &&...args) {
            (glz::parse<CHelper::BinaryFormat>::template op<Opts>(args, ctx, it, end), ...);
        };
        read(n.id, n.brief, n.description, n.isMustAfterSpace, n.min, n.max);
        if (bool(ctx.error)) [[unlikely]] {
            delete node;
            return;
        }
        if (!n.isMustAfterSpace.has_value()) [[unlikely]] {
            n.isMustAfterSpace = Node::NodeTypeDetail<Node::NodeTypeId::JSON_INTEGER>::isMustAfterSpace;
        }
        t.nodeTypeId = Node::NodeTypeId::JSON_INTEGER;
        t.data = node;
    }
    template<auto Opts, class Ctx, class It, class End>
    inline void nodeReadBinary_JSON_LIST(Node::NodeWithType &t, Ctx &ctx, It &it, End &end) {
        const auto &nodeCreateStage = Node::NodeTypeDetail<Node::NodeTypeId::JSON_LIST>::nodeCreateStage;
        if (nodeCreateStage.empty() || std::find(nodeCreateStage.begin(), nodeCreateStage.end(), currentCreateStage) == nodeCreateStage.end()) [[unlikely]] {
            ctx.error = glz::error_code::no_matching_variant_type;
            return;
        }
        auto *node = new Node::NodeJsonList();
        auto &n = *node;
        auto read = [&](auto &&...args) {
            (glz::parse<CHelper::BinaryFormat>::template op<Opts>(args, ctx, it, end), ...);
        };
        read(n.id, n.brief, n.description, n.isMustAfterSpace, n.data);
        if (bool(ctx.error)) [[unlikely]] {
            delete node;
            return;
        }
        if (!n.isMustAfterSpace.has_value()) [[unlikely]] {
            n.isMustAfterSpace = Node::NodeTypeDetail<Node::NodeTypeId::JSON_LIST>::isMustAfterSpace;
        }
        t.nodeTypeId = Node::NodeTypeId::JSON_LIST;
        t.data = node;
    }
    template<auto Opts, class Ctx, class It, class End>
    inline void nodeReadBinary_JSON_NULL(Node::NodeWithType &t, Ctx &ctx, It &it, End &end) {
        const auto &nodeCreateStage = Node::NodeTypeDetail<Node::NodeTypeId::JSON_NULL>::nodeCreateStage;
        if (nodeCreateStage.empty() || std::find(nodeCreateStage.begin(), nodeCreateStage.end(), currentCreateStage) == nodeCreateStage.end()) [[unlikely]] {
            ctx.error = glz::error_code::no_matching_variant_type;
            return;
        }
        auto *node = new Node::NodeJsonNull();
        auto &n = *node;
        auto read = [&](auto &&...args) {
            (glz::parse<CHelper::BinaryFormat>::template op<Opts>(args, ctx, it, end), ...);
        };
        read(n.id, n.brief, n.description, n.isMustAfterSpace);
        if (bool(ctx.error)) [[unlikely]] {
            delete node;
            return;
        }
        if (!n.isMustAfterSpace.has_value()) [[unlikely]] {
            n.isMustAfterSpace = Node::NodeTypeDetail<Node::NodeTypeId::JSON_NULL>::isMustAfterSpace;
        }
        t.nodeTypeId = Node::NodeTypeId::JSON_NULL;
        t.data = node;
    }
    template<auto Opts, class Ctx, class It, class End>
    inline void nodeReadBinary_JSON_ENTRY(Node::NodeWithType &t, Ctx &ctx, It &it, End &end) {
        const auto &nodeCreateStage = Node::NodeTypeDetail<Node::NodeTypeId::JSON_ENTRY>::nodeCreateStage;
        if (nodeCreateStage.empty() || std::find(nodeCreateStage.begin(), nodeCreateStage.end(), currentCreateStage) == nodeCreateStage.end()) [[unlikely]] {
            ctx.error = glz::error_code::no_matching_variant_type;
            return;
        }
        auto *node = new Node::NodeJsonEntry();
        auto &n = *node;
        auto read = [&](auto &&...args) {
            (glz::parse<CHelper::BinaryFormat>::template op<Opts>(args, ctx, it, end), ...);
        };
        read(n.id, n.brief, n.description, n.isMustAfterSpace, n.key, n.value);
        if (bool(ctx.error)) [[unlikely]] {
            delete node;
            return;
        }
        if (!n.isMustAfterSpace.has_value()) [[unlikely]] {
            n.isMustAfterSpace = Node::NodeTypeDetail<Node::NodeTypeId::JSON_ENTRY>::isMustAfterSpace;
        }
        t.nodeTypeId = Node::NodeTypeId::JSON_ENTRY;
        t.data = node;
    }
    template<auto Opts, class Ctx, class It, class End>
    inline void nodeReadBinary_JSON_OBJECT(Node::NodeWithType &t, Ctx &ctx, It &it, End &end) {
        const auto &nodeCreateStage = Node::NodeTypeDetail<Node::NodeTypeId::JSON_OBJECT>::nodeCreateStage;
        if (nodeCreateStage.empty() || std::find(nodeCreateStage.begin(), nodeCreateStage.end(), currentCreateStage) == nodeCreateStage.end()) [[unlikely]] {
            ctx.error = glz::error_code::no_matching_variant_type;
            return;
        }
        auto *node = new Node::NodeJsonObject();
        auto &n = *node;
        auto read = [&](auto &&...args) {
            (glz::parse<CHelper::BinaryFormat>::template op<Opts>(args, ctx, it, end), ...);
        };
        read(n.id, n.brief, n.description, n.isMustAfterSpace, n.data);
        if (bool(ctx.error)) [[unlikely]] {
            delete node;
            return;
        }
        if (!n.isMustAfterSpace.has_value()) [[unlikely]] {
            n.isMustAfterSpace = Node::NodeTypeDetail<Node::NodeTypeId::JSON_OBJECT>::isMustAfterSpace;
        }
        t.nodeTypeId = Node::NodeTypeId::JSON_OBJECT;
        t.data = node;
    }
    template<auto Opts, class Ctx, class It, class End>
    inline void nodeReadBinary_JSON_STRING(Node::NodeWithType &t, Ctx &ctx, It &it, End &end) {
        const auto &nodeCreateStage = Node::NodeTypeDetail<Node::NodeTypeId::JSON_STRING>::nodeCreateStage;
        if (nodeCreateStage.empty() || std::find(nodeCreateStage.begin(), nodeCreateStage.end(), currentCreateStage) == nodeCreateStage.end()) [[unlikely]] {
            ctx.error = glz::error_code::no_matching_variant_type;
            return;
        }
        auto *node = new Node::NodeJsonString();
        auto &n = *node;
        auto read = [&](auto &&...args) {
            (glz::parse<CHelper::BinaryFormat>::template op<Opts>(args, ctx, it, end), ...);
        };
        read(n.id, n.brief, n.description, n.isMustAfterSpace, n.data);
        if (bool(ctx.error)) [[unlikely]] {
            delete node;
            return;
        }
        if (!n.isMustAfterSpace.has_value()) [[unlikely]] {
            n.isMustAfterSpace = Node::NodeTypeDetail<Node::NodeTypeId::JSON_STRING>::isMustAfterSpace;
        }
        t.nodeTypeId = Node::NodeTypeId::JSON_STRING;
        t.data = node;
    }
    template<auto Opts, class Ctx, class It, class End>
    inline void nodeReadBinary_NAMESPACE_ID(Node::NodeWithType &t, Ctx &ctx, It &it, End &end) {
        const auto &nodeCreateStage = Node::NodeTypeDetail<Node::NodeTypeId::NAMESPACE_ID>::nodeCreateStage;
        if (nodeCreateStage.empty() || std::find(nodeCreateStage.begin(), nodeCreateStage.end(), currentCreateStage) == nodeCreateStage.end()) [[unlikely]] {
            ctx.error = glz::error_code::no_matching_variant_type;
            return;
        }
        auto *node = new Node::NodeNamespaceId();
        auto &n = *node;
        auto read = [&](auto &&...args) {
            (glz::parse<CHelper::BinaryFormat>::template op<Opts>(args, ctx, it, end), ...);
        };
        read(n.id, n.brief, n.description, n.isMustAfterSpace, n.key, n.ignoreError, n.contents);
        if (bool(ctx.error)) [[unlikely]] {
            delete node;
            return;
        }
        if (!n.isMustAfterSpace.has_value()) [[unlikely]] {
            n.isMustAfterSpace = Node::NodeTypeDetail<Node::NodeTypeId::NAMESPACE_ID>::isMustAfterSpace;
        }
        t.nodeTypeId = Node::NodeTypeId::NAMESPACE_ID;
        t.data = node;
    }
    template<auto Opts, class Ctx, class It, class End>
    inline void nodeReadBinary_NORMAL_ID(Node::NodeWithType &t, Ctx &ctx, It &it, End &end) {
        const auto &nodeCreateStage = Node::NodeTypeDetail<Node::NodeTypeId::NORMAL_ID>::nodeCreateStage;
        if (nodeCreateStage.empty() || std::find(nodeCreateStage.begin(), nodeCreateStage.end(), currentCreateStage) == nodeCreateStage.end()) [[unlikely]] {
            ctx.error = glz::error_code::no_matching_variant_type;
            return;
        }
        auto *node = new Node::NodeNormalId();
        auto &n = *node;
        auto read = [&](auto &&...args) {
            (glz::parse<CHelper::BinaryFormat>::template op<Opts>(args, ctx, it, end), ...);
        };
        read(n.id, n.brief, n.description, n.isMustAfterSpace, n.key, n.ignoreError, n.contents);
        if (bool(ctx.error)) [[unlikely]] {
            delete node;
            return;
        }
        if (!n.isMustAfterSpace.has_value()) [[unlikely]] {
            n.isMustAfterSpace = Node::NodeTypeDetail<Node::NodeTypeId::NORMAL_ID>::isMustAfterSpace;
        }
        t.nodeTypeId = Node::NodeTypeId::NORMAL_ID;
        t.data = node;
    }
    template<auto Opts, class Ctx, class It, class End>
    inline void nodeReadBinary_POSITION(Node::NodeWithType &t, Ctx &ctx, It &it, End &end) {
        const auto &nodeCreateStage = Node::NodeTypeDetail<Node::NodeTypeId::POSITION>::nodeCreateStage;
        if (nodeCreateStage.empty() || std::find(nodeCreateStage.begin(), nodeCreateStage.end(), currentCreateStage) == nodeCreateStage.end()) [[unlikely]] {
            ctx.error = glz::error_code::no_matching_variant_type;
            return;
        }
        auto *node = new Node::NodePosition();
        auto &n = *node;
        auto read = [&](auto &&...args) {
            (glz::parse<CHelper::BinaryFormat>::template op<Opts>(args, ctx, it, end), ...);
        };
        read(n.id, n.brief, n.description, n.isMustAfterSpace);
        if (bool(ctx.error)) [[unlikely]] {
            delete node;
            return;
        }
        if (!n.isMustAfterSpace.has_value()) [[unlikely]] {
            n.isMustAfterSpace = Node::NodeTypeDetail<Node::NodeTypeId::POSITION>::isMustAfterSpace;
        }
        t.nodeTypeId = Node::NodeTypeId::POSITION;
        t.data = node;
    }
    template<auto Opts, class Ctx, class It, class End>
    inline void nodeReadBinary_RANGE(Node::NodeWithType &t, Ctx &ctx, It &it, End &end) {
        const auto &nodeCreateStage = Node::NodeTypeDetail<Node::NodeTypeId::RANGE>::nodeCreateStage;
        if (nodeCreateStage.empty() || std::find(nodeCreateStage.begin(), nodeCreateStage.end(), currentCreateStage) == nodeCreateStage.end()) [[unlikely]] {
            ctx.error = glz::error_code::no_matching_variant_type;
            return;
        }
        auto *node = new Node::NodeRange();
        auto &n = *node;
        auto read = [&](auto &&...args) {
            (glz::parse<CHelper::BinaryFormat>::template op<Opts>(args, ctx, it, end), ...);
        };
        read(n.id, n.brief, n.description, n.isMustAfterSpace);
        if (bool(ctx.error)) [[unlikely]] {
            delete node;
            return;
        }
        if (!n.isMustAfterSpace.has_value()) [[unlikely]] {
            n.isMustAfterSpace = Node::NodeTypeDetail<Node::NodeTypeId::RANGE>::isMustAfterSpace;
        }
        t.nodeTypeId = Node::NodeTypeId::RANGE;
        t.data = node;
    }
    template<auto Opts, class Ctx, class It, class End>
    inline void nodeReadBinary_RELATIVE_FLOAT(Node::NodeWithType &t, Ctx &ctx, It &it, End &end) {
        const auto &nodeCreateStage = Node::NodeTypeDetail<Node::NodeTypeId::RELATIVE_FLOAT>::nodeCreateStage;
        if (nodeCreateStage.empty() || std::find(nodeCreateStage.begin(), nodeCreateStage.end(), currentCreateStage) == nodeCreateStage.end()) [[unlikely]] {
            ctx.error = glz::error_code::no_matching_variant_type;
            return;
        }
        auto *node = new Node::NodeRelativeFloat();
        auto &n = *node;
        auto read = [&](auto &&...args) {
            (glz::parse<CHelper::BinaryFormat>::template op<Opts>(args, ctx, it, end), ...);
        };
        read(n.id, n.brief, n.description, n.isMustAfterSpace, n.canUseCaretNotation);
        if (bool(ctx.error)) [[unlikely]] {
            delete node;
            return;
        }
        if (!n.isMustAfterSpace.has_value()) [[unlikely]] {
            n.isMustAfterSpace = Node::NodeTypeDetail<Node::NodeTypeId::RELATIVE_FLOAT>::isMustAfterSpace;
        }
        t.nodeTypeId = Node::NodeTypeId::RELATIVE_FLOAT;
        t.data = node;
    }
    template<auto Opts, class Ctx, class It, class End>
    inline void nodeReadBinary_REPEAT(Node::NodeWithType &t, Ctx &ctx, It &it, End &end) {
        const auto &nodeCreateStage = Node::NodeTypeDetail<Node::NodeTypeId::REPEAT>::nodeCreateStage;
        if (nodeCreateStage.empty() || std::find(nodeCreateStage.begin(), nodeCreateStage.end(), currentCreateStage) == nodeCreateStage.end()) [[unlikely]] {
            ctx.error = glz::error_code::no_matching_variant_type;
            return;
        }
        auto *node = new Node::NodeRepeat();
        auto &n = *node;
        auto read = [&](auto &&...args) {
            (glz::parse<CHelper::BinaryFormat>::template op<Opts>(args, ctx, it, end), ...);
        };
        read(n.id, n.brief, n.description, n.isMustAfterSpace, n.key);
        if (bool(ctx.error)) [[unlikely]] {
            delete node;
            return;
        }
        if (!n.isMustAfterSpace.has_value()) [[unlikely]] {
            n.isMustAfterSpace = Node::NodeTypeDetail<Node::NodeTypeId::REPEAT>::isMustAfterSpace;
        }
        t.nodeTypeId = Node::NodeTypeId::REPEAT;
        t.data = node;
    }
    template<auto Opts, class Ctx, class It, class End>
    inline void nodeReadBinary_STRING(Node::NodeWithType &t, Ctx &ctx, It &it, End &end) {
        const auto &nodeCreateStage = Node::NodeTypeDetail<Node::NodeTypeId::STRING>::nodeCreateStage;
        if (nodeCreateStage.empty() || std::find(nodeCreateStage.begin(), nodeCreateStage.end(), currentCreateStage) == nodeCreateStage.end()) [[unlikely]] {
            ctx.error = glz::error_code::no_matching_variant_type;
            return;
        }
        auto *node = new Node::NodeString();
        auto &n = *node;
        auto read = [&](auto &&...args) {
            (glz::parse<CHelper::BinaryFormat>::template op<Opts>(args, ctx, it, end), ...);
        };
        read(n.id, n.brief, n.description, n.isMustAfterSpace, n.canContainSpace, n.ignoreLater);
        if (bool(ctx.error)) [[unlikely]] {
            delete node;
            return;
        }
        if (!n.isMustAfterSpace.has_value()) [[unlikely]] {
            n.isMustAfterSpace = Node::NodeTypeDetail<Node::NodeTypeId::STRING>::isMustAfterSpace;
        }
        t.nodeTypeId = Node::NodeTypeId::STRING;
        t.data = node;
    }
    template<auto Opts, class Ctx, class It, class End>
    inline void nodeReadBinary_TARGET_SELECTOR(Node::NodeWithType &t, Ctx &ctx, It &it, End &end) {
        const auto &nodeCreateStage = Node::NodeTypeDetail<Node::NodeTypeId::TARGET_SELECTOR>::nodeCreateStage;
        if (nodeCreateStage.empty() || std::find(nodeCreateStage.begin(), nodeCreateStage.end(), currentCreateStage) == nodeCreateStage.end()) [[unlikely]] {
            ctx.error = glz::error_code::no_matching_variant_type;
            return;
        }
        auto *node = new Node::NodeTargetSelector();
        auto &n = *node;
        auto read = [&](auto &&...args) {
            (glz::parse<CHelper::BinaryFormat>::template op<Opts>(args, ctx, it, end), ...);
        };
        read(n.id, n.brief, n.description, n.isMustAfterSpace, n.isMustPlayer, n.isMustNPC, n.isOnlyOne, n.isWildcard);
        if (bool(ctx.error)) [[unlikely]] {
            delete node;
            return;
        }
        if (!n.isMustAfterSpace.has_value()) [[unlikely]] {
            n.isMustAfterSpace = Node::NodeTypeDetail<Node::NodeTypeId::TARGET_SELECTOR>::isMustAfterSpace;
        }
        t.nodeTypeId = Node::NodeTypeId::TARGET_SELECTOR;
        t.data = node;
    }
    template<auto Opts, class Ctx, class It, class End>
    inline void nodeReadBinary_TEXT(Node::NodeWithType &t, Ctx &ctx, It &it, End &end) {
        const auto &nodeCreateStage = Node::NodeTypeDetail<Node::NodeTypeId::TEXT>::nodeCreateStage;
        if (nodeCreateStage.empty() || std::find(nodeCreateStage.begin(), nodeCreateStage.end(), currentCreateStage) == nodeCreateStage.end()) [[unlikely]] {
            ctx.error = glz::error_code::no_matching_variant_type;
            return;
        }
        auto *node = new Node::NodeText();
        auto &n = *node;
        auto read = [&](auto &&...args) {
            (glz::parse<CHelper::BinaryFormat>::template op<Opts>(args, ctx, it, end), ...);
        };
        read(n.id, n.brief, n.description, n.isMustAfterSpace, n.data);
        if (bool(ctx.error)) [[unlikely]] {
            delete node;
            return;
        }
        if (!n.isMustAfterSpace.has_value()) [[unlikely]] {
            n.isMustAfterSpace = Node::NodeTypeDetail<Node::NodeTypeId::TEXT>::isMustAfterSpace;
        }
        t.nodeTypeId = Node::NodeTypeId::TEXT;
        t.data = node;
    }
#define CHELPER_NODE_READ_BINARY_CASE(v1)           \
    case Node::NodeTypeId::v1:                      \
        nodeReadBinary_##v1<Opts>(t, ctx, it, end); \
        break;
    // 二进制格式读取节点：uint8 类型 ID + 成员（与写出严格对应）
    template<auto Opts, class Ctx, class It, class End>
    inline void readNodeBinary(Node::NodeWithType &t, Ctx &ctx, It &it, End &end) {
        std::uint8_t typeId = 0;
        glz::parse<CHelper::BinaryFormat>::template op<Opts>(typeId, ctx, it, end);
        if (bool(ctx.error)) [[unlikely]] {
            return;
        }
        switch (typeId) {
            CHELPER_PASTE(CHELPER_NODE_READ_BINARY_CASE, CHELPER_SERIALIZABLE_NODE_TYPES)
            default:
                ctx.error = glz::error_code::no_matching_variant_type;
                break;
        }
    }
    // 第一遍扫描：跳过对象/map 的所有值，仅提取 "type" 的值
    template<std::uint32_t Fmt, auto Opts>
    inline bool scanNodeTypeName(std::string &typeName, glz::is_context auto &&ctx, auto &&it, auto &&end) {
        if constexpr (Fmt == glz::JSON) {
            glz::skip_ws<Opts>(ctx, it, end);
            if (*it != '{') [[unlikely]] {
                ctx.error = glz::error_code::expected_brace;
                return false;
            }
            ++it;
            glz::skip_ws<Opts>(ctx, it, end);
            if (*it == '}') {
                ++it;
            } else {
                while (true) {
                    glz::skip_ws<Opts>(ctx, it, end);
                    std::string key;
                    glz::parse<glz::JSON>::op<Opts>(key, ctx, it, end);
                    if (bool(ctx.error)) return false;
                    glz::skip_ws<Opts>(ctx, it, end);
                    ++it;// ':'
                    glz::skip_ws<Opts>(ctx, it, end);
                    if (key == "type") {
                        glz::parse<glz::JSON>::op<Opts>(typeName, ctx, it, end);
                        if (bool(ctx.error)) return false;
                    } else {
                        glz::skip_value<glz::JSON>::op<Opts>(ctx, it, end);
                        if (bool(ctx.error)) return false;
                    }
                    glz::skip_ws<Opts>(ctx, it, end);
                    if (*it == ',') {
                        ++it;
                        continue;
                    }
                    if (*it == '}') {
                        ++it;
                        break;
                    }
                    ctx.error = glz::error_code::syntax_error;
                    return false;
                }
            }
            return true;
        } else {
            // MSGPACK
            if (it >= end) [[unlikely]] {
                ctx.error = glz::error_code::unexpected_end;
                return false;
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
                return false;
            }
            for (uint32_t i = 0; i < size; ++i) {
                std::string key;
                glz::parse<glz::MSGPACK>::op<Opts>(key, ctx, it, end);
                if (bool(ctx.error)) return false;
                if (key == "type") {
                    glz::parse<glz::MSGPACK>::op<Opts>(typeName, ctx, it, end);
                    if (bool(ctx.error)) return false;
                } else {
                    glz::skip_value<glz::MSGPACK>::op<Opts>(ctx, it, end);
                    if (bool(ctx.error)) return false;
                }
            }
            return true;
        }
    }

#define CHELPER_NODE_READ_CASE(v1)                                                                                  \
    case Node::NodeTypeId::v1: {                                                                                    \
        const auto &nodeCreateStage = Node::NodeTypeDetail<Node::NodeTypeId::v1>::nodeCreateStage;                  \
        if (nodeCreateStage.empty() ||                                                                              \
            std::find(nodeCreateStage.begin(), nodeCreateStage.end(), currentCreateStage) == nodeCreateStage.end()) \
                [[unlikely]] {                                                                                      \
            ctx.error = glz::error_code::no_matching_variant_type;                                                  \
            return;                                                                                                 \
        }                                                                                                           \
        using NT = Node::NodeTypeDetail<Node::NodeTypeId::v1>::Type;                                                \
        auto *node = new NT();                                                                                      \
        glz::parse<Fmt>::template op<Opts>(*node, ctx, it, end);                                                    \
        if (bool(ctx.error)) [[unlikely]] {                                                                         \
            delete node;                                                                                            \
            return;                                                                                                 \
        }                                                                                                           \
        if (!node->isMustAfterSpace.has_value()) [[unlikely]] {                                                     \
            node->isMustAfterSpace = Node::NodeTypeDetail<Node::NodeTypeId::v1>::isMustAfterSpace;                  \
        }                                                                                                           \
        t.nodeTypeId = Node::NodeTypeId::v1;                                                                        \
        t.data = node;                                                                                              \
        break;                                                                                                      \
    }

    // 第二遍：按具体节点类型反序列化
    template<std::uint32_t Fmt, auto Opts>
    inline void readNodeValue(Node::NodeWithType &t, const std::string_view typeName, glz::is_context auto &&ctx, auto &&it,
                              auto &&end) {
        const std::optional<Node::NodeTypeId::NodeTypeId> id = Node::getNodeTypeIdByName(typeName);
        if (!id.has_value()) [[unlikely]] {
            ctx.error = glz::error_code::no_matching_variant_type;
            return;
        }
        switch (id.value()) {
            CHELPER_PASTE(CHELPER_NODE_READ_CASE, CHELPER_SERIALIZABLE_NODE_TYPES)
            default:
                CHELPER_UNREACHABLE();
        }
    }

    // 节点对象反序列化：第一遍提取 "type"，第二遍重置迭代器按具体类型读取
    template<std::uint32_t Fmt, auto Opts>
    inline void readNodeWithType(Node::NodeWithType &t, glz::is_context auto &&ctx, auto &&it, auto &&end) {
        constexpr auto opts = glz::opts{.error_on_unknown_keys = false};
        const auto start = it;
        std::string typeName;
        if (!scanNodeTypeName<Fmt, opts>(typeName, ctx, it, end)) return;
        it = start;
        readNodeValue<Fmt, opts>(t, typeName, ctx, it, end);
    }
}// namespace CHelper

namespace glz {
    template<>
    struct to<JSON, CHelper::Node::NodeWithType> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
            CHelper::writeNodeValue<JSON, Opts>(value, ctx, b, ix);
        }
    };

    template<>
    struct to<MSGPACK, CHelper::Node::NodeWithType> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
            CHelper::writeNodeValue<MSGPACK, Opts>(value, ctx, b, ix);
        }
    };

    template<>
    struct from<JSON, CHelper::Node::NodeWithType> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&it, auto &&end) {
            CHelper::readNodeWithType<JSON, Opts>(value, ctx, it, end);
        }
    };

    template<>
    struct from<MSGPACK, CHelper::Node::NodeWithType> {
        template<auto Opts>
        static void op(auto &&value, uint8_t, glz::is_context auto &&ctx, auto &&it, auto &&end) {
            // tag 已被分发器消费，回退一个字节后统一走两遍读取
            --it;
            CHelper::readNodeWithType<MSGPACK, Opts>(value, ctx, it, end);
        }
    };

    template<>
    struct to<CHelper::BinaryFormat, CHelper::Node::NodeWithType> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
            CHelper::writeNodeBinary<Opts>(value, ctx, b, ix);
        }
    };

    template<>
    struct from<CHelper::BinaryFormat, CHelper::Node::NodeWithType> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&it, auto &&end) {
            CHelper::readNodeBinary<Opts>(value, ctx, it, end);
        }
    };

}// namespace glz

// ================= FreeableNodeWithTypes / NodeJsonElement / RepeatData =================
namespace CHelper::Node {
    class FreeableNodeWithTypes;
    class NodeJsonElement;
    struct RepeatData;
}// namespace CHelper::Node

// FreeableNodeWithTypes 直接以其内部的 nodes 数组形式序列化（与旧版格式一致）
template<>
struct glz::to<glz::JSON, CHelper::Node::FreeableNodeWithTypes> {
    template<auto Opts>
    static void op(auto &&value, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
        serialize<JSON>::op<Opts>(value.nodes, ctx, b, ix);
    }
};

template<>
struct glz::to<glz::MSGPACK, CHelper::Node::FreeableNodeWithTypes> {
    template<auto Opts>
    static void op(auto &&value, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
        serialize<MSGPACK>::op<Opts>(value.nodes, ctx, b, ix);
    }
};

template<>
struct glz::from<glz::JSON, CHelper::Node::FreeableNodeWithTypes> {
    template<auto Opts>
    static void op(auto &&value, glz::is_context auto &&ctx, auto &&it, auto &&end) {
        parse<JSON>::op<Opts>(value.nodes, ctx, it, end);
    }
};

template<>
struct glz::from<glz::MSGPACK, CHelper::Node::FreeableNodeWithTypes> {
    template<auto Opts>
    static void op(auto &&value, uint8_t tag, glz::is_context auto &&ctx, auto &&it, auto &&end) {
        // tag 已由分发器消费，直接传递给 vector 读取
        from<MSGPACK, std::vector<CHelper::Node::NodeWithType>>::op<Opts>(value.nodes, tag, ctx, it, end);
    }
};

template<>
struct glz::to<CHelper::BinaryFormat, CHelper::Node::FreeableNodeWithTypes> {
    template<auto Opts>
    static void op(auto &&value, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
        serialize<CHelper::BinaryFormat>::template op<Opts>(value.nodes, ctx, b, ix);
    }
};

template<>
struct glz::from<CHelper::BinaryFormat, CHelper::Node::FreeableNodeWithTypes> {
    template<auto Opts>
    static void op(auto &&value, glz::is_context auto &&ctx, auto &&it, auto &&end) {
        parse<CHelper::BinaryFormat>::template op<Opts>(value.nodes, ctx, it, end);
    }
};

// 以下两个特化严格遵循旧版二进制布局：
// NodeJsonElement 旧格式直接写字符串（id 必有值，无 optional 存在标记）
template<>
struct glz::to<CHelper::BinaryFormat, CHelper::Node::NodeJsonElement> {
    template<auto Opts>
    static void op(auto &&value, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
        if (!value.id.has_value()) [[unlikely]] {
            ctx.error = glz::error_code::invalid_nullable_read;
            return;
        }
        serialize<CHelper::BinaryFormat>::template op<Opts>(value.id.value(), ctx, b, ix);
        serialize<CHelper::BinaryFormat>::template op<Opts>(value.nodes, ctx, b, ix);
        serialize<CHelper::BinaryFormat>::template op<Opts>(value.startNodeId, ctx, b, ix);
    }
};

template<>
struct glz::from<CHelper::BinaryFormat, CHelper::Node::NodeJsonElement> {
    template<auto Opts>
    static void op(auto &&value, glz::is_context auto &&ctx, auto &&it, auto &&end) {
        std::string id;
        parse<CHelper::BinaryFormat>::template op<Opts>(id, ctx, it, end);
        value.id = std::move(id);
        parse<CHelper::BinaryFormat>::template op<Opts>(value.nodes, ctx, it, end);
        parse<CHelper::BinaryFormat>::template op<Opts>(value.startNodeId, ctx, it, end);
    }
};

template<>
struct glz::meta<CHelper::Node::NodeJsonElement> {
    using T = CHelper::Node::NodeJsonElement;
    // JSON 键名与旧版一致：id / node / start
    static constexpr auto value = glz::object("id", &T::id, "node", &T::nodes, "start", &T::startNodeId);
};

template<>
struct glz::meta<CHelper::Node::RepeatData> {
    using T = CHelper::Node::RepeatData;
    static constexpr auto value = glz::object(&T::id, &T::breakNodes, &T::repeatNodes, &T::isEnd);
};

// ================= 模型类型 meta 定义 =================
template<>
struct glz::meta<CHelper::NormalId> {
    using T = CHelper::NormalId;
    static constexpr auto value = glz::object(&T::name, &T::description);
};

template<>
struct glz::meta<CHelper::NamespaceId> {
    using T = CHelper::NamespaceId;
    static constexpr auto value = glz::object(&T::name, &T::description, &T::idNamespace);
};

template<>
struct glz::meta<CHelper::ItemId> {
    using T = CHelper::ItemId;
    static constexpr auto value = glz::object(&T::name, &T::description, &T::idNamespace, &T::max, &T::descriptions);
};

template<>
struct glz::meta<CHelper::Manifest> {
    using T = CHelper::Manifest;
    static constexpr auto value =
            glz::object(&T::name, &T::description, &T::version, &T::versionType, &T::branch, &T::author,
                        &T::updateDate, &T::packId, &T::versionCode, &T::isBasicPack, &T::isDefault);
};

// ================= CPack 数据类型 meta 定义 =================
template<>
struct glz::meta<CHelper::NormalIdEntry> {
    using T = CHelper::NormalIdEntry;
    static constexpr auto value = glz::object(&T::id, &T::content);
};

template<>
struct glz::meta<CHelper::NamespaceIdEntry> {
    using T = CHelper::NamespaceIdEntry;
    static constexpr auto value = glz::object(&T::id, &T::content);
};

template<>
struct glz::meta<CHelper::BlockIdsEntry> {
    using T = CHelper::BlockIdsEntry;
    static constexpr auto value = glz::object(&T::id, &T::content);
};

template<>
struct glz::meta<CHelper::ItemIdsEntry> {
    using T = CHelper::ItemIdsEntry;
    static constexpr auto value = glz::object(&T::id, &T::content);
};

template<>
struct glz::meta<CHelper::IdEntry> {
    static constexpr std::string_view tag = "type";
    static constexpr auto ids = std::array{"normal", "namespace", "block", "item"};
};

template<>
struct glz::meta<CHelper::CPackJsonData> {
    using T = CHelper::CPackJsonData;
    static constexpr auto value = glz::object(&T::manifest, &T::id, &T::json, &T::repeat, &T::command);
};

template<>
struct glz::meta<CHelper::CPackData> {
    using T = CHelper::CPackData;
    static constexpr auto value = glz::object(&T::manifest, &T::normalIds, &T::namespaceIds, &T::itemIds, &T::blockIds, &T::jsonNodes, &T::repeatNodeData, &T::commands);
};

template<>
struct glz::meta<CHelper::Old2New::BlockFixEntry> {
    using T = CHelper::Old2New::BlockFixEntry;
    static constexpr auto value = glz::object(&T::name, &T::data, &T::newBlockId, &T::blockState);
};

// ================= NodePerCommand =================
namespace CHelper::Node {

    struct WrappedNodeWire {
        int32_t definition = -1;
        std::vector<uint32_t> next;
    };

    struct NodePerCommandWire {
        std::vector<std::u16string> name;
        std::optional<std::u16string> description;
        std::vector<std::u16string> syntax;
        glz::ordered_small_map<NodeWithType> node;
        // 预解析的节点图（仅 msgpack 格式包含；JSON 格式由 syntax 重建）
        std::optional<std::vector<WrappedNodeWire>> wrappedNodes;
        std::optional<std::vector<uint32_t>> startNodes;
    };

    // 由 syntax 字符串构建语法树（JSON 格式路径，与旧版逻辑一致）
    inline void buildNodePerCommandTrie(NodePerCommand &t) {
        //id map: token string -> node definition
        std::vector<std::pair<std::string_view, NodeWithType *>> idMap;
        for (auto &item: t.nodes.nodes) {
            auto *serializable = static_cast<NodeSerializable *>(item.data);
            if (!serializable->id.has_value()) {
                continue;
            }
            const auto &id = serializable->id.value();
            for (size_t start = 0, end; start < id.size(); start = end + 1) {
                const size_t findStart = (id[start] == '[' || id[start] == '<') ? id.find(id[start] == '[' ? ']' : '>', start) + 1 : start;
                end = std::min(id.find('|', findStart), id.size());
                if (end > start) {
                    idMap.emplace_back(std::string_view(id.data() + start, end - start), &item);
                }
            }
        }
        std::sort(idMap.begin(), idMap.end(), [](const auto &a, const auto &b) {
            return a.first.size() > b.first.size();
        });
        //flat trie: [0]=root, [i>0] maps to wrappedNodes[i-1]
        struct TrieNode {
            NodeWithType *definition = nullptr;
            std::vector<size_t> children;
            bool needsLf = false;
        };
        std::vector<TrieNode> trie(1);
        bool hasOptionalFirst = false;
        for (const auto &syntaxUtf16: t.syntax) {
            const std::string syntax = utf8::utf16to8(syntaxUtf16);
            size_t position = syntax.find(u' ');
            if (position == std::string::npos) {
                hasOptionalFirst = true;
                continue;
            }
            hasOptionalFirst |= position + 1 < syntax.size() && syntax[position + 1] == u'[';
            size_t current = 0;
            while (position < syntax.size()) {
                while (position < syntax.size() && syntax[position] == u' ') {
                    ++position;
                }
                if (position >= syntax.size()) {
                    break;
                }
                const size_t tokenStartPos = position;
                NodeWithType *definition = nullptr;
                for (auto &[tokenView, definitionView]: idMap) {
                    if (position + tokenView.size() <= syntax.size() &&
                        std::string_view(syntax.data() + position, tokenView.size()) == tokenView) {
                        definition = definitionView;
                        position += tokenView.size();
                        break;
                    }
                }
                if (!definition) [[unlikely]] {
                    throw std::runtime_error("unknown syntax token");
                }
                if (syntax[tokenStartPos] == u'[' && current != 0) {
                    trie[current].needsLf = true;
                }
                size_t childIndex = SIZE_MAX;
                for (const auto child: trie[current].children) {
                    if (trie[child].definition == definition) {
                        childIndex = child;
                        break;
                    }
                }
                if (childIndex == SIZE_MAX) {
                    trie.emplace_back(definition);
                    childIndex = trie.size() - 1;
                    trie[current].children.push_back(childIndex);
                }
                current = childIndex;
            }
            if (current) {
                trie[current].needsLf = true;
            }
        }
        //materialize wrappedNodes (trie[0] excluded)
        t.wrappedNodes.reserve(trie.size() - 1);
        for (size_t i = 1; i < trie.size(); ++i) {
            t.wrappedNodes.emplace_back(*trie[i].definition);
        }
        //connect nextNodes
        for (size_t i = 1; i < trie.size(); ++i) {
            auto &wrappedNode = t.wrappedNodes[i - 1];
            for (const auto child: trie[i].children) {
                wrappedNode.pushNextNode(&t.wrappedNodes[child - 1]);
            }
            if (trie[i].needsLf) {
                wrappedNode.pushNextNode(NodeLF::getInstance());
            }
        }
        //populate startNodes
        t.startNodes.clear();
        for (const auto child: trie[0].children) {
            t.startNodes.push_back(&t.wrappedNodes[child - 1]);
        }
        if (hasOptionalFirst) {
            t.startNodes.push_back(NodeLF::getInstance());
        }
    }

    // 由预解析的节点图构建（msgpack 格式路径，与旧版 from_binary 逻辑一致）
    inline void buildNodePerCommandGraph(NodePerCommand &t, const std::vector<WrappedNodeWire> &wrapped,
                                         const std::vector<uint32_t> &startIndices) {
        const size_t wrappedCount = wrapped.size();
        t.wrappedNodes.reserve(wrappedCount);
        for (size_t i = 0; i < wrappedCount; ++i) {
            const auto defIdx = wrapped[i].definition;
            if (defIdx < 0 || static_cast<size_t>(defIdx) >= t.nodes.nodes.size()) [[unlikely]] {
                throw std::runtime_error("invalid node definition index");
            }
            t.wrappedNodes.emplace_back(t.nodes.nodes[static_cast<size_t>(defIdx)]);
        }
        for (size_t i = 0; i < wrappedCount; ++i) {
            auto &wrappedNode = t.wrappedNodes[i];
            for (const auto targetIdx: wrapped[i].next) {
                if (targetIdx == UINT32_MAX) {
                    wrappedNode.pushNextNode(NodeLF::getInstance());
                } else {
                    if (targetIdx >= wrappedCount) [[unlikely]] {
                        throw std::runtime_error("invalid wrapped node index");
                    }
                    wrappedNode.pushNextNode(&t.wrappedNodes[targetIdx]);
                }
            }
        }
        t.startNodes.clear();
        t.startNodes.reserve(startIndices.size());
        for (const auto idx: startIndices) {
            if (idx == UINT32_MAX) {
                t.startNodes.push_back(NodeLF::getInstance());
            } else {
                if (idx >= wrappedCount) [[unlikely]] {
                    throw std::runtime_error("invalid start node index");
                }
                t.startNodes.push_back(&t.wrappedNodes[idx]);
            }
        }
    }

    inline void nodePerCommandFromWire(NodePerCommand &t, NodePerCommandWire &&wire) {
        t.name = std::move(wire.name);
        if (t.name.empty()) [[unlikely]] {
            throw std::runtime_error("command size cannot be zero");
        }
        t.description = std::move(wire.description);
        t.syntax = std::move(wire.syntax);
        t.nodes.nodes.reserve(wire.node.size());
        for (auto &[key, node]: wire.node) {
            // 键即节点 id，写回节点数据
            static_cast<NodeSerializable *>(node.data)->id = key;
            t.nodes.nodes.push_back(std::move(node));
        }
        if (wire.wrappedNodes.has_value()) {
            buildNodePerCommandGraph(t, wire.wrappedNodes.value(),
                                     wire.startNodes.value_or(std::vector<uint32_t>{}));
        } else {
            buildNodePerCommandTrie(t);
        }
    }

    inline void nodePerCommandToWire(const NodePerCommand &t, NodePerCommandWire &wire, const bool includeGraph) {
        wire.name = t.name;
        wire.description = t.description;
        wire.syntax = t.syntax;
        for (const auto &node: t.nodes.nodes) {
            const auto *serializable = static_cast<const NodeSerializable *>(node.data);
            if (serializable->id.has_value()) {
                wire.node.emplace(*serializable->id, node);
            }
        }
        if (includeGraph) {
            auto &wrapped = wire.wrappedNodes.emplace();
            wrapped.reserve(t.wrappedNodes.size());
            for (const auto &wrappedNode: t.wrappedNodes) {
                auto &item = wrapped.emplace_back();
                for (size_t i = 0; i < t.nodes.nodes.size(); ++i) {
                    if (t.nodes.nodes[i].data == wrappedNode.innerNode.data) {
                        item.definition = static_cast<int32_t>(i);
                        break;
                    }
                }
                for (const auto *next: wrappedNode.nextNodes) {
                    if (next == NodeLF::getInstance()) {
                        item.next.push_back(UINT32_MAX);
                    } else {
                        item.next.push_back(static_cast<uint32_t>(next - t.wrappedNodes.data()));
                    }
                }
            }
            auto &starts = wire.startNodes.emplace();
            starts.reserve(t.startNodes.size());
            for (const auto *start: t.startNodes) {
                if (start == NodeLF::getInstance()) {
                    starts.push_back(UINT32_MAX);
                } else {
                    starts.push_back(static_cast<uint32_t>(start - t.wrappedNodes.data()));
                }
            }
        }
    }
}// namespace CHelper::Node

template<>
struct glz::meta<CHelper::Node::WrappedNodeWire> {
    using T = CHelper::Node::WrappedNodeWire;
    static constexpr auto value = glz::object(&T::definition, &T::next);
};

template<>
struct glz::meta<CHelper::Node::NodePerCommandWire> {
    using T = CHelper::Node::NodePerCommandWire;
    static constexpr auto value = glz::object(&T::name, &T::description, &T::syntax, &T::node, &T::wrappedNodes, &T::startNodes);
};

namespace glz {
    template<>
    struct to<JSON, CHelper::Node::NodePerCommand> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
            CHelper::Node::NodePerCommandWire wire;
            CHelper::Node::nodePerCommandToWire(value, wire, false);
            serialize<JSON>::op<Opts>(wire, ctx, b, ix);
        }
    };

    template<>
    struct to<MSGPACK, CHelper::Node::NodePerCommand> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
            CHelper::Node::NodePerCommandWire wire;
            CHelper::Node::nodePerCommandToWire(value, wire, true);
            serialize<MSGPACK>::op<Opts>(wire, ctx, b, ix);
        }
    };

    template<>
    struct from<JSON, CHelper::Node::NodePerCommand> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&it, auto &&end) {
            CHelper::Node::NodePerCommandWire wire;
            parse<JSON>::op<Opts>(wire, ctx, it, end);
            if (bool(ctx.error)) return;
            CHelper::Node::nodePerCommandFromWire(value, std::move(wire));
        }
    };

    template<>
    struct from<MSGPACK, CHelper::Node::NodePerCommand> {
        template<auto Opts>
        static void op(auto &&value, uint8_t, glz::is_context auto &&ctx, auto &&it, auto &&end) {
            // tag 已被分发器消费，回退后解析 map 头
            --it;
            CHelper::Node::NodePerCommandWire wire;
            parse<MSGPACK>::op<Opts>(wire, ctx, it, end);
            if (bool(ctx.error)) return;
            CHelper::Node::nodePerCommandFromWire(value, std::move(wire));
        }
    };

    // 严格遵循旧版二进制布局：name, description, syntax, nodes(数组，元素含 id),
    // wrappedCount + [defIdx, nextCount, nextIndices...](UINT32_MAX 表示 LF),
    // startCount + [startIndices...]；无键名、无 optional 存在标记
    template<>
    struct to<CHelper::BinaryFormat, CHelper::Node::NodePerCommand> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
            serialize<CHelper::BinaryFormat>::template op<Opts>(value.name, ctx, b, ix);
            serialize<CHelper::BinaryFormat>::template op<Opts>(value.description, ctx, b, ix);
            serialize<CHelper::BinaryFormat>::template op<Opts>(value.syntax, ctx, b, ix);
            serialize<CHelper::BinaryFormat>::template op<Opts>(value.nodes, ctx, b, ix);
            const std::uint32_t wrappedCount = static_cast<std::uint32_t>(value.wrappedNodes.size());
            serialize<CHelper::BinaryFormat>::template op<Opts>(wrappedCount, ctx, b, ix);
            for (const auto &wrappedNode: value.wrappedNodes) {
                std::int32_t defIdx = -1;
                for (std::size_t i = 0; i < value.nodes.nodes.size(); ++i) {
                    if (value.nodes.nodes[i].data == wrappedNode.innerNode.data) {
                        defIdx = static_cast<std::int32_t>(i);
                        break;
                    }
                }
                serialize<CHelper::BinaryFormat>::template op<Opts>(defIdx, ctx, b, ix);
                const std::uint32_t nextCount = static_cast<std::uint32_t>(wrappedNode.nextNodes.size());
                serialize<CHelper::BinaryFormat>::template op<Opts>(nextCount, ctx, b, ix);
                for (const auto *next: wrappedNode.nextNodes) {
                    const std::uint32_t nextIdx = next == CHelper::Node::NodeLF::getInstance()
                                                          ? UINT32_MAX
                                                          : static_cast<std::uint32_t>(next - value.wrappedNodes.data());
                    serialize<CHelper::BinaryFormat>::template op<Opts>(nextIdx, ctx, b, ix);
                }
            }
            const std::uint32_t startCount = static_cast<std::uint32_t>(value.startNodes.size());
            serialize<CHelper::BinaryFormat>::template op<Opts>(startCount, ctx, b, ix);
            for (const auto *start: value.startNodes) {
                const std::uint32_t startIdx = start == CHelper::Node::NodeLF::getInstance()
                                                       ? UINT32_MAX
                                                       : static_cast<std::uint32_t>(start - value.wrappedNodes.data());
                serialize<CHelper::BinaryFormat>::template op<Opts>(startIdx, ctx, b, ix);
            }
        }
    };

    template<>
    struct from<CHelper::BinaryFormat, CHelper::Node::NodePerCommand> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&it, auto &&end) {
            parse<CHelper::BinaryFormat>::template op<Opts>(value.name, ctx, it, end);
            parse<CHelper::BinaryFormat>::template op<Opts>(value.description, ctx, it, end);
            parse<CHelper::BinaryFormat>::template op<Opts>(value.syntax, ctx, it, end);
            parse<CHelper::BinaryFormat>::template op<Opts>(value.nodes, ctx, it, end);
            std::uint32_t wrappedCount = 0;
            parse<CHelper::BinaryFormat>::template op<Opts>(wrappedCount, ctx, it, end);
            std::vector<CHelper::Node::WrappedNodeWire> wrapped(wrappedCount);
            for (auto &wrappedWire: wrapped) {
                std::int32_t defIdx = -1;
                parse<CHelper::BinaryFormat>::template op<Opts>(defIdx, ctx, it, end);
                wrappedWire.definition = defIdx;
                std::uint32_t nextCount = 0;
                parse<CHelper::BinaryFormat>::template op<Opts>(nextCount, ctx, it, end);
                wrappedWire.next.resize(nextCount);
                for (auto &nextIdx: wrappedWire.next) {
                    parse<CHelper::BinaryFormat>::template op<Opts>(nextIdx, ctx, it, end);
                }
            }
            std::uint32_t startCount = 0;
            parse<CHelper::BinaryFormat>::template op<Opts>(startCount, ctx, it, end);
            std::vector<std::uint32_t> startIndices(startCount);
            for (auto &startIdx: startIndices) {
                parse<CHelper::BinaryFormat>::template op<Opts>(startIdx, ctx, it, end);
            }
            CHelper::Node::buildNodePerCommandGraph(value, wrapped, startIndices);
        }
    };
}// namespace glz


// ================= BlockId 序列化（从 BlockId.h 移入） =================
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


// ================= Old2New 序列化 =================
namespace CHelper::Old2New {

#ifndef CHELPER_NO_FILESYSTEM
    inline BlockFixData blockFixDataFromJson(const std::filesystem::path &path) {
        std::vector<BlockFixEntry> entries;
        readJsonFromFile(entries, path);
        BlockFixData blockFixData;
        for (auto &entry: entries) {
            auto &dataValueToBlockState = blockFixData.try_emplace(std::move(entry.name)).first->second;
            dataValueToBlockState.insert_or_assign(entry.data,
                                                   std::make_pair(std::move(entry.newBlockId),
                                                                  std::move(entry.blockState)));
        }
        return blockFixData;
    }
#endif

    inline std::string blockFixDataToBinary(const BlockFixData &blockFixData) {
        std::string buffer;
        writeBinary(buffer, blockFixData);
        return buffer;
    }

    inline BlockFixData blockFixDataFromBinary(std::string_view buffer) {
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

    inline void CPack::writeJsonToDirectory(const std::filesystem::path &path) const {
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

    inline std::string CPack::toJson() const {
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

    inline void CPack::writeJsonToFile(const std::filesystem::path &path) const {
        writeJsonToFileWithCreateDirectory(path, toJson());
    }

    inline void CPack::writeBinToFile(const std::filesystem::path &path) const {
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
}// namespace CHelper
#endif

// ================= CPack 读取（唯一允许构建 CPack 的入口） =================
namespace CHelper::serialization {

#ifndef CHELPER_NO_FILESYSTEM
    inline std::unique_ptr<CPack> createCPackByDirectory(const std::filesystem::path &path) {
        Profile::push("start load CPack by DIRECTORY: {}", FORMAT_ARG(path.string()));
#if defined(CHelperDebug) && !defined(CHELPER_NO_FILESYSTEM)
        size_t stackSize = Profile::stack.size();
#endif
        auto cpack = std::unique_ptr<CPack>(new CPack());
        currentCreateStage = Node::NodeCreateStage::NONE;
        Profile::push("loading manifest");
        readJsonFromFile(cpack->manifest, path / "manifest.json");
        Profile::next("loading id data");
        for (const auto &file: std::filesystem::recursive_directory_iterator(path / "id")) {
            Profile::next(R"(loading id data in path "{}")", FORMAT_ARG(file.path().string()));
            IdEntry entry;
            readJsonFromFile(entry, file.path());
            cpack->applyId(entry);
        }
        Profile::next("loading json data");
        currentCreateStage = Node::NodeCreateStage::JSON_NODE;
        for (const auto &file: std::filesystem::recursive_directory_iterator(path / "json")) {
            Profile::next(R"(loading json data in path "{}")", FORMAT_ARG(file.path().string()));
            Node::NodeJsonElement item;
            readJsonFromFile(item, file.path());
            cpack->applyJson(std::move(item));
        }
        Profile::next("loading repeat data");
        currentCreateStage = Node::NodeCreateStage::REPEAT_NODE;
        for (const auto &file: std::filesystem::recursive_directory_iterator(path / "repeat")) {
            Profile::next(R"(loading repeat data in path "{}")", FORMAT_ARG(file.path().string()));
            Node::RepeatData item;
            readJsonFromFile(item, file.path());
            cpack->applyRepeat(std::move(item));
        }
        Profile::next("loading commands");
        currentCreateStage = Node::NodeCreateStage::COMMAND_PARAM_NODE;
        for (const auto &file: std::filesystem::recursive_directory_iterator(path / "command")) {
            Profile::next(R"(loading command in path "{}")", FORMAT_ARG(file.path().string()));
            Node::NodePerCommand item;
            readJsonFromFile(item, file.path());
            cpack->applyCommand(std::move(item));
        }
        Profile::next("init cpack");
        currentCreateStage = Node::NodeCreateStage::NONE;
        cpack->afterApply();
        Profile::pop();
#if defined(CHelperDebug) && !defined(CHELPER_NO_FILESYSTEM)
        if (Profile::stack.size() != stackSize) [[unlikely]] {
            SPDLOG_WARN("error profile stack after loading cpack");
        }
#endif
        Profile::pop();
        return cpack;
    }

    inline std::unique_ptr<CPack> createCPackByJsonFile(const std::filesystem::path &cpackPath) {
        return createCPackByJson(readFileToString(cpackPath));
    }
#endif

    inline std::unique_ptr<CPack> createCPackByJson(const std::string &json) {
        Profile::push("start load CPack by JSON");
#if defined(CHelperDebug) && !defined(CHELPER_NO_FILESYSTEM)
        size_t stackSize = Profile::stack.size();
#endif
        // 单文件格式一次性读取所有节点，JSON_NODE 阶段覆盖全部可序列化的节点类型
        currentCreateStage = Node::NodeCreateStage::JSON_NODE;
        CPackJsonData data;
        readJson(data, json);
        auto cpack = std::unique_ptr<CPack>(new CPack());
        currentCreateStage = Node::NodeCreateStage::NONE;
        Profile::push("loading manifest");
        cpack->manifest = std::move(data.manifest);
        Profile::next("loading id data");
        for (const auto &entry: data.id) {
            cpack->applyId(entry);
        }
        Profile::next("loading json data");
        currentCreateStage = Node::NodeCreateStage::JSON_NODE;
        for (auto &item: data.json) {
            cpack->applyJson(std::move(item));
        }
        Profile::next("loading repeat data");
        currentCreateStage = Node::NodeCreateStage::REPEAT_NODE;
        for (auto &item: data.repeat) {
            cpack->applyRepeat(std::move(item));
        }
        Profile::next("loading command data");
        currentCreateStage = Node::NodeCreateStage::COMMAND_PARAM_NODE;
        for (auto &item: data.command) {
            cpack->applyCommand(std::move(item));
        }
        Profile::next("init cpack");
        currentCreateStage = Node::NodeCreateStage::NONE;
        cpack->afterApply();
        Profile::pop();
#if defined(CHelperDebug) && !defined(CHELPER_NO_FILESYSTEM)
        if (Profile::stack.size() != stackSize) [[unlikely]] {
            SPDLOG_WARN("error profile stack after loading cpack");
        }
#endif
        Profile::pop();
        return cpack;
    }

    inline std::unique_ptr<CPack> createCPackByBinary(std::string_view data) {
        Profile::push("start load CPack by binary");
#if defined(CHelperDebug) && !defined(CHELPER_NO_FILESYSTEM)
        size_t stackSize = Profile::stack.size();
#endif
        // 二进制格式一次性读取所有节点，JSON_NODE 阶段覆盖全部可序列化的节点类型
        currentCreateStage = Node::NodeCreateStage::JSON_NODE;
        CPackData cpackData;
        readBinary(cpackData, data);
        auto cpack = std::unique_ptr<CPack>(new CPack());
        currentCreateStage = Node::NodeCreateStage::NONE;
        Profile::push("loading manifest");
        cpack->manifest = std::move(cpackData.manifest);
        Profile::next("loading normal id data");
        cpack->normalIds = std::move(cpackData.normalIds);
        Profile::next("loading namespace id data");
        cpack->namespaceIds = std::move(cpackData.namespaceIds);
        Profile::next("loading item id data");
        cpack->itemIds = std::move(cpackData.itemIds);
        Profile::next("loading block id data");
        cpack->blockIds = std::move(cpackData.blockIds);
        Profile::next("loading json data");
        currentCreateStage = Node::NodeCreateStage::JSON_NODE;
        cpack->jsonNodes = std::move(cpackData.jsonNodes);
        Profile::next("loading repeat data");
        currentCreateStage = Node::NodeCreateStage::REPEAT_NODE;
        cpack->repeatNodeData = std::move(cpackData.repeatNodeData);
        Profile::next("loading command data");
        currentCreateStage = Node::NodeCreateStage::COMMAND_PARAM_NODE;
        cpack->commands = std::move(cpackData.commands);
        Profile::next("init cpack");
        currentCreateStage = Node::NodeCreateStage::NONE;
        cpack->afterApply();
        Profile::pop();
#if defined(CHelperDebug) && !defined(CHELPER_NO_FILESYSTEM)
        if (Profile::stack.size() != stackSize) [[unlikely]] {
            SPDLOG_WARN("error profile stack after loading cpack");
        }
#endif
        Profile::pop();
        return cpack;
    }
}// namespace CHelper::serialization

#endif//CHELPER_SERIALIZATION_H
