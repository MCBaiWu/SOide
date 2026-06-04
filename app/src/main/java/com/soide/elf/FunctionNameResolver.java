package com.soide.elf;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * v1.4.6: 函数名解析器
 * <p>
 * 把 disassembly 中的 bl/blx/blr/call/jal 目标地址替换为可读函数名。
 * 规则：
 *  1) 如果目标地址在已知函数列表中 → 替换为该函数名
 *  2) 如果目标地址在任何已解析函数的范围内（PLT/GOT 等）→ 用 sub_xxx
 *  3) 否则保留原 0xADDR
 */
public final class FunctionNameResolver {

    private FunctionNameResolver() {}

    /** 在一组函数上做名称解析 (原地修改 DisassembledInstruction.opStr 与 assembly 字段) */
    public static void resolveAll(List<FunctionInfo> funcs, boolean enabled) {
        if (funcs == null || funcs.isEmpty() || !enabled) return;

        // 1) 收集地址 → 名称
        Map<Long, String> addrToName = new HashMap<>();
        // 一些弱符号 (WEAK) 可能重复，用最先出现的 FUNC 名字
        for (FunctionInfo f : funcs) {
            if (f == null || f.name == null) continue;
            if (!addrToName.containsKey(f.address)) {
                addrToName.put(f.address, f.name);
            }
        }
        if (addrToName.isEmpty()) return;

        // 2) 替换
        for (FunctionInfo f : funcs) {
            if (f.instructions == null) continue;
            for (DisassembledInstruction ins : f.instructions) {
                if (ins == null) continue;
                String mn = ins.mnemonic == null ? "" : ins.mnemonic.toLowerCase(Locale.ROOT);
                if (mn.isEmpty()) continue;
                boolean isCall = mn.equals("bl") || mn.equals("blx") || mn.equals("blr")
                        || mn.equals("call") || mn.equals("jal") || mn.equals("jalr")
                        || mn.equals("b") /* unconditional branch, 也可能是 tail-call */
                        || mn.equals("br");
                if (!isCall) continue;
                if (ins.opStr == null || ins.opStr.isEmpty()) continue;

                // 从 opStr 中抽 hex (0x...) 字符串
                int idx = ins.opStr.toLowerCase(Locale.ROOT).indexOf("0x");
                if (idx < 0) {
                    // 尝试纯 hex 字面量
                    int hx = firstHexPrefix(ins.opStr);
                    if (hx < 0) continue;
                    idx = hx;
                }
                int end = idx + 2;
                while (end < ins.opStr.length()) {
                    char c = ins.opStr.charAt(end);
                    if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) end++;
                    else break;
                }
                if (end <= idx + 2) continue;
                String hex = ins.opStr.substring(idx, end);
                long addr;
                try {
                    addr = Long.parseUnsignedLong(hex.substring(2), 16);
                } catch (NumberFormatException ex) {
                    continue;
                }
                String name = addrToName.get(addr);
                if (name == null) {
                    // 2nd try: 落进某函数范围（PLT 等）
                    FunctionInfo owner = findOwnerFunction(funcs, addr);
                    if (owner != null && owner.address == addr) {
                        name = owner.name;
                    } else if (owner != null) {
                        name = "sub_" + Long.toHexString(addr).toLowerCase(Locale.ROOT);
                    } else {
                        // 保留原 0xADDR，不再做无意义替换
                        continue;
                    }
                }
                String newOp = ins.opStr.substring(0, idx) + name
                        + ins.opStr.substring(end);
                ins.opStr = newOp;
            }
        }
    }

    private static int firstHexPrefix(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
                if (i + 1 < s.length() && s.charAt(i + 1) == 'x') return i;
            }
        }
        return -1;
    }

    private static FunctionInfo findOwnerFunction(List<FunctionInfo> funcs, long addr) {
        FunctionInfo best = null;
        long bestSize = Long.MAX_VALUE;
        for (FunctionInfo f : funcs) {
            if (f == null) continue;
            if (addr < f.address) continue;
            long end = f.address + (f.size > 0 ? f.size : 0);
            if (end > 0 && addr >= end) continue;
            long sz = (f.size > 0 ? f.size : 0);
            if (sz < bestSize) { best = f; bestSize = sz; }
        }
        return best;
    }
}
