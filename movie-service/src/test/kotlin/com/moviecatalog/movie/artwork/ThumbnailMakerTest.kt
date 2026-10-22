package com.moviecatalog.movie.artwork

import com.moviecatalog.support.GrpcErrors
import io.grpc.StatusRuntimeException
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.fileSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ThumbnailMakerTest {
    private val dir: Path = Files.createTempDirectory("thumb-test")
    private val limits = ArtworkLimits()
    private val inspector = ImageInspector(limits)
    private val maker = ThumbnailMaker(limits)

    private fun thumbnailOf(bytes: ByteArray): Path {
        val source = dir.resolve("source").also { Files.write(it, bytes) }
        val target = dir.resolve("thumb.jpg")
        maker.make(source, inspector.inspect(source), target)
        return target
    }

    @Test
    fun `a large poster becomes a 320 px wide JPEG under 60 KB`() {
        val target = thumbnailOf(TestImages.jpeg(2000, 3000))
        val image = ImageIO.read(target.toFile())
        assertEquals(320, image.width)
        assertEquals(480, image.height)
        assertTrue(target.fileSize() < 60 * 1024, "thumbnail was ${target.fileSize()} bytes")
    }

    @Test
    fun `a small picture is not enlarged`() {
        val image = ImageIO.read(thumbnailOf(TestImages.png(100, 50)).toFile())
        assertEquals(100, image.width)
        assertEquals(50, image.height)
    }

    @Test
    fun `transparency is flattened onto white`() {
        val image = ImageIO.read(thumbnailOf(TestImages.transparentPng(64, 64)).toFile())
        val rgb = image.getRGB(10, 10)
        listOf(16, 8, 0).forEach { shift -> assertTrue(((rgb shr shift) and 0xFF) > 240, "pixel was ${Integer.toHexString(rgb)}") }
    }

    @Test
    fun `WebP input gives a JPEG thumbnail`() {
        val image = ImageIO.read(thumbnailOf(TestImages.webp()).toFile())
        assertEquals(1, image.width)
    }

    @Test
    fun `a PNG cut off in the middle is rejected as corrupt`() {
        val png = TestImages.png(300, 300)
        val source = dir.resolve("cut").also { Files.write(it, png.copyOf(png.size / 2)) }
        val info = ImageInfo("image/png", "png", 300, 300)
        val e = assertFailsWith<StatusRuntimeException> { maker.make(source, info, dir.resolve("cut-thumb.jpg")) }
        assertEquals("artwork.corrupt", GrpcErrors.detailOf(e)!!.violationsList.single().messageKey)
    }
}
