package com.soide.ui;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textview.MaterialTextView;
import com.google.gson.Gson;
import com.soide.R;
import com.soide.elf.FunctionInfo;

import java.io.File;
import java.io.FileInputStream;

/**
 * 函数详情：标题 + 元信息 + 反汇编指令列表。
 * 通过临时 json 文件传递 FunctionInfo。
 */
public class FuncDetailActivity extends AppCompatActivity {

    public static final String EXTRA_JSON_PATH = "json_path";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_func_detail);

        String path = getIntent().getStringExtra(EXTRA_JSON_PATH);
        if (path == null) {
            finish();
            return;
        }

        FunctionInfo f;
        try (FileInputStream in = new FileInputStream(new File(path))) {
            byte[] buf = in.readAllBytes();
            f = new Gson().fromJson(new String(buf, "UTF-8"), FunctionInfo.class);
        } catch (Exception e) {
            finish();
            return;
        }

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

        RecyclerView rv = findViewById(R.id.rv_asm);
        rv.setLayoutManager(new LinearLayoutManager(this));
        AsmAdapter adapter = new AsmAdapter(f.instructions);
        rv.setAdapter(adapter);
    }
}
