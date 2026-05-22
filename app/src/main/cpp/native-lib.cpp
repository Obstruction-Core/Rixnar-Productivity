#include <jni.h>
#include <string>
#include <vector>
#include <algorithm>

const std::vector<std::string> BROWSERS = {
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
        "com.microsoft.emmx", "com.microsoft.emmx.beta",
        "com.opera.browser", "com.opera.mini.native", "com.opera.gx.mobile", "com.opera.touch",
        "com.brave.browser", "com.brave.browser_beta", "com.brave.browser_nightly",
        "com.vivaldi.browser", "com.vivaldi.browser.snapshot",
        "com.kiwibrowser.browser", "com.ecosia.android", "com.puffinbrowserpro", "com.puffin.browser",
        "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix",
        "org.mozilla.fennec_aurora", "org.mozilla.focus", "org.mozilla.klar",
        "org.mozilla.reference.browser", "io.github.forkmaintainers.iceraven",
        "com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.beta",
        "com.duckduckgo.mobile.android", "com.ghostery.android.ghostery", "com.privacy.browser",
        "org.torproject.torbrowser", "org.torproject.torbrowser_alpha",
        "de.baumann.browser", "com.cookiegames.smartcookie", "org.bromite.bromite",
        "org.ungoogled.chromium.extensions.stable", "com.stoutner.privacybrowser.standard",
        "info.plateaukao.einkbro", "com.jamal2367.styx",
        "acr.browser.lightning", "acr.browser.barebones", "com.ncapdevi.fragnav",
        "com.qwant.liberty", "com.yandex.browser", "com.yandex.browser.beta",
        "com.uc.browser.en", "com.UCMobile.intl",
        "mark.via", "mark.via.gp",
        "com.mi.globalbrowser", "com.mi.globalbrowser.mini",
        "com.huawei.browser", "com.heliossoftware.heliossuite",
        "com.naver.whale.android", "com.surf.browser", "com.tobesoft.xpress",
        "com.lynket.browser", "arun.com.chromer", "com.flynx", "com.tinyBrowser",
        "com.ghostery.android", "com.piribo.browser",
        "org.adblockplus.browser", "com.adblockbrowser", "com.jaumo",
        "net.slions.fulguris", "com.lenworthrose.traffic", "com.iode.browser",
};

const std::vector<std::string> CREDIT_GATED = {
        "com.instagram.android", "com.facebook.katana", "com.facebook.lite", "com.facebook.orca",
        "com.twitter.android", "com.twitter.android.lite",
        "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
        "com.snapchat.android", "com.pinterest", "com.reddit.frontpage", "com.tumblr",
        "com.linkedin.android", "com.linkedin.android.lite",
        "com.vkontakte.android", "ru.ok.android",
        "com.whatsapp", "com.whatsapp.w4b",
        "org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram",
        "com.discord", "com.signal.android",
        "com.google.android.youtube", "com.youtube.music",
        "com.netflix.mediaclient", "com.amazon.avod.thirdpartyclient",
        "com.hotstar.android", "com.disney.disneyplus", "com.hulu.plus",
        "com.twitch.android.app", "tv.twitch.android.app",
        "com.mxtech.videoplayer.ad", "com.mxtech.videoplayer.pro", "org.videolan.vlc",
        "com.supercell.clashofclans", "com.supercell.clashroyale", "com.supercell.brawlstars",
        "com.kiloo.subwaysurf", "com.imangi.templerun2", "com.mojang.minecraftpe",
        "com.pubg.imobile", "com.tencent.ig",
        "com.dts.freefireth", "com.dts.freefiremax", "com.mobile.legends",
        "com.activision.callofduty.shooter", "com.garena.game.codm", "com.roblox.client",
        "com.ea.game.fifa15_row", "com.ea.gp.fifamobile",
        "com.gameloft.android.ANMP.GloftA9HM",
        "com.king.candycrushsaga", "com.king.candycrushsodasaga", "com.zynga.poker",
        "com.josh.android", "com.moj.app", "in.mohalla.sharechat", "com.sharechat.lite",
        "com.google.android.apps.magazines", "flipboard.app",
        "com.inshorts.newsinhindi", "com.eterno",
        "com.spotify.music", "com.gaana", "com.jio.media.jiocinema",
};

bool isMatch(const std::vector<std::string>& list, const std::string& pkg) {
    return std::find(list.begin(), list.end(), pkg) != list.end();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_Rixnar_NeverGiveUp_BlockerService_checkAppStatus(
        JNIEnv *env, jobject, jstring jPackage) {
    if (!jPackage) return 0;
    const char* raw = env->GetStringUTFChars(jPackage, nullptr);
    std::string pkg(raw);
    env->ReleaseStringUTFChars(jPackage, raw);
    if (isMatch(BROWSERS, pkg)) return 2;
    if (isMatch(CREDIT_GATED, pkg)) return 1;
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_Rixnar_NeverGiveUp_RelentlessAccessibility_checkAppStatus(
        JNIEnv *env, jobject, jstring jPackage) {
    if (!jPackage) return 0;
    const char* raw = env->GetStringUTFChars(jPackage, nullptr);
    std::string pkg(raw);
    env->ReleaseStringUTFChars(jPackage, raw);
    if (isMatch(BROWSERS, pkg)) return 2;
    if (isMatch(CREDIT_GATED, pkg)) return 1;
    return 0;
}
