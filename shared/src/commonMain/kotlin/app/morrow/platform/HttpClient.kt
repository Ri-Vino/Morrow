package app.morrow.platform

/**
 * A plain HTTPS GET. Only weather and city search use it, and nothing may call it while weather is off.
 * Implementations send no cookies or identifiers, only a `Morrow/<version>` User-Agent.
 */
interface HttpClient {
    /**
     * Answers with whatever status the server sends, errors included.
     * Throws [HttpException] when no answer arrives: offline, a timeout or a failed TLS handshake.
     * Cancelling the caller (for example with `withTimeout`) abandons the request.
     */
    suspend fun get(url: String): HttpResponse
}

data class HttpResponse(val status: Int, val body: String)

/** No answer from the server; see [HttpClient.get]. */
class HttpException(message: String, cause: Throwable? = null) : Exception(message, cause)
