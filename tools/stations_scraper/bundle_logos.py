#!/usr/bin/env python3
"""
bundle_logos.py

Genereerib kõik 82 Kellraadio jaamalogo kohalikeks WebP-ikoonideks kausta
`app/src/main/assets/logos/`:
- 15 ERR-i jaama: renderdab ametlikest `eestiraadio.err.ee` SVG radadest (1024->256px LANCZOS)
  üliteravad 256x256 täis-taustaga WebP-märgid; KlaRa ja R2 veebiraadiotel kasutab
  ametlikku K/R sümbolit + paksu selget tüpograafiat (Aino / Roboto Condensed Bold).
- 67 ülejäänud jaama: laeb alla valideeritud originaalpildid, skaleerib max 192x192 px
  ja salvestab WebP-na (`st_<id>.webp`).
- Salvestab `sourceIconUrl` väärtuse ja seab `iconUrl` -> `file:///android_asset/logos/...`.
"""

import io
import json
import os
import re
import urllib.request
from typing import Dict, List, Optional, Tuple
from PIL import Image, ImageChops, ImageDraw, ImageFont

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, "..", ".."))
REGISTRY_PATH = os.path.join(SCRIPT_DIR, "station_registry.json")
FOREIGN_SEED_PATH = os.path.join(SCRIPT_DIR, "seed_foreign_stations.json")
LOGOS_DIR = os.path.join(REPO_ROOT, "app", "src", "main", "assets", "logos")
DRAWABLE_DIR = os.path.join(REPO_ROOT, "app", "src", "main", "res", "drawable")
DIST_DIR = os.path.join(SCRIPT_DIR, "dist")
PREVIEW_HTML_PATH = os.path.join(DIST_DIR, "logo_preview.html")
ERR_LOGOS_CACHE = os.path.join(SCRIPT_DIR, "err_svg_cache.json")

USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/131.0.0.0"

FONT_CONDENSED_BOLD = r"C:\Windows\Fonts\RobotoCondensed-Bold.ttf"
FONT_AINO_BOLD = r"C:\Windows\Fonts\Aino_31_170411-Bold.otf"
FONT_SEGOE_BOLD = r"C:\Windows\Fonts\segoeuib.ttf"

# ERR-i 15 kanalit: (slug, selector, channelTint, shapeTint, isCore60, crispLabel)
ERR_SPECS = [
    ("vikerraadio", "ui-vikerraadio-logo", "#21ACA8", "#FFFFFF", True, None),
    ("raadio_2", "ui-raadio2-logo", "#01A9DD", "#FFFFFF", True, None),
    ("klassikaraadio", "ui-klassikaraadio-logo", "#192E73", "#FFFFFF", True, None),
    ("raadio_tallinn", "ui-raadiotallinn-logo", "#006294", "#FFFFFF", True, None),
    ("klara_klassika", "ui-klaraklassika-logo", "#1A256C", "#38D4F5", False, "KLASSIKA"),
    ("klara_jazz", "ui-klarajazz-logo", "#0D272D", "#F5E8C8", False, "JAZZ"),
    ("klara_nostalgia", "ui-klaranostalgia-logo", "#6C1A4B", "#FFD39E", False, "NOSTALGIA"),
    ("klara_meditatsioon", "ui-klarameditatsioon-logo", "#4E418A", "#FFFFFF", False, "MEDITAT."),
    ("r2_chill", "ui-r2chill-logo", "#101F76", "#FF5C82", False, "CHILL"),
    ("r2_rock", "ui-r2rock-logo", "#101F76", "#FF5C82", False, "ROCK"),
    ("r2_eesti", "ui-r2eesti-logo", "#1B2038", "#FFE2D8", False, "EESTI"),
    ("r2_music", "ui-r2music-logo", "#3E291B", "#C5FFE0", False, "MUSIC"),
    ("r2_rap", "ui-r2p-logo", "#1B2038", "#FFE2D8", False, "RAP"),
    ("r2_pop", "ui-r2pop-logo", "#101F76", "#FF5C82", False, "POP"),
    ("r2_alternatiiv", "ui-r2alternatiiv-logo", "#3E291B", "#C5FFE0", False, "ALT"),
]


def hex_to_rgb(h: str) -> Tuple[int, int, int]:
    h = h.lstrip("#")
    return tuple(int(h[i : i + 2], 16) for i in (0, 2, 4))


def load_err_svg_cache() -> Dict[str, List]:
    if os.path.exists(ERR_LOGOS_CACHE):
        with open(ERR_LOGOS_CACHE, "r", encoding="utf-8") as f:
            return json.load(f)
    # Tõmba otse eestiraadio.err.ee chunkist
    req = urllib.request.Request("https://eestiraadio.err.ee/main-LYKPEYXA.js", headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=10) as r:
        js = r.read().decode("utf-8", "ignore")
    m = re.search(r'E as pn[^;]*from\s*"([^"]+)"', js)
    chunk_url = "https://eestiraadio.err.ee/" + m.group(1).lstrip("./")
    req2 = urllib.request.Request(chunk_url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req2, timeout=10) as r:
        cjs = r.read().decode("utf-8", "ignore")
    logos = {}
    for m2 in re.finditer(r'selectors:\[\["(ui-[a-z0-9-]+-logo)"\]\].*?consts:(\[\[.*?\]\]),template:', cjs):
        try:
            logos[m2.group(1)] = json.loads(m2.group(2))
        except Exception:
            pass
    with open(ERR_LOGOS_CACHE, "w", encoding="utf-8") as f:
        json.dump(logos, f, indent=2)
    return logos


def parse_svg_path_subpaths(d: str) -> List[List[Tuple[float, float]]]:
    """Teisendab SVG path stringi (M, L, H, V, C, Z) suletud polügoonideks."""
    tokens = re.findall(r'[MmLlHhVvCcZz]|-?\d*\.?\d+(?:[eE][+-]?\d+)?', d)
    i = 0
    cmd = ""
    cx, cy = 0.0, 0.0
    sx, sy = 0.0, 0.0
    subpaths: List[List[Tuple[float, float]]] = []
    current: List[Tuple[float, float]] = []

    def is_num(tok: str) -> bool:
        return bool(tok) and tok[0] not in "MmLlHhVvCcZz"

    while i < len(tokens):
        t = tokens[i]
        if not is_num(t):
            cmd = t
            i += 1
            if cmd in ("Z", "z"):
                if current:
                    current.append((sx, sy))
                    subpaths.append(current)
                    current = []
                cx, cy = sx, sy
            continue

        if cmd in ("M", "m"):
            if current:
                subpaths.append(current)
                current = []
            x, y = float(tokens[i]), float(tokens[i + 1])
            i += 2
            if cmd == "m":
                x += cx
                y += cy
            cx, cy = x, y
            sx, sy = x, y
            current.append((cx, cy))
            cmd = "L" if cmd == "M" else "l"
        elif cmd in ("L", "l"):
            x, y = float(tokens[i]), float(tokens[i + 1])
            i += 2
            if cmd == "l":
                x += cx
                y += cy
            cx, cy = x, y
            current.append((cx, cy))
        elif cmd in ("H", "h"):
            x = float(tokens[i])
            i += 1
            cx = x if cmd == "H" else cx + x
            current.append((cx, cy))
        elif cmd in ("V", "v"):
            y = float(tokens[i])
            i += 1
            cy = y if cmd == "V" else cy + y
            current.append((cx, cy))
        elif cmd in ("C", "c"):
            x1, y1 = float(tokens[i]), float(tokens[i + 1])
            x2, y2 = float(tokens[i + 2]), float(tokens[i + 3])
            x3, y3 = float(tokens[i + 4]), float(tokens[i + 5])
            i += 6
            if cmd == "c":
                x1 += cx; y1 += cy
                x2 += cx; y2 += cy
                x3 += cx; y3 += cy
            p0 = (cx, cy)
            steps = 24
            for k in range(1, steps + 1):
                u = k / steps
                mu = 1.0 - u
                px = (mu**3)*p0[0] + 3*(mu**2)*u*x1 + 3*mu*(u**2)*x2 + (u**3)*x3
                py = (mu**3)*p0[1] + 3*(mu**2)*u*y1 + 3*mu*(u**2)*y2 + (u**3)*y3
                current.append((px, py))
            cx, cy = x3, y3
        else:
            i += 1

    if current:
        subpaths.append(current)
    return subpaths


def rasterize_evenodd_mask(subpaths: List[List[Tuple[float, float]]], size: int, scale: float, ox: float, oy: float) -> Image.Image:
    mask = Image.new("L", (size, size), 0)
    for sp in subpaths:
        if len(sp) < 3:
            continue
        pts = [(x * scale + ox, y * scale + oy) for (x, y) in sp]
        layer = Image.new("L", (size, size), 0)
        ImageDraw.Draw(layer).polygon(pts, fill=255)
        mask = ImageChops.difference(mask, layer)
    return mask


def pick_font(size: int, condensed: bool = False) -> ImageFont.FreeTypeFont:
    candidates = [FONT_CONDENSED_BOLD, FONT_AINO_BOLD, FONT_SEGOE_BOLD] if condensed else [FONT_AINO_BOLD, FONT_SEGOE_BOLD, FONT_CONDENSED_BOLD]
    for path in candidates:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def render_err_badge(slug: str, sel: str, bg_hex: str, fg_hex: str, is_core: bool, label: Optional[str], err_logos: Dict[str, List], out_path: str):
    hi = 1024
    bg_rgb = hex_to_rgb(bg_hex)
    fg_rgb = hex_to_rgb(fg_hex)
    items = err_logos[sel]

    if is_core:
        # Põhikanalid: taust on valge (et väljalõige oleks valge), selle peal channelTint evenOdd mask!
        img = Image.new("RGBA", (hi, hi), (255, 255, 255, 255))
        # Täida välisnurgad taustavärviga + keskel valge ring (et läbi lõike paistaks valge)
        img_bg = Image.new("RGBA", (hi, hi), (*bg_rgb, 255))
        draw_bg = ImageDraw.Draw(img_bg)
        pad = 36
        draw_bg.ellipse([pad, pad, hi - pad, hi - pad], fill=(255, 255, 255, 255))
        d = ""
        for arr in items[1:]:
            kv = {arr[i]: arr[i + 1] for i in range(0, len(arr), 2)}
            if "d" in kv:
                d = kv["d"]
                break
        subpaths = parse_svg_path_subpaths(d)
        mask = rasterize_evenodd_mask(subpaths, hi, scale=hi / 60.0, ox=0.0, oy=0.0)
        fg_layer = Image.new("RGBA", (hi, hi), (*bg_rgb, 255))
        img_bg.paste(fg_layer, (0, 0), mask)
        img = img_bg
    else:
        # Veebikanalid (KlaRa & R2): täis-taust + SUUR ametlik sümbol üleval + paks terav tekst all!
        img = Image.new("RGBA", (hi, hi), (*bg_rgb, 255))
        # Võta ainult path 1 (ametlik K-laine või R sümbol, ilma tillukese mikrotekstita)
        symbol_d = ""
        has_tx = False
        for arr in items[1:]:
            kv = {arr[i]: arr[i + 1] for i in range(0, len(arr), 2)}
            if "transform" in kv and "translate(-1 0)" in kv["transform"]:
                has_tx = True
            if "d" in kv:
                symbol_d = kv["d"]
                break

        subpaths = parse_svg_path_subpaths(symbol_d)
        # Arvuta sümboli täpne piirdekast, et see ilusti keskele ja suuremaks paigutada
        all_pts = [pt for sp in subpaths for pt in sp]
        min_x = min(p[0] for p in all_pts)
        max_x = max(p[0] for p in all_pts)
        min_y = min(p[1] for p in all_pts)
        max_y = max(p[1] for p in all_pts)
        sym_w = max_x - min_x
        sym_h = max_y - min_y

        target_h = 440.0
        scale = target_h / sym_h
        ox = (hi - sym_w * scale) / 2.0 - min_x * scale
        oy = 115.0 - min_y * scale

        mask = rasterize_evenodd_mask(subpaths, hi, scale=scale, ox=ox, oy=oy)
        fg_layer = Image.new("RGBA", (hi, hi), (*fg_rgb, 255))
        img.paste(fg_layer, (0, 0), mask)

        # Joonista alla paks terav silt
        if label:
            draw = ImageDraw.Draw(img)
            use_condensed = len(label) >= 7
            font_size = 240 if len(label) <= 4 else (215 if len(label) <= 5 else 185)
            font = pick_font(font_size, condensed=use_condensed)
            bbox = draw.textbbox((0, 0), label, font=font)
            tw = bbox[2] - bbox[0]
            while tw > 880 and font_size > 80:
                font_size -= 8
                font = pick_font(font_size, condensed=use_condensed)
                bbox = draw.textbbox((0, 0), label, font=font)
                tw = bbox[2] - bbox[0]
            th = bbox[3] - bbox[1]
            tx = (hi - tw) / 2.0 - bbox[0]
            # Joonda vertikaalselt alumisse kolmandikku
            ty = 630.0 + (260.0 - th) / 2.0 - bbox[1]
            draw.text((tx, ty), label, font=font, fill=(*fg_rgb, 255))

    out_img = img.resize((256, 256), Image.Resampling.LANCZOS)
    out_img.save(out_path, format="WEBP", quality=92, method=6)


def _normalize_remote_image(im: Image.Image, needs_light_bg: bool = False) -> Image.Image:
    """Tagab, et laiadele ristkülikutele ja tumeda tekstiga läbipaistvatele logodele
    (nt Kuku) küpsetatakse puhas ruut-taust, samas kui tavalised värvilised/ruudukujulised
    logod jäävad puhtaks ilma halli raamita."""
    im = im.convert("RGBA")
    max_side = 192
    w, h = im.size
    ratio = max(w / h, h / w)

    if needs_light_bg:
        canvas = Image.new("RGBA", (max_side, max_side), (248, 250, 252, 255))
        pad = 14
        im_fit = im.copy()
        im_fit.thumbnail((max_side - 2 * pad, max_side - 2 * pad), Image.Resampling.LANCZOS)
        ox = (max_side - im_fit.width) // 2
        oy = (max_side - im_fit.height) // 2
        canvas.paste(im_fit, (ox, oy), im_fit)
        return canvas

    if ratio > 1.25:
        # Lai logo (nt BR24, Big FM, XFM): täida ruut logo enda nurgavärviga
        corners = [
            im.getpixel((0, 0)),
            im.getpixel((w - 1, 0)),
            im.getpixel((0, h - 1)),
            im.getpixel((w - 1, h - 1)),
        ]
        opaque_corners = [c for c in corners if c[3] >= 230]
        bg_col = opaque_corners[0] if opaque_corners else (248, 250, 252, 255)
        canvas = Image.new("RGBA", (max_side, max_side), bg_col)
        im_fit = im.copy()
        im_fit.thumbnail((max_side - 16, max_side - 16), Image.Resampling.LANCZOS)
        ox = (max_side - im_fit.width) // 2
        oy = (max_side - im_fit.height) // 2
        canvas.paste(im_fit, (ox, oy), im_fit)
        return canvas

    if w > max_side or h > max_side:
        im.thumbnail((max_side, max_side), Image.Resampling.LANCZOS)
    return im


def bundle_remote_logo(url: str, out_path: str, needs_light_bg: bool = False) -> Tuple[bool, str]:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=12) as resp:
        data = resp.read(3 * 1024 * 1024)
    im = Image.open(io.BytesIO(data))
    im = _normalize_remote_image(im, needs_light_bg=needs_light_bg)
    im.save(out_path, format="WEBP", quality=90, method=6)
    return True, f"{im.width}x{im.height}"


def render_preview_html(entries: List[Dict]) -> str:
    cards = []
    for e in entries:
        name_esc = e["name"]
        cat_esc = f"{e['countrycode']} • {e['category']}"
        rel_img = "../../../app/src/main/assets/logos/" + e["filename"]
        cards.append(f"""
        <div class="card ok">
          <div class="chip">
            <img src="{rel_img}" alt="{name_esc}" />
          </div>
          <div class="info">
            <div class="title">{name_esc}</div>
            <div class="sub">{cat_esc}</div>
            <div class="meta">{e['meta']}</div>
            <div class="url">{e['iconUrl']}</div>
          </div>
        </div>""")

    return f"""<!DOCTYPE html>
<html lang="et">
<head>
<meta charset="utf-8" />
<title>Kellraadio — Pakitud logode eelvaade ({len(entries)}/{len(entries)})</title>
<style>
  body {{ background: #0A0C10; color: #F8FAFC; font-family: system-ui, sans-serif; margin: 24px; }}
  h1 {{ color: #FFA726; margin-bottom: 4px; }}
  p.summary {{ color: #94A3B8; margin-top: 0; }}
  .grid {{ display: grid; grid-template-columns: repeat(auto-fill, minmax(360px, 1fr)); gap: 12px; margin-top: 20px; }}
  .card {{ background: #181B26; border: 1px solid #242938; border-radius: 10px; padding: 10px 12px; display: flex; align-items: center; gap: 12px; }}
  .chip {{ width: 44px; height: 44px; border-radius: 8px; flex-shrink: 0; display: flex; align-items: center; justify-content: center; overflow: hidden; background: transparent; padding: 0; border: none; }}
  .chip img {{ width: 100%; height: 100%; object-fit: contain; display: block; }}
  .info {{ min-width: 0; flex: 1; }}
  .title {{ font-weight: 600; font-size: 15px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }}
  .sub {{ font-size: 11px; color: #94A3B8; }}
  .meta {{ font-size: 11px; color: #FFB74D; margin-top: 2px; }}
  .url {{ font-size: 10px; color: #64748B; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }}
</style>
</head>
<body>
  <h1>Kellraadio — Pakitud WebP logode eelvaade (ilma halli raamita)</h1>
  <p class="summary">Kõik {len(entries)} jaama logo on pakitud otse äpi `assets/logos/` kausta (100% offline, ilma halli äärejooneta).</p>
  <div class="grid">
    {''.join(cards)}
  </div>
</body>
</html>"""


def main():
    os.makedirs(LOGOS_DIR, exist_ok=True)
    os.makedirs(DIST_DIR, exist_ok=True)

    err_logos = load_err_svg_cache()
    err_by_slug = {spec[0]: spec for spec in ERR_SPECS}

    with open(REGISTRY_PATH, "r", encoding="utf-8") as f:
        registry = json.load(f)
    with open(FOREIGN_SEED_PATH, "r", encoding="utf-8") as f:
        foreign = json.load(f)

    preview_entries = []
    total_bytes = 0

    # 1. Eesti jaamad
    for slug, meta in registry.items():
        if slug in err_by_slug:
            _, sel, bg, fg, is_core, label = err_by_slug[slug]
            fname = f"err_{slug}.webp"
            out_path = os.path.join(LOGOS_DIR, fname)
            render_err_badge(slug, sel, bg, fg, is_core, label, err_logos, out_path)
            sz = os.path.getsize(out_path)
            total_bytes += sz
            asset_uri = f"file:///android_asset/logos/{fname}"
            meta["iconUrl"] = asset_uri
            preview_entries.append({
                "id": meta["id"],
                "priority": meta["priority"],
                "name": meta["name"],
                "category": meta["category"],
                "countrycode": meta["countrycode"],
                "filename": fname,
                "is_err": True,
                "meta": f"ERR WebP 256x256 • {sz // 1024} KB",
                "iconUrl": asset_uri,
            })
        else:
            src_url = meta.get("sourceIconUrl") or meta.get("iconUrl", "")
            if src_url.startswith("http"):
                meta["sourceIconUrl"] = src_url
            fname = f"st_{meta['id']}_{slug}.webp"
            out_path = os.path.join(LOGOS_DIR, fname)
            ok, dims = bundle_remote_logo(meta["sourceIconUrl"], out_path, needs_light_bg=(slug == "kuku"))
            sz = os.path.getsize(out_path)
            total_bytes += sz
            asset_uri = f"file:///android_asset/logos/{fname}"
            meta["iconUrl"] = asset_uri
            preview_entries.append({
                "id": meta["id"],
                "priority": meta["priority"],
                "name": meta["name"],
                "category": meta["category"],
                "countrycode": meta["countrycode"],
                "filename": fname,
                "is_err": False,
                "meta": f"WebP {dims} • {sz / 1024:.1f} KB",
                "iconUrl": asset_uri,
            })

    # 2. Välisjaamad
    for st in foreign:
        src_url = st.get("sourceIconUrl") or st.get("iconUrl", "")
        if src_url.startswith("http"):
            st["sourceIconUrl"] = src_url
        fname = f"st_{st['id']}.webp"
        out_path = os.path.join(LOGOS_DIR, fname)
        ok, dims = bundle_remote_logo(st["sourceIconUrl"], out_path, needs_light_bg=False)
        sz = os.path.getsize(out_path)
        total_bytes += sz
        asset_uri = f"file:///android_asset/logos/{fname}"
        st["iconUrl"] = asset_uri
        preview_entries.append({
            "id": st["id"],
            "priority": st.get("priority", 9999),
            "name": st["name"],
            "category": st.get("category", ""),
            "countrycode": st.get("countrycode", ""),
            "filename": fname,
            "is_err": False,
            "meta": f"WebP {dims} • {sz / 1024:.1f} KB",
            "iconUrl": asset_uri,
        })

    preview_entries.sort(key=lambda x: (x["priority"], x["id"]))

    with open(REGISTRY_PATH, "w", encoding="utf-8") as f:
        json.dump(registry, f, indent=2, ensure_ascii=False)
    with open(FOREIGN_SEED_PATH, "w", encoding="utf-8") as f:
        json.dump(foreign, f, indent=2, ensure_ascii=False)
    with open(PREVIEW_HTML_PATH, "w", encoding="utf-8") as f:
        f.write(render_preview_html(preview_entries))

    print(f"Pakitud {len(preview_entries)} logo kausta {LOGOS_DIR} (kokku {total_bytes / 1024:.1f} KB / {total_bytes / (1024*1024):.2f} MB)")
    print(f"Eelvaade: {PREVIEW_HTML_PATH}")


if __name__ == "__main__":
    main()
