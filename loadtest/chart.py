# -*- coding: utf-8 -*-
"""生成压测结果图表：chart.png（TPS 曲线 + 延迟分布）"""
import os
from PIL import Image, ImageDraw, ImageFont

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "chart.png")
FONT = r"C:\Windows\Fonts\msyh.ttc"
W, H = 1280, 560
BG = (255, 255, 255)
INK = (26, 26, 26)
GREY = (110, 110, 110)
GRID = (232, 232, 232)
BLUE = (31, 78, 121)
ORANGE = (230, 126, 34)
GREEN = (39, 174, 96)
RED = (192, 57, 43)

conc = [50, 100, 200]
tps = [318, 399, 350]
p50 = [45, 103, 248]
p95 = [103, 206, 662]
p99 = [148, 273, 1024]


def f(size, bold=False):
    try:
        return ImageFont.truetype(FONT, size)
    except Exception:
        return ImageFont.load_default()


img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img)

ft_title = f(22, True)
ft_sub = f(13)
ft_axis = f(13)
ft_val = f(13, True)

d.text((40, 24), "天机学堂 · 领取优惠券接口压测（JMeter 5.4.1）", font=ft_title, fill=INK)
d.text((40, 56), "网关 192.168.150.101:10010 → tj-promotion；CPU/内存指标未采集，仅接口维度", font=ft_sub, fill=GREY)

# ---- 左图：TPS ----
L, T, R, B = 80, 120, 600, 470
d.line([(L, T - 10), (L, B), (R, B)], fill=GREY, width=1)
max_tps = 500
for i in range(0, 6):
    y = B - (B - T) * i / 5
    d.line([(L, y), (R, y)], fill=GRID, width=1)
    d.text((L - 44, y - 8), str(int(max_tps * i / 5)), font=ft_axis, fill=GREY)

def x_of(c):
    return L + (R - L) * (c - 30) / (220 - 30)

pts = [(x_of(c), B - (B - T) * t / max_tps) for c, t in zip(conc, tps)]
d.line(pts, fill=BLUE, width=3)
for (x, y), c, t in zip(pts, conc, tps):
    d.ellipse([x - 5, y - 5, x + 5, y + 5], fill=BLUE)
    d.text((x - 16, y - 30), str(t), font=ft_val, fill=BLUE)
for c in conc:
    d.text((x_of(c) - 18, B + 10), "%d 并发" % c, font=ft_axis, fill=INK)
d.text((L, T - 32), "吞吐量 TPS", font=f(14, True), fill=BLUE)
d.text((x_of(100) - 40, B - (B - T) * 399 / max_tps - 58), "拐点", font=ft_sub, fill=GREY)

# ---- 右图：延迟 ----
L2, T2, R2, B2 = 720, 120, 1240, 470
d.line([(L2, T2 - 10), (L2, B2), (R2, B2)], fill=GREY, width=1)
max_ms = 1200
for i in range(0, 5):
    y = B2 - (B2 - T2) * i / 4
    d.line([(L2, y), (R2, y)], fill=GRID, width=1)
    d.text((L2 - 44, y - 8), str(int(max_ms * i / 4)), font=ft_axis, fill=GREY)

group_w = (R2 - L2) / len(conc)
bar_w = 34
series = [("P50", p50, GREEN), ("P95", p95, ORANGE), ("P99", p99, RED)]
for gi, c in enumerate(conc):
    gx = L2 + group_w * gi
    start = gx + group_w / 2 - (bar_w * 3 + 8) / 2
    for si, (name, vals, color) in enumerate(series):
        v = vals[gi]
        h = (B2 - T2) * v / max_ms
        x0 = start + si * (bar_w + 4)
        d.rectangle([x0, B2 - h, x0 + bar_w, B2], fill=color)
        d.text((x0 - 2, B2 - h - 20), str(v), font=f(11), fill=INK)
    d.text((gx + group_w / 2 - 18, B2 + 10), "%d 并发" % c, font=ft_axis, fill=INK)
d.text((L2, T2 - 32), "响应时间（ms）", font=f(14, True), fill=INK)
for i, (name, _, color) in enumerate(series):
    x = R2 - 180 + i * 60
    d.rectangle([x, T2 - 28, x + 10, T2 - 18], fill=color)
    d.text((x + 14, T2 - 32), name, font=ft_sub, fill=INK)

d.text((40, H - 34), "写路径：0 错误、0 超卖（8000 次成功 = Redis 库存减少 8000 = 落库 8000 行）", font=ft_sub, fill=GREY)

img.save(OUT)
print("saved", OUT)