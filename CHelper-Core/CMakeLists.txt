cmake_minimum_required(VERSION 3.21)
project(CHelper LANGUAGES CXX)

# using c++ 20
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# export compile commands
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# Enable Compile warning
if (CMAKE_CXX_COMPILER_ID STREQUAL "GNU" OR CMAKE_CXX_COMPILER_ID STREQUAL "Clang")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra -Wno-unused-parameter")
elseif (MSVC)
    add_compile_options("/W4")
    add_compile_options("/wd4100")
endif ()

# Fix issues in MSVC
if (MSVC)
    add_compile_options("/utf-8")
endif ()

# ThirdParty: fmt
add_subdirectory(3rdparty/fmt)
if (MSVC)
    set_property(TARGET fmt PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
endif ()

# ThirdParty: utf8cpp
add_subdirectory(3rdparty/utfcpp)

# ThirdParty: xxHash
set(XXHASH_BUILD_XXHSUM OFF)
option(BUILD_SHARED_LIBS OFF)
add_subdirectory(3rdparty/xxHash/cmake_unofficial EXCLUDE_FROM_ALL)

# ThirdParty: serialization
add_subdirectory(3rdparty/serialization)

# ThirdParty: spdlog
set(SPDLOG_FMT_EXTERNAL ON)
add_subdirectory(3rdparty/spdlog)
if (MSVC)
    set_property(TARGET spdlog PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
endif ()

# ThirdParty: GTest
set(gtest_force_shared_crt ON CACHE BOOL "" FORCE)
add_subdirectory(3rdparty/googletest)
if (MSVC)
    set_property(TARGET gtest PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    set_property(TARGET gtest_main PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
endif ()

# Param Deliver
if (CMAKE_BUILD_TYPE AND (CMAKE_BUILD_TYPE STREQUAL "Debug"))
    set(CHelperDebug true)
else ()
    set(CHelperDebug false)
endif ()
set(RESOURCE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../CHelper-Resource)
configure_file(ParamDeliver.h.in ${CMAKE_BINARY_DIR}/include/ParamDeliver.h @ONLY)

# Sources
file(GLOB_RECURSE CHELPER_CORE_SOURCE_FILE src/chelper/*.cpp)

# CHelper Core
add_library(CHelperCore STATIC ${CHELPER_CORE_SOURCE_FILE})
target_precompile_headers(CHelperCore PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/include/pch.h)
target_include_directories(CHelperCore PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include ${CMAKE_BINARY_DIR}/include ${CMAKE_CURRENT_SOURCE_DIR}/src)
target_link_libraries(CHelperCore PUBLIC fmt::fmt spdlog::spdlog utf8cpp xxHash::xxhash serialization::serialization)
if (MSVC)
    set_property(TARGET CHelperCore PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
endif ()
add_library(CHelper::Core ALIAS CHelperCore)

# CHelper Core (no filesystem)
add_library(CHelperNoFilesystemCore STATIC ${CHELPER_CORE_SOURCE_FILE})
target_precompile_headers(CHelperNoFilesystemCore PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/include/pch.h)
target_include_directories(CHelperNoFilesystemCore PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include ${CMAKE_BINARY_DIR}/include ${CMAKE_CURRENT_SOURCE_DIR}/src)
target_link_libraries(CHelperNoFilesystemCore PUBLIC fmt::fmt spdlog::spdlog utf8cpp xxHash::xxhash serialization::serialization)
target_compile_definitions(CHelperNoFilesystemCore PUBLIC CHELPER_NO_FILESYSTEM)
if (MSVC)
    set_property(TARGET CHelperNoFilesystemCore PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
endif ()
add_library(CHelper::NoFilesystemCore ALIAS CHelperNoFilesystemCore)

# CHelper Android
if (NOT MSVC)
    add_library(CHelperAndroid SHARED src/apps/CHelperAndroid.cpp)
    target_compile_definitions(CHelperAndroid PUBLIC CHelperAndroid)
    if (ANDROID)
        target_link_libraries(CHelperAndroid PUBLIC android log)
    else ()
        target_compile_definitions(CHelperAndroid PUBLIC __ANDROID__)
        target_include_directories(CHelperAndroid PUBLIC include_android)
    endif ()
    target_link_libraries(CHelperAndroid PRIVATE CHelper::Core)
endif ()

# CHelper Resource Generator
add_executable(CHelperResourceGenerator src/apps/CHelperResourceGenerator.cpp)
target_link_libraries(CHelperResourceGenerator PRIVATE CHelper::Core)
if (MSVC)
    set_property(TARGET CHelperResourceGenerator PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
endif ()

# CHelper Test
file(GLOB_RECURSE TEST_FILE tests/*.cpp)
add_executable(CHelperTest ${TEST_FILE})
target_link_libraries(CHelperTest PRIVATE CHelper::Core GTest::gtest_main)
if (MSVC)
    set_property(TARGET CHelperTest PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
endif ()

# Web Application
add_library(CHelperWeb src/apps/CHelperWeb.cpp)
if (NOT EMSCRIPTEN)
    target_include_directories(CHelperWeb PRIVATE 3rdparty/emscripten/system/include)
endif ()
target_link_libraries(CHelperWeb PRIVATE CHelper::NoFilesystemCore)

# ThirdParty: Qt6
find_package(Qt6 QUIET COMPONENTS Core Gui Widgets)
if (Qt6_FOUND)
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
    if (MSVC)
        set_property(TARGET CHelperQt PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    endif ()
endif ()
