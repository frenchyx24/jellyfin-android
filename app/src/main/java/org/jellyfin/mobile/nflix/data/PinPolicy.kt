package org.jellyfin.mobile.nflix.data

import timber.log.Timber

/** Where the pinning failure count and bypass window are persisted (SharedPreferences in the app). */
interface PinStateStore {
    fun getLong(key: String, fallback: Long): Long
    fun putLong(key: String, value: Long)
}

/** In-memory store, used by tests and as a last resort when prefs cannot be opened. */
class MemoryPinStateStore : PinStateStore {
    private val values = mutableMapOf<String, Long>()
    override fun getLong(key: String, fallback: Long): Long = values[key] ?: fallback
    override fun putLong(key: String, value: Long) {
        values[key] = value
    }
}

/**
 * Certificate pinning policy, pure Kotlin so the rules can be tested off-device.
 *
 * Pins are enforced only before [EXPIRY_EPOCH_MILLIS]: past that date a stale pin must never
 * break the app again. A pin mismatch while system trust validates is counted; after
 * [FAILURES_BEFORE_BYPASS] consecutive mismatches pinning is disabled for [BYPASS_DAYS] days.
 * A success resets the counter. The user is never locked out by pinning.
 */
class PinPolicy(
    private val store: PinStateStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** True when the current date is still inside the baked pin window. */
    fun inPinWindow(at: Long = now()): Boolean = at < EXPIRY_EPOCH_MILLIS

    /** True when pinning must be applied to the connection right now. */
    fun pinningEnabled(at: Long = now()): Boolean {
        if (!inPinWindow(at)) return false
        val until = store.getLong(KEY_BYPASS_UNTIL, 0L)
        return until <= at
    }

    /** Counts one pin mismatch (system trust already validated the chain). Returns true when bypass activates. */
    fun onMismatch(at: Long = now()): Boolean {
        val failures = store.getLong(KEY_FAILURES, 0L) + 1
        store.putLong(KEY_FAILURES, failures)
        if (failures < FAILURES_BEFORE_BYPASS) return false
        store.putLong(KEY_FAILURES, 0L)
        store.putLong(KEY_BYPASS_UNTIL, at + BYPASS_MILLIS)
        store.putLong(KEY_LAST_BYPASS, at)
        Timber.w("Nflix pinning bypassed for %d days after %d consecutive pin mismatches", BYPASS_DAYS, FAILURES_BEFORE_BYPASS)
        return true
    }

    /** A request that passed the pin check clears the consecutive failure count. */
    fun onSuccess() {
        if (store.getLong(KEY_FAILURES, 0L) != 0L) store.putLong(KEY_FAILURES, 0L)
    }

    fun consecutiveFailures(): Long = store.getLong(KEY_FAILURES, 0L)

    /** True when OkHttp's CertificatePinner refused a chain that system trust accepted. */
    fun isPinMismatch(error: Throwable): Boolean =
        error is javax.net.ssl.SSLPeerUnverifiedException && error.message?.contains("Certificate pinning failure") == true

    /** Certificate pins computed from the live chains of the Mira hosts (2026-11-20). */
    object Pins {
        /** Shared leaf/issuer: CN=miracloud.fr, issued by Google Trust Services WE1. */
        const val WE1_INTERMEDIATE = "kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4="
        const val GTS_ROOT_R4 = "mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c="

        /** Backup roots for the current issuer family and for a possible Let's Encrypt switch. */
        const val GTS_ROOT_R1 = "hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc="
        const val GTS_ROOT_R2 = "Vfd95BwDeSQo+NUYxVEEIlvkOlWY2SalKK1lPhzOx78="
        const val GTS_ROOT_R3 = "QXnt2YHvdHR3tJYmQIr0Paosp6t/nggsEGD4QJZ3Q0g="
        const val ISRG_ROOT_X1 = "C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M="
        const val ISRG_ROOT_X2 = "diGVwiVYbubAI3RW4hB9xU8e/CH2GnkuvVFZE8zmgzI="
        const val GLOBALSIGN_ROOT_CA = "K87oWBWM9UZfyddvDfoxL+8lpNyoUB2ptGtn0fv6G2Q="

        val hosts = listOf("nflix.miracloud.fr", "annonces.miracloud.fr", "auth.miracloud.fr")

        /** Intermediate + roots: any one chain certificate must match. The leaf is left out, it rotates. */
        val accepted = listOf(
            WE1_INTERMEDIATE,
            GTS_ROOT_R4,
            GTS_ROOT_R1,
            GTS_ROOT_R2,
            GTS_ROOT_R3,
            GLOBALSIGN_ROOT_CA,
            ISRG_ROOT_X1,
            ISRG_ROOT_X2,
        )

        val byHost: Map<String, List<String>> = hosts.associateWith { accepted }
    }

    companion object {
        /** 2027-11-30T00:00:00Z: after this date pins are not enforced, whatever the chain says. */
        const val EXPIRY_EPOCH_MILLIS = 1_827_532_800_000L
        const val FAILURES_BEFORE_BYPASS = 3L
        const val BYPASS_DAYS = 7L
        const val BYPASS_MILLIS = BYPASS_DAYS * 24 * 60 * 60 * 1000

        const val KEY_FAILURES = "nflix_pin_failures"
        const val KEY_BYPASS_UNTIL = "nflix_pin_bypass_until"
        const val KEY_LAST_BYPASS = "nflix_pin_last_bypass"
    }
}
