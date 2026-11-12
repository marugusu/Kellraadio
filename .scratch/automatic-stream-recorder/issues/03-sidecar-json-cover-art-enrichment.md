# 03: JSON kõrvafailide genereerimine, kaanepildi laadimine ja API päringud (Sidecar JSON, Cover Art & Metadata Enrichment)

**What to build:**
Iga salvestatud pala ja saate varustamine masinloetava `<failinimi>.json` kõrvafaili ning albumi kaanepildiga (`cover.jpg`). Loo alguses käivitatakse taustal päringud veebiteenustesse (iTunes, LRCLIB, MusicBrainz), et leida album, ilmumisaasta, žanr, laulusõnad ja kaanepildi URL. Kaanepilt laaditakse albumi kausta. Pala või saate lõppemisel koostatakse kõigi kogutud metaandmete, täpse failisuuruse ja kestusega kõrvafail, mis võimaldab arvutis ffmpeg või agentide abil faile ilma täiendava infootsinguta sildistada.

**Blocked by:** 02: Lugude ja saadete klassifitseerimine, kaustapuu ja kahekohaline nummerdus

**Status:** ready-for-agent

- [ ] Loo tuvastamisel käivitatakse asünkroonne päring `MusicInfoRepository` kaudu täiendavate metaandmete ja kaanepildi saamiseks.
- [ ] Leitud kaanepilt laaditakse alla ja salvestatakse albumi kausta nimega `cover.jpg` (kui pilt juba kaustas eksisteerib, korduslaadimist ei tehta).
- [ ] Pala või saate sulgemisel genereeritakse spetsifikatsioonile vastav `<failinimi>.json` fail väljadega: `title`, `artist`, `album`, `year`, `genre`, `trackNumber`, `lyrics`, `station`, `stationUrl`, `recordedAt`, `durationSeconds`, `fileSize`, `codec`, `bitrate`, `audioFile`, `coverArtFile`, `partial`.
- [ ] Kui veebipäring aegub või ei leia vastet, kirjutatakse puuduvad väljad turvaliselt väärtusega `null`, katkestamata salvestamist.
- [ ] JVM üksustestid verifitseerivad JSON-skeemi vastavust, väljade korrektsust ja asünkroonsete päringute tõrkekindlust.

