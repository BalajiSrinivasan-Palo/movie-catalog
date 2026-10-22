// Real films for the seed. Facts (titles, years, runtimes, genres, cast and crew) are public information;
// synopses and short bios are our own words. A birth date is left null where it is not certain.
// Ten films have public-domain posters committed in posters/ (D24, posters/CREDITS.md). Films marked
// `localPoster: true` have copyrighted posters that are never committed: the seed reads them from the
// git-ignored data/local-posters/ when present and otherwise leaves the placeholder (D25).

export const PEOPLE = [
	{ name: 'Buster Keaton', birthDate: '1895-10-04', bio: 'American actor and filmmaker, a master of silent physical comedy.' },
	{ name: 'Clyde Bruckman', birthDate: '1894-09-20', bio: 'American writer and director of silent and early sound comedies.' },
	{ name: 'Marion Mack', birthDate: '1902-04-09', bio: 'American actress best known for her role in The General.' },
	{ name: 'Joseph M. Schenck', birthDate: '1876-12-25', bio: 'American film producer and studio executive.' },
	{ name: 'Kathryn McGuire', birthDate: '1903-12-06', bio: 'American actress and dancer of the silent era.' },
	{ name: 'Robert Wiene', birthDate: '1873-04-27', bio: 'German director of the Expressionist era.' },
	{ name: 'Carl Mayer', birthDate: '1894-11-20', bio: 'Austrian screenwriter of German silent cinema.' },
	{ name: 'Hans Janowitz', birthDate: '1890-12-02', bio: 'Czech-Austrian writer who co-wrote The Cabinet of Dr. Caligari.' },
	{ name: 'Werner Krauss', birthDate: '1884-06-23', bio: 'German stage and screen actor.' },
	{ name: 'Conrad Veidt', birthDate: '1893-01-22', bio: 'German actor of silent and sound films in Europe and Hollywood.' },
	{ name: 'Lil Dagover', birthDate: '1887-09-30', bio: 'German actress whose career ran from silent films to television.' },
	{ name: 'Erich Pommer', birthDate: '1889-07-20', bio: 'German film producer of the Weimar era.' },
	{ name: 'Charlie Chaplin', birthDate: '1889-04-16', bio: 'English comic actor and filmmaker, creator of the Tramp.' },
	{ name: 'Georgia Hale', birthDate: null, bio: 'American actress of the silent era.' },
	{ name: 'Mack Swain', birthDate: '1876-02-16', bio: 'American comic actor of the silent era.' },
	{ name: 'Rupert Julian', birthDate: '1879-01-25', bio: 'New Zealand-born actor and director in Hollywood.' },
	{ name: 'Gaston Leroux', birthDate: '1868-05-06', bio: 'French journalist and novelist, author of The Phantom of the Opera.' },
	{ name: 'Lon Chaney', birthDate: '1883-04-01', bio: 'American actor known for his transformations and make-up.' },
	{ name: 'Mary Philbin', birthDate: null, bio: 'American actress of the silent era.' },
	{ name: 'Norman Kerry', birthDate: '1894-06-16', bio: 'American actor of silent films.' },
	{ name: 'Carl Laemmle', birthDate: '1867-01-17', bio: 'German-born American producer and founder of Universal Pictures.' },
	{ name: 'Sergei Eisenstein', birthDate: '1898-01-22', bio: 'Soviet director and theorist of film montage.' },
	{ name: 'Nina Agadzhanova', birthDate: null, bio: 'Soviet screenwriter.' },
	{ name: 'Aleksandr Antonov', birthDate: null, bio: 'Soviet actor.' },
	{ name: 'Howard Hawks', birthDate: '1896-05-30', bio: 'American director who worked across comedy, westerns and crime.' },
	{ name: 'Charles Lederer', birthDate: '1910-12-31', bio: 'American screenwriter.' },
	{ name: 'Cary Grant', birthDate: '1904-01-18', bio: 'English-born American actor, a leading man of classic Hollywood.' },
	{ name: 'Rosalind Russell', birthDate: '1907-06-04', bio: 'American actress known for fast-talking comedy roles.' },
	{ name: 'Ralph Bellamy', birthDate: '1904-06-17', bio: 'American actor of stage, film and television.' },
	{ name: 'Stanley Donen', birthDate: '1924-04-13', bio: 'American director and choreographer.' },
	{ name: 'Peter Stone', birthDate: '1930-02-27', bio: 'American writer for film, theatre and television.' },
	{ name: 'Audrey Hepburn', birthDate: '1929-05-04', bio: 'British actress and humanitarian.' },
	{ name: 'Walter Matthau', birthDate: '1920-10-01', bio: 'American actor known for comedy roles.' },
	{ name: 'Ed Wood', birthDate: '1924-10-10', bio: 'American low-budget filmmaker of science fiction and horror.' },
	{ name: 'Bela Lugosi', birthDate: '1882-10-20', bio: 'Hungarian-American actor, famous as Dracula.' },
	{ name: 'Maila Nurmi', birthDate: '1922-12-11', bio: 'Finnish-American actress who created the character Vampira.' },
	{ name: 'Tor Johnson', birthDate: '1903-10-19', bio: 'Swedish wrestler and actor.' },
	{ name: 'William Castle', birthDate: '1914-04-24', bio: 'American director and producer known for horror film gimmicks.' },
	{ name: 'Robb White', birthDate: '1909-06-20', bio: 'American author and screenwriter.' },
	{ name: 'Vincent Price', birthDate: '1911-05-27', bio: 'American actor known for his horror roles.' },
	{ name: 'Carol Ohmart', birthDate: '1927-06-03', bio: 'American actress of film and television.' },
	{ name: 'Jan de Bont', birthDate: '1943-10-22', bio: 'Dutch cinematographer and director.' },
	{ name: 'Graham Yost', birthDate: '1959-09-05', bio: 'Canadian screenwriter and television producer.' },
	{ name: 'Mark Gordon', birthDate: null, bio: 'American film and television producer.' },
	{ name: 'Keanu Reeves', birthDate: '1964-09-02', bio: 'Canadian actor known for action films.' },
	{ name: 'Sandra Bullock', birthDate: '1964-07-26', bio: 'American actress and producer.' },
	{ name: 'Dennis Hopper', birthDate: '1936-05-17', bio: 'American actor, director and photographer.' },
	{ name: 'Chris Columbus', birthDate: '1958-09-10', bio: 'American director, producer and screenwriter.' },
	{ name: 'Steve Kloves', birthDate: '1960-03-18', bio: 'American screenwriter.' },
	{ name: 'David Heyman', birthDate: '1961-07-26', bio: 'British film producer.' },
	{ name: 'Daniel Radcliffe', birthDate: '1989-07-23', bio: 'English actor.' },
	{ name: 'Rupert Grint', birthDate: '1988-08-24', bio: 'English actor.' },
	{ name: 'Emma Watson', birthDate: '1990-04-15', bio: 'English actress and activist.' }
];

const actor = (person, characterName) => ({ person, role: 'ACTOR', characterName });
const crew = (person, role) => ({ person, role, characterName: null });

export const FILMS = [
	{
		title: 'The General',
		releaseYear: 1926,
		runtimeMinutes: 78,
		genres: ['COMEDY', 'ACTION', 'WAR'],
		synopsis: 'A Southern railway engineer chases the Union spies who stole his locomotive, and with it the woman he loves.',
		poster: 'the-general-1926.jpg',
		credits: [
			crew('Buster Keaton', 'DIRECTOR'),
			crew('Clyde Bruckman', 'DIRECTOR'),
			crew('Clyde Bruckman', 'WRITER'),
			crew('Joseph M. Schenck', 'PRODUCER'),
			actor('Buster Keaton', 'Johnnie Gray'),
			actor('Marion Mack', 'Annabelle Lee')
		]
	},
	{
		title: 'The Cabinet of Dr. Caligari',
		releaseYear: 1920,
		runtimeMinutes: 76,
		genres: ['HORROR', 'MYSTERY'],
		synopsis: 'A travelling showman and his sleepwalker arrive in a small town, and a series of murders begins.',
		poster: 'the-cabinet-of-dr-caligari-1920.jpg',
		credits: [
			crew('Robert Wiene', 'DIRECTOR'),
			crew('Carl Mayer', 'WRITER'),
			crew('Hans Janowitz', 'WRITER'),
			crew('Erich Pommer', 'PRODUCER'),
			actor('Werner Krauss', 'Dr. Caligari'),
			actor('Conrad Veidt', 'Cesare'),
			actor('Lil Dagover', 'Jane')
		]
	},
	{
		title: 'Sherlock Jr.',
		releaseYear: 1924,
		runtimeMinutes: 45,
		genres: ['COMEDY'],
		synopsis: 'A cinema projectionist who wants to be a detective dreams his way into the film he is showing.',
		poster: 'sherlock-jr-1924.jpg',
		credits: [
			crew('Buster Keaton', 'DIRECTOR'),
			crew('Clyde Bruckman', 'WRITER'),
			crew('Joseph M. Schenck', 'PRODUCER'),
			actor('Buster Keaton', 'Projectionist'),
			actor('Kathryn McGuire', 'The Girl')
		]
	},
	{
		title: 'The Gold Rush',
		releaseYear: 1925,
		runtimeMinutes: 95,
		genres: ['COMEDY', 'ADVENTURE'],
		synopsis: 'A lone prospector braves the Klondike cold, hunger and a hungry partner in search of gold and love.',
		poster: 'the-gold-rush-1925.jpg',
		credits: [
			crew('Charlie Chaplin', 'DIRECTOR'),
			crew('Charlie Chaplin', 'WRITER'),
			crew('Charlie Chaplin', 'PRODUCER'),
			actor('Charlie Chaplin', 'The Lone Prospector'),
			actor('Georgia Hale', 'Georgia'),
			actor('Mack Swain', 'Big Jim McKay')
		]
	},
	{
		title: 'The Phantom of the Opera',
		releaseYear: 1925,
		runtimeMinutes: 93,
		genres: ['HORROR', 'ROMANCE'],
		synopsis: 'A masked figure haunting the Paris Opera House takes a young singer under his wing, with deadly devotion.',
		poster: 'the-phantom-of-the-opera-1925.jpg',
		credits: [
			crew('Rupert Julian', 'DIRECTOR'),
			crew('Gaston Leroux', 'WRITER'),
			crew('Carl Laemmle', 'PRODUCER'),
			actor('Lon Chaney', 'The Phantom'),
			actor('Mary Philbin', 'Christine Daaé'),
			actor('Norman Kerry', 'Raoul de Chagny')
		]
	},
	{
		title: 'Battleship Potemkin',
		releaseYear: 1925,
		runtimeMinutes: 75,
		genres: ['DRAMA', 'WAR'],
		synopsis: 'Sailors on a Russian battleship rise up against their officers in 1905, and the port of Odessa answers.',
		poster: 'battleship-potemkin-1925.jpg',
		credits: [
			crew('Sergei Eisenstein', 'DIRECTOR'),
			crew('Nina Agadzhanova', 'WRITER'),
			actor('Aleksandr Antonov', 'Grigory Vakulinchuk')
		]
	},
	{
		title: 'His Girl Friday',
		releaseYear: 1940,
		runtimeMinutes: 92,
		genres: ['COMEDY', 'ROMANCE'],
		synopsis: 'A newspaper editor uses one last big story to stop his star reporter, and former wife, from remarrying.',
		poster: 'his-girl-friday-1940.jpg',
		credits: [
			crew('Howard Hawks', 'DIRECTOR'),
			crew('Howard Hawks', 'PRODUCER'),
			crew('Charles Lederer', 'WRITER'),
			actor('Cary Grant', 'Walter Burns'),
			actor('Rosalind Russell', 'Hildy Johnson'),
			actor('Ralph Bellamy', 'Bruce Baldwin')
		]
	},
	{
		title: 'Charade',
		releaseYear: 1963,
		runtimeMinutes: 113,
		genres: ['MYSTERY', 'ROMANCE', 'THRILLER', 'COMEDY'],
		synopsis: 'In Paris, a new widow is pursued by men who want the fortune her husband stole, and helped by a stranger.',
		poster: 'charade-1963.jpg',
		credits: [
			crew('Stanley Donen', 'DIRECTOR'),
			crew('Stanley Donen', 'PRODUCER'),
			crew('Peter Stone', 'WRITER'),
			actor('Cary Grant', 'Peter Joshua'),
			actor('Audrey Hepburn', 'Regina Lampert'),
			actor('Walter Matthau', 'Hamilton Bartholomew')
		]
	},
	{
		title: 'Plan 9 from Outer Space',
		releaseYear: 1957,
		runtimeMinutes: 79,
		genres: ['SCI_FI', 'HORROR'],
		synopsis: 'Aliens raise the dead of a California cemetery to stop humanity from building a doomsday weapon.',
		poster: 'plan-9-from-outer-space-1957.jpg',
		credits: [
			crew('Ed Wood', 'DIRECTOR'),
			crew('Ed Wood', 'WRITER'),
			crew('Ed Wood', 'PRODUCER'),
			actor('Bela Lugosi', 'Ghoul Man'),
			actor('Maila Nurmi', 'Ghoul Woman'),
			actor('Tor Johnson', 'Inspector Clay')
		]
	},
	{
		title: 'House on Haunted Hill',
		releaseYear: 1959,
		runtimeMinutes: 75,
		genres: ['HORROR', 'MYSTERY'],
		synopsis: 'A millionaire offers five guests money to stay the night in a haunted house, and not everyone means well.',
		poster: 'house-on-haunted-hill-1959.jpg',
		credits: [
			crew('William Castle', 'DIRECTOR'),
			crew('William Castle', 'PRODUCER'),
			crew('Robb White', 'WRITER'),
			actor('Vincent Price', 'Frederick Loren'),
			actor('Carol Ohmart', 'Annabelle Loren')
		]
	},
	{
		title: 'Speed',
		releaseYear: 1994,
		runtimeMinutes: 116,
		genres: ['ACTION', 'THRILLER', 'CRIME'],
		synopsis: 'A police officer must keep a city bus above fifty miles an hour, or the bomb on board will go off.',
		poster: 'speed-1994.webp',
		localPoster: true,
		credits: [
			crew('Jan de Bont', 'DIRECTOR'),
			crew('Graham Yost', 'WRITER'),
			crew('Mark Gordon', 'PRODUCER'),
			actor('Keanu Reeves', 'Jack Traven'),
			actor('Sandra Bullock', 'Annie Porter'),
			actor('Dennis Hopper', 'Howard Payne')
		]
	},
	{
		title: "Harry Potter and the Philosopher's Stone",
		releaseYear: 2001,
		runtimeMinutes: 152,
		genres: ['FANTASY', 'ADVENTURE'],
		synopsis: 'On his eleventh birthday an orphan learns he is a wizard and begins his first year at Hogwarts.',
		poster: 'harry-potter-and-the-philosophers-stone-2001.jpg',
		localPoster: true,
		credits: [
			crew('Chris Columbus', 'DIRECTOR'),
			crew('Steve Kloves', 'WRITER'),
			crew('David Heyman', 'PRODUCER'),
			actor('Daniel Radcliffe', 'Harry Potter'),
			actor('Rupert Grint', 'Ron Weasley'),
			actor('Emma Watson', 'Hermione Granger')
		]
	}
];
