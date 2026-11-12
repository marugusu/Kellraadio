# 0005. Äratuse salvestus ja pidev helivoo salvestamine

Automaatne raadiovoo salvestamine peab toetama nii äratusega käivituvaid saateid kui ka pidevat taustasalvestust, tagades samal ajal telefoni salvestusmahu kaitse ning sujuva kasutuskogemuse jaamade vahetamisel.

### Peamised disainiotsused:

1. **Äratusepõhine konfiguratsioon ja andmebaasi skeem**:
   - Salvestamise valik määratakse iga äratuse jaoks eraldi äratuse seadistamise dialoogis (`AlarmDialog`) lülitina "Salvesta äratus".
   - Room andmebaasi olemisse `Alarm` lisandub väli `val recordAudio: Boolean = false`.
   - Andmebaasi versioon tõstetakse versioonile 10 koos SQLite migratsiooniga `MIGRATION_9_10`:
     `ALTER TABLE alarms ADD COLUMN recordAudio INTEGER NOT NULL DEFAULT 0`
   - Äratuste vaates (`AlarmsScreen`) kuvatakse salvestava äratuse kellaaja kõrval punane salvestusmärk (indikaator).

2. **Pidev salvestus seadetes (Continuous Recording)**:
   - Üldseadete vaatesse (`SettingsScreen`) lisandub eraldi sektsioon "Salvestamine" koos pealülitiga "Pidev salvestus" (`continuous_recording`).
   - Pidev salvestus on ülimuslik: selle sisselülitamisel salvestatakse automaatselt iga helivoo esitust sõltumata käivitamisviisist (käsitsi, vidinast, meedianupust või äratusest).
   - Kui pidev salvestus on välja lülitatud, salvestatakse ainult neid äratusi, millel on `recordAudio == true`, või käsitsi alustatud salvestusi.

3. **Salvestussessiooni elutsükkel ja jaamade vahetamine**:
   - Salvestamine ei lõpe äratuse režiimist väljumisel (nt äratuse vaigistamisel või edasilükkamisel), vaid kestab seni, kuni kasutaja vajutab esituse peatamise nuppu ("Stop") või peatab salvestuse käsitsi.
   - Jaama vahetamisel püsib salvestussessioon aktiivsena (`isRecording = true`): eelmise jaama pooleliolev helifail ja kõrvafail suletakse korrektselt (alla 30-sekundilised klipid kustutatakse) ning uue jaama voo saabumisel alustatakse viivitamatult uue helifaili ja kõrvafaili kirjutamist uude kausta.
   - Kui "Pidev salvestus" on sisse lülitatud ja kasutaja peatab salvestuse käsitsi raadio mängimise ajal, püsib salvestus peatatuna antud jaama kuulamise lõpuni. Uue jaama valimisel või uue esituse alustamisel rakendub uuesti üldreegel ja salvestamine taastub.

4. **Kettamahu kaitsepiir (Storage Safeguard)**:
   - Salvestussüsteem kontrollib perioodiliselt ja iga uue pala alustamisel vaba kettamahtu failisüsteemis (`StatFs`).
   - Kui seadme vaba kettamaht langeb alla 500 MB, peatatakse salvestamine automaatselt, pooleliolev fail suletakse korrektselt ning kasutajale kuvatakse süsteemne hoiatusteavitus ("Salvestus peatatud: vaba kettamaht alla 500 MB").

5. **Võrgu puudumine**:
   - Kui äratuse ajal võrguühendus puudub ja helivoogu ei saa avada, salvestamist ei alustata ning tühje faile kettale ei genereerita.

