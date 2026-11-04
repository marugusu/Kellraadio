# Äratuskella käivitumise töökindlus unerežiimis (Alarm Execution Reliability)

## Problem Statement

Kasutaja seadistab Kellraadios hommikuse äratuse, et ärgata oma lemmikraadiojaama saatel. Öösel viibib telefon sügavas unerežiimis (Doze / deep sleep), ekraan on lukus ja välja lülitatud. Kui saabub määratud äratuse aeg, süsteemne äratuse sündmus küll vallandub, kuid raadio esitamine ei käivitu ning heli ei kõla. Kasutaja magab sisse, sest äratuse taustaprotsess ei hoia telefoni protsessorit piisavalt kaua ärkvel, et andmebaasipäring ja meediateenus jõuaksid edukalt käivituda.

## Solution

Tagada äratuse 100% töökindel käivitumine sügavast unerežiimist. Kui süsteemne äratuse signaal saabub, võetakse koheselt riistvaraline protsessori ärkvelolekulukk (WakeLock), mis hoiab seadme ärkvel kogu vajaliku asünkroonse ettevalmistusaja vältel (andmebaasist jaama info pärimine ja taustateenuse käivituskäsk). Raadioteenus võtab juhtimise ja ärkveloleku üle enne esialgse luku vabastamist. Ühekordne äratus deaktiveeritakse andmebaasis alles pärast teenuse edukat käivitamist. Raadio alustab mängimist kasutaja poolt varem seatud helitugevusel nagu klassikaline kellraadio.

## User Stories

1. As a radio listener, I want my scheduled alarm to reliably start playing radio when my phone is in deep sleep overnight, so that I wake up on time every morning.
2. As a radio listener, I want the radio to start playing at the exact volume level I left it at when going to bed, so that the alarm does not blast abruptly or change my preferred volume.
3. As a radio listener, I want the alarm to trigger my selected radio station without requiring any manual screen touches or unlocking, so that I hear the broadcast immediately upon waking.
4. As a radio listener, I want one-time alarms to remain saved and enabled if a transient system error interrupts service startup, so that I don't lose my alarm configuration without audio having played.
5. As a radio listener, I want one-time alarms to be marked as completed in the app once playback has been dispatched, so that the alarm state accurately reflects that it already fired.
6. As a radio listener, I want recurring alarms to automatically schedule their next chronological occurrence right after firing, so that I remain covered for future configured days.
7. As a battery-conscious phone user, I want the alarm processor wake lock to be held only for the minimum required time (with a strict timeout), so that my battery is not drained if an unexpected error occurs during startup.
8. As a radio listener, I want the alarm playback notification to allow me to immediately stop the radio with one tap, so that I can easily silence the radio once I am awake.
9. As a user with a locked phone, I want the ongoing radio notification to display the active station name, so that I immediately know what station is playing.
10. As a radio listener, I want my alarms to function without disturbing my lock screen with unnecessary full-screen popup activities, so that my bedside lock screen remains peaceful and standard.
11. As a radio listener, I want the alarm to play the pure radio stream without intrusive fallback ringtones or electronic beeps, so that my wake-up experience matches a traditional clock radio.
12. As a phone user, I want the alarm scheduling to be resilient against device reboot, so that my alarms survive overnight system updates or reboots.

## Implementation Decisions

- **WakeLock Management during Alarm Broadcast**:
  - The broadcast receiver module handling the system alarm intent acquires a partial processor wake lock (`PARTIAL_WAKE_LOCK`) immediately in synchronous execution before delegating work to any asynchronous dispatcher.
  - The wake lock is tagged with a distinct identifier to facilitate power tracking and debugging.
  - The wake lock is configured with a safe maximum timeout (30 seconds) to prevent battery drain in catastrophic failure scenarios.
  - The wake lock is released in a guaranteed `finally` block once the foreground playback service launch request has been successfully dispatched to the system.

- **Audio Stream and Volume Preservation (ADR-0001)**:
  - Playback initiated by an alarm adheres strictly to the classic clock-radio domain model: audio routes through the standard media playback pipeline (`USAGE_MEDIA`).
  - No programmatic volume alteration is performed on the audio stream; it faithfully outputs at the user's existing volume setting.
  - No secondary fallback ringtones or alarms from system ringtone storage are triggered.

- **One-Time Alarm Lifecycle Reordering**:
  - The database update that transitions a one-time alarm from enabled to disabled is deferred until *after* the foreground service start call completes.
  - If the service dispatch encounters an immediate system exception, the alarm state in the database is not prematurely deactivated.

- **Service Hand-Off**:
  - The media playback service already possesses its own 30-second wake lock upon receiving an alarm trigger intent, ensuring seamless hand-off from receiver to playback buffering without a CPU sleep window between the two components.

- **UI and Notification Boundary**:
  - No new full-screen activities or lock-screen override dialogs are introduced.
  - Notification channels and presentation remain unified under the existing media service notification structure.

## Testing Decisions

- **What makes a good test**:
  - A good test verifies visible external behavior and invariants at the seam, not internal implementation trivia.
  - The test must verify that invoking the alarm receiver with a valid alarm ID:
    1. Holds the CPU awake during the asynchronous transition.
    2. Successfully commands the foreground media service to start with the target station stream and the alarm trigger flag.
    3. Updates the database record for single-use alarms upon successful dispatch.
    4. Guarantees the release of the wake lock even if the database or service launch throws an unexpected exception.
- **Tested Modules**:
  - The alarm broadcast receiver module.
  - The alarm scheduling and rescheduling utility functions.
- **Prior Art**:
  - Existing JVM unit tests in the codebase using MockK/Mockito and standard coroutine test dispatchers (`runTest`, `StandardTestDispatcher`).

## Out of Scope

- Introducing local audio ringtone fallbacks when internet streams fail.
- Overriding Android system volume levels or forcing alarm stream routing (`USAGE_ALARM`).
- Creating full-screen lock screen dismiss/snooze activities (`FLAG_TURN_SCREEN_ON`).
- Redesigning notification channels or altering notification priority levels.

## Further Notes

- Documented in project domain architecture decision record `docs/adr/0001-radio-alarm-behavior.md` and domain glossary `GLOSSARY.md`.
- Conforms to AGENTS.md Section 2.1 battery and WakeLock hygiene policy: WakeLocks are strictly reserved for alarm trigger activation until audio playback begins.
