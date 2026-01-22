cd ..

git clone https://github.com/emscripten-core/emsdk.git
cd ./emsdk
git pull
./emsdk install latest
./emsdk activate latest
source ./emsdk_env.sh
sudo apt-get install -y cmake ninja-build
cd ..

cmake -B cmake-build-emscripten-release -D CMAKE_BUILD_TYPE=Release -D CMAKE_TOOLCHAIN_FILE="./emsdk/upstream/emscripten/cmake/Modules/Platform/Emscripten.cmake" -G "Ninja"
cd cmake-build-emscripten-release
cmake --build . --target CHelperWeb --parallel
emcc libCHelperWeb.a include/CHelper-Core/libCHelperNoFilesystemCore.a include/CHelper-Core/include/fmt/libfmt.a include/CHelper-Core/include/spdlog/libspdlog.a include/CHelper-Core/include/xxHash/cmake_unofficial/libxxhash.a -O3 -o libCHelperWeb.js -s FILESYSTEM=0 -s DISABLE_EXCEPTION_CATCHING=1 -s ALLOW_MEMORY_GROWTH -s ENVIRONMENT="web" -s EXPORTED_FUNCTIONS="['_init','_release','_onTextChanged','_onSelectionChanged','_getParamHint','_getErrorReasons','_getSuggestionSize','_getSuggestion','_getAllSuggestions','_onSuggestionClick','_getSyntaxTokens','_malloc','_free']" -s WASM=1 -s "EXPORTED_RUNTIME_METHODS=[]"
cd ..

cmake -B cmake-build-emscripten-minsizerel -D CMAKE_BUILD_TYPE=MinSizeRel -D CMAKE_TOOLCHAIN_FILE="./emsdk/upstream/emscripten/cmake/Modules/Platform/Emscripten.cmake" -G "Ninja"
cd cmake-build-emscripten-minsizerel
cmake --build . --target CHelperWeb --parallel
emcc libCHelperWeb.a include/CHelper-Core/libCHelperNoFilesystemCore.a include/CHelper-Core/include/fmt/libfmt.a include/CHelper-Core/include/spdlog/libspdlog.a include/CHelper-Core/include/xxHash/cmake_unofficial/libxxhash.a -Os -o libCHelperWeb.js -s FILESYSTEM=0 -s DISABLE_EXCEPTION_CATCHING=1 -s ALLOW_MEMORY_GROWTH -s ENVIRONMENT="web" -s EXPORTED_FUNCTIONS="['_init','_release','_onTextChanged','_onSelectionChanged','_getParamHint','_getErrorReasons','_getSuggestionSize','_getSuggestion','_getAllSuggestions','_onSuggestionClick','_getSyntaxTokens','_malloc','_free']" -s WASM=1 -s "EXPORTED_RUNTIME_METHODS=[]"
cd ..

python3 ./script/patch-wasm.py