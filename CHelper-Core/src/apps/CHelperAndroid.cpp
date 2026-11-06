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

#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <chelper/CHelperCore.h>
#include <chelper/serialization/IO.h>
#include <chelper/serialization/Serialization.h>
#include <jni.h>
#include <pch.h>
#include <spdlog/sinks/android_sink.h>

namespace {
    template<class T>
    class LocalRef {
        JNIEnv *env;
        T value;

    public:
        LocalRef(JNIEnv *env, T value) : env(env), value(value) {}
        ~LocalRef() {
            if (value) env->DeleteLocalRef(value);
        }
        LocalRef(const LocalRef &) = delete;
        T get() const { return value; }
        T release() { return std::exchange(value, nullptr); }
    };
    void checkJni([[maybe_unused]] JNIEnv *env) {
#if CHelperDebug
        // 字段 ID、对象类型、数组范围由本层保证；只在调试时验证这些不变量。
        if (env->ExceptionCheck()) throw std::runtime_error("JNI operation failed");
#endif
    }
    template<class T>
    T requireJni(JNIEnv *env, T value) {
        // 这些分配/查找函数通过空返回值表示失败；正常路径不再调用 ExceptionCheck。
        if (!value) throw std::runtime_error("JNI allocation or lookup failed");
        return value;
    }
    jsize checkedSize(size_t value) {
        if (value > static_cast<size_t>(std::numeric_limits<jsize>::max())) throw std::length_error("JNI result is too large");
        return static_cast<jsize>(value);
    }
    void throwJavaError(JNIEnv *env, const char *message) noexcept {
        if (env->ExceptionCheck()) return;
        LocalRef<jclass> type(env, env->FindClass("java/lang/RuntimeException"));
        if (type.get()) env->ThrowNew(type.get(), message);
    }
    template<class Function>
    auto jniCall(JNIEnv *env, Function &&function) noexcept -> std::invoke_result_t<Function> {
        using Result = std::invoke_result_t<Function>;
        try {
            return std::invoke(std::forward<Function>(function));
        } catch (const std::exception &error) { throwJavaError(env, error.what()); } catch (...) {
            throwJavaError(env, "unknown CHelper native error");
        }
        if constexpr (!std::is_void_v<Result>) return Result{};
    }
    struct JavaBindings {
        jclass errorType = nullptr, suggestionType = nullptr, clickType = nullptr;
        jfieldID errorText = nullptr, errorStart = nullptr, errorEnd = nullptr;
        jfieldID suggestionName = nullptr, suggestionDescription = nullptr;
        jfieldID clickText = nullptr, clickSelection = nullptr;
        void initialize(JNIEnv *env) {
            const auto load = [&](const char *name, jclass &type) {
                LocalRef<jclass> local(env, requireJni(env, env->FindClass(name)));
                type = static_cast<jclass>(requireJni(env, env->NewGlobalRef(local.get())));
            };
            load("yancey/chelper/core/ErrorReason", errorType);
            load("yancey/chelper/core/Suggestion", suggestionType);
            load("yancey/chelper/core/ClickSuggestionResult", clickType);
            errorText = requireJni(env, env->GetFieldID(errorType, "errorReason", "Ljava/lang/String;"));
            errorStart = requireJni(env, env->GetFieldID(errorType, "start", "I"));
            errorEnd = requireJni(env, env->GetFieldID(errorType, "end", "I"));
            suggestionName = requireJni(env, env->GetFieldID(suggestionType, "name", "Ljava/lang/String;"));
            suggestionDescription = requireJni(env, env->GetFieldID(suggestionType, "description", "Ljava/lang/String;"));
            clickText = requireJni(env, env->GetFieldID(clickType, "text", "Ljava/lang/String;"));
            clickSelection = requireJni(env, env->GetFieldID(clickType, "selection", "I"));
        }
        void clear(JNIEnv *env) noexcept {
            for (auto type: {errorType, suggestionType, clickType})
                if (type) env->DeleteGlobalRef(type);
            errorType = suggestionType = clickType = nullptr;
        }
    } bindings;

    class JavaStringChars {
        JNIEnv *env;
        jstring value;
        const jchar *characters;
        jsize length;

    public:
        JavaStringChars(JNIEnv *env, jstring value) : env(env), value(value) {
            if (!value) throw std::invalid_argument("null Java string");
            length = env->GetStringLength(value);
            characters = requireJni(env, env->GetStringChars(value, nullptr));
        }
        ~JavaStringChars() { env->ReleaseStringChars(value, characters); }
        JavaStringChars(const JavaStringChars &) = delete;
        std::u16string_view view() const {
            return {reinterpret_cast<const char16_t *>(characters), static_cast<size_t>(length)};
        }
    };

    template<class Consumer>
    auto withAssetData(JNIEnv *env, jobject managerObject, const std::string &path, Consumer &&consume) {
        if (!managerObject) throw std::invalid_argument("null Android asset manager");
        auto *manager = AAssetManager_fromJava(env, managerObject);
        if (!manager) throw std::runtime_error("invalid Android asset manager");
        const std::unique_ptr<AAsset, decltype(&AAsset_close)> asset(
                AAssetManager_open(manager, path.c_str(), AASSET_MODE_BUFFER), AAsset_close);
        if (!asset) throw std::runtime_error("cannot open asset: " + path);
        const auto length = AAsset_getLength64(asset.get());
        if (length < 0) throw std::runtime_error("failed to read asset size");
        // Decode while the asset owns the buffer; the decoded pack owns its data.
        if (length == 0) return std::invoke(std::forward<Consumer>(consume), std::string_view{});
        if (const auto *buffer = AAsset_getBuffer(asset.get())) {
            return std::invoke(std::forward<Consumer>(consume), std::string_view(static_cast<const char *>(buffer), static_cast<size_t>(length)));
        }
        if (AAsset_seek64(asset.get(), 0, SEEK_SET) < 0) throw std::runtime_error("failed to seek asset: " + path);
        std::string data(static_cast<size_t>(length), '\0');
        size_t offset = 0;
        while (offset < data.size()) {
            const int count = AAsset_read(asset.get(), data.data() + offset, data.size() - offset);
            if (count <= 0) throw std::runtime_error("failed to read asset: " + path);
            offset += static_cast<size_t>(count);
        }
        return std::invoke(std::forward<Consumer>(consume), std::string_view(data));
    }
}// namespace

std::u16string jstring2u16string(JNIEnv *env, jstring value) {
    const JavaStringChars characters(env, value);
    return std::u16string(characters.view());
}

jstring u16string2jstring(JNIEnv *env, std::u16string_view value) {
    return requireJni(env, env->NewString(reinterpret_cast<const jchar *>(value.data()), checkedSize(value.size())));
}

std::string jstring2string(JNIEnv *env, jstring value) {
    const JavaStringChars characters(env, value);
    return utf8::utf16to8(characters.view());
}

jobject errorReason2jobject(JNIEnv *env, jclass errorReasonClass, const CHelper::ErrorReason &errorReason) {
    // 三个 DTO 的所有字段均由下方 native 代码写入，无需运行随后被覆盖的默认初始化。
    LocalRef<jobject> result(env, requireJni(env, env->AllocObject(errorReasonClass)));
    LocalRef<jstring> text(env, u16string2jstring(env, errorReason.getMessage()));
    env->SetObjectField(result.get(), bindings.errorText, text.get());
    env->SetIntField(result.get(), bindings.errorStart, checkedSize(errorReason.start));
    env->SetIntField(result.get(), bindings.errorEnd, checkedSize(errorReason.end));
    checkJni(env);
    return result.release();
}

jobjectArray errorReasons2jobjectArray(JNIEnv *env, const std::vector<std::shared_ptr<const CHelper::ErrorReason>> &errorReasons) {
    LocalRef<jobjectArray> result(env, requireJni(env, env->NewObjectArray(checkedSize(errorReasons.size()), bindings.errorType, nullptr)));
    for (size_t i = 0; i < errorReasons.size(); ++i) {
        LocalRef<jobject> entry(env, errorReason2jobject(env, bindings.errorType, *errorReasons[i]));
        env->SetObjectArrayElement(result.get(), static_cast<jsize>(i), entry.get());
        checkJni(env);
    }
    return result.release();
}

jobject suggestion2jobject(JNIEnv *env, jclass suggestionClass, const CHelper::AutoSuggestion::Suggestion &suggestion) {
    LocalRef<jobject> result(env, requireJni(env, env->AllocObject(suggestionClass)));
    LocalRef<jstring> name(env, u16string2jstring(env, suggestion.content->name));
    LocalRef<jstring> description(env, suggestion.content->description ? u16string2jstring(env, *suggestion.content->description) : nullptr);
    env->SetObjectField(result.get(), bindings.suggestionName, name.get());
    env->SetObjectField(result.get(), bindings.suggestionDescription, description.get());
    checkJni(env);
    return result.release();
}

jobjectArray suggestions2jobjectArray(JNIEnv *env, const std::vector<CHelper::AutoSuggestion::Suggestion> &suggestions) {
    LocalRef<jobjectArray> result(env, requireJni(env, env->NewObjectArray(checkedSize(suggestions.size()), bindings.suggestionType, nullptr)));
    for (size_t i = 0; i < suggestions.size(); ++i) {
        LocalRef<jobject> entry(env, suggestion2jobject(env, bindings.suggestionType, suggestions[i]));
        env->SetObjectArrayElement(result.get(), static_cast<jsize>(i), entry.get());
        checkJni(env);
    }
    return result.release();
}

jintArray syntaxTokenTypes2jintArray(JNIEnv *env, const std::vector<CHelper::SyntaxHighlight::SyntaxTokenType::SyntaxTokenType> &tokenTypes) {
    LocalRef<jintArray> result(env, requireJni(env, env->NewIntArray(checkedSize(tokenTypes.size()))));
    const std::vector<jint> values(tokenTypes.begin(), tokenTypes.end());
    if (!values.empty()) env->SetIntArrayRegion(result.get(), 0, checkedSize(values.size()), values.data());
    checkJni(env);
    return result.release();
}

jobject clickSuggestionResult2jobject(JNIEnv *env, const std::pair<std::u16string, size_t> &result) {
    LocalRef<jobject> output(env, requireJni(env, env->AllocObject(bindings.clickType)));
    LocalRef<jstring> text(env, u16string2jstring(env, result.first));
    env->SetObjectField(output.get(), bindings.clickText, text.get());
    env->SetIntField(output.get(), bindings.clickSelection, checkedSize(result.second));
    checkJni(env);
    return output.release();
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    try {
        bindings.initialize(env);
        spdlog::set_default_logger(spdlog::android_logger_mt("android", "CHelperNative"));
        return JNI_VERSION_1_6;
    } catch (...) {
        bindings.clear(env);
        return JNI_ERR;
    }
}

extern "C" [[maybe_unused]] JNIEXPORT jlong JNICALL
Java_yancey_chelper_core_CHelperCore_create0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jobject assetManager, jstring cpack_path) {
    return jniCall(env, [&]() -> jlong {
        const auto path = jstring2string(env, cpack_path);
        // A null AssetManager denotes a filesystem path, never binary file contents.
        const auto create = [](std::string_view data) {
            auto pack = CHelper::serialization::createCPackByBinary(data);
            return reinterpret_cast<jlong>(new CHelper::CHelperCore(std::move(pack)));
        };
        if (assetManager) return withAssetData(env, assetManager, path, create);
        return create(CHelper::readFileToString(std::filesystem::path(path)));
    });
}

extern "C" [[maybe_unused]] JNIEXPORT void JNICALL
Java_yancey_chelper_core_CHelperCore_release0(
        [[maybe_unused]] JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer) {
    return jniCall(env, [&]() -> void {
        delete reinterpret_cast<CHelper::CHelperCore *>(pointer);
    });
}

extern "C" [[maybe_unused]] JNIEXPORT jlong JNICALL
Java_yancey_chelper_core_CHelperCore_createContext0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer, jstring command) {
    return jniCall(env, [&]() -> jlong {
        const auto *core = reinterpret_cast<const CHelper::CHelperCore *>(pointer);
        if (!core) return jlong{};
        const JavaStringChars characters(env, command);
        return reinterpret_cast<jlong>(core->createContext(characters.view()));
    });
}

CHelper::Old2New::BlockFixData blockFixData0;

extern "C" [[maybe_unused]] JNIEXPORT jboolean JNICALL
Java_yancey_chelper_core_CHelperCore_old2newInit0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jobject assetManager, jstring blockFixDataPath) {
    return jniCall(env, [&]() -> jboolean {
        return withAssetData(env, assetManager, jstring2string(env, blockFixDataPath), [](std::string_view data) -> jboolean {
            blockFixData0 = CHelper::Old2New::blockFixDataFromBinary(data);
            return JNI_TRUE;
        });
    });
}

extern "C" [[maybe_unused]] JNIEXPORT jstring JNICALL
Java_yancey_chelper_core_CHelperCore_old2new0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jstring old) {
    return jniCall(env, [&]() -> jstring {
        if (!old) return nullptr;
        // Kotlin initializes this table before exposing conversion calls.
        return u16string2jstring(env, CHelper::CHelperCore::old2new(blockFixData0, jstring2u16string(env, old)));
    });
}

// 和CHelperCore不同，CommandContext没有可变状态，
// 所有操作都由调用方传入位置参数，因此可以把同一个CommandContext
// 交给多个线程同时读取，也可以创建多个CommandContext并行工作

extern "C" [[maybe_unused]] JNIEXPORT void JNICALL
Java_yancey_chelper_core_CommandContext_release0(
        [[maybe_unused]] JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer) {
    return jniCall(env, [&]() -> void {
        CHelper::CHelperCore::deleteContext(reinterpret_cast<CHelper::CommandContext *>(pointer));
    });
}

extern "C" [[maybe_unused]] JNIEXPORT jstring JNICALL
Java_yancey_chelper_core_CommandContext_command0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer) {
    return jniCall(env, [&]() -> jstring {
        auto *context = reinterpret_cast<CHelper::CommandContext *>(pointer);
        if (context == nullptr) [[unlikely]] {
            SPDLOG_WARN("call Java_yancey_chelper_core_CommandContext_command0 when context is nullptr");
            return nullptr;
        }
        return u16string2jstring(env, context->getCommand());
    });
}

extern "C" [[maybe_unused]] JNIEXPORT jstring JNICALL
Java_yancey_chelper_core_CommandContext_getStructure0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer) {
    return jniCall(env, [&]() -> jstring {
        auto *context = reinterpret_cast<CHelper::CommandContext *>(pointer);
        if (context == nullptr) [[unlikely]] {
            SPDLOG_WARN("call Java_yancey_chelper_core_CommandContext_getStructure0 when context is nullptr");
            return nullptr;
        }
        return u16string2jstring(env, context->getStructure());
    });
}

extern "C" [[maybe_unused]] JNIEXPORT jstring JNICALL
Java_yancey_chelper_core_CommandContext_getParamHint0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer, jint index) {
    return jniCall(env, [&]() -> jstring {
        auto *context = reinterpret_cast<CHelper::CommandContext *>(pointer);
        if (context == nullptr) [[unlikely]] {
            SPDLOG_WARN("call Java_yancey_chelper_core_CommandContext_getParamHint0 when context is nullptr");
            return nullptr;
        }
        return u16string2jstring(env, context->getParamHint(index));
    });
}

extern "C" [[maybe_unused]] JNIEXPORT jobjectArray JNICALL
Java_yancey_chelper_core_CommandContext_getErrorReasons0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer) {
    return jniCall(env, [&]() -> jobjectArray {
        auto *context = reinterpret_cast<CHelper::CommandContext *>(pointer);
        if (context == nullptr) [[unlikely]] {
            SPDLOG_WARN("call Java_yancey_chelper_core_CommandContext_getErrorReasons0 when context is nullptr");
            return errorReasons2jobjectArray(env, {});
        }
        return errorReasons2jobjectArray(env, context->getErrorReasons());
    });
}

extern "C" [[maybe_unused]] JNIEXPORT jint JNICALL
Java_yancey_chelper_core_CommandContext_getSuggestionsSize0(
        [[maybe_unused]] JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer, jint index) {
    return jniCall(env, [&]() -> jint {
        auto *context = reinterpret_cast<CHelper::CommandContext *>(pointer);
        if (context == nullptr) [[unlikely]] {
            SPDLOG_WARN("call Java_yancey_chelper_core_CommandContext_getSuggestionsSize0 when context is nullptr");
            return 0;
        }
        return static_cast<jint>(context->getSuggestions(index).size());
    });
}

extern "C" [[maybe_unused]] JNIEXPORT jobject JNICALL
Java_yancey_chelper_core_CommandContext_getSuggestion0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer, jint index, jint which) {
    return jniCall(env, [&]() -> jobject {
        auto *context = reinterpret_cast<CHelper::CommandContext *>(pointer);
        if (context == nullptr) [[unlikely]] {
            SPDLOG_WARN("call Java_yancey_chelper_core_CommandContext_getSuggestion0 when context is nullptr");
            return nullptr;
        }
        if (which < 0) [[unlikely]] {
            SPDLOG_WARN("call Java_yancey_chelper_core_CommandContext_getSuggestion0 when which < 0");
            return nullptr;
        }
        std::vector<CHelper::AutoSuggestion::Suggestion> suggestions = context->getSuggestions(index);
        if (static_cast<jint>(suggestions.size()) <= which) [[unlikely]] {
            SPDLOG_WARN("call Java_yancey_chelper_core_CommandContext_getSuggestion0 when suggestions.size() <= which");
            return nullptr;
        }
        return suggestion2jobject(env, bindings.suggestionType, suggestions[static_cast<size_t>(which)]);
    });
}

extern "C" [[maybe_unused]] JNIEXPORT jobjectArray JNICALL
Java_yancey_chelper_core_CommandContext_getSuggestions0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer, jint index) {
    return jniCall(env, [&]() -> jobjectArray {
        auto *context = reinterpret_cast<CHelper::CommandContext *>(pointer);
        if (context == nullptr) [[unlikely]] {
            SPDLOG_WARN("call Java_yancey_chelper_core_CommandContext_getSuggestions0 when context is nullptr");
            return suggestions2jobjectArray(env, {});
        }
        return suggestions2jobjectArray(env, context->getSuggestions(index));
    });
}

extern "C" [[maybe_unused]] JNIEXPORT jint JNICALL
Java_yancey_chelper_core_CommandContext_getNodeCount0(
        [[maybe_unused]] JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer) {
    return jniCall(env, [&]() -> jint {
        auto *context = reinterpret_cast<CHelper::CommandContext *>(pointer);
        if (context == nullptr) [[unlikely]] {
            SPDLOG_WARN("call Java_yancey_chelper_core_CommandContext_getNodeCount0 when context is nullptr");
            return 0;
        }
        return static_cast<jint>(context->getNodeCount());
    });
}

extern "C" [[maybe_unused]] JNIEXPORT jobject JNICALL
Java_yancey_chelper_core_CommandContext_applySuggestion0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer, jint index, jint which) {
    return jniCall(env, [&]() -> jobject {
        auto *context = reinterpret_cast<CHelper::CommandContext *>(pointer);
        if (context == nullptr) [[unlikely]] {
            SPDLOG_WARN("call Java_yancey_chelper_core_CommandContext_applySuggestion0 when context is nullptr");
            return nullptr;
        }
        std::optional<std::pair<std::u16string, size_t>> result = context->applySuggestion(index, which);
        if (result.has_value()) [[likely]] {
            return clickSuggestionResult2jobject(env, result.value());
        } else {
            return nullptr;
        }
    });
}

extern "C" [[maybe_unused]] JNIEXPORT jintArray JNICALL
Java_yancey_chelper_core_CommandContext_getColors0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer) {
    return jniCall(env, [&]() -> jintArray {
        auto *context = reinterpret_cast<CHelper::CommandContext *>(pointer);
        if (context == nullptr) [[unlikely]] {
            SPDLOG_WARN("call Java_yancey_chelper_core_CommandContext_getColors0 when context is nullptr");
            return nullptr;
        }
        return syntaxTokenTypes2jintArray(env, context->getSyntaxResult().tokenTypes);
    });
}

extern "C" JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *) {
    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_OK) bindings.clear(env);
}
