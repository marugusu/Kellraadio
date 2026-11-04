# Süsteemsete äratuste taastamine ja püsivus (Alarm Persistence and Recovery)

## Problem Statement

Kasutaja konfigureerib Kellraadios äratuse, et ärgata igal hommikul kindlal kellaajal lemmikraadiojaama saatel. Reaalsetes elulistes tingimustes puutub telefon aga kokku sündmustega, mis võivad süsteemse äratuse tühistada või nihutada:
1. Telefon teeb öösel automaatse taaskäivituse (nt operatsioonisüsteemi turvauuendus või aku tühjenemise järgne taaskäivitus), mille käigus Android pühib mälust kõik süsteemsed `AlarmManager`i äratused.
2. Saabub sügisene või kevadine kellakeeramine (suveaeg/talveaeg) või kasutaja vahetab ajavööndit, kuid süsteemne äratus jääb vana ajatempli tõttu käivituma vale tunni peal.
3. Kellraadio äpi taustaprotsess suletakse mälupuudusel või jookseb krahhi, mistõttu kasutaja kardab, kas äratus üldse süsteemi alles jäi.
4. Äratuse käivitumisel tekib ootamatu tõrge (nt võrguühenduse puudumine, vigane striimi URL või taustateenuse käivituspiirang), mis krahhib täitmisahela enne, kui järgmine äratus jõutakse süsteemis ajastada. Tulemusena katkeb tulevaste päevade äratuste ahel jäädavalt.

## Solution

Tagada äratuste vankumatu püsivus ja automaatne isetervenemine läbi mitmetasemelise taastamissüsteemi:
1. **Püsivus kettal ja süsteemis:** Kõik äratused püsivad turvaliselt lokaalses andmebaasis ning järgmine äratus registreeritakse Androidi süsteemiteenuses `AlarmManager.setAlarmClock`. Äpi krahhi või protsessi taustal sulgemise korral jääb äratus süsteemi alles ja äratab rakenduse õigel ajal.
2. **Süsteemsete sündmuste järgne taastamine:** Seadme taaskäivitamisel (`BOOT_COMPLETED`), rakenduse paketi uuendamisel (`MY_PACKAGE_REPLACED`), kellaaja käsitsi muutmisel (`TIME_SET`) ja ajavööndi vahetumisel (`TIMEZONE_CHANGED`) loetakse andmebaasist viivitamatult aktiivsed äratused ning arvutatakse ja registreeritakse järgmine süsteemne äratus uue õige kellaaja järgi.
3. **Tõrkekindel ahelajastamine:** Äratuse käivitumisel eraldatakse meedia taasesituse käivitamine ja järgmise äratuse ajastamine. Isegi kui raadioteenuse start peaks ebaõnnestuma, deaktiveeritakse ühekordne äratus ja ajastatakse järgmine aktiivne äratus alati, vältides tulevaste äratuste katkemist.
4. **Isetervenemine äpi avamisel:** Igal rakenduse käivitamisel viiakse andmebaasi ja süsteemse `AlarmManager`i seis taustal automaatselt kooskõlla.

## User Stories

1. As a radio listener, I want my scheduled alarm to remain active in the system if the app process is terminated or crashes in the background, so that I still wake up at the right time without needing to reopen the app.
2. As a radio listener, I want my alarms to be automatically restored when my phone reboots after an overnight system update, so that I don't oversleep due to device restarts.
3. As a frequent traveler changing time zones, I want my alarm to recalculate its trigger time automatically when the system time zone changes, so that it fires at the local time I configured.
4. As a radio listener, I want my alarm time to remain accurate during seasonal daylight saving time shifts (clocks turning back or forward), so that I wake up at the correct real-world hour.
5. As a phone user, I want the system clock icon to appear on my Android status bar and lock screen for Kellraadio alarms, so that I have clear visual confirmation that the system holds my alarm.
6. As a radio listener, I want one-time alarms to be marked as completed and disabled even if the internet stream fails to start, so that the broken alarm does not loop or schedule itself for the following day.
7. As a radio listener, I want the next chronological alarm to be scheduled without fail even if the current radio playback service throws an unexpected startup exception, so that a playback failure on one day does not break my alarms for future days.
8. As a radio listener, I want the app to automatically verify and synchronize system alarms whenever I open the application, so that any unexpected drift or external desynchronization is silently repaired.
9. As a user editing or toggling an alarm in the app, I want the next system alarm to immediately recalculate and update on the home screen widget, so that all surfaces reflect the exact next upcoming alarm time.
10. As a radio listener, I want the alarm recovery process after boot to happen asynchronously without slowing down device startup, so that system boot performance is unaffected.
11. As a radio listener with multiple configured alarms, I want the recovery logic to evaluate all enabled alarms and select the earliest chronological occurrence, so that the nearest alarm always has priority in the system.
12. As a battery-conscious phone user, I want system broadcast receivers for boot and time events to finish their work cleanly without holding the device awake indefinitely, so that battery is preserved.

## Implementation Decisions

- **System Broadcast Filtering for Time and Boot Events**:
  - The existing boot broadcast receiver module is extended to listen for system time changes (`TIME_SET`) and time zone modifications (`TIMEZONE_CHANGED`) alongside system boot completion (`BOOT_COMPLETED`) and application package replacement (`MY_PACKAGE_REPLACED`).
  - All triggers route through the same unified alarm rescheduling pipeline, which inspects enabled alarms in persistent storage and registers the next upcoming alarm with the system alarm manager.
  - No duplicate receiver classes are introduced; the single system event receiver encapsulates all operating system lifecycle recovery triggers.

- **Fail-Safe Alarm Coordinator Lifecycle (ADR-0002)**:
  - The alarm coordinator decouples playback service invocation from the rescheduling and lifecycle completion of the current alarm.
  - Playback launch is guarded within isolated error handling so that runtime exceptions (such as service startup constraints or networking failures) do not prevent subsequent execution steps.
  - One-time alarms are marked as inactive in persistent storage regardless of playback failure to prevent undesirable re-triggering.
  - Invocation of the next alarm rescheduling callback is guaranteed, ensuring the continuity of the alarm schedule chain.
  - CPU wake lock release remains strictly enforced in a final execution block.

- **Startup Self-Healing**:
  - The primary UI ViewModel initiates a silent background verification upon initialization that ensures the next alarm is registered in the system alarm manager based on current persistent storage state.
  - This background check operates without user-facing toasts or UI blocking, guaranteeing self-healing after any anomalous system state.

- **Direct Boot Boundary (ADR-0002)**:
  - Direct boot mode (`LOCKED_BOOT_COMPLETED` prior to first user credential entry) is explicitly excluded from scope due to credential-encrypted database boundaries and network constraints on locked devices.

## Testing Decisions

- **What makes a good test**:
  - A good test targets the external behavioral contracts and invariants across the highest available seam without testing private implementation details.
  - Tests must verify:
    1. Rescheduling callback execution is guaranteed when playback service launch succeeds.
    2. Rescheduling callback execution is guaranteed even when playback service launch throws a runtime exception.
    3. One-time alarm deactivation in persistent storage occurs even if playback launch fails.
    4. Wake lock release is guaranteed under both success and failure execution branches.
- **Tested Modules**:
  - The alarm execution coordinator module (`AlarmTriggerCoordinator`).
  - Alarm time calculation and date boundary utilities.
- **Prior Art**:
  - Existing JVM unit tests in `AlarmTriggerCoordinatorTest` verifying asynchronous wake lock lifecycle, fake DAO updates, and mock playback launchers without Android framework dependencies.

## Out of Scope

- Implementing Direct Boot support (`android:directBootAware="true"`) or migrating database storage to Device-Protected Storage before first user PIN entry.
- Handling Android "Force Stop" scenarios where the operating system deliberately purges all package alarms until manual app restart.
- Implementing offline fallback audio alarms (synthesized tones or local media files) in the event of persistent network loss.
- Custom notification sound overrides or volume hijacking.

## Further Notes

- This specification satisfies the requirements discussed in architecture decision record `docs/adr/0002-alarm-persistence-and-recovery.md` and maintains full backwards compatibility with domain models defined in `GLOSSARY.md`.

