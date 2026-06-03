package com.soide.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.textview.MaterialTextView;
import com.soide.R;

/**
 * 通用文本内容 Fragment
 * 用于显示 ELF Header / 段 / 节区 / 字符串 / 重定位等单段文本
 */
public class TextContentFragment extends Fragment {

    private static final String ARG_CONTENT = "content";

    public static TextContentFragment newInstance(String content) {
        TextContentFragment f = new TextContentFragment();
        Bundle b = new Bundle();
        b.putString(ARG_CONTENT, content);
        f.setArguments(b);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_text_content, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MaterialTextView tv = view.findViewById(R.id.tv_content);
        String content = getArguments() != null ? getArguments().getString(ARG_CONTENT) : null;
        tv.setText(content != null ? content : "(无内容)");
    }
}