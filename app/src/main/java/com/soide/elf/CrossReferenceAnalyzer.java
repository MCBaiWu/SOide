package com.soide.elf;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 交叉引用分析: 从函数内指令找出对其他函数/导入/对象的引用。
 * <p>
 * 简易实现: 扫描指令中出现的目标地址 (bl/b 的目标)，
 * 匹配到符号表或导入符号表里。
 */
public class CrossReferenceAnalyzer {

    public static class XRef {
        public long fromAddr;     // 发起引用的指令地址
        public long toAddr;       // 目标地址
        public String mnemonic;   // 助记符 (bl, b, cbz...)
        public String opStr;      // 原始操作数
        public String targetName; // 目标函数名 (可能为 null)

        public XRef(long fromAddr, long toAddr, String mnemonic, String opStr, String targetName) {
            this.fromAddr = fromAddr;
            this.toAddr = toAddr;
            this.mnemonic = mnemonic;
            this.opStr = opStr;
            this.targetName = targetName;
        }
    }

    /**
     * @param insns 函数内指令列表
     * @param symbols 全局符号表 (用于把目标地址翻译成名字)
     * @param imports 导入符号 (PLT 桩)
     */
    public static List<XRef> find(List<DisassembledInstruction> insns,
                                   List<SymbolEntry> symbols,
                                   List<ImportedFunction> imports) {
        List<XRef> refs = new ArrayList<>();
        if (insns == null || insns.isEmpty()) return refs;
        for (DisassembledInstruction i : insns) {
            String mn = i.mnemonic == null ? "" : i.mnemonic.toLowerCase(Locale.ROOT);
            if (!(mn.equals("bl") || mn.equals("blx") || mn.equals("b")
                    || mn.equals("jal") || mn.equals("jalr")
                    || mn.startsWith("b.") || mn.startsWith("cb")
                    || mn.equals("call") || mn.equals("br") || mn.equals("bx"))) continue;
            Long target = extractTarget(i.opStr);
            if (target == null) continue;
            String name = null;
            if (symbols != null) {
                for (SymbolEntry s : symbols) {
                    if (s.stValue == target && s.name != null && !s.name.isEmpty()) {
                        name = s.name;
                        break;
                    }
                }
            }
            if (name == null && imports != null) {
                for (ImportedFunction imp : imports) {
                    if (imp.pltAddress == target && imp.name != null) {
                        name = imp.name;
                        break;
                    }
                }
            }
            refs.add(new XRef(i.address, target, i.mnemonic, i.opStr, name));
        }
        return refs;
    }

    private static Long extractTarget(String op) {
        if (op == null) return null;
        String s = op.trim();
        if (s.startsWith("#")) s = s.substring(1);
        // 取首个 token
        int sp = s.indexOf(' ');
        if (sp > 0) s = s.substring(0, sp);
        sp = s.indexOf(',');
        if (sp > 0) s = s.substring(0, sp);
        try {
            return Long.parseUnsignedLong(s, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
