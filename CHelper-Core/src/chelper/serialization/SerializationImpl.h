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

/**
 * Internal serialization implementation: glaze format adapters, node read/write
 * machinery and reflection metas.  Header-safe by construction (templates, inline
 * functions and class-template specializations only).
 *
 * Included by Serialization.cpp, which adds the single-translation-unit CPack and
 * Old2New entry points, and by in-tree tests that exercise the low-level format
 * adapters directly.
 */

#include <chelper/node/CommandNode.h>
#include <chelper/node/NodeType.h>
#include <chelper/old2new/Old2New.h>
#include <chelper/resources/CPack.h>
#include <chelper/serialization/BinaryFormat.h>
#include <chelper/serialization/IO.h>
#include <glaze/containers/ordered_small_map.hpp>
#include <utility>

// ================= 反序列化上下文 =================
namespace CHelper {

    /**
     * 节点反序列化上下文：携带当前 CPack 加载阶段，用于限制哪些节点类型允许被反序列化。
     * 阶段跟随上下文在调用链上传递，而不是放在全局变量里：
     * 全局变量会被并发创建 CPack 的线程互相覆盖，导致节点被误判为非法类型。
     */
    struct NodeReadContext : glz::context {
        Node::NodeCreateStage::NodeCreateStage createStage = Node::NodeCreateStage::JSON_NODE;
        std::shared_ptr<CPackMemoryResource> cpackMemory;
    };

    /**
     * 取出上下文携带的加载阶段。
     * 未携带阶段的普通上下文（如下游直接用 glz::read_json 读取节点数据）不限制节点类型，
     * 与阶段为 JSON_NODE（其覆盖全部可序列化的节点类型）等价。
     */
    [[nodiscard]] inline Node::NodeCreateStage::NodeCreateStage getCreateStage(const glz::is_context auto &ctx) {
        if constexpr (requires { ctx.createStage; }) {
            return ctx.createStage;
        } else {
            return Node::NodeCreateStage::JSON_NODE;
        }
    }

    // 当前加载阶段是否允许创建该节点类型（nodeCreateStage 为空 = 该类型不可反序列化）
    template<Node::NodeTypeId::NodeTypeId Id>
    [[nodiscard]] bool nodeCreateStageAllows(const glz::is_context auto &ctx) {
        const auto &stages = Node::NodeTypeDetail<Id>::nodeCreateStage;
        return std::find(stages.begin(), stages.end(), getCreateStage(ctx)) != stages.end();
    }
}// namespace CHelper

// ================= std::u16string / char16_t 支持（JSON / MSGPACK，UTF-8 承载） =================
// 转换函数必须写全限定名 ::CHelper::U16Conv::。这里在 glz 命名空间内，
// MSVC 的非限定查找不会继续向外找到 CHelper，会直接报 identifier not found。
// 项目只在 JSON / MSGPACK / 自定义二进制（见 BinaryFormat.h）中承载 u16string。
namespace glz {
    template<class Traits, class Alloc>
    struct from<JSON, std::basic_string<char16_t, Traits, Alloc>> {
        template<auto Opts>
        static void op(std::basic_string<char16_t, Traits, Alloc> &value,
                       glz::is_context auto &&ctx,
                       auto &&it,
                       auto &&end) {
            std::string utf8;
            from<JSON, std::string>::op<Opts>(utf8, ctx, it, end);
            ::CHelper::U16Conv::convertToU16(utf8, value);
        }
    };

    template<class Traits, class Alloc>
    struct from<MSGPACK, std::basic_string<char16_t, Traits, Alloc>> {
        template<auto Opts>
        static void op(std::basic_string<char16_t, Traits, Alloc> &value,
                       std::uint8_t tag,
                       glz::is_context auto &&ctx,
                       auto &&it,
                       auto &&end) {
            // tag 已由分发器消费，直接交给 std::string 读取
            std::string utf8;
            from<MSGPACK, std::string>::op<Opts>(utf8, tag, ctx, it, end);
            ::CHelper::U16Conv::convertToU16(utf8, value);
        }
    };

    template<class Traits, class Alloc>
    struct to<JSON, std::basic_string<char16_t, Traits, Alloc>> {
        template<auto Opts>
        static void op(const std::basic_string<char16_t, Traits, Alloc> &value,
                       glz::is_context auto &&ctx,
                       auto &&b,
                       auto &&ix) noexcept {
            serialize<JSON>::op<Opts>(utf8::utf16to8(value), ctx, b, ix);
        }
    };

    template<class Traits, class Alloc>
    struct to<MSGPACK, std::basic_string<char16_t, Traits, Alloc>> {
        template<auto Opts>
        static void op(const std::basic_string<char16_t, Traits, Alloc> &value,
                       glz::is_context auto &&ctx,
                       auto &&b,
                       auto &&ix) noexcept {
            serialize<MSGPACK>::op<Opts>(utf8::utf16to8(value), ctx, b, ix);
        }
    };

    // 修复 glaze msgpack 读取器对 nullable 类型直接调用 emplace() 的问题
    // （v8.3.0 起，shared_ptr / unique_ptr 没有 emplace，见官方 msgpack_test 中被跳过的用例）
    template<class T>
    struct from<MSGPACK, std::shared_ptr<T>> {
        template<auto Opts>
        static void op(std::shared_ptr<T> &value, std::uint8_t tag, glz::is_context auto &&ctx, auto &&it, auto &&end) {
            if (tag == msgpack::nil) {
                value.reset();
                return;
            }
            if (!value) {
                if constexpr (requires { ctx.cpackMemory; }) {
                    if (ctx.cpackMemory) {
                        value = CHelper::allocateShared<T>(ctx.cpackMemory);
                    } else {
                        value = std::make_shared<T>();
                    }
                } else {
                    value = std::make_shared<T>();
                }
            }
            from<MSGPACK, T>::template op<Opts>(*value, tag, ctx, it, end);
        }
    };

    template<class T>
    struct from<MSGPACK, std::unique_ptr<T>> {
        template<auto Opts>
        static void op(std::unique_ptr<T> &value, std::uint8_t tag, glz::is_context auto &&ctx, auto &&it, auto &&end) {
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

    // char16_t（单字符）按长度为 1 的字符串读写
    template<>
    struct from<JSON, char16_t> {
        template<auto Opts>
        static void op(char16_t &value, glz::is_context auto &&ctx, auto &&it, auto &&end) {
            std::u16string text;
            parse<JSON>::template op<Opts>(text, ctx, it, end);
            if (!bool(ctx.error) && text.size() == 1) value = text.front();
            else if (!bool(ctx.error))
                ctx.error = glz::error_code::syntax_error;
        }
    };

    template<>
    struct to<JSON, char16_t> {
        template<auto Opts>
        static void op(const char16_t &value, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
            serialize<JSON>::template op<Opts>(std::u16string(1, value), ctx, b, ix);
        }
    };

    template<>
    struct from<MSGPACK, char16_t> {
        template<auto Opts>
        static void op(char16_t &value, std::uint8_t tag, glz::is_context auto &&ctx, auto &&it, auto &&end) {
            std::u16string text;
            from<MSGPACK, std::u16string>::template op<Opts>(text, tag, ctx, it, end);
            if (!bool(ctx.error) && text.size() == 1) value = text.front();
            else if (!bool(ctx.error))
                ctx.error = glz::error_code::syntax_error;
        }
    };

    template<>
    struct to<MSGPACK, char16_t> {
        template<auto Opts>
        static void op(const char16_t &value, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
            serialize<MSGPACK>::template op<Opts>(std::u16string(1, value), ctx, b, ix);
        }
    };
}// namespace glz

// ================= 节点序列化 =================

namespace CHelper::Node {
    //可序列化的资源节点（JSON/MSGPACK 中以 "type" 字段标识的对象节点），
    //不含 WRAPPED / LF / PER_COMMAND / JSON_ELEMENT / ANY / ENTRY 等只存在于运行期的类型
    using JsonSerializableNodeTypes = Meta::TypeList<
            NodeBlock, NodeBoolean, NodeCommand, NodeCommandName, NodeFloat, NodeInteger, NodeIntegerWithUnit, NodeItem,
            NodeJson, NodeJsonBoolean, NodeJsonFloat, NodeJsonInteger, NodeJsonList, NodeJsonNull, NodeJsonEntry,
            NodeJsonObject, NodeJsonString, NodeNamespaceId, NodeNormalId, NodePosition, NodeRange, NodeRelativeFloat,
            NodeRepeat, NodeString, NodeTargetSelector, NodeText>;

    //语法节点：资源中以节点表 id 相互引用，序列化时按引用写出
    using GrammarNodeTypes = Meta::TypeList<NodeAnd, NodeOr, NodeList, NodeOptional, NodeSingleSymbol, NodeEqualEntry>;
}// namespace CHelper::Node

// 节点类型的字段声明只存在于 glz::meta 一处（键名与成员名一致，和旧版 CODEC_REGISTER_JSON_KEY 相同），
// JSON/MSGPACK 的写出键、二进制的紧凑成员顺序都由它推导；键名统一显式写出，不依赖成员指针取名。
// glz::meta 特化须在全局作用域，使用全限定名
template<>
struct glz::meta<CHelper::Node::NodeBlock> {
    using T = CHelper::Node::NodeBlock;
    static constexpr auto value = glz::object("id", &T::id, "brief", &T::brief, "description", &T::description,
                                              "isMustAfterSpace", &T::isMustAfterSpace, "nodeBlockType", &T::nodeBlockType);
};
template<>
struct glz::meta<CHelper::Node::NodeBoolean> {
    using T = CHelper::Node::NodeBoolean;
    static constexpr auto value = glz::object("id", &T::id, "brief", &T::brief, "description", &T::description,
                                              "isMustAfterSpace", &T::isMustAfterSpace, "descriptionTrue", &T::descriptionTrue,
                                              "descriptionFalse", &T::descriptionFalse);
};
template<>
struct glz::meta<CHelper::Node::NodeCommand> {
    using T = CHelper::Node::NodeCommand;
    static constexpr auto value = glz::object("id", &T::id, "brief", &T::brief, "description", &T::description,
                                              "isMustAfterSpace", &T::isMustAfterSpace);
};
template<>
struct glz::meta<CHelper::Node::NodeCommandName> {
    using T = CHelper::Node::NodeCommandName;
    static constexpr auto value = glz::object("id", &T::id, "brief", &T::brief, "description", &T::description,
                                              "isMustAfterSpace", &T::isMustAfterSpace);
};
template<>
struct glz::meta<CHelper::Node::NodeFloat> {
    using T = CHelper::Node::NodeFloat;
    static constexpr auto value = glz::object("id", &T::id, "brief", &T::brief, "description", &T::description,
                                              "isMustAfterSpace", &T::isMustAfterSpace, "min", &T::min, "max", &T::max);
};
template<>
struct glz::meta<CHelper::Node::NodeInteger> {
    using T = CHelper::Node::NodeInteger;
    static constexpr auto value = glz::object("id", &T::id, "brief", &T::brief, "description", &T::description,
                                              "isMustAfterSpace", &T::isMustAfterSpace, "min", &T::min, "max", &T::max);
};
template<>
struct glz::meta<CHelper::Node::NodeIntegerWithUnit> {
    using T = CHelper::Node::NodeIntegerWithUnit;
    static constexpr auto value = glz::object("id", &T::id, "brief", &T::brief, "description", &T::description,
                                              "isMustAfterSpace", &T::isMustAfterSpace, "units", &T::units);
};
template<>
struct glz::meta<CHelper::Node::NodeItem> {
    using T = CHelper::Node::NodeItem;
    static constexpr auto value = glz::object("id", &T::id, "brief", &T::brief, "description", &T::description,
                                              "isMustAfterSpace", &T::isMustAfterSpace, "nodeItemType", &T::nodeItemType);
};
template<>
struct glz::meta<CHelper::Node::NodeJson> {
    using T = CHelper::Node::NodeJson;
    static constexpr auto value = glz::object("id", &T::id, "brief", &T::brief, "description", &T::description,
                                              "isMustAfterSpace", &T::isMustAfterSpace, "key", &T::key);
};
template<>
struct glz::meta<CHelper::Node::NodeJsonBoolean> {
    using T = CHelper::Node::NodeJsonBoolean;
    static constexpr auto value = glz::object("id", &T::id, "brief", &T::brief, "description", &T::description,
                                              "isMustAfterSpace", &T::isMustAfterSpace, "descriptionTrue", &T::descriptionTrue,
                                              "descriptionFalse", &T::descriptionFalse);
};
template<>
struct glz::meta<CHelper::Node::NodeJsonFloat> {
    using T = CHelper::Node::NodeJsonFloat;
    static constexpr auto value = glz::object("id", &T::id, "brief", &T::brief, "description", &T::description,
                                              "isMustAfterSpace", &T::isMustAfterSpace, "min", &T::min, "max", &T::max);
};
template<>
struct glz::meta<CHelper::Node::NodeJsonInteger> {
    using T = CHelper::Node::NodeJsonInteger;
    static constexpr auto value = glz::object("id", &T::id, "brief", &T::brief, "description", &T::description,
                                              "isMustAfterSpace", &T::isMustAfterSpace, "min", &T::min, "max", &T::max);
};
template<>
struct glz::meta<CHelper::Node::NodeJsonList> {
    using T = CHelper::Node::NodeJsonList;
    static constexpr auto value = glz::object("id", &T::id, "brief", &T::brief, "description", &T::description,
                                              "isMustAfterSpace", &T::isMustAfterSpace, "data", &T::data);
};
template<>
struct glz::meta<CHelper::Node::NodeJsonNull> {
    using T = CHelper::Node::NodeJsonNull;
    static constexpr auto value = glz::object("id", &T::id, "brief", &T::brief, "description", &T::description,
                                              "isMustAfterSpace", &T::isMustAfterSpace);
};
template<>
struct glz::meta<CHelper::Node::NodeJsonEntry> {
    using T = CHelper::Node::NodeJsonEntry;
    static constexpr auto value = glz::object("id", &T::id, "brief", &T::brief, "description", &T::description,
                                              "isMustAfterSpace", &T::isMustAfterSpace, "key", &T::key, "value", &T::value);
};
template<>
struct glz::meta<CHelper::Node::NodeJsonObject> {
    using T = CHelper::Node::NodeJsonObject;
    static constexpr auto value = glz::object("id", &T::id, "brief", &T::brief, "description", &T::description,
                                              "isMustAfterSpace", &T::isMustAfterSpace, "data", &T::data);
};
template<>
struct glz::meta<CHelper::Node::NodeJsonString> {
    using T = CHelper::Node::NodeJsonString;
    static constexpr auto value = glz::object("id", &T::id, "brief", &T::brief, "description", &T::description,
                                              "isMustAfterSpace", &T::isMustAfterSpace, "data", &T::data);
};
template<>
struct glz::meta<CHelper::Node::NodeNamespaceId> {
    using T = CHelper::Node::NodeNamespaceId;
    static constexpr auto value = glz::object("id", &T::id, "brief", &T::brief, "description", &T::description,
                                              "isMustAfterSpace", &T::isMustAfterSpace, "key", &T::key, "ignoreError",
                                              &T::ignoreError, "contents", &T::contents);
};
template<>
struct glz::meta<CHelper::Node::NodeNormalId> {
    using T = CHelper::Node::NodeNormalId;
    static constexpr auto value = glz::object("id", &T::id, "brief", &T::brief, "description", &T::description,
                                              "isMustAfterSpace", &T::isMustAfterSpace, "key", &T::key, "ignoreError",
                                              &T::ignoreError, "contents", &T::contents);
};
template<>
struct glz::meta<CHelper::Node::NodePosition> {
    using T = CHelper::Node::NodePosition;
    static constexpr auto value = glz::object("id", &T::id, "brief", &T::brief, "description", &T::description,
                                              "isMustAfterSpace", &T::isMustAfterSpace);
};
template<>
struct glz::meta<CHelper::Node::NodeRange> {
    using T = CHelper::Node::NodeRange;
    static constexpr auto value = glz::object("id", &T::id, "brief", &T::brief, "description", &T::description,
                                              "isMustAfterSpace", &T::isMustAfterSpace);
};
template<>
struct glz::meta<CHelper::Node::NodeRelativeFloat> {
    using T = CHelper::Node::NodeRelativeFloat;
    static constexpr auto value = glz::object("id", &T::id, "brief", &T::brief, "description", &T::description,
                                              "isMustAfterSpace", &T::isMustAfterSpace, "canUseCaretNotation",
                                              &T::canUseCaretNotation);
};
template<>
struct glz::meta<CHelper::Node::NodeRepeat> {
    using T = CHelper::Node::NodeRepeat;
    static constexpr auto value = glz::object("id", &T::id, "brief", &T::brief, "description", &T::description,
                                              "isMustAfterSpace", &T::isMustAfterSpace, "key", &T::key);
};
template<>
struct glz::meta<CHelper::Node::NodeString> {
    using T = CHelper::Node::NodeString;
    static constexpr auto value = glz::object("id", &T::id, "brief", &T::brief, "description", &T::description,
                                              "isMustAfterSpace", &T::isMustAfterSpace, "allowMissingString",
                                              &T::allowMissingString, "canContainSpace", &T::canContainSpace, "ignoreLater",
                                              &T::ignoreLater);
};
template<>
struct glz::meta<CHelper::Node::NodeTargetSelector> {
    using T = CHelper::Node::NodeTargetSelector;
    static constexpr auto value = glz::object("id", &T::id, "brief", &T::brief, "description", &T::description,
                                              "isMustAfterSpace", &T::isMustAfterSpace, "isMustPlayer", &T::isMustPlayer,
                                              "isMustNPC", &T::isMustNPC, "isOnlyOne", &T::isOnlyOne, "isWildcard", &T::isWildcard);
};
template<>
struct glz::meta<CHelper::Node::NodeText> {
    using T = CHelper::Node::NodeText;
    static constexpr auto value = glz::object("id", &T::id, "brief", &T::brief, "description", &T::description,
                                              "isMustAfterSpace", &T::isMustAfterSpace, "data", &T::data);
};

template<>
struct glz::meta<CHelper::Node::NodeAnd> {
    using T = CHelper::Node::NodeAnd;
    static constexpr auto value = glz::object("id", &T::id, "nodes", &T::childNodeIds);
};
template<>
struct glz::meta<CHelper::Node::NodeOr> {
    using T = CHelper::Node::NodeOr;
    static constexpr auto value = glz::object("id", &T::id, "nodes", &T::childNodeIds, "isAttachToEnd", &T::isAttachToEnd,
                                              "isUseFirst", &T::isUseFirst, "noSuggestion", &T::noSuggestion);
};
template<>
struct glz::meta<CHelper::Node::NodeList> {
    using T = CHelper::Node::NodeList;
    static constexpr auto value = glz::object("id", &T::id, "left", &T::nodeLeftId, "element", &T::nodeElementId,
                                              "separator", &T::nodeSeparatorId, "right", &T::nodeRightId);
};
template<>
struct glz::meta<CHelper::Node::NodeOptional> {
    using T = CHelper::Node::NodeOptional;
    static constexpr auto value = glz::object("id", &T::id, "node", &T::optionalNodeId);
};
template<>
struct glz::meta<CHelper::Node::NodeSingleSymbol> {
    using T = CHelper::Node::NodeSingleSymbol;
    static constexpr auto value = glz::object("id", &T::id, "symbol", &T::symbol, "description", &T::description,
                                              "isAddSpace", &T::isAddSpace);
};
template<>
struct glz::meta<CHelper::Node::EqualData> {
    using T = CHelper::Node::EqualData;
    static constexpr auto value = glz::object("name", &T::name, "description", &T::description,
                                              "canUseNotEqual", &T::canUseNotEqual, "value", &T::valueNodeId);
};
template<>
struct glz::meta<CHelper::Node::NodeEqualEntry> {
    using T = CHelper::Node::NodeEqualEntry;
    static constexpr auto value = glz::object("id", &T::id, "values", &T::equalDatas);
};

namespace CHelper::detail {
    // 节点写出视图的成员转发：按节点 glz::meta 的成员指针取底层节点的成员
    template<auto Member>
    struct NodeMemberForward {
        template<class View>
        constexpr decltype(auto) operator()(const View &self) const {
            return std::invoke(Member, *self.node);
        }
    };

    // 节点写出视图：包装节点引用。meta 由节点 glz::meta 程序化生成：
    // "type"（节点类型名）置于首位，其余键/成员从节点 meta 推导。
    // 仅用于写出——读取直接进入节点本身，从而避开 msgpack 读取端对 lambda 成员的限制
    template<class NodeType>
    struct NodeWriteView {
        const NodeType *node;
    };

    // 视图 meta 条目：I==0 → "type" 键；I==1 → 类型名 lambda；
    // 其后每两项对应一个节点成员（键, 转发 functor），键与成员指针取自 glz::reflect
    //（同时兼容无键 meta——键名由成员指针推导——与手写带键 meta）
    template<class NodeType, std::size_t I>
    constexpr auto viewMetaEntry() {
        using R = glz::reflect<NodeType>;
        if constexpr (I == 0) {
            return glz::sv{"type"};
        } else if constexpr (I == 1) {
            return [](const NodeWriteView<NodeType> &) constexpr { return Node::NodeTypeDetail<NodeType::nodeTypeId>::name; };
        } else if constexpr (I % 2 == 0) {
            return R::keys[(I - 2) / 2];
        } else {
            return NodeMemberForward<glz::get<(I - 2) / 2>(R::values)>{};
        }
    }

    template<class NodeType>
    constexpr auto makeNodeWriteViewMeta() {
        constexpr auto memberCount = glz::reflect<NodeType>::size;
        return [&]<std::size_t... Is>(std::index_sequence<Is...>) constexpr {
            return glz::detail::Object{glz::tuple{viewMetaEntry<NodeType, Is>()...}};
        }(std::make_index_sequence<2 * memberCount + 2>{});
    }
}// namespace CHelper::detail

template<class NodeType>
struct glz::meta<CHelper::detail::NodeWriteView<NodeType>> {
    static constexpr auto value = CHelper::detail::makeNodeWriteViewMeta<NodeType>();
};

namespace CHelper {
    // 按具体节点类型写出节点对象（由 Node::dispatchNodeType 分派）。
    // JSON/MSGPACK 经写出视图自带 "type" 首键；二进制由 writeNodeWithType 先写 uint8 类型 ID，
    // 成员按 glz::meta 顺序紧凑排列（无键名）。
    // 二进制格式非自描述，修改 glz::meta 字段列表后已分发的 .cpack 需要用资源生成器重新生成
    template<class NodeType, std::uint32_t Fmt, auto Opts, class Ctx, class B>
    void writeNodeObjectValue(const Node::NodeWithType &t, Ctx &ctx, B &b, std::size_t &ix) {
        static_assert(std::is_same_v<NodeType, typename Node::NodeTypeDetail<NodeType::nodeTypeId>::Type>);
        const auto &n = *static_cast<const NodeType *>(t.data);
        if constexpr (Fmt == CHelper::BinaryFormat) {
            glz::serialize<CHelper::BinaryFormat>::template op<Opts>(n, ctx, b, ix);
        } else {
            detail::NodeWriteView<NodeType> view{&n};
            glz::serialize<Fmt>::template op<Opts>(view, ctx, b, ix);
        }
    }


    // 把节点对象写入缓冲区。用 functor 承载分派体，而不是泛型 lambda：
    // MSVC 对泛型 lambda 体的内联决策明显更差，基准实测写出耗时回退约 15%
    template<std::uint32_t Fmt, auto Opts, class Ctx, class B>
    struct WriteNodeValueFn {
        const Node::NodeWithType &t;
        Ctx &ctx;
        B &b;
        std::size_t &ix;

        template<class NodeType>
        void operator()() const {
            if constexpr (Meta::typeListContains<NodeType, Node::GrammarNodeTypes> ||
                          Meta::typeListContains<NodeType, Node::JsonSerializableNodeTypes>) {
                writeNodeObjectValue<NodeType, Fmt, Opts>(t, ctx, b, ix);
            } else if constexpr (Fmt == CHelper::BinaryFormat) {
                //运行期节点不会出现在资源数据里，写出时类型只可能来自内存中的合法节点
                std::unreachable();
            } else {
                //运行期节点（WRAPPED / LF / PER_COMMAND 等）不作为资源对象写出
                ctx.error = glz::error_code::no_matching_variant_type;
            }
        }
    };

    // 节点写出总入口：二进制先写 uint8 类型 ID；JSON/MSGPACK 的类型名由节点对象自带的 "type" 键承载。
    // 三种格式的成员键名/顺序都由 glz::meta 推导
    template<std::uint32_t Fmt, auto Opts, class Ctx, class B>
    inline void writeNodeWithType(const Node::NodeWithType &t, Ctx &ctx, B &b, std::size_t &ix) {
        if constexpr (Fmt == CHelper::BinaryFormat) {
            const std::uint8_t typeId = static_cast<std::uint8_t>(t.nodeTypeId);
            glz::serialize<CHelper::BinaryFormat>::template op<Opts>(typeId, ctx, b, ix);
        }
        Node::dispatchNodeType(
                t.nodeTypeId,
                WriteNodeValueFn<Fmt, Opts, Ctx, B>{t, ctx, b, ix},
                [&] {
                    if constexpr (Fmt == CHelper::BinaryFormat) {
                        std::unreachable();
                    } else {
                        ctx.error = glz::error_code::no_matching_variant_type;
                    }
                });
    }
}// namespace CHelper

// ================= JSON / MessagePack 通用解析辅助 =================
namespace CHelper {

    // 解析 msgpack map 头（fixmap / map16 / map32），返回键值对数量；
    // 数据不足或不是 map 头时返回 nullopt（不设置 ctx.error，由调用方决定报错方式）
    inline std::optional<std::uint32_t> readMsgpackMapSize(auto &it, const auto &end) {
        if (it >= end) {
            return std::nullopt;
        }
        const std::uint8_t tag = static_cast<std::uint8_t>(*it++);
        if (tag >= 0x80 && tag <= 0x8f) {
            return static_cast<std::uint32_t>(tag & 0x0f);
        }
        if (tag == 0xde) {
            if (end - it < 2) {
                return std::nullopt;
            }
            const std::uint32_t size = (static_cast<std::uint32_t>(static_cast<std::uint8_t>(it[0])) << 8) | static_cast<std::uint8_t>(it[1]);
            it += 2;
            return size;
        }
        if (tag == 0xdf) {
            if (end - it < 4) {
                return std::nullopt;
            }
            const std::uint32_t size = (static_cast<std::uint32_t>(static_cast<std::uint8_t>(it[0])) << 24) |
                                       (static_cast<std::uint32_t>(static_cast<std::uint8_t>(it[1])) << 16) |
                                       (static_cast<std::uint32_t>(static_cast<std::uint8_t>(it[2])) << 8) | static_cast<std::uint8_t>(it[3]);
            it += 4;
            return size;
        }
        return std::nullopt;
    }

    template<std::uint32_t Fmt>
    inline bool peekString(std::string_view &result, auto &it, const auto &end);

    // 逐成员遍历 JSON 对象 / msgpack map（f 以 (key, ctx, it, end) 回调处理每个值）。
    // 键以 string_view 传递：无转义时直接指向缓冲区（零拷贝），含转义或格式异常时才解码到 std::string
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
                std::string decoded;
                std::string_view key;
                if (!peekString<Fmt>(key, it, end)) {
                    glz::parse<glz::JSON>::op<Opts>(decoded, ctx, it, end);
                    if (bool(ctx.error)) return;
                    key = decoded;
                }
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
            const auto size = readMsgpackMapSize(it, end);
            if (!size.has_value()) [[unlikely]] {
                ctx.error = glz::error_code::syntax_error;
                return;
            }
            for (std::uint32_t i = 0; i < size.value(); ++i) {
                std::string decoded;
                std::string_view key;
                if (!peekString<Fmt>(key, it, end)) {
                    glz::parse<glz::MSGPACK>::op<Opts>(decoded, ctx, it, end);
                    if (bool(ctx.error)) return;
                    key = decoded;
                }
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
            const std::uint8_t tag = static_cast<std::uint8_t>(*it++);
            std::uint32_t size = 0;
            if (tag >= 0x90 && tag <= 0x9f) {
                size = tag & 0x0f;
            } else if (tag == 0xdc) {
                size = (static_cast<std::uint32_t>(static_cast<std::uint8_t>(it[0])) << 8) | static_cast<std::uint8_t>(it[1]);
                it += 2;
            } else if (tag == 0xdd) {
                size = (static_cast<std::uint32_t>(static_cast<std::uint8_t>(it[0])) << 24) |
                       (static_cast<std::uint32_t>(static_cast<std::uint8_t>(it[1])) << 16) |
                       (static_cast<std::uint32_t>(static_cast<std::uint8_t>(it[2])) << 8) | static_cast<std::uint8_t>(it[3]);
                it += 4;
            } else [[unlikely]] {
                ctx.error = glz::error_code::syntax_error;
                return;
            }
            for (std::uint32_t i = 0; i < size; ++i) {
                f(ctx, it, end);
                if (bool(ctx.error)) return;
            }
        }
    }

    // 判断当前值是否为 null（msgpack 的 obj 写出会把 nullopt optional 写成 nil）
    template<std::uint32_t Fmt, auto Opts>
    bool valueIsNull(glz::is_context auto &&ctx, auto &&it, auto &&end) {
        if constexpr (Fmt == glz::JSON) {
            glz::skip_ws<Opts>(ctx, it, end);
            return *it == 'n';
        } else {
            return static_cast<std::uint8_t>(*it) == 0xc0;
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

    // 读取可空成员：写入端会把 nullopt 的 optional 写成 null / nil
    template<std::uint32_t Fmt, auto Opts, class T>
    inline void readOptionalMember(std::optional<T> &value, glz::is_context auto &&ctx, auto &&it, auto &&end) {
        if (valueIsNull<Fmt, Opts>(ctx, it, end)) {
            skipNull<Fmt, Opts>(ctx, it, end);
            value.reset();
        } else {
            value.emplace();
            glz::parse<Fmt>::template op<Opts>(*value, ctx, it, end);
        }
    }

    // 写出 PropertyValue 的原始值（由 type 决定活跃成员）
    template<std::uint32_t Fmt, auto Opts>
    void writePropertyValue(const PropertyValue &v, const PropertyType::PropertyType type,
                            glz::is_context auto &&ctx, auto &&b, auto &&ix) {
        switch (type) {
            case PropertyType::STRING:
                glz::serialize<Fmt>::template op<Opts>(static_cast<const std::pmr::u16string &>(*v.string), ctx, b, ix);
                break;
            case PropertyType::BOOLEAN:
                glz::serialize<Fmt>::template op<Opts>(v.boolean, ctx, b, ix);
                break;
            case PropertyType::INTEGER:
                glz::serialize<Fmt>::template op<Opts>(v.integer, ctx, b, ix);
                break;
            default:
                std::unreachable();
        }
    }

    inline void releasePropertyValue(const PropertyValue &v, const PropertyType::PropertyType type) {
        if (type == PropertyType::STRING) {
            delete v.string;
        }
    }

    // 读取 PropertyValue：类型由值本身判定。
    // JSON 窥视首字符；MSGPACK 的 tag 已被 map 遍历器消费，即当前值的首字节
    template<std::uint32_t Fmt, auto Opts>
    void readPropertyValue(PropertyValue &v, PropertyType::PropertyType &type,
                           glz::is_context auto &&ctx, auto &&it, auto &&end) {
        if constexpr (Fmt == glz::JSON) {
            glz::skip_ws<Opts>(ctx, it, end);
            if (*it == '"') [[likely]] {
                type = PropertyType::STRING;
                v.string = new CHelper::PropertyString();
                glz::parse<glz::JSON>::op<Opts>(static_cast<std::pmr::u16string &>(*v.string), ctx, it, end);
            } else if (*it == 't' || *it == 'f') [[likely]] {
                type = PropertyType::BOOLEAN;
                glz::parse<glz::JSON>::op<Opts>(v.boolean, ctx, it, end);
            } else {
                type = PropertyType::INTEGER;
                glz::parse<glz::JSON>::op<Opts>(v.integer, ctx, it, end);
            }
        } else {
            // MSGPACK
            const std::uint8_t tag = static_cast<std::uint8_t>(*it++);
            if ((tag >= 0xa0 && tag <= 0xbf) || tag == 0xd9 || tag == 0xda || tag == 0xdb) [[likely]] {
                type = PropertyType::STRING;
                v.string = new CHelper::PropertyString();
                std::string utf8;
                glz::from<glz::MSGPACK, std::string>::op<Opts>(utf8, tag, ctx, it, end);
                ::CHelper::U16Conv::convertToU16(utf8, *v.string);
            } else if (tag == 0xc2 || tag == 0xc3) [[likely]] {
                type = PropertyType::BOOLEAN;
                v.boolean = tag == 0xc3;
            } else {
                type = PropertyType::INTEGER;
                glz::from<glz::MSGPACK, std::int32_t>::op<Opts>(v.integer, tag, ctx, it, end);
            }
        }
    }

    // 二进制格式：类型已由 Property::type 确定，按类型读取对应成员
    template<auto Opts>
    void readBinaryPropertyValue(PropertyValue &v, const PropertyType::PropertyType type,
                                 glz::is_context auto &&ctx, auto &&it, auto &&end) {
        switch (type) {
            case PropertyType::STRING:
                v.string = new CHelper::PropertyString();
                glz::parse<CHelper::BinaryFormat>::template op<Opts>(static_cast<std::pmr::u16string &>(*v.string), ctx, it, end);
                break;
            case PropertyType::BOOLEAN:
                glz::parse<CHelper::BinaryFormat>::template op<Opts>(v.boolean, ctx, it, end);
                break;
            case PropertyType::INTEGER:
                glz::parse<CHelper::BinaryFormat>::template op<Opts>(v.integer, ctx, it, end);
                break;
            default:
                std::unreachable();
        }
    }
}// namespace CHelper

// ================= 节点对象读取（JSON / MessagePack） =================
namespace CHelper {

    //按节点类型反序列化 JSON/MSGPACK 的节点对象（对象体由 glz::meta 描述）；
    //JSON_ENTRY 等带空 nodeCreateStage 的类型在运行时被拒绝，保持旧分发表内的行为
    template<class NodeType, std::uint32_t Fmt, auto Opts, class Ctx, class It, class End>
    inline void readNodeObject(Node::NodeWithType &t, Ctx &ctx, It &it, End &end) {
        if (!nodeCreateStageAllows<NodeType::nodeTypeId>(ctx)) [[unlikely]] {
            ctx.error = glz::error_code::no_matching_variant_type;
            return;
        }
        auto *node = new NodeType();
        glz::parse<Fmt>::template op<Opts>(*node, ctx, it, end);
        if (bool(ctx.error)) [[unlikely]] {
            delete node;
            return;
        }
        t.nodeTypeId = NodeType::nodeTypeId;
        t.data = node;
    }

    // 节点对象读取的统一分发：类型 id 已由调用方校验（名称查表或 uint8 范围检查），
    // 可反序列化类型进入 readNodeObject，其余（WRAPPED / LF / PER_COMMAND 等运行期类型）按不匹配处理
    template<std::uint32_t Fmt, auto Opts, class Ctx, class It, class End>
    inline void readNodeByTypeId(Node::NodeWithType &t, const Node::NodeTypeId::NodeTypeId id, Ctx &ctx, It &it, End &end) {
        Node::dispatchNodeType(
                id,
                [&]<class NodeType>() {
                    if constexpr (Meta::typeListContains<NodeType, Node::GrammarNodeTypes> ||
                                  Meta::typeListContains<NodeType, Node::JsonSerializableNodeTypes>) {
                        readNodeObject<NodeType, Fmt, Opts>(t, ctx, it, end);
                    } else {
                        ctx.error = glz::error_code::no_matching_variant_type;
                    }
                },
                [&] { ctx.error = glz::error_code::no_matching_variant_type; });
    }

    // 按类型名反序列化（JSON/MSGPACK：由预读或完整扫描得到类型名后分派）
    template<std::uint32_t Fmt, auto Opts>
    inline void readNodeValue(Node::NodeWithType &t, const std::string_view typeName, glz::is_context auto &&ctx, auto &&it,
                              auto &&end) {
        const std::optional<Node::NodeTypeId::NodeTypeId> id = Node::getNodeTypeIdByName(typeName);
        if (!id.has_value()) [[unlikely]] {
            ctx.error = glz::error_code::no_matching_variant_type;
            return;
        }
        readNodeByTypeId<Fmt, Opts>(t, id.value(), ctx, it, end);
    }

    // 预读一个不含转义的字符串（JSON 键名/类型名、msgpack str）：
    // 含转义、长度不足或不是字符串时返回 false，由调用方回退到完整扫描
    template<std::uint32_t Fmt>
    inline bool peekString(std::string_view &result, auto &source, const auto &end) {
        auto it = source;
        if constexpr (Fmt == glz::JSON) {
            if (it >= end || *it != '"') {
                return false;
            }
            ++it;
            const auto *start = it;
            while (it < end && *it != '"') {
                if (*it == '\\') {
                    return false;
                }
                ++it;
            }
            if (it >= end) {
                return false;
            }
            result = std::string_view(start, static_cast<std::size_t>(it - start));
            ++it;
            source = it;
            return true;
        } else {
            // MSGPACK：fixstr / str8 / str16 / str32
            if (it >= end) {
                return false;
            }
            const std::uint8_t tag = static_cast<std::uint8_t>(*it++);
            std::uint32_t size = 0;
            if (tag >= 0xa0 && tag <= 0xbf) {
                size = tag & 0x1f;
            } else if (tag == 0xd9) {
                if (it >= end) return false;
                size = static_cast<std::uint8_t>(*it++);
            } else if (tag == 0xda) {
                if (end - it < 2) return false;
                size = (static_cast<std::uint32_t>(static_cast<std::uint8_t>(it[0])) << 8) | static_cast<std::uint8_t>(it[1]);
                it += 2;
            } else if (tag == 0xdb) {
                if (end - it < 4) return false;
                size = (static_cast<std::uint32_t>(static_cast<std::uint8_t>(it[0])) << 24) |
                       (static_cast<std::uint32_t>(static_cast<std::uint8_t>(it[1])) << 16) |
                       (static_cast<std::uint32_t>(static_cast<std::uint8_t>(it[2])) << 8) | static_cast<std::uint8_t>(it[3]);
                it += 4;
            } else {
                return false;
            }
            if (size > static_cast<std::size_t>(end - it)) {
                return false;
            }
            result = std::string_view(it, size);
            it += size;
            source = it;
            return true;
        }
    }

    // 预读节点对象的第一个成员：写出端固定把 "type" 放在最前，
    // 命中即可直接取得类型名，无需为了找 "type" 而完整扫描整个节点对象。
    // 未命中（成员顺序不同、含转义、格式错误等）返回 false，由调用方回退到完整扫描；
    // 预读只是试探，失败时不改变 ctx.error，迭代器由调用方重置
    template<std::uint32_t Fmt, auto Opts>
    inline bool peekNodeTypeName(std::string_view &typeName, glz::is_context auto &&ctx, auto &&it, auto &&end) {
        const auto savedError = ctx.error;
        bool found = false;
        if constexpr (Fmt == glz::JSON) {
            glz::skip_ws<Opts>(ctx, it, end);
            if (!bool(ctx.error) && it < end && *it == '{') {
                ++it;
                glz::skip_ws<Opts>(ctx, it, end);
                std::string_view key;
                if (!bool(ctx.error) && peekString<Fmt>(key, it, end) && key == "type") {
                    glz::skip_ws<Opts>(ctx, it, end);
                    if (!bool(ctx.error) && it < end && *it == ':') {
                        ++it;
                        glz::skip_ws<Opts>(ctx, it, end);
                        found = !bool(ctx.error) && peekString<Fmt>(typeName, it, end);
                    }
                }
            }
        } else {
            // MSGPACK：map 头 + 第一个键
            const auto size = readMsgpackMapSize(it, end);
            std::string_view key;
            if (size.has_value() && size.value() > 0 && peekString<Fmt>(key, it, end) && key == "type") {
                found = peekString<Fmt>(typeName, it, end);
            }
        }
        ctx.error = savedError;
        return found;
    }

    // 完整扫描节点对象，提取 "type" 的值（"type" 不在首位时使用）。
    // 未找到 "type" 时 typeName 保持为空，由 readNodeValue 按未知类型拒绝
    template<std::uint32_t Fmt, auto Opts>
    inline void scanNodeTypeName(std::string &typeName, glz::is_context auto &&ctx, auto &&it, auto &&end) {
        forEachObjectMember<Fmt, Opts>(ctx, it, end, [&](const std::string_view key, auto &&ctx2, auto &&it2, auto &&end2) {
            if (key == "type") {
                glz::parse<Fmt>::template op<Opts>(typeName, ctx2, it2, end2);
            } else {
                glz::skip_value<Fmt>::template op<Opts>(ctx2, it2, end2);
            }
        });
    }

    // 节点读取总入口，三种格式共用：
    // 二进制（非自描述）：先读 uint8 类型 ID，范围校验后分派；
    // JSON/MSGPACK（自描述）：写出端固定把 "type" 放在最前，先预读第一个成员即可确定类型，
    // 整个节点只被完整解析一次；顺序不同或无法预读时回退到完整扫描（扫描 + 重置迭代器重读）
    template<std::uint32_t Fmt, auto Opts>
    inline void readNodeWithType(Node::NodeWithType &t, glz::is_context auto &&ctx, auto &&it, auto &&end) {
        if constexpr (Fmt == CHelper::BinaryFormat) {
            std::uint8_t typeId = 0;
            glz::parse<CHelper::BinaryFormat>::template op<Opts>(typeId, ctx, it, end);
            if (bool(ctx.error)) [[unlikely]] {
                return;
            }
            //类型 id 来自外部数据，先做范围合法性检查（旧实现对非法值直接触发 UB）
            if (typeId >= static_cast<std::uint8_t>(Node::NodeTypeId::NodeTypeIdCount)) [[unlikely]] {
                ctx.error = glz::error_code::no_matching_variant_type;
                return;
            }
            readNodeByTypeId<Fmt, Opts>(t, static_cast<Node::NodeTypeId::NodeTypeId>(typeId), ctx, it, end);
        } else {
            constexpr auto opts = glz::opts{.error_on_unknown_keys = false};
            const auto start = it;
            std::string_view peekedTypeName;
            if (peekNodeTypeName<Fmt, opts>(peekedTypeName, ctx, it, end)) {
                it = start;
                readNodeValue<Fmt, opts>(t, peekedTypeName, ctx, it, end);
                return;
            }
            it = start;
            std::string typeName;
            scanNodeTypeName<Fmt, opts>(typeName, ctx, it, end);
            if (bool(ctx.error)) [[unlikely]] {
                return;
            }
            it = start;
            readNodeValue<Fmt, opts>(t, typeName, ctx, it, end);
        }
    }
}// namespace CHelper

namespace glz {
    template<>
    struct to<JSON, CHelper::Node::NodeWithType> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
            CHelper::writeNodeWithType<JSON, Opts>(value, ctx, b, ix);
        }
    };

    template<>
    struct to<MSGPACK, CHelper::Node::NodeWithType> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
            CHelper::writeNodeWithType<MSGPACK, Opts>(value, ctx, b, ix);
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
        static void op(auto &&value, std::uint8_t, glz::is_context auto &&ctx, auto &&it, auto &&end) {
            // tag 已被分发器消费，回退一个字节后走与 JSON 相同的节点读取路径
            --it;
            CHelper::readNodeWithType<MSGPACK, Opts>(value, ctx, it, end);
        }
    };

    // 二进制格式：类型 ID 与成员顺序的读写实现见 readNodeWithType / writeNodeWithType
    template<>
    struct to<CHelper::BinaryFormat, CHelper::Node::NodeWithType> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
            CHelper::writeNodeWithType<CHelper::BinaryFormat, Opts>(value, ctx, b, ix);
        }
    };

    template<>
    struct from<CHelper::BinaryFormat, CHelper::Node::NodeWithType> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&it, auto &&end) {
            CHelper::readNodeWithType<CHelper::BinaryFormat, Opts>(value, ctx, it, end);
        }
    };
}// namespace glz

// ================= FreeableNodeWithTypes / NodeJsonElement / RepeatData =================

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
    static void op(auto &&value, std::uint8_t tag, glz::is_context auto &&ctx, auto &&it, auto &&end) {
        // tag 已由分发器消费，直接传递给 vector 读取
        from<MSGPACK, std::pmr::vector<CHelper::Node::NodeWithType>>::op<Opts>(value.nodes, tag, ctx, it, end);
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
    static constexpr auto value = glz::object("id", &T::id, "breakNodes", &T::breakNodes, "repeatNodes", &T::repeatNodes, "isEnd", &T::isEnd);
};

// ================= 模型类型 meta 定义 =================
template<>
struct glz::meta<CHelper::NormalId> {
    using T = CHelper::NormalId;
    static constexpr auto value = glz::object("name", &T::name, "description", &T::description);
};

template<>
struct glz::meta<CHelper::NamespaceId> {
    using T = CHelper::NamespaceId;
    static constexpr auto value = glz::object("name", &T::name, "description", &T::description, "idNamespace", &T::idNamespace);
};

template<>
struct glz::meta<CHelper::ItemId> {
    using T = CHelper::ItemId;
    static constexpr auto value = glz::object("name", &T::name, "description", &T::description, "idNamespace", &T::idNamespace, "max", &T::max, "descriptions", &T::descriptions);
};

template<>
struct glz::meta<CHelper::Manifest> {
    using T = CHelper::Manifest;
    static constexpr auto value =
            glz::object("name", &T::name, "description", &T::description, "version", &T::version, "versionType", &T::versionType,
                        "branch", &T::branch, "author", &T::author, "updateDate", &T::updateDate, "packId", &T::packId,
                        "versionCode", &T::versionCode, "isBasicPack", &T::isBasicPack, "isDefault", &T::isDefault);
};

// ================= CPack 数据类型 meta 定义 =================
template<>
struct glz::meta<CHelper::NormalIdEntry> {
    using T = CHelper::NormalIdEntry;
    static constexpr auto value = glz::object("id", &T::id, "content", &T::content);
};

template<>
struct glz::meta<CHelper::NamespaceIdEntry> {
    using T = CHelper::NamespaceIdEntry;
    static constexpr auto value = glz::object("id", &T::id, "content", &T::content);
};

template<>
struct glz::meta<CHelper::BlockIdsEntry> {
    using T = CHelper::BlockIdsEntry;
    static constexpr auto value = glz::object("id", &T::id, "content", &T::content);
};

template<>
struct glz::meta<CHelper::ItemIdsEntry> {
    using T = CHelper::ItemIdsEntry;
    static constexpr auto value = glz::object("id", &T::id, "content", &T::content);
};

template<>
struct glz::meta<CHelper::GrammarEntry> {
    using T = CHelper::GrammarEntry;
    static constexpr auto value = glz::object("id", &T::id, "type", &T::type, "content", &T::content);
};

template<>
struct glz::meta<CHelper::IdEntry> {
    static constexpr std::string_view tag = "type";
    static constexpr auto ids = std::array{"normal", "namespace", "block", "item"};
};

template<>
struct glz::meta<CHelper::CPackJsonData> {
    using T = CHelper::CPackJsonData;
    static constexpr auto value = glz::object("manifest", &T::manifest, "id", &T::id, "grammar", &T::grammar, "json", &T::json, "repeat", &T::repeat, "command", &T::command);
};

template<>
struct glz::meta<CHelper::CPackData> {
    using T = CHelper::CPackData;
    static constexpr auto value = glz::object("manifest", &T::manifest, "normalIds", &T::normalIds, "namespaceIds", &T::namespaceIds, "itemIds", &T::itemIds,
                                              "blockIds", &T::blockIds, "jsonNodes", &T::jsonNodes, "repeatNodeData", &T::repeatNodeData,
                                              "commands", &T::commands, "grammar", &T::grammar);
};

template<>
struct glz::meta<CHelper::Old2New::BlockFixEntry> {
    using T = CHelper::Old2New::BlockFixEntry;
    static constexpr auto value = glz::object("name", &T::name, "data", &T::data, "newBlockId", &T::newBlockId, "blockState", &T::blockState);
};

// ================= NodePerCommand =================
namespace CHelper::Node {

    struct WrappedNodeWire {
        std::int32_t definition = -1;
        std::vector<std::uint32_t> next;
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
            for (std::size_t start = 0, end; start < id.size(); start = end + 1) {
                const std::size_t findStart = (id[start] == '[' || id[start] == '<') ? id.find(id[start] == '[' ? ']' : '>', start) + 1 : start;
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
            std::vector<std::size_t> children;
            bool needsLf = false;
        };
        std::vector<TrieNode> trie(1);
        bool hasOptionalFirst = false;
        for (const auto &syntaxUtf16: t.syntax) {
            const std::string syntax = utf8::utf16to8(syntaxUtf16);
            std::size_t position = syntax.find(u' ');
            if (position == std::string::npos) {
                hasOptionalFirst = true;
                continue;
            }
            hasOptionalFirst |= position + 1 < syntax.size() && syntax[position + 1] == u'[';
            std::size_t current = 0;
            while (position < syntax.size()) {
                while (position < syntax.size() && syntax[position] == u' ') {
                    ++position;
                }
                if (position >= syntax.size()) {
                    break;
                }
                const std::size_t tokenStartPos = position;
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
                std::size_t childIndex = SIZE_MAX;
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
        for (std::size_t i = 1; i < trie.size(); ++i) {
            t.wrappedNodes.emplace_back(*trie[i].definition);
        }
        //connect nextNodes
        for (std::size_t i = 1; i < trie.size(); ++i) {
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

    // 由预解析的节点图构建（msgpack / 二进制格式路径，与旧版 from_binary 逻辑一致）
    inline void buildNodePerCommandGraph(NodePerCommand &t, const std::vector<WrappedNodeWire> &wrapped,
                                         const std::vector<std::uint32_t> &startIndices) {
        const std::size_t wrappedCount = wrapped.size();
        t.wrappedNodes.reserve(wrappedCount);
        for (std::size_t i = 0; i < wrappedCount; ++i) {
            const auto defIdx = wrapped[i].definition;
            if (defIdx < 0 || static_cast<std::size_t>(defIdx) >= t.nodes.nodes.size()) [[unlikely]] {
                throw std::runtime_error("invalid node definition index");
            }
            t.wrappedNodes.emplace_back(t.nodes.nodes[static_cast<std::size_t>(defIdx)]);
        }
        for (std::size_t i = 0; i < wrappedCount; ++i) {
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

    // 由内存中的节点图（指针）生成写出的紧凑表示：定义/后继/起始都用下标表示
    inline void buildNodePerCommandWireGraph(const NodePerCommand &t, std::vector<WrappedNodeWire> &wrapped,
                                             std::vector<std::uint32_t> &starts) {
        wrapped.reserve(t.wrappedNodes.size());
        for (const auto &wrappedNode: t.wrappedNodes) {
            auto &item = wrapped.emplace_back();
            for (std::size_t i = 0; i < t.nodes.nodes.size(); ++i) {
                if (t.nodes.nodes[i].data == wrappedNode.innerNode.data) {
                    item.definition = static_cast<std::int32_t>(i);
                    break;
                }
            }
            for (const auto *next: wrappedNode.nextNodes) {
                if (next == NodeLF::getInstance()) {
                    item.next.push_back(UINT32_MAX);
                } else {
                    item.next.push_back(static_cast<std::uint32_t>(next - t.wrappedNodes.data()));
                }
            }
        }
        starts.reserve(t.startNodes.size());
        for (const auto *start: t.startNodes) {
            if (start == NodeLF::getInstance()) {
                starts.push_back(UINT32_MAX);
            } else {
                starts.push_back(static_cast<std::uint32_t>(start - t.wrappedNodes.data()));
            }
        }
    }
}// namespace CHelper::Node

template<>
struct glz::meta<CHelper::Node::WrappedNodeWire> {
    using T = CHelper::Node::WrappedNodeWire;
    static constexpr auto value = glz::object("definition", &T::definition, "next", &T::next);
};

namespace glz {
    // 严格遵循旧版二进制布局：name, description, syntax, nodes(数组，元素含 id),
    // wrappedCount + [defIdx, nextCount, nextIndices...](UINT32_MAX 表示 LF),
    // startCount + [startIndices...]；无键名、无 optional 存在标记。
    // 节点图与 JSON 不同，必须预解析（定义/后继/起始均用下标表示）
    template<>
    struct to<CHelper::BinaryFormat, CHelper::Node::NodePerCommand> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
            serialize<CHelper::BinaryFormat>::template op<Opts>(value.name, ctx, b, ix);
            serialize<CHelper::BinaryFormat>::template op<Opts>(value.description, ctx, b, ix);
            serialize<CHelper::BinaryFormat>::template op<Opts>(value.syntax, ctx, b, ix);
            serialize<CHelper::BinaryFormat>::template op<Opts>(value.nodes, ctx, b, ix);
            std::vector<CHelper::Node::WrappedNodeWire> wrapped;
            std::vector<std::uint32_t> starts;
            CHelper::Node::buildNodePerCommandWireGraph(value, wrapped, starts);
            serialize<CHelper::BinaryFormat>::template op<Opts>(wrapped, ctx, b, ix);
            serialize<CHelper::BinaryFormat>::template op<Opts>(starts, ctx, b, ix);
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
            if (bool(ctx.error)) [[unlikely]] { return; }
            if (wrappedCount > static_cast<size_t>(end - it) / (2 * sizeof(std::uint32_t))) [[unlikely]] {
                ctx.error = error_code::unexpected_end;
                return;
            }
            // 先固定最终节点数组的地址，前向/后向引用都直接写入最终指针图。
            // LF 仅用于构造占位节点，实际 definition 会在下面逐项覆盖。
            value.wrappedNodes.clear();
            value.startNodes.clear();
            value.wrappedNodes.reserve(wrappedCount);
            for (std::uint32_t i = 0; i < wrappedCount; ++i) {
                value.wrappedNodes.emplace_back(CHelper::Node::NodeLF::getInstance()->innerNode);
            }
            const auto getWrappedNode = [&](const std::uint32_t index) {
                if (index == UINT32_MAX) {
                    return CHelper::Node::NodeLF::getInstance();
                }
                if (index >= wrappedCount) [[unlikely]] {
                    throw std::runtime_error("invalid wrapped node index");
                }
                return &value.wrappedNodes[index];
            };
            for (auto &wrappedNode: value.wrappedNodes) {
                std::int32_t defIdx = -1;
                parse<CHelper::BinaryFormat>::template op<Opts>(defIdx, ctx, it, end);
                if (bool(ctx.error)) [[unlikely]] { return; }
                if (defIdx < 0 || static_cast<size_t>(defIdx) >= value.nodes.nodes.size()) [[unlikely]] {
                    throw std::runtime_error("invalid node definition index");
                }
                wrappedNode.innerNode = value.nodes.nodes[static_cast<size_t>(defIdx)];
                std::uint32_t nextCount = 0;
                parse<CHelper::BinaryFormat>::template op<Opts>(nextCount, ctx, it, end);
                if (bool(ctx.error)) [[unlikely]] { return; }
                if (nextCount > static_cast<size_t>(end - it) / sizeof(std::uint32_t)) [[unlikely]] {
                    ctx.error = error_code::unexpected_end;
                    return;
                }
                wrappedNode.nextNodes.reserve(nextCount);
                for (std::uint32_t i = 0; i < nextCount; ++i) {
                    std::uint32_t nextIdx = 0;
                    parse<CHelper::BinaryFormat>::template op<Opts>(nextIdx, ctx, it, end);
                    wrappedNode.nextNodes.push_back(getWrappedNode(nextIdx));
                }
            }
            // 前向引用的 definition 至此已经全部填好，再计算与 pushNextNode 相同的 LF 缓存。
            for (auto &wrappedNode: value.wrappedNodes) {
                wrappedNode.hasNextLF = std::ranges::any_of(wrappedNode.nextNodes, [](const auto *next) {
                    return next->innerNode.nodeTypeId == CHelper::Node::NodeTypeId::LF;
                });
            }
            std::uint32_t startCount = 0;
            parse<CHelper::BinaryFormat>::template op<Opts>(startCount, ctx, it, end);
            if (bool(ctx.error)) [[unlikely]] { return; }
            if (startCount > static_cast<size_t>(end - it) / sizeof(std::uint32_t)) [[unlikely]] {
                ctx.error = error_code::unexpected_end;
                return;
            }
            value.startNodes.reserve(startCount);
            for (std::uint32_t i = 0; i < startCount; ++i) {
                std::uint32_t startIdx = 0;
                parse<CHelper::BinaryFormat>::template op<Opts>(startIdx, ctx, it, end);
                if (startIdx != UINT32_MAX && startIdx >= wrappedCount) [[unlikely]] {
                    throw std::runtime_error("invalid start node index");
                }
                value.startNodes.push_back(getWrappedNode(startIdx));
            }
        }
    };
}// namespace glz

namespace CHelper {
    // ================= NodePerCommand 的 JSON / MessagePack 表示 =================
    // name / description / syntax 与节点定义都直接读写最终结构，不再经过中间的 wire 结构：
    // 读出时节点按 "id -> 节点" 的键值对直接写入 nodes 并就地写回 id，
    // 写出时用 glz::obj 引用最终成员，只有节点索引需要临时构造

    template<std::uint32_t Fmt, auto Opts>
    inline void readNodePerCommand(Node::NodePerCommand &t, glz::is_context auto &&ctx, auto &&it, auto &&end) {
        std::optional<std::vector<Node::WrappedNodeWire>> wrappedNodes;
        std::optional<std::vector<std::uint32_t>> startNodes;
        forEachObjectMember<Fmt, Opts>(ctx, it, end, [&](const std::string_view key, auto &&ctx2, auto &&it2, auto &&end2) {
            if (key == "name") [[likely]] {
                glz::parse<Fmt>::template op<Opts>(t.name, ctx2, it2, end2);
            } else if (key == "description") [[likely]] {
                glz::parse<Fmt>::template op<Opts>(t.description, ctx2, it2, end2);
            } else if (key == "syntax") [[likely]] {
                glz::parse<Fmt>::template op<Opts>(t.syntax, ctx2, it2, end2);
            } else if (key == "node") [[likely]] {
                forEachObjectMember<Fmt, Opts>(ctx2, it2, end2, [&](const std::string_view id, auto &&ctx3, auto &&it3, auto &&end3) {
                    // 键即节点 id，直接写回节点（键重复时两个节点都会保留，与二进制格式一致）
                    Node::NodeWithType node;
                    glz::parse<Fmt>::template op<Opts>(node, ctx3, it3, end3);
                    if (bool(ctx3.error)) [[unlikely]] {
                        return;
                    }
                    static_cast<Node::NodeSerializable *>(node.data)->id = std::pmr::string(id.data(), id.size());
                    t.nodes.nodes.push_back(std::move(node));
                });
            } else if (key == "wrappedNodes") {
                readOptionalMember<Fmt, Opts>(wrappedNodes, ctx2, it2, end2);
            } else if (key == "startNodes") {
                readOptionalMember<Fmt, Opts>(startNodes, ctx2, it2, end2);
            } else if constexpr (Opts.error_on_unknown_keys) {
                ctx2.error = glz::error_code::unknown_key;
            } else {
                glz::skip_value<Fmt>::template op<Opts>(ctx2, it2, end2);
            }
        });
        if (bool(ctx.error)) [[unlikely]] {
            return;
        }
        if (t.name.empty()) [[unlikely]] {
            throw std::runtime_error("command name cannot be empty");
        }
        if (wrappedNodes.has_value()) {
            const std::vector<std::uint32_t> noStartNodes;
            Node::buildNodePerCommandGraph(t, wrappedNodes.value(),
                                           startNodes.has_value() ? startNodes.value() : noStartNodes);
        } else {
            Node::buildNodePerCommandTrie(t);
        }
    }

    template<std::uint32_t Fmt, auto Opts>
    inline void writeNodePerCommand(const Node::NodePerCommand &t, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
        // 节点定义按 id 建立索引：只复制节点句柄与键，节点数据仍由 CPack 持有
        glz::ordered_small_map<Node::NodeWithType> nodes;
        nodes.reserve(t.nodes.nodes.size());
        for (const auto &node: t.nodes.nodes) {
            const auto *serializable = static_cast<const Node::NodeSerializable *>(node.data);
            if (serializable->id.has_value()) {
                nodes.try_emplace(std::string(serializable->id->data(), serializable->id->size()), node);
            }
        }
        std::optional<std::vector<Node::WrappedNodeWire>> wrappedNodes;
        std::optional<std::vector<std::uint32_t>> startNodes;
        if constexpr (Fmt == glz::MSGPACK) {
            // JSON 的节点图由 syntax 重建，只有 MessagePack 需要写出预解析的节点图
            Node::buildNodePerCommandWireGraph(t, wrappedNodes.emplace(), startNodes.emplace());
        }
        // name / description / syntax 直接引用最终结构，不产生深拷贝
        auto value = glz::obj{"name", t.name, "description", t.description, "syntax", t.syntax, "node", nodes,
                              "wrappedNodes", wrappedNodes, "startNodes", startNodes};
        glz::serialize<Fmt>::template op<Opts>(value, ctx, b, ix);
    }
}// namespace CHelper

namespace glz {
    // NodePerCommand 的 JSON / MessagePack 表示（读写实现见上）
    template<>
    struct to<JSON, CHelper::Node::NodePerCommand> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
            CHelper::writeNodePerCommand<JSON, Opts>(value, ctx, b, ix);
        }
    };

    template<>
    struct to<MSGPACK, CHelper::Node::NodePerCommand> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
            CHelper::writeNodePerCommand<MSGPACK, Opts>(value, ctx, b, ix);
        }
    };

    template<>
    struct from<JSON, CHelper::Node::NodePerCommand> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&it, auto &&end) {
            CHelper::readNodePerCommand<JSON, Opts>(value, ctx, it, end);
        }
    };

    template<>
    struct from<MSGPACK, CHelper::Node::NodePerCommand> {
        template<auto Opts>
        static void op(auto &&value, std::uint8_t, glz::is_context auto &&ctx, auto &&it, auto &&end) {
            // tag 已被分发器消费，回退后解析 map 头
            --it;
            CHelper::readNodePerCommand<MSGPACK, Opts>(value, ctx, it, end);
        }
    };
}// namespace glz

// ================= BlockId 序列化（从 BlockId.h 移入） =================
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
        forEachObjectMember<Fmt, Opts>(ctx, it, end, [&](const std::string_view key, auto &&ctx2, auto &&it2, auto &&end2) {
            if (key == "name") [[likely]] {
                glz::parse<Fmt>::template op<Opts>(t.name, ctx2, it2, end2);
            } else if (key == "defaultValue") [[likely]] {
                readPropertyValue<Fmt, Opts>(t.defaultValue, t.type, ctx2, it2, end2);
                hasDefaultValue = true;
            } else if (key == "valid") {
                if (valueIsNull<Fmt, Opts>(ctx2, it2, end2)) {
                    skipNull<Fmt, Opts>(ctx2, it2, end2);
                    t.valid = std::nullopt;
                    return;
                }
                t.valid = std::make_optional<std::pmr::vector<PropertyValue>>();
                forEachArrayElement<Fmt, Opts>(ctx2, it2, end2, [&](auto &&ctx3, auto &&it3, auto &&end3) {
                    PropertyValue propertyValue;
                    PropertyType::PropertyType type = t.type;
                    readPropertyValue<Fmt, Opts>(propertyValue, type, ctx3, it3, end3);
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
            t.valid = std::make_optional<std::pmr::vector<PropertyValue>>();
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

    // BlockPropertyValueDescription 的写出体（JSON / MSGPACK 键布局一致）
    template<std::uint32_t Fmt, auto Opts>
    void writeBlockPropertyValueDescriptionView(const BlockPropertyValueDescriptionWriter &value,
                                                glz::is_context auto &&ctx, auto &&b, auto &&ix) {
        auto inner = glz::obj{"valueName", PropertyValueWriter{&value.value->valueName, value.type},
                              "description", value.value->description};
        glz::serialize<Fmt>::template op<Opts>(inner, ctx, b, ix);
    }

    // 读取 BlockPropertyDescription：type 由第一个 valueName 的类型判定
    template<std::uint32_t Fmt, auto Opts>
    void readBlockPropertyDescription(BlockPropertyDescription &t, glz::is_context auto &&ctx, auto &&it, auto &&end) {
        t.release();
        bool hasPropertyType = false;
        forEachObjectMember<Fmt, Opts>(ctx, it, end, [&](const std::string_view key, auto &&ctx2, auto &&it2, auto &&end2) {
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
                                                   [&](const std::string_view key2, auto &&ctx4, auto &&it4, auto &&end4) {
                                                       if (key2 == "valueName") [[likely]] {
                                                           readPropertyValue<Fmt, Opts>(
                                                                   blockPropertyValueDescription.valueName, type, ctx4, it4, end4);
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
        static void op(auto &&value, std::uint8_t, glz::is_context auto &&ctx, auto &&it, auto &&end) {
            // tag 已被分发器消费，回退后由 readProperty 解析 map 头
            --it;
            CHelper::readProperty<MSGPACK, Opts>(value, ctx, it, end);
        }
    };

    template<>
    struct to<JSON, CHelper::BlockPropertyValueDescriptionWriter> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
            CHelper::writeBlockPropertyValueDescriptionView<JSON, Opts>(value, ctx, b, ix);
        }
    };

    template<>
    struct to<MSGPACK, CHelper::BlockPropertyValueDescriptionWriter> {
        template<auto Opts>
        static void op(auto &&value, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
            CHelper::writeBlockPropertyValueDescriptionView<MSGPACK, Opts>(value, ctx, b, ix);
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
        static void op(auto &&value, std::uint8_t, glz::is_context auto &&ctx, auto &&it, auto &&end) {
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
    static constexpr auto value = glz::object("blocks", &T::blocks, "properties", &T::properties);
};

template<>
struct glz::meta<CHelper::BlockPropertyDescriptions> {
    using T = CHelper::BlockPropertyDescriptions;
    static constexpr auto value = glz::object("common", &T::common, "block", &T::block);
};

template<>
struct glz::meta<CHelper::BlockId> {
    using T = CHelper::BlockId;
    static constexpr auto value = glz::object("name", &T::name, "description", &T::description, "idNamespace", &T::idNamespace, "properties", &T::properties);
};

template<>
struct glz::meta<CHelper::BlockIds> {
    using T = CHelper::BlockIds;
    static constexpr auto value = glz::object("blockStateValues", &T::blockStateValues, "blockPropertyDescriptions", &T::blockPropertyDescriptions);
};

// Grammar 条目在读取 content 时切换到 GRAMMAR_NODE 阶段。
// 这样阶段权限绑定在资源类型上，而不是绑定在某个固定文件名上。
namespace CHelper {

    // GrammarEntry.content 段的读取（JSON / 二进制共用）：切换加载阶段并解析节点表，
    // content 未带 id 时回填条目 id
    template<std::uint32_t Fmt, auto Opts>
    void readGrammarEntryContent(GrammarEntry &value, glz::is_context auto &&ctx, auto &&it, auto &&end) {
        if constexpr (requires { ctx.createStage; }) {
            const auto oldStage = ctx.createStage;
            ctx.createStage = Node::NodeCreateStage::GRAMMAR_NODE;
            if (value.content == nullptr) {
                if (ctx.cpackMemory) {
                    value.content = CHelper::allocateShared<Node::NodeJsonElement>(ctx.cpackMemory);
                } else {
                    value.content = std::make_shared<Node::NodeJsonElement>();
                }
            }
            glz::parse<Fmt>::template op<Opts>(*value.content, ctx, it, end);
            if (!value.content->id.has_value()) {
                value.content->id = value.id;
            }
            ctx.createStage = oldStage;
        } else {
            ctx.error = glz::error_code::no_matching_variant_type;
        }
    }
}// namespace CHelper

template<>
struct glz::from<glz::JSON, CHelper::GrammarEntry> {
    template<auto Opts>
    static void op(auto &&value, glz::is_context auto &&ctx, auto &&it, auto &&end) {
        bool hasId = false;
        bool hasType = false;
        bool hasContent = false;
        CHelper::forEachObjectMember<glz::JSON, Opts>(ctx, it, end,
                                                      [&](const std::string_view key, auto &&memberCtx, auto &&memberIt, auto &&memberEnd) {
                                                          if (key == "id") {
                                                              glz::parse<glz::JSON>::template op<Opts>(value.id, memberCtx, memberIt, memberEnd);
                                                              hasId = true;
                                                          } else if (key == "type") {
                                                              glz::parse<glz::JSON>::template op<Opts>(value.type, memberCtx, memberIt, memberEnd);
                                                              hasType = true;
                                                          } else if (key == "content") {
                                                              CHelper::readGrammarEntryContent<glz::JSON, Opts>(value, ctx, memberIt, memberEnd);
                                                              hasContent = true;
                                                          } else {
                                                              glz::skip_value<glz::JSON>::template op<Opts>(memberCtx, memberIt, memberEnd);
                                                          }
                                                      });
        if (!hasId || !hasType || !hasContent || value.type != "grammar") {
            ctx.error = glz::error_code::no_matching_variant_type;
        }
    }
};

template<>
struct glz::from<CHelper::BinaryFormat, CHelper::GrammarEntry> {
    template<auto Opts>
    static void op(auto &&value, glz::is_context auto &&ctx, auto &&it, auto &&end) {
        glz::parse<CHelper::BinaryFormat>::template op<Opts>(value.id, ctx, it, end);
        glz::parse<CHelper::BinaryFormat>::template op<Opts>(value.type, ctx, it, end);
        CHelper::readGrammarEntryContent<CHelper::BinaryFormat, Opts>(value, ctx, it, end);
        if (value.type != "grammar") {
            ctx.error = glz::error_code::no_matching_variant_type;
        }
    }
};

template<>
struct glz::to<glz::JSON, std::pmr::vector<bool>> {
    template<auto Opts>
    static void op(auto &&value, glz::is_context auto &&ctx, auto &&b, auto &&ix) {
        std::vector<bool> standardValue(value.begin(), value.end());
        glz::serialize<glz::JSON>::template op<Opts>(standardValue, ctx, b, ix);
    }
};

template<>
struct glz::from<glz::JSON, std::pmr::vector<bool>> {
    template<auto Opts>
    static void op(auto &&value, glz::is_context auto &&ctx, auto &&it, auto &&end) {
        value.clear();
        CHelper::forEachArrayElement<glz::JSON, Opts>(ctx, it, end, [&](auto &&ctx2, auto &&it2, auto &&end2) {
            glz::skip_ws<Opts>(ctx2, it2, end2);
            bool item = false;
            glz::parse<glz::JSON>::template op<Opts>(item, ctx2, it2, end2);
            value.push_back(item);
        });
    }
};
