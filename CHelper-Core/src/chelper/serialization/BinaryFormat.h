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

#ifndef CHELPER_BINARY_FORMAT_H
#define CHELPER_BINARY_FORMAT_H

#include <chelper/util/CPackMemory.h>
#include <pch.h>

namespace CHelper {

    // 自定义二进制序列化格式（glaze 用户自定义格式 ID，glaze 保留 65536 以下的 ID）。
    // 非自描述：对象按成员声明顺序紧凑排列，无键名、无类型名，结构完全由读取方的类型定义决定。
    // 线路格式（小端）：
    //   整数/浮点        固定宽度原始字节
    //   枚举             底层整数值
    //   string/u16string uint32 长度 + UTF-8 字节
    //   optional/指针    uint8 存在标记 + 值
    //   vector           uint32 数量 + 元素
    //   map              uint32 数量 + 键值对
    //   对象             按 glz::meta 成员顺序依次排列
    inline constexpr std::uint32_t BinaryFormat = 65536;

}// namespace CHelper

namespace glz {

    template<class T>
    struct is_std_vector : std::false_type {};

    template<class V, class A>
    struct is_std_vector<std::vector<V, A>> : std::true_type {};

    template<class T>
    inline constexpr bool is_std_vector_v = is_std_vector<T>::value;

    // std::vector<bool> 是位压缩特化：迭代器和 back() 给的是代理引用而不是 bool&，
    // 不能取地址、不能绑定非 const 引用，也不能直接传给按元素类型实例化的编解码
    template<class T>
    struct is_vector_bool : std::false_type {};

    template<class A>
    struct is_vector_bool<std::vector<bool, A>> : std::true_type {};

    template<class T>
    inline constexpr bool is_vector_bool_v = is_vector_bool<T>::value;

    // pair 被 glz::reflectable 排除（glaze 对 pair 有各格式自己的处理），需要专用编解码
    template<class T>
    struct is_std_pair : std::false_type {};

    template<class T1, class T2>
    struct is_std_pair<std::pair<T1, T2>> : std::true_type {};

    template<class T>
    inline constexpr bool is_std_pair_v = is_std_pair<T>::value;

    // 枚举取底层整型（惰性实例化，避免对非枚举类型实例化 underlying_type）
    template<class T>
    struct raw_type {
        using type = T;
    };

    template<class T>
        requires std::is_enum_v<T>
    struct raw_type<T> {
        using type = std::underlying_type_t<T>;
    };

    template<class T>
    using raw_type_t = typename raw_type<T>::type;

    template<>
    struct parse<CHelper::BinaryFormat> {
        template<auto Opts, class T, is_context Ctx, class It, class End>
        GLZ_ALWAYS_INLINE static void op(T &&value, Ctx &&ctx, It &it, End &end) {
            from<CHelper::BinaryFormat, std::remove_cvref_t<T>>::template op<Opts>(std::forward<T>(value), ctx, it, end);
        }
    };

    template<>
    struct serialize<CHelper::BinaryFormat> {
        template<auto Opts, class T, is_context Ctx, class B, class IX>
        GLZ_ALWAYS_INLINE static void op(T &&value, Ctx &&ctx, B &&b, IX &&ix) {
            to<CHelper::BinaryFormat, std::remove_cvref_t<T>>::template op<Opts>(std::forward<T>(value),
                                                                                 std::forward<Ctx>(ctx),
                                                                                 std::forward<B>(b),
                                                                                 std::forward<IX>(ix));
        }
    };

    // ================= 数值 / 枚举（小端固定宽度） =================
    template<class T>
        requires(std::is_arithmetic_v<T> || std::is_enum_v<T>)
    struct to<CHelper::BinaryFormat, T> {
        template<auto Opts, class V, is_context Ctx, class B>
        static void op(V &&value, Ctx &&, B &&b, auto &&ix) noexcept {
            using Raw = raw_type_t<T>;
            maybe_pad<sizeof(Raw)>(b, ix);
            if constexpr (std::endian::native == std::endian::little) {
                const Raw raw = static_cast<Raw>(value);
                std::memcpy(data_at(b, ix), &raw, sizeof(Raw));
            } else {
                Raw raw = static_cast<Raw>(value);
                std::uint8_t bytes[sizeof(Raw)];
                std::memcpy(bytes, &raw, sizeof(Raw));
                for (size_t i = 0; i < sizeof(Raw); ++i) {
                    data_at(b, ix)[i] = static_cast<std::byte>(bytes[sizeof(Raw) - 1 - i]);
                }
            }
            ix += sizeof(Raw);
        }
    };

    template<class T>
        requires(std::is_arithmetic_v<T> || std::is_enum_v<T>)
    struct from<CHelper::BinaryFormat, T> {
        template<auto Opts, class V, is_context Ctx, class It, class End>
        static void op(V &&value, Ctx &&ctx, It &&it, End &&end) noexcept {
            using Raw = raw_type_t<T>;
            if (it + sizeof(Raw) > end) [[unlikely]] {
                ctx.error = error_code::unexpected_end;
                return;
            }
            Raw raw{};
            if constexpr (std::endian::native == std::endian::little) {
                std::memcpy(&raw, &(*it), sizeof(Raw));
            } else {
                std::uint8_t bytes[sizeof(Raw)];
                std::memcpy(bytes, &(*it), sizeof(Raw));
                for (size_t i = 0; i < sizeof(Raw); ++i) {
                    reinterpret_cast<std::uint8_t *>(&raw)[i] = bytes[sizeof(Raw) - 1 - i];
                }
            }
            it += sizeof(Raw);
            // vector<bool> 等代理引用需要先转成本类型再赋值
            T result = static_cast<T>(raw);
            value = std::move(result);
        }
    };

    // ================= 字符串（uint32 长度 + UTF-8 字节） =================
    template<class T>
        requires requires { typename T::value_type; typename T::allocator_type; } &&
                 (std::is_same_v<typename T::value_type, char> || std::is_same_v<typename T::value_type, char16_t>)
    struct to<CHelper::BinaryFormat, T> {
        template<auto Opts>
        static void op(auto &&value, is_context auto &&ctx, auto &&b, auto &&ix) noexcept {
            const std::string utf8 = [&value] {
                if constexpr (std::is_same_v<typename T::value_type, char>) {
                    return std::string(value.data(), value.size());
                } else {
                    return utf8::utf16to8(std::u16string_view(value.data(), value.size()));
                }
            }();
            to<CHelper::BinaryFormat, std::uint32_t>::template op<Opts>(static_cast<std::uint32_t>(utf8.size()), ctx, b, ix);
            maybe_pad(utf8.size(), b, ix);
            if (utf8.size() > 0) {
                std::memcpy(data_at(b, ix), utf8.data(), utf8.size());
            }
            ix += utf8.size();
        }
    };

    template<class T>
        requires requires { typename T::value_type; typename T::allocator_type; } &&
                 (std::is_same_v<typename T::value_type, char> || std::is_same_v<typename T::value_type, char16_t>)
    struct from<CHelper::BinaryFormat, T> {
        template<auto Opts>
        static void op(auto &&value, is_context auto &&ctx, auto &&it, auto &&end) {
            std::uint32_t size = 0;
            from<CHelper::BinaryFormat, std::uint32_t>::template op<Opts>(size, ctx, it, end);
            if (it + size > end) [[unlikely]] {
                ctx.error = error_code::unexpected_end;
                return;
            }
            std::string_view utf8;
            if (size > 0) {
                utf8 = std::string_view(static_cast<const char *>(&(*it)), size);
                it += size;
            }
            if constexpr (std::is_same_v<typename T::value_type, char>) {
                value.assign(utf8);
            } else {
                CHelper::U16Conv::convertToU16(utf8, value);
            }
        }
    };

    // ================= optional =================
    template<class T>
    struct to<CHelper::BinaryFormat, std::optional<T>> {
        template<auto Opts>
        static void op(auto &&value, is_context auto &&ctx, auto &&b, auto &&ix) noexcept {
            const bool hasValue = value.has_value();
            to<CHelper::BinaryFormat, bool>::template op<Opts>(hasValue, ctx, b, ix);
            if (hasValue) {
                serialize<CHelper::BinaryFormat>::template op<Opts>(*value, ctx, b, ix);
            }
        }
    };

    template<class T>
    struct from<CHelper::BinaryFormat, std::optional<T>> {
        template<auto Opts>
        static void op(auto &&value, is_context auto &&ctx, auto &&it, auto &&end) {
            bool hasValue = false;
            from<CHelper::BinaryFormat, bool>::template op<Opts>(hasValue, ctx, it, end);
            if (hasValue) {
                value.emplace();
                from<CHelper::BinaryFormat, T>::template op<Opts>(*value, ctx, it, end);
            } else {
                value.reset();
            }
        }
    };

    // ================= shared_ptr / unique_ptr =================
    // 智能指针不允许为空，直接写目标值（与旧版二进制格式一致）
    template<class T>
    struct to<CHelper::BinaryFormat, std::shared_ptr<T>> {
        template<auto Opts>
        static void op(auto &&value, is_context auto &&ctx, auto &&b, auto &&ix) noexcept {
            assert(value != nullptr);
            serialize<CHelper::BinaryFormat>::template op<Opts>(*value, ctx, b, ix);
        }
    };

    template<class T>
    struct from<CHelper::BinaryFormat, std::shared_ptr<T>> {
        template<auto Opts>
        static void op(auto &&value, is_context auto &&ctx, auto &&it, auto &&end) {
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
            from<CHelper::BinaryFormat, T>::template op<Opts>(*value, ctx, it, end);
        }
    };

    template<class T>
    struct to<CHelper::BinaryFormat, std::unique_ptr<T>> {
        template<auto Opts>
        static void op(auto &&value, is_context auto &&ctx, auto &&b, auto &&ix) noexcept {
            assert(value != nullptr);
            serialize<CHelper::BinaryFormat>::template op<Opts>(*value, ctx, b, ix);
        }
    };

    template<class T>
    struct from<CHelper::BinaryFormat, std::unique_ptr<T>> {
        template<auto Opts>
        static void op(auto &&value, is_context auto &&ctx, auto &&it, auto &&end) {
            if (!value) {
                value = std::make_unique<T>();
            }
            from<CHelper::BinaryFormat, T>::template op<Opts>(*value, ctx, it, end);
        }
    };


    // ================= pair（依次写 first、second） =================
    template<class T>
        requires(is_std_pair_v<T>)
    struct to<CHelper::BinaryFormat, T> {
        template<auto Opts, class V, is_context Ctx, class B>
        static void op(V &&value, Ctx &&ctx, B &&b, auto &&ix) noexcept {
            serialize<CHelper::BinaryFormat>::template op<Opts>(value.first, ctx, b, ix);
            serialize<CHelper::BinaryFormat>::template op<Opts>(value.second, ctx, b, ix);
        }
    };

    template<class T>
        requires(is_std_pair_v<T>)
    struct from<CHelper::BinaryFormat, T> {
        template<auto Opts, class V, is_context Ctx, class It, class End>
        static void op(V &&value, Ctx &&ctx, It &&it, End &&end) {
            parse<CHelper::BinaryFormat>::template op<Opts>(value.first, ctx, it, end);
            parse<CHelper::BinaryFormat>::template op<Opts>(value.second, ctx, it, end);
        }
    };

    // ================= vector（uint32 数量 + 元素） =================
    template<class T>
        requires(is_std_vector_v<T>)
    struct to<CHelper::BinaryFormat, T> {
        template<auto Opts>
        static void op(auto &&value, is_context auto &&ctx, auto &&b, auto &&ix) noexcept {
            to<CHelper::BinaryFormat, std::uint32_t>::template op<Opts>(static_cast<std::uint32_t>(value.size()), ctx, b, ix);
            for (const auto &item: value) {
                if constexpr (is_vector_bool_v<T>) {
                    // 迭代器给的是位代理引用：先落成 bool 再写。
                    // libc++ 的代理是纯右值、不会像 MSVC 那样退化成 bool，
                    // 直接把代理当元素类型会实例化出未定义的 to<BinaryFormat, 代理类型>
                    const bool bit = item;
                    serialize<CHelper::BinaryFormat>::template op<Opts>(bit, ctx, b, ix);
                } else {
                    serialize<CHelper::BinaryFormat>::template op<Opts>(item, ctx, b, ix);
                }
            }
        }
    };

    template<class T>
        requires(is_std_vector_v<T>)
    struct from<CHelper::BinaryFormat, T> {
        template<auto Opts>
        static void op(auto &&value, is_context auto &&ctx, auto &&it, auto &&end) {
            std::uint32_t size = 0;
            from<CHelper::BinaryFormat, std::uint32_t>::template op<Opts>(size, ctx, it, end);
            value.clear();
            if (size > 0) {
                value.reserve(size);
                for (std::uint32_t i = 0; i < size; ++i) {
                    if constexpr (is_vector_bool_v<T>) {
                        // 同 to：back() 也是代理，不能取地址或绑定非 const 引用
                        bool bit = false;
                        from<CHelper::BinaryFormat, bool>::template op<Opts>(bit, ctx, it, end);
                        if (bool(ctx.error)) [[unlikely]] {
                            return;
                        }
                        value.push_back(bit);
                    } else {
                        from<CHelper::BinaryFormat, typename T::value_type>::template op<Opts>(
                                value.emplace_back(), ctx, it, end);
                        if (bool(ctx.error)) [[unlikely]] {
                            return;
                        }
                    }
                }
            }
        }
    };

    // ================= map（uint32 数量 + 键值对） =================
    template<class T>
        requires(glz::readable_map_t<T> || glz::writable_map_t<T>)
    struct to<CHelper::BinaryFormat, T> {
        template<auto Opts>
        static void op(auto &&value, is_context auto &&ctx, auto &&b, auto &&ix) noexcept {
            to<CHelper::BinaryFormat, std::uint32_t>::template op<Opts>(static_cast<std::uint32_t>(value.size()), ctx, b, ix);
            for (auto &[key, mapped]: value) {
                serialize<CHelper::BinaryFormat>::template op<Opts>(key, ctx, b, ix);
                serialize<CHelper::BinaryFormat>::template op<Opts>(mapped, ctx, b, ix);
            }
        }
    };

    template<class T>
        requires glz::readable_map_t<T>
    struct from<CHelper::BinaryFormat, T> {
        template<auto Opts>
        static void op(auto &&value, is_context auto &&ctx, auto &&it, auto &&end) {
            std::uint32_t size = 0;
            from<CHelper::BinaryFormat, std::uint32_t>::template op<Opts>(size, ctx, it, end);
            value.clear();
            for (std::uint32_t i = 0; i < size; ++i) {
                using Key = typename T::key_type;
                using Mapped = typename T::mapped_type;
                Key key{};
                from<CHelper::BinaryFormat, Key>::template op<Opts>(key, ctx, it, end);
                if (bool(ctx.error)) [[unlikely]] {
                    return;
                }
                auto [iter, inserted] = value.try_emplace(std::move(key));
                if (!inserted) [[unlikely]] {
                    ctx.error = error_code::syntax_error;
                    return;
                }
                from<CHelper::BinaryFormat, Mapped>::template op<Opts>(iter->second, ctx, it, end);
                if (bool(ctx.error)) [[unlikely]] {
                    return;
                }
            }
        }
    };

    // ================= glaze 反射对象（按 meta 成员顺序紧凑排列） =================
    // glaze_object_t（自定义 glz::meta）：成员是 meta 中的成员指针
    template<class T>
        requires(glz::glaze_object_t<T>)
    struct to<CHelper::BinaryFormat, T> {
        template<auto Opts, class V, is_context Ctx, class B>
        static void op(V &&value, Ctx &&ctx, B &&b, auto &&ix) noexcept {
            for_each<reflect<T>::size>([&]<auto I>() {
                decltype(auto) member = get<I>(reflect<T>::values);
                serialize<CHelper::BinaryFormat>::template op<Opts>(get_member(value, member), ctx, b, ix);
            });
        }
    };

    template<class T>
        requires(glz::glaze_object_t<T>)
    struct from<CHelper::BinaryFormat, T> {
        template<auto Opts, class V, is_context Ctx, class It, class End>
        static void op(V &&value, Ctx &&ctx, It &&it, End &&end) {
            for_each<reflect<T>::size>([&]<auto I>() {
                if (bool(ctx.error)) [[unlikely]] {
                    return;
                }
                decltype(auto) member = get<I>(reflect<T>::values);
                parse<CHelper::BinaryFormat>::template op<Opts>(get_member(value, member), ctx, it, end);
            });
        }
    };

    // reflectable（纯聚合体）：成员通过结构化绑定 to_tie 访问
    template<class T>
        requires(glz::reflectable<T>)
    struct to<CHelper::BinaryFormat, T> {
        template<auto Opts, class V, is_context Ctx, class B>
        static void op(V &&value, Ctx &&ctx, B &&b, auto &&ix) noexcept {
            decltype(auto) tie = to_tie(value);
            for_each<reflect<T>::size>([&]<auto I>() {
                serialize<CHelper::BinaryFormat>::template op<Opts>(get<I>(tie), ctx, b, ix);
            });
        }
    };

    template<class T>
        requires(glz::reflectable<T>)
    struct from<CHelper::BinaryFormat, T> {
        template<auto Opts, class V, is_context Ctx, class It, class End>
        static void op(V &&value, Ctx &&ctx, It &&it, End &&end) {
            decltype(auto) tie = to_tie(value);
            for_each<reflect<T>::size>([&]<auto I>() {
                if (bool(ctx.error)) [[unlikely]] {
                    return;
                }
                parse<CHelper::BinaryFormat>::template op<Opts>(get<I>(tie), ctx, it, end);
            });
        }
    };
}// namespace glz

#endif//CHELPER_BINARY_FORMAT_H

// ================= 通用 I/O 辅助函数（所有文件通过 pch.h 可用） =================
namespace CHelper {

#ifndef CHELPER_NO_FILESYSTEM
    // 读取整个文件（资源 JSON 加载用）
    inline std::string readFileToString(const std::filesystem::path &path) {
        std::ifstream is(path, std::ios::binary);
        if (!is.is_open()) [[unlikely]] {
            throw std::runtime_error("fail to open file: " + path.string());
        }
        is.seekg(0, std::ios::end);
        const auto fileSize = is.tellg();
        if (fileSize == std::streampos(-1)) [[unlikely]] {
            throw std::runtime_error("fail to get file size: " + path.string());
        }
        std::string content(static_cast<size_t>(fileSize), '\0');
        is.seekg(0, std::ios::beg);
        if (!is) [[unlikely]] {
            throw std::runtime_error("fail to seek file: " + path.string());
        }
        if (!content.empty() && !is.read(content.data(), static_cast<std::streamsize>(content.size()))) [[unlikely]] {
            throw std::runtime_error("fail to read file: " + path.string());
        }
        return content;
    }
#endif

    // JSON 读取（失败时抛出带定位信息的异常）
    // ctx 允许调用方携带自定义反序列化上下文（如 CPack 加载阶段），不传则使用默认上下文
    template<class T, class Ctx>
        requires glz::is_context<Ctx>
    void readJson(T &value, const std::string_view buffer, Ctx &ctx) {
        const auto ec = glz::read<glz::opts{}>(value, buffer, ctx);
        if (bool(ec)) [[unlikely]] {
            throw std::runtime_error("fail to parse json: " + glz::format_error(ec, buffer));
        }
    }

    template<class T>
    void readJson(T &value, const std::string_view buffer) {
        glz::context ctx{};
        readJson(value, buffer, ctx);
    }

#ifndef CHELPER_NO_FILESYSTEM
    template<class T, class Ctx>
        requires glz::is_context<Ctx>
    void readJsonFromFile(T &value, const std::filesystem::path &path, Ctx &ctx) {
        std::string buffer = readFileToString(path);
        readJson(value, buffer, ctx);
    }

    template<class T>
    void readJsonFromFile(T &value, const std::filesystem::path &path) {
        glz::context ctx{};
        readJsonFromFile(value, path, ctx);
    }
#endif

    // JSON 写出（紧凑格式）
    template<class T>
    [[nodiscard]] std::string writeJson(const T &value) {
        std::string buffer;
        const auto ec = glz::write_json(value, buffer);
        if (bool(ec)) [[unlikely]] {
            throw std::runtime_error("fail to write json");
        }
        return buffer;
    }

    // MessagePack 读写
    template<class T>
    void writeMsgpack(std::string &buffer, const T &value) {
        const auto ec = glz::write_msgpack(value, buffer);
        if (bool(ec)) [[unlikely]] {
            throw std::runtime_error("fail to write msgpack");
        }
    }

    template<class T>
    void readMsgpack(T &value, const std::string_view buffer) {
        const auto ec = glz::read_msgpack(value, buffer);
        if (bool(ec)) [[unlikely]] {
            throw std::runtime_error("fail to parse msgpack: " + glz::format_error(ec, buffer));
        }
    }

    // 自定义二进制格式（.cpack / old2new.dat）
    template<class T>
    void writeBinary(std::string &buffer, const T &value) {
        glz::context ctx{};
        const auto ec = glz::write<glz::opts{.format = CHelper::BinaryFormat}>(value, buffer, ctx);
        if (bool(ec)) [[unlikely]] {
            throw std::runtime_error("fail to write binary");
        }
    }

    template<class T, class Ctx>
        requires glz::is_context<Ctx>
    void readBinary(T &value, const std::string_view buffer, Ctx &ctx) {
        const auto ec = glz::read<glz::opts{.format = CHelper::BinaryFormat}>(value, buffer, ctx);
        if (bool(ec)) [[unlikely]] {
            throw std::runtime_error("fail to parse binary: " + glz::format_error(ec, buffer));
        }
    }

    template<class T>
    void readBinary(T &value, const std::string_view buffer) {
        glz::context ctx{};
        readBinary(value, buffer, ctx);
    }
}// namespace CHelper
