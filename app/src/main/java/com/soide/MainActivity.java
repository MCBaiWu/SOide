package com.soide;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;
import com.soide.ui.AssemblerFragment;
import com.soide.ui.BuildConfigHelper;
import com.soide.ui.DemangleFragment;
import com.soide.ui.HistoryFragment;
import com.soide.ui.HomeFragment;
import com.soide.ui.ParseFragment;
import com.soide.ui.SettingsFragment;
import com.soide.ui.ToolsFragment;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 主界面：100% 用 Java 代码构建，不依赖任何 XML 布局文件。
 *
 * 布局结构：
 *   LinearLayout (vertical)
 *     ├─ FrameLayout  (fragment container, weight=1)
 *     └─ BottomNavigationView  (5 项 = BottomNavigationView 上限)
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String STATE_SELECTED = "selectedItem";

    // 自定义 tab id（避开 R.id.* 命名空间冲突）
    public static final int TAB_HOME = 0x7A01;
    public static final int TAB_PARSE = 0x7A02;
    public static final int TAB_DEMANGLE = 0x7A03;
    public static final int TAB_TOOLS = 0x7A04;
    public static final int TAB_ASSEMBLER = 0x7A05;

    // 历史记录从底部导航移除，单独通过 HomeFragment 卡片打开
    public static final int TAB_HISTORY = 0x7A10;

    // 设置页：单独通过 HomeFragment 卡片打开
    public static final int TAB_SETTINGS = 0x7A11;

    private static final int DEFAULT_TAB = TAB_HOME;

    private BottomNavigationView bottomNav;
    private FrameLayout fragmentContainer;
    private TextView statusBanner;
    private int currentTab = DEFAULT_TAB;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // 应用保存的主题 (必须在 super.onCreate 前)
        applySavedTheme();
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate start");

        try {
            // -------- 根 LinearLayout (vertical) --------
            LinearLayout root = new LinearLayout(this);
            root.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(Color.parseColor("#FFEFEFEF"));
            root.setFitsSystemWindows(true);

            // -------- 顶部状态条 --------
            statusBanner = new TextView(this);
            LinearLayout.LayoutParams bannerLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            statusBanner.setLayoutParams(bannerLp);
            statusBanner.setBackgroundColor(Color.parseColor("#FF1A6EF0"));
            statusBanner.setTextColor(Color.WHITE);
            statusBanner.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            statusBanner.setPadding(dp(16), dp(20), dp(16), dp(8));
            statusBanner.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            statusBanner.setText("SOide v" + BuildConfigHelper.versionName());
            root.addView(statusBanner);

            // -------- Fragment 容器 (weight=1) --------
            fragmentContainer = new FrameLayout(this);
            fragmentContainer.setId(View.generateViewId());
            LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            fragmentContainer.setLayoutParams(containerLp);
            fragmentContainer.setBackgroundColor(Color.WHITE);
            root.addView(fragmentContainer);

            // -------- 底部导航 (max 5 items) --------
            bottomNav = new BottomNavigationView(this);
            LinearLayout.LayoutParams navLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            bottomNav.setLayoutParams(navLp);
            bottomNav.setBackgroundColor(Color.WHITE);
            bottomNav.setLabelVisibilityMode(BottomNavigationView.LABEL_VISIBILITY_LABELED);
            bottomNav.setElevation(dp(8));

            // 防御性检查：BottomNavigationView 最多 5 个 item
            int max = bottomNav.getMaxItemCount();
            if (max < 5) {
                throw new IllegalStateException(
                        "BottomNavigationView max item count = " + max + ", need 5");
            }

            // 5 项菜单
            Menu menu = bottomNav.getMenu();
            addMenuItem(menu, TAB_HOME,      0, "首页",     R.drawable.ic_home);
            addMenuItem(menu, TAB_PARSE,     1, "SO 解析",   R.drawable.ic_parse);
            addMenuItem(menu, TAB_DEMANGLE,  2, "C++ 符号",  R.drawable.ic_demangle);
            addMenuItem(menu, TAB_TOOLS,     3, "进制转换",  R.drawable.ic_tools);
            addMenuItem(menu, TAB_ASSEMBLER, 4, "汇编",     R.drawable.ic_assembler);

            root.addView(bottomNav);

            setContentView(root);

            bottomNav.setOnItemSelectedListener(this::onTabSelected);

            int target = DEFAULT_TAB;
            if (savedInstanceState != null) {
                int saved = savedInstanceState.getInt(STATE_SELECTED, DEFAULT_TAB);
                if (saved != 0 && saved != TAB_HISTORY) target = saved;
            }
            currentTab = target;

            // 首次加载主动加载 fragment 并标记 checked
            Fragment initial = createFragmentForTab(target);
            if (initial != null) {
                replaceFragment(initial);
            }
            MenuItem targetItem = bottomNav.getMenu().findItem(target);
            if (targetItem != null) targetItem.setChecked(true);
            updateBanner(target);

            Log.i(TAG, "onCreate done, currentTab=" + target);

        } catch (Throwable t) {
            Log.e(TAG, "onCreate failed", t);
            // 漂亮兜底页
            showErrorFallback(t);
        }
    }

    private void addMenuItem(Menu menu, int id, int order, String title, int iconRes) {
        MenuItem item = menu.add(0, id, order, title);
        item.setIcon(iconRes);
    }

    private boolean onTabSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        Log.i(TAG, "onTabSelected: " + id);
        try {
            Fragment frag = createFragmentForTab(id);
            if (frag != null) {
                replaceFragment(frag);
                currentTab = id;
                updateBanner(id);
                return true;
            }
        } catch (Throwable t) {
            Log.e(TAG, "onTabSelected failed", t);
            showErrorFallback(t);
        }
        return false;
    }

    /**
     * 供 HomeFragment 等子 fragment 调用，切换到指定 tab。
     * TAB_HISTORY 不在底部导航中，会单独打开。
     */
    public void selectTab(int tabId) {
        if (tabId == TAB_HISTORY) {
            // 历史记录单独加载，不动底部导航
            try {
                replaceFragment(new HistoryFragment());
                updateBanner(tabId);
            } catch (Throwable t) {
                Log.e(TAG, "open history failed", t);
                showErrorFallback(t);
            }
            return;
        }
        if (tabId == TAB_SETTINGS) {
            // 设置页单独加载
            try {
                replaceFragment(new SettingsFragment());
                updateBanner(tabId);
            } catch (Throwable t) {
                Log.e(TAG, "open settings failed", t);
                showErrorFallback(t);
            }
            return;
        }
        if (bottomNav == null) return;
        MenuItem item = bottomNav.getMenu().findItem(tabId);
        if (item == null) return;
        if (!item.isChecked()) {
            item.setChecked(true);
        } else {
            Fragment frag = createFragmentForTab(tabId);
            if (frag != null) {
                replaceFragment(frag);
                currentTab = tabId;
                updateBanner(tabId);
            }
        }
    }

    @Nullable
    private Fragment createFragmentForTab(int id) {
        if (id == TAB_HOME) return new HomeFragment();
        if (id == TAB_PARSE) return new ParseFragment();
        if (id == TAB_DEMANGLE) return new DemangleFragment();
        if (id == TAB_TOOLS) return new ToolsFragment();
        if (id == TAB_ASSEMBLER) return new AssemblerFragment();
        return null;
    }

    private void replaceFragment(Fragment frag) {
        try {
            FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
            tx.setReorderingAllowed(true);
            tx.replace(fragmentContainer.getId(), frag);
            tx.commit();
        } catch (Throwable t) {
            Log.e(TAG, "replaceFragment failed", t);
        }
    }

    private void updateBanner(int tabId) {
        if (statusBanner == null) return;
        String name;
        if (tabId == TAB_HOME) name = "首页";
        else if (tabId == TAB_PARSE) name = "SO 解析";
        else if (tabId == TAB_DEMANGLE) name = "C++ 符号";
        else if (tabId == TAB_TOOLS) name = "进制转换";
        else if (tabId == TAB_ASSEMBLER) name = "汇编";
        else if (tabId == TAB_HISTORY) name = "历史记录";
        else if (tabId == TAB_SETTINGS) name = "设置";
        else name = "?";
        statusBanner.setText("SOide v" + BuildConfigHelper.versionName() + "  ·  " + name);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_SELECTED, currentTab);
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    /**
     * 在 super.onCreate 之前调用，把 SharedPreferences 里保存的主题模式应用到 AppCompatDelegate。
     * values-night/themes.xml 会自动接管所有 window 背景、状态栏色等。
     */
    private void applySavedTheme() {
        try {
            int mode = SettingsFragment.readTheme(this);
            switch (mode) {
                case SettingsFragment.THEME_LIGHT:
                    androidx.appcompat.app.AppCompatDelegate
                            .setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
                    break;
                case SettingsFragment.THEME_DARK:
                    androidx.appcompat.app.AppCompatDelegate
                            .setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
                    break;
                case SettingsFragment.THEME_SYSTEM:
                default:
                    androidx.appcompat.app.AppCompatDelegate
                            .setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                    break;
            }
        } catch (Throwable t) {
            Log.w(TAG, "applySavedTheme failed: " + t.getMessage());
        }
    }

    // ============================================================
    // 错误兜底页 - Material Design 3 风格
    // ============================================================
    private void showErrorFallback(Throwable t) {
        try {
            String stack = stackTraceToString(t);
            String summary = "主界面构建失败";
            View root = buildErrorView(summary, t.getClass().getName() + ": "
                    + (t.getMessage() == null ? "(无详细信息)" : t.getMessage()),
                    stack, "onCreate");
            setContentView(root);
        } catch (Throwable inner) {
            // 最后兜底：纯文本 TextView
            try {
                TextView err = new TextView(this);
                err.setBackgroundColor(Color.parseColor("#FFBA1A1A"));
                err.setTextColor(Color.WHITE);
                err.setPadding(dp(16), dp(40), dp(16), dp(16));
                err.setText("主界面构建失败:\n" + t);
                setContentView(err);
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * 构造 MD3 风格错误页。可从其他位置复用。
     */
    public View buildErrorView(String title, String shortMsg, String details, String scene) {
        NestedScrollView scroll = new NestedScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#FFF7F8FB"));
        scroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(24), dp(16), dp(32));
        scroll.addView(content);

        // === 顶部红/橙渐变错误 Hero 卡片 ===
        MaterialCardView heroCard = new MaterialCardView(this);
        heroCard.setRadius(dp(24));
        heroCard.setCardElevation(dp(6));
        heroCard.setCardBackgroundColor(Color.parseColor("#FFBA1A1A"));
        GradientDrawable heroBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#FFBA1A1A"), Color.parseColor("#FF7F0F0F")});
        heroBg.setCornerRadius(dp(24));
        heroCard.setBackground(heroBg);

        LinearLayout heroInner = new LinearLayout(this);
        heroInner.setOrientation(LinearLayout.VERTICAL);
        heroInner.setPadding(dp(24), dp(28), dp(24), dp(28));
        heroInner.setGravity(Gravity.CENTER);

        // 错误图标（白色大圆 + 感叹号）
        FrameLayout iconBg = new FrameLayout(this);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(72), dp(72));
        iconLp.gravity = Gravity.CENTER;
        iconBg.setLayoutParams(iconLp);
        GradientDrawable iconCircle = new GradientDrawable();
        iconCircle.setShape(GradientDrawable.OVAL);
        iconCircle.setColor(Color.parseColor("#33FFFFFF"));
        iconBg.setBackground(iconCircle);
        TextView iconText = new TextView(this);
        iconText.setText("!");
        iconText.setTextColor(Color.WHITE);
        iconText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        iconText.setTypeface(Typeface.DEFAULT_BOLD);
        iconText.setGravity(Gravity.CENTER);
        iconBg.addView(iconText, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        heroInner.addView(iconBg);

        TextView titleTv = new TextView(this);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dp(12);
        titleTv.setLayoutParams(titleLp);
        titleTv.setText(title);
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        titleTv.setGravity(Gravity.CENTER);
        heroInner.addView(titleTv);

        if (!TextUtils.isEmpty(scene)) {
            TextView sceneTv = new TextView(this);
            LinearLayout.LayoutParams sceneLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            sceneLp.topMargin = dp(4);
            sceneTv.setLayoutParams(sceneLp);
            sceneTv.setText("发生位置: " + scene);
            sceneTv.setTextColor(Color.parseColor("#CCFFFFFF"));
            sceneTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            sceneTv.setGravity(Gravity.CENTER);
            heroInner.addView(sceneTv);
        }

        heroCard.addView(heroInner);
        content.addView(heroCard);

        // === 错误概要卡片 ===
        MaterialCardView summaryCard = new MaterialCardView(this);
        LinearLayout.LayoutParams sumLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sumLp.topMargin = dp(20);
        summaryCard.setLayoutParams(sumLp);
        summaryCard.setRadius(dp(20));
        summaryCard.setCardElevation(dp(2));
        summaryCard.setCardBackgroundColor(Color.WHITE);

        LinearLayout sumInner = new LinearLayout(this);
        sumInner.setOrientation(LinearLayout.VERTICAL);
        sumInner.setPadding(dp(20), dp(20), dp(20), dp(20));

        TextView sumTitle = new TextView(this);
        sumTitle.setText("错误概要");
        sumTitle.setTextColor(Color.parseColor("#FF1B1B1F"));
        sumTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        sumTitle.setTypeface(Typeface.DEFAULT_BOLD);
        sumInner.addView(sumTitle);

        TextView sumMsg = new TextView(this);
        LinearLayout.LayoutParams sumMsgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sumMsgLp.topMargin = dp(8);
        sumMsg.setLayoutParams(sumMsgLp);
        sumMsg.setText(shortMsg);
        sumMsg.setTextColor(Color.parseColor("#FF44474E"));
        sumMsg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        sumMsg.setLineSpacing(dp(2), 1.0f);
        sumMsg.setTextIsSelectable(true);
        sumInner.addView(sumMsg);

        summaryCard.addView(sumInner);
        content.addView(summaryCard);

        // === 详细信息卡片（可滚动） ===
        MaterialCardView detailCard = new MaterialCardView(this);
        LinearLayout.LayoutParams detLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        detLp.topMargin = dp(12);
        detailCard.setLayoutParams(detLp);
        detailCard.setRadius(dp(20));
        detailCard.setCardElevation(dp(2));
        detailCard.setCardBackgroundColor(Color.parseColor("#FFF1F2F4"));

        LinearLayout detInner = new LinearLayout(this);
        detInner.setOrientation(LinearLayout.VERTICAL);
        detInner.setPadding(dp(20), dp(16), dp(20), dp(16));

        TextView detTitle = new TextView(this);
        detTitle.setText("堆栈详情");
        detTitle.setTextColor(Color.parseColor("#FF1B1B1F"));
        detTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        detTitle.setTypeface(Typeface.DEFAULT_BOLD);
        detInner.addView(detTitle);

        TextView detText = new TextView(this);
        LinearLayout.LayoutParams detTextLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        detTextLp.topMargin = dp(8);
        detText.setLayoutParams(detTextLp);
        detText.setText(details);
        detText.setTextColor(Color.parseColor("#FF202125"));
        detText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        detText.setTypeface(Typeface.MONOSPACE);
        detText.setLineSpacing(dp(1), 1.0f);
        detText.setTextIsSelectable(true);
        detText.setMaxLines(20);
        detText.setEllipsize(TextUtils.TruncateAt.END);
        detInner.addView(detText);

        detailCard.addView(detInner);
        content.addView(detailCard);

        // === 设备/环境信息卡片 ===
        MaterialCardView envCard = new MaterialCardView(this);
        LinearLayout.LayoutParams envLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        envLp.topMargin = dp(12);
        envCard.setLayoutParams(envLp);
        envCard.setRadius(dp(20));
        envCard.setCardElevation(dp(2));
        envCard.setCardBackgroundColor(Color.WHITE);

        LinearLayout envInner = new LinearLayout(this);
        envInner.setOrientation(LinearLayout.VERTICAL);
        envInner.setPadding(dp(20), dp(16), dp(20), dp(16));

        TextView envTitle = new TextView(this);
        envTitle.setText("运行环境");
        envTitle.setTextColor(Color.parseColor("#FF1B1B1F"));
        envTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        envTitle.setTypeface(Typeface.DEFAULT_BOLD);
        envInner.addView(envTitle);

        addInfoRow(envInner, "App 版本", "SOide v" + BuildConfigHelper.versionName());
        addInfoRow(envInner, "Android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        addInfoRow(envInner, "设备", Build.MANUFACTURER + " " + Build.MODEL);
        addInfoRow(envInner, "ABI", Build.SUPPORTED_ABIS[0]);

        envCard.addView(envInner);
        content.addView(envCard);

        // === 操作按钮行 ===
        LinearLayout btnRow = new LinearLayout(this);
        LinearLayout.LayoutParams btnRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        btnRowLp.topMargin = dp(20);
        btnRow.setLayoutParams(btnRowLp);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);

        MaterialButton copyBtn = new MaterialButton(this);
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyLp.rightMargin = dp(6);
        copyBtn.setLayoutParams(copyLp);
        copyBtn.setText("复制完整错误");
        copyBtn.setCornerRadius(dp(16));
        copyBtn.setBackgroundTintList(ColorStateList.valueOf(
                Color.parseColor("#FF1A6EF0")));
        copyBtn.setIconResource(android.R.drawable.ic_menu_save);
        copyBtn.setOnClickListener(v -> copyToClipboard(buildFullReport(title, shortMsg, details)));
        btnRow.addView(copyBtn);

        MaterialButton restartBtn = new MaterialButton(this);
        LinearLayout.LayoutParams restartLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        restartLp.leftMargin = dp(6);
        restartBtn.setLayoutParams(restartLp);
        restartBtn.setText("重新启动");
        restartBtn.setCornerRadius(dp(16));
        restartBtn.setBackgroundTintList(ColorStateList.valueOf(
                Color.parseColor("#FFBA1A1A")));
        restartBtn.setIconResource(android.R.drawable.ic_menu_revert);
        restartBtn.setOnClickListener(v -> {
            Intent i = getBaseContext().getPackageManager()
                    .getLaunchIntentForPackage(getPackageName());
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
            finish();
            Runtime.getRuntime().exit(0);
        });
        btnRow.addView(restartBtn);

        content.addView(btnRow);

        return scroll;
    }

    private void addInfoRow(LinearLayout parent, String key, String value) {
        LinearLayout row = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        row.setLayoutParams(lp);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView k = new TextView(this);
        k.setText(key);
        k.setTextColor(Color.parseColor("#FF74777F"));
        k.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(dp(80),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        k.setLayoutParams(kp);
        row.addView(k);

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(Color.parseColor("#FF1B1B1F"));
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        v.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        v.setTextIsSelectable(true);
        row.addView(v);

        parent.addView(row);
    }

    private void copyToClipboard(String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("SOide Error", text));
                // 在主活动里找根 view 弹 Snackbar
                View root = findViewById(android.R.id.content);
                if (root != null) {
                    Snackbar.make(root, "已复制到剪贴板", Snackbar.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "copy failed", t);
        }
    }

    private String buildFullReport(String title, String shortMsg, String details) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== SOide 错误报告 ===\n");
        sb.append("App: SOide v").append(BuildConfigHelper.versionName()).append("\n");
        sb.append("Android: ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        sb.append("ABI: ").append(Build.SUPPORTED_ABIS[0]).append("\n");
        sb.append("Title: ").append(title).append("\n");
        sb.append("Message: ").append(shortMsg).append("\n\n");
        sb.append("--- Stack Trace ---\n");
        sb.append(details);
        return sb.toString();
    }

    private String stackTraceToString(Throwable t) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            t.printStackTrace(pw);
            pw.flush();
            return sw.toString();
        } catch (Throwable e) {
            return t.getClass().getName() + ": " + t.getMessage();
        }
    }
}
