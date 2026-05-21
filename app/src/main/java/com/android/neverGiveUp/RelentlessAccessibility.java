package com.android.neverGiveUp;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class RelentlessAccessibility extends AccessibilityService {

    // No JNI here — pure Java browser/social check
    private static final Set<String> BROWSERS = new HashSet<>(Arrays.asList(
            "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
            "com.microsoft.emmx", "com.microsoft.emmx.beta",
            "com.opera.browser", "com.opera.mini.native", "com.opera.gx.mobile",
            "com.brave.browser", "com.brave.browser_beta", "com.brave.browser_nightly",
            "com.vivaldi.browser", "com.kiwibrowser.browser", "com.ecosia.android",
            "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix",
            "org.mozilla.fennec_aurora", "org.mozilla.focus", "org.mozilla.klar",
            "com.sec.android.app.sbrowser", "com.duckduckgo.mobile.android",
            "org.torproject.torbrowser", "org.torproject.torbrowser_alpha",
            "de.baumann.browser", "org.bromite.bromite", "acr.browser.lightning",
            "com.qwant.liberty", "com.yandex.browser", "com.uc.browser.en",
            "com.UCMobile.intl", "mark.via", "mark.via.gp",
            "com.mi.globalbrowser", "com.huawei.browser", "com.naver.whale.android",
            "org.adblockplus.browser", "net.slions.fulguris", "com.iode.browser",
            "com.stoutner.privacybrowser.standard", "info.plateaukao.einkbro",
            "com.ghostery.android", "com.puffin.browser", "com.puffinbrowserpro"
    ));

    private static final Set<String> CREDIT_GATED = new HashSet<>(Arrays.asList(
            "com.instagram.android", "com.facebook.katana", "com.facebook.lite",
            "com.facebook.orca", "com.twitter.android", "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill", "com.snapchat.android", "com.pinterest",
            "com.reddit.frontpage", "com.tumblr", "com.linkedin.android",
            "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger",
            "org.thunderdog.challegram", "com.discord", "com.signal.android",
            "com.google.android.youtube", "com.netflix.mediaclient",
            "com.amazon.avod.thirdpartyclient", "com.hotstar.android",
            "com.twitch.android.app", "tv.twitch.android.app",
            "com.supercell.clashofclans", "com.supercell.clashroyale",
            "com.supercell.brawlstars", "com.kiloo.subwaysurf",
            "com.mojang.minecraftpe", "com.pubg.imobile", "com.tencent.ig",
            "com.dts.freefireth", "com.mobile.legends", "com.roblox.client",
            "com.king.candycrushsaga", "com.spotify.music", "com.gaana",
            "com.google.android.apps.magazines", "flipboard.app",
            "com.discord", "com.jio.media.jiocinema"
    ));
    // Add this static method to BlockerService.java
    public static boolean isSocialUnlocked() {
        return socialUnlocked;
    }

    // Add this static field at the top of BlockerService class
    public static boolean socialUnlocked = false;

    @Override
    public void onServiceConnected() {
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        info.notificationTimeout = 0;
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (event.getPackageName() == null) return;

        String pkg = event.getPackageName().toString();
        if (pkg.isEmpty() || pkg.equals(getPackageName())) return;

        SharedPreferences prefs = getSharedPreferences("relentless", MODE_PRIVATE);
        Set<String> extraBrowsers = prefs.getStringSet("extra_browsers", new HashSet<>());
        Set<String> extraSocial   = prefs.getStringSet("extra_social",   new HashSet<>());

        if (BROWSERS.contains(pkg) || extraBrowsers.contains(pkg)) {
            goHome();
            goHome(); // double tap
        } else if (CREDIT_GATED.contains(pkg) || extraSocial.contains(pkg)) {
            // Check unlock state via BlockerService static state
            if (!BlockerService.isSocialUnlocked()) {
                Intent i = new Intent(this, CreditGateActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(i);
            }
        }
    }

    private void goHome() {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_HOME);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    @Override
    public void onInterrupt() {}
}