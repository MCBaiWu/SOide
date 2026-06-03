// ndk_disasm.cpp
// 真 Capstone 反汇编库的 JNI 包装。
// 底层调用 capstone-engine/capstone (https://github.com/capstone-engine/capstone)。
// 启用 ARM / Thumb / AArch64 (ARM64) 架构，详见 CMakeLists.txt。
#include <cstdint>
#include <cstring>
#include <cstdio>
#include <cstdlib>
#include <capstone/capstone.h>

#include "ndk_native.h"

// ---------------------------------------------------------------
// 通用 disasm 辅助：把 capstone 的 cs_insn 序列写入 ndk_insn_t[]
// ---------------------------------------------------------------
static int copy_cs_to_ndk(const cs_insn* insn, size_t count,
                          ndk_insn_t* out, int max_out) {
    int n = 0;
    for (size_t i = 0; i < count && n < max_out; ++i) {
        const cs_insn& ci = insn[i];
        uint16_t sz = (uint16_t) ci.size;
        if (sz > 8) sz = 8;
        ndk_insn_t& o = out[n];
        o.address = (uint64_t) ci.address;
        o.size = sz;
        std::memset(o.bytes, 0, sizeof(o.bytes));
        if (ci.bytes && sz > 0) {
            std::memcpy(o.bytes, ci.bytes, sz);
        }
        std::snprintf(o.mnemonic, sizeof(o.mnemonic), "%s", ci.mnemonic);
        std::snprintf(o.op_str,  sizeof(o.op_str),  "%s", ci.op_str);
        ++n;
    }
    return n;
}

static int do_disasm(cs_arch arch, cs_mode mode,
                     const uint8_t* code, size_t len, uint64_t address,
                     ndk_insn_t* out, int max_out) {
    if (!code || !out || max_out <= 0) return 0;

    csh handle = 0;
    if (cs_open(arch, mode, &handle) != CS_ERR_OK) {
        return 0;
    }
    cs_option(handle, CS_OPT_SYNTAX, CS_OPT_SYNTAX_DEFAULT);
    // Capstone 5.x: 详情开关（开启后 op_str 更详细）
    cs_option(handle, CS_OPT_DETAIL, CS_OPT_OFF);

    cs_insn* insn = nullptr;
    size_t count = cs_disasm(handle, code, len, address, 0, &insn);
    int n = 0;
    if (count > 0 && insn != nullptr) {
        n = copy_cs_to_ndk(insn, count, out, max_out);
    }
    if (insn) cs_free(insn, count);
    cs_close(&handle);
    return n;
}

// ---------------------------------------------------------------
// ARM32 反汇编 (is_thumb=0 → ARM, is_thumb=1 → Thumb)
// ---------------------------------------------------------------
extern "C" int ndk_disasm_arm(const uint8_t* code, size_t len, uint64_t address, int is_thumb,
                              ndk_insn_t* out, int max_out) {
    cs_mode mode = is_thumb ? CS_MODE_THUMB : CS_MODE_ARM;
    return do_disasm(CS_ARCH_ARM, mode, code, len, address, out, max_out);
}

// ---------------------------------------------------------------
// ARM64 (AArch64) 反汇编
// ---------------------------------------------------------------
extern "C" int ndk_disasm_arm64(const uint8_t* code, size_t len, uint64_t address,
                                ndk_insn_t* out, int max_out) {
    return do_disasm(CS_ARCH_AARCH64, (cs_mode) CS_MODE_ARM,
                     code, len, address, out, max_out);
}
