package com.android.neverGiveUp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class CreditGateActivity extends AppCompatActivity {

    static { System.loadLibrary("relentless"); }

    public native int nativeGetCredits();
    public native boolean nativePurchaseSocialPass();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_credit_gate);

        TextView creditText = findViewById(R.id.creditStatusText);
        Button unlockButton = findViewById(R.id.btnUnlockSocial);

        int credits = nativeGetCredits();
        creditText.setText(String.valueOf(credits));

        if (credits < 50) {
            unlockButton.setEnabled(false);
            unlockButton.setText("Not enough credits");
            unlockButton.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF636366));
        }

        unlockButton.setOnClickListener(v -> {
            if (nativePurchaseSocialPass()) {
                BlockerService.socialUnlocked = true;
                BlockerService.unlockExpiryMs = System.currentTimeMillis() + (30 * 60 * 1000);
                Toast.makeText(this, "30 minutes unlocked.", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
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