cmake_minimum_required(VERSION 3.22.1)
project(soide LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ============================================================
# 第三方库：Capstone 反汇编引擎 (真库集成)
# ============================================================
# 集成 capstone-engine/capstone (https://github.com/capstone-engine/capstone)
# 只启用 ARM + AArch64 架构，节省体积。
set(CAPSTONE_BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(CAPSTONE_BUILD_STATIC_LIBS ON  CACHE BOOL "" FORCE)
set(CAPSTONE_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(CAPSTONE_BUILD_CSTOOL   OFF CACHE BOOL "" FORCE)
set(CAPSTONE_BUILD_CSTEST   OFF CACHE BOOL "" FORCE)
set(CAPSTONE_INSTALL        OFF CACHE BOOL "" FORCE)
set(CAPSTONE_ARCHITECTURE_DEFAULT OFF CACHE BOOL "" FORCE)
# 只启用 ARM (32-bit) 和 AArch64 (64-bit)
set(CAPSTONE_ARM_SUPPORT    ON  CACHE BOOL "" FORCE)
set(CAPSTONE_AARCH64_SUPPORT ON CACHE BOOL "" FORCE)
# 关闭所有其他架构
set(CAPSTONE_M68K_SUPPORT       OFF CACHE BOOL "" FORCE)
set(CAPSTONE_MIPS_SUPPORT       OFF CACHE BOOL "" FORCE)
set(CAPSTONE_PPC_SUPPORT        OFF CACHE BOOL "" FORCE)
set(CAPSTONE_SPARC_SUPPORT      OFF CACHE BOOL "" FORCE)
set(CAPSTONE_SYSZ_SUPPORT       OFF CACHE BOOL "" FORCE)
set(CAPSTONE_XCORE_SUPPORT      OFF CACHE BOOL "" FORCE)
set(CAPSTONE_X86_SUPPORT        OFF CACHE BOOL "" FORCE)
set(CAPSTONE_TMS320C64X_SUPPORT OFF CACHE BOOL "" FORCE)
set(CAPSTONE_M680X_SUPPORT      OFF CACHE BOOL "" FORCE)
set(CAPSTONE_EVM_SUPPORT        OFF CACHE BOOL "" FORCE)
set(CAPSTONE_MOS65XX_SUPPORT    OFF CACHE BOOL "" FORCE)
set(CAPSTONE_WASM_SUPPORT       OFF CACHE BOOL "" FORCE)
set(CAPSTONE_BPF_SUPPORT        OFF CACHE BOOL "" FORCE)
set(CAPSTONE_RISCV_SUPPORT      OFF CACHE BOOL "" FORCE)
set(CAPSTONE_SH_SUPPORT         OFF CACHE BOOL "" FORCE)
set(CAPSTONE_TRICORE_SUPPORT    OFF CACHE BOOL "" FORCE)
set(CAPSTONE_ALPHA_SUPPORT      OFF CACHE BOOL "" FORCE)
set(CAPSTONE_HPPA_SUPPORT       OFF CACHE BOOL "" FORCE)
set(CAPSTONE_LOONGARCH_SUPPORT  OFF CACHE BOOL "" FORCE)
set(CAPSTONE_XTENSA_SUPPORT     OFF CACHE BOOL "" FORCE)
set(CAPSTONE_ARC_SUPPORT        OFF CACHE BOOL "" FORCE)

add_subdirectory(third_party/capstone EXCLUDE_FROM_ALL)

# ============================================================
# 本模块 native 库
# ============================================================
add_library(soide-native SHARED
    native-bridge.cpp
    ndk_disasm.cpp
    ndk_asm.cpp
)

# 公共链接
find_library(log-lib log)
target_link_libraries(soide-native
    PRIVATE
        capstone_static
        ${log-lib}
)
