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
#include <jni.h>
#include <pch.h>
#include <spdlog/sinks/android_sink.h>

std::u16string jstring2u16string(JNIEnv *env, jstring jString) {
    if (jString == nullptr) [[unlikely]] {
        SPDLOG_WARN("call jstring2u16string when jString is null");
        return {};
    }
    jsize length = env->GetStringLength(jString);
    const jchar *jchars = env->GetStringChars(jString, nullptr);
    std::u16string str = std::u16string(reinterpret_cast<const char16_t *>(jchars), length);
    env->ReleaseStringChars(jString, jchars);
    return str;
}

jstring u16string2jstring(JNIEnv *env, const std::u16string_view u16string) {
    return env->NewString(reinterpret_cast<const jchar *>(u16string.data()), static_cast<jsize>(u16string.size()));
}

std::string jstring2string(JNIEnv *env, jstring jString) {
    if (jString == nullptr) [[unlikely]] {
        SPDLOG_WARN("call jstring2string when jString is null");
        return {};
    }
    const char *cstr = env->GetStringUTFChars(jString, nullptr);
    std::string str = cstr;
    env->ReleaseStringUTFChars(jString, cstr);
    return str;
}

jstring string2jstring(JNIEnv *env, const std::string &string) {
    return env->NewStringUTF(string.c_str());
}

jobject errorReason2jobject(JNIEnv *env, jclass errorReasonClass, const CHelper::ErrorReason &errorReason) {
    jobject javaErrorReason = env->AllocObject(errorReasonClass);
    env->SetObjectField(javaErrorReason,
                        env->GetFieldID(errorReasonClass, "errorReason", "Ljava/lang/String;"),
                        u16string2jstring(env, errorReason.errorReason));
    env->SetIntField(javaErrorReason,
                     env->GetFieldID(errorReasonClass, "start", "I"),
                     static_cast<jint>(errorReason.start));
    env->SetIntField(javaErrorReason,
                     env->GetFieldID(errorReasonClass, "end", "I"),
                     static_cast<jint>(errorReason.end));
    return javaErrorReason;
}

jobjectArray errorReasons2jobjectArray(JNIEnv *env, const std::vector<std::shared_ptr<CHelper::ErrorReason>> &errorReasons) {
    jclass errorReasonClass = env->FindClass("yancey/chelper/core/ErrorReason");
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(errorReasons.size()), errorReasonClass, nullptr);
    for (size_t i = 0; i < errorReasons.size(); ++i) {
        env->SetObjectArrayElement(result, i, errorReason2jobject(env, errorReasonClass, *errorReasons[i]));
    }
    return result;
}

jobject suggestion2jobject(JNIEnv *env, jclass suggestionClass, const CHelper::AutoSuggestion::Suggestion &suggestion) {
    jobject javaSuggestion = env->AllocObject(suggestionClass);
    env->SetObjectField(javaSuggestion,
                        env->GetFieldID(suggestionClass, "name", "Ljava/lang/String;"),
                        u16string2jstring(env, suggestion.content->name));
    env->SetObjectField(javaSuggestion,
                        env->GetFieldID(suggestionClass, "description", "Ljava/lang/String;"),
                        suggestion.content->description.has_value()
                                ? u16string2jstring(env, suggestion.content->description.value())
                                : nullptr);
    return javaSuggestion;
}

jobjectArray suggestions2jobjectArray(JNIEnv *env, const std::vector<CHelper::AutoSuggestion::Suggestion> &suggestions) {
    jclass suggestionClass = env->FindClass("yancey/chelper/core/Suggestion");
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(suggestions.size()), suggestionClass, nullptr);
    for (size_t i = 0; i < suggestions.size(); ++i) {
        env->SetObjectArrayElement(result, i, suggestion2jobject(env, suggestionClass, suggestions[i]));
    }
    return result;
}

jintArray syntaxTokenTypes2jintArray(JNIEnv *env, const std::vector<CHelper::SyntaxHighlight::SyntaxTokenType::SyntaxTokenType> &tokenTypes) {
    jintArray result = env->NewIntArray(static_cast<jsize>(tokenTypes.size()));
    std::vector<jint> tokenTypes0(tokenTypes.size());
    for (size_t i = 0; i < tokenTypes.size(); ++i) {
        tokenTypes0[i] = static_cast<jint>(tokenTypes[i]);
    }
    env->SetIntArrayRegion(result, 0, static_cast<jsize>(tokenTypes0.size()), tokenTypes0.data());
    return result;
}

jobject clickSuggestionResult2jobject(JNIEnv *env, const std::pair<std::u16string, size_t> &result) {
    jclass resultClass = env->FindClass("yancey/chelper/core/ClickSuggestionResult");
    jobject javaResult = env->AllocObject(resultClass);
    env->SetObjectField(javaResult,
                        env->GetFieldID(resultClass, "text", "Ljava/lang/String;"),
                        u16string2jstring(env, result.first));
    env->SetIntField(javaResult,
                     env->GetFieldID(resultClass, "selection", "I"),
                     static_cast<jint>(result.second));
    return javaResult;
}

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
#ifdef __ANDROID__
    spdlog::set_default_logger(spdlog::android_logger_mt("android", "CHelperNative"));
#endif
    return JNI_VERSION_1_6;
}

extern "C" [[maybe_unused]] JNIEXPORT jlong JNICALL
Java_yancey_chelper_core_CHelperCore_create0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jobject assetManager, jstring cpack_path) {
    if (cpack_path == nullptr) {
        SPDLOG_WARN("call Java_yancey_chelper_core_CHelperCore_create0 when cpack_path is null");
        return reinterpret_cast<jlong>(nullptr);
    }
    try {
        std::string cpackPath = jstring2string(env, cpack_path);
        if (assetManager == nullptr) [[unlikely]] {
            // 显式构造 string_view：Android 同时可见 createByBinary(path) 与
            // createByBinary(string_view) 两个重载，直接传 std::string 会产生歧义
            CHelper::CHelperCore *core = CHelper::CHelperCore::createByBinary(std::string_view(cpackPath));
            return reinterpret_cast<jlong>(core);
        } else {
            AAssetManager *mgr = AAssetManager_fromJava(env, assetManager);
            AAsset *asset = AAssetManager_open(mgr, cpackPath.c_str(), AASSET_MODE_BUFFER);
            if (asset == nullptr) [[unlikely]] {
                return reinterpret_cast<jlong>(nullptr);
            }
            auto dataFileSize = static_cast<size_t>(AAsset_getLength(asset));
            char *buffer = new char[dataFileSize];
            int numBytesRead = AAsset_read(asset, buffer, dataFileSize);
            AAsset_close(asset);
            CHelper::CHelperCore *core = CHelper::CHelperCore::createByBinary(std::string_view(buffer, numBytesRead));
            delete[] buffer;
            return reinterpret_cast<jlong>(core);
        }
    } catch (...) {
        SPDLOG_WARN("fail to init CHelper Core");
        return reinterpret_cast<jlong>(nullptr);
    }
}

extern "C" [[maybe_unused]] JNIEXPORT void JNICALL
Java_yancey_chelper_core_CHelperCore_release0(
        [[maybe_unused]] JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer) {
    delete reinterpret_cast<CHelper::CHelperCore *>(pointer);
}

extern "C" [[maybe_unused]] JNIEXPORT jlong JNICALL
Java_yancey_chelper_core_CHelperCore_createContext0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer, jstring command) {
    auto *core = reinterpret_cast<CHelper::CHelperCore *>(pointer);
    if (core == nullptr) [[unlikely]] {
        SPDLOG_WARN("call Java_yancey_chelper_core_CHelperCore_createContext0 when core is nullptr");
        return reinterpret_cast<jlong>(nullptr);
    }
    if (command == nullptr) [[unlikely]] {
        SPDLOG_WARN("call Java_yancey_chelper_core_CHelperCore_createContext0 when command is null");
        return reinterpret_cast<jlong>(nullptr);
    }
    try {
        auto *context = core->createContext(jstring2u16string(env, command));
        return reinterpret_cast<jlong>(context);
    } catch (...) {
        SPDLOG_WARN("fail to create CommandContext");
        return reinterpret_cast<jlong>(nullptr);
    }
}

CHelper::Old2New::BlockFixData blockFixData0;

extern "C" [[maybe_unused]] JNIEXPORT jboolean JNICALL
Java_yancey_chelper_core_CHelperCore_old2newInit0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jobject assetManager, jstring blockFixDataPath) {
    if (blockFixDataPath == nullptr) [[unlikely]] {
        SPDLOG_WARN("call Java_yancey_chelper_core_CHelperCore_old2newInit0 when blockFixDataPath is nullptr");
        return false;
    }
    std::string blockFixDataPath0 = jstring2string(env, blockFixDataPath);
    try {
        AAssetManager *mgr = AAssetManager_fromJava(env, assetManager);
        AAsset *asset = AAssetManager_open(mgr, blockFixDataPath0.c_str(), AASSET_MODE_BUFFER);
        if (asset == nullptr) [[unlikely]] {
            return false;
        }
        auto dataFileSize = static_cast<size_t>(AAsset_getLength(asset));
        char *buffer = new char[dataFileSize];
        int numBytesRead = AAsset_read(asset, buffer, dataFileSize);
        AAsset_close(asset);
        blockFixData0 = CHelper::Old2New::blockFixDataFromBinary(std::string_view(buffer, numBytesRead));
        delete[] buffer;
        return true;
    } catch (const std::exception &e) {
        return false;
    }
}

extern "C" [[maybe_unused]] JNIEXPORT jstring JNICALL
Java_yancey_chelper_core_CHelperCore_old2new0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jstring old) {
    if (old == nullptr) [[unlikely]] {
        SPDLOG_WARN("call Java_yancey_chelper_core_CHelperCore_old2new0 when old is nullptr");
        return nullptr;
    }
    return u16string2jstring(env, CHelper::CHelperCore::old2new(blockFixData0, jstring2u16string(env, old)));
}

// 和CHelperCore不同，CommandContext没有可变状态，
// 所有操作都由调用方传入位置参数，因此可以把同一个CommandContext
// 交给多个线程同时读取，也可以创建多个CommandContext并行工作

extern "C" [[maybe_unused]] JNIEXPORT void JNICALL
Java_yancey_chelper_core_CommandContext_release0(
        [[maybe_unused]] JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer) {
    CHelper::CHelperCore::deleteContext(reinterpret_cast<CHelper::CommandContext *>(pointer));
}

extern "C" [[maybe_unused]] JNIEXPORT jstring JNICALL
Java_yancey_chelper_core_CommandContext_command0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer) {
    auto *context = reinterpret_cast<CHelper::CommandContext *>(pointer);
    if (context == nullptr) [[unlikely]] {
        SPDLOG_WARN("call Java_yancey_chelper_core_CommandContext_command0 when context is nullptr");
        return nullptr;
    }
    return u16string2jstring(env, context->getCommand());
}

extern "C" [[maybe_unused]] JNIEXPORT jstring JNICALL
Java_yancey_chelper_core_CommandContext_getStructure0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer) {
    auto *context = reinterpret_cast<CHelper::CommandContext *>(pointer);
    if (context == nullptr) [[unlikely]] {
        SPDLOG_WARN("call Java_yancey_chelper_core_CommandContext_getStructure0 when context is nullptr");
        return nullptr;
    }
    return u16string2jstring(env, context->getStructure());
}

extern "C" [[maybe_unused]] JNIEXPORT jstring JNICALL
Java_yancey_chelper_core_CommandContext_getParamHint0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer, jint index) {
    auto *context = reinterpret_cast<CHelper::CommandContext *>(pointer);
    if (context == nullptr) [[unlikely]] {
        SPDLOG_WARN("call Java_yancey_chelper_core_CommandContext_getParamHint0 when context is nullptr");
        return nullptr;
    }
    return u16string2jstring(env, context->getParamHint(index));
}

extern "C" [[maybe_unused]] JNIEXPORT jobjectArray JNICALL
Java_yancey_chelper_core_CommandContext_getErrorReasons0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer) {
    auto *context = reinterpret_cast<CHelper::CommandContext *>(pointer);
    if (context == nullptr) [[unlikely]] {
        SPDLOG_WARN("call Java_yancey_chelper_core_CommandContext_getErrorReasons0 when context is nullptr");
        return errorReasons2jobjectArray(env, {});
    }
    return errorReasons2jobjectArray(env, context->getErrorReasons());
}

extern "C" [[maybe_unused]] JNIEXPORT jint JNICALL
Java_yancey_chelper_core_CommandContext_getSuggestionsSize0(
        [[maybe_unused]] JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer, jint index) {
    auto *context = reinterpret_cast<CHelper::CommandContext *>(pointer);
    if (context == nullptr) [[unlikely]] {
        SPDLOG_WARN("call Java_yancey_chelper_core_CommandContext_getSuggestionsSize0 when context is nullptr");
        return 0;
    }
    return static_cast<jint>(context->getSuggestions(index).size());
}

extern "C" [[maybe_unused]] JNIEXPORT jobject JNICALL
Java_yancey_chelper_core_CommandContext_getSuggestion0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer, jint index, jint which) {
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
    return suggestion2jobject(env, env->FindClass("yancey/chelper/core/Suggestion"), suggestions.at(which));
}

extern "C" [[maybe_unused]] JNIEXPORT jobjectArray JNICALL
Java_yancey_chelper_core_CommandContext_getSuggestions0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer, jint index) {
    auto *context = reinterpret_cast<CHelper::CommandContext *>(pointer);
    if (context == nullptr) [[unlikely]] {
        SPDLOG_WARN("call Java_yancey_chelper_core_CommandContext_getSuggestions0 when context is nullptr");
        return suggestions2jobjectArray(env, {});
    }
    return suggestions2jobjectArray(env, context->getSuggestions(index));
}

extern "C" [[maybe_unused]] JNIEXPORT jint JNICALL
Java_yancey_chelper_core_CommandContext_getNodeCount0(
        [[maybe_unused]] JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer) {
    auto *context = reinterpret_cast<CHelper::CommandContext *>(pointer);
    if (context == nullptr) [[unlikely]] {
        SPDLOG_WARN("call Java_yancey_chelper_core_CommandContext_getNodeCount0 when context is nullptr");
        return 0;
    }
    return static_cast<jint>(context->getNodeCount());
}

extern "C" [[maybe_unused]] JNIEXPORT jobject JNICALL
Java_yancey_chelper_core_CommandContext_applySuggestion0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer, jint index, jint which) {
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
}

extern "C" [[maybe_unused]] JNIEXPORT jintArray JNICALL
Java_yancey_chelper_core_CommandContext_getColors0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer) {
    auto *context = reinterpret_cast<CHelper::CommandContext *>(pointer);
    if (context == nullptr) [[unlikely]] {
        SPDLOG_WARN("call Java_yancey_chelper_core_CommandContext_getColors0 when context is nullptr");
        return nullptr;
    }
    return syntaxTokenTypes2jintArray(env, context->getSyntaxResult().tokenTypes);
}
