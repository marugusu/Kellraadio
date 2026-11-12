# 03: Pidev helivoo salvestus üldseadetes (Continuous Stream Recording)

**What to build:**
Võimaldada kasutajal rakenduse seadetest sisse lülitada pidev salvestamine. Kui "Pidev salvestus" on aktiivne, käivitub salvestamine automaatselt iga kord, kui mistahes raadiojaam mängima hakkab (sõltumata sellest, kas käivitati käsitsi, vidinast, äratusest või välisest meedianupust). Käsitsi peatamine peatab salvestuse vaid antud jaama kuulamise ajaks.

**Blocked by:** 02: Äratuse salvestamise käivitamine ja elutsükli sidumine (Alarm Recording Trigger & Lifecycle)

**Status:** ready-for-agent

- [ ] Rakenduse üldseadetes (`SettingsScreen`) on uus sektsioon "Salvestamine" koos lülitiga "Pidev salvestus" ja selgitava tekstiga.
- [ ] Valik salvestatakse püsivalt seadetesse (`SharedPreferences`) ja seda vahendab `MainViewModel`.
- [ ] `RadioService` kontrollib jaama esituse alguses pideva salvestuse eelistust ja käivitab voo avanemisel automaatselt salvestamise.
- [ ] Pidev salvestus on ülimuslik: kui see on sisse lülitatud, salvestatakse ka need äratused, millel eraldi äratuse salvestuse lüliti oli välja lülitatud.
- [ ] Kui kasutaja vajutab mängimise ajal käsitsi "Peata salvestus", jääb salvestus antud jaama ajaks peatatuks ilma heliesitust katkestamata.
- [ ] Seadete salvestamist ja teenuse otsuseloogikat katavad üksustestid.

