package com.moviecatalog.movie.artwork

import com.moviecatalog.support.GrpcErrors
import io.grpc.StatusRuntimeException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ImageInspectorTest {
    private val dir: Path = Files.createTempDirectory("inspector-test")
    private val inspector = ImageInspector(ArtworkLimits())

    private fun file(bytes: ByteArray, name: String = "upload.bin"): Path = dir.resolve(name).also { Files.write(it, bytes) }

    private fun rejection(bytes: ByteArray, limits: ArtworkLimits = ArtworkLimits()): String {
        val e = assertFailsWith<StatusRuntimeException> { ImageInspector(limits).inspect(file(bytes)) }
        return GrpcErrors.detailOf(e)!!.violationsList.single().messageKey
    }

    @Test
    fun `detects PNG JPEG and WebP from content and reads their size`() {
        assertEquals(ImageInfo("image/png", "png", 40, 30), inspector.inspect(file(TestImages.png(40, 30))))
        assertEquals(ImageInfo("image/jpeg", "jpg", 40, 30), inspector.inspect(file(TestImages.jpeg(40, 30))))
        val webp = inspector.inspect(file(TestImages.webp()))
        assertEquals("image/webp", webp.contentType)
        assertEquals(1, webp.width)
        assertEquals(1, webp.height)
    }

    @Test
    fun `the file name does not matter, only the bytes`() {
        val info = inspector.inspect(file(TestImages.png(5, 5), "poster.jpg"))
        assertEquals("image/png", info.contentType)
        assertEquals("artwork.type.unsupported", rejection("<html>not an image</html>".toByteArray()))
    }

    @Test
    fun `rejects empty files and unsupported types`() {
        assertEquals("artwork.empty", rejection(ByteArray(0)))
        assertEquals("artwork.type.unsupported", rejection("GIF89a....".toByteArray()))
        assertEquals("artwork.type.unsupported", rejection(byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D))) // %PDF-
    }

    @Test
    fun `rejects truncated JPEG and WebP files`() {
        val jpeg = TestImages.jpeg(60, 40)
        assertEquals("artwork.corrupt", rejection(jpeg.copyOf(jpeg.size - 10)))
        val webp = TestImages.webp()
        assertEquals("artwork.corrupt", rejection(webp.copyOf(webp.size - 4)))
    }

    @Test
    fun `rejects pictures larger than the dimension limit and accepts the limit itself`() {
        val small = ArtworkLimits(maxDimension = 10)
        assertEquals("artwork.dimensions.too_large", rejection(TestImages.png(11, 5), small))
        assertEquals("artwork.dimensions.too_large", rejection(TestImages.png(5, 11), small))
        assertEquals(10, ImageInspector(small).inspect(file(TestImages.png(10, 10))).width)
    }
}
