# Palade pommikindel raamistamine (Bulletproof Track Framing)

## Problem Statement

Raadiovoo salvestamisel ei taga senine 8-sekundilise viivepuhvriga ühekordne tükeldus alati puhast algust ja lõppu:
1. Kui `MediaCodec`-põhine piirituvastus ebaõnnestub või selle 500 ms ajapiir saab enne puhvri lõppu täis, tagastatakse 0 ms tagasinihe (`frames.size`), mistõttu jääb eelmise **Pala** lõppu ~3 sekundit kõlli või järgmist lugu ning järgmise **Pala** algusest läheb sama palju kaduma.
2. Energialanguse skoorija otsib ainult V-kujulist auku (`minOf(beforeAvg, afterAvg) - troughDb`), mistõttu jääb tuvastamata raadioeetris väga levinud vaikse lõpu (*fade-out*) ja järsu alguse (*step-up*) üleminek.
3. Kui jaam mängib kahe loo vahel lühikese kõlli ilma eraldi metainfota (`Pala A → 3–10 s kõll → Pala B`), jääb lühike kõll eelmise **Pala** lõppu.
4. Kitsas 8-sekundiline aken ei jäta piisavalt varu suurema metainfo viite (4–12 s) ega topeltpiiri (vahekõlli väljalõikamise) käsitlemiseks.

## Solution

Asendada senine habras 8-sekundiline lõikeloogika lihtsa, deterministliku ja ülimalt töökindla *bit-perfect* mälupuhvri-põhise raamistusega, ilma keerulise kettapõhise vahefailide süsteemita ja ilma kasutajaliidese seadistusliuguriteta:
1. **Laiendatud libisev mälupuhver (~20 s, ~400 KB RAM):** Võimaldab hoida mälus korraga vana loo lõpu, võimaliku vahekõlli ja uue loo alguse, ilma kettapõhiste ajutiste failideta.
2. **Kõik kolm raadioülemineku kuju:** Akustiline analüüs tuvastab lisaks vaikusele ja energialangusele (*dip*) ka vaikse lõpu → järsu alguse (*step-up*) ülemineku.
3. **Metainfota vahekõlli väljalõikamine (Dual-Boundary Excision):** Kui üleminekuaknas on kaks selget akustilist piiri teineteisest 2–12 s kaugusel (lühike kõll kahe pala vahel), lõpeb eelmine **Pala** esimesel piiril, vahekõll jäetakse välja ja uus **Pala** algab teisel piiril.
4. **Garanteeritud varunihe ja täielik dekodeerimine:** Dekoodri eelarve ja puhvri täitmine tagavad kogu akna analüüsi; tõrke korral ei tagastata kunagi 0 ms tagasinihet, vaid rakendub konservatiivne varunihe.
5. **Pehme kestusekontroll (Soft Duration Tie-Breaker):** iTunes/MusicBrainz päringust saadav ametlik kestus aitab mitme kandidaatpiiri korral valida puhtaima raami, kuid ei kirjuta üle selgeid akustilisi piire (*radio edit* ja tundmatute lugude kaitse).
6. **Diagnostiline logimine:** Iga tükeldus logib ühe selge kokkuvõtva rea (puhvri kestus, dekodeerimise aeg, valitud strateegia ja nihe).

## User Stories

1. As a radio listener recording music, I want every recorded **Pala** to have a clean start and clean end without 3 seconds of jingle or the next song, so that I can listen to or archive tracks without manual trimming.
2. As a listener who wants a "set and forget" experience, I want track framing to work automatically without exposing buffer or threshold sliders in settings.
3. As an audiophile, I want all recorded audio to remain 100% *bit-perfect* (lossless MP3/AAC frames) without re-encoding on the phone.
4. As a listener on a station that plays short untagged jingles between songs, I want the intermediate jingle to be dropped cleanly between the two songs instead of leaking into either file.
5. As a listener on a station with smooth fade-outs followed by abrupt jingles or song intros, I want the step-up energy boundary to be detected reliably.
6. As a listener on a station with heavy audio compression (*crossfade* without silence), I want the framing to prefer cutting off foreign jingles/next-track audio rather than leaving foreign audio on the track.
7. As a listener recording an unindexed local track or a shortened *radio edit*, I want acoustic boundary detection to still work cleanly even when official duration metadata is missing or differs from the broadcast.
8. As a listener tapping Stop or switching stations mid-session, I want any completed pending **Pala** to finalize immediately without data loss, and any ≥ 30 s partial track (`"partial": true`) to keep its clean framed start.
9. As a listener recording talk shows (**Saated**) or using **Äratuse salvestus**, I want spoken programs to remain completely untrimmed so no speech is lost.
10. As a developer or power user inspecting logs, I want each track boundary decision logged with strategy and offset details so station quirks can be verified immediately.

## Implementation Decisions

- **Keel ja arhitektuuripiirid (Lihtne ja pommikindel tuum):**
  - Säilitada senine mälupõhine `LookbackFrameBuffer` + `StreamRecordingCoordinator` voog ilma kettapõhiste ajutiste *staging*-failideta, vältides krahhi- ja koristuskeerukust.
  - Suurendada `AppConfig.Recording.LOOKBACK_BUFFER_SECONDS` väärtuseni `20L` (~350–500 KB RAM), `SPLIT_SEARCH_WINDOW_MS` väärtuseni `15000L` (15 s) ja `SPLIT_FALLBACK_OFFSET_MS` väärtuseni `2800L` (2,8 s).
  - Jätab kogu käitumise täielikult `AppConfig.Recording` konstandite alla ilma UI-seadistusteta.

- **Veakindel `MediaCodecAudioBoundaryDetector`:**
  - Laadib tsüklis kõik saadaolevad sisendpuhvrid enne väljundi ootamist ning kasutab piisavat ajapiiri (`DECODE_BUDGET_MS = 1500L`), et kogu otsinguaken (mitte ainult esimene osa) saaks dekodeeritud.
  - Kui dekooder ei toimi, aegub või sisendandmed on puudulikud, tagastab detektor alati deterministliku varunihke (`AudioBoundaryScorer.fallbackSplitIndex(...)`), **mitte kunagi** `frames.size`.

- **Täiendatud `AudioBoundaryScorer` (vaikus, *dip*, *step-up*, vahekõlli väljalõige):**
  - Tuvastab kolm kandidaaditüüpi:
    1. **Vaikus (*Silence*):** `troughDb <= -42.0` ja vähemalt `8 dB` kontrast ümbritsevaga.
    2. **Energialangus (*Dip*):** `minOf(beforeAvg, afterAvg) - troughDb >= 6.0`.
    3. **Järsk tõus (*Step-up*):** vaikne lõpp / *fade-out* enne järsku algust (`afterAvg - beforeAvg >= 10.0` ja ülemineku eel suhteliselt madal energia).
  - Tagastab ühe piiri asemel vahemiku `[closeEndIdx, nextStartIdx]` (vaikimisi võrdsed):
    - Kui otsinguaknas leidub kaks tugevat üleminekut `b1` ja `b2` vahekaugusega `2000 ms .. 12000 ms` ning vahepealne lõik on lühike kõll/vahepala, suletakse eelmine **Pala** kohas `b1` ja järgmine **Pala** avatakse kohas `b2` (vahepealsed kõllikaadrid jäetakse vahele).
    - *Crossfade* konflikti korral eelistatakse piiri, mis välistab võõra heli mõlemast palast.

- **Pehme ametlik kestusekontroll (*Soft Duration Anchor*):**
  - `ItunesResult` täiendatakse väljaga `trackTimeMillis: Long?` ja `MbRecording` väljaga `length: Long?`, mis lisatakse mudelisse `SongAdditionalInfo(durationMs: Long?)`.
  - Kui enne `Pala` sulgemist on `SongAdditionalInfo.durationMs` kättesaadav ja otsinguaknas on mitu arvestatavat akustilist lõpukandidaati, kasutatakse ametlikku kestust **ainult viigilahutajana** kandidaatide vahel, mille kestuse hälve jääb mõistlikku vahemikku (nt `±5 s`). Suurema erinevuse (*radio edit*) või puuduva kestuse korral usaldatakse ainult akustilist skoori.

- **Ulatus ja lõpetamine:**
  - Käsitsi peatamisel (`stopRecording`), jaamavahetusel ja vähese kettamahu katkestusel tühjendatakse puhver kohe aktiivsesse faili; `isUnsplitShowMode` (**Äratuse salvestus** ja **Saated**) ei kasuta agressiivset trimmimist.

## Testing Decisions

- **Unit-testid (JVM, riistvarast sõltumatu):**
  - `AudioBoundarySplitTest`:
    - *Step-up* ülemineku test: vaikne *fade-out* (`-30 dB`) → järsk algus (`-10 dB`) tuvastatakse täpselt alguse piiril.
    - Vahekõlli väljalõikamise test: `Pala A → 4 s kõll → Pala B` ilma vahepealse metainfota lõikab kõlli välja (`closeEndIdx < nextStartIdx`), nii et `Pala A` ei saa kõlli ja `Pala B` saab puhta alguse.
    - Veakindluse test: kui dekooder tagastab vea või tühja tulemuse, kasutatakse varunihet (`fallbackOffsetMs`), mitte `frames.size`.
    - Kestuse viigilahutaja test: kahe samaväärse akustilise kandidaadi vahel valitakse see, mis klapib `officialDurationMs`-iga, kuid suure hälbe (*radio edit*) korral seda ignoreeritakse.
  - `StreamRecordingCoordinatorTest`:
    - Kontrollib 20 s puhvri käitumist, vahekõlli väljalõikamist failide baitide tasemel, osalise loo käitumist ning käsitsi peatamist.
- **Instrumentatsioonitest (`MediaCodecAudioBoundaryDetectorTest`):**
  - Olemasolevatele vaikuse ja *crossfade* testidele lisatakse *step-up* (vaikne *fade-out* → vali algus) reaalne AAC kodeeritud test.

## Out of Scope

- Kettapõhine vahefailide *staging* ja krahhist taastumise puhver.
- Mitte-ICY striimide (`icy-metaint <= 0`, nt HLS) mängijapuhvri (30–60 s) ümberkorraldamine; need jäävad parima pingutuse varuteele (`Player.Listener.onMetadata`).
- Telefonis helifaili otste ümberkodeerimine *fade-in/fade-out* lisamiseks (rikuks *bit-perfect* põhimõtet).
- Kasutajaliidese seaded või liugurid lõikepunkti käsitsi muutmiseks.

## Further Notes

- Asendab arhitektuuriliselt dokumendi [ADR-0007](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/docs/adr/0007-lookback-buffer-and-acoustic-splitting.md) uue otsusega [ADR-0008](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/docs/adr/0008-bulletproof-track-framing.md).
- Järgib domeenisõnastikku [GLOSSARY.md](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/GLOSSARY.md).
