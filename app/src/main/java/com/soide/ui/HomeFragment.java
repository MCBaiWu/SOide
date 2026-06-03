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
        MaterialTextView tv = view.findViewById(R.id.tv_version);
        tv.setText("v" + BuildConfigHelper.versionName());

        MaterialButton btnParse = view.findViewById(R.id.btn_open_parse);
        MaterialButton btnDemangle = view.findViewById(R.id.btn_open_demangle);
        MaterialButton btnTools = view.findViewById(R.id.btn_open_tools);
        MaterialButton btnAsm = view.findViewById(R.id.btn_open_assembler);

        btnParse.setOnClickListener(v -> navigate(R.id.nav_parse));
        btnDemangle.setOnClickListener(v -> navigate(R.id.nav_demangle));
        btnTools.setOnClickListener(v -> navigate(R.id.nav_tools));
        btnAsm.setOnClickListener(v -> navigate(R.id.nav_assembler));
    }

    private void navigate(int id) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).findViewById(R.id.bottom_navigation)
                    .setSelectedItemId(id);
        }
    }
}
