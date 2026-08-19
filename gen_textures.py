from PIL import Image
import os

OUT = "src/main/resources/resource_pack/assets/leet/textures/item"
os.makedirs(OUT, exist_ok=True)

def make(name, draw):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    draw(px)
    img.save(os.path.join(OUT, name + ".png"))
    print("wrote", name)

def set4(px, x, y, c):
    # draw a 4x4 block at (x,y) as cell coordinates
    for dy in range(4):
        for dx in range(4):
            if 0 <= x*4+dx < 16 and 0 <= y*4+dy < 16:
                px[x*4+dx, y*4+dy] = c

# SALT: scattered white/gray granules
def salt(px):
    granule = (235, 235, 235, 255)
    edge = (200, 200, 200, 255)
    dots = [(2,2),(5,3),(9,2),(12,4),(3,6),(7,5),(11,6),(4,9),(8,8),(13,9),(6,12),(10,12),(12,13),(2,12),(5,14)]
    for (x,y) in dots:
        px[x,y]=granule
    for (x,y) in dots:
        for (dx,dy) in [(-1,0),(1,0),(0,-1),(0,1)]:
            if (x+dx,y+dy) not in dots and 0<=x+dx<16 and 0<=y+dy<16 and px[x+dx,y+dy][3]==0:
                px[x+dx,y+dy]=edge
make("salt", salt)

# SOY SEED: small tan oval with a dark crease
def soy_seed(px):
    body = (214, 187, 130, 255)
    dark = (160, 128, 80, 255)
    highlight = (236, 216, 170, 255)
    cells = [(x,y) for y in range(3,10) for x in range(5,12)]
    for x,y in cells:
        # oval mask
        dx0 = abs(x-8); dy0 = abs((y-6)*1.5)
        if dx0*2 + dy0 > 6:
            continue
        px[x,y]=body
    px[8,8]=dark; px[7,9]=dark; px[8,9]=dark; px[9,8]=dark
    px[6,4]=highlight; px[7,4]=highlight; px[8,4]=highlight; px[8,5]=highlight
make("soy-seed", soy_seed)

# SOY OIL: pale yellow bottle
def soy_oil(px):
    bottle = (214, 196, 110, 255)
    dark = (170, 150, 80, 255)
    light = (240, 226, 150, 255)
    # bottle silhouette: neck at top, body below
    for y in range(2,4):
        for x in range(7,10):
            px[x,y]=bottle
    for x in range(6,11):
        px[x,3]=dec(px[x,3],bottle)
        px[x,2]=dec(px[x,2],bottle)
    for y in range(4,14):
        for x in range(5,12):
            if y==4 or y==13:
                if 5<=x<=11: px[x,y]=bottle
            else:
                if 6<=x<=10: px[x,y]=bottle
    # cork
    for x in range(6,11):
        px[x,1]=(168,132,60,255)
    # highlight streak
    for y in range(5,12):
        for x in range(6,7):
            px[x,y]=light
    # bottom shade
    for y in range(12,14):
        for x in range(6,11):
            px[x,y]=dark
def dec(pixel,bottle):
    return bottle
make("soy-oil", soy_oil)

# SOY SAUCE: dark bottle with pale label
def soy_sauce(px):
    body = (40, 30, 25, 255)
    dark = (22, 15, 12, 255)
    label = (226, 216, 190, 255)
    for x in range(7,10):
        px[x,2]=body; px[x,3]=body
    for x in range(6,11):
        px[x,1]=(60,45,35,255)
    for y in range(4,14):
        for x in range(6,11):
            px[x,y]=body if not (y==12 or (10<=x<=10 and 10<=y<=11)) else dark
    px[8,12]=dark; px[7,13]=dark; px[8,13]=dark; px[9,13]=dark
    # label band
    for x in range(6,11):
        for y in range(6,9):
            px[x,y]=label
    px[8,7]=(0,0,0,255)
make("soy-sauce", soy_sauce)

# SOY CROP: four distinct growth-stage textures (sprout -> young -> bushy -> mature)
def _soy_stage(px, stage):
    transparent = (0, 0, 0, 0)
    soil = (120, 88, 52, 255)
    stem = (76, 138, 62, 255)
    leaf = (98, 168, 76, 255)
    dleaf = (70, 128, 56, 255)
    pod = (70, 96, 40, 255)
    podl = (110, 140, 56, 255)

    # how tall / how many leaf rows per stage
    heights = [(7,), (5,6), (4,5,6), (3,4,5,6)]
    rows = heights[stage]
    # stem through the row band
    for y in range(rows[0], 13):
        px[8, y] = stem
    # leaf pairs per row
    widths = [3, 3, 4, 4]
    spread = widths[stage]
    for row_y in rows:
        for dx in range(1, spread + 1):
            px[8 + dx, row_y] = leaf if (dx % 2 == 1) else dleaf
            px[8 - dx, row_y] = leaf if (dx % 2 == 1) else dleaf
        # a brighter dot in the center of each leaf tier
        px[8, row_y] = stem
    # top growing tip
    px[8, rows[0] - 1] = leaf
    px[9, rows[0] - 1] = leaf
    px[7, rows[0] - 1] = leaf
    # mature stage gets bean pods hanging off the lower leaves
    if stage == 3:
        for (px0, py0) in [(6, 7), (10, 6)]:
            px[px0, py0] = pod
            px[px0 + (0 if px0 < 8 else -1), py0 + 1] = podl
            px[px0 + (0 if px0 < 8 else -1), py0 + 2] = pod
    # soil mound at base
    for x in range(4, 13):
        px[x, 13] = soil
    for x in range(5, 12):
        px[x, 14] = soil

for si in range(4):
    def stage_draw(px, s=si):
        _soy_stage(px, s)
    make("soy-crop-" + str(si), stage_draw)

print("done")
