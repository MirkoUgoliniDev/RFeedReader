package it.mirkougolinidev.rfeedreader.core.network

import android.net.Uri
import android.os.Build
import android.text.Html
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.Locale

data class ParsedFeed(
    val title: String,
    val description: String,
    val imageUrl: String?,
    val websiteUrl: String?,
    val episodes: List<ParsedEpisode>
)

data class ParsedEpisode(
    val stableKey: String,
    val guid: String,
    val title: String,
    val description: String,
    val enclosureUrl: String,
    val mimeType: String?,
    val byteLength: Long?,
    val publishedAt: Long?,
    val durationMs: Long?,
    val imageUrl: String?
)

class FeedParser {
    suspend fun fetchAndParse(feedUrl: String): Pair<String, ParsedFeed> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val uri = runCatching { Uri.parse(feedUrl) }.getOrNull()
            ?: throw FeedException("L'indirizzo del feed non è valido.")
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            throw FeedException("Inserisci un indirizzo web HTTP o HTTPS valido.")
        }

        val connection = (URL(feedUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "RFeedReader/0.1 (Android podcast reader)")
            setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*;q=0.5")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw FeedException("Il server ha risposto con errore HTTP $status.")
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_FEED_BYTES) throw FeedException("Il feed è troppo grande per essere importato.")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            connection.url.toString() to parse(bytes, connection.url.toString())
        } catch (error: FeedException) {
            throw error
        } catch (error: Exception) {
            throw FeedException("Impossibile leggere il feed. Controlla la connessione e l'indirizzo.", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(bytes: ByteArray, baseUrl: String): ParsedFeed {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
        val parser = factory.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
            setInput(ByteArrayInputStream(bytes), null)
        }
        var title = ""
        var description = ""
        var image: String? = null
        var website: String? = null
        var rootTag: String? = null
        val episodes = mutableListOf<ParsedEpisode>()
        var item: MutableEpisode? = null
        var insideImage = false
        var imageUrlText: String? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val tag = parser.name.lowercase(Locale.ROOT)
                if (rootTag == null) rootTag = tag.substringAfter(':')
                if (tag == "item" || tag == "entry") item = MutableEpisode()
                else if (tag == "image" && item == null) {
                    val href = parser.attr("href")
                    if (href != null) image = resolve(baseUrl, href)
                    insideImage = true
                } else if (tag == "enclosure" && item != null) {
                    item.enclosure = (parser.attr("url") ?: parser.attr("href"))?.let { resolve(baseUrl, it) }
                    item.mimeType = parser.attr("type")
                    item.byteLength = parser.attr("length")?.toLongOrNull()
                } else if (tag == "link") {
                    val href = parser.attr("href")
                    val rel = parser.attr("rel")
                    val type = parser.attr("type")
                    if (item != null) {
                        if (rel == "enclosure" || type?.startsWith("audio/") == true) {
                            item.enclosure = href?.let { resolve(baseUrl, it) } ?: parser.nextText().trim().let { resolve(baseUrl, it) }
                            item.mimeType = type
                        }
                    } else if (href != null && (rel == null || rel == "alternate")) {
                        website = resolve(baseUrl, href)
                    }
                } else if (tag == "itunes:image" || tag == "image" && item != null) {
                    val href = parser.attr("href") ?: parser.attr("url")
                    if (item != null) item.image = href?.let { resolve(baseUrl, it) }
                    else if (href != null) image = resolve(baseUrl, href)
                } else if (tag == "title" || tag in setOf("description", "summary", "subtitle", "content", "encoded", "guid", "id", "pubdate", "published", "updated", "date", "duration", "url", "logo", "icon")) {
                    val value = runCatching { parser.nextText().trim() }.getOrDefault("")
                    if (item != null) when (tag) {
                        "title" -> item.title = value
                        "description", "summary", "subtitle", "content", "encoded" -> item.description = value
                        "guid", "id" -> item.guid = value
                        "pubdate", "published", "updated", "date" -> item.date = parseDate(value)
                        "duration" -> item.durationMs = parseDuration(value)
                        "url" -> if (insideImage && item.image == null) item.image = resolve(baseUrl, value)
                    } else when (tag) {
                        "title" -> title = value
                        "description", "summary", "subtitle", "content", "encoded" -> description = value
                        "url" -> if (insideImage && image == null) image = resolve(baseUrl, value)
                        "logo", "icon" -> image = resolve(baseUrl, value)
                    }
                }
            } else if (event == XmlPullParser.END_TAG) {
                val tag = parser.name.lowercase(Locale.ROOT)
                if (tag == "item" || tag == "entry") {
                    item?.toParsed()?.let { episodes.add(it.copy(description = plainText(it.description))) }
                    item = null
                } else if (tag == "image") {
                    insideImage = false
                }
            }
            event = parser.next()
        }
        if (rootTag !in setOf("rss", "feed", "rdf")) throw FeedException("Il contenuto ricevuto non è un feed RSS o Atom riconosciuto.")
        if (episodes.isEmpty()) throw FeedException("Il feed è valido, ma non contiene episodi audio riconosciuti.")
        if (title.isBlank()) title = runCatching { Uri.parse(baseUrl).host.orEmpty() }.getOrDefault("Podcast")
        return ParsedFeed(title.trim(), plainText(description), image, website, episodes)
    }

    private fun XmlPullParser.attr(name: String): String? =
        getAttributeValue(null, name) ?: (0 until attributeCount).firstNotNullOfOrNull { index ->
            if (getAttributeName(index).substringAfter(':').equals(name, ignoreCase = true)) getAttributeValue(index) else null
        }

    private fun resolve(base: String, value: String): String? = runCatching { URL(URL(base), value).toString() }.getOrNull()

    // Html.fromHtml(String, Int) esiste solo da API 24: sotto si usa la variante storica.
    private fun plainText(value: String): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        Html.fromHtml(value, Html.FROM_HTML_MODE_COMPACT)
    } else {
        @Suppress("DEPRECATION") Html.fromHtml(value)
    }.toString().trim()

    private fun parseDate(value: String): Long? {
        try { return Instant.parse(value).toEpochMilli() } catch (_: DateTimeParseException) { }
        try { return OffsetDateTime.parse(value).toInstant().toEpochMilli() } catch (_: DateTimeParseException) { }
        val formats = listOf("EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm Z", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd")
        return formats.firstNotNullOfOrNull { pattern ->
            runCatching { SimpleDateFormat(pattern, Locale.US).apply { isLenient = true }.parse(value)?.time }.getOrNull()
        }
    }

    private fun parseDuration(value: String): Long? {
        value.toLongOrNull()?.let { return it * 1000L }
        val parts = value.split(':').mapNotNull(String::toLongOrNull)
        if (parts.isEmpty() || parts.size > 3) return null
        return parts.fold(0L) { total, part -> total * 60 + part }.times(1000L)
    }

    private data class MutableEpisode(
        var title: String = "", var description: String = "", var guid: String = "", var enclosure: String? = null,
        var mimeType: String? = null, var byteLength: Long? = null, var date: Long? = null,
        var durationMs: Long? = null, var image: String? = null
    ) {
        fun toParsed(): ParsedEpisode? {
            val enclosureUrl = enclosure ?: return null
            val resolvedEnclosure = runCatching { URL(enclosureUrl).toString() }.getOrNull() ?: return null
            if (title.isBlank()) title = resolvedEnclosure.substringAfterLast('/').substringBefore('?').ifBlank { "Episodio" }
            val identity = guid.ifBlank { listOf(resolvedEnclosure, date?.toString().orEmpty(), title).joinToString("|") }
            val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
            return ParsedEpisode(digest, guid.ifBlank { digest }, title.trim(), description, resolvedEnclosure,
                mimeType, byteLength, date, durationMs, image)
        }
    }

    companion object { private const val MAX_FEED_BYTES = 5 * 1024 * 1024 }
}

class FeedException(message: String, cause: Throwable? = null) : Exception(message, cause)
