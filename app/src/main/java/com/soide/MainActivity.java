package com.soide;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textview.MaterialTextView;
import com.soide.elf.ElfFile;
import com.soide.elf.ElfParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    private static final int BUFFER_SIZE = 4096;

    private MaterialTextView tvFilePath;
    private MaterialTextView tvResult;
    private MaterialButton btnSelectFile;
    private LinearProgressIndicator progressBar;

    private final ActivityResultLauncher<Intent> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        handleSelectedFile(uri);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvFilePath = findViewById(R.id.tv_file_path);
        tvResult = findViewById(R.id.tv_result);
        btnSelectFile = findViewById(R.id.btn_select_file);
        progressBar = findViewById(R.id.progress_bar);

        btnSelectFile.setOnClickListener(v -> openFilePicker());
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        filePickerLauncher.launch(intent);
    }

    private void handleSelectedFile(Uri uri) {
        String fileName = getFileName(uri);
        tvFilePath.setText(fileName);
        tvResult.setText("");
        showProgress(true);

        new Thread(() -> {
            try {
                File tempFile = copyToTempFile(uri);

                ElfParser parser = new ElfParser();
                ElfFile elfFile = parser.parse(tempFile);

                String result = elfFile.toString();

                runOnUiThread(() -> {
                    tvResult.setText(result);
                    showProgress(false);
                    Toast.makeText(this, R.string.parse_success, Toast.LENGTH_SHORT).show();
                });

                tempFile.delete();

            } catch (Exception e) {
                runOnUiThread(() -> {
                    showProgress(false);
                    String errorMsg = getString(R.string.parse_failed) + ": " + e.getMessage();
                    tvResult.setText(errorMsg);
                    Toast.makeText(this, R.string.parse_failed, Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private File copyToTempFile(Uri uri) throws Exception {
        File tempFile = File.createTempFile("soide_", ".tmp", getCacheDir());
        try (InputStream is = getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(tempFile)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }
        return tempFile;
    }

    private String getFileName(Uri uri) {
        String name = null;
        try (android.database.Cursor cursor = getContentResolver()
                .query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    name = cursor.getString(idx);
                }
            }
        } catch (Exception ignored) {
        }
        return name != null ? name : uri.getLastPathSegment();
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnSelectFile.setEnabled(!show);
        if (show) {
            progressBar.setIndeterminate(true);
        }
    }
}