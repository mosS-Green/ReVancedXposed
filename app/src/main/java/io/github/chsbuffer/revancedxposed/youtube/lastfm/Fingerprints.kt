package io.github.chsbuffer.revancedxposed.youtube.lastfm

import io.github.chsbuffer.revancedxposed.fingerprint
import org.luckypray.dexkit.query.enums.StringMatchType

// Hooking into standard Android PreferenceFragment to inject our settings
// This is a broad hook; for production, narrow this down to the specific App Settings Class
val preferenceFragmentFingerprint = fingerprint {
    classMatcher { 
        // YouTube uses X (AndroidX) preferences usually
        className("androidx.preference.PreferenceFragmentCompat", StringMatchType.Equals) 
    }
    methodMatcher {
        name = "onCreatePreferences"
    }
}

val mediaSessionFingerprint = fingerprint {
    classMatcher {
        // Target standard Android MediaSession
        className("android.media.session.MediaSession", StringMatchType.Equals)
    }
    // We will hook setMetadata and setPlaybackState
}
