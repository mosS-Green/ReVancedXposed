package io.github.chsbuffer.revancedxposed.youtube.lastfm

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import io.github.chsbuffer.revancedxposed.patch
import kotlin.concurrent.thread

// Constant for SharedPreferences
const val PREF_LASTFM_SESSION = "revanced_lastfm_session"
const val PREF_LASTFM_ENABLED = "revanced_lastfm_enabled"

val LastFmSettingsPatch = patch(name = "Last.fm Scrobbling") {
    
    // Direct hook using XposedHelpers to avoid fingerprint match failures
    try {
        XposedHelpers.findAndHookMethod(
            "androidx.preference.PreferenceFragmentCompat",
            lpparam.classLoader,
            "onCreatePreferences",
            android.os.Bundle::class.java,
            String::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val fragment = param.thisObject as PreferenceFragmentCompat
                    val context = fragment.context ?: return
                    val screen = fragment.preferenceScreen ?: return
                    
                    if (screen.findPreference<Preference>("revanced_lastfm_category") != null) return

                    addLastFmSettings(context, screen, fragment)
                }
            }
        )
    } catch (e: Throwable) {
        // Log error but don't fail the patch loading significantly
        Log.e("ReVancedLastFm", "Failed to hook PreferenceFragmentCompat", e)
    }
}

fun addLastFmSettings(context: Context, screen: PreferenceScreen, fragment: PreferenceFragmentCompat) {
    val prefs = context.getSharedPreferences("revanced_xposed_prefs", Context.MODE_PRIVATE)

    // 1. Create Category
    val category = PreferenceCategory(context).apply {
        key = "revanced_lastfm_category"
        title = "Last.fm Scrobbling"
    }
    screen.addPreference(category)

    // 2. Enable/Disable Switch
    val enableSwitch = SwitchPreferenceCompat(context).apply {
        key = PREF_LASTFM_ENABLED
        title = "Enable Scrobbling"
        isChecked = prefs.getBoolean(PREF_LASTFM_ENABLED, false)
        setOnPreferenceChangeListener { _, newValue ->
            prefs.edit().putBoolean(PREF_LASTFM_ENABLED, newValue as Boolean).apply()
            true
        }
    }
    category.addPreference(enableSwitch)

    // 3. Auth/Logout Button
    val authPref = Preference(context).apply {
        key = "revanced_lastfm_auth"
        title = if (prefs.contains(PREF_LASTFM_SESSION)) "Logged in as User" else "Login to Last.fm"
        summary = if (prefs.contains(PREF_LASTFM_SESSION)) "Click to logout" else "Click to authorize via Browser"
        
        setOnPreferenceClickListener {
            if (prefs.contains(PREF_LASTFM_SESSION)) {
                // Logout Logic
                prefs.edit().remove(PREF_LASTFM_SESSION).apply()
                title = "Login to Last.fm"
                summary = "Click to authorize via Browser"
            } else {
                // Login Logic
                initiateAuthFlow(context, prefs, this)
            }
            true
        }
    }
    category.addPreference(authPref)
}

fun initiateAuthFlow(context: Context, prefs: SharedPreferences, authPref: Preference) {
    thread {
        // 1. Get Token from API
        val token = LastFmApi.getToken()
        if (token == null) {
            Log.e("ReVancedLastFm", "Failed to get token from Last.fm")
            return@thread
        }

        // 2. Open Browser for User Approval
        Handler(Looper.getMainLooper()).post {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(LastFmApi.getAuthUrl(token)))
            browserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(browserIntent)

            // 3. Show Dialog asking user to confirm they approved
            AlertDialog.Builder(context)
                .setTitle("Last.fm Authorization")
                .setMessage("Please approve the application in your browser, then return here and click 'Confirm'.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm") { _, _ ->
                    // 4. Exchange Token for Session
                    thread {
                        val sessionKey = LastFmApi.getSession(token)
                        Handler(Looper.getMainLooper()).post {
                            if (sessionKey != null) {
                                prefs.edit().putString(PREF_LASTFM_SESSION, sessionKey).apply()
                                authPref.title = "Logged in"
                                authPref.summary = "Click to logout"
                            } else {
                                Log.e("ReVancedLastFm", "Authorization failed or timed out.")
                            }
                        }
                    }
                }
                .show()
        }
    }
}
