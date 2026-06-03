package com.soide.util;

/**
 * 进制转换工具。 支持 BIN/OCT/DEC/HEX 互转，自动识别 0x 前缀。
 */
public final class BaseConverter {

    public static class Result {
        public final String binary;
        public final String octal;
        public final String decimal;
        public final String hex;
        public final boolean ok;
        public final String error;

        Result(String b, String o, String d, String h, boolean ok, String err) {
            this.binary = b;
            this.octal = o;
            this.decimal = d;
            this.hex = h;
            this.ok = ok;
            this.error = err;
        }

        static Result error(String msg) {
            return new Result("", "", "", "", false, msg);
        }
    }

    private BaseConverter() {}

    public static Result convert(String input) {
        if (input == null) return Result.error("empty");
        String s = input.trim();
        if (s.isEmpty()) return Result.error("empty");

        boolean negative = false;
        if (s.startsWith("-") || s.startsWith("+")) {
            if (s.startsWith("-")) negative = true;
            s = s.substring(1);
        }

        int radix;
        if (s.startsWith("0x") || s.startsWith("0X")) {
            s = s.substring(2);
            radix = 16;
        } else if (s.startsWith("0b") || s.startsWith("0B")) {
            s = s.substring(2);
            radix = 2;
        } else if (s.startsWith("0o") || s.startsWith("0O")) {
            s = s.substring(2);
            radix = 8;
        } else if (s.matches("[01]+")) {
            radix = 2;
        } else if (s.matches("[0-7]+")) {
            radix = 8;
        } else if (s.matches("[0-9a-fA-F]+")) {
            radix = 16;
        } else if (s.matches("[0-9]+")) {
            radix = 10;
        } else {
            return Result.error("无法识别的数字格式");
        }

        long value;
        try {
            value = Long.parseLong(s, radix);
        } catch (NumberFormatException e) {
            return Result.error("数字超出范围或格式错误");
        }

        if (negative) value = -value;

        String sign = negative ? "-" : "";
        return new Result(
                sign + Long.toBinaryString(Math.abs(value)),
                sign + Long.toOctalString(Math.abs(value)),
                sign + Long.toString(value),
                sign + "0x" + Long.toHexString(Math.abs(value)),
                true,
                null
        );
    }
}
