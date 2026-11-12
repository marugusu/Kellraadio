# 05: Kettamahu kaitsepiir (Storage Safeguard & Low Space Warning)

**What to build:**
Kaitsta telefoni salvestusmälu kriitilise täissaamise eest pikaajalise automaatse salvestamise või unustatud äratuse korral. Kui seadme vaba kettamaht langeb alla 500 MB, peatab salvestussüsteem salvestamise automaatselt, suleb aktiivsed failid puhtalt ja teavitab kasutajat süsteemse teavitusega.

**Blocked by:** 02: Äratuse salvestamise käivitamine ja elutsükli sidumine (Alarm Recording Trigger & Lifecycle)

**Status:** completed

- [x] Loodud on isoleeritud testimise õmblus `StorageSpaceChecker` koos Androidi `StatFs` produktsioonirealisatsiooniga.
- [x] `StreamRecordingCoordinator` kontrollib vaba kettamahtu enne salvestamise alustamist ning iga uue pala või saate alguses.
- [x] Kui vaba maht on alla `AppConfig.Recording.MIN_FREE_STORAGE_BYTES` (500 MB), salvestust ei alustata või käimasolev salvestus peatatakse koheselt.
- [x] Salvestuse peatamisel suletakse pooleliolev helifail ja JSON-kõrvafail korrektselt (alla 30s klipid eemaldatakse).
- [x] Kettamahu kriitilisel piiril edastatakse `RadioService`-le sündmus, mis kuvab kasutajale selge süsteemse teavituse ("Salvestus peatatud: vaba kettamaht alla 500 MB").
- [x] Äratuse käivitumisel ilma võrguühenduseta salvestamist ei alustata ega tekitata tühje faile.
- [x] Kettamahu kontrolli, piirmäärasid ja teavituse saatmist katavad deterministlikud JVM üksustestid test-duubliga.
