// ndk_native.h - 公共 native API
#pragma once

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct ndk_insn {
    uint64_t address;
    uint16_t size;          // 2 (Thumb) or 4 (ARM)
    uint32_t bytes[4];      // 最多 8 字节（存 4 个 uint32_t，留足空间）
    char     mnemonic[16];
    char     op_str[64];
} ndk_insn_t;
struct ndk_insn;  // 旧名兼容 alias，保留以防旧 cpp 引用

int ndk_disasm_arm(const uint8_t* code, size_t len, uint64_t address, int is_thumb,
                   ndk_insn_t* out, int max_out);

int ndk_asm_arm(const char* line, int is_thumb, uint8_t* out, int max_out);

uint32_t ndk_sysv_hash(const char* name);
uint32_t ndk_gnu_hash(const char* name);

#ifdef __cplusplus
}
#endif
