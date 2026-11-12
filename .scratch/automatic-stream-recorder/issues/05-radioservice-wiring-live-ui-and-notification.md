# 05: Raadioteenuse sidumine, UI elav tagasiside ja teavitusriba indikaator (RadioService Wiring, Live UI & Notification)

**What to build:**
Uue salvestusmootori lõplik sidumine rakenduse kasutajaliidese ja taustateenusega. Kasutaja alustab ja peatab salvestamist mängija olemasolevast punasest salvestusnupust. Salvestuskohaks seatakse USB-kättesaadav kaust `Android/data/app.radiorecalarm/files/Music/`. Mängija juhtpaneel kuvab reaalajas parajasti salvestatava pala või saate nime ning sessiooni jooksul edukalt salvestatud lugude loendurit. Süsteemi teavitusribal ja lukustuskuval kuvatakse salvestuse ajal selge indikaator `🔴 Salvestan: ...`, säilitades standardsed meedianupud puhtana.

**Blocked by:** 04: Prügiklippide filter, osalised lood ja võrgu taastumine

**Status:** done (feature/automatic-stream-recorder)

- [x] `RadioService` integreerib `StreamRecordingCoordinator`i, edastades `RetryingHttpDataSource` baidid ja ICY-metainfo koordinaatorile.
- [x] Salvestusasukoht on seadistatud `context.getExternalFilesDir("Music")` baasil, toetades kõiki alamkaustu ja failitüüpe ilma süsteemseid lisalubasid nõudmata.
- [x] Mängija juhtnupp (`PlayerControls.kt`) käivitab ja peatab salvestuse samamoodi nagu varem, kuid kuvab aktiivse salvestatava pala nime ja sessiooni loendurit (nt *"Salvestatud: 3 pala"*).
- [x] Teavitusribal (`RadioNotificationManager.kt`) ja lukustuskuval kuvatakse salvestamise ajal tekstiline staatus `🔴 Salvestan: <Pala nimi>`, jättes meedia juhtnupud (eelmine, esita/paus, järgmine) puutumatuks.
- [x] Kontrollitud käsitsi ja automatiseeritud testidega, et salvestatud failid tekivad korrektselt kaustapuusse koos JSON-failide ja kaanepiltidega ning on USB-ühendusega arvutile nähtavad.

