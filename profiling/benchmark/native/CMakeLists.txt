cmake_minimum_required(VERSION 3.22)
project(vlm_benchmark CXX)
set(CMAKE_CXX_STANDARD 17)
if(NOT LLAMA_SOURCE OR NOT LLAMA_BUILD)
    message(FATAL_ERROR "Set LLAMA_SOURCE and LLAMA_BUILD to the pinned llama.cpp checkout and its Android static build")
endif()
add_executable(vlm-bench main.cpp)
target_include_directories(vlm-bench PRIVATE "${LLAMA_SOURCE}/include" "${LLAMA_SOURCE}/ggml/include" "${LLAMA_SOURCE}/tools/mtmd" "${LLAMA_SOURCE}/vendor")
target_link_libraries(vlm-bench PRIVATE
    "-Wl,--start-group"
    "${LLAMA_BUILD}/tools/mtmd/libmtmd.a" "${LLAMA_BUILD}/src/libllama.a"
    "${LLAMA_BUILD}/ggml/src/libggml.a" "${LLAMA_BUILD}/ggml/src/libggml-cpu.a" "${LLAMA_BUILD}/ggml/src/libggml-base.a"
    "${LLAMA_BUILD}/vendor/hash/libvendor-hash.a"
    "-Wl,--end-group" log android dl)
