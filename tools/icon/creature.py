# Mascotte originale : petit esprit rond, oreilles de fennec, pousse-feuille lumineuse sur la tête, sourire en coin.
# Dessinée en SVG (espace 0..100), rendue avec cairosvg.
def creature_svg(size=1000, with_shadow=True):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="-8 -10 116 116">
<defs>
 <linearGradient id="fur" x1="22" y1="18" x2="80" y2="92" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#A29CFF"/><stop offset="0.5" stop-color="#6D62F2"/><stop offset="1" stop-color="#3F34B4"/></linearGradient>
 <linearGradient id="furDark" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#4C42C8"/><stop offset="1" stop-color="#2B2285"/></linearGradient>
 <radialGradient id="belly" cx="50" cy="82" r="15" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#FFF6DC"/><stop offset="0.7" stop-color="#F8DDAE"/><stop offset="1" stop-color="#EBC48C"/></radialGradient>
 <radialGradient id="muzzleSolid" cx="0.5" cy="0.35" r="0.8"><stop offset="0" stop-color="#FFF4DC"/><stop offset="1" stop-color="#F3CFA0"/></radialGradient>
 <radialGradient id="muzzle" cx="50" cy="61" r="15" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#FFF3D8" stop-opacity="0.95"/><stop offset="0.5" stop-color="#F8DEB4" stop-opacity="0.85"/><stop offset="1" stop-color="#F6DBB0" stop-opacity="0"/></radialGradient>
 <linearGradient id="inner" x1="0" y1="1" x2="0" y2="0"><stop offset="0" stop-color="#FF9A6B"/><stop offset="1" stop-color="#FFD0A0"/></linearGradient>
 <radialGradient id="iris" cx="0.4" cy="0.35" r="0.75"><stop offset="0" stop-color="#FFE27A"/><stop offset="0.55" stop-color="#FFB234"/><stop offset="1" stop-color="#E8741A"/></radialGradient>
 <linearGradient id="leaf" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#A5F5C8"/><stop offset="1" stop-color="#2FA98C"/></linearGradient>
 <radialGradient id="bud" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#FFFBE0"/><stop offset="0.45" stop-color="#FFD66B"/><stop offset="1" stop-color="#FFD66B" stop-opacity="0"/></radialGradient>
 <radialGradient id="blush" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#FF8FA8" stop-opacity="0.75"/><stop offset="1" stop-color="#FF8FA8" stop-opacity="0"/></radialGradient>
 <radialGradient id="shadow" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#0B0720" stop-opacity="0.55"/><stop offset="1" stop-color="#0B0720" stop-opacity="0"/></radialGradient>
 <radialGradient id="rim" cx="0.25" cy="0.2" r="0.8"><stop offset="0" stop-color="#FFFFFF" stop-opacity="0.38"/><stop offset="0.5" stop-color="#FFFFFF" stop-opacity="0"/></radialGradient>
</defs>
{'<ellipse cx="50" cy="94" rx="30" ry="5" fill="url(#shadow)"/>' if with_shadow else ''}
<!-- queue touffue, derrière le corps -->
<g>
 <path d="M66,93 C84,98 98,90 95,78 C93,70 85,70 85,77 C85,82 90,82 88,86 C86,90 77,91 69,87Z" fill="url(#furDark)"/>
 <path d="M97,79 C99,72 94,67 89,70 C86,72 87,78 91,80 C94,81 96,81 97,79Z" fill="url(#belly)"/>
</g>
<!-- corps -->
<path d="M28,74 C20,90 30,100 50,100 C70,100 80,90 72,74 C66,66 34,66 28,74Z" fill="url(#fur)"/>
<clipPath id="bodyClip"><path d="M28,74 C20,90 30,100 50,100 C70,100 80,90 72,74 C66,66 34,66 28,74Z"/></clipPath>
<g clip-path="url(#bodyClip)"><ellipse cx="50" cy="75.5" rx="27" ry="6.5" fill="#1B1260" fill-opacity="0.3"/></g>
<ellipse cx="50" cy="85" rx="13.5" ry="12" fill="url(#belly)"/>
<!-- pieds -->
<ellipse cx="38" cy="98" rx="8.5" ry="4.2" fill="url(#furDark)"/><ellipse cx="62" cy="98" rx="8.5" ry="4.2" fill="url(#furDark)"/>
<!-- bras -->
<g>
 <ellipse cx="31.5" cy="83" rx="4.6" ry="8.6" transform="rotate(14 31.5 83)" fill="url(#fur)"/><ellipse cx="34" cy="90.4" rx="3.3" ry="2.7" fill="url(#belly)"/>
 <ellipse cx="68.5" cy="83" rx="4.6" ry="8.6" transform="rotate(-14 68.5 83)" fill="url(#fur)"/><ellipse cx="66" cy="90.4" rx="3.3" ry="2.7" fill="url(#belly)"/>
</g>
<!-- oreilles -->
<g>
 <path d="M27,36 C14,24 8,8 11,-1 C25,3 40,14 44,28Z" fill="url(#fur)"/>
 <path d="M29,31 C21,22 17,12 18,5 C27,9 36,17 39,27Z" fill="url(#inner)"/>
 <path d="M73,36 C86,24 94,10 93,0 C79,3 62,14 58,28Z" fill="url(#fur)"/>
 <path d="M71,31 C79,22 85,13 85,6 C76,10 66,18 63,27Z" fill="url(#inner)"/>
</g>
<!-- tête -->
<path d="M50,20 C72,20 85,33 85,50 C85,66 70,75 50,75 C30,75 15,66 15,50 C15,33 28,20 50,20Z" fill="url(#fur)"/>
<path d="M17,52 C13,56 14,62 19,63 Z" fill="url(#fur)"/><path d="M83,52 C87,56 86,62 81,63 Z" fill="url(#fur)"/>
<clipPath id="headClip"><path d="M50,20 C72,20 85,33 85,50 C85,66 70,75 50,75 C30,75 15,66 15,50 C15,33 28,20 50,20Z"/></clipPath>
<g clip-path="url(#headClip)"><rect x="10" y="15" width="80" height="65" fill="url(#rim)"/></g>
<!-- museau et joues -->
<ellipse cx="50" cy="62.2" rx="14" ry="9.6" fill="url(#muzzleSolid)"/>
<ellipse cx="28.5" cy="61" rx="7.5" ry="4.8" fill="url(#blush)"/><ellipse cx="71.5" cy="61" rx="7.5" ry="4.8" fill="url(#blush)"/>
<!-- yeux -->
<g>
 <ellipse cx="36" cy="50" rx="9" ry="10" fill="#FFF7E6"/>
 <ellipse cx="64" cy="50" rx="9" ry="10" fill="#FFF7E6"/>
 <ellipse cx="37" cy="51.5" rx="6.6" ry="7.6" fill="url(#iris)"/><ellipse cx="63" cy="51.5" rx="6.6" ry="7.6" fill="url(#iris)"/>
 <ellipse cx="37.6" cy="52" rx="3.7" ry="5" fill="#22143F"/><ellipse cx="62.4" cy="52" rx="3.7" ry="5" fill="#22143F"/>
 <circle cx="35.2" cy="48.4" r="2.1" fill="#FFFFFF"/><circle cx="60.8" cy="48.4" r="2.1" fill="#FFFFFF"/>
 <circle cx="39.6" cy="55" r="1" fill="#FFFFFF" fill-opacity="0.9"/><circle cx="65" cy="55" r="1" fill="#FFFFFF" fill-opacity="0.9"/>
 <!-- paupières mi-closes : regard en coin -->
 <path d="M25.6,49.2 C24,36 47,34.5 46.4,47.6 C41,45 31,45.2 25.6,49.2Z" fill="url(#fur)"/>
 <path d="M74.4,49.2 C76,36 53,34.5 53.6,47.6 C59,45 69,45.2 74.4,49.2Z" fill="url(#fur)"/>
 <clipPath id="lidClip"><path d="M25.6,49.2 C24,36 47,34.5 46.4,47.6 C41,45 31,45.2 25.6,49.2Z"/><path d="M74.4,49.2 C76,36 53,34.5 53.6,47.6 C59,45 69,45.2 74.4,49.2Z"/></clipPath>
 <g clip-path="url(#lidClip)"><rect x="10" y="15" width="80" height="65" fill="url(#rim)"/></g>
 <path d="M25.6,49.2 C31,45.2 41,45 46.4,47.6" stroke="#241A74" stroke-width="1.1" fill="none" stroke-linecap="round"/>
 <path d="M74.4,49.2 C69,45.2 59,45 53.6,47.6" stroke="#241A74" stroke-width="1.1" fill="none" stroke-linecap="round"/>
 <!-- sourcil droit relevé -->
 <path d="M56,37 C62,33 70,33.5 74,37" stroke="#241A74" stroke-width="1.7" fill="none" stroke-linecap="round"/>
 <path d="M27,39.5 C31,37.6 37,37.3 42,38.6" stroke="#241A74" stroke-width="1.7" fill="none" stroke-linecap="round"/>
</g>
<!-- nez, sourire en coin et croc -->
<path d="M47.7,58 C49,56.8 51,56.8 52.3,58 C51.6,60.2 48.4,60.2 47.7,58Z" fill="#2A1B5C"/>
<path d="M40.5,64.2 C45,71 55,70.6 61.8,61.2" stroke="#2A1B5C" stroke-width="1.5" fill="none" stroke-linecap="round"/>
<path d="M44.8,67.2 L46.6,71.3 L48.6,68.4 Z" fill="#FFFFFF" stroke="#2A1B5C" stroke-width="0.5" stroke-linejoin="round"/>
<!-- pousse-feuille lumineuse -->
<g>
 <path d="M50,22 C50,14 46,9 40,8" stroke="#2FA98C" stroke-width="2.2" fill="none" stroke-linecap="round"/>
 <path d="M40,8 C32,1 24,5 25,12 C31,15 37,13 40,8Z" fill="url(#leaf)"/>
 <path d="M40,8 C34,7 29,8.6 26.2,11.2" stroke="#1E8F78" stroke-width="0.7" fill="none" stroke-linecap="round" opacity="0.8"/>
 <path d="M50,17 C57,9 66,10 67,16 C61,20 54,20 50,17Z" fill="url(#leaf)"/>
 <circle cx="40" cy="8" r="5.2" fill="url(#bud)"/><circle cx="40" cy="8" r="1.7" fill="#FFF8D8"/>
</g>
</svg>'''
if __name__ == "__main__":
    import cairosvg, io
    from PIL import Image
    png = cairosvg.svg2png(bytestring=creature_svg(900).encode())
    im = Image.open(io.BytesIO(png)).convert("RGBA")
    bg = Image.new("RGBA", im.size, (34, 22, 84, 255)); bg.alpha_composite(im)
    bg.convert("RGB").save("/tmp/icon/creature_v1.png"); print("ok", im.size)
