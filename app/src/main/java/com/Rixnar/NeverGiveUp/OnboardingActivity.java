package com.Rixnar.NeverGiveUp;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.app.DatePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class OnboardingActivity extends AppCompatActivity {

    private static final String CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";
    private static final int KEY_LENGTH = 30;

    private SharedPreferences prefs;
    private String generatedKey = null;
    private long lockDurationMs = 0;

    private View stepDuration, stepKey, stepVerify, stepDone;
    private TextView tvStep1Info, tvStep2Info, tvStep3Info;
    private Button btnSet1Day, btnSet7Days, btnSet30Days, btnSetCustom;
    private ImageView ivQRCode;
    private EditText etVerifyCode;
    private Button btnVerify, btnShareKey, btnSaveKey, btnStartApp;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Auto-detect and follow system theme
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        super.onCreate(savedInstanceState);

        PersistenceHelper.loadFromInternalStorage(this);
        prefs = getSharedPreferences("relentless", MODE_PRIVATE);
        if (prefs.getBoolean("onboarding_complete", false)) {
            launchMainApp();
            return;
        }

        enableFullscreen();
        setContentView(R.layout.activity_onboarding);

        initViews();
        
        // Restore state or start at step 1
        String tempKey = prefs.getString("onboarding_key_temp", null);
        long tempDuration = prefs.getLong("onboarding_duration_temp", 0);
        if (tempKey != null && tempDuration > 0) {
            generatedKey = tempKey;
            lockDurationMs = tempDuration;
            showStep2();
        } else {
            showStep1();
        }
    }

    private void enableFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.statusBars());
    }

    private void initViews() {
        stepDuration = findViewById(R.id.stepDuration);
        stepKey = findViewById(R.id.stepKey);
        stepVerify = findViewById(R.id.stepVerify);
        stepDone = findViewById(R.id.stepDone);

        tvStep1Info = findViewById(R.id.tvStep1Info);
        tvStep2Info = findViewById(R.id.tvStep2Info);
        tvStep3Info = findViewById(R.id.tvStep3Info);
        ivQRCode = findViewById(R.id.ivQRCode);
        etVerifyCode = findViewById(R.id.etVerifyCode);
        btnVerify = findViewById(R.id.btnVerify);
        btnShareKey = findViewById(R.id.btnShareKey);
        btnSaveKey = findViewById(R.id.btnSaveKey);
        btnStartApp = findViewById(R.id.btnStartApp);

        btnSet1Day = findViewById(R.id.btnSet1Day);
        btnSet7Days = findViewById(R.id.btnSet7Days);
        btnSet30Days = findViewById(R.id.btnSet30Days);
        btnSetCustom = findViewById(R.id.btnSetCustom);

        btnSet1Day.setOnClickListener(v -> setDuration(1));
        btnSet7Days.setOnClickListener(v -> setDuration(7));
        btnSet30Days.setOnClickListener(v -> setDuration(30));
        btnSetCustom.setOnClickListener(v -> showCustomDatePicker());

        btnShareKey.setOnClickListener(v -> shareKey());
        btnSaveKey.setOnClickListener(v -> {
            // Copy to clipboard
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Relentless Master Key", generatedKey);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Key copied to clipboard!", Toast.LENGTH_LONG).show();
            showStep3();
        });

        btnVerify.setOnClickListener(v -> verifyCode());
        btnStartApp.setOnClickListener(v -> launchMainApp());
    }

    private void showStep1() {
        hideAllSteps();
        stepDuration.setVisibility(View.VISIBLE);
    }

    private void showStep2() {
        hideAllSteps();
        stepKey.setVisibility(View.VISIBLE);
        
        if (generatedKey == null) {
            generatedKey = generateKey();
            prefs.edit()
                .putString("onboarding_key_temp", generatedKey)
                .putLong("onboarding_duration_temp", lockDurationMs)
                .apply();
            PersistenceHelper.saveToInternalStorage(this);
        }
        
        String keyText = "This is shown exactly ONCE. Store it somewhere safe.\n\n" + generatedKey;
        tvStep2Info.setText(keyText);
        
        // Generate QR code
        try {
            QRCodeWriter writer = new QRCodeWriter();
            int size = 512;
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            
            BitMatrix matrix = writer.encode(generatedKey, BarcodeFormat.QR_CODE, size, size, hints);
            
            int width = matrix.getWidth();
            int height = matrix.getHeight();
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    pixels[y * width + x] = matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
                }
            }
            
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            ivQRCode.setImageBitmap(bitmap);
        } catch (WriterException e) {
            tvStep2Info.setText("KEY: " + generatedKey + "\n\n(Enable camera for QR code generation)");
        }
    }

    private void showStep3() {
        hideAllSteps();
        stepVerify.setVisibility(View.VISIBLE);
        tvStep3Info.setText("Enter the master key to verify you've saved it correctly:");
    }


    private void hideAllSteps() {
        stepDuration.setVisibility(View.GONE);
        stepKey.setVisibility(View.GONE);
        stepVerify.setVisibility(View.GONE);
        if (stepDone != null) stepDone.setVisibility(View.GONE);
    }

    private void setDuration(int days) {
        lockDurationMs = days * 24L * 60L * 60L * 1000L;
        prefs.edit().putLong("onboarding_duration_temp", lockDurationMs).apply();
        checkAndRequestAdmin();
    }

    private void checkAndRequestAdmin() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(this, RelentlessAdminReceiver.class);
        if (!dpm.isAdminActive(adminComponent)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Admin Protection")
                    .setMessage("Rixnar requires Device Administrator privileges to prevent the app from being uninstalled during your lock period.\n\nClick 'Activate' on the next screen.")
                    .setPositiveButton("Activate Admin", (dialog, which) -> {
                        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
                        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                "Rixnar needs admin to prevent uninstall.");
                        startActivityForResult(intent, 100);
                    })
                    .setCancelable(false)
                    .show();
        } else {
            showStep2();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100) {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName adminComponent = new ComponentName(this, RelentlessAdminReceiver.class);
            if (dpm.isAdminActive(adminComponent)) {
                showStep2();
            } else {
                Toast.makeText(this, "Admin permission is mandatory.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showCustomDatePicker() {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog dp = new DatePickerDialog(this,
            (view, year, month, day) -> {
                Calendar selected = Calendar.getInstance();
                selected.set(year, month, day, 23, 59, 59);
                long diff = selected.getTimeInMillis() - System.currentTimeMillis();
                if (diff < 0) {
                    Toast.makeText(this, "You cannot select a date in the past.", Toast.LENGTH_SHORT).show();
                } else {
                    lockDurationMs = diff;
                    prefs.edit().putLong("onboarding_duration_temp", lockDurationMs).apply();
                    checkAndRequestAdmin();
                }
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        );
        dp.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dp.setTitle("Set Lock Duration End Date");
        dp.show();
    }

    private String generateKey() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(KEY_LENGTH);
        for (int i = 0; i < KEY_LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return input;
        }
    }

    private void shareKey() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Rixnar Master Key");
        shareIntent.putExtra(Intent.EXTRA_TEXT, "My Rixnar Master Key:\n" + generatedKey + "\n\nStore this safely!");
        startActivity(Intent.createChooser(shareIntent, "Share Key Via"));
    }

    private void verifyCode() {
        String entered = etVerifyCode.getText().toString();
        if (entered.equals(generatedKey)) {
            prefs.edit()
                .putString("key_hash", hash(generatedKey))
                .putLong("lock_end_date", lockDurationMs > 0 ? (System.currentTimeMillis() + lockDurationMs) : 0)
                .putInt("native_credits", 100)
                .putBoolean("onboarding_complete", true)
                .putString("tracking_init_date_string", dateFormat.format(System.currentTimeMillis()))
                .remove("onboarding_key_temp")
                .remove("onboarding_duration_temp")
                .apply();
            
            PersistenceHelper.saveToInternalStorage(this);
            launchMainApp();
        } else {
            Toast.makeText(this, "Incorrect key. Please try again.", Toast.LENGTH_SHORT).show();
            etVerifyCode.setText("");
        }
    }

    private void launchMainApp() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
