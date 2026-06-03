package com.soide.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.soide.R;

/**
 * 函数详情 - 汇编指令 tab
 */
public class AsmTabFragment extends Fragment {

    private static final String ARG_PATH = "data_path";

    public static AsmTabFragment newInstance(FuncDetailData d) {
        AsmTabFragment f = new AsmTabFragment();
        Bundle b = new Bundle();
        b.putString(ARG_PATH, FuncDetailHost.putData(d));
        f.setArguments(b);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_asm_tab, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FuncDetailData d = FuncDetailHost.loadData(getArguments() != null ? getArguments().getString(ARG_PATH) : null);
        RecyclerView rv = view.findViewById(R.id.rv_asm);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(new AsmAdapter(d != null && d.function != null ? d.function.instructions : null));
    }
}
