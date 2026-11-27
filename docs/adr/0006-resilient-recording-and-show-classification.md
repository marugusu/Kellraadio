# 0006. Häiringukindel salvestamine, saadete klassifitseerimine ja ülekirjutamiskeeld

Status: accepted (täiendab ja muudab osaliselt dokumente [ADR-0004](0004-stream-recording-architecture.md) ja [ADR-0005](0005-alarm-and-continuous-recording.md))

Aktiivne raadiosalvestus ei tohi katkeda väljundseadme kadumisel (nt autost väljumisel) ega väliste audiofookuse häiringute tõttu, ning pikad raadiosaated ei tohi sattuda muusikapaladena `Music` kausta ega saada lühemate juppide poolt üle kirjutatud. Otsustasime hoida salvestuse ajal töös ühte vaigistatavat ExoPlayeri helivoogu, käsitleda äratuse salvestusi ja üle 10-minutilisi klippe saadetena ning keelata pikemate olemasolevate salvestiste kustutamise duplikaadikontrollis.

### Peamised disainiotsused:

1. **Häiringukindel taustasalvestus ühe voo peal**:
   - Kui salvestus on aktiivne, haldab `RadioService` audiofookust ja `AUDIO_BECOMING_NOISY` sündmusi ise.
   - Bluetoothi/kõrvaklappide lahtiühendamisel või teise äpi audiofookuse võtmisel vaigistatakse ainult mängija väljund (`player.volume = 0f`), ilma ExoPlayerit peatamata ja HTTP-soklit sulgemata. Heli jääb vaigistatuks seni, kuni kasutaja vajutab ise taasesituse/heli taastamise nuppu (teavituses `🔴 Salvestab taustal (vaigistatud)`).
   - Telefonikõne ajal (`TRANSIENT_AUDIO_FOCUS_LOSS`) vaigistatakse väljund kõne ajaks nii, et striimivoo salvestamine kettale ei katke, ning kõne lõppemisel taastub heli automaatselt.
   - Kasutaja enda vajutatud **Paus** või **Stop** (ning teavituse **Peata salvestus**) lõpetab salvestuse ja sulgeb faili korrektselt.
   - Kui taasesitus peatatakse välise katkestuse tõttu ilma aktiivse salvestuseta (`AUDIO_BECOMING_NOISY`, püsiv `AUDIOFOCUS_LOSS` või vaigistatud salvestuse peatamisel tekkiv paus), peab `RadioService` vabastama audiofookuse (`abandonAudioFocus()`). Pausil ja mittesalvestavas olekus saabunud mööduvaid fookusekaotusi (`AUDIOFOCUS_LOSS_TRANSIENT`, `CAN_DUCK`) eiratakse, et hilisem kõne lõpp ei käivitaks peatatud raadiot uuesti.

2. **Saate (`Broadcasts`) vs Pala (`Music`) piir ja äratuse salvestus**:
   - **Äratuse salvestus** toimub vaikimisi ühe tükeldamata **Saate** failina (`Recordings/Broadcasts/<Jaam>/`, varasemalt `Music/Saated/<Jaam>/`) — vahepealne ICY-metainfo muutus ei poolita äratusega alanud saadet (jaamavahetusel taastub tavapärane tükeldusreegel). See muudab [ADR-0005](0005-alarm-and-continuous-recording.md) varasemat eeldust, et äratus lõikab vaikimisi üksikuid muusikapalu.
   - **Saateks** liigitatakse kohe ka üheosalised (ilma `" - "`-ta) metainfod ja `AppConfig.Metadata.IGNORE_TERMS` terminid (nt `Uudised`, `Ilmateade`, `Sport`, `Saatepaus`).
   - **10-minuti reegel**: kui algselt **Palaks** arvatud salvestis kestab lõpetamisel üle 10 minuti (`> 600 s`), tõstetakse see `finalizeTrack` etapis automaatselt `Recordings/Broadcasts/<Jaam>/<Jaam>_<YYYYMMDD_HHmmss>.<ext>` alla koos `Saade` kõrvafailiga ja ilma muusika-API päringuta. Alla 30-sekundilised prügiklipid kustutatakse endiselt.

3. **Duplikaatide ja osaliste salvestiste ülekirjutamiskeeld**:
   - `Music` kausta duplikaadikontroll ei tohi kunagi kustutada olemasolevat salvestist, kui uus fail on sellest lühem.
   - Olemasoleva `"partial": true` faili võib asendada ainult juhul, kui uus mitte-osaline fail on päriselt pikem/samaväärne täispikk versioon; muudel juhtudel säilitatakse mõlemad failid järjekorranumbriga (`_01`, `_02`).

### Kaalutud alternatiivid (Considered Options):

* **Teise HTTP-ühenduse avamine salvestusele**: Mängijast eraldi taustalaadija väldiks ExoPlayeri audiofookusega manipuleerimist, kuid tekitaks 2× mobiilse andmeside kulu, rikuks raadiojaamade ühe-IP-ühenduse piiranguid ning viiks kuuldava ja salvestatava heli sünkroonist välja. Valisime ühe voo vaigistamise (`volume = 0f`).
* **Saadete käsitsi eristamine vs 10-minuti piir**: Ainult metainfo põhjal ei saa välistada saatejuhtide või `"Saate nimi - Saatejuht"` vormingus saateid; 10-minuti lävend püüab sellised pikad saated usaldusväärselt kinni ilma käsitsi sekkumiseta.

### Tagajärjed (Consequences):

* Akusäästu ja taustateenuse reegel jääb kehtima: `PARTIAL_WAKE_LOCK` ei hoita tavalise salvestuse ajal; aktiivset taustasalvestust hoiab töös `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` esiplaaniteenus.
* Kui salvestamise ajal tekib vajadus äratusega siiski üksikuid laule tükeldada, tuleb see lisada tulevikus eraldi valikuna äratuse seadistusse.
