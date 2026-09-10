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

#include <chelper/CHelperCore.h>
#include <emscripten/emscripten.h>

#include <cstdint>
#include <cstring>

namespace {

    // 用于向JS返回数据的缓冲区，JS侧是单线程的，可以安全复用
    std::vector<std::uint8_t> buffer;

    // ---- 手写内存协议（与 CHelper-Web/src/core/libCHelperWeb.js 一一对应）----
    // 规则：
    //   1. 所有 uint32 字段都位于 4 字节对齐地址
    //   2. UTF-16 字符串按 uint16 连续存储（只需 2 字节对齐）
    //   3. 可变长字符串之后如果还有 uint32 字段，必须补齐到 4 字节对齐
    //   4. buffer 大小计算与真实写入使用完全相同的规则（同一套 align4/offset）
    // 所有布局都基于 "4 字节对齐的起始位置" 计算 offset，u32 写入统一走 writeU32
    // （memcpy，不依赖指针解引用的对齐假设），杜绝未对齐 uint32_t* 解引用。

    // 4 字节向上对齐：C++ 与 JS 必须使用完全相同的公式
    static size_t align4(size_t value) {
        return (value + 3) & ~size_t(3);
    }

    // buffer.data() 与下一个 4 字节对齐地址之间的起始 padding 字节数（0~3）
    static size_t startPadding() {
        size_t address = reinterpret_cast<size_t>(buffer.data());
        return align4(address) - address;
    }

    // 把 buffer 扩容到能容纳 contentSize 字节内容（不含起始 padding），返回起始 padding。
    // 先预留 contentSize + 3 字节把 data() 地址定住，再收缩到实际大小，
    // 保证 prepare 之后 buffer 不会再扩容、data() 地址不再变化。
    // 起始 padding 是协议的一部分，必须真实存在于 buffer 中。
    static size_t prepareBuffer(size_t contentSize) {
        buffer.resize(contentSize + 3);
        size_t padding = startPadding();
        buffer.resize(padding + contentSize);
        return padding;
    }

    // 在 buffer 的 offset 处写入一个 4 字节无符号整数；
    // 调用方保证 (buffer.data() + offset) % 4 == 0
    static size_t writeU32(size_t offset, std::uint32_t value) {
        std::memcpy(buffer.data() + offset, &value, sizeof(value));
        return offset + sizeof(value);
    }

    // 写入 UTF-16 字符串: [u32 长度][u16 数据]，返回数据末尾的位置
    static size_t writeUtf16(size_t offset, const std::u16string &string) {
        offset = writeU32(offset, static_cast<std::uint32_t>(string.size()));
        if (!string.empty()) {
            std::memcpy(buffer.data() + offset, string.data(), string.size() * sizeof(char16_t));
        }
        return offset + string.size() * sizeof(char16_t);
    }

    // 布局（全部相对 4 字节对齐的起始位置）:
    //   u16字符串     [u32 长度][u16 数据]
    //   错误列表      [u32 数量]([u32 start][u32 end][u32 长度][u16 数据][补齐到4])*
    //   补全列表      [u32 数量]([u32 name长度][u32 description长度][u16 name][u16 description][补齐到4])*
    //   单条补全      [u32 name长度][u32 description长度][u16 name][u16 description]
    //   点击结果      [u32 光标位置][u32 长度][u16 数据]
    //   语法token     [u32 数量][u8 数据]*

    // 布局: [u32 长度][u16 字符串]
    const uint8_t *writeU16String(const std::u16string &string) {
        size_t offset = prepareBuffer(4 + string.size() * sizeof(char16_t));
        writeUtf16(offset, string);
        return buffer.data();
    }

    // 布局: [u32 数量]([u32 start][u32 end][u32 长度][u16 字符串][补齐到4])*
    const uint8_t *writeErrorReasons(const std::vector<std::shared_ptr<CHelper::ErrorReason>> &errorReasons) {
        size_t contentSize = sizeof(std::uint32_t);
        for (const auto &item: errorReasons) {
            // 每条记录: start(4) + end(4) + 字符串长度(4) + 数据(len*2)，之后补齐到 4
            contentSize = align4(contentSize + 12 + item->errorReason.size() * sizeof(char16_t));
        }
        size_t offset = prepareBuffer(contentSize);
        offset = writeU32(offset, static_cast<std::uint32_t>(errorReasons.size()));
        for (const auto &item: errorReasons) {
            offset = writeU32(offset, static_cast<std::uint32_t>(item->start));
            offset = writeU32(offset, static_cast<std::uint32_t>(item->end));
            offset = writeUtf16(offset, item->errorReason);
            offset = align4(offset);
        }
        return buffer.data();
    }

    // 一条补全建议的内容字节数（不含记录间 padding）
    size_t getSuggestionBytes(const CHelper::AutoSuggestion::Suggestion &suggestion) {
        size_t size = 8;// name长度 + description长度
        size += suggestion.content->name.size() * sizeof(char16_t);
        if (suggestion.content->description.has_value()) {
            size += suggestion.content->description->size() * sizeof(char16_t);
        }
        return size;
    }

    // 写入一条补全建议: [u32 name长度][u32 description长度][u16 name][u16 description]
    // 返回数据末尾的位置（不含记录间 padding，由调用方决定是否补齐）。
    // 注意：name/description 的长度已作为 u32 写在前面，这里只写 u16 数据本身，
    // 不能使用带长度前缀的 writeUtf16。
    size_t writeSuggestion(size_t offset, const CHelper::AutoSuggestion::Suggestion &suggestion) {
        const std::u16string &name = suggestion.content->name;
        const std::optional<std::u16string> &description = suggestion.content->description;
        size_t nameLength = name.size();
        size_t descriptionLength = description.has_value() ? description->size() : 0;
        offset = writeU32(offset, static_cast<std::uint32_t>(nameLength));
        offset = writeU32(offset, static_cast<std::uint32_t>(descriptionLength));
        if (nameLength != 0) {
            std::memcpy(buffer.data() + offset, name.data(), nameLength * sizeof(char16_t));
            offset += nameLength * sizeof(char16_t);
        }
        if (descriptionLength != 0) {
            std::memcpy(buffer.data() + offset, description->data(), descriptionLength * sizeof(char16_t));
            offset += descriptionLength * sizeof(char16_t);
        }
        return offset;
    }

    // 布局: [u32 数量]([u32 name长度][u32 description长度][u16 name][u16 description][补齐到4])*
    const uint8_t *writeSuggestions(const std::vector<CHelper::AutoSuggestion::Suggestion> &suggestions) {
        size_t contentSize = sizeof(std::uint32_t);
        for (const auto &item: suggestions) {
            // 每条记录结束后补齐到 4，保证下一条记录的 u32 字段对齐
            contentSize = align4(contentSize + getSuggestionBytes(item));
        }
        size_t offset = prepareBuffer(contentSize);
        offset = writeU32(offset, static_cast<std::uint32_t>(suggestions.size()));
        for (const auto &item: suggestions) {
            offset = writeSuggestion(offset, item);
            offset = align4(offset);
        }
        return buffer.data();
    }

    // 布局: [u32 光标位置][u32 长度][u16 字符串]
    const uint8_t *writeSuggestionClickResult(const std::pair<std::u16string, size_t> &result) {
        size_t offset = prepareBuffer(8 + result.first.size() * sizeof(char16_t));
        offset = writeU32(offset, static_cast<std::uint32_t>(result.second));
        offset = writeUtf16(offset, result.first);
        return buffer.data();
    }

    // 布局: [u32 数量][u8]*
    const uint8_t *writeSyntaxTokens(const std::vector<CHelper::SyntaxHighlight::SyntaxTokenType::SyntaxTokenType> &tokenTypes) {
        size_t offset = prepareBuffer(4 + tokenTypes.size());
        offset = writeU32(offset, static_cast<std::uint32_t>(tokenTypes.size()));
        if (!tokenTypes.empty()) {
            std::memcpy(buffer.data() + offset, tokenTypes.data(), tokenTypes.size());
        }
        return buffer.data();
    }

}// namespace

extern "C" {

EMSCRIPTEN_KEEPALIVE CHelper::CHelperCore *init(const char *cpackPtr, size_t cpackLength) {
    return CHelper::CHelperCore::createByBinary(std::string_view(cpackPtr, cpackLength));
}

EMSCRIPTEN_KEEPALIVE void release(const CHelper::CHelperCore *core) {
    delete core;
}

// 和CHelperCore不同，CommandContext没有可变状态，
// 所有操作都由调用方传入位置参数，因此可以把同一个CommandContext
// 交给多个线程同时读取，也可以创建多个CommandContext并行工作

EMSCRIPTEN_KEEPALIVE CHelper::CommandContext *createCommandContext(const CHelper::CHelperCore *core, const char16_t *command) {
    if (core == nullptr) [[unlikely]] {
        return nullptr;
    }
    try {
        return core->createContext(command);
    } catch (...) {
        return nullptr;
    }
}

EMSCRIPTEN_KEEPALIVE void releaseCommandContext(CHelper::CommandContext *context) {
    CHelper::CHelperCore::deleteContext(context);
}

EMSCRIPTEN_KEEPALIVE const uint8_t *contextGetCommand(const CHelper::CommandContext *context) {
    if (context == nullptr) [[unlikely]] {
        return nullptr;
    }
    return writeU16String(context->getCommand());
}

EMSCRIPTEN_KEEPALIVE const uint8_t *contextGetStructure(const CHelper::CommandContext *context) {
    if (context == nullptr) [[unlikely]] {
        return nullptr;
    }
    return writeU16String(context->getStructure());
}

EMSCRIPTEN_KEEPALIVE const uint8_t *contextGetParamHint(const CHelper::CommandContext *context, size_t index) {
    if (context == nullptr) [[unlikely]] {
        return nullptr;
    }
    return writeU16String(context->getParamHint(index));
}

EMSCRIPTEN_KEEPALIVE const uint8_t *contextGetErrorReasons(const CHelper::CommandContext *context) {
    if (context == nullptr) [[unlikely]] {
        return nullptr;
    }
    return writeErrorReasons(context->getErrorReasons());
}

EMSCRIPTEN_KEEPALIVE size_t contextGetSuggestionSize(const CHelper::CommandContext *context, size_t index) {
    if (context == nullptr) [[unlikely]] {
        return 0;
    }
    return context->getSuggestions(index).size();
}

EMSCRIPTEN_KEEPALIVE const uint8_t *contextGetSuggestion(const CHelper::CommandContext *context, size_t index, size_t which) {
    if (context == nullptr) [[unlikely]] {
        return nullptr;
    }
    std::vector<CHelper::AutoSuggestion::Suggestion> suggestions = context->getSuggestions(index);
    if (which >= suggestions.size()) {
        return nullptr;
    }
    // 单条补全建议不带数量前缀，也不需要在末尾补齐（后面没有别的字段）
    size_t offset = prepareBuffer(getSuggestionBytes(suggestions[which]));
    writeSuggestion(offset, suggestions[which]);
    return buffer.data();
}

EMSCRIPTEN_KEEPALIVE const uint8_t *contextGetAllSuggestions(const CHelper::CommandContext *context, size_t index) {
    if (context == nullptr) [[unlikely]] {
        return nullptr;
    }
    return writeSuggestions(context->getSuggestions(index));
}

EMSCRIPTEN_KEEPALIVE const uint8_t *contextApplySuggestion(const CHelper::CommandContext *context, size_t index, size_t which) {
    if (context == nullptr) [[unlikely]] {
        return nullptr;
    }
    auto result = context->applySuggestion(index, which);
    if (!result.has_value()) {
        return nullptr;
    }
    return writeSuggestionClickResult(result.value());
}

EMSCRIPTEN_KEEPALIVE const uint8_t *contextGetSyntaxTokens(const CHelper::CommandContext *context) {
    if (context == nullptr) [[unlikely]] {
        return nullptr;
    }
    return writeSyntaxTokens(context->getSyntaxResult().tokenTypes);
}

EMSCRIPTEN_KEEPALIVE size_t contextGetNodeCount(const CHelper::CommandContext *context) {
    if (context == nullptr) [[unlikely]] {
        return 0;
    }
    return context->getNodeCount();
}
}
