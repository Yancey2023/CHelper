cmake_minimum_required(VERSION 3.21)
project(CHelper LANGUAGES CXX)

# set options
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
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
endif()

include(FetchContent)
find_package(Git REQUIRED)
set(CHELPER_THIRDPARTY_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/3rdparty_downloads")
# fresh checkout 下该目录（.gitignore 中的生成目录）不存在，且 execute_process
# 不会自动创建 WORKING_DIRECTORY，必须在使用前显式创建，否则 git clone 无法启动。
file(MAKE_DIRECTORY "${CHELPER_THIRDPARTY_SOURCE_DIR}")

# 拉取第三方库源码到 3rdparty_downloads（只含源码，不含构建产物）。
# - 首次缺失或损坏时由本函数负责 clone + checkout 到锁定版本（含嵌套子模块）；
# - 之后通过 FETCHCONTENT_SOURCE_DIR_<NAME> 让 FetchContent 直接复用，不再执行
#   下载/删除步骤。这样多个构建目录（如 CLion 的多个 profile）并发配置只会读取
#   同一份源码、互不破坏，也避免每次新建构建目录都重新下载 emscripten 等大仓库。
# 调用处用关键字参数：GIT_REPOSITORY / GIT_TAG，可选 SOURCE_SUBDIR / EXCLUDE_FROM_ALL。
# 注意：GIT_TAG 需用完整 commit 哈希，以便与已检出的 HEAD 精确比对决定是否需要重新拉取。
function(chelper_fetch_thirdparty name)
    cmake_parse_arguments(ARG "EXCLUDE_FROM_ALL;SYSTEM" "GIT_REPOSITORY;GIT_TAG;SOURCE_SUBDIR" "" ${ARGN})

    set(sourceDir "${CHELPER_THIRDPARTY_SOURCE_DIR}/${name}")
    string(TOUPPER "${name}" nameUpper)

    # 校验源码目录是否已是目标版本的 git 仓库
    set(sourceValid FALSE)
    if(EXISTS "${sourceDir}/.git")
        execute_process(
            COMMAND "${GIT_EXECUTABLE}" rev-parse --verify HEAD
            WORKING_DIRECTORY "${sourceDir}"
            OUTPUT_VARIABLE head
            OUTPUT_STRIP_TRAILING_WHITESPACE
            RESULT_VARIABLE rc)
        if(rc EQUAL 0 AND head STREQUAL ARG_GIT_TAG)
            set(sourceValid TRUE)
        endif()
    endif()

    if(NOT sourceValid)
        message(STATUS "[FetchContent] Third-party ${name}: cloning ${ARG_GIT_TAG} ...")
        file(REMOVE_RECURSE "${sourceDir}")
        execute_process(
            COMMAND "${GIT_EXECUTABLE}" clone --no-checkout "${ARG_GIT_REPOSITORY}" "${name}"
            WORKING_DIRECTORY "${CHELPER_THIRDPARTY_SOURCE_DIR}"
            RESULT_VARIABLE rc)
        if(NOT rc EQUAL 0)
            message(FATAL_ERROR "Failed to clone ${ARG_GIT_REPOSITORY} for ${name}")
        endif()
        execute_process(
            COMMAND "${GIT_EXECUTABLE}" checkout "${ARG_GIT_TAG}" --
            WORKING_DIRECTORY "${sourceDir}"
            RESULT_VARIABLE rc)
        if(NOT rc EQUAL 0)
            message(FATAL_ERROR "Failed to checkout ${ARG_GIT_TAG} for ${name}")
        endif()
        execute_process(
            COMMAND "${GIT_EXECUTABLE}" submodule update --init --recursive
            WORKING_DIRECTORY "${sourceDir}"
            RESULT_VARIABLE rc)
        if(NOT rc EQUAL 0)
            message(FATAL_ERROR "Failed to init submodules for ${name}")
        endif()
    else()
        message(STATUS "[FetchContent] Third-party ${name}: using cached source")
    endif()

    # 让 FetchContent 直接复用已就绪的源码：不下载、不 rm -rf、不产生 -src/-subbuild
    set(FETCHCONTENT_SOURCE_DIR_${nameUpper} "${sourceDir}")
    FetchContent_Declare(${name} ${ARGN})
    FetchContent_MakeAvailable(${name})
    message(STATUS "[FetchContent] Third-party ${name}: ready")
endfunction()

chelper_fetch_thirdparty(fmt
    GIT_REPOSITORY https://github.com/fmtlib/fmt.git
    GIT_TAG c0cd0fcfece8ac1541857a6dc5955745f1ec165
)
chelper_fetch_thirdparty(utfcpp
    GIT_REPOSITORY https://github.com/nemtrif/utfcpp.git
    GIT_TAG cfc9112cee3e817e8b72948a675f78479546f0cf
)
chelper_fetch_thirdparty(xxHash
    GIT_REPOSITORY https://github.com/Cyan4973/xxHash.git
    GIT_TAG e626a72bc2321cd320e953a0ccf1584cad60f363
    SOURCE_SUBDIR cmake_unofficial
    EXCLUDE_FROM_ALL
)
chelper_fetch_thirdparty(glaze
    GIT_REPOSITORY https://github.com/stephenberry/glaze.git
    GIT_TAG dce5e0f7ec572725ec369a0bc59a00eeb2270a9a
)
chelper_fetch_thirdparty(spdlog
    GIT_REPOSITORY https://github.com/gabime/spdlog.git
    GIT_TAG 0f7562a0f9273cfc71fddc6ae52ebff7a490fa04
)
chelper_fetch_thirdparty(googletest
    GIT_REPOSITORY https://github.com/google/googletest.git
    GIT_TAG 85087857ad10bd407cd6ed2f52f7ea9752db621f
)
if(NOT ANDROID AND NOT EMSCRIPTEN)
    chelper_fetch_thirdparty(
        emscripten
        GIT_REPOSITORY https://github.com/emscripten-core/emscripten.git
        GIT_TAG 0c06f7235acc1d63037f088025f76bc013f84bbb
    )
endif()
if(MSVC)
    set_property(TARGET fmt PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    set_property(TARGET spdlog PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    set_property(TARGET gtest PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    set_property(TARGET gtest_main PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
endif()

# Param Deliver
if(CMAKE_BUILD_TYPE AND (CMAKE_BUILD_TYPE STREQUAL "Debug"))
    set(CHelperDebug true)
else()
    set(CHelperDebug false)
endif()
set(RESOURCE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../CHelper-Resource)
configure_file(ParamDeliver.h.in ${CMAKE_BINARY_DIR}/include/ParamDeliver.h @ONLY)

# Sources
file(GLOB_RECURSE CHELPER_CORE_SOURCE_FILE src/chelper/*.cpp)

# CHelper Core
add_library(CHelperCore STATIC ${CHELPER_CORE_SOURCE_FILE})
target_precompile_headers(CHelperCore PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/include/pch.h)
target_include_directories(CHelperCore PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include ${CMAKE_BINARY_DIR}/include ${CMAKE_CURRENT_SOURCE_DIR}/src)
target_link_libraries(CHelperCore PUBLIC fmt::fmt spdlog::spdlog utf8cpp xxHash::xxhash glaze::glaze)
if(MSVC)
    set_property(TARGET CHelperCore PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
endif()
add_library(CHelper::Core ALIAS CHelperCore)

# CHelper Core (no filesystem)
add_library(CHelperNoFilesystemCore STATIC ${CHELPER_CORE_SOURCE_FILE})
target_precompile_headers(CHelperNoFilesystemCore PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/include/pch.h)
target_include_directories(CHelperNoFilesystemCore PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include ${CMAKE_BINARY_DIR}/include ${CMAKE_CURRENT_SOURCE_DIR}/src)
target_link_libraries(CHelperNoFilesystemCore PUBLIC fmt::fmt spdlog::spdlog utf8cpp xxHash::xxhash glaze::glaze)
target_compile_definitions(CHelperNoFilesystemCore PUBLIC CHELPER_NO_FILESYSTEM)
if(MSVC)
    set_property(TARGET CHelperNoFilesystemCore PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
endif()
add_library(CHelper::NoFilesystemCore ALIAS CHelperNoFilesystemCore)

# CHelper Android
if(NOT MSVC AND NOT EMSCRIPTEN)
    add_library(CHelperAndroid SHARED src/apps/CHelperAndroid.cpp)
    target_compile_definitions(CHelperAndroid PUBLIC CHelperAndroid)
    if(ANDROID)
        target_link_libraries(CHelperAndroid PUBLIC android log)
    else()
        target_include_directories(CHelperAndroid PUBLIC include_android)
    endif()
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
    target_link_libraries(CHelperTest PRIVATE CHelper::Core GTest::gtest_main)
    if(MSVC)
        set_property(TARGET CHelperTest PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    endif()
endif()

# Web Application
if(NOT ANDROID)
    add_library(CHelperWeb src/apps/CHelperWeb.cpp)
    if(NOT EMSCRIPTEN)
        target_include_directories(CHelperWeb PRIVATE ${CHELPER_THIRDPARTY_SOURCE_DIR}/emscripten/system/include)
    endif()
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
        target_link_libraries(CHelperQt PRIVATE CHelper::Core Qt6::Core Qt6::Gui Qt6::Widgets)
        if(MSVC)
            set_property(TARGET CHelperQt PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
        endif()
    endif()
endif()
