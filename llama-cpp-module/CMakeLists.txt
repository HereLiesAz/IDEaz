cmake_minimum_required(VERSION 3.22.1)
project("llama-android")

# llama.cpp must be present as a git submodule at ./llama.cpp (see README.md).
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON   ON  CACHE BOOL "" FORCE)
add_subdirectory(llama.cpp build-llama)

add_library(llama-android SHARED llama-android.cpp)

find_library(log-lib log)
# `common` provides common_init_from_params / tokenization helpers used by the glue.
target_link_libraries(llama-android llama common android ${log-lib})
