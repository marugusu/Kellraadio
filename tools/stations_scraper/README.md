# Kellraadio Jaamade Automaatne Kraapija ja Generaator

See moodul vastutab Kellraadio rakenduse jaamade nimekirja (`stations.json`) automaatse kogumise, valideerimise ja avaldamise eest.

## Kuidas süsteem töötab?

1. **Automaatne kraapimine:**
   - **ERR:** `https://icecast.err.ee/status-json.xsl` (valitakse kõrgeima kvaliteediga 320k striimid).
   - **Sky Media:** `https://stream.skymedia.ee/live/status-json.xsl` (Sky Plus, Retro FM, Rock FM, Relax FM 5 teemakanalit, Kiss FM jne).
   - **Tre Raadio:** `https://cdn.treraadio.ee/status-json.xsl` (Ring FM, Tre Raadio, Ruut FM).
   - **TV3:** Kraabitakse otse `https://raadiod.tv3.ee/` veebilehtede `<audio>` mängijate aktiivsed striimi-aadressid (Star FM, Power Hit Radio, Star FM Eesti, Star FM+ jne).
   - **Duo Media:** Kanoonilised EUDDN vood (Elmar, Kuku, MyHits, Duo kanalid).
   - **Välisjaamad:** Loetakse failist `seed_foreign_stations.json` (BBC, Yle, SuomiPop, Antenne Bayern jne).

2. **Stabiilsed ID-d ja Lemmikute säilimine:**
   - `station_registry.json` seob iga jaama unikaalse tunnuse kindla `id` numbriga.
   - Isegi kui jaama striimi URL peaks tulevikus muutuma, jääb jaama `id` püsivaks ja kasutajate lemmikud ei liigu paigast.

3. **Väljundid:**
   - `dist/stations.json` – valmis JSON serverisse/Gisti laadimiseks.
   - `app/src/main/assets/stations.json` – rakenduse sisene võrguühenduseta varukoopia.

---

## Kohalik testimine

Käivita käsurealt:
```bash
python tools/stations_scraper/generate_stations.py
```

Uuenduse otse Gisti saatmiseks:
```bash
python tools/stations_scraper/generate_stations.py --publish --gist-token <SINU_GITHUB_TOKEN>
```

---

## GitHub Actions ja saladused (Secrets)

Automaatse öise töövõo jaoks on GitHubi repositooriumis vajalik lisada saladus:
- **`Settings` -> `Secrets and variables` -> `Actions` -> `New repository secret`**:
  - Nimi: `GIST_TOKEN`
  - Väärtus: Sinu GitHubi Personal Access Token (classic või fine-grained), millel on `gist` õigus.
