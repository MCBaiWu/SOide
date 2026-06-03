package com.soide.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.soide.R;
import com.soide.elf.DisassembledInstruction;

import java.util.List;

public class AsmAdapter extends RecyclerView.Adapter<AsmAdapter.VH> {

    private final List<DisassembledInstruction> insns;

    public AsmAdapter(List<DisassembledInstruction> insns) {
        this.insns = insns;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_insn, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        DisassembledInstruction ins = insns.get(position);
        h.tvAddr.setText(String.format("0x%x", ins.address));
        h.tvBytes.setText(bytesToHex(ins.bytes));
        h.tvAsm.setText((ins.mnemonic == null ? "" : ins.mnemonic) + " "
                + (ins.opStr == null ? "" : ins.opStr));
    }

    @Override
    public int getItemCount() {
        return insns == null ? 0 : insns.size();
    }

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format("%02x", bytes[i] & 0xff));
        }
        return sb.toString();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvAddr, tvBytes, tvAsm;

        VH(@NonNull View v) {
            super(v);
            tvAddr = v.findViewById(R.id.tv_addr);
            tvBytes = v.findViewById(R.id.tv_bytes);
            tvAsm = v.findViewById(R.id.tv_asm);
        }
    }
}
