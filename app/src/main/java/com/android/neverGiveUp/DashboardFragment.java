package com.android.neverGiveUp;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class DashboardFragment extends Fragment {

    static {
        System.loadLibrary("relentless");
    }

    private TextView tvCredits, tvTimer, tvStatus;
    private TextView tvDashDaysGone, tvDashDaysWon, tvDashDaysFell, tvDashPercentageNum;
    private Button btnDashRedirectToStats;

    private SharedPreferences prefs;
    private Handler handler = new Handler();
    private Runnable refreshRunnable;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private native int nativeGetCredits();
    private native long nativeGetUnlockSecondsRemaining();
    private native boolean nativePurchaseSocialPass();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);
        prefs = getActivity().getSharedPreferences("relentless", Context.MODE_PRIVATE);

        // Core display layer view registration
        tvCredits = view.findViewById(R.id.tvCredits);
        tvTimer = view.findViewById(R.id.tvTimer);
        tvStatus = view.findViewById(R.id.tvStatus);

        // Metrics row layout mappings
        tvDashDaysGone = view.findViewById(R.id.tvDashDaysGone);
        tvDashDaysWon = view.findViewById(R.id.tvDashDaysWon);
        tvDashDaysFell = view.findViewById(R.id.tvDashDaysFell);
        tvDashPercentageNum = view.findViewById(R.id.tvDashPercentageNum);
        btnDashRedirectToStats = view.findViewById(R.id.btnDashRedirectToStats);

        // Native JNI spending implementation button callback
        Button btnUnlock = view.findViewById(R.id.btnUnlock);
        btnUnlock.setOnClickListener(v -> {
            if (nativePurchaseSocialPass()) {
                BlockerService.socialUnlocked = true;
                BlockerService.unlockExpiryMs = System.currentTimeMillis() + (30 * 60 * 1000);
                Toast.makeText(getActivity(), "30 minutes unlocked!", Toast.LENGTH_SHORT).show();
                calculatePureDashboardMetrics(); // Instantly refresh calculations
            } else {
                Toast.makeText(getActivity(), "Insufficient credits!", Toast.LENGTH_SHORT).show();
            }
        });

        Button btnRevoke = view.findViewById(R.id.btnRevoke);
        btnRevoke.setOnClickListener(v -> {
            Toast.makeText(getActivity(), "Use sidebar menu to revoke admin", Toast.LENGTH_SHORT).show();
        });

        // FIXED: Explicitly maps transactions and updates bottom navbar highlight without lookups
        btnDashRedirectToStats.setOnClickListener(v -> {
            if (getActivity() != null) {
                // 1. Swap the Fragment UI inside the main frame container
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new StatisticsFragment())
                        .addToBackStack(null)
                        .commit();

                // 2. Locate navigation component layout explicitly via your true resource ID
                View navView = getActivity().findViewById(R.id.nav_view);
                if (navView instanceof com.google.android.material.bottomnavigation.BottomNavigationView) {
                    com.google.android.material.bottomnavigation.BottomNavigationView bottomNav =
                            (com.google.android.material.bottomnavigation.BottomNavigationView) navView;

                    // Force navigation selection directly using your menu file's exact target ID
                    bottomNav.setSelectedItemId(R.id.navigation_statistics);
                }
            }
        });

        startRefreshLoop();
        return view;
    }

    // FIXED: Forces metrics to parse SharedPreferences ledger every single time screen regains focus
    @Override
    public void onResume() {
        super.onResume();
        // This tells the app: "Hey, whenever I look at this screen, refresh the numbers!"
        calculatePureDashboardMetrics();
    }

    private void startRefreshLoop() {
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (getActivity() == null) return;

                int credits = nativeGetCredits();
                tvCredits.setText(String.valueOf(credits));

                long secs = nativeGetUnlockSecondsRemaining();
                if (secs > 0) {
                    long mins = secs / 60;
                    long s = secs % 60;
                    tvTimer.setText(mins + ":" + String.format("%02d", s));
                    tvTimer.setTextColor(Color.parseColor("#4CAF50"));
                } else {
                    tvTimer.setText("—");
                    tvTimer.setTextColor(Color.WHITE);
                }

                handler.postDelayed(this, 1000);
            }
        };
        handler.post(refreshRunnable);
    }

    private void calculatePureDashboardMetrics() {
        String todayString = dateFormat.format(System.currentTimeMillis());
        String initDateString = prefs.getString("tracking_init_date_string", todayString);
        Set<String> slips = prefs.getStringSet("immutable_slips_pool", new HashSet<>());

        Calendar initCal = Calendar.getInstance();
        Calendar todayCal = Calendar.getInstance();
        try {
            initCal.setTime(dateFormat.parse(initDateString));
            todayCal.setTime(dateFormat.parse(todayString));
        } catch (Exception ignored) {}

        long diffInMillis = todayCal.getTimeInMillis() - initCal.getTimeInMillis();
        long totalDaysGone = TimeUnit.MILLISECONDS.toDays(diffInMillis) + 1;
        if (totalDaysGone <= 0) totalDaysGone = 1;

        long totalWinsCount = 0;
        long totalSlipsCount = 0;

        Calendar loopCal = (Calendar) initCal.clone();

        while (dateFormat.format(loopCal.getTime()).compareTo(todayString) <= 0) {
            String loopStr = dateFormat.format(loopCal.getTime());
            if (slips.contains(loopStr)) {
                totalSlipsCount++;
            } else if (!loopStr.equals(todayString)) {
                totalWinsCount++;
            }
            loopCal.add(Calendar.DAY_OF_YEAR, 1);
        }

        tvDashDaysGone.setText(String.valueOf(totalDaysGone));
        tvDashDaysWon.setText(String.valueOf(totalWinsCount));
        tvDashDaysFell.setText(String.valueOf(totalSlipsCount));

        long netScoreBalance = totalWinsCount - totalSlipsCount;

        if (netScoreBalance > 0) {
            tvDashPercentageNum.setText("+" + netScoreBalance);
            tvDashPercentageNum.setTextColor(Color.parseColor("#4CAF50"));
        } else if (netScoreBalance < 0) {
            tvDashPercentageNum.setText(String.valueOf(netScoreBalance));
            tvDashPercentageNum.setTextColor(Color.parseColor("#E57373"));
        } else {
            tvDashPercentageNum.setText("0");
            tvDashPercentageNum.setTextColor(Color.WHITE);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (refreshRunnable != null) handler.removeCallbacks(refreshRunnable);
    }
}