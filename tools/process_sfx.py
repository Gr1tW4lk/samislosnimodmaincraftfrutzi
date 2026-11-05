"""Нарезает скачанные CC0-звуки: находит громкие фрагменты, обрезает тишину, нормализует, сохраняет в ogg (моно).

Запуск: python -I tools/process_sfx.py <папка_с_сырыми_mp3>
"""
import os
import subprocess
import sys

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "hardest", "sounds", "sfx")
FFMPEG = r"D:\Programs\ffmpeg\bin\ffmpeg.exe"
SR = 44100

# имя: (сколько вариантов, мин. длина, макс. длина)
SPEC = {
    "goose_honk": (3, 0.15, 1.2),
    "goose_hiss": (1, 0.3, 1.6),
    "slap": (2, 0.04, 0.5),
    "cash": (1, 0.3, 1.8),
    "stamp": (1, 0.03, 0.5),
    "courier_bell": (1, 0.3, 2.0),
    "splat": (1, 0.08, 1.0),
    "whoosh": (1, 0.15, 0.9),
    "tiny_steps": (3, 0.03, 0.25),
    "siren": (1, 0.8, 2.5),
    "mimic_growl": (1, 0.4, 1.6),
    "mimic_chomp": (2, 0.05, 0.6),
    "sniper_shot": (1, 0.15, 1.6),
    "parachute": (1, 0.15, 1.0),
    "gift_open": (1, 0.1, 1.2),
    "bug_skitter": (2, 0.05, 0.5),
    "slam": (1, 0.2, 1.6),
    "tree_creak": (1, 0.4, 1.6),
    "tree_fall": (1, 0.6, 2.4),
    "rumble": (1, 1.0, 2.6),
}


def load(path):
    raw = subprocess.run([FFMPEG, "-v", "error", "-i", path, "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"],
                         capture_output=True).stdout
    return np.frombuffer(raw, dtype=np.float32).astype(np.float64)


def segments(x, min_len, max_len):
    win = int(SR * 0.01)
    n = len(x) // win
    if n == 0:
        return []
    rms = np.sqrt(np.mean(x[:n * win].reshape(n, win) ** 2, axis=1))
    thr = rms.max() * 0.12
    active = rms > thr
    segs = []
    i = 0
    while i < n:
        if active[i]:
            j = i
            gap = 0
            while j < n and gap < 6:
                gap = gap + 1 if not active[j] else 0
                j += 1
            s, e = i * win, (j - gap) * win
            segs.append((s, e, float(rms[i:j].max())))
            i = j
        else:
            i += 1
    out = []
    for s, e, peak in segs:
        length = (e - s) / SR
        if length < min_len:
            continue
        if length > max_len:
            e = s + int(max_len * SR)
        out.append((s, e, peak))
    out.sort(key=lambda z: -z[2])
    return out


def cut(x, s, e):
    pre, post = int(0.015 * SR), int(0.08 * SR)
    s = max(0, s - pre)
    e = min(len(x), e + post)
    y = x[s:e].copy()
    fi, fo = int(0.005 * SR), min(len(y) // 3, int(0.06 * SR))
    y[:fi] *= np.linspace(0, 1, fi)
    y[-fo:] *= np.linspace(1, 0, fo)
    y = y / (np.max(np.abs(y)) + 1e-9) * 0.89
    return y


def save(y, path):
    p = subprocess.Popen([FFMPEG, "-v", "error", "-y", "-f", "f32le", "-ar", str(SR), "-ac", "1", "-i", "-",
                          "-c:a", "libvorbis", "-q:a", "5", path], stdin=subprocess.PIPE)
    p.communicate(y.astype(np.float32).tobytes())


def main():
    raw = sys.argv[1]
    os.makedirs(OUT, exist_ok=True)
    report = []
    only = set(sys.argv[2:])
    for name, (count, mn, mx) in SPEC.items():
        if only and name not in only:
            continue
        cands = sorted(f for f in os.listdir(raw) if f.startswith(name + "__") and f.endswith(".mp3"))
        picked = []
        for f in cands:
            x = load(os.path.join(raw, f))
            if len(x) < SR * 0.05:
                continue
            for s, e, peak in segments(x, mn, mx):
                picked.append((f, cut(x, s, e)))
                if len(picked) >= count:
                    break
            if len(picked) >= count:
                break
        if not picked:
            report.append(f"{name}: НЕТ")
            continue
        for i, (f, y) in enumerate(picked):
            fname = f"{name}_{i + 1}.ogg" if count > 1 else f"{name}.ogg"
            save(y, os.path.join(OUT, fname))
        report.append(f"{name}: {len(picked)} шт. из {', '.join(sorted(set(p[0] for p in picked)))} ({', '.join(f'{len(p[1]) / SR:.2f}s' for p in picked)})")
    print("\n".join(report))


if __name__ == "__main__":
    main()
