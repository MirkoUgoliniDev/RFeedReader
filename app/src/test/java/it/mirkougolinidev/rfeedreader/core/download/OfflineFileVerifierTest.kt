package it.mirkougolinidev.rfeedreader.core.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import it.mirkougolinidev.rfeedreader.core.database.EpisodeEntity

class OfflineFileVerifierTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun acceptsOnlyNonEmptyDownloadedFileInsidePrivateEpisodeDirectory() {
        val root = folder.newFolder("episodes")
        val audio = folder.newFile("episodes/episode.mp3").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        assertEquals(audio.canonicalFile, OfflineFileVerifier.verifiedFile(root, episode("DOWNLOADED", audio.absolutePath, 3L)))
    }

    @Test
    fun rejectsQueuedEpisodeEvenWhenFileExists() {
        val root = folder.newFolder("episodes")
        val audio = folder.newFile("episodes/episode.mp3").apply { writeBytes(byteArrayOf(1)) }

        assertNull(OfflineFileVerifier.verifiedFile(root, episode("QUEUED", audio.absolutePath, 1L)))
    }

    @Test
    fun rejectsEmptyAndOutsideFiles() {
        val root = folder.newFolder("episodes")
        val empty = folder.newFile("episodes/empty.mp3")
        val outside = folder.newFile("outside.mp3").apply { writeBytes(byteArrayOf(1)) }

        assertNull(OfflineFileVerifier.verifiedFile(root, episode("DOWNLOADED", empty.absolutePath, 0L)))
        assertNull(OfflineFileVerifier.verifiedFile(root, episode("DOWNLOADED", outside.absolutePath, 1L)))
    }

    @Test
    fun rejectsFileWhoseLengthDoesNotMatchDownloadedMetadata() {
        val root = folder.newFolder("episodes")
        val audio = folder.newFile("episodes/episode.mp3").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        assertNull(OfflineFileVerifier.verifiedFile(root, episode("DOWNLOADED", audio.absolutePath, 2L)))
    }

    private fun episode(state: String, path: String, length: Long) = EpisodeEntity(
        id = "episode", podcastId = "podcast", stableKey = "guid", guid = "guid", title = "Titolo",
        description = "", enclosureUrl = "https://example.test/audio.mp3", mimeType = "audio/mpeg",
        byteLength = length, publishedAt = null, durationMs = null, imageUrl = null,
        downloadState = state, localPath = path, downloadedBytes = length
    )
}
