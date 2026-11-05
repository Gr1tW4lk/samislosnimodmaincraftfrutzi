"""Синтез звуков и музыки мода (numpy). Всё своё — никаких авторских прав.

Запуск: python tools/gen_synth.py
"""
import os
import subprocess
import tempfile
import wave

import numpy as np
from scipy.signal import lfilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SND = os.path.join(ROOT, "src", "main", "resources", "assets", "hardest", "sounds")
FFMPEG = r"D:\Programs\ffmpeg\bin\ffmpeg.exe"
SR = 44100
RNG = np.random.default_rng(7)


def t(dur):
    return np.arange(int(SR * dur)) / SR


def env(n, a=0.005, d=0.1, s=0.6, r=0.1, total=None):
    n = int(n)
    out = np.ones(n)
    ai, di, ri = int(a * SR), int(d * SR), int(r * SR)
    ai = min(ai, n)
    out[:ai] = np.linspace(0, 1, ai) if ai > 0 else out[:ai]
    if di > 0:
        e = min(n, ai + di)
        out[ai:e] = np.linspace(1, s, e - ai)
        out[e:] = s
    if ri > 0 and n > ri:
        out[-ri:] *= np.linspace(1, 0, ri)
    return out


def sine(f, dur, phase=0):
    tt = t(dur)
    if callable(f):
        ph = 2 * np.pi * np.cumsum(f(tt)) / SR
        return np.sin(ph + phase)
    return np.sin(2 * np.pi * f * tt + phase)


def square(f, dur, duty=0.5):
    tt = t(dur)
    if callable(f):
        ph = np.cumsum(f(tt)) / SR
    else:
        ph = f * tt
    return np.where((ph % 1.0) < duty, 1.0, -1.0)


def tri(f, dur):
    tt = t(dur)
    ph = (f * tt) % 1.0
    return 4 * np.abs(ph - 0.5) - 1


def noise(dur):
    return RNG.uniform(-1, 1, int(SR * dur))


def lowpass(x, cutoff):
    # однополюсный фильтр — достаточно для «мягкости»
    a = np.exp(-2 * np.pi * cutoff / SR)
    return lfilter([1 - a], [1, -a], x)


def crush(x, bits=6, hold=4):
    q = 2 ** bits
    y = np.round(x * q) / q
    y = np.repeat(y[::hold], hold)[:len(x)]
    return y


def mix(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[:len(p)] += p
    return out


def cat(*parts):
    return np.concatenate(parts)


def silence(dur):
    return np.zeros(int(SR * dur))


def norm(x, peak=0.89):
    m = np.max(np.abs(x)) + 1e-9
    return x / m * peak


def save(path, x, stereo=False):
    x = norm(x)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        tmpname = tmp.name
    data = (x * 32767).astype(np.int16)
    with wave.open(tmpname, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data.tobytes())
    subprocess.run([FFMPEG, "-v", "error", "-y", "-i", tmpname, "-c:a", "libvorbis", "-q:a", "5", path], check=True)
    os.remove(tmpname)
    print("ok", os.path.relpath(path, SND))


def sfx(name, x):
    save(os.path.join(SND, "sfx", name + ".ogg"), x)


def note(n):
    """MIDI -> Гц."""
    return 440.0 * 2 ** ((n - 69) / 12)


# ----------------------------------------------------------------------- эффекты

def glitch(dur, seed):
    r = np.random.default_rng(seed)
    out = []
    left = dur
    while left > 0:
        d = r.uniform(0.02, 0.08)
        kind = r.integers(0, 4)
        if kind == 0:
            seg = square(r.uniform(80, 1600), d, r.uniform(0.1, 0.5)) * 0.6
        elif kind == 1:
            seg = noise(d) * 0.5
        elif kind == 2:
            seg = sine(r.uniform(200, 3000), d) * 0.7
        else:
            seg = silence(d)
        rep = r.integers(1, 4)
        out.append(np.tile(seg, rep))
        left -= d * rep
    x = crush(np.concatenate(out), 5, 3)
    n = int(SR * dur)
    if len(x) < n:
        x = np.concatenate([x, np.zeros(n - len(x))])
    return x[:n]


def make_sfx():
    sfx("glitch_short", glitch(0.35, 1) * env(SR * 0.35, 0.002, 0.05, 0.9, 0.08))
    sfx("glitch_long", glitch(1.3, 2) * env(SR * 1.3, 0.002, 0.2, 0.9, 0.3))
    sfx("beep", square(1250, 0.1, 0.5) * env(SR * 0.1, 0.002, 0.02, 0.8, 0.02) * 0.5)
    sfx("popup_close", cat(noise(0.01) * 0.8, sine(lambda tt: 1800 + 3000 * tt, 0.06) * np.linspace(1, 0, int(SR * 0.06))))
    ok = cat(*[sine(note(n), 0.11) * env(SR * 0.11, 0.002, 0.05, 0.5, 0.03) for n in (76, 81)],
             mix(sine(note(88), 0.5), sine(note(84), 0.5) * 0.6) * env(SR * 0.5, 0.002, 0.15, 0.3, 0.3))
    sfx("captcha_ok", ok)
    sfx("captcha_fail", lowpass(square(140, 0.7, 0.5) * env(SR * 0.7, 0.005, 0.1, 0.8, 0.1), 1800))
    rw = mix(sine(lambda x: 400 + 1500 * np.abs(np.sin(x * 18)), 0.9) * 0.5, noise(0.9) * 0.15) * env(SR * 0.9, 0.01, 0.2, 0.8, 0.2)
    sfx("rewind", crush(rw, 6, 2))
    sfx("laser_charge", sine(lambda x: 300 + 1600 * x ** 1.5, 1.0) * (0.6 + 0.4 * np.sin(t(1.0) * 2 * np.pi * 18)) * env(SR, 0.01, 0.1, 0.9, 0.08))
    swell = lowpass(noise(1.2), 900) * np.linspace(0, 1, int(SR * 1.2)) ** 2
    hit = mix(sine(lambda x: 70 * np.exp(-x * 1.5), 2.0), sine(lambda x: 140 * np.exp(-x * 1.5), 2.0) * 0.4,
              lowpass(noise(2.0), 300) * 1.5 * env(SR * 2, 0.002, 0.2, 0.2, 1.5)) * env(SR * 2.0, 0.002, 0.4, 0.5, 1.4)
    sfx("wasted", cat(swell * 0.6, hit))
    sfx("tiktok_swoosh", lowpass(noise(0.5), 3000) * np.sin(np.linspace(0, np.pi, int(SR * 0.5))) ** 2)
    stut = cat(*[np.tile(glitch(0.03, 20 + i), 3) for i in range(4)])
    sfx("lag_stutter", stut * 0.7)
    chime = mix(*[sine(note(n), 1.8) * env(SR * 1.8, 0.05 + i * 0.08, 0.4, 0.5, 1.0) * 0.4 for i, n in enumerate((60, 64, 67, 72, 76))])
    sfx("update_chime", chime)
    jingle = []
    for n, d in [(72, 0.15), (76, 0.15), (79, 0.15), (84, 0.3), (79, 0.15), (84, 0.6)]:
        jingle.append(mix(square(note(n), d, 0.25) * 0.4, tri(note(n - 12), d) * 0.5) * env(SR * d, 0.005, 0.05, 0.6, 0.05))
    sfx("ad_jingle", cat(*jingle))

    # паутина: мокрое «чпок» + свист
    sfx("web_shot", mix(lowpass(noise(0.25), 2500) * env(SR * 0.25, 0.002, 0.08, 0.2, 0.1),
                        sine(lambda x: 900 - 2500 * x, 0.25) * env(SR * 0.25, 0.002, 0.1, 0.1, 0.1) * 0.5))
    # Кровавая луна: низкий колокол + гул
    bell = mix(*[sine(f, 4.0) * np.exp(-t(4.0) * k) * a for f, k, a in ((73.4, 0.6, 1.0), (146.8, 0.9, 0.5), (220.0, 1.3, 0.35), (293.7, 1.8, 0.2), (369.9, 2.4, 0.12))])
    drone = lowpass(mix(square(36.7, 4.0, 0.5) * 0.3, noise(4.0) * 0.2), 300) * env(SR * 4.0, 0.8, 1.0, 0.7, 1.5)
    sfx("blood_moon", mix(bell, drone))
    # смена фазы дракона: удар + обратный «вдох»
    rev = (lowpass(noise(1.0), 2000) * np.linspace(0, 1, int(SR * 1.0)) ** 3)
    boom = mix(sine(lambda x: 60 * np.exp(-x * 1.2), 2.5), lowpass(noise(2.5), 250) * 2) * env(SR * 2.5, 0.002, 0.4, 0.4, 1.8)
    sfx("dragon_phase", cat(rev * 0.7, boom))
    make_boss_sfx()
    make_cannon_sfx()


def make_cannon_sfx():
    """Выстрел Драконобоя: щелчок, лазерный «пиу» вниз и басовый толчок."""
    n = 0.32
    zap = sine(lambda x: 2400 * np.exp(-x * 14) + 300, n) * np.exp(-t(n) * 9)
    buzz = square(lambda x: 900 * np.exp(-x * 10) + 120, n, 0.3) * np.exp(-t(n) * 14) * 0.35
    thump = sine(lambda x: 90 * np.exp(-x * 6) + 40, n) * np.exp(-t(n) * 12) * 1.2
    click = noise(0.012) * np.linspace(1, 0, int(SR * 0.012))
    sfx("cannon_shot", mix(zap, buzz, thump, click * 0.8))


def make_boss_sfx():
    """Звуки атак Пробуждённого дракона."""
    # удар босса: суббас + треск + длинный хвост
    n = 3.2
    sub = sine(lambda x: 26 + 54 * np.exp(-x * 2.2), n) * np.exp(-t(n) * 1.1)
    body = lowpass(noise(n), 220) * 3.0 * np.exp(-t(n) * 2.0)
    crack = noise(0.06) * np.linspace(1, 0, int(SR * 0.06))
    sfx("boss_boom", mix(sub * 1.2, body, crack * 0.9) * env(SR * n, 0.002, 0.3, 0.8, 1.2))
    # ударная волна: свист ветра + рокот
    n = 2.0
    sweep = lowpass(noise(n), 1500) * np.sin(np.linspace(0, np.pi, int(SR * n))) ** 1.5
    rumble = lowpass(noise(n), 120) * 3 * np.exp(-t(n) * 1.2)
    sfx("shockwave", mix(sweep * 0.8, rumble, sine(lambda x: 45 - 10 * x, n) * 0.5 * np.exp(-t(n) * 1.5)))
    # вихрь Пустоты: гул, который всё быстрее вращается
    n = 3.8
    tt = t(n)
    wob = 0.5 + 0.5 * np.sin(2 * np.pi * np.cumsum(2 + 10 * tt / n) / SR)
    drone = mix(sine(lambda x: 38 + 20 * x / n, n), sine(lambda x: 57 + 31 * x / n, n) * 0.6, lowpass(noise(n), 600) * 0.8 * wob)
    sfx("vortex", drone * (0.3 + 0.7 * (tt / n) ** 2) * env(SR * n, 0.3, 0.2, 1.0, 0.25))
    # падение метеорита: нисходящий свист + треск огня
    n = 2.6
    whistle = sine(lambda x: 2000 * np.exp(-x * 0.9) + 180, n) * 0.4
    rush = lowpass(noise(n), 900) * (t(n) / n) ** 1.5 * 1.2
    crackle = (RNG.uniform(0, 1, int(SR * n)) > 0.9985) * RNG.uniform(-1, 1, int(SR * n)) * 2
    sfx("meteor_fall", mix(whistle * (0.3 + t(n) / n), rush, lowpass(crackle, 3000)) * env(SR * n, 0.4, 0.1, 1.0, 0.05))
    # вдох дракона: обратный шум + низкий хрип
    n = 1.5
    rev = lowpass(noise(n), 1200) * np.linspace(0, 1, int(SR * n)) ** 2.5
    rasp = lowpass(square(lambda x: 48 + 16 * x, n, 0.3), 400) * np.linspace(0, 1, int(SR * n)) ** 2 * 0.5
    sfx("dragon_inhale", mix(rev, rasp) * env(SR * n, 0.05, 0.1, 1.0, 0.08))
    # кристалл вырывается из земли: звон + хруст
    n = 0.7
    bells = mix(*[sine(lambda x, f=f: f * (1 + 0.15 * x), n) * np.exp(-t(n) * k) * a
                  for f, k, a in ((1760, 6, 0.5), (2637, 8, 0.4), (3520, 10, 0.3), (4186, 12, 0.2))])
    crunch = lowpass(noise(0.12), 2500) * np.linspace(1, 0, int(SR * 0.12))
    sfx("crystal_rise", mix(crunch, bells))


# ----------------------------------------------------------------------- музыка

def kick(d=0.25):
    return sine(lambda x: 150 * np.exp(-x * 25) + 45, d) * env(SR * d, 0.001, 0.15, 0.0, 0.05)


def snare(d=0.2):
    return mix(noise(d) * 0.7, tri(190, d) * 0.4) * env(SR * d, 0.001, 0.12, 0.0, 0.05)


def hat(d=0.06):
    n = noise(d)
    n = n - lowpass(n, 6000)
    return n * env(SR * d, 0.001, 0.04, 0.0, 0.01) * 0.6


def saw(f, dur):
    ph = f * t(dur)
    return 2 * (ph % 1.0) - 1


def comb(x, delay, fb):
    d = int(SR * delay)
    a = np.zeros(d + 1)
    a[0] = 1
    a[d] = -fb
    return lfilter([1.0], a, x)


def dragon_music():
    """Тема битвы с Пробуждённым Эндер-драконом: ре минор, 150 BPM, хор, бас, литавры."""
    bpm = 150
    beat = 60 / bpm
    step = beat / 4
    bars = 32
    total = int(SR * beat * 4 * bars)
    pads = np.zeros(total + SR * 3)
    track = np.zeros(total + SR * 3)

    def put(buf, x, at, gain=1.0):
        st = int(at * SR)
        e = min(len(buf), st + len(x))
        buf[st:e] += x[:e - st] * gain

    # Dm  Bb  Gm  A  (i VI iv V)
    roots = [38, 34, 31, 33]
    chords = [[62, 65, 69], [58, 62, 65], [55, 58, 62], [57, 61, 64]]
    lead = [74, None, 74, 76, 77, None, 76, 74, 73, None, 69, None, 74, None, None, None,
            77, None, 77, 79, 81, None, 79, 77, 76, None, 73, None, 74, None, None, None]
    for bar in range(bars):
        ci = bar % 4
        bt = bar * beat * 4
        sec = bar // 8
        # хор / пэд: три детюненных пилы
        for n in chords[ci]:
            d = beat * 4
            v = mix(saw(note(n - 12), d), saw(note(n - 12) * 1.004, d), saw(note(n - 12) * 0.996, d)) / 3
            put(pads, lowpass(v, 1400) * env(SR * d, 0.35, 0.3, 0.8, 0.4), bt, 0.22)
        # бас восьмыми
        if sec >= 1 or bar % 8 >= 4:
            for i in range(8):
                d = beat / 2
                x = lowpass(saw(note(roots[ci]), d * 0.9), 420) * env(SR * d * 0.9, 0.003, 0.08, 0.6, 0.03)
                put(track, x, bt + i * d, 0.42)
        # остинато 16-ми
        if sec >= 1:
            pat = [0, 1, 2, 1, 0, 1, 2, 3]
            for i in range(16):
                n = chords[ci][pat[i % 8] % 3] + (12 if pat[i % 8] == 3 else 0)
                x = square(note(n), step * 0.9, 0.3) * env(SR * step * 0.9, 0.001, 0.05, 0.15, 0.02)
                put(track, lowpass(x, 3000), bt + i * step, 0.10)
        # медная мелодия
        if sec >= 2:
            for i in range(16):
                n = lead[(bar % 2) * 16 + i]
                if n is None:
                    continue
                d = step * 3.5
                up = 12 if sec == 3 else 0
                vib = 1 + 0.005 * np.sin(t(d) * 2 * np.pi * 5.5)
                x = lowpass(mix(saw(note(n + up - 12), d), saw(note(n + up - 12) * 1.003, d)) * vib, 2600)
                put(track, x * env(SR * d, 0.02, 0.15, 0.7, 0.12), bt + i * step, 0.16)
        # барабаны: литавры в интро, дальше полная установка
        for b in range(4):
            tb = bt + b * beat
            taiko = mix(sine(lambda x: 70 * np.exp(-x * 6) + 40, 0.6), lowpass(noise(0.6), 500) * 0.6) * env(SR * 0.6, 0.001, 0.25, 0.0, 0.2)
            if sec == 0:
                if b in (0, 2) or (bar % 4 == 3 and b == 3):
                    put(track, taiko, tb, 0.9)
                continue
            put(track, sine(lambda x: 150 * np.exp(-x * 25) + 42, 0.3) * env(SR * 0.3, 0.001, 0.2, 0, 0.05), tb, 0.85 if b in (0, 2) else 0.5)
            if b in (1, 3):
                sn = mix(noise(0.25) * 0.8, tri(185, 0.25) * 0.4) * env(SR * 0.25, 0.001, 0.15, 0, 0.05)
                put(track, comb(sn, 0.09, 0.35), tb, 0.45)
            for hh in range(2):
                n2 = noise(0.05)
                put(track, (n2 - lowpass(n2, 7000)) * env(SR * 0.05, 0.001, 0.03, 0, 0.01), tb + hh * beat / 2 + beat / 4, 0.35)
            if bar % 8 == 7 and b == 3:
                for k in range(4):
                    put(track, taiko, tb + k * step, 0.6)
    pads = comb(pads, 0.13, 0.4)
    out = (track + pads)[:total]
    out = np.tanh(out * 1.5) / 1.15
    save(os.path.join(SND, "music", "dragon.ogg"), out)


if __name__ == "__main__":
    import sys
    make_sfx()
    if "--no-music" not in sys.argv:
        dragon_music()
