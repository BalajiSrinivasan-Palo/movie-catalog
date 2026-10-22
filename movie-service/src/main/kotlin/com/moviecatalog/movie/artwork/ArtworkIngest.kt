package com.moviecatalog.movie.artwork

import com.moviecatalog.support.GrpcErrors
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.deleteIfExists
import kotlin.io.path.fileSize

/** An upload that passed every check; its files already have their final names. */
data class PreparedArtwork(
    val id: UUID,
    val info: ImageInfo,
    val sizeBytes: Long,
    val originalPath: String,
    val thumbnailPath: String,
)

/**
 * Turns a stream of bytes into stored files. Bytes go straight from each chunk to a temp file, so memory
 * use does not depend on the image size (NFR-ARCH-05), and the size limit is checked as bytes arrive (S-301 AC1).
 * Nothing becomes visible until every check has passed, and any failure removes what was written (BR-11).
 */
class ArtworkIngest(
    private val storage: ArtworkStorage,
    private val limits: ArtworkLimits = ArtworkLimits(),
) {
    private val inspector = ImageInspector(limits)
    private val thumbnails = ThumbnailMaker(limits)

    fun begin(): Session = Session()

    inner class Session {
        private val temp: Path = storage.newTemp()
        private var out: OutputStream? = Files.newOutputStream(temp)
        private var total = 0L
        private var thumbTemp: Path? = null
        private val committed = mutableListOf<String>()

        /** Writes [chunk]; fails and cleans up once the limit is exceeded. */
        fun write(chunk: com.google.protobuf.ByteString) {
            val stream = out ?: error("session closed")
            total += chunk.size()
            if (total > limits.maxBytes) {
                abort()
                throw GrpcErrors.invalidArgument("file" to "artwork.too_large")
            }
            chunk.writeTo(stream)
        }

        /** Validates, makes the thumbnail and gives both files their final names. Cleans up on any failure. */
        fun finish(): PreparedArtwork {
            try {
                out?.close()
                out = null
                val info = inspector.inspect(temp)
                val thumb = storage.newTemp().also { thumbTemp = it }
                thumbnails.make(temp, info, thumb)
                val id = UUID.randomUUID()
                val originalPath = ArtworkStorage.originalName(id, info.extension)
                val thumbnailPath = ArtworkStorage.thumbnailName(id)
                storage.commit(temp, originalPath).also { committed += originalPath }
                storage.commit(thumb, thumbnailPath).also { committed += thumbnailPath }
                thumbTemp = null
                return PreparedArtwork(id, info, storage.resolve(originalPath).fileSize(), originalPath, thumbnailPath)
            } catch (e: Throwable) {
                committed.forEach { storage.delete(it) }
                abort()
                throw e
            }
        }

        /** Removes everything this session wrote. Safe to call more than once. */
        fun abort() {
            runCatching { out?.close() }
            out = null
            temp.deleteIfExists()
            thumbTemp?.deleteIfExists()
        }
    }
}
