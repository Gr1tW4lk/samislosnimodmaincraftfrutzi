"""Экспорт моделей мобов (из tools/models.py) в JSON-меши для рендера в Blender.

Каждая модель → список квадов: 4 вершины (в блоках, Y вверх), 4 UV (0..1, V вверх как в Blender).
Запуск: python tools/export_mesh.py <out.json>
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import models  # noqa: E402


def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def translate(x, y, z):
    return [[1, 0, 0, x], [0, 1, 0, y], [0, 0, 1, z], [0, 0, 0, 1]]


def rot_zyx(rz, ry, rx):
    # как Quaternionf.rotationZYX(roll, yaw, pitch) в ModelPart.rotate
    cz, sz = math.cos(rz), math.sin(rz)
    cy, sy = math.cos(ry), math.sin(ry)
    cx, sx = math.cos(rx), math.sin(rx)
    Rz = [[cz, -sz, 0, 0], [sz, cz, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]
    Ry = [[cy, 0, sy, 0], [0, 1, 0, 0], [-sy, 0, cy, 0], [0, 0, 0, 1]]
    Rx = [[1, 0, 0, 0], [0, cx, -sx, 0], [0, sx, cx, 0], [0, 0, 0, 1]]
    return mat_mul(mat_mul(Rz, Ry), Rx)


def apply(m, p):
    x, y, z = p
    return [m[i][0] * x + m[i][1] * y + m[i][2] * z + m[i][3] for i in range(3)]


def cube_quads(c, tw, th, m):
    x, y, z = c.origin
    sx, sy, sz = c.size
    d = c.dil
    x0, y0, z0 = x - d, y - d, z - d
    f, g, h = x + sx + d, y + sy + d, z + sz + d
    if c.mirror:
        x0, f = f, x0
    v1 = (x0, y0, z0); v2 = (f, y0, z0); v3 = (f, g, z0); v4 = (x0, g, z0)
    v5 = (x0, y0, h); v6 = (f, y0, h); v7 = (f, g, h); v8 = (x0, g, h)
    u, v = c.uv
    j = u; k = u + sz; l = u + sz + sx; mm = u + sz + sx + sx; n = u + sz + sx + sz; o = u + sz + sx + sz + sx
    p = v; q = v + sz; r = v + sz + sy
    faces = [
        ([v6, v5, v1, v2], (k, p, l, q)),
        ([v3, v4, v8, v7], (l, q, mm, p)),
        ([v1, v5, v8, v4], (j, q, k, r)),
        ([v2, v1, v4, v3], (k, q, l, r)),
        ([v6, v2, v3, v7], (l, q, n, r)),
        ([v5, v6, v7, v8], (n, q, o, r)),
    ]
    out = []
    for verts, (u1, va, u2, vb) in faces:
        uvs = [(u2, va), (u1, va), (u1, vb), (u2, vb)]
        pts = [apply(m, vv) for vv in verts]
        # модельное пространство (px, Y вниз) -> блоки, Y вверх, X отражён (как scale(-1,-1,1))
        pts = [(-px_ / 16.0, (24 - py) / 16.0, pz / 16.0) for (px_, py, pz) in pts]
        uvs = [(uu / tw, 1 - vv / th) for (uu, vv) in uvs]
        out.append({"v": pts, "uv": uvs})
    return out


def walk(part, parent, tw, th, quads, extra):
    m = mat_mul(parent, translate(*part.pivot))
    rx, ry, rz = part.rot
    m = mat_mul(m, rot_zyx(rz, ry, rx))
    for c in part.cubes:
        quads.extend(cube_quads(c, tw, th, m))
    if part.name == "monitor":
        extra["monitor"] = m
    for ch in part.children:
        walk(ch, m, tw, th, quads, extra)


def export(model):
    quads, extra = [], {}
    ident = [[1, 0, 0, 0], [0, 1, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]
    for p in model.parts:
        walk(p, ident, model.tex_w, model.tex_h, quads, extra)
    return quads, extra


def main():
    out = {}
    boss = models.boss()
    quads, extra = export(boss)
    m = extra["monitor"]
    # лицо на экране: кадр 5 (злая ухмылка) атласа faces.png 128x72
    u0, v0 = (5 % 4) * 32 / 128, (5 // 4) * 24 / 72
    u1, v1 = u0 + 32 / 128, v0 + 24 / 72
    corners = [(9.5, -7.5), (-9.5, -7.5), (-9.5, 7.5), (9.5, 7.5)]
    uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
    pts = [apply(m, (cx, cy, -10.05)) for cx, cy in corners]
    pts = [(-a / 16.0, (24 - b) / 16.0, c / 16.0) for a, b, c in pts]
    face = [{"v": pts, "uv": [(uu, 1 - vv) for uu, vv in uvs]}]
    out["boss"] = {"quads": quads, "face": face}
    goose = models.goose()
    gq, _ = export(goose)
    out["goose"] = {"quads": gq}
    with open(sys.argv[1], "w", encoding="utf-8") as fh:
        json.dump(out, fh)
    print("quads:", len(quads), len(gq))


if __name__ == "__main__":
    main()
