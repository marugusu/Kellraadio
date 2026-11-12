# 03: Pidev helivoo salvestus üldseadetes (Continuous Stream Recording)

**What to build:**
Võimaldada kasutajal rakenduse seadetest sisse lülitada pidev salvestamine. Kui "Pidev salvestus" on aktiivne, käivitub salvestamine automaatselt iga kord, kui mistahes raadiojaam mängima hakkab (sõltumata sellest, kas käivitati käsitsi, vidinast, äratusest või välisest meedianupust). Käsitsi peatamine peatab salvestuse vaid antud jaama kuulamise ajaks.

**Blocked by:** 02: Äratuse salvestamise käivitamine ja elutsükli sidumine (Alarm Recording Trigger & Lifecycle)

**Status:** completed

- [x] Rakenduse üldseadetes (`SettingsScreen`) on uus sektsioon "Salvestamine" koos lülitiga "Pidev salvestus" ja selgitava tekstiga.
- [x] Valik salvestatakse püsivalt seadetesse (`SharedPreferences`) ja seda vahendab `MainViewModel`.
- [x] `RadioService` kontrollib jaama esituse alguses pideva salvestuse eelistust ja käivitab voo avanemisel automaatselt salvestamise.
- [x] Pidev salvestus on ülimuslik: kui see on sisse lülitatud, salvestatakse ka need äratused, millel eraldi äratuse salvestuse lüliti oli välja lülitatud.
- [x] Kui kasutaja vajutab mängimise ajal käsitsi "Peata salvestus", jääb salvestus antud jaama ajaks peatatuks ilma heliesitust katkestamata.
- [x] Seadete salvestamist ja teenuse otsuseloogikat katavad üksustestid.
