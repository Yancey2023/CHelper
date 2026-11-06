// PMR optional construction shared by all serialization entry points.
#pragma once

#include <chelper/util/CPackMemory.h>
#include <pch.h>

namespace glz {
    // JSON nullable reader 的构造扩展点；optional 内的 PMR 容器同样要捕获加载资源。
    template<class T>
        requires std::uses_allocator_v<T, std::pmr::polymorphic_allocator<std::byte>>
    struct meta<std::optional<T>> {
        static constexpr auto construct = [] {
            return std::optional<T>(std::in_place, CHelper::CPackMemoryRouter::getAllocationResource());
        };
    };

    // Glaze 的 shared_ptr 构造路径不使用 meta 构造器，显式传入 PMR 资源。
    template<class T>
        requires std::uses_allocator_v<T, std::pmr::polymorphic_allocator<std::byte>>
    struct from<JSON, std::shared_ptr<T>> {
        template<auto Options>
        static void op(auto &value, is_context auto &&ctx, auto &it, auto end) {
            constexpr auto Opts = ws_handled_off<Options>();
            if constexpr (!check_ws_handled(Options)) {
                if (skip_ws<Opts>(ctx, it, end)) return;
            }
            if (*it == 'n') {
                std::nullptr_t nil = nullptr;
                parse<JSON>::template op<Opts>(nil, ctx, it, end);
                if (!bool(ctx.error)) value.reset();
                return;
            }
            if (!value) value = std::make_shared<T>(CHelper::CPackMemoryRouter::getAllocationResource());
            parse<JSON>::template op<Opts>(*value, ctx, it, end);
        }
    };

    template<class T>
        requires std::uses_allocator_v<T, std::pmr::polymorphic_allocator<std::byte>>
    struct from<MSGPACK, std::optional<T>> {
        template<auto Opts>
        static void op(auto &&value, uint8_t tag, is_context auto &&ctx, auto &it, const auto &end) {
            if (tag == msgpack::nil) {
                value.reset();
                return;
            }
            if (!value) CHelper::emplaceScopedOptional(value);
            from<MSGPACK, T>::template op<Opts>(*value, tag, ctx, it, end);
        }
    };
}// namespace glz
