package dev.darjs.callrelay

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Sends [CallEvent]s to the Relay via HTTP POST.
 *
 * Best-effort: the initial attempt plus 2 retries (1s and 3s delays). Returns
 * true on a 202 Accepted. Network failures are swallowed and retried; the
 * service never blocks the call lifecycle on the result.
 */
class EventSender {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun sendEvent(event: CallEvent): Boolean = withContext(Dispatchers.IO) {
        val url = BuildConfig.WORKER_URL.removeSuffix("/") + "/events"
        val body = event.toJson().toString()
            .toRequestBody(JSON)
        val retryDelays = listOf(1000L, 3000L)

        for (attempt in 0..retryDelays.size) {
            if (attempt > 0) delay(retryDelays[attempt - 1])
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${BuildConfig.POST_TOKEN}")
                .post(body)
                .build()
            try {
                client.newCall(request).execute().use { resp ->
                    if (resp.code == 202) return@withContext true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // best-effort; retry or give up below
            }
        }
        false
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
