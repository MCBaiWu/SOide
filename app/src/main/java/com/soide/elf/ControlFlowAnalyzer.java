package com.soide.elf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 控制流分析: 从反汇编指令列表构建基本块 (BB) 和分支图。
 * <p>
 * v1.4.3 改进：
 * - 引入 {@link EdgeKind} 区分真/假/默认分支，供 UI 着色
 * - 修复 BB 划分空块、相邻 BB 重叠、bl/call 不当 add successor 等问题
 * - 暴露 layoutHints 让 CfgCanvasView 不画穿块的边
 */
public class ControlFlowAnalyzer {

    /** 边的类型，对应 UI 着色：真分支绿、假分支红、默认蓝 */
    public enum EdgeKind {
        /** 条件分支"成立"的目标 - 绿色 */
        TRUE_BRANCH,
        /** 条件分支"不成立"的 fall-through 目标 - 红色 */
        FALSE_BRANCH,
        /** 无条件跳转、调用、顺序流 - 蓝色 */
        UNCONDITIONAL,
    }

    public static class Block {
        public long startAddr;
        public long endAddr;          // inclusive
        public List<DisassembledInstruction> instructions;
        public List<Edge> successors;   // 后继边
        public boolean isEntry;
        public boolean isExit;
        // 测量时填充，供 UI 层布局用
        public float extraMeasuredWidth;
        public float extraMeasuredHeight;

        public Block(long startAddr) {
            this.startAddr = startAddr;
            this.instructions = new ArrayList<>();
            this.successors = new ArrayList<>();
            this.isEntry = false;
            this.isExit = false;
        }
    }

    public static class Edge {
        public long from;
        public long to;
        public EdgeKind kind;
        public String label;   // 例如 "true"/"false"/"uncond"/"call"

        public Edge(long from, long to, EdgeKind kind, String label) {
            this.from = from;
            this.to = to;
            this.kind = kind;
            this.label = label;
        }
    }

    public static class CFG {
        public List<Block> blocks;
        public List<DisassembledInstruction> instructions;
        public Map<Long, Block> byAddr;

        public CFG() {
            blocks = new ArrayList<>();
            byAddr = new HashMap<>();
            instructions = new ArrayList<>();
        }
    }

    /** 把指令列表切成基本块并标记跳转边。 */
    public static CFG build(List<DisassembledInstruction> insns) {
        CFG cfg = new CFG();
        if (insns == null || insns.isEmpty()) return cfg;
        cfg.instructions = insns;

        // 1) 找 leaders: 函数入口 + 跳转目标 + 分支后的下一条
        Set<Long> leaders = new HashSet<>();
        leaders.add(insns.get(0).address);
        Map<Long, DisassembledInstruction> byAddr = indexByAddress(insns);

        for (DisassembledInstruction i : insns) {
            String mn = i.mnemonic == null ? "" : i.mnemonic.toLowerCase(Locale.ROOT);
            boolean isCall = mn.equals("bl") || mn.equals("blx") || mn.equals("call")
                    || mn.equals("jal") || mn.equals("jalr");
            boolean isRet = mn.equals("ret") || mn.equals("br") || mn.equals("bx lr")
                    || (mn.equals("bx") && i.opStr != null && i.opStr.contains("lr"));
            boolean isUncond = mn.equals("b") || mn.equals("br") || isRet;
            boolean isCond = mn.startsWith("b.") || mn.startsWith("cb") || mn.startsWith("tb")
                    || isArmCondBranch(mn);

            // 分支/调用 之后下一条是新 BB（call 之后也会 fall-through）
            int idx = indexOfAddress(insns, i.address);
            if (idx < 0) continue;
            if (isCall || isRet || isUncond || isCond) {
                if (idx + 1 < insns.size()) {
                    leaders.add(insns.get(idx + 1).address);
                }
            }

            // 跳转目标
            Long tgt = extractBranchTarget(i);
            if (tgt != null) {
                // 目标地址必须在指令表里
                if (byAddr.containsKey(tgt)) {
                    leaders.add(tgt);
                }
            }
        }

        // 2) 按 leader 切分
        List<Long> sorted = new ArrayList<>(leaders);
        Collections.sort(sorted);
        // 过滤出有指令的 leader
        sorted.removeIf(a -> !byAddr.containsKey(a));

        for (int i = 0; i < sorted.size(); i++) {
            long start = sorted.get(i);
            Block b = new Block(start);
            // end = 下个 leader - 1 (但要保证 end 至少在 start 之后有指令)
            long end;
            if (i + 1 < sorted.size()) {
                end = sorted.get(i + 1) - 1;
            } else {
                DisassembledInstruction last = insns.get(insns.size() - 1);
                end = last.address;
            }
            b.endAddr = end;
            for (DisassembledInstruction ins : insns) {
                if (ins.address >= start && ins.address <= end) {
                    b.instructions.add(ins);
                }
            }
            if (b.instructions.isEmpty()) continue; // 跳过空 BB
            cfg.blocks.add(b);
            cfg.byAddr.put(start, b);
        }

        // 3) 标记 entry / exit + 建边
        if (!cfg.blocks.isEmpty()) cfg.blocks.get(0).isEntry = true;

        for (int bi = 0; bi < cfg.blocks.size(); bi++) {
            Block b = cfg.blocks.get(bi);
            if (b.instructions.isEmpty()) continue;
            DisassembledInstruction last = b.instructions.get(b.instructions.size() - 1);
            String mn = last.mnemonic == null ? "" : last.mnemonic.toLowerCase(Locale.ROOT);

            boolean isCall = mn.equals("bl") || mn.equals("blx") || mn.equals("call")
                    || mn.equals("jal") || mn.equals("jalr");
            boolean isRet = mn.equals("ret")
                    || (mn.equals("bx") && last.opStr != null && last.opStr.contains("lr"))
                    || (mn.equals("br") && last.opStr != null && last.opStr.contains("x30"));
            boolean isUncond = mn.equals("b") || mn.equals("br") || isRet;
            boolean isCond = mn.startsWith("b.") || mn.startsWith("cb") || mn.startsWith("tb")
                    || isArmCondBranch(mn);

            if (isRet) {
                b.isExit = true;
                continue;
            }

            Long tgt = extractBranchTarget(last);
            int idx = indexOfAddress(insns, last.address);

            if (isUncond) {
                if (isCall) {
                    // call: 标蓝色 default 边到目标
                    if (tgt != null && cfg.byAddr.containsKey(tgt)) {
                        b.successors.add(new Edge(b.startAddr, tgt, EdgeKind.UNCONDITIONAL, "call"));
                    }
                    // call 之后通常 fall-through，加一个 default 边
                    if (idx + 1 < insns.size()) {
                        long ft = insns.get(idx + 1).address;
                        if (cfg.byAddr.containsKey(ft)) {
                            b.successors.add(new Edge(b.startAddr, ft, EdgeKind.UNCONDITIONAL, "next"));
                        }
                    }
                } else {
                    // 无条件跳转：只连到目标
                    if (tgt != null && cfg.byAddr.containsKey(tgt)) {
                        b.successors.add(new Edge(b.startAddr, tgt, EdgeKind.UNCONDITIONAL, "b"));
                    }
                }
            } else if (isCond) {
                // 条件分支: 真分支 → 目标 (绿)，假分支 → 下一条 (红)
                if (tgt != null && cfg.byAddr.containsKey(tgt)) {
                    b.successors.add(new Edge(b.startAddr, tgt, EdgeKind.TRUE_BRANCH, "true"));
                }
                if (idx + 1 < insns.size()) {
                    long ft = insns.get(idx + 1).address;
                    if (cfg.byAddr.containsKey(ft)) {
                        b.successors.add(new Edge(b.startAddr, ft, EdgeKind.FALSE_BRANCH, "false"));
                    }
                }
            } else {
                // 普通顺序流: fall-through 默认蓝
                if (idx + 1 < insns.size()) {
                    long ft = insns.get(idx + 1).address;
                    if (cfg.byAddr.containsKey(ft)) {
                        b.successors.add(new Edge(b.startAddr, ft, EdgeKind.UNCONDITIONAL, "next"));
                    }
                }
            }
        }
        return cfg;
    }

    /** ARM 条件后缀 e.g. beq/bne/bgt/blt/bge/ble/bhi/blo/bhs/bls/bcc/bcs/bmi/bpl/bvc/bvs */
    private static boolean isArmCondBranch(String mn) {
        if (!mn.startsWith("b") || mn.length() < 3) return false;
        String suf = mn.substring(1);
        return suf.equals("eq") || suf.equals("ne") || suf.equals("gt") || suf.equals("lt")
                || suf.equals("ge") || suf.equals("le") || suf.equals("hi") || suf.equals("lo")
                || suf.equals("hs") || suf.equals("ls") || suf.equals("cc") || suf.equals("cs")
                || suf.equals("mi") || suf.equals("pl") || suf.equals("vc") || suf.equals("vs");
    }

    private static Map<Long, DisassembledInstruction> indexByAddress(List<DisassembledInstruction> insns) {
        Map<Long, DisassembledInstruction> m = new HashMap<>();
        for (DisassembledInstruction i : insns) m.put(i.address, i);
        return m;
    }

    private static int indexOfAddress(List<DisassembledInstruction> insns, long addr) {
        for (int i = 0; i < insns.size(); i++) {
            if (insns.get(i).address == addr) return i;
        }
        return -1;
    }

    /** 提取分支目标: 解析 opStr 中首个十六进制数 */
    private static Long extractBranchTarget(DisassembledInstruction ins) {
        if (ins.opStr == null) return null;
        String op = ins.opStr.trim();
        if (op.startsWith("#")) op = op.substring(1);
        // 第一个 token
        int sp = op.indexOf(' ');
        if (sp > 0) op = op.substring(0, sp);
        sp = op.indexOf(',');
        if (sp > 0) op = op.substring(0, sp);
        op = op.trim();
        if (op.startsWith("0x") || op.startsWith("0X")) {
            try {
                return Long.parseUnsignedLong(op.substring(2), 16);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        // 也支持纯 16 进制字面量
        if (op.matches("[0-9a-fA-F]+")) {
            try {
                return Long.parseUnsignedLong(op, 16);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
