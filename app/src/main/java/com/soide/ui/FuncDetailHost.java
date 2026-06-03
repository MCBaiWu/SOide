package com.soide.ui;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 进程内 FuncDetailData 共享存储 (避免每次都序列化到磁盘)。
 */
public final class FuncDetailHost {

    private static final Map<String, FuncDetailData> STORE = new HashMap<>();

    private FuncDetailHost() {}

    /** 存放数据，返回 key (UUID)。 */
    public static synchronized String put(FuncDetailData d) {
        if (d == null) return null;
        String key = UUID.randomUUID().toString();
        STORE.put(key, d);
        return key;
    }

    public static synchronized FuncDetailData get(String key) {
        return key == null ? null : STORE.get(key);
    }

    public static synchronized void remove(String key) {
        if (key != null) STORE.remove(key);
    }

    public static synchronized void clear() {
        STORE.clear();
    }

    // --- 兼容旧版: 用文件传递 (给 FuncDetailActivity 调) ---

    public static synchronized String putData(FuncDetailData d) {
        return put(d);
    }

    public static synchronized FuncDetailData loadData(String key) {
        if (key == null) return null;
        FuncDetailData d = STORE.get(key);
        if (d != null) return d;
        // 兼容旧版: 从文件读
        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream(new File(key)))) {
            return (FuncDetailData) ois.readObject();
        } catch (Throwable t) {
            return null;
        }
    }

    /** 把数据存到文件，返回文件路径 (供 Intent 传递) */
    public static String putToFile(Context ctx, FuncDetailData d) {
        if (ctx == null || d == null) return null;
        try {
            File f = new File(ctx.getCacheDir(), "func_detail_" + System.currentTimeMillis() + ".ser");
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f))) {
                oos.writeObject(d);
            }
            return f.getAbsolutePath();
        } catch (Throwable t) {
            return null;
        }
    }
}
