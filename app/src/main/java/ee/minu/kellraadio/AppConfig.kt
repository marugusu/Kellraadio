package ee.minu.kellraadio

object AppConfig {

    // --- 1. API URL-id ja SEADED ---
    object Api {
        // Sinu jaamade nimekiri GitHubis
        const val STATIONS_LIST_BASE_URL = "https://gist.githubusercontent.com/marugusu/e886795e2e2ae5df7b9573bd3f84333b/raw/"

        // Radio Browser API
        const val RADIO_BROWSER_BASE_URL = "https://de1.api.radio-browser.info/"

        // Muusika info API-d
        const val LRCLIB_BASE_URL = "https://lrclib.net/"
        const val ITUNES_BASE_URL = "https://itunes.apple.com/"
        const val MUSICBRAINZ_BASE_URL = "https://musicbrainz.org/ws/2/"

        // Viisakas on öelda API-le, kes me oleme
        const val USER_AGENT = "Kellraadio/1.0 ( minumeil@example.com )"
    }

    // --- 2. METAANDMETE FILTREERIMINE ---
    object Metadata {
        // Sõnad, mille puhul me EI otsi lisainfot (laulusõnu, pilti)
        val IGNORE_TERMS = listOf(
            "Otseeeter",      // Eesti
            "Live Stream",    // Inglise
            "Live Broadcast", // Inglise alt
            "Otse",           // Liivi
            "생방송",          // Korea
            "Saatepaus",      // ERR
            "Uudised",        // ERR
            "Reklaam",        // Üldine
            "Sport",
            "Ilmateade"
        )

        // Jaamad, mis saadavad infot valepidi: "Pealkiri - Esitaja" (mitte "Esitaja - Pealkiri")
        // Peab olema täpne jaama nimi, nagu see on sinu JSON failis/äpis
        val REVERSED_STATIONS = listOf(
            "Star FM 80s",
            "Star FM 90s"
        )
    }

    // --- 3. UI JA DISAIN (Lühendid ja logod) ---
    object UI {
        // Erandid jaamade lühendite genereerimisel (Logo asendaja)
        // Võti peab olema VÄIKETÄHTEDEGA!
        val STATION_INITIALS_EXCEPTIONS = mapOf(
            "raadio 2" to "R2",
            "raadio 4" to "R4",
            "sky plus" to "SKY+",
            "klassikaraadio" to "KLAS",
            "vikerraadio" to "VIKR",
            "yle radio 1" to "YLE1",
            "heart 70s" to "70",
            "heart 80s" to "80",
            "heart 90s" to "90",
            "retro disco" to "DISC",
            "retro love" to "LOVE",
            "ida" to "IDA",
            "star fm 80s" to "80",
            "star fm 90s" to "90"
        )

        // Sõnad, mida me lühendite tegemisel ignoreerime
        val NOISE_WORDS = listOf(
            "raadio", "radio", "fm", "eesti", "onair", "channel", "klara",
            "live", "est", "fin", "the", "hits", "love", "duo", "elmari", "elmar"
        )
    }
}