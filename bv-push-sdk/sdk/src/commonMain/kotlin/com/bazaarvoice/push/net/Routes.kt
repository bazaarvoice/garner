package com.bazaarvoice.push.net

/**
 * Push API route literals with `{appId}` / `{deviceId}` placeholders exactly
 * as in the API specification, expanded at call time.
 */
public object Routes {
    public const val REGISTER: String = "/v1/devices"
    public const val UPDATE_TOKEN: String = "/v1/devices/{deviceId}/token"
    public const val LOGIN: String = "/v1/login"
    public const val REFRESH: String = "/v1/token/refresh"
    public const val LOGOUT: String = "/v1/logout"
    public const val OPT_OUT: String = "/v1/devices/{appId}/{deviceId}/opt-out"
    public const val OPT_IN: String = "/v1/devices/{appId}/{deviceId}/opt-in"

    public fun expand(template: String, appId: Int, deviceId: String): String =
        template.replace("{appId}", appId.toString()).replace("{deviceId}", deviceId)
}
