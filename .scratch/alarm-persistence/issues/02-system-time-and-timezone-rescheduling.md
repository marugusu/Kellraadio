# 02: Süsteemiaja ja ajavööndi muutuste automaatne taastaja (Time and Time Zone Change Alarm Rescheduling)

**What to build:**
Sügisese/kevadise kellakeeramise (suveaeg/talveaeg), ajavööndi vahetumise või süsteemikella käsitsi muutmise korral võtab süsteemne vastuvõtja automaatselt vastu vastavad sündmused, arvutab viivitamatult kehtivate aktiivsete äratuste põhjal välja uue täpse ajatempli, registreerib selle `AlarmManager`is ja uuendab avakuva vidina. Kasutaja ärkab alati õigel kohalikul kellaajal.

**Blocked by:** None (can start immediately)

**Status:** done (branch fix/time-change-rescheduling, needs on-device check)

- [x] AndroidManifest.xml failis on süsteemsele vastuvõtjale lisatud sündmused `android.intent.action.TIME_SET` ja `android.intent.action.TIMEZONE_CHANGED`.
- [x] Vastuvõtja käsitleb `TIME_SET` ja `TIMEZONE_CHANGED` sündmusi, käivitades asünkroonselt järgmise äratuse ümberarvutuse ja registreerimise süsteemses `AlarmManager`is.
- [x] Avakuva vidin uuendatakse pärast äratuste ümberarvutust uue ajaga.
- [x] Logis kajastub süsteemiaja muutuse järgne äratuse edukas taastamine.

