#!/usr/bin/env python3
"""Pembuat aset Susun Kata (Hanz Dev Official): latar, ikon, suara. Semua dibuat prosedural (tanpa lisensi pihak ketiga)."""
import os, math, wave, subprocess, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "app", "src", "main", "res")
W, H = 720, 1280
FONT = os.path.join(HERE, "..", "app", "src", "main", "assets", "fonts", "Poppins-Bold.ttf")

def lerp(a, b, t): return a + (b - a) * t

def vgrad(top, bottom, h=H, w=W):
    t = np.linspace(0, 1, h)[:, None, None]
    a = np.array(top, float)[None, None, :]; b = np.array(bottom, float)[None, None, :]
    return np.repeat(a + (b - a) * t, w, axis=1)

def fbm_1d(n, rng, octaves=5, base=3, persistence=0.5):
    x = np.zeros(n); amp = 1.0; tot = 0
    for o in range(octaves):
        k = base * (2 ** o)
        pts = rng.random(k + 2)
        xs = np.linspace(0, k, n)
        i = np.floor(xs).astype(int); f = xs - i
        f = f * f * (3 - 2 * f)
        x += (pts[i] * (1 - f) + pts[i + 1] * f) * amp
        tot += amp; amp *= persistence
    return x / tot

def ridge(img, base_y, amp, color, rng, base=3, haze=None, hazea=0.0, octaves=5):
    arr = img
    prof = fbm_1d(W, rng, octaves=octaves, base=base)
    ys = (base_y - prof * amp).astype(int)
    yy = np.arange(H)[:, None]
    mask = yy >= ys[None, :]
    col = np.array(color, float)
    grad = np.clip((yy - ys[None, :]) / 420.0, 0, 1)[:, :, None]
    layer = col[None, None, :] * (1 - 0.30 * grad)
    if haze is not None:
        layer = layer * (1 - hazea) + np.array(haze, float)[None, None, :] * hazea
    arr[:] = np.where(mask[:, :, None], layer, arr)
    return ys

def clouds(img, rng, n, y0, y1, alpha=0.85, tint=(255, 255, 255)):
    layer = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(layer)
    for _ in range(n):
        cx = rng.uniform(-80, W + 80); cy = rng.uniform(y0, y1)
        for _ in range(rng.integers(5, 9)):
            rx = rng.uniform(60, 150); ry = rng.uniform(22, 46)
            ox = rng.uniform(-110, 110); oy = rng.uniform(-14, 14)
            d.ellipse([cx + ox - rx, cy + oy - ry, cx + ox + rx, cy + oy + ry], fill=int(255 * alpha))
    layer = layer.filter(ImageFilter.GaussianBlur(22))
    m = np.asarray(layer, float)[:, :, None] / 255.0
    img[:] = img * (1 - m) + np.array(tint, float)[None, None, :] * m

def sun(img, x, y, r, color, glow=260):
    yy, xx = np.mgrid[0:H, 0:W]
    d = np.sqrt((xx - x) ** 2 + (yy - y) ** 2)
    g = np.clip(1 - d / glow, 0, 1) ** 2.2
    img[:] = img + g[:, :, None] * np.array(color, float)[None, None, :] * 0.55
    core = np.clip((r - d) / 3.0 + 0.5, 0, 1)[:, :, None]
    img[:] = img * (1 - core) + np.array((255, 250, 235), float)[None, None, :] * core

def grain(img, rng, s=3.0):
    img[:] = img + rng.normal(0, s, img.shape[:2])[:, :, None]

def terraces(img, rng, top_y):
    # sawah terasering: pita hijau mengikuti kontur
    yy = np.arange(H)[:, None]
    for i in range(26):
        y = top_y + (i ** 1.35) * 17
        wob = fbm_1d(W, rng, 4, 2) * (30 + i * 3)
        ys = (y + wob).astype(int)
        shade = i / 26.0
        base = np.array(lerp(np.array((110, 190, 60)), np.array((150, 214, 40)), (i % 2) * 0.6 + shade * 0.3), float)
        mask = yy >= ys[None, :]
        grad = np.clip((yy - ys[None, :]) / (22 + i * 2.2), 0, 1)[:, :, None]
        layer = base[None, None, :] * (0.70 + 0.30 * grad) * (0.82 + 0.28 * shade)
        # tepi pematang terang
        edge = (np.abs(yy - ys[None, :]) < 2 + i * 0.2)[:, :, None]
        layer = np.where(edge, np.array((205, 230, 120), float)[None, None, :] * (0.75 + shade * 0.25), layer)
        img[:] = np.where(mask[:, :, None], layer, img)

def trees(img, rng, y_min, y_max, n, dark, light):
    layer = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(layer)
    for _ in range(n):
        x = rng.uniform(-20, W + 20); y = rng.uniform(y_min, y_max)
        s = 14 + (y - y_min) / (y_max - y_min) * 46
        c = tuple(int(lerp(dark[k], light[k], rng.random())) for k in range(3))
        d.ellipse([x - s, y - s * 0.9, x + s, y + s * 0.7], fill=c)
        d.ellipse([x - s * 0.55, y - s * 1.15, x + s * 0.55, y - s * 0.1], fill=tuple(min(255, int(v * 1.15)) for v in c))
    img[:] = np.asarray(layer, float)

def sea(img, rng, y0, top_col, bot_col, sun_x=None):
    yy = np.arange(H)[:, None, None]
    t = np.clip((yy - y0) / (H - y0), 0, 1)
    col = np.array(top_col, float) + (np.array(bot_col, float) - np.array(top_col, float)) * t
    col = np.repeat(col, W, axis=1)
    xx = np.arange(W)[None, :, None]
    wave_ = np.sin(xx * 0.05 + yy * 0.25 + np.sin(yy * 0.04) * 4) * 0.5 + 0.5
    col = col * (0.92 + 0.14 * wave_ * (0.3 + t))
    if sun_x is not None:
        refl = np.exp(-((xx - sun_x) ** 2) / (2 * (30 + 90 * t) ** 2)) * (1 - t) * 0.8
        col = col + refl * np.array((255, 190, 90), float) * (0.5 + 0.5 * wave_)
    mask = (yy >= y0)
    img[:] = np.where(mask, col, img)

def palms(draw_img, rng, x, base_y, h, lean, col=(20, 40, 30)):
    d = ImageDraw.Draw(draw_img)
    pts = [(x, base_y)]
    for i in range(1, 9):
        t = i / 8
        pts.append((x + lean * (t ** 1.6) * h, base_y - t * h))
    for i in range(len(pts) - 1):
        d.line([pts[i], pts[i + 1]], fill=col, width=int(14 - i))
    tx, ty = pts[-1]
    for k in range(9):
        ang = math.radians(-160 + k * 40 + rng.uniform(-8, 8))
        L = rng.uniform(95, 140)
        mx, my = tx + math.cos(ang) * L * 0.55, ty + math.sin(ang) * L * 0.45 - 12
        ex, ey = tx + math.cos(ang) * L, ty + math.sin(ang) * L * 0.55 + 38
        d.line([(tx, ty), (mx, my), (ex, ey)], fill=col, width=7, joint="curve")

def stars(img, rng, n, ymax):
    for _ in range(n):
        x = int(rng.integers(0, W)); y = int(rng.integers(0, ymax))
        b = rng.uniform(150, 255)
        img[y, x] = np.array((b, b, b * 0.95))
        if rng.random() < 0.15 and 1 <= x < W - 1 and 1 <= y < H - 1:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                img[y + dy, x + dx] = np.maximum(img[y + dy, x + dx], b * 0.5)

def finish(img, name, seed):
    rng = np.random.default_rng(seed)
    grain(img, rng, 2.2)
    # vignette halus agar teks UI terbaca
    yy, xx = np.mgrid[0:H, 0:W]
    v = 1 - 0.18 * (((xx - W / 2) / (W / 2)) ** 2 + ((yy - H / 2) / (H / 2)) ** 2) ** 0.8
    img[:] = img * v[:, :, None]
    out = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
    os.makedirs(os.path.join(RES, "drawable-nodpi"), exist_ok=True)
    out.save(os.path.join(RES, "drawable-nodpi", name + ".jpg"), quality=84, optimize=True, progressive=True)
    return out

def bg_terasering():
    rng = np.random.default_rng(1)
    img = vgrad((92, 150, 214), (214, 232, 238))
    sun(img, 560, 330, 30, (255, 240, 190), 360)
    clouds(img, rng, 7, 120, 430, 0.9)
    ridge(img, 560, 170, (96, 128, 150), rng, 2, haze=(200, 220, 235), hazea=0.55)
    ridge(img, 620, 150, (66, 120, 98), rng, 3, haze=(190, 215, 220), hazea=0.35)
    ridge(img, 700, 110, (44, 110, 62), rng, 4, haze=(160, 200, 170), hazea=0.15)
    terraces(img, rng, 700)
    trees(img, rng, 760, 1180, 70, (22, 80, 36), (70, 150, 60))
    return finish(img, "bg_terasering", 11)

def bg_pantai():
    rng = np.random.default_rng(2)
    img = vgrad((255, 140, 90), (255, 214, 140))
    img = vgrad((70, 60, 130), (255, 170, 100))
    sun(img, 360, 600, 54, (255, 190, 100), 480)
    clouds(img, rng, 6, 160, 480, 0.55, (255, 200, 170))
    sea(img, rng, 640, (255, 170, 110), (30, 80, 120), sun_x=360)
    # pasir
    yy = np.arange(H)[:, None]
    shore = (990 + fbm_1d(W, rng, 3, 2) * 90).astype(int)
    mask = yy >= shore[None, :]
    sand = np.array((236, 200, 150), float)[None, None, :] * (0.86 + 0.14 * np.clip((yy - shore[None, :]) / 300, 0, 1)[:, :, None])
    foam = (np.abs(yy - shore[None, :]) < 7)[:, :, None]
    img[:] = np.where(mask[:, :, None], sand, img)
    img[:] = np.where(foam, np.array((255, 245, 235), float), img)
    layer = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
    palms(layer, rng, 90, 1240, 520, 0.35); palms(layer, rng, 640, 1250, 430, -0.30)
    img = np.asarray(layer, float)
    return finish(img, "bg_pantai", 12)

def bg_gunung():
    rng = np.random.default_rng(3)
    img = vgrad((255, 196, 150), (140, 190, 235))
    img = vgrad((120, 160, 225), (255, 215, 175))
    sun(img, 150, 520, 34, (255, 220, 160), 380)
    clouds(img, rng, 8, 100, 520, 0.8, (255, 240, 230))
    # gunung berapi kerucut
    layer = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)); d = ImageDraw.Draw(layer)
    d.polygon([(40, 900), (310, 360), (350, 345), (400, 350), (440, 365), (720, 900)], fill=(88, 96, 130))
    layer = layer.filter(ImageFilter.GaussianBlur(1.2)); img = np.asarray(layer, float)
    ridge(img, 820, 120, (84, 120, 120), rng, 3, haze=(210, 200, 215), hazea=0.35)
    ridge(img, 900, 110, (52, 110, 78), rng, 4, haze=(170, 205, 190), hazea=0.15)
    ridge(img, 1000, 110, (36, 96, 52), rng, 5)
    trees(img, rng, 1010, 1260, 90, (18, 70, 32), (60, 140, 50))
    return finish(img, "bg_gunung", 13)

def bg_hutan():
    rng = np.random.default_rng(4)
    img = vgrad((160, 215, 190), (230, 245, 205))
    sun(img, 420, 180, 28, (255, 255, 210), 420)
    clouds(img, rng, 4, 80, 300, 0.5)
    ridge(img, 520, 150, (80, 140, 120), rng, 3, haze=(200, 230, 215), hazea=0.5)
    ridge(img, 640, 140, (46, 120, 80), rng, 4, haze=(170, 215, 190), hazea=0.3)
    ridge(img, 760, 130, (28, 96, 56), rng, 5, haze=(120, 180, 140), hazea=0.15)
    trees(img, rng, 700, 1260, 260, (14, 66, 32), (58, 142, 54))
    # sinar matahari menembus
    ray = Image.new("L", (W, H), 0); d = ImageDraw.Draw(ray)
    for k in range(5):
        x0 = 330 + k * 40
        d.polygon([(x0, 0), (x0 + 36, 0), (x0 - 220 + k * 90, H), (x0 - 330 + k * 90, H)], fill=40)
    ray = ray.filter(ImageFilter.GaussianBlur(26)); m = np.asarray(ray, float)[:, :, None] / 255
    img = img + m * np.array((255, 250, 200), float) * 0.5
    return finish(img, "bg_hutan", 14)

def bg_danau():
    rng = np.random.default_rng(5)
    img = vgrad((250, 120, 120), (255, 205, 150))
    img = vgrad((92, 80, 160), (255, 160, 120))
    sun(img, 360, 520, 46, (255, 200, 140), 430)
    clouds(img, rng, 6, 140, 440, 0.6, (255, 190, 190))
    ridge(img, 600, 190, (86, 70, 120), rng, 3, haze=(230, 150, 150), hazea=0.5)
    ridge(img, 680, 130, (60, 60, 96), rng, 4, haze=(200, 130, 140), hazea=0.3)
    sea(img, rng, 700, (255, 170, 130), (42, 70, 120), sun_x=360)
    # tepi hutan gelap di depan
    ridge(img, 1180, 120, (18, 38, 36), rng, 6)
    return finish(img, "bg_danau", 15)

def bg_malam():
    rng = np.random.default_rng(6)
    img = vgrad((10, 14, 48), (56, 62, 120))
    stars(img, rng, 520, 760)
    # bulan
    yy, xx = np.mgrid[0:H, 0:W]
    d = np.sqrt((xx - 520) ** 2 + (yy - 230) ** 2)
    img[:] = img + (np.clip(1 - d / 300, 0, 1) ** 2)[:, :, None] * np.array((150, 170, 255), float) * 0.45
    core = np.clip((46 - d) / 3 + 0.5, 0, 1)[:, :, None]
    img[:] = img * (1 - core) + np.array((250, 248, 225), float) * core
    clouds(img, rng, 3, 300, 700, 0.18, (170, 180, 230))
    ridge(img, 760, 160, (36, 46, 96), rng, 3, haze=(60, 70, 130), hazea=0.4)
    ridge(img, 880, 150, (22, 36, 70), rng, 4, haze=(40, 56, 100), hazea=0.25)
    ridge(img, 1020, 140, (12, 26, 46), rng, 5)
    # kunang-kunang
    layer = Image.new("L", (W, H), 0); dd = ImageDraw.Draw(layer)
    for _ in range(40):
        x = rng.uniform(0, W); y = rng.uniform(780, 1240); r = rng.uniform(2, 5)
        dd.ellipse([x - r, y - r, x + r, y + r], fill=255)
    layer = layer.filter(ImageFilter.GaussianBlur(4)); m = np.asarray(layer, float)[:, :, None] / 255
    img = img + m * np.array((230, 255, 150), float) * 1.3
    return finish(img, "bg_malam", 16)

def icon_master(size=1024):
    return Image.new("RGBA", (size, size), (0x1F, 0x7A, 0x3A, 255))

def draw_tiles(base, scale=1.0):
    """Ubin KATA 2x2 bertepi tebal (motif DESIGN.md): krem + satu ubin emas sebagai aksen."""
    s = base.size[0]
    d = ImageDraw.Draw(base)
    f = ImageFont.truetype(FONT, int(s * 0.25 * scale))
    tile = int(s * 0.36 * scale); gap = int(s * 0.05 * scale)
    edge = int(tile * 0.07)
    letters = [("K", 0, 0), ("A", 1, 0), ("T", 0, 1), ("A", 1, 1)]
    total = tile * 2 + gap
    ox = (s - total) // 2; oy = (s - total) // 2 - edge
    for ch, cx, cy in letters:
        x = ox + cx * (tile + gap); y = oy + cy * (tile + gap)
        gold = (cx, cy) == (1, 1)
        face = (0xF2, 0xB7, 0x05, 255) if gold else (0xF5, 0xF0, 0xE1, 255)
        edge_c = (0x8A, 0x65, 0x00, 255) if gold else (0xB9, 0xAF, 0x92, 255)
        r = int(tile * 0.14)
        d.rounded_rectangle([x, y + edge, x + tile, y + tile + edge], r, fill=edge_c)
        d.rounded_rectangle([x, y, x + tile, y + tile], r, fill=face)
        bb = d.textbbox((0, 0), ch, font=f)
        d.text((x + tile / 2 - (bb[0] + bb[2]) / 2, y + tile / 2 - (bb[1] + bb[3]) / 2), ch, font=f, fill=(0x14, 0x21, 0x1A, 255))
    return base

def make_icons():
    master = draw_tiles(icon_master(1024))
    sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for dens, px in sizes.items():
        d = os.path.join(RES, "mipmap-" + dens); os.makedirs(d, exist_ok=True)
        sq = master.resize((px, px), Image.LANCZOS)
        mask = Image.new("L", (px, px), 0); ImageDraw.Draw(mask).rounded_rectangle([0, 0, px - 1, px - 1], int(px * 0.22), fill=255)
        out = Image.new("RGBA", (px, px), (0, 0, 0, 0)); out.paste(sq, (0, 0), mask)
        out.save(os.path.join(d, "ic_launcher.png"), optimize=True)
        circ = Image.new("L", (px, px), 0); ImageDraw.Draw(circ).ellipse([0, 0, px - 1, px - 1], fill=255)
        out2 = Image.new("RGBA", (px, px), (0, 0, 0, 0)); out2.paste(sq, (0, 0), circ)
        out2.save(os.path.join(d, "ic_launcher_round.png"), optimize=True)
    # adaptif: latar polos + ubin di area aman (66%)
    fg = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    inner = draw_tiles(Image.new("RGBA", (1024, 1024), (0, 0, 0, 0)), scale=0.62).resize((432, 432), Image.LANCZOS)
    fg.alpha_composite(inner)
    fg.save(os.path.join(RES, "drawable-nodpi", "ic_launcher_foreground.png"), optimize=True)
    master.resize((512, 512), Image.LANCZOS).convert("RGB").save(os.path.join(HERE, "..", "ikon_playstore_512.png"), optimize=True)

SR = 22050

def env(n, a=0.004, d=0.0, curve=6.0):
    t = np.arange(n) / SR
    e = np.exp(-curve * t / max(n / SR, 1e-4))
    att = np.minimum(t / a, 1.0)
    return e * att

def tone(freq, dur, harm=(1.0, 0.35, 0.12), curve=5.0, vib=0.0):
    n = int(SR * dur); t = np.arange(n) / SR
    x = np.zeros(n)
    for k, a in enumerate(harm, 1):
        x += a * np.sin(2 * np.pi * freq * k * t + (np.sin(2 * np.pi * 5 * t) * vib if vib else 0))
    return x * env(n, curve=curve)

def mix(parts, total):
    out = np.zeros(int(SR * total))
    for start, sig, g in parts:
        i = int(SR * start); out[i:i + len(sig)] += sig[:len(out) - i] * g
    return out

def save_wav(name, x, peak=0.8):
    x = x / (np.max(np.abs(x)) + 1e-9) * peak
    fade = int(SR * 0.004); x[-fade:] *= np.linspace(1, 0, fade)
    d = os.path.join(RES, "raw"); os.makedirs(d, exist_ok=True)
    with wave.open(os.path.join(d, name + ".wav"), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((x * 32767).astype("<i2").tobytes())

def midi(n): return 440 * 2 ** ((n - 69) / 12)

def make_sounds():
    rng = np.random.default_rng(7)
    save_wav("tick", tone(880, 0.07, (1, 0.2), curve=9), 0.55)
    save_wav("klik", tone(520, 0.05, (1, 0.5, 0.2), curve=10), 0.5)
    save_wav("pop", mix([(0, tone(midi(72), 0.28, curve=6), 1), (0.07, tone(midi(76), 0.34, curve=6), 0.9)], 0.45), 0.8)
    wr = np.concatenate([tone(190, 0.12, (1, 0.8, 0.5), curve=4), tone(140, 0.2, (1, 0.8, 0.5), curve=5)])
    save_wav("salah", wr, 0.6)
    save_wav("bonus", mix([(0, tone(midi(79), 0.22, curve=7), 1), (0.08, tone(midi(83), 0.22, curve=7), 0.9), (0.16, tone(midi(88), 0.4, curve=6), 0.8)], 0.6), 0.7)
    save_wav("koin", mix([(0, tone(midi(83), 0.1, (1, .3), curve=8), 1), (0.07, tone(midi(88), 0.3, (1, .3), curve=6), 1)], 0.45), 0.7)
    n = int(SR * 0.5); t = np.arange(n) / SR
    sweep = np.sin(2 * np.pi * (500 * t + 900 * t * t)) * env(n, curve=4)
    save_wav("petunjuk", sweep + 0.4 * np.sin(2 * np.pi * (1000 * t + 1800 * t * t)) * env(n, curve=5), 0.55)
    save_wav("kocok", (rng.normal(0, 1, int(SR * 0.28)) * np.hanning(int(SR * 0.28)) * np.sin(np.linspace(0, 8 * math.pi, int(SR * 0.28)))), 0.45)
    notes = [60, 64, 67, 72, 76, 79, 84]
    parts = [(i * 0.11, tone(midi(n_), 0.7, (1, .4, .15), curve=5), 1.0 - i * 0.05) for i, n_ in enumerate(notes)]
    parts.append((0.8, tone(midi(72), 1.1, (1, .5, .2), curve=3), 0.8)); parts.append((0.8, tone(midi(79), 1.1, (1, .5, .2), curve=3), 0.7)); parts.append((0.8, tone(midi(84), 1.1, (1, .5, .2), curve=3), 0.6))
    save_wav("menang", mix(parts, 2.1), 0.8)

def make_music():
    bpm = 84; beat = 60 / bpm; bars = 8
    total = bars * 4 * beat
    n = int(SR * total)
    out = np.zeros(n)
    rng = np.random.default_rng(9)
    # progresi C - Am - F - G (dua kali), pentatonik
    chords = [[48, 55, 64], [45, 52, 60], [41, 48, 57], [43, 50, 59]] * 2
    for b, ch in enumerate(chords):
        start = b * 4 * beat
        for note in ch:
            dur = 4 * beat
            m = int(SR * dur); t = np.arange(m) / SR
            sig = (np.sin(2 * np.pi * midi(note) * t) + 0.3 * np.sin(2 * np.pi * midi(note) * 2 * t)) * np.sin(np.pi * t / dur) ** 1.5
            i = int(SR * start); j = min(n, i + m)
            out[i:j] += sig[:j - i] * 0.16
    scale = [72, 74, 76, 79, 81, 84, 79, 76]
    pos = 0.0
    seq = [0, 2, 4, 3, 2, 1, 2, 4, 5, 4, 2, 3, 1, 2, 0, 1] * 2
    for k, idx in enumerate(seq):
        if k % 3 == 2 and rng.random() < 0.5: pos += beat; continue
        note = scale[idx % len(scale)] + (0 if k % 8 else 0)
        sig = tone(midi(note), 1.2, (1, .35, .1), curve=4.5)
        i = int(SR * pos)
        if i >= n: break
        j = i + len(sig)
        if j <= n: out[i:j] += sig * 0.30
        else:  # menyambung ke awal agar loop mulus
            out[i:] += sig[:n - i] * 0.30; out[:j - n] += sig[n - i:] * 0.30
        pos += beat
    out = out / (np.max(np.abs(out)) + 1e-9) * 0.7
    d = os.path.join(RES, "raw"); os.makedirs(d, exist_ok=True)
    tmp = os.path.join(HERE, "_musik.wav")
    with wave.open(tmp, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes((out * 32767).astype("<i2").tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp, "-c:a", "libvorbis", "-q:a", "2", os.path.join(d, "musik.ogg")], check=True)
    os.remove(tmp)

if __name__ == "__main__":
    for f in (bg_terasering, bg_pantai, bg_gunung, bg_hutan, bg_danau, bg_malam):
        f(); print("latar", f.__name__)
    make_icons(); print("ikon")
    make_sounds(); make_music(); print("suara")
