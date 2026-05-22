package com.Rixnar.NeverGiveUp;

// Central list used for display. Blocking is still enforced by native-lib + BlockerService extra lists.
public final class AppCatalog {
    private AppCatalog() {}

    // Mirrors native-lib.cpp browser list (for UI display of installed apps).
    public static final String[] BROWSER_PACKAGES = new String[] {
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
            "net.slions.fulguris", "com.lenworthrose.traffic", "com.iode.browser"
    };

    // Mirrors native-lib.cpp credit gated list (for UI display of installed apps).
    public static final String[] CREDIT_GATED_PACKAGES = new String[] {
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
            "com.spotify.music", "com.gaana", "com.jio.media.jiocinema"
    };
}

