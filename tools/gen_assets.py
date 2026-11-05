"""Генерирует JSON-ресурсы мода: модели, блоки, sounds.json, языки, лут, рецепты, генерацию мира, достижения.

Запуск: python tools/gen_assets.py
"""
import json
import os
import re
import subprocess

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
A = os.path.join(RES, "assets", "hardest")
D = os.path.join(RES, "data", "hardest")
FFPROBE = r"D:\Programs\ffmpeg\bin\ffprobe.exe"
NS = "hardest"


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(obj, fh, ensure_ascii=False, indent=2)
        fh.write("\n")


ITEMS = ["slipper", "dragon_cannon", "ctrl_z", "hot_potato", "goose_feather", "goose_pillow", "tax_receipt", "raspberry_tea", "strange_mushroom"]
EGGS = ["goose", "tax_inspector", "mimic", "courier", "doppelganger", "parachute_cow", "sniper_skeleton", "runaway_block"]


def models():
    for it in ITEMS:
        parent = "minecraft:item/handheld" if it in ("slipper", "dragon_cannon") else "minecraft:item/generated"
        write(os.path.join(A, "models", "item", it + ".json"), {"parent": parent, "textures": {"layer0": f"{NS}:item/" + it}})
    for e in EGGS:
        write(os.path.join(A, "models", "item", e + "_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})
    write(os.path.join(A, "models", "block", "gift_box.json"), {
        "parent": "minecraft:block/block",
        "textures": {"particle": f"{NS}:block/gift_box_side", "side": f"{NS}:block/gift_box_side",
                     "top": f"{NS}:block/gift_box_top", "bottom": f"{NS}:block/gift_box_bottom"},
        "elements": [
            {"from": [1.5, 0, 1.5], "to": [14.5, 11, 14.5], "faces": {
                "north": {"uv": [1.5, 5, 14.5, 16], "texture": "#side"}, "south": {"uv": [1.5, 5, 14.5, 16], "texture": "#side"},
                "east": {"uv": [1.5, 5, 14.5, 16], "texture": "#side"}, "west": {"uv": [1.5, 5, 14.5, 16], "texture": "#side"},
                "down": {"texture": "#bottom", "cullface": "down"}, "up": {"texture": "#top"}}},
            {"from": [1, 11, 1], "to": [15, 14, 15], "faces": {
                "north": {"uv": [1, 0, 15, 3], "texture": "#side"}, "south": {"uv": [1, 0, 15, 3], "texture": "#side"},
                "east": {"uv": [1, 0, 15, 3], "texture": "#side"}, "west": {"uv": [1, 0, 15, 3], "texture": "#side"},
                "down": {"texture": "#bottom"}, "up": {"texture": "#top"}}},
        ],
    })
    write(os.path.join(A, "blockstates", "gift_box.json"), {"variants": {"": {"model": f"{NS}:block/gift_box"}}})
    write(os.path.join(A, "models", "item", "gift_box.json"), {"parent": f"{NS}:block/gift_box"})
    write(os.path.join(A, "models", "block", "strange_mushroom.json"), {"parent": "minecraft:block/cross", "textures": {"cross": f"{NS}:block/strange_mushroom"}})
    write(os.path.join(A, "blockstates", "strange_mushroom.json"), {"variants": {"": {"model": f"{NS}:block/strange_mushroom"}}})


def duration_ticks(path):
    out = subprocess.run([FFPROBE, "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path],
                         capture_output=True, text=True).stdout.strip()
    try:
        return int(float(out) * 20 + 0.5)
    except ValueError:
        return 60


def sounds():
    snd, durations = {}, {}
    vdir = os.path.join(A, "sounds", "voice")
    for f in sorted(os.listdir(vdir)):
        if f.endswith(".ogg"):
            vid = f[:-4]
            snd["voice." + vid] = {"sounds": [{"name": f"{NS}:voice/" + vid}]}
            durations[vid] = duration_ticks(os.path.join(vdir, f))
    groups = {}
    for f in sorted(os.listdir(os.path.join(A, "sounds", "sfx"))):
        if f.endswith(".ogg"):
            base = f[:-4]
            m = re.match(r"(.+)_(\d+)$", base)
            groups.setdefault(m.group(1) if m else base, []).append(base)
    for key, files in groups.items():
        snd["sfx." + key] = {"sounds": [{"name": f"{NS}:sfx/" + b} for b in files]}
    snd["music.dragon"] = {"sounds": [{"name": f"{NS}:music/dragon", "stream": True}]}
    write(os.path.join(A, "sounds.json"), snd)
    write(os.path.join(A, "voice_durations.json"), durations)
    print("sounds:", len(snd))


def lang():
    L = {
        "itemGroup.hardest.main": "Самый сложный мод",
        "effect.hardest.cold": "Простуда",
        "item.hardest.slipper": "Мамин тапок",
        "item.hardest.dragon_cannon": "Драконобой 9000",
        "item.hardest.ctrl_z": "Клавиша Ctrl+Z",
        "item.hardest.hot_potato": "Горячая картошка",
        "item.hardest.goose_feather": "Гусиное перо",
        "item.hardest.goose_pillow": "Гусиная подушка",
        "item.hardest.tax_receipt": "Квитанция об уплате налога",
        "item.hardest.raspberry_tea": "Бабушкин чай с малиной",
        "item.hardest.strange_mushroom": "Странный гриб",
        "block.hardest.gift_box": "Посылка",
        "block.hardest.strange_mushroom": "Странный гриб",
        "entity.hardest.goose": "Гусь",
        "entity.hardest.runaway_block": "Блок-беглец",
        "entity.hardest.tax_inspector": "Налоговый инспектор",
        "entity.hardest.mimic": "Мимик",
        "entity.hardest.courier": "Крипер-курьер",
        "entity.hardest.doppelganger": "Двойник",
        "entity.hardest.parachute_cow": "Корова-парашютистка",
        "entity.hardest.sniper_skeleton": "Скелет-снайпер",
        "entity.hardest.slipper": "Тапок",
        "entity.hardest.web_shot": "Комок паутины",
        "hardest.lore.slipper.1": "ПКМ — бросить. Вернётся, как бумеранг",
        "hardest.lore.slipper.2": "Легендарное оружие всех мам",
        "hardest.lore.slipper.3": "x3 урона по Эндер-дракону",
        "hardest.lore.dragon_cannon.1": "Зажми ПКМ — огонь очередью",
        "hardest.lore.dragon_cannon.2": "Дракону −2 HP за выстрел · 128 блоков · автоприцел",
        "hardest.lore.dragon_cannon.3": "Собрано в гараже из арбалета и обиды на дракона",
        "hardest.lore.ctrl_z.1": "Отменяет одну смерть",
        "hardest.lore.ctrl_z.2": "Откатывает тебя на 5 секунд назад",
        "hardest.lore.hot_potato.1": "Ударь ей моба, чтобы передать. Не выбрасывай!",
        "hardest.lore.goose_feather.1": "Гусь был против",
        "hardest.lore.goose_pillow.1": "Пока она в инвентаре, кровать не берёт плату",
        "hardest.lore.goose_pillow.2": "Гуси знают, что ты сделал",
        "hardest.lore.tax_receipt.1": "Оплачено: 13% от всего",
        "hardest.lore.tax_receipt.2": "Покажи инспектору — и он уйдёт ни с чем",
        "hardest.lore.raspberry_tea.1": "Лечит простуду. Бабушка проверила",
        "hardest.lore.strange_mushroom.1": "Очень сытный",
        "hardest.lore.strange_mushroom.2": "Побочные эффекты: да",
    }
    names = {"goose": "гусь", "tax_inspector": "налоговый инспектор", "mimic": "мимик", "courier": "крипер-курьер",
             "doppelganger": "двойник", "parachute_cow": "корова-парашютистка", "sniper_skeleton": "скелет-снайпер",
             "runaway_block": "блок-беглец"}
    for e, n in names.items():
        L[f"item.hardest.{e}_spawn_egg"] = "Яйцо призыва: " + n
    with open(os.path.join(ROOT, "tools", "voice_lines.json"), encoding="utf-8") as fh:
        for ln in json.load(fh):
            L["hardest.voice." + ln["id"]] = ln["text"]
    write(os.path.join(A, "lang", "ru_ru.json"), L)
    write(os.path.join(A, "lang", "en_us.json"), L)
    # ванильные ключи переопределяются только из пространства имён minecraft
    # «ПОТРАЧЕНО» рисует сам мод (WastedScreen) — ванильный заголовок прячем
    vanilla = {"deathScreen.title": "", "deathScreen.title.hardcore": "",
               "entity.minecraft.ender_dragon": "Пробуждённый Эндер-дракон"}
    mc_lang = os.path.join(RES, "assets", "minecraft", "lang")
    write(os.path.join(mc_lang, "ru_ru.json"), vanilla)
    write(os.path.join(mc_lang, "en_us.json"), vanilla)
    print("lang:", len(L))


def data():
    def loot(name, pools):
        write(os.path.join(D, "loot_tables", "entities", name + ".json"), {"type": "minecraft:entity", "pools": pools})

    def item_pool(item, lo, hi):
        return {"rolls": 1, "entries": [{"type": "minecraft:item", "name": item, "functions": [
            {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}},
            {"function": "minecraft:looting_enchant", "count": {"type": "minecraft:uniform", "min": 0, "max": 1}}]}]}

    loot("goose", [item_pool(f"{NS}:goose_feather", 1, 3), item_pool("minecraft:chicken", 1, 2)])
    loot("courier", [item_pool("minecraft:gunpowder", 1, 3)])
    loot("sniper_skeleton", [item_pool("minecraft:bone", 1, 3), item_pool("minecraft:arrow", 2, 6)])
    for n in ("mimic", "tax_inspector", "doppelganger"):
        loot(n, [])
    loot("parachute_cow", [item_pool("minecraft:beef", 1, 3), item_pool("minecraft:leather", 0, 2)])
    write(os.path.join(D, "loot_tables", "blocks", "gift_box.json"), {"type": "minecraft:block", "pools": [
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:gift_box"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    write(os.path.join(D, "loot_tables", "blocks", "strange_mushroom.json"), {"type": "minecraft:block", "pools": [
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:strange_mushroom"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})

    write(os.path.join(D, "recipes", "dragon_cannon.json"), {
        "type": "minecraft:crafting_shaped", "pattern": [" DE", "DCD", "ID "],
        "key": {"D": {"item": "minecraft:diamond"}, "E": {"item": "minecraft:ender_eye"},
                "C": {"item": "minecraft:crossbow"}, "I": {"item": "minecraft:iron_block"}},
        "result": {"item": f"{NS}:dragon_cannon"}})
    write(os.path.join(D, "recipes", "slipper.json"), {
        "type": "minecraft:crafting_shaped", "pattern": [" L ", "LSL", "LLL"],
        "key": {"L": {"item": "minecraft:leather"}, "S": {"item": "minecraft:string"}},
        "result": {"item": f"{NS}:slipper"}})
    write(os.path.join(D, "recipes", "ctrl_z.json"), {
        "type": "minecraft:crafting_shaped", "pattern": ["RFR", "FDF", "RFR"],
        "key": {"R": {"item": "minecraft:redstone"}, "F": {"item": f"{NS}:goose_feather"}, "D": {"item": "minecraft:diamond"}},
        "result": {"item": f"{NS}:ctrl_z"}})
    write(os.path.join(D, "recipes", "goose_pillow.json"), {
        "type": "minecraft:crafting_shaped", "pattern": ["FFF", "WWW"],
        "key": {"F": {"item": f"{NS}:goose_feather"}, "W": {"item": "minecraft:white_wool"}},
        "result": {"item": f"{NS}:goose_pillow"}})
    write(os.path.join(D, "recipes", "raspberry_tea.json"), {
        "type": "minecraft:crafting_shapeless",
        "ingredients": [{"item": "minecraft:glass_bottle"}, {"item": "minecraft:sweet_berries"}, {"item": "minecraft:sweet_berries"}, {"item": "minecraft:sugar"}],
        "result": {"item": f"{NS}:raspberry_tea"}})

    # странные грибы в мире
    write(os.path.join(D, "worldgen", "configured_feature", "strange_mushroom.json"), {
        "type": "minecraft:random_patch", "config": {"tries": 20, "xz_spread": 5, "y_spread": 2, "feature": {
            "feature": {"type": "minecraft:simple_block", "config": {"to_place": {"type": "minecraft:simple_state_provider",
                                                                                 "state": {"Name": f"{NS}:strange_mushroom"}}}},
            "placement": [{"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [
                {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
                {"type": "minecraft:would_survive", "state": {"Name": f"{NS}:strange_mushroom"}}]}}]}}})
    write(os.path.join(D, "worldgen", "placed_feature", "strange_mushroom.json"), {
        "feature": f"{NS}:strange_mushroom",
        "placement": [{"type": "minecraft:rarity_filter", "chance": 5}, {"type": "minecraft:in_square"},
                      {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"}, {"type": "minecraft:biome"}]})


ADVANCEMENTS = [
    ("goose", f"{NS}:goose_feather", "Гусь-грабитель", "Гусь украл у тебя вещь", "task"),
    ("pillow", f"{NS}:goose_pillow", "Гусиный пух", "Сделай гусиную подушку и спи бесплатно", "goal"),
    ("tax", f"{NS}:tax_receipt", "Налоговый резидент", "Заплати 13% налоговой", "task"),
    ("courier", f"{NS}:gift_box", "Распишитесь", "Получи посылку от крипера-курьера", "task"),
    ("captcha_ok", "minecraft:paper", "Я не робот", "Пройди капчу", "task"),
    ("captcha_fail", "minecraft:redstone", "Я робот", "Провали капчу", "task"),
    ("diamond_run", "minecraft:diamond_ore", "Алмазы убежали", "Алмазная руда отрастила ноги", "task"),
    ("catch_block", "minecraft:diamond", "Поймал беглеца", "Догони и сломай убегающий блок", "goal"),
    ("timber", "minecraft:oak_log", "ДЕРЕВО!", "Свали дерево целиком", "task"),
    ("cave_in", "minecraft:gravel", "Обвал", "Попади под обвал в шахте", "task"),
    ("sneeze", "minecraft:snowball", "Будь здоров", "Чихни так, что улетишь назад", "task"),
    ("mushroom", f"{NS}:strange_mushroom", "Грибной трип", "Съешь странный гриб", "task"),
    ("witch", "minecraft:splash_potion", "Мемное зелье", "Попади под ведьмино зелье с побочкой", "task"),
    ("wasted", "minecraft:skeleton_skull", "ПОТРАЧЕНО", "Умри в первый раз", "task"),
    ("slipper_crit", f"{NS}:slipper", "Мамин гнев", "Нанеси критический удар тапком", "goal"),
    ("ctrl_z", f"{NS}:ctrl_z", "Отменить смерть", "Тебя спасла клавиша Ctrl+Z", "goal"),
    ("mimic", "minecraft:chest", "Сундук с зубами", "Разбуди мимика", "task"),
    ("potato", f"{NS}:hot_potato", "Горячо!", "Передай горячую картошку мобу", "task"),
    ("sleep", "minecraft:red_bed", "Платная подписка", "Заплати алмаз за сон", "task"),
    ("blood_moon", "minecraft:redstone_block", "Кровавая луна", "Доживи до рассвета в Кровавую луну", "goal"),
    ("loading", "minecraft:clock", "Загрузка...", "Дождись загрузки Незера или Края", "task"),
    ("dragon_seen", "minecraft:dragon_head", "Он проснулся", "Встреть Пробуждённого Эндер-дракона", "task"),
    ("dragon", "minecraft:dragon_egg", "Самый сложный мод пройден", "Победи Пробуждённого Эндер-дракона", "challenge"),
]


def advancements():
    base = os.path.join(D, "advancements", "main")
    write(os.path.join(base, "root.json"), {
        "display": {"icon": {"item": f"{NS}:slipper"}, "title": "Самый сложный мод", "description": "Пройди майнкрафт. Если сможешь",
                    "background": "minecraft:textures/block/crying_obsidian.png", "frame": "task",
                    "show_toast": False, "announce_to_chat": False},
        "criteria": {"tick": {"trigger": "minecraft:tick"}}})
    for aid, icon, title, desc, frame in ADVANCEMENTS:
        if aid == "pillow":
            criteria = {"has": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": [f"{NS}:goose_pillow"]}]}}}
        else:
            criteria = {"done": {"trigger": "minecraft:impossible"}}
        write(os.path.join(base, aid + ".json"), {
            "parent": f"{NS}:main/root",
            "display": {"icon": {"item": icon}, "title": title, "description": desc, "frame": frame,
                        "show_toast": True, "announce_to_chat": True, "hidden": False},
            "criteria": criteria})


def splashes():
    lines = [
        "Самый сложный мод!", "Гусь уже рядом!", "Налог 13% на всё!", "Сон стоит алмаз!", "Это не баг, это фича!",
        "Тапок x3 урона!", "Ctrl+Z не поможет!", "Пинг 999!", "Капча обязательна!", "Реклама через 5...",
        "Коровопад сегодня!", "Мимик или сундук?", "ПОТРАЧЕНО", "Курьер уже идёт!", "Блоки убегают!",
        "Дерево падает!", "Апчхи!", "Кровавая луна близко!", "Дракон проснулся!", "Не ешь странные грибы!",
        "Наковальни по прогнозу!", "Бабушкин чай лечит всё!", "Минус уши!",
    ]
    path = os.path.join(RES, "assets", "minecraft", "texts", "splashes.txt")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(lines) + "\n")


def clean_old():
    """Удаляем остатки старой версии (модели и достижения, которых больше нет)."""
    keep_items = set(ITEMS) | {e + "_spawn_egg" for e in EGGS} | {"gift_box"}
    d = os.path.join(A, "models", "item")
    if os.path.isdir(d):
        for f in os.listdir(d):
            if f[:-5] not in keep_items:
                os.remove(os.path.join(d, f))
    for rel in (("models", "block", "glitch.json"), ("blockstates", "glitch.json")):
        p = os.path.join(A, *rel)
        if os.path.exists(p):
            os.remove(p)
    adv = os.path.join(D, "advancements", "main")
    ids = {a[0] for a in ADVANCEMENTS} | {"root"}
    if os.path.isdir(adv):
        for f in os.listdir(adv):
            if f[:-5] not in ids:
                os.remove(os.path.join(adv, f))
    for name in ("bug", "iigor_boss", "neuro_clone"):
        p = os.path.join(D, "loot_tables", "entities", name + ".json")
        if os.path.exists(p):
            os.remove(p)
    for rel in (("recipes", "antivirus.json"),):
        p = os.path.join(D, *rel)
        if os.path.exists(p):
            os.remove(p)


if __name__ == "__main__":
    clean_old()
    models()
    sounds()
    lang()
    data()
    advancements()
    splashes()
    print("assets ok")
