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
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;
import com.soide.R;
import com.soide.util.Demangler;

public class DemangleFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_demangle, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextInputEditText et = view.findViewById(R.id.et_input);
        MaterialButton btn = view.findViewById(R.id.btn_demangle);
        MaterialTextView tv = view.findViewById(R.id.tv_result);

        btn.setOnClickListener(v -> {
            String input = et.getText() != null ? et.getText().toString().trim() : "";
            if (input.isEmpty()) {
                Toast.makeText(requireContext(), "请输入符号", Toast.LENGTH_SHORT).show();
                return;
            }
            Demangler.Result r = Demangler.demangle(input);
            tv.setText(r.demangled);
            tv.setOnLongClickListener(v1 -> {
                ClipboardManager cm = (ClipboardManager) requireContext()
                        .getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("demangled", r.demangled));
                Toast.makeText(requireContext(), R.string.copied, Toast.LENGTH_SHORT).show();
                return true;
            });
        });
    }
}
