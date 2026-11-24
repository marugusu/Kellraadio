package app.radiorecalarm

import java.text.Normalizer
import java.util.Locale

/**
 * Shared track-matching and scoring logic across metadata providers
 * (iTunes, Spotify, MusicBrainz) so unrelated fuzzy search results are rejected.
 */
object MetadataTrackMatcher {
    private const val MIN_TOKEN_JACCARD = 0.60

    private val combiningMarksRegex = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val parensRegex = Regex("\\([^)]*\\)|\\[[^]]*]")
    private val apostropheRegex = Regex("['’`]")
    private val nonAlphaNumRegex = Regex("[^\\p{L}\\p{N}]+")
    private val multiSpaceRegex = Regex("\\s+")
    private val trailingEditionRegex = Regex(
        """\s+-\s+(?:(?:\d{4}\s+)?remaster(?:ed)?|radio\s+edit|single\s+version|album\s+version|live(?:\s+at.*)?|acoustic(?:\s+version)?|mono|stereo|instrumental|demo|deluxe(?:\s+edition)?|explicit|clean).*$""",
        RegexOption.IGNORE_CASE
    )
    private val featSplitRegex = Regex("""\s+(?:feat\.?|ft\.?|featuring|with)\s+""", RegexOption.IGNORE_CASE)
    private val yearTokenRegex = Regex("""^(?:19|20)\d{2}$""")

    private val artistNoiseTokens = setOf("feat", "ft", "featuring", "with")
    private val editionWords = setOf(
        "remaster", "remastered", "radio", "edit", "version", "mix", "remix",
        "live", "acoustic", "mono", "stereo", "deluxe", "bonus", "track",
        "original", "extended", "club", "dub", "instrumental", "single",
        "album", "explicit", "clean", "feat", "ft", "with"
    )
    private val derivativeWords = setOf("karaoke", "tribute")

    /**
     * Selects the highest-scoring candidate from [items] that matches both [queryArtist] and
     * [queryTitle], or returns `null` when no candidate passes validation.
     */
    fun <T> selectBest(
        items: List<T>,
        queryArtist: String,
        queryTitle: String,
        candidateArtists: (T) -> List<String>,
        candidateTitle: (T) -> String,
        tieBreaker: (T) -> Int = { 0 }
    ): T? {
        if (items.isEmpty() || queryArtist.isBlank() || queryTitle.isBlank()) return null

        var bestItem: T? = null
        var bestScore = Int.MIN_VALUE

        for (item in items) {
            val score = scoreCandidate(
                queryArtist = queryArtist,
                queryTitle = queryTitle,
                candidateArtists = candidateArtists(item),
                candidateTitle = candidateTitle(item),
                tieBreaker = tieBreaker(item)
            ) ?: continue

            if (score > bestScore) {
                bestScore = score
                bestItem = item
            }
        }
        return bestItem
    }

    /**
     * Scores a candidate against [queryArtist] and [queryTitle], returning `null` if either
     * artist or title fails to meet the match threshold.
     */
    fun scoreCandidate(
        queryArtist: String,
        queryTitle: String,
        candidateArtists: List<String>,
        candidateTitle: String,
        tieBreaker: Int = 0
    ): Int? {
        val titleScore = scoreTitle(queryTitle, candidateTitle)
        if (titleScore <= 0) return null

        val artistScore = scoreArtist(queryArtist, candidateArtists, candidateTitle)
        if (artistScore <= 0) return null

        val derivativePenalty = if (hasUnrequestedDerivative(queryArtist, queryTitle, candidateArtists, candidateTitle)) {
            20
        } else {
            0
        }
        return (titleScore + artistScore + tieBreaker - derivativePenalty).coerceAtLeast(1)
    }

    internal fun scoreTitle(queryTitle: String, candidateTitle: String): Int {
        val queryBase = cleanText(stripParentheticals(queryTitle))
        val candidateStripped = stripParentheticals(candidateTitle).replace(trailingEditionRegex, "").trim()
        val candidateBase = cleanText(candidateStripped)

        if (queryBase.isBlank() || candidateBase.isBlank()) return 0
        if (queryBase == candidateBase) return 100

        val queryFull = cleanText(queryTitle)
        val candidateFull = cleanText(candidateTitle)
        if (queryFull.isNotBlank() && queryFull == candidateFull) return 95

        val queryTokens = queryBase.split(" ").toSet()
        val candidateTokens = candidateBase.split(" ").toSet()
        val extraCandidateTokens = candidateTokens - queryTokens
        if (candidateTokens.containsAll(queryTokens) &&
            extraCandidateTokens.isNotEmpty() &&
            extraCandidateTokens.all { it in editionWords || yearTokenRegex.matches(it) }
        ) {
            return 90
        }

        val jaccard = jaccardSimilarity(queryTokens, candidateTokens)
        return if (jaccard >= MIN_TOKEN_JACCARD) (jaccard * 85).toInt().coerceAtLeast(1) else 0
    }

    internal fun scoreArtist(
        queryArtist: String,
        candidateArtists: List<String>,
        rawCandidateTitle: String = ""
    ): Int {
        val queryClean = cleanText(stripParentheticals(queryArtist))
        val candidateCleanList = candidateArtists
            .map { cleanText(stripParentheticals(it)) }
            .filter { it.isNotBlank() }

        if (queryClean.isBlank() || candidateCleanList.isEmpty()) return 0

        val joinedCandidates = candidateCleanList.joinToString(" ")
        if (queryClean in candidateCleanList || queryClean == joinedCandidates) return 100

        val queryNoThe = stripLeadingThe(queryClean)
        val candidatesNoThe = candidateCleanList.map(::stripLeadingThe)
        val joinedNoThe = stripLeadingThe(joinedCandidates)
        if (queryNoThe in candidatesNoThe || queryNoThe == joinedNoThe) return 95

        val featParts = queryArtist.split(featSplitRegex, limit = 2)
        if (featParts.size == 2) {
            val primaryQuery = stripLeadingThe(cleanText(stripParentheticals(featParts[0])))
            if (primaryQuery.isNotBlank() && (primaryQuery in candidatesNoThe || primaryQuery == joinedNoThe)) {
                val featTokens = tokenize(cleanText(stripParentheticals(featParts[1]))) - artistNoiseTokens
                val titleAndArtistTokens = tokenize(joinedCandidates) + tokenize(cleanText(rawCandidateTitle))
                return if (featTokens.isNotEmpty() && titleAndArtistTokens.containsAll(featTokens)) 90 else 75
            }
        }

        if (candidatesNoThe.size > 1 && queryNoThe == candidatesNoThe.first()) {
            return 75
        }

        val queryTokens = tokenize(queryNoThe) - artistNoiseTokens
        val candidateTokens = candidatesNoThe.flatMap(::tokenize).toSet() - artistNoiseTokens
        if (queryTokens.isEmpty() || candidateTokens.isEmpty()) return 0

        val jaccard = jaccardSimilarity(queryTokens, candidateTokens)
        return if (jaccard >= MIN_TOKEN_JACCARD) (jaccard * 85).toInt().coerceAtLeast(1) else 0
    }

    private fun hasUnrequestedDerivative(
        queryArtist: String,
        queryTitle: String,
        candidateArtists: List<String>,
        candidateTitle: String
    ): Boolean {
        val queryTokens = tokenize(cleanText(queryArtist)) + tokenize(cleanText(queryTitle))
        if (queryTokens.any { it in derivativeWords }) return false

        val candidateTokens = tokenize(cleanText(candidateTitle)) +
            candidateArtists.flatMap { tokenize(cleanText(it)) }
        return candidateTokens.any { it in derivativeWords }
    }

    private fun stripParentheticals(text: String): String {
        val stripped = text.replace(parensRegex, " ").trim()
        return stripped.ifBlank {
            text.replace("(", " ")
                .replace(")", " ")
                .replace("[", " ")
                .replace("]", " ")
                .trim()
        }
    }

    private fun cleanText(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return combiningMarksRegex.replace(decomposed, "")
            .lowercase(Locale.ROOT)
            .replace(apostropheRegex, "")
            .replace(nonAlphaNumRegex, " ")
            .replace(multiSpaceRegex, " ")
            .trim()
    }

    private fun stripLeadingThe(cleanText: String): String {
        return if (cleanText.startsWith("the ") && cleanText.length > 4) {
            cleanText.removePrefix("the ").trim()
        } else {
            cleanText
        }
    }

    private fun tokenize(cleanText: String): Set<String> {
        if (cleanText.isBlank()) return emptySet()
        return cleanText.split(" ").filter { it.isNotBlank() }.toSet()
    }

    private fun jaccardSimilarity(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersectionCount = a.count { it in b }
        if (intersectionCount == 0) return 0.0
        val unionCount = a.size + b.size - intersectionCount
        return intersectionCount.toDouble() / unionCount.toDouble()
    }
}
