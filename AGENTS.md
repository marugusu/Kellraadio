# AGENTS.md — Kellraadio AI Pair Programming Guide

Tere tulemast! See dokument on keskne, universaalne reeglistik ja arhitektuurijuhend igale AI-agendile (Google Antigravity, Gemini, Claude, Cursor, Copilot), kes töötab **Kellraadio** koodibaasis.

Iga agent **PEAB** järgima allpool toodud reegleid enne mistahes koodi muutmist, kompileerimist või git-toimingute tegemist.

---

## 🛑 0. KÕIKUMATUD TURVAREEGLID (INVIOLABLE LAWS)

Need reeglid on absoluutsed. Nende rikkumine ei ole lubatud üheski olukorras.

### 0.1. Kasutaja poolelioleva töö kaitse (CRITICAL)
* Kasutajal on sageli failides [MainActivity.kt](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/app/src/main/java/app/radiorecalarm/MainActivity.kt) ja [SongInfoSheet.kt](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/app/src/main/java/app/radiorecalarm/ui/SongInfoSheet.kt) käsil commitimata visuaalseid või paigutuse katsetusi.
* **KEEGI EI TOHI KUNAGI** käivitada käske `git restore`, `git checkout --` ega `git reset --hard` kasutaja failide peal.
* **KEEGI EI TOHI KUNAGI** käivitada pimedat `git add .` ega `git commit -a`.
* Enne mistahes lisamist kontrolli alati `git status` ja `git diff`.
* Stageda tohib **AINULT** konkreetselt muudetud faile täpse failiteega (nt `git add app/build.gradle.kts`).
* Kui haru vahetamine nõuab puhast töökataloogi, kasuta `git stash push -m "User tweaks" <failid>` ja taasta need kohe pärast toimingut käsuga `git stash pop`.

### 0.2. Git ja harude poliitika
* Kõik uued arendused ja mitte-triviaalne silumine tehakse **eraldi harus** (`feature/<nimi>` või `fix/<nimi>`).
* **Otse `master` harusse ei tohi pushida testimata koodi.**
* `master` harusse mergemiseks küsi alati kasutajalt selge kinnitus.

### 0.3. Versioonide ühtlustamise seadus (VERSION SYNCHRONIZATION)
* `versionName` viimane number (patch number) **PEAB ALATI** olema täpselt võrdne `versionCode` väärtusega:
  $$\text{versionName} = \text{"1.1.<versionCode>"}$$
  *Näide: kui `versionCode = 20`, siis `versionName = "1.1.20"`.*
* Uue versiooni tegemisel tõstetakse `versionCode` 1 võrra ja `versionName` viimane number viiakse sellega samaks.
* Reegli kontroll on automatiseeritud ka avaldamisskriptis [publish_release.py](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/tools/publish_release.py).

### 0.4. Väljalasked (Release Automation)
* Väljalasked publitseeritakse **AINULT** skriptiga [tools/publish_release.py](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/tools/publish_release.py).
* Mitte kunagi ei tehta väljalaskeid brauseri kaudu käsitsi.
* Väljalaske käsk:
  ```bash
  python tools/publish_release.py v1.1.X "Muudatuste lühikirjeldus"
  ```

### 0.5. Avalik repositoorium ja turvalisus
* Repositoorium `marugusu/Kellraadio` on **avalik**.
* Mitte kunagi ei tohi koodi ega git-ajalukku lisada API võtmeid, paroole, privaatseid sertifikaate ega isiklikke autentimistokeneid.

---

## 📱 1. TEHNILINE ARHITEKTUUR JA STÄKK

* **Platform**: Android 8.0+ (`minSdk = 26`, `compileSdk = 36`, `targetSdk = 36`), Java 11.
* **Põhipakett**: `app.radiorecalarm`
* **Arhitektuurimuster**: Puhas **MVVM** (Model-View-ViewModel) koos Repository mustriga.
* **UI**: **Jetpack Compose** + **Material 3** (vaikimisi tume teema, "Titanium Amber" stiilis toonid).
* **Meedia esitamine**: **AndroidX Media3** (ExoPlayer + MediaSession), mida juhib taustateenus [RadioService.kt](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/app/src/main/java/app/radiorecalarm/RadioService.kt) (`foregroundServiceType = mediaPlayback`).
* **Andmebaas**: **Room SQLite** ([AppDatabase.kt](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/app/src/main/java/app/radiorecalarm/data/AppDatabase.kt)).
* **Asünkroonsus**: Kotlin Coroutines & `StateFlow`. UI kogub olekut `collectAsStateWithLifecycle()` või `collectAsState()` abil.
* **Võrk & API**: Retrofit + Kotlinx Serialization (`Json { ignoreUnknownKeys = true }`).
* **Konfiguratsioon**: Kõik kesksed konstandid, jaamade API-d, välistatavad metaandmed ja UI läved asuvad failis [AppConfig.kt](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/app/src/main/java/app/radiorecalarm/AppConfig.kt).

---

## 🎯 2. ARENDUSSTANDARDID JA KODUKORD

### 2.1. Aku ja taustaprotsesside hügieen (Battery & WakeLock)
* **ÄRA HOIA `PARTIAL_WAKE_LOCK`i tavalise raadio kuulamise ajal!** Heli esitamine toimub ExoPlayeri ja heliriistvara kaudu ning Android hoiab audiolõime ise ärkvel.
* `WakeLock` on rangelt lubatud **AINULT äratuskella käivitumisel** (`TRIGGERED_BY == "ALARM"`), et ekraani lukustuse taga äratus kindlasti helisema hakkaks.
* Vabasta äratuse WakeLock kohe, kui esitamine algab (`isPlaying == true`) või kasutaja peatab äratuse (kaitseks automaatne 30-sekundiline aegumistähtaeg).
* Tegevuse elutsükkel: lauluinfo päringud (`fetchSongInfo`) tuleb peatada, kui äpp läheb taustale (kontrolli `lifecycleState.isAtLeast(Lifecycle.State.RESUMED)` ja kasuta `cancelFetch()`).
* Avakuva vidinad: [HomeWidgetReceiver.kt](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/app/src/main/java/app/radiorecalarm/widget/HomeWidgetReceiver.kt) ei tohi vidina uuendamiseks käivitada `RadioService` teenust.

### 2.2. Kohanduv UI (Responsive Layout)
* Toetatud peavad olema nii püstpaigutus (Portrait) kui rõhtpaigutus (Landscape).
* Laiadel ekraanidel (laius $\ge$ `AppConfig.UI.Layout.WIDTH_THRESHOLD_WIDE_SCREEN_DP`) kasutatakse jagatud veerupaigutust.
* Igal Compose komponendil peab olema `@Preview` funktsioon visuaalseks eelvaateks.
* Kasutajale nähtavad tekstid peavad asuma failides `res/values/strings.xml` ja `res/values-et/strings.xml` (eesti keel on peamine lokaat).

### 2.3. Andmebaasi migratsioonid (Room DB)
* Kui muudad mistahes Room entity't:
  1. Tõsta andmebaasi versiooni failis [AppDatabase.kt](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/app/src/main/java/app/radiorecalarm/data/AppDatabase.kt).
  2. Kirjuta vastav SQLite migratsioon faili [DatabaseMigrations.kt](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/app/src/main/java/app/radiorecalarm/data/DatabaseMigrations.kt).
  3. Registreeri migratsioon `AppDatabase.buildDatabase` meetodis.
  4. Ära kasuta `fallbackToDestructiveMigration()` ilma kasutaja selgesõnalise nõusolekuta.

---

## 🛠 3. SPETSIAALSED OSKUSED (CUSTOM SKILLS)

Täpsemate ja mitmesammuliste toimingute jaoks laadi ja järgi spetsiaalseid oskusi:

| Oskus | Asukoht | Millal kasutada |
| :--- | :--- | :--- |
| **release-publisher** | [.agents/skills/release-publisher/](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/.agents/skills/release-publisher/SKILL.md) | Versioonitõusud ja GitHub Release publitseerimine |
| **room-migration-helper** | [.agents/skills/room-migration-helper/](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/.agents/skills/room-migration-helper/SKILL.md) | Tabelite, veergude või indeksite lisamine/muutmine |
| **compose-component-generator** | [.agents/skills/compose-component-generator/](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/.agents/skills/compose-component-generator/SKILL.md) | Uute Material 3 Compose ekraanide ja dialoogide loomine |
| **retrofit-api-integrator** | [.agents/skills/retrofit-api-integrator/](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/.agents/skills/retrofit-api-integrator/SKILL.md) | Uute veebiteenuste ja REST API-de sidumine |

Täiendavad reeglid:
* [.agents/rules/general.md](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/.agents/rules/general.md)
* [.agents/rules/git-workflow.md](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/.agents/rules/git-workflow.md)
* [.agents/rules/database.md](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/.agents/rules/database.md)

---

## ⚡ 4. KIIRKÄSUD (CHEATSHEET)

```bash
# Debug APK kompileerimine kohalikuks testimiseks
./gradlew assembleDebug

# Üksustestide käivitamine
./gradlew test

# Uue versiooni avaldamine (automaatne üleslaadimine ja release loomine)
python tools/publish_release.py v1.1.X "Versiooni muudatuste kirjeldus"

# Git kontroll enne stagedmist (alati kontrolli!)
git status
git diff
```
