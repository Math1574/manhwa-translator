package app.manhwa.core

/**
 * Expressions toutes faites, verbes à particule, titres d'arts martiaux et faux amis de l'anglais : un petit modèle les traduit volontiers mot à mot
 * (« kill two birds with one stone » → « tuer deux oiseaux », « get away » → « se lever », « scum » → « écume »).
 *
 * Deux usages :
 *  - [find] repère ces expressions dans les lignes à traduire ; le prompt donne alors au modèle leur équivalent français ;
 *  - [calques] repère, dans une traduction déjà faite, le rendu mot à mot de l'une d'elles : la ligne est refaite avec l'équivalent.
 *
 * Format de la table (une expression par ligne) : `expression anglaise | équivalent français | [rendu fautif en français, expression régulière]`.
 * Dans l'expression : `~verbe` (toutes les formes : « ~get » = get, gets, got, gotten, getting), `*` (zéro à trois mots quelconques), `a/b/c` (l'un de ces mots), `@be` (une forme du verbe être).
 */
object IdiomHints {
    /** Une expression repérée dans la ligne [line] (à partir de 1) : [matched] est le texte de la ligne, [french] ce qu'il faut dire. */
    data class Hit(val line: Int, val matched: String, val french: String)

    private class Entry(val phrase: String, val french: String, val bad: Regex?, val pattern: Regex, val words: Int)

    /** Au plus [maxPerLine] expressions par ligne et [maxTotal] par demande : le prompt reste court. */
    fun find(texts: List<String>, maxTotal: Int = 10, maxPerLine: Int = 3): List<Hit> {
        val out = ArrayList<Hit>()
        for ((i, raw) in texts.withIndex()) {
            if (out.size >= maxTotal) break
            val s = normalize(raw)
            if (s.length < 6) continue
            for (h in matches(s).take(maxPerLine)) { if (out.size >= maxTotal) break; out.add(Hit(i + 1, h.second, h.first.french)) }
        }
        return out
    }

    /** Expressions de [src] dont la traduction [tr] est le rendu mot à mot connu. */
    fun calques(src: String, tr: String): List<Hit> {
        val s = normalize(src)
        if (s.length < 6) return emptyList()
        val low = tr.lowercase()
        return matches(s).filter { (e, _) -> e.bad != null && e.bad.containsMatchIn(low) }.map { (e, m) -> Hit(1, m, e.french) }
    }

    // ---------- mise en correspondance ----------

    private fun normalize(raw: String): String {
        val sb = StringBuilder(" ")
        for (ch in raw.lowercase()) sb.append(if (ch in 'a'..'z' || ch in '0'..'9' || ch == '\'') ch else if (ch == '’') '\'' else ' ')
        return sb.toString().replace(Regex(" +"), " ").let { if (it.endsWith(" ")) it else "$it " }
    }

    /** Expressions présentes dans [s] (texte normalisé), les plus longues d'abord ; une expression contenue dans une autre déjà retenue est écartée. */
    private fun matches(s: String): List<Pair<Entry, String>> {
        val found = ArrayList<Triple<Entry, IntRange, String>>()
        for (e in entries) {
            val m = e.pattern.find(s) ?: continue
            found.add(Triple(e, m.range, m.value.trim()))
        }
        found.sortWith(compareByDescending<Triple<Entry, IntRange, String>> { it.second.last - it.second.first }.thenBy { it.second.first })
        val kept = ArrayList<Triple<Entry, IntRange, String>>()
        for (f in found) {
            if (kept.any { k -> f.second.first >= k.second.first && f.second.last <= k.second.last }) continue
            kept.add(f)
        }
        return kept.sortedBy { it.second.first }.map { it.first to it.third }
    }

    // ---------- lecture de la table ----------

    private val irregular: Map<String, List<String>> = listOf(
        "get got gotten getting", "take took taken taking", "go goes went gone going", "come came coming", "give gave given giving", "make made making",
        "run ran running", "break broke broken breaking", "hold held holding", "let letting", "put putting", "keep kept keeping", "bring brought bringing",
        "fall fell fallen falling", "stand stood standing", "see saw seen seeing", "cut cutting", "hit hitting", "set setting", "shut shutting", "sit sat sitting",
        "speak spoke spoken speaking", "tell told telling", "think thought thinking", "throw threw thrown throwing", "wake woke woken waking", "lose lost losing",
        "pay paid paying", "bite bit bitten biting", "beat beaten beating", "feel felt feeling", "leave left leaving", "mean meant meaning", "find found finding",
        "fight fought fighting", "know knew known knowing", "say said saying", "send sent sending", "shoot shot shooting", "sleep slept sleeping", "swear swore sworn swearing",
        "swing swung swinging", "teach taught teaching", "tear tore torn tearing", "understand understood", "win won winning", "draw drew drawn drawing", "drive drove driven driving",
        "eat ate eaten eating", "fly flew flown flying", "forget forgot forgotten forgetting", "hide hid hidden hiding", "lay laid laying", "lend lent lending", "read reading",
        "ride rode ridden riding", "rise rose risen rising", "seek sought seeking", "sell sold selling", "spend spent spending", "strike struck striking", "stick stuck sticking",
        "wear wore worn wearing", "bleed bled", "burn burnt burned", "dig dug digging", "hang hung hanging", "shake shook shaken shaking", "slip slipped slipping", "sneak snuck sneaked",
    ).associate { l -> val p = l.split(" "); p[0] to (p + (p[0] + "s")).distinct() }

    private fun forms(v: String): List<String> {
        irregular[v]?.let { return it }
        val out = LinkedHashSet<String>(); out.add(v)
        val e = v.endsWith("e")
        out.add(if (v.endsWith("s") || v.endsWith("x") || v.endsWith("z") || v.endsWith("ch") || v.endsWith("sh")) v + "es" else if (v.endsWith("y") && v.length > 2 && v[v.length - 2] !in "aeiou") v.dropLast(1) + "ies" else v + "s")
        out.add(if (e) v + "d" else if (v.endsWith("y") && v.length > 2 && v[v.length - 2] !in "aeiou") v.dropLast(1) + "ied" else v + "ed")
        out.add(if (e) v.dropLast(1) + "ing" else v + "ing")
        if (!e && v.length >= 3) { out.add(v + v.last() + "ed"); out.add(v + v.last() + "ing") }   // « stop » → « stopped », « stopping »
        return out.toList()
    }

    private fun compile(phrase: String): Regex {
        val sb = StringBuilder("(?<= )")
        val toks = phrase.trim().split(Regex("\\s+"))
        for ((i, t) in toks.withIndex()) {
            val last = i == toks.size - 1
            when {
                t == "*" -> sb.append("(?:[a-z0-9']+ ){0,3}?")
                t == "@be" -> sb.append("(?:be|is|am|are|was|were|been|being|i'm|you're|he's|she's|we're|they're|it's|that's|i've|you've|we've|they've) ")
                t.startsWith("~") -> sb.append("(?:").append(forms(t.substring(1)).joinToString("|") { Regex.escape(it) }).append(")").append(if (last) "" else " ")
                t.contains('/') -> sb.append("(?:").append(t.split('/').joinToString("|") { Regex.escape(it) }).append(")").append(if (last) "" else " ")
                else -> sb.append(Regex.escape(t)).append(if (last) "" else " ")
            }
        }
        sb.append("(?= )")
        return Regex(sb.toString())
    }

    private val entries: List<Entry> by lazy {
        TABLE.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.mapNotNull { line ->
            val p = line.split(" | ").map { it.trim() }
            if (p.size < 2) return@mapNotNull null
            Entry(p[0], p[1], p.getOrNull(2)?.takeIf { it.isNotEmpty() }?.let { Regex(it) }, compile(p[0]), p[0].split(" ").size)
        }
    }

    /** Nombre d'expressions de la table (sert aux tests). */
    internal fun size(): Int = entries.size
    internal fun phrases(): List<String> = entries.map { it.phrase }

    // ---------- la table ----------

    private val TABLE = """
# ===== verbes à particule : « get » =====
~get away | s'enfuir, s'échapper ; « get away from » = s'éloigner de, s'écarter de
~get away with | s'en tirer sans être puni, s'en sortir impunément
~get over | se remettre de, surmonter, passer à autre chose
~get in the way | gêner, se mettre en travers du chemin
~get * on * side | rallier à sa cause, mettre de son côté
~get * out of * sight | ôter de ma vue, éloigner
~get a grip | ressaisis-toi, reprends-toi
~get * together | se ressaisir ; réunir
~get even | se venger, rendre la pareille
~get through | traverser, venir à bout de, survivre à
~get * wrong | se tromper, mal comprendre
~get the better of | l'emporter sur, prendre le dessus sur
~get hold of | mettre la main sur, se procurer
# ===== « take », « put » =====
~take down | abattre, vaincre
~take it easy | du calme, vas-y doucement
~take * revenge | se venger
~take it out on | passer ses nerfs sur
~take * life | tuer, ôter la vie
~take kindly to | apprécier, bien prendre
~put up with | supporter, tolérer
~put * through | faire subir, faire endurer
~put * in * place | remettre à sa place
~put * shoes | se mettre à la place de
# ===== « go », « come » =====
~go * wrong | mal tourner
~come up with | trouver, imaginer
~come to think of it | à bien y réfléchir, maintenant que j'y pense
# ===== « give », « turn », « look », « run » =====
~give * away | trahir, révéler ; offrir
~turn against | se retourner contre
~turn * against | monter contre, dresser contre | transform\w* .{0,30}contre
~look the other way | fermer les yeux, détourner le regard
~run * mouth | l'ouvrir, jacasser, dire n'importe quoi
~run wild | se déchaîner
~run over | écraser, renverser
# ===== « break », « hold », « let », « make » =====
~break down | s'effondrer, tomber en panne
~hold * ground | tenir bon, ne pas reculer
~hand over | remettre, livrer, rendre
~hand * over | remettre, livrer
~let * down | décevoir
~let * off | épargner, laisser partir
~let * know | faire savoir, prévenir
~let * slide | laisser passer, fermer les yeux
~make sense | avoir du sens, être logique ; « can't make sense of » = ne rien comprendre à | fai(t|s|sai[ts]?|ront|rait|ent)\w* du sens
~make up * mind | se décider
~make up for | compenser, se rattraper
~make a scene | faire un scandale
# ===== autres verbes à particule =====
~pull off | réussir, réaliser
~pass away | décéder, mourir
~show off | frimer, se vanter
~stand by | rester à disposition, soutenir
~fall for | tomber amoureux de ; se laisser berner par
~back down | céder, battre en retraite
~sit * right with | plaire (« it doesn't sit right with me » = ça ne me plaît pas)
~lash out | s'emporter, s'en prendre à
~throw * away | jeter, gâcher (« throw away a chance » = laisser passer une occasion)
~play dumb | faire l'innocent
~pretend | faire semblant de | prétend
# ===== expressions toutes faites (proverbes, images) =====
~kill two birds with one stone | faire d'une pierre deux coups | oiseau
piece of cake | un jeu d'enfant, fastoche | gâteau
~rain cats and dogs | pleuvoir des cordes | chats et
~break a leg | bonne chance, merde | jambe
~spill the beans | vendre la mèche, tout déballer | haricot
~let the cat out of the bag | vendre la mèche | chat (hors|sort)
under the weather | patraque, pas dans son assiette | sous le temps
~cost an arm and a leg | coûter les yeux de la tête | bras et
~bite the bullet | serrer les dents, s'y résoudre | balle
when pigs fly | quand les poules auront des dents | cochon
the last straw | la goutte d'eau qui fait déborder le vase | dernière paille
~beat around the bush | tourner autour du pot | buisson
~hit the nail on the head | mettre le doigt dessus, faire mouche | clou
~cry over spilled milk | pleurer sur le passé, ce qui est fait est fait | lait renversé
~bark up the wrong tree | se tromper de cible, faire fausse route | mauvais arbre
~add insult to injury | en rajouter une couche | insulte à la blessure
a blessing in disguise | un mal pour un bien | bénédiction déguisée
once in a blue moon | tous les trente-six du mois, très rarement | lune bleue
on the same page | sur la même longueur d'onde | même page
no pain no gain | on n'a rien sans rien | pas de douleur
~speak of the devil | quand on parle du loup | diable
~ring a bell | ça me dit quelque chose | cloche
~let sleeping dogs lie | ne pas réveiller le chat qui dort
off the hook | tiré d'affaire, tranquille
through thick and thin | contre vents et marées, pour le meilleur et pour le pire
to make a long story short | bref, pour faire court
water under the bridge | c'est du passé, ce qui est fait est fait
the tables have turned | les rôles sont inversés
the tip of the iceberg | la partie émergée de l'iceberg
cold feet | la trouille, se dégonfler | pieds froids
out of the blue | à l'improviste, sans prévenir | bleu
on thin ice | en terrain glissant, en situation risquée | glace mince
~cut corners | bâcler, rogner sur les moyens
~cut to the chase | aller droit au but | chasse
~call it a day | s'arrêter là, en rester là
~face the music | assumer les conséquences | musique
~jump the gun | agir trop vite, précipiter les choses
~miss the boat | rater le coche, laisser passer sa chance | bateau
~pull * leg | faire marcher
~twist * arm | forcer la main
~see eye to eye | être du même avis
~throw in the towel | jeter l'éponge | serviette
~walk on eggshells | marcher sur des œufs | coquilles d'œuf
~play it by ear | improviser | oreille
~sleep on it | la nuit porte conseil
~bury the hatchet | enterrer la hache de guerre
~burn bridges | couper les ponts
~steal * thunder | voler la vedette | tonnerre
~give * a piece of * mind | dire ses quatre vérités
~give * the slip | semer
~hold * tongue | tenir sa langue, se taire
~turn a blind eye | fermer les yeux sur
~turn * blind eye | fermer les yeux sur
~bat an eye | sourciller, ciller
~lose * mind | perdre la tête
~hit the jackpot | décrocher le gros lot
~come clean | avouer, dire la vérité
~hang in there | tiens bon, accroche-toi
~hold a grudge | garder rancune
~think twice | réfléchir à deux fois
needless to say | inutile de le dire, cela va sans dire
to say the least | c'est le moins qu'on puisse dire
rule of thumb | règle générale
a pain in the neck | un casse-pieds, une plaie
take it with a grain of salt | à prendre avec des pincettes | grain de sel
the whole nine yards | le grand jeu, tout le tralala | neuf mètres|neuf yards
the writing on the wall | c'était écrit, les signes ne trompent pas
in the nick of time | juste à temps, de justesse
~touch * hair | toucher à un cheveu de (« il ne m'a pas touché un cheveu »)
~bite off more than * can chew | voir trop grand, viser trop haut
# ===== formules de dialogue =====
not a chance | pas question, jamais de la vie | pas une chance
over my dead body | il faudra me passer sur le corps
leave it to me | laisse-moi faire, je m'en occupe
mind your own business | occupe-toi de tes affaires
none of your business | ça ne te regarde pas
you'd better not | n'essaie surtout pas de, tu ferais mieux de ne pas | fais attention à ne pas
you'd better | tu ferais mieux de
i'd better | je ferais mieux de
had better | ferait mieux de
what the hell | mais qu'est-ce que, bon sang
who the hell | mais qui diable, qui est ce
how the hell | comment diable
i owe you one | je te dois une fière chandelle
beats me | aucune idée
hold your horses | du calme, doucement
to think that | dire que, quand on pense que
@be fed up | en avoir assez, en avoir marre
what are you up to | qu'est-ce que tu manigances
in heat | en chaleur | période de chaleur
on edge | sur les nerfs
no matter what | quoi qu'il arrive, coûte que coûte
no wonder | pas étonnant que
# ===== faux amis et mots piégeux =====
~realize that/how/what/why/who/where/when/it/this/i/you/he/she/we/they/now/just | se rendre compte (de), comprendre, prendre conscience (jamais « réaliser » pour « comprendre ») | réalis(?!é par)
definitely | à coup sûr, sans aucun doute, forcément (jamais « définitivement ») | définitivement
actually | en fait, à vrai dire (« actuellement » est faux) | actuellement
eventually | finalement, à la longue (« éventuellement » est faux) | éventuellement
sensible | raisonnable, sensé | \bsensible\b
scum | racaille, ordure, vermine (jamais « écume ») | écume
wench | garce, donzelle
brat | morveux, sale gosse
ugly mug | sale tronche
sappy | à l'eau de rose, mièvre
bullshit | conneries, foutaises
cover your ears | bouchez-vous les oreilles, bouche-toi les oreilles | \bcouvrir (vos|tes)\b
# ===== titres, rangs et termes d'arts martiaux =====
eldest young master | jeune maître aîné (le fils aîné) | plus jeune maître|plus âgé des jeunes
young lady | jeune demoiselle, mademoiselle
senior brother | grand frère d'armes, aîné | senior
junior brother | petit frère d'armes, cadet | junior
senior sister | grande sœur d'armes | senior
junior sister | petite sœur d'armes | junior
martial brother | frère d'armes
martial sister | sœur d'armes
martial uncle | oncle d'armes
senior | aîné, vénérable (titre d'arts martiaux) | \bsenior\b
junior | cadet (titre d'arts martiaux) | \bjunior\b
sect leader | chef de secte
sect master | maître de secte
alliance leader | chef de l'Alliance
association leader | chef de l'Association
clan head | chef de clan
grandmaster | grand maître
~inner energy | énergie interne
qi deviation | déviation du qi
~cultivation base | niveau de cultivation
"""
}
