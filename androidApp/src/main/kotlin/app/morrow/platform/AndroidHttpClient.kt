package app.morrow.platform

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import kotlin.time.Duration.Companion.seconds

/** HTTPS GETs with HttpURLConnection: no cookies, no cache, gzip, and a `Morrow/<version>` User-Agent. */
class AndroidHttpClient(versionName: String) : HttpClient {
    private val userAgent = "Morrow/$versionName"

    override suspend fun get(url: String): HttpResponse {
        require(url.startsWith("https://")) { "Only HTTPS: $url" }
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw HttpException("Couldn't open $url", e)
        }
        connection.apply {
            connectTimeout = TIMEOUT
            readTimeout = TIMEOUT
            useCaches = false
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept-Encoding", "gzip")
        }
        return coroutineScope {
            val call = async(Dispatchers.IO) { connection.read() }
            try {
                call.await()
            } catch (e: CancellationException) {
                // The blocking read can't see cancellation; closing the socket ends it.
                connection.disconnect()
                throw e
            }
        }
    }

    private fun HttpURLConnection.read(): HttpResponse = try {
        val status = responseCode
        val stream = (if (status >= 400) errorStream else inputStream)
        val body = stream?.let { raw ->
            val unzipped = if (contentEncoding.equals("gzip", ignoreCase = true)) GZIPInputStream(raw) else raw
            unzipped.use { it.readBytes().decodeToString() }
        }.orEmpty()
        HttpResponse(status, body)
    } catch (e: IOException) {
        throw HttpException("No answer from ${url.host}", e)
    } finally {
        disconnect()
    }

    private companion object {
        val TIMEOUT = 10.seconds.inWholeMilliseconds.toInt()
    }
}
