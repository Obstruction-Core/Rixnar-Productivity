package com.Rixnar.NeverGiveUp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class CreditGateActivity extends AppCompatActivity {

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableFullscreen();
        setContentView(R.layout.activity_credit_gate);

        PersistenceHelper.loadFromInternalStorage(this);
        prefs = getSharedPreferences("relentless", MODE_PRIVATE);

        TextView creditText = findViewById(R.id.creditStatusText);
        Button unlockButton = findViewById(R.id.btnUnlockSocial);
        Button exitButton = findViewById(R.id.btnExitGate);

        int credits = prefs.getInt("native_credits", 100);
        creditText.setText(String.valueOf(credits));

        if (BlockerService.isSocialUnlocked(this)) {
            finish();
            return;
        }

        if (credits < 50) {
            unlockButton.setEnabled(false);
            unlockButton.setText("Need 50 credits");
            unlockButton.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF636366));
        }

        exitButton.setOnClickListener(v -> goHome());

        unlockButton.setOnClickListener(v -> {
            if (BlockerService.isSocialUnlocked(this)) {
                Toast.makeText(this, "Already activated.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            int currentCredits = prefs.getInt("native_credits", 100);
            if (currentCredits >= 50) {
                prefs.edit()
                    .putInt("native_credits", currentCredits - 50)
                    .putBoolean("social_unlocked", true)
                    .putLong("social_unlock_expiry", System.currentTimeMillis() + (30 * 60 * 1000))
                    .apply();
                PersistenceHelper.saveToInternalStorage(this);
                Toast.makeText(this, "30 minutes unlocked.", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "Insufficient credits.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void enableFullscreen() {
        setShowWhenLocked(true);
        setTurnScreenOn(true);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;

        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.statusBars());
    }

    private void goHome() {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_HOME);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }

    @Override
    public void onBackPressed() { goHome(); }
}
