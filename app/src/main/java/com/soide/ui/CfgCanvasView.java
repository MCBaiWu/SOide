package com.soide.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.soide.R;
import com.soide.elf.ControlFlowAnalyzer;
import com.soide.elf.DisassembledInstruction;
import com.soide.util.ThemeUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * 控制流图 (CFG) Canvas 视图。
 * <p>
 * v1.4.5 改进：
 * <ul>
 *   <li>布局：BFS 层级布局 (树状)，每个基本块 y 由其层级决定，x 在层级内均匀分布
 *       (针对环 / 多入口：同块只放一个位置)</li>
 *   <li>边：紧邻前向走短竖线；非紧邻前向走右侧通道；回边走左侧通道 — 绝不穿块</li>
 *   <li>边颜色：TRUE_BRANCH 绿 / FALSE_BRANCH 红 / UNCONDITIONAL 蓝</li>
 *   <li>手势：单指拖动 (pan) + 双指捏合 (zoom)，自适应 day/night</li>
 *   <li>块填充：跟随 ?attr/colorSurface (夜间深、昼间浅)；entry 块用 ?attr/colorPrimaryContainer</li>
 * </ul>
 */
public class CfgCanvasView extends View {

    private static final float BLOCK_TITLE_H_DP = 22f;
    private static final float BLOCK_LINE_H_DP = 16f;
    private static final float BLOCK_PAD_H_DP = 14f;
    private static final float BLOCK_PAD_V_DP = 10f;
    private static final float BLOCK_GAP_X_DP = 36f;
    private static final float BLOCK_GAP_Y_DP = 36f;
    private static final float EDGE_OFFSET_DP = 18f;
    private static final float TEXT_SIZE_DP = 11f;
    private static final float MIN_SCALE = 0.3f;
    private static final float MAX_SCALE = 3.0f;

    private ControlFlowAnalyzer.CFG cfg;
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blockFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blockStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowFill = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Map<Long, RectF> blockRects = new HashMap<>();
    private final Map<Long, Integer> blockLevel = new HashMap<>();
    private final Map<Integer, List<Long>> levelBlocks = new HashMap<>();

    private float contentWidth = 0f;
    private float contentHeight = 0f;

    // === 手势 ===
    private float scale = 1f;
    private float offsetX = 0f;
    private float offsetY = 0f;
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

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
        arrowFill.setStyle(Paint.Style.FILL);
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        // 手势检测
        scaleDetector = new ScaleGestureDetector(ctx, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * factor));
                invalidate();
                return true;
            }
        });
        gestureDetector = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                offsetX -= dx;
                offsetY -= dy;
                invalidate();
                return true;
            }
            @Override
            public boolean onDown(MotionEvent e) { return true; }
        });
    }

    public void setCfg(ControlFlowAnalyzer.CFG cfg) {
        this.cfg = cfg;
        blockRects.clear();
        blockLevel.clear();
        levelBlocks.clear();
        requestLayout();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        boolean a = scaleDetector.onTouchEvent(ev);
        boolean b = gestureDetector.onTouchEvent(ev);
        return a || b || super.onTouchEvent(ev);
    }

    /** 计算每个 block 所属的"层级"，用于树状布局。BFS 从 entry 开始。 */
    private void computeLevels() {
        blockLevel.clear();
        levelBlocks.clear();
        if (cfg == null || cfg.blocks == null || cfg.blocks.isEmpty()) return;

        // 找 entry
        ControlFlowAnalyzer.Block entry = null;
        for (ControlFlowAnalyzer.Block b : cfg.blocks) {
            if (b.isEntry) { entry = b; break; }
        }
        if (entry == null) entry = cfg.blocks.get(0);

        Queue<ControlFlowAnalyzer.Block> q = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        q.add(entry);
        blockLevel.put(entry.startAddr, 0);
        visited.add(entry.startAddr);
        addToLevel(0, entry.startAddr);

        while (!q.isEmpty()) {
            ControlFlowAnalyzer.Block cur = q.poll();
            int curLevel = blockLevel.get(cur.startAddr);
            for (ControlFlowAnalyzer.Edge e : cur.successors) {
                if (visited.contains(e.to)) continue;
                visited.add(e.to);
                int nl = curLevel + 1;
                blockLevel.put(e.to, nl);
                addToLevel(nl, e.to);
                ControlFlowAnalyzer.Block next = findBlock(e.to);
                if (next != null) q.add(next);
            }
        }
        // unreachable blocks → 放在最后一行
        int maxLevel = 0;
        for (int l : blockLevel.values()) if (l > maxLevel) maxLevel = l;
        for (ControlFlowAnalyzer.Block b : cfg.blocks) {
            if (!visited.contains(b.startAddr)) {
                int l = maxLevel + 1;
                blockLevel.put(b.startAddr, l);
                addToLevel(l, b.startAddr);
            }
        }
    }

    private void addToLevel(int level, long addr) {
        List<Long> list = levelBlocks.get(level);
        if (list == null) {
            list = new ArrayList<>();
            levelBlocks.put(level, list);
        }
        list.add(addr);
    }

    private ControlFlowAnalyzer.Block findBlock(long addr) {
        if (cfg == null) return null;
        for (ControlFlowAnalyzer.Block b : cfg.blocks) {
            if (b.startAddr == addr) return b;
        }
        return null;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (cfg == null || cfg.blocks == null || cfg.blocks.isEmpty()) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        Context ctx = getContext();
        float pad = dp(ctx, BLOCK_PAD_H_DP);
        float maxW = dp(ctx, 280f);
        float lineH = dp(ctx, BLOCK_LINE_H_DP);
        float titleH = dp(ctx, BLOCK_TITLE_H_DP);
        float vPad = dp(ctx, BLOCK_PAD_V_DP);
        float gapX = dp(ctx, BLOCK_GAP_X_DP);
        float gapY = dp(ctx, BLOCK_GAP_Y_DP);
        float edgeOff = dp(ctx, EDGE_OFFSET_DP);

        // 计算每块高 / 宽
        for (ControlFlowAnalyzer.Block b : cfg.blocks) {
            int lines = Math.min(8, b.instructions.size());
            float h = vPad * 2 + titleH + lineH * lines;
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
            b.extraMeasuredWidth = blockW;
            b.extraMeasuredHeight = h;
        }

        // 树状布局：按层级摆放
        computeLevels();
        int maxLevel = 0;
        int widestLevelCount = 0;
        for (int l : levelBlocks.keySet()) {
            if (l > maxLevel) maxLevel = l;
            if (levelBlocks.get(l).size() > widestLevelCount) widestLevelCount = levelBlocks.get(l).size();
        }
        // 最宽层决定总宽
        float maxBlockW = 0f;
        for (ControlFlowAnalyzer.Block b : cfg.blocks) {
            if (b.extraMeasuredWidth > maxBlockW) maxBlockW = b.extraMeasuredWidth;
        }
        float totalW = widestLevelCount * (maxBlockW + gapX) - gapX + edgeOff * 2 + dp(ctx, 16f);
        float totalH = (maxLevel + 1) * (dp(ctx, 60f) + gapY) + dp(ctx, 32f);
        if (totalW < dp(ctx, 320f)) totalW = dp(ctx, 320f);
        if (totalH < dp(ctx, 240f)) totalH = dp(ctx, 240f);
        contentWidth = totalW;
        contentHeight = totalH;

        int width = resolveSize((int) Math.ceil(contentWidth), widthMeasureSpec);
        int height = resolveSize((int) Math.ceil(contentHeight), heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (cfg == null || cfg.blocks.isEmpty()) return;
        Context ctx = getContext();

        // 主题色 (跟随 day/night)
        int surface = ThemeUtils.colorSurface(ctx);
        int surfaceVariant = ThemeUtils.colorSurfaceVariant(ctx);
        int onSurface = ThemeUtils.colorOnSurface(ctx);
        c.drawColor(surface);

        // 应用平移/缩放
        c.save();
        c.translate(offsetX, offsetY);
        c.scale(scale, scale);

        float gapX = dp(ctx, BLOCK_GAP_X_DP);
        float gapY = dp(ctx, BLOCK_GAP_Y_DP);
        float edgeOff = dp(ctx, EDGE_OFFSET_DP);
        float blockY0 = dp(ctx, 16f);

        // 计算每块 (x, y)
        blockRects.clear();
        // 每层总宽，居中
        float maxBlockW = 0f;
        for (ControlFlowAnalyzer.Block b : cfg.blocks) {
            if (b.extraMeasuredWidth > maxBlockW) maxBlockW = b.extraMeasuredWidth;
        }
        for (Map.Entry<Integer, List<Long>> e : levelBlocks.entrySet()) {
            int level = e.getKey();
            List<Long> addrs = e.getValue();
            float layerW = addrs.size() * (maxBlockW + gapX) - gapX;
            float layerX0 = edgeOff + dp(ctx, 8f) + (contentWidth - edgeOff * 2 - dp(ctx, 16f) - layerW) / 2f;
            for (int i = 0; i < addrs.size(); i++) {
                long addr = addrs.get(i);
                ControlFlowAnalyzer.Block b = findBlock(addr);
                if (b == null) continue;
                float x = layerX0 + i * (maxBlockW + gapX);
                float y = blockY0 + level * (dp(ctx, 60f) + gapY);
                RectF r = new RectF(x, y, x + b.extraMeasuredWidth, y + b.extraMeasuredHeight);
                blockRects.put(addr, r);
            }
        }

        // 1) 画边
        for (ControlFlowAnalyzer.Block b : cfg.blocks) {
            RectF src = blockRects.get(b.startAddr);
            if (src == null) continue;
            for (ControlFlowAnalyzer.Edge e : b.successors) {
                RectF dst = blockRects.get(e.to);
                if (dst == null) continue;
                drawEdge(c, src, dst, e, onSurface);
            }
        }
        // 2) 画块
        for (ControlFlowAnalyzer.Block b : cfg.blocks) {
            RectF r = blockRects.get(b.startAddr);
            if (r == null) continue;
            drawBlock(c, b, r, surface, surfaceVariant, onSurface);
        }

        c.restore();
    }

    private void drawBlock(Canvas c, ControlFlowAnalyzer.Block b, RectF r,
                           int surface, int surfaceVariant, int onSurface) {
        Context ctx = getContext();
        // 背景：entry 用 primary tint，exit 用 error tint，普通块用 surface
        int fill;
        if (b.isEntry) fill = ThemeUtils.colorPrimary(ctx);
        else if (b.isExit) fill = ThemeUtils.colorError(ctx);
        else fill = surface;
        blockFill.setColor(fill);
        c.drawRoundRect(r, dp(ctx, 8f), dp(ctx, 8f), blockFill);
        int stroke = b.isExit ? ThemeUtils.colorError(ctx) : ThemeUtils.colorOutline(ctx);
        blockStroke.setColor(stroke);
        blockStroke.setStrokeWidth(b.isExit ? dp(ctx, 2.2f) : dp(ctx, 1.2f));
        c.drawRoundRect(r, dp(ctx, 8f), dp(ctx, 8f), blockStroke);

        // 标题
        float textX = r.left + dp(ctx, BLOCK_PAD_H_DP);
        float titleY = r.top + dp(ctx, BLOCK_PAD_V_DP) + dp(ctx, BLOCK_TITLE_H_DP) - dp(ctx, 3f);
        int titleColor = (b.isEntry || b.isExit) ? Color.WHITE : onSurface;
        textPaint.setColor(titleColor);
        textPaint.setFakeBoldText(true);
        textPaint.setTextSize(sp(ctx, 12f));
        String tag = (b.isEntry ? "▶ ENTRY " : (b.isExit ? "■ EXIT  " : "BB     "))
                + String.format(Locale.US, "0x%x", b.startAddr);
        c.drawText(tag, textX, titleY, textPaint);
        textPaint.setFakeBoldText(false);
        textPaint.setTextSize(sp(ctx, TEXT_SIZE_DP));

        // 指令
        float insY = titleY + dp(ctx, BLOCK_LINE_H_DP);
        float lineH = dp(ctx, BLOCK_LINE_H_DP);
        int branchColor = ThemeUtils.colorPrimary(ctx);
        int normalColor = (b.isEntry || b.isExit) ? Color.WHITE
                : ThemeUtils.colorOnSurfaceVariant(ctx);
        for (int i = 0; i < Math.min(8, b.instructions.size()); i++) {
            DisassembledInstruction ins = b.instructions.get(i);
            String mn = ins.mnemonic != null ? ins.mnemonic : "";
            String op = ins.opStr != null ? ins.opStr : "";
            String line = String.format(Locale.US, "%-7s %s", mn, op);
            textPaint.setColor(isBranchMnemonic(mn) ? branchColor : normalColor);
            c.drawText(line, textX, insY + i * lineH, textPaint);
        }
    }

    private void drawEdge(Canvas c, RectF src, RectF dst, ControlFlowAnalyzer.Edge e, int onSurface) {
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
        arrowFill.setColor(color);

        boolean forward = dst.top >= src.top;
        boolean adjacent = forward
                && Math.abs(src.centerX() - dst.centerX()) < dp(ctx, 4f)
                && Math.abs(dst.top - src.bottom) <= dp(ctx, BLOCK_GAP_Y_DP) + 4f;

        Path path = new Path();

        if (adjacent) {
            float x = src.centerX();
            path.moveTo(x, src.bottom);
            path.lineTo(x, dst.top - 6f);
            c.drawPath(path, edgePaint);
            drawArrowHead(c, x, dst.top, 90f, color);
            return;
        }

        // 侧通道
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
            path.moveTo(src.centerX(), src.bottom);
            path.lineTo(sideX, src.bottom);
            path.lineTo(sideX, dst.top - 6f);
            path.lineTo(dst.centerX(), dst.top - 6f);
            arrowX = dst.centerX();
            arrowY = dst.top;
            labelY = (src.bottom + dst.top) / 2f;
        } else {
            float enterX = dst.centerX() + dp(ctx, 8f);
            if (enterX > dst.right - dp(ctx, 4f)) enterX = dst.right - dp(ctx, 4f);
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
        arrowFill.setColor(color);
        c.drawPath(p, arrowFill);
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
