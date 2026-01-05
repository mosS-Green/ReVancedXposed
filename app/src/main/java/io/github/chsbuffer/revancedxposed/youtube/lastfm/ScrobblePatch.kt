package io.github.chsbuffer.revancedxposed.youtube.lastfm

import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import io.github.chsbuffer.revancedxposed.patch

val ScrobblePatch = patch(name = "Last.fm Scrobbler Core") {
    
    // Hook setMetadata to detect track changes
    XposedHelpers.findAndHookMethod(
        "android.media.session.MediaSession",
        lpparam.classLoader,
        "setMetadata",
        "android.media.MediaMetadata",
        object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val context = android.app.AndroidAppHelper.currentApplication() ?: return
                val metadata = param.args[0] as? MediaMetadata
                ScrobbleManager.onMetadataChanged(context, metadata)
            }
        }
    )

    // Hook setPlaybackState to detect Pause/Play
    XposedHelpers.findAndHookMethod(
        "android.media.session.MediaSession",
        lpparam.classLoader,
        "setPlaybackState",
        "android.media.session.PlaybackState",
        object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val context = android.app.AndroidAppHelper.currentApplication() ?: return
                val state = param.args[0] as? PlaybackState
                ScrobbleManager.onPlaybackStateChanged(context, state)
            }
        }
    )
}
