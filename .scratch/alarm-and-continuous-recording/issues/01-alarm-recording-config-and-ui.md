# 01: Äratuse salvestuse andmebaas, lüliti ja indikaator (Alarm Recording Config & UI)

**What to build:**
Võimaldada kasutajal määrata igale äratusele eraldi salvestamise valik. Äratuse loomise või muutmise dialoogis kuvatakse lüliti "Salvesta äratus", valik salvestatakse püsivalt rakenduse andmebaasi (skeemi migratsioon versioonile 10) ning äratuste peavaates kuvatakse salvestaval äratusel selge punane salvestusindikaator kellaaja kõrval.

**Blocked by:** None (can start immediately)

**Status:** completed

- [x] Room andmebaasi `Alarm` olemisse on lisatud väli `recordAudio: Boolean = false` ja andmebaasi versioon on tõstetud versioonile 10.
- [x] Lisatud on toimiv SQLite migratsioon `MIGRATION_9_10`, mis lisab olemasolevale `alarms` tabelile tulba `recordAudio INTEGER NOT NULL DEFAULT 0`.
- [x] Andmebaasi migratsioonile on kirjutatud ja läbivad automatiseeritud üksustestid.
- [x] Äratuse seadistamise dialoogis (`AlarmDialog`) on funktsioneeriv Material 3 lüliti "Salvesta äratus" koos selgitava alapealkirjaga.
- [x] Äratuste nimekirjas (`AlarmsScreen`) kuvatakse salvestava äratuse juures punane salvestusmärk (indikaator).
- [x] Olemasolevad äratused säilivad uuendamisel tõrgeteta, vaikimisi väljalülitatud salvestamisega.
