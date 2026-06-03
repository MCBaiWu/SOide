package com.soide.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.tabs.TabLayout;
import com.soide.R;
import com.soide.nativebridge.NativeBridge;
import com.soide.util.Assembler;

import java.util.Locale;

/**
 * 汇编 / 反汇编工具 - 100% Java UI
 * <p>
 * 顶层 2 个 tab：汇编 / 反汇编
 * 每种模式下 3 个子架构 tab：ARM / Thumb / AArch64 (ARM64)
 * 全部走 NDK 真实 capstone + keystone 库。
 */
public class AssemblerFragment extends Fragment {

    private static final String TAG = "AssemblerFragment";

    // 顶层 tab: 0=汇编, 1=反汇编
    private static final int MODE_ASSEMBLE = 0;
    private static final int MODE_DISASSEMBLE = 1;

    // 架构: 0=ARM, 1=Thumb, 2=AArch64
    private static final int ARCH_ARM = 0;
    private static final int ARCH_THUMB = 1;
    private static final int ARCH_AARCH64 = 2;

    private TabLayout topTabs;
    private TabLayout archTabs;
    private TextView inputLabel;
    private EditText input;
    private TextView inputHint;
    private MaterialButton actionBtn;
    private MaterialCardView resultCard;
    private TextView resultTitle;
    private TextView resultText;
    private TextView nativeStatus;

    private int currentMode = MODE_ASSEMBLE;
    private int currentArch = ARCH_ARM;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        Log.i(TAG, "onCreateView");

        // 根 NestedScrollView
        android.widget.ScrollView scroll = new android.widget.ScrollView(ctx);
        scroll.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scroll.setBackgroundColor(Color.parseColor("#FFF7F8FB"));

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(32));
        scroll.addView(root);

        // 标题
        TextView title = new TextView(ctx);
        title.setText("汇编 / 反汇编");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.parseColor("#FF1B1B1F"));
        root.addView(title);

        TextView sub = new TextView(ctx);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(2);
        sub.setLayoutParams(subLp);
        sub.setText("capstone + keystone 真库 (NDK)  ·  ARM / Thumb / AArch64");
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        sub.setTextColor(Color.parseColor("#FF74777F"));
        root.addView(sub);

        // 顶层 mode tabs
        topTabs = new TabLayout(ctx);
        LinearLayout.LayoutParams ttLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ttLp.topMargin = dp(16);
        topTabs.setLayoutParams(ttLp);
        topTabs.addTab(topTabs.newTab().setText("汇编").setId(MODE_ASSEMBLE));
        topTabs.addTab(topTabs.newTab().setText("反汇编").setId(MODE_DISASSEMBLE));
        topTabs.setTabIndicatorFullWidth(false);
        root.addView(topTabs);

        // 架构 tabs
        archTabs = new TabLayout(ctx);
        LinearLayout.LayoutParams atLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        atLp.topMargin = dp(8);
        archTabs.setLayoutParams(atLp);
        archTabs.addTab(archTabs.newTab().setText("ARM").setId(ARCH_ARM));
        archTabs.addTab(archTabs.newTab().setText("Thumb").setId(ARCH_THUMB));
        archTabs.addTab(archTabs.newTab().setText("AArch64").setId(ARCH_AARCH64));
        archTabs.setTabIndicatorFullWidth(false);
        root.addView(archTabs);

        // 输入区卡片
        MaterialCardView inputCard = new MaterialCardView(ctx);
        LinearLayout.LayoutParams icLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        icLp.topMargin = dp(16);
        inputCard.setLayoutParams(icLp);
        inputCard.setRadius(dp(20));
        inputCard.setCardElevation(dp(2));
        inputCard.setCardBackgroundColor(Color.WHITE);

        LinearLayout inputInner = new LinearLayout(ctx);
        inputInner.setOrientation(LinearLayout.VERTICAL);
        inputInner.setPadding(dp(20), dp(16), dp(20), dp(20));

        inputLabel = new TextView(ctx);
        inputLabel.setText("汇编指令");
        inputLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        inputLabel.setTypeface(Typeface.DEFAULT_BOLD);
        inputLabel.setTextColor(Color.parseColor("#FF1B1B1F"));
        inputInner.addView(inputLabel);

        inputHint = new TextView(ctx);
        LinearLayout.LayoutParams ihLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ihLp.topMargin = dp(2);
        inputHint.setLayoutParams(ihLp);
        inputHint.setText("例如: mov r0, #1");
        inputHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        inputHint.setTextColor(Color.parseColor("#FF74777F"));
        inputInner.addView(inputHint);

        input = new EditText(ctx);
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        etLp.topMargin = dp(8);
        input.setLayoutParams(etLp);
        input.setMinLines(2);
        input.setMaxLines(4);
        input.setTypeface(Typeface.MONOSPACE);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        input.setBackgroundColor(Color.parseColor("#FFF1F2F4"));
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setTextColor(Color.parseColor("#FF202125"));
        input.setHint("mov r0, r1");
        input.setSingleLine(false);
        inputInner.addView(input);

        inputCard.addView(inputInner);
        root.addView(inputCard);

        // 操作按钮
        actionBtn = new MaterialButton(ctx);
        LinearLayout.LayoutParams abLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        abLp.topMargin = dp(12);
        actionBtn.setLayoutParams(abLp);
        actionBtn.setCornerRadius(dp(16));
        actionBtn.setText("汇编");
        actionBtn.setBackgroundTintList(ColorStateList.valueOf(
                Color.parseColor("#FF1A6EF0")));
        root.addView(actionBtn);

        // 结果卡片
        resultCard = new MaterialCardView(ctx);
        LinearLayout.LayoutParams rcLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rcLp.topMargin = dp(16);
        resultCard.setLayoutParams(rcLp);
        resultCard.setRadius(dp(20));
        resultCard.setCardElevation(dp(2));
        resultCard.setCardBackgroundColor(Color.WHITE);

        LinearLayout resultInner = new LinearLayout(ctx);
        resultInner.setOrientation(LinearLayout.VERTICAL);
        resultInner.setPadding(dp(20), dp(16), dp(20), dp(20));

        resultTitle = new TextView(ctx);
        resultTitle.setText("结果");
        resultTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        resultTitle.setTypeface(Typeface.DEFAULT_BOLD);
        resultTitle.setTextColor(Color.parseColor("#FF1B1B1F"));
        resultInner.addView(resultTitle);

        resultText = new TextView(ctx);
        LinearLayout.LayoutParams rtLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rtLp.topMargin = dp(8);
        resultText.setLayoutParams(rtLp);
        resultText.setTypeface(Typeface.MONOSPACE);
        resultText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        resultText.setTextColor(Color.parseColor("#FF202125"));
        resultText.setLineSpacing(dp(1), 1.0f);
        resultText.setTextIsSelectable(true);
        resultText.setText("—");
        resultInner.addView(resultText);

        resultCard.addView(resultInner);
        root.addView(resultCard);

        // Native 状态
        nativeStatus = new TextView(ctx);
        LinearLayout.LayoutParams nsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nsLp.topMargin = dp(12);
        nativeStatus.setLayoutParams(nsLp);
        nativeStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        String n = NativeBridge.isSupported()
                ? "✓ NDK loaded: " + NativeBridge.getVersion()
                : "✗ NDK not loaded, fallback to Java";
        nativeStatus.setText(n);
        nativeStatus.setTextColor(NativeBridge.isSupported()
                ? Color.parseColor("#FF006A60")
                : Color.parseColor("#FFBA1A1A"));
        root.addView(nativeStatus);

        // ===== 事件 =====
        topTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                currentMode = tab.getPosition();
                applyModeUi();
                clearResult();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        archTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                currentArch = tab.getPosition();
                applyArchUi();
                clearResult();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        actionBtn.setOnClickListener(v -> doAction());

        applyModeUi();
        applyArchUi();
        return scroll;
    }

    private void applyModeUi() {
        if (currentMode == MODE_ASSEMBLE) {
            inputLabel.setText("汇编指令");
            inputHint.setText("例如: mov r0, #1  /  sub sp, sp, #16  /  add x0, x1, x2");
            input.setHint("mov r0, r1");
            actionBtn.setText("汇编");
            // 汇编不允许 AArch64 Thumb 模式: 架构 tab 只显示 ARM / AArch64
            archTabs.getTabAt(ARCH_THUMB).view.setEnabled(false);
            archTabs.getTabAt(ARCH_THUMB).view.setAlpha(0.35f);
            if (currentArch == ARCH_THUMB) {
                archTabs.selectTab(archTabs.getTabAt(ARCH_ARM), true);
                currentArch = ARCH_ARM;
            }
        } else {
            inputLabel.setText("机器码 (16 进制，空格分隔)");
            inputHint.setText("例如 ARM/Thumb: 01 00 a0 e1     AArch64: 00 28 20 0a e1 03 1f aa");
            input.setHint("01 00 a0 e1");
            actionBtn.setText("反汇编");
            archTabs.getTabAt(ARCH_THUMB).view.setEnabled(true);
            archTabs.getTabAt(ARCH_THUMB).view.setAlpha(1f);
        }
    }

    private void applyArchUi() {
        String archName;
        if (currentArch == ARCH_ARM) archName = "ARM";
        else if (currentArch == ARCH_THUMB) archName = "Thumb";
        else archName = "AArch64";
        // 顶部 banner 已经在 MainActivity 里；这里可以在 native status 行追加
        nativeStatus.setText(nativeStatus.getText() + "  ·  " + archName);
    }

    private void clearResult() {
        resultText.setText("—");
    }

    private void doAction() {
        String s = input.getText() != null ? input.getText().toString().trim() : "";
        if (s.isEmpty()) {
            android.widget.Toast.makeText(requireContext(),
                    currentMode == MODE_ASSEMBLE ? "请输入汇编指令" : "请输入机器码",
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentMode == MODE_ASSEMBLE) {
            doAssemble(s);
        } else {
            doDisassemble(s);
        }
    }

    private void doAssemble(String line) {
        byte[] bytes = null;
        if (currentArch == ARCH_AARCH64) {
            bytes = NativeBridge.assembleArm64(line);
        } else {
            boolean isThumb = currentArch == ARCH_THUMB;
            bytes = NativeBridge.assemble(line, isThumb);
        }
        if (bytes == null || bytes.length == 0) {
            // 回退到 Java
            try {
                int mode = currentArch == ARCH_THUMB ? Assembler.MODE_THUMB : Assembler.MODE_ARM;
                Assembler.Result r = Assembler.assemble(line, mode);
                if (r != null && r.ok) bytes = r.bytes;
            } catch (Throwable ignored) {}
        }
        if (bytes == null || bytes.length == 0) {
            resultText.setText("✗ 汇编失败: 无法识别指令");
            return;
        }
        StringBuilder hex = new StringBuilder();
        StringBuilder ascii = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) hex.append(' ');
            hex.append(String.format(Locale.US, "%02x", bytes[i] & 0xff));
            ascii.append(String.format(Locale.US, "%02x", bytes[i] & 0xff));
        }
        resultText.setText("机器码 (hex):\n" + hex
                + "\n\n字节: " + bytes.length
                + "  ·  位: " + (bytes.length * 8)
                + "\n原始: " + ascii);
    }

    private void doDisassemble(String hexInput) {
        byte[] bytes = parseHex(hexInput);
        if (bytes == null || bytes.length == 0) {
            resultText.setText("✗ 输入解析失败: 16 进制格式不对");
            return;
        }
        java.util.List<NativeBridge.DisasmResult> list;
        if (currentArch == ARCH_AARCH64) {
            list = NativeBridge.disasmArm64(bytes, 0L);
        } else {
            list = NativeBridge.disasm(bytes, 0L, currentArch == ARCH_THUMB);
        }
        if (list == null || list.isEmpty()) {
            resultText.setText("✗ 反汇编失败: capstone 无法解析这段字节");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("地址       字节                指令\n");
        sb.append("─────────────────────────────────────\n");
        for (NativeBridge.DisasmResult ins : list) {
            sb.append(String.format(Locale.US, "0x%08X  ", ins.address));
            StringBuilder bb = new StringBuilder();
            for (int k = 0; k < ins.size; k++) {
                bb.append(String.format(Locale.US, "%02x ", ins.bytes[k] & 0xff));
            }
            while (bb.length() < 16) bb.append(' ');
            sb.append(bb).append(' ');
            sb.append(ins.mnemonic);
            if (ins.opStr != null && !ins.opStr.isEmpty()) {
                sb.append(' ').append(ins.opStr);
            }
            sb.append('\n');
        }
        sb.append("─────────────────────────────────────\n");
        sb.append("共 ").append(list.size()).append(" 条指令");
        resultText.setText(sb.toString());
    }

    private byte[] parseHex(String s) {
        s = s.replace(",", " ").replace("0x", " ").replace("\n", " ").replace("\t", " ");
        String[] tok = s.trim().split("\\s+");
        java.util.ArrayList<Byte> out = new java.util.ArrayList<>();
        for (String t : tok) {
            if (t.isEmpty()) continue;
            try {
                int v = Integer.parseInt(t, 16);
                out.add((byte) (v & 0xff));
            } catch (Exception e) {
                return null;
            }
        }
        byte[] r = new byte[out.size()];
        for (int i = 0; i < r.length; i++) r[i] = out.get(i);
        return r;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}
