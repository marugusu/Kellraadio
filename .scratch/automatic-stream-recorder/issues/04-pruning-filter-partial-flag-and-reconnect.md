# 04: Prügiklippide filter, osalised lood ja võrgu taastumine (Pruning Filter, Partial Flag & Reconnect Continuity)

**What to build:**
Salvestuse puhastamine lühikestest jaamakõllidest ning vigastest segmentidest, poolelt loolt alustamise toetamine ja lühikeste võrgukatkestuste silumine. Kui suletava pala või saate kestus on rangelt alla 30 sekundi, kustutatakse helifail ja sellega seotud ajutised failid automaatselt kettalt. Kui kasutaja vajutab salvestusnuppu hetkel, mil laul juba käib, salvestatakse lugu alates nupuvajutusest ja selle kõrvafail saab märke `"partial": true`. Lühiajalise võrgukatkestuse või puhverdamise korral jätkatakse sama faili kirjutamist ilma seda pooleks lõikamata, kui esitaja ja pealkiri on samad.

**Blocked by:** 03: JSON kõrvafailide genereerimine, kaanepildi laadimine ja API päringud

**Status:** ready-for-agent

- [ ] Loo või saate sulgemisel kontrollitakse selle tegelikku kestust: kui `durationSeconds < 30`, kustutatakse helifail ja kõrvafail viivitamatult kettalt.
- [ ] Kui salvestamine käivitatakse jaamas juba mängiva laulu ajal, saab kõrvafaili väli `partial` väärtuseks `true` (täispika pala puhul `false`).
- [ ] Kui võrguühendus taastub lühiajaliselt ja jaama metainfo vastab parajasti kirjutatavale palale, jätkatakse sama faili täitmist ilma uut salvestust avamata.
- [ ] JVM üksustestid verifitseerivad alla 30-sekundiliste klippide kustutamise, pikemate säilitamise, `"partial": true` määramise ja võrgukatkestuse järgse faili terviklikkuse.

