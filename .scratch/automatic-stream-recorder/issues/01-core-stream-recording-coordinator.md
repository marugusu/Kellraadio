# 01: Tuumikkoordinaator ja voosisene baidipõhine tükeldus (Core Stream Recording Coordinator & Byte-Aligned Splitting)

**What to build:**
Salvestuse haldamise ja helifailide voosisese tükeldamise eraldamine `RadioService` küljest puhtasse `StreamRecordingCoordinator` moodulisse. Kui kasutaja käivitab salvestamise ja jaama voog jookseb, suunatakse toored striimibaidid kettale avatud faili. Kui jaama server edastab voo sees ICY-metainfo ploki uue loo või saate andmetega, lõpetab koordinaator vana faili ja alustab uut faili täpselt sel baidipositsioonil, välistades esituspuhvrist tuleneva viite ja laulu alguse kärpimise.

**Blocked by:** None (can start immediately)

**Status:** done (feature/automatic-stream-recorder)

- [x] Loodud `StreamRecordingCoordinator` moodul, mis võtab vastu tooreid striimibaite (`ByteArray, offset, length`) ning voosiseseid metainfo teavitusi.
- [x] Kui metainfo muutub (esitaja või pealkirja muutus), suletakse aktiivne helifail ja avatakse koheselt uus fail täpsel baidipiiril.
- [x] Salvestamise alustamisel (`startRecording`) ja peatamisel (`stopRecording`) hallatakse failivooge turvaliselt ja lekkevabalt ilma mälupuhvriteta.
- [x] Faili kirjutamine toimub otse kettale ilma helivoo retranskodeerimiseta (bit-perfect MP3 või AAC).
- [x] JVM üksustestid verifitseerivad, et simuleeritud baidivoo ja metainfo vahetuse korral tükeldatakse failid kaheks täpselt õigel baidil ning failid suletakse korrektselt.

