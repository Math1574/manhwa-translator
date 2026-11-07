import io
import cairosvg
import numpy as np
from PIL import Image, ImageFilter, ImageChops

def render(svg, size=1024):
    return Image.open(io.BytesIO(cairosvg.svg2png(bytestring=svg.replace('SIZE', str(size)).encode()))).convert("RGBA")

def finish(img, ink=(40, 24, 92), halo=(255, 205, 140), halo_alpha=0.45, out=512):
    """Contour « encre » fin et contre-jour doux, puis rognage serré en carré."""
    a = img.split()[3]
    edge = a.filter(ImageFilter.MaxFilter(7)); ring = ImageChops.subtract(edge, a).filter(ImageFilter.GaussianBlur(1.2))
    res = Image.new("RGBA", img.size, (0, 0, 0, 0))
    if halo_alpha > 0:
        h = a.filter(ImageFilter.MaxFilter(31)).filter(ImageFilter.GaussianBlur(img.width * 0.022))
        hc = Image.new("RGBA", img.size, halo + (0,)); hc.putalpha(h.point(lambda v: int(v * halo_alpha))); res.alpha_composite(hc)
    ic = Image.new("RGBA", img.size, ink + (0,)); ic.putalpha(ring.point(lambda v: min(255, int(v * 0.85)))); res.alpha_composite(ic)
    res.alpha_composite(img)
    bb = res.split()[3].filter(ImageFilter.MaxFilter(9)).getbbox(); res = res.crop(bb)
    side = max(res.size); sq = Image.new("RGBA", (side + 40, side + 40), (0, 0, 0, 0)); sq.alpha_composite(res, ((sq.width - res.width) // 2, (sq.height - res.height) // 2))
    return sq.resize((out, out), Image.LANCZOS)

OWL = '''<svg xmlns="http://www.w3.org/2000/svg" width="SIZE" height="SIZE" viewBox="-6 -6 112 112">
<defs>
 <linearGradient id="teal" x1="20" y1="10" x2="76" y2="96" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#7FE0D0"/><stop offset="0.5" stop-color="#37A59C"/><stop offset="1" stop-color="#1D6B78"/></linearGradient>
 <linearGradient id="tealDark" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#2C8D8A"/><stop offset="1" stop-color="#16556A"/></linearGradient>
 <radialGradient id="chest" cx="0.5" cy="0.3" r="0.8"><stop offset="0" stop-color="#FFF6E2"/><stop offset="1" stop-color="#F0D4A2"/></radialGradient>
 <radialGradient id="disc" cx="0.5" cy="0.4" r="0.7"><stop offset="0" stop-color="#FFFBEF"/><stop offset="1" stop-color="#F3DDB3"/></radialGradient>
 <radialGradient id="iris" cx="0.4" cy="0.35" r="0.8"><stop offset="0" stop-color="#FFE48A"/><stop offset="0.6" stop-color="#FFB93A"/><stop offset="1" stop-color="#E5811C"/></radialGradient>
 <linearGradient id="beak" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFB347"/><stop offset="1" stop-color="#E5701A"/></linearGradient>
 <linearGradient id="gold" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FFE7A0"/><stop offset="1" stop-color="#D9A030"/></linearGradient>
 <linearGradient id="bookA" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#E5566B"/><stop offset="1" stop-color="#A62E48"/></linearGradient>
 <linearGradient id="bookB" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#8C6CF0"/><stop offset="1" stop-color="#5236B8"/></linearGradient>
 <radialGradient id="shadow" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#0B0720" stop-opacity="0.5"/><stop offset="1" stop-color="#0B0720" stop-opacity="0"/></radialGradient>
</defs>
<ellipse cx="50" cy="97" rx="32" ry="4.5" fill="url(#shadow)"/>
<!-- pile de livres -->
<rect x="22" y="86" width="56" height="9" rx="2" fill="url(#bookB)"/><rect x="24" y="88" width="52" height="1.6" fill="#F6E9C8" opacity="0.9"/>
<rect x="28" y="78" width="46" height="9" rx="2" fill="url(#bookA)"/><rect x="30" y="80" width="42" height="1.6" fill="#F6E9C8" opacity="0.9"/>
<!-- queue / ailes de fond -->
<path d="M16,48 C6,62 10,82 24,84 C20,72 22,58 28,50Z" fill="url(#tealDark)"/><path d="M84,48 C94,62 90,82 76,84 C80,72 78,58 72,50Z" fill="url(#tealDark)"/>
<!-- corps -->
<path d="M50,16 C76,16 82,40 80,60 C78,80 66,86 50,86 C34,86 22,80 20,60 C18,40 24,16 50,16Z" fill="url(#teal)"/>
<!-- aigrettes -->
<path d="M26,26 C20,16 20,8 24,2 C32,8 38,16 40,24Z" fill="url(#tealDark)"/><path d="M74,26 C80,16 80,8 76,2 C68,8 62,16 60,24Z" fill="url(#tealDark)"/>
<!-- poitrine plumée -->
<path d="M50,52 C66,52 72,66 70,74 C68,84 60,86 50,86 C40,86 32,84 30,74 C28,66 34,52 50,52Z" fill="url(#chest)"/>
<g stroke="#D9B47A" stroke-width="1.1" fill="none" stroke-linecap="round" opacity="0.8"><path d="M40,62 C42,65 46,65 48,62"/><path d="M52,62 C54,65 58,65 60,62"/><path d="M36,70 C38,73 42,73 44,70"/><path d="M46,70 C48,73 52,73 54,70"/><path d="M56,70 C58,73 62,73 64,70"/><path d="M42,78 C44,81 48,81 50,78"/><path d="M50,78 C52,81 56,81 58,78"/></g>
<!-- disque facial et yeux -->
<circle cx="37" cy="40" r="16.5" fill="url(#disc)"/><circle cx="63" cy="40" r="16.5" fill="url(#disc)"/>
<circle cx="37" cy="41" r="10.2" fill="url(#iris)"/><circle cx="63" cy="41" r="10.2" fill="url(#iris)"/>
<circle cx="37.6" cy="41.6" r="5.6" fill="#241638"/><circle cx="62.4" cy="41.6" r="5.6" fill="#241638"/>
<circle cx="34.6" cy="38" r="2.5" fill="#FFFFFF"/><circle cx="59.6" cy="38" r="2.5" fill="#FFFFFF"/><circle cx="40.2" cy="44.6" r="1.1" fill="#FFFFFF" opacity="0.9"/><circle cx="65.2" cy="44.6" r="1.1" fill="#FFFFFF" opacity="0.9"/>
<!-- sourcils plumeux, l'un relevé -->
<path d="M24,27 C30,23 38,23 44,27" stroke="#1D6B78" stroke-width="2.4" fill="none" stroke-linecap="round"/><path d="M56,24 C62,19 70,20 76,25" stroke="#1D6B78" stroke-width="2.4" fill="none" stroke-linecap="round"/>
<!-- lunettes rondes -->
<g fill="none" stroke="url(#gold)" stroke-width="2.4"><circle cx="37" cy="41" r="13.2"/><circle cx="63" cy="41" r="13.2"/><path d="M50,39 C48,37.4 52,37.4 50,39" stroke-width="2"/></g>
<path d="M26,35 C28,31 31,29.6 34,29.4" stroke="#FFFFFF" stroke-width="1.5" fill="none" stroke-linecap="round" opacity="0.8"/><path d="M52,35 C54,31 57,29.6 60,29.4" stroke="#FFFFFF" stroke-width="1.5" fill="none" stroke-linecap="round" opacity="0.8"/>
<!-- bec -->
<path d="M45.6,50 C48,47.4 52,47.4 54.4,50 C53.6,54.6 51.6,58 50,59.4 C48.4,58 46.4,54.6 45.6,50Z" fill="url(#beak)"/>
<!-- ailes avant : celle de droite tient un crayon -->
<path d="M22,56 C12,60 12,76 22,82 C28,76 30,64 28,56Z" fill="url(#teal)"/><path d="M20,66 C17,70 18,74 20,77" stroke="#1D6B78" stroke-width="1" fill="none" opacity="0.7"/>
<g transform="translate(13,18) rotate(24 78 64)"><rect x="73" y="38" width="5.2" height="30" rx="1" fill="#FFD25E"/><rect x="73" y="38" width="2" height="30" fill="#FFE9A6"/><path d="M73,68 L78.2,68 L75.6,75 Z" fill="#F3D8B0"/><path d="M74.6,72 L77,72 L75.6,75Z" fill="#3A2A55"/><rect x="73" y="35" width="5.2" height="4" rx="1.2" fill="#FF8FB8"/></g>
<path d="M78,56 C88,60 88,76 78,82 C72,76 70,64 72,56Z" fill="url(#teal)"/>
<!-- pattes -->
<g fill="url(#beak)"><path d="M38,84 L35,88 M38,84 L38,88.4 M38,84 L41,88"/></g>
<g stroke="#E5701A" stroke-width="2" stroke-linecap="round" fill="none"><path d="M40,83 L37,87 M40,83 L40,87.4 M40,83 L43,87"/><path d="M60,83 L57,87 M60,83 L60,87.4 M60,83 L63,87"/></g>
</svg>'''

DRAGON = '''<svg xmlns="http://www.w3.org/2000/svg" width="SIZE" height="SIZE" viewBox="-6 -6 112 112">
<defs>
 <linearGradient id="red" x1="20" y1="10" x2="76" y2="96" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#FFA08A"/><stop offset="0.5" stop-color="#F2635E"/><stop offset="1" stop-color="#C73050"/></linearGradient>
 <linearGradient id="redDark" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#E5524F"/><stop offset="1" stop-color="#A32447"/></linearGradient>
 <radialGradient id="belly" cx="0.5" cy="0.3" r="0.8"><stop offset="0" stop-color="#FFF1C9"/><stop offset="1" stop-color="#FFCB7D"/></radialGradient>
 <linearGradient id="wing" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FFC4D6"/><stop offset="1" stop-color="#E884B0"/></linearGradient>
 <linearGradient id="horn" x1="0" y1="1" x2="0" y2="0"><stop offset="0" stop-color="#F2C46B"/><stop offset="1" stop-color="#FFF2C4"/></linearGradient>
 <radialGradient id="eyeI" cx="0.4" cy="0.35" r="0.8"><stop offset="0" stop-color="#7BE6D0"/><stop offset="1" stop-color="#1F9C8C"/></radialGradient>
 <radialGradient id="flame" cx="0.5" cy="0.75" r="0.8"><stop offset="0" stop-color="#FFF8D2"/><stop offset="0.45" stop-color="#FFC94A"/><stop offset="1" stop-color="#FF6A2E"/></radialGradient>
 <radialGradient id="fglow" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#FFD66B" stop-opacity="0.65"/><stop offset="1" stop-color="#FFD66B" stop-opacity="0"/></radialGradient>
 <radialGradient id="blush" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#FF6F91" stop-opacity="0.65"/><stop offset="1" stop-color="#FF6F91" stop-opacity="0"/></radialGradient>
 <radialGradient id="shadow" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#0B0720" stop-opacity="0.5"/><stop offset="1" stop-color="#0B0720" stop-opacity="0"/></radialGradient>
</defs>
<ellipse cx="50" cy="96" rx="32" ry="4.5" fill="url(#shadow)"/>
<!-- ailes -->
<path d="M26,52 C6,36 4,16 10,6 C20,14 32,28 36,44Z" fill="url(#wing)"/><path d="M26,52 C14,38 10,22 10,6 M26,52 C20,36 18,24 20,12" stroke="#D2648F" stroke-width="1.4" fill="none" stroke-linecap="round"/>
<path d="M74,52 C94,36 96,16 90,6 C80,14 68,28 64,44Z" fill="url(#wing)"/><path d="M74,52 C86,38 90,22 90,6 M74,52 C80,36 82,24 80,12" stroke="#D2648F" stroke-width="1.4" fill="none" stroke-linecap="round"/>
<!-- queue à pointe en flèche -->
<path d="M70,88 C92,92 100,76 92,66 C88,61 82,64 85,70 C87,75 92,76 89,82 C86,87 78,87 70,84Z" fill="url(#redDark)"/>
<path d="M90,62 L100,58 L96,70 Z" fill="url(#horn)" transform="rotate(8 94 64)"/>
<!-- corps -->
<path d="M24,76 C16,94 28,102 50,102 C72,102 84,94 76,76 C70,60 30,60 24,76Z" fill="url(#red)"/>
<ellipse cx="50" cy="86" rx="14" ry="13" fill="url(#belly)"/>
<g stroke="#F2B26A" stroke-width="1" fill="none" stroke-linecap="round" opacity="0.8"><path d="M40,80 C47,83 53,83 60,80"/><path d="M38,86 C46,89.4 54,89.4 62,86"/><path d="M40,92 C47,95 53,95 60,92"/></g>
<!-- piques dorsales -->
<g fill="url(#horn)"><path d="M44,60 L50,50 L56,60Z"/><path d="M33,64 L36,55 L42,63Z"/><path d="M58,63 L64,55 L67,64Z"/></g>
<!-- cornes et nageoires -->
<path d="M32,24 C26,14 28,6 32,2 C38,8 42,16 42,24Z" fill="url(#horn)"/><path d="M68,24 C74,14 72,6 68,2 C62,8 58,16 58,24Z" fill="url(#horn)"/>
<path d="M18,40 C8,38 6,30 8,24 C14,28 20,32 22,38Z" fill="url(#wing)"/><path d="M82,40 C92,38 94,30 92,24 C86,28 80,32 78,38Z" fill="url(#wing)"/>
<!-- tête -->
<path d="M50,18 C74,18 86,32 86,48 C86,64 72,72 50,72 C28,72 14,64 14,48 C14,32 26,18 50,18Z" fill="url(#red)"/>
<path d="M45.2,57 C46,55.6 47.6,55.8 48,57" stroke="#8F2440" stroke-width="1.3" fill="none" stroke-linecap="round"/><path d="M52,57 C52.4,55.8 54,55.6 54.8,57" stroke="#8F2440" stroke-width="1.3" fill="none" stroke-linecap="round"/>
<ellipse cx="27" cy="58" rx="7.5" ry="4.8" fill="url(#blush)"/><ellipse cx="73" cy="58" rx="7.5" ry="4.8" fill="url(#blush)"/>
<!-- yeux et sourcils : sourire malicieux -->
<ellipse cx="35" cy="45" rx="8.2" ry="9" fill="#FFF7EE"/><ellipse cx="65" cy="45" rx="8.2" ry="9" fill="#FFF7EE"/>
<ellipse cx="36" cy="46.4" rx="6" ry="6.8" fill="url(#eyeI)"/><ellipse cx="64" cy="46.4" rx="6" ry="6.8" fill="url(#eyeI)"/>
<ellipse cx="36.6" cy="47" rx="3.3" ry="4.4" fill="#1A0F33"/><ellipse cx="63.4" cy="47" rx="3.3" ry="4.4" fill="#1A0F33"/>
<circle cx="34.4" cy="43.8" r="2" fill="#FFFFFF"/><circle cx="62" cy="43.8" r="2" fill="#FFFFFF"/><circle cx="38.4" cy="49.6" r="0.9" fill="#FFFFFF" opacity="0.9"/><circle cx="65.6" cy="49.6" r="0.9" fill="#FFFFFF" opacity="0.9"/>
<path d="M26,35.4 C30,32.6 36,32.4 42,34" stroke="#7A1E3C" stroke-width="1.7" fill="none" stroke-linecap="round"/><path d="M57,31.6 C63,28.4 70,29.2 74,33" stroke="#7A1E3C" stroke-width="1.7" fill="none" stroke-linecap="round"/>
<path d="M40,64 C45,69 55,69 61,62.6" stroke="#7A1E3C" stroke-width="1.5" fill="none" stroke-linecap="round"/><path d="M44.5,66.2 L46.2,69.8 L48.2,67.4Z" fill="#FFFFFF" stroke="#7A1E3C" stroke-width="0.5" stroke-linejoin="round"/>
<!-- petite flamme dans la patte -->
<circle cx="80" cy="76" r="13" fill="url(#fglow)"/>
<path d="M80,64 C86,70 88,76 84,81 C82,83.4 78,83.4 76,81 C72,76 75,70 80,64Z" fill="url(#flame)"/><path d="M80,72 C83,75 83,79 81,80.6 C79,80.6 77.6,78 80,72Z" fill="#FFF6C8" opacity="0.9"/>
<ellipse cx="78" cy="86" rx="5.6" ry="4.2" fill="url(#red)"/><ellipse cx="23" cy="86" rx="5.2" ry="4.2" fill="url(#red)"/>
</svg>'''

CLOUD = '''<svg xmlns="http://www.w3.org/2000/svg" width="SIZE" height="SIZE" viewBox="-6 -6 112 112">
<defs>
 <linearGradient id="cl" x1="0" y1="0" x2="0.4" y2="1"><stop offset="0" stop-color="#FFFFFF"/><stop offset="0.6" stop-color="#EBE3FF"/><stop offset="1" stop-color="#C9BBF5"/></linearGradient>
 <linearGradient id="cap" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#7C8CFF"/><stop offset="1" stop-color="#3F3AA8"/></linearGradient>
 <radialGradient id="blush" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#FF8FB4" stop-opacity="0.85"/><stop offset="1" stop-color="#FF8FB4" stop-opacity="0"/></radialGradient>
 <radialGradient id="star" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#FFFBE0"/><stop offset="1" stop-color="#FFD66B"/></radialGradient>
 <radialGradient id="shadow" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#0B0720" stop-opacity="0.35"/><stop offset="1" stop-color="#0B0720" stop-opacity="0"/></radialGradient>
</defs>
<ellipse cx="50" cy="96" rx="26" ry="3.6" fill="url(#shadow)"/>
<!-- nuage : cercles superposés, puis ventre doux -->
<g fill="url(#cl)">
 <circle cx="27" cy="68" r="19"/><circle cx="74" cy="68" r="19"/><circle cx="50" cy="52" r="26"/><circle cx="34" cy="50" r="17"/><circle cx="67" cy="50" r="17"/><ellipse cx="50" cy="76" rx="34" ry="18"/>
</g>
<path d="M18,76 C28,92 72,92 82,76 C74,86 26,86 18,76Z" fill="#B7A6EE" opacity="0.45"/>
<!-- bonnet de nuit -->
<path d="M26,34 C30,16 52,8 66,20 C60,18 48,20 40,30 C34,36 30,38 26,34Z" fill="url(#cap)"/>
<path d="M24,36 C32,38 58,30 70,24 C72,28 66,32 58,36 C48,40 34,42 24,36Z" fill="#F4EEFF"/>
<path d="M66,20 C74,16 82,22 80,32 C78,28 74,26 70,26" fill="url(#cap)"/>
<circle cx="82" cy="31" r="5" fill="url(#star)"/><path d="M82,24 L83.4,29.6 L89,31 L83.4,32.4 L82,38 L80.6,32.4 L75,31 L80.6,29.6Z" fill="#FFFBE0" opacity="0.95"/>
<g fill="#FFF6C8" opacity="0.95"><path d="M48,17 l1,2.6 l2.6,1 l-2.6,1 l-1,2.6 l-1,-2.6 l-2.6,-1 l2.6,-1Z"/><path d="M38,24 l0.7,1.8 l1.8,0.7 l-1.8,0.7 l-0.7,1.8 l-0.7,-1.8 l-1.8,-0.7 l1.8,-0.7Z"/></g>
<!-- visage endormi et content -->
<path d="M33,58 C36,53.4 41,53.4 44,58" stroke="#5A4BA8" stroke-width="2.4" fill="none" stroke-linecap="round"/><path d="M56,58 C59,53.4 64,53.4 67,58" stroke="#5A4BA8" stroke-width="2.4" fill="none" stroke-linecap="round"/>
<ellipse cx="30" cy="66" rx="7" ry="4.4" fill="url(#blush)"/><ellipse cx="70" cy="66" rx="7" ry="4.4" fill="url(#blush)"/>
<path d="M45,68 C48,72 52,72 55,68" stroke="#5A4BA8" stroke-width="2" fill="none" stroke-linecap="round"/>
<!-- petits bras, étoiles et « z » -->
<ellipse cx="12" cy="68" rx="6.4" ry="5" fill="url(#cl)"/><ellipse cx="88" cy="68" rx="6.4" ry="5" fill="url(#cl)"/>
<g fill="url(#star)"><path d="M10,36 l1.4,3.6 l3.6,1.4 l-3.6,1.4 l-1.4,3.6 l-1.4,-3.6 l-3.6,-1.4 l3.6,-1.4Z"/><path d="M93,52 l1,2.6 l2.6,1 l-2.6,1 l-1,2.6 l-1,-2.6 l-2.6,-1 l2.6,-1Z"/><path d="M6,84 l0.9,2.2 l2.2,0.9 l-2.2,0.9 l-0.9,2.2 l-0.9,-2.2 l-2.2,-0.9 l2.2,-0.9Z"/></g>
<g stroke="#FFFFFF" stroke-width="1.8" fill="none" stroke-linecap="round" stroke-linejoin="round" opacity="0.9"><path d="M88,8 L94,8 L88,14 L94,14"/><path d="M80,2 L84,2 L80,6 L84,6" stroke-width="1.4"/></g>
</svg>'''

if __name__ == "__main__":
    import os
    os.makedirs('/tmp/lofi/out', exist_ok=True)
    cfg = {"hibou": (OWL, (20, 60, 70), (255, 214, 150), 0.40), "dragon": (DRAGON, (90, 24, 56), (255, 190, 130), 0.50), "nuage": (CLOUD, (80, 64, 150), (190, 170, 255), 0.55)}
    ims = {}
    for name, (svg, ink, halo, ha) in cfg.items():
        im = finish(render(svg), ink=ink, halo=halo, halo_alpha=ha); im.save(f'/tmp/lofi/out/mascot_{name}.png', optimize=True); ims[name] = im
        print(name, os.path.getsize(f'/tmp/lofi/out/mascot_{name}.png') // 1024, 'Ko')
    sheet = Image.new("RGB", (512 * 3 + 20, 512), (15, 10, 31))
    for k, name in enumerate(ims):
        bg = Image.new("RGBA", (512, 512), (15, 10, 31, 255)); bg.alpha_composite(ims[name]); sheet.paste(bg.convert("RGB"), (k * 522, 0))
    sheet.save('/tmp/lofi/mascots_sheet.png')
