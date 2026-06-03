package com.soide.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.soide.R;
import com.soide.elf.ControlFlowAnalyzer;
import com.soide.elf.DisassembledInstruction;
import com.soide.util.ThemeUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 控制流图 Canvas 视图。
 * <p>
 * 布局策略：
 * - 基本块自上而下排成一列
 * - 块内画 1 个圆角矩形 + 标题 + 最多 6 行指令
 * - 边从源块底部出发，到达目标块顶部，颜色按边类型（真绿/假红/默认蓝）
 * - 前向边走右侧通道，回边走左侧通道 — 保证不穿块
 */
public class CfgCanvasView extends View {

    private static final float BLOCK_TITLE_H_DP = 22f;
    private static final float BLOCK_LINE_H_DP = 16f;
    private static final float BLOCK_PAD_H_DP = 14f;
    private static final float BLOCK_PAD_V_DP = 10f;
    private static final float BLOCK_GAP_DP = 28f;       // 块间竖直间距
    private static final float EDGE_OFFSET_DP = 16f;     // 边通道距块的偏移
    private static final float TEXT_SIZE_DP = 11f;

    private ControlFlowAnalyzer.CFG cfg;
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blockFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blockStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private final Map<Long, RectF> blockRects = new HashMap<>();  // block -> rect
    private float contentWidth = 0f;
    private float contentHeight = 0f;

    public CfgCanvasView(Context context) { super(context); init(context); }
    public CfgCanvasView(Context context, @Nullable AttributeSet a) { super(context, a); init(context); }
    public CfgCanvasView(Context context, @Nullable AttributeSet a, int s) { super(context, a, s); init(context); }

    private void init(Context ctx) {
        textPaint.setTypeface(android.graphics.Typeface.MONOSPACE);
        textPaint.setTextSize(sp(ctx, TEXT_SIZE_DP));
        textPaint.setColor(ThemeUtils.colorOnSurface(ctx));
        labelPaint.setTypeface(android.graphics.Typeface.MONOSPACE);
        labelPaint.setTextSize(sp(ctx, TEXT_SIZE_DP));
        labelPaint.setColor(ThemeUtils.colorOnSurface(ctx));
        blockFill.setStyle(Paint.Style.FILL);
        blockStroke.setStyle(Paint.Style.STROKE);
        blockStroke.setStrokeWidth(dp(ctx, 1.2f));
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(dp(ctx, 1.6f));
        edgePaint.setStrokeCap(Paint.Cap.ROUND);
        setLayerType(LAYER_TYPE_SOFTWARE, null); // 路径需要硬件加速下抗锯齿
    }

    public void setCfg(ControlFlowAnalyzer.CFG cfg) {
        this.cfg = cfg;
        blockRects.clear();
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (cfg == null || cfg.blocks == null || cfg.blocks.isEmpty()) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        Context ctx = getContext();
        float pad = dp(ctx, BLOCK_PAD_H_DP);
        float maxW = dp(ctx, 320f);  // 单块最大宽度
        // 估算每块高度：标题 + 最多6行指令
        float lineH = dp(ctx, BLOCK_LINE_H_DP);
        float titleH = dp(ctx, BLOCK_TITLE_H_DP);
        float vPad = dp(ctx, BLOCK_PAD_V_DP);
        float gap = dp(ctx, BLOCK_GAP_DP);
        float edgeOff = dp(ctx, EDGE_OFFSET_DP);

        // 收集每块高度 + 计算最宽宽度
        float totalH = gap;
        for (ControlFlowAnalyzer.Block b : cfg.blocks) {
            int lines = Math.min(8, b.instructions.size());
            float h = vPad * 2 + titleH + lineH * lines;
            // 测每行宽度取最大
            float w = 0f;
            String addrTxt = String.format(Locale.US, "0x%x", b.startAddr);
            w = Math.max(w, textPaint.measureText(addrTxt));
            for (int i = 0; i < Math.min(3, b.instructions.size()); i++) {
                DisassembledInstruction ins = b.instructions.get(i);
                String line = String.format(Locale.US, "%-7s %s",
                        ins.mnemonic, ins.opStr != null ? ins.opStr : "");
                w = Math.max(w, textPaint.measureText(line));
            }
            float blockW = Math.min(maxW, pad * 2 + w);
            w = blockW;
            b.extraMeasuredHeight = h;
            b.extraMeasuredWidth = w;
            totalH += h + gap;
        }
        // 内容宽: 单块宽 + 左右边通道
        contentWidth = (cfg.blocks.isEmpty() ? 0 : cfg.blocks.get(0).extraMeasuredWidth)
                + edgeOff * 2 + dp(ctx, 8f);
        contentHeight = totalH;

        int width = resolveSize((int) Math.ceil(contentWidth + dp(ctx, 16f)), widthMeasureSpec);
        int height = resolveSize((int) Math.ceil(contentHeight + dp(ctx, 16f)), heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (cfg == null || cfg.blocks.isEmpty()) return;
        Context ctx = getContext();

        float gap = dp(ctx, BLOCK_GAP_DP);
        float pad = dp(ctx, BLOCK_PAD_H_DP);
        float vPad = dp(ctx, BLOCK_PAD_V_DP);
        float titleH = dp(ctx, BLOCK_TITLE_H_DP);
        float lineH = dp(ctx, BLOCK_LINE_H_DP);
        float edgeOff = dp(ctx, EDGE_OFFSET_DP);
        float left = edgeOff + dp(ctx, 8f);

        // 计算每块位置
        float y = gap;
        for (ControlFlowAnalyzer.Block b : cfg.blocks) {
            float w = b.extraMeasuredWidth;
            float h = b.extraMeasuredHeight;
            float x = left;
            RectF r = new RectF(x, y, x + w, y + h);
            blockRects.put(b.startAddr, r);
            y += h + gap;
        }

        // 1) 画边（先画，使块覆盖在上层）
        for (ControlFlowAnalyzer.Block b : cfg.blocks) {
            RectF src = blockRects.get(b.startAddr);
            if (src == null) continue;
            for (ControlFlowAnalyzer.Edge e : b.successors) {
                RectF dst = blockRects.get(e.to);
                if (dst == null) continue;
                drawEdge(c, src, dst, e);
            }
        }
        // 2) 画块
        for (ControlFlowAnalyzer.Block b : cfg.blocks) {
            RectF r = blockRects.get(b.startAddr);
            if (r == null) continue;
            drawBlock(c, b, r);
        }
    }

    private void drawBlock(Canvas c, ControlFlowAnalyzer.Block b, RectF r) {
        Context ctx = getContext();
        int surface = ThemeUtils.colorSurface(ctx);
        int surfaceVariant = ThemeUtils.colorSurfaceVariant(ctx);
        int primary = ThemeUtils.colorPrimary(ctx);
        int onSurface = ThemeUtils.colorOnSurface(ctx);
        int onPrimary = ThemeUtils.colorOnPrimary(ctx);

        // 背景
        blockFill.setColor(b.isEntry ? primary : surface);
        c.drawRoundRect(r, 8f, 8f, blockFill);
        blockStroke.setColor(b.isExit ? ContextCompat.getColor(ctx, R.color.md_theme_light_error)
                : ThemeUtils.colorOutline(ctx));
        blockStroke.setStrokeWidth(b.isExit ? dp(ctx, 2.2f) : dp(ctx, 1.2f));
        c.drawRoundRect(r, 8f, 8f, blockStroke);

        // 标题 (地址)
        float textX = r.left + dp(ctx, BLOCK_PAD_H_DP);
        float titleY = r.top + dp(ctx, BLOCK_PAD_V_DP) + dp(ctx, BLOCK_TITLE_H_DP) - dp(ctx, 3f);
        textPaint.setColor(b.isEntry ? onPrimary : onSurface);
        textPaint.setFakeBoldText(true);
        textPaint.setTextSize(sp(ctx, 12f));
        String tag = (b.isEntry ? "▶ ENTRY " : (b.isExit ? "■ EXIT  " : "BB     "))
                + String.format(Locale.US, "0x%x", b.startAddr);
        c.drawText(tag, textX, titleY, textPaint);
        textPaint.setFakeBoldText(false);
        textPaint.setTextSize(sp(ctx, TEXT_SIZE_DP));

        // 指令
        float insY = titleY + dp(ctx, BLOCK_LINE_H_DP);
        for (int i = 0; i < Math.min(8, b.instructions.size()); i++) {
            DisassembledInstruction ins = b.instructions.get(i);
            String mn = ins.mnemonic != null ? ins.mnemonic : "";
            String op = ins.opStr != null ? ins.opStr : "";
            String line = String.format(Locale.US, "%-7s %s", mn, op);
            // 高亮分支指令
            if (isBranchMnemonic(mn)) {
                textPaint.setColor(ContextCompat.getColor(ctx, R.color.md_theme_light_primary));
            } else {
                textPaint.setColor(b.isEntry ? onPrimary : ThemeUtils.colorOnSurfaceVariant(ctx));
            }
            c.drawText(line, textX, insY + i * lineH, textPaint);
        }
    }

    private void drawEdge(Canvas c, RectF src, RectF dst, ControlFlowAnalyzer.Edge e) {
        Context ctx = getContext();
        int color;
        switch (e.kind) {
            case TRUE_BRANCH: color = 0xFF2E7D32; break;   // 绿
            case FALSE_BRANCH: color = 0xFFC62828; break;  // 红
            case UNCONDITIONAL:
            default: color = 0xFF1A6EF0; break;            // 蓝
        }
        edgePaint.setColor(color);
        labelPaint.setColor(color);

        boolean forward = dst.top >= src.top;
        boolean adjacent = Math.abs(dst.top - src.bottom) < dp(ctx, BLOCK_GAP_DP) + 4f
                && Math.abs(src.centerX() - dst.centerX()) < 1f;

        Path path = new Path();

        if (adjacent && forward) {
            // 紧邻的下个块: 短竖线
            float x = src.centerX();
            path.moveTo(x, src.bottom);
            path.lineTo(x, dst.top - 6f);
            c.drawPath(path, edgePaint);
            drawArrowHead(c, x, dst.top, 90f, color);
            return;
        }

        // 走侧通道，避免穿块
        float sideX;
        if (forward) {
            sideX = src.right + dp(ctx, EDGE_OFFSET_DP);
        } else {
            sideX = src.left - dp(ctx, EDGE_OFFSET_DP);
            if (sideX < dp(ctx, 4f)) sideX = dp(ctx, 4f);
        }
        float arrowX, arrowY;
        float labelX = sideX + dp(ctx, 4f);
        float labelY;
        if (forward) {
            // 前向边: 源 -> 右侧通道 -> 目标上沿
            path.moveTo(src.centerX(), src.bottom);
            path.lineTo(sideX, src.bottom);
            path.lineTo(sideX, dst.top - 6f);
            path.lineTo(dst.centerX(), dst.top - 6f);
            arrowX = dst.centerX();
            arrowY = dst.top;
            labelY = (src.bottom + dst.top) / 2f;
        } else {
            // 回边: 源 -> 侧通道 -> 目标底部进入
            float enterX = dst.centerX() - dp(ctx, 8f);
            path.moveTo(src.centerX(), src.bottom);
            path.lineTo(sideX, src.bottom);
            path.lineTo(sideX, dst.bottom + 2f);
            path.lineTo(enterX, dst.bottom + 2f);
            path.lineTo(enterX, dst.bottom);
            arrowX = enterX;
            arrowY = dst.bottom;
            labelY = (src.bottom + dst.bottom) / 2f + dp(ctx, 2f);
        }
        c.drawPath(path, edgePaint);
        drawArrowHead(c, arrowX, arrowY, forward ? 90f : -90f, color);

        String lbl = e.label != null ? e.label : "";
        if (!lbl.isEmpty()) {
            c.drawText(lbl, labelX, labelY, labelPaint);
        }
    }

    private void drawArrowHead(Canvas c, float x, float y, float deg, int color) {
        Path p = new Path();
        double a = Math.toRadians(deg);
        float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
        float l = 8f;
        p.moveTo(x, y);
        p.lineTo(x - l * dx + l * 0.4f * dy, y - l * dy - l * 0.4f * dx);
        p.lineTo(x - l * dx - l * 0.4f * dy, y - l * dy + l * 0.4f * dx);
        p.close();
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);
        c.drawPath(p, fill);
    }

    private static boolean isBranchMnemonic(String mn) {
        if (mn == null || mn.isEmpty()) return false;
        String l = mn.toLowerCase(Locale.ROOT);
        return l.equals("b") || l.equals("bl") || l.equals("bx") || l.equals("blx")
                || l.equals("br") || l.equals("ret") || l.equals("cbz") || l.equals("cbnz")
                || l.equals("tbz") || l.equals("tbnz") || l.startsWith("b.")
                || l.equals("jal") || l.equals("jalr") || l.equals("call");
    }

    private static float dp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }
    private static float sp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, c.getResources().getDisplayMetrics());
    }
}
