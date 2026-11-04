# Kellraadio Domeenisõnastik

Kellraadio on Androidi raadiorakendus ja raadio-äratuskell, mis võimaldab kuulata reaalajas raadiovooge, hallata jaamu ning ärgata kasutaja valitud helitugevusel mängiva lemmikraadiojaama saatel.

## Äratuskell (Alarm)

**Äratus (Alarm)**:
Süsteemse `AlarmManager`i poolt määratud ajal käivitatav raadioesitus, mis alustab taustateenuses (`RadioService`) valitud raadiojaama mängimist kasutaja poolt varem määratud meediahelitugevusel.
_Avoid_: Timer, meeldetuletus, piipar, helin

**Äratuse taastamine (Alarm Rescheduling)**:
Protsess, kus pärast seadme alglaadimist, ajavööndi muutust või eelmise äratuse käivitumist arvutatakse andmebaasi põhjal välja järgmine aktiivne äratus ning registreeritakse see Androidi süsteemses `AlarmManager`is.
_Avoid_: Äratuse taaslaadimine, äratuse sünkroonimine
