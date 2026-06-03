cmake_minimum_required(VERSION 3.22.1)
project(soide LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 公共 native 库：keystone + capstone 风格的反汇编/汇编支持
add_library(soide-native SHARED
    native-bridge.cpp
    ndk_disasm.cpp
    ndk_asm.cpp
)

# 公共链接
find_library(log-lib log)
target_link_libraries(soide-native ${log-lib})
