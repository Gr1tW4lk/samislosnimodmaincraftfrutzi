"""Скачивает CC0-звуки с freesound (превью) в отдельную папку для дальнейшей обработки.

Лицензия CC0 — можно использовать где угодно, включая монетизируемые видео.
Запуск: python -I tools/fetch_sfx.py <папка_для_сырых_файлов>
"""
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

QUERIES = {
    "goose_honk": "goose honk",
    "goose_hiss": "goose hiss",
    "slap": "slap face",
    "cash": "cash register",
    "stamp": "rubber stamp",
    "courier_bell": "doorbell",
    "splat": "splat",
    "whoosh": "whoosh",
    "tiny_steps": "footsteps running small",
    "siren": "police siren",
    "mimic_growl": "monster growl",
    "mimic_chomp": "bite chomp",
    "sniper_shot": "rifle shot",
    "parachute": "parachute",
    "gift_open": "party popper",
    "bug_skitter": "insect skitter",
    "slam": "heavy impact",
    "tree_creak": "wood creak",
    "tree_fall": "tree falling",
    "rumble": "rumble earthquake",
}


def search(q, n=4):
    url = "https://freesound.org/search/?q=" + urllib.parse.quote(q) + "&f=license%3A%22Creative+Commons+0%22"
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    html = urllib.request.urlopen(req, timeout=30).read().decode("utf-8", "ignore")
    found = []
    for m in re.findall(r"https://cdn\.freesound\.org/previews/[^\"']+?-lq\.mp3", html):
        hq = m.replace("-lq.mp3", "-hq.mp3")
        if hq not in found:
            found.append(hq)
    return found[:n]


def main():
    dest = sys.argv[1]
    os.makedirs(dest, exist_ok=True)
    meta = {}
    only = set(sys.argv[2:])
    for name, q in QUERIES.items():
        if only and name not in only:
            continue
        try:
            urls = search(q)
        except Exception as e:
            print("search fail", name, e)
            continue
        meta[name] = urls
        for i, u in enumerate(urls):
            path = os.path.join(dest, f"{name}__{i}.mp3")
            if os.path.exists(path):
                continue
            try:
                req = urllib.request.Request(u, headers={"User-Agent": "Mozilla/5.0"})
                data = urllib.request.urlopen(req, timeout=60).read()
                with open(path, "wb") as fh:
                    fh.write(data)
                print("ok", name, i, len(data))
            except Exception as e:
                print("dl fail", name, i, e)
            time.sleep(0.4)
    with open(os.path.join(dest, "sources.json"), "w", encoding="utf-8") as fh:
        json.dump(meta, fh, indent=1)


if __name__ == "__main__":
    main()
