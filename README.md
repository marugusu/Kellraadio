# Kellraadio

Kellraadio on Android-platvormile loodud raadiopleier ja äratuskell, mis keskendub stabiilsele heli esitusele, täpsele ajastusele ja optimaalsele kasutajakogemusele erinevates seadmetüüpides. Rakendus on arendatud järgides kaasaegseid Androidi arendusstandardeid ja materjalidisaini põhimõtteid.

## Funktsionaalsus

* **Täpne äratussüsteem**: Rakendus kasutab AlarmManager.setAlarmClock API-t ja USE_EXACT_ALARM õigusi, mis tagab raadiostriimi käivitumise ka Androidi energiasäästurežiimide (Doze mode) ajal.
* **Katkematu taustaheli**: Heli esitust haldab MediaPlayback-tüüpi Foreground Service koos WakeLock süsteemiga, vältides süsteemi poolt protsessi peatamist.
* **Adaptiivne kasutajaliides**: Jetpack Compose'is realiseeritud vaated, mis kohanduvad dünaamiliselt seadme orientatsiooni ja ekraani laiusega (Navigation Bar ja Navigation Rail).
* **Esitusajalugu**: Room-andmebaasil põhinev kuulamisajalugu koos nutika duplikaatide filtreerimise ja YouTube'i otsingu integratsiooniga.
* **Dünaamiline jaamade haldus**: Raadiojaamade nimekiri laetakse JSON-formaadis välisest allikast, tagades andmete uuenemise ilma rakenduse versiooniuuenduseta.

## Tehniline teostus

* **UI Framework**: Jetpack Compose (Material 3)
* **Audio Engine**: AndroidX Media3 (ExoPlayer ja MediaSession)
* **Persistence Layer**: Room Persistence Library (SQL-põhine andmehaldus)
* **Networking**: Retrofit 2 ja Kotlinx Serialization
* **Asynchrony**: Kotlin Coroutines ja StateFlow reaalajas andmevahetuseks
* **Architecture**: Repository muster andmeallikate abstraheerimiseks

## Tehnilised väljakutsed ja erilahendused

### Bluetooth metaandmete sünkroonimine
Internetiraadio striimide puhul esineb sageli viivitusi või ühilduvusprobleeme välis-seadmetega (nt autode multimeediasüsteemid Skoda, VW grupi mudelid). Rakenduses on rakendatud spetsiaalne MediaMetadata sünkroonimise loogika, mis simuleerib meedia staatust ja fikseeritud kestust, et sundida Bluetooth-vastuvõtjat andmeid reaalajas uuendama.

### Skaleeritav kasutajakogemus
Rõhtasendis (Landscape) arvutab rakendus ekraani laiuse (dp ühikutes) ja jaotab mängija ning sisu vahelise ruumi dünaamiliselt. See tagab, et kasutajaliides on ühtviisi loetav ja mugavalt kasutatav nii kompaktsetes telefonides, tahvelarvutites kui ka Android TV seadmetes.

### Andmebaasi migratsioon ja terviklikkus
Rakendus haldab mitut andmetabelit (jaamad ja ajalugu). Ajaloo salvestamise loogika püüab kinni striimist saabuvad tühjad metaandmete paketid ning rakendab "Otseeeter" asendusloogikat, säilitades sealjuures puhta ajaloo ilma korduvate sissekanneteta.

## Paigaldamine ja nõuded

* Operatsioonisüsteem: Android 8.0 (API 26) või uuem.
* Nõutavad õigused: POST_NOTIFICATIONS, USE_EXACT_ALARM, INTERNET, FOREGROUND_SERVICE_MEDIA_PLAYBACK.
* Paigalduspakett (APK) on kättesaadav Releases sektsioonis.

## Litsents

Projekt on arendatud õppe- ja isiklikuks otstarbeks. Kõik edastatavad raadiostriimid ja nendega seotud kaubamärgid kuuluvad vastavatele ringhäälinguorganisatsioonidele.
