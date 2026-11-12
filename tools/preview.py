#!/usr/bin/env python3
"""Renders preview.png using the same layout rules as TrioRenderer.kt (see TrioSpec).

    pip install pillow
    python3 tools/preview.py [prefix]      # writes <prefix>_speed.png, _power.png and _gear.png
"""
import math
import os
import sys
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
FONTS = os.path.join(HERE, "..", "app", "src", "main", "assets", "fonts")

# --- TrioSpec (keep in sync with TrioRenderer.kt) ---------------------------------------
MARGIN = 0.02
MID_PAIR_MARGIN = 0.06  # free space above the middle digits / below the middle caption (fraction of field height)
SIDE_RATIO = 2 / 3 * 1.1  # side digit height / full digit height (10 % larger than before; capped at the middle digit height)
MID_SCALE = 0.9         # middle digits are 10 % smaller than what is left after the caption row and margins
ICON_RATIO = 1.25       # caption icon box size / caption height
ICON_GAP = 0.04         # gap between caption icon and caption text / field height
ICON_GREEN = (34, 180, 85, 255)  # #22B455, all caption icons
NARROW = 0.8            # all text horizontally condensed to 80 % width
LABEL_RATIO = 0.13      # caption height / field height
LABEL_GAP = 0.05        # gap between value and caption / field height
DOT_RADIUS = 0.06       # recording dot radius / field height (capped at DOT_MAX_RADIUS_PX)
DOT_MAX_RADIUS_PX = 9.0
DOT_INSET = 0.04        # dot inset from the corner / field height
SEPARATORS = True       # slanted separator lines between the values
SEP_HEIGHT = 0.5        # separator length / field height, centred top to bottom
SEP_ANGLE_DEG = 9.0     # lean, matches the italic angle of the bundled font
SEP_STROKE = 0.022      # stroke / field height (floor SEP_STROKE_MIN_PX)
SEP_STROKE_MIN_PX = 2.0
SEP_COLOR = (133, 139, 147, 255)  # medium gray (#858B93), same on both themes
SEP_GAP = 0.15          # clear space between a side value and the middle value (floor SEP_GAP_MIN_PX)
SEP_GAP_MIN_PX = 12.0
PAD = 0.05
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
LABEL_PATH = MID_PATH                                              # captions are bold italic
VAL_PATH = MID_PATH                                                # left/right values are bold italic too


def draw_icon(img, kind, cx, cy, size, color=None, Q=4):
    """Draws a caption icon into [img], centred on (cx, cy), size x size px. The shapes live in a 24 x 24 box and
    are the same numbers TrioRenderer.kt uses (TrioIcons)."""
    color = color or ICON_GREEN
    S = max(2, round(size))
    L = S * Q
    u = L / 24.0
    layer = Image.new("RGBA", (L, L), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)

    def dot(x, y, r):
        d.ellipse([(x - r) * u, (y - r) * u, (x + r) * u, (y + r) * u], fill=color)

    def line(x1, y1, x2, y2, w, round_caps=True):
        d.line([(x1 * u, y1 * u), (x2 * u, y2 * u)], fill=color, width=max(1, round(w * u)))
        if round_caps:
            dot(x1, y1, w / 2)
            dot(x2, y2, w / 2)

    def polar(r, deg, c=(12, 12)):
        a = math.radians(deg)
        return c[0] + r * math.cos(a), c[1] + r * math.sin(a)

    if kind == "speedometer":
        cx_, cy_, r, w = 12, 13.5, 8.5, 2.2
        o = r + w / 2  # PIL draws the arc width inward from the outer ellipse; a canvas stroke is centred
        d.arc([(cx_ - o) * u, (cy_ - o) * u, (cx_ + o) * u, (cy_ + o) * u], 135, 45, fill=color, width=round(w * u))
        for deg in (135, 45):
            dot(*polar(r, deg, (cx_, cy_)), w / 2)
        line(12, 13.5, 17.4, 8.1, 2.2)
        dot(12, 13.5, 2.1)
    elif kind == "bolt":
        d.polygon([(x * u, y * u) for x, y in [(13, 2), (4.5, 13.5), (11, 13.5), (10, 22), (19.5, 9.5), (13, 9.5)]], fill=color)
    elif kind == "heart":
        dot(7.5, 8.2, 5.4)
        dot(16.5, 8.2, 5.4)
        d.polygon([(x * u, y * u) for x, y in [(2.2, 9.6), (21.8, 9.6), (12, 21.5)]], fill=color)
    elif kind == "chainring":
        o = 6.0 + 1.0
        d.ellipse([(12 - o) * u, (12 - o) * u, (12 + o) * u, (12 + o) * u], outline=color, width=round(2.0 * u))
        for i in range(10):  # teeth: radial bars with flat ends
            deg = i * 36
            x1, y1, x2, y2 = *polar(6.0, deg), *polar(9.4, deg)
            line(x1, y1, x2, y2, 2.8, round_caps=False)
        for i in range(5):  # spider arms
            deg = -90 + 72 * i
            x1, y1, x2, y2 = *polar(2.0, deg), *polar(6.0, deg)
            line(x1, y1, x2, y2, 1.5, round_caps=False)
        dot(12, 12, 2.0)
    elif kind == "crank":
        # one crank arm: hollow axle hub, tapered arm, pedal bar across the end
        o = 2.9 + 1.3
        d.ellipse([(7 - o) * u, (7 - o) * u, (7 + o) * u, (7 + o) * u], outline=color, width=round(2.6 * u))
        d.polygon([(x * u, y * u) for x, y in [(10.96, 7.56), (18.06, 15.94), (15.94, 18.06), (7.56, 10.96)]], fill=color)
        line(14.17, 19.83, 19.83, 14.17, 3.2, round_caps=False)
    else:
        raise ValueError(kind)

    layer = layer.resize((S, S), Image.LANCZOS)
    img.alpha_composite(layer, (round(cx - S / 2), round(cy - S / 2)))


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


def mid_digit_height(h):
    """Field height minus the margins, the gap above the caption and the caption itself, times MID_SCALE."""
    return MID_SCALE * h * (1 - 2 * MID_PAIR_MARGIN - LABEL_GAP - LABEL_RATIO)


def plan(w, h, content, k):
    left, mid, right = content
    full = h * (1 - 2 * MARGIN)
    mid_size = size_for_digit_height(MID_PATH, mid_digit_height(h))
    side_size = size_for_digit_height(VAL_PATH, min(full * SIDE_RATIO, mid_digit_height(h)))
    pad = max(h * PAD, PAD_MIN_PX * k)
    gap_to_mid = max(h * SEP_GAP, SEP_GAP_MIN_PX * k) if SEPARATORS else pad
    avail = max((w - width_of(MID_PATH, mid_size, mid, NARROW)) / 2 - pad - gap_to_mid, 1)
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

    # rows shared by all three values: middle value + caption centred vertically; side values on the same
    # baseline, all captions on the same row
    label_h = H * LABEL_RATIO
    gap = H * LABEL_GAP
    mid_h = mid_digit_height(H)
    mid_top = (H - (mid_h + gap + label_h)) / 2
    baseline = mid_top + mid_h
    mid_cy = mid_top + mid_h / 2
    label_cy = baseline + gap + label_h / 2
    label_size = size_for_digit_height(LABEL_PATH, label_h)
    icon_size = label_h * ICON_RATIO
    icon_gap = H * ICON_GAP

    mw = width_of(MID_PATH, p["mid_size"], p["mid"], NARROW)
    put(p["mid"], MID_PATH, p["mid_size"], (W - mw) / 2, mid_cy, NARROW, fg)

    t, b = digit_bounds(VAL_PATH, p["side_size"])
    side_h = b - t
    value_cy = baseline - side_h / 2

    lw = width_of(VAL_PATH, p["side_size"], p["left"], p["side_squeeze"])
    rw = width_of(VAL_PATH, p["side_size"], p["right"], p["side_squeeze"])
    # side values are centred between the field edge and their separator, which sits mid-gap beside the middle value
    mid_left = (W - mw) / 2
    mid_right = mid_left + mw
    gap = max(H * SEP_GAP, SEP_GAP_MIN_PX * k) if SEPARATORS else p["pad"]
    sep_l, sep_r = mid_left - gap / 2, mid_right + gap / 2
    lx = max(sep_l / 2 - lw / 2, p["pad"])
    rx = min((sep_r + W) / 2 - rw / 2, W - p["pad"] - rw)
    put(p["left"], VAL_PATH, p["side_size"], lx, value_cy, p["side_squeeze"], fg)
    put(p["right"], VAL_PATH, p["side_size"], rx, value_cy, p["side_squeeze"], fg)

    if SEPARATORS:
        L = H * SEP_HEIGHT
        dx = (L / 2) * math.tan(math.radians(SEP_ANGLE_DEG))
        sw = max(H * SEP_STROKE, SEP_STROKE_MIN_PX * k)
        sd_ = ImageDraw.Draw(img)
        for cx in (sep_l, sep_r):
            a, b = (cx + dx, (H - L) / 2), (cx - dx, (H + L) / 2)
            sd_.line([a, b], fill=SEP_COLOR, width=round(sw))
            for q in (a, b):  # round caps
                sd_.ellipse([q[0] - sw / 2, q[1] - sw / 2, q[0] + sw / 2, q[1] + sw / 2], fill=SEP_COLOR)

    def label(spec, cx):
        """spec = (text, icon or None): icon then text, centred as a group on cx and kept inside the field."""
        text, icon = spec
        tw = width_of(LABEL_PATH, label_size, text, NARROW)
        iw = (icon_size + icon_gap) if icon else 0
        gw = iw + tw
        x = min(max(cx - gw / 2, p["pad"]), max(p["pad"], W - p["pad"] - gw))
        if icon:
            draw_icon(img, icon, x + icon_size / 2, label_cy, icon_size)
        put(text, LABEL_PATH, label_size, x + iw, label_cy, NARROW, dim)

    label(labels[0], lx + lw / 2)
    label(labels[1], rx + rw / 2)
    if len(labels) > 2 and labels[2][0]:
        label(labels[2], W / 2)

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
        (480, 110, True, ("17.24", "28.3", "42.63"), (("MPH", "speedometer"), ("MI", "speedometer"), ("MPH", "speedometer")), True),
        (480, 110, False, ("17.24", "28.3", "42.63"), (("MPH", "speedometer"), ("MI", "speedometer"), ("MPH", "speedometer")), True),
        (480, 140, True, ("17.24", "28.3", "42.63"), (("MPH", "speedometer"), ("MI", "speedometer"), ("MPH", "speedometer")), True),
        (480, 110, True, ("24.91", "36.8", "142.63"), (("MPH", "speedometer"), ("MI", "speedometer"), ("MPH", "speedometer"))),
        (480, 72, True, ("17.24", "28.3", "42.63"), (("MPH", "speedometer"), ("MI", "speedometer"), ("MPH", "speedometer")), True),
    ]
    power = [
        (480, 110, True, ("152", "238", "49/51"), (("AVG", "bolt"), ("L/R", "bolt"), ("3SEC", "bolt"))),
        (480, 110, False, ("152", "238", "49/51"), (("AVG", "bolt"), ("L/R", "bolt"), ("3SEC", "bolt"))),
        (480, 140, True, ("152", "238", "49/51"), (("AVG", "bolt"), ("L/R", "bolt"), ("3SEC", "bolt"))),
        (480, 110, True, ("187", "1043", "52/48"), (("AVG", "bolt"), ("L/R", "bolt"), ("3SEC", "bolt"))),
    ]
    gear = [
        (480, 110, True, ("48-13", "152", "92"), (("GEAR", "chainring"), ("CAD", "crank"), ("BPM", "heart"))),
        (480, 110, False, ("48-13", "152", "92"), (("GEAR", "chainring"), ("CAD", "crank"), ("BPM", "heart"))),
        (480, 140, True, ("48-13", "152", "92"), (("GEAR", "chainring"), ("CAD", "crank"), ("BPM", "heart"))),
        (480, 110, True, ("34-11", "191", "104"), (("GEAR", "chainring"), ("CAD", "crank"), ("BPM", "heart"))),
    ]
    sheet(prefix + "_gear.png", "Gear Trio  -  gear | heart rate | cadence",
          "Layout rules identical to the extension", gear)
    sheet(prefix + "_speed.png", "Speed Trio  -  avg speed | current speed | ride distance",
          "Imperial units; red dot flashes in the upper-left while any camera records (shown in its on phase)", speed)
    sheet(prefix + "_power.png", "Power Trio  -  avg power | 3 s power | left/right balance",
          "Layout rules identical to the extension", power)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "preview")
