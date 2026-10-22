package com.moviecatalog.movie

import com.moviecatalog.contracts.movie.v1.CreditRole as CreditRoleMessage
import com.moviecatalog.contracts.movie.v1.Genre as GenreMessage
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import java.time.Instant
import java.util.Locale
import java.util.UUID

/** The fixed genre list (BRD section 7). The stored value is the enum name; [display] is what people see and search. */
enum class Genre(val display: String, val message: GenreMessage) {
    ACTION("Action", GenreMessage.GENRE_ACTION),
    ADVENTURE("Adventure", GenreMessage.GENRE_ADVENTURE),
    ANIMATION("Animation", GenreMessage.GENRE_ANIMATION),
    COMEDY("Comedy", GenreMessage.GENRE_COMEDY),
    CRIME("Crime", GenreMessage.GENRE_CRIME),
    DOCUMENTARY("Documentary", GenreMessage.GENRE_DOCUMENTARY),
    DRAMA("Drama", GenreMessage.GENRE_DRAMA),
    FANTASY("Fantasy", GenreMessage.GENRE_FANTASY),
    HORROR("Horror", GenreMessage.GENRE_HORROR),
    MYSTERY("Mystery", GenreMessage.GENRE_MYSTERY),
    ROMANCE("Romance", GenreMessage.GENRE_ROMANCE),
    SCI_FI("Sci-Fi", GenreMessage.GENRE_SCI_FI),
    THRILLER("Thriller", GenreMessage.GENRE_THRILLER),
    WAR("War", GenreMessage.GENRE_WAR),
    WESTERN("Western", GenreMessage.GENRE_WESTERN),
    ;

    companion object {
        /** Null for GENRE_UNSPECIFIED and for values this version does not know. */
        fun from(message: GenreMessage): Genre? = entries.firstOrNull { it.message == message }
    }
}

enum class CreditRole(val message: CreditRoleMessage) {
    ACTOR(CreditRoleMessage.CREDIT_ROLE_ACTOR),
    DIRECTOR(CreditRoleMessage.CREDIT_ROLE_DIRECTOR),
    WRITER(CreditRoleMessage.CREDIT_ROLE_WRITER),
    PRODUCER(CreditRoleMessage.CREDIT_ROLE_PRODUCER),
    ;

    companion object {
        fun from(message: CreditRoleMessage): CreditRole? = entries.firstOrNull { it.message == message }
    }
}

/** `titleKey` is the trimmed, lower-cased title: the unique key (with year) and the sort and search column. */
@Entity
@Table(name = "movie")
class Movie(
    @Id var id: UUID,
    var title: String,
    var titleKey: String,
    var releaseYear: Int,
    var runtimeMinutes: Int?,
    var synopsis: String,
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "movie_genre", joinColumns = [JoinColumn(name = "movie_id")])
    @Enumerated(EnumType.STRING)
    @Column(name = "genre")
    var genres: MutableSet<Genre>,
    var createdAt: Instant,
    var updatedAt: Instant,
)

/** References the person by id only: people-service owns people (BR-06). */
@Entity
@Table(name = "credit")
class Credit(
    @Id var id: UUID,
    var movieId: UUID,
    var personId: UUID,
    @Enumerated(EnumType.STRING) var role: CreditRole,
    var characterName: String?,
    var createdAt: Instant,
)

fun titleKeyOf(title: String): String = title.trim().lowercase(Locale.ROOT)
