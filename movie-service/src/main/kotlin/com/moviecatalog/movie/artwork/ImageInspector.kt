package com.moviecatalog.movie.artwork

import com.moviecatalog.support.GrpcErrors
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import kotlin.io.path.fileSize

data class ImageInfo(val contentType: String, val extension: String, val width: Int, val height: Int)

data class ArtworkLimits(val maxBytes: Long = 5L * 1024 * 1024, val maxDimension: Int = 6000, val thumbnailWidth: Int = 320)

/**
 * Decides what an uploaded file really is, from its bytes only (BR-08): the file name and the type the
 * client declared are never looked at. Dimensions come from the image header, before any full decode.
 */
class ImageInspector(private val limits: ArtworkLimits) {
    fun inspect(file: Path): ImageInfo {
        val size = file.fileSize()
        if (size == 0L) throw rejected("artwork.empty")
        val type = sniff(file) ?: throw rejected("artwork.type.unsupported")
        if (!structureComplete(type, file, size)) throw rejected("artwork.corrupt")
        val (width, height) = try {
            readHeaderSize(type.contentType, file)
        } catch (e: IOException) {
            throw rejected("artwork.corrupt")
        } catch (e: RuntimeException) {
            throw rejected("artwork.corrupt")
        }
        if (width < 1 || height < 1) throw rejected("artwork.corrupt")
        if (width > limits.maxDimension || height > limits.maxDimension) throw rejected("artwork.dimensions.too_large")
        return ImageInfo(type.contentType, type.extension, width, height)
    }

    private enum class Kind(val contentType: String, val extension: String) {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp"),
    }

    private fun sniff(file: Path): Kind? {
        val head = ByteArray(12)
        val read = Files.newInputStream(file).use { it.readNBytes(head, 0, head.size) }
        fun startsWith(vararg bytes: Int) = read >= bytes.size && bytes.indices.all { head[it] == bytes[it].toByte() }
        return when {
            startsWith(0xFF, 0xD8, 0xFF) -> Kind.JPEG
            startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> Kind.PNG
            read >= 12 && startsWith('R'.code, 'I'.code, 'F'.code, 'F'.code) &&
                head[8] == 'W'.code.toByte() && head[9] == 'E'.code.toByte() && head[10] == 'B'.code.toByte() && head[11] == 'P'.code.toByte() -> Kind.WEBP
            else -> null
        }
    }

    /**
     * Cheap completeness checks for what a header read cannot see: JPEG decoders tolerate a missing tail, so
     * require the end-of-image marker; a WebP states its own length. PNG truncation is caught when decoding.
     */
    private fun structureComplete(kind: Kind, file: Path, size: Long): Boolean = when (kind) {
        Kind.JPEG -> {
            // Allow zero padding after the marker, which some encoders write.
            val tail = ByteArray(minOf(size, 64L).toInt())
            java.io.RandomAccessFile(file.toFile(), "r").use { f ->
                f.seek(size - tail.size)
                f.readFully(tail)
            }
            var end = tail.size
            while (end > 0 && tail[end - 1] == 0.toByte()) end--
            end >= 2 && tail[end - 2] == 0xFF.toByte() && tail[end - 1] == 0xD9.toByte()
        }
        Kind.WEBP -> {
            val head = ByteArray(8)
            Files.newInputStream(file).use { it.readNBytes(head, 0, 8) }
            val declared = (head[4].toLong() and 0xFF) or ((head[5].toLong() and 0xFF) shl 8) or
                ((head[6].toLong() and 0xFF) shl 16) or ((head[7].toLong() and 0xFF) shl 24)
            declared + 8 <= size
        }
        Kind.PNG -> true
    }

    private fun readHeaderSize(contentType: String, file: Path): Pair<Int, Int> {
        val reader = readerFor(contentType) ?: throw IOException("no reader for $contentType")
        try {
            ImageIO.createImageInputStream(file.toFile()).use { input ->
                reader.setInput(input, true, true)
                return reader.getWidth(0) to reader.getHeight(0)
            }
        } finally {
            reader.dispose()
        }
    }

    private fun rejected(key: String) = GrpcErrors.invalidArgument("file" to key)

    companion object {
        fun readerFor(contentType: String): ImageReader? =
            ImageIO.getImageReadersByMIMEType(contentType).asSequence().firstOrNull()
    }
}
