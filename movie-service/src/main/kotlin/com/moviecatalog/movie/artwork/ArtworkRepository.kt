package com.moviecatalog.movie.artwork

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ArtworkRepository : JpaRepository<Artwork, UUID> {
    fun findByMovieId(movieId: UUID): Artwork?

    /** One query for a whole list page, so listing movies does not cost one lookup per movie. */
    fun findByMovieIdIn(movieIds: Collection<UUID>): List<Artwork>
}
