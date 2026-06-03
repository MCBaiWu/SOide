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
 * 简易实现：识别 b/bl/b.eq/bx lr 等指令，划分 BB。
 * <p>
 * 输出 BB list + edges，每个 BB 包含:
 * - 起始地址
 * - 指令列表
 * - 后继 BB 起始地址
 */
public class ControlFlowAnalyzer {

    public static class Block {
        public long startAddr;
        public long endAddr;          // inclusive
        public List<DisassembledInstruction> instructions;
        public List<Long> successors;  // 目标 BB 起始地址
        public boolean isEntry;        // 函数入口
        public boolean isExit;         // 包含 ret / bx lr

        public Block(long startAddr) {
            this.startAddr = startAddr;
            this.instructions = new ArrayList<>();
            this.successors = new ArrayList<>();
            this.isEntry = false;
            this.isExit = false;
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

        // 1) 找出所有"块头"地址: 函数入口 + 所有跳转目标
        Set<Long> leaders = new HashSet<>();
        leaders.add(insns.get(0).address);
        for (DisassembledInstruction i : insns) {
            String mn = i.mnemonic == null ? "" : i.mnemonic.toLowerCase(Locale.ROOT);
            // 分支/调用/返回 之后下一条也是 leader
            boolean isBranch = mn.startsWith("b") || mn.equals("bl") || mn.equals("blx")
                    || mn.equals("call") || mn.equals("jal") || mn.equals("jalr")
                    || mn.equals("ret") || mn.equals("br") || mn.equals("bx");
            if (isBranch) {
                Long target = extractBranchTarget(i, insns);
                if (target != null) leaders.add(target);
                // 下一条指令也是新 BB
                int idx = insns.indexOf(i);
                if (idx + 1 < insns.size()) leaders.add(insns.get(idx + 1).address);
            }
            if (mn.startsWith("cbz") || mn.startsWith("cbnz")
                    || mn.startsWith("tbz") || mn.startsWith("tbnz")) {
                int idx = insns.indexOf(i);
                if (idx + 1 < insns.size()) leaders.add(insns.get(idx + 1).address);
            }
        }

        // 2) 按 leader 切分
        List<Long> sortedLeaders = new ArrayList<>(leaders);
        Collections.sort(sortedLeaders);
        for (int i = 0; i < sortedLeaders.size(); i++) {
            long start = sortedLeaders.get(i);
            long end = insns.get(insns.size() - 1).address;
            if (i + 1 < sortedLeaders.size()) end = sortedLeaders.get(i + 1) - 1;
            Block b = new Block(start);
            b.endAddr = end;
            // 收集指令
            for (DisassembledInstruction ins : insns) {
                if (ins.address >= start && ins.address <= end) {
                    b.instructions.add(ins);
                }
            }
            cfg.blocks.add(b);
            cfg.byAddr.put(start, b);
        }

        // 3) 标记 entry / exit，建边
        if (!cfg.blocks.isEmpty()) cfg.blocks.get(0).isEntry = true;
        for (Block b : cfg.blocks) {
            if (b.instructions.isEmpty()) continue;
            DisassembledInstruction last = b.instructions.get(b.instructions.size() - 1);
            String mn = last.mnemonic == null ? "" : last.mnemonic.toLowerCase(Locale.ROOT);
            if (mn.equals("ret") || (mn.equals("bx") && last.opStr != null && last.opStr.contains("lr"))) {
                b.isExit = true;
                continue;
            }
            // 后继: 1) 跳转目标 2) 顺序下一条
            Long target = extractBranchTarget(last, insns);
            if (target != null) b.successors.add(target);
            int idx = insns.indexOf(last);
            if (idx + 1 < insns.size() && isConditionalOrFallThrough(mn)) {
                b.successors.add(insns.get(idx + 1).address);
            }
        }
        return cfg;
    }

    private static boolean isConditionalOrFallThrough(String mn) {
        // 条件分支: b.* ; 无条件跳转不接 fall-through
        if (mn.startsWith("b.")) return true;
        if (mn.startsWith("cb") || mn.startsWith("tb")) return true;
        if (mn.equals("beq") || mn.equals("bne") || mn.equals("bgt") || mn.equals("blt")
                || mn.equals("bge") || mn.equals("ble") || mn.equals("bhi") || mn.equals("blo")
                || mn.equals("bhs") || mn.equals("bls")
                || mn.equals("b.cc") || mn.equals("b.cs") || mn.equals("b.mi") || mn.equals("b.pl")) return true;
        return false;
    }

    private static Long extractBranchTarget(DisassembledInstruction ins, List<DisassembledInstruction> insns) {
        if (ins.opStr == null) return null;
        String op = ins.opStr.trim();
        // #0x1234 or 0x1234
        if (op.startsWith("#")) op = op.substring(1);
        try {
            return Long.parseUnsignedLong(op, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
