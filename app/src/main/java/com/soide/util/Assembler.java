package com.soide.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 轻量级 ARM/Thumb 汇编器子集实现。
 *
 * 支持的 ARM (32-bit) 指令：
 *   mov, movw, movt, add, sub, ldr, str, push, pop, nop, b, bl, bx, blx, cmp, tst,
 *   and, orr, eor, mvn, lsl, lsr, asr, mrs
 *
 * 支持的 Thumb (16-bit) 指令：
 *   mov, add, sub, ldr, str, push, pop, nop, b, bl, bx, blx, cmp, tst,
 *   and, orr, eor, mvn, lsl, lsr, asr
 *
 * 不支持复杂寻址、立即数范围校验、协处理器指令等高级用法。
 */
public final class Assembler {

    public static final int MODE_ARM = 0;
    public static final int MODE_THUMB = 1;

    public static class Result {
        public final String hex;     // 空格分隔的十六进制字节
        public final byte[] bytes;   // 原始字节
        public final boolean ok;
        public final String error;

        public Result(String hex, byte[] bytes, boolean ok, String error) {
            this.hex = hex;
            this.bytes = bytes;
            this.ok = ok;
            this.error = error;
        }

        static Result fail(String msg) {
            return new Result(null, null, false, msg);
        }
    }

    private Assembler() {}

    public static Result assemble(String line, int mode) {
        if (line == null) return Result.fail("空指令");
        String s = line.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return Result.fail("空指令");

        // 拆分助记符和操作数
        int sp = indexOfWhitespace(s);
        String mnemonic = sp < 0 ? s : s.substring(0, sp);
        String operands = sp < 0 ? "" : s.substring(sp + 1).trim();
        String[] parts = splitOperands(operands);

        try {
            byte[] code;
            if (mode == MODE_THUMB) {
                code = assembleThumb(mnemonic, parts);
            } else {
                code = assembleArm(mnemonic, parts);
            }
            return new Result(formatHex(code), code, true, null);
        } catch (Exception e) {
            return Result.fail("无法识别指令: " + e.getMessage());
        }
    }

    private static String formatHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format(Locale.ROOT, "%02x", b[i] & 0xff));
        }
        return sb.toString();
    }

    private static int indexOfWhitespace(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) return i;
        }
        return -1;
    }

    private static String[] splitOperands(String ops) {
        if (ops.isEmpty()) return new String[0];
        List<String> r = new ArrayList<>();
        for (String p : ops.split(",")) {
            r.add(p.trim());
        }
        return r.toArray(new String[0]);
    }

    // ---------------- ARM (32-bit) ----------------

    private static byte[] assembleArm(String m, String[] ops) {
        switch (m) {
            case "nop": {
                int insn = 0xe320f000;
                return toBytes(insn, 4);
            }
            case "mov": {
                if (ops.length != 2) throw new IllegalArgumentException("mov rd, rm");
                int rd = reg(ops[0]);
                int rm = reg(ops[1]);
                // mov rd, rm -> 0xe1a00000 | (rd<<12) | rm
                int insn = 0xe1a00000 | (rd << 12) | rm;
                return toBytes(insn, 4);
            }
            case "movw": {
                if (ops.length != 2) throw new IllegalArgumentException("movw rd, #imm16");
                int rd = reg(ops[0]);
                int imm = parseImm(ops[1]) & 0xffff;
                int insn = 0xe3000000 | (rd << 12) | imm;
                return toBytes(insn, 4);
            }
            case "movt": {
                if (ops.length != 2) throw new IllegalArgumentException("movt rd, #imm16");
                int rd = reg(ops[0]);
                int imm = parseImm(ops[1]) & 0xffff;
                int insn = 0xe3400000 | (rd << 12) | imm;
                return toBytes(insn, 4);
            }
            case "add": {
                if (ops.length != 3) throw new IllegalArgumentException("add rd, rn, op2");
                int rd = reg(ops[0]);
                int rn = reg(ops[1]);
                int op2 = parseOp2(ops[2]);
                int insn = 0xe0800000 | (rn << 16) | (rd << 12) | op2;
                return toBytes(insn, 4);
            }
            case "sub": {
                if (ops.length != 3) throw new IllegalArgumentException("sub rd, rn, op2");
                int rd = reg(ops[0]);
                int rn = reg(ops[1]);
                int op2 = parseOp2(ops[2]);
                int insn = 0xe0400000 | (rn << 16) | (rd << 12) | op2;
                return toBytes(insn, 4);
            }
            case "cmp": {
                if (ops.length != 2) throw new IllegalArgumentException("cmp rn, op2");
                int rn = reg(ops[0]);
                int op2 = parseOp2(ops[1]);
                int insn = 0xe1500000 | (rn << 16) | op2;
                return toBytes(insn, 4);
            }
            case "tst": {
                if (ops.length != 2) throw new IllegalArgumentException("tst rn, op2");
                int rn = reg(ops[0]);
                int op2 = parseOp2(ops[1]);
                int insn = 0xe1100000 | (rn << 16) | op2;
                return toBytes(insn, 4);
            }
            case "and": {
                if (ops.length != 3) throw new IllegalArgumentException("and rd, rn, op2");
                int rd = reg(ops[0]);
                int rn = reg(ops[1]);
                int op2 = parseOp2(ops[2]);
                int insn = 0xe0000000 | (rn << 16) | (rd << 12) | op2;
                return toBytes(insn, 4);
            }
            case "orr": {
                if (ops.length != 3) throw new IllegalArgumentException("orr rd, rn, op2");
                int rd = reg(ops[0]);
                int rn = reg(ops[1]);
                int op2 = parseOp2(ops[2]);
                int insn = 0xe1800000 | (rn << 16) | (rd << 12) | op2;
                return toBytes(insn, 4);
            }
            case "eor": {
                if (ops.length != 3) throw new IllegalArgumentException("eor rd, rn, op2");
                int rd = reg(ops[0]);
                int rn = reg(ops[1]);
                int op2 = parseOp2(ops[2]);
                int insn = 0xe0200000 | (rn << 16) | (rd << 12) | op2;
                return toBytes(insn, 4);
            }
            case "mvn": {
                if (ops.length != 2) throw new IllegalArgumentException("mvn rd, op2");
                int rd = reg(ops[0]);
                int op2 = parseOp2(ops[1]);
                int insn = 0xe1e00000 | (rd << 12) | op2;
                return toBytes(insn, 4);
            }
            case "lsl": {
                if (ops.length != 3) throw new IllegalArgumentException("lsl rd, rm, #imm");
                int rd = reg(ops[0]);
                int rm = reg(ops[1]);
                int imm = parseImm(ops[2]) & 0x1f;
                int insn = 0xe1a00000 | (rd << 12) | rm | (imm << 7);
                return toBytes(insn, 4);
            }
            case "lsr": {
                if (ops.length != 3) throw new IllegalArgumentException("lsr rd, rm, #imm");
                int rd = reg(ops[0]);
                int rm = reg(ops[1]);
                int imm = parseImm(ops[2]) & 0x1f;
                int insn = 0xe1a00020 | (rd << 12) | rm | (imm << 7);
                return toBytes(insn, 4);
            }
            case "asr": {
                if (ops.length != 3) throw new IllegalArgumentException("asr rd, rm, #imm");
                int rd = reg(ops[0]);
                int rm = reg(ops[1]);
                int imm = parseImm(ops[2]) & 0x1f;
                int insn = 0xe1a00040 | (rd << 12) | rm | (imm << 7);
                return toBytes(insn, 4);
            }
            case "bx": {
                if (ops.length != 1) throw new IllegalArgumentException("bx rm");
                int rm = reg(ops[0]);
                int insn = 0xe12fff10 | rm;
                return toBytes(insn, 4);
            }
            case "blx": {
                if (ops.length != 1) throw new IllegalArgumentException("blx rm");
                int rm = reg(ops[0]);
                int insn = 0xe12fff30 | rm;
                return toBytes(insn, 4);
            }
            case "b": {
                if (ops.length != 1) throw new IllegalArgumentException("b #imm");
                int imm = parseImm(ops[1]) & 0x00ffffff;
                int insn = 0xea000000 | imm;
                return toBytes(insn, 4);
            }
            case "bl": {
                if (ops.length != 1) throw new IllegalArgumentException("bl #imm");
                int imm = parseImm(ops[1]) & 0x00ffffff;
                int insn = 0xeb000000 | imm;
                return toBytes(insn, 4);
            }
            case "push": {
                // 简化: push {rN, ...} -> stmdb sp!, {rN-list}
                long reglist = parseRegList(ops[0]);
                int insn = (int) (0xe92d0000L | reglist);
                return toBytes(insn, 4);
            }
            case "pop": {
                long reglist = parseRegList(ops[0]);
                int insn = (int) (0xe8bd0000L | reglist);
                return toBytes(insn, 4);
            }
            case "ldr": {
                if (ops.length != 2) throw new IllegalArgumentException("ldr rt, [op]");
                int rt = reg(ops[0]);
                int mem = parseMem(ops[1]);
                int insn = 0xe5900000 | (rt << 12) | mem;
                return toBytes(insn, 4);
            }
            case "str": {
                if (ops.length != 2) throw new IllegalArgumentException("str rt, [op]");
                int rt = reg(ops[0]);
                int mem = parseMem(ops[1]);
                int insn = 0xe5800000 | (rt << 12) | mem;
                return toBytes(insn, 4);
            }
            case "mrs": {
                if (ops.length != 2) throw new IllegalArgumentException("mrs rd, cpsr");
                int rd = reg(ops[0]);
                int insn = 0xe10f0000 | (rd << 12);
                return toBytes(insn, 4);
            }
            default:
                throw new IllegalArgumentException("不支持: " + m);
        }
    }

    // ---------------- Thumb (16-bit) ----------------

    private static byte[] assembleThumb(String m, String[] ops) {
        switch (m) {
            case "nop": {
                return new byte[]{(byte) 0x00, (byte) 0xbf};
            }
            case "mov": {
                if (ops.length != 2) throw new IllegalArgumentException("mov rd, rm");
                int rd = reg(ops[0]);
                int rm = reg(ops[1]);
                if (rd > 7 || rm > 7) throw new IllegalArgumentException("Thumb mov 仅支持 r0-r7");
                int insn = 0x4600 | (rd << 8) | ((rm & 0x7) << 3);
                return toBytes(insn, 2);
            }
            case "add": {
                if (ops.length != 3) throw new IllegalArgumentException("add rd, rn, rm");
                int rd = reg(ops[0]);
                int rn = reg(ops[1]);
                int rm = reg(ops[2]);
                if (rd > 7 || rn > 7 || rm > 7) throw new IllegalArgumentException("Thumb add 仅支持 r0-r7");
                int insn = 0x4400 | (rd << 8) | (rm << 6) | (rn << 3);
                return toBytes(insn, 2);
            }
            case "sub": {
                if (ops.length != 3) throw new IllegalArgumentException("sub rd, rn, rm");
                int rd = reg(ops[0]);
                int rn = reg(ops[1]);
                int rm = reg(ops[2]);
                if (rd > 7 || rn > 7 || rm > 7) throw new IllegalArgumentException("Thumb sub 仅支持 r0-r7");
                int insn = 0x1a00 | (rd << 8) | (rm << 6) | (rn << 3);
                return toBytes(insn, 2);
            }
            case "cmp": {
                if (ops.length != 2) throw new IllegalArgumentException("cmp rn, rm");
                int rn = reg(ops[0]);
                int rm = reg(ops[1]);
                if (rn > 7 || rm > 7) throw new IllegalArgumentException("Thumb cmp 仅支持 r0-r7");
                int insn = 0x4500 | (rm << 6) | (rn << 3);
                return toBytes(insn, 2);
            }
            case "tst": {
                if (ops.length != 2) throw new IllegalArgumentException("tst rn, rm");
                int rn = reg(ops[0]);
                int rm = reg(ops[1]);
                if (rn > 7 || rm > 7) throw new IllegalArgumentException("Thumb tst 仅支持 r0-r7");
                int insn = 0x4200 | (rm << 6) | (rn << 3);
                return toBytes(insn, 2);
            }
            case "and": {
                if (ops.length != 3) throw new IllegalArgumentException("and rd, rm");
                int rd = reg(ops[0]);
                int rm = reg(ops[1]);
                if (rd > 7 || rm > 7) throw new IllegalArgumentException("Thumb and 仅支持 r0-r7");
                int insn = 0x4000 | (rm << 6) | (rd << 3);
                return toBytes(insn, 2);
            }
            case "orr": {
                if (ops.length != 2) throw new IllegalArgumentException("orr rd, rm");
                int rd = reg(ops[0]);
                int rm = reg(ops[1]);
                if (rd > 7 || rm > 7) throw new IllegalArgumentException("Thumb orr 仅支持 r0-r7");
                int insn = 0x4300 | (rm << 6) | (rd << 3);
                return toBytes(insn, 2);
            }
            case "eor": {
                if (ops.length != 2) throw new IllegalArgumentException("eor rd, rm");
                int rd = reg(ops[0]);
                int rm = reg(ops[1]);
                if (rd > 7 || rm > 7) throw new IllegalArgumentException("Thumb eor 仅支持 r0-r7");
                int insn = 0x4040 | (rm << 6) | (rd << 3);
                return toBytes(insn, 2);
            }
            case "mvn": {
                if (ops.length != 2) throw new IllegalArgumentException("mvn rd, rm");
                int rd = reg(ops[0]);
                int rm = reg(ops[1]);
                if (rd > 7 || rm > 7) throw new IllegalArgumentException("Thumb mvn 仅支持 r0-r7");
                int insn = 0x43c0 | (rm << 6) | (rd << 3);
                return toBytes(insn, 2);
            }
            case "lsl": {
                if (ops.length != 3) throw new IllegalArgumentException("lsl rd, rs, #imm5");
                int rd = reg(ops[0]);
                int rs = reg(ops[1]);
                int imm = parseImm(ops[2]) & 0x1f;
                if (rd > 7 || rs > 7) throw new IllegalArgumentException("Thumb lsl 仅支持 r0-r7");
                int insn = 0x0000 | (imm << 6) | (rs << 3) | rd;
                return toBytes(insn, 2);
            }
            case "lsr": {
                if (ops.length != 3) throw new IllegalArgumentException("lsr rd, rs, #imm5");
                int rd = reg(ops[0]);
                int rs = reg(ops[1]);
                int imm = parseImm(ops[2]) & 0x1f;
                if (rd > 7 || rs > 7) throw new IllegalArgumentException("Thumb lsr 仅支持 r0-r7");
                int insn = 0x0800 | (imm << 6) | (rs << 3) | rd;
                return toBytes(insn, 2);
            }
            case "asr": {
                if (ops.length != 3) throw new IllegalArgumentException("asr rd, rs, #imm5");
                int rd = reg(ops[0]);
                int rs = reg(ops[1]);
                int imm = parseImm(ops[2]) & 0x1f;
                if (rd > 7 || rs > 7) throw new IllegalArgumentException("Thumb asr 仅支持 r0-r7");
                int insn = 0x1000 | (imm << 6) | (rs << 3) | rd;
                return toBytes(insn, 2);
            }
            case "push": {
                long reglist = parseRegList(ops[0]);
                int insn = (int) (0xb500 | (reglist & 0xfff));
                return toBytes(insn, 2);
            }
            case "pop": {
                long reglist = parseRegList(ops[0]);
                int insn = (int) (0xbd00 | (reglist & 0xfff));
                return toBytes(insn, 2);
            }
            case "ldr": {
                if (ops.length != 2) throw new IllegalArgumentException("ldr rt, [op]");
                int rt = reg(ops[0]);
                if (ops[1].startsWith("[pc") || ops[1].startsWith("[PC")) {
                    int imm = parseImm(ops[1].replaceAll("[^0-9xXa-fA-F-]", "")) & 0x3fc;
                    int insn = 0x4800 | (rt << 8) | (imm >> 2);
                    return toBytes(insn, 2);
                }
                int rn = parseRegBracket(ops[1]);
                if (rn > 7 || rt > 7) throw new IllegalArgumentException("Thumb ldr 仅支持 r0-r7");
                int insn = 0x6800 | (rt << 8) | (rn << 3);
                return toBytes(insn, 2);
            }
            case "str": {
                if (ops.length != 2) throw new IllegalArgumentException("str rt, [op]");
                int rt = reg(ops[0]);
                int rn = parseRegBracket(ops[1]);
                if (rn > 7 || rt > 7) throw new IllegalArgumentException("Thumb str 仅支持 r0-r7");
                int insn = 0x6000 | (rt << 8) | (rn << 3);
                return toBytes(insn, 2);
            }
            case "b": {
                int imm = parseImm(ops[0]) & 0x7ff;
                int insn = 0xe000 | imm;
                return toBytes(insn, 2);
            }
            case "bl": {
                throw new IllegalArgumentException("Thumb bl 请使用 blx");
            }
            case "blx": {
                int rm = reg(ops[0]);
                int insn = 0x4780 | (rm << 3);
                return toBytes(insn, 2);
            }
            case "bx": {
                int rm = reg(ops[0]);
                int insn = 0x4700 | (rm << 3);
                return toBytes(insn, 2);
            }
            default:
                throw new IllegalArgumentException("不支持: " + m);
        }
    }

    // ---------------- helpers ----------------

    private static int reg(String s) {
        s = s.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("r") || s.startsWith("R")) {
            try {
                int n = Integer.parseInt(s.substring(1));
                if (n < 0 || n > 15) throw new IllegalArgumentException("reg out of range: " + s);
                return n;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("bad reg: " + s);
            }
        }
        if (s.equals("sp")) return 13;
        if (s.equals("lr")) return 14;
        if (s.equals("pc")) return 15;
        throw new IllegalArgumentException("bad reg: " + s);
    }

    private static int parseImm(String s) {
        s = s.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.startsWith("0x") || s.startsWith("0X")) {
            return (int) Long.parseLong(s.substring(2), 16);
        }
        return Integer.parseInt(s);
    }

    private static int parseOp2(String s) {
        s = s.trim();
        if (s.startsWith("#")) {
            int v = parseImm(s.substring(1));
            int imm8 = v & 0xff;
            int rot = 0;
            int x = v;
            while (x > 0xff && rot < 16) {
                x = (x >>> 2) | ((x & 3) << 30);
                rot += 2;
                if ((x & ~0xff) == 0) break;
            }
            if (x > 0xff) throw new IllegalArgumentException("immediate out of range");
            return (rot << 8) | imm8;
        }
        if (s.startsWith("r")) return reg(s);
        return reg(s);
    }

    private static int parseMem(String s) {
        // 简化: [rn, #imm] 或 [rn]
        s = s.trim();
        if (!s.startsWith("[")) throw new IllegalArgumentException("bad mem: " + s);
        String body = s.substring(1);
        int end = body.indexOf(']');
        if (end < 0) throw new IllegalArgumentException("bad mem: " + s);
        body = body.substring(0, end);
        String[] parts = body.split(",");
        int rn = reg(parts[0].trim());
        int imm = 0;
        if (parts.length == 2) {
            imm = parseImm(parts[1].trim());
        }
        if (imm < 0) {
            imm = imm & 0xfff;
            return (1 << 23) | (rn << 16) | (imm & 0xfff);
        }
        return (rn << 16) | (imm & 0xfff);
    }

    private static int parseRegBracket(String s) {
        s = s.trim();
        if (!s.startsWith("[")) throw new IllegalArgumentException("bad mem: " + s);
        int end = s.indexOf(']');
        if (end < 0) throw new IllegalArgumentException("bad mem: " + s);
        String body = s.substring(1, end).split(",")[0].trim();
        return reg(body);
    }

    private static long parseRegList(String s) {
        s = s.trim();
        if (s.startsWith("{") && s.endsWith("}")) s = s.substring(1, s.length() - 1);
        String[] parts = s.split(",");
        long mask = 0;
        for (String p : parts) {
            p = p.trim();
            if (p.isEmpty()) continue;
            if (p.equals("lr")) { mask |= (1L << 14); continue; }
            if (p.equals("pc")) { mask |= (1L << 15); continue; }
            mask |= (1L << reg(p));
        }
        return mask;
    }

    private static byte[] toBytes(int insn, int n) {
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) {
            b[i] = (byte) ((insn >> (i * 8)) & 0xff);
        }
        return b;
    }
}
