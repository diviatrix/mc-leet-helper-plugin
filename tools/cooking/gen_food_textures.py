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

# PRETZEL: tan twisted loop / pretzel shape
def pretzel(px):
    tan = (196, 150, 88, 255)
    dark = (150, 108, 60, 255)
    salt = (240, 240, 240, 255)
    # rough pretzel: a ring with a twist at the top
    for dx in range(4):
        px[6+dx, 9] = tan; px[6+dx, 10] = tan
    for dx in range(4):
        px[5+dx, 4] = tan
    px[5,4]=tan; px[10,4]=tan
    px[5,5]=tan; px[10,5]=tan
    px[4,6]=tan; px[11,6]=tan
    px[4,7]=tan; px[11,7]=tan
    px[4,8]=tan; px[11,8]=tan
    px[5,10]=tan; px[10,10]=tan
    # twist center
    px[8,6]=tan; px[8,5]=tan; px[7,5]=tan; px[9,5]=tan
    # salt flecks
    for (x,y) in [(7,2),(9,2),(8,8),(7,11),(10,11),(6,3),(11,3)]:
        px[x,y]=salt
make("pretzel", pretzel)

# BEEF JERKY: dark-brown dried strips
def beef_jerky(px):
    strips = [(110,56,36,255),(96,44,26,255),(124,68,44,255)]
    for s in range(3):
        y = 4 + s*4
        c = strips[s]
        for x in range(3,14):
            px[x,y]=c
        px[3,y]=strips[(s+1)%3]; px[13,y]=strips[(s+2)%3]
        px[4,y+1]=c; px[5,y+1]=c
make("beef-jerky", beef_jerky)

# CHICKEN JERKY: tan dried strips
def chicken_jerky(px):
    strips = [(188,150,96,255),(170,132,80,255),(200,164,110,255)]
    for s in range(3):
        y = 4 + s*4
        c = strips[s]
        for x in range(3,14):
            px[x,y]=c
        px[3,y]=strips[(s+1)%3]; px[13,y]=strips[(s+2)%3]
        px[4,y+1]=c; px[5,y+1]=c
make("chicken-jerky", chicken_jerky)

# JAMON (ham): rosy cured ham slice with white fat rind
def hamon(px):
    meat = (216, 140, 120, 255)
    dark = (170, 96, 80, 255)
    fat = (250, 240, 225, 255)
    rind = (150, 90, 70, 255)
    # ham slab with fat cap
    for x in range(3,14):
        px[x,2]=rind
        px[x,3]=fat
    for y in range(4,13):
        for x in range(3,14):
            px[x,y]=meat
    # marbling
    for (x,y) in [(5,6),(8,8),(11,5),(6,10),(9,11),(12,9)]:
        px[x,y]=dark
make("hamon", hamon)

# POTATO CHIPS: golden curled chips
def potato_chips(px):
    chip = (230, 180, 90, 255)
    dc = (196, 142, 60, 255)
    # a few overlapping curled chips
    for (cx,cy) in [(4,5),(8,8),(6,10)]:
        for dx in range(4):
            for dy in range(3):
                px[cx+dx,cy+dy]=chip
        px[cx,cy]=dc; px[cx+3,cy]=dc
        px[cx+1,cy+2]=dc; px[cx+2,cy]=dc
make("potato-chips", potato_chips)

# DRY SALMON: orange-pink dried fillet
def dry_salmon(px):
    body = (226, 130, 90, 255)
    stripe = (236, 170, 120, 255)
    edge = (150, 70, 50, 255)
    for y in range(3,13):
        for x in range(3,14):
            px[x,y]= body if (y%2==0) else stripe
    for x in range(3,14):
        px[x,3]=edge; px[x,12]=edge
make("dry-salmon", dry_salmon)

# DRY COD: pale salted dried fillet
def dry_cod(px):
    body = (226, 216, 178, 255)
    stripe = (206, 190, 148, 255)
    edge = (150, 130, 100, 255)
    for y in range(3,13):
        for x in range(3,14):
            px[x,y]= body if (y%2==0) else stripe
    for x in range(3,14):
        px[x,3]=edge; px[x,12]=edge
make("dry-cod", dry_cod)

# CHOCOLATE BAR: brown bar with scored segments
def chocolate_bar(px):
    body = (92, 56, 30, 255)
    dark = (60, 34, 16, 255)
    light = (120, 76, 42, 255)
    # bar outline
    for x in range(3,14):
        px[x,4]=body; px[x,12]=body
    for y in range(4,13):
        px[3,y]=body; px[13,y]=body
    for y in range(5,12):
        for x in range(4,13):
            px[x,y]=body
    # scored segments (cross cuts)
    for x in range(6,7):
        for y in range(5,12):
            px[x,y]=dark
    for x in range(9,10):
        for y in range(5,12):
            px[x,y]=dark
    for y in [7,8]:
        for x in range(4,13):
            px[x,y]=dark
    # top shine
    for x in range(4,6):
        px[x,5]=light
    px[4,5]=light; px[5,5]=light
make("chocolate-bar", chocolate_bar)

# CHOCOLATE PIECE: a single square segment
def chocolate_piece(px):
    body = (92, 56, 30, 255)
    dark = (60, 34, 16, 255)
    light = (120, 76, 42, 255)
    # small square
    for y in range(5,12):
        for x in range(4,13):
            px[x,y]=body
    for x in range(4,13):
        px[x,5]=body; px[x,11]=body
    for y in range(5,12):
        px[4,y]=body; px[12,y]=body
    px[4,5]=light; px[5,5]=light; px[6,5]=light
    px[5,6]=light; px[6,6]=light
    px[8,8]=dark
make("chocolate-piece", chocolate_piece)

print("done")
