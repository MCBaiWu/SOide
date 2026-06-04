package com.soide.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.Nullable;

import com.soide.elf.ControlFlowAnalyzer;
import com.soide.elf.DisassembledInstruction;
import com.soide.util.ThemeUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
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
 * v1.4.6 改进：
 * <ul>
 *   <li>布局：BFS 层级布局 + 每层独立计算 X (不跨层居中，块就在最左侧，避免远在画布右侧)</li>
 *   <li>预计算：setCfg() 时一次性 computeLayout() 算出每个 block 的 (x,y) 矩形</li>
 *   <li>自适应缩放：初次显示时按 View 实际大小做 fit-to-width (避免大函数看不到入口)</li>
 *   <li>边：真分支绿 / 假分支红 / 无条件蓝, 紧邻前向短竖线, 非紧邻走右侧通道</li>
 *   <li>手势：单指 pan + 双指 pinch-zoom, 范围 0.2x ~ 4x</li>
 *   <li>主题色：跟随 day/night; 入口块主色, 出口块错误色</li>
 * </ul>
 */
public class CfgCanvasView extends View {

    private static final String TAG = "CfgCanvasView";

    private static final float BLOCK_TITLE_H_DP = 22f;
    private static final float BLOCK_LINE_H_DP = 15f;
    private static final float BLOCK_PAD_H_DP = 12f;
    private static final float BLOCK_PAD_V_DP = 8f;
    private static final float BLOCK_GAP_X_DP = 32f;
    private static final float BLOCK_GAP_Y_DP = 36f;
    private static final float EDGE_OFFSET_DP = 16f;
    private static final float TEXT_SIZE_DP = 11f;
    private static final float TITLE_TEXT_SP = 12f;
    private static final float MIN_SCALE = 0.2f;
    private static final float MAX_SCALE = 4.0f;
    private static final float MAX_BLOCK_W_DP = 240f;

    private ControlFlowAnalyzer.CFG cfg;
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blockFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blockStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** key = block.startAddr, value = (x,y,w,h) in unscaled content space */
    private final Map<Long, RectF> blockRects = new HashMap<>();
    private final Map<Long, Integer> blockLevel = new HashMap<>();
    private final Map<Integer, List<Long>> levelBlocks = new HashMap<>();

    private float contentWidth = 0f;
    private float contentHeight = 0f;
    private int maxLevel = 0;

    // === 手势 ===
    private float scale = 1f;
    private float offsetX = 0f;
    private float offsetY = 0f;
    private float minScale = MIN_SCALE;  // 重新自适应后更新
    private boolean didAutoFit = false;
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    public CfgCanvasView(Context context) { super(context); init(context); }
    public CfgCanvasView(Context context, @Nullable AttributeSet a) { super(context, a); init(context); }
    public CfgCanvasView(Context context, @Nullable AttributeSet a, int s) { super(context, a, s); init(context); }

    private void init(Context ctx) {
        textPaint.setTypeface(android.graphics.Typeface.MONOSPACE);
        textPaint.setTextSize(sp(ctx, TEXT_SIZE_DP));
        titlePaint.setTypeface(android.graphics.Typeface.MONOSPACE);
        titlePaint.setTextSize(sp(ctx, TITLE_TEXT_SP));
        titlePaint.setFakeBoldText(true);
        labelPaint.setTypeface(android.graphics.Typeface.MONOSPACE);
        labelPaint.setTextSize(sp(ctx, TEXT_SIZE_DP));
        blockFill.setStyle(Paint.Style.FILL);
        blockStroke.setStyle(Paint.Style.STROKE);
        blockStroke.setStrokeWidth(dp(ctx, 1.2f));
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(dp(ctx, 1.6f));
        edgePaint.setStrokeCap(Paint.Cap.ROUND);
        arrowFill.setStyle(Paint.Style.FILL);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(ctx, 0.5f));
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        // 背景
        setBackgroundColor(ThemeUtils.colorSurface(ctx));

        // 手势检测
        scaleDetector = new ScaleGestureDetector(ctx, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                scale = Math.max(minScale, Math.min(MAX_SCALE, scale * factor));
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
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                // 双击重置视图
                autoFit();
                invalidate();
                return true;
            }
        });
    }

    public void setCfg(ControlFlowAnalyzer.CFG cfg) {
        Log.i(TAG, "setCfg blocks=" + (cfg != null && cfg.blocks != null ? cfg.blocks.size() : 0));
        this.cfg = cfg;
        didAutoFit = false;
        scale = 1f;
        offsetX = 0f;
        offsetY = 0f;
        computeLayout();
        requestLayout();
        invalidate();
    }

    /**
     * 一次性预计算布局:
     *  1) BFS 给每个 block 分配层级
     *  2) 估算每个 block 的宽/高
     *  3) 按层级均匀分配 X 位置
     *  4) 算出 contentWidth/contentHeight
     */
    private void computeLayout() {
        blockRects.clear();
        blockLevel.clear();
        levelBlocks.clear();
        contentWidth = 0f;
        contentHeight = 0f;
        maxLevel = 0;

        if (cfg == null || cfg.blocks == null || cfg.blocks.isEmpty()) {
            return;
        }

        Context ctx = getContext();
        float pad = dp(ctx, BLOCK_PAD_H_DP);
        float vPad = dp(ctx, BLOCK_PAD_V_DP);
        float titleH = dp(ctx, BLOCK_TITLE_H_DP);
        float lineH = dp(ctx, BLOCK_LINE_H_DP);
        float gapX = dp(ctx, BLOCK_GAP_X_DP);
        float gapY = dp(ctx, BLOCK_GAP_Y_DP);
        float edgeOff = dp(ctx, EDGE_OFFSET_DP);
        float maxW = dp(ctx, MAX_BLOCK_W_DP);

        // 1) BFS 分配层级
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
        // unreachable -> 最后一行
        for (ControlFlowAnalyzer.Block b : cfg.blocks) {
            if (!visited.contains(b.startAddr)) {
                int l = maxLevel() + 1;
                blockLevel.put(b.startAddr, l);
                addToLevel(l, b.startAddr);
            }
        }
        maxLevel = maxLevel();

        // 2) 估算每个 block 的宽/高 (写回 Block.extra*)
        for (ControlFlowAnalyzer.Block b : cfg.blocks) {
            int lines = Math.min(8, b.instructions.size());
            float h = vPad * 2 + titleH + lineH * lines;
            float w = 0f;
            String addrTxt = String.format(Locale.US, "0x%x", b.startAddr);
            w = Math.max(w, titlePaint.measureText(addrTxt));
            for (int i = 0; i < Math.min(3, b.instructions.size()); i++) {
                DisassembledInstruction ins = b.instructions.get(i);
                String line = String.format(Locale.US, "%-7s %s",
                        ins.mnemonic != null ? ins.mnemonic : "",
                        ins.opStr != null ? ins.opStr : "");
                w = Math.max(w, textPaint.measureText(line));
            }
            float blockW = Math.min(maxW, pad * 2 + w);
            b.extraMeasuredWidth = blockW;
            b.extraMeasuredHeight = h;
        }

        // 3) 按层级摆放 (每层内左对齐, X 直接从 edgeOff 开始)
        // 找最宽层级宽度作为 contentWidth
        float widestLevelW = 0f;
        Map<Integer, Float> levelWidths = new HashMap<>();
        for (Map.Entry<Integer, List<Long>> e : levelBlocks.entrySet()) {
            int level = e.getKey();
            List<Long> addrs = e.getValue();
            float lw = 0f;
            for (int i = 0; i < addrs.size(); i++) {
                ControlFlowAnalyzer.Block b = findBlock(addrs.get(i));
                if (b == null) continue;
                lw += b.extraMeasuredWidth;
            }
            lw += gapX * Math.max(0, addrs.size() - 1);
            levelWidths.put(level, lw);
            if (lw > widestLevelW) widestLevelW = lw;
        }

        float totalW = widestLevelW + edgeOff * 2 + dp(ctx, 16f);
        float levelH = dp(ctx, 60f);   // 块 + 间隔
        float totalH = (maxLevel + 1) * levelH + gapY + dp(ctx, 16f);
        if (totalW < dp(ctx, 280f)) totalW = dp(ctx, 280f);
        if (totalH < dp(ctx, 200f)) totalH = dp(ctx, 200f);
        contentWidth = totalW;
        contentHeight = totalH;

        // 4) 摆放每个 block
        for (Map.Entry<Integer, List<Long>> e : levelBlocks.entrySet()) {
            int level = e.getKey();
            List<Long> addrs = e.getValue();
            float layerW = levelWidths.get(level);
            // 每层 x0: 左对齐 (从 edgeOff 开始)
            float layerX0 = edgeOff + dp(ctx, 8f);
            float y = dp(ctx, 8f) + level * levelH;
            float x = layerX0;
            for (int i = 0; i < addrs.size(); i++) {
                long addr = addrs.get(i);
                ControlFlowAnalyzer.Block b = findBlock(addr);
                if (b == null) continue;
                RectF r = new RectF(x, y, x + b.extraMeasuredWidth, y + b.extraMeasuredHeight);
                blockRects.put(addr, r);
                x += b.extraMeasuredWidth + gapX;
            }
        }
        Log.i(TAG, "computeLayout contentW=" + contentWidth + " contentH=" + contentHeight
                + " maxLevel=" + maxLevel + " blocks=" + blockRects.size());
    }

    private int maxLevel() {
        int m = 0;
        for (int l : blockLevel.values()) if (l > m) m = l;
        return m;
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
            int w = resolveSize((int) dp(getContext(), 320f), widthMeasureSpec);
            int h = resolveSize((int) dp(getContext(), 240f), heightMeasureSpec);
            setMeasuredDimension(w, h);
            return;
        }
        int width = resolveSize((int) Math.ceil(contentWidth), widthMeasureSpec);
        int height = resolveSize((int) Math.ceil(contentHeight), heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (!didAutoFit && w > 0 && h > 0 && contentWidth > 0) {
            autoFit();
            didAutoFit = true;
            invalidate();
        }
    }

    /** 自适应缩放: 让 entry block 落在视图中央并 fit 到可视范围 */
    private void autoFit() {
        if (cfg == null || cfg.blocks == null || cfg.blocks.isEmpty()) return;
        ControlFlowAnalyzer.Block entry = null;
        for (ControlFlowAnalyzer.Block b : cfg.blocks) {
            if (b.isEntry) { entry = b; break; }
        }
        if (entry == null) entry = cfg.blocks.get(0);
        RectF er = blockRects.get(entry.startAddr);
        if (er == null) return;

        int vw = getWidth();
        int vh = getHeight();
        if (vw <= 0 || vh <= 0) return;

        // 选择合适的 scale
        float sX = (vw - dp(getContext(), 32f)) / contentWidth;
        float sY = (vh - dp(getContext(), 32f)) / contentHeight;
        float s = Math.min(sX, sY);
        if (s < MIN_SCALE) s = MIN_SCALE;
        if (s > 1.0f) s = 1.0f;   // 不要放大
        scale = s;
        minScale = Math.max(MIN_SCALE, s * 0.5f);

        // 偏移: 让 entry 块的中心在视图中心
        float entryCx = er.centerX();
        float entryCy = er.centerY();
        offsetX = vw / 2f - entryCx * scale;
        offsetY = vh / 2f - entryCy * scale;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        boolean a = scaleDetector.onTouchEvent(ev);
        boolean b = gestureDetector.onTouchEvent(ev);
        return a || b || super.onTouchEvent(ev);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        Context ctx = getContext();

        // 主题色 (跟随 day/night)
        int surface = ThemeUtils.colorSurface(ctx);
        int outline = ThemeUtils.colorOutline(ctx);
        int onSurface = ThemeUtils.colorOnSurface(ctx);
        c.drawColor(surface);

        if (cfg == null || cfg.blocks == null || cfg.blocks.isEmpty()) {
            textPaint.setColor(onSurface);
            textPaint.setTextSize(sp(ctx, 14f));
            String msg = "无控制流数据";
            float tw = textPaint.measureText(msg);
            c.drawText(msg, (getWidth() - tw) / 2f, getHeight() / 2f, textPaint);
            return;
        }

        // 背景网格 (轻)
        if (blockRects.isEmpty()) {
            // 还没布局, 退化为画一个提示
            textPaint.setColor(onSurface);
            textPaint.setTextSize(sp(ctx, 14f));
            String msg = "CFG 布局中...";
            float tw = textPaint.measureText(msg);
            c.drawText(msg, (getWidth() - tw) / 2f, getHeight() / 2f, textPaint);
            return;
        }

        // 应用平移/缩放
        c.save();
        c.translate(offsetX, offsetY);
        c.scale(scale, scale);

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
            drawBlock(c, b, r, surface, outline, onSurface);
        }

        c.restore();
    }

    private void drawBlock(Canvas c, ControlFlowAnalyzer.Block b, RectF r,
                           int surface, int outline, int onSurface) {
        Context ctx = getContext();
        int primary = ThemeUtils.colorPrimary(ctx);
        int onPrimary = ThemeUtils.colorOnPrimary(ctx);
        int error = ThemeUtils.colorError(ctx);

        // 背景
        int fill;
        int txtColor;
        if (b.isEntry) {
            fill = primary;
            txtColor = onPrimary;
        } else if (b.isExit) {
            fill = error;
            txtColor = Color.WHITE;
        } else {
            fill = surface;
            txtColor = onSurface;
        }
        blockFill.setColor(fill);
        c.drawRoundRect(r, dp(ctx, 6f), dp(ctx, 6f), blockFill);
        blockStroke.setColor(b.isEntry || b.isExit ? txtColor : outline);
        blockStroke.setStrokeWidth((b.isEntry || b.isExit) ? dp(ctx, 2f) : dp(ctx, 1f));
        c.drawRoundRect(r, dp(ctx, 6f), dp(ctx, 6f), blockStroke);

        // 标题
        float textX = r.left + dp(ctx, BLOCK_PAD_H_DP);
        float titleY = r.top + dp(ctx, BLOCK_PAD_V_DP) + dp(ctx, BLOCK_TITLE_H_DP) - dp(ctx, 4f);
        titlePaint.setColor(txtColor);
        titlePaint.setTextSize(sp(ctx, TITLE_TEXT_SP));
        String tag = (b.isEntry ? "▶ ENTRY " : (b.isExit ? "■ EXIT  " : "      BB "))
                + String.format(Locale.US, "0x%x", b.startAddr);
        c.drawText(tag, textX, titleY, titlePaint);

        // 指令
        float insY = titleY + dp(ctx, BLOCK_LINE_H_DP);
        float lineH = dp(ctx, BLOCK_LINE_H_DP);
        int branchColor = b.isEntry || b.isExit ? txtColor : ThemeUtils.colorPrimary(ctx);
        int normalColor = b.isEntry || b.isExit ? txtColor
                : ThemeUtils.colorOnSurfaceVariant(ctx);
        int maxLines = Math.min(8, b.instructions.size());
        textPaint.setTextSize(sp(ctx, TEXT_SIZE_DP));
        for (int i = 0; i < maxLines; i++) {
            DisassembledInstruction ins = b.instructions.get(i);
            String mn = ins.mnemonic != null ? ins.mnemonic : "";
            String op = ins.opStr != null ? ins.opStr : "";
            String line = String.format(Locale.US, "%-7s %s", mn, op);
            textPaint.setColor(isBranchMnemonic(mn) ? branchColor : normalColor);
            c.drawText(line, textX, insY + i * lineH, textPaint);
        }
        // 如果指令超过显示数, 加 "..." 提示
        if (b.instructions.size() > maxLines) {
            textPaint.setColor(normalColor);
            c.drawText("... +" + (b.instructions.size() - maxLines) + " 条",
                    textX, insY + maxLines * lineH, textPaint);
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
