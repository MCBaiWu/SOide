package com.soide.ui;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.soide.util.ThemeUtils;

/**
 * v1.4.6: 所有 tab 项目点击弹出的详情对话框。
 * <p>
 * 布局：type 标签（顶部）+ title（突出）+ subtitle + meta，每行可单独长按复制，
 * 底部"复制全部"按钮 + 关闭按钮。
 */
public final class ItemDetailDialog {

    private ItemDetailDialog() {}

    public static AlertDialog show(Context ctx, DetailAdapter.Item it) {
        if (it == null) return null;
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(ctx, 20);
        root.setPadding(pad, pad, pad, pad / 2);
        root.setBackgroundColor(ThemeUtils.colorSurface(ctx));

        // type 标签
        if (it.type != null && !it.type.isEmpty()) {
            TextView tvType = new TextView(ctx);
            tvType.setText(it.type);
            tvType.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tvType.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            tvType.setTextColor(ThemeUtils.colorPrimary(ctx));
            tvType.setPadding(0, 0, 0, dp(ctx, 8));
            root.addView(tvType);
        }

        // title
        if (it.title != null && !it.title.isEmpty()) {
            TextView tv = makeRow(ctx, it.title, 16, true,
                    ThemeUtils.colorOnSurface(ctx), () -> copy(ctx, it.title));
            root.addView(tv);
        }
        // subtitle
        if (it.subtitle != null && !it.subtitle.isEmpty()) {
            TextView tv = makeRow(ctx, it.subtitle, 13, false,
                    ThemeUtils.colorOnSurface(ctx), () -> copy(ctx, it.subtitle));
            root.addView(tv);
        }
        // meta
        if (it.meta != null && !it.meta.isEmpty()) {
            TextView tv = makeRow(ctx, it.meta, 12, false,
                    ThemeUtils.colorOnSurfaceVariant(ctx), () -> copy(ctx, it.meta));
            root.addView(tv);
        }

        // 拼装 "复制全部" 文本
        final String allText = buildAllText(it);

        return new AlertDialog.Builder(ctx)
                .setView(root)
                .setPositiveButton("关闭", null)
                .setNeutralButton("复制全部", (d, w) -> {
                    copy(ctx, allText);
                })
                .show();
    }

    private static TextView makeRow(Context ctx, String text, float sp, boolean bold,
                                    int color, Runnable onLong) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD,
                android.graphics.Typeface.BOLD);
        tv.setTextColor(color);
        tv.setTextIsSelectable(true);
        tv.setPadding(0, dp(ctx, 6), 0, dp(ctx, 6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tv.setLayoutParams(lp);
        // 整行可点复制
        tv.setOnClickListener(v -> onLong.run());
        tv.setOnLongClickListener(v -> { onLong.run(); return true; });
        return tv;
    }

    private static String buildAllText(DetailAdapter.Item it) {
        if (it.copyText != null && !it.copyText.isEmpty()) return it.copyText;
        StringBuilder sb = new StringBuilder();
        if (it.type != null) sb.append('[').append(it.type).append("] ");
        if (it.title != null) sb.append(it.title);
        if (it.subtitle != null) sb.append("  ").append(it.subtitle);
        if (it.meta != null) sb.append("  ").append(it.meta);
        return sb.toString().trim();
    }

    private static void copy(Context ctx, String text) {
        if (text == null || text.isEmpty()) return;
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("soide", text));
            Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show();
        }
    }

    private static int dp(Context ctx, int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                ctx.getResources().getDisplayMetrics());
    }
}
