package it.mirkougolinidev.rfeedreader.core.download

import it.mirkougolinidev.rfeedreader.core.database.EpisodeEntity
import java.io.File

object OfflineFileVerifier {
    fun verifiedFile(episodesDirectory: File, episode: EpisodeEntity): File? {
        if (episode.downloadState != "DOWNLOADED") return null
        val path = episode.localPath?.let(::File) ?: return null
        return runCatching {
            val root = episodesDirectory.canonicalFile
            val file = path.canonicalFile
            // downloadedBytes e' quanto abbiamo scritto davvero e resta il riferimento affidabile.
            // Il byteLength dichiarato nel feed no: Radio 24 lo invia a 1000 per ogni episodio e
            // confrontarlo rendeva non riproducibili file scaricati correttamente.
            file.takeIf {
                it.isFile && it.length() > 0L && it.length() == episode.downloadedBytes &&
                    it.path.startsWith(root.path + File.separator)
            }
        }.getOrNull()
    }
}
