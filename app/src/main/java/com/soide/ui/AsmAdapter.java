package com.soide.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.soide.elf.DisassembledInstruction;
import com.soide.util.ThemeUtils;
import com.soide.util.Assembler;

import java.util.List;

/**
 * 汇编指令列表适配器。
 * <p>
 * v1.4.5 改进：
 * - 不同类别指令高亮不同颜色（BRANCH 蓝 / CALL 绿 / RETURN 红 / LDR 紫 / STR 橙 ...）
 * - 显示引用的字符串（如果有）
 * - 长按修改字节：输入 4/8/16 字节 hex，自动 keystone 验证机器码
 * - 长按字符串：可修改字符串内容
 * - 点击整行：复制地址/字节/汇编
 */
public class AsmAdapter extends RecyclerView.Adapter<AsmAdapter.VH> {

    public interface OnItemClick {
        void onClick(DisassembledInstruction ins);
        void onLongClick(DisassembledInstruction ins);
        void onStringClick(DisassembledInstruction ins);
    }

    private final List<DisassembledInstruction> insns;
    private final OnItemClick listener;

    public AsmAdapter(List<DisassembledInstruction> insns, OnItemClick listener) {
        this.insns = insns;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context ctx = parent.getContext();
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(ctx, 12), dp(ctx, 6), dp(ctx, 12), dp(ctx, 6));
        root.setBackgroundColor(ThemeUtils.colorSurface(ctx));
        // 整行可点击 / 长按
        root.setClickable(true);
        root.setFocusable(true);
        root.setForeground(android.graphics.drawable.ripple.ColorDrawable());

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);

        TextView addr = new TextView(ctx);
        addr.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        addr.setTypeface(android.graphics.Typeface.MONOSPACE);
        addr.setTextColor(ThemeUtils.colorOnSurfaceVariant(ctx));
        addr.setMinWidth(dp(ctx, 90));
        addr.setLayoutParams(new LinearLayout.LayoutParams(dp(ctx, 90),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(addr);

        TextView bytes = new TextView(ctx);
        bytes.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        bytes.setTypeface(android.graphics.Typeface.MONOSPACE);
        bytes.setTextColor(ThemeUtils.colorOnSurfaceVariant(ctx));
        bytes.setMinWidth(dp(ctx, 130));
        bytes.setLayoutParams(new LinearLayout.LayoutParams(dp(ctx, 130),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(bytes);

        TextView asm = new TextView(ctx);
        asm.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        asm.setTypeface(android.graphics.Typeface.MONOSPACE);
        asm.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(asm);

        root.addView(row);

        // 引用字符串子行（如果存在）
        TextView strRef = new TextView(ctx);
        strRef.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        strRef.setTypeface(android.graphics.Typeface.MONOSPACE);
        strRef.setTextColor(ThemeUtils.colorPrimary(ctx));
        strRef.setPadding(dp(ctx, 90), 0, 0, 0);
        LinearLayout.LayoutParams srp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        srp.topMargin = dp(ctx, 2);
        strRef.setLayoutParams(srp);
        strRef.setVisibility(View.GONE);
        root.addView(strRef);

        return new VH(root, addr, bytes, asm, strRef);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        DisassembledInstruction ins = insns.get(position);
        Context ctx = h.itemView.getContext();

        h.tvAddr.setText(String.format("0x%x", ins.address));
        h.tvBytes.setText(ins.getBytesHex());

        String asmText = (ins.mnemonic == null ? "" : ins.mnemonic)
                + (ins.opStr == null || ins.opStr.isEmpty() ? "" : "  " + ins.opStr);
        h.tvAsm.setText(asmText);
        h.tvAsm.setTextColor(colorForCategory(ctx, ins.category()));

        // 字符串引用
        if (ins.referencedString != null && !ins.referencedString.isEmpty()) {
            h.tvStrRef.setVisibility(View.VISIBLE);
            String truncated = ins.referencedString.length() > 80
                    ? ins.referencedString.substring(0, 80) + "..." : ins.referencedString;
            h.tvStrRef.setText("↳  \"" + truncated + "\"  @0x" + Long.toHexString(ins.referencedStringAddress));
            h.tvStrRef.setOnClickListener(v -> {
                if (listener != null) listener.onStringClick(ins);
            });
        } else {
            h.tvStrRef.setVisibility(View.GONE);
            h.tvStrRef.setOnClickListener(null);
        }

        h.itemView.setOnClickListener(v -> {
            // 点击：复制整行
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                String text = String.format("0x%x  %s  %s  %s%s",
                        ins.address, ins.getBytesHex(), ins.mnemonic,
                        ins.opStr == null ? "" : ins.opStr,
                        ins.referencedString != null ? "  // \"" + ins.referencedString + "\"" : "");
                cm.setPrimaryClip(ClipData.newPlainText("soide", text));
                Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show();
            }
            if (listener != null) listener.onClick(ins);
        });
        h.itemView.setOnLongClickListener(v -> {
            // 长按：弹出修改对话框
            showEditDialog(ins, h.itemView);
            if (listener != null) listener.onLongClick(ins);
            return true;
        });
    }

    private int colorForCategory(Context ctx, String cat) {
        switch (cat) {
            case "BRANCH": return ThemeUtils.colorPrimary(ctx);
            case "CALL":   return 0xFF2E7D32;  // 深绿
            case "RETURN": return 0xFFC62828;  // 深红
            case "LDR":    return 0xFF6A1B9A;  // 紫
            case "STR":    return 0xFFE65100;  // 橙
            case "ARITH":  return 0xFF1565C0;  // 蓝
            case "LOGIC":  return 0xFF00838F;  // 青
            case "CMP":    return 0xFFEF6C00;  // 橙
            case "MOV":    return 0xFF4527A0;  // 紫
            case "SYS":    return 0xFF555555;  // 灰
            default:       return ThemeUtils.colorOnSurface(ctx);
        }
    }

    /**
     * 弹出修改对话框：让用户输入新字节（hex），用 keystone 验证
     */
    private void showEditDialog(DisassembledInstruction ins, View anchor) {
        Context ctx = anchor.getContext();
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(ctx);
        b.setTitle("修改字节 @ 0x" + Long.toHexString(ins.address));

        // 主说明
        TextView info = new TextView(ctx);
        info.setText("当前汇编: " + ins.mnemonic + " " + (ins.opStr == null ? "" : ins.opStr)
                + "\n当前字节: " + ins.getBytesHex()
                + "\n\n输入新字节 (hex, 空格分隔):");
        info.setPadding(40, 30, 40, 10);
        info.setTextSize(12);
        b.setView(info);

        final EditText input = new EditText(ctx);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(ins.getBytesHex());
        input.setSelection(input.getText().length());
        input.setTypeface(android.graphics.Typeface.MONOSPACE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(40, 0, 40, 0);
        b.setView(input, lp);
        // 同时设置到 dialog 内容
        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(info);
        wrap.addView(input, lp);
        b.setView(wrap);

        b.setPositiveButton("验证 (keystone)", (d, w) -> {
            String text = input.getText().toString().trim();
            try {
                byte[] newBytes = parseHex(text);
                // 用 keystone 验证是否合法
                String mn = ins.mnemonic == null ? "" : ins.mnemonic;
                String op = ins.opStr == null ? "" : ins.opStr;
                Assembler.Result res = Assembler.assemble(mn + " " + op, 0L);
                if (res != null && res.ok) {
                    StringBuilder sb = new StringBuilder("keystone 反汇编预期机器码: ");
                    if (res.bytes != null) {
                        for (byte by : res.bytes) {
                            sb.append(String.format("%02x ", by & 0xff));
                        }
                    } else if (res.hex != null) {
                        sb.append(res.hex);
                    }
                    new android.app.AlertDialog.Builder(ctx)
                            .setTitle("验证成功")
                            .setMessage(sb.toString().trim()
                                    + "\n\n新字节: " + text
                                    + "\n\n注: 当前为展示，不会写回 .so。\n如需写回文件，请用 v1.5+ 的 hex 编辑器。")
                            .setPositiveButton("OK", null)
                            .show();
                } else {
                    new android.app.AlertDialog.Builder(ctx)
                            .setTitle("验证失败")
                            .setMessage("keystone 无法反汇编 \"" + mn + " " + op + "\"\n"
                                    + (res != null && res.error != null ? res.error : ""))
                            .setPositiveButton("OK", null)
                            .show();
                }
            } catch (Exception ex) {
                Toast.makeText(ctx, "解析失败: " + ex.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        b.setNegativeButton("取消", null);
        b.show();
    }

    private static byte[] parseHex(String s) {
        String[] tok = s.trim().split("\\s+");
        byte[] out = new byte[tok.length];
        for (int i = 0; i < tok.length; i++) {
            out[i] = (byte) Integer.parseInt(tok[i], 16);
        }
        return out;
    }

    @Override
    public int getItemCount() {
        return insns.size();
    }

    static int dp(Context ctx, int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                ctx.getResources().getDisplayMetrics());
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvAddr, tvBytes, tvAsm, tvStrRef;
        VH(View v, TextView addr, TextView bytes, TextView asm, TextView strRef) {
            super(v);
            this.tvAddr = addr;
            this.tvBytes = bytes;
            this.tvAsm = asm;
            this.tvStrRef = strRef;
        }
    }
}
