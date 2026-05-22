package com.Rixnar.NeverGiveUp;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.HapticFeedbackConstants;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class DashboardFragment extends Fragment {

    private TextView tvCredits, tvTimer, tvStatus;
    private TextView tvDashDaysGone, tvDashDaysWon, tvDashDaysFell, tvDashPercentageNum;
    private TextView tvDashCurrentStreak, tvDashLastStreak;
    private Button btnDashRedirectToStats;

    private SharedPreferences prefs;
    private Handler handler = new Handler();
    private Runnable refreshRunnable;
    private SharedPreferences.OnSharedPreferenceChangeListener prefsListener;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

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
        tvDashCurrentStreak = view.findViewById(R.id.tvDashCurrentStreak);
        tvDashLastStreak = view.findViewById(R.id.tvDashLastStreak);
        btnDashRedirectToStats = view.findViewById(R.id.btnDashRedirectToStats);

        Button btnUnlock = view.findViewById(R.id.btnUnlock);
        btnUnlock.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (BlockerService.isSocialUnlocked(getActivity())) {
                Toast.makeText(getActivity(), "Already activated.", Toast.LENGTH_SHORT).show();
                return;
            }

            int creditsNow = prefs.getInt("native_credits", 100);
            if (creditsNow >= 50) {
                prefs.edit()
                    .putInt("native_credits", creditsNow - 50)
                    .putBoolean("social_unlocked", true)
                    .putLong("social_unlock_expiry", System.currentTimeMillis() + (30 * 60 * 1000))
                    .apply();
                PersistenceHelper.saveToInternalStorage(getActivity());
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                }
                Toast.makeText(getActivity(), "30 minutes unlocked!", Toast.LENGTH_SHORT).show();
                calculatePureDashboardMetrics();
            } else {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    v.performHapticFeedback(HapticFeedbackConstants.REJECT);
                }
                Toast.makeText(getActivity(), "Need 50 credits!", Toast.LENGTH_SHORT).show();
            }
        });



        btnDashRedirectToStats.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (getActivity() == null) return;
            BottomNavigationView bottomNav = getActivity().findViewById(R.id.bottom_navigation);
            if (bottomNav != null) {
                bottomNav.setSelectedItemId(R.id.navigation_statistics);
            }
        });

        calculatePureDashboardMetrics();
        startRefreshLoop();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        PersistenceHelper.loadFromInternalStorage(getActivity());
        calculatePureDashboardMetrics();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (prefs == null && getActivity() != null) {
            prefs = getActivity().getSharedPreferences("relentless", Context.MODE_PRIVATE);
        }
        if (prefs != null && prefsListener == null) {
            prefsListener = (sharedPreferences, key) -> {
                if ("immutable_slips_pool".equals(key) || "tracking_init_date_string".equals(key) || "native_credits".equals(key)) {
                    if (getActivity() != null) {
                        calculatePureDashboardMetrics();
                    }
                }
            };
        }
        if (prefs != null && prefsListener != null) {
            prefs.registerOnSharedPreferenceChangeListener(prefsListener);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (prefs != null && prefsListener != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener);
        }
    }

    private void startRefreshLoop() {
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (getActivity() == null) return;

                int credits = prefs.getInt("native_credits", 100);
                tvCredits.setText(String.valueOf(credits));

                long expiry = prefs.getLong("social_unlock_expiry", 0);
                long now = System.currentTimeMillis();
                if (expiry > now && prefs.getBoolean("social_unlocked", false)) {
                    long secs = (expiry - now) / 1000;
                    long mins = secs / 60;
                    long s = secs % 60;
                    tvTimer.setText(mins + ":" + String.format("%02d", s));
                    tvTimer.setTextColor(ContextCompat.getColor(getActivity(), R.color.brand_green));
                } else {
                    tvTimer.setText("—");
                    tvTimer.setTextColor(ContextCompat.getColor(getActivity(), R.color.text_primary));
                }

                handler.postDelayed(this, 1000);
            }
        };
        handler.post(refreshRunnable);
    }

    private void calculatePureDashboardMetrics() {
        String todayString = dateFormat.format(System.currentTimeMillis());
        Set<String> slips = prefs.getStringSet("immutable_slips_pool", new HashSet<>());
        String initDateString = prefs.getString("tracking_init_date_string", null);

        if (initDateString == null || initDateString.trim().isEmpty()) {
            Calendar jan1 = Calendar.getInstance();
            jan1.set(Calendar.MONTH, Calendar.JANUARY);
            jan1.set(Calendar.DAY_OF_MONTH, 1);
            initDateString = dateFormat.format(jan1.getTime());
            prefs.edit().putString("tracking_init_date_string", initDateString).commit();
        }

        if (slips != null) {
            String earliest = initDateString;
            for (String s : slips) {
                if (s != null && s.compareTo(earliest) < 0) {
                    earliest = s;
                }
            }
            if (earliest.compareTo(todayString) > 0) earliest = todayString;
            if (!earliest.equals(initDateString)) {
                initDateString = earliest;
                prefs.edit().putString("tracking_init_date_string", initDateString).commit();
            }
        }

        Calendar initCal = Calendar.getInstance();
        Calendar todayCal = Calendar.getInstance();
        Calendar endOfYearCal = Calendar.getInstance();
        try {
            initCal.setTime(dateFormat.parse(initDateString));
            todayCal.setTime(dateFormat.parse(todayString));
        } catch (Exception ignored) {}

        endOfYearCal.set(initCal.get(Calendar.YEAR), Calendar.DECEMBER, 31, 23, 59, 59);
        long totalActivePoolWindowDays =
                TimeUnit.MILLISECONDS.toDays(endOfYearCal.getTimeInMillis() - initCal.getTimeInMillis()) + 1;
        if (totalActivePoolWindowDays <= 0) totalActivePoolWindowDays = 1;

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

        // Streak Calculation
        long currentStreak;
        long lastStreak = 0;
        long tempStreak = 0;
        Calendar streakCal = (Calendar) initCal.clone();
        while (dateFormat.format(streakCal.getTime()).compareTo(todayString) <= 0) {
            String dateStr = dateFormat.format(streakCal.getTime());
            if (slips.contains(dateStr)) {
                if (tempStreak > 0) lastStreak = tempStreak;
                tempStreak = 0;
            } else if (!dateStr.equals(todayString)) {
                tempStreak++;
            }
            streakCal.add(Calendar.DAY_OF_YEAR, 1);
        }
        currentStreak = tempStreak;

        tvDashCurrentStreak.setText(String.valueOf(currentStreak));
        tvDashLastStreak.setText(String.valueOf(lastStreak));

        long netScoreBalance = totalWinsCount - totalSlipsCount;
        float finalPercentage = ((float) netScoreBalance / (float) totalActivePoolWindowDays) * 100f;
        String percentSign = finalPercentage > 0 ? "+" : "";
        tvDashPercentageNum.setText(percentSign + String.format(Locale.US, "%.1f", finalPercentage) + "%");

        if (finalPercentage > 0) {
            tvDashPercentageNum.setTextColor(Color.parseColor("#4CAF50"));
        } else if (finalPercentage < 0) {
            tvDashPercentageNum.setTextColor(Color.parseColor("#E57373"));
        } else {
            tvDashPercentageNum.setTextColor(Color.WHITE);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (refreshRunnable != null) handler.removeCallbacks(refreshRunnable);
    }
}
