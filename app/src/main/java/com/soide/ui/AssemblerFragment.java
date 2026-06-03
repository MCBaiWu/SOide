package com.soide.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;
import com.soide.R;
import com.soide.util.Assembler;

public class AssemblerFragment extends Fragment {

    private int currentMode = Assembler.MODE_ARM;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_assembler, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TabLayout tab = view.findViewById(R.id.tab_mode);
        tab.addTab(tab.newTab().setText(R.string.asm_mode_arm));
        tab.addTab(tab.newTab().setText(R.string.asm_mode_thumb));

        TextInputEditText et = view.findViewById(R.id.et_input);
        MaterialButton btn = view.findViewById(R.id.btn_assemble);
        MaterialTextView tv = view.findViewById(R.id.tv_result);

        tab.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentMode = tab.getPosition() == 0 ? Assembler.MODE_ARM : Assembler.MODE_THUMB;
            }

            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        btn.setOnClickListener(v -> {
            String input = et.getText() != null ? et.getText().toString().trim() : "";
            if (input.isEmpty()) {
                Toast.makeText(requireContext(), "请输入指令", Toast.LENGTH_SHORT).show();
                return;
            }
            Assembler.Result r = Assembler.assemble(input, currentMode);
            if (r.ok) {
                tv.setText(r.hex + "\n\n(" + (r.bytes.length * 8) + " bits, " + r.bytes.length + " bytes)");
                tv.setOnLongClickListener(v1 -> {
                    ClipboardManager cm = (ClipboardManager) requireContext()
                            .getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("asm", r.hex));
                    Toast.makeText(requireContext(), R.string.copied, Toast.LENGTH_SHORT).show();
                    return true;
                });
            } else {
                tv.setText(getString(R.string.asm_failed) + ": " + r.error);
                tv.setOnLongClickListener(null);
            }
        });
    }
}
