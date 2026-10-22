package com.moviecatalog.movie.artwork

import com.moviecatalog.movie.MovieRepository
import com.moviecatalog.support.GrpcErrors
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

enum class Variant { ORIGINAL, THUMBNAIL }

/** What the download handler needs to stream a file. */
data class ArtworkFile(val path: Path, val contentType: String, val sizeBytes: Long, val version: String)

/**
 * Stores, replaces and removes artwork. The database row and the files change together: new files are
 * written first, the row switches in one transaction, and old files are deleted only after that commit,
 * so a failure at any point leaves a consistent movie (BR-09, BR-11).
 */
@Service
class ArtworkService(
    private val artworks: ArtworkRepository,
    private val movies: MovieRepository,
    private val storage: ArtworkStorage,
    transactionManager: PlatformTransactionManager,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val tx = TransactionTemplate(transactionManager)

    // Striped locks: a movie id always maps to the same lock, so two uploads (or an upload and a delete) for the
    // same movie run one after the other. Different movies may share a lock, which only costs a little waiting.
    // A fixed array cannot lose a lock between "get" and "lock", which a map of locks that are removed could.
    private val locks = Array(STRIPES) { ReentrantLock() }

    /** Runs [block] holding the movie's lock. Callers that also need a transaction open it inside the lock. */
    fun <T> underMovieLock(movieId: UUID, block: () -> T): T =
        locks[Math.floorMod(movieId.hashCode(), STRIPES)].withLock(block)

    private fun <T> locked(movieId: UUID, block: () -> T): T = underMovieLock(movieId, block)

    /** Picture files on disk that no row refers to (for example after a crash). */
    fun findOrphans(): List<String> {
        val known = artworks.findAll().flatMap { listOf(it.originalPath, it.thumbnailPath) }.toSet()
        return storage.listStoredFiles().filter { it !in known }
    }

    /** Logs [findOrphans] once at startup. Log only: nothing is deleted. */
    @EventListener(ApplicationReadyEvent::class)
    fun logOrphans() {
        try {
            val orphans = findOrphans()
            if (orphans.isNotEmpty()) log.warn("{} artwork file(s) on disk have no database row, for example {}", orphans.size, orphans.first())
        } catch (e: Exception) {
            log.warn("Could not check for orphan artwork files", e)
        }
    }

    fun requireMovie(movieId: UUID) {
        if (!movies.existsById(movieId)) throw GrpcErrors.notFound("movie.not_found")
    }

    /** Makes [prepared] the movie's artwork. If anything fails, the prepared files are removed. */
    fun replace(movieId: UUID, prepared: PreparedArtwork): Artwork = locked(movieId) {
        var oldFiles: List<String> = emptyList()
        try {
            val saved = tx.execute {
                if (!movies.existsById(movieId)) throw GrpcErrors.notFound("movie.not_found")
                val existing = artworks.findByMovieId(movieId)
                oldFiles = listOfNotNull(existing?.originalPath, existing?.thumbnailPath)
                val row = existing ?: Artwork(
                    prepared.id, movieId, prepared.info.contentType, prepared.sizeBytes, prepared.info.width, prepared.info.height,
                    prepared.originalPath, prepared.thumbnailPath, prepared.id.toString(), Instant.now(clock),
                )
                if (existing != null) {
                    existing.contentType = prepared.info.contentType
                    existing.sizeBytes = prepared.sizeBytes
                    existing.width = prepared.info.width
                    existing.height = prepared.info.height
                    existing.originalPath = prepared.originalPath
                    existing.thumbnailPath = prepared.thumbnailPath
                    existing.version = prepared.id.toString()
                    existing.createdAt = Instant.now(clock)
                }
                artworks.saveAndFlush(row)
            }!!
            deleteFiles(oldFiles) // after commit: tx.execute has returned
            saved
        } catch (e: DataIntegrityViolationException) {
            deleteFiles(listOf(prepared.originalPath, prepared.thumbnailPath))
            // Only a movie that is really gone is "not found"; any other constraint problem is an internal error.
            if (!movies.existsById(movieId)) throw GrpcErrors.notFound("movie.not_found")
            throw e
        } catch (e: Throwable) {
            deleteFiles(listOf(prepared.originalPath, prepared.thumbnailPath))
            throw e
        }
    }

    fun remove(movieId: UUID) = locked(movieId) {
        val oldFiles = tx.execute {
            if (!movies.existsById(movieId)) throw GrpcErrors.notFound("movie.not_found")
            val existing = artworks.findByMovieId(movieId) ?: throw GrpcErrors.notFound("artwork.not_found")
            artworks.delete(existing)
            artworks.flush()
            listOf(existing.originalPath, existing.thumbnailPath)
        }!!
        deleteFiles(oldFiles)
    }

    fun find(movieId: UUID): Artwork? = artworks.findByMovieId(movieId)

    /** Artwork for many movies at once, keyed by movie id. */
    fun forMovies(movieIds: Collection<UUID>): Map<UUID, Artwork> =
        if (movieIds.isEmpty()) emptyMap() else artworks.findByMovieIdIn(movieIds).associateBy { it.movieId }

    fun fileFor(movieId: UUID, variant: Variant): ArtworkFile {
        val art = artworks.findByMovieId(movieId) ?: run {
            requireMovie(movieId)
            throw GrpcErrors.notFound("artwork.not_found")
        }
        return when (variant) {
            Variant.ORIGINAL -> ArtworkFile(storage.resolve(art.originalPath), art.contentType, art.sizeBytes, art.version)
            Variant.THUMBNAIL -> {
                val path = storage.resolve(art.thumbnailPath)
                ArtworkFile(path, "image/jpeg", java.nio.file.Files.size(path), art.version)
            }
        }
    }

    /** Deletes the files of [movieId]'s artwork once the surrounding transaction commits (movie delete, BR-03). */
    fun deleteFilesAfterCommit(movieId: UUID) {
        val art = artworks.findByMovieId(movieId) ?: return
        val files = listOf(art.originalPath, art.thumbnailPath)
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = deleteFiles(files)
            })
        } else {
            deleteFiles(files)
        }
    }

    // A file that cannot be removed is logged and left; it is unreachable because its row is gone.
    private fun deleteFiles(relative: List<String>) = relative.forEach {
        try {
            storage.delete(it)
        } catch (e: Exception) {
            log.warn("Could not delete an artwork file", e)
        }
    }

    private companion object {
        const val STRIPES = 64
    }
}
