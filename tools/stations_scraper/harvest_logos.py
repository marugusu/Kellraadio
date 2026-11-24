#!/usr/bin/env python3
"""
harvest_logos.py

Automaatne raadiojaamade logode korjaja ja kvaliteedikontrollija Kellraadio jaoks.
Otsib igale jaamale (station_registry.json + seed_foreign_stations.json) parima logo:
1. Loeb Duo/Pleier portaalist (`pleier.ee`) täpse kanalite JSON-i.
2. Küsib RadioBrowser API-st `favicon` ja `homepage` URL-i (täpse riigikoodi + URL-i järgi).
3. Kraabib jaamade ametlikke kodulehti (`apple-touch-icon`, suure `icon`, `og:image`, SVG).
4. Rakendab brändi alamkanalitele (nt R2 *, KlaRa *, Rock FM *, Relax *) ema-brändi logo, kui eraldi logo puudub.
5. Valideerib iga kandidaadi (HTTP 200, tegelik pildisisu, mõõtmed/formaat, HTTPS eelistus).
6. Genereerib `dist/logo_preview.html` eelvaate Titanium & Amber kiibi sees ning
   `--apply` lipuga kirjutab `iconUrl` väljad registrisse.
"""

import argparse
import concurrent.futures
import html as html_lib
import json
import os
import re
import struct
import urllib.error
import urllib.parse
import urllib.request
from html.parser import HTMLParser
from typing import Any, Dict, List, Optional, Tuple

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REGISTRY_PATH = os.path.join(SCRIPT_DIR, "station_registry.json")
FOREIGN_SEED_PATH = os.path.join(SCRIPT_DIR, "seed_foreign_stations.json")
DIST_DIR = os.path.join(SCRIPT_DIR, "dist")
PREVIEW_HTML_PATH = os.path.join(DIST_DIR, "logo_preview.html")
REPORT_JSON_PATH = os.path.join(DIST_DIR, "logo_report.json")

USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

# Teadaolevad kodulehed / kureeritud otsevihjed
KNOWN_HOMEPAGES: Dict[str, List[str]] = {
    # ERR
    "vikerraadio": ["https://vikerraadio.err.ee/", "https://jupiter.err.ee/vikerraadio"],
    "raadio_2": ["https://r2.err.ee/"],
    "klassikaraadio": ["https://klassikaraadio.err.ee/", "https://jupiter.err.ee/klassikaraadio"],
    "klara_klassika": ["https://klara.err.ee/"],
    "klara_nostalgia": ["https://klara.err.ee/"],
    "klara_meditatsioon": ["https://klara.err.ee/"],
    "klara_jazz": ["https://klara.err.ee/"],
    "r2_chill": ["https://r2extra.err.ee/"],
    "r2_rock": ["https://r2extra.err.ee/"],
    "r2_eesti": ["https://r2extra.err.ee/"],
    "r2_music": ["https://r2extra.err.ee/"],
    "r2_rap": ["https://r2extra.err.ee/"],
    "r2_pop": ["https://r2extra.err.ee/"],
    "r2_alternatiiv": ["https://r2extra.err.ee/"],
    "raadio_tallinn": ["https://raadiotallinn.err.ee/"],
    # Duo / Pleier
    "elmar": ["https://elmar.pleier.ee/"],
    "elmar_kuld": ["https://elmarikuld.pleier.ee/"],
    "elmari_ballaadid": ["https://pleier.ee/digijaamad"],
    "elmari_tantsuohtu": ["https://elmaritantsuohtu.pleier.ee/"],
    "kuku": ["https://kuku.pleier.ee/"],
    "raadio_duo": ["https://raadioduo.pleier.ee/"],
    "myhits": ["https://myhits.pleier.ee/"],
    "myhits_rock": ["https://myhitsrock.pleier.ee/"],
    "myhits_dance": ["https://myhitsdance.pleier.ee/"],
    "myhits_kpop": ["https://pleier.ee/digijaamad"],
    "duo_country": ["https://duocountry.pleier.ee/", "https://pleier.ee/"],
    "duo_gold": ["https://duogold.pleier.ee/", "https://pleier.ee/"],
    "duo_softmix": ["https://duosoftmix.pleier.ee/", "https://pleier.ee/"],
    "duo_party": ["https://duoparty.pleier.ee/", "https://pleier.ee/"],
    "duo_hitmix": ["https://duohitmix.pleier.ee/", "https://pleier.ee/"],
    "duo_christmas": ["https://pleier.ee/digijaamad/duo-christmas", "https://pleier.ee/digijaamad"],
    "raadio_kidzone": ["https://kidzone.pleier.ee/", "https://pleier.ee/"],
    # Sky Media
    "sky_plus": ["https://skyplus.sky.ee/", "https://sky.ee/"],
    "retro_fm": ["https://retrofm.sky.ee/"],
    "retro_fm_eesti": ["https://retrofm.sky.ee/"],
    "retro_disco": ["https://retrofm.sky.ee/"],
    "retro_love": ["https://retrofm.sky.ee/"],
    "rock_fm": ["https://rockfm.sky.ee/"],
    "rock_fm_metal": ["https://rockfm.sky.ee/"],
    "rock_fm_classic": ["https://rockfm.sky.ee/"],
    "kiss_fm": ["https://kissfm.sky.ee/"],
    "nrj": ["https://nrj.sky.ee/"],
    "nrj_dnb": ["https://nrj.sky.ee/"],
    "relax_fm": ["https://relaxfm.sky.ee/"],
    "relax_cafe": ["https://relaxfm.sky.ee/"],
    "relax_instrumental": ["https://relaxfm.sky.ee/"],
    "relax_international": ["https://relaxfm.sky.ee/"],
    "relax_spa": ["https://relaxfm.sky.ee/"],
    "raadio_7": ["https://raadio7.ee/"],
    "legendaarne": ["https://legendaarne.ee/", "https://sky.ee/"],
    # Tre / Ring / Ruut
    "tre_raadio": ["https://treraadio.ee/"],
    "tre_raadio_ring_fm": ["https://ringfm.ee/", "https://treraadio.ee/"],
    "ruut_fm": ["https://ruutfm.ee/"],
    # TV3
    "star_fm": ["https://raadiod.tv3.ee/starfm/"],
    "star_fm_eesti": ["https://raadiod.tv3.ee/starfmeesti/"],
    "star_fm_80s": ["https://raadiod.tv3.ee/starfm/"],
    "star_fm_90s": ["https://raadiod.tv3.ee/starfm/"],
    "power_hit_radio": ["https://raadiod.tv3.ee/power/"],
    # Muud EE
    "big_fm": ["https://bigfm.ee/"],
    "raadio_kadi": ["https://kadi.ee/"],
    "aripaev": ["https://www.aripaev.ee/raadio"],
    "ida": ["https://idaidaida.net/"],
    "kaguraadio": ["https://kaguraadio.ee/"],
    "meie_raadio": ["https://meieraadio.ee/"],
    "xfm": ["https://xfm.ee/"],
    "finest_fm": ["https://finestfm.fi/"],
}

# Välisjaamade kodulehed ID järgi
FOREIGN_HOMEPAGES: Dict[int, List[str]] = {
    29: ["https://schlagerparadies.de/"],
    30: ["https://www.antenne.de/"],
    31: ["https://www.supla.fi/radiorock"],
    32: ["https://yle.fi/aihe/yle-radio-1"],
    33: ["https://yle.fi/aihe/yle-radio-suomi"],
    34: ["https://yle.fi/aihe/yle-sami-radio"],
    36: ["https://www.bbc.co.uk/sounds/play/live:bbc_radio_one"],
    37: ["https://www.bbc.co.uk/sounds/play/live:bbc_radio_fourfm"],
    58: ["https://www.capitalfm.com/"],
    60: ["https://www.mygoldmusic.co.uk/"],
    61: ["https://www.heart.co.uk/70s/"],
    62: ["https://www.heart.co.uk/80s/"],
    63: ["https://www.heart.co.uk/90s/"],
    64: ["https://www.br.de/radio/bayern1/index.html"],
    67: ["https://www.rtlradio.de/"],
    68: ["https://www.br.de/radio/br24/index.html"],
    69: ["https://www.ballermann-radio.de/"],
    50: ["https://walmradio.com/"],
}

ERR_BUNDLED_SLUGS = {
    "vikerraadio", "raadio_2", "klassikaraadio", "raadio_tallinn",
    "klara_klassika", "klara_jazz", "klara_nostalgia", "klara_meditatsioon",
    "r2_chill", "r2_rock", "r2_eesti", "r2_music", "r2_rap", "r2_pop", "r2_alternatiiv",
}

# Kureeritud ametlikud otse-logod (kui portaalid annavad kehva/vale varuvariandi)
CURATED_DIRECT_CANDIDATES: Dict[str, List[Tuple[str, str, int]]] = {
    "elmari_ballaadid": [("https://f303.pmo.ee/media/2025/07/elmari-ballaadid-3000x3000px.png", "pleier-official", 145)],
    "duo_christmas": [("https://f303.pmo.ee/media/2025/10/duo-christmas.png", "pleier-official", 145)],
    "myhits_kpop": [("https://f303.pmo.ee/media/2026/10/myhits-k-pop.png", "pleier-official", 145)],
    "rock_fm": [("https://sky.ee/storage/radio-stations/2025/12/Rock-FM.png", "sky-official", 110)],
    "rock_fm_metal": [("https://sky.ee/storage/radio-stations/2025/12/Rock-FM.png", "sky-brand", 105)],
    "rock_fm_classic": [("https://sky.ee/storage/radio-stations/2025/12/Rock-FM.png", "sky-brand", 105)],
    "relax_fm": [("https://sky.ee/storage/radio-stations/2025/12/Relax-FM-1.png", "sky-official", 110)],
    "relax_cafe": [("https://sky.ee/storage/radio-stations/2025/12/Relax-FM-1.png", "sky-brand", 105)],
    "relax_instrumental": [("https://sky.ee/storage/radio-stations/2025/12/Relax-FM-1.png", "sky-brand", 105)],
    "relax_international": [("https://sky.ee/storage/radio-stations/2025/12/Relax-FM-1.png", "sky-brand", 105)],
    "relax_spa": [("https://sky.ee/storage/radio-stations/2025/12/Relax-FM-1.png", "sky-brand", 105)],
}

CURATED_FOREIGN_BY_ID: Dict[int, List[Tuple[str, str, int]]] = {
    31: [("https://img.nm-ovp.nelonenmedia.fi/v1/novelist?src=/files/misc_images/2024-08/RadioRock_2560x2560.jpg", "supla-official", 115)],
    36: [("https://sounds.files.bbci.co.uk/3.12.0/networks/bbc_radio_one/blocks-colour_600x600.png", "bbc-official", 120)],
    37: [("https://sounds.files.bbci.co.uk/3.12.0/networks/bbc_radio_four/blocks-colour_600x600.png", "bbc-official", 120)],
    50: [("https://www.christmasvinyls.store/cdn/shop/files/gpt-image-2_cambia_la_frase_let_it_spin_por_Christmas_Vinyls._el_bode_de_color_beige_que_rod-0-removebg-preview.png?v=1786573937&width=500", "curated-direct", 130)],
    67: [("https://www.rtlradio.de/assets/icons/icon-180x180.png", "rtl-https", 110)],
    68: [("https://www.br.de/layout/img/wellen/br24-radio-teaser-100~_v-img__16__9__xl_-d31c35f8186ebeb80b0cd843a7c267a0e0c81647.png?version=38738", "br-official", 105)],
}

BLOCKED_IMAGE_HINTS = ("default-track-image", "novelist.webp", "google-chrome", "edge-logo", "firefox-logo", "safari-logo")


class MetaIconParser(HTMLParser):
    def __init__(self, base_url: str):
        super().__init__()
        self.base_url = base_url
        self.candidates: List[Tuple[str, str, int]] = []

    def handle_starttag(self, tag: str, attrs: List[Tuple[str, Optional[str]]]):
        attr_map = {k.lower(): (v or "") for k, v in attrs}
        if tag == "link":
            rel = attr_map.get("rel", "").lower()
            href = attr_map.get("href", "").strip()
            sizes = attr_map.get("sizes", "").lower()
            if not href:
                return
            abs_url = urllib.parse.urljoin(self.base_url, href)
            if "apple-touch-icon" in rel:
                self.candidates.append((abs_url, "apple-touch-icon", 95))
            elif "icon" in rel or "shortcut" in rel:
                bonus = 65
                m = re.search(r"(\d+)x\d+", sizes)
                if m:
                    sz = int(m.group(1))
                    bonus = min(90, 50 + sz // 4)
                if abs_url.lower().endswith(".svg"):
                    bonus = 82
                self.candidates.append((abs_url, f"link-icon({sizes or 'any'})", bonus))
        elif tag == "meta":
            prop = (attr_map.get("property") or attr_map.get("name") or "").lower()
            content = attr_map.get("content", "").strip()
            if prop in ("og:image", "twitter:image", "msapplication-tileimage") and content:
                abs_url = urllib.parse.urljoin(self.base_url, content)
                bonus = 75 if "tile" in prop else 45
                self.candidates.append((abs_url, prop, bonus))
        elif tag == "img":
            src = (attr_map.get("src") or attr_map.get("data-src") or "").strip()
            cls = (attr_map.get("class") or "") + " " + (attr_map.get("alt") or "") + " " + (attr_map.get("id") or "")
            if src and "logo" in cls.lower():
                abs_url = urllib.parse.urljoin(self.base_url, src)
                if not abs_url.startswith("data:"):
                    self.candidates.append((abs_url, "img-logo", 60))


def http_get(url: str, timeout: int = 8, accept: str = "*/*") -> Tuple[Optional[int], Dict[str, str], bytes]:
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": USER_AGENT,
            "Accept": accept,
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            headers = {k.lower(): v for k, v in resp.headers.items()}
            data = resp.read(2 * 1024 * 1024)
            return resp.status, headers, data
    except Exception:
        return None, {}, b""


def inspect_image_bytes(data: bytes, content_type: str, url: str) -> Optional[Dict[str, Any]]:
    if not data or len(data) < 32:
        return None
    ct = (content_type or "").lower()
    lower_url = url.lower().split("?")[0]

    head_text = data[:1024].decode("utf-8", errors="ignore").lower()
    if "image/svg" in ct or lower_url.endswith(".svg") or "<svg" in head_text:
        # Coil kasutab vaikimisi rasterpilte (PNG/WebP/JPG/ICO) ilma välise coil-svg moodulita
        return None

    if data[:8] == b"\x89PNG\r\n\x1a\n" and len(data) >= 24:
        w, h = struct.unpack(">II", data[16:24])
        if w < 16 or h < 16:
            return None
        return {"format": "png", "width": w, "height": h, "bytes": len(data)}

    if data[:2] == b"\xff\xd8":
        w, h = _parse_jpeg_size(data)
        if w and h and (w < 16 or h < 16):
            return None
        return {"format": "jpg", "width": w or 0, "height": h or 0, "bytes": len(data)}

    if data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        return {"format": "webp", "width": 0, "height": 0, "bytes": len(data)}

    if data[:4] == b"\x00\x00\x01\x00" and len(data) >= 6:
        count = struct.unpack("<H", data[4:6])[0]
        best = 0
        for i in range(min(count, 16)):
            off = 6 + i * 16
            if off + 4 <= len(data):
                bw = data[off] or 256
                bh = data[off + 1] or 256
                best = max(best, min(bw, bh))
        return {"format": "ico", "width": best, "height": best, "bytes": len(data)}

    if data[:6] in (b"GIF87a", b"GIF89a") and len(data) >= 10:
        w, h = struct.unpack("<HH", data[6:10])
        if w < 24 or h < 24:
            return None
        return {"format": "gif", "width": w, "height": h, "bytes": len(data)}

    return None


def _parse_jpeg_size(data: bytes) -> Tuple[int, int]:
    i = 2
    n = len(data)
    while i + 9 < n:
        if data[i] != 0xFF:
            i += 1
            continue
        marker = data[i + 1]
        if marker in (0xC0, 0xC1, 0xC2):
            h, w = struct.unpack(">HH", data[i + 5 : i + 9])
            return w, h
        if marker == 0xD8 or marker == 0xD9 or (0xD0 <= marker <= 0xD7):
            i += 2
            continue
        if i + 4 > n:
            break
        seg_len = struct.unpack(">H", data[i + 2 : i + 4])[0]
        i += 2 + seg_len
    return 0, 0


def scrape_pleier_channels() -> Dict[str, List[Tuple[str, str, int]]]:
    """Loeb pleier.ee avalehelt kõigi Duo/Elmar/MyHits/Kuku kanalite täpsed pildid."""
    extra: Dict[str, List[Tuple[str, str, int]]] = {}
    status, _, data = http_get("https://pleier.ee/", timeout=10)
    if status != 200 or not data:
        return extra

    text = data.decode("utf-8", errors="ignore").replace("\\u002F", "/").replace("\\/", "/")
    name_to_slug = {
        "kuku": "kuku",
        "elmar": "elmar",
        "elmari kuld": "elmar_kuld",
        "elmari ballaadid": "elmari_ballaadid",
        "elmari tantsuõhtu": "elmari_tantsuohtu",
        "raadio duo": "raadio_duo",
        "myhits": "myhits",
        "myhits rock": "myhits_rock",
        "myhits dance": "myhits_dance",
        "myhits k-pop": "myhits_kpop",
        "duo rock": "myhits_rock",
        "duo dance": "myhits_dance",
        "duo hitmix": "duo_hitmix",
        "duo country": "duo_country",
        "duo gold": "duo_gold",
        "duo softmix": "duo_softmix",
        "duo party": "duo_party",
        "duo christmas": "duo_christmas",
        "raadio kidzone": "raadio_kidzone",
    }
    for m in re.finditer(r'"name"\s*:\s*"([^"]{2,40})"[^{}]{0,400}"image"\s*:\s*"(https?://[^"]+)"', text):
        ch_name = m.group(1).strip().lower()
        img_url = m.group(2).strip()
        slug = name_to_slug.get(ch_name)
        if slug:
            extra.setdefault(slug, []).append((img_url, "pleier-channel-json", 145))
    return extra


def query_radiobrowser(name: str, country_code: str, stream_url: str) -> Tuple[List[str], List[str]]:
    favicons: List[str] = []
    homepages: List[str] = []
    queries = [
        f"https://de1.api.radio-browser.info/json/stations/byurl?url={urllib.parse.quote(stream_url)}",
        f"https://de1.api.radio-browser.info/json/stations/search?name={urllib.parse.quote(name)}&countrycode={urllib.parse.quote(country_code)}&limit=5&order=clickcount&reverse=true",
    ]
    for q in queries:
        status, _, data = http_get(q, timeout=8, accept="application/json")
        if status == 200 and data:
            try:
                items = json.loads(data.decode("utf-8"))
                for item in items:
                    item_cc = (item.get("countrycode") or "").upper()
                    if country_code and item_cc and item_cc != country_code.upper():
                        continue
                    fav = (item.get("favicon") or "").strip()
                    hp = (item.get("homepage") or "").strip()
                    if fav.startswith("http") and fav not in favicons:
                        favicons.append(fav)
                    if hp.startswith("http") and hp not in homepages:
                        homepages.append(hp)
            except Exception:
                pass
        if favicons:
            break
    return favicons, homepages


def extract_page_icons(page_url: str) -> List[Tuple[str, str, int]]:
    status, _, data = http_get(page_url, timeout=8, accept="text/html")
    if status != 200 or not data:
        return []
    text = data.decode("utf-8", errors="ignore")
    parser = MetaIconParser(page_url)
    try:
        parser.feed(text)
    except Exception:
        pass
    parsed = urllib.parse.urlparse(page_url)
    if parsed.scheme and parsed.netloc:
        parser.candidates.append((f"{parsed.scheme}://{parsed.netloc}/favicon.ico", "default-favicon", 30))
    return parser.candidates


def score_candidate(info: Dict[str, Any], bonus: int, url: str) -> float:
    w = info.get("width", 0)
    h = info.get("height", 0)
    fmt = info.get("format", "")
    score = float(bonus)

    if fmt == "svg":
        score += 35
    elif w >= 180 and h >= 180:
        score += 40
    elif w >= 96 and h >= 96:
        score += 30
    elif w >= 48 and h >= 48:
        score += 18
    elif w > 0 and w < 32:
        score -= 20

    if w > 0 and h > 0:
        ratio = max(w / h, h / w)
        if ratio <= 1.35:
            score += 20
        elif ratio > 2.5:
            score -= 15

    if url.startswith("https://"):
        score += 10
    else:
        score -= 25

    if fmt in ("png", "webp"):
        score += 8
    elif fmt == "ico":
        score -= 5

    return score


def harvest_one(
    station: Dict[str, Any],
    slug: Optional[str],
    portal_extra: Dict[str, List[Tuple[str, str, int]]],
) -> Dict[str, Any]:
    sid = station["id"]
    name = station["name"]
    country = station.get("countrycode", "")
    stream_url = station.get("url") or station.get("fallback_url") or ""

    if slug and slug in ERR_BUNDLED_SLUGS:
        res_name = f"ic_err_{slug}"
        return {
            "id": sid,
            "slug": slug,
            "name": name,
            "category": station.get("category", ""),
            "countrycode": country,
            "best": {
                "url": f"res://{res_name}",
                "kind": "err-vector-drawable",
                "score": 250.0,
                "format": "vector",
                "width": 100,
                "height": 100,
                "bytes": 0,
            },
            "alternatives": [],
        }

    raw_candidates: List[Tuple[str, str, int]] = []

    if slug and slug in CURATED_DIRECT_CANDIDATES:
        raw_candidates.extend(CURATED_DIRECT_CANDIDATES[slug])
    if sid in CURATED_FOREIGN_BY_ID:
        raw_candidates.extend(CURATED_FOREIGN_BY_ID[sid])
    if slug and slug in portal_extra:
        raw_candidates.extend(portal_extra[slug])

    rb_favicons, rb_homepages = query_radiobrowser(name, country, stream_url)
    for fav in rb_favicons:
        raw_candidates.append((fav, "radiobrowser-favicon", 78))

    pages_to_check: List[str] = []
    if slug and slug in KNOWN_HOMEPAGES:
        pages_to_check.extend(KNOWN_HOMEPAGES[slug])
    elif sid in FOREIGN_HOMEPAGES:
        pages_to_check.extend(FOREIGN_HOMEPAGES[sid])
    for hp in rb_homepages:
        if hp not in pages_to_check:
            pages_to_check.append(hp)

    for page in pages_to_check[:2]:
        raw_candidates.extend(extract_page_icons(page))

    dedup: Dict[str, Tuple[str, int]] = {}
    for c_url, kind, bonus in raw_candidates:
        c_url = c_url.strip()
        if not c_url.startswith("http"):
            continue
        low = c_url.lower()
        if any(bad in low for bad in BLOCKED_IMAGE_HINTS):
            continue
        if c_url not in dedup or bonus > dedup[c_url][1]:
            dedup[c_url] = (kind, bonus)

    verified: List[Dict[str, Any]] = []
    for c_url, (kind, bonus) in list(dedup.items())[:12]:
        status, headers, data = http_get(c_url, timeout=7)
        if status != 200:
            continue
        info = inspect_image_bytes(data, headers.get("content-type", ""), c_url)
        if not info:
            continue
        sc = score_candidate(info, bonus, c_url)
        verified.append({
            "url": c_url,
            "kind": kind,
            "score": sc,
            **info,
        })

    verified.sort(key=lambda x: x["score"], reverse=True)
    best = verified[0] if verified else None

    return {
        "id": sid,
        "slug": slug,
        "name": name,
        "category": station.get("category", ""),
        "countrycode": country,
        "best": best,
        "alternatives": verified[1:4],
    }


def _vector_xml_to_inline_svg(res_name: str) -> Optional[str]:
    import xml.etree.ElementTree as ET
    xml_path = os.path.abspath(os.path.join(SCRIPT_DIR, "..", "..", "app", "src", "main", "res", "drawable", f"{res_name}.xml"))
    if not os.path.exists(xml_path):
        return None
    ns = "{http://schemas.android.com/apk/res/android}"
    root = ET.parse(xml_path).getroot()

    def convert_node(elem) -> str:
        tag = elem.tag.split("}")[-1]
        if tag == "path":
            fill = elem.attrib.get(ns + "fillColor", "#FFFFFF")
            d = elem.attrib.get(ns + "pathData", "")
            fr = ' fill-rule="evenodd"' if elem.attrib.get(ns + "fillType") == "evenOdd" else ""
            return f'<path fill="{html_lib.escape(fill)}"{fr} d="{html_lib.escape(d)}" />'
        if tag == "group":
            px = float(elem.attrib.get(ns + "pivotX", "0"))
            py = float(elem.attrib.get(ns + "pivotY", "0"))
            sx = float(elem.attrib.get(ns + "scaleX", "1"))
            sy = float(elem.attrib.get(ns + "scaleY", "1"))
            tx = float(elem.attrib.get(ns + "translateX", "0"))
            ty = float(elem.attrib.get(ns + "translateY", "0"))
            transforms = []
            if px or py:
                transforms.append(f"translate({px} {py})")
            if sx != 1 or sy != 1:
                transforms.append(f"scale({sx} {sy})")
            if px or py:
                transforms.append(f"translate({-px} {-py})")
            if tx or ty:
                transforms.append(f"translate({tx} {ty})")
            children = "".join(convert_node(c) for c in elem)
            tr_attr = f' transform="{" ".join(transforms)}"' if transforms else ""
            return f"<g{tr_attr}>{children}</g>"
        return ""

    inner = "".join(convert_node(c) for c in root)
    return f'<svg viewBox="0 0 100 100" width="100%" height="100%" xmlns="http://www.w3.org/2000/svg">{inner}</svg>'


def render_preview_html(results: List[Dict[str, Any]]) -> str:
    found_count = sum(1 for r in results if r["best"] is not None)
    cards = []
    for r in results:
        best = r["best"]
        name_esc = html_lib.escape(r["name"])
        cat_esc = html_lib.escape(f"{r['countrycode']} • {r['category']}")
        chip_cls = "chip-dark"
        if best:
            if best["url"].startswith("res://"):
                res_name = best["url"].removeprefix("res://")
                svg_html = _vector_xml_to_inline_svg(res_name)
                img_html = svg_html or f'<span class="fallback">{html_lib.escape(res_name)}</span>'
                chip_cls = "chip-full"
                url_html = f'<span>{html_lib.escape(best["url"])}</span>'
            else:
                img_html = f'<img src="{html_lib.escape(best["url"])}" alt="{name_esc}" loading="lazy" />'
                chip_cls = "chip-light"
                url_html = f'<a href="{html_lib.escape(best["url"])}" target="_blank">{html_lib.escape(best["url"][:55])}...</a>'
            meta = f'{best["format"].upper()} {best["width"]}x{best["height"]} • {html_lib.escape(best["kind"])}'
            status_cls = "ok"
        else:
            initials = "".join([w[0] for w in r["name"].split()[:2]]).upper()[:4] or "RADI"
            img_html = f'<span class="fallback">{html_lib.escape(initials)}</span>'
            meta = "PUUDUB — jääb initsiaalimärk"
            status_cls = "missing"
            url_html = "<span>—</span>"

        alt_links = " ".join(
            f'<a class="alt" href="{html_lib.escape(a["url"])}" target="_blank">alt({a["format"]})</a>'
            for a in r["alternatives"]
        )

        cards.append(f"""
        <div class="card {status_cls}">
          <div class="chip {chip_cls}">
            {img_html}
          </div>
          <div class="info">
            <div class="title">{name_esc}</div>
            <div class="sub">{cat_esc}</div>
            <div class="meta">{meta}</div>
            <div class="url">{url_html} {alt_links}</div>
          </div>
        </div>""")

    return f"""<!DOCTYPE html>
<html lang="et">
<head>
<meta charset="utf-8" />
<title>Kellraadio — Jaamaikoonide eelvaade ({found_count}/{len(results)})</title>
<style>
  body {{ background: #0A0C10; color: #F8FAFC; font-family: system-ui, sans-serif; margin: 24px; }}
  h1 {{ color: #FFA726; margin-bottom: 4px; }}
  p.summary {{ color: #94A3B8; margin-top: 0; }}
  .grid {{ display: grid; grid-template-columns: repeat(auto-fill, minmax(360px, 1fr)); gap: 12px; margin-top: 20px; }}
  .card {{ background: #181B26; border: 1px solid #242938; border-radius: 10px; padding: 10px 12px; display: flex; align-items: center; gap: 12px; }}
  .card.missing {{ border-color: #7F1D1D; }}
  .chip {{ width: 44px; height: 44px; border-radius: 8px; flex-shrink: 0; display: flex; align-items: center; justify-content: center; box-sizing: border-box; border: 1px solid #242938; overflow: hidden; }}
  .chip-light {{ background: #E2E8F0; padding: 4px; }}
  .chip-full {{ background: transparent; padding: 0; }}
  .chip-dark {{ background: #12151E; }}
  .chip img {{ width: 100%; height: 100%; object-fit: contain; display: block; }}
  .fallback {{ font-weight: 800; font-size: 12px; color: #94A3B8; }}
  .info {{ min-width: 0; flex: 1; }}
  .title {{ font-weight: 600; font-size: 15px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }}
  .sub {{ font-size: 11px; color: #94A3B8; }}
  .meta {{ font-size: 11px; color: #FFB74D; margin-top: 2px; }}
  .url {{ font-size: 10px; color: #64748B; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }}
  a {{ color: #64748B; text-decoration: none; }}
  a:hover {{ color: #FFA726; }}
  a.alt {{ color: #38BDF8; margin-left: 6px; }}
</style>
</head>
<body>
  <h1>Kellraadio — Logode eelvaade</h1>
  <p class="summary">Leitud {found_count} / {len(results)} jaamale. Kiip simuleerib #E2E8F0 plaatina-kiipi (4px sisevahe, Fit) #181B26 kaardi peal.</p>
  <div class="grid">
    {''.join(cards)}
  </div>
</body>
</html>"""


def main():
    parser = argparse.ArgumentParser(description="Kogu Kellraadio jaamadele logod")
    parser.add_argument("--apply", action="store_true", help="Kirjuta leitud iconUrl-id registrisse")
    args = parser.parse_args()

    os.makedirs(DIST_DIR, exist_ok=True)

    with open(REGISTRY_PATH, "r", encoding="utf-8") as f:
        registry = json.load(f)
    foreign = []
    if os.path.exists(FOREIGN_SEED_PATH):
        with open(FOREIGN_SEED_PATH, "r", encoding="utf-8") as f:
            foreign = json.load(f)

    print("Laen Pleieri kanalite JSON-i...")
    portal_extra = scrape_pleier_channels()
    print(f"  -> Pleierist leiti täpsed logod {len(portal_extra)} kanalile.")

    tasks: List[Tuple[Dict[str, Any], Optional[str]]] = []
    for slug, meta in registry.items():
        tasks.append((meta, slug))
    for f_station in foreign:
        tasks.append((f_station, None))

    print(f"Otsin ja valideerin logosid {len(tasks)} jaamale...")
    results: List[Dict[str, Any]] = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:
        futs = [pool.submit(harvest_one, st, sl, portal_extra) for st, sl in tasks]
        for fut in concurrent.futures.as_completed(futs):
            results.append(fut.result())

    order_map = {st["id"]: idx for idx, (st, _) in enumerate(tasks)}
    results.sort(key=lambda r: order_map.get(r["id"], 9999))

    with open(REPORT_JSON_PATH, "w", encoding="utf-8") as f:
        json.dump(results, f, indent=2, ensure_ascii=False)
    with open(PREVIEW_HTML_PATH, "w", encoding="utf-8") as f:
        f.write(render_preview_html(results))

    found = sum(1 for r in results if r["best"] is not None)
    print(f"Valmis! Leiti logo {found}/{len(results)} jaamale.")
    print(f"Eelvaade: {PREVIEW_HTML_PATH}")

    if args.apply:
        by_id = {r["id"]: r["best"]["url"] for r in results if r["best"] is not None}
        for slug, meta in registry.items():
            if meta["id"] in by_id:
                meta["iconUrl"] = by_id[meta["id"]]
        with open(REGISTRY_PATH, "w", encoding="utf-8") as f:
            json.dump(registry, f, indent=2, ensure_ascii=False)
        for st in foreign:
            if st["id"] in by_id:
                st["iconUrl"] = by_id[st["id"]]
        with open(FOREIGN_SEED_PATH, "w", encoding="utf-8") as f:
            json.dump(foreign, f, indent=2, ensure_ascii=False)
        print("Uuendati station_registry.json ja seed_foreign_stations.json.")


if __name__ == "__main__":
    main()
