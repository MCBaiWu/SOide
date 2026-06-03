package com.soide.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import com.soide.util.BaseConverter;

public class ToolsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tools, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextInputEditText et = view.findViewById(R.id.et_input);

        // 由于 include 中的 view 都用同一个 id 会被覆盖，我们直接用 include 中的 row 并查找
        View rowBin = view.findViewById(R.id.row_bin);
        View rowOct = view.findViewById(R.id.row_oct);
        View rowDec = view.findViewById(R.id.row_dec);
        View rowHex = view.findViewById(R.id.row_hex);

        MaterialTextView lblBin = rowBin.findViewById(R.id.tv_label);
        MaterialTextView lblOct = rowOct.findViewById(R.id.tv_label);
        MaterialTextView lblDec = rowDec.findViewById(R.id.tv_label);
        MaterialTextView lblHex = rowHex.findViewById(R.id.tv_label);
        lblBin.setText(R.string.base_bin);
        lblOct.setText(R.string.base_oct);
        lblDec.setText(R.string.base_dec);
        lblHex.setText(R.string.base_hex);

        MaterialTextView valBin = rowBin.findViewById(R.id.tv_value);
        MaterialTextView valOct = rowOct.findViewById(R.id.tv_value);
        MaterialTextView valDec = rowDec.findViewById(R.id.tv_value);
        MaterialTextView valHex = rowHex.findViewById(R.id.tv_value);

        MaterialButton btnBin = rowBin.findViewById(R.id.btn_copy);
        MaterialButton btnOct = rowOct.findViewById(R.id.btn_copy);
        MaterialButton btnDec = rowDec.findViewById(R.id.btn_copy);
        MaterialButton btnHex = rowHex.findViewById(R.id.btn_copy);

        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                BaseConverter.Result r = BaseConverter.convert(s.toString());
                if (r.ok) {
                    valBin.setText(r.binary);
                    valOct.setText(r.octal);
                    valDec.setText(r.decimal);
                    valHex.setText(r.hex);
                    btnBin.setEnabled(true);
                    btnOct.setEnabled(true);
                    btnDec.setEnabled(true);
                    btnHex.setEnabled(true);
                } else {
                    valBin.setText(r.error != null ? r.error : "");
                    valOct.setText("");
                    valDec.setText("");
                    valHex.setText("");
                    btnBin.setEnabled(false);
                    btnOct.setEnabled(false);
                    btnDec.setEnabled(false);
                    btnHex.setEnabled(false);
                }
            }
        });

        btnBin.setOnClickListener(v -> copy("binary", valBin.getText().toString()));
        btnOct.setOnClickListener(v -> copy("octal", valOct.getText().toString()));
        btnDec.setOnClickListener(v -> copy("decimal", valDec.getText().toString()));
        btnHex.setOnClickListener(v -> copy("hex", valHex.getText().toString()));
    }

    private void copy(String label, String value) {
        if (value == null || value.isEmpty()) return;
        ClipboardManager cm = (ClipboardManager) requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, value));
        Toast.makeText(requireContext(), R.string.copied, Toast.LENGTH_SHORT).show();
    }
}
