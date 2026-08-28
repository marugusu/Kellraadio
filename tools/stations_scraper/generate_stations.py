#!/usr/bin/env python3
"""
generate_stations.py

Täisautomaatne Eesti raadiojaamade ja välisjaamade voogude kogumise ja JSON-i
genereerimise skript Kellraadio rakenduse jaoks.

Kogub reaalajas andmeid:
- ERR (Eesti Rahvusringhääling) Icecast API
- Sky Media (Sky Plus, Retro FM, Rock FM, Relax FM jne) Icecast API
- Tre Raadio / Ring FM / Ruut FM Icecast API
- TV3 Grupp (Star FM, Power Hit Radio jne) raadiod.tv3.ee veebilehtedelt
- Duo Media (Kuku, Elmar, Duo jne) EUDDN / Pleier voogudest
- Muud kohalikud ja välisjaamad (seed_foreign_stations.json)
"""

import os
import sys
import json
import re
import urllib.request
import urllib.error
import argparse
from typing import Dict, List, Any, Optional

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REGISTRY_PATH = os.path.join(SCRIPT_DIR, "station_registry.json")
FOREIGN_SEED_PATH = os.path.join(SCRIPT_DIR, "seed_foreign_stations.json")
DIST_DIR = os.path.join(SCRIPT_DIR, "dist")
OUTPUT_JSON_PATH = os.path.join(DIST_DIR, "stations.json")
APP_ASSETS_DIR = os.path.abspath(os.path.join(SCRIPT_DIR, "..", "..", "app", "src", "main", "assets"))
APP_ASSETS_JSON = os.path.join(APP_ASSETS_DIR, "stations.json")

USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"


def fetch_json(url: str, timeout: int = 10) -> Optional[Dict[str, Any]]:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except Exception as e:
        print(f"[WARN] Viga JSONi pärimisel ({url}): {e}")
        return None


def fetch_html(url: str, timeout: int = 10) -> Optional[str]:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "text/html"})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.read().decode("utf-8")
    except Exception as e:
        print(f"[WARN] Viga HTMLi pärimisel ({url}): {e}")
        return None


def scrape_err() -> Dict[str, str]:
    """Kraabib ERR Icecast serverist kõrgeima kvaliteediga (320k) striimid."""
    print("[1/5] Kraabin ERR Icecast serverit...")
    data = fetch_json("https://icecast.err.ee/status-json.xsl")
    if not data or "icestats" not in data or "source" not in data["icestats"]:
        return {}

    sources = data["icestats"]["source"]
    if isinstance(sources, dict):
        sources = [sources]

    # Kaardistus ERR mountpointist jaama slug-ini
    mount_to_slug = {
        "vikerraadiokorge.mp3": "vikerraadio",
        "raadio2korge.mp3": "raadio_2",
        "klassikaraadiokorge.mp3": "klassikaraadio",
        "klaraklassikakorge.mp3": "klara_klassika",
        "klaranostalgiakorge.mp3": "klara_nostalgia",
        "klarameditatsioonkorge.mp3": "klara_meditatsioon",
        "klarajazzkorge.mp3": "klara_jazz",
        "r2chillkorge.mp3": "r2_chill",
        "r2rockkorge.mp3": "r2_rock",
        "r2eestikorge.mp3": "r2_eesti",
        "r2musickorge.mp3": "r2_music",
        "r2pkorge.mp3": "r2_rap",
        "r2popkorge.mp3": "r2_pop",
        "r2alternatiivkorge.mp3": "r2_alternatiiv",
        "raadiotallinnkorge.mp3": "raadio_tallinn"
    }

    results = {}
    for src in sources:
        listen_url = src.get("listenurl", "")
        # nt "http://icecast.err.ee:80/vikerraadiokorge.mp3"
        mount = listen_url.split("/")[-1]
        if mount in mount_to_slug:
            slug = mount_to_slug[mount]
            # Normaliseeri https-iks
            secure_url = f"https://icecast.err.ee/{mount}"
            results[slug] = secure_url

    print(f"  -> Leitud {len(results)} ERR jaama.")
    return results


def scrape_sky() -> Dict[str, str]:
    """Kraabib Sky Media Icecast serverist aktiivsed jaamad."""
    print("[2/5] Kraabin Sky Media Icecast serverit...")
    data = fetch_json("https://stream.skymedia.ee/live/status-json.xsl")
    if not data or "icestats" not in data or "source" not in data["icestats"]:
        return {}

    sources = data["icestats"]["source"]
    if isinstance(sources, dict):
        sources = [sources]

    mount_to_slug = {
        "SKYPLUS": "sky_plus",
        "RETRO": "retro_fm",
        "retro2.mp3": "retro_fm_eesti",
        "retro1.mp3": "retro_disco",
        "RetroJ6ulud": "retro_love",
        "rck": "rock_fm",
        "rckmetal": "rock_fm_metal",
        "rckclassic": "rock_fm_classic",
        "KISS": "kiss_fm",
        "KISS_AAC": "kiss_fm",
        "NRJ": "nrj",
        "NRJdnb": "nrj_dnb",
        "relax": "relax_fm",
        "relax_HD": "relax_fm",
        "cafe": "relax_cafe",
        "instrumental": "relax_instrumental",
        "international": "relax_international",
        "spa": "relax_spa",
        "R7": "raadio_7",
        "Legendaarne": "legendaarne"
    }

    results = {}
    for src in sources:
        listen_url = src.get("listenurl", "")
        mount = listen_url.split("/")[-1].split("?")[0]
        if mount in mount_to_slug:
            slug = mount_to_slug[mount]
            # Eelistame otse CDN või stream.skymedia.ee URL-i
            if slug not in results or "HD" in mount or "_AAC" in mount:
                results[slug] = f"https://stream.skymedia.ee/live/{mount}"

    print(f"  -> Leitud {len(results)} Sky Media jaama.")
    return results


def scrape_tre() -> Dict[str, str]:
    """Kraabib Tre Raadio Icecast serverist aktiivsed jaamad."""
    print("[3/5] Kraabin Tre Raadio Icecast serverit...")
    data = fetch_json("https://cdn.treraadio.ee/status-json.xsl")
    if not data or "icestats" not in data or "source" not in data["icestats"]:
        return {}

    sources = data["icestats"]["source"]
    if isinstance(sources, dict):
        sources = [sources]

    results = {}
    for src in sources:
        listen_url = src.get("listenurl", "")
        mount = listen_url.split("/")[-1].lower()
        if "ringfm" in mount:
            results["tre_raadio_ring_fm"] = "https://cdn.treraadio.ee/ringfm"
        elif "treraadio" in mount:
            results["tre_raadio"] = "https://cdn.treraadio.ee/treraadio"
        elif "ruutfm" in mount:
            results["ruut_fm"] = "https://cdn.treraadio.ee/ruutfm"

    print(f"  -> Leitud {len(results)} Tre Raadio jaama.")
    return results


def scrape_tv3() -> Dict[str, str]:
    """Kraabib raadiod.tv3.ee veebilehtedelt kehtivad audio striimid."""
    print("[4/5] Kraabin TV3 (Star FM, Power) lehti...")
    pages = {
        "star_fm": "https://raadiod.tv3.ee/starfm/",
        "power_hit_radio": "https://raadiod.tv3.ee/power/",
        "star_fm_eesti": "https://raadiod.tv3.ee/starfmeesti/"
    }

    results = {
        # TV3 stabiilsed digikanalid
        "star_fm_80s": "https://ice.leviracloud.eu/StarFM80s",
        "star_fm_90s": "https://ice.leviracloud.eu/StarFM90s"
    }

    for slug, url in pages.items():
        html = fetch_html(url)
        if html:
            # Otsi <audio class="player__audio"...><source src="..."
            m = re.search(r'<audio class="player__audio"[^>]*>.*?<source\s+src="([^"]+)"', html, re.DOTALL)
            if m:
                stream_url = m.group(1).strip()
                # Kui on saadaval 320k versioon, kasuta kvaliteetsemat
                if "star128" in stream_url:
                    stream_url = "https://ice.leviracloud.eu/star320-mp3"
                elif "starFMEesti128" in stream_url:
                    stream_url = "https://ice.leviracloud.eu/starFMEesti320-mp3"
                results[slug] = stream_url

    print(f"  -> Leitud {len(results)} TV3 jaama.")
    return results


def get_duo_streams() -> Dict[str, str]:
    """Duo Media / Postimees Grupi EUDDN kanoonilised striimid."""
    base = "https://router.euddn.net/8103046e16b71d15d692b57c187875c7"
    return {
        "elmar": f"{base}/elmar.aac",
        "elmar_kuld": f"{base}/elmarikuld.aac",
        "elmari_ballaadid": f"{base}/EB_elmariballaadid.aac",
        "elmari_tantsuohtu": f"{base}/elmaritantsuohtu.aac",
        "kuku": f"{base}/kuku_high.mp3",
        "myhits": f"{base}/myhits.mp3",
        "duo_rock": f"{base}/rokk.aac",
        "duo_country": f"{base}/dc_duocountry.aac",
        "duo_gold": f"{base}/kuld.aac",
        "duo_softmix": f"{base}/DS_softmix.aac",
        "duo_party": f"{base}/duodance.aac",
        "duo_hitmix": f"{base}/DHM_hitmix.aac",
        "duo_dance": f"{base}/md_mhdance.aac",
        "duo_christmas": f"{base}/christmas.aac"
    }


def build_stations_list() -> List[Dict[str, Any]]:
    """Ühendab kõik reaalajas kraabitud striimid ja baas-registri."""
    with open(REGISTRY_PATH, "r", encoding="utf-8") as f:
        registry = json.load(f)

    # 1. Korja kõigi kraapijate tulemused
    scraped_streams: Dict[str, str] = {}
    scraped_streams.update(scrape_err())
    scraped_streams.update(scrape_sky())
    scraped_streams.update(scrape_tre())
    scraped_streams.update(scrape_tv3())
    scraped_streams.update(get_duo_streams())

    print(f"[5/5] Koostan lõplikku jaamade nimekirja...")

    final_stations = []

    # 2. Eesti jaamad registrist
    for slug, meta in registry.items():
        stream_url = scraped_streams.get(slug, meta.get("fallback_url"))
        station = {
            "id": meta["id"],
            "name": meta["name"],
            "url": stream_url,
            "isActive": True,
            "priority": meta["priority"],
            "category": meta["category"],
            "countrycode": meta["countrycode"]
        }
        final_stations.append(station)

    # 3. Välisjaamad seemnest (seed_foreign_stations.json)
    if os.path.exists(FOREIGN_SEED_PATH):
        with open(FOREIGN_SEED_PATH, "r", encoding="utf-8") as f:
            foreign = json.load(f)
            final_stations.extend(foreign)

    # 4. Sorteeri priority ja ID järgi
    final_stations.sort(key=lambda x: (x.get("priority", 9999), x.get("id", 0)))

    return final_stations


def validate_stations(stations: List[Dict[str, Any]]) -> bool:
    """Valideerib genereeritud jaamade nimekirja terviklikkust."""
    seen_ids = set()
    errors = []

    for s in stations:
        sid = s.get("id")
        name = s.get("name")
        url = s.get("url")
        if not sid or not name or not url:
            errors.append(f"Vigane jaama kirje: {s}")
        if sid in seen_ids:
            errors.append(f"Dubleeruv jaama ID: {sid} ({name})")
        seen_ids.add(sid)

    if errors:
        print("[ERROR] Valideerimise vead:")
        for err in errors:
            print(" -", err)
        return False

    print(f"[OK] Valideeritud edukalt: kokku {len(stations)} jaama.")
    return True


def publish_to_gist(gist_id: str, token: str, stations_json_str: str) -> bool:
    """Uuendab jaamade nimekirja GitHub Gistis API kaudu."""
    print(f"Avaldan uuenduse GitHub Gisti (ID: {gist_id})...")
    url = f"https://api.github.com/gists/{gist_id}"
    payload = {
        "description": "Kellraadio stations list (auto-updated)",
        "files": {
            "stations.json": {
                "content": stations_json_str
            }
        }
    }
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, method="PATCH", headers={
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "User-Agent": USER_AGENT,
        "Content-Type": "application/json"
    })

    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            if resp.status == 200:
                print("[OK] GitHub Gist uuendatud edukalt!")
                return True
            else:
                print(f"[ERROR] Gisti uuendamine ebaõnnestus koodiga {resp.status}")
                return False
    except Exception as e:
        print(f"[ERROR] Gisti uuendamise viga: {e}")
        return False


def main():
    parser = argparse.ArgumentParser(description="Genereeri Kellraadio stations.json")
    parser.add_argument("--publish", action="store_true", help="Avalda uuendus otse GitHub Gisti")
    parser.add_argument("--gist-id", default=os.environ.get("GIST_ID", "e886795e2e2ae5df7b9573bd3f84333b"), help="GitHub Gist ID")
    parser.add_argument("--gist-token", default=os.environ.get("GIST_TOKEN"), help="GitHub Personal Access Token")
    args = parser.parse_args()

    os.makedirs(DIST_DIR, exist_ok=True)

    stations = build_stations_list()
    if not validate_stations(stations):
        sys.exit(1)

    json_content = json.dumps(stations, indent=2, ensure_ascii=False)

    # 1. Salvesta dist/stations.json
    with open(OUTPUT_JSON_PATH, "w", encoding="utf-8") as f:
        f.write(json_content)
    print(f"Salvestatud: {OUTPUT_JSON_PATH}")

    # 2. Salvesta app/src/main/assets/stations.json
    os.makedirs(APP_ASSETS_DIR, exist_ok=True)
    with open(APP_ASSETS_JSON, "w", encoding="utf-8") as f:
        f.write(json_content)
    print(f"Salvestatud äpi varukoopia: {APP_ASSETS_JSON}")

    # 3. Soovi korral avalda Gisti
    if args.publish:
        if not args.gist_token:
            print("[WARN] --publish märgitud, kuid GIST_TOKEN puudub. Gisti ei uuendatud.")
        else:
            publish_to_gist(args.gist_id, args.gist_token, json_content)

    print("\nValmis!")


if __name__ == "__main__":
    main()
