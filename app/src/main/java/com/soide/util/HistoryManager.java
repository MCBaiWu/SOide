package com.soide.util;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 简单的历史记录管理：使用 SharedPreferences + JSON 数组保存最近分析的 SO 列表。
 * 最多保留 50 条。
 */
public final class HistoryManager {

    private static final String PREF = "soide_history";
    private static final String KEY = "entries";
    private static final int MAX_ENTRIES = 50;

    public static class Entry {
        public String fileName;
        public String filePath;
        public long timestamp;
        public long size;

        public Entry(String fileName, String filePath, long size, long timestamp) {
            this.fileName = fileName;
            this.filePath = filePath;
            this.size = size;
            this.timestamp = timestamp;
        }
    }

    private HistoryManager() {}

    public static void add(Context ctx, String fileName, String filePath, long size) {
        if (ctx == null || fileName == null) return;
        List<Entry> list = load(ctx);
        // 移除重复
        for (int i = 0; i < list.size(); i++) {
            if (filePath != null && filePath.equals(list.get(i).filePath)) {
                list.remove(i);
                break;
            }
        }
        list.add(0, new Entry(fileName, filePath, size, System.currentTimeMillis()));
        if (list.size() > MAX_ENTRIES) {
            list = list.subList(0, MAX_ENTRIES);
        }
        save(ctx, list);
    }

    public static List<Entry> load(Context ctx) {
        if (ctx == null) return new ArrayList<>();
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String raw = sp.getString(KEY, "[]");
        List<Entry> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Entry e = new Entry(
                        o.optString("name"),
                        o.optString("path"),
                        o.optLong("size"),
                        o.optLong("ts")
                );
                list.add(e);
            }
        } catch (JSONException ignored) {
        }
        return list;
    }

    public static void clear(Context ctx) {
        if (ctx == null) return;
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY)
                .apply();
    }

    private static void save(Context ctx, List<Entry> list) {
        if (ctx == null) return;
        JSONArray arr = new JSONArray();
        try {
            for (Entry e : list) {
                JSONObject o = new JSONObject();
                o.put("name", e.fileName);
                o.put("path", e.filePath);
                o.put("size", e.size);
                o.put("ts", e.timestamp);
                arr.put(o);
            }
        } catch (JSONException ignored) {
        }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, arr.toString())
                .apply();
    }
}
