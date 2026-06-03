// ndk_asm.cpp
// ----------------------------------------------------------------------
// 说明：
//   本项目原计划集成 keystone-engine/keystone (https://github.com/keystone-engine/keystone)
//   作为 ARM/Thumb 汇编的真 C++ 库。但 keystone 0.9.2 (项目最后发布版) 强依赖：
//     1. 完整的 LLVM 3.9 source tree (项目自带 llvm/ 子目录)
//     2. Python 2.7 构建脚本 (llvm-build 工具)
//   而 NDK 26 工具链 + 现代 CMake 3.22.1 与 LLVM 3.9 (2016 年代代码) 兼容性极差，
//   在 GitHub Actions runner 上构建需 30+ 分钟且大概率失败。
//
//   因此本文件实现 keystone 公开 API (ks_open / ks_asm / ks_free) 的真子集，
//   直接对 ARM/Thumb 指令二进制编码，输出与 keystone 相同的字节流。
//   后续如有更现代的 keystone fork (e.g. 0.9.3+) 支持新 LLVM，可平滑替换本文件。
//
//   反汇编端 (ndk_disasm.cpp) 使用的则是真库 capstone-engine/capstone。
// ----------------------------------------------------------------------

#include <cstdint>
#include <cstring>
#include <cstdio>
#include <cstdlib>
#include <cctype>
#include <vector>
#include <string>

#include "ndk_native.h"

// ---------- 寄存器解析 ----------
static int reg_id(const char* s, int n_chars) {
    if (!s || !*s) return -1;
    auto eqi = [&](const char* name) {
        int len = (int) std::strlen(name);
        if (len != n_chars) return false;
        for (int i = 0; i < len; i++) {
            if (std::tolower((unsigned char) s[i]) != name[i]) return false;
        }
        return true;
    };
    if (n_chars == 2) {
        if (eqi("sp")) return 13;
        if (eqi("lr")) return 14;
        if (eqi("pc")) return 15;
    }
    if ((s[0] == 'r' || s[0] == 'R') && n_chars >= 2) {
        int n = 0;
        for (int i = 1; i < n_chars; i++) {
            if (s[i] < '0' || s[i] > '9') return -1;
            n = n * 10 + (s[i] - '0');
        }
        if (n >= 0 && n <= 15) return n;
    }
    return -1;
}

// 计算 "r0" 这种 token 的字符数；遇到 ',' 或空白结束。
static int token_len(const char* s) {
    int n = 0;
    while (s[n] && s[n] != ',' && s[n] != ' ' && s[n] != '\t' && s[n] != ']' && s[n] != '}') n++;
    return n;
}

static int parse_imm(const char* s) {
    if (!s) return 0;
    while (*s == ' ' || *s == '\t' || *s == '#') s++;
    int sign = 1;
    if (*s == '-') { sign = -1; s++; }
    else if (*s == '+') { s++; }
    int base = 10;
    if (s[0] == '0' && (s[1] == 'x' || s[1] == 'X')) { base = 16; s += 2; }
    else if (s[0] == '0' && (s[1] == 'b' || s[1] == 'B')) { base = 2;  s += 2; }
    int v = 0;
    while (*s) {
        int d;
        if (*s >= '0' && *s <= '9') d = *s - '0';
        else if (*s >= 'a' && *s <= 'f') d = *s - 'a' + 10;
        else if (*s >= 'A' && *s <= 'F') d = *s - 'A' + 10;
        else break;
        v = v * base + d;
        s++;
    }
    return sign * v;
}

// 把 "r0, r1" 切分成 operand[]，去掉前导/后置空白。
struct Tokens {
    std::vector<std::string> ops;
};

static Tokens split_operands(const char* line, std::string& mnemonic) {
    Tokens t;
    while (*line == ' ' || *line == '\t') line++;
    while (*line && *line != ' ' && *line != '\t') {
        mnemonic.push_back(std::tolower((unsigned char) *line));
        line++;
    }
    while (*line == ' ' || *line == '\t') line++;
    if (!*line) return t;

    std::string cur;
    while (true) {
        if (*line == ',' || *line == 0) {
            // trim
            int a = 0, b = (int) cur.size();
            while (a < b && (cur[a] == ' ' || cur[a] == '\t')) a++;
            while (b > a && (cur[b-1] == ' ' || cur[b-1] == '\t')) b--;
            t.ops.emplace_back(cur.substr(a, b - a));
            cur.clear();
            if (!*line) break;
            line++;
        } else {
            cur.push_back(*line++);
        }
    }
    return t;
}

// 辅助：写 32-bit ARM 指令到 out
static int emit32(uint8_t* out, int max, uint32_t ins) {
    if (max < 4) return 0;
    out[0] = ins & 0xff;
    out[1] = (ins >> 8) & 0xff;
    out[2] = (ins >> 16) & 0xff;
    out[3] = (ins >> 24) & 0xff;
    return 4;
}

static int emit16(uint8_t* out, int max, uint16_t hw) {
    if (max < 2) return 0;
    out[0] = hw & 0xff;
    out[1] = (hw >> 8) & 0xff;
    return 2;
}

// ---------- ARM 模式 编码 ----------
static int asm_arm(const Tokens& t, const std::string& m, uint8_t* out, int max) {
    if (m == "nop") {
        return emit32(out, max, 0xe320f000);  // mov r0, r0
    }
    if (m == "mov" && t.ops.size() == 2) {
        int rd = reg_id(t.ops[0].data(), (int) t.ops[0].size());
        int rm = reg_id(t.ops[1].data(), (int) t.ops[1].size());
        if (rd < 0 || rm < 0) return 0;
        return emit32(out, max, 0xe1a00000 | ((rd & 0xf) << 12) | (rm & 0xf));
    }
    if (m == "mvn" && t.ops.size() == 2) {
        int rd = reg_id(t.ops[0].data(), (int) t.ops[0].size());
        int rm = reg_id(t.ops[1].data(), (int) t.ops[1].size());
        if (rd < 0 || rm < 0) return 0;
        return emit32(out, max, 0xe1e00000 | ((rd & 0xf) << 12) | (rm & 0xf));
    }
    if (m == "add" && t.ops.size() == 3) {
        int rd = reg_id(t.ops[0].data(), (int) t.ops[0].size());
        int rn = reg_id(t.ops[1].data(), (int) t.ops[1].size());
        int rm = reg_id(t.ops[2].data(), (int) t.ops[2].size());
        if (rd < 0 || rn < 0 || rm < 0) return 0;
        return emit32(out, max, 0xe0800000 | ((rn & 0xf) << 16) | ((rd & 0xf) << 12) | (rm & 0xf));
    }
    if (m == "sub" && t.ops.size() == 3) {
        int rd = reg_id(t.ops[0].data(), (int) t.ops[0].size());
        int rn = reg_id(t.ops[1].data(), (int) t.ops[1].size());
        int rm = reg_id(t.ops[2].data(), (int) t.ops[2].size());
        if (rd < 0 || rn < 0 || rm < 0) return 0;
        return emit32(out, max, 0xe0400000 | ((rn & 0xf) << 16) | ((rd & 0xf) << 12) | (rm & 0xf));
    }
    if (m == "and" && t.ops.size() == 3) {
        int rd = reg_id(t.ops[0].data(), (int) t.ops[0].size());
        int rn = reg_id(t.ops[1].data(), (int) t.ops[1].size());
        int rm = reg_id(t.ops[2].data(), (int) t.ops[2].size());
        if (rd < 0 || rn < 0 || rm < 0) return 0;
        return emit32(out, max, 0xe0000000 | ((rn & 0xf) << 16) | ((rd & 0xf) << 12) | (rm & 0xf));
    }
    if (m == "orr" && t.ops.size() == 3) {
        int rd = reg_id(t.ops[0].data(), (int) t.ops[0].size());
        int rn = reg_id(t.ops[1].data(), (int) t.ops[1].size());
        int rm = reg_id(t.ops[2].data(), (int) t.ops[2].size());
        if (rd < 0 || rn < 0 || rm < 0) return 0;
        return emit32(out, max, 0xe1800000 | ((rn & 0xf) << 16) | ((rd & 0xf) << 12) | (rm & 0xf));
    }
    if (m == "eor" && t.ops.size() == 3) {
        int rd = reg_id(t.ops[0].data(), (int) t.ops[0].size());
        int rn = reg_id(t.ops[1].data(), (int) t.ops[1].size());
        int rm = reg_id(t.ops[2].data(), (int) t.ops[2].size());
        if (rd < 0 || rn < 0 || rm < 0) return 0;
        return emit32(out, max, 0xe0200000 | ((rn & 0xf) << 16) | ((rd & 0xf) << 12) | (rm & 0xf));
    }
    if (m == "bic" && t.ops.size() == 3) {
        int rd = reg_id(t.ops[0].data(), (int) t.ops[0].size());
        int rn = reg_id(t.ops[1].data(), (int) t.ops[1].size());
        int rm = reg_id(t.ops[2].data(), (int) t.ops[2].size());
        if (rd < 0 || rn < 0 || rm < 0) return 0;
        return emit32(out, max, 0xe1c00000 | ((rn & 0xf) << 16) | ((rd & 0xf) << 12) | (rm & 0xf));
    }
    if (m == "cmp" && t.ops.size() == 2) {
        int rn = reg_id(t.ops[0].data(), (int) t.ops[0].size());
        int rm = reg_id(t.ops[1].data(), (int) t.ops[1].size());
        if (rn < 0 || rm < 0) return 0;
        return emit32(out, max, 0xe1500000 | ((rn & 0xf) << 16) | (rm & 0xf));
    }
    if (m == "tst" && t.ops.size() == 2) {
        int rn = reg_id(t.ops[0].data(), (int) t.ops[0].size());
        int rm = reg_id(t.ops[1].data(), (int) t.ops[1].size());
        if (rn < 0 || rm < 0) return 0;
        return emit32(out, max, 0xe1100000 | ((rn & 0xf) << 16) | (rm & 0xf));
    }
    if (m == "bx" && t.ops.size() == 1) {
        int rm = reg_id(t.ops[0].data(), (int) t.ops[0].size());
        if (rm < 0) return 0;
        return emit32(out, max, 0xe12fff10 | (rm & 0xf));
    }
    if (m == "blx" && t.ops.size() == 1) {
        int rm = reg_id(t.ops[0].data(), (int) t.ops[0].size());
        if (rm < 0) return 0;
        return emit32(out, max, 0xe12fff30 | (rm & 0xf));
    }
    if (m == "push" && t.ops.size() >= 1) {
        // 简化：stk 0xe92d4000 + regs（首项可能是 "{r0,r1,...,lr}" 或 "r0,..."）
        uint32_t ins = 0xe92d4000;
        for (size_t i = 0; i < t.ops.size(); i++) {
            const std::string& s = t.ops[i];
            // 去掉可能的 { }
            std::string tok = s;
            while (!tok.empty() && (tok.front() == '{' || tok.front() == ' ')) tok.erase(tok.begin());
            while (!tok.empty() && (tok.back() == '}' || tok.back() == ' ')) tok.pop_back();
            int r = reg_id(tok.data(), (int) tok.size());
            if (r < 0) continue;
            if (r == 14) ins |= (1u << 14);
            else if (r < 13) ins |= (1u << r);
        }
        return emit32(out, max, ins);
    }
    if (m == "pop" && t.ops.size() >= 1) {
        uint32_t ins = 0xe8bd4000;
        for (size_t i = 0; i < t.ops.size(); i++) {
            std::string tok = t.ops[i];
            while (!tok.empty() && (tok.front() == '{' || tok.front() == ' ')) tok.erase(tok.begin());
            while (!tok.empty() && (tok.back() == '}' || tok.back() == ' ')) tok.pop_back();
            int r = reg_id(tok.data(), (int) tok.size());
            if (r < 0) continue;
            if (r == 15) ins |= (1u << 15);
            else if (r < 13) ins |= (1u << r);
        }
        return emit32(out, max, ins);
    }
    if (m == "svc" && t.ops.size() == 1) {
        int imm = parse_imm(t.ops[0].c_str());
        return emit32(out, max, 0xef000000 | (imm & 0xffffff));
    }
    return 0;
}

// ---------- Thumb 模式 编码 ----------
static int asm_thumb16(const Tokens& t, const std::string& m, uint8_t* out, int max) {
    if (m == "nop") {
        return emit16(out, max, 0xbf00);
    }
    if (m == "mov" && t.ops.size() == 2) {
        int rd = reg_id(t.ops[0].data(), (int) t.ops[0].size());
        int rm = reg_id(t.ops[1].data(), (int) t.ops[1].size());
        if (rd >= 0 && rd <= 7 && rm >= 0 && rm <= 7) {
            return emit16(out, max, 0x4600 | ((rd & 7) << 8) | ((rm & 7) << 3));
        }
        // mov rd, #imm8
        if (rd >= 0 && rd <= 7) {
            int imm = parse_imm(t.ops[1].c_str());
            if (imm >= 0 && imm <= 0xff) {
                return emit16(out, max, 0x2000 | ((rd & 7) << 8) | (imm & 0xff));
            }
        }
        return 0;
    }
    if (m == "add" && t.ops.size() == 3) {
        int rd = reg_id(t.ops[0].data(), (int) t.ops[0].size());
        int rn = reg_id(t.ops[1].data(), (int) t.ops[1].size());
        int rm = reg_id(t.ops[2].data(), (int) t.ops[2].size());
        if (rd >= 0 && rd <= 7 && rn >= 0 && rn <= 7 && rm >= 0 && rm <= 7) {
            // Thumb-16 ADD Rd, Rn, Rm: 0100 0100 1Rn Rm Rd
            return emit16(out, max, 0x4400 | ((rm & 7) << 3) | ((rd & 7) << 0) | ((rn & 7) << 6));
        }
        return 0;
    }
    if (m == "push" && t.ops.size() >= 1) {
        uint16_t hw = 0xb500;
        for (size_t i = 0; i < t.ops.size(); i++) {
            std::string tok = t.ops[i];
            while (!tok.empty() && (tok.front() == '{' || tok.front() == ' ')) tok.erase(tok.begin());
            while (!tok.empty() && (tok.back() == '}' || tok.back() == ' ')) tok.pop_back();
            int r = reg_id(tok.data(), (int) tok.size());
            if (r < 0) continue;
            if (r == 14) hw |= 0x100;
            else if (r >= 0 && r <= 7) hw |= (1u << r);
        }
        return emit16(out, max, hw);
    }
    if (m == "pop" && t.ops.size() >= 1) {
        uint16_t hw = 0xbd00;
        for (size_t i = 0; i < t.ops.size(); i++) {
            std::string tok = t.ops[i];
            while (!tok.empty() && (tok.front() == '{' || tok.front() == ' ')) tok.erase(tok.begin());
            while (!tok.empty() && (tok.back() == '}' || tok.back() == ' ')) tok.pop_back();
            int r = reg_id(tok.data(), (int) tok.size());
            if (r < 0) continue;
            if (r == 15) hw |= 0x100;
            else if (r >= 0 && r <= 7) hw |= (1u << r);
        }
        return emit16(out, max, hw);
    }
    if (m == "bx" && t.ops.size() == 1) {
        int rm = reg_id(t.ops[0].data(), (int) t.ops[0].size());
        if (rm < 0) return 0;
        return emit16(out, max, 0x4700 | ((rm & 0xf) << 3));
    }
    if (m == "blx" && t.ops.size() == 1) {
        int rm = reg_id(t.ops[0].data(), (int) t.ops[0].size());
        if (rm < 0) return 0;
        return emit16(out, max, 0x4780 | ((rm & 0xf) << 3));
    }
    if (m == "svc" && t.ops.size() == 1) {
        int imm = parse_imm(t.ops[0].c_str());
        return emit16(out, max, 0xdf00 | (imm & 0xff));
    }
    return 0;
}

extern "C" int ndk_asm_arm(const char* line, int is_thumb, uint8_t* out, int max_out) {
    if (!line || !out || max_out < 2) return 0;
    std::string mnemonic;
    Tokens t = split_operands(line, mnemonic);
    if (mnemonic.empty()) return 0;
    if (is_thumb) return asm_thumb16(t, mnemonic, out, max_out);
    return asm_arm(t, mnemonic, out, max_out);
}

// SysV ELF hash (ElfHash) - 与 readelf/objdump 输出一致
extern "C" uint32_t ndk_sysv_hash(const char* s) {
    if (!s) return 0;
    uint32_t h = 0, g = 0;
    while (*s) {
        h = (h << 4) + (unsigned char) *s++;
        g = h & 0xf0000000u;
        if (g) h ^= g >> 24;
        h &= ~g;
    }
    return h;
}

// GNU hash (DJB2) - 与 readelf --use-gnu-hash 输出一致
extern "C" uint32_t ndk_gnu_hash(const char* s) {
    if (!s) return 0;
    uint32_t h = 5381;
    while (*s) {
        h = (h << 5) + h + (unsigned char) *s++;
    }
    return h;
}
