"""Генерация озвучки мода: edge-tts -> ffmpeg (обработка голоса) -> ogg в ресурсы мода.

Запуск: python tools/gen_voice.py [--force] [id ...]
"""
import asyncio
import json
import os
import subprocess
import sys

import edge_tts

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LINES = os.path.join(ROOT, "tools", "voice_lines.json")
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "hardest", "sounds", "voice")
CACHE = os.path.join(ROOT, "tools", "tts_cache")
FFMPEG = r"D:\Programs\ffmpeg\bin\ffmpeg.exe"

# голос: (tts-голос, rate, pitch, ffmpeg-фильтр)
VOICES = {
    "igor": ("ru-RU-DmitryNeural", "-4%", "-6Hz",
             "asetrate=24000*0.93,aresample=44100,atempo=1.075,"
             "aecho=0.8:0.75:6|11:0.30|0.20,"
             "flanger=delay=0:depth=2:regen=10:speed=0.3:width=60,"
             "acrusher=bits=9:mode=log:aa=1:mix=0.14,"
             "highpass=f=100,equalizer=f=2500:t=q:w=1:g=3"),
    "boss": ("ru-RU-DmitryNeural", "+0%", "-10Hz",
             "asetrate=24000*0.82,aresample=44100,atempo=1.15,"
             "aecho=0.8:0.8:9|17|29:0.40|0.30|0.20,"
             "flanger=delay=2:depth=4:regen=30:speed=0.6,"
             "acrusher=bits=7:mode=log:aa=1:mix=0.22,"
             "lowshelf=f=150:g=4"),
    "tax": ("ru-RU-DmitryNeural", "-12%", "-14Hz",
            "highpass=f=180,lowpass=f=6500,aecho=0.6:0.4:40:0.12"),
    "courier": ("ru-RU-DmitryNeural", "+28%", "+12Hz",
                "highpass=f=120"),
    "player": ("ru-RU-DmitryNeural", "+10%", "+0Hz", "highpass=f=100"),
    "twin": ("ru-RU-DmitryNeural", "-6%", "-4Hz",
             "aecho=0.8:0.7:15|31:0.45|0.30,"
             "flanger=delay=5:depth=6:regen=45:speed=2,"
             "acrusher=bits=6:mode=log:aa=1:mix=0.28"),
    "ad": ("ru-RU-SvetlanaNeural", "+14%", "+10Hz",
           "acompressor=threshold=-22dB:ratio=4:attack=5:release=60,"
           "highpass=f=150,aecho=0.7:0.3:25:0.08"),
    "mom": ("ru-RU-SvetlanaNeural", "+8%", "-18Hz",
            "acompressor=threshold=-25dB:ratio=6:attack=3:release=40,"
            "acrusher=bits=12:mix=0.08,highpass=f=120"),
    "food": ("ru-RU-SvetlanaNeural", "+22%", "+20Hz",
             "asetrate=24000*1.42,aresample=44100,atempo=0.85,highpass=f=200"),
    "block": ("ru-RU-DmitryNeural", "+18%", "+10Hz",
              "asetrate=24000*1.6,aresample=44100,atempo=0.8,highpass=f=200"),
}

LOUD = {"boss": "-12", "mom": "-11", "food": "-12", "block": "-12"}


async def tts(text, voice, rate, pitch, path):
    com = edge_tts.Communicate(text, voice, rate=rate, pitch=pitch)
    await com.save(path)


def process(src, dst, flt, loud):
    chain = f"{flt},loudnorm=I={loud}:TP=-1.5:LRA=9,apad=pad_dur=0.12"
    cmd = [FFMPEG, "-v", "error", "-y", "-i", src, "-af", chain,
           "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5", dst]
    subprocess.run(cmd, check=True)


async def main():
    force = "--force" in sys.argv
    only = [a for a in sys.argv[1:] if not a.startswith("--")]
    os.makedirs(OUT_DIR, exist_ok=True)
    os.makedirs(CACHE, exist_ok=True)
    with open(LINES, encoding="utf-8") as f:
        lines = json.load(f)
    for ln in lines:
        lid = ln["id"]
        if only and lid not in only:
            continue
        dst = os.path.join(OUT_DIR, lid + ".ogg")
        if os.path.exists(dst) and not force and not only:
            continue
        voice, rate, pitch, flt = VOICES[ln["voice"]]
        say = ln.get("say", ln["text"])
        mp3 = os.path.join(CACHE, lid + ".mp3")
        for attempt in range(5):
            try:
                await tts(say, voice, rate, pitch, mp3)
                if os.path.getsize(mp3) > 0:
                    break
                print("empty", lid)
            except Exception as e:  # сеть иногда отваливается
                print("retry", lid, e)
            await asyncio.sleep(2)
        if not os.path.exists(mp3) or os.path.getsize(mp3) == 0:
            print("FAILED", lid)
            continue
        process(mp3, dst, flt, LOUD.get(ln["voice"], "-14"))
        print("ok", lid)


if __name__ == "__main__":
    asyncio.run(main())
