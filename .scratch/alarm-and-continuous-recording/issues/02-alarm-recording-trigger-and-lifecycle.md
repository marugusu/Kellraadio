# 02: Äratuse salvestamise käivitamine ja elutsükli sidumine (Alarm Recording Trigger & Lifecycle)

**What to build:**
Kui käivitub äratus, millel on salvestamine sisse lülitatud, käivitub koos raadiovooga automaatselt taustal helisalvestus. Äratuse vaigistamine või teavituse sulgemine ei katkesta salvestust — salvestus kestab seni, kuni kasutaja peatab heliesituse või vajutab käsitsi salvestuse peatamise nuppu.

**Blocked by:** 01: Äratuse salvestuse andmebaas, lüliti ja indikaator (Alarm Recording Config & UI)

**Status:** completed

- [x] `AlarmPlaybackLauncher` õmblus toetab salvestuse parameetrit (`recordAudio: Boolean`).
- [x] `AlarmTriggerCoordinator` loeb andmebaasist äratuse `recordAudio` väärtuse ja edastab selle usaldusväärselt käivitusõmblusele.
- [x] `RadioService` alustab automaatselt `StreamRecordingCoordinator` abil salvestamist, kui äratuse käivitus sisaldab salvestamise lippu.
- [x] Äratuse režiimi lõppemine (teavituse sulgemine, vaigistamine) ei peata salvestust, kui raadio mängib edasi.
- [x] Raadio heliesituse peatamine ("Stop" nupp) lõpetab ka aktiivse salvestuse ja suleb failid korrektselt.
- [x] Kasutaja saab salvestuse eraldi peatada teavituse või mängija kaudu ilma raadiot kinni panemata.
- [x] Koordinaatori ja teenuse käitumist katavad läbivad JVM üksustestid.
