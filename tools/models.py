"""Спецификация моделей мобов. Отсюда генерируется Java (ModelDefs) и раскладка UV для текстур.

Координаты — как в Minecraft entity-моделях: Y вниз, ноги на y=24, перед моба смотрит в -Z.
"""
import os


class Cube:
    def __init__(self, name, origin, size, dil=0.0, mirror=False, tag=None, share=None):
        self.name = name
        self.origin = origin
        self.size = size
        self.dil = dil
        self.mirror = mirror
        self.uv = None
        self.tag = tag
        self.share = share


class Part:
    def __init__(self, name, pivot=(0, 0, 0), rot=(0, 0, 0), cubes=(), children=()):
        self.name = name
        self.pivot = pivot
        self.rot = rot
        self.cubes = list(cubes)
        self.children = list(children)


class Model:
    def __init__(self, name, tex_w, tex_h, root_parts):
        self.name = name
        self.tex_w = tex_w
        self.tex_h = tex_h
        self.parts = root_parts

    def all_cubes(self):
        out = []

        def walk(p):
            for c in p.cubes:
                out.append(c)
            for ch in p.children:
                walk(ch)
        for p in self.parts:
            walk(p)
        return out

    def pack(self):
        """Простая полочная упаковка UV-развёрток кубов. Кубы с share берут UV у «оригинала» и зеркалятся."""
        cubes = sorted([c for c in self.all_cubes() if not c.share], key=lambda c: -(c.size[2] + c.size[1]))
        x, y, shelf = 0, 0, 0
        for c in cubes:
            w, h, d = [int(round(v)) for v in c.size]
            uw, uh = 2 * d + 2 * w, d + h
            uw = max(uw, 1)
            uh = max(uh, 1)
            if x + uw > self.tex_w:
                x = 0
                y += shelf
                shelf = 0
            if y + uh > self.tex_h:
                raise RuntimeError(f"{self.name}: не влезает {c.name}")
            c.uv = (x, y)
            x += uw
            shelf = max(shelf, uh)
        by_name = {c.name: c for c in self.all_cubes()}
        for c in self.all_cubes():
            if c.share:
                c.uv = by_name[c.share].uv
                c.mirror = True
        return self


def faces(c):
    """Прямоугольники граней куба на текстуре: (x, y, w, h)."""
    u, v = c.uv
    w, h, d = [int(round(s)) for s in c.size]
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),       # сторона -X модели (у моба — его правый бок)
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


# ------------------------------------------------------------------ модели

def goose():
    return Model("goose", 64, 64, [
        Part("body", (0, 17, 0), cubes=[Cube("body", (-3.5, -3, -5), (7, 6, 11))], children=[
            Part("tail", (0, -2, 5.5), (-0.5, 0, 0), cubes=[Cube("tail", (-2.5, -1.5, -1), (5, 3, 4))]),
        ]),
        Part("neck", (0, 15, -4), cubes=[Cube("neck", (-1.5, -9, -1.5), (3, 10, 3))], children=[
            Part("head", (0, -9, 0), cubes=[Cube("head", (-2, -4, -3.5), (4, 4, 6))], children=[
                Part("beak", (0, -2, -3.5), cubes=[Cube("beak", (-1.5, -1, -4), (3, 1, 4))]),
                Part("jaw", (0, -1, -3.5), cubes=[Cube("jaw", (-1.5, 0, -3), (3, 1, 3))]),
            ]),
        ]),
        Part("left_wing", (3.5, 15, -2), cubes=[Cube("wing_l", (0, -0.5, 0), (1, 5, 9))]),
        Part("right_wing", (-3.5, 15, -2), cubes=[Cube("wing_r", (-1, -0.5, 0), (1, 5, 9))]),
        Part("left_leg", (1.5, 20, 1), cubes=[Cube("leg_l", (-0.5, 0, -0.5), (1, 4, 1)), Cube("foot_l", (-1.5, 3, -2.5), (3, 1, 3))]),
        Part("right_leg", (-1.5, 20, 1), cubes=[Cube("leg_r", (-0.5, 0, -0.5), (1, 4, 1)), Cube("foot_r", (-1.5, 3, -2.5), (3, 1, 3))]),
    ]).pack()


def runaway():
    # сам блок рисуется отдельно (12px куб от y=6 до y=18), здесь — ножки, ручки и лицо
    return Model("runaway_block", 64, 32, [
        Part("left_leg", (3, 18, 0), cubes=[Cube("leg_l", (-1, 0, -1), (2, 5, 2)), Cube("shoe_l", (-1.5, 4, -3), (3, 2, 4))]),
        Part("right_leg", (-3, 18, 0), cubes=[Cube("leg_r", (-1, 0, -1), (2, 5, 2)), Cube("shoe_r", (-1.5, 4, -3), (3, 2, 4))]),
        Part("left_arm", (6, 12, 0), cubes=[Cube("arm_l", (0, -1, -1), (5, 2, 2)), Cube("hand_l", (5, -1.5, -1.5), (2, 3, 3))]),
        Part("right_arm", (-6, 12, 0), cubes=[Cube("arm_r", (-5, -1, -1), (5, 2, 2)), Cube("hand_r", (-7, -1.5, -1.5), (2, 3, 3))]),
        Part("face", (0, 11, -6), cubes=[
            Cube("eye_l", (1, -2.5, -1), (3, 3, 1)),
            Cube("eye_r", (-4, -2.5, -1), (3, 3, 1)),
            Cube("mouth", (-2, 2, -1), (4, 3, 1)),
        ]),
    ]).pack()


def tax():
    return Model("tax_inspector", 128, 64, [
        Part("head", (0, 0, 0), cubes=[Cube("head", (-4, -10, -4), (8, 10, 8))], children=[
            Part("nose", (0, -2, 0), cubes=[Cube("nose", (-1, -1, -6), (2, 4, 2))]),
            Part("glasses", (0, 0, 0), cubes=[Cube("glasses", (-4, -6, -4.6), (8, 2, 1))]),
        ]),
        Part("body", (0, 0, 0), cubes=[Cube("body", (-4, 0, -3), (8, 12, 6)), Cube("coat", (-4, 0, -3), (8, 18, 6), dil=0.4)]),
        Part("right_arm", (-5, 2, 0), cubes=[Cube("arm_r", (-2, -2, -2), (4, 12, 4))], children=[
            Part("briefcase", (-0.5, 9, 0), cubes=[Cube("case", (-1.5, 1, -4.5), (3, 7, 9)), Cube("handle", (-0.5, -0.5, -1.5), (1, 2, 3))]),
        ]),
        Part("left_arm", (5, 2, 0), cubes=[Cube("arm_l", (-2, -2, -2), (4, 12, 4))], children=[
            Part("stamp", (0, 10, 0), cubes=[Cube("stamp_handle", (-1, -1, -1), (2, 3, 2)), Cube("stamp_base", (-1.5, 2, -1.5), (3, 2, 3))]),
        ]),
        Part("right_leg", (-2, 12, 0), cubes=[Cube("leg_r", (-2, 0, -2), (4, 12, 4))]),
        Part("left_leg", (2, 12, 0), cubes=[Cube("leg_l", (-2, 0, -2), (4, 12, 4))]),
    ]).pack()


def mimic():
    return Model("mimic", 64, 128, [
        Part("base", (0, 21, 0), cubes=[Cube("base", (-7, -10, -7), (14, 10, 14))], children=[
            Part("lower_teeth", (0, -10, 0), cubes=[
                Cube("lt_front", (-6, -2, -7), (12, 2, 1)),
                Cube("lt_left", (6, -2, -6), (1, 2, 11)),
                Cube("lt_right", (-7, -2, -6), (1, 2, 11)),
            ]),
            Part("tongue", (0, -10.5, 3), cubes=[Cube("tongue", (-2.5, -0.5, -9), (5, 1, 9))]),
        ]),
        Part("lid", (0, 11, 7), cubes=[Cube("lid", (-7, -5, -14), (14, 5, 14))], children=[
            Part("lock", (0, -2, -14), cubes=[Cube("lock", (-1, -1, -1), (2, 4, 1))]),
            Part("upper_teeth", (0, 0, 0), cubes=[
                Cube("ut_front", (-6, 0, -14), (12, 2, 1)),
                Cube("ut_left", (6, 0, -13), (1, 2, 11)),
                Cube("ut_right", (-7, 0, -13), (1, 2, 11)),
            ]),
            Part("eyes", (0, 0, 0), cubes=[Cube("eyes", (-5, -1, -12), (10, 1, 3))]),
        ]),
        Part("leg_fl", (-5, 21, -5), cubes=[Cube("leg_fl", (-1, 0, -1), (2, 3, 2))]),
        Part("leg_fr", (5, 21, -5), cubes=[Cube("leg_fr", (-1, 0, -1), (2, 3, 2))]),
        Part("leg_bl", (-5, 21, 5), cubes=[Cube("leg_bl", (-1, 0, -1), (2, 3, 2))]),
        Part("leg_br", (5, 21, 5), cubes=[Cube("leg_br", (-1, 0, -1), (2, 3, 2))]),
    ]).pack()


def courier_extra():
    # кепка и термо-рюкзак курьера поверх обычной модели крипера
    return Model("courier_extra", 64, 64, [
        Part("cap", (0, 6, 0), cubes=[Cube("cap", (-4.5, -9, -4.5), (9, 3, 9)), Cube("visor", (-4, -7, -8.5), (8, 1, 4))]),
        Part("backpack", (0, 6, 0), cubes=[Cube("pack", (-5, 0, 2), (10, 10, 7)), Cube("pack_lid", (-5.5, -1, 1.5), (11, 2, 8))]),
    ]).pack()


def parachute():
    return Model("parachute", 128, 64, [
        Part("canopy", (0, -24, 0), cubes=[Cube("top", (-9, -2, -9), (18, 2, 18))], children=[
            Part("panel_n", (0, 0, -9), (0.7, 0, 0), cubes=[Cube("panel_n", (-9, -1, -7), (18, 1, 7))]),
            Part("panel_s", (0, 0, 9), (-0.7, 0, 0), cubes=[Cube("panel_s", (-9, -1, 0), (18, 1, 7))]),
            Part("panel_w", (9, 0, 0), (0, 0, -0.7), cubes=[Cube("panel_w", (0, -1, -9), (7, 1, 18))]),
            Part("panel_e", (-9, 0, 0), (0, 0, 0.7), cubes=[Cube("panel_e", (-7, -1, -9), (7, 1, 18))]),
            Part("rope1", (7, 3, 7), (0.32, 0, 0.32), cubes=[Cube("rope1", (-0.5, 0, -0.5), (1, 24, 1))]),
            Part("rope2", (-7, 3, 7), (0.32, 0, -0.32), cubes=[Cube("rope2", (-0.5, 0, -0.5), (1, 24, 1))]),
            Part("rope3", (7, 3, -7), (-0.32, 0, 0.32), cubes=[Cube("rope3", (-0.5, 0, -0.5), (1, 24, 1))]),
            Part("rope4", (-7, 3, -7), (-0.32, 0, -0.32), cubes=[Cube("rope4", (-0.5, 0, -0.5), (1, 24, 1))]),
        ]),
    ]).pack()


def dragon():
    """Пробуждённый Эндер-дракон — собран в Blender (blender/ender_dragon_2.blend), экспорт в tools/dragon_model.json."""
    import json
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "dragon_model.json")
    with open(path, encoding="utf-8") as fh:
        data = json.load(fh)

    def build(p):
        cubes = [Cube(c["name"], tuple(c["origin"]), tuple(c["size"]), tag=c["tag"], share=c.get("share")) for c in p["cubes"]]
        return Part(p["name"], tuple(p["pivot"]), tuple(p["rot"]), cubes=cubes, children=[build(k) for k in p["children"]])
    return Model("dragon", 512, 512, [build(p) for p in data]).pack()


MODELS = [goose, runaway, tax, mimic, courier_extra, parachute, dragon]
