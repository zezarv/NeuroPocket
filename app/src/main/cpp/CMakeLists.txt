cmake_minimum_required(VERSION 3.22)
project(neuropocket)

# Vulkan SDK для шейдеров ggml-vulkan (glslc + SPIRV-Headers из SDK).
if(DEFINED ENV{VULKAN_SDK})
    list(APPEND CMAKE_PREFIX_PATH "$ENV{VULKAN_SDK}/Lib/cmake")
    find_program(Vulkan_GLSLC_EXECUTABLE glslc PATHS "$ENV{VULKAN_SDK}/Bin" NO_DEFAULT_PATH)
endif()
if(NOT DEFINED SPIRV-Headers_DIR AND EXISTS "C:/VulkanSDK/1.4.357.0/Lib/cmake/SPIRV-Headers/SPIRV-HeadersConfig.cmake")
    set(SPIRV-Headers_DIR "C:/VulkanSDK/1.4.357.0/Lib/cmake/SPIRV-Headers" CACHE PATH "" FORCE)
endif()

# Keep llama.cpp lean: no tests/tools/server/examples (auto-off when added as subdir,
# but force explicitly for safety).
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
# mtmd отдельно (vision), без tools tree
set(LLAMA_BUILD_MTMD ON CACHE BOOL "" FORCE)
# GPU-offload на Adreno через Vulkan (S24U). Нужен Vulkan SDK на машине сборки.
set(GGML_VULKAN ON CACHE BOOL "" FORCE)
# ccache на этой машине бит (находит, но не запускается) — только прямые вызовы
set(GGML_CCACHE OFF CACHE BOOL "" FORCE)

# vulkan.hpp (C++ биндинги) есть только в Vulkan SDK, в NDK их нет.
if(EXISTS "C:/VulkanSDK/1.4.357.0/Include/vulkan/vulkan.hpp")
    add_compile_options(-IC:/VulkanSDK/1.4.357.0/Include)
endif()

add_subdirectory(llama.cpp)

add_library(neuropocket SHARED bridge.cpp)

target_include_directories(neuropocket PRIVATE
    llama.cpp/include
    llama.cpp/ggml/include
    llama.cpp/tools/mtmd
)

target_link_libraries(neuropocket PRIVATE
    llama
    mtmd
    android
    log
)

# arm64 CPU tuning
target_compile_options(neuropocket PRIVATE -O3 -fvisibility=hidden)

# --- whisper: prebuilt statics (separate build dir, avoids ggml target clash with llama) ---
add_library(whisper_prebuilt STATIC IMPORTED)
set_target_properties(whisper_prebuilt PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/whisper-build/src/libwhisper.a)
add_library(wggml STATIC IMPORTED)
set_target_properties(wggml PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/whisper-build/ggml/src/libggml.a)
add_library(wggml_base STATIC IMPORTED)
set_target_properties(wggml_base PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/whisper-build/ggml/src/libggml-base.a)
add_library(wggml_cpu STATIC IMPORTED)
set_target_properties(wggml_cpu PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/whisper-build/ggml/src/libggml-cpu.a)

add_library(npwhisper SHARED whisper_bridge.cpp)

target_include_directories(npwhisper PRIVATE
    whisper.cpp/include
    whisper.cpp/ggml/include
)

target_link_libraries(npwhisper PRIVATE
    whisper_prebuilt
    wggml
    wggml_base
    wggml_cpu
    android
    log
)

target_compile_options(npwhisper PRIVATE -O3 -fvisibility=hidden -fopenmp)
target_link_options(npwhisper PRIVATE -fopenmp)
# hide static ggml/whisper symbols inside the .so to avoid clashes with llama's ggml at runtime
target_link_options(npwhisper PRIVATE "LINKER:--exclude-libs,ALL")

# --- stable-diffusion: prebuilt static (separate build dir, avoids ggml target clash) ---
add_library(sd_prebuilt STATIC IMPORTED)
set_target_properties(sd_prebuilt PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/sd-build/libstable-diffusion.a)
add_library(sggml STATIC IMPORTED)
set_target_properties(sggml PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/sd-build/ggml/src/libggml.a)
add_library(sggml_base STATIC IMPORTED)
set_target_properties(sggml_base PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/sd-build/ggml/src/libggml-base.a)
add_library(sggml_cpu STATIC IMPORTED)
set_target_properties(sggml_cpu PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/sd-build/ggml/src/libggml-cpu.a)

add_library(npsd SHARED sd_bridge.cpp)

target_include_directories(npsd PRIVATE
    sd.cpp/include
    sd.cpp/ggml/include
)

target_link_libraries(npsd PRIVATE
    sd_prebuilt
    sggml
    sggml_base
    sggml_cpu
    android
    log
)

target_compile_options(npsd PRIVATE -Os -fvisibility=hidden -fopenmp -flto=thin)
target_link_options(npsd PRIVATE -fopenmp -flto=thin "LINKER:--exclude-libs,ALL" "-Wl,--icf=all" "-Wl,-O1")
