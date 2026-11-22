# 0009. Ülekattega palade raamistamine ilma akustilise lõikamiseta

Status: accepted (asendab dokumendi [ADR-0008](0008-bulletproof-track-framing.md))

Varasem `MediaCodec`-põhine akustiline piirituvastus ja vahekõllide automaatne väljalõikamine üritas leida kahe pala vahel ühe täpse lõikehetke, kuid eksis vaiksete sissejuhatuste, *crossfade*-üleminekute ja kõikuvate metainfo viivituste korral, jättes palade algused või lõpud kärbituks. Otsustasime eemaldada `MediaCodec`-tuvastuse täielikult ning asendada selle deterministliku kahepoolse ülekattega raamistamisega (**Eelvaru** + **Järelvaru**), seades **Seaded → Salvestamine** alla nii varuaja (vaikimisi `10 s`) kui ka lühiklipi miinimumkestuse (vaikimisi `15 s`).

### Peamised disainiotsused

1. **Sümmeetriline *pre-roll + post-roll* ülekate**:
   - **Pala** avamisel kirjutatakse faili algusesse viimased `N` sekundit (**Eelvaru**, vaikimisi `10 s`) aktiivse salvestamise ajal hoitavast mäluringpuhvrist ning uued kaadrid kirjutatakse kettale kohe ilma viivituseta.
   - **Pala** sulgumisel metainfo muutumise hetkel ei panda faili kohe kinni, vaid jätkatakse saabuvate kaadrite kirjutamist veel `N` sekundit (**Järelvaru**, vaikimisi `10 s`), toetades mitut paralleelselt lõpetavat kirjutajat, et kiired vahekõllid ei katkestaks eelmise pala lõpuvaru.
   - Salvestuse käsitsi peatamisel (`stopRecording`), jaamavahetusel või kettamahu kaitsel suletakse käesolev pala ja kõik aktiivsed järelvarud kohe ilma ooteajata.
   - **Saated** ja **Äratuse salvestus** jäävad tükeldamata ja ilma lisavarudeta; ülekate rakendub ainult **Palade** otstel (sealhulgas `Pala ↔ Saade` üleminekutel).
2. **Põhikestus vs faili tegelik kestus**:
   - Miinimumkestuse (`15 s`) prügiklipi filter ja `10 minuti` (`600 s`) saateks liigitamine hindavad **põhikestust** (aeg loo metainfo algusest järgmise metainfo saabumiseni ilma eel- ja järelvaruta), et `10 s` varud ei muudaks 10-sekundilist **Vahekõlli** 30-sekundiliseks säilitatavaks palaks.
   - **Kõrvafaili** (`.json`) `durationSeconds` salvestab tegeliku failipikkuse koos varudega ning skeem püsib muutumatuna.
3. **Seadistatavus, lihtsus ja taastekindlus**:
   - **Seaded → Salvestamine** sisaldab kahte valikut: minimaalne pala pikkus (`0 s / 15 s / 30 s / 60 s`, vaikimisi `15 s`) ja algus- ja lõpuvaru (`0 s / 5 s / 10 s / 15 s / 20 s`, vaikimisi `10 s`).
   - Mitte-ICY (`icy-metaint <= 0`) striimid jäävad lihtsale varuteele ja kuulamisel ilma salvestamiseta passiivset ringpuhvrit ei hoita.
   - Sama jaama võrgukatkestusest taastumisel jätkatakse kirjutamist samasse faili, lähtestades ainult kaadriparsimise joondamise (`AudioFrameAccumulator`), ning käsitsi `stopRecording`-uga katkestatud viimane pala märgitakse `"partial": true`.

### Kaalutud alternatiivid (Considered Options)

* **Täpsem akustiline lõikus või serveripoolne/kettapõhine järeltöötlus**: Jääks endiselt haavatavaks *crossfade*'i ja raadiojaamade erinevate üleminekustiilide suhtes ning lisaks keerukust ja akutulu, samas kui kasutaja aktsepteerib vahekõllide jäämist faili otstesse ning saab need vajadusel arvutis täpseks lõigata.
* **Passiivne ringpuhver kogu kuulamise ajal**: Aitaks ka keset laulu "Salvesta" vajutamisel algust püüda, kuid hoiaks pidevalt taustal lisapuhvrit ja seoks mängija puhverdusnihkega, minnes vastuollu lihtsama ja töökindlama arhitektuuri eesmärgiga.

### Tagajärjed (Consequences)

* `AudioBoundarySplit.kt` (`MediaCodecAudioBoundaryDetector`, `AudioBoundaryScorer`, `LookbackFrameBuffer`) eemaldatakse koodibaasist täielikult.
* Iga **Pala** jääb 100% *bit-perfect* terviklike MP3/ADTS helikaadritega failiks ning kahe kõrvuti oleva pala failid võivad otstest kattuda kuni seadistatud varuaja ulatuses.
