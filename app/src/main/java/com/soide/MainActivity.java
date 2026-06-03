package com.soide;

import android.os.Bundle;
import android.util.Log;
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

    private static final String TAG = "MainActivity";
    private static final String STATE_SELECTED = "selectedItem";
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            setContentView(R.layout.activity_main);

            bottomNav = findViewById(R.id.bottom_navigation);
            if (bottomNav == null) {
                Log.e(TAG, "bottom_navigation not found in layout");
                return;
            }
            bottomNav.setOnItemSelectedListener(this::onNavSelected);

            int target = R.id.nav_home;
            if (savedInstanceState != null) {
                int sel = savedInstanceState.getInt(STATE_SELECTED, R.id.nav_home);
                if (sel != 0) target = sel;
            }
            bottomNav.setSelectedItemId(target);
        } catch (Throwable t) {
            Log.e(TAG, "onCreate failed", t);
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
        try {
            Fragment frag = createFragmentForId(item.getItemId());
            if (frag != null) {
                switchFragment(frag);
                return true;
            }
        } catch (Throwable t) {
            Log.e(TAG, "onNavSelected failed", t);
        }
        return false;
    }

    private Fragment createFragmentForId(int id) {
        if (id == R.id.nav_home) {
            return new HomeFragment();
        } else if (id == R.id.nav_parse) {
            return new ParseFragment();
        } else if (id == R.id.nav_demangle) {
            return new DemangleFragment();
        } else if (id == R.id.nav_history) {
            return new HistoryFragment();
        } else if (id == R.id.nav_tools) {
            return new ToolsFragment();
        } else if (id == R.id.nav_assembler) {
            return new AssemblerFragment();
        }
        return null;
    }

    private void switchFragment(Fragment frag) {
        try {
            FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
            tx.setReorderingAllowed(true);
            tx.replace(R.id.fragment_container, frag);
            tx.commit();
        } catch (Throwable t) {
            Log.e(TAG, "switchFragment failed", t);
        }
    }
}
