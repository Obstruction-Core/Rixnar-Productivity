package com.Rixnar.NeverGiveUp;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class PersistenceHelper {
    private static final String FILE_NAME = "relentless_state.json";
    private static final String PREFS_NAME = "relentless";

    public static final String[] STRING_KEYS = { "key_hash", "tracking_init_date_string", "onboarding_key_temp", "last_credit_reset_date_string" };
    public static final String[] LONG_KEYS   = { "lock_end_date", "last_credit_reset", "onboarding_duration_temp", "social_unlock_expiry", "last_backup_export" };
    public static final String[] INT_KEYS    = { "native_credits" };
    public static final String[] BOOL_KEYS   = { "onboarding_complete", "social_unlocked", "settings_lock_admin", "accessibility_setup_locked", "asked_ignore_battery_optimizations", "settings_backup_reminder" };
    public static final String[] SET_KEYS    = { "immutable_slips_pool", "extra_browsers", "extra_social" };

    public static void saveToInternalStorage(Context context) {
        try {
            JSONObject json = getPrefsAsJson(context);
            File file = new File(context.getFilesDir(), FILE_NAME);
            try (FileOutputStream fos = new FileOutputStream(file);
                 OutputStreamWriter osw = new OutputStreamWriter(fos)) {
                osw.write(json.toString());
                osw.flush();
            }
        } catch (Exception e) {
            Log.e("PersistenceHelper", "Error saving: " + e.getMessage());
        }
    }

    public static void loadFromInternalStorage(Context context) {
        try {
            File file = new File(context.getFilesDir(), FILE_NAME);
            if (!file.exists()) return;

            StringBuilder sb = new StringBuilder();
            try (FileInputStream fis = new FileInputStream(file);
                 InputStreamReader isr = new InputStreamReader(fis)) {
                char[] buffer = new char[1024];
                int read;
                while ((read = isr.read(buffer)) != -1) {
                    sb.append(buffer, 0, read);
                }
            }

            JSONObject json = new JSONObject(sb.toString());
            applyJsonToPrefs(context, json);
        } catch (Exception e) {
            Log.e("PersistenceHelper", "Error loading: " + e.getMessage());
        }
    }

    public static JSONObject getPrefsAsJson(Context context) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        JSONObject json = new JSONObject();
        for (String k : STRING_KEYS) {
            String s = prefs.getString(k, null);
            if (s != null) json.put(k, s);
        }
        for (String k : LONG_KEYS) json.put(k, prefs.getLong(k, 0));
        for (String k : INT_KEYS)  json.put(k, prefs.getInt(k, 0));
        for (String k : BOOL_KEYS) json.put(k, prefs.getBoolean(k, false));
        for (String k : SET_KEYS) {
            Set<String> s = prefs.getStringSet(k, null);
            if (s != null) json.put(k, new JSONArray(s));
        }
        return json;
    }

    public static void applyJsonToPrefs(Context context, JSONObject json) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            if (json.isNull(k)) continue;
            Object v = json.get(k);

            if (v instanceof JSONArray) {
                JSONArray a = (JSONArray) v;
                Set<String> s = new HashSet<>();
                for (int i = 0; i < a.length(); i++) s.add(a.getString(i));
                editor.putStringSet(k, s);
            } else if (v instanceof String) {
                editor.putString(k, (String) v);
            } else if (v instanceof Boolean) {
                editor.putBoolean(k, (Boolean) v);
            } else if (v instanceof Number) {
                Number n = (Number) v;
                if (isLongKey(k)) {
                    editor.putLong(k, n.longValue());
                } else if (isIntKey(k)) {
                    editor.putInt(k, n.intValue());
                }
            }
        }
        editor.commit();
    }

    private static boolean isLongKey(String t) {
        for (String k : LONG_KEYS) if (k.equals(t)) return true;
        return false;
    }

    private static boolean isIntKey(String t) {
        for (String k : INT_KEYS) if (k.equals(t)) return true;
        return false;
    }
}
