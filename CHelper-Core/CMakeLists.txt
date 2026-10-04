cmake_minimum_required(VERSION 3.31)
project(CHelper LANGUAGES CXX)

# set options
set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

include(CheckCXXSourceCompiles)
check_cxx_source_compiles(
    [=[
    #include <expected>
    int main() {
        std::expected<int, int> value = 42;
        return value.value() == 42 ? 0 : 1;
    }
]=]
    CHELPER_HAS_STD_EXPECTED)
if(NOT CHELPER_HAS_STD_EXPECTED)
    message(FATAL_ERROR "CHelper requires C++23 std::expected. Use a compatible compiler and standard library "
                        "(for Linux, GCC 13+ or Clang 19+ with libstdc++ 13+).")
endif()

set(CMAKE_EXPORT_COMPILE_COMMANDS ON)
set(XXHASH_BUILD_XXHSUM OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(SPDLOG_FMT_EXTERNAL ON CACHE BOOL "" FORCE)
set(gtest_force_shared_crt ON CACHE BOOL "" FORCE)

# Enable Compile warning
if(CMAKE_CXX_COMPILER_ID STREQUAL "GNU" OR CMAKE_CXX_COMPILER_ID STREQUAL "Clang")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra -Wno-unused-parameter")
elseif(MSVC)
    add_compile_options("/W4")
    add_compile_options("/wd4100")
endif()

# Fix issues in MSVC
if(MSVC)
    add_compile_options("/utf-8")
    add_compile_options("/Zc:__cplusplus")
    # 序列化等重度模板的编译单元会超出 COFF 节段上限
    add_compile_options("/bigobj")
endif()

include(FetchContent)
find_package(Git REQUIRED)
set(CHELPER_THIRDPARTY_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/3rdparty_downloads")
# fresh checkout 下该目录（.gitignore 中的生成目录）不存在，且 execute_process 不会自动创建
# WORKING_DIRECTORY，必须在使用前显式创建，否则 git clone 无法启动。
file(MAKE_DIRECTORY "${CHELPER_THIRDPARTY_SOURCE_DIR}")

# 拉取第三方库源码到 3rdparty_downloads（只含源码，不含构建产物）。 FetchContent 负责缺失源码的下载和版本检出；已有缓存则通过
# FETCHCONTENT_SOURCE_DIR_<NAME> 直接复用。这样多个构建目录（如 CLion 的多个
# profile）只共享同一份源码，构建元数据和编译产物仍留在各自的 build/_deps 中。 调用处用关键字参数：GIT_REPOSITORY /
# GIT_TAG，可选 SOURCE_SUBDIR / EXCLUDE_FROM_ALL。 GIT_TAG 固定为带版本号的发布标签，
# 仅在主动升级依赖时修改，不跟踪开发分支。
function(chelper_fetch_thirdparty name)
    cmake_parse_arguments(ARG "EXCLUDE_FROM_ALL;SYSTEM" "GIT_REPOSITORY;GIT_TAG;SOURCE_SUBDIR" "" ${ARGN})

    set(sourceDir "${CHELPER_THIRDPARTY_SOURCE_DIR}/${name}")
    string(TOUPPER "${name}" nameUpper)
    set(contentOptions GIT_REPOSITORY "${ARG_GIT_REPOSITORY}" GIT_TAG "${ARG_GIT_TAG}" SOURCE_DIR "${sourceDir}")
    if(ARG_SOURCE_SUBDIR)
        list(APPEND contentOptions SOURCE_SUBDIR "${ARG_SOURCE_SUBDIR}")
    endif()
    if(ARG_EXCLUDE_FROM_ALL)
        list(APPEND contentOptions EXCLUDE_FROM_ALL)
    endif()
    if(ARG_SYSTEM)
        list(APPEND contentOptions SYSTEM)
    endif()

    # An existing source cache is supplied through FetchContent's documented
    # override.  When it does not exist, FetchContent populates SOURCE_DIR
    # directly.  In both cases, build metadata stays under build/_deps.
    if(EXISTS "${sourceDir}/.git")
        # Resolve the release tag to a commit before comparing with HEAD.
        # Fetch only when the requested tag is not available in the local cache.
        execute_process(
            COMMAND "${GIT_EXECUTABLE}" rev-parse --verify "refs/tags/${ARG_GIT_TAG}^{commit}"
            WORKING_DIRECTORY "${sourceDir}"
            RESULT_VARIABLE tagResult
            OUTPUT_VARIABLE targetRevision
            OUTPUT_STRIP_TRAILING_WHITESPACE
            ERROR_QUIET
        )
        if(NOT tagResult EQUAL 0)
            execute_process(
                COMMAND "${GIT_EXECUTABLE}" fetch origin "refs/tags/${ARG_GIT_TAG}:refs/tags/${ARG_GIT_TAG}"
                WORKING_DIRECTORY "${sourceDir}"
                COMMAND_ERROR_IS_FATAL ANY
            )
            execute_process(
                COMMAND "${GIT_EXECUTABLE}" rev-parse --verify "refs/tags/${ARG_GIT_TAG}^{commit}"
                WORKING_DIRECTORY "${sourceDir}"
                OUTPUT_VARIABLE targetRevision
                OUTPUT_STRIP_TRAILING_WHITESPACE
                COMMAND_ERROR_IS_FATAL ANY
            )
        endif()
        execute_process(
            COMMAND "${GIT_EXECUTABLE}" rev-parse HEAD
            WORKING_DIRECTORY "${sourceDir}"
            OUTPUT_VARIABLE sourceRevision
            OUTPUT_STRIP_TRAILING_WHITESPACE
            COMMAND_ERROR_IS_FATAL ANY
        )
        if(NOT sourceRevision STREQUAL targetRevision)
            execute_process(
                COMMAND "${GIT_EXECUTABLE}" status --porcelain --untracked-files=no
                WORKING_DIRECTORY "${sourceDir}"
                OUTPUT_VARIABLE sourceChanges
                OUTPUT_STRIP_TRAILING_WHITESPACE
                COMMAND_ERROR_IS_FATAL ANY
            )
            if(sourceChanges)
                message(FATAL_ERROR "${name}: source cache has local changes; cannot update to ${ARG_GIT_TAG}")
            endif()
            execute_process(
                COMMAND "${GIT_EXECUTABLE}" checkout --detach "${targetRevision}"
                WORKING_DIRECTORY "${sourceDir}"
                COMMAND_ERROR_IS_FATAL ANY
            )
        endif()
    endif()
    if(IS_DIRECTORY "${sourceDir}"
        AND (EXISTS "${sourceDir}/.git"
             OR EXISTS "${sourceDir}/CMakeLists.txt"
             OR (ARG_SOURCE_SUBDIR AND EXISTS "${sourceDir}/${ARG_SOURCE_SUBDIR}/CMakeLists.txt")))
        set(FETCHCONTENT_SOURCE_DIR_${nameUpper} "${sourceDir}")
    endif()

    # FetchContent owns population and keeps downloaded sources in the shared
    # source cache.  Its per-build source/build metadata remains under the current
    # build directory's _deps directory.
    FetchContent_Declare(${name} ${contentOptions})
    FetchContent_MakeAvailable(${name})
    message(STATUS "[FetchContent] Third-party ${name}: ready")
endfunction()

chelper_fetch_thirdparty(fmt
    GIT_REPOSITORY https://github.com/fmtlib/fmt.git
    GIT_TAG 12.2.0
)
chelper_fetch_thirdparty(utfcpp
    GIT_REPOSITORY https://github.com/nemtrif/utfcpp.git
    GIT_TAG v4.2.1
)
chelper_fetch_thirdparty(xxHash
    GIT_REPOSITORY https://github.com/Cyan4973/xxHash.git
    GIT_TAG v0.8.4
    SOURCE_SUBDIR build/cmake
    EXCLUDE_FROM_ALL
)
chelper_fetch_thirdparty(unordered_dense
    GIT_REPOSITORY https://github.com/martinus/unordered_dense.git
    GIT_TAG v5.2.0
    SYSTEM
)
chelper_fetch_thirdparty(glaze
    GIT_REPOSITORY https://github.com/stephenberry/glaze.git
    GIT_TAG v9.0.0
)
chelper_fetch_thirdparty(spdlog
    GIT_REPOSITORY https://github.com/gabime/spdlog.git
    GIT_TAG v1.17.0
)
chelper_fetch_thirdparty(googletest
    GIT_REPOSITORY https://github.com/google/googletest.git
    GIT_TAG v1.18.0
)
if(MSVC)
    set_property(TARGET xxhash PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    set_property(TARGET fmt PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    set_property(TARGET spdlog PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    set_property(TARGET gtest PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    set_property(TARGET gtest_main PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
endif()

# Param Deliver
if(CMAKE_BUILD_TYPE AND (CMAKE_BUILD_TYPE STREQUAL "Debug"))
    set(CHelperDebug 1)
else()
    set(CHelperDebug 0)
endif()
set(RESOURCE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../CHelper-Resource)
configure_file(ParamDeliver.h.in ${CMAKE_BINARY_DIR}/include/ParamDeliver.h @ONLY)

# Sources
file(GLOB_RECURSE CHELPER_CORE_SOURCE_FILE src/chelper/*.cpp)

# CHelper Core
add_library(CHelperCore STATIC ${CHELPER_CORE_SOURCE_FILE})
target_precompile_headers(CHelperCore PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/include/pch.h)
target_include_directories(CHelperCore PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_BINARY_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/src
)
target_link_libraries(CHelperCore PUBLIC fmt::fmt spdlog::spdlog utf8cpp xxHash::xxhash unordered_dense::unordered_dense glaze::glaze)
set_property(TARGET CHelperCore PROPERTY CXX_VISIBILITY_PRESET hidden)
set_property(TARGET CHelperCore PROPERTY VISIBILITY_INLINES_HIDDEN ON)
if(MSVC)
    set_property(TARGET CHelperCore PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
endif()
add_library(CHelper::Core ALIAS CHelperCore)

# CHelper Core (no filesystem)
add_library(CHelperNoFilesystemCore STATIC ${CHELPER_CORE_SOURCE_FILE})
target_precompile_headers(CHelperNoFilesystemCore PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/include/pch.h)
target_include_directories(CHelperNoFilesystemCore PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_BINARY_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/src
)
target_link_libraries(CHelperNoFilesystemCore PUBLIC fmt::fmt spdlog::spdlog utf8cpp xxHash::xxhash unordered_dense::unordered_dense glaze::glaze)
target_compile_definitions(CHelperNoFilesystemCore PUBLIC CHELPER_NO_FILESYSTEM)
set_property(TARGET CHelperNoFilesystemCore PROPERTY CXX_VISIBILITY_PRESET hidden)
set_property(TARGET CHelperNoFilesystemCore PROPERTY VISIBILITY_INLINES_HIDDEN ON)
if(MSVC)
    set_property(TARGET CHelperNoFilesystemCore PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
endif()
add_library(CHelper::NoFilesystemCore ALIAS CHelperNoFilesystemCore)

# CHelper Android
if(ANDROID)
    add_library(CHelperAndroid SHARED src/apps/CHelperAndroid.cpp)
    target_compile_definitions(CHelperAndroid PUBLIC CHelperAndroid)
    target_link_libraries(CHelperAndroid PUBLIC android log)
    target_link_options(CHelperAndroid PRIVATE "-Wl,--exclude-libs,ALL")
    set_property(TARGET CHelperAndroid PROPERTY CXX_VISIBILITY_PRESET hidden)
    set_property(TARGET CHelperAndroid PROPERTY VISIBILITY_INLINES_HIDDEN ON)
    target_link_libraries(CHelperAndroid PRIVATE CHelper::Core)
endif()

# CHelper Resource Generator
if(NOT ANDROID AND NOT EMSCRIPTEN)
    add_executable(CHelperResourceGenerator src/apps/CHelperResourceGenerator.cpp)
    target_link_libraries(CHelperResourceGenerator PRIVATE CHelper::Core)
    if(MSVC)
        set_property(TARGET CHelperResourceGenerator PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    endif()
endif()

# CHelper Test
if(NOT ANDROID AND NOT EMSCRIPTEN)
    file(GLOB_RECURSE TEST_FILE tests/*.cpp)
    add_executable(CHelperTest ${TEST_FILE})
    # 原生测试复用网页接口实现，验证 UTF-16 输出协议。
    target_sources(CHelperTest PRIVATE src/apps/CHelperWeb.cpp)
    target_link_libraries(CHelperTest PRIVATE CHelper::Core GTest::gtest_main)
    if(MSVC)
        set_property(TARGET CHelperTest PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    endif()
endif()

# Web Application
if(EMSCRIPTEN)
    add_library(CHelperWeb src/apps/CHelperWeb.cpp)
    set_property(TARGET CHelperWeb PROPERTY CXX_VISIBILITY_PRESET hidden)
    set_property(TARGET CHelperWeb PROPERTY VISIBILITY_INLINES_HIDDEN ON)
    target_link_libraries(CHelperWeb PRIVATE CHelper::NoFilesystemCore)
endif()

# ThirdParty: Qt6
if(NOT ANDROID AND NOT EMSCRIPTEN)
    find_package(Qt6 QUIET COMPONENTS Core Gui Widgets)
    if(Qt6_FOUND)
        set(CMAKE_AUTOMOC ON)
        set(CMAKE_AUTORCC ON)
        set(CMAKE_AUTOUIC ON)

        # Qt Application
        add_executable(CHelperQt WIN32
            src/apps/qt/CHelperQt.h
            src/apps/qt/CHelperQt.cpp
            src/apps/qt/chelper.qrc
            src/apps/qt/chelper.ui
        )
        # Qt's emit macro conflicts with identifiers in Glaze headers.
        target_compile_definitions(CHelperQt PRIVATE QT_NO_KEYWORDS)
        target_link_libraries(CHelperQt PRIVATE CHelper::Core Qt6::Core Qt6::Gui Qt6::Widgets)
        if(MSVC)
            set_property(TARGET CHelperQt PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
        endif()
    endif()
endif()
