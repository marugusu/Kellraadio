# 0004. Raadiovoo salvestamise arhitektuur ja kõrvafailid

Automaatne raadiosalvestus peab töötama stabiilselt taustal ilma telefoni akut koormamata ja helikvaliteeti rikkumata. Otsustasime salvestada jaamade helivood bit-perfect kujul (MP3 või AAC) ning luua iga pala või saate kõrvale struktureeritud JSON-kõrvafaili (`<fail>.json`) ja kaanepildi (`cover.jpg`), jättes helifailide ID3v2 sildistamise arvutis ffmpeg ja automatiseeritud agentide hooleks. 

### Peamised disainiotsused:
1. **Salvestusvorming ja kõrvafailid**: Helivoog salvestatakse puhtalt ilma telefonis ümber kodeerimata (`.mp3` või `.aac`). Iga pala kõrvale salvestatakse masinloetav `.json` fail (artist, pealkiri, album, aasta, žanr, laulusõnad, jaam, algusaeg, kestus, bitikiirus, kodeering) ja alla laaditud `cover.jpg`.
2. **Kaustapuu ja nummerdus**: Palad paigutatakse struktuuri `Music/Muusika/<Esitaja>/<Album>/<NN> - <Esitaja> - <Pealkiri>.<ext>`. Numbrina kasutatakse kahekohalist ametlikku palanumbrit (`01`, `02`, ...) või selle puudumisel salvestamise järjekorranumbrit albumi kaustas. Jutusaated koonduvad kausta `Music/Saated/<Jaam>/<Jaam>_<YYYYMMDD_HHmmss>.<ext>`.
3. **Voosisene lõikamine (Zero-Latency Byte Alignment)**: Helifailid poolitatakse täpselt sel baidipositsioonil, kus jaam edastab ICY-metainfo ploki (`RetryingHttpDataSource` tasemel), välistades puhvrist tuleneva viite.
4. **Prügiklippide filter ja osalised palad**: Alla 30 sekundi kestvad vaheklipid, jaamakõllid ja reklaamid kustutatakse automaatselt. Poolelt laulult alustatud salvestised talletatakse, märkides kõrvafailis `"partial": true`. Lühiajaliste võrgukatkestuste korral jätkatakse sama pala kirjutamist olemasolevasse faili.
5. **Salvestusketas ja teavitus**: Failid talletatakse rakenduse välisele kettale (`getExternalFilesDir("Music")`), mis toetab piiranguteta kõiki failitüüpe ja alamkaustu ning on USB (MTP) kaudu arvutile vabalt ligipääsetav ilma süsteemseid lisalubasid küsimata. Lukustuskuval ja teavitusribal kuvatakse salvestamise ajal tekstiline indikaator (`🔴 Salvestan: ...`), säilitades standardsed meedianupud puhtana.


