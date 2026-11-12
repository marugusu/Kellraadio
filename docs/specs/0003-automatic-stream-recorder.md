# Automaatne raadiovoo salvesti (Automatic Radio Stream Recorder)

## Problem Statement

Kasutaja kuulab raadiot ning soovib eetri kaudu edastatavaid laule ja saateid automaatselt ja korrastatult salvestada. Tavaliste raadiosalvestite puhul seisab kasutaja silmitsi mitmete tüütute probleemidega:
1. Salvestis on üks hiiglaslik tundidepikkune helifail, millest konkreetsete laulude otsimine ja käsitsi välja lõikamine nõuab suurt ajakulu.
2. Lugude vahetumisel raadios puudub automaatne tükeldus või tehakse lõige esituspuhvri viivituse tõttu vales kohas, lõigates maha laulu esimesed sekundid.
3. Raadioeeter on täis 5–15-sekundilisi jaamakõlle, teadustusi ja lühireklaame, mis tekitavad automaatsel poolitamisel kettale sadu kasutuid prügifaile.
4. Salvestatud failidel puuduvad korrektsed metaandmed (album, aasta, žanr, laulusõnad, kaanepilt), mistõttu muusikakogu korrastamine ja arvutis sildistamine on vaevaline.
5. Reaalajas MP3-ks retranskodeerimine telefonis kurnab tugevalt akut ja halvendab helikvaliteeti, samas kui Androidi ranged failisüsteemi piirangud (Scoped Storage) teevad failide arvutisse kopeerimise keeruliseks.

## Solution

Luua automaatne ja taustal toimiv raadiovoo salvestussüsteem, mis tagab maksimaalse helikvaliteedi, puhta kaustapuu ja täpse metaandmestiku:
1. **Bit-perfect salvestus ja kõrvafailid (ADR-0004):** Jaama helivoog salvestatakse puhtalt algsel kujul (MP3 või AAC) ilma akut koormava ümberkodeerimiseta. Iga pala kõrvale salvestatakse struktureeritud JSON-kõrvafail (`<fail>.json`) kõigi tuvastatud metaandmetega ja veebist alla laaditud `cover.jpg`.
2. **Korrastatud kaustapuu ja standardne nummerdus:** Tuvastatud muusikapalad jaotatakse albumite kaupa kaustadesse (`Music/Muusika/<Esitaja>/<Album>/`), varustatakse kahekohalise palanumbriga (`01`, `02`, ...) ning jutusaated paigutatakse kausta `Music/Saated/<Jaam>/`.
3. **Voosisene nullviivitusega tükeldus (Zero-Latency Byte Alignment):** Salvestus poolitatakse uueks failiks täpselt sel baidipositsioonil võrguvoos, kus jaam edastab ICY-metainfo, tagades puhta lõike ilma taasesituspuhvri nihketa.
4. **Intelligentne prügifilter ja osaliste lugude tugi:** Alla 30 sekundi kestvad vaheklipid ja reklaamid kustutatakse automaatselt. Poolelt laulult alustatud salvestised säilitatakse ja märgistatakse kõrvafailis selge tunnusega `"partial": true`.
5. **Vaba USB ligipääs:** Failid talletatakse välisele salvestuskettale (`Android/data/app.radiorecalarm/files/Music/`), mis on arvutiga USB kaudu koheselt kättesaadav ega vaja Androidi süsteemseid lisalubasid.

## User Stories

1. As a radio listener, I want to tap a record button while listening to any station, so that the app immediately begins capturing the live audio stream to persistent storage.
2. As a music collector, I want songs to be automatically split into individual audio files when station metadata changes, so that I don't have to manually cut songs from a continuous recording.
3. As a music collector, I want songs to be saved in an organized folder structure by artist and album, so that my recorded music library is neatly cataloged immediately.
4. As a music collector, I want each recorded song file to be prefixed with a two-digit track number (`01`, `02`, ...), so that tracks preserve their natural album order or recording sequence.
5. As an audiophile, I want the recorded audio to capture the native stream bytes without re-encoding, so that battery is saved and audio quality is preserved at 100% fidelity.
6. As a power user preparing music for my personal library, I want a detailed JSON sidecar file saved next to every audio file, so that I can run automated ffmpeg scripts or agents on my PC to embed ID3v2 tags and lyrics.
7. As a music collector, I want album cover artwork (`cover.jpg`) to be automatically fetched and saved alongside the track files, so that cover art is immediately available for offline tagging and viewing.
8. As a radio listener, I want station jingles, station IDs, and short ads lasting under 30 seconds to be automatically deleted, so that my storage is not cluttered with hundreds of useless audio snippets.
9. As a radio listener hearing a great song already in progress, I want to tap record and have the partial song saved, with the sidecar file marking `"partial": true`, so that I don't lose the song even if its beginning was missed.
10. As a radio listener tuning in to a talk show or news broadcast without song metadata, I want the audio to be categorized and saved into a dedicated `Saated/` folder, so that spoken programs do not mix into music album folders.
11. As a radio listener, when a station transitions from a talk show to a music song, I want the show file to cleanly close and the song to start in its album folder, so that talk and music are distinctly separated.
12. As a radio listener on mobile data experiencing a momentary 2-second reconnect, I want recording to seamlessly continue appending to the active file if the song metadata hasn't changed, so that brief network hiccups do not fragment tracks.
13. As an audiophile, I want track splits to occur at the exact byte boundary where the radio server sends the ICY metadata block in the stream, so that the playback buffer does not cut off the first few seconds of the song.
14. As a user recording on the go, I want the player UI to show the name of the track currently being recorded and a counter of completed tracks in the session, so that I have immediate visibility into recording progress.
15. As a user with a locked phone, I want the system notification to show a clear text recording indicator (`🔴 Salvestan: ...`) while keeping standard playback buttons clean, so that I can verify recording state at a glance.
16. As a PC user connecting via USB cable (MTP), I want recorded folders, audio files, JSON sidecars, and cover images to be immediately visible and copyable, so that I don't need root permissions or specialized file transfer tools.
17. As a listener stopping recording, I want the final in-progress track or show to be flushed and safely closed, pruning it if it was under 30 seconds, so that no corrupted half-written files remain on disk.
18. As a user recording a song that was already recorded earlier in the day, I want the new file to automatically receive a suffix index (e.g. `_01`, `_02`), so that previous recordings are never overwritten or lost.
19. As a radio listener listening to songs not listed in iTunes, I want the recording to gracefully fall back to a `Teadmata album/` folder with null fields in the sidecar JSON, so that unindexed local music is still captured reliably.
20. As a phone user, I want the background recording to stream directly from the network data source to disk without accumulating large audio chunks in RAM, so that the app cannot crash with OutOfMemory errors.
21. As a radio listener waking up to a morning broadcast, I want my alarm to automatically start recording the station, so that I capture the broadcast while waking up without having to manually start recording.
22. As an avid radio recorder, I want a setting to record continuously whenever the radio is streaming, so that all stations I listen to are automatically archived.
23. As a listener switching stations while recording, I want the recording session to remain active, closing the previous station's file cleanly and immediately starting to record the new station.
24. As a user with limited phone storage, I want recording to halt automatically if free storage drops below 500 MB, with a clear notification, so that my phone never runs out of space.
25. As an alarm user, I want each alarm card to indicate if recording is enabled, so that I can see at a glance which alarms will record.

## Implementation Decisions

- **Dedicated Coordinator Seam (`StreamRecordingCoordinator`)**:
  - All recording state management, stream byte dispatching, ICY metadata transition detection, duration timing, and filesystem writing are decoupled from Android framework services into a standalone coordinator.
  - The coordinator defines explicit inputs: raw stream bytes, metadata transition triggers, enriched metadata queries, and storage targets.
  - `RadioService` connects its existing `RetryingHttpDataSource` byte callback and metadata listener to this coordinator.

- **Zero-Latency Stream-Level Byte Alignment**:
  - Splitting occurs inside the HTTP data source callback where ICY metadata frames are stripped from the audio payload.
  - When the ICY parser identifies a change in title/artist, the current file output stream is flushed and completed at that exact byte offset, and the subsequent bytes route into the newly created track file.

- **Folder Hierarchy and File Naming Rules**:
  - Music base path: `Android/data/app.radiorecalarm/files/Music/`
  - Track path: `Muusika/<SanitizedArtist>/<SanitizedAlbum>/<NN> - <SanitizedArtist> - <SanitizedTitle>.<ext>`
  - Fallback album folder when album metadata is absent: `Teadmata album`
  - Show path: `Saated/<SanitizedStation>/<SanitizedStation>_<yyyyMMdd_HHmmss>.<ext>`
  - Track number formatting: Two-digit zero-padded number (`01`, `02`, ...). If the official iTunes/MusicBrainz track number is known, it is formatted as two digits; otherwise, it is assigned the next sequential index in that album directory.
  - Collision resolution: If target file exists, append `_01`, `_02`, etc.

- **Sidecar JSON Schema**:
  - Alongside every completed `<basename>.<ext>`, a companion `<basename>.json` is written upon track completion.
  - Schema shape:
```json
{
  "title": "String",
  "artist": "String",
  "album": "String?",
  "year": "String?",
  "genre": "String?",
  "trackNumber": "Int?",
  "lyrics": "String?",
  "station": "String",
  "stationUrl": "String",
  "recordedAt": "ISO-8601 Timestamp",
  "durationSeconds": "Long",
  "fileSize": "Long",
  "codec": "mp3 | aac",
  "bitrate": "String?",
  "audioFile": "String (relative filename)",
  "coverArtFile": "String? (relative filename, e.g. cover.jpg)",
  "partial": "Boolean"
}
```

- **Enriched Metadata Lifecycle & Fallback**:
  - As soon as a new track is detected, a concurrent background query is dispatched to `MusicInfoRepository` to query iTunes and LRCLIB/MusicBrainz for album, year, genre, lyrics, and cover artwork URL.
  - Download of `cover.jpg` occurs into the destination directory. If `cover.jpg` already exists in that album folder from a previous track, redownload is skipped.
  - When the track completes, the accumulated enriched data is combined with exact file size and duration, and written to disk. If the network query timed out or returned no results, missing fields are written as `null`.

- **Pruning and Minimum Duration Filter**:
  - Any track or show file whose recorded duration is strictly less than 30 seconds (`durationSeconds < 30`) is automatically deleted from disk, along with any associated temporary sidecar artifacts.
  - This purges station jingles, 10-second station IDs, and momentary metadata glitches.

- **Network Dropout Resilience**:
  - If the player reconnects following a brief connection loss (handled by `RetryingHttpDataSource`) and the incoming metadata matches the currently recorded track, recording stream continues into the same open file without resetting duration or splitting.

- **Alarm-Triggered Recording (ADR-0005)**:
  - Configured per-alarm via a "Salvesta äratus" toggle in `AlarmDialog`.
  - Persisted in Room database `Alarm` entity: `val recordAudio: Boolean = false` (Database schema version 10 via `MIGRATION_9_10`).
  - When the alarm triggers, `AlarmTriggerCoordinator` launches playback with `EXTRA_RECORD = true`.
  - Recording continues as long as audio plays; dismissing/snoozing the alarm does not cancel recording unless playback itself is stopped.
  - Alarms with recording enabled display a red recording dot/badge in `AlarmsScreen`.

- **Continuous Stream Recording (ADR-0005)**:
  - General Settings (`SettingsScreen`) provides a dedicated "Salvestamine" section with a "Pidev salvestus" (`continuous_recording`) preference.
  - When enabled, any playback initiation (manual, widget, notification, alarm) automatically begins stream recording.
  - Continuous recording supersedes per-alarm settings (all playback sessions record).
  - Manual tap of "Peata salvestus" stops recording for the active station without terminating playback; tuning to a new station resumes recording.

- **Station-Switch Continuity**:
  - When changing stations during an active recording session (`isRecording = true`), the coordinator cleanly finalizes the previous station's file/sidecar (pruning if < 30s) and immediately routes the incoming station's stream into a new file and folder without requiring user re-activation.

- **Storage Safeguard (Low Disk Space Protection)**:
  - Coordinator checks available device storage (`StatFs`). If free disk space drops below 500 MB, recording automatically stops, the current file is flushed cleanly, and a system notification warns the user ("Salvestus peatatud: vaba kettamaht alla 500 MB").

## Testing Decisions

- **What makes a good test**:
  - Tests verify observable filesystem outcomes, timing rules, and metadata content at the public interface of `StreamRecordingCoordinator`.
  - Tests must never inspect private internal state, buffers, or mocked sockets.
- **Test Scenarios**:
  1. *Stream Splitting*: Feed a simulated stream with an ICY metadata change from Track A to Track B; verify that Track A closes and Track B begins at the exact byte boundary.
  2. *Minimum Duration Pruning*: Feed a 15-second track followed by a 180-second track; verify the 15-second file is deleted and the 180-second file is preserved.
  3. *Sidecar Verification*: Verify that the output `.json` matches the specified schema with correct duration, timestamps, artist, and album.
  4. *Partial Track Flagging*: Start recording mid-track; verify the sidecar has `"partial": true`.
  5. *Show vs Music Classification*: Provide metadata with "Otseeeter" or station name; verify it routes to `Saated/` instead of `Muusika/`.
  6. *Two-Digit Numbering*: Verify tracks are saved as `01 - ...`, `02 - ...`, and collisions use `_01`, `_02`.
  7. *Reconnect Continuity*: Simulate a momentary zero-byte gap without metadata change; verify bytes are appended to the existing file.
- **Prior Art**:
  - Existing JVM unit tests in `AlarmTriggerCoordinatorTest` verifying pure asynchronous state handling and coordination without Android platform dependencies.

## Out of Scope

- In-app audio player or browsing screen for recorded files (recordings are accessed via PC or external Android file/music apps in this version).
- In-app re-encoding or transcoding of audio streams from AAC to MP3.
- In-app ID3 tag writing directly into audio headers (delegated to external PC scripts via the rich JSON sidecar).
- Scheduled timer-based recording (e.g. "record station X at 20:00").

## Further Notes

- This specification adheres to architecture decision records [docs/adr/0004-stream-recording-architecture.md](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/docs/adr/0004-stream-recording-architecture.md) and [docs/adr/0005-alarm-and-continuous-recording.md](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/docs/adr/0005-alarm-and-continuous-recording.md), as well as domain definitions in [GLOSSARY.md](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/GLOSSARY.md).

