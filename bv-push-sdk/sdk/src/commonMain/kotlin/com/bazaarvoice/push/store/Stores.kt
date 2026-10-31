package com.bazaarvoice.push.store

/**
 * Storage ports implemented per platform. Secrets only in
 * the secure store; everything else in settings. Production adapters in
 * every test — no in-memory doubles.
 */
public interface SecureStore {
    public fun get(key: String): String?
    public fun put(key: String, value: String)
    public fun remove(key: String)
}

public interface SettingsStore {
    public fun get(key: String): String?
    public fun put(key: String, value: String)
    public fun remove(key: String)
}
