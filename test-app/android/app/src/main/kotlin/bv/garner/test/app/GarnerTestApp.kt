package bv.garner.test.app

import android.app.Application

/**
 * Starts the event listeners with the process, before any screen or SDK
 * entry point (FCM service, quiet hours release) runs, so the Inbox keeps
 * pushes that arrive while the app is closed.
 */
class GarnerTestApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ActivityLog.start()
        PushHistory.start(this)
    }
}
