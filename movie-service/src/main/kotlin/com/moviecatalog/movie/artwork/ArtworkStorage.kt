package com.moviecatalog.movie.artwork

import org.slf4j.LoggerFactory
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.io.path.deleteIfExists
import kotlin.io.path.isRegularFile

/**
 * The directory that holds artwork files. File names are generated here, never taken from the client
 * (NFR-SEC-02), and every path is checked to stay inside [root], so a traversal name cannot escape.
 *
 * Layout: `{root}/tmp/{uuid}.part` for uploads in flight, `{root}/{aa}/{id}-original.{ext}` and
 * `{root}/{aa}/{id}-thumb.jpg` for committed files, where `aa` is the first two characters of the id.
 */
class ArtworkStorage(root: Path) {
    val root: Path = root.toAbsolutePath().normalize()
    private val tmp: Path = this.root.resolve("tmp")
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        Files.createDirectories(tmp)
        sweepTemp()
    }

    /** Removes `.part` files left by an interrupted run. Nothing can be in flight while the service starts. */
    fun sweepTemp() {
        Files.newDirectoryStream(tmp, "*.part").use { stale ->
            stale.forEach {
                log.info("Removing stale upload file")
                it.deleteIfExists()
            }
        }
    }

    fun newTemp(): Path = Files.createFile(tmp.resolve("${UUID.randomUUID()}.part"))

    /** Gives a temp file its final, server-generated name. The move is atomic, so readers never see half a file. */
    fun commit(temp: Path, relative: String) {
        val target = resolve(relative)
        Files.createDirectories(target.parent)
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(temp, target)
        }
    }

    /** Deletes a committed file. Missing files are fine: cleanup must be safe to repeat. */
    fun delete(relative: String?) {
        if (relative == null) return
        resolve(relative).deleteIfExists()
    }

    /** The absolute path of a stored file, or an error if [relative] would leave the storage directory. */
    fun resolve(relative: String): Path {
        val path = root.resolve(relative).normalize()
        require(path.startsWith(root) && path != root) { "path outside artwork directory" }
        return path
    }

    fun exists(relative: String): Boolean = resolve(relative).isRegularFile()

    /** Relative names of every stored picture file (not temp files), for the startup orphan check. */
    fun listStoredFiles(): List<String> =
        if (!Files.isDirectory(root)) emptyList()
        else Files.walk(root).use { stream ->
            stream.filter { it.isRegularFile() && !it.startsWith(tmp) }
                .map { root.relativize(it).toString().replace('\\', '/') }
                .toList()
        }

    fun tempFileCount(): Int = Files.newDirectoryStream(tmp).use { it.count() }

    companion object {
        fun originalName(id: UUID, extension: String) = "${id.toString().take(2)}/$id-original.$extension"
        fun thumbnailName(id: UUID) = "${id.toString().take(2)}/$id-thumb.jpg"
    }
}
