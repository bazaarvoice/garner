package com.bazaarvoice.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import com.bazaarvoice.push.push.ParsedPush
import java.net.URL

/**
 * Channel `bv_review_requests` ("BV Review Requests"; name and
 * description overridable through string resources; importance HIGH).
 * BigPictureStyle with a downsampled hero image and collapsed thumbnail;
 * text-only fallback.
 */
public object AndroidNotificationBuilder {
    public const val CHANNEL_ID: String = "bv_review_requests"
    public const val TAG: String = "bv_push"

    public fun ensureChannel(context: Context) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val name = stringResource(context, "bv_push_channel_name", "BV Review Requests")
        val description = stringResource(context, "bv_push_channel_description", "Review request notifications")
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH).apply {
                this.description = description
            }
        )
    }

    public enum class PostResult { POSTED, SUPPRESSED_BLOCKED, SUPPRESSED_DENIED }

    public fun show(
        context: Context,
        notificationId: Int,
        parsed: ParsedPush.BvPush,
        ctaLabel: String?,
        tapHandler: ((String) -> Boolean)? = null,
    ): PostResult {
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
            ?: return PostResult.SUPPRESSED_DENIED
        // A user-blocked channel or revoked POST_NOTIFICATIONS
        // posts nothing; the caller emits the Received event.
        if (!manager.areNotificationsEnabled()) return PostResult.SUPPRESSED_DENIED
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            manager.getNotificationChannel(CHANNEL_ID)?.importance ==
                NotificationManager.IMPORTANCE_NONE
        ) {
            return PostResult.SUPPRESSED_BLOCKED
        }
        val hero = parsed.imageUrl?.let { download(it) }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon(context))
            .setContentTitle(parsed.title)
            .setContentText(parsed.body)
            .setAutoCancel(true)
            .setContentIntent(tapPendingIntent(context, parsed, tapHandler))
        if (hero != null) {
            builder.setStyle(
                NotificationCompat.BigPictureStyle().bigPicture(hero).bigLargeIcon(null as Bitmap?)
            )
        }
        manager.notify(TAG, notificationId, builder.build())
        return PostResult.POSTED
    }

    private fun tapPendingIntent(
        context: Context,
        parsed: ParsedPush.BvPush,
        tapHandler: ((String) -> Boolean)?,
    ): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val trampoline = Intent(context, BVPushTapActivity::class.java).apply {
            putExtra(BVPushTapActivity.EXTRA_NOTIFICATION_ID, parsed.notificationId)
            putExtra(BVPushTapActivity.EXTRA_REVIEW_URL, parsed.reviewUrl)
        }
        val stack = if (launch != null) arrayOf(launch, trampoline) else arrayOf(trampoline)
        return PendingIntent.getActivities(
            context, parsed.notificationId.hashCode(), stack,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun download(url: String): Bitmap? = try {
        URL(url).openConnection().apply {
            connectTimeout = 8000
            readTimeout = 8000
        }.getInputStream().use { input ->
            val options = BitmapFactory.Options().apply { inSampleSize = 2 }
            BitmapFactory.decodeStream(input, null, options)
        }
    } catch (_: Exception) {
        null
    }

    private fun smallIcon(context: Context): Int {
        // Host override first (integer resource), then the Firebase default,
        // then the SDK-bundled fallback: posting must never fail for lack of
        // an icon. The app icon is not used: it is usually an
        // adaptive drawable, invalid as a status-bar icon.
        val resources = context.resources
        val id = resources.getIdentifier("com_bazaarvoice_push_notification_icon", "integer", context.packageName)
        if (id != 0) return id
        val meta = context.packageManager.getApplicationInfo(
            context.packageName, PackageManager.GET_META_DATA,
        ).metaData
        val firebaseDefault: Int = meta?.getInt(
            "com.google.firebase.messaging.default_notification_icon", 0) ?: 0
        if (firebaseDefault != 0) return firebaseDefault
        return com.bazaarvoice.push.R.drawable.bv_push_notification_icon
    }

    private fun stringResource(context: Context, name: String, fallback: String): String {
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        return if (id != 0) context.getString(id) else fallback
    }
}
