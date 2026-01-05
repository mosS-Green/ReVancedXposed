package io.github.chsbuffer.revancedxposed.youtube.lastfm

import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import de.robv.android.xposed.XC_MethodHook
import io.github.chsbuffer.revancedxposed.patch

val ScrobblePatch = patch(name = "Last.fm Scrobbler Core") {
    
    // Hook setMetadata to detect track changes
    findMethodDirect {
        findMethod {
            matcher {
                // Hooking Android Framework class loaded in the App process
                declaredClass { name = "android.media.session.MediaSession" }
                name = "setMetadata"
                paramTypes("android.media.MediaMetadata")
            }
        }
    }.hookMethod(object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            val session = param.thisObject as MediaSession
            // Context is usually needed to access SharedPreferences
            // MediaSession doesn't expose context easily via public API, 
            // but we can try to get it from the app context or reflection if needed.
            // However, hooking 'android.app.Application' to get global context is a prerequisite usually.
            
            // For this snippet, assuming we can get context from the Xposed environment or the session controller's context
            // A safer bet in Xposed is AndroidAppHelper.currentApplication()
            val context = android.app.AndroidAppHelper.currentApplication() ?: return
            
            val metadata = param.args[0] as? MediaMetadata
            ScrobbleManager.onMetadataChanged(context, metadata)
        }
    })

    // Hook setPlaybackState to detect Pause/Play
    findMethodDirect {
        findMethod {
            matcher {
                declaredClass { name = "android.media.session.MediaSession" }
                name = "setPlaybackState"
                paramTypes("android.media.session.PlaybackState")
            }
        }
    }.hookMethod(object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            val context = android.app.AndroidAppHelper.currentApplication() ?: return
            val state = param.args[0] as? PlaybackState
            ScrobbleManager.onPlaybackStateChanged(context, state)
        }
    })
}
