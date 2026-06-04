package com.soide.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.soide.R;
import com.soide.util.ThemeUtils;

import java.util.List;

public class DetailAdapter extends RecyclerView.Adapter<DetailAdapter.VH> {

    public static class Item {
        public String type;
        public String title;
        public String subtitle;
        public String meta;
        public String copyText; // 点击时复制的字符串（默认用 title|subtitle|meta）
    }

    public interface OnClick {
        void onClick(Item item);
    }

    private final List<Item> items;
    private final OnClick onClick;

    public DetailAdapter(List<Item> items, OnClick onClick) {
        this.items = items;
        this.onClick = onClick;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_detail, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Item it = items.get(position);
        h.title.setText(it.title != null ? it.title : "");
        h.subtitle.setText(it.subtitle != null ? it.subtitle : "");
        h.meta.setText(it.meta != null ? it.meta : "");
        h.type.setText(it.type != null ? it.type : "");
        h.root.setOnClickListener(v -> {
            // v1.4.6: 点击弹出详情对话框（对话框里每行可点复制）
            ItemDetailDialog.show(h.itemView.getContext(), it);
            // 自定义回调
            if (onClick != null) onClick.onClick(it);
        });
        h.root.setOnLongClickListener(v -> {
            // 长按：复制整行到剪贴板
            copyItemToClipboard(h.itemView.getContext(), it);
            return true;
        });
    }

    private void copyItemToClipboard(Context ctx, Item it) {
        String text = it.copyText;
        if (text == null) {
            StringBuilder sb = new StringBuilder();
            if (it.title != null) sb.append(it.title);
            if (it.subtitle != null) sb.append("  ").append(it.subtitle);
            if (it.meta != null) sb.append("  ").append(it.meta);
            text = sb.toString().trim();
        }
        if (text == null || text.isEmpty()) return;
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("soide", text));
            Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final CardView root;
        final TextView title, subtitle, meta, type;

        VH(@NonNull View v) {
            super(v);
            root = (CardView) v;
            title = v.findViewById(R.id.tv_title);
            subtitle = v.findViewById(R.id.tv_subtitle);
            meta = v.findViewById(R.id.tv_meta);
            type = v.findViewById(R.id.tv_type);
            // 主题色
            Context ctx = v.getContext();
            root.setCardBackgroundColor(ThemeUtils.colorSurface(ctx));
            title.setTextColor(ThemeUtils.colorOnSurface(ctx));
            subtitle.setTextColor(ThemeUtils.colorOnSurfaceVariant(ctx));
            meta.setTextColor(ThemeUtils.colorOnSurfaceVariant(ctx));
            type.setTextColor(ThemeUtils.colorPrimary(ctx));
        }
    }
}
