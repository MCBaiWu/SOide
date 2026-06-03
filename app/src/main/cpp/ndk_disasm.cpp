// ndk_disasm.cpp
// 真 Capstone 反汇编库的 JNI 包装。
// 底层调用 capstone-engine/capstone (https://github.com/capstone-engine/capstone)。
// 仅启用 ARM + AArch64 架构，详见 CMakeLists.txt。
#include <cstdint>
#include <cstring>
#include <cstdio>
#include <cstdlib>
#include <capstone/capstone.h>

#include "ndk_native.h"

extern "C" int ndk_disasm_arm(const uint8_t* code, size_t len, uint64_t address, int is_thumb,
                              ndk_insn_t* out, int max_out) {
    if (!code || !out || max_out <= 0) return 0;

    csh handle = 0;
    cs_arch arch = CS_ARCH_ARM;
    cs_mode mode = is_thumb ? CS_MODE_THUMB : CS_MODE_ARM;

    if (cs_open(arch, mode, &handle) != CS_ERR_OK) {
        return 0;
    }

    // 让 Capstone 打印更详细的细节 (op_str)
    cs_option(handle, CS_OPT_SYNTAX, CS_OPT_SYNTAX_DEFAULT);

    cs_insn* insn = nullptr;
    size_t count = cs_disasm(handle, code, len, address, 0, &insn);
    if (count == 0 || insn == nullptr) {
        cs_close(&handle);
        return 0;
    }

    int n = 0;
    for (size_t i = 0; i < count && n < max_out; ++i) {
        const cs_insn& ci = insn[i];

        // bytes: Capstone 一次最多 16 字节，但 ARM 指令最长 4 字节、Thumb 2 字节
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

    cs_free(insn, count);
    cs_close(&handle);
    return n;
}
