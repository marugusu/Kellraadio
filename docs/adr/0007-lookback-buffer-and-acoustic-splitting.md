# 0007. Viivepuhver ja akustiline lõikepunkt Icecasti metainfo viivituse kompenseerimiseks

Status: superseded by [ADR-0008](0008-bulletproof-track-framing.md) (täiendab dokumenti [ADR-0004](0004-stream-recording-architecture.md))

Raadiojaamade väljastusautomaatika ja Icecasti kodeerijate puhvrite tõttu saabub `StreamTitle` metainfo sageli 2–4 sekundit pärast **Pala** tegelikku algust ja lõppu. Kui **Automaatne tükeldus** lõikab faili täpselt `StreamTitle` saabumise hetkel, jääb eelmise **Pala** lõppu 2–3 sekundit jaamakõlli ning järgmise **Pala** algusest läheb 2–3 sekundit kaduma (eriti kui vahepealne kõll või reklaam kestab alla 30 sekundi ja kustutatakse prügina). Otsustasime lisada salvestustorule 8-sekundilise **Viivepuhvri** ja metainfo piiril käivitatava **Akustilise lõikepunkti** tuvastuse.

### Peamised disainiotsused:

1. **8-sekundiline tihendatud kaadrite viivepuhver (`LookbackFrameBuffer`)**:
   - **Pala** tükeldusega salvestamisel ei kirjutata sisenevaid MP3/AAC ADTS helikaadreid kohe kettale, vaid viimast kuni 8 sekundit (~160–320 KB mälus) hoitakse eelmise faili sulgemise ootel mälupuhvris.
   - Puhvri ületäitumisel kirjutatakse vanimad kaadrid jooksvalt aktiivsesse faili.
   - Salvestuse käsitsi peatamisel (`stopRecording`) tühjendatakse kogu puhver kohe kettale, nii et lõpust ei lähe midagi kaduma.
   - **Äratuse salvestus** (tükeldamata **Saade**) kirjutab endiselt otse kettale ilma viivepuhvrita.

2. **On-demand akustiline piirituvastus Androidi `MediaCodec`-iga**:
   - Kuna raadiojaamad kasutavad valdavalt püsiva bitikiirusega (`CBR`/`ABR`) vooge, ei näita pelgalt kaadrite baitide suurus vaikust ega lugude vahetust.
   - Ainult `StreamTitle` muutumise hetkel (keskmiselt kord ~3 minuti tagant) dekodeerib `MediaCodecAudioBoundaryDetector` puhvris oleva kuni ~6,5-sekundilise akna kiirelt PCM-energiaks, leiab **Akustilise lõikepunkti** (pausi, vaikuse- või üleminekukoha) ning poolitab siiski algsed tihendatud MP3/AAC kaadrid ilma ümberkodeerimiseta (**Salvestus** jääb 100% bit-perfect kujule).
   - Kui selget energialangust ei leita (nt pidev vali üleminek või `MediaCodec` tõrge), kasutatakse konservatiivset tagasinihet (`2,0 s`, arvestades uue segmendi kaitseaega).

3. **Täielikult automaatne taustakäitumine ja ulatus**:
   - Kompensatsioon töötab automaatselt `AppConfig.Recording` konstandite alusel ilma kasutajaliidesesse käsitsi nihkeliugureid lisamata.
   - Mitte-ICY (`icy-metaint <= 0`) striimide `Player.Listener.onMetadata()` varutee jääb sellest muudatusest väljapoole.

### Kaalutud alternatiivid (Considered Options):

* **Fikseeritud ajanihe (nt alati `-2,5 s`)**: Lihtne teostus, kuid erinevatel jaamadel on metainfo viive erinev (0 s kuni 5 s); fikseeritud nihe lõikaks nullviitega jaamadel laulude lõppu maha ja jätaks 4-sekundilise viitega jaamadel ikka kõlli sisse.
* **Kattuv ülekate (padding)**: Mõlemasse otsa lisatud varu hoiab ära alguse kao, kuid jätab jaamakõllid alati failidesse alles ja sunnib kasutajat neid arvutis käsitsi lõikama.

### Tagajärjed (Consequences):

* Kahe järjestikuse metainfomuutuse vahel (nt **Pala** → lühike `< 30 s` kõll → järgmine **Pala**) säilivad järgmise **Pala** esimesed sekundid puhvris ega kustu koos prügiklipiga.
* Protsessi ootamatu süsteemse tapmise (`kill -9`) korral võib kettale kirjutamata jääda kuni 8 sekundit jooksvast lõpust; tavapärasel peatamisel, jaamavahetusel ja salvestuse lõpetamisel kirjutatakse puhver alati lõpuni välja.
