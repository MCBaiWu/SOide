package com.soide.ui;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.soide.BuildConfig;

/**
 * 简单的版本号访问工具，避免直接引用 BuildConfig 导致 stub 生成问题。
 */
public final class BuildConfigHelper {
    private BuildConfigHelper() {}

    public static String versionName() {
        return BuildConfig.VERSION_NAME;
    }

    public static int versionCode() {
        return BuildConfig.VERSION_CODE;
    }
}
