package it.mirkougolinidev.rfeedreader.core.network

import it.mirkougolinidev.rfeedreader.core.database.PodcastEntity
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object OpmlDocument {
    fun read(input: InputStream): List<String> {
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
            setInput(input, null)
        }
        var root: String? = null
        val urls = linkedSetOf<String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                if (root == null) root = parser.name.substringAfter(':').lowercase()
                if (parser.name.substringAfter(':').equals("outline", ignoreCase = true)) {
                    val type = parser.attribute("type")
                    val url = parser.attribute("xmlUrl")?.trim()
                    val supportedType = type.isNullOrBlank() || type.equals("rss", ignoreCase = true) || type.equals("atom", ignoreCase = true)
                    if (url != null && supportedType &&
                        (url.startsWith("http://", true) || url.startsWith("https://", true))) urls += url
                }
            }
            event = parser.next()
        }
        require(root == "opml") { "Il file selezionato non è un documento OPML." }
        require(urls.isNotEmpty()) { "Il file OPML non contiene feed RSS/Atom." }
        return urls.toList()
    }

    fun write(podcasts: List<PodcastEntity>, output: OutputStream) {
        val writer = OutputStreamWriter(output, StandardCharsets.UTF_8)
        writer.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        writer.appendLine("<opml version=\"2.0\"><head><title>RFeedReader</title></head><body>")
        podcasts.forEach { podcast ->
            writer.append("<outline type=\"rss\" text=\"").append(escape(podcast.title))
                .append("\" title=\"").append(escape(podcast.title))
                .append("\" xmlUrl=\"").append(escape(podcast.feedUrl)).append("\"/>")
                .appendLine()
        }
        writer.appendLine("</body></opml>")
        writer.flush()
    }

    private fun XmlPullParser.attribute(name: String): String? =
        getAttributeValue(null, name) ?: (0 until attributeCount).firstNotNullOfOrNull { index ->
            if (getAttributeName(index).equals(name, ignoreCase = true)) getAttributeValue(index) else null
        }

    private fun escape(value: String): String = value.replace("&", "&amp;").replace("\"", "&quot;")
        .replace("<", "&lt;").replace(">", "&gt;").replace("'", "&apos;")
}
