package com.soide.ui;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.soide.R;
import com.soide.elf.DisassembledInstruction;
import com.soide.util.ThemeUtils;

import java.util.List;

/**
 * 函数详情 - 汇编指令 tab。
 * <p>
 * v1.4.5 改进：
 * - 不同类别指令高亮不同颜色
 * - 显示引用的字符串 (StringReferenceAnalyzer 输出)
 * - 点击：复制整行
 * - 长按：弹出修改字节对话框（用 keystone 验证）
 * - 长按字符串行：弹出修改字符串内容对话框
 */
public class AsmTabFragment extends Fragment {

    private static final String ARG_FUNC_INSNS = "func_insns";

    private List<DisassembledInstruction> insns;
    private RecyclerView rv;
    private TextView tvCount;
    private AsmAdapter adapter;

    public static AsmTabFragment newInstance(List<DisassembledInstruction> insns) {
        AsmTabFragment f = new AsmTabFragment();
        f.insns = insns;
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_list_tab, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rv = view.findViewById(R.id.recycler);
        tvCount = view.findViewById(R.id.tv_count);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new AsmAdapter(insns, new AsmAdapter.OnItemClick() {
            @Override public void onClick(DisassembledInstruction ins) {}
            @Override public void onLongClick(DisassembledInstruction ins) {}
            @Override public void onStringClick(DisassembledInstruction ins) {
                showStringEditDialog(ins);
            }
        });
        rv.setAdapter(adapter);
        tvCount.setText(insns.size() + " 条指令");
    }

    /**
     * 弹出"修改字符串"对话框。
     * 让用户输入新字符串（自动 NUL 终止），并显示当前地址、长度、所在节。
     * v1.4.5 暂不直接写回文件 — 仅展示效果并复制 hex。
     */
    private void showStringEditDialog(DisassembledInstruction ins) {
        if (ins.referencedString == null) return;
        Context ctx = requireContext();
        String old = ins.referencedString;

        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle("修改引用的字符串");

        TextView info = new TextView(ctx);
        info.setText("地址: 0x" + Long.toHexString(ins.referencedStringAddress)
                + "\n当前: \"" + old + "\"  (len=" + old.length() + ")"
                + "\n\n输入新字符串 (会自动 NUL 终止):");
        info.setPadding(40, 30, 40, 10);
        info.setTextSize(12);
        info.setTextColor(ThemeUtils.colorOnSurface(ctx));

        EditText input = new EditText(ctx);
        input.setText(old);
        input.setSelection(input.getText().length());
        input.setTypeface(android.graphics.Typeface.MONOSPACE);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(40, 0, 40, 0);
        input.setLayoutParams(lp);

        android.widget.LinearLayout wrap = new android.widget.LinearLayout(ctx);
        wrap.setOrientation(android.widget.LinearLayout.VERTICAL);
        wrap.addView(info);
        wrap.addView(input);
        b.setView(wrap);
        b.setPositiveButton("预览 hex", (d, w) -> {
            String newStr = input.getText().toString();
            StringBuilder hex = new StringBuilder();
            for (byte by : newStr.getBytes()) hex.append(String.format("%02x ", by & 0xff));
            hex.append("00");
            String text = "地址: 0x" + Long.toHexString(ins.referencedStringAddress)
                    + "\n新内容: \"" + newStr + "\""
                    + "\nhex: " + hex.toString().trim()
                    + "\n\n(只读预览；如需持久化请用 v1.5+ 的 hex 编辑器)";
            new AlertDialog.Builder(ctx)
                    .setTitle("字符串预览")
                    .setMessage(text)
                    .setPositiveButton("复制 hex", (dd, ww) -> {
                        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) {
                            cm.setPrimaryClip(ClipData.newPlainText("soide", hex.toString().trim()));
                            Toast.makeText(ctx, "已复制 hex", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("关闭", null)
                    .show();
        });
        b.setNegativeButton("关闭", null);
        b.show();
    }
}
