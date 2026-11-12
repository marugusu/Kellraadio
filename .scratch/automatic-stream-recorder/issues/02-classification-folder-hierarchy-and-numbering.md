# 02: Lugude ja saadete klassifitseerimine, kaustapuu ja kahekohaline nummerdus (Classification, Folder Hierarchy & Numbering)

**What to build:**
Tuvastatud salvestiste automaatne liigitamine ja paigutamine organiseeritud kaustapuusse. Muusikapalad koonduvad albumikaustadesse kujul `Muusika/<Esitaja>/<Album>/<NN> - <Esitaja> - <Pealkiri>.<ext>`. Iga pala failinimi saab kahekohalise numbri (`01`, `02`, ...), mis võetakse albumi ametlikust palanumbrist või kausta järjekorranumbrist, ning korduvate palade korral lisatakse sabaindeks (`_01`, `_02`). Jutusaated ja otseeetri plokid (kui metainfo on jaama nimi või "Otseeeter") liiguvad puhtalt kausta `Saated/<Jaam>/<Jaam>_<YYYYMMDD_HHmmss>.<ext>`, tagades laulude ja jutusaadete täieliku eraldatuse.

**Blocked by:** 01: Tuumikkoordinaator ja voosisene baidipõhine tükeldus

**Status:** done (feature/automatic-stream-recorder)

- [x] Metainfo analüsaator tuvastab, kas tegu on muusikalise Palaga või Saatega ("Otseeeter", jaama nimi või tühi väärtus suunab Saatesse).
- [x] Muusikapalade sihtkaust luuakse struktuuris `Muusika/<SanitizedArtist>/<SanitizedAlbum>/`. Kui album pole teada, kasutatakse kausta `Teadmata album`.
- [x] Failinimi algab kahekohalise numbriga (`01`, `02`, ...). Kui ametlikku palanumbrit pole, leitakse järgmine vaba järjekorranumber antud albumi kaustas.
- [x] Kui sama nimega fail juba eksisteerib, lisatakse automaatselt kordumatu järelliide `_01`, `_02`.
- [x] Saated salvestatakse eraldi kausta `Saated/<SanitizedStation>/<SanitizedStation>_<yyyyMMdd_HHmmss>.<ext>`.
- [x] JVM üksustestid verifitseerivad korrektse kaustapuu loomise, kahekohalise nummerduse genereerimise, duplikaatide käsitlemise ning ülemineku saade $\to$ lugu $\to$ saade.

