package it.mirkougolinidev.rfeedreader.core.network

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import it.mirkougolinidev.rfeedreader.core.database.PodcastEntity
import org.junit.Assert.assertTrue
import org.junit.Test

class OpmlDocumentTest {
    @Test
    fun exportsPodcastSubscriptionsAsEscapedOpml() {
        val output = ByteArrayOutputStream()
        OpmlDocument.write(
            listOf(
                PodcastEntity("id", "https://example.test/feed?a=1&b=2", "https://example.test/feed", "News & More", "", null, null, 0L)
            ), output
        )

        val xml = output.toString(StandardCharsets.UTF_8.name())
        assertTrue(xml.contains("<opml version=\"2.0\">"))
        assertTrue(xml.contains("text=\"News &amp; More\""))
        assertTrue(xml.contains("xmlUrl=\"https://example.test/feed?a=1&amp;b=2\""))
    }
}
