package com.soide.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.soide.R;
import com.soide.elf.ControlFlowAnalyzer;

/**
 * 函数详情 - 控制流 tab
 * v1.4.3 重写为自定义 Canvas CFG 视图，块+边可视化，色按边类型：
 *   - 真分支 (条件成立): 绿
 *   - 假分支 (条件不成立): 红
 *   - 默认 (无跳转/顺序/call): 蓝
 *   边通过左右侧通道走，绝不穿块。
 */
public class ControlFlowTabFragment extends Fragment {

    private static final String ARG_KEY = "key";

    public static ControlFlowTabFragment newInstance(FuncDetailData d) {
        ControlFlowTabFragment f = new ControlFlowTabFragment();
        Bundle b = new Bundle();
        b.putString(ARG_KEY, FuncDetailHost.put(d));
        f.setArguments(b);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_control_flow_tab, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView tvCount = view.findViewById(R.id.tv_count);
        NestedScrollView scroll = view.findViewById(R.id.scroll);
        CfgCanvasView canvas = view.findViewById(R.id.cfg_canvas);

        FuncDetailData d = FuncDetailHost.loadData(getArguments() != null ? getArguments().getString(ARG_KEY) : null);
        if (d == null || d.function == null) {
            tvCount.setText("控制流");
            return;
        }
        ControlFlowAnalyzer.CFG cfg = ControlFlowAnalyzer.build(d.function.instructions);
        int condEdges = 0;
        int uncond = 0;
        for (var b : cfg.blocks) for (var e : b.successors) {
            if (e.kind == ControlFlowAnalyzer.EdgeKind.TRUE_BRANCH
                    || e.kind == ControlFlowAnalyzer.EdgeKind.FALSE_BRANCH) condEdges++;
            else uncond++;
        }
        tvCount.setText(String.format(java.util.Locale.US,
                "控制流  ·  %d 个基本块  ·  %d 条件边  ·  %d 默认边",
                cfg.blocks.size(), condEdges, uncond));
        canvas.setCfg(cfg);
    }
}
