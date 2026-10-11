package app.morrow.weather

import app.morrow.platform.HttpClient
import app.morrow.platform.HttpResponse

/** Answers every GET with [answer] and remembers the URLs asked for. Never touches the network. */
class FakeHttpClient(private val answer: (String) -> HttpResponse = { HttpResponse(200, "{}") }) : HttpClient {
    val urls = mutableListOf<String>()

    override suspend fun get(url: String): HttpResponse {
        urls += url
        return answer(url)
    }

    companion object {
        fun ok(body: String) = FakeHttpClient { HttpResponse(200, body) }
    }
}
