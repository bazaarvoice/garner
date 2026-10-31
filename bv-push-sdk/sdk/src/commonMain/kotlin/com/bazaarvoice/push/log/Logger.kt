package com.bazaarvoice.push.log

/**
 * Levels NONE/ERROR/WARN/INFO/DEBUG with redaction.
 * Never logged: deviceApiKey, auth/refresh tokens, email (plain or hashed),
 * FCM token (prefix only), full review URL (query carries a token).
 */
public enum class Level { NONE, ERROR, WARN, INFO, DEBUG }

public interface LogSink {
    public fun write(level: Level, tag: String, message: String)
}

public object Logger {
    public var level: Level = Level.WARN
    public var sink: LogSink? = null

    private val secretPatterns = listOf(
        Regex("deviceApiKey['\"]?\\s*[:=]\\s*\\S+"),
        Regex("authToken['\"]?\\s*[:=]\\s*\\S+"),
        Regex("refreshToken['\"]?\\s*[:=]\\s*\\S+"),
    )

    public fun redact(message: String): String {
        var out = message
        secretPatterns.forEach { out = it.replace(out, "[redacted]") }
        // Strip query strings (review URLs carry authentication tokens).
        out = Regex("(https?://[^\\s?]+)\\?[^\\s]*").replace(out, "$1")
        return out
    }

    public fun log(level: Level, tag: String, message: String) {
        if (level <= Logger.level) sink?.write(level, tag, redact(message))
    }
}
