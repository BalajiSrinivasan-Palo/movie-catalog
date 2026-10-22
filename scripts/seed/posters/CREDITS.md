# Seed poster credits

These ten posters are used by the seed data (`scripts/seed/`, D24). Each was downloaded on 2026-10-22 from Wikimedia Commons, where it is marked **public domain**. Commons accepts a file only if it is in the public domain both in the United States and in its country of origin. The usual reasons for these posters are publication before 1931, or publication in the US without a copyright notice or without renewal. Each file's Commons page gives the exact reason.

Files at 800 pixels wide are Commons' own resized copies. `the-general-1926.jpg` was converted from the Commons PNG to JPEG (quality 85) with macOS `sips`. No other changes were made.

| File | Film | Source (Wikimedia Commons) | Original credit on Commons |
|------|------|----------------------------|----------------------------|
| `the-general-1926.jpg` | The General (1926) | https://commons.wikimedia.org/wiki/File:The_General_(1926)_-_Movie_Poster_2.png | United Artists Corporation |
| `the-cabinet-of-dr-caligari-1920.jpg` | The Cabinet of Dr. Caligari (1920) | https://commons.wikimedia.org/wiki/File:Das_Cabinet_des_Dr._Caligari.JPG | See Commons page |
| `sherlock-jr-1924.jpg` | Sherlock Jr. (1924) | https://commons.wikimedia.org/wiki/File:Sherlock_jr_poster.jpg | Unknown author |
| `the-gold-rush-1925.jpg` | The Gold Rush (1925) | https://commons.wikimedia.org/wiki/File:Gold_rush_poster.jpg | Charles Chaplin Productions / United Artists |
| `the-phantom-of-the-opera-1925.jpg` | The Phantom of the Opera (1925) | https://commons.wikimedia.org/wiki/File:Phantom_of_the_opera_1925_poster.jpg | Universal Pictures |
| `battleship-potemkin-1925.jpg` | Battleship Potemkin (1925) | https://commons.wikimedia.org/wiki/File:Vintage_Potemkin.jpg | Not named on the poster |
| `his-girl-friday-1940.jpg` | His Girl Friday (1940) | https://commons.wikimedia.org/wiki/File:His_Girl_Friday_(1940_poster)_crop.jpg | Columbia Pictures |
| `charade-1963.jpg` | Charade (1963) | https://commons.wikimedia.org/wiki/File:Charade_(1963_poster).jpg | Universal Pictures |
| `plan-9-from-outer-space-1957.jpg` | Plan 9 from Outer Space (1957) | https://commons.wikimedia.org/wiki/File:Plan_9_Alternative_poster.jpg | Distributors Corporation of America |
| `house-on-haunted-hill-1959.jpg` | House on Haunted Hill (1959) | https://commons.wikimedia.org/wiki/File:House_on_Haunted_Hill.jpg | Reynold Brown (artist) |

Film facts (titles, years, runtimes, genres, cast and crew) are public information. The synopses and short biographies in `scripts/seed/films.mjs` were written for this project.

## Local-only posters (not in this repository)

*Speed* (1994) and *Harry Potter and the Philosopher's Stone* (2001) are in the seed data, but their posters are copyrighted, so they are **not committed** (D25). The seed uploads them only when these files exist on the machine running it, in the git-ignored folder `data/local-posters/`:

- `speed-1994.webp`
- `harry-potter-and-the-philosophers-stone-2001.jpg`

Without them, these two films show the app's "No image" placeholder.

