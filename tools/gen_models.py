"""Генерирует ModelDefs.java из tools/models.py."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import models  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "client", "java", "com", "hardest", "client", "model", "ModelDefs.java")


def f(v):
    s = repr(float(v))
    return s + "f"


def camel(name):
    parts = name.split("_")
    return parts[0] + "".join(p.capitalize() for p in parts[1:])


def gen_part(lines, parent_var, part, counter):
    var = f"p{counter[0]}"
    counter[0] += 1
    b = "ModelPartBuilder.create()"
    for c in part.cubes:
        u, v = c.uv
        b += f".uv({u}, {v}).mirrored({'true' if c.mirror else 'false'}).cuboid({f(c.origin[0])}, {f(c.origin[1])}, {f(c.origin[2])}, {f(c.size[0])}, {f(c.size[1])}, {f(c.size[2])}, new Dilation({f(c.dil)}))"
    px, py, pz = part.pivot
    rx, ry, rz = part.rot
    lines.append(f"        ModelPartData {var} = {parent_var}.addChild(\"{part.name}\", {b}, ModelTransform.of({f(px)}, {f(py)}, {f(pz)}, {f(rx)}, {f(ry)}, {f(rz)}));")
    for ch in part.children:
        gen_part(lines, var, ch, counter)


def main():
    out = [
        "package com.hardest.client.model;",
        "",
        "import net.minecraft.client.model.Dilation;",
        "import net.minecraft.client.model.ModelData;",
        "import net.minecraft.client.model.ModelPartBuilder;",
        "import net.minecraft.client.model.ModelPartData;",
        "import net.minecraft.client.model.ModelTransform;",
        "import net.minecraft.client.model.TexturedModelData;",
        "",
        "/**",
        " * СГЕНЕРИРОВАНО tools/gen_models.py — не править руками.",
        " */",
        "@SuppressWarnings(\"unused\")",
        "public final class ModelDefs {",
    ]
    for fn in models.MODELS:
        m = fn()
        out.append(f"    public static TexturedModelData {camel(m.name)}() {{")
        out.append("        ModelData data = new ModelData();")
        out.append("        ModelPartData root = data.getRoot();")
        counter = [0]
        lines = []
        for p in m.parts:
            gen_part(lines, "root", p, counter)
        out.extend(lines)
        out.append(f"        return TexturedModelData.of(data, {m.tex_w}, {m.tex_h});")
        out.append("    }")
        out.append("")
    out.append("    private ModelDefs() {")
    out.append("    }")
    out.append("}")
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(out) + "\n")
    print("written", OUT)


if __name__ == "__main__":
    main()
