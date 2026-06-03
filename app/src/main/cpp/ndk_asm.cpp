// ndk_asm.cpp
// 极简 ARM / Thumb 汇编器子集 - 模拟 keystone 的常用指令。
#include <cstdint>
#include <cstring>
#include <cstdio>
#include <cstdlib>
#include <cctype>

static int reg_id(const char* s) {
    if (!s || !*s) return -1;
    if (s[0] == 'r' || s[0] == 'R') {
        if (s[1] == '1' && s[2] == '3') return 13; // r13 = sp
        if (s[1] == '1' && s[2] == '4') return 14; // r14 = lr
        if (s[1] == '1' && s[2] == '5') return 15; // r15 = pc
        if (s[1] >= '0' && s[1] <= '9') {
            int n = s[1] - '0';
            if (n >= 0 && n <= 7) return n;
        }
    }
    if (!std::strncmp(s, "sp", 2) || !std::strncmp(s, "SP", 2)) return 13;
    if (!std::strncmp(s, "lr", 2) || !std::strncmp(s, "LR", 2)) return 14;
    if (!std::strncmp(s, "pc", 2) || !std::strncmp(s, "PC", 2)) return 15;
    return -1;
}

static int parse_imm(const char* s) {
    if (!s) return 0;
    if (*s == '#') s++;
    if (!std::strncmp(s, "0x", 2) || !std::strncmp(s, "0X", 2)) {
        return (int) std::strtoul(s + 2, nullptr, 16);
    }
    return std::atoi(s);
}

static int tolower_strcmp(const char* a, const char* b) {
    while (*a && *b) {
        int ca = std::tolower((unsigned char) *a);
        int cb = std::tolower((unsigned char) *b);
        if (ca != cb) return 1;
        a++; b++;
    }
    return *a || *b;
}

// 分隔助记符与操作数
static int split_operands(const char* line, char* mnemonic, int mn_size, const char** ops, int max_ops) {
    // 跳过前导空白
    while (*line == ' ' || *line == '\t') line++;
    int n = 0;
    const char* p = line;
    while (*p && *p != ' ' && *p != '\t') {
        if (n < mn_size - 1) mnemonic[n++] = (char) std::tolower((unsigned char) *p);
        p++;
    }
    mnemonic[n] = 0;
    while (*p == ' ' || *p == '\t') p++;
    if (!*p) return 0;
    int count = 0;
    const char* start = p;
    while (*p && count < max_ops) {
        if (*p == ',') {
            ops[count++] = start;
            start = p + 1;
        }
        p++;
    }
    ops[count++] = start;
    return count;
}

extern "C" int ndk_asm_arm(const char* line, int is_thumb, uint8_t* out, int max_out) {
    if (!line || !out || max_out < 4) return 0;
    char mnemonic[16] = {0};
    const char* ops[6] = {0};

    int n_ops = split_operands(line, mnemonic, sizeof(mnemonic), ops, 5);
    if (n_ops <= 0) return 0;

    // Trim leading whitespace on each op
    auto trim = [](const char*& s) {
        while (*s == ' ' || *s == '\t') s++;
    };
    for (int i = 0; i < n_ops; i++) trim(ops[i]);

    if (is_thumb) {
        // Thumb-16
        if (!strcmp(mnemonic, "nop") && n_ops == 1) {
            out[0] = 0x00; out[1] = 0xbf;
            return 2;
        }
        if (!strcmp(mnemonic, "mov") && n_ops == 2) {
            int rd = reg_id(ops[0]);
            int rm = reg_id(ops[1]);
            if (rd >= 0 && rd <= 7 && rm >= 0 && rm <= 7) {
                uint16_t hw = 0x4600 | ((rd & 7) << 8) | ((rm & 7) << 3);
                out[0] = hw & 0xff; out[1] = (hw >> 8) & 0xff;
                return 2;
            }
        }
        if (!strcmp(mnemonic, "push") && n_ops >= 1) {
            // 简化：处理常见 {r0-r7, lr}
            uint16_t hw = 0xb500;
            for (int i = 0; i < n_ops; i++) {
                if (!strcmp(ops[i], "lr") || !strcmp(ops[i], "LR")) hw |= 0x100;
            }
            out[0] = hw & 0xff; out[1] = (hw >> 8) & 0xff;
            return 2;
        }
        if (!strcmp(mnemonic, "pop") && n_ops >= 1) {
            uint16_t hw = 0xbd00;
            for (int i = 0; i < n_ops; i++) {
                if (!strcmp(ops[i], "pc") || !strcmp(ops[i], "PC")) hw |= 0x100;
            }
            out[0] = hw & 0xff; out[1] = (hw >> 8) & 0xff;
            return 2;
        }
        return 0;
    }

    // ARM 32-bit
    if (!strcmp(mnemonic, "nop") && n_ops == 1) {
        out[0] = 0x00; out[1] = 0xf0; out[2] = 0x20; out[3] = 0xe3;
        return 4;
    }
    if (!strcmp(mnemonic, "bx") && n_ops == 1) {
        int rm = reg_id(ops[0]);
        if (rm < 0) return 0;
        uint32_t ins = 0xe12fff10 | (rm & 0xf);
        out[0] = ins & 0xff; out[1] = (ins >> 8) & 0xff;
        out[2] = (ins >> 16) & 0xff; out[3] = (ins >> 24) & 0xff;
        return 4;
    }
    if (!strcmp(mnemonic, "blx") && n_ops == 1) {
        int rm = reg_id(ops[0]);
        if (rm < 0) return 0;
        uint32_t ins = 0xe12fff30 | (rm & 0xf);
        out[0] = ins & 0xff; out[1] = (ins >> 8) & 0xff;
        out[2] = (ins >> 16) & 0xff; out[3] = (ins >> 24) & 0xff;
        return 4;
    }
    if (!strcmp(mnemonic, "mov") && n_ops == 2) {
        int rd = reg_id(ops[0]);
        int rm = reg_id(ops[1]);
        if (rd < 0 || rm < 0) return 0;
        uint32_t ins = 0xe1a00000 | ((rd & 0xf) << 12) | (rm & 0xf);
        out[0] = ins & 0xff; out[1] = (ins >> 8) & 0xff;
        out[2] = (ins >> 16) & 0xff; out[3] = (ins >> 24) & 0xff;
        return 4;
    }
    if (!strcmp(mnemonic, "push") && n_ops >= 1) {
        // 简化：stk 0xe92d4000 + regs
        uint32_t ins = 0xe92d4000;
        for (int i = 0; i < n_ops; i++) {
            int r = reg_id(ops[i]);
            if (r == 14) ins |= (1u << 14);
            else if (r >= 0 && r < 13) ins |= (1u << r);
        }
        out[0] = ins & 0xff; out[1] = (ins >> 8) & 0xff;
        out[2] = (ins >> 16) & 0xff; out[3] = (ins >> 24) & 0xff;
        return 4;
    }
    if (!strcmp(mnemonic, "pop") && n_ops >= 1) {
        uint32_t ins = 0xe8bd4000;
        for (int i = 0; i < n_ops; i++) {
            int r = reg_id(ops[i]);
            if (r == 15) ins |= (1u << 15);
            else if (r >= 0 && r < 13) ins |= (1u << r);
        }
        out[0] = ins & 0xff; out[1] = (ins >> 8) & 0xff;
        out[2] = (ins >> 16) & 0xff; out[3] = (ins >> 24) & 0xff;
        return 4;
    }

    return 0;
}
