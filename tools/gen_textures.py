"""Генератор пиксель-арт текстур мода. Запуск: python tools/gen_textures.py"""
import math
import os
import random
import sys

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import models  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets")
TEX = os.path.join(ASSETS, "hardest", "textures")


def out(path):
    full = os.path.join(TEX, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    return full


R = random.Random(1337)


def clamp(v):
    return max(0, min(255, int(v)))


def jitter(c, var):
    d = R.randint(-var, var)
    if len(c) == 4:
        return (clamp(c[0] + d), clamp(c[1] + d), clamp(c[2] + d), c[3])
    return (clamp(c[0] + d), clamp(c[1] + d), clamp(c[2] + d), 255)


def mul(c, k):
    return (clamp(c[0] * k), clamp(c[1] * k), clamp(c[2] * k), c[3] if len(c) == 4 else 255)


def fill(img, rect, color, var=0):
    x, y, w, h = rect
    for i in range(w):
        for j in range(h):
            img.putpixel((x + i, y + j), jitter(color, var) if var else (color if len(color) == 4 else color + (255,)))


def px(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c if len(c) == 4 else c + (255,))


def box(img, cube, color, var=6, shade=True, faces=None):
    """Покрасить все грани куба: верх светлее, низ темнее, бока чуть темнее."""
    f = models.faces(cube)
    for name, rect in f.items():
        if faces and name not in faces:
            continue
        k = 1.0
        if shade:
            k = {"top": 1.08, "bottom": 0.72, "front": 1.0, "back": 0.86, "left": 0.92, "right": 0.92}[name]
        fill(img, rect, mul(color, k), var)


def find(model, name):
    for c in model.all_cubes():
        if c.name == name:
            return c
    raise KeyError(name)


def face(model, cube, name):
    return models.faces(find(model, cube))[name]


def border(img, rect, color):
    x, y, w, h = rect
    for i in range(w):
        px(img, x + i, y, color)
        px(img, x + i, y + h - 1, color)
    for j in range(h):
        px(img, x, y + j, color)
        px(img, x + w - 1, y + j, color)


def new(w, h):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


# ===================================================================== ГУСЬ

def goose():
    m = models.goose()
    img = new(m.tex_w, m.tex_h)
    white = (238, 238, 232)
    for c in ("body", "tail", "neck", "head"):
        box(img, find(m, c), white, 5)
    # перья на теле — лёгкие полосы
    x, y, w, h = face(m, "body", "left")
    for j in range(0, h, 2):
        for i in range(w):
            if (i + j) % 3 == 0:
                px(img, x + i, y + j, (215, 215, 210))
    x, y, w, h = face(m, "body", "right")
    for j in range(0, h, 2):
        for i in range(w):
            if (i + j) % 3 == 0:
                px(img, x + i, y + j, (215, 215, 210))
    # крылья
    for c in ("wing_l", "wing_r"):
        box(img, find(m, c), (226, 226, 220), 5)
        for side in ("left", "right"):
            x, y, w, h = face(m, c, side)
            for j in range(h):
                for i in range(w):
                    if (i - j) % 3 == 0:
                        px(img, x + i, y + j, (196, 196, 190))
            for i in range(w):
                px(img, x + i, y + h - 1, (175, 175, 170))
    # глаза по бокам головы, ближе к клюву
    x, y, w, h = face(m, "head", "right")
    px(img, x + w - 2, y + 1, (20, 20, 20))
    px(img, x + w - 3, y + 1, (40, 40, 40))
    x, y, w, h = face(m, "head", "left")
    px(img, x + 1, y + 1, (20, 20, 20))
    px(img, x + 2, y + 1, (40, 40, 40))
    # клюв
    orange = (245, 150, 25)
    box(img, find(m, "beak"), orange, 6)
    box(img, find(m, "jaw"), (226, 128, 20), 6)
    x, y, w, h = face(m, "beak", "bottom")
    fill(img, (x, y, w, h), (200, 70, 80))
    x, y, w, h = face(m, "jaw", "top")
    fill(img, (x, y, w, h), (200, 70, 80))
    x, y, w, h = face(m, "beak", "front")
    fill(img, (x, y, w, h), (60, 40, 20))  # тёмный кончик
    # лапы
    for c in ("leg_l", "leg_r", "foot_l", "foot_r"):
        box(img, find(m, c), (240, 140, 30), 8)
    img.save(out("entity/goose.png"))


# ===================================================================== БЛОК-БЕГЛЕЦ

def runaway():
    m = models.runaway()
    img = new(m.tex_w, m.tex_h)
    for c in ("leg_l", "leg_r"):
        box(img, find(m, c), (60, 62, 75), 4)
    for c in ("shoe_l", "shoe_r"):
        box(img, find(m, c), (215, 40, 40), 6)
        x, y, w, h = face(m, c, "bottom")
        fill(img, (x, y, w, h), (240, 240, 240))
        for side in ("front", "left", "right", "back"):
            x, y, w, h = face(m, c, side)
            for i in range(w):
                px(img, x + i, y + h - 1, (240, 240, 240))
    for c in ("arm_l", "arm_r"):
        box(img, find(m, c), (60, 62, 75), 4)
    for c in ("hand_l", "hand_r"):
        box(img, find(m, c), (245, 245, 245), 3)
    for c in ("eye_l", "eye_r"):
        box(img, find(m, c), (250, 250, 250), 0, shade=False)
        x, y, w, h = face(m, c, "front")
        border(img, (x, y, w, h), (25, 25, 25))
        px(img, x + 1, y + 1, (15, 15, 15))
    x, y, w, h = face(m, "mouth", "front")
    fill(img, (x, y, w, h), (25, 10, 10))
    for i in range(w):
        px(img, x + i, y, (245, 245, 245))
        px(img, x + i, y + h - 1, (210, 60, 70))
    for side in ("top", "bottom", "left", "right", "back"):
        fill(img, face(m, "mouth", side), (25, 10, 10))
    img.save(out("entity/runaway_block.png"))


# ===================================================================== НАЛОГОВИК

def tax():
    m = models.tax()
    img = new(m.tex_w, m.tex_h)
    skin = (222, 176, 142)
    hair = (70, 62, 55)
    box(img, find(m, "head"), skin, 4)
    # волосы: макушка с лысиной, затылок
    x, y, w, h = face(m, "head", "top")
    fill(img, (x, y, w, h), hair, 6)
    fill(img, (x + 2, y + 2, w - 4, h - 4), mul(skin, 1.05), 3)
    for i in range(w):
        px(img, x + i, y + 3, hair)  # «зачёс»
    x, y, w, h = face(m, "head", "back")
    fill(img, (x, y, w, 6), hair, 6)
    for side in ("left", "right"):
        x, y, w, h = face(m, "head", side)
        fill(img, (x, y, w, 3), hair, 6)
        px(img, x + w // 2, y + 5, mul(skin, 0.8))  # ухо
        px(img, x + w // 2, y + 6, mul(skin, 0.8))
    # лицо
    x, y, w, h = face(m, "head", "front")
    fill(img, (x, y, w, 2), hair, 6)
    for i in (1, 2, 5, 6):
        px(img, x + i, y + 3, (60, 45, 35))  # брови, хмурые
    px(img, x + 2, y + 5, (250, 250, 250))
    px(img, x + 1, y + 5, (40, 40, 60))
    px(img, x + 5, y + 5, (250, 250, 250))
    px(img, x + 6, y + 5, (40, 40, 60))
    for i in range(2, 6):
        px(img, x + i, y + 7, (85, 70, 55))  # усы
    for i in range(3, 5):
        px(img, x + i, y + 8, (150, 90, 85))  # рот
    box(img, find(m, "nose"), mul(skin, 0.9), 3)
    # очки: только оправа, стёкла прозрачные
    c = find(m, "glasses")
    x, y, w, h = models.faces(c)["front"]
    for i in range(w):
        px(img, x + i, y, (20, 20, 20))
    for i in (0, 3, 4, 7):
        px(img, x + i, y + 1, (20, 20, 20))
    for side in ("left", "right"):
        fill(img, models.faces(c)[side], (20, 20, 20))
    # костюм
    suit = (52, 56, 70)
    box(img, find(m, "body"), suit, 3)
    x, y, w, h = face(m, "body", "front")
    for j in range(5):
        for i in range(3 - j // 2, 5 + j // 2):
            px(img, x + i, y + j, (240, 240, 240))
    for j in range(1, 9):
        px(img, x + 3, y + j, (170, 25, 35))
        px(img, x + 4, y + j, (190, 30, 40))
    # пальто
    coat = (60, 62, 74)
    cc = find(m, "coat")
    box(img, cc, coat, 4)
    fr = models.faces(cc)
    x, y, w, h = fr["front"]
    for j in range(7):
        for i in range(2 + j // 3, 6 - j // 3):
            px(img, x + i, y + j, (0, 0, 0, 0))
    for j in range(h):
        px(img, x + 3, y + j, mul(coat, 0.7)) if j >= 7 else None
    px(img, x + 2, y + 9, (200, 180, 90))
    px(img, x + 2, y + 12, (200, 180, 90))
    for name in ("top",):
        x, y, w, h = fr[name]
        fill(img, (x + 1, y + 1, w - 2, h - 2), (0, 0, 0, 0))
    x, y, w, h = fr["bottom"]
    fill(img, (x, y, w, h), (0, 0, 0, 0))
    # руки
    for c in ("arm_r", "arm_l"):
        box(img, find(m, c), coat, 4)
        for side in ("front", "back", "left", "right"):
            x, y, w, h = face(m, c, side)
            fill(img, (x, y + h - 3, w, 1), (235, 235, 235))
            fill(img, (x, y + h - 2, w, 2), skin, 3)
        fill(img, face(m, c, "bottom"), skin, 3)
    # ноги
    for c in ("leg_r", "leg_l"):
        box(img, find(m, c), (42, 44, 54), 3)
        for side in ("front", "back", "left", "right"):
            x, y, w, h = face(m, c, side)
            fill(img, (x, y + h - 2, w, 2), (18, 18, 20))
    # портфель
    box(img, find(m, "case"), (112, 70, 36), 6)
    for side in ("left", "right"):
        x, y, w, h = face(m, "case", side)
        border(img, (x, y, w, h), (75, 45, 22))
        px(img, x + 2, y + 1, (230, 190, 60))
        px(img, x + w - 3, y + 1, (230, 190, 60))
    box(img, find(m, "handle"), (40, 30, 20), 3)
    # штамп
    box(img, find(m, "stamp_handle"), (145, 95, 50), 5)
    box(img, find(m, "stamp_base"), (60, 60, 65), 4)
    fill(img, face(m, "stamp_base", "bottom"), (190, 20, 25))
    img.save(out("entity/tax_inspector.png"))


# ===================================================================== МИМИК

def planks(img, rect, base=(150, 102, 55)):
    x, y, w, h = rect
    for j in range(h):
        for i in range(w):
            c = jitter(base, 7)
            if j % 4 == 3:
                c = mul(c, 0.75)
            px(img, x + i, y + j, c)


def mimic():
    m = models.mimic()
    img = new(m.tex_w, m.tex_h)
    iron = (95, 95, 100)
    for c in ("base", "lid"):
        cube = find(m, c)
        for name, rect in models.faces(cube).items():
            planks(img, rect, mul((150, 102, 55), {"top": 1.05, "bottom": 0.75}.get(name, 0.95)))
            x, y, w, h = rect
            # железные уголки
            for j in range(h):
                px(img, x, y + j, iron)
                px(img, x + w - 1, y + j, iron)
            for i in range(w):
                px(img, x + i, y, iron)
                px(img, x + i, y + h - 1, iron)
    # пасть внутри
    x, y, w, h = face(m, "base", "top")
    for j in range(h):
        for i in range(w):
            d = min(i, j, w - 1 - i, h - 1 - j)
            px(img, x + i, y + j, (clamp(140 - d * 12), 15, 20) if d > 0 else iron)
    x, y, w, h = face(m, "lid", "bottom")
    for j in range(h):
        for i in range(w):
            d = min(i, j, w - 1 - i, h - 1 - j)
            c = (clamp(150 - d * 10), 20, 25) if d > 0 else iron
            if d > 1 and j % 3 == 0:
                c = mul(c, 0.7)
            px(img, x + i, y + j, c)
    box(img, find(m, "lock"), (225, 185, 50), 6)
    x, y, w, h = face(m, "lock", "front")
    px(img, x, y + 1, (30, 20, 10))
    px(img, x + 1, y + 1, (30, 20, 10))
    # зубы
    tooth = (242, 240, 228)
    for c in ("ut_front", "ut_left", "ut_right"):
        cube = find(m, c)
        for name, (x, y, w, h) in models.faces(cube).items():
            for i in range(w):
                px(img, x + i, y, tooth)
                if h > 1 and i % 2 == 0:
                    px(img, x + i, y + 1, tooth)
            if name in ("top", "bottom"):
                fill(img, (x, y, w, h), tooth)
    for c in ("lt_front", "lt_left", "lt_right"):
        cube = find(m, c)
        for name, (x, y, w, h) in models.faces(cube).items():
            for i in range(w):
                px(img, x + i, y + h - 1, tooth)
                if h > 1 and i % 2 == 1:
                    px(img, x + i, y + h - 2, tooth)
            if name in ("top", "bottom"):
                fill(img, (x, y, w, h), tooth)
    box(img, find(m, "tongue"), (220, 80, 105), 8)
    x, y, w, h = face(m, "tongue", "top")
    for j in range(h):
        px(img, x + w // 2, y + j, (180, 50, 75))
    # глаза внутри крышки
    cube = find(m, "eyes")
    for name, (x, y, w, h) in models.faces(cube).items():
        fill(img, (x, y, w, h), (25, 5, 5))
    x, y, w, h = models.faces(cube)["bottom"]
    for ex in (1, w - 4):
        fill(img, (x + ex, y, 3, h), (255, 225, 20))
        for j in range(h):
            px(img, x + ex + 1, y + j, (10, 10, 10))
    x, y, w, h = models.faces(cube)["front"]
    for ex in (1, w - 4):
        fill(img, (x + ex, y, 3, h), (255, 225, 20))
        px(img, x + ex + 1, y, (10, 10, 10))
    for c in ("leg_fl", "leg_fr", "leg_bl", "leg_br"):
        box(img, find(m, c), (80, 55, 30), 6)
    img.save(out("entity/mimic.png"))


# ===================================================================== БАГ

def creeper_face(img, x, y):
    dark = (15, 30, 15)
    for (i, j) in [(1, 2), (2, 2), (1, 3), (2, 3), (5, 2), (6, 2), (5, 3), (6, 3),
                   (3, 4), (4, 4), (2, 5), (3, 5), (4, 5), (5, 5), (2, 6), (5, 6)]:
        px(img, x + i, y + j, dark)


def courier():
    img = new(64, 32)
    green = (90, 175, 70)

    def area(u, v, w, h, d, color, var=14):
        for rect in [(u + d, v, w, d), (u + d + w, v, w, d), (u, v + d, d, h), (u + d, v + d, w, h),
                     (u + d + w, v + d, d, h), (u + 2 * d + w, v + d, w, h)]:
            fill(img, rect, color, var)
    area(0, 0, 8, 8, 8, green)
    creeper_face(img, 8, 8)
    area(0, 16, 4, 6, 4, mul(green, 0.85))
    # тело в жилетке
    area(16, 16, 8, 12, 4, (205, 225, 40), 8)
    for rx, ry, rw in [(20, 20, 8), (16, 20, 4), (28, 20, 4), (32, 20, 8)]:
        for yy in (ry + 4, ry + 8):
            fill(img, (rx, yy, rw, 1), (210, 210, 215))
    # зелёная шея/вырез
    fill(img, (21, 20, 6, 2), green, 10)
    img.save(out("entity/courier.png"))

    m = models.courier_extra()
    ex = new(m.tex_w, m.tex_h)
    yellow = (245, 200, 30)
    box(ex, find(m, "cap"), yellow, 5)
    box(ex, find(m, "visor"), mul(yellow, 0.85), 4)
    x, y, w, h = face(m, "cap", "front")
    fill(ex, (x + 3, y, 3, 2), (40, 160, 60))
    box(ex, find(m, "pack"), yellow, 6)
    box(ex, find(m, "pack_lid"), mul(yellow, 0.85), 4)
    # логотип на спине: монитор с улыбкой
    x, y, w, h = face(m, "pack", "back")
    fill(ex, (x + 2, y + 2, w - 4, h - 4), (25, 25, 25))
    fill(ex, (x + 3, y + 3, w - 6, h - 6), (15, 40, 20))
    px(ex, x + 4, y + 4, (60, 255, 110))
    px(ex, x + w - 5, y + 4, (60, 255, 110))
    for i in range(4, w - 4):
        px(ex, x + i, y + h - 5, (60, 255, 110))
    px(ex, x + 3, y + h - 6, (60, 255, 110))
    px(ex, x + w - 4, y + h - 6, (60, 255, 110))
    ex.save(out("entity/courier_extra.png"))


# ===================================================================== ПАРАШЮТ

def parachute():
    m = models.parachute()
    img = new(m.tex_w, m.tex_h)
    for c in ("top", "panel_n", "panel_s", "panel_w", "panel_e"):
        for name, (x, y, w, h) in models.faces(find(m, c)).items():
            for i in range(w):
                for j in range(h):
                    stripe = ((i + j) // 3) % 2 if c in ("top",) else (i // 3) % 2
                    col = (225, 45, 45) if stripe == 0 else (245, 245, 245)
                    px(img, x + i, y + j, jitter(col, 5))
    for i in range(1, 5):
        box(img, find(m, f"rope{i}"), (205, 205, 200), 6)
    img.save(out("entity/parachute.png"))


# ===================================================================== ДВОЙНИК

def clone_eyes():
    img = new(64, 64)
    for (x, y) in [(9, 12), (10, 12), (13, 12), (14, 12)]:
        px(img, x, y, (255, 30, 30))
    for (x, y) in [(9, 11), (14, 11)]:
        px(img, x, y, (160, 10, 10))
    img.save(out("entity/clone_eyes.png"))


# ===================================================================== ПРЕДМЕТЫ 16x16

def sprite(rows, palette):
    img = new(16, 16)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != "." and ch in palette:
                px(img, x, y, palette[ch])
    return img


def cannon():
    """Драконобой 9000: тёмный корпус, бирюзовое ядро, светящееся дуло."""
    sprite([
        "............kkk.",
        "...........kCck.",
        "..........kcCck.",
        ".........kmlck..",
        "........kmlmk...",
        ".......kmlmk....",
        "......kdmlmk....",
        ".....kdmcmk.....",
        "....kddmCk......",
        "...kddmdkk......",
        "..kgkdddk.......",
        ".kggkkdk........",
        ".kggk.kk........",
        "..kgk...........",
        "...k............",
        "................",
    ], {"k": (18, 10, 28), "d": (52, 34, 78), "m": (92, 58, 140), "l": (150, 112, 205),
        "c": (80, 235, 255), "C": (210, 255, 255), "g": (60, 44, 52)}).save(out("item/dragon_cannon.png"))


def items():
    sprite([
        "................",
        "......kkkk......",
        ".....kRrRRk.....",
        "....kRRrRRrk....",
        "....krrrrrrk....",
        "....kRRrRRrk....",
        "....kRRrRRrk....",
        "....kwwwwwwk....",
        "....kbBBBBbk....",
        "....kbBBBBbk....",
        "....kbBBBBbk....",
        ".....kbBBbk.....",
        ".....kbBBbk.....",
        ".....kbBBbk.....",
        "......kkkk......",
        "................",
    ], {"k": (45, 25, 20), "R": (205, 40, 45), "r": (140, 20, 30), "w": (245, 240, 225), "b": (125, 82, 48), "B": (165, 115, 70)}).save(out("item/slipper.png"))

    sprite([
        "................",
        ".kkkkkkkkkkkkkk.",
        ".kLLLLLLLLLLLLk.",
        ".kLssssssssssDk.",
        ".kLsppppppppsDk.",
        ".kLspZZZZZZpsDk.",
        ".kLspppppZppsDk.",
        ".kLsppppZpppsDk.",
        ".kLspppZppppsDk.",
        ".kLsppZpppppsDk.",
        ".kLspZZZZZZpsDk.",
        ".kLsppppppppsDk.",
        ".kLssssssssssDk.",
        ".kDDDDDDDDDDDDk.",
        ".kkkkkkkkkkkkkk.",
        "................",
    ], {"k": (30, 30, 35), "L": (235, 235, 240), "D": (120, 120, 130), "s": (200, 200, 210), "p": (215, 215, 222), "Z": (150, 40, 220)}).save(out("item/ctrl_z.png"))

    sprite([
        "......y.........",
        ".....yoy...y....",
        "....yoroy.yoy...",
        "....yorroyoroy..",
        "...yorRRroRrroy.",
        "...kkkkkkkkkkk..",
        "..kbbbbbbbbbbbk.",
        ".kbbbdbbbbbdbbbk",
        ".kbbbbbbdbbbbbbk",
        ".kbdbbbbbbbbdbbk",
        ".kbbbbbbbbbbbbbk",
        "..kbbbbdbbbbbbk.",
        "..kbbbbbbbbbbk..",
        "...kkbbbbbbkk...",
        ".....kkkkkk.....",
        "................",
    ], {"y": (255, 230, 80), "o": (255, 150, 30), "r": (240, 70, 20), "R": (200, 20, 10), "k": (70, 40, 20), "b": (190, 140, 80), "d": (130, 90, 50)}).save(out("item/hot_potato.png"))

    sprite([
        "................",
        "............wW..",
        "..........wwWw..",
        ".........wwWww..",
        "........wwWwww..",
        ".......wwWwwg...",
        "......wwWwwg....",
        ".....wwWwwg.....",
        "....wwWwwg......",
        "...wwWwwg.......",
        "...wWwwg........",
        "..wWwg..........",
        "..Wg............",
        ".q..............",
        "q...............",
        "................",
    ], {"w": (245, 245, 240), "W": (200, 200, 195), "g": (210, 210, 205), "q": (150, 150, 140)}).save(out("item/goose_feather.png"))

    sprite([
        "................",
        "...pppppppppp...",
        "...pnnnnnnnnp...",
        "...pppppppppp...",
        "...plllllllpp...",
        "...pppppppppp...",
        "...pllllllllp...",
        "...pppppppppp...",
        "...plllllpppp...",
        "...pppppprrrp...",
        "...ppppprpprr...",
        "...pplllrrrrr...",
        "...ppppppprrp...",
        "...p.pp.pp.pp...",
        "................",
        "................",
    ], {"p": (245, 240, 225), "n": (40, 40, 40), "l": (150, 150, 160), "r": (200, 30, 40)}).save(out("item/tax_receipt.png"))

    sprite([
        "................",
        "................",
        "..wwwwwwwwwwww..",
        ".wWWWWWWWWWWWWw.",
        ".wWWWWWWWWWWWWw.",
        ".wWWfWWWWWWfWWw.",
        ".wWWWWWWWWWWWWw.",
        ".wWWWWWWWfWWWWw.",
        ".wWWWfWWWWWWWWw.",
        ".wWWWWWWWWWWfWw.",
        ".wWWWWWWWWWWWWw.",
        "..wwwwwwwwwwww..",
        "................",
        "................",
        "................",
        "................",
    ], {"w": (200, 200, 210), "W": (245, 245, 250), "f": (225, 225, 230)}).save(out("item/goose_pillow.png"))

    sprite([
        "......s.s.......",
        ".......s.s......",
        "......s.s.......",
        "..kkkkkkkkkkk...",
        "..kGGGGGGGGGk...",
        "..kGRRRRRRRGkkk.",
        "..kGRrRRrRRGk.k.",
        "..kGRRRrRRRGk.k.",
        "..kGRRRRRRRGkkk.",
        "..kGRRrRRRRGk...",
        "..kGGGGGGGGGk...",
        "...kGGGGGGGk....",
        "....kkkkkkk.....",
        "...ddddddddd....",
        "................",
        "................",
    ], {"s": (230, 230, 240), "k": (60, 40, 30), "G": (240, 235, 225), "R": (190, 30, 60), "r": (240, 90, 120), "d": (120, 80, 50)}).save(out("item/raspberry_tea.png"))

    mush = {"M": (230, 60, 200), "m": (160, 30, 150), "w": (255, 230, 255), "s": (235, 225, 210), "S": (190, 180, 165), "g": (90, 255, 140)}
    rows = [
        "................",
        "................",
        ".....mmmmmm.....",
        "...mMMwMMMMMm...",
        "..mMMMMMMwMMMm..",
        "..MwMMgMMMMMwM..",
        ".mMMMMMMMMgMMMm.",
        ".mmmmmmmmmmmmmm.",
        "......ssss......",
        "......sSss......",
        "......ssSs......",
        "......ssss......",
        ".....sSssSs.....",
        "................",
        "................",
        "................",
    ]
    sprite(rows, mush).save(out("item/strange_mushroom.png"))
    sprite(rows, mush).save(out("block/strange_mushroom.png"))

    # иконка эффекта «Простуда» 18x18
    ic = new(18, 18)
    for y in range(18):
        for x in range(18):
            dx, dy = x - 8.5, y - 8.5
            if dx * dx + dy * dy < 64:
                px(ic, x, y, (150, 210, 240) if dx * dx + dy * dy < 36 else (90, 160, 210))
    for (x, y) in [(6, 7), (11, 7), (7, 12), (8, 12), (9, 12), (10, 12), (8, 10), (9, 10)]:
        px(ic, x, y, (30, 60, 90))
    for (x, y) in [(8, 13), (8, 14), (9, 15)]:
        px(ic, x, y, (120, 230, 90))
    p2 = os.path.join(TEX, "mob_effect", "cold.png")
    os.makedirs(os.path.dirname(p2), exist_ok=True)
    ic.save(p2)


# ===================================================================== БЛОКИ

def blocks():
    red, ribbon = (205, 35, 45), (250, 205, 40)
    side = new(16, 16)
    for x in range(16):
        for y in range(16):
            c = jitter(red, 6)
            if 7 <= x <= 8:
                c = jitter(ribbon, 6)
            if y in (0, 15) or x in (0, 15):
                c = mul(c, 0.8)
            px(side, x, y, c)
    side.save(out("block/gift_box_side.png"))
    top = new(16, 16)
    for x in range(16):
        for y in range(16):
            c = jitter(red, 6)
            if 7 <= x <= 8 or 7 <= y <= 8:
                c = jitter(ribbon, 6)
            if y in (0, 15) or x in (0, 15):
                c = mul(c, 0.8)
            px(top, x, y, c)
    # бантик
    for (x, y) in [(5, 5), (6, 6), (4, 6), (5, 7), (10, 5), (9, 6), (11, 6), (10, 7), (5, 9), (10, 9), (4, 10), (11, 10)]:
        px(top, x, y, (255, 230, 90))
    top.save(out("block/gift_box_top.png"))
    bottom = new(16, 16)
    fill(bottom, (0, 0, 16, 16), mul(red, 0.7), 5)
    bottom.save(out("block/gift_box_bottom.png"))




# ===================================================================== ПРОБУЖДЁННЫЙ ЭНДЕР-ДРАКОН

def dragon():
    m = models.dragon()
    img = new(m.tex_w, m.tex_h)
    glow = new(m.tex_w, m.tex_h)
    rnd = random.Random(77)
    TRANSPARENT = (0, 0, 0, 0)

    def gp(x, y, c):
        if 0 <= x < glow.width and 0 <= y < glow.height:
            glow.putpixel((x, y), c + (255,))

    def scales(rect, base, belly=False):
        x, y, w, h = rect
        for j in range(h):
            for i in range(w):
                c = base
                if ((i // 2) + (j // 2)) % 2 == 0:
                    c = mul(c, 1.18)
                if (i + j * 3) % 7 == 0:
                    c = mul(c, 0.8)
                c = jitter(c, 4)
                if belly and j % 3 == 0:
                    c = mul(c, 0.75)
                px(img, x + i, y + j, c)
        # светящиеся трещины
        if w * h > 60 and not belly:
            for _ in range(max(1, w * h // 260)):
                cx, cy = x + rnd.randrange(w), y + rnd.randrange(h)
                for _k in range(rnd.randint(3, 8)):
                    if x <= cx < x + w and y <= cy < y + h:
                        px(img, cx, cy, (120, 50, 170))
                        gp(cx, cy, (150, 40, 220))
                    cx += rnd.choice((-1, 0, 1))
                    cy += rnd.choice((0, 1))

    for c in m.all_cubes():
        if c.share:
            continue
        f = models.faces(c)
        tag = c.tag
        for name, rect in f.items():
            x, y, w, h = rect
            if w <= 0 or h <= 0:
                continue
            if tag in ("scale", "jaw", "snout"):
                base = (30, 21, 42) if name != "bottom" else (58, 46, 70)
                scales(rect, base, belly=(name == "bottom"))
                if tag == "jaw" and name == "top":
                    fill(img, rect, (95, 18, 38), 6)
                    for i in range(0, w, 3):
                        px(img, x + i, y, (140, 40, 60))
                if tag == "snout" and name == "bottom":
                    fill(img, rect, (85, 16, 34), 5)
            elif tag == "plate":
                fill(img, rect, (52, 36, 72), 5)
                for i in range(w):
                    px(img, x + i, y, (96, 74, 128))
                for j in range(0, h, 4):
                    px(img, x, y + j, (120, 100, 150))
            elif tag == "spike":
                for j in range(h):
                    k = 1.0 + (1.0 - j / max(1, h - 1)) * 0.9
                    for i in range(w):
                        px(img, x + i, y + j, jitter(mul((58, 44, 78), k), 4))
            elif tag == "belly":
                scales(rect, (66, 54, 80), belly=True)
            elif tag == "bone":
                fill(img, rect, (36, 26, 48), 4)
                for i in range(w):
                    px(img, x + i, y + h // 2, (66, 50, 86))
            elif tag in ("membrane", "membrane_tip"):
                tip = tag == "membrane_tip"
                for j in range(h):
                    for i in range(w):
                        t = j / max(1, h - 1)  # 0 — задний край, 1 — у кости
                        c = (int(84 - 30 * t), int(26 - 10 * t), int(118 - 40 * t))
                        cut = 2 + 6 * abs(math.sin(math.pi * i / 21.0))
                        if tip:
                            cut += (i / w) ** 2 * 34
                        if j < cut:
                            px(img, x + i, y + j, TRANSPARENT)
                            continue
                        px(img, x + i, y + j, jitter(c, 3))
                # прожилки
                for vx in ([10, 34, 56] if not tip else [8, 30, 52]):
                    cx = vx
                    for j in range(h - 1, -1, -1):
                        cx += rnd.choice((-1, 0, 0, 1))
                        cx = max(0, min(w - 1, cx))
                        if img.getpixel((x + cx, y + j))[3] > 0:
                            px(img, x + cx, y + j, (130, 60, 170))
                            gp(x + cx, y + j, (70, 0, 100))
            elif tag == "horn":
                for j in range(h):
                    for i in range(w):
                        c = (140, 124, 132)
                        if (j + i) % 5 == 0:
                            c = (110, 96, 104)
                        px(img, x + i, y + j, jitter(c, 5))
            elif tag == "horn_tip":
                fill(img, rect, (205, 196, 196), 5)
            elif tag == "claw":
                fill(img, rect, (196, 186, 176), 5)
            elif tag == "teeth":
                fill(img, rect, (238, 232, 216), 3)
            elif tag == "eye":
                fill(img, rect, (245, 120, 255))
                for j in range(h):
                    for i in range(w):
                        edge = i in (0, w - 1) or j in (0, h - 1)
                        gp(x + i, y + j, (220, 40, 255) if edge else (255, 210, 255))
            elif tag == "crystal":
                for j in range(h):
                    for i in range(w):
                        c = (200, 130, 255) if (i + j) % 2 == 0 else (240, 190, 255)
                        px(img, x + i, y + j, c)
                        gp(x + i, y + j, c[:3])
            elif tag == "nostril":
                fill(img, rect, (110, 24, 120))
                fill(glow, rect, (170, 30, 200))
            elif tag == "blade":
                fill(img, rect, (48, 34, 64), 4)
                for i in range(w):
                    px(img, x + i, y, (200, 160, 240))
                    gp(x + i, y, (180, 60, 255))
                for j in range(h):
                    px(img, x, y + j, (170, 130, 220))
            else:
                fill(img, rect, (60, 40, 80), 4)
    img.save(out("entity/dragon.png"))
    glow.save(out("entity/dragon_glow.png"))
    # ярость (третья фаза): то же свечение, но кроваво-красное
    rage = new(m.tex_w, m.tex_h)
    for yy in range(glow.height):
        for xx in range(glow.width):
            r_, g_, b_, a_ = glow.getpixel((xx, yy))
            if a_ == 0:
                continue
            v = max(r_, g_, b_)
            rage.putpixel((xx, yy), (v, int(v * 0.25), int(v * 0.12), 255))
    rage.save(out("entity/dragon_glow_rage.png"))


# ===================================================================== ИКОНКА

def icon():
    img = Image.new("RGBA", (128, 128), (30, 8, 44, 255))
    d = ImageDraw.Draw(img)
    for i in range(0, 128, 8):
        d.line([0, i, 128, i], fill=(42, 12, 60, 255))
    slip = Image.open(out("item/slipper.png")).resize((96, 96), Image.NEAREST).rotate(-25, resample=Image.NEAREST)
    img.paste(slip, (16, 16), slip)
    img.save(os.path.join(ASSETS, "hardest", "icon.png"))


if __name__ == "__main__":
    goose()
    runaway()
    tax()
    mimic()
    courier()
    parachute()
    clone_eyes()
    items()
    cannon()
    blocks()
    dragon()
    icon()
    print("textures ok")
