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
