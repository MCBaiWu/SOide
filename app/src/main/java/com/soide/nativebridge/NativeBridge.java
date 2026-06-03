package com.soide.nativebridge;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * JNI 桥接层：让 Java 调用 NDK 编译的 native 库。
 * <p>
 * - nativeDisasm: native 实现 ARM/Thumb 反汇编（capstone 风格）
 * - nativeAssemble: native 实现 ARM/Thumb 汇编（keystone 风格）
 * - nativeSysvHash / nativeGnuHash: native ELF 字符串哈希
 */
public class NativeBridge {

    private static final String TAG = "NativeBridge";
    private static boolean loaded = false;
    private static boolean supported = false;
    private static String version = "(not loaded)";

    static {
        try {
            System.loadLibrary("soide-native");
            loaded = true;
            supported = true;
            version = nativeVersion();
            Log.i(TAG, "soide-native loaded: " + version);
        } catch (Throwable t) {
            loaded = false;
            supported = false;
            Log.w(TAG, "soide-native not available, falling back to Java: " + t.getMessage());
        }
    }

    public static boolean isSupported() {
        return supported;
    }

    public static String getVersion() {
        return version;
    }

    public static class DisasmResult {
        public final long address;
        public final int size;
        public final byte[] bytes;
        public final String mnemonic;
        public final String opStr;

        public DisasmResult(long address, int size, byte[] bytes, String mnemonic, String opStr) {
            this.address = address;
            this.size = size;
            this.bytes = bytes;
            this.mnemonic = mnemonic;
            this.opStr = opStr;
        }
    }

    public static List<DisasmResult> disasm(byte[] code, long address, boolean isThumb) {
        List<DisasmResult> out = new ArrayList<>();
        if (code == null || code.length == 0) return out;
        if (!supported) return out;
        try {
            Object[] rows = nativeDisasm(code, address, isThumb);
            for (Object row : rows) {
                Object[] cols = (Object[]) row;
                long addr = (Long) cols[0];
                int size = (Integer) cols[1];
                byte[] bytes = (byte[]) cols[2];
                String mn = (String) cols[3];
                String op = (String) cols[4];
                out.add(new DisasmResult(addr, size, bytes, mn, op));
            }
        } catch (Throwable t) {
            Log.w(TAG, "native disasm failed: " + t.getMessage());
        }
        return out;
    }

    public static byte[] assemble(String line, boolean isThumb) {
        if (!supported || line == null) return null;
        try {
            return nativeAssemble(line, isThumb);
        } catch (Throwable t) {
            Log.w(TAG, "native asm failed: " + t.getMessage());
            return null;
        }
    }

    public static long sysvHash(String name) {
        if (!supported) return 0L;
        try { return nativeSysvHash(name); } catch (Throwable t) { return 0L; }
    }

    public static long gnuHash(String name) {
        if (!supported) return 0L;
        try { return nativeGnuHash(name); } catch (Throwable t) { return 0L; }
    }

    // ----- native methods -----
    private static native Object[] nativeDisasm(byte[] code, long address, boolean isThumb);
    private static native byte[] nativeAssemble(String line, boolean isThumb);
    private static native long nativeSysvHash(String name);
    private static native long nativeGnuHash(String name);
    private static native String nativeVersion();
}
