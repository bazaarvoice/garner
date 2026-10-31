package com.bazaarvoice.push.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.serialization.json.Json

public actual fun platformHttpClient(json: Json): HttpClient = HttpClient(OkHttp)
