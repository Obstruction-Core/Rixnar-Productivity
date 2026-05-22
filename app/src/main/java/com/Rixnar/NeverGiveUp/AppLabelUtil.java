package com.Rixnar.NeverGiveUp;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import java.util.Locale;

public final class AppLabelUtil {
    private AppLabelUtil() {}

    public static boolean looksLikePackageName(String s) {
        return s != null && s.contains(".") && !s.contains(" ");
    }

    public static String getInstalledLabelOrNull(Context context, String packageName) {
        if (context == null || packageName == null) return null;
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            CharSequence label = pm.getApplicationLabel(ai);
            return label != null ? label.toString() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String toDisplayName(Context context, String maybePackageOrLabel) {
        if (context == null) return maybePackageOrLabel;
        if (maybePackageOrLabel == null) return "";

        String s = maybePackageOrLabel.trim();
        if (s.isEmpty()) return "";

        // Heuristic: if it doesn't look like a package name, treat it as a user label already.
        if (!looksLikePackageName(s)) return s;

        String installed = getInstalledLabelOrNull(context, s);
        if (installed != null && !installed.trim().isEmpty()) return installed;
        return s; // fallback: package name
    }

    public static String toDisplayNameOrFallback(Context context, String maybePackageOrLabel) {
        if (maybePackageOrLabel == null) return "";
        String s = maybePackageOrLabel.trim();
        if (s.isEmpty()) return "";
        if (!looksLikePackageName(s)) return s;

        String installed = getInstalledLabelOrNull(context, s);
        if (installed != null && !installed.trim().isEmpty()) return installed;
        return fallbackNameFromPackage(s);
    }

    public static String fallbackNameFromPackage(String packageName) {
        if (packageName == null) return "";
        String p = packageName.toLowerCase(Locale.US);

        // Common high-signal mappings (covers the “Chrome not com.android.chrome” case)
        if (p.contains("chrome")) return "Chrome";
        if (p.contains("firefox")) return "Firefox";
        if (p.contains("brave")) return "Brave";
        if (p.contains("vivaldi")) return "Vivaldi";
        if (p.contains("opera")) return "Opera";
        if (p.contains("duckduckgo")) return "DuckDuckGo";
        if (p.contains("edge") || p.contains("emmx")) return "Edge";
        if (p.contains("torbrowser")) return "Tor Browser";
        if (p.contains("instagram")) return "Instagram";
        if (p.contains("youtube")) return "YouTube";
        if (p.contains("tiktok") || p.contains("musically") || p.contains("trill")) return "TikTok";
        if (p.contains("facebook")) return "Facebook";
        if (p.contains("whatsapp")) return "WhatsApp";
        if (p.contains("telegram")) return "Telegram";
        if (p.contains("snapchat")) return "Snapchat";
        if (p.contains("discord")) return "Discord";
        if (p.contains("netflix")) return "Netflix";
        if (p.contains("spotify")) return "Spotify";
        if (p.contains("reddit")) return "Reddit";

        // Generic fallback: last segment, cleaned up.
        String last = packageName;
        int idx = packageName.lastIndexOf('.');
        if (idx >= 0 && idx < packageName.length() - 1) {
            last = packageName.substring(idx + 1);
        }
        last = last.replace('_', ' ').replace('-', ' ').trim();
        if (last.isEmpty()) return packageName;
        return Character.toUpperCase(last.charAt(0)) + last.substring(1);
    }
}
