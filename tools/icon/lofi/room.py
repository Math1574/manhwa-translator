# Petite chambre de nuit : fenêtre sur la ville, guirlande lumineuse, étagère, plante, tapis, mug fumant. Dessin en SVG (espace 0..100).
import io, random
import cairosvg
from PIL import Image
def room_svg(size):
    random.seed(3)
    cols = ["#8E6CF0", "#FF8FB8", "#4FD1B8", "#FFC56B", "#6C8CFF", "#C9A6FF", "#FF9E7A"]
    books = ""; x = 76.5
    for i in range(7):
        w = random.choice([2.2, 2.8, 3.2]); h = random.choice([8, 10, 12, 9]); c = cols[i % len(cols)]
        books += f'<rect x="{x:.1f}" y="{37-h:.1f}" width="{w}" height="{h}" rx="0.5" fill="{c}"/>'; x += w + 0.5
    books2 = ""; x = 76.5
    for i in range(7):
        w = random.choice([2.4, 3.0, 3.4]); h = random.choice([8, 11, 9, 12]); c = cols[(i + 3) % len(cols)]
        books2 += f'<rect x="{x:.1f}" y="{57-h:.1f}" width="{w}" height="{h}" rx="0.5" fill="{c}"/>'; x += w + 0.5
    stars = "".join(f'<circle cx="{random.uniform(9,29):.1f}" cy="{random.uniform(14,26):.1f}" r="{random.choice([0.25,0.35,0.5])}" fill="#FFF6DC" opacity="{random.uniform(0.6,1):.2f}"/>' for _ in range(16))
    skyline = "".join(f'<rect x="{x0}" y="{36-h}" width="{w}" height="{h}" fill="#1A1245"/>' + "".join(f'<rect x="{x0+0.8+k*1.6:.1f}" y="{36-h+1.4+j*2.2:.1f}" width="0.7" height="0.9" fill="#FFD98A" opacity="0.85"/>' for k in range(int(w//1.6)) for j in range(int((h-1.5)//2.2)) if random.random() > 0.45)
                      for x0, w, h in [(9, 4, 7), (13.5, 3.2, 10), (17, 4.6, 6), (22, 3.4, 9), (25.8, 3.6, 7)])
    bulbs = ""
    for t in [i / 8 for i in range(9)]:
        x = t * 100; y = 5 + 7 * (1 - (2 * t - 1) ** 2) * 0.9 + 1
        bulbs += f'<circle cx="{x:.1f}" cy="{y:.1f}" r="1.1" fill="#FFE0A0"/><circle cx="{x:.1f}" cy="{y:.1f}" r="2.6" fill="url(#bulb)"/>'
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 100 100">
<defs>
 <linearGradient id="wall" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#1B1349"/><stop offset="0.6" stop-color="#2C2062"/><stop offset="1" stop-color="#3A2A7A"/></linearGradient>
 <linearGradient id="sky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#0A1250"/><stop offset="0.7" stop-color="#4E3A9C"/><stop offset="1" stop-color="#C27FB5"/></linearGradient>
 <linearGradient id="floor" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#4A2F6E"/><stop offset="1" stop-color="#241446"/></linearGradient>
 <radialGradient id="bulb" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#FFE9B0" stop-opacity="0.8"/><stop offset="1" stop-color="#FFE9B0" stop-opacity="0"/></radialGradient>
 <linearGradient id="leaf" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#6FE3A6"/><stop offset="1" stop-color="#1E8F6E"/></linearGradient>
 <linearGradient id="pot" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#E88A5E"/><stop offset="1" stop-color="#A8482A"/></linearGradient>
 <linearGradient id="wood" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#5B3A8A"/><stop offset="1" stop-color="#3A2468"/></linearGradient>
 <radialGradient id="rug" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#C77DB4"/><stop offset="1" stop-color="#7A4A9A"/></radialGradient>
</defs>
<rect width="100" height="100" fill="url(#wall)"/>
<rect x="6.5" y="10" width="26.5" height="30" rx="2" fill="#3A2A78"/>
<rect x="8" y="11.5" width="23.5" height="27" rx="1.2" fill="url(#sky)"/>
{stars}
<path d="M17.4,15.2 C13.6,15.8 12,20.2 14.2,23.4 C16,26 20.2,26.4 22.4,23.8 C19.6,24.2 16.8,22.4 16.6,19.2 C16.5,17.6 16.8,16.4 17.4,15.2Z" fill="#FFF1CC"/>
<g>{skyline}</g>
<path d="M19.75,11.5 L19.75,38.5 M8,25 L31.5,25" stroke="#3A2A78" stroke-width="1.1"/>
<path d="M6.5,40.6 L33,40.6" stroke="#4B3690" stroke-width="1.6" stroke-linecap="round"/>
<path d="M-2,5 C25,13 75,13 102,5" stroke="#1A1040" stroke-width="0.5" fill="none"/>{bulbs}
<rect x="73.5" y="18" width="24" height="46" rx="1.6" fill="url(#wood)"/>
<rect x="75" y="19.5" width="21" height="43" rx="0.8" fill="#201244"/>
<rect x="75" y="38" width="21" height="1.4" fill="#4B3690"/><rect x="75" y="58" width="21" height="1.4" fill="#4B3690"/>
{books}{books2}
<path d="M84,19.5 C83,15 86,13 89,14.5" stroke="#4FD1B8" stroke-width="0.6" fill="none"/><path d="M89,14.5 C92,12 95,14 94,17" stroke="#4FD1B8" stroke-width="0.6" fill="none"/>
<ellipse cx="89" cy="14.2" rx="2" ry="1.1" fill="#4FD1B8" opacity="0.9"/><ellipse cx="85" cy="16" rx="1.6" ry="1" fill="#6FE3A6"/>
<path d="M0,80 L100,80 L100,100 L0,100Z" fill="url(#floor)"/>
<ellipse cx="50" cy="91" rx="44" ry="9.5" fill="url(#rug)"/>
<ellipse cx="50" cy="91" rx="34" ry="6.8" fill="none" stroke="#F6C4DC" stroke-width="0.5" opacity="0.6"/>
<path d="M8.5,79 C6,70 5,62 8,55 C11,62 12,71 11,79Z" fill="url(#leaf)"/><path d="M10,79 C14,70 20,64 27,63 C24,70 18,76 13,79Z" fill="url(#leaf)"/>
<path d="M8,79 C3,72 1,66 -1,62 C5,64 9,70 10,79Z" fill="url(#leaf)"/><path d="M11,79 C17,76 22,77 26,80 C20,82 15,82 11,79Z" fill="#2BA67E"/>
<path d="M6,78 L16,78 L15,90 C15,91 14,92 13,92 L9,92 C8,92 7,91 7,90Z" fill="url(#pot)"/>
<path d="M82,86 L90,86 L89,93 C89,94 88,95 87,95 L85,95 C84,95 83,94 83,93Z" fill="#F4E4D0"/><path d="M90,87.6 C93,87.6 93,92 90,92" stroke="#F4E4D0" stroke-width="1" fill="none"/>
<path d="M84,84 C83,82 85,81 84,79 M87,84.4 C86,82.4 88,81.4 87,79.4" stroke="#FFFFFF" stroke-width="0.5" fill="none" opacity="0.6" stroke-linecap="round"/>
</svg>'''
def room_layer(size):
    return Image.open(io.BytesIO(cairosvg.svg2png(bytestring=room_svg(size).encode()))).convert("RGBA")
