# 03: Seadmel verifitseerimine sügavas unerežiimis

**What to build:** Verifitseerida äratuse toimimine reaalses seadmekeskkonnas (ühendatud POCO X6 Pro seadmel), viies seadme ekraani väljalülituse ja sügava unerežiimi (Doze režiim) seisundisse. Kinnitada, et äratus äratab seadme ja alustab ettenähtud jaama helivoo mängimist määratud helitugevusel ilma ekraani eelneva puudutamiseta.

**Blocked by:** 01: WakeLockiga äratuse vastuvõtja ja teenuse käivitus unerežiimis, 02: Ühekordse äratuse elutsükli turvaline deaktiveerimine

**Status:** done

- [x] Äpp on paigaldatud ühendatud füüsilisele seadmele.
- [x] Testäratus on seadistatud lähiminutitele ja telefon on viidud sügavasse unerežiimi (ekraan suletud, süsteemne Doze sundkäivitatud).
- [x] Saabunud äratuse minutil ärkab heliprotsess ellu ja valitud raadiojaama esitus algab automaatselt.
- [x] Süsteemilogid kinnitavad korrektset WakeLocki haarata-vabasta tsüklit ning sujuvat teenuse ülevõtmist.

### Verifitseerimise tulemus ja logitõendid (POCO X6 Pro 5G / HyperOS Android 14)

1. **Paigaldus**: Silumisversiooni APK (`app-debug.apk`) paigaldatud edukalt seadmele (`garnetp_global` / `23122PCD1G`).
2. **Ajastus**: Testäratus seatud kellaks 11:55:00 jaama *Rock FM* jaoks.
3. **Sügav unerežiim (Doze `IDLE`)**:
   - Laadija virtuaalselt eemaldatud (`dumpsys battery unplug`).
   - Ekraan välja lülitatud (`input keyevent 26`).
   - Seade viidud sügavasse unerežiimi: `mState=IDLE mLightState=OVERRIDE` kinnitatud `cmd deviceidle get deep` abil.
4. **Äratus ja teenuse käivitus kell 11:55:00**:
   - `11:55:00.019` – `AlarmReceiver` haaras `radiow::AlarmReceiverWakeLock`.
   - `11:55:00.024` – Süsteem lubas taustast FGS: `Background started FGS: Allowed [callingPackage: app.radiorecalarm]`.
   - `11:55:00.146` – `AlarmReceiver` vabastas `radiow::AlarmReceiverWakeLock` pärast andmebaasipäringut ja teenuse väljakutset (kestus 127 ms).
   - `11:55:00.284` – `RadioService` võttis juhtimise üle ja haaras `radiow::RadioWakeLock`.
   - `11:55:01.303` – Riistvaraline helirada avati (`AudioMix`).
   - `11:55:01.315` – `RadioService` vabastas `radiow::RadioWakeLock` kohe, kui esitamine algas (`onIsPlayingChanged(isPlaying = true)`).
   - Audioesitus jätkus sujuvalt režiimis `USAGE_MEDIA / CONTENT_TYPE_MUSIC`, heliprotsessi olek `state=PLAYING(3)`.
   - Ühekordne äratus deaktiveeriti ja süsteem ajastas järgmise kronoloogilise äratuse.

