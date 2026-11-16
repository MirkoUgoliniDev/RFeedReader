package it.mirkougolinidev.rfeedreader.core.common

/**
 * Feed proposti alla prima installazione. Ogni indirizzo viene aggiunto una volta sola:
 * se l'utente rimuove il podcast non viene riproposto agli avvii successivi.
 */
object DefaultFeeds {
    val URLS: List<String> = listOf(
        // Scienza, tecnologia e cultura digitale
        "https://digitalia.fm/feeds/digitalia.xml",
        "https://www.radio24.ilsole24ore.com/podcast/2024.xml",
        "https://www.spreaker.com/show/1977562/episodes/feed",
        // Storia e racconto
        "https://www.spreaker.com/show/4255213/episodes/feed",
        "https://feeds.megaphone.fm/IPS8860333488",
        "https://www.spreaker.com/show/3032568/episodes/feed",
        // Economia, attualità e finanza
        "https://feeds.megaphone.fm/IPS7485736463",
        "https://www.radio24.ilsole24ore.com/podcast/focus-economia.xml",
        "https://www.radio24.ilsole24ore.com/podcast/24mattino.xml",
        "https://feeds.megaphone.fm/RICCARDOSPADA2271908995",
        // Intrattenimento e design
        "https://feeds.megaphone.fm/AMCS7780999547",
        "https://anchor.fm/s/29e4e570/podcast/rss"
    )
}
