# 02: Äratuse salvestamise käivitamine ja elutsükli sidumine (Alarm Recording Trigger & Lifecycle)

**What to build:**
Kui käivitub äratus, millel on salvestamine sisse lülitatud, käivitub koos raadiovooga automaatselt taustal helisalvestus. Äratuse vaigistamine või teavituse sulgemine ei katkesta salvestust — salvestus kestab seni, kuni kasutaja peatab heliesituse või vajutab käsitsi salvestuse peatamise nuppu.

**Blocked by:** 01: Äratuse salvestuse andmebaas, lüliti ja indikaator (Alarm Recording Config & UI)

**Status:** ready-for-agent

- [ ] `AlarmPlaybackLauncher` õmblus toetab salvestuse parameetrit (`recordAudio: Boolean`).
- [ ] `AlarmTriggerCoordinator` loeb andmebaasist äratuse `recordAudio` väärtuse ja edastab selle usaldusväärselt käivitusõmblusele.
- [ ] `RadioService` alustab automaatselt `StreamRecordingCoordinator` abil salvestamist, kui äratuse käivitus sisaldab salvestamise lippu.
- [ ] Äratuse režiimi lõppemine (teavituse sulgemine, vaigistamine) ei peata salvestust, kui raadio mängib edasi.
- [ ] Raadio heliesituse peatamine ("Stop" nupp) lõpetab ka aktiivse salvestuse ja suleb failid korrektselt.
- [ ] Kasutaja saab salvestuse eraldi peatada teavituse või mängija kaudu ilma raadiot kinni panemata.
- [ ] Koordinaatori ja teenuse käitumist katavad läbivad JVM üksustestid.

