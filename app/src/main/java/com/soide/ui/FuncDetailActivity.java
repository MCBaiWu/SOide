package com.soide.ui;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.material.textview.MaterialTextView;
import com.soide.R;
import com.soide.elf.FunctionInfo;

/**
 * 函数详情：4 个标签页 - 汇编指令 / 控制流 / 伪 C / 交叉引用。
 * 通过 {@link FuncDetailHost} 进程内共享 {@link FuncDetailData}。
 */
public class FuncDetailActivity extends AppCompatActivity {

    public static final String EXTRA_DATA_KEY = "data_key";

    private static final String[] TAB_TITLES = {"汇编", "控制流", "伪 C", "交叉引用"};

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_func_detail);

        String key = getIntent().getStringExtra(EXTRA_DATA_KEY);
        if (key == null) { finish(); return; }

        FuncDetailData data = FuncDetailHost.loadData(key);
        if (data == null || data.function == null) { finish(); return; }

        FunctionInfo f = data.function;

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setTitle(f.name);
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        MaterialTextView tvTitle = findViewById(R.id.tv_title);
        MaterialTextView tvMeta = findViewById(R.id.tv_meta);
        tvTitle.setText(f.name != null ? f.name : "(unnamed)");
        StringBuilder meta = new StringBuilder();
        meta.append(String.format("0x%x  •  size=%d  •  %s",
                f.address, f.size,
                f.sectionName != null ? f.sectionName : ""));
        if (f.isThumb) meta.append("  •  [Thumb]");
        meta.append("  •  insns=").append(f.instructions != null ? f.instructions.size() : 0);
        tvMeta.setText(meta);

        ViewPager2 vp = findViewById(R.id.view_pager);
        TabLayout tl = findViewById(R.id.tab_layout);
        FuncDetailPagerAdapter adapter = new FuncDetailPagerAdapter(this, data);
        vp.setAdapter(adapter);
        new TabLayoutMediator(tl, vp, (tab, position) -> tab.setText(TAB_TITLES[position])).attach();
    }

    /** ViewPager2 适配器 */
    static class FuncDetailPagerAdapter extends FragmentStateAdapter {
        private final FuncDetailData data;

        FuncDetailPagerAdapter(@NonNull AppCompatActivity a, FuncDetailData d) {
            super(a);
            this.data = d;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 1: return ControlFlowTabFragment.newInstance(data);
                case 2: return PseudoCTabFragment.newInstance(data);
                case 3: return XRefTabFragment.newInstance(data);
                default: return AsmTabFragment.newInstance(data);
            }
        }

        @Override
        public int getItemCount() {
            return TAB_TITLES.length;
        }
    }
}
