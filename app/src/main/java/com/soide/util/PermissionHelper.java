package com.soide.util;

import android.Manifest;
import android.os.Build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 集中管理 SOide 需要的全部运行时权限。
 * <p>
 * 主要场景：打开、解析、修改、编辑 .so 文件；
 *          保存修改后的副本到公共目录；
 *          读取 /system/lib 等敏感目录。
 * <p>
 * 所有权限都是 optional 的：没拿到时功能受限但不会崩溃。
 */
public final class PermissionHelper {

    private PermissionHelper() {}

    /**
     * 返回全部需要申请的运行时权限。
     * 老 API (<23) 不需要权限，新 API 会过滤掉不适用的项。
     */
    public static List<String> allRequiredPermissions() {
        List<String> list = new ArrayList<>();

        // ---- 读：Android 13+ 分粒度；老版本走 STORAGE ----
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.READ_MEDIA_IMAGES);
            list.add(Manifest.permission.READ_MEDIA_VIDEO);
            list.add(Manifest.permission.READ_MEDIA_AUDIO);
        } else {
            list.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        // ---- 写：所有 API 都保留 ----
        list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);

        // Android 11+ 申请 MANAGE_EXTERNAL_STORAGE（特殊处理，在 UI 里要跳转）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // MANAGE_EXTERNAL_STORAGE 不是普通运行时权限，
            // 需要 ACTION_MANAGE_APP_STORAGE_ALL_FILES_ACCESS_REQUEST 跳转。
            // 这里只在弹窗里提示，不直接 request。
        }

        return Collections.unmodifiableList(list);
    }

    /**
     * 那些需要用 Intent 跳转 Settings 申请的"特殊"权限。
     * 拿不到这些权限时不影响解析 .so 文件，但保存/写入公共目录会被拒。
     */
    public static List<String> specialPermissions() {
        List<String> list = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            list.add(Manifest.permission.MANAGE_EXTERNAL_STORAGE);
        }
        return list;
    }
}
