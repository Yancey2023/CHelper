@REM 加载环境（假设已经下载好了emsdk并且加进了环境变量）
echo ----- 加载环境 -----
call emsdk_env.bat
call cd ..

@REM 编译Release模式
echo ----- [Release] 加载CMake项目 -----
call cmake -B cmake-build-emscripten-release -D CMAKE_BUILD_TYPE=Release -D CMAKE_TOOLCHAIN_FILE="D:\emsdk\upstream\emscripten\cmake\Modules\Platform\Emscripten.cmake" -G "Ninja"
call cd cmake-build-emscripten-release
echo ----- [Release] 编译CMake项目 -----
call cmake --build . --target CHelperWeb --parallel
echo ----- [Release] 链接wasm -----
call emcc libCHelperWeb.a include/CHelper-Core/libCHelperNoFilesystemCore.a include/CHelper-Core/include/fmt/libfmt.a include/CHelper-Core/include/spdlog/libspdlog.a include/CHelper-Core/include/xxHash/cmake_unofficial/libxxhash.a -O3 -o libCHelperWeb.js -s FILESYSTEM=0 -s DISABLE_EXCEPTION_CATCHING=1 -s ALLOW_MEMORY_GROWTH -s ENVIRONMENT="web" -s EXPORTED_FUNCTIONS="['_init','_release','_onTextChanged','_onSelectionChanged','_getParamHint','_getErrorReasons','_getSuggestionSize','_getSuggestion','_getAllSuggestions','_onSuggestionClick','_getSyntaxTokens','_malloc','_free']" -s WASM=1 -s "EXPORTED_RUNTIME_METHODS=[]"
call cd ..

@REM 编译MinSizeRel模式
echo ----- [MinSizeRel] 加载CMake项目 -----
call cmake -B cmake-build-emscripten-minsizerel -D CMAKE_BUILD_TYPE=MinSizeRel -D CMAKE_TOOLCHAIN_FILE="D:\emsdk\upstream\emscripten\cmake\Modules\Platform\Emscripten.cmake" -G "Ninja"
call cd cmake-build-emscripten-minsizerel
echo ----- [MinSizeRel] 编译CMake项目 -----
call cmake --build . --target CHelperWeb --parallel
echo ----- [MinSizeRel] 链接wasm -----
call emcc libCHelperWeb.a include/CHelper-Core/libCHelperNoFilesystemCore.a include/CHelper-Core/include/fmt/libfmt.a include/CHelper-Core/include/spdlog/libspdlog.a include/CHelper-Core/include/xxHash/cmake_unofficial/libxxhash.a -Os -o libCHelperWeb.js -s FILESYSTEM=0 -s DISABLE_EXCEPTION_CATCHING=1 -s ALLOW_MEMORY_GROWTH -s ENVIRONMENT="web" -s EXPORTED_FUNCTIONS="['_init','_release','_onTextChanged','_onSelectionChanged','_getParamHint','_getErrorReasons','_getSuggestionSize','_getSuggestion','_getAllSuggestions','_onSuggestionClick','_getSyntaxTokens','_malloc','_free']" -s WASM=1 -s "EXPORTED_RUNTIME_METHODS=[]"
call cd ..

@REM 修改JS胶水代码
echo ----- 修改JS胶水代码 -----
call python ./script/patch-wasm.py
