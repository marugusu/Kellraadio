# 0008. Lihtne ja pommikindel palade raamistamine laiendatud mälupuhvriga

Status: superseded by [ADR-0009](0009-overlap-track-framing.md) (asendas dokumendi [ADR-0007](0007-lookback-buffer-and-acoustic-splitting.md))

Senine 8-sekundilise **Viivepuhvri** ja ühekordse energialanguse-põhise lõikuse teostus jättis raadiojaamade lõppu sageli ~3 sekundit kõlli või järgmist lugu ja kärpis uue loo algust, sest `MediaCodec` tõrkel tagastati 0 ms tagasinihe, 500 ms dekodeerimise ajapiir jättis akna lõpu analüüsimata ning *fade-out → järsk algus* (*step-up*) üleminekuid ja metainfota vahekõlle ei tuvastatud. Otsustasime laiendada olemasolevat mälupuhvrit **~20 sekundini (~400 KB RAM)** ning lisada *step-up* tuvastuse, kahepiirilise vahekõlli väljalõikamise, garanteeritud varunihke ja pehme ametliku kestuse viigilahutaja, hoides arhitektuuri lihtsana ilma kettapõhiste ajutiste failideta.

### Kaalutud alternatiivid (Considered Options)

* **Kettapõhine *staging* ja 60 s ajutised varufailid (ka mitte-ICY/HLS jaoks)**: Võimaldaks katta ka 30–60 s mängijapuhvri viidet, kuid tooks sisse mitmekihilise ajutiste failide halduse, krahhist taastumise ja viibiva lõpetamise keerukuse, suurendades uute vigade riski.
* **Helifaili otste ümberkodeerimine *fade-in/fade-out* lisamiseks**: Pehmendaks raadiojaamade *crossfade* üleminekuid, kuid rikuks *bit-perfect* salvestuspõhimõtet ([ADR-0004](0004-stream-recording-architecture.md)), kulutaks akut ja sõltuks seadmete kodeerijate eripäradest.
* **Praegune 8 s puhver väikeste konstantide muutmisega**: Ei võimalda mahutada suurema viitega jaamu ega lõigata välja kahe loo vahele jäävat metainfota kõlli.

### Tagajärjed (Consequences)

* Iga **Pala** jääb endiselt 100% kadudeta *bit-perfect* MP3/AAC failiks ilma seadistusliuguriteta kasutajaliideses.
* Mälukasutus salvestamise ajal suureneb marginaalselt (~400 KB RAM), samas kaovad kettapõhiste vahefailidega seotud riskid.
* Mitte-ICY striimid (`icy-metaint <= 0`) jäävad teadlikult varutee (`Player.Listener.onMetadata`) piiridesse; **Saated** ja **Äratuse salvestus** jäävad endiselt kärpimata.
