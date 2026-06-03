package com.soide.ui;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.soide.R;

/**
 * 设置 fragment：100% Java MD3 风格。
 * <p>
 * 支持：
 * - 主题: 跟随系统 / 白天 / 黑夜
 * - 关于: App / NDK 版本 / GitHub
 */
public class SettingsFragment extends Fragment {

    private static final String TAG = "SettingsFragment";

    public static final String PREF = "soide_settings";
    public static final String KEY_THEME = "theme_mode";

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT  = 1;
    public static final int THEME_DARK   = 2;

    private RadioGroup themeGroup;
    private TextView themeStatus;
    private final int[] themeRadioIds = new int[3];

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                             @Nullable android.view.ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Log.i(TAG, "onCreateView");
        Context ctx = requireContext();

        NestedScrollView scroll = new NestedScrollView(ctx);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(ContextCompat.getColor(ctx, R.color.md_theme_light_background));

        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(32));
        scroll.addView(content);

        // 标题
        TextView title = new TextView(ctx);
        title.setText("设置");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setTextColor(ContextCompat.getColor(ctx, R.color.md_theme_light_onBackground));
        content.addView(title);

        TextView sub = new TextView(ctx);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(2);
        sub.setLayoutParams(subLp);
        sub.setText("主题 / 关于");
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        sub.setTextColor(ContextCompat.getColor(ctx, R.color.md_theme_light_onSurfaceVariant));
        content.addView(sub);

        // === 主题卡片 ===
        content.addView(buildSectionTitle(ctx, "外观"));
        content.addView(buildThemeCard(ctx));

        // === 关于卡片 ===
        content.addView(buildSectionTitle(ctx, "关于"));
        content.addView(buildAboutCard(ctx));

        return scroll;
    }

    private TextView buildSectionTitle(Context ctx, String s) {
        TextView t = new TextView(ctx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(20);
        lp.bottomMargin = dp(8);
        t.setLayoutParams(lp);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setTextColor(ContextCompat.getColor(ctx, R.color.md_theme_light_onSurfaceVariant));
        return t;
    }

    private View buildThemeCard(Context ctx) {
        MaterialCardView card = new MaterialCardView(ctx);
        card.setRadius(dp(20));
        card.setCardElevation(dp(2));
        card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.md_theme_light_surface));

        LinearLayout inner = new LinearLayout(ctx);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(20), dp(16), dp(20), dp(8));

        TextView head = new TextView(ctx);
        head.setText("主题风格");
        head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        head.setTypeface(head.getTypeface(), android.graphics.Typeface.BOLD);
        head.setTextColor(ContextCompat.getColor(ctx, R.color.md_theme_light_onSurface));
        inner.addView(head);

        TextView desc = new TextView(ctx);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dl.topMargin = dp(4);
        desc.setLayoutParams(dl);
        desc.setText("选择应用外观。修改后立即生效。");
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        desc.setTextColor(ContextCompat.getColor(ctx, R.color.md_theme_light_onSurfaceVariant));
        inner.addView(desc);

        themeGroup = new RadioGroup(ctx);
        LinearLayout.LayoutParams rgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rgLp.topMargin = dp(12);
        themeGroup.setLayoutParams(rgLp);
        themeGroup.setOrientation(LinearLayout.VERTICAL);

        addThemeOption(ctx, themeGroup, "跟随系统", THEME_SYSTEM);
        addThemeOption(ctx, themeGroup, "白天模式", THEME_LIGHT);
        addThemeOption(ctx, themeGroup, "黑夜模式", THEME_DARK);

        int current = readTheme(ctx);
        if (current >= 0 && current < themeRadioIds.length) {
            themeGroup.check(themeRadioIds[current]);
        }

        themeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int idx = indexOfRadioId(group, checkedId);
            if (idx >= 0) {
                writeTheme(ctx, idx);
                if (themeStatus != null) {
                    themeStatus.setText("当前: " + themeName(idx));
                }
                applyThemeAndRecreate(idx);
            }
        });

        inner.addView(themeGroup);

        themeStatus = new TextView(ctx);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.topMargin = dp(8);
        sl.bottomMargin = dp(4);
        themeStatus.setLayoutParams(sl);
        themeStatus.setText("当前: " + themeName(current));
        themeStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        themeStatus.setTextColor(ContextCompat.getColor(ctx, R.color.md_theme_light_primary));
        inner.addView(themeStatus);

        card.addView(inner);
        return card;
    }

    private void addThemeOption(Context ctx, RadioGroup group, String title, int mode) {
        RadioButton rb = new RadioButton(ctx);
        int rid = View.generateViewId();
        rb.setId(rid);
        rb.setText(title);
        rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        rb.setTextColor(ContextCompat.getColor(ctx, R.color.md_theme_light_onSurface));
        rb.setPadding(dp(8), dp(10), dp(8), dp(10));
        themeRadioIds[mode] = rid;
        group.addView(rb);
    }

    private int indexOfRadioId(RadioGroup group, int checkedId) {
        for (int i = 0; i < themeRadioIds.length; i++) {
            if (themeRadioIds[i] == checkedId) return i;
        }
        return -1;
    }

    private View buildAboutCard(Context ctx) {
        MaterialCardView card = new MaterialCardView(ctx);
        card.setRadius(dp(20));
        card.setCardElevation(dp(2));
        card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.md_theme_light_surface));

        LinearLayout inner = new LinearLayout(ctx);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(20), dp(16), dp(20), dp(20));

        addInfoRow(ctx, inner, "App 名称", "SOide");
        addInfoRow(ctx, inner, "版本", "v" + BuildConfigHelper.versionName()
                + " (" + BuildConfigHelper.versionCode() + ")");
        addInfoRow(ctx, inner, "NDK", com.soide.nativebridge.NativeBridge.getVersion());
        addInfoRow(ctx, inner, "GitHub", "github.com/MCBaiWu/SOide");

        card.addView(inner);
        return card;
    }

    private void addInfoRow(Context ctx, LinearLayout parent, String key, String value) {
        LinearLayout row = new LinearLayout(ctx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        row.setLayoutParams(lp);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView k = new TextView(ctx);
        k.setText(key);
        k.setTextColor(ContextCompat.getColor(ctx, R.color.md_theme_light_onSurfaceVariant));
        k.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(dp(80),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        k.setLayoutParams(kp);
        row.addView(k);

        TextView v = new TextView(ctx);
        v.setText(value);
        v.setTextColor(ContextCompat.getColor(ctx, R.color.md_theme_light_onSurface));
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        v.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        v.setTextIsSelectable(true);
        row.addView(v);

        parent.addView(row);
    }

    // ============================================================
    // 工具方法
    // ============================================================
    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    public static int readTheme(Context ctx) {
        if (ctx == null) return THEME_SYSTEM;
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getInt(KEY_THEME, THEME_SYSTEM);
    }

    public static void writeTheme(Context ctx, int mode) {
        if (ctx == null) return;
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putInt(KEY_THEME, mode).apply();
    }

    public static String themeName(int mode) {
        if (mode == THEME_LIGHT) return "白天模式";
        if (mode == THEME_DARK) return "黑夜模式";
        return "跟随系统";
    }

    /**
     * 在 App 启动时调用，把保存的主题模式应用到 AppCompatDelegate。
     * 这样整个 app 的窗口、状态栏等都会跟着变。
     */
    public static void applyInitialTheme() {
        // 默认 SYSTEM，由 MainActivity attach 时再覆盖
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    private void applyThemeAndRecreate(int mode) {
        if (getActivity() == null) return;
        switch (mode) {
            case THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case THEME_SYSTEM:
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
        getActivity().recreate();
    }
}
