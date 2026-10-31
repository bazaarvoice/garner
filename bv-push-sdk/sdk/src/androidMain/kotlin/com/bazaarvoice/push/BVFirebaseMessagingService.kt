package com.bazaarvoice.push

import android.content.Context
import android.content.Intent
import com.bazaarvoice.push.push.ParsedPush
import com.bazaarvoice.push.push.PushPayloadParser
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Declared `exported=false` in the library manifest.
 * `onMessageReceived` → the handling pipeline; `onNewToken` → the token
 * source; non-BV messages are left alone.
 */
public open class BVFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        // Greppable receipt proof (`adb logcat -s BVPush:I`): id only, never
        // tokens, hashes, or bodies.
        android.util.Log.i(TAG, "BVPush received id=${data["correspondence_id"]}")
        if (PushPayloadParser.parse(data) is ParsedPush.NotBv) return
        BVPush.handleRemoteMessage(this, data)
    }

    override fun onNewToken(token: String) {
        android.util.Log.i(TAG, "BVPush token refreshed")
        BVPush.onNewToken(this, token)
    }

    public companion object {
        private const val TAG = "BVPush"

        /** For host-owned services: returns "handled" for BV messages. */
        public fun handleRemoteMessage(context: Context, data: Map<String, String>): Boolean {
            if (PushPayloadParser.parse(data) is ParsedPush.NotBv) return false
            BVPush.handleRemoteMessage(context, data)
            return true
        }
    }
}
