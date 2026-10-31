package com.bazaarvoice.push.net

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Platform HTTP engine: OkHttp on Android/JVM, Darwin on iOS.
 * The engine is explicit — a bare `HttpClient()` finds no engine at runtime.
 */
public expect fun platformHttpClient(json: Json): HttpClient

internal fun defaultJson(): Json = Json { ignoreUnknownKeys = true }

public fun defaultHttpClient(): HttpClient =
    platformHttpClient(defaultJson()).config {
        install(ContentNegotiation) { json(defaultJson()) }
    }
