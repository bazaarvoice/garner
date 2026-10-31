package com.bazaarvoice.push.events

/**
 * Local lifecycle events. No event leaves the device; when
 * analytics resume, a separate module subscribes to this stream.
 */
public sealed interface BVPushEvent {
    public data object Registered : BVPushEvent
    public data class RegistrationBlocked(val reason: String) : BVPushEvent
    public data object PushTokenSynced : BVPushEvent
    public data object IdentityLinked : BVPushEvent
    /**
     * `linkIdentity` kept the user but can't log in yet: the SDK logs in only after register and
     * push token sync, and does so by itself once the step named by [reason] is done.
     * [NOT_REGISTERED]: no device ID yet (a blocked registration also sends [RegistrationBlocked]).
     * [WAITING_FOR_PUSH_TOKEN]: registered, but the push token isn't sent yet.
     */
    public data class IdentityPending(val reason: String) : BVPushEvent {
        public companion object {
            public const val NOT_REGISTERED: String = "NOT_REGISTERED"
            public const val WAITING_FOR_PUSH_TOKEN: String = "WAITING_FOR_PUSH_TOKEN"
        }
    }
    public data object SignedIn : BVPushEvent
    public data object SessionRefreshed : BVPushEvent
    public data object SignedOut : BVPushEvent
    public data object IdentityUnlinked : BVPushEvent
    public data class PreferenceSynced(val optedOut: Boolean) : BVPushEvent
    public data class NotificationReceived(val id: String) : BVPushEvent
    public data class NotificationDisplayed(val id: String) : BVPushEvent
    public data class NotificationHeld(val id: String, val untilMillis: Long) : BVPushEvent
    public data class NotificationSuppressed(val id: String, val reason: String) : BVPushEvent
    public data class NotificationOpened(val id: String) : BVPushEvent
    public data class ReviewOpened(val id: String, val presenter: String) : BVPushEvent
    public data class Error(val kind: String, val correlationId: String?) : BVPushEvent
}
