cmake_minimum_required(VERSION 3.22.1)
project(scriverse_runtime LANGUAGES CXX)

add_library(scriverse_runtime SHARED runtime_bridge.cpp)

target_compile_features(scriverse_runtime PRIVATE cxx_std_20)
target_compile_options(scriverse_runtime PRIVATE -Wall -Wextra -Werror -fvisibility=hidden)
target_link_options(
    scriverse_runtime
    PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
target_link_libraries(scriverse_runtime PRIVATE log)
