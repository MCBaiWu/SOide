package com.soide.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.soide.MainActivity;
import com.soide.R;
import com.soide.util.ThemeUtils;

/**
 * 首页：100% 用 Java 代码构建，Material Design 3 风格。
 *
 * 结构：
 *   NestedScrollView
 *     └─ LinearLayout (vertical)
 *          ├─ 顶部 Hero 卡片（渐变 primary 背景 + App 名 + 副标题 + 版本号徽章）
 *          ├─ "主要功能" 标题
 *          └─ 4 张功能卡（MaterialCardView，icon + title + desc）
 */
public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Log.i(TAG, "onCreateView");

        Context ctx = requireContext();

        NestedScrollView scroll = new NestedScrollView(ctx);
        scroll.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(ThemeUtils.colorSurface(ctx));

        LinearLayout content = new LinearLayout(ctx);
        content.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(32));

        try {
            // === Hero 卡片 ===
            content.addView(buildHeroCard(ctx));

            // === "主要功能" 区域标题 ===
            TextView sectionTitle = new TextView(ctx);
            sectionTitle.setText("主要功能");
            sectionTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            sectionTitle.setTextColor(ThemeUtils.colorOnSurface(ctx));
            sectionTitle.setTypeface(sectionTitle.getTypeface(), android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            stLp.topMargin = dp(24);
            stLp.bottomMargin = dp(12);
            sectionTitle.setLayoutParams(stLp);
            content.addView(sectionTitle);

            // === 5 张功能卡 ===
            content.addView(buildFeatureCard(ctx,
                    R.drawable.ic_parse,
                    "SO 解析",
                    "解析 ELF/.SO 文件的节区、段、符号、动态表、重定位等",
                    Color.parseColor("#FF1A6EF0"),
                    Color.parseColor("#FFD9E2FF"),
                    MainActivity.TAB_PARSE));

            content.addView(buildFeatureCard(ctx,
                    R.drawable.ic_demangle,
                    "C++ 符号",
                    "解码 Itanium ABI 的 C++ mangled 符号名",
                    Color.parseColor("#FF6B5778"),
                    Color.parseColor("#FFF2DAFF"),
                    MainActivity.TAB_DEMANGLE));

            content.addView(buildFeatureCard(ctx,
                    R.drawable.ic_tools,
                    "进制转换",
                    "BIN / OCT / DEC / HEX 任意进制数字互转",
                    Color.parseColor("#FF535F70"),
                    Color.parseColor("#FFD7E3F7"),
                    MainActivity.TAB_TOOLS));

            content.addView(buildFeatureCard(ctx,
                    R.drawable.ic_assembler,
                    "汇编器",
                    "ARM/Thumb 汇编指令 → 机器码（keystone 真库）",
                    Color.parseColor("#FFBA1A1A"),
                    Color.parseColor("#FFFFDAD6"),
                    MainActivity.TAB_ASSEMBLER));

            content.addView(buildFeatureCard(ctx,
                    R.drawable.ic_history,
                    "历史记录",
                    "查看最近解析过的 SO 文件列表，一键重新打开",
                    Color.parseColor("#FF006A60"),
                    Color.parseColor("#FFCDEBE7"),
                    MainActivity.TAB_HISTORY));

            content.addView(buildFeatureCard(ctx,
                    R.drawable.ic_settings,
                    "设置",
                    "切换白天 / 黑夜主题，查看 App 信息",
                    Color.parseColor("#FF535F70"),
                    Color.parseColor("#FFD7E3F7"),
                    MainActivity.TAB_SETTINGS));

            // === 底部小提示 ===
            TextView footer = new TextView(ctx);
            footer.setText("底部导航 · 反汇编 capstone 真库 · 汇编 keystone 真库");
            footer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            footer.setTextColor(ThemeUtils.colorOnSurfaceVariant(ctx));
            footer.setGravity(Gravity.CENTER);
            footer.setPadding(0, dp(16), 0, 0);
            content.addView(footer);

        } catch (Throwable t) {
            Log.e(TAG, "build content failed", t);
            TextView err = new TextView(ctx);
            err.setText("首页构建失败: " + t.getMessage());
            err.setTextColor(Color.RED);
            err.setPadding(dp(16), dp(16), dp(16), dp(16));
            content.addView(err);
        }

        scroll.addView(content);
        return scroll;
    }

    // ============================================================
    // Hero 卡片：渐变 primary 背景 + 大标题 + 副标题 + 版本徽章
    // v1.4.6: 用 VipGlowCardView
    // ============================================================
    private View buildHeroCard(Context ctx) {
        VipGlowCardView hero = new VipGlowCardView(ctx);
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hero.setLayoutParams(heroLp);
        hero.setColors(
                Color.parseColor("#FF1A6EF0"),
                Color.parseColor("#FF6B9BFF"),
                Color.parseColor("#FF004A9F"));
        hero.setGlowColor(Color.parseColor("#A0FFFFFF"));
        hero.setRadius(dp(24));
        hero.setCardElevation(dp(8));
        hero.setPreventCornerOverlap(true);

        LinearLayout inner = new LinearLayout(ctx);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(24), dp(28), dp(24), dp(28));
        inner.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // App 名 + 大号 logo 同一行
        LinearLayout titleRow = new LinearLayout(ctx);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        // 圆形 logo
        FrameLayout logoBg = new FrameLayout(ctx);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(56), dp(56));
        logoLp.rightMargin = dp(16);
        logoBg.setLayoutParams(logoLp);

        GradientDrawable logoCircle = new GradientDrawable();
        logoCircle.setShape(GradientDrawable.OVAL);
        logoCircle.setColor(Color.parseColor("#33FFFFFF"));
        logoBg.setBackground(logoCircle);

        TextView logoText = new TextView(ctx);
        logoText.setText("S");
        logoText.setTextColor(Color.WHITE);
        logoText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        logoText.setTypeface(logoText.getTypeface(), android.graphics.Typeface.BOLD);
        logoText.setGravity(Gravity.CENTER);
        logoBg.addView(logoText, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        titleRow.addView(logoBg);

        // 大标题
        TextView appName = new TextView(ctx);
        appName.setText("SOide");
        appName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        appName.setTypeface(appName.getTypeface(), android.graphics.Typeface.BOLD);
        appName.setTextColor(Color.WHITE);
        appName.setLetterSpacing(0.02f);
        titleRow.addView(appName);

        inner.addView(titleRow);

        // 副标题
        TextView subtitle = new TextView(ctx);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(8);
        subtitle.setLayoutParams(subLp);
        subtitle.setText("Android ELF / .SO 文件分析工具");
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        subtitle.setTextColor(Color.parseColor("#CCFFFFFF"));
        inner.addView(subtitle);

        // 版本徽章
        MaterialCardView badge = new MaterialCardView(ctx);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeLp.topMargin = dp(16);
        badge.setLayoutParams(badgeLp);
        badge.setRadius(dp(20));
        badge.setCardBackgroundColor(Color.parseColor("#33FFFFFF"));
        badge.setCardElevation(0);

        TextView badgeText = new TextView(ctx);
        badgeText.setText("v " + BuildConfigHelper.versionName() + "  ·  capstone + keystone 真库");
        badgeText.setTextColor(Color.WHITE);
        badgeText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        badgeText.setPadding(dp(12), dp(6), dp(12), dp(6));
        badge.addView(badgeText);
        inner.addView(badge);

        hero.addView(inner);
        return hero;
    }

    // ============================================================
    // 功能卡：左侧彩色圆形 icon + 右侧标题/描述
    // v1.4.6: 用 VipGlowCardView 替代 MaterialCardView，高级渐变 + 水平发光闪烁
    // ============================================================
    private View buildFeatureCard(Context ctx,
                                  @DrawableRes int iconRes,
                                  String title,
                                  String desc,
                                  @ColorInt int accent,
                                  @ColorInt int accentContainer,
                                  int tabId) {
        // 三色渐变: accent → 白色淡色 → accentContainer 偏亮
        int top = accent;
        int mid = blendColors(accent, Color.WHITE, 0.45f);
        int bot = blendColors(accent, Color.BLACK, 0.30f);

        VipGlowCardView card = new VipGlowCardView(ctx);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(14);
        card.setLayoutParams(cardLp);
        card.setColors(top, mid, bot);
        card.setGlowColor(Color.parseColor("#80FFFFFF"));
        card.setGlowEnabled(true);
        card.setRadius(dp(22));
        card.setCardElevation(dp(8));
        card.setRippleColor(ColorStateList.valueOf(Color.parseColor("#33FFFFFF")));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> {
            Log.i(TAG, "click feature: " + title);
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).selectTab(tabId);
            }
        });

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(18), dp(18), dp(18));

        // 圆形 icon 背景 (半透明白)
        FrameLayout iconBg = new FrameLayout(ctx);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        iconLp.rightMargin = dp(16);
        iconBg.setLayoutParams(iconLp);

        GradientDrawable iconCircle = new GradientDrawable();
        iconCircle.setShape(GradientDrawable.OVAL);
        iconCircle.setColor(Color.parseColor("#33FFFFFF"));
        iconBg.setBackground(iconCircle);

        ImageView icon = new ImageView(ctx);
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        FrameLayout.LayoutParams iconInner = new FrameLayout.LayoutParams(dp(24), dp(24));
        iconInner.gravity = Gravity.CENTER;
        icon.setLayoutParams(iconInner);
        iconBg.addView(icon);
        row.addView(iconBg);

        // 文字区域
        LinearLayout textCol = new LinearLayout(ctx);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textCol.setLayoutParams(textLp);

        TextView titleTv = new TextView(ctx);
        titleTv.setText(title);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        titleTv.setTypeface(titleTv.getTypeface(), android.graphics.Typeface.BOLD);
        titleTv.setTextColor(Color.WHITE);
        titleTv.setShadowLayer(2f, 0f, 1f, Color.parseColor("#66000000"));
        textCol.addView(titleTv);

        TextView descTv = new TextView(ctx);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = dp(4);
        descTv.setLayoutParams(descLp);
        descTv.setText(desc);
        descTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        descTv.setTextColor(Color.parseColor("#EEFFFFFF"));
        descTv.setLineSpacing(dp(2), 1.0f);
        descTv.setShadowLayer(1.5f, 0f, 1f, Color.parseColor("#66000000"));
        textCol.addView(descTv);

        row.addView(textCol);

        // 右侧箭头 (白色)
        ImageView arrow = new ImageView(ctx);
        arrow.setImageResource(android.R.drawable.ic_media_play);
        arrow.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        LinearLayout.LayoutParams arrowLp = new LinearLayout.LayoutParams(dp(18), dp(18));
        arrowLp.leftMargin = dp(8);
        arrow.setLayoutParams(arrowLp);
        row.addView(arrow);

        card.addView(row);
        return card;
    }

    private static int blendColors(int c1, int c2, float ratio) {
        final float ir = 1.0f - ratio;
        float a = Color.alpha(c1) * ir + Color.alpha(c2) * ratio;
        float r = Color.red(c1) * ir + Color.red(c2) * ratio;
        float g = Color.green(c1) * ir + Color.green(c2) * ratio;
        float b = Color.blue(c1) * ir + Color.blue(c2) * ratio;
        return Color.argb((int) a, (int) r, (int) g, (int) b);
    }

    // ============================================================
    // 工具方法
    // ============================================================
    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    /** 从 int 颜色值里取出 RRGGBB 部分（去掉 alpha），用于拼透明度字符串。 */
    private String colorHex(@ColorInt int color) {
        return String.format("%06X", 0xFFFFFF & color);
    }
}
