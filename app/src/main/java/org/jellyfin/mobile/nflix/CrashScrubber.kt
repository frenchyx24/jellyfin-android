package org.jellyfin.mobile.nflix

/**
 * Removes credentials from text that leaves the device (crash traces, log uploads).
 *
 * Pure: no Android dependencies, so the rules are tested on the JVM. The redaction keeps the
 * label visible (`ApiKey=<redacted>`) because the shape of the failure still helps debugging.
 */
object CrashScrubber {
    const val REDACTED = "<redacted>"

    /** Query/header pairs whose value is a credential, with `=` or `:` as the separator. */
    private val pairs = listOf(
        Regex("""(?i)\b(api[_-]?key)\s*([=:])\s*("[^"]*"|[^\s&"',;]+)"""),
        Regex("""(?i)\b(access[_-]?token)\s*([=:])\s*("[^"]*"|[^\s&"',;]+)"""),
        Regex("""(?i)\b(refresh[_-]?token)\s*([=:])\s*("[^"]*"|[^\s&"',;]+)"""),
        Regex("""(?i)\b(client[_-]?secret)\s*([=:])\s*("[^"]*"|[^\s&"',;]+)"""),
        Regex("""(?i)\b(token)\s*([=:])\s*("[^"]*"|[^\s&"',;]+)"""),
    )

    /** Tokens carried in a header: `X-Emby-Token: …`, `Authorization: …`. */
    private val headers = listOf(
        Regex("""(?i)\b(x-emby-token|x-emby-authorization|authorization)\s*([:=])\s*("[^"]*"|[^\s"',;]+)"""),
        Regex("""(?i)\b(bearer)\s+([A-Za-z0-9\-._~+/]{8,}=*)"""),
    )

    /** A 32-character hexadecimal string is a Jellyfin/Mira token or id with secret value. */
    private val hexToken = Regex("""\b[0-9a-fA-F]{32}\b""")

    /** URL userinfo (`https://user:pass@host`) is a credential too. */
    private val urlUserInfo = Regex("""://[^/\s:@]+:[^/\s@]+@""")

    fun scrub(text: String): String {
        var out = text
        for (rule in pairs) out = rule.replace(out) { "${it.groupValues[1]}${it.groupValues[2]}$REDACTED" }
        for (rule in headers) {
            out = rule.replace(out) {
                val g = it.groupValues
                if (g.size > 2) "${g[1]} ${REDACTED}" else "${g[1]}${g[2]}$REDACTED"
            }
        }
        out = hexToken.replace(out, REDACTED)
        out = urlUserInfo.replace(out, "://$REDACTED@")
        return out
    }
}
