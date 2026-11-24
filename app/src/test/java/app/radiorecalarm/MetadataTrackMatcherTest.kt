package app.radiorecalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MetadataTrackMatcherTest {

    @Test
    fun `rejects completely unrelated track even with tieBreaker`() {
        val score = MetadataTrackMatcher.scoreCandidate(
            queryArtist = "Ludmilla Issakova",
            queryTitle = "Uruguai naljalaul",
            candidateArtists = listOf("Laura"),
            candidateTitle = "Ultra",
            tieBreaker = 5
        )
        assertNull(score)
    }

    @Test
    fun `rejects partial first-name artist mismatch like Laura vs Laura Pausini`() {
        val score = MetadataTrackMatcher.scoreCandidate(
            queryArtist = "Laura",
            queryTitle = "Ultra",
            candidateArtists = listOf("Laura Pausini"),
            candidateTitle = "Ultra"
        )
        assertNull(score)
    }

    @Test
    fun `rejects single-word title inside different multi-word title`() {
        val score = MetadataTrackMatcher.scoreCandidate(
            queryArtist = "Taylor Swift",
            queryTitle = "Love",
            candidateArtists = listOf("Taylor Swift"),
            candidateTitle = "Love Story"
        )
        assertNull(score)
    }

    @Test
    fun `matches remastered and parenthetical edition titles`() {
        val remasteredScore = MetadataTrackMatcher.scoreCandidate(
            queryArtist = "Queen",
            queryTitle = "Bohemian Rhapsody",
            candidateArtists = listOf("Queen"),
            candidateTitle = "Bohemian Rhapsody - Remastered 2011"
        )
        val radioEditScore = MetadataTrackMatcher.scoreCandidate(
            queryArtist = "Curly Strings",
            queryTitle = "Kauges külas",
            candidateArtists = listOf("Curly Strings"),
            candidateTitle = "Kauges külas (Radio Edit)"
        )
        assertNotNull(remasteredScore)
        assertNotNull(radioEditScore)
    }

    @Test
    fun `matches diacritic-folded and featured artist tracks`() {
        val foldedScore = MetadataTrackMatcher.scoreCandidate(
            queryArtist = "Curly Strings",
            queryTitle = "Kauges kulas",
            candidateArtists = listOf("Curly Strings"),
            candidateTitle = "Kauges Külas"
        )
        val featScore = MetadataTrackMatcher.scoreCandidate(
            queryArtist = "David Guetta feat. Sia",
            queryTitle = "Titanium",
            candidateArtists = listOf("David Guetta"),
            candidateTitle = "Titanium (feat. Sia)"
        )
        assertNotNull(foldedScore)
        assertNotNull(featScore)
    }

    @Test
    fun `selectBestItunesResult rejects wrong artist and picks matching candidate`() {
        val wrong = ItunesResult(
            artistName = "Laura",
            trackName = "Ultra",
            collectionName = "Ultra",
            artworkUrl100 = "https://img/wrong.jpg"
        )
        val right = ItunesResult(
            artistName = "Curly Strings",
            trackName = "Kauges Külas",
            collectionName = "Üle Ilma",
            artworkUrl100 = "https://img/right.jpg"
        )

        assertNull(
            MusicInfoRepository.selectBestItunesResult(
                results = listOf(wrong),
                artist = "Ludmilla Issakova",
                title = "Uruguai naljalaul"
            )
        )

        val picked = MusicInfoRepository.selectBestItunesResult(
            results = listOf(wrong, right),
            artist = "Curly Strings",
            title = "Kauges külas"
        )
        assertEquals("Üle Ilma", picked?.collectionName)
    }

    @Test
    fun `selectBestMusicBrainzRecording validates score and artist-title match`() {
        val lowScore = MbRecording(
            id = "low",
            score = 45,
            title = "Kauges külas",
            artistCredit = listOf(MbArtistCredit(name = "Curly Strings"))
        )
        val wrongTitle = MbRecording(
            id = "wrong",
            score = 95,
            title = "Ultra",
            artistCredit = listOf(MbArtistCredit(name = "Laura"))
        )
        val valid = MbRecording(
            id = "valid",
            score = 100,
            title = "Kauges külas",
            artistCredit = listOf(MbArtistCredit(name = "Curly Strings")),
            releases = listOf(MbRelease(id = "rel1", title = "Üle ilma", status = "Official"))
        )

        assertNull(
            MusicInfoRepository.selectBestMusicBrainzRecording(
                recordings = listOf(lowScore, wrongTitle),
                artist = "Curly Strings",
                title = "Kauges külas"
            )
        )

        val picked = MusicInfoRepository.selectBestMusicBrainzRecording(
            recordings = listOf(lowScore, wrongTitle, valid),
            artist = "Curly Strings",
            title = "Kauges külas"
        )
        assertEquals("valid", picked?.id)
    }
}
