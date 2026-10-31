package com.bazaarvoice.push.push

/**
 * Display policy, first match wins. Pure function of the parsed
 * payload, local state, current instant and zone.
 */
public enum class DisplayDecision {
    DISPLAY,
    HOLD,
    SUPPRESS_OPTED_OUT,
    SUPPRESS_LOGGED_OUT,
    NOT_HANDLED,
    DROP,
}

public object DisplayPolicy {

    public fun decide(
        parsed: ParsedPush,
        optedOut: Boolean,
        loggedOut: Boolean,
        isTestPush: Boolean,
        inQuietWindow: Boolean,
    ): DisplayDecision {
        if (parsed is ParsedPush.NotBv) return DisplayDecision.NOT_HANDLED
        if (parsed is ParsedPush.Invalid) return DisplayDecision.DROP
        if (optedOut) return DisplayDecision.SUPPRESS_OPTED_OUT
        if (loggedOut && !isTestPush) return DisplayDecision.SUPPRESS_LOGGED_OUT
        if (inQuietWindow) return DisplayDecision.HOLD
        return DisplayDecision.DISPLAY
    }
}
