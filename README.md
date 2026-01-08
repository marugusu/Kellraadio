# Kellraadio 📻⏰

Lihtne, kiire ja töökindel Eesti internetiraadio ja äratuskell Androidile.

See rakendus võimaldab kuulata Eesti ja välismaiseid raadiojaamu, seada äratusi (mis käivitavad raadio) ja kasutada unetaimerit. Rakendus on optimeeritud töötama stabiilselt taustal ja ühilduma auto Bluetooth-süsteemidega.

## 📱 Funktsioonid

*   **Internetiraadio:** Mängib 70+ raadiojaama (Shoutcast/Icecast striimid).
*   **Täpne äratus:** Kasutab `AlarmManager`i ja `USE_EXACT_ALARM` luba, et äratada kindlal ajal ka siis, kui telefon on sügavas unes (Doze mode).
*   **Taustarežiim:** Kasutab *Foreground Service*-it, et raadio mängiks stabiilselt ka siis, kui ekraan on väljas.
*   **Bluetooth Metadata Fix:** Spetsiaalne loogika (metadata duration & track numbers), et laulude nimed ilmuksid korrektselt ka pirtsakatele autoekraanidele (nt Skoda/VW süsteemid).
*   **Unetaimer:** Automaatne väljalülitus (nt 30 min pärast).
*   **Püsiv olek:** Toetab ekraani pööramist (Portrait/Landscape) ilma katkestusteta ja andmekadudeta (`rememberSaveable`).
*   **Dünaamiline nimekiri:** Raadiojaamade nimekiri laetakse internetist (GitHub Gist) ja salvestatakse telefoni andmebaasi.

## 🛠 Tehnoloogiad

Projekt on kirjutatud 100% **Kotlinis** ja kasutab kaasaegseid Androidi komponente:

*   **UI:** Jetpack Compose (Material3 disain).
*   **Audio:** AndroidX Media3 (ExoPlayer).
*   **Andmebaas:** Room Database (jaamade vahemälu).
*   **Võrk:** Retrofit & Kotlinx Serialization (JSON laadimine).
*   **Arhitektuur:** Repository muster, UI seisundi hoidmine mälus.

## 📸 Ekraanipildid

*(Siia võid hiljem lisada pildid oma äpist, nt screenshots kaustast)*

## 🔧 Tehnilised väljakutsed ja lahendused

### 1. Bluetooth Metadata sünkroniseerimine
Paljud autode helisüsteemid ei kuva internetiraadio laulude nimesid, kui striim ei saada infot "loo kestuse" või "järjekorranumbri" kohta.
**Lahendus:** `RadioService` simuleerib autole, et iga lugu kestab 5 minutit ja omab kindlat ID-d. See sunnib auto ekraani infot uuendama.

### 2. Äratuse töökindlus (Android 14+)
Android piirab taustal töötavate äppide tegevust.
**Lahendus:** Rakendus kasutab `BootReceiver`-it, et taastada äratused pärast telefoni restarti, ja `WakeLock`-e, et tagada striimi käivitumine ka unerežiimis.

### 3. Ekraani pööramine (Configuration Changes)
**Lahendus:** Kasutatud on `rememberSaveable` ja Activity elutsükli haldust, et vältida jaamade nimekirja uuesti laadimist ja muusika hakkimist telefoni keeramisel.

## 📥 Paigaldamine

1. Lae alla viimane [Release APK](https://github.com/SinuKasutajaNimi/Kellraadio/releases).
2. Installi fail oma Android seadmesse.
3. Anna vajalikud load (Teavitused ja Täpne äratus).

## Litsents

See on era-projekt õppe eesmärgil.
