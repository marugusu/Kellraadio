# Äratuse salvestamine ja pidev helivoo salvestus (Alarm and Continuous Stream Recording)

## Problem Statement

Kasutaja kuulab ja ärkab Kellraadio vahendusel oma lemmikraadiojaamade saatel ning soovib eetri kaudu edastatavaid laule ja saateid automaatselt arhiivida. Tänases rakenduses puudub võimalus salvestamist automaatselt ajastada:
1. Kui hommikune äratus heliseb (nt kell 07:00 lemmiksaate alguses), peab kasutaja telefoni avama, ekraaniluku eemaldama ja käsitsi salvestusnupule vajutama, mis riksub hommikuse rahu või jääb magamise tõttu tegemata.
2. Kasutaja, kes soovib arhiivida kõiki kuulatud jaamu ja muusikat, peab iga jaama käivitamisel meeles pidama salvestamise käsitsi alustamist.
3. Jaama vahetamisel katkeb olemasolev salvestussessioon ja uus jaam ei hakka salvestama ilma uue käsitsi vajutuseta.
4. Järelevalveta automaatse salvestamise või pikaajalise kuulamise korral valitseb oht, et seadme salvestusmälu saab kriitiliselt täis, põhjustades süsteemitõrkeid või rakenduse krahhe.

## Solution

Täiendada Kellraadio salvestussüsteemi kahe automaatse käivitusmehhanismiga, tagades samal ajal seadme mälumahu kaitse ning katkematu sessioonihalduse:
1. **Äratusepõhine salvestus (ADR-0005)**: Iga äratuse seadistamisel saab kasutaja valida, kas antud äratus käivitab jaama mängima hakkamisel kohe ka taustal salvestamise. Äratuse vaigistamine või teavituse sulgemine ei katkesta salvestust — salvestus kestab seni, kuni kasutaja peatab esituse või salvestuse.
2. **Pidev helivoo salvestus üldseadetes**: Rakenduse seadetesse lisandub lüliti "Pidev salvestus", mille sisselülitamisel salvestatakse automaatselt iga helivoo esitust sõltumata käivitusviisist (käsitsi, vidinast või äratusest).
3. **Jaamavahetuse sujuvus**: Salvestussessioon kestab jaama vahetamisel edasi: eelmine fail suletakse puhtalt (praakides alla 30-sekundilised klipid) ja uue jaama voo saabumisel luuakse viivitamatult uus fail ja kaust.
4. **Kettamahu automaatkaitse (Storage Safeguard)**: Salvestus peatub automaatselt ja kasutajale kuvatakse hoiatusteavitus, kui seadme vaba kettamaht langeb alla 500 MB.

## User Stories

1. As a radio listener waking up to a morning radio show, I want to enable recording on a specific alarm, so that the morning show is captured automatically while I wake up without touching my phone.
2. As a music collector, I want my weekday wake-up alarm to record my favorite music station, so that new tracks played in the early morning are neatly saved to my library.
3. As a radio listener, I want my regular wake-up alarms to play without recording by default, so that my storage is not filled by alarms where I just want to wake up.
4. As an alarm manager, I want to toggle recording on and off individually for each alarm in the alarm editor dialog, so that I have granular control over which alarms record.
5. As an alarm manager, I want the alarms list screen to display a distinct red recording badge next to any alarm that has recording enabled, so that I can immediately see which alarms are configured to record.
6. As a radio listener waking up to an alarm recording, I want the recording to continue uninterrupted even when I dismiss the alarm notification or snooze, so that I don't lose the radio program while waking up.
7. As a radio listener, I want an alarm recording to stop cleanly when I explicitly stop radio playback or tap the stop recording button, so that the file is safely finalized on disk.
8. As an audiophile who archives radio broadcasts, I want a "Continuous Recording" master switch in the app settings, so that every time I stream any radio station, recording begins automatically.
9. As a power user, when Continuous Recording is enabled in Settings, I want every alarm and manual playback session to record automatically, so that no manual taps are ever required to capture radio audio.
10. As a radio listener with Continuous Recording enabled, I want to be able to manually tap "Stop Recording" while listening to a station without stopping playback, so that I can stop archiving a specific track or program I am not interested in.
11. As a listener with Continuous Recording enabled who manually stopped recording on station A, I want recording to automatically resume when I tune into station B, so that my global preference continues to be honored for future listening.
12. As a radio listener recording a broadcast who changes stations, I want the active recording session to persist, seamlessly finalizing the previous station's file and opening a new file for the newly selected station.
13. As a listener switching stations while recording, I want the finalized file of the previous station to be pruned if it lasted less than 30 seconds, so that quick channel surfing does not leave garbage snippets on my storage.
14. As a phone owner with limited storage, I want the recorder to continuously monitor available storage space on my device, so that unattended recordings cannot fill my phone to 100% capacity.
15. As a phone owner, I want recording to stop immediately and finalize files safely if free device storage drops below 500 MB, so that the operating system remains stable.
16. As a user whose recording stopped due to low storage, I want a system notification warning me that recording was stopped due to low disk space, so that I know why recording ceased.
17. As an alarm user whose phone has no internet connection when the alarm triggers, I want the system to handle the network error without starting an empty or corrupted recording session.
18. As a user upgrading the app, I want my existing alarms to be preserved with recording disabled by default, so that my existing alarm schedule continues working without unexpected storage use.
19. As a user opening Settings, I want a dedicated "Salvestamine" section grouping the continuous recording switch, storage protection status, and storage folder path, so that recording settings are discoverable and organized.
20. As a user connecting my phone to a PC via USB, I want all recordings initiated by alarms or continuous recording to appear in the standard Music directory with their JSON sidecar files, so that I can manage them freely.

## Implementation Decisions

- **Room Database Migration (`MIGRATION_9_10`)**:
  - The `Alarm` database entity is extended with `val recordAudio: Boolean = false`.
  - The Room database version is incremented from 9 to 10 in `AppDatabase`.
  - SQLite migration script added:
    ```sql
    ALTER TABLE alarms ADD COLUMN recordAudio INTEGER NOT NULL DEFAULT 0
    ```
  - Room migration registered and verified with unit tests.

- **Alarm Trigger Coordinator and Seam Extension**:
  - The `AlarmPlaybackLauncher` abstraction seam is updated:
    ```kotlin
    interface AlarmPlaybackLauncher {
        fun launchPlaybackService(streamUrl: String, stationName: String, recordAudio: Boolean = false)
    }
    ```
  - `AlarmTriggerCoordinator.processAlarm(alarmId)` queries the alarm entity and supplies `alarm.recordAudio` to the launcher.
  - `AndroidAlarmPlaybackLauncher` passes `EXTRA_RECORD = recordAudio` in the intent starting `RadioService`.

- **Continuous Recording Preferences**:
  - Stored in `SharedPreferences` under key `AppConfig.Recording.PREF_CONTINUOUS_RECORDING`.
  - Exposed via `MainViewModel` UI state to `SettingsScreen`.
  - Evaluated in `RadioService` whenever playback commences: if `continuous_recording` is `true` OR `intent.getBooleanExtra(EXTRA_RECORD, false)` is `true`, `startRecording()` is automatically triggered once stream buffering completes.

- **Station Switch Continuity in `RadioService`**:
  - `RadioService` tracks user-initiated recording state independently from individual station playback lifecycle.
  - When `playStation` is invoked while `isRecording` is active:
    1. The outgoing station's active file is closed via `recordingCoordinator.stopRecording()` (with pruning of < 30s clips).
    2. The recording coordinator resets station-specific track state.
    3. Once the new station connects and begins playing audio, `recordingCoordinator.startRecording(...)` automatically re-engages without requiring UI input.
  - If the user explicitly tapped the "Stop Recording" UI action during a stream, recording remains paused until the next station change (if continuous recording is enabled) or until manual re-engagement.

- **Storage Space Seam (`StorageSpaceChecker`)**:
  - Introduced to enable deterministic JVM testing without Android `StatFs`:
    ```kotlin
    interface StorageSpaceChecker {
        fun getAvailableBytes(path: File): Long
    }
    ```
  - The production Android implementation queries `StatFs(path.absolutePath).availableBytes`.
  - `StreamRecordingCoordinator` checks available storage before starting a recording session and upon each track/show transition.
  - Threshold: `AppConfig.Recording.MIN_FREE_STORAGE_BYTES = 500L * 1024L * 1024L` (500 MB).
  - If available bytes < 500 MB:
    1. Active recording is stopped and flushed.
    2. A low-storage event is emitted to `RadioService` to display a warning notification to the user.

- **UI Components**:
  - `AlarmDialog`: Material 3 `Switch` row "Salvesta äratus" with explanatory subtitle.
  - `AlarmsScreen`: Visual red recording badge / icon beside the alarm time.
  - `SettingsScreen`: New "Salvestamine" section with "Pidev salvestus" switch and low-storage threshold indicator.

## Testing Decisions

- **What makes a good test**:
  - Tests verify observable behavior, state transitions, and file outcomes at public coordinator seams without inspecting private loops or reflection.
  - Tests must execute deterministically on the JVM without requiring an Android emulator or device hardware.
- **Tested Modules**:
  1. `AlarmTriggerCoordinatorTest`: Verify that when an alarm with `recordAudio = true` fires, `AlarmPlaybackLauncher` receives `recordAudio = true`. Verify that single-use alarms are still disabled properly and recurring alarms rescheduled.
  2. `DatabaseMigrationsTest`: Verify that `MIGRATION_9_10` correctly migrates a version 9 database schema containing existing alarms to version 10 with `recordAudio = false` default.
  3. `StorageSpaceCheckerTest` / `StreamRecordingCoordinatorTest`:
     - Verify that if `StorageSpaceChecker` reports < 500 MB, recording refuses to start or safely stops existing recording.
     - Verify that station change during active recording properly completes the previous track and seamlessly initiates a new file for the next station.
- **Prior Art**:
  - `AlarmTriggerCoordinatorTest.kt` in `app/src/test/java/app/radiorecalarm/`.

## Out of Scope

- Scheduled recording of arbitrary time intervals without an alarm (e.g. "record station X from 14:00 to 16:00 without playing sound").
- Custom per-alarm storage limit or time duration cutoff (recording stops when playback stops).
- Cloud backup or automatic syncing of recordings to Google Drive or external servers.
- Editing or trimming recorded audio files inside the Android app.

## Further Notes

- Adheres to architecture decision records [docs/adr/0004-stream-recording-architecture.md](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/docs/adr/0004-stream-recording-architecture.md) and [docs/adr/0005-alarm-and-continuous-recording.md](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/docs/adr/0005-alarm-and-continuous-recording.md).
- Adheres to domain language in [GLOSSARY.md](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/GLOSSARY.md).

