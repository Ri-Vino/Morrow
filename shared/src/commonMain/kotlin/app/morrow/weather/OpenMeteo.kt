package app.morrow.weather

import app.morrow.platform.HttpClient
import app.morrow.platform.HttpException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

internal val OpenMeteoJson = Json { ignoreUnknownKeys = true }

private const val TOO_MANY_REQUESTS = 429

/** GETs [url] and reads the reply as JSON, turning every way that can fail into a [FetchException]. */
internal suspend fun HttpClient.getJson(url: String): JsonElement {
    val response = try {
        get(url)
    } catch (e: HttpException) {
        throw FetchException("No answer from Open-Meteo", cause = e)
    }
    if (response.status == TOO_MANY_REQUESTS) throw FetchException("Open-Meteo asked us to slow down", rateLimited = true)
    if (response.status !in 200..299) throw FetchException("Open-Meteo answered ${response.status}")
    return readOrFail { OpenMeteoJson.parseToJsonElement(response.body) }
}

/** Runs [read], turning a reply that isn't the expected shape into a [FetchException]. */
internal inline fun <T> readOrFail(read: () -> T): T = try {
    read()
} catch (e: IllegalArgumentException) {
    // Also catches SerializationException and kotlinx-datetime's parse errors, which extend it.
    throw FetchException("Couldn't read Open-Meteo's reply", cause = e)
}

/** Percent-encodes [text] as UTF-8 for a URL query value. */
internal fun urlEncode(text: String): String = buildString {
    for (byte in text.encodeToByteArray()) {
        val c = byte.toInt().toChar()
        if (byte >= 0 && (c.isLetterOrDigit() || c in "-._~")) {
            append(c)
        } else {
            append('%')
            append(HEX[(byte.toInt() shr 4) and 0xF])
            append(HEX[byte.toInt() and 0xF])
        }
    }
}

private const val HEX = "0123456789ABCDEF"
