package com.soide.elf.pseudoc;

import com.soide.elf.ControlFlowAnalyzer;
import com.soide.elf.DisassembledInstruction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 简易伪 C 转换器。
 * <p>
 * v1.4.3 改进：
 * - 用 {@link ControlFlowAnalyzer} 构建 BB，先给每个块起始打 label，
 *   跳转指令翻译成 {@code if (cond) goto L_X;}
 * - 修正 bx/ret/push/pop 等常见指令
 * - 支持 ARM64 全套 b.* + cbz/cbnz/tbz/tbnz
 * - 函数调用优先用 symbols / imports 解析成具名函数
 * <p>
 * 仍是简单实现，留出 {@link PseudoCConverter} 接口方便以后替换。
 */
public class SimplePseudoC implements PseudoCConverter {

    private static final Set<String> CALL_MNEMONICS = Set.of(
            "bl", "blx", "call", "jal", "jalr"
    );

    private static final Set<String> RET_MNEMONICS = Set.of(
            "ret"
    );

    @Override
    public String name() {
        return "Simple (启发式)";
    }

    @Override
    public List<String> convert(PseudoCContext ctx) {
        List<String> out = new ArrayList<>();
        if (ctx.instructions == null || ctx.instructions.isEmpty()) {
            out.add("// (no instructions)");
            return out;
        }

        // 1) 函数签名
        String fname = ctx.functionName != null && !ctx.functionName.isEmpty()
                ? ctx.functionName
                : ("sub_" + Long.toHexString(ctx.functionAddress));
        out.add("void " + fname + "() {");
        out.add("    // size=" + ctx.functionSize
                + (ctx.isThumb ? "  [Thumb]" : "")
                + "  insn=" + ctx.instructions.size());

        // 2) 构建 CFG → 给每块打 label
        ControlFlowAnalyzer.CFG cfg = ControlFlowAnalyzer.build(ctx.instructions);
        Map<Long, String> blockLabel = new HashMap<>();
        int idx = 0;
        for (var b : cfg.blocks) {
            blockLabel.put(b.startAddr, "L_" + Integer.toHexString(idx++));
        }

        // 3) 输出：每行指令
        Map<Long, DisassembledInstruction> byAddr = new HashMap<>();
        for (var i : ctx.instructions) byAddr.put(i.address, i);

        boolean atBlockStart = true;
        for (DisassembledInstruction ins : ctx.instructions) {
            // 如果是某块的开始，打 label
            String lbl = blockLabel.get(ins.address);
            if (lbl != null && atBlockStart) {
                out.add(lbl + ":");
            }
            out.add("    " + transformOne(ins, ctx, blockLabel));
            atBlockStart = lbl != null;  // 下条紧跟在 label 后也是块开始
        }

        out.add("}");
        return out;
    }

    private String transformOne(DisassembledInstruction ins, PseudoCContext ctx,
                                Map<Long, String> blockLabel) {
        String mn = ins.mnemonic == null ? "" : ins.mnemonic.toLowerCase(Locale.ROOT);
        String op = ins.opStr == null ? "" : ins.opStr.trim();

        if (RET_MNEMONICS.contains(mn)) {
            return "return;";
        }
        if (mn.equals("bx")) {
            // bx lr / bx x30
            if (op.equals("lr") || op.equals("x30")) return "return;";
        }
        if (CALL_MNEMONICS.contains(mn)) {
            return "    " + callOp(op, ctx) + ";";
        }
        if (mn.equals("b") || mn.equals("br")) {
            return branchLine("uncond", op, blockLabel, "");
        }
        if (mn.startsWith("b.") || isArmCondBranch(mn)) {
            String cond = branchCondition(mn);
            return branchLine("cond", op, blockLabel, cond);
        }
        if (mn.startsWith("cbz") || mn.startsWith("cbnz")
                || mn.startsWith("tbz") || mn.startsWith("tbnz")) {
            return cbLine(mn, op, blockLabel);
        }
        if (mn.equals("push")) {
            return "// push " + op;
        }
        if (mn.equals("pop")) {
            return "// pop " + op;
        }
        if (mn.equals("mov") || mn.startsWith("mov") || mn.equals("mvn")
                || mn.equals("movz") || mn.equals("movk") || mn.equals("movn")
                || mn.equals("mov.w")) {
            return op + ";";
        }
        if (mn.startsWith("ldr") || mn.startsWith("str") || mn.startsWith("ldp") || mn.startsWith("stp")) {
            return op + ";  // mem";
        }
        if (mn.equals("nop")) {
            return "// nop";
        }
        if (mn.equals("add") || mn.equals("sub") || mn.equals("mul") || mn.equals("div")
                || mn.equals("and") || mn.equals("orr") || mn.equals("eor") || mn.equals("lsl")
                || mn.equals("lsr") || mn.equals("asr") || mn.equals("cmp") || mn.equals("tst")) {
            return op + ";";
        }
        if (mn.equals("svc") || mn.equals("brk") || mn.equals("hvc") || mn.equals("smc")
                || mn.equals("syscall")) {
            return "// syscall " + op;
        }
        // 默认：原样输出
        return (ins.mnemonic != null ? ins.mnemonic : "?")
                + (ins.opStr != null ? " " + ins.opStr : "") + ";";
    }

    private static String callOp(String op, PseudoCContext ctx) {
        if (op == null) return "call ?()";
        Long addr = tryParseAddr(op);
        if (addr != null) {
            if (ctx.imports != null && ctx.imports.containsKey(addr)) {
                return ctx.imports.get(addr) + "()";
            }
            if (ctx.labels != null && ctx.labels.containsKey(addr)) {
                return ctx.labels.get(addr) + "()";
            }
            return "call_0x" + Long.toHexString(addr) + "()";
        }
        // 寄存器间调用 (e.g. blr x8) - 没法解析
        if (op.startsWith("x") || op.startsWith("w")) {
            return "call " + op + "()  // indirect";
        }
        return "call " + op + "()";
    }

    private static String branchLine(String kind, String op, Map<Long, String> blockLabel, String cond) {
        String lbl = labelFor(op, blockLabel);
        if (cond == null || cond.isEmpty()) {
            return "goto " + lbl + ";";
        }
        return "if (" + cond + ") goto " + lbl + ";";
    }

    private static String cbLine(String mn, String op, Map<Long, String> blockLabel) {
        // op e.g. "x0, #0x1234" or "w8, #0x5678"
        if (op == null || op.isEmpty()) return "// " + mn;
        String[] parts = op.split(",");
        if (parts.length < 2) return "// " + mn + " " + op;
        String reg = parts[0].trim();
        String tgt = parts[1].trim();
        String lbl = labelFor(tgt, blockLabel);
        boolean isZero = mn.startsWith("cbz") || mn.equals("tbz");
        String op_ = isZero ? " == 0" : " != 0";
        return "if (" + reg + op_ + ") goto " + lbl + ";";
    }

    private static String labelFor(String op, Map<Long, String> blockLabel) {
        if (op == null) return "?";
        Long addr = tryParseAddr(op);
        if (addr != null && blockLabel != null && blockLabel.containsKey(addr)) {
            return blockLabel.get(addr);
        }
        return "L_0x" + (op.startsWith("0x") || op.startsWith("0X") ? op.substring(2) : op);
    }

    private static String branchCondition(String mn) {
        // ARM64: b.eq/b.ne/b.gt/.../b.hi/b.lo
        // ARM : beq/bne/...
        switch (mn) {
            case "b.eq": case "beq": return "==";
            case "b.ne": case "bne": return "!=";
            case "b.gt": case "bgt": return ">";
            case "b.lt": case "blt": return "<";
            case "b.ge": case "bge": return ">=";
            case "b.le": case "ble": return "<=";
            case "b.hi": case "bhi": return ">u";
            case "b.ls": case "bls": return "<=u";
            case "b.hs": case "bhs": return ">=u";
            case "b.lo": case "blo": return "<u";
            case "b.eq": return "==";
            default: return "";
        }
    }

    private static boolean isArmCondBranch(String mn) {
        if (!mn.startsWith("b") || mn.length() < 3) return false;
        String suf = mn.substring(1);
        return suf.equals("eq") || suf.equals("ne") || suf.equals("gt") || suf.equals("lt")
                || suf.equals("ge") || suf.equals("le") || suf.equals("hi") || suf.equals("lo")
                || suf.equals("hs") || suf.equals("ls") || suf.equals("cc") || suf.equals("cs")
                || suf.equals("mi") || suf.equals("pl") || suf.equals("vc") || suf.equals("vs");
    }

    private static Long tryParseAddr(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.startsWith("#") || s.startsWith("0x") || s.startsWith("0X")) {
            s = s.startsWith("#") ? s.substring(1) : s;
            if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
            try {
                return Long.parseUnsignedLong(s, 16);
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
