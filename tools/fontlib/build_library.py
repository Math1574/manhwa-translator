#!/usr/bin/env python3
"""Bibliothèque de polices du texte traduit : source unique des candidats.

Produit :
  - core/src/main/kotlin/app/manhwa/core/FontLibrary.kt  (liste des candidats, utilisée par l'application et les tests)
  - app/src/main/assets/fonts/lib/*.ttf                  (fichiers de police copiés depuis --src)
  - --static DIR (optionnel) : une police statique par candidat (instances des polices variables), pour mesurer en JVM (Java2D ne gère pas les axes)

Toutes les polices sont libres (SIL OFL ou Apache 2.0), téléchargées depuis le dépôt Google Fonts.
"""
import argparse, os, shutil, sys

# (fichier source dans --src, nom du fichier dans l'application)
FILES = {
 "ComicNeue-Regular.ttf": "comicneue/ComicNeue-Regular.ttf", "ComicNeue-Bold.ttf": "comicneue/ComicNeue-Bold.ttf", "ComicNeue-Light.ttf": "comicneue/ComicNeue-Light.ttf",
 "ComicNeue-Italic.ttf": "comicneue/ComicNeue-Italic.ttf", "ComicNeue-BoldItalic.ttf": "comicneue/ComicNeue-BoldItalic.ttf",
 "Bangers-Regular.ttf": "bangers/Bangers-Regular.ttf", "Boogaloo-Regular.ttf": "boogaloo/Boogaloo-Regular.ttf", "Chewy-Regular.ttf": "chewy/Chewy-Regular.ttf",
 "GochiHand-Regular.ttf": "gochihand/GochiHand-Regular.ttf", "PatrickHand-Regular.ttf": "patrickhand/PatrickHand-Regular.ttf", "PatrickHandSC-Regular.ttf": "patrickhandsc/PatrickHandSC-Regular.ttf",
 "Kalam-Light.ttf": "kalam/Kalam-Light.ttf", "Kalam-Regular.ttf": "kalam/Kalam-Regular.ttf", "Kalam-Bold.ttf": "kalam/Kalam-Bold.ttf",
 "Caveat-VF.ttf": "caveat/Caveat[wght].ttf", "CaveatBrush-Regular.ttf": "caveatbrush/CaveatBrush-Regular.ttf",
 "GloriaHallelujah.ttf": "gloriahallelujah/GloriaHallelujah.ttf", "IndieFlower-Regular.ttf": "indieflower/IndieFlower-Regular.ttf",
 "ShadowsIntoLightTwo-Regular.ttf": "shadowsintolighttwo/ShadowsIntoLightTwo-Regular.ttf", "ArchitectsDaughter-Regular.ttf": "architectsdaughter/ArchitectsDaughter-Regular.ttf",
 "CoveredByYourGrace.ttf": "coveredbyyourgrace/CoveredByYourGrace.ttf", "ShortStack-Regular.ttf": "shortstack/ShortStack-Regular.ttf", "Delius-Regular.ttf": "delius/Delius-Regular.ttf",
 "PermanentMarker-Regular.ttf": "permanentmarker/PermanentMarker-Regular.ttf", "RockSalt-Regular.ttf": "rocksalt/RockSalt-Regular.ttf",
 "LuckiestGuy-Regular.ttf": "luckiestguy/LuckiestGuy-Regular.ttf", "LilitaOne-Regular.ttf": "lilitaone/LilitaOne-Regular.ttf",
 "LondrinaSolid-Light.ttf": "londrinasolid/LondrinaSolid-Light.ttf", "LondrinaSolid-Regular.ttf": "londrinasolid/LondrinaSolid-Regular.ttf", "LondrinaSolid-Black.ttf": "londrinasolid/LondrinaSolid-Black.ttf",
 "Sniglet-Regular.ttf": "sniglet/Sniglet-Regular.ttf", "Sniglet-ExtraBold.ttf": "sniglet/Sniglet-ExtraBold.ttf",
 "Fredoka-VF.ttf": "fredoka/Fredoka[wdth,wght].ttf", "Baloo2-VF.ttf": "baloo2/Baloo2[wght].ttf", "Nunito-VF.ttf": "nunito/Nunito[wght].ttf", "Recursive-VF.ttf": "recursive/Recursive[CASL,CRSV,MONO,slnt,wght].ttf",
 "Anton-Regular.ttf": "anton/Anton-Regular.ttf", "BebasNeue-Regular.ttf": "bebasneue/BebasNeue-Regular.ttf", "FjallaOne-Regular.ttf": "fjallaone/FjallaOne-Regular.ttf",
 "Antonio-VF.ttf": "antonio/Antonio[wght].ttf", "LeagueGothic-VF.ttf": "leaguegothic/LeagueGothic[wdth].ttf", "Oswald-VF.ttf": "oswald/Oswald[wght].ttf",
 "RobotoCondensed-VF.ttf": "robotocondensed/RobotoCondensed[wght].ttf", "Archivo-VF.ttf": "archivo/Archivo[wdth,wght].ttf",
 "NotoSans-VF.ttf": "notosans/NotoSans[wdth,wght].ttf", "NotoSerif-VF.ttf": "notoserif/NotoSerif[wdth,wght].ttf", "Lora-VF.ttf": "lora/Lora[wght].ttf",
 "Playfair-VF.ttf": "playfairdisplay/PlayfairDisplay[wght].ttf", "Cinzel-VF.ttf": "cinzel/Cinzel[wght].ttf",
 "CourierPrime-Regular.ttf": "courierprime/CourierPrime-Regular.ttf", "CourierPrime-Bold.ttf": "courierprime/CourierPrime-Bold.ttf", "SpecialElite-Regular.ttf": "specialelite/SpecialElite-Regular.ttf",
 # polices stylisées (voir STYLED plus bas)
 "Rajdhani-Bold.ttf": "rajdhani/Rajdhani-Bold.ttf", "Exo2-VF.ttf": "exo2/Exo2[wght].ttf", "Oxanium-VF.ttf": "oxanium/Oxanium[wght].ttf",
 "PirataOne-Regular.ttf": "pirataone/PirataOne-Regular.ttf", "MedievalSharp.ttf": "medievalsharp/MedievalSharp.ttf", "Almendra-Regular.ttf": "almendra/Almendra-Regular.ttf", "Almendra-Bold.ttf": "almendra/Almendra-Bold.ttf",
 "Creepster-Regular.ttf": "creepster/Creepster-Regular.ttf", "Butcherman-Regular.ttf": "butcherman/Butcherman-Regular.ttf",
 "DancingScript-VF.ttf": "dancingscript/DancingScript[wght].ttf", "KaushanScript-Regular.ttf": "kaushanscript/KaushanScript-Regular.ttf",
 "BlackOpsOne-Regular.ttf": "blackopsone/BlackOpsOne-Regular.ttf", "StardosStencil-Bold.ttf": "stardosstencil/StardosStencil-Bold.ttf",
 "TitanOne-Regular.ttf": "titanone/TitanOne-Regular.ttf", "Knewave-Regular.ttf": "knewave/Knewave-Regular.ttf",
}

# classes grossières : comic (lettrage de bulle rond ou gras), hand (écriture fine), brush (marqueur, pinceau), display (très gras de titre), condensed (condensé numérique),
# sans (sans empattement neutre ou arrondi), serif, mono
specs = []   # (id, fichier, axes dict, classe, inclinaison native)
# polices choisies automatiquement : robustes, lisibles en français à toute taille, jamais fines ni décoratives (Rock Salt, Covered By Your Grace, Caveat fin, Patrick Hand, Kalam léger,
# Comic Neue léger, Sniglet léger, machine à écrire, Cinzel… restent dans la bibliothèque mais ne sont jamais retenues : retour d'usage sur le build 110, qui les choisissait pour des bulles
# au lettrage gras, avec des polices différentes d'une bulle à l'autre alors que l'original a la même)
AUTO = {"comicneue-bold", "caveatbrush", "boogaloo", "chewy", "baloo2-w700", "fredoka-w600-d100", "recursive-c1-w800",    # script, comic, brush : lettrage de bulle
        "anton", "oswald-w700", "oswald-w600", "archivo-w900-d62", "archivo-w700-d62", "bebasneue", "bangers",             # condensés numériques et cris
        "notosans-w700-d100", "notosans-w600-d100", "archivo-w700-d100",                                                  # sans empattement neutres
        "lora-w700", "notoserif-w700-d100"}                                                                               # empattement (narration, si les montants sont nettement plus épais que les barres)
# pénalités douces (points de score) pour les polices qui plaisent moins quand une autre convient aussi
PENALTY = {"bangers": 0.05, "recursive-c1-w800": 0.03}
def S(i, f, cls, axes=None, italic=False): specs.append((i, f, axes or {}, cls, italic))
def fmt(d): return str(d).rstrip('0').rstrip('.') if isinstance(d, float) else str(d)

# --- lettrage de bulle (comic)
S("comicneue-light", "ComicNeue-Light.ttf", "comic"); S("comicneue-regular", "ComicNeue-Regular.ttf", "comic"); S("comicneue-bold", "ComicNeue-Bold.ttf", "comic")
S("comicneue-italic", "ComicNeue-Italic.ttf", "comic", italic=True); S("comicneue-bolditalic", "ComicNeue-BoldItalic.ttf", "comic", italic=True)
S("boogaloo", "Boogaloo-Regular.ttf", "comic"); S("chewy", "Chewy-Regular.ttf", "comic"); S("sniglet", "Sniglet-Regular.ttf", "comic"); S("sniglet-extrabold", "Sniglet-ExtraBold.ttf", "comic")
for w in (400, 500, 600, 700, 800): S(f"baloo2-w{w}", "Baloo2-VF.ttf", "comic", {"wght": w})
for d in (75, 100, 125):
    for w in (400, 600, 700): S(f"fredoka-w{w}-d{d}", "Fredoka-VF.ttf", "comic", {"wght": w, "wdth": d})
for c in (0, 1):
    for w in (400, 600, 800, 1000): S(f"recursive-c{c}-w{w}", "Recursive-VF.ttf", "comic" if c else "sans", {"CASL": c, "wght": w, "slnt": 0, "MONO": 0, "CRSV": 0.5})
S("recursive-c1-w600-it", "Recursive-VF.ttf", "comic", {"CASL": 1, "wght": 600, "slnt": -12, "MONO": 0, "CRSV": 0.5}, True)
S("recursive-c1-w800-it", "Recursive-VF.ttf", "comic", {"CASL": 1, "wght": 800, "slnt": -12, "MONO": 0, "CRSV": 0.5}, True)
# --- écriture fine (hand)
S("patrickhand", "PatrickHand-Regular.ttf", "hand"); S("patrickhandsc", "PatrickHandSC-Regular.ttf", "hand"); S("gochihand", "GochiHand-Regular.ttf", "hand")
S("kalam-light", "Kalam-Light.ttf", "hand"); S("kalam-regular", "Kalam-Regular.ttf", "hand"); S("kalam-bold", "Kalam-Bold.ttf", "hand")
for w in (400, 500, 600, 700): S(f"caveat-w{w}", "Caveat-VF.ttf", "hand", {"wght": w})
S("gloriahallelujah", "GloriaHallelujah.ttf", "hand"); S("indieflower", "IndieFlower-Regular.ttf", "hand"); S("shadowsintolight", "ShadowsIntoLightTwo-Regular.ttf", "hand")
S("architectsdaughter", "ArchitectsDaughter-Regular.ttf", "hand"); S("coveredbyyourgrace", "CoveredByYourGrace.ttf", "hand"); S("shortstack", "ShortStack-Regular.ttf", "hand"); S("delius", "Delius-Regular.ttf", "hand")
# --- marqueur, pinceau (brush)
S("caveatbrush", "CaveatBrush-Regular.ttf", "brush"); S("permanentmarker", "PermanentMarker-Regular.ttf", "brush"); S("rocksalt", "RockSalt-Regular.ttf", "brush")
# --- très gras de titre (display)
S("bangers", "Bangers-Regular.ttf", "display"); S("luckiestguy", "LuckiestGuy-Regular.ttf", "display"); S("lilitaone", "LilitaOne-Regular.ttf", "display")
S("londrina-light", "LondrinaSolid-Light.ttf", "display"); S("londrina-regular", "LondrinaSolid-Regular.ttf", "display"); S("londrina-black", "LondrinaSolid-Black.ttf", "display")
# --- condensé numérique
S("anton", "Anton-Regular.ttf", "condensed"); S("bebasneue", "BebasNeue-Regular.ttf", "condensed"); S("fjallaone", "FjallaOne-Regular.ttf", "condensed")
for w in (300, 400, 500, 600, 700): S(f"oswald-w{w}", "Oswald-VF.ttf", "condensed", {"wght": w})
for w in (300, 400, 500, 600, 700): S(f"antonio-w{w}", "Antonio-VF.ttf", "condensed", {"wght": w})
for d in (75, 87.5, 100): S(f"leaguegothic-d{fmt(d)}", "LeagueGothic-VF.ttf", "condensed", {"wdth": d})
for w in (300, 400, 500, 700, 900): S(f"robotocondensed-w{w}", "RobotoCondensed-VF.ttf", "condensed", {"wght": w})
for d in (62, 75, 87.5, 100, 125):
    for w in (500, 700, 900): S(f"archivo-w{w}-d{fmt(d)}", "Archivo-VF.ttf", "condensed" if d <= 75 else "sans", {"wght": w, "wdth": d})
# --- sans empattement neutre ou arrondi
for d in (62.5, 75, 87.5, 100):
    for w in (400, 500, 600, 700, 800): S(f"notosans-w{w}-d{fmt(d)}", "NotoSans-VF.ttf", "sans", {"wght": w, "wdth": d})
for w in (300, 400, 600, 700, 800, 900): S(f"nunito-w{w}", "Nunito-VF.ttf", "sans", {"wght": w})
# --- empattement
for d in (87.5, 100):
    for w in (400, 600, 700, 800): S(f"notoserif-w{w}-d{fmt(d)}", "NotoSerif-VF.ttf", "serif", {"wght": w, "wdth": d})
for w in (400, 500, 600, 700): S(f"lora-w{w}", "Lora-VF.ttf", "serif", {"wght": w})
for w in (400, 600, 800, 900): S(f"playfair-w{w}", "Playfair-VF.ttf", "serif", {"wght": w})
for w in (400, 600, 800): S(f"cinzel-w{w}", "Cinzel-VF.ttf", "serif", {"wght": w})
# --- machine à écrire
S("courierprime", "CourierPrime-Regular.ttf", "mono"); S("courierprime-bold", "CourierPrime-Bold.ttf", "mono"); S("specialelite", "SpecialElite-Regular.ttf", "mono")

# --- polices stylisées : HORS du modèle de style appris (StyleModel) ; elles ne sont jamais choisies par StyleMatcher. StyledFonts les choisit seulement quand l'original le demande
# (fenêtre « système » : technique ; lettrage nettement gothique, d'horreur, script, pochoir ou percutant : par la forme des lettres) ; on peut aussi les choisir à la main dans l'éditeur.
# Toutes couvrent le français (é è ê à ç œ « » ’ …) ; Orbitron, qui n'a pas « » , est écartée.
styled = []   # (id, fichier, axes dict, classe)
def X(i, f, cls, axes=None): styled.append((i, f, axes or {}, cls))
X("rajdhani-bold", "Rajdhani-Bold.ttf", "tech")
for w in (600, 800): X(f"exo2-w{w}", "Exo2-VF.ttf", "tech", {"wght": w})
X("oxanium-w700", "Oxanium-VF.ttf", "tech", {"wght": 700})
X("pirataone", "PirataOne-Regular.ttf", "gothic"); X("medievalsharp", "MedievalSharp.ttf", "gothic"); X("almendra", "Almendra-Regular.ttf", "gothic"); X("almendra-bold", "Almendra-Bold.ttf", "gothic")
X("creepster", "Creepster-Regular.ttf", "horror"); X("butcherman", "Butcherman-Regular.ttf", "horror")
for w in (600, 700): X(f"dancingscript-w{w}", "DancingScript-VF.ttf", "script", {"wght": w})
X("kaushanscript", "KaushanScript-Regular.ttf", "script")
X("blackopsone", "BlackOpsOne-Regular.ttf", "stencil"); X("stardosstencil-bold", "StardosStencil-Bold.ttf", "stencil")
X("titanone", "TitanOne-Regular.ttf", "impact"); X("knewave", "Knewave-Regular.ttf", "impact")

def axes_str(a): return ", ".join(f"'{k}' {fmt(float(v)) if not float(v).is_integer() else int(v)}" for k, v in a.items())

def kotlin():
    L = ["package app.manhwa.core", "",
         "// GÉNÉRÉ par tools/fontlib/build_library.py : ne pas modifier à la main (modifier le script puis le relancer).", "",
         "/** Candidats de police du texte traduit : un fichier de police et, pour une police variable, les valeurs de ses axes. Toutes sont libres (SIL OFL ou Apache 2.0). */",
         "object FontLibrary {",
         "    /** [cls] : classe de style grossière (comic, hand, brush, display, condensed, sans, serif, mono). [italic] : police nativement inclinée. */",
         "    /** [auto] : choisie automatiquement (robuste et lisible) ; [penalty] : pénalité douce de score quand une autre police convient aussi. */",
         "    class Spec(val id: String, val file: String, val axes: String, val cls: String, val italic: Boolean = false, val penalty: Float = 0f, val auto: Boolean = false)", "",
         "    val specs: List<Spec> = listOf("]
    for (i, f, a, c, it) in specs:
        pen = PENALTY.get(i, 0.0); au = i in AUTO
        extra = (", true" if it else (", false" if (pen or au) else "")) + ((f", {pen}f" if pen else ", 0f") if au else (f", {pen}f" if pen else "")) + (", true" if au else "")
        L.append(f'        Spec("{i}", "lib/{f}", "{axes_str(a)}", "{c}"' + extra + "),")
    L += ["    )", "",
          "    /** Polices stylisées (classes tech, gothic, horror, script, stencil, impact) : hors du modèle de style appris, jamais choisies par StyleMatcher ; voir [StyledFonts] et l'éditeur de correction. */",
          "    val styled: List<Spec> = listOf("]
    for (i, f, a, c) in styled:
        L.append(f'        Spec("{i}", "lib/{f}", "{axes_str(a)}", "{c}"),')
    L += ["    )", "", "    private val byId: Map<String, Spec> = (specs + styled).associateBy { it.id }", "    fun spec(id: String): Spec? = byId[id]", "}", ""]
    return "\n".join(L)

if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True, help="dossier des polices téléchargées (sous-dossier par famille)")
    ap.add_argument("--static", help="dossier de sortie des polices statiques (une par candidat), pour les mesures en JVM")
    ap.add_argument("--repo", default=os.path.join(os.path.dirname(__file__), "..", ".."))
    a = ap.parse_args()
    repo = os.path.abspath(a.repo)
    ids = [s[0] for s in specs] + [s[0] for s in styled]
    assert len(ids) == len(set(ids)), "identifiants en double"
    open(os.path.join(repo, "core/src/main/kotlin/app/manhwa/core/FontLibrary.kt"), "w", encoding="utf8").write(kotlin())
    dst = os.path.join(repo, "app/src/main/assets/fonts/lib"); os.makedirs(dst, exist_ok=True)
    for name, rel in FILES.items():
        p = os.path.join(a.src, rel)
        if not os.path.exists(p):
            if os.path.exists(os.path.join(dst, name)): continue          # déjà dans l'application (--src ne contient alors que les nouvelles polices)
            print("manque", p); sys.exit(1)
        shutil.copyfile(p, os.path.join(dst, name))
    print(len(specs), "candidats,", len(styled), "polices stylisées,", len(FILES), "fichiers de police")
    if a.static:
        from fontTools.ttLib import TTFont
        from fontTools.varLib import instancer
        os.makedirs(a.static, exist_ok=True)
        for (i, f, ax, c, it) in specs + [(i, f, ax, c, False) for (i, f, ax, c) in styled]:
            out = os.path.join(a.static, i + ".ttf")
            if os.path.exists(out): continue
            font = TTFont(os.path.join(dst, f))
            if "fvar" in font:
                limits = {axis.axisTag: ax.get(axis.axisTag, axis.defaultValue) for axis in font["fvar"].axes}
                font = instancer.instantiateVariableFont(font, limits)
            font.save(out)
        print("polices statiques écrites dans", a.static)
