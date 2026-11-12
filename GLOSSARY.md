# Kellraadio

Raadiokuulamise ja raadioäratuse rakenduse domeenisõnastik. Määratleb ühtse keele jaamade, allikate, rühmitamise ja äratuste kirjeldamiseks.

## Language

**Jaam (Station)**:
Üks konkreetne raadiojaam koos oma nime ja striimiaadressiga, mida saab kuulata või äratuseks määrata.
_Avoid_: Kanal, raadiokanal, voog

**Allikas (Source)**:
Jaama päritolu, mis määrab, kas tegu on rakenduse kureeritud nimekirjast pärit jaamaga või kasutaja enda lisatud jaamaga.
_Avoid_: Kategooria "My" kui päritolu

**Minu jaamad (User Stations)**:
Kasutaja enda käsitsi või otsingu kaudu lisatud jaamade kogu, mida kasutaja saab ise muuta ja kustutada.
_Avoid_: Oma kanalid

**Kureeritud jaamad (Curated Stations)**:
Keskse jaamade nimekirja kaudu pakutavad jaamad, mida uuendatakse serverist.
_Avoid_: Serveri kanalid, kaugjaamad

**Riik (Country)**:
Jaama päritoluriik ISO kahetähelise koodina, mille alusel jaamad peagruppidesse koonduvad.
_Avoid_: Riigitunnus, lokaat

**Alamgrupp (Subgroup)**:
Jaama täpsustav rühm riigi sees — Eesti jaamadel meediaettevõte või pakkuja (nt ERR, Duo, Sky, TV3), välisjaamadel teema või žanr.
_Avoid_: Kategooria, alamfilter kui omadus

**Lemmik (Favorite)**:
Kasutaja poolt esiletõstetud jaam, mis kuulub püsiva järjekorraga kiirvaliku vaatesse.
_Avoid_: Lemmikkanal

**Salvestus (Recording)**:
Raadio helivoo reaalajas talletamine failisüsteemi bit-perfect helifailina.
_Avoid_: Audio rip, striimitõmmis

**Pala (Track)**:
Tuvastatud esitaja ja pealkirjaga muusikateos, millele päritakse laiendatud metaandmed ja kaanepilt.
_Avoid_: Lugu, laul kui tehniline salvestis

**Saade (Broadcast / Show)**:
Mitte-muusikaline raadioprogramm, uudisteplokk või jaama üldine otseeeter, millel puudub konkreetne muusikapala metainfo.
_Avoid_: Otseeeter kui failitüüp, jutt

**Kõrvafail (Sidecar File)**:
Helifailiga kaasas käiv struktureeritud `.json` metaandmefail koos kaanepildiga (`cover.jpg`) hilisemaks sildistamiseks.
_Avoid_: Info fail, txt fail, nfo

**Automaatne tükeldus (Auto-splitting)**:
Salvestusvoo poolitamine uude faili reaalajas jaama metainfo muutumise hetkel.
_Avoid_: Segmentimine, lõikamine
