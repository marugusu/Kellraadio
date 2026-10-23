# 02: Ühekordse äratuse elutsükli turvaline deaktiveerimine

**What to build:** Muuta ühekordse äratuse deaktiveerimise ja järgmise äratuse ümberplaneerimise ajastust nii, et äratus märgitakse andmebaasis lõpetatuks alles pärast meediateenuse käivituskäsu edukat üleandmist süsteemile. Kui meediateenuse käivitamisel tekib ootamatu süsteemitõrge, ei lülitu äratus enneaegselt passiivseks, säilitades kasutaja konfigureeritud äratuse.

**Blocked by:** 01: WakeLockiga äratuse vastuvõtja ja teenuse käivitus unerežiimis

**Status:** done

- [x] Ühekordse äratuse deaktiveerimine andmebaasis toimub alles pärast meediateenuse edukat väljakutset.
- [x] Järgmise kronoloogilise äratuse ümberplaneerimine ja avakuva vidina oleku uuendamine toimub pärast teenuse käivitamist.
- [x] Teenuse käivitamise erandi või rikke korral jääb äratus andmebaasis alles ja ei lülitu enneaegselt passiivseks.
- [x] Üksustestid kinnitavad ühekordse äratuse korrektset elutsüklit ja olekumuudatusi nii õnnestunud kui ka ebaõnnestunud teenusekäivituse stsenaariumis.
