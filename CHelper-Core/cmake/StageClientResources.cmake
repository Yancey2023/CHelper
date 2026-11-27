cmake_minimum_required(VERSION 3.31)

get_filename_component(projectRoot "${CMAKE_CURRENT_LIST_DIR}/../.." ABSOLUTE)
set(resourcesSource "$ENV{usage_source}")
set(resourcesOutput "$ENV{usage_output}")
if(resourcesSource STREQUAL "" OR resourcesOutput STREQUAL "")
  message(FATAL_ERROR "Pass the source and output directories through mise.")
endif()
cmake_path(ABSOLUTE_PATH resourcesSource BASE_DIRECTORY "${projectRoot}"
           NORMALIZE)
cmake_path(ABSOLUTE_PATH resourcesOutput BASE_DIRECTORY "${projectRoot}"
           NORMALIZE)
set(buildRoot "${projectRoot}/build")
cmake_path(IS_PREFIX buildRoot "${resourcesOutput}" NORMALIZE isBuildOutput)
if(NOT isBuildOutput OR resourcesOutput STREQUAL buildRoot)
  message(FATAL_ERROR "Resource staging requires an output below ${buildRoot}.")
endif()

if(CMAKE_HOST_WIN32)
  set(executableSuffix ".exe")
endif()
execute_process(
  COMMAND
    "${projectRoot}/build/resource_generator/CHelperResourceGenerator${executableSuffix}"
    "${resourcesSource}" "${resourcesOutput}" --binary-only
    COMMAND_ERROR_IS_FATAL ANY)

foreach(assetDirectory IN ITEMS about rawtext)
  set(source "${projectRoot}/CHelper-Compose/resources/${assetDirectory}")
  set(staged "${resourcesOutput}/${assetDirectory}")
  file(
    GLOB_RECURSE stagedFiles
    RELATIVE "${staged}"
    "${staged}/*")
  if(assetDirectory STREQUAL "about")
    list(REMOVE_ITEM stagedFiles release_note.txt)
  endif()
  foreach(relativePath IN LISTS stagedFiles)
    if(NOT EXISTS "${source}/${relativePath}")
      file(REMOVE "${staged}/${relativePath}")
    endif()
  endforeach()
  file(COPY "${source}" DESTINATION "${resourcesOutput}")
endforeach()

file(COPY_FILE "${projectRoot}/CHANGELOG.md"
     "${resourcesOutput}/about/release_note.txt" ONLY_IF_DIFFERENT)
