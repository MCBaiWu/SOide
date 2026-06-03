package com.soide.util;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.soide.elf.ElfFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * 解析结果缓存：以文件路径为 key，将 ElfFile 序列化到 cacheDir/soide/ 下。
 * 使用 GSON 序列化。第一次解析后保存；再次打开同一文件时直接读缓存。
 */
public final class CacheStore {

    private static final String TAG = "CacheStore";
    private static final String DIR = "soide";
    /**
     * 缓存数据格式版本号。修改 {@link #save} / {@link #load} 的字段后必须
     * 同步 bump 这个版本号，否则旧版缓存（可能字段不兼容）会被错误复用。
     * <p>
     * 历史：
     * <ul>
     *   <li>v3: 移除 JNA Capstone 后改用 NativeBridge 反汇编缓存；
     *          DisassembledInstruction.mnemonic 之前可能为空，现在保证有值。
     *          bump 到 v3 后旧缓存全部失效，强制重新解析。</li>
     * </ul>
     */
    public static final int CACHE_VERSION = 3;

    private CacheStore() {}

    private static File cacheDir(Context ctx) {
        File d = new File(ctx.getCacheDir(), DIR);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static String keyFor(String filePath) {
        if (filePath == null) return "null";
        String s = filePath.replaceAll("[^a-zA-Z0-9_.-]", "_");
        return s + ".json";
    }

    /**
     * 应用启动时检查缓存目录，如果缓存版本不匹配则全部清空。
     * 用于解决"修了 bug 但用户重新打开还是老数据"的问题。
     */
    public static void pruneIfStaleVersion(Context ctx) {
        File d = cacheDir(ctx);
        if (d == null || !d.exists()) return;
        File vf = new File(d, "version.txt");
        int current = -1;
        try {
            if (vf.exists()) {
                try (FileInputStream in = new FileInputStream(vf)) {
                    byte[] buf = in.readAllBytes();
                    String s = new String(buf, "UTF-8").trim();
                    current = Integer.parseInt(s);
                }
            }
        } catch (Throwable t) {
            current = -1;
        }
        if (current == CACHE_VERSION) return;

        // 版本不一致 → 全部清空
        File[] files = d.listFiles();
        if (files != null) {
            int n = 0;
            for (File f : files) {
                try {
                    if (f.isFile() && f.delete()) n++;
                } catch (Throwable ignored) {}
            }
            Log.i(TAG, "Cache version " + current + " != " + CACHE_VERSION
                    + ", cleared " + n + " stale files");
        }
        // 写新版本号
        try {
            try (FileOutputStream out = new FileOutputStream(vf)) {
                out.write(Integer.toString(CACHE_VERSION).getBytes("UTF-8"));
            }
        } catch (Throwable t) {
            Log.w(TAG, "write cache version failed", t);
        }
    }

    public static boolean has(Context ctx, String filePath) {
        return new File(cacheDir(ctx), keyFor(filePath)).exists();
    }

    public static void save(Context ctx, String filePath, ElfFile elf) {
        try {
            Gson g = new GsonBuilder().serializeNulls().create();
            String json = g.toJson(elf);
            File f = new File(cacheDir(ctx), keyFor(filePath));
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(json.getBytes("UTF-8"));
            }
        } catch (Exception e) {
            Log.w(TAG, "save cache failed", e);
        }
    }

    public static ElfFile load(Context ctx, String filePath) {
        try {
            File f = new File(cacheDir(ctx), keyFor(filePath));
            if (!f.exists()) return null;
            byte[] buf;
            try (FileInputStream in = new FileInputStream(f)) {
                buf = in.readAllBytes();
            }
            String json = new String(buf, "UTF-8");
            return new Gson().fromJson(json, ElfFile.class);
        } catch (Exception e) {
            Log.w(TAG, "load cache failed", e);
            return null;
        }
    }
}
