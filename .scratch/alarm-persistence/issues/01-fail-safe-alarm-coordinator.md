# 01: Tõrkekindel äratuse koordinaator ja ahela katkematus (Fail-Safe Alarm Coordinator & Rescheduling Guarantee)

**What to build:**
Kui äratus saabub ja raadioteenuse käivitamine ebaõnnestub (nt striimi võrguviga, server maas või süsteemne taustateenuse piirang), tagab koordinaator ikkagi ühekordse äratuse deaktiveerimise andmebaasis, protsessori ärkvelolekuluku vabastamise ning järgmise süsteemse äratuse ajastamise tagasikutse. Ühe äratuse esitustõrge ei tohi kunagi katkestada tulevaste päevade äratuste ahelat ega jätta vigast jaama homseks korduma.

**Blocked by:** None (can start immediately)

**Status:** done (branch fix/fail-safe-alarm-coordinator)

- [x] Kui meediateenuse käivitamine viskab erindi, logitakse tõrge, kuid ühekordne äratus märgitakse andmebaasis ikkagi deaktiveerituks.
- [x] Järgmise äratuse ajastamise tagasikutse (`onAlarmRescheduled`) käivitatakse kindlasti ka siis, kui taasesituse start viskab erindi.
- [x] Protsessori ärkvelolekulukk (`WakeLock`) vabastatakse igal juhul, vältides aku tühjenemist vea korral.
- [x] Üksustestid failis `AlarmTriggerCoordinatorTest` verifitseerivad, et nii õnnestunud stardi kui visatud erindi korral kutsutakse välja `onAlarmRescheduled`, deaktiveeritakse ühekordne äratus ja vabastatakse lukk.

