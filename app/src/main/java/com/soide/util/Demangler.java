package com.soide.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 轻量级 C++ (Itanium ABI) 符号 demangler。
 *
 * 支持常见的 _ZN...E 形式的 mangled name：
 *   _ZN3foo3barEi  -> foo::bar(int)
 *   _ZN5Outer5Inner6methodEv -> Outer::Inner::method()
 *   _ZN3std6vectorIiE9push_backEi -> std::vector<int>::push_back(int)
 *   _Z3fooPKci  -> foo(char const*, int)
 *
 * 不支持的复杂子集（模板参数、运算符 new/delete、特殊名字）会返回原始符号并标注 "(unsupported)"。
 */
public final class Demangler {

    private Demangler() {}

    public static class Result {
        public final String demangled;
        public final boolean supported;

        Result(String demangled, boolean supported) {
            this.demangled = demangled;
            this.supported = supported;
        }
    }

    public static Result demangle(String mangled) {
        if (mangled == null || mangled.isEmpty()) {
            return new Result("", true);
        }
        String s = mangled.trim();

        // 常见非 mangled 名称直接返回
        if (!s.startsWith("_Z")) {
            return new Result(s, true);
        }

        try {
            int idx = 2; // 跳过 _Z
            StringBuilder out = new StringBuilder();
            List<String> nested = new ArrayList<>();

            // 可选：'_' (在源码中以 '_' 开头的 C++ 符号)
            if (idx < s.length() && s.charAt(idx) == '_') {
                out.append('_');
                idx++;
            }

            // 解析 nested-name：长度前缀 (十进制)
            int lenEnd = idx;
            while (lenEnd < s.length() && Character.isDigit(s.charAt(lenEnd))) {
                lenEnd++;
            }
            if (lenEnd == idx) {
                return new Result(s + "  (unsupported)", false);
            }
            int firstLen = Integer.parseInt(s.substring(idx, lenEnd));
            idx = lenEnd;

            int parsed = parseOneName(s, idx, firstLen, nested);
            if (parsed < 0) {
                return new Result(s + "  (unsupported)", false);
            }
            idx = parsed;

            for (int i = 0; i < nested.size(); i++) {
                if (i > 0) out.append("::");
                out.append(nested.get(i));
            }

            // 解析参数
            if (idx < s.length()) {
                String args = parseArgs(s, idx);
                if (args == null) {
                    return new Result(s + "  (unsupported)", false);
                }
                out.append('(').append(args).append(')');
                idx += args.length() + 2; // +2 是 args 字符串内两侧的 '<' '>' 占位
                // 我们通过 parseArgs 返回的是已经处理好的字符串，因此不需要再加偏移
            }

            return new Result(out.toString(), true);
        } catch (Exception e) {
            return new Result(s + "  (unsupported)", false);
        }
    }

    /** 解析一段长度前缀的 identifier（identifier-style）。 */
    private static int parseOneName(String s, int start, int len, List<String> nested) {
        if (start + len > s.length()) return -1;
        String name = s.substring(start, start + len);
        nested.add(name);
        int idx = start + len;
        // 处理 C1..C9 构造函数 / D1..D2 析构函数 -> 用类名替换
        if (idx < s.length()) {
            char c = s.charAt(idx);
            if (c >= 'C' && c <= 'C' + 9) {
                if (nested.size() >= 2) {
                    String last = nested.remove(nested.size() - 1);
                    nested.set(nested.size() - 1, last);
                }
                idx++;
            } else if (c == 'D' && (s.charAt(idx + 1) == '0' || s.charAt(idx + 1) == '1' || s.charAt(idx + 1) == '2')) {
                String last = nested.remove(nested.size() - 1);
                if (nested.size() >= 1) {
                    nested.set(nested.size() - 1, "~" + last);
                }
                idx += 2;
            }
        }
        return idx;
    }

    /** 解析参数类型列表。 */
    private static String parseArgs(String s, int start) {
        StringBuilder out = new StringBuilder();
        int i = start;
        while (i < s.length() && s.charAt(i) != 'E') {
            String type = parseType(s, i);
            if (type == null) return null;
            if (out.length() > 0) out.append(", ");
            out.append(type);
            // 移动 i 跳过已解析的类型
            i = skipType(s, i);
        }
        if (i >= s.length()) return null; // 没遇到 E
        return out.toString();
    }

    private static String parseType(String s, int i) {
        if (i >= s.length()) return null;
        char c = s.charAt(i);
        switch (c) {
            case 'v': return "void";
            case 'b': return "bool";
            case 'c': return "char";
            case 'h': return "unsigned char";
            case 's': return "short";
            case 't': return "unsigned short";
            case 'i': return "int";
            case 'j': return "unsigned int";
            case 'l': return "long";
            case 'm': return "unsigned long";
            case 'x': return "long long";
            case 'y': return "unsigned long long";
            case 'f': return "float";
            case 'd': return "double";
            case 'e': return "...";
            case 'w': return "wchar_t";
            case 'K': return "...";  // 省略号包 ... (省略号参数包)
            case 'P': {
                // pointer
                String inner = parseType(s, i + 1);
                if (inner == null) return null;
                return inner + "*";
            }
            case 'R': {
                // reference (lvalue)
                String inner = parseType(s, i + 1);
                if (inner == null) return null;
                return inner + "&";
            }
            case 'O': {
                // rvalue reference
                String inner = parseType(s, i + 1);
                if (inner == null) return null;
                return inner + "&&";
            }
            case 'C': {
                // const
                String inner = parseType(s, i + 1);
                if (inner == null) return null;
                return inner;  // 简化：忽略 const 修饰
            }
            case 'V': {
                // volatile
                String inner = parseType(s, i + 1);
                if (inner == null) return null;
                return inner;  // 简化
            }
            case 'S': {
                // signed / 无修饰 (S_ 等)
                String inner = parseType(s, i + 1);
                if (inner == null) return null;
                return inner;
            }
            case 'A': {
                // array: A<count><element>
                int numEnd = i + 1;
                while (numEnd < s.length() && Character.isDigit(s.charAt(numEnd))) numEnd++;
                String element = parseType(s, numEnd);
                if (element == null) return null;
                String count = s.substring(i + 1, numEnd);
                if (count.isEmpty()) count = "?";
                return element + "[" + count + "]";
            }
            case 'F': {
                // function pointer: F<argtypes><ret>
                StringBuilder args = new StringBuilder();
                int j = i + 1;
                while (j < s.length() && s.charAt(j) != 'E' && s.charAt(j) != '_') {
                    String a = parseType(s, j);
                    if (a == null) return null;
                    if (args.length() > 0) args.append(", ");
                    args.append(a);
                    j = skipType(s, j);
                    if (j < s.length() && s.charAt(j) == '_') j++;  // 参数分隔
                }
                String ret = null;
                if (j < s.length() && s.charAt(j) == 'E') {
                    j++;
                    ret = parseType(s, j);
                    if (ret == null) return null;
                }
                if (ret == null) ret = "void";
                return ret + "(*)(" + args + ")";
            }
            case 'N': {
                // nested name: N<prefix-len><name><...>E
                int numEnd = i + 1;
                while (numEnd < s.length() && Character.isDigit(s.charAt(numEnd))) numEnd++;
                int len = Integer.parseInt(s.substring(i + 1, numEnd));
                int nameEnd = numEnd + len;
                String name = s.substring(numEnd, nameEnd);
                // 查找结束 E
                int eIdx = findMatchingE(s, nameEnd);
                if (eIdx < 0) return null;
                return name + "::?";  // 简化，不解析嵌套
            }
            case 'L': {
                // 字面量类型 L<length><chars>E
                int numEnd = i + 1;
                while (numEnd < s.length() && Character.isDigit(s.charAt(numEnd))) numEnd++;
                int len = Integer.parseInt(s.substring(i + 1, numEnd));
                int end = numEnd + len;
                if (end >= s.length() || s.charAt(end) != 'E') return null;
                return "literal(\"" + s.substring(numEnd, end) + "\")";
            }
            default:
                return null;
        }
    }

    /** 计算 parseType 实际消耗的字符数。 */
    private static int skipType(String s, int i) {
        char c = s.charAt(i);
        switch (c) {
            case 'v': case 'b': case 'c': case 'h': case 's': case 't':
            case 'i': case 'j': case 'l': case 'm': case 'x': case 'y':
            case 'f': case 'd': case 'e': case 'w':
                return i + 1;
            case 'K':
                return i + 1;
            case 'P': case 'R': case 'O': case 'C': case 'V': case 'S':
                return skipType(s, i + 1);
            case 'A': {
                int numEnd = i + 1;
                while (numEnd < s.length() && Character.isDigit(s.charAt(numEnd))) numEnd++;
                return skipType(s, numEnd);
            }
            case 'F': {
                int j = i + 1;
                while (j < s.length()) {
                    char cj = s.charAt(j);
                    if (cj == 'E' || cj == '_') {
                        j++;
                        if (cj == 'E') {
                            return skipType(s, j);
                        }
                    } else {
                        j = skipType(s, j);
                    }
                }
                return j;
            }
            case 'N': {
                int e = findMatchingE(s, i);
                return e < 0 ? s.length() : e + 1;
            }
            case 'L': {
                int numEnd = i + 1;
                while (numEnd < s.length() && Character.isDigit(s.charAt(numEnd))) numEnd++;
                int len = Integer.parseInt(s.substring(i + 1, numEnd));
                int end = numEnd + len;
                if (end < s.length() && s.charAt(end) == 'E') return end + 1;
                return end;
            }
            default:
                return i + 1;
        }
    }

    private static int findMatchingE(String s, int from) {
        int depth = 0;
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == 'N') depth++;
            else if (c == 'E') {
                if (depth == 0) return i;
                depth--;
            }
        }
        return -1;
    }
}
