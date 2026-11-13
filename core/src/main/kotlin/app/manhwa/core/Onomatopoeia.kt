package app.manhwa.core

/**
 * Onomatopées (bruitages) : on les laisse telles quelles, sans les traduire ni toucher à l'image. Un bruitage est un dessin autant qu'un texte : l'effacer puis le redessiner en
 * police de remplacement l'abîme, et sa « traduction » n'apporte rien. Règle volontairement prudente, pour ne jamais écarter une réplique ni un mot qui se traduit :
 *  - seuls les textes hors bulle comptent (un « HMPH » ou un « HAHAHA » dans une bulle de taille normale est une réplique : traduit) ;
 *  - un bloc de trois mots au plus ;
 *  - les bruitages qui ne servent qu'à cela (« BOOM », « THUD », « WHOOSH », « TSK ») sont écartés à toute taille ;
 *  - les bruitages qui sont aussi de vrais mots (« SLASH », « CLICK », « SIGH », « POP ») ne le sont qu'en gros lettrage, ou répétés (« CLAP CLAP ») : seuls et en taille normale ils
 *    restent traduits (légende, nom de compétence, réplique) ;
 *  - un bruitage inventé ou romanisé est reconnu à sa FORME (lettres étirées « KRAAAK », aucune voyelle « TCH », syllabe répétée « DUDUDUK »), jamais parce qu'un gros mot est absent du
 *    dictionnaire : un nom rare ou un mot peu courant en grand lettrage est traduit comme le reste. Seul un très gros lettrage d'un mot unique inconnu est laissé (décor).
 * Anglais seulement : le japonais, le coréen et le chinois ne sont pas concernés.
 */
object Onomatopoeia {
    private fun squash(w: String): String {
        val sb = StringBuilder()
        for (c in w.lowercase()) if (sb.isEmpty() || sb.last() != c) sb.append(c)
        return sb.toString()
    }

    private fun set(words: String): Set<String> = words.split(' ').filter { it.isNotBlank() }.map { squash(it) }.toSet()

    // bruitages qui ne servent qu'à cela ; comparés après réduction des lettres répétées (« BOOOM » = « BOOM »)
    private val pure: Set<String> = set(
        "boom bang bam wham pow bop zap whack thwack thump thud thunk clunk clonk bonk wallop kapow blam kaboom kablam crash crack krash krak krack thwomp whomp whump " +
        "splat plop squelch gurgle glug slosh sploosh sizzle fizz crackle drip plonk plunk plink squish squirt spurt " +
        "swish swoosh whoosh whish whirr whir whiz vroom fwoosh whoomp thwip zing shing whap doink boing " +
        "clack clang clank clink jangle ding dong bong gong beep boop honk bzzt bzz tick tock rattle rumble creak squeak squeal screech rustle crunch munch chomp slurp clomp clop patter " +
        "thrum twang ting tink dink pling tinkle ping blip bleep toot zzt zzz " +
        "hiss growl snarl purr meow woof chirp tweet squawk caw hoot " +
        "gulp gasp huff puff pant sniff snort tsk tch hmph grr brr pfft psst shh ahem achoo smooch mwah",
    )

    // bruitages qui sont aussi de vrais mots : bruitage seulement en gros lettrage ou répétés
    private val ambiguous: Set<String> = set(
        "snap pop smash bash slam slash slice chop slap whip flap flutter flick click knock ring scratch scrape clap stomp zip zoom swoop splash chime hum roar howl buzz sigh " +
        "tap pat step kiss cough burp yawn snore dun dum doom",
    )

    private val word = Regex("[A-Za-z]+")
    private val vowels = "aeiouy"
    // petits mots de réplique qui peuvent se répéter (« NO NO NO », « HA HA ») : jamais une syllabe de bruitage
    private val speechUnits = setOf("no", "so", "ha", "he", "ho", "go", "oh", "ah", "eh", "uh", "um", "hm", "ok", "ya", "ye", "yo", "do", "to", "be", "me", "we")

    /** Forme d'un bruitage : lettres étirées, aucune voyelle ou syllabe répétée, pour un mot qui n'est pas un mot anglais. */
    private fun soundShaped(w: String, lex: EnglishLexicon?): Boolean {
        val l = w.lowercase()
        if (l.length < 3) return false
        val sq = squash(l)
        val unknown = lex == null || !lex.known(sq)
        // lettres étirées : trois fois la même lettre de suite (AAAAH, KRAAAK, GRRRR, SHHHH) ; « NOOOO » (= « no ») est une réplique
        if (Regex("(.)\\1\\1").containsMatchIn(l)) return unknown
        // aucune voyelle (TCH, PFFT, BRRR, HMPH, PSST)
        if (l.none { it in vowels }) return lex == null || !lex.known(l)
        // syllabe de deux ou trois lettres répétée au moins trois fois (DUDUDUK, TATATA)
        for (p in 2..3) {
            if (l.length < 3 * p) continue
            val u = l.substring(0, p)
            if (u in speechUnits) continue
            var reps = 0; var i = 0
            while (i + p <= l.length && l.startsWith(u, i)) { reps++; i += p }
            if (reps >= 3) return lex == null || !lex.known(l)
        }
        return false
    }

    /**
     * [relativeHeight] : hauteur de ligne du bloc rapportée à la médiane de la page (un gros lettrage dépasse 1,3). [lex] : lexique anglais ; sans lui, seule la forme
     * des mots compte.
     */
    fun isSfx(text: String, kind: BlockKind, relativeHeight: Float, lex: EnglishLexicon?): Boolean {
        // dans une bulle, une réplique reste traduite ; sauf un gros lettrage de bruitage que le détecteur a pris pour une bulle (le texte d'une vraie bulle a la taille normale)
        if (kind == BlockKind.IN_BUBBLE && relativeHeight < 1.3f) return false
        if (ScriptDetector.dominant(text) != "en") return false
        // une apostrophe au milieu d'un mot (« DON'T », « I'LL ») : c'est une réplique, pas un bruitage
        if (Regex("[A-Za-z]['’][A-Za-z]").containsMatchIn(text)) return false
        val toks = word.findAll(text).map { it.value }.toList()
        if (toks.isEmpty() || toks.size > 3) return false
        val sq = toks.map { squash(it) }
        // gros lettrage : seuil plus strict quand on ne sait pas si le texte est dans une bulle
        val big = if (kind == BlockKind.FREE) relativeHeight >= 1.3f else relativeHeight >= 1.6f
        if (sq.all { it in pure }) return true
        if (sq.all { it in pure || it in ambiguous } && (big || toks.size >= 2)) return true
        if (kind == BlockKind.IN_BUBBLE) return false
        // bruitage romanisé ou étiré hors bulle : deux mots au plus, tous de forme sonore
        if (toks.size <= 2 && toks.all { soundShaped(it, lex) }) return true
        // très gros lettrage d'un mot unique inconnu (titre, nom, bruitage dessiné) : laissé tel quel
        if (lex != null && toks.size == 1 && relativeHeight >= 2.0f && toks[0].length >= 4 && !lex.known(toks[0])) return true
        return false
    }
}
