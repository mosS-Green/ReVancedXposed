package io.github.chsbuffer.revancedxposed.youtube.lastfm

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

object LastFmApi {
    private const val API_KEY = "YOUR_LASTFM_API_KEY" // REPLACE THIS
    private const val SHARED_SECRET = "YOUR_LASTFM_SHARED_SECRET" // REPLACE THIS
    private const val API_ROOT = "http://ws.audioscrobbler.com/2.0/"

    // Generate Method Signature: md5(params + secret)
    private fun generateSignature(params: Map<String, String>): String {
        val sortedParams = params.toSortedMap()
        val sb = StringBuilder()
        for ((k, v) in sortedParams) {
            sb.append(k).append(v)
        }
        sb.append(SHARED_SECRET)
        
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(sb.toString().toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    // Step 1: Get Request Token
    fun getToken(): String? {
        val params = mapOf(
            "method" to "auth.getToken",
            "api_key" to API_KEY,
            "format" to "json"
        )
        val sig = generateSignature(params)
        val response = executeRequest(params, sig)
        return response?.optString("token")
    }

    // Step 2: Exchange Token for Session Key
    fun getSession(token: String): String? {
        val params = mapOf(
            "method" to "auth.getSession",
            "api_key" to API_KEY,
            "token" to token,
            "format" to "json"
        )
        val sig = generateSignature(params)
        val response = executeRequest(params, sig)
        
        return response?.optJSONObject("session")?.optString("key")
    }

    private fun executeRequest(params: Map<String, String>, signature: String): JSONObject? {
        try {
            val urlBuilder = StringBuilder(API_ROOT).append("?")
            params.forEach { (k, v) -> urlBuilder.append("$k=$v&") }
            urlBuilder.append("api_sig=$signature")

            val connection = URL(urlBuilder.toString()).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            
            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                return JSONObject(responseText)
            }
        } catch (e: Exception) {
            Log.e("ReVancedLastFm", "API Error", e)
        }
        return null
    }

    fun getAuthUrl(token: String): String {
        return "http://www.last.fm/api/auth/?api_key=$API_KEY&token=$token"
    }

    // Step 3: Update "Now Playing"
    fun updateNowPlaying(artist: String, track: String, sessionKey: String) {
        val params = mutableMapOf(
            "method" to "track.updateNowPlaying",
            "artist" to artist,
            "track" to track,
            "api_key" to API_KEY,
            "sk" to sessionKey
        )
        val sig = generateSignature(params)
        postRequest(params, sig)
    }

    // Step 4: Scrobble a track (mark as played)
    fun scrobble(artist: String, track: String, timestamp: Long, sessionKey: String) {
        val params = mutableMapOf(
            "method" to "track.scrobble",
            "artist" to artist,
            "track" to track,
            "timestamp" to timestamp.toString(),
            "api_key" to API_KEY,
            "sk" to sessionKey
        )
        val sig = generateSignature(params)
        postRequest(params, sig)
    }

    private fun postRequest(params: Map<String, String>, signature: String) {
        try {
            val url = URL(API_ROOT)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            
            // Construct POST body
            val postDataBuilder = StringBuilder()
            params.forEach { (k, v) -> 
                postDataBuilder.append(k).append("=").append(URLEncoder.encode(v, "UTF-8")).append("&") 
            }
            postDataBuilder.append("api_sig=$signature")
            val postData = postDataBuilder.toString().toByteArray(Charsets.UTF_8)

            connection.outputStream.use { it.write(postData) }
            
            // Fire and forget, or log response
            if (connection.responseCode != 200) {
                Log.e("ReVancedLastFm", "Post failed: ${connection.responseCode} ${connection.responseMessage}")
            }
        } catch (e: Exception) {
            Log.e("ReVancedLastFm", "Network Error", e)
        }
    }
}
