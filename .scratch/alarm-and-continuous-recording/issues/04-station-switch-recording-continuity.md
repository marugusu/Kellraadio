# 04: Salvestussessiooni püsivus jaamade vahetamisel (Station-Switch Continuity)

**What to build:**
Tagada, et jaama vahetamisel aktiivne salvestussessioon ei katkeks. Kui kasutaja kuulab raadiot ja salvestamine on käimas (äratuse, käsitsi käivitamise või pideva salvestuse kaudu) ning valib teise jaama, suletakse eelmise jaama pooleliolev fail puhtalt ja uue jaama voo saabumisel alustatakse automaatselt uue helifaili ja kõrvafaili kirjutamist uude kausta ilma kasutajapoolse lisavajutuseta.

**Blocked by:** 03: Pidev helivoo salvestus üldseadetes (Continuous Stream Recording)

**Status:** completed

- [x] Jaama vahetamisel säilitab `RadioService` üldise salvestusoleku (`isRecording = true`).
- [x] Eelmise jaama pooleliolev helifail ja JSON-kõrvafail viimistletakse ja suletakse puhtalt; alla 30-sekundilised klipid kustutatakse kettalt.
- [x] Uue jaama võrguvoo ja metaandmete saabumisel alustatakse viivitamatult uue faili kirjutamist vastavalt jaama/esitaja kaustapuu reeglitele.
- [x] Kui "Pidev salvestus" oli sees ja kasutaja oli eelmise jaama ajal salvestuse käsitsi peatanud, taastub uue jaama valimisel automaatne salvestamine.
- [x] Jaamavahetuse sujuvust ja failide korrektset poolitamist katavad `StreamRecordingCoordinator` üksustestid.
