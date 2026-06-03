package com.soide;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.soide.ui.AssemblerFragment;
import com.soide.ui.DemangleFragment;
import com.soide.ui.HistoryFragment;
import com.soide.ui.HomeFragment;
import com.soide.ui.ParseFragment;
import com.soide.ui.ToolsFragment;

public class MainActivity extends AppCompatActivity {

    private static final String STATE_SELECTED = "selectedItem";
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(this::onNavSelected);

        if (savedInstanceState != null) {
            int sel = savedInstanceState.getInt(STATE_SELECTED, R.id.nav_home);
            bottomNav.setSelectedItemId(sel);
        } else {
            bottomNav.setSelectedItemId(R.id.nav_home);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (bottomNav != null) {
            outState.putInt(STATE_SELECTED, bottomNav.getSelectedItemId());
        }
    }

    private boolean onNavSelected(@NonNull MenuItem item) {
        Fragment frag = null;
        int id = item.getItemId();
        if (id == R.id.nav_home) {
            frag = new HomeFragment();
        } else if (id == R.id.nav_parse) {
            frag = new ParseFragment();
        } else if (id == R.id.nav_demangle) {
            frag = new DemangleFragment();
        } else if (id == R.id.nav_history) {
            frag = new HistoryFragment();
        } else if (id == R.id.nav_tools) {
            frag = new ToolsFragment();
        } else if (id == R.id.nav_assembler) {
            frag = new AssemblerFragment();
        }
        if (frag != null) {
            switchFragment(frag);
            return true;
        }
        return false;
    }

    private void switchFragment(Fragment frag) {
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        tx.setReorderingAllowed(true);
        tx.replace(R.id.fragment_container, frag);
        tx.commit();
    }
}
