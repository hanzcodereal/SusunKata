#!/usr/bin/env python3
"""Pembuat level Susun Kata (Hanz Dev Official).

Membaca tools/kata_sumber.txt, membuat:
  app/src/main/assets/kata.txt     kamus (huruf besar, satu kata per baris)
  app/src/main/assets/levels.json  level berisi huruf roda + susunan kotak

Setiap level DIVERIFIKASI (solver): semua kata bisa dibentuk dari huruf roda,
tidak ada kata liar yang terbentuk di kisi, semua kata saling tersambung.
Jalankan:  python3 tools/buat_level.py
"""
import json, random, sys, os
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "app", "src", "main", "assets")
FORCED = {"LAUT"}  # level 1 (tutorial, mirip contoh)
BLOCK = {"BANCI", "GILA", "BODOH", "EDAN"}

def load_words():
    raw = open(os.path.join(HERE, "kata_sumber.txt"), encoding="utf-8").read().split()
    ws = sorted({w.upper() for w in raw if w.isalpha() and 3 <= len(w) <= 7} - BLOCK)
    return ws

WORDS = load_words()
CNT = {w: Counter(w) for w in WORDS}

def formable(w, letters_cnt):
    return all(letters_cnt[ch] >= n for ch, n in CNT[w].items())

class Grid:
    def __init__(self):
        self.cells = {}
        self.h = set()
        self.v = set()
        self.placed = []
    def can_place(self, word, r, c, horiz, first=False):
        dr, dc = (0, 1) if horiz else (1, 0)
        if (r - dr, c - dc) in self.cells or (r + dr * len(word), c + dc * len(word)) in self.cells:
            return False
        inter = 0
        for k, ch in enumerate(word):
            rr, cc = r + dr * k, c + dc * k
            if (rr, cc) in self.cells:
                if self.cells[(rr, cc)] != ch:
                    return False
                if (horiz and (rr, cc) in self.h) or ((not horiz) and (rr, cc) in self.v):
                    return False
                inter += 1
            else:
                nb = [(rr - 1, cc), (rr + 1, cc)] if horiz else [(rr, cc - 1), (rr, cc + 1)]
                if any(n in self.cells for n in nb):
                    return False
        return first or inter >= 1
    def put(self, word, r, c, horiz):
        dr, dc = (0, 1) if horiz else (1, 0)
        for k, ch in enumerate(word):
            p = (r + dr * k, c + dc * k)
            self.cells[p] = ch
            (self.h if horiz else self.v).add(p)
        self.placed.append((word, r, c, horiz))
    def bbox(self, extra=None):
        pts = list(self.cells.keys()) + (extra or [])
        rs = [p[0] for p in pts]; cs = [p[1] for p in pts]
        return min(rs), max(rs), min(cs), max(cs)

def word_cells(word, r, c, horiz):
    dr, dc = (0, 1) if horiz else (1, 0)
    return [(r + dr * k, c + dc * k) for k in range(len(word))]

def layout(root, cands, rng, target, max_rows=7, max_cols=7, tries=80):
    best = None
    others = [w for w in cands if w != root]
    for _ in range(tries):
        g = Grid()
        g.put(root, 0, 0, rng.random() < 0.5)
        order = others[:]
        rng.shuffle(order)
        order.sort(key=lambda w: -len(w) + rng.random() * 2.2)
        for w in order:
            if len(g.placed) >= target:
                break
            opts = []
            for (pr, pc), ch in list(g.cells.items()):
                for i, x in enumerate(w):
                    if x != ch:
                        continue
                    for horiz in (True, False):
                        r, c = (pr, pc - i) if horiz else (pr - i, pc)
                        if g.can_place(w, r, c, horiz):
                            r0, r1, c0, c1 = g.bbox(word_cells(w, r, c, horiz))
                            if r1 - r0 + 1 <= max_rows and c1 - c0 + 1 <= max_cols:
                                opts.append(((r1 - r0 + 1) * (c1 - c0 + 1) + rng.random(), r, c, horiz))
            if opts:
                opts.sort()
                _, r, c, horiz = opts[0]
                g.put(w, r, c, horiz)
        r0, r1, c0, c1 = g.bbox()
        area = (r1 - r0 + 1) * (c1 - c0 + 1)
        score = (len(g.placed), -area)
        if best is None or score > best[0]:
            best = (score, g)
    return best[1]

def normalize(g):
    r0, r1, c0, c1 = g.bbox()
    words = [{"w": w, "r": r - r0, "c": c - c0, "h": 1 if h else 0} for (w, r, c, h) in g.placed]
    return r1 - r0 + 1, c1 - c0 + 1, words

def verify(level, letters_cnt_ok=True):
    letters = Counter(level["letters"])
    rows, cols = level["rows"], level["cols"]
    grid = {}
    wset = set()
    for w in level["words"]:
        assert w["w"] not in wset, "kata ganda " + w["w"]
        wset.add(w["w"])
        assert all(letters[ch] >= n for ch, n in Counter(w["w"]).items()), "tidak bisa dibentuk " + w["w"]
        for k, ch in enumerate(w["w"]):
            r = w["r"] + (0 if w["h"] else k); c = w["c"] + (k if w["h"] else 0)
            assert 0 <= r < rows and 0 <= c < cols, "di luar kisi"
            assert grid.get((r, c), ch) == ch, "huruf bentrok"
            grid[(r, c)] = ch
    runs = set()
    for r in range(rows):
        s = ""
        for c in range(cols + 1):
            ch = grid.get((r, c)) if c < cols else None
            if ch: s += ch
            else:
                if len(s) >= 2: runs.add(s)
                s = ""
    for c in range(cols):
        s = ""
        for r in range(rows + 1):
            ch = grid.get((r, c)) if r < rows else None
            if ch: s += ch
            else:
                if len(s) >= 2: runs.add(s)
                s = ""
    assert runs == wset, "kata liar di kisi: %s" % (runs ^ wset)
    # keterhubungan
    seen = set(); stack = [next(iter(grid))]
    while stack:
        p = stack.pop()
        if p in seen: continue
        seen.add(p)
        for d in ((1,0),(-1,0),(0,1),(0,-1)):
            q = (p[0]+d[0], p[1]+d[1])
            if q in grid: stack.append(q)
    assert len(seen) == len(grid), "kisi tidak tersambung"
    assert len(level["letters"]) <= 7

def main():
    rng = random.Random(20261113)
    classes = {4: (3, 4, 12), 5: (4, 6, 26), 6: (5, 8, 34), 7: (6, 10, 28)}
    chosen = []
    seen_keys = set()
    for n, (min_words, max_words, quota) in classes.items():
        seeds = []
        for root in [w for w in WORDS if len(w) == n]:
            key = "".join(sorted(root))
            if key in seen_keys: continue
            lc = Counter(root)
            cands = [w for w in WORDS if formable(w, lc)]
            if len(cands) >= min_words:
                seeds.append((len(cands), root, key, cands))
        seeds.sort(key=lambda x: (x[1] not in FORCED, -x[0], x[1]))
        taken = 0
        for cnt, root, key, cands in seeds:
            if taken >= quota: break
            if key in seen_keys: continue
            g = layout(root, cands, rng, 3 if root in FORCED else max_words)
            if len(g.placed) < min_words: continue
            rows, cols, words = normalize(g)
            lv = {"letters": root, "rows": rows, "cols": cols, "words": words, "_n": len(words)}
            verify(lv)
            chosen.append(lv); seen_keys.add(key); taken += 1
        print("panjang %d: %d level (tersedia %d)" % (n, taken, len(seeds)))
    chosen.sort(key=lambda l: (len(l["letters"]), l["_n"]))
    # level 1 = LAUT bila ada
    for i, l in enumerate(chosen):
        if l["letters"] == "LAUT":
            chosen.insert(0, chosen.pop(i)); break
    for l in chosen: l.pop("_n")
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, "kata.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(WORDS) + "\n")
    with open(os.path.join(OUT, "levels.json"), "w", encoding="utf-8") as f:
        json.dump(chosen, f, ensure_ascii=False, separators=(",", ":"))
    # laporan
    print("TOTAL level:", len(chosen), "| kamus:", len(WORDS), "kata")
    tot_bonus = 0
    for i, l in enumerate(chosen):
        lc = Counter(l["letters"]); ws = {w["w"] for w in l["words"]}
        bonus = [w for w in WORDS if formable(w, lc) and w not in ws]
        tot_bonus += len(bonus)
        if i < 6 or i % 10 == 0:
            print("  L%-3d %-8s grid %dx%d kata=%d bonus=%d" % (i+1, l["letters"], l["rows"], l["cols"], len(l["words"]), len(bonus)))
    print("rata-rata bonus/level: %.1f" % (tot_bonus / len(chosen)))
    # tes uji: ulang verifikasi dari berkas
    for l in json.load(open(os.path.join(OUT, "levels.json"), encoding="utf-8")):
        verify(l)
    print("VERIFIKASI OK: semua level lolos")

if __name__ == "__main__":
    sys.exit(main())
