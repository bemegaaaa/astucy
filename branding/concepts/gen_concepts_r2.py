# Gerador dos conceitos r2 (simbolos) — Astucy
import math
import os

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)))

def P(cx, cy, r, deg):
    a = math.radians(deg)
    return (round(cx + r * math.cos(a), 2), round(cy - r * math.sin(a), 2))

def fmt(x):
    s = f"{x:.2f}"
    return s.rstrip("0").rstrip(".") if "." in s else s

def pt(p):
    return f"{fmt(p[0])} {fmt(p[1])}"

# ---------- conceito 1: portal em camadas (3 frames deslocados na diagonal) ----------
S1, S2, S3 = 176, 116, 52
c1, c2, c3 = (128, 128), (112, 144), (96, 160)
RX = 40, 27, 14

def sq_path(c, size, rx):
    x0, y0 = c[0] - size / 2, c[1] - size / 2
    x1, y1 = c[0] + size / 2, c[1] + size / 2
    return (f"M{fmt(x0+rx)} {fmt(y0)} H{fmt(x1-rx)} A{rx} {rx} 0 0 1 {fmt(x1)} {fmt(y0+rx)}"
            f" V{fmt(y1-rx)} A{rx} {rx} 0 0 1 {fmt(x1-rx)} {fmt(y1)}"
            f" H{fmt(x0+rx)} A{rx} {rx} 0 0 1 {fmt(x0)} {fmt(y1-rx)}"
            f" V{fmt(y0+rx)} A{rx} {rx} 0 0 1 {fmt(x0+rx)} {fmt(y0)} Z")

frames = sq_path(c1, S1, RX[0]) + " " + sq_path(c2, S2, RX[1]) + " " + sq_path(c3, S3, RX[2])

svg1 = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256">
  <rect width="256" height="256" fill="#ffffff"/>
  <title>Astucy - portal em camadas</title>
  <path fill="#000000" fill-rule="evenodd" d="{frames}"/>
</svg>'''

# ---------- conceito 2: faisca de teclas (3 keycaps em cascata) ----------
def key(c, s, rot):
    rx = round(s * 0.24)
    return (f'<g transform="translate({c[0]} {c[1]}) rotate({rot})">'
            f'<path fill="#000000" d="{sq_path((0, 0), s, rx)}"/></g>')

k1 = key((204, 50), 84, 0)
k2 = key((114, 142), 52, 45)
k3 = key((44, 212), 40, 0)
svg2 = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256">
  <rect width="256" height="256" fill="#ffffff"/>
  <title>Astucy - faisca de teclas</title>
  {k1}
  {k2}
  {k3}
</svg>'''

# ---------- conceito 3: iris (anel com 3 fendas a 120 graus) ----------
CX, CY, RO, RI = 128, 128, 96, 46
SLITS = [90, 210, 330]
HALF = 12  # meia largura da fenda em graus

def petal(theta1, theta2):
    o1, o2 = P(CX, CY, RO, theta1), P(CX, CY, RO, theta2)
    i2, i1 = P(CX, CY, RI, theta2), P(CX, CY, RI, theta1)
    span = theta2 - theta1
    large = 1 if span > 180 else 0
    return (f"M{pt(o1)} A{RO} {RO} 0 {large} 0 {pt(o2)}"
            f" L{pt(i2)} A{RI} {RI} 0 {large} 1 {pt(i1)} Z")

petals = [petal(s + HALF, s - HALF + 360 if s + HALF > 360 else s + 360 - HALF) for s in SLITS]
# spans de 96 graus centrados longe das fendas
spans = [(102, 198), (222, 318), (342, 378)]
petals = [petal(a, b) for a, b in spans]

svg3 = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256">\n'
svg3 += '  <rect width="256" height="256" fill="#ffffff"/>\n'
svg3 += "  <title>Astucy - iris</title>\n"
for p_ in petals:
    svg3 += f'  <path fill="#000000" d="{p_}"/>\n'
svg3 += "</svg>"

import os
files = {
    "concept-portal-camadas.svg": svg1,
    "concept-teclas-faisca.svg": svg2,
    "concept-iris-anel.svg": svg3,
}
for name, content in files.items():
    with open(os.path.join(OUT, name), "w", encoding="utf-8") as f:
        f.write(content)
    print("ok", name)
