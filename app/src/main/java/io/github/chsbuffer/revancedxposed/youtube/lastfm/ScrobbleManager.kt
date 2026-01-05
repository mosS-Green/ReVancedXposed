package io.github.chsbuffer.revancedxposed.youtube.lastfm

import android.content.Context
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.util.Log
import kotlin.concurrent.thread

object ScrobbleManager {
    private var currentArtist: String? = null
    private var currentTrack: String? = null
    private var startTime: Long = 0
    private var duration: Long = 0
    private var isPlaying = false
    private var lastScrobbledTrack: String? = null // To prevent double scrobbling

    fun onMetadataChanged(context: Context, metadata: MediaMetadata?) {
        if (metadata == null) return

        // Extract Standard Android Metadata
        val newArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) 
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) 
            ?: metadata.getString(MediaMetadata.METADATA_KEY_AUTHOR)
        val newTrack = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        val newDuration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)

        if (newArtist.isNullOrEmpty() || newTrack.isNullOrEmpty()) return

        // If track changed
        if (newArtist != currentArtist || newTrack != currentTrack) {
            // 1. Attempt to scrobble the *previous* track if it met criteria
            checkAndScrobblePrevious(context)

            // 2. Update state to new track
            currentArtist = newArtist
            currentTrack = newTrack
            duration = newDuration
            startTime = System.currentTimeMillis() / 1000
            lastScrobbledTrack = null // Reset scrobble flag

            // 3. Send "Now Playing"
            sendNowPlaying(context, newArtist, newTrack)
        }
    }

    fun onPlaybackStateChanged(context: Context, state: PlaybackState?) {
        if (state == null) return
        val wasPlaying = isPlaying
        isPlaying = (state.state == PlaybackState.STATE_PLAYING)

        // If we just paused/stopped, we might want to calculate time listened, 
        // but for simplicity in this version, we calculate time diff when the track changes.
    }

    private fun checkAndScrobblePrevious(context: Context) {
        if (currentArtist == null || currentTrack == null) return
        
        // Safety check: Don't scrobble if we already did for this instance
        if ("$currentArtist|$currentTrack" == lastScrobbledTrack) return

        val now = System.currentTimeMillis() / 1000
        val timePlayed = now - startTime
        
        // Last.fm Rule: Track must be longer than 30s AND (played for > 4 mins OR > 50% duration)
        val isValidDuration = duration > 30000 // 30s in ms (Metadata usually sends ms)
        val percentagePlayed = if (duration > 0) (timePlayed * 1000).toFloat() / duration else 0f
        
        // Convert duration to seconds for logic if needed, but simple percentage works
        if (isValidDuration && (timePlayed >= 240 || percentagePlayed >= 0.5)) {
            val prefs = context.getSharedPreferences("revanced_xposed_prefs", Context.MODE_PRIVATE)
            val sessionKey = prefs.getString(PREF_LASTFM_SESSION, null) ?: return
            
            val artistToScrobble = currentArtist!!
            val trackToScrobble = currentTrack!!
            val timestamp = startTime

            thread {
                LastFmApi.scrobble(artistToScrobble, trackToScrobble, timestamp, sessionKey)
                Log.d("ReVancedLastFm", "Scrobbled: $trackToScrobble by $artistToScrobble")
            }
            lastScrobbledTrack = "$currentArtist|$currentTrack"
        }
    }

    private fun sendNowPlaying(context: Context, artist: String, track: String) {
        val prefs = context.getSharedPreferences("revanced_xposed_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(PREF_LASTFM_ENABLED, false)) return
        val sessionKey = prefs.getString(PREF_LASTFM_SESSION, null) ?: return

        thread {
            LastFmApi.updateNowPlaying(artist, track, sessionKey)
        }
    }
}
