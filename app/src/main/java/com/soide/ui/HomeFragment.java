package com.soide.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;
import com.soide.MainActivity;
import com.soide.R;

public class HomeFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        try {
            MaterialTextView tv = view.findViewById(R.id.tv_version);
            if (tv != null) {
                tv.setText("v" + BuildConfigHelper.versionName());
            }

            MaterialButton btnParse = view.findViewById(R.id.btn_open_parse);
            MaterialButton btnDemangle = view.findViewById(R.id.btn_open_demangle);
            MaterialButton btnTools = view.findViewById(R.id.btn_open_tools);
            MaterialButton btnAsm = view.findViewById(R.id.btn_open_assembler);

            if (btnParse != null) btnParse.setOnClickListener(v -> navigate(R.id.nav_parse));
            if (btnDemangle != null) btnDemangle.setOnClickListener(v -> navigate(R.id.nav_demangle));
            if (btnTools != null) btnTools.setOnClickListener(v -> navigate(R.id.nav_tools));
            if (btnAsm != null) btnAsm.setOnClickListener(v -> navigate(R.id.nav_assembler));
        } catch (Throwable t) {
            android.util.Log.e("HomeFragment", "init failed", t);
        }
    }

    private void navigate(int id) {
        try {
            if (getActivity() instanceof MainActivity) {
                BottomNavigationView nav = ((MainActivity) getActivity())
                        .findViewById(R.id.bottom_navigation);
                if (nav != null) nav.setSelectedItemId(id);
            }
        } catch (Throwable ignored) {}
    }
}
