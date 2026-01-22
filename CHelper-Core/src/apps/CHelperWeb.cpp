//
// Created by Yancey on 2024/4/2.
//

#include <chelper/CHelperCore.h>
#include <emscripten/emscripten.h>
#include <emscripten/val.h>

std::vector<std::uint8_t> buffer;

extern "C" {

EMSCRIPTEN_KEEPALIVE CHelper::CHelperCore *init(const char *cpackPtr, size_t cpackLength) {
    return CHelper::CHelperCore::create([&cpackPtr, &cpackLength]() -> std::unique_ptr<CHelper::CPack> {
        std::string str = std::string(cpackPtr, cpackLength);
        std::istringstream iss(str);
        return CHelper::CPack::createByBinary(iss);
    });
}

EMSCRIPTEN_KEEPALIVE void release(const CHelper::CHelperCore *core) {
    delete core;
}

EMSCRIPTEN_KEEPALIVE void onTextChanged(CHelper::CHelperCore *core, const char16_t *content, size_t index) {
    if (core == nullptr) [[unlikely]] {
        return;
    }
    core->onTextChanged(content, index);
}

EMSCRIPTEN_KEEPALIVE void onSelectionChanged(CHelper::CHelperCore *core, size_t index) {
    if (core == nullptr) [[unlikely]] {
        return;
    }
    core->onSelectionChanged(index);
}

EMSCRIPTEN_KEEPALIVE const uint8_t *getStructure(const CHelper::CHelperCore *core) {
    if (core == nullptr) [[unlikely]] {
        return nullptr;
    }
    std::u16string structure = core->getStructure();
    buffer.resize((reinterpret_cast<size_t>(buffer.data()) % 4) + 4 + (structure.size() * 2));
    *reinterpret_cast<uint32_t *>(buffer.data()) = static_cast<uint32_t>(structure.size());
    memcpy((reinterpret_cast<size_t>(buffer.data()) % 4) + buffer.data() + 4, structure.data(), structure.size() * 2);
    return buffer.data();
}

EMSCRIPTEN_KEEPALIVE const uint8_t *getParamHint(const CHelper::CHelperCore *core) {
    if (core == nullptr) [[unlikely]] {
        return nullptr;
    }
    std::u16string description = core->getParamHint();
    buffer.resize((reinterpret_cast<size_t>(buffer.data()) % 4) + 4 + (description.size() * 2));
    *reinterpret_cast<uint32_t *>(buffer.data()) = static_cast<uint32_t>(description.size());
    memcpy((reinterpret_cast<size_t>(buffer.data()) % 4) + buffer.data() + 4, description.data(), description.size() * 2);
    return buffer.data();
}

EMSCRIPTEN_KEEPALIVE const uint8_t *getErrorReasons(const CHelper::CHelperCore *core) {
    if (core == nullptr) [[unlikely]] {
        return nullptr;
    }
    auto errorReasons = core->getErrorReasons();
    size_t size = (reinterpret_cast<size_t>(buffer.data()) % 4) + 4;
    for (const auto &item: errorReasons) {
        size = size + (reinterpret_cast<size_t>(buffer.data() + size) % 4) + 12 + item->errorReason.size() * 2;
    }
    buffer.resize(size);
    uint8_t *pointer = buffer.data();
    pointer += reinterpret_cast<size_t>(pointer) % 4;
    *reinterpret_cast<uint32_t *>(pointer) = static_cast<uint32_t>(errorReasons.size());
    pointer += 4;
    for (const auto &item: errorReasons) {
        pointer += reinterpret_cast<size_t>(pointer) % 4;
        *reinterpret_cast<uint32_t *>(pointer) = static_cast<uint32_t>(item->start);
        pointer += 4;
        *reinterpret_cast<uint32_t *>(pointer) = static_cast<uint32_t>(item->end);
        pointer += 4;
        *reinterpret_cast<uint32_t *>(pointer) = static_cast<uint32_t>(item->errorReason.size());
        pointer += 4;
        memcpy(pointer, item->errorReason.data(), item->errorReason.size() * 2);
        pointer += item->errorReason.size() * 2;
    }
    return buffer.data();
}

EMSCRIPTEN_KEEPALIVE size_t getSuggestionSize(CHelper::CHelperCore *core) {
    if (core == nullptr) [[unlikely]] {
        return 0;
    }
    auto suggestions = core->getSuggestions();
    if (suggestions == nullptr) {
        return 0;
    }
    return suggestions->size();
}

EMSCRIPTEN_KEEPALIVE const uint8_t *getSuggestion(CHelper::CHelperCore *core, size_t which) {
    if (core == nullptr) [[unlikely]] {
        return nullptr;
    }
    auto suggestions = core->getSuggestions();
    if (suggestions == nullptr || which >= suggestions->size()) {
        return nullptr;
    }
    const auto &suggestion = (*suggestions)[which];
    size_t nameSize = suggestion.content->name.size();
    size_t descriptionSize = suggestion.content->description.has_value() ? suggestion.content->description.value().size() : 0;
    buffer.resize((reinterpret_cast<size_t>(buffer.data()) % 4) + 8 + ((nameSize + descriptionSize) * 2));
    uint8_t *pointer = buffer.data();
    pointer += reinterpret_cast<size_t>(pointer) % 4;
    *reinterpret_cast<uint32_t *>(pointer) = static_cast<uint32_t>(nameSize);
    pointer += 4;
    *reinterpret_cast<uint32_t *>(pointer) = static_cast<uint32_t>(descriptionSize);
    pointer += 4;
    memcpy(pointer, suggestion.content->name.data(), nameSize * 2);
    pointer += nameSize * 2;
    if (descriptionSize != 0) {
        memcpy(pointer, suggestion.content->description.value().data(), descriptionSize * 2);
        // ReSharper disable once CppDFAUnusedValue
        pointer += descriptionSize * 2;
    }
    return buffer.data();
}

EMSCRIPTEN_KEEPALIVE const uint8_t *getAllSuggestions(CHelper::CHelperCore *core) {
    if (core == nullptr) [[unlikely]] {
        return nullptr;
    }
    auto suggestions = core->getSuggestions();
    if (suggestions == nullptr) {
        return nullptr;
    }
    size_t size = (reinterpret_cast<size_t>(buffer.data()) % 4) + 4;
    for (const auto &item: *suggestions) {
        size_t nameSize = item.content->name.size();
        size_t descriptionSize = item.content->description.has_value() ? item.content->description.value().size() : 0;
        size = size + (reinterpret_cast<size_t>(buffer.data() + size) % 4) + 8 + nameSize * 2 + descriptionSize * 2;
    }
    buffer.resize(size);
    uint8_t *pointer = buffer.data();
    pointer += reinterpret_cast<size_t>(pointer) % 4;
    *reinterpret_cast<uint32_t *>(pointer) = static_cast<uint32_t>(suggestions->size());
    pointer += 4;
    for (const auto &item: *suggestions) {
        size_t nameSize = item.content->name.size();
        size_t descriptionSize = item.content->description.has_value() ? item.content->description.value().size() : 0;
        pointer += reinterpret_cast<size_t>(pointer) % 4;
        *reinterpret_cast<uint32_t *>(pointer) = static_cast<uint32_t>(nameSize);
        pointer += 4;
        *reinterpret_cast<uint32_t *>(pointer) = static_cast<uint32_t>(descriptionSize);
        pointer += 4;
        memcpy(pointer, item.content->name.data(), nameSize * 2);
        pointer += nameSize * 2;
        if (descriptionSize != 0) {
            memcpy(pointer, item.content->description.value().data(), descriptionSize * 2);
            // ReSharper disable once CppDFAUnusedValue
            pointer += descriptionSize * 2;
        }
    }
    return buffer.data();
}

EMSCRIPTEN_KEEPALIVE uint8_t *onSuggestionClick(CHelper::CHelperCore *core, size_t which) {
    if (core == nullptr) [[unlikely]] {
        return nullptr;
    }
    auto result = core->onSuggestionClick(which);
    if (!result.has_value()) {
        return nullptr;
    }
    buffer.resize((reinterpret_cast<size_t>(buffer.data()) % 4) + 8 + (result->first.size() * 2));
    uint8_t *pointer = buffer.data();
    pointer += reinterpret_cast<size_t>(pointer) % 4;
    *reinterpret_cast<uint32_t *>(pointer) = static_cast<uint32_t>(result.value().second);
    pointer += 4;
    *reinterpret_cast<uint32_t *>(pointer) = static_cast<uint32_t>(result.value().first.size());
    pointer += 4;
    memcpy(pointer, result.value().first.data(), result.value().first.size() * 2);
    // ReSharper disable once CppDFAUnusedValue
    pointer += result.value().first.size() * 2;
    return buffer.data();
}

EMSCRIPTEN_KEEPALIVE uint8_t *getSyntaxTokens(CHelper::CHelperCore *core) {
    if (core == nullptr) [[unlikely]] {
        return nullptr;
    }
    auto result = core->getSyntaxResult();
    buffer.resize((reinterpret_cast<size_t>(buffer.data()) % 4) + 8 + result.tokenTypes.size());
    uint8_t *pointer = buffer.data();
    pointer += reinterpret_cast<size_t>(pointer) % 4;
    *reinterpret_cast<uint32_t *>(pointer) = static_cast<uint32_t>(result.tokenTypes.size());
    pointer += 4;
    memcpy(pointer, result.tokenTypes.data(), result.tokenTypes.size());
    // ReSharper disable once CppDFAUnusedValue
    pointer += result.tokenTypes.size();
    return buffer.data();
}
}