package org.jellyfin.mobile.nflix.data

import android.content.SharedPreferences
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.ThreadLocalRandom

/** SharedPreferences-backed pin state, so a bypass survives an app restart. */
class PrefsPinStateStore(private val prefs: SharedPreferences) : PinStateStore {
    override fun getLong(key: String, fallback: Long): Long = prefs.getLong(key, fallback)
    override fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }
}

object NflixHttp {
    /** Retries, idempotent requests only: safe to replay a GET or a HEAD, never a POST. */
    class RetryInterceptor(
        private val maxAttempts: Int = 3,
        private val baseDelayMillis: Long = 400,
        private val maxDelayMillis: Long = 10_000,
        private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val replayable = request.method == "GET" || request.method == "HEAD"
            var attempt = 1
            while (true) {
                try {
                    val response = chain.proceed(request)
                    if (!replayable || attempt >= maxAttempts || !transient(response.code)) return response
                    val wait = retryAfterMillis(response) ?: backoff(attempt)
                    response.close()
                    sleep(wait)
                    attempt++
                } catch (error: IOException) {
                    // Certificate problems do not heal with another attempt.
                    val hopeless = error is javax.net.ssl.SSLException || !replayable || attempt >= maxAttempts || (chain.call()?.isCanceled() == true)
                    if (hopeless) throw error
                    sleep(backoff(attempt))
                    attempt++
                }
            }
        }

        private fun transient(code: Int): Boolean = code == 429 || code in 500..599

        /** Retry-After in seconds or as an HTTP date, capped so a hostile server cannot freeze the app. */
        private fun retryAfterMillis(response: Response): Long? {
            val header = response.header("Retry-After")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val seconds = header.toLongOrNull()
            val millis = if (seconds != null) {
                seconds * 1000
            } else {
                val format = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US)
                val date = runCatching { format.parse(header)?.time }.getOrNull() ?: return null
                (date - System.currentTimeMillis()).coerceAtLeast(0)
            }
            return millis.coerceIn(0, maxDelayMillis)
        }

        /** Exponential backoff with jitter: 0.4s, 0.8s, 1.6s ... plus up to half the delay. */
        private fun backoff(attempt: Int): Long {
            val base = (baseDelayMillis shl (attempt - 1)).coerceAtMost(maxDelayMillis)
            val jitter = (base / 2 * ThreadLocalRandom.current().nextDouble()).toLong()
            return (base + jitter).coerceAtMost(maxDelayMillis)
        }
    }

    /** Pins [PinPolicy.Pins.hosts] while the policy allows it, and never locks the user out of the app. */
    class PinFallbackInterceptor(
        private val policy: PinPolicy,
        private val fallbackClient: () -> OkHttpClient,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val host = chain.request().url.host
            if (host !in PinPolicy.Pins.byHost || !policy.pinningEnabled()) return chain.proceed(chain.request())
            return try {
                chain.proceed(chain.request()).also { policy.onSuccess() }
            } catch (error: IOException) {
                if (!policy.isPinMismatch(error)) throw error
                if (!policy.onMismatch()) throw error
                Timber.w("Nflix pinning bypassed: retrying %s without certificate pins", host)
                fallbackClient().newCall(chain.request()).execute()
            }
        }
    }
}
