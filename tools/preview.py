#!/usr/bin/env python3
"""Renders preview.png using the same layout rules as TrioRenderer.kt (see TrioSpec).

    pip install pillow
    python3 tools/preview.py [prefix]      # writes <prefix>_speed.png, _power.png and _gear.png
"""
import os
import sys
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
FONTS = os.path.join(HERE, "..", "app", "src", "main", "assets", "fonts")

# --- TrioSpec (keep in sync with TrioRenderer.kt) ---------------------------------------
MARGIN = 0.02
MID_RATIO = 0.81        # middle digit height / full digit height (0.9 x 0.9); width scales with it
SIDE_RATIO = 2 / 3      # side digit height / full digit height (unchanged by MID_RATIO)
NARROW = 0.8            # all text horizontally condensed to 80 % width
LABEL_RATIO = 0.13      # caption height / field height
LABEL_GAP = 0.05        # gap between value and caption / field height
DOT_RADIUS = 0.06       # recording dot radius / field height (capped at DOT_MAX_RADIUS_PX)
DOT_MAX_RADIUS_PX = 9.0
DOT_INSET = 0.04        # dot inset from the corner / field height
PAD = 0.06
PAD_MIN_PX = 6.0
SIDE_MIN_SCALE = 0.45   # sides shrink down to this fraction of their 2/3 height, then get squeezed
# -----------------------------------------------------------------------------------------

DIGITS = "0123456789"
REF = 200


def face(*names):
    for n in names:
        p = os.path.join(FONTS, n)
        if os.path.exists(p):
            return p
    raise FileNotFoundError(names)


MID_PATH = face("avenir-bold-oblique.ttf", "nunitosans-bolditalic.ttf")
SIDE_PATH = face("avenir-oblique.ttf", "nunitosans-italic.ttf")   # captions
VAL_PATH = MID_PATH                                                # left/right values are bold italic too


def font(path, size):
    return ImageFont.truetype(path, size)


def digit_bounds(path, size):
    l, t, r, b = font(path, size).getbbox(DIGITS, anchor="ls")
    return t, b


def size_for_digit_height(path, target):
    t, b = digit_bounds(path, REF)
    return REF * target / (b - t)


def width_of(path, size, text, squeeze):
    return font(path, size).getlength(text) * squeeze


def plan(w, h, content, k):
    left, mid, right = content
    full = h * (1 - 2 * MARGIN)
    mid_size = size_for_digit_height(MID_PATH, full * MID_RATIO)
    side_size = size_for_digit_height(VAL_PATH, full * SIDE_RATIO)
    pad = max(h * PAD, PAD_MIN_PX * k)
    avail = max((w - width_of(MID_PATH, mid_size, mid, NARROW)) / 2 - 2 * pad, 1)
    widest = max(width_of(VAL_PATH, side_size, left, NARROW), width_of(VAL_PATH, side_size, right, NARROW))
    scale = avail / widest
    if scale >= 1:
        size, squeeze, eff = side_size, NARROW, 1.0
    elif scale >= SIDE_MIN_SCALE:
        size, squeeze, eff = side_size * scale, NARROW, scale
    else:
        size, squeeze, eff = side_size * SIDE_MIN_SCALE, NARROW * scale / SIDE_MIN_SCALE, SIDE_MIN_SCALE
    return dict(mid=mid, mid_size=mid_size, left=left, right=right, side_size=size, side_squeeze=squeeze, pad=pad,
                note=f"values at {eff*(2/3)*100:.0f}% of field height")


def render_field(w, h, content, labels, dark, k=1, dot=False):
    """w, h in device px; k = supersampling factor for a crisper preview."""
    W, H = w * k, h * k
    bg = (0, 0, 0, 255) if dark else (255, 255, 255, 255)
    fg = (255, 255, 255, 255) if dark else (17, 17, 17, 255)
    dim = (184, 190, 198, 255) if dark else (74, 80, 88, 255)
    img = Image.new("RGBA", (W, H), bg)
    p = plan(W, H, content, k)

    def put(text, path, size, x, cy, squeeze, color):
        """Draw text horizontally scaled by `squeeze` (like Paint.textScaleX), digits centred on cy."""
        t, b = digit_bounds(path, size)
        base = cy - (t + b) / 2
        f = font(path, size)
        pad_px = 12
        tmp = Image.new("RGBA", (int(f.getlength(text)) + 2 * pad_px, H), (0, 0, 0, 0))
        ImageDraw.Draw(tmp).text((pad_px, base), text, font=f, anchor="ls", fill=color)
        tmp = tmp.resize((max(1, round(tmp.width * squeeze)), H), Image.LANCZOS)
        img.alpha_composite(tmp, (round(x - pad_px * squeeze), 0))

    # middle
    mw = width_of(MID_PATH, p["mid_size"], p["mid"], NARROW)
    put(p["mid"], MID_PATH, p["mid_size"], (W - mw) / 2, H / 2, NARROW, fg)

    # left / right: value + caption underneath, pair centred vertically
    t, b = digit_bounds(VAL_PATH, p["side_size"])
    side_h = b - t
    label_h = H * LABEL_RATIO
    gap = H * LABEL_GAP
    top = (H - (side_h + gap + label_h)) / 2
    value_cy = top + side_h / 2
    label_cy = top + side_h + gap + label_h / 2
    label_size = size_for_digit_height(SIDE_PATH, label_h)

    lw = width_of(VAL_PATH, p["side_size"], p["left"], p["side_squeeze"])
    rw = width_of(VAL_PATH, p["side_size"], p["right"], p["side_squeeze"])
    lx, rx = p["pad"], W - p["pad"] - rw
    put(p["left"], VAL_PATH, p["side_size"], lx, value_cy, p["side_squeeze"], fg)
    put(p["right"], VAL_PATH, p["side_size"], rx, value_cy, p["side_squeeze"], fg)

    def label(text, cx):
        w_ = width_of(SIDE_PATH, label_size, text, NARROW)
        x = min(max(cx - w_ / 2, p["pad"]), max(p["pad"], W - p["pad"] - w_))
        put(text, SIDE_PATH, label_size, x, label_cy, NARROW, dim)

    label(labels[0], lx + lw / 2)
    label(labels[1], rx + rw / 2)

    if dot:  # flashing red recording dot, shown here in its "on" phase
        r = min(H * DOT_RADIUS, DOT_MAX_RADIUS_PX * k)
        inset = max(H * DOT_INSET, 3 * k)
        ImageDraw.Draw(img).ellipse([inset, inset, inset + 2 * r, inset + 2 * r], fill=(255, 45, 45, 255))
    return img, p


def sheet(out, title, subtitle, cases):
    """cases: list of (w, h, dark, (left, mid, right), (left_label, right_label))."""
    k = 2
    label_f = ImageFont.truetype(face("nunitosans-italic.ttf"), 22)
    small_f = ImageFont.truetype(face("nunitosans-italic.ttf"), 18)
    margin, gap, top = 40, 34, 110
    sheet_w = 480 * k + 2 * margin
    sheet_h = top + sum(c[1] * k + 52 + gap for c in cases) + 20
    img_ = Image.new("RGBA", (sheet_w, sheet_h), (38, 42, 48, 255))
    sd = ImageDraw.Draw(img_)
    sd.text((margin, 28), title, font=label_f, fill=(235, 238, 242))
    sd.text((margin, 62), subtitle, font=small_f, fill=(160, 168, 178))
    y = top
    for c in cases:
        (w, h, dark, content, labels), dot = c[:5], (c[5] if len(c) > 5 else False)
        img, p = render_field(w, h, content, labels, dark, k, dot)
        theme = "dark" if dark else "light"
        extra = ", recording dot on" if dot else ""
        sd.text((margin, y), f"{w} x {h} px, {theme}  -  {' | '.join(content)}  ({p['note']}{extra})",
                font=small_f, fill=(200, 206, 214))
        y += 30
        img_.alpha_composite(img, (margin, y))
        sd.rectangle([margin - 1, y - 1, margin + img.width, y + img.height], outline=(90, 98, 108))
        y += img.height + gap + 22
    img_.convert("RGB").save(out)
    print("wrote", out)


def main(prefix):
    speed = [
        (480, 110, True, ("17.24", "28.3", "42.63"), ("MPH", "MI"), True),
        (480, 110, False, ("17.24", "28.3", "42.63"), ("MPH", "MI"), True),
        (480, 140, True, ("17.24", "28.3", "42.63"), ("MPH", "MI"), True),
        (480, 110, True, ("24.91", "36.8", "142.63"), ("MPH", "MI")),
        (480, 72, True, ("17.24", "28.3", "42.63"), ("MPH", "MI"), True),
    ]
    power = [
        (480, 110, True, ("152", "238", "49/51"), ("AVG", "L/R")),
        (480, 110, False, ("152", "238", "49/51"), ("AVG", "L/R")),
        (480, 140, True, ("152", "238", "49/51"), ("AVG", "L/R")),
        (480, 110, True, ("187", "1043", "52/48"), ("AVG", "L/R")),
    ]
    gear = [
        (480, 110, True, ("48-13", "152", "92"), ("GEAR", "CAD")),
        (480, 110, False, ("48-13", "152", "92"), ("GEAR", "CAD")),
        (480, 140, True, ("48-13", "152", "92"), ("GEAR", "CAD")),
        (480, 110, True, ("34-11", "191", "104"), ("GEAR", "CAD")),
    ]
    sheet(prefix + "_gear.png", "Gear Trio  -  gear | heart rate | cadence",
          "Layout rules identical to the extension", gear)
    sheet(prefix + "_speed.png", "Speed Trio  -  avg speed | current speed | ride distance",
          "Imperial units; red dot flashes in the upper-left while any camera records (shown in its on phase)", speed)
    sheet(prefix + "_power.png", "Power Trio  -  avg power | 3 s power | left/right balance",
          "Layout rules identical to the extension", power)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "preview")
