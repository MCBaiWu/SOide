package com.soide.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * 应用设置项持久化工具 (v1.4.6)
 * <p>
 * 集中所有 key，避免散落在各处。
 */
public final class SettingsPrefs {

    private SettingsPrefs() {}

    public static final String FILE = "soide_settings";

    // ---- 主题 ----
    public static final String KEY_THEME = "theme_mode";

    // ---- 反汇编分析 ----
    /** 是否启用字符串引用分析（开：识别 LDR/ADR/ADRP+ADD 指向的字符串并高亮） */
    public static final String KEY_ENABLE_STRING_REF = "enable_string_ref";
    /** 是否启用函数名解析（开：bl 0x12345 解析为 bl sub_12345） */
    public static final String KEY_ENABLE_FUNC_NAME = "enable_func_name";

    public static SharedPreferences prefs(@NonNull Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean isStringRefEnabled(@NonNull Context ctx) {
        return prefs(ctx).getBoolean(KEY_ENABLE_STRING_REF, true);
    }

    public static void setStringRefEnabled(@NonNull Context ctx, boolean v) {
        prefs(ctx).edit().putBoolean(KEY_ENABLE_STRING_REF, v).apply();
    }

    public static boolean isFuncNameEnabled(@NonNull Context ctx) {
        return prefs(ctx).getBoolean(KEY_ENABLE_FUNC_NAME, true);
    }

    public static void setFuncNameEnabled(@NonNull Context ctx, boolean v) {
        prefs(ctx).edit().putBoolean(KEY_ENABLE_FUNC_NAME, v).apply();
    }
}
