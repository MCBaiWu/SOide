// ndk_disasm.cpp
// 极简 ARM / Thumb 反汇编器子集实现。覆盖常见助记符，用于在 NDK native 层
// 完成 capstone 的部分功能。注意：本实现是子集，针对 SO 解析的常用指令，
// 并不追求 100% 兼容 capstone 的全量指令集。
#include <cstdint>
#include <cstring>
#include <cstdio>
#include <cstdlib>
#include "ndk_native.h"

static void set_mn(ndk_insn_t* i, const char* m) {
    std::snprintf(i->mnemonic, sizeof(i->mnemonic), "%s", m);
}
static void set_op(ndk_insn_t* i, const char* o) {
    std::snprintf(i->op_str, sizeof(i->op_str), "%s", o);
}

static const char* reg_name(uint32_t r) {
    static const char* names[16] = {
        "r0","r1","r2","r3","r4","r5","r6","r7",
        "r8","r9","r10","r11","r12","sp","lr","pc"
    };
    if (r < 16) return names[r];
    return "?";
}

static int emit_unknown(ndk_insn_t* i, uint64_t addr, const uint8_t* code, int n) {
    i->address = addr;
    i->size = (uint16_t) n;
    for (int k = 0; k < n && k < 8; k++) i->bytes[k] = code[k];
    for (int k = n; k < 8; k++) i->bytes[k] = 0;
    set_mn(i, "byte");
    set_op(i, "(unknown)");
    return 1;
}

// ---------------- Thumb ----------------

static int dis_thumb_one(const uint8_t* code, size_t len, uint64_t addr, ndk_insn_t* out) {
    if (len < 2) return 0;
    uint16_t hw = (uint16_t) code[0] | ((uint16_t) code[1] << 8);
    out->address = addr;
    out->size = 2;
    out->bytes[0] = code[0];
    out->bytes[1] = code[1];
    out->bytes[2] = 0;
    out->bytes[3] = 0;

    // mov rd, rm (0x4600 | (rd << 8) | (rm << 3))
    if ((hw & 0xff00) == 0x4600) {
        uint32_t rd = (hw >> 8) & 0x7;
        uint32_t rm = (hw >> 3) & 0x7;
        set_mn(out, "mov");
        std::snprintf(out->op_str, sizeof(out->op_str), "%s, %s",
                     reg_name(rd), reg_name(rm));
        return 1;
    }
    // ldr Rt, [Rn] (0x6800 | (rt<<8) | (rn<<3))
    if ((hw & 0xf600) == 0x6800) {
        uint32_t rt = (hw >> 8) & 0x7;
        uint32_t rn = (hw >> 3) & 0x7;
        set_mn(out, "ldr");
        std::snprintf(out->op_str, sizeof(out->op_str), "%s, [%s]",
                     reg_name(rt), reg_name(rn));
        return 1;
    }
    // str Rt, [Rn] (0x6000 | (rt<<8) | (rn<<3))
    if ((hw & 0xf600) == 0x6000) {
        uint32_t rt = (hw >> 8) & 0x7;
        uint32_t rn = (hw >> 3) & 0x7;
        set_mn(out, "str");
        std::snprintf(out->op_str, sizeof(out->op_str), "%s, [%s]",
                     reg_name(rt), reg_name(rn));
        return 1;
    }
    // push {regs} (0xb500 | (regs & 0xfff))
    if ((hw & 0xff00) == 0xb500) {
        set_mn(out, "push");
        std::snprintf(out->op_str, sizeof(out->op_str), "{%s%s}",
                     hw & 0x100 ? "lr" : "", "regs");
        return 1;
    }
    // pop {regs} (0xbd00 | (regs & 0xfff))
    if ((hw & 0xff00) == 0xbd00) {
        set_mn(out, "pop");
        std::snprintf(out->op_str, sizeof(out->op_str), "{%s%s}",
                     hw & 0x100 ? "pc" : "", "regs");
        return 1;
    }
    // nop
    if (hw == 0x46c0 || hw == 0xbf00) {
        set_mn(out, "nop");
        out->op_str[0] = 0;
        return 1;
    }
    // bx rm (0x4700 | (rm << 3))
    if ((hw & 0xff80) == 0x4700) {
        uint32_t rm = (hw >> 3) & 0xf;
        set_mn(out, "bx");
        std::snprintf(out->op_str, sizeof(out->op_str), "%s", reg_name(rm));
        return 1;
    }
    // blx rm (0x4780 | (rm << 3))
    if ((hw & 0xff80) == 0x4780) {
        uint32_t rm = (hw >> 3) & 0xf;
        set_mn(out, "blx");
        std::snprintf(out->op_str, sizeof(out->op_str), "%s", reg_name(rm));
        return 1;
    }
    // b unconditional (0xe000 | imm11) - signed
    if ((hw & 0xf800) == 0xe000) {
        int32_t off = hw & 0x7ff;
        if (off & 0x400) off |= ~0x7ff;
        off *= 4;
        set_mn(out, "b");
        std::snprintf(out->op_str, sizeof(out->op_str), "0x%llx", (unsigned long long)(addr + 4 + off));
        return 1;
    }

    return emit_unknown(out, addr, code, 2);
}

// ---------------- ARM (A1 encoding simplified) ----------------

static int dis_arm_one(const uint8_t* code, size_t len, uint64_t addr, ndk_insn_t* out) {
    if (len < 4) return 0;
    uint32_t ins = (uint32_t) code[0]
                 | ((uint32_t) code[1] << 8)
                 | ((uint32_t) code[2] << 16)
                 | ((uint32_t) code[3] << 24);
    out->address = addr;
    out->size = 4;
    out->bytes[0] = code[0];
    out->bytes[1] = code[1];
    out->bytes[2] = code[2];
    out->bytes[3] = code[3];

    // nop: 0xe320f000
    if (ins == 0xe320f000) { set_mn(out, "nop"); out->op_str[0] = 0; return 1; }
    // bx rm: 0xe12fff10 | rm
    if ((ins & 0x0fffffff) == 0x012fff10) {
        set_mn(out, "bx");
        std::snprintf(out->op_str, sizeof(out->op_str), "%s", reg_name(ins & 0xf));
        return 1;
    }
    // blx rm: 0xe12fff30 | rm
    if ((ins & 0x0fffffff) == 0x012fff30) {
        set_mn(out, "blx");
        std::snprintf(out->op_str, sizeof(out->op_str), "%s", reg_name(ins & 0xf));
        return 1;
    }
    // push {regs}: 0xe92d????  stmdb sp!, {regs}
    if ((ins & 0xffff0000) == 0xe92d0000) {
        set_mn(out, "push");
        std::snprintf(out->op_str, sizeof(out->op_str), "{%s}", "regs");
        return 1;
    }
    // pop {regs}: 0xe8bd????  ldmia sp!, {regs}
    if ((ins & 0xffff0000) == 0xe8bd0000) {
        set_mn(out, "pop");
        std::snprintf(out->op_str, sizeof(out->op_str), "{%s}", "regs");
        return 1;
    }
    // mov rd, rm: 0xe1a00000 | (rd << 12) | rm
    if ((ins & 0xffff0ff0) == 0xe1a00000) {
        uint32_t rd = (ins >> 12) & 0xf;
        uint32_t rm = ins & 0xf;
        set_mn(out, "mov");
        std::snprintf(out->op_str, sizeof(out->op_str), "%s, %s",
                     reg_name(rd), reg_name(rm));
        return 1;
    }
    // movw rd, #imm16: 0xe3000000 | (rd<<12) | imm
    if ((ins & 0xfff00000) == 0xe3000000) {
        uint32_t rd = (ins >> 12) & 0xf;
        uint32_t imm = ins & 0xfff;
        imm |= ((ins >> 4) & 0xf000);
        set_mn(out, "movw");
        std::snprintf(out->op_str, sizeof(out->op_str), "%s, #0x%x",
                     reg_name(rd), imm);
        return 1;
    }
    // movt rd, #imm16: 0xe3400000 | (rd<<12) | imm
    if ((ins & 0xfff00000) == 0xe3400000) {
        uint32_t rd = (ins >> 12) & 0xf;
        uint32_t imm = ins & 0xfff;
        imm |= ((ins >> 4) & 0xf000);
        set_mn(out, "movt");
        std::snprintf(out->op_str, sizeof(out->op_str), "%s, #0x%x",
                     reg_name(rd), imm);
        return 1;
    }
    return emit_unknown(out, addr, code, 4);
}

// ---------------- entry ----------------

extern "C" int ndk_disasm_arm(const uint8_t* code, size_t len, uint64_t address, int is_thumb,
                              ndk_insn_t* out, int max_out) {
    if (!code || !out || max_out <= 0) return 0;
    size_t pos = 0;
    uint64_t addr = address;
    int n = 0;
    while (pos < len && n < max_out) {
        int consumed = 0;
        if (is_thumb) {
            consumed = dis_thumb_one(code + pos, len - pos, addr, &out[n]);
        } else {
            consumed = dis_arm_one(code + pos, len - pos, addr, &out[n]);
        }
        if (consumed <= 0) break;
        pos += (size_t) consumed;
        addr += (uint64_t) consumed;
        n++;
    }
    return n;
}
