#!/usr/bin/env python3
"""Cek kontras WCAG untuk palet Susun Kata, termasuk teks di atas foto latar (titik terburuk).
Jalankan: python3 tools/cek_kontras.py   (gagal dengan kode 1 bila ada pasangan di bawah batas)"""
import os, sys
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
BG = os.path.join(HERE, "..", "app", "src", "main", "res", "drawable-nodpi")

PAL = {
    "hijau": "1F7A3A", "hijau_tepi": "124D25", "krem": "F5F0E1", "krem_tepi": "B9AF92",
    "tinta": "14211A", "emas": "F2B705", "emas_tepi": "8A6500", "putih": "FFFFFF",
}

def rgb(h): return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], float)

def lum(c):
    c = np.asarray(c, float) / 255.0
    c = np.where(c <= 0.03928, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)
    return 0.2126 * c[..., 0] + 0.7152 * c[..., 1] + 0.0722 * c[..., 2]

def ratio(a, b):
    la, lb = lum(a), lum(b)
    hi, lo = np.maximum(la, lb), np.minimum(la, lb)
    return (hi + 0.05) / (lo + 0.05)

fails = 0
def check(name, fg, bg, need):
    global fails
    r = float(ratio(fg, bg))
    ok = r >= need
    fails += 0 if ok else 1
    print("%-52s %5.2f:1  (butuh %.1f)  %s" % (name, r, need, "PASS" if ok else "FAIL"))

P = {k: rgb(v) for k, v in PAL.items()}
print("== Teks (4.5 normal, 3.0 besar >= 24px)")
check("putih di hijau (huruf ubin, tombol utama)", P["putih"], P["hijau"], 4.5)
check("tinta di krem (huruf roda, tombol sekunder, kartu)", P["tinta"], P["krem"], 4.5)
check("tinta di emas (huruf kata bonus, label hadiah)", P["tinta"], P["emas"], 4.5)
check("emas di tinta (angka koin di pil gelap)", P["emas"], P["tinta"], 4.5)
check("krem di tinta (toast, label tombol bulat)", P["krem"], P["tinta"], 4.5)
check("putih di tinta (judul di panel gelap)", P["putih"], P["tinta"], 4.5)
check("hijau di krem (judul kartu)", P["hijau"], P["krem"], 4.5)
print("== Non-teks (3.0)")
check("ubin hijau vs papan tinta", P["hijau"], P["tinta"], 3.0)
check("ubin krem vs papan tinta", P["krem"], P["tinta"], 3.0)
check("cincin tinta vs cakram krem (roda)", P["tinta"], P["krem"], 3.0)
check("tepi emas vs krem (koin di pil krem)", P["emas_tepi"], P["krem"], 3.0)
check("ikon krem vs tombol tinta", P["krem"], P["tinta"], 3.0)
check("tombol hijau vs kartu krem", P["hijau"], P["krem"], 3.0)

def scrimmed(img, alpha):
    return img * (1 - alpha) + P["tinta"][None, None, :] * alpha

print("== Teks putih di atas foto + penutup tinta (piksel TERANG terburuk, persentil 99.5)")
H = 1280
for name in ["bg_terasering", "bg_pantai", "bg_gunung", "bg_hutan", "bg_danau", "bg_malam"]:
    im = np.asarray(Image.open(os.path.join(BG, name + ".jpg")).convert("RGB"), float)
    top = im[: int(H * 0.25)]
    # judul layar bermain: gradasi penutup 0.62 di tepi atas
    worst_top = scrimmed(top, 0.62)
    p = np.percentile(lum(worst_top), 99.5)
    r = (1.0 + 0.05) / (p + 0.05)
    ok = r >= 4.5; fails += 0 if ok else 1
    print("%-52s %5.2f:1  (butuh 4.5)  %s" % (name + " bagian atas, penutup 0.62", r, "PASS" if ok else "FAIL"))
    full = scrimmed(im, 0.62)
    p = np.percentile(lum(full), 99.5)
    r = (1.0 + 0.05) / (p + 0.05)
    ok = r >= 4.5; fails += 0 if ok else 1
    print("%-52s %5.2f:1  (butuh 4.5)  %s" % (name + " seluruh layar menu, penutup 0.62", r, "PASS" if ok else "FAIL"))
print("\nTOTAL GAGAL:", fails)
sys.exit(1 if fails else 0)
