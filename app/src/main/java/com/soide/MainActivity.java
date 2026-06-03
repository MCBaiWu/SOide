package com.soide;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.soide.ui.AssemblerFragment;
import com.soide.ui.BuildConfigHelper;
import com.soide.ui.DemangleFragment;
import com.soide.ui.HistoryFragment;
import com.soide.ui.HomeFragment;
import com.soide.ui.ParseFragment;
import com.soide.ui.ToolsFragment;

/**
 * 主界面：100% 用 Java 代码构建，不依赖任何 XML 布局文件。
 *
 * 布局结构：
 *   LinearLayout (vertical)
 *     ├─ FrameLayout  (fragment container, weight=1)
 *     └─ BottomNavigationView
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String STATE_SELECTED = "selectedItem";

    // 自定义 tab id（避开 R.id.* 命名空间冲突）
    public static final int TAB_HOME = 0x7A01;
    public static final int TAB_PARSE = 0x7A02;
    public static final int TAB_DEMANGLE = 0x7A03;
    public static final int TAB_HISTORY = 0x7A04;
    public static final int TAB_TOOLS = 0x7A05;
    public static final int TAB_ASSEMBLER = 0x7A06;

    private static final int DEFAULT_TAB = TAB_HOME;

    private BottomNavigationView bottomNav;
    private FrameLayout fragmentContainer;
    private TextView statusBanner;        // 顶部状态条（调试用：显示当前 tab）
    private int currentTab = DEFAULT_TAB;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
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

            // -------- 顶部状态条 (debug banner，永远可见) --------
            statusBanner = new TextView(this);
            LinearLayout.LayoutParams bannerLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            statusBanner.setLayoutParams(bannerLp);
            statusBanner.setBackgroundColor(Color.parseColor("#FF1A6EF0"));
            statusBanner.setTextColor(Color.WHITE);
            statusBanner.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            statusBanner.setPadding(dp(16), dp(20), dp(16), dp(8));   // 上方留出状态栏空间
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

            // -------- 底部导航 --------
            bottomNav = new BottomNavigationView(this);
            LinearLayout.LayoutParams navLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            bottomNav.setLayoutParams(navLp);
            bottomNav.setBackgroundColor(Color.WHITE);
            bottomNav.setLabelVisibilityMode(BottomNavigationView.LABEL_VISIBILITY_LABELED);
            // 给 BottomNavigationView 一个明显的下边框阴影
            bottomNav.setElevation(dp(8));

            // 程序化构建菜单
            Menu menu = bottomNav.getMenu();
            addMenuItem(menu, TAB_HOME, 0, "首页", R.drawable.ic_home);
            addMenuItem(menu, TAB_PARSE, 1, "SO 解析", R.drawable.ic_parse);
            addMenuItem(menu, TAB_DEMANGLE, 2, "C++ 符号", R.drawable.ic_demangle);
            addMenuItem(menu, TAB_HISTORY, 3, "历史记录", R.drawable.ic_history);
            addMenuItem(menu, TAB_TOOLS, 4, "进制转换", R.drawable.ic_tools);
            addMenuItem(menu, TAB_ASSEMBLER, 5, "汇编", R.drawable.ic_assembler);

            root.addView(bottomNav);

            setContentView(root);

            bottomNav.setOnItemSelectedListener(this::onTabSelected);

            int target = DEFAULT_TAB;
            if (savedInstanceState != null) {
                int saved = savedInstanceState.getInt(STATE_SELECTED, DEFAULT_TAB);
                if (saved != 0) target = saved;
            }
            currentTab = target;

            // 关键：BottomNavigationView 第一次加载时，第一项已经 checked，
            // listener 不会触发。所以我们显式加载 fragment 并标记 checked。
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
            // 兜底：万一构建失败，至少显示一个错误页面
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
        }
        return false;
    }

    /**
     * 供 HomeFragment 等子 fragment 调用，切换到指定 tab。
     */
    public void selectTab(int tabId) {
        if (bottomNav == null) return;
        MenuItem item = bottomNav.getMenu().findItem(tabId);
        if (item == null) return;
        if (!item.isChecked()) {
            item.setChecked(true);   // 会触发 listener
        } else {
            // 已经选中：listener 不会触发，主动重载
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
        if (id == TAB_HISTORY) return new HistoryFragment();
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
        else if (tabId == TAB_HISTORY) name = "历史记录";
        else if (tabId == TAB_TOOLS) name = "进制转换";
        else if (tabId == TAB_ASSEMBLER) name = "汇编";
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

    private int sp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }

    private void showErrorFallback(Throwable t) {
        try {
            TextView err = new TextView(this);
            err.setBackgroundColor(Color.parseColor("#FFBA1A1A"));
            err.setTextColor(Color.WHITE);
            err.setPadding(dp(16), dp(40), dp(16), dp(16));
            err.setText("主界面构建失败:\n" + t.getClass().getName() + ": " + t.getMessage());
            setContentView(err);
        } catch (Throwable ignored) {
        }
    }
}
