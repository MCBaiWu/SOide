package com.soide.util;

import android.content.Context;
import android.content.res.Configuration;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.soide.R;

/**
 * 主题工具：根据当前 day/night 模式从 MD3 主题中取色，
 * 替代硬编码颜色 (0xFFxxxxxx)。
 */
public final class ThemeUtils {

    private ThemeUtils() {}

    public static boolean isNight(@NonNull Context ctx) {
        int mode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    @ColorInt
    public static int colorPrimary(@NonNull Context ctx) {
        return MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorPrimary,
                ContextCompat.getColor(ctx, R.color.md_theme_light_primary));
    }

    @ColorInt
    public static int colorOnPrimary(@NonNull Context ctx) {
        return MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnPrimary,
                ContextCompat.getColor(ctx, R.color.md_theme_light_onPrimary));
    }

    @ColorInt
    public static int colorPrimaryContainer(@NonNull Context ctx) {
        return MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorPrimaryContainer,
                ContextCompat.getColor(ctx, R.color.md_theme_light_primaryContainer));
    }

    @ColorInt
    public static int colorSecondary(@NonNull Context ctx) {
        return MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorSecondary,
                ContextCompat.getColor(ctx, R.color.md_theme_light_secondary));
    }

    @ColorInt
    public static int colorSurface(@NonNull Context ctx) {
        return MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorSurface,
                ContextCompat.getColor(ctx, R.color.md_theme_light_surface));
    }

    @ColorInt
    public static int colorOnSurface(@NonNull Context ctx) {
        return MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurface,
                ContextCompat.getColor(ctx, R.color.md_theme_light_onSurface));
    }

    @ColorInt
    public static int colorOnSurfaceVariant(@NonNull Context ctx) {
        return MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant,
                ContextCompat.getColor(ctx, R.color.md_theme_light_onSurfaceVariant));
    }

    @ColorInt
    public static int colorSurfaceVariant(@NonNull Context ctx) {
        return MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorSurfaceVariant,
                ContextCompat.getColor(ctx, R.color.md_theme_light_surfaceVariant));
    }

    @ColorInt
    public static int colorOutline(@NonNull Context ctx) {
        return MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOutline,
                ContextCompat.getColor(ctx, R.color.md_theme_light_outline));
    }

    @ColorInt
    public static int colorError(@NonNull Context ctx) {
        return MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorError,
                ContextCompat.getColor(ctx, R.color.md_theme_light_error));
    }

    @ColorInt
    public static int colorOnError(@NonNull Context ctx) {
        return MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnError,
                ContextCompat.getColor(ctx, R.color.md_theme_light_onError));
    }
}
