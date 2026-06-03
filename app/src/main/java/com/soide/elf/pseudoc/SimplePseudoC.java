package com.soide.elf.pseudoc;

import com.soide.elf.DisassembledInstruction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 简易伪 C 转换器。
 * <p>
 * 当前实现：
 * 1. 识别函数头 / 尾 (push/pop/ret/bx lr)
 * 2. 把常见助记符翻译成类 C 语句
 * 3. 跳转指令翻译成 if / goto
 * 4. 函数调用翻译成 &quot;func(args)&quot;
 * <p>
 * 这是一个简单易扩展的基线实现，方便以后替换。
 */
public class SimplePseudoC implements PseudoCConverter {

    private static final Set<String> CALL_MNEMONICS = Set.of(
            "bl", "blx", "b.le", // ARM/AArch64
            "call", // x86
            "jal", "jalr", "call" // RISC-V / MIPS
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
        StringBuilder sig = new StringBuilder();
        sig.append("void ").append(ctx.functionName != null ? ctx.functionName : "sub_" + Long.toHexString(ctx.functionAddress))
                .append("() {");
        out.add(sig.toString());
        out.add("    // size=" + ctx.functionSize
                + (ctx.isThumb ? "  [Thumb]" : "")
                + "  insn=" + ctx.instructions.size());

        // 2) 简单的指令模式
        int indent = 1;
        for (DisassembledInstruction ins : ctx.instructions) {
            String line = transformOne(ins, ctx, indent);
            out.add(line);
        }

        out.add("}");
        return out;
    }

    private String transformOne(DisassembledInstruction ins, PseudoCContext ctx, int indent) {
        String pad = repeat("    ", indent);
        String mn = ins.mnemonic == null ? "" : ins.mnemonic.toLowerCase(Locale.ROOT);
        String op = ins.opStr == null ? "" : ins.opStr.trim();

        if (mn.equals("ret") || mn.equals("bx") && op.equals("lr")) {
            return pad + "return;";
        }
        if (mn.equals("bl") || mn.equals("blx") || mn.equals("call") || mn.equals("jal") || mn.equals("jalr")) {
            String target = extractFirstAddress(op);
            if (target != null && ctx.labels != null && ctx.labels.containsKey(parseAddr(target))) {
                return pad + ctx.labels.get(parseAddr(target)) + "();";
            }
            if (target != null && ctx.imports != null && ctx.imports.containsKey(parseAddr(target))) {
                return pad + ctx.imports.get(parseAddr(target)) + "();";
            }
            if (target != null) {
                return pad + "call_" + target + "();";
            }
            return pad + "// call " + op;
        }
        if (mn.equals("b") || mn.equals("b.eq") || mn.equals("b.ne")
                || mn.equals("b.gt") || mn.equals("b.lt") || mn.equals("b.ge") || mn.equals("b.le")
                || mn.equals("beq") || mn.equals("bne") || mn.equals("bgt") || mn.equals("blt")
                || mn.equals("bge") || mn.equals("ble") || mn.equals("bhi") || mn.equals("blo")
                || mn.equals("bhs") || mn.equals("bls")
                || mn.equals("b.cc") || mn.equals("b.cs") || mn.equals("b.mi") || mn.equals("b.pl")
                || mn.equals("b.vs") || mn.equals("b.vc")) {
            String cond = branchCondition(mn);
            String label = branchLabel(op, ctx);
            if (cond.isEmpty()) {
                return pad + "goto " + label + ";";
            } else {
                return pad + "if (" + cond + ") goto " + label + ";";
            }
        }
        if (mn.startsWith("cbz") || mn.startsWith("cbnz")) {
            return pad + "if (" + op.replace(",", " == 0) goto ") + ";";
        }
        if (mn.equals("push")) {
            return pad + "// push " + op;
        }
        if (mn.equals("pop")) {
            return pad + "// pop " + op;
        }
        if (mn.equals("mov") || mn.equals("movz") || mn.equals("movk") || mn.equals("movn") || mn.equals("mvn") || mn.equals("mov.w")) {
            return pad + op + ";";
        }
        if (mn.startsWith("ldr") || mn.startsWith("str")) {
            return pad + op + "; // mem";
        }
        if (mn.equals("nop")) {
            return pad + "// nop";
        }
        // 默认：直接打印原指令
        return pad + (ins.mnemonic != null ? ins.mnemonic : "?") + " " + (ins.opStr != null ? ins.opStr : "") + ";";
    }

    private static String repeat(String s, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(s);
        return b.toString();
    }

    private static String branchCondition(String mn) {
        switch (mn) {
            case "beq": case "b.eq": return "==";
            case "bne": case "b.ne": return "!=";
            case "bgt": case "b.gt": return ">";
            case "blt": case "b.lt": return "<";
            case "bge": case "b.ge": return ">=";
            case "ble": case "b.le": return "<=";
            default: return "";
        }
    }

    private static String branchLabel(String op, PseudoCContext ctx) {
        if (op == null) return "?";
        // 优先用 labels 替换
        Long addr = tryParseAddr(op);
        if (addr != null && ctx.labels != null && ctx.labels.containsKey(addr)) {
            return ctx.labels.get(addr);
        }
        return op;
    }

    private static String extractFirstAddress(String op) {
        if (op == null) return null;
        int hash = op.indexOf('#');
        if (hash >= 0) return op.substring(hash + 1).trim();
        return op.split(",")[0].trim();
    }

    private static Long tryParseAddr(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.startsWith("#") || s.startsWith("0x") || s.startsWith("0X")) {
            s = s.startsWith("#") ? s.substring(1) : s;
            try {
                return Long.parseUnsignedLong(s, 16);
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static long parseAddr(String s) {
        Long l = tryParseAddr(s);
        return l == null ? 0L : l;
    }
}
