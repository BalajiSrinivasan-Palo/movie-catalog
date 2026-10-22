package com.moviecatalog.movie.artwork

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import javax.imageio.ImageIO

/** Small images made in code, so tests need no binary fixtures. */
object TestImages {
    /** A 1x1 lossless WebP, as base64, so WebP handling is tested without an encoder. */
    const val WEBP_BASE64 = "UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA=="

    fun webp(): ByteArray = Base64.getDecoder().decode(WEBP_BASE64)

    /** A smooth, colourful picture that resembles a poster more than flat colour does. */
    fun picture(width: Int, height: Int, alpha: Boolean = false): BufferedImage {
        val image = BufferedImage(width, height, if (alpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        for (y in 0 until height step maxOf(1, height / 64)) {
            g.color = Color(40 + (y * 180 / height), 60, 200 - (y * 150 / height))
            g.fillRect(0, y, width, maxOf(1, height / 64))
        }
        g.color = Color(250, 220, 60)
        g.fillOval(width / 4, height / 4, width / 2, height / 2)
        g.dispose()
        return image
    }

    fun png(width: Int, height: Int, alpha: Boolean = false): ByteArray = encode(picture(width, height, alpha), "png")

    fun jpeg(width: Int, height: Int): ByteArray = encode(picture(width, height), "jpeg")

    /** A PNG whose pixels are all fully transparent. */
    fun transparentPng(width: Int, height: Int): ByteArray =
        encode(BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png")

    private fun encode(image: BufferedImage, format: String): ByteArray =
        ByteArrayOutputStream().also { check(ImageIO.write(image, format, it)) }.toByteArray()

    /**
     * A grey PNG of any size written without building the picture in memory. Rows are all zero, so the
     * file stays small even at 6000x6000, which makes the dimension limit testable cheaply.
     */
    fun solidGrayPng(width: Int, height: Int): ByteArray {
        val data = ByteArrayOutputStream()
        val deflater = Deflater(Deflater.BEST_SPEED)
        DeflaterOutputStream(data, deflater).use { z ->
            val row = ByteArray(width + 1) // filter byte 0, then zero pixels
            repeat(height) { z.write(row) }
        }
        deflater.end()
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        chunk(out, "IHDR", ByteArrayOutputStream().also {
            writeInt(it, width); writeInt(it, height); it.write(byteArrayOf(8, 0, 0, 0, 0))
        }.toByteArray())
        chunk(out, "IDAT", data.toByteArray())
        chunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun chunk(out: OutputStream, type: String, body: ByteArray) {
        writeInt(out, body.size)
        val typed = type.toByteArray(Charsets.US_ASCII) + body
        out.write(typed)
        writeInt(out, CRC32().also { it.update(typed) }.value.toInt())
    }

    private fun writeInt(out: OutputStream, v: Int) {
        out.write(v ushr 24); out.write(v ushr 16); out.write(v ushr 8); out.write(v)
    }

    /** The same bytes followed by zero padding up to [size]; decoders ignore data after the image ends (PNG). */
    fun padded(png: ByteArray, size: Int): ByteArray = png.copyOf(size)
}
