package com.moviecatalog.movie.artwork

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Path

@Configuration
class ArtworkConfig {
    @Bean
    fun artworkLimits(
        @Value("\${artwork.max-bytes}") maxBytes: Long,
        @Value("\${artwork.max-dimension}") maxDimension: Int,
        @Value("\${artwork.thumbnail-width}") thumbnailWidth: Int,
    ) = ArtworkLimits(maxBytes, maxDimension, thumbnailWidth)

    @Bean
    fun artworkStorage(@Value("\${artwork.dir}") dir: String) = ArtworkStorage(Path.of(dir))

    @Bean
    fun artworkIngest(storage: ArtworkStorage, limits: ArtworkLimits) = ArtworkIngest(storage, limits)
}
