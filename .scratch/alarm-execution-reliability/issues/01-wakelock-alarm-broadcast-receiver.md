# 01: WakeLockiga äratuse vastuvõtja ja teenuse käivitus unerežiimis

**What to build:** Tagada, et sügavas unerežiimis (Doze / deep sleep) asuv telefon ei läheks tagasi magama enne, kui äratuse taustaprotsess on jaama andmed andmebaasist hankinud ja raadioteenuse edukalt käivitanud. Äratuse vastuvõtja peab koheselt haarama riistvaralise protsessori ärkvelolekuluku (WakeLock turvalimiidiga 30s), hoidma protsessorit ärkvel kogu asünkroonse päringu vältel ning vabastama luku kindlalt pärast meediateenuse käivitamist.

**Blocked by:** None (can start immediately)

**Status:** done

- [x] Äratuse sündmuse saabumisel haaratakse koheselt osaline protsessori ärkvelolekulukk (PARTIAL_WAKE_LOCK).
- [x] Protsessorit hoitakse ärkvel läbi asünkroonse andmebaasipäringu kuni raadioteenuse käivitamiseni.
- [x] Raadioteenus käivitatakse äratuse parameetritega (TRIGGERED_BY = "ALARM" ja jaama metaandmed).
- [x] Ärkvelolekulukk vabastatakse kindlalt lõpetamisel või tõrke korral (finally plokis).
- [x] Äratuse vastuvõtja elutsükkel ja lukuhaldus on kaetud automatiseeritud üksustestidega.

