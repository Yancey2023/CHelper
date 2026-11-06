cmake_minimum_required(VERSION 3.31)
project(CHelper LANGUAGES CXX)

# set options
set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
# The project uses headers, not C++ modules. Avoid Ninja's implicit module scan
# (and an unnecessary dependency on clang-scan-deps).
set(CMAKE_CXX_SCAN_FOR_MODULES OFF)
if(MSVC)
  # Qt 的预编译库使用动态 CRT；核心、应用和依赖统一使用相同运行库。
  set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>DLL")
endif()

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
  message(
    FATAL_ERROR
      "CHelper requires C++23 std::expected. Use a compatible compiler and standard library "
      "(for Linux, GCC 13+ or Clang 19+ with libstdc++ 13+).")
endif()

set(CMAKE_EXPORT_COMPILE_COMMANDS ON)
set(XXHASH_BUILD_XXHSUM
    OFF
    CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS
    OFF
    CACHE BOOL "" FORCE)
set(SPDLOG_FMT_EXTERNAL
    ON
    CACHE BOOL "" FORCE)
set(gtest_force_shared_crt
    ON
    CACHE BOOL "" FORCE)

option(CHELPER_BUILD_BENCHMARKS
       "Build allocation-instrumented benchmarks separately" OFF)
option(CHELPER_ENABLE_SANITIZERS
       "Enable AddressSanitizer and UndefinedBehaviorSanitizer" OFF)
option(CHELPER_BUILD_FUZZERS "Build Clang libFuzzer targets" OFF)
option(CHELPER_ENABLE_RESOURCE_AUDIT
       "Instrument arena allocation requests for scope tests" OFF)
if(ANDROID OR EMSCRIPTEN)
  option(BUILD_TESTING "Build native unit tests" OFF)
endif()
include(CTest)

include(FetchContent)
find_package(Git REQUIRED)
set(CHELPER_THIRDPARTY_SOURCE_DIR
    "${CMAKE_CURRENT_SOURCE_DIR}/3rdparty_downloads")
# fresh checkout 下该目录（.gitignore 中的生成目录）不存在，且 execute_process 不会自动创建
# WORKING_DIRECTORY，必须在使用前显式创建，否则 git clone 无法启动。
file(MAKE_DIRECTORY "${CHELPER_THIRDPARTY_SOURCE_DIR}")

# 拉取第三方库源码到 3rdparty_downloads（只含源码，不含构建产物）。 FetchContent 负责缺失源码的下载和版本检出；已有缓存则通过
# FETCHCONTENT_SOURCE_DIR_<NAME> 直接复用。这样多个构建目录（如 CLion 的多个
# profile）只共享同一份源码，构建元数据和编译产物仍留在各自的 build/_deps 中。 调用处用关键字参数：GIT_REPOSITORY /
# GIT_TAG，可选 SOURCE_SUBDIR / EXCLUDE_FROM_ALL。 GIT_TAG 固定为带版本号的发布标签，
# 仅在主动升级依赖时修改，不跟踪开发分支。
function(chelper_fetch_thirdparty name)
  cmake_parse_arguments(ARG "EXCLUDE_FROM_ALL;SYSTEM"
                        "GIT_REPOSITORY;GIT_TAG;SOURCE_SUBDIR" "" ${ARGN})

  set(sourceDir "${CHELPER_THIRDPARTY_SOURCE_DIR}/${name}")
  string(TOUPPER "${name}" nameUpper)
  set(contentOptions GIT_REPOSITORY "${ARG_GIT_REPOSITORY}" GIT_TAG
                     "${ARG_GIT_TAG}" SOURCE_DIR "${sourceDir}")
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
    # Resolve the release tag to a commit before comparing with HEAD. Fetch only
    # when the requested tag is not available in the local cache.
    execute_process(
      COMMAND "${GIT_EXECUTABLE}" rev-parse --verify
              "refs/tags/${ARG_GIT_TAG}^{commit}"
      WORKING_DIRECTORY "${sourceDir}"
      RESULT_VARIABLE tagResult
      OUTPUT_VARIABLE targetRevision
      OUTPUT_STRIP_TRAILING_WHITESPACE ERROR_QUIET)
    if(NOT tagResult EQUAL 0)
      execute_process(
        COMMAND "${GIT_EXECUTABLE}" fetch origin
                "refs/tags/${ARG_GIT_TAG}:refs/tags/${ARG_GIT_TAG}"
        WORKING_DIRECTORY "${sourceDir}" COMMAND_ERROR_IS_FATAL ANY)
      execute_process(
        COMMAND "${GIT_EXECUTABLE}" rev-parse --verify
                "refs/tags/${ARG_GIT_TAG}^{commit}"
        WORKING_DIRECTORY "${sourceDir}"
        OUTPUT_VARIABLE targetRevision
        OUTPUT_STRIP_TRAILING_WHITESPACE COMMAND_ERROR_IS_FATAL ANY)
    endif()
    execute_process(
      COMMAND "${GIT_EXECUTABLE}" rev-parse HEAD
      WORKING_DIRECTORY "${sourceDir}"
      OUTPUT_VARIABLE sourceRevision
      OUTPUT_STRIP_TRAILING_WHITESPACE COMMAND_ERROR_IS_FATAL ANY)
    if(NOT sourceRevision STREQUAL targetRevision)
      execute_process(
        COMMAND "${GIT_EXECUTABLE}" status --porcelain --untracked-files=no
        WORKING_DIRECTORY "${sourceDir}"
        OUTPUT_VARIABLE sourceChanges
        OUTPUT_STRIP_TRAILING_WHITESPACE COMMAND_ERROR_IS_FATAL ANY)
      if(sourceChanges)
        message(
          FATAL_ERROR
            "${name}: source cache has local changes; cannot update to ${ARG_GIT_TAG}"
        )
      endif()
      execute_process(
        COMMAND "${GIT_EXECUTABLE}" checkout --detach "${targetRevision}"
        WORKING_DIRECTORY "${sourceDir}" COMMAND_ERROR_IS_FATAL ANY)
    endif()
  endif()
  if(IS_DIRECTORY "${sourceDir}"
     AND (EXISTS "${sourceDir}/.git"
          OR EXISTS "${sourceDir}/CMakeLists.txt"
          OR (ARG_SOURCE_SUBDIR
              AND EXISTS "${sourceDir}/${ARG_SOURCE_SUBDIR}/CMakeLists.txt")
         ))
    set(FETCHCONTENT_SOURCE_DIR_${nameUpper} "${sourceDir}")
  endif()

  # FetchContent owns population and keeps downloaded sources in the shared
  # source cache.  Its per-build source/build metadata remains under the current
  # build directory's _deps directory.
  FetchContent_Declare(${name} ${contentOptions})
  FetchContent_MakeAvailable(${name})
  message(STATUS "[FetchContent] Third-party ${name}: ready")
endfunction()

chelper_fetch_thirdparty(fmt GIT_REPOSITORY https://github.com/fmtlib/fmt.git
                         GIT_TAG 12.2.0)
chelper_fetch_thirdparty(utfcpp GIT_REPOSITORY
                         https://github.com/nemtrif/utfcpp.git GIT_TAG v4.2.1)
chelper_fetch_thirdparty(
  xxHash
  GIT_REPOSITORY
  https://github.com/Cyan4973/xxHash.git
  GIT_TAG
  v0.8.4
  SOURCE_SUBDIR
  build/cmake
  EXCLUDE_FROM_ALL)
chelper_fetch_thirdparty(
  unordered_dense GIT_REPOSITORY
  https://github.com/martinus/unordered_dense.git GIT_TAG v5.2.0 SYSTEM)
chelper_fetch_thirdparty(
  glaze GIT_REPOSITORY https://github.com/stephenberry/glaze.git GIT_TAG v9.0.0)
chelper_fetch_thirdparty(spdlog GIT_REPOSITORY
                         https://github.com/gabime/spdlog.git GIT_TAG v1.17.0)
if(BUILD_TESTING OR CHELPER_BUILD_BENCHMARKS)
  chelper_fetch_thirdparty(
    googletest GIT_REPOSITORY https://github.com/google/googletest.git GIT_TAG
    v1.18.0)
endif()

# Generated paths contain no configuration-specific values.
set(RESOURCE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../CHelper-Resource)
configure_file(ParamDeliver.h.in
               ${CMAKE_CURRENT_BINARY_DIR}/include/ParamDeliver.h @ONLY)

include(cmake/Sources.cmake)

function(chelper_enable_sanitizers target)
  if(CHELPER_ENABLE_SANITIZERS)
    if(MSVC)
      target_compile_options(${target} PRIVATE /fsanitize=address)
      target_link_options(${target} PRIVATE /INCREMENTAL:NO)
    else()
      target_compile_options(${target} PRIVATE -fsanitize=address,undefined
                                               -fno-omit-frame-pointer)
      target_link_options(${target} PRIVATE -fsanitize=address,undefined)
    endif()
  endif()
endfunction()

# MSVC STL annotations must agree in every object linked into an ASan binary.
foreach(dependency fmt spdlog gtest gtest_main)
  if(TARGET ${dependency})
    chelper_enable_sanitizers(${dependency})
  endif()
endforeach()

function(chelper_configure_target target)
  if(CHELPER_ENABLE_RESOURCE_AUDIT)
    target_compile_definitions(${target} PUBLIC CHELPER_RESOURCE_AUDIT=1)
  endif()
  target_compile_features(${target} PUBLIC cxx_std_23)
  target_compile_definitions(${target}
                             PUBLIC CHelperDebug=$<IF:$<CONFIG:Debug>,1,0>)
  if(MSVC)
    target_compile_options(${target} PRIVATE /W4 /wd4100 /utf-8 /Zc:__cplusplus
                                             /bigobj)
  else()
    target_compile_options(${target} PRIVATE -Wall -Wextra
                                             -Wno-unused-parameter)
  endif()
  chelper_enable_sanitizers(${target})

endfunction()

function(chelper_add_core target)
  add_library(${target} STATIC ${CHELPER_CORE_SOURCE_FILES})
  chelper_configure_target(${target})
  target_precompile_headers(${target} PRIVATE
                            ${CMAKE_CURRENT_SOURCE_DIR}/include/pch.h)
  target_include_directories(
    ${target}
    PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include
           ${CMAKE_CURRENT_BINARY_DIR}/include ${CMAKE_CURRENT_SOURCE_DIR}/src)
  target_link_libraries(
    ${target} PUBLIC fmt::fmt spdlog::spdlog utf8cpp xxHash::xxhash
                     unordered_dense::unordered_dense glaze::glaze)
  set_target_properties(${target} PROPERTIES CXX_VISIBILITY_PRESET hidden
                                             VISIBILITY_INLINES_HIDDEN ON)
  if(CHELPER_BUILD_FUZZERS)
    target_compile_options(
      ${target} PRIVATE -fsanitize=fuzzer-no-link,address,undefined
                        -fno-omit-frame-pointer)
  endif()
endfunction()

chelper_add_core(CHelperCore)
add_library(CHelper::Core ALIAS CHelperCore)
chelper_add_core(CHelperNoFilesystemCore)
target_compile_definitions(CHelperNoFilesystemCore PUBLIC CHELPER_NO_FILESYSTEM)
add_library(CHelper::NoFilesystemCore ALIAS CHelperNoFilesystemCore)

if(ANDROID)
  add_library(CHelperAndroid SHARED src/apps/CHelperAndroid.cpp)
  chelper_configure_target(CHelperAndroid)
  target_compile_definitions(CHelperAndroid PRIVATE CHelperAndroid)
  target_link_libraries(CHelperAndroid PRIVATE CHelper::Core android log)
  target_link_options(CHelperAndroid PRIVATE "-Wl,--exclude-libs,ALL")
  set_target_properties(CHelperAndroid PROPERTIES CXX_VISIBILITY_PRESET hidden
                                                  VISIBILITY_INLINES_HIDDEN ON)
endif()

if(EMSCRIPTEN)
  add_library(CHelperWeb src/apps/CHelperWeb.cpp)
  chelper_configure_target(CHelperWeb)
  target_link_libraries(CHelperWeb PRIVATE CHelper::NoFilesystemCore)
  set_target_properties(CHelperWeb PROPERTIES CXX_VISIBILITY_PRESET hidden
                                              VISIBILITY_INLINES_HIDDEN ON)
endif()

if(NOT ANDROID AND NOT EMSCRIPTEN)
  add_executable(CHelperResourceGenerator src/apps/CHelperResourceGenerator.cpp)
  chelper_configure_target(CHelperResourceGenerator)
  target_link_libraries(CHelperResourceGenerator PRIVATE CHelper::Core)

  if(BUILD_TESTING)
    add_executable(CHelperTest ${CHELPER_TEST_SOURCE_FILES}
                               src/apps/CHelperWeb.cpp)
    chelper_configure_target(CHelperTest)
    target_link_libraries(CHelperTest PRIVATE CHelper::Core GTest::gtest_main)
    include(GoogleTest)
    gtest_discover_tests(CHelperTest DISCOVERY_TIMEOUT 30)
  endif()
  if(CHELPER_BUILD_BENCHMARKS)
    add_executable(CHelperBench tests/BenchTest.cpp
                                tests/LongCommandBenchTest.cpp)
    chelper_configure_target(CHelperBench)
    target_link_libraries(CHelperBench PRIVATE CHelper::Core GTest::gtest_main)
    add_executable(CHelperLatencyBench tests/BenchTest.cpp
                                       tests/LongCommandBenchTest.cpp)
    chelper_configure_target(CHelperLatencyBench)
    target_compile_definitions(CHelperLatencyBench
                               PRIVATE CHELPER_BENCH_TIMING_ONLY=1)
    target_link_libraries(CHelperLatencyBench PRIVATE CHelper::Core
                                                      GTest::gtest_main)
  endif()
  if(CHELPER_BUILD_FUZZERS)
    if(NOT CMAKE_CXX_COMPILER_ID STREQUAL "Clang" OR MSVC)
      message(
        FATAL_ERROR "CHelper fuzzers require Clang with libFuzzer (Linux/macOS)"
      )
    endif()
    add_executable(CHelperFuzz tests/fuzz/CoreFuzz.cpp)
    chelper_configure_target(CHelperFuzz)
    target_compile_options(CHelperFuzz
                           PRIVATE -UNDEBUG -fsanitize=fuzzer,address,undefined)
    target_link_options(CHelperFuzz PRIVATE -fsanitize=fuzzer,address,undefined)
    target_link_libraries(CHelperFuzz PRIVATE CHelper::Core)
  endif()

  find_package(Qt6 QUIET COMPONENTS Core Gui Widgets)
  if(Qt6_FOUND)
    add_executable(
      CHelperQt WIN32 src/apps/qt/CHelperQt.h src/apps/qt/CHelperQt.cpp
                      src/apps/qt/chelper.qrc src/apps/qt/chelper.ui)
    chelper_configure_target(CHelperQt)
    set_target_properties(
      CHelperQt
      PROPERTIES AUTOMOC ON
                 AUTORCC ON
                 AUTOUIC ON)
    target_compile_definitions(CHelperQt PRIVATE QT_NO_KEYWORDS)
    target_link_libraries(CHelperQt PRIVATE CHelper::Core Qt6::Core Qt6::Gui
                                            Qt6::Widgets)
  endif()
endif()
