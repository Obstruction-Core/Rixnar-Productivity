package com.android.neverGiveUp;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.flexbox.FlexboxLayout;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import android.content.res.Resources;
import android.widget.DatePicker;

public class StatisticsFragment extends Fragment {

    private SlipMeterView slipMeterCircle;
    private LinearLayout cardLaunchCalendar;
    private FlexboxLayout calendarFlexboxContainer;
    private TextView tvActiveDateDisplay, tvMonthYearHeader, tvMeterPercentage, tvMeterLabel, tvSelectedDateLabel, tvSelectedDateStatus;
    private Button btnCommitSlip;

    private SharedPreferences prefs;
    private Calendar currentMonthCalendar;
    private Calendar selectedDayCalendar;
    private String selectedDateString;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat humanDateFormat = new SimpleDateFormat("MMMM dd, yyyy", Locale.US);

    private Handler flashHandler = new Handler();
    private float flashAlpha = 1.0f;
    private boolean flashDescending = true;
    private Runnable flashRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_statistics, container, false);
        prefs = getActivity().getSharedPreferences("relentless", Context.MODE_PRIVATE);

        // Track user initialization baseline setup context
        if (!prefs.contains("tracking_init_date_string")) {
            String initialDate = dateFormat.format(System.currentTimeMillis());
            prefs.edit().putString("tracking_init_date_string", initialDate).apply();
        }

        cardLaunchCalendar = view.findViewById(R.id.cardLaunchCalendar);
        calendarFlexboxContainer = view.findViewById(R.id.calendarFlexboxContainer);
        tvActiveDateDisplay = view.findViewById(R.id.tvActiveDateDisplay);
        tvMonthYearHeader = view.findViewById(R.id.tvMonthYearHeader);
        slipMeterCircle = view.findViewById(R.id.slipMeterCircle);
        tvMeterPercentage = view.findViewById(R.id.tvMeterPercentage);
        tvMeterLabel = view.findViewById(R.id.tvMeterLabel);
        tvSelectedDateLabel = view.findViewById(R.id.tvSelectedDateLabel);
        tvSelectedDateStatus = view.findViewById(R.id.tvSelectedDateStatus);
        btnCommitSlip = view.findViewById(R.id.btnCommitSlip);

        currentMonthCalendar = Calendar.getInstance();
        selectedDayCalendar = Calendar.getInstance();
        selectedDateString = dateFormat.format(selectedDayCalendar.getTime());
        tvActiveDateDisplay.setText(humanDateFormat.format(selectedDayCalendar.getTime()));

        cardLaunchCalendar.setOnClickListener(v -> showSafeDatePickerDialog());

        btnCommitSlip.setOnClickListener(v -> {
            Set<String> slips = new HashSet<>(prefs.getStringSet("immutable_slips_pool", new HashSet<>()));
            slips.add(selectedDateString);
            prefs.edit().putStringSet("immutable_slips_pool", slips).apply();

            Toast.makeText(getActivity(), "Slip saved!", Toast.LENGTH_SHORT).show();

            // Refresh the UI within the Stats screen so the user sees the change immediately
            updateSelectedDayUI();
            calculateMetrics();
            populatePhysicalCalendar();
        });

        updateSelectedDayUI();
        calculateMetrics();
        populatePhysicalCalendar();
        startFlashingAnimationLoop();

        return view;
    }

    private void startFlashingAnimationLoop() {
        flashRunnable = new Runnable() {
            @Override
            public void run() {
                if (flashDescending) {
                    flashAlpha -= 0.08f;
                    if (flashAlpha <= 0.2f) flashDescending = false;
                } else {
                    flashAlpha += 0.08f;
                    if (flashAlpha >= 1.0f) flashDescending = true;
                }

                // Smoothly update just today's circle item
                for (int i = 0; i < calendarFlexboxContainer.getChildCount(); i++) {
                    View cell = calendarFlexboxContainer.getChildAt(i);
                    TextView tv = cell.findViewById(R.id.tvDayNumberContainer);
                    if (tv != null && "today".equals(tv.getTag())) {
                        GradientDrawable bg = (GradientDrawable) tv.getBackground();
                        if (bg != null) {
                            bg.setStroke((int) (2 * getResources().getDisplayMetrics().density),
                                    Color.argb((int) (flashAlpha * 255), 76, 175, 80));
                        }
                    }
                }
                flashHandler.postDelayed(this, 70);
            }
        };
        flashHandler.post(flashRunnable);
    }

    private void populatePhysicalCalendar() {
        calendarFlexboxContainer.removeAllViews();

        SimpleDateFormat headerFormat = new SimpleDateFormat("MMMM yyyy", Locale.US);
        tvMonthYearHeader.setText(headerFormat.format(currentMonthCalendar.getTime()));

        String todayStr = dateFormat.format(System.currentTimeMillis());
        String initStr = prefs.getString("tracking_init_date_string", todayStr);
        Set<String> slips = prefs.getStringSet("immutable_slips_pool", new HashSet<>());

        ArrayList<Calendar> daysList = new ArrayList<>();
        Calendar helperCal = (Calendar) currentMonthCalendar.clone();
        helperCal.set(Calendar.DAY_OF_MONTH, 1);

        int firstDayOfWeekOffset = helperCal.get(Calendar.DAY_OF_WEEK) - 1;
        helperCal.add(Calendar.DAY_OF_MONTH, -firstDayOfWeekOffset);

        while (daysList.size() < 42) {
            daysList.add((Calendar) helperCal.clone());
            helperCal.add(Calendar.DAY_OF_MONTH, 1);
        }

        int screenWidth = getResources().getDisplayMetrics().widthPixels - (int)(64 * getResources().getDisplayMetrics().density);
        int cellWidthSpec = screenWidth / 7;

        for (Calendar cellCal : daysList) {
            View cellView = LayoutInflater.from(getActivity()).inflate(R.layout.calendar_day_item, calendarFlexboxContainer, false);

            ViewGroup.LayoutParams lp = cellView.getLayoutParams();
            lp.width = cellWidthSpec;
            lp.height = (int)(54 * getResources().getDisplayMetrics().density);
            cellView.setLayoutParams(lp);

            View rootItem = cellView.findViewById(R.id.calendarDayRootItem);
            TextView tvDayNumber = cellView.findViewById(R.id.tvDayNumberContainer);
            String cellStr = dateFormat.format(cellCal.getTime());

            tvDayNumber.setText(String.valueOf(cellCal.get(Calendar.DAY_OF_MONTH)));

            if (cellCal.get(Calendar.MONTH) != currentMonthCalendar.get(Calendar.MONTH)) {
                tvDayNumber.setTextColor(Color.parseColor("#1A1A1A"));
                tvDayNumber.setBackgroundColor(Color.TRANSPARENT);
            } else {
                if (cellStr.equals(todayStr)) {
                    if (slips.contains(cellStr)) {
                        tvDayNumber.setBackgroundResource(R.drawable.day_slip_circle);
                    } else {
                        tvDayNumber.setBackgroundResource(R.drawable.day_ongoing_circle);
                        tvDayNumber.setTag("today");
                    }
                    tvDayNumber.setTextColor(Color.WHITE);
                } else if (slips.contains(cellStr)) {
                    tvDayNumber.setBackgroundResource(R.drawable.day_slip_circle);
                    tvDayNumber.setTextColor(Color.parseColor("#E57373"));
                } else if (cellStr.compareTo(todayStr) > 0) {
                    tvDayNumber.setBackgroundColor(Color.TRANSPARENT);
                    tvDayNumber.setTextColor(Color.parseColor("#333333"));
                } else if (cellStr.compareTo(initStr) >= 0) {
                    tvDayNumber.setBackgroundResource(R.drawable.day_win_circle);
                    tvDayNumber.setTextColor(Color.parseColor("#4CAF50"));
                } else {
                    tvDayNumber.setBackgroundColor(Color.TRANSPARENT);
                    tvDayNumber.setTextColor(Color.parseColor("#666666"));
                }

                rootItem.setOnClickListener(v -> {
                    selectedDayCalendar = (Calendar) cellCal.clone();
                    selectedDateString = cellStr;
                    tvActiveDateDisplay.setText(humanDateFormat.format(selectedDayCalendar.getTime()));
                    updateSelectedDayUI();
                });
            }

            calendarFlexboxContainer.addView(cellView);
        }
    }

    private void showSafeDatePickerDialog() {
        // Initialize the picker with the exact date the user is currently looking at
        int year = selectedDayCalendar.get(Calendar.YEAR);
        int month = selectedDayCalendar.get(Calendar.MONTH);
        int day = selectedDayCalendar.get(Calendar.DAY_OF_MONTH);

        // Use a modern, dark device default dialog container base
        DatePickerDialog datePickerDialog = new DatePickerDialog(getActivity(),
                android.R.style.Theme_DeviceDefault_Dialog_Alert, null, year, month, day);

        // 1. Hide the calendar grid view to isolate the high-performance spinner/wheels layout layer
        datePickerDialog.getDatePicker().setCalendarViewShown(false);

        // 2. Strict tracking boundary guidelines (January 1st to Right Now)
        Calendar minBounds = Calendar.getInstance();
        minBounds.set(year, Calendar.JANUARY, 1, 0, 0, 0);
        datePickerDialog.getDatePicker().setMinDate(minBounds.getTimeInMillis());
        datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis());

        // 3. SECURE INTERCEPTION: Manual Button Click Overrides
        datePickerDialog.setButton(DatePickerDialog.BUTTON_POSITIVE, "CONFIRM DATE", (dialog, which) -> {
            // Forcefully extract the precise selection right from the wheel mechanics
            int confirmedYear = datePickerDialog.getDatePicker().getYear();
            int confirmedMonth = datePickerDialog.getDatePicker().getMonth();
            int confirmedDay = datePickerDialog.getDatePicker().getDayOfMonth();

            // Sync the internal state tracking engines completely
            selectedDayCalendar.set(confirmedYear, confirmedMonth, confirmedDay);
            selectedDateString = dateFormat.format(selectedDayCalendar.getTime());

            // Sync the physical base calendar to match this timeline
            currentMonthCalendar.setTime(selectedDayCalendar.getTime());

            // Instantly rewrite typography maps across the layout
            tvActiveDateDisplay.setText(humanDateFormat.format(selectedDayCalendar.getTime()));
            updateSelectedDayUI();
            populatePhysicalCalendar(); // Redraws the grid for the chosen month immediately!
        });

        datePickerDialog.setButton(DatePickerDialog.BUTTON_NEGATIVE, "DISMISS", (dialog, which) -> dialog.dismiss());

        // 4. PREMIUM COSMETIC DESIGN LAYERS
        if (datePickerDialog.getWindow() != null) {
            GradientDrawable dialogBg = new GradientDrawable();
            dialogBg.setColor(Color.parseColor("#121212")); // Rich midnight charcoal surface card
            dialogBg.setCornerRadius(28 * getResources().getDisplayMetrics().density); // Smooth organic curvature
            dialogBg.setStroke((int)(1.5f * getResources().getDisplayMetrics().density), Color.parseColor("#262626")); // Structural border

            datePickerDialog.getWindow().setBackgroundDrawable(dialogBg);
            datePickerDialog.getWindow().setDimAmount(0.75f); // Rich background ambient shading dim
        }

        // 5. RUNTIME VIEW TREE EXTRACTION
        datePickerDialog.setOnShowListener(dialog -> {
            try {
                android.widget.DatePicker picker = datePickerDialog.getDatePicker();

                // Strip the bulky native calendar/text header container out of the layout entirely
                int headerId = Resources.getSystem().getIdentifier("date_picker_header", "id", "android");
                if (headerId != 0) {
                    View header = picker.findViewById(headerId);
                    if (header != null) header.setVisibility(View.GONE);
                }

                // Polish Action Button Typography to fit your dark aesthetic
                Button positiveBtn = datePickerDialog.getButton(DatePickerDialog.BUTTON_POSITIVE);
                Button negativeBtn = datePickerDialog.getButton(DatePickerDialog.BUTTON_NEGATIVE);

                if (positiveBtn != null) {
                    positiveBtn.setTextColor(Color.parseColor("#4CAF50")); // Neon Green accent
                    positiveBtn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f);
                    positiveBtn.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
                }
                if (negativeBtn != null) {
                    negativeBtn.setTextColor(Color.parseColor("#A0A0A0")); // Muted structural gray
                    negativeBtn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f);
                    negativeBtn.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
                }
            } catch (Exception ignored) {}
        });

        datePickerDialog.show();
    }

    // ADDED BACK: Dynamic bottom detail status tracking controller console layout injector
    private void updateSelectedDayUI() {
        tvSelectedDateLabel.setText("Selected Date: " + selectedDateString);
        String todayString = dateFormat.format(System.currentTimeMillis());
        Set<String> slips = prefs.getStringSet("immutable_slips_pool", new HashSet<>());

        if (slips.contains(selectedDateString)) {
            tvSelectedDateStatus.setText("LOCKED FAILURE (UNALTERABLE)");
            tvSelectedDateStatus.setTextColor(Color.parseColor("#E57373"));
            btnCommitSlip.setVisibility(View.GONE);
        } else if (selectedDateString.equals(todayString)) {
            tvSelectedDateStatus.setText("HOLDING ON — STILL CLEAR");
            tvSelectedDateStatus.setTextColor(Color.parseColor("#4CAF50"));
            btnCommitSlip.setVisibility(View.VISIBLE);
            btnCommitSlip.setText("⚠️ Mark Today as Slip");
        } else if (selectedDateString.compareTo(todayString) > 0) {
            tvSelectedDateStatus.setText("LOCKED FUTURE DAY (NOT ARRIVED YET)");
            tvSelectedDateStatus.setTextColor(Color.parseColor("#333333"));
            btnCommitSlip.setVisibility(View.GONE);
        } else {
            tvSelectedDateStatus.setText("CONFIRMED PAST WIN ✓");
            tvSelectedDateStatus.setTextColor(Color.parseColor("#4CAF50"));
            btnCommitSlip.setVisibility(View.VISIBLE);
            btnCommitSlip.setText("⚠️ Lock Past Slip for this Day");
        }
    }

    private void calculateMetrics() {
        String todayString = dateFormat.format(System.currentTimeMillis());
        String initDateString = prefs.getString("tracking_init_date_string", todayString);
        Set<String> slips = prefs.getStringSet("immutable_slips_pool", new HashSet<>());

        Calendar initCal = Calendar.getInstance();
        Calendar endOfYearCal = Calendar.getInstance();
        try { initCal.setTime(dateFormat.parse(initDateString)); } catch (Exception ignored) {}

        endOfYearCal.set(initCal.get(Calendar.YEAR), Calendar.DECEMBER, 31, 23, 59, 59);

        long totalActivePoolWindowDays = TimeUnit.MILLISECONDS.toDays(endOfYearCal.getTimeInMillis() - initCal.getTimeInMillis()) + 1;
        if (totalActivePoolWindowDays <= 0) totalActivePoolWindowDays = 1;

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

        float netPerformanceRatio = (float) (totalWinsCount - totalSlipsCount) / totalActivePoolWindowDays;
        float finalPercentage = netPerformanceRatio * 100f;

        slipMeterCircle.setPercentage(finalPercentage);
        String percentSign = finalPercentage >= 0 ? "+" : "";
        tvMeterPercentage.setText(percentSign + String.format("%.1f", finalPercentage) + "%");

        if (finalPercentage > 0) {
            tvMeterLabel.setText("SURPLUS (+ve)");
            tvMeterLabel.setTextColor(Color.parseColor("#4CAF50"));
        } else if (finalPercentage < 0) {
            tvMeterLabel.setText("DEFICIT (-ve)");
            tvMeterLabel.setTextColor(Color.parseColor("#E57373"));
        } else {
            tvMeterLabel.setText("BASELINE");
            tvMeterLabel.setTextColor(Color.GRAY);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (flashRunnable != null) flashHandler.removeCallbacks(flashRunnable);
    }
}