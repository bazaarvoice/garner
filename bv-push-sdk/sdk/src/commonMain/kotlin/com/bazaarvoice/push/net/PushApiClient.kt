package com.bazaarvoice.push.net

import com.bazaarvoice.push.crypto.RequestSigner
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.json.Json

/**
 * Ktor client for the push API, with typed models mirroring its OpenAPI specification.
 * Sends pre-serialized bytes; omits absent optionals.
 * One 401 on a signed call is retried once with a fresh timestamp and
 * signature (never the same bytes); the server-clock offset from the HTTP
 * `Date` header applies to that retry.
 * Every call carries `X-BV-App-Id`, which the API gateway routes on (an App ID it can't
 * route: 412 INVALID_REQUEST from the gateway) ([PushApiHeaders]).
 */
public class PushApiClient(
    private val baseUrl: String,
    private val http: HttpClient = defaultClient(),
    private val clock: Clock = Clock.System,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    public data class CallResult<T>(
        val status: Int,
        val value: T?,
        val error: ErrorEnvelope?,
        val retryAfterMillis: Long?,
    )

    public suspend fun register(body: DeviceRegistrationRequest): CallResult<DeviceRegistrationResponse> =
        postJson(Routes.REGISTER, PushApiHeaders.forRegister(body.appId),
            json.encodeToString(body).encodeToByteArray())

    public suspend fun updateToken(
        deviceId: String,
        appId: Int,
        deviceApiKey: String,
        body: TokenUpdateRequest,
    ): CallResult<Unit> =
        putSigned(Routes.expand(Routes.UPDATE_TOKEN, appId, deviceId), deviceId, appId, deviceApiKey,
            json.encodeToString(body).encodeToByteArray())

    public suspend fun login(
        deviceId: String,
        appId: Int,
        deviceApiKey: String,
        body: LoginRequest,
    ): CallResult<LoginResponse> =
        postSigned(Routes.LOGIN, deviceId, appId, deviceApiKey,
            json.encodeToString(body).encodeToByteArray())

    public suspend fun refresh(deviceId: String, appId: Int, refreshToken: String): CallResult<RefreshResponse> =
        postBearer(Routes.REFRESH, deviceId, appId, refreshToken, ByteArray(0))

    public suspend fun logout(deviceId: String, appId: Int, authToken: String): CallResult<Unit> =
        postBearer(Routes.LOGOUT, deviceId, appId, authToken, ByteArray(0))

    public suspend fun optOut(
        deviceId: String,
        appId: Int,
        authToken: String,
    ): CallResult<Unit> =
        postBearer(Routes.expand(Routes.OPT_OUT, appId, deviceId), deviceId, appId, authToken, ByteArray(0))

    public suspend fun optIn(
        deviceId: String,
        appId: Int,
        authToken: String,
    ): CallResult<Unit> =
        postBearer(Routes.expand(Routes.OPT_IN, appId, deviceId), deviceId, appId, authToken, ByteArray(0))

    private suspend inline fun <reified R> postJson(
        path: String,
        headers: Map<String, String>,
        bytes: ByteArray,
    ): CallResult<R> = request("POST", path, headers, bytes)

    private suspend inline fun <reified R> postSigned(
        path: String,
        deviceId: String,
        appId: Int,
        deviceApiKey: String,
        bytes: ByteArray,
    ): CallResult<R> = executeSigned("POST", path, deviceId, appId, deviceApiKey, bytes)

    private suspend inline fun <reified R> putSigned(
        path: String,
        deviceId: String,
        appId: Int,
        deviceApiKey: String,
        bytes: ByteArray,
    ): CallResult<R> = executeSigned("PUT", path, deviceId, appId, deviceApiKey, bytes)

    private suspend inline fun <reified R> postBearer(
        path: String,
        deviceId: String,
        appId: Int,
        bearer: String,
        bytes: ByteArray,
    ): CallResult<R> = request("POST", path, PushApiHeaders.forBearer(deviceId, appId, bearer), bytes)

    private suspend inline fun <reified R> executeSigned(
        method: String,
        path: String,
        deviceId: String,
        appId: Int,
        deviceApiKey: String,
        bytes: ByteArray,
    ): CallResult<R> {
        val first = signedHeaders(deviceId, appId, deviceApiKey, method, path, bytes, timestamp())
        val result: CallResult<R> = request(method, path, first, bytes)
        if (result.status != 401) return result
        // One retry with a fresh timestamp and signature, applying any
        // server-clock offset; never the same bytes.
        val second = signedHeaders(deviceId, appId, deviceApiKey, method, path, bytes, timestamp())
        return request(method, path, second, bytes)
    }

    private suspend inline fun <reified R> request(
        method: String,
        path: String,
        headers: Map<String, String>,
        bytes: ByteArray,
    ): CallResult<R> {
        val response: HttpResponse = when (method) {
            "POST" -> http.post(baseUrl + path) { applyCall(headers, bytes) }
            "PUT" -> http.put(baseUrl + path) { applyCall(headers, bytes) }
            else -> http.get(baseUrl + path) { headers.forEach { (k, v) -> header(k, v) } }
        }
        val status = response.status.value
        if (response.status.isSuccess()) {
            val text = response.bodyAsText()
            val value: R? = if (text.isBlank()) null
            else runCatching { json.decodeFromString<R>(text) }.getOrNull()
            @Suppress("UNCHECKED_CAST")
            return CallResult(status, value ?: (Unit as R), null, null)
        }
        val error = runCatching {
            json.decodeFromString<ErrorEnvelope>(response.bodyAsText())
        }.getOrNull()
        val retryAfter = response.headers["Retry-After"]?.let {
            RetryPolicy.retryAfterMillis(it, clock.now().toEpochMilliseconds())
        }
        return CallResult(status, null, error, retryAfter)
    }

    private fun signedHeaders(
        deviceId: String,
        appId: Int,
        deviceApiKey: String,
        method: String,
        path: String,
        bytes: ByteArray,
        timestamp: String,
    ): Map<String, String> {
        val signed = RequestSigner.sign(deviceId, appId, deviceApiKey, method, path, bytes, timestamp)
        return PushApiHeaders.forSigned(signed.deviceId, signed.appId, signed.timestamp, signed.signature)
    }

    private fun timestamp(): String {
        val text: String = clock.now().toString()
        val base = text.substringBefore('.')
        return if (base.endsWith("Z")) base else "${base}Z"
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyCall(
        headers: Map<String, String>,
        bytes: ByteArray,
    ) {
        headers.forEach { (k, v) -> header(k, v) }
        contentType(ContentType.Application.Json)
        setBody(bytes)
    }

    public companion object {
        public fun defaultClient(): HttpClient = defaultHttpClient()
    }
}
