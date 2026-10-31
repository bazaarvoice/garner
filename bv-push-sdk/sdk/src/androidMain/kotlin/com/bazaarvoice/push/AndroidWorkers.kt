package com.bazaarvoice.push

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * ConnectivityManager network callback + process lifecycle foreground
 * triggers. Workers: network-constrained one-time sync resume
 * and the weekly session keep-alive.
 */
public class ConnectivityMonitor(private val context: Context) {
    @Volatile
    public var connected: Boolean = true
        private set

    public fun start(onChange: (Boolean) -> Unit) {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                connected = true
                onChange(true)
            }

            override fun onLost(network: Network) {
                connected = manager.activeNetwork?.let {
                    manager.getNetworkCapabilities(it)
                }?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                onChange(connected)
            }
        })
    }
}

public class ForegroundTriggers {
    public fun onForeground(owner: LifecycleOwner, onForeground: () -> Unit) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = onForeground()
        })
    }
}

public class SyncResumeWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        BVPush.onConnectivityRestored()
        return Result.success()
    }

    public companion object {
        private const val NAME = "bv_push_sync_resume"
        public fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncResumeWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}

public class SessionKeepAliveWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        BVPush.onKeepAlive()
        return Result.success()
    }

    public companion object {
        private const val NAME = "bv_push_session_keepalive"
        public fun enqueueWeekly(context: Context) {
            val request = androidx.work.PeriodicWorkRequestBuilder<SessionKeepAliveWorker>(
                7, java.util.concurrent.TimeUnit.DAYS,
            ).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME, androidx.work.ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/** Encrypted held payloads released by WorkManager at the release instant. */
public class QuietHoursReleaseWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        BVPush.releaseHeld(applicationContext, inputData.getString("notification_id"))
        return Result.success()
    }

    public companion object {
        private const val NAME_PREFIX = "bv_push_release_"
        public fun enqueueAt(context: Context, notificationId: String, delayMinutes: Long) {
            val request = OneTimeWorkRequestBuilder<QuietHoursReleaseWorker>()
                .setInitialDelay(delayMinutes, java.util.concurrent.TimeUnit.MINUTES)
                .setInputData(androidx.work.Data.Builder().putString("notification_id", notificationId).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                NAME_PREFIX + notificationId, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
