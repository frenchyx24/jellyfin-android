package org.jellyfin.mobile.nflix.cast

import android.content.Context

/** The libre flavour ships without Google Cast: only Jellyfin remote sessions are offered. */
object CastBackends {
    @Suppress("UNUSED_PARAMETER")
    fun chromecast(context: Context, receiverId: () -> String, credentials: () -> CastProtocol.Credentials?, serverId: () -> String?): CastBackend? = null
}
