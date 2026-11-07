# Chat lofi qui lit un livre lumineux. Dessin en SVG (espace 0..100) découpé en couches pour pouvoir éclairer le chat à la lueur du livre.
import io
import cairosvg
from PIL import Image

VB = "-8 -8 116 116"
DEFS = '''<defs>
 <linearGradient id="fur" x1="30" y1="8" x2="68" y2="100" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#FFBE7C"/><stop offset="0.5" stop-color="#F59A4B"/><stop offset="1" stop-color="#D86F2E"/></linearGradient>
 <linearGradient id="furDark" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#E88A3C"/><stop offset="1" stop-color="#B95A22"/></linearGradient>
 <radialGradient id="cream" cx="0.5" cy="0.4" r="0.7"><stop offset="0" stop-color="#FFF6E4"/><stop offset="1" stop-color="#F7D9A9"/></radialGradient>
 <linearGradient id="earIn" x1="0" y1="1" x2="0" y2="0"><stop offset="0" stop-color="#FF86A4"/><stop offset="1" stop-color="#FFB3C4"/></linearGradient>
 <linearGradient id="hp" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#CDB9FF"/><stop offset="1" stop-color="#8D68F0"/></linearGradient>
 <linearGradient id="iris" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#4FD1B8"/><stop offset="1" stop-color="#1F8C7E"/></linearGradient>
 <linearGradient id="pageL" x1="1" y1="0" x2="0" y2="0"><stop offset="0" stop-color="#FFFBE6"/><stop offset="0.6" stop-color="#FFEBB5"/><stop offset="1" stop-color="#F7CF86"/></linearGradient>
 <linearGradient id="pageR" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#FFFBE6"/><stop offset="0.6" stop-color="#FFEBB5"/><stop offset="1" stop-color="#F7CF86"/></linearGradient>
 <linearGradient id="cover" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#5A3FC0"/><stop offset="1" stop-color="#2D1C78"/></linearGradient>
 <linearGradient id="coverL" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#7656E6"/><stop offset="1" stop-color="#2B1A74"/></linearGradient>
 <linearGradient id="coverR" x1="1" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#7656E6"/><stop offset="1" stop-color="#2B1A74"/></linearGradient>
 <linearGradient id="rimTop" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFE3A0" stop-opacity="0.85"/><stop offset="1" stop-color="#FFE3A0" stop-opacity="0"/></linearGradient>
</defs>'''

def _svg(inner, size):
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="{VB}">{DEFS}{inner}</svg>'

BODY = '''
<clipPath id="tailClip"><path d="M70,94 C92,98 102,76 94,63 C90,56 81,58 83,67 C85,73 91,73 88,81 C85,88 77,89 69,86Z"/></clipPath>
<path d="M70,94 C92,98 102,76 94,63 C90,56 81,58 83,67 C85,73 91,73 88,81 C85,88 77,89 69,86Z" fill="url(#furDark)"/>
<g clip-path="url(#tailClip)" stroke="#A8481A" stroke-width="3" stroke-linecap="round" opacity="0.55" fill="none"><path d="M86,60 C90,63 97,64 100,62"/><path d="M82,70 C88,72 96,72 100,70"/><path d="M80,80 C86,82 94,81 98,79"/></g>
<path d="M23,80 C16,98 30,106 50,106 C70,106 84,98 77,80 C72,60 28,60 23,80Z" fill="url(#fur)"/>
<ellipse cx="50" cy="67" rx="11" ry="6.5" fill="url(#cream)"/>
<path d="M26,30 C20,16 22,5 27,-1 C37,3 47,12 49,22Z" fill="url(#fur)"/><path d="M29,25 C25,16 26,9 29,5 C35,8 41,13 43,19Z" fill="url(#earIn)"/>
<path d="M74,30 C80,16 78,5 73,-1 C63,3 53,12 51,22Z" fill="url(#fur)"/><path d="M71,25 C75,16 74,9 71,5 C65,8 59,13 57,19Z" fill="url(#earIn)"/>
<path d="M50,16 C72,16 77,30 77,40 C77,55 66,62 50,62 C34,62 23,55 23,40 C23,30 28,16 50,16Z" fill="url(#fur)"/>
<path d="M24,44 C19,47 20,54 26,54Z" fill="url(#fur)"/><path d="M76,44 C81,47 80,54 74,54Z" fill="url(#fur)"/>
<g stroke="#B9531C" stroke-width="1.7" stroke-linecap="round" opacity="0.75" fill="none"><path d="M50,18.5 L50,26"/><path d="M44,19.6 L45.2,26"/><path d="M56,19.6 L54.8,26"/></g>
<ellipse cx="50" cy="48" rx="11.5" ry="7.8" fill="url(#cream)"/>
<ellipse cx="32.5" cy="48.5" rx="5.2" ry="3.2" fill="#FF8FA8" opacity="0.45"/><ellipse cx="67.5" cy="48.5" rx="5.2" ry="3.2" fill="#FF8FA8" opacity="0.45"/>
<path d="M47.4,43.4 C49,42.2 51,42.2 52.6,43.4 C51.6,46 48.4,46 47.4,43.4Z" fill="#D2577A"/>
<path d="M50,46 L50,48.2 M50,48.2 C48.2,50.2 45.6,49.8 45,48.6 M50,48.2 C51.8,50.2 54.4,49.8 55,48.6" stroke="#5A2A3A" stroke-width="0.9" fill="none" stroke-linecap="round"/>
<g stroke="#FFF3DD" stroke-width="0.55" stroke-linecap="round" opacity="0.85" fill="none"><path d="M36,47 L21,44.5"/><path d="M36,49 L20,50"/><path d="M37,51 L24,55"/><path d="M64,47 L79,44.5"/><path d="M64,49 L80,50"/><path d="M63,51 L76,55"/></g>
<g>
 <ellipse cx="38.5" cy="38" rx="5.4" ry="5.8" fill="#241638"/><ellipse cx="61.5" cy="38" rx="5.4" ry="5.8" fill="#241638"/>
 <ellipse cx="38.5" cy="40.6" rx="4.2" ry="4.1" fill="url(#iris)"/><ellipse cx="61.5" cy="40.6" rx="4.2" ry="4.1" fill="url(#iris)"/>
 <ellipse cx="38.5" cy="41" rx="2.2" ry="2.9" fill="#150C28"/><ellipse cx="61.5" cy="41" rx="2.2" ry="2.9" fill="#150C28"/>
 <ellipse cx="38.2" cy="43.2" rx="2.6" ry="1.15" fill="#FFE7A6" opacity="0.95"/><ellipse cx="61.8" cy="43.2" rx="2.6" ry="1.15" fill="#FFE7A6" opacity="0.95"/>
 <circle cx="36.8" cy="36.6" r="1" fill="#FFFFFF"/><circle cx="59.8" cy="36.6" r="1" fill="#FFFFFF"/>
 <path d="M32.6,38 C33,32.6 44.4,32.6 44.8,38 C41.2,36 36.2,36 32.6,38Z" fill="url(#fur)"/>
 <path d="M55.2,38 C55.6,32.6 67,32.6 67.4,38 C63.8,36 58.8,36 55.2,38Z" fill="url(#fur)"/>
 <path d="M32.6,38 C36.2,36 41.2,36 44.8,38" stroke="#4A1F14" stroke-width="1.05" fill="none" stroke-linecap="round"/>
 <path d="M55.2,38 C58.8,36 63.8,36 67.4,38" stroke="#4A1F14" stroke-width="1.05" fill="none" stroke-linecap="round"/>
</g>
<path d="M24,43 C21,12 79,12 76,43" stroke="#7C57E0" stroke-width="5" fill="none" stroke-linecap="round"/>
<path d="M24,43 C21,12 79,12 76,43" stroke="url(#hp)" stroke-width="3.4" fill="none" stroke-linecap="round"/>
<ellipse cx="23.5" cy="45" rx="6.6" ry="9" fill="url(#hp)"/><ellipse cx="76.5" cy="45" rx="6.6" ry="9" fill="url(#hp)"/>
<ellipse cx="26" cy="45" rx="3.2" ry="6.4" fill="#FF93BD"/><ellipse cx="74" cy="45" rx="3.2" ry="6.4" fill="#FF93BD"/>
'''

PAGES = '''
<!-- livre tenu ouvert devant le ventre, vu de dos : la couverture nous fait face, les pages (et la lumière) sont tournées vers le chat -->
<path d="M25,71 C35,66.6 44,66.6 50,69 L50,91 C44,88.4 35,88.4 26,92Z" fill="url(#coverL)"/>
<path d="M75,71 C65,66.6 56,66.6 50,69 L50,91 C56,88.4 65,88.4 74,92Z" fill="url(#coverR)"/>
<path d="M25,71 C35,66.6 44,66.6 50,69 L50,76 C44,73.6 35,73.6 26,77Z" fill="url(#rimTop)"/>
<path d="M75,71 C65,66.6 56,66.6 50,69 L50,76 C56,73.6 65,73.6 74,77Z" fill="url(#rimTop)"/>
<path d="M26.5,91.6 C35,88 44,88 50,90.4 C56,88 65,88 73.5,91.6" stroke="#E9C46A" stroke-width="0.9" fill="none" stroke-linecap="round" opacity="0.85"/>
<path d="M50,69.4 L50,90.6" stroke="#CDB9FF" stroke-width="1.3" opacity="0.75"/>
<!-- tranche des pages, éclairée par la lumière du livre : un liseré lumineux au bord haut -->
<path d="M24.4,70.4 C35,65.2 44,65 50,67.8 C56,65 65,65.2 75.6,70.4" stroke="#FFEFB8" stroke-width="2.8" fill="none" stroke-linecap="round"/>
<path d="M24.4,70.4 C35,65.2 44,65 50,67.8 C56,65 65,65.2 75.6,70.4" stroke="#FFFFFF" stroke-width="1.1" fill="none" stroke-linecap="round" opacity="0.95"/>
'''

PAWS = '''
<ellipse cx="27" cy="82.5" rx="6.8" ry="5" fill="url(#fur)"/><ellipse cx="73" cy="82.5" rx="6.8" ry="5" fill="url(#fur)"/>
<g stroke="#B9531C" stroke-width="0.7" opacity="0.55" stroke-linecap="round" fill="none"><path d="M24.5,80.8 L24.5,83.6"/><path d="M27,80.2 L27,83.6"/><path d="M29.5,80.8 L29.5,83.6"/><path d="M70.5,80.8 L70.5,83.6"/><path d="M73,80.2 L73,83.6"/><path d="M75.5,80.8 L75.5,83.6"/></g>
'''

def layer(inner, size):
    png = cairosvg.svg2png(bytestring=_svg(inner, size).encode())
    return Image.open(io.BytesIO(png)).convert("RGBA")
