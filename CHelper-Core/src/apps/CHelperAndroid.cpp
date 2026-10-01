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
#include <chelper/FragmentContext.h>
#include <chelper/extension/Composer.h>
#include <chelper/extension/MainPack.h>
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

jstring u16string2jstring(JNIEnv *env, const std::u16string &u16string) {
    return env->NewString(reinterpret_cast<const jchar *>(u16string.c_str()), static_cast<jsize>(u16string.size()));
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
    env->SetObjectField(javaSuggestion,
                        env->GetFieldID(suggestionClass, "packName", "Ljava/lang/String;"),
                        suggestion.content->packName.has_value()
                                ? u16string2jstring(env, suggestion.content->packName.value())
                                : nullptr);
    env->SetIntField(javaSuggestion,
                     env->GetFieldID(suggestionClass, "start", "I"),
                     static_cast<jint>(suggestion.start));
    env->SetIntField(javaSuggestion,
                     env->GetFieldID(suggestionClass, "end", "I"),
                     static_cast<jint>(suggestion.end));
    env->SetBooleanField(javaSuggestion,
                         env->GetFieldID(suggestionClass, "isAddSpace", "Z"),
                         suggestion.isAddSpace);
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

// 旧的 assets/文件 .cpack 加载通道（create0/fromAssets/fromFile）已随主路径切换删除，
// 内核只经 compose0（主包段 + 拓展包）创建。

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
        std::istringstream iss(std::string(buffer, numBytesRead));
        serialization::from_binary(iss, blockFixData0);
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

// ---------------------------------------------------------------------------
// 主包（MainPack）与合成器（Composer）JNI 导出（P0）
// ---------------------------------------------------------------------------

namespace {

    std::shared_ptr<std::vector<uint8_t>> jbyteArray2bytes(JNIEnv *env, jbyteArray bytesJ) {
        jsize len = env->GetArrayLength(bytesJ);
        jbyte *data = env->GetByteArrayElements(bytesJ, nullptr);
        auto bytes = std::make_shared<std::vector<uint8_t>>(
                reinterpret_cast<uint8_t *>(data), reinterpret_cast<uint8_t *>(data) + len);
        env->ReleaseByteArrayElements(bytesJ, data, JNI_ABORT);
        return bytes;
    }

    // 把 C++ 异常原因抛成 Java RuntimeException，供 Kotlin 层显示/上报
    void throwJniError(JNIEnv *env, const std::string &message) {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        jclass cls = env->FindClass("java/lang/RuntimeException");
        if (cls != nullptr) {
            env->ThrowNew(cls, message.c_str());
            env->DeleteLocalRef(cls);
        }
    }

}// namespace

extern "C" [[maybe_unused]] JNIEXPORT jlong JNICALL
Java_yancey_chelper_core_MainPack_open0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jobjectArray relPaths, jobjectArray contents) {
    if (relPaths == nullptr || contents == nullptr) [[unlikely]] {
        SPDLOG_WARN("call MainPack_open0 when relPaths/contents is null");
        return 0;
    }
    try {
        jsize n = env->GetArrayLength(relPaths);
        std::vector<CHelper::Extension::PackFile> files;
        files.reserve(static_cast<size_t>(n));
        for (jsize i = 0; i < n; ++i) {
            auto relJ = static_cast<jstring>(env->GetObjectArrayElement(relPaths, i));
            auto bytesJ = static_cast<jbyteArray>(env->GetObjectArrayElement(contents, i));
            std::string rel = jstring2string(env, relJ);
            files.push_back({rel, jbyteArray2bytes(env, bytesJ)});
        }
        auto pack = CHelper::Extension::MainPack::open(
                CHelper::Extension::MainPackSource{CHelper::Extension::MainPackSource::Kind::Files, std::move(files), {}});
        return reinterpret_cast<jlong>(pack.release());
    } catch (const std::exception &e) {
        SPDLOG_WARN("fail to open MainPack: {}", e.what());
        throwJniError(env, std::string("open MainPack failed: ") + e.what());
        return 0;
    } catch (...) {
        SPDLOG_WARN("fail to open MainPack (unknown)");
        throwJniError(env, "open MainPack failed: unknown error");
        return 0;
    }
}

extern "C" [[maybe_unused]] JNIEXPORT void JNICALL
Java_yancey_chelper_core_MainPack_release0(
        [[maybe_unused]] JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer) {
    delete reinterpret_cast<CHelper::Extension::MainPack *>(pointer);
}

extern "C" [[maybe_unused]] JNIEXPORT jobjectArray JNICALL
Java_yancey_chelper_core_MainPack_listSegments0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer) {
    auto *pack = reinterpret_cast<CHelper::Extension::MainPack *>(pointer);
    if (pack == nullptr) [[unlikely]] {
        return nullptr;
    }
    const auto &segments = pack->listSegments();
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(segments.size()), stringClass, nullptr);
    for (size_t i = 0; i < segments.size(); ++i) {
        const std::string line = segments[i].id + "\t" + segments[i].version + "\t" +
                                 segments[i].packId + "\t" + utf8::utf16to8(segments[i].name);
        env->SetObjectArrayElement(result, static_cast<jsize>(i), string2jstring(env, line));
    }
    return result;
}

extern "C" [[maybe_unused]] JNIEXPORT jbyteArray JNICALL
Java_yancey_chelper_core_MainPack_readFile0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer, jstring versionType, jstring branch, jstring relPath) {
    auto *pack = reinterpret_cast<CHelper::Extension::MainPack *>(pointer);
    if (pack == nullptr || relPath == nullptr) [[unlikely]] {
        return nullptr;
    }
    try {
        auto seg = pack->loadSegment(jstring2string(env, versionType), jstring2string(env, branch));
        std::string rel = jstring2string(env, relPath);
        for (const auto &f: seg.files) {
            if (f.relPath == rel && f.bytes) {
                jbyteArray result = env->NewByteArray(static_cast<jsize>(f.bytes->size()));
                env->SetByteArrayRegion(result, 0, static_cast<jsize>(f.bytes->size()),
                                        reinterpret_cast<const jbyte *>(f.bytes->data()));
                return result;
            }
        }
        return nullptr;
    } catch (const std::exception &e) {
        SPDLOG_WARN("fail to read file {} from {}: {}", jstring2string(env, relPath),
                    jstring2string(env, versionType) + "/" + jstring2string(env, branch), e.what());
        throwJniError(env, std::string("read file failed: ") + e.what());
        return nullptr;
    } catch (...) {
        throwJniError(env, "read file failed: unknown error");
        return nullptr;
    }
}

extern "C" [[maybe_unused]] JNIEXPORT jlong JNICALL
Java_yancey_chelper_core_CHelperCore_compose0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jlong mainPackPtr,
        jobjectArray segments, jobjectArray packRelPaths, jobjectArray packContents, jintArray packCounts) {
    auto *pack = reinterpret_cast<CHelper::Extension::MainPack *>(mainPackPtr);
    if (pack == nullptr || segments == nullptr) [[unlikely]] {
        return 0;
    }
    try {
        // 启用段（当前取第一个）
        auto segJ = static_cast<jstring>(env->GetObjectArrayElement(segments, 0));
        std::string seg = jstring2string(env, segJ);
        size_t slash = seg.find('/');
        CHelper::Extension::SegmentData segData = pack->loadSegment(seg.substr(0, slash), seg.substr(slash + 1));

        // 拓展包切分（packCounts 记录每包文件数）
        std::vector<CHelper::Extension::ExtensionPackData> packs;
        if (packRelPaths != nullptr && packContents != nullptr && packCounts != nullptr) {
            jsize packCount = env->GetArrayLength(packCounts);
            jint *counts = env->GetIntArrayElements(packCounts, nullptr);
            size_t idx = 0;
            for (jsize p = 0; p < packCount; ++p) {
                CHelper::Extension::ExtensionPackData pd;
                for (jint c = 0; c < counts[p]; ++c) {
                    auto relJ = static_cast<jstring>(env->GetObjectArrayElement(packRelPaths, static_cast<jsize>(idx)));
                    auto bytesJ = static_cast<jbyteArray>(env->GetObjectArrayElement(packContents, static_cast<jsize>(idx)));
                    pd.files.push_back({jstring2string(env, relJ), jbyteArray2bytes(env, bytesJ)});
                    ++idx;
                }
                packs.push_back(std::move(pd));
            }
            env->ReleaseIntArrayElements(packCounts, counts, JNI_ABORT);
        }

        auto result = CHelper::Extension::compose(segData, packs);
        auto *core = new CHelper::CHelperCore(result.cpack);
        return reinterpret_cast<jlong>(core);
    } catch (const std::exception &e) {
        SPDLOG_WARN("fail to compose main pack + extension packs: {}", e.what());
        throwJniError(env, std::string("compose failed: ") + e.what());
        return 0;
    } catch (...) {
        SPDLOG_WARN("fail to compose main pack + extension packs (unknown)");
        throwJniError(env, "compose failed: unknown error");
        return 0;
    }
}

// ---------------------------------------------------------------------------
// FragmentContext（片段补全：目标选择器 / ID 键表）JNI 导出
// ---------------------------------------------------------------------------

extern "C" [[maybe_unused]] JNIEXPORT jlong JNICALL
Java_yancey_chelper_core_FragmentContext_openSelector0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jlong corePtr, jstring content) {
    auto *core = reinterpret_cast<CHelper::CHelperCore *>(corePtr);
    if (core == nullptr || content == nullptr) [[unlikely]] {
        return 0;
    }
    try {
        auto ctx = CHelper::FragmentContext::createTargetSelector(
                core->getSharedCPack(), jstring2u16string(env, content));
        return reinterpret_cast<jlong>(ctx.release());
    } catch (...) {
        return 0;
    }
}

extern "C" [[maybe_unused]] JNIEXPORT jlong JNICALL
Java_yancey_chelper_core_FragmentContext_openId0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jlong corePtr, jstring key, jstring content) {
    auto *core = reinterpret_cast<CHelper::CHelperCore *>(corePtr);
    if (core == nullptr || key == nullptr || content == nullptr) [[unlikely]] {
        return 0;
    }
    try {
        auto ctx = CHelper::FragmentContext::createId(
                core->getSharedCPack(), jstring2string(env, key), jstring2u16string(env, content));
        return reinterpret_cast<jlong>(ctx.release());
    } catch (...) {
        return 0;
    }
}

extern "C" [[maybe_unused]] JNIEXPORT void JNICALL
Java_yancey_chelper_core_FragmentContext_release0(
        [[maybe_unused]] JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer) {
    delete reinterpret_cast<CHelper::FragmentContext *>(pointer);
}

extern "C" [[maybe_unused]] JNIEXPORT jint JNICALL
Java_yancey_chelper_core_FragmentContext_getSuggestionsSize0(
        [[maybe_unused]] JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer, jint index) {
    auto *ctx = reinterpret_cast<CHelper::FragmentContext *>(pointer);
    if (ctx == nullptr) [[unlikely]] {
        return 0;
    }
    return static_cast<jint>(ctx->getSuggestions(static_cast<size_t>(index)).size());
}

extern "C" [[maybe_unused]] JNIEXPORT jobject JNICALL
Java_yancey_chelper_core_FragmentContext_getSuggestion0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer, jint index, jint which) {
    auto *ctx = reinterpret_cast<CHelper::FragmentContext *>(pointer);
    if (ctx == nullptr) [[unlikely]] {
        return nullptr;
    }
    auto suggestions = ctx->getSuggestions(static_cast<size_t>(index));
    if (which < 0 || static_cast<jint>(suggestions.size()) <= which) [[unlikely]] {
        return nullptr;
    }
    return suggestion2jobject(env, env->FindClass("yancey/chelper/core/Suggestion"), suggestions.at(which));
}

extern "C" [[maybe_unused]] JNIEXPORT jobjectArray JNICALL
Java_yancey_chelper_core_FragmentContext_getSuggestions0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer, jint index) {
    auto *ctx = reinterpret_cast<CHelper::FragmentContext *>(pointer);
    if (ctx == nullptr) [[unlikely]] {
        return suggestions2jobjectArray(env, {});
    }
    return suggestions2jobjectArray(env, ctx->getSuggestions(static_cast<size_t>(index)));
}

extern "C" [[maybe_unused]] JNIEXPORT jobjectArray JNICALL
Java_yancey_chelper_core_FragmentContext_getErrorReasons0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer) {
    auto *ctx = reinterpret_cast<CHelper::FragmentContext *>(pointer);
    if (ctx == nullptr) [[unlikely]] {
        return errorReasons2jobjectArray(env, {});
    }
    return errorReasons2jobjectArray(env, ctx->getErrorReasons());
}

extern "C" [[maybe_unused]] JNIEXPORT jobject JNICALL
Java_yancey_chelper_core_FragmentContext_applySuggestion0(
        JNIEnv *env, [[maybe_unused]] jobject thiz, jlong pointer, jint index, jint which) {
    auto *ctx = reinterpret_cast<CHelper::FragmentContext *>(pointer);
    if (ctx == nullptr) [[unlikely]] {
        return nullptr;
    }
    auto result = ctx->applySuggestion(static_cast<size_t>(index), static_cast<size_t>(which));
    if (result.has_value()) [[likely]] {
        return clickSuggestionResult2jobject(env, result.value());
    }
    return nullptr;
}
