"""Gera os assets visuais do Block Control (namespace blockcontrol) e as prévias dos menus.

Uso:  python tools/gerar_assets.py [pasta-de-cabecas]
Saída:
  resourcepack/craftengine/blockcontrol/   pacote de recursos no formato do CraftEngine
  docs/previas/                             prévias PNG dos menus, folha de ícones e GIF da animação

Ícones: os de tools/icones/ (enviados pelo dono do servidor) são convertidos para 64x64; os demais
são desenhados aqui no mesmo estilo (contorno preto grosso, cores chapadas). Mesmo formato do
AuctionHouseSerp: ícones pelo componente item_model e fundos por uma fonte própria.

Animação leve via .png.mcmeta: o cliente anima a textura sozinho, sem custo no servidor.

[pasta-de-cabecas] é opcional: PNGs de cabeças reais (ex.: baixadas de um serviço de avatares)
usadas só nas prévias. No jogo as cabeças sempre mostram a skin de cada jogador.
"""
import json
import math
import os
import sys
from PIL import Image, ImageChops, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NS = "blockcontrol"
BUNDLE = os.path.join(ROOT, "resourcepack", "craftengine", NS)
ASSETS = os.path.join(BUNDLE, "resourcepack", "assets", NS)
PREVIEWS = os.path.join(ROOT, "docs", "previas")
SOURCES = os.path.join(ROOT, "tools", "icones")

CLEAR = (0, 0, 0, 0)
INK = (17, 17, 17, 255)

BG_CHARS = {3: "\ue000", 6: "\ue001"}
SHIFT_START = "\uf000"  # -8: volta ao canto do inventário
SHIFT_BACK = "\uf001"   # -165: devolve o cursor 4 px depois do início do título (x = 12)
TITLE_INDENT = 4             # o texto começa dentro da faixa, não na ponta dela
GUI_WIDTH = 176

SIZE = 64          # resolução dos ícones
FRAMES = 12        # quadros dos ícones animados
FRAMETIME = 4      # ticks por quadro (12 x 4 = 2,4 s por ciclo: animação lenta e leve)
HI_RES = 256       # tela de desenho antes de reduzir para 64


def ui_font(size):
    for candidate in ("DejaVuSans.ttf", "arial.ttf", "C:/Windows/Fonts/arial.ttf",
                      "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(candidate, size)
        except OSError:
            pass
    return ImageFont.load_default()


def ensure(path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    return path


def write_json(path, data):
    with open(ensure(path), "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=True)
        f.write("\n")


def rgb(hex_color, a=255):
    hex_color = hex_color.lstrip("#")
    return tuple(int(hex_color[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


def lerp(c1, c2, t):
    t = max(0.0, min(1.0, t))
    return tuple(round(a + (b - a) * t) for a, b in zip(c1, c2))


# ------------------------------------------------------------------ ícones enviados

def remove_white_background(img, tol=40):
    """Apaga só o branco ligado às bordas (o branco de dentro do desenho fica)."""
    img = img.convert("RGBA")
    w, h = img.size
    px = img.load()
    seen = set()
    stack = [(x, y) for x in range(w) for y in (0, h - 1)] + [(x, y) for y in range(h) for x in (0, w - 1)]
    while stack:
        x, y = stack.pop()
        if (x, y) in seen or not (0 <= x < w and 0 <= y < h):
            continue
        seen.add((x, y))
        r, g, b, a = px[x, y]
        if a == 0 or (r > 255 - tol and g > 255 - tol and b > 255 - tol):
            px[x, y] = CLEAR
            stack.extend(((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))
    return img


def trim(img, margin=0.06):
    """Recorta o espaço vazio e centraliza num quadrado com uma pequena margem."""
    box = img.getbbox()
    if box:
        img = img.crop(box)
    side = round(max(img.size) * (1 + 2 * margin))
    out = Image.new("RGBA", (side, side), CLEAR)
    out.alpha_composite(img, ((side - img.width) // 2, (side - img.height) // 2))
    return out


def load_source(name):
    for ext in (".png", ".webp", ".avif"):
        path = os.path.join(SOURCES, name + ext)
        if os.path.exists(path):
            img = Image.open(path)
            img = remove_white_background(img) if img.mode != "RGBA" else img.convert("RGBA")
            return trim(img)
    raise FileNotFoundError(name)


_cache = {}


def source(name):
    if name not in _cache:
        _cache[name] = load_source(name)
    return _cache[name]


def small(img):
    return img.resize((SIZE, SIZE), Image.LANCZOS)


# ------------------------------------------------------------------ ícones desenhados (mesmo estilo)

def canvas():
    img = Image.new("RGBA", (HI_RES, HI_RES), CLEAR)
    return img, ImageDraw.Draw(img)


W = 18  # espessura do contorno na tela de 256


def outlined_polygon(d, pts, fill):
    d.polygon(pts, fill=fill)
    d.line(pts + [pts[0]], fill=INK, width=W, joint="curve")


def person(d, ox, oy, s=1.0, fill=rgb("cbd5e1")):
    d.ellipse((ox + 58 * s, oy + 20 * s, ox + 142 * s, oy + 104 * s), fill=fill, outline=INK, width=W)
    d.rounded_rectangle((ox + 22 * s, oy + 118 * s, ox + 178 * s, oy + 210 * s), radius=48 * s,
                        fill=fill, outline=INK, width=W)


def ban(d, cx, cy, r, color=rgb("ef4444")):
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=rgb("ffffff"), outline=INK, width=W)
    inner = r - W
    d.ellipse((cx - inner, cy - inner, cx + inner, cy + inner), outline=color, width=W)
    k = inner * 0.68
    d.line((cx - k, cy + k, cx + k, cy - k), fill=color, width=W)


def draw_bloquear(t=0.0):
    img, d = canvas()
    person(d, 10, 10, 0.92)
    pulse = 0.5 + 0.5 * math.sin(t * 2 * math.pi)
    ban(d, 182, 182, 58 + round(pulse * 4), color=lerp(rgb("dc2626"), rgb("f87171"), pulse))
    return img


def draw_desbloquear(t=0.0):
    img, d = canvas()
    d.arc((62, 18, 178, 134), 180, 360, fill=INK, width=W + 14)
    d.arc((62, 18, 178, 134), 180, 360, fill=rgb("cbd5e1"), width=14)
    d.rounded_rectangle((40, 112, 216, 236), radius=26, fill=rgb("4ade80"), outline=INK, width=W)
    d.ellipse((112, 148, 144, 180), fill=INK)
    d.rectangle((120, 170, 136, 204), fill=INK)
    return img


def draw_voz(t=0.0):
    img, d = canvas()
    pts = [(28, 96), (72, 96), (128, 44), (128, 212), (72, 160), (28, 160)]
    outlined_polygon(d, pts, rgb("e2e8f0"))
    for k in range(2):
        r = 48 + k * 40
        alpha = 255 if t == 0 else round(255 * (0.55 + 0.45 * math.sin((t - k * 0.25) * 2 * math.pi)))
        d.arc((150 - r, 128 - r, 150 + r, 128 + r), -50, 50, fill=(17, 17, 17, max(90, alpha)), width=W)
    return img


def draw_vazio(t=0.0):
    img, d = canvas()
    dy = round(math.sin(t * 2 * math.pi) * 6)
    shape, sd = canvas()
    person(sd, 28, 24 + dy, 1.0, fill=rgb("e5e7eb"))
    mask = shape.getchannel("A").point(lambda a: 255 if a else 0)
    dash = Image.new("L", (HI_RES, HI_RES), 0)
    dd = ImageDraw.Draw(dash)
    for k in range(-HI_RES, HI_RES * 2, 40):
        dd.line((k, 0, k + HI_RES, HI_RES), fill=255, width=24)
    faded = Image.new("RGBA", (HI_RES, HI_RES), rgb("9ca3af"))
    img.paste(faded, (0, 0), ImageChops.multiply(mask, dash))
    return img


def draw_arrow(direction):
    img, d = canvas()
    pts = [(28, 100), (128, 100), (128, 40), (228, 128), (128, 216), (128, 156), (28, 156)]
    if direction == "left":
        pts = [(256 - x, y) for x, y in pts]
    outlined_polygon(d, pts, rgb("f87171"))
    return img


def draw_voltar():
    """Seta de retorno: sai para a esquerda no alto, contorna pela direita e volta embaixo."""
    img, d = canvas()
    path = [(96, 72), (160, 72)]
    for k in range(0, 181, 10):
        a = math.radians(-90 + k)
        path.append((160 + 60 * math.cos(a), 132 + 60 * math.sin(a)))
    path.append((64, 192))
    d.line(path, fill=INK, width=58, joint="curve")
    d.line(path, fill=rgb("f87171"), width=24, joint="curve")
    outlined_polygon(d, [(22, 72), (104, 18), (104, 126)], rgb("f87171"))
    return img


def draw_fechar():
    img, d = canvas()
    for a, b in (((56, 56), (200, 200)), ((56, 200), (200, 56))):
        d.line(a + b, fill=INK, width=56)
    for a, b in (((64, 64), (192, 192)), ((64, 192), (192, 64))):
        d.line(a + b, fill=rgb("e5e7eb"), width=24)
    return img


# ------------------------------------------------------------------ animação leve dos enviados

def shine(img, t, strength=90):
    """Reflexo diagonal suave que atravessa o desenho."""
    w, h = img.size
    band = Image.new("L", (w, h), 0)
    bd = ImageDraw.Draw(band)
    x = -w * 0.6 + t * w * 2.2
    bd.polygon([(x, 0), (x + w * 0.12, 0), (x - w * 0.3, h), (x - w * 0.42, h)], fill=strength)
    mask = ImageChops.multiply(band, img.getchannel("A"))
    out = img.copy()
    out.paste(Image.new("RGBA", (w, h), (255, 255, 255, 255)), (0, 0), mask)
    return out


def bob(img, t, amount=1):
    dy = round(math.sin(t * 2 * math.pi) * amount)
    out = Image.new("RGBA", img.size, CLEAR)
    out.alpha_composite(img, (0, dy))
    return out


def anim_escudo(t):
    return shine(small(source("escudo")), t)


def anim_tpa(t):
    # Galáxia girando devagar (a figura se repete a cada 180°).
    big = source("tpa")
    return small(big.rotate(-t * 180, resample=Image.BICUBIC))


def anim_tell(t):
    return bob(small(source("tell")), t)


def anim_chat(t):
    return bob(small(source("chat")), (t + 0.5) % 1)


STATIC = {
    "ajuda": lambda: small(source("ajuda")),
    "cancelar": lambda: small(source("cancelar")),
    "confirmar": lambda: small(source("confirmar")),
    "desbloquear": lambda: small(draw_desbloquear()),
    "fechar": lambda: small(draw_fechar()),
    "proximo": lambda: small(draw_arrow("right")),
    "anterior": lambda: small(draw_arrow("left")),
    "voltar": lambda: small(draw_voltar()),
    "moldura": lambda: Image.new("RGBA", (SIZE, SIZE), CLEAR),
}
ANIMATED = {
    "escudo": anim_escudo,
    "tpa": anim_tpa,
    "chat": anim_chat,
    "tell": anim_tell,
    "bloquear": lambda t: small(draw_bloquear(t)),
    "voz": lambda t: small(draw_voz(t)),
    "vazio": lambda t: small(draw_vazio(t)),
}
ICONS = list(STATIC) + list(ANIMATED)


def draw_icon(name, t=0.0):
    if name in ANIMATED:
        return ANIMATED[name](t)
    return STATIC[name]()


def strip(name):
    sheet = Image.new("RGBA", (SIZE, SIZE * FRAMES), CLEAR)
    for i in range(FRAMES):
        sheet.alpha_composite(draw_icon(name, i / FRAMES), (0, SIZE * i))
    return sheet


# ------------------------------------------------------------------ fundo (claro, no estilo do jogo)

BG = {
    "body": rgb("c6c6c6"), "light": rgb("ffffff"), "shadow": rgb("555555"), "edge": rgb("000000"),
    "slot": rgb("8b8b8b"), "slot_dark": rgb("373737"), "slot_light": rgb("ffffff"),
    "bar": rgb("a58c8c"), "bar_dark": rgb("4a2f2f"), "bar_light": rgb("f3e3e3"),
    "accent": rgb("8c1c24"), "accent_soft": rgb("b89a9a"),
}


def draw_slot(d, x, y, bar):
    fill, dark, light = (BG["bar"], BG["bar_dark"], BG["bar_light"]) if bar else (BG["slot"], BG["slot_dark"], BG["slot_light"])
    d.rectangle((x, y, x + 17, y + 17), fill=fill)
    d.line((x, y, x + 16, y), fill=dark)
    d.line((x, y, x, y + 16), fill=dark)
    d.line((x + 1, y + 17, x + 17, y + 17), fill=light)
    d.line((x + 17, y + 1, x + 17, y + 17), fill=light)


GOLD = rgb("d4a64a")
GOLD_LIGHT = rgb("f6dc9a")
GOLD_DARK = rgb("8a6420")


def gold_line(d, y, x0, x1, w):
    """Filete dourado com brilho no centro que se apaga nas pontas."""
    for x in range(x0, x1 + 1):
        t = abs(x - w / 2) / ((x1 - x0) / 2)
        d.point((x, y), fill=lerp(GOLD_LIGHT, GOLD, min(1, t * 1.3)) if t < 0.8 else lerp(GOLD, GOLD_DARK, (t - 0.8) / 0.2))


def diamond(d, cx, cy):
    """Losango dourado de 5 px com reflexo."""
    d.polygon([(cx, cy - 2), (cx + 2, cy), (cx, cy + 2), (cx - 2, cy)], fill=GOLD)
    d.point((cx, cy - 1), fill=GOLD_LIGHT)
    d.point((cx - 1, cy), fill=GOLD_LIGHT)
    d.point((cx + 1, cy + 1), fill=GOLD_DARK)


def ribbon(d, w):
    """Faixa do título: vinho profundo com filetes dourados e pontas em seta."""
    top, bot = 4, 14
    mid_y = (top + bot) / 2
    x0, x1 = 5, w - 6
    wine_edge, wine_mid = rgb("4a0b11"), rgb("8c1c24")
    for y in range(top, bot + 1):
        t = abs(y - mid_y) / (mid_y - top)
        color = lerp(wine_mid, wine_edge, t ** 1.5)
        inset = round(abs(y - mid_y) * 0.6)                 # pontas em seta, discretas
        d.line((x0 + 3 - inset, y, x1 - 3 + inset, y), fill=color)
    gold_line(d, top, x0 + 3, x1 - 3, w)
    gold_line(d, bot, x0 + 3, x1 - 3, w)
    d.line((x0 + 4, top + 1, x1 - 4, top + 1), fill=rgb("a8343a"))   # luz sob o filete de cima
    # ornamento à direita: losango com um traço que se apaga para a esquerda
    cx = x1 - 9
    diamond(d, cx, round(mid_y))
    for k in range(1, 22):
        d.point((cx - 3 - k, round(mid_y)), fill=lerp(GOLD, wine_mid, k / 21))


def background(rows, bars=()):
    """Painel claro como o do jogo, com cantos arredondados e filetes vermelhos discretos:
    um sob o título e um separando o cabeçalho e a barra de navegação da lista."""
    w, h = GUI_WIDTH, 17 + rows * 18 + 2           # termina antes do rótulo "Inventário"
    img = Image.new("RGBA", (w, h), CLEAR)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, w - 1, h - 1), radius=4, fill=BG["edge"])
    d.rounded_rectangle((1, 1, w - 2, h - 2), radius=3, fill=BG["shadow"])
    d.rounded_rectangle((1, 1, w - 3, h - 3), radius=3, fill=BG["light"])
    d.rounded_rectangle((3, 3, w - 3, h - 3), radius=2, fill=BG["shadow"])
    d.rounded_rectangle((3, 3, w - 4, h - 4), radius=2, fill=BG["body"])

    def accent(y, x0=7, x1=w - 7):
        for x in range(x0, x1):
            t = abs(x - w / 2) / (w / 2 - 7)
            d.point((x, y), fill=lerp(BG["accent"], BG["accent_soft"], t ** 1.6))

    ribbon(d, w)
    for r in range(rows):
        for c in range(9):
            draw_slot(d, 7 + c * 18, 17 + r * 18, False)
    for r in bars:                                   # filete entre a faixa e a lista
        if r == 0:
            accent(17 + 18)
        else:
            accent(17 + r * 18 - 1)
    return img


BACKGROUNDS = {3: dict(), 6: dict(bars=(0, 5))}


def build_pack():
    with open(ensure(os.path.join(BUNDLE, "pack.yml")), "w", encoding="utf-8", newline="\n") as f:
        f.write("# Pacote de recursos do Block Control para o CraftEngine (integra o pack único do Serpens).\n"
                "author: Zwo\nversion: 1.0.0\ndescription: Ícones e fundos do painel do Block Control\nnamespace: blockcontrol\n")
    with open(ensure(os.path.join(BUNDLE, "configuration", "blockcontrol.yml")), "w", encoding="utf-8", newline="\n") as f:
        f.write("# O Block Control não registra itens, blocos nem imagens no CraftEngine.\n"
                "# Ícones usam o componente item_model (assets/blockcontrol/items/gui/*) e os fundos\n"
                "# usam a fonte própria blockcontrol:gui. Este arquivo existe só para marcar o pacote.\n")
    tex_dir = os.path.join(ASSETS, "textures", "item", "gui")
    if os.path.isdir(tex_dir):
        for f in os.listdir(tex_dir):                          # remove sobras de versões anteriores
            os.remove(os.path.join(tex_dir, f))
    for name in ICONS:
        tex = os.path.join(tex_dir, name + ".png")
        if name in ANIMATED:
            strip(name).save(ensure(tex))
            write_json(tex + ".mcmeta", {"animation": {"frametime": FRAMETIME, "interpolate": False}})
        else:
            draw_icon(name).save(ensure(tex))
        write_json(os.path.join(ASSETS, "models", "item", "gui", name + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/gui/{name}"}})
        write_json(os.path.join(ASSETS, "items", "gui", name + ".json"),
                   {"model": {"type": "minecraft:model", "model": f"{NS}:item/gui/{name}"}})
    providers = []
    for rows, char in BG_CHARS.items():
        img = background(rows, **BACKGROUNDS[rows])
        img.save(ensure(os.path.join(ASSETS, "textures", "font", "gui", f"fundo_{rows}.png")))
        providers.append({"type": "bitmap", "file": f"{NS}:font/gui/fundo_{rows}.png",
                          "height": img.height, "ascent": 13, "chars": [char]})
    providers.append({"type": "space", "advances": {SHIFT_START: -8, SHIFT_BACK: -(GUI_WIDTH + 1 - 8 - TITLE_INDENT)}})
    write_json(os.path.join(ASSETS, "font", "gui.json"), {"providers": providers})


# ------------------------------------------------------------------ prévias

HI = 4      # escala da prévia (equivale à interface do jogo em 4x)
HEADS = []


def load_heads(folder):
    if folder and os.path.isdir(folder):
        for f in sorted(os.listdir(folder)):
            if f.lower().endswith(".png"):
                HEADS.append(Image.open(os.path.join(folder, f)).convert("RGBA"))


def sample_head(i):
    if HEADS:
        return HEADS[i % len(HEADS)]
    img = Image.new("RGBA", (8, 8), rgb("d6a57c"))
    ImageDraw.Draw(img).rectangle((0, 0, 7, 2), fill=rgb(["8b5a2b", "3b82f6", "f59e0b", "10b981"][i % 4]))
    return img


def player_inventory(width):
    img = Image.new("RGBA", (width, 90), CLEAR)
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, width - 1, 89), fill=BG["body"])
    d.line((width - 1, 0, width - 1, 89), fill=BG["shadow"])
    d.line((0, 89, width - 1, 89), fill=BG["shadow"])
    for r in range(4):
        y = 10 + r * 18 + (4 if r == 3 else 0)
        for c in range(9):
            draw_slot(d, 7 + c * 18, y, False)
    return img


def preview_frame(title, rows, slots, heads=(), t=0.0):
    bg = background(rows, **BACKGROUNDS[rows])
    inv = player_inventory(GUI_WIDTH)
    base = Image.new("RGBA", (GUI_WIDTH, bg.height + inv.height - 4), CLEAR)
    base.alpha_composite(inv, (0, bg.height - 4))
    base.alpha_composite(bg, (0, 0))
    big = base.resize((base.width * HI, base.height * HI), Image.NEAREST)
    d = ImageDraw.Draw(big)
    d.text(((8 + TITLE_INDENT) * HI, 4 * HI), title, fill=rgb("fbeed5"), font=ui_font(9 * HI))
    d.text((8 * HI, (bg.height - 2) * HI), "Inventário", fill=rgb("404040"), font=ui_font(9 * HI))
    for slot, name in slots.items():
        big.alpha_composite(draw_icon(name, t).resize((16 * HI, 16 * HI), Image.LANCZOS),
                            ((8 + (slot % 9) * 18) * HI, (18 + (slot // 9) * 18) * HI))
    for i, slot in enumerate(heads):
        big.alpha_composite(sample_head(i).resize((16 * HI, 16 * HI), Image.NEAREST),
                            ((8 + (slot % 9) * 18) * HI, (18 + (slot // 9) * 18) * HI))
    return big


def preview(file, title, rows, slots, heads=()):
    preview_frame(title, rows, slots, heads).save(ensure(os.path.join(PREVIEWS, file)))


def preview_gif(file, title, rows, slots, heads=()):
    frames = [preview_frame(title, rows, slots, heads, i / FRAMES).convert("P", palette=Image.ADAPTIVE)
              for i in range(FRAMES)]
    frames[0].save(ensure(os.path.join(PREVIEWS, file)), save_all=True, append_images=frames[1:],
                   duration=FRAMETIME * 50, loop=0)


MAIN_SLOTS = {1: "chat", 2: "tell", 4: "escudo", 6: "tpa", 7: "voz",
              45: "anterior", 47: "ajuda", 49: "bloquear", 51: "fechar", 53: "proximo"}
LIST_SLOTS = [10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34,
              37, 38, 39, 40, 41, 42, 43]


def icon_sheet():
    names = [n for n in ICONS if n != "moldura"]
    cols, cell = 8, 120
    rows = (len(names) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell, rows * cell), BG["body"])
    d = ImageDraw.Draw(sheet)
    for i, name in enumerate(names):
        x, y = (i % cols) * cell, (i // cols) * cell
        sheet.alpha_composite(draw_icon(name).resize((96, 96), Image.LANCZOS), (x + 12, y + 2))
        label = name + (" *" if name in ANIMATED else "")
        d.text((x + cell // 2, y + 108), label, fill=rgb("303030"), font=ui_font(12), anchor="mm")
    sheet.save(ensure(os.path.join(PREVIEWS, "icones.png")))


def build_previews():
    for f in os.listdir(PREVIEWS) if os.path.isdir(PREVIEWS) else []:
        os.remove(os.path.join(PREVIEWS, f))
    preview_gif("principal.gif", "Bloqueios", 6, MAIN_SLOTS, heads=LIST_SLOTS[:8])
    empty = dict(MAIN_SLOTS)
    empty[22] = "vazio"
    preview_gif("vazio.gif", "Bloqueios", 6, empty)
    preview("escolher.png", "Bloquear jogador", 6, {4: "bloquear", 45: "anterior", 49: "voltar", 53: "proximo"},
            heads=LIST_SLOTS[:8])
    preview("confirmar.png", "Bloquear Notch?", 3, {11: "confirmar", 15: "cancelar"}, heads=[13])
    preview("desbloquear.png", "Desbloquear Notch?", 3, {11: "desbloquear", 15: "cancelar"}, heads=[13])
    icon_sheet()


if __name__ == "__main__":
    load_heads(sys.argv[1] if len(sys.argv) > 1 else None)
    build_pack()
    build_previews()
    print("Assets em", BUNDLE)
    print("Prévias em", PREVIEWS)
