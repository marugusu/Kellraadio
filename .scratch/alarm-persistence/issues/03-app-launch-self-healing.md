# 03: Rakenduse käivituse isetervenemine (App Launch Alarm Self-Healing)

**What to build:**
Igal rakenduse käivitamisel viib ViewModel taustal läbi vaikse enesekontrolli, mis loeb andmebaasist kehtiva aktiivsete äratuste seisu ja sünkroniseerib selle süsteemse `AlarmManager`iga. Kui mingil põhjusel peaks süsteemi ja andmebaasi seis olema lahknenud, likvideeritakse ebakõla automaatselt ilma kasutajat häirivate teavitusteta või UI-d blokeerimata.

**Blocked by:** 01: Tõrkekindel äratuse koordinaator ja ahela katkematus

**Status:** done (branch fix/app-launch-self-healing, needs on-device check)

- [x] MainViewModel initsialiseerimise ajal käivitatakse taustakorutiinis `AlarmUtils.scheduleNextAlarmInternal(context)`.
- [x] Avakuva vidin uuendatakse koos süsteemse äratuse sünkroonimisega.
- [x] Taustakontroll ei tekita kasutajale nähtavaid Toast-teavitusi ega aeglusta rakenduse käivitumist.

