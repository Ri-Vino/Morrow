@file:OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)

package app.morrow.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setValue
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * HTTPS GETs with an ephemeral NSURLSession: no cookies, no cache, no stored credentials, and a `Morrow/<version>`
 * User-Agent. NSURLSession asks for gzip and unzips by itself.
 */
class IosHttpClient(version: String) : HttpClient {
    private val userAgent = "Morrow/$version"

    private val session: NSURLSession = NSURLSession.sessionWithConfiguration(
        NSURLSessionConfiguration.ephemeralSessionConfiguration().apply {
            setHTTPCookieStorage(null)
            setHTTPShouldSetCookies(false)
            setURLCache(null)
            setURLCredentialStorage(null)
            setRequestCachePolicy(NSURLRequestReloadIgnoringLocalCacheData)
            setTimeoutIntervalForRequest(TIMEOUT_SECONDS)
        },
    )

    override suspend fun get(url: String): HttpResponse {
        require(url.startsWith("https://")) { "Only HTTPS: $url" }
        val address = NSURL.URLWithString(url) ?: throw HttpException("Not a URL: $url")
        val request = NSMutableURLRequest.requestWithURL(address).apply {
            setValue(userAgent, forHTTPHeaderField = "User-Agent")
            setTimeoutInterval(TIMEOUT_SECONDS)
        }
        return suspendCancellableCoroutine { continuation ->
            // The handler runs on the session's own queue; resuming hands the answer back to the caller's thread.
            val task = session.dataTaskWithRequest(request) { data, response, error ->
                val status = (response as? NSHTTPURLResponse)?.statusCode
                when {
                    error != null -> continuation.resumeWithException(HttpException("No answer: ${error.localizedDescription}"))
                    status == null -> continuation.resumeWithException(HttpException("No HTTP answer from $url"))
                    else -> continuation.resume(HttpResponse(status.toInt(), data?.text().orEmpty()))
                }
            }
            // Cancelling ends the request, and its handler's late answer is ignored.
            continuation.invokeOnCancellation { task.cancel() }
            task.resume()
        }
    }

    private fun NSData.text(): String {
        val bytes = ByteArray(length.toInt())
        if (bytes.isEmpty()) return ""
        bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
        return bytes.decodeToString()
    }

    private companion object {
        const val TIMEOUT_SECONDS = 10.0
    }
}
