package eu.kanade.tachiyomi.source.online.handlers

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class FilterHandler {

    class TextField(name: String, val key: String) : Filter.Text(name)
    class Tag(val id: String, name: String) : Filter.TriState(name)
    class ContentList(contents: List<Tag>) : Filter.Group<Tag>("Content", contents)
    class FormatList(formats: List<Tag>) : Filter.Group<Tag>("Format", formats)
    class GenreList(genres: List<Tag>) : Filter.Group<Tag>("Genres", genres)
    class R18 : Filter.Select<String>("R18+", arrayOf("Default", "Show all", "Show only", "Show none"))
    class Demographic : Filter.Select<String>("Demographic", arrayOf("All", "Shounen", "Shoujo", "Seinen", "Josei"))
    class PublicationStatus : Filter.Select<String>("Publication status", arrayOf("All", "Ongoing", "Completed", "Cancelled", "Hiatus"))
    class ThemeList(themes: List<Tag>) : Filter.Group<Tag>("Themes", themes)
    class TagInclusionMode : Filter.Select<String>("Tag inclusion mode", arrayOf("All (and)", "Any (or)"), 0)
    class TagExclusionMode : Filter.Select<String>("Tag exclusion mode", arrayOf("All (and)", "Any (or)"), 1)

    class SortFilter : Filter.Sort("Sort",
            sortables.map { it.first }.toTypedArray(),
            Filter.Sort.Selection(0, true))

    class OriginalLanguage : Filter.Select<String>("Original Language", sourceLang.map { it.first }.toTypedArray())

    fun getFilterList() = FilterList(
            TextField("Author", "author"),
            TextField("Artist", "artist"),
            R18(),
            SortFilter(),
            Demographic(),
            PublicationStatus(),
            OriginalLanguage(),
            ContentList(contentType),
            FormatList(formats),
            GenreList(genre),
            ThemeList(themes),
            TagInclusionMode(),
            TagExclusionMode()
    )


    companion object {
        val sortables = listOf(
                Triple("Update date", 0, 1),
                Triple("Alphabetically", 2, 3),
                Triple("Number of comments", 4, 5),
                Triple("Rating", 6, 7),
                Triple("Views", 8, 9),
                Triple("Follows", 10, 11))

        val sourceLang = listOf(
                Pair("All", "0"),
                Pair("Japanese", "2"),
                Pair("English", "1"),
                Pair("Polish", "3"),
                Pair("German", "8"),
                Pair("French", "10"),
                Pair("Vietnamese", "12"),
                Pair("Chinese", "21"),
                Pair("Indonesian", "27"),
                Pair("Korean", "28"),
                Pair("Spanish (LATAM)", "29"),
                Pair("Thai", "32"),
                Pair("Filipino", "34"))

        val contentType = listOf(
                Tag("9", "Ecchi"),
                Tag("32", "Smut"),
                Tag("49", "Gore"),
                Tag("50", "Sexual Violence")
        ).sortedWith(compareBy { it.name })

        val formats = listOf(
                Tag("1", "4-koma"),
                Tag("4", "Award Winning"),
                Tag("7", "Doujinshi"),
                Tag("21", "Oneshot"),
                Tag("36", "Long Strip"),
                Tag("42", "Adaptation"),
                Tag("43", "Anthology"),
                Tag("44", "Web Comic"),
                Tag("45", "Full Color"),
                Tag("46", "User Created"),
                Tag("47", "Official Colored"),
                Tag("48", "Fan Colored")
        ).sortedWith(compareBy { it.name })

        val genre = listOf(
                Tag("2", "Action"),
                Tag("3", "Adventure"),
                Tag("5", "Comedy"),
                Tag("8", "Drama"),
                Tag("10", "Fantasy"),
                Tag("13", "Historical"),
                Tag("14", "Horror"),
                Tag("17", "Mecha"),
                Tag("18", "Medical"),
                Tag("20", "Mystery"),
                Tag("22", "Psychological"),
                Tag("23", "Romance"),
                Tag("25", "Sci-Fi"),
                Tag("28", "Shoujo Ai"),
                Tag("30", "Shounen Ai"),
                Tag("31", "Slice of Life"),
                Tag("33", "Sports"),
                Tag("35", "Tragedy"),
                Tag("37", "Yaoi"),
                Tag("38", "Yuri"),
                Tag("41", "Isekai"),
                Tag("51", "Crime"),
                Tag("52", "Magical Girls"),
                Tag("53", "Philosophical"),
                Tag("54", "Superhero"),
                Tag("55", "Thriller"),
                Tag("56", "Wuxia")
        ).sortedWith(compareBy { it.name })

        val themes = listOf(
                Tag("6", "Cooking"),
                Tag("11", "Gyaru"),
                Tag("12", "Harem"),
                Tag("16", "Martial Arts"),
                Tag("19", "Music"),
                Tag("24", "School Life"),
                Tag("34", "Supernatural"),
                Tag("40", "Video Games"),
                Tag("57", "Aliens"),
                Tag("58", "Animals"),
                Tag("59", "Crossdressing"),
                Tag("60", "Demons"),
                Tag("61", "Delinquents"),
                Tag("62", "Genderswap"),
                Tag("63", "Ghosts"),
                Tag("64", "Monster Girls"),
                Tag("65", "Loli"),
                Tag("66", "Magic"),
                Tag("67", "Military"),
                Tag("68", "Monsters"),
                Tag("69", "Ninja"),
                Tag("70", "Office Workers"),
                Tag("71", "Police"),
                Tag("72", "Post-Apocalyptic"),
                Tag("73", "Reincarnation"),
                Tag("74", "Reverse Harem"),
                Tag("75", "Samurai"),
                Tag("76", "Shota"),
                Tag("77", "Survival"),
                Tag("78", "Time Travel"),
                Tag("79", "Vampires"),
                Tag("80", "Traditional Games"),
                Tag("81", "Virtual Reality"),
                Tag("82", "Zombies"),
                Tag("83", "Incest")
        ).sortedWith(compareBy { it.name })

        val allTypes = (contentType + formats + genre + themes).map { it.id to it.name }.toMap()

    }
}