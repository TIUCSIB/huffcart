# -*- coding: utf-8 -*-
"""从素材手柄图抠出独立按键精灵 + 生成挖孔底图。"""
from PIL import Image, ImageDraw

img = Image.open("app/src/main/res/drawable-nodpi/asset_gamepad.png").convert("RGBA")
w, h = img.size
px = img.load()
out = "app/src/main/res/drawable-nodpi/"

# 面板底色采样（挖孔填充用）
def avg_panel(x0, y0, x1, y1):
    rs = gs = bs = n = 0
    for x in range(x0, x1):
        for y in range(y0, y1):
            r, g, b, a = px[x, y]
            if a > 200:
                rs += r; gs += g; bs += b; n += 1
    return (rs // n, gs // n, bs // n, 255)

panel_top = avg_panel(150, 20, 210, 60)      # 面板上部
panel_mid = avg_panel(150, 100, 210, 140)    # 面板中部

# ---- B / A：圆形掩膜 ----
def circle_sprite(bbox, center, r):
    x0, y0, x1, y1 = bbox
    crop = img.crop(bbox).convert("RGBA")
    mask = Image.new("L", crop.size, 0)
    d = ImageDraw.Draw(mask)
    ccx, ccy = center[0] - x0, center[1] - y0
    d.ellipse((ccx - r, ccy - r, ccx + r, ccy + r), fill=255)
    crop.putalpha(mask)
    return crop

b_sprite = circle_sprite((222, 50, 290, 120), (255, 84), 31)
a_sprite = circle_sprite((297, 37, 366, 108), (331, 72), 31)

# ---- 十字键：几何掩膜（横竖臂 + 自带阴影）----
cx, cy = 69, 92
arm_half, thick = 68, 27  # 半臂长/臂厚（像素）
cross_bbox = (0, cy - arm_half - 6, cx + arm_half + 4, cy + arm_half + 8)
x0, y0, x1, y1 = cross_bbox
crop = img.crop(cross_bbox).convert("RGBA")
mask = Image.new("L", crop.size, 0)
d = ImageDraw.Draw(mask)
# 竖臂 + 横臂（含下方 4px 的自带阴影区）
d.rounded_rectangle((cx - thick / 2 - x0, cy - arm_half - y0, cx + thick / 2 - x0, cy + arm_half - y0 + 4), radius=8, fill=255)
d.rounded_rectangle((cx - arm_half - x0, cy - thick / 2 - y0, cx + arm_half - x0, cy + thick / 2 - y0 + 4), radius=8, fill=255)
crop.putalpha(mask)

# ---- 胶囊：圆角矩形掩膜 ----
def pill_sprite(bbox):
    x0, y0, x1, y1 = bbox
    crop = img.crop(bbox).convert("RGBA")
    mask = Image.new("L", crop.size, 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle((2, 2, crop.size[0] - 3, crop.size[1] - 3), radius=14, fill=255)
    crop.putalpha(mask)
    return crop

sel_sprite = pill_sprite((126, 140, 216, 182))
start_sprite = pill_sprite((216, 140, 304, 182))

# ---- 挖孔底图：按键区域填面板色 ----
base = img.copy()
bp = base.load()
def erase(bbox, color):
    x0, y0, x1, y1 = bbox
    for x in range(x0, x1):
        for y in range(y0, y1):
            bp[x, y] = color
# B/A 挖孔用各自位置的面板色（含底影区域一并挖掉）
erase((222, 50, 290, 122), panel_mid)
erase((297, 37, 366, 108), avg_panel(300, 20, 360, 36))
# 十字挖孔：臂掩膜范围内填凹槽内部色（采样臂外、凹槽内）
recess_color = avg_panel(24, 46, 44, 60)
d = ImageDraw.Draw(base)
d.rounded_rectangle((cx - thick / 2, cy - arm_half, cx + thick / 2, cy + arm_half + 4), radius=8, fill=recess_color)
d.rounded_rectangle((cx - arm_half, cy - thick / 2, cx + arm_half, cy + thick / 2 + 4), radius=8, fill=recess_color)
# 胶囊挖孔
erase((126, 140, 216, 184), panel_mid)
erase((216, 140, 304, 184), panel_mid)

base.save(out + "skin_pad_base.png", optimize=True)
b_sprite.save(out + "skin_btn_b.png", optimize=True)
a_sprite.save(out + "skin_btn_a.png", optimize=True)
crop.save(out + "skin_dpad.png", optimize=True)
sel_sprite.save(out + "skin_pill_select.png", optimize=True)
start_sprite.save(out + "skin_pill_start.png", optimize=True)

for n, im in (("base", base), ("dpad", crop), ("b", b_sprite), ("a", a_sprite), ("sel", sel_sprite), ("start", start_sprite)):
    print(n, im.size)

# 预览：底图 + 精灵就位（B 下沉 5px 演示起伏）
preview = base.copy()
pv = preview.load()
preview.paste(b_sprite, (222, 50 + 5), b_sprite)
preview.paste(a_sprite, (297, 37), a_sprite)
preview.paste(crop, cross_bbox, crop)
preview.paste(sel_sprite, (126, 140), sel_sprite)
preview.paste(start_sprite, (216, 140), start_sprite)
preview.save(r"C:\Users\biscuit\AppData\Local\Temp\skin-preview.png")
print("preview saved")
