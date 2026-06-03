package com.soide.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.soide.MainActivity;
import com.soide.R;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";

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
        Log.i(TAG, "onViewCreated");

        try {
            TextView tv = view.findViewById(R.id.tv_version);
            if (tv != null) {
                tv.setText("v" + BuildConfigHelper.versionName());
            }
        } catch (Throwable t) {
            Log.e(TAG, "set version failed", t);
        }

        wireButton(view, R.id.btn_open_parse,     R.id.nav_parse);
        wireButton(view, R.id.btn_open_demangle,  R.id.nav_demangle);
        wireButton(view, R.id.btn_open_tools,     R.id.nav_tools);
        wireButton(view, R.id.btn_open_assembler, R.id.nav_assembler);
    }

    private void wireButton(View root, int buttonId, int navId) {
        try {
            Button btn = root.findViewById(buttonId);
            if (btn == null) {
                Log.w(TAG, "button not found: " + buttonId);
                return;
            }
            btn.setOnClickListener(v -> navigate(navId));
        } catch (Throwable t) {
            Log.e(TAG, "wireButton failed for " + buttonId, t);
        }
    }

    private void navigate(int navId) {
        try {
            if (getActivity() instanceof MainActivity) {
                BottomNavigationView nav = ((MainActivity) getActivity())
                        .findViewById(R.id.bottom_navigation);
                if (nav != null) nav.setSelectedItemId(navId);
            }
        } catch (Throwable t) {
            Log.e(TAG, "navigate failed", t);
        }
    }
}
