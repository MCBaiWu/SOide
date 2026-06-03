package com.soide.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.soide.R;
import com.soide.elf.ElfFile;
import com.soide.util.CacheStore;

public class SoDetailActivity extends AppCompatActivity {

    public static final String EXTRA_FILE_PATH = "file_path";
    public static final String EXTRA_FILE_NAME = "file_name";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_so_detail);

        Intent it = getIntent();
        String path = it.getStringExtra(EXTRA_FILE_PATH);
        String name = it.getStringExtra(EXTRA_FILE_NAME);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(name != null ? name : "SO 详情");
        toolbar.setNavigationOnClickListener(v -> finish());

        if (path == null) {
            finish();
            return;
        }

        ElfFile elf = CacheStore.load(this, path);
        if (elf == null) {
            finish();
            return;
        }

        ViewPager2 vp = findViewById(R.id.view_pager);
        TabLayout tl = findViewById(R.id.tab_layout);
        DetailPagerAdapter adapter = new DetailPagerAdapter(this, elf);
        vp.setAdapter(adapter);
        new TabLayoutMediator(tl, vp, (tab, position) -> {
            tab.setText(adapter.getTabTitle(position));
        }).attach();
    }
}
