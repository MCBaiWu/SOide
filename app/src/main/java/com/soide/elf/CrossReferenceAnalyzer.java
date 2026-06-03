package com.soide.elf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 交叉引用分析: 从函数内指令找出对其他函数/导入/对象的引用。
 * <p>
 * v1.4.3 改进：
 * - 加入 O(1) 地址 → 名字查找（先建索引再扫描）
 * - 解析 opStr 时跳过寄存器名、逗号等噪音
 * - 包含 ARM64 ADR/ADRP + LDR (literal) 等"近引用"
 */
public class CrossReferenceAnalyzer {

    public static class XRef {
        public long fromAddr;     // 发起引用的指令地址
        public long toAddr;       // 目标地址
        public String mnemonic;   // 助记符 (bl, b, cbz...)
        public String opStr;      // 原始操作数
        public String targetName; // 目标函数名 (可能为 null)
        public XRefKind kind;     // 引用类型

        public XRef(long fromAddr, long toAddr, String mnemonic, String opStr,
                    String targetName, XRefKind kind) {
            this.fromAddr = fromAddr;
            this.toAddr = toAddr;
            this.mnemonic = mnemonic;
            this.opStr = opStr;
            this.targetName = targetName;
            this.kind = kind;
        }
    }

    public enum XRefKind {
        CALL, JUMP, BRANCH_COND, LOAD_ADDR, DATA_LOAD, OTHER
    }

    /**
     * @param insns 函数内指令列表
     * @param symbols 全局符号表
     * @param imports 导入符号 (PLT 桩)
     */
    public static List<XRef> find(List<DisassembledInstruction> insns,
                                   List<SymbolEntry> symbols,
                                   List<ImportedFunction> imports) {
        List<XRef> refs = new ArrayList<>();
        if (insns == null || insns.isEmpty()) return refs;

        // 建索引 (O(1) 查)
        Map<Long, String> addrToName = new HashMap<>();
        if (symbols != null) {
            for (SymbolEntry s : symbols) {
                if (s.stValue != 0 && s.name != null && !s.name.isEmpty()
                        && s.stValue < 0x8000000000000000L /* 排除 -1 */) {
                    addrToName.putIfAbsent(s.stValue, s.name);
                }
            }
        }
        if (imports != null) {
            for (ImportedFunction imp : imports) {
                if (imp.pltAddress != 0 && imp.name != null && !imp.name.isEmpty()) {
                    addrToName.putIfAbsent(imp.pltAddress, imp.name);
                }
                if (imp.gotOffset != 0) {
                    addrToName.putIfAbsent(imp.gotOffset, imp.name + "@got");
                }
            }
        }

        for (DisassembledInstruction i : insns) {
            String mn = i.mnemonic == null ? "" : i.mnemonic.toLowerCase(Locale.ROOT);
            String op = i.opStr == null ? "" : i.opStr.trim();

            // 类型判断
            XRefKind kind = XRefKind.OTHER;
            if (mn.equals("bl") || mn.equals("blx") || mn.equals("call")
                    || mn.equals("jal") || mn.equals("jalr")) {
                kind = XRefKind.CALL;
            } else if (mn.startsWith("b.") || mn.startsWith("cb") || mn.startsWith("tb")) {
                kind = XRefKind.BRANCH_COND;
            } else if (mn.equals("b") || mn.equals("br") || mn.equals("bx")) {
                kind = XRefKind.JUMP;
            } else if (mn.equals("adr") || mn.equals("adrp")) {
                kind = XRefKind.LOAD_ADDR;
            } else if (mn.startsWith("ldr") || mn.startsWith("ldp")) {
                kind = XRefKind.DATA_LOAD;
            }

            // 不是引用指令就跳过
            if (kind == XRefKind.OTHER) continue;

            Long target = extractTarget(op);
            if (target == null) continue;
            String name = addrToName.get(target);
            refs.add(new XRef(i.address, target, i.mnemonic, op, name, kind));
        }
        return refs;
    }

    /**
     * 解析目标: 取 opStr 中首个 0x 数字 token。
     * 跳过寄存器名、逗号、方括号。
     */
    private static Long extractTarget(String op) {
        if (op == null || op.isEmpty()) return null;
        String s = op;
        // 1) 找 0x
        int idx = s.indexOf("0x");
        if (idx >= 0) {
            int end = idx + 2;
            while (end < s.length() && isHexChar(s.charAt(end))) end++;
            String hex = s.substring(idx + 2, end);
            try {
                return Long.parseUnsignedLong(hex, 16);
            } catch (NumberFormatException ignored) {}
        }
        // 2) 找 # 0x
        idx = s.indexOf("#0x");
        if (idx >= 0) {
            int end = idx + 3;
            while (end < s.length() && isHexChar(s.charAt(end))) end++;
            String hex = s.substring(idx + 3, end);
            try {
                return Long.parseUnsignedLong(hex, 16);
            } catch (NumberFormatException ignored) {}
        }
        // 3) 找 # 数字
        idx = s.indexOf('#');
        if (idx >= 0) {
            int end = idx + 1;
            while (end < s.length() && isHexChar(s.charAt(end))) end++;
            if (end > idx + 1) {
                String hex = s.substring(idx + 1, end);
                try {
                    return Long.parseUnsignedLong(hex, 16);
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    private static boolean isHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
