cmake_minimum_required(VERSION 3.22.1)
project("molmo_agent_native" CXX C)

# Build llama.cpp as a static library (we only ship our JNI .so)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)

# CPU-only for Android (no CUDA/Metal/Vulkan)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_OPENCL OFF CACHE BOOL "" FORCE)

# Provide a build number so LLAMA_INSTALL_VERSION is valid
set(LLAMA_BUILD_NUMBER 1 CACHE STRING "" FORCE)

# Add llama.cpp (builds ggml + llama targets)
add_subdirectory(llama.cpp)

# Build mtmd (multimodal) as a static library from sources directly,
# bypassing llama.cpp/tools/mtmd/CMakeLists.txt which tries to build
# CLI executables that depend on targets we don't build.
set(MTMD_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/tools/mtmd)
add_library(mtmd STATIC
    ${MTMD_DIR}/mtmd.cpp
    ${MTMD_DIR}/mtmd-audio.cpp
    ${MTMD_DIR}/mtmd-image.cpp
    ${MTMD_DIR}/mtmd-helper.cpp
    ${MTMD_DIR}/clip.cpp
    ${MTMD_DIR}/models/cogvlm.cpp
    ${MTMD_DIR}/models/conformer.cpp
    ${MTMD_DIR}/models/deepseekocr.cpp
    ${MTMD_DIR}/models/glm4v.cpp
    ${MTMD_DIR}/models/internvl.cpp
    ${MTMD_DIR}/models/kimik25.cpp
    ${MTMD_DIR}/models/kimivl.cpp
    ${MTMD_DIR}/models/llama4.cpp
    ${MTMD_DIR}/models/llava.cpp
    ${MTMD_DIR}/models/minicpmv.cpp
    ${MTMD_DIR}/models/mobilenetv5.cpp
    ${MTMD_DIR}/models/nemotron-v2-vl.cpp
    ${MTMD_DIR}/models/paddleocr.cpp
    ${MTMD_DIR}/models/pixtral.cpp
    ${MTMD_DIR}/models/qwen2vl.cpp
    ${MTMD_DIR}/models/qwen3vl.cpp
    ${MTMD_DIR}/models/siglip.cpp
    ${MTMD_DIR}/models/whisper-enc.cpp
    ${MTMD_DIR}/models/youtuvl.cpp
)

target_link_libraries(mtmd PUBLIC ggml llama)
target_include_directories(mtmd PUBLIC ${MTMD_DIR})
target_include_directories(mtmd PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/vendor
)
target_compile_features(mtmd PRIVATE cxx_std_17)
# Suppress warnings from third-party stb_image.h / miniaudio.h
target_compile_options(mtmd PRIVATE -Wno-cast-qual)

# Our JNI bridge library
add_library(llama_jni SHARED llama_jni.cpp)

target_link_libraries(llama_jni
    PRIVATE
    llama
    mtmd
    ggml
    log      # Android logging
    android  # Android native APIs
)

target_include_directories(llama_jni
    PRIVATE
    llama.cpp/include
    llama.cpp/ggml/include
    llama.cpp/tools/mtmd
)

target_compile_features(llama_jni PRIVATE cxx_std_17)
target_compile_options(llama_jni PRIVATE -O3 -fPIC)
