cmake_minimum_required(VERSION 3.31)

set(sourceDir "${CMAKE_CURRENT_LIST_DIR}/..")
set(buildDir "$ENV{CHELPER_WASM_BUILD_DIR}")
set(configureStamp "${buildDir}/.web-configure")
set(userPresets "${sourceDir}/CMakeUserPresets.json")
find_program(webNinja NAMES ninja REQUIRED)
file(SHA256 "${sourceDir}/CMakePresets.json" presetsHash)
string(SHA256 configureKey
              "${presetsHash}\n${CMAKE_COMMAND}\n${webNinja}\n$ENV{EMSDK}")

# Ninja tracks the CMake sources. Preset changes need an explicit configure
# because Ninja's regeneration command does not reapply the preset.
if(EXISTS "${configureStamp}"
   AND EXISTS "${buildDir}/CMakeCache.txt"
   AND EXISTS "${buildDir}/build.ninja"
   AND EXISTS "${buildDir}/cmake_install.cmake"
   AND NOT EXISTS "${userPresets}")
  file(READ "${configureStamp}" previousKey)
  if(previousKey STREQUAL configureKey)
    return()
  endif()
endif()

file(REMOVE "${configureStamp}")
execute_process(
  COMMAND "${CMAKE_COMMAND}" --preset web
          "-DCMAKE_MAKE_PROGRAM:FILEPATH=${webNinja}"
  WORKING_DIRECTORY "${sourceDir}" COMMAND_ERROR_IS_FATAL ANY)
# User presets may include files outside the project.
if(NOT EXISTS "${userPresets}")
  file(WRITE "${configureStamp}" "${configureKey}")
endif()
