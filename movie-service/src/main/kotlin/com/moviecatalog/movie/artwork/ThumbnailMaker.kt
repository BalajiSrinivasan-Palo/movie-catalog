package com.moviecatalog.movie.artwork

import com.moviecatalog.support.GrpcErrors
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.Path
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.FileImageOutputStream

/**
 * Makes the JPEG thumbnail (S-302). Large images are read with subsampling, so a 6000x6000 original is
 * never fully decoded into memory. Transparency is flattened onto white, because JPEG has no alpha.
 */
class ThumbnailMaker(private val limits: ArtworkLimits) {
    /** Writes the thumbnail to [target]. Any decoding problem means the upload is rejected as corrupt. */
    fun make(source: Path, info: ImageInfo, target: Path) {
        try {
            val decoded = decode(source, info)
            val scaled = scale(decoded)
            write(scaled, target)
        } catch (e: IOException) {
            throw corrupt()
        } catch (e: RuntimeException) {
            throw corrupt()
        }
    }

    private fun decode(source: Path, info: ImageInfo): BufferedImage {
        val reader = ImageInspector.readerFor(info.contentType) ?: throw IOException("no reader")
        try {
            ImageIO.createImageInputStream(source.toFile()).use { input ->
                reader.setInput(input, true, true)
                val param = reader.defaultReadParam
                // Read at most about twice the thumbnail width: enough quality, bounded memory.
                val factor = maxOf(1, info.width / (limits.thumbnailWidth * 2))
                if (factor > 1) param.setSourceSubsampling(factor, factor, 0, 0)
                return reader.read(0, param)
            }
        } finally {
            reader.dispose()
        }
    }

    private fun scale(image: BufferedImage): BufferedImage {
        val width = minOf(limits.thumbnailWidth, image.width) // never enlarge
        val height = maxOf(1, Math.round(image.height.toDouble() * width / image.width).toInt())
        val out = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        try {
            g.color = Color.WHITE
            g.fillRect(0, 0, width, height)
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.drawImage(image, 0, 0, width, height, null)
        } finally {
            g.dispose()
        }
        return out
    }

    private fun write(image: BufferedImage, target: Path) {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        try {
            val param = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = QUALITY
            }
            FileImageOutputStream(target.toFile()).use { out ->
                writer.output = out
                writer.write(null, IIOImage(image, null, null), param)
            }
        } finally {
            writer.dispose()
        }
    }

    private fun corrupt() = GrpcErrors.invalidArgument("file" to "artwork.corrupt")

    companion object {
        const val QUALITY = 0.82f
    }
}
