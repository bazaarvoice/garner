package com.bazaarvoice.push.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import kotlinx.serialization.json.Json

public actual fun platformHttpClient(json: Json): HttpClient = HttpClient(Darwin)
